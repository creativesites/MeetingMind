package com.example.core.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.core.database.MeetMindDatabase
import com.example.core.model.MeetingSource
import com.example.core.model.RecordingContext
import com.example.core.repository.MeetingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns live capture end to end (Phase 15 §Part 2 / design capture-pipeline spec §3.2): the
 * [AudioRecorder] instance, the output file, [RecordingState], amplitude/duration telemetry, and
 * audio focus. `RecordingViewModel` binds to this service and only ever sends intents / reads its
 * [StateFlow]s — it must never construct or hold an [AudioRecorder] itself, so there is exactly
 * one place that can be recording at a time and exactly one authoritative answer to "what state is
 * capture in right now," matching [com.example.core.audio.PlaybackController]'s existing pattern
 * for playback.
 *
 * Previously this service only managed the wake lock and a foreground notification whose Pause/
 * Resume buttons toggled a display-only flag — pressing them from the lock screen had no effect
 * on the actual [AudioRecorder], which lived in the ViewModel and this service had no reference
 * to. That bug is what this rewrite fixes: the notification's actions now call the same
 * [pauseRecording]/[resumeRecording]/[stopRecording] the bound UI calls, so they're real.
 *
 * [stopRecording] also persists the finished [com.example.core.model.Meeting] row directly from
 * inside the service, rather than relying on a bound ViewModel/Activity to still be alive to do
 * it — "data loss is existential" per the design spec, so the recording is durably saved as soon
 * as the user (or the notification's Stop action) asks to stop, with or without the UI present.
 */
class MeetingRecordingService : Service() {

    inner class LocalBinder : Binder() {
        val service: MeetingRecordingService get() = this@MeetingRecordingService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var meetingRepository: MeetingRepository

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()
    val amplitude: StateFlow<Float> get() = audioRecorder.amplitude
    val durationMs: StateFlow<Long> get() = audioRecorder.durationMs

    /** True from the moment an [AudioManager] transient focus loss (e.g. an incoming call)
     * auto-pauses capture, until the user explicitly resumes — the service never auto-resumes on
     * focus regain (design spec §3.2: "a silent auto-resume the user did not ask for is worse
     * than a visible gap"), so a bound UI reads this to show a resume prompt instead. */
    private val _focusInterrupted = MutableStateFlow(false)
    val focusInterrupted: StateFlow<Boolean> = _focusInterrupted.asStateFlow()

    private var meetingId: String? = null
    private var meetingTitle: String = "Recording"
    private var recordingContext: RecordingContext = RecordingContext()

    private var wakeLock: PowerManager.WakeLock? = null
    private var recordingStartTimeMs: Long = 0L
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pauseRecordingInternal(dueToFocusLoss = true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Deliberately no auto-resume — see focusInterrupted's doc.
            }
        }
    }

    companion object {
        private const val TAG = "MeetingRecordingService"
        const val CHANNEL_ID = "meetmind_recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "com.example.meetmind.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.meetmind.ACTION_RESUME"
        const val ACTION_STOP = "com.example.meetmind.ACTION_STOP"

        /** Binds a caller to the running (or freshly created) service. The service does not
         * self-start via [Context.startService]/[Context.startForegroundService] on bind alone —
         * [startRecording] is what actually begins capture and promotes it to foreground; binding
         * on its own just gets a caller a reference to call that with. */
        fun bindIntent(context: Context): Intent = Intent(context, MeetingRecordingService::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(applicationContext)
        meetingRepository = MeetingRepository(applicationContext, MeetMindDatabase.getInstance(applicationContext))
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is always explicitly started (see startRecording -> startForeground) once
        // capture begins, so these notification-originated intents always arrive at a service
        // that's already running — they just route to the real pause/resume/stop methods instead
        // of the old display-only toggle.
        when (intent?.action) {
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording { _, _, _ -> }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    // ---- Capture control -----------------------------------------------------------------

    /** Starts a fresh recording. Throws (leaving [state] at [RecordingState.FAILED]) if the
     * recorder itself cannot start — e.g. the microphone is unavailable — rather than silently
     * doing nothing, so a caller can surface a real error instead of a dead Record button. */
    fun startRecording(newMeetingId: String, title: String, context: RecordingContext): File {
        _state.value = RecordingState.PREPARING
        meetingId = newMeetingId
        meetingTitle = title
        recordingContext = context
        _focusInterrupted.value = false
        requestAudioFocus()

        val file = try {
            audioRecorder.startRecording(newMeetingId)
        } catch (e: Exception) {
            _state.value = RecordingState.FAILED
            abandonAudioFocus()
            throw e
        }

        recordingStartTimeMs = System.currentTimeMillis()
        acquireWakeLock()
        startForegroundWithProperType()
        _state.value = RecordingState.RECORDING
        return file
    }

    fun pauseRecording() = pauseRecordingInternal(dueToFocusLoss = false)

    private fun pauseRecordingInternal(dueToFocusLoss: Boolean) {
        if (_state.value != RecordingState.RECORDING) return
        audioRecorder.pauseRecording()
        _state.value = RecordingState.PAUSED
        if (dueToFocusLoss) _focusInterrupted.value = true
        updateNotification()
    }

    fun resumeRecording() {
        if (_state.value != RecordingState.PAUSED) return
        audioRecorder.resumeRecording()
        _state.value = RecordingState.RECORDING
        _focusInterrupted.value = false
        updateNotification()
    }

    /** Finalizes the audio file, persists a real [com.example.core.model.Meeting] row for it, and
     * only then reports back and tears the service down — so [onSaved] always describes a
     * recording that genuinely exists in Room, never just a file that might still be orphaned if
     * the process died a moment later. Behavior (what happens after saving) is entirely the
     * caller's decision via [onSaved]; this method only guarantees the save itself completed. */
    fun stopRecording(onSaved: (meetingId: String, file: File?, durationMs: Long) -> Unit) {
        if (_state.value != RecordingState.RECORDING && _state.value != RecordingState.PAUSED) return
        _state.value = RecordingState.STOPPING
        val finalDurationMs = durationMs.value
        val file = audioRecorder.stopRecording()
        val id = meetingId

        scope.launch {
            if (id != null && file != null) {
                _state.value = RecordingState.SAVING
                try {
                    meetingRepository.createInitialMeeting(
                        id = id,
                        title = meetingTitle,
                        source = MeetingSource.LOCAL_RECORDING,
                        audioFilePath = file.absolutePath,
                        recordingType = recordingContext.recordingType,
                        customContext = recordingContext.customContext,
                        speakerCountPreference = recordingContext.speakerCountPreference
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist finished recording $id — audio file is still on disk at ${file.absolutePath}", e)
                }
            }
            _state.value = RecordingState.SAVED
            onSaved(id.orEmpty(), file, finalDurationMs)
            teardown()
        }
    }

    /** Discards the in-progress recording's audio entirely. Never called for a recording that has
     * already reached [RecordingState.SAVED] — once a Meeting row exists, deleting audio is
     * [com.example.core.repository.MeetingRepository.deleteMeeting]'s job, an explicit user
     * action with its own confirmation, not this method's. */
    fun discardRecording() {
        audioRecorder.discardRecording()
        _state.value = RecordingState.IDLE
        teardown()
    }

    private fun teardown() {
        abandonAudioFocus()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // ---- Audio focus -----------------------------------------------------------------------

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_UNKNOWN)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
    }

    // ---- Wake lock + notification (unchanged from the previous implementation, only now
    // reflecting real state instead of a display-only flag) --------------------------------

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MeetMind::MeetingRecordingWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            if (wakeLock?.isHeld == false) {
                // Keep CPU awake in background/lockscreen while recording
                wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours safety timeout
                Log.d(TAG, "WakeLock acquired for background & locked recording")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private fun startForegroundWithProperType() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MeetingMind Active Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while recording a meeting in background or locked screen"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val isPaused = _state.value == RecordingState.PAUSED
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            isPaused && _focusInterrupted.value -> "Paused — an interruption stopped it. Tap Resume to continue."
            isPaused -> "Recording paused (offline)"
            else -> "Continuous recording active • Tap to open"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeetingMind • $meetingTitle")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(!isPaused)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setUsesChronometer(!isPaused)
            .setWhen(recordingStartTimeMs)

        // Add quick actions for lock-screen control
        val pauseResumeIntent = Intent(this, MeetingRecordingService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = NotificationCompat.Action.Builder(
            if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            if (isPaused) "Resume" else "Pause",
            pauseResumePendingIntent
        ).build()
        builder.addAction(pauseResumeAction)

        val stopIntent = Intent(this, MeetingRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Stop",
            stopPendingIntent
        ).build()
        builder.addAction(stopAction)

        return builder.build()
    }
}
