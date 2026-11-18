package com.craftflowtechnologies.meetingmind.core.audio

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
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.craftflowtechnologies.meetingmind.core.common.DeviceCapabilityDetector
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.MeetingSource
import com.craftflowtechnologies.meetingmind.core.model.RecordingContext
import com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns live capture end to end (Phase 15 §Part 2 / design capture-pipeline spec §3.2): the
 * [AudioRecorder] instance, the output file, [RecordingState], amplitude/duration telemetry, and
 * audio focus. `RecordingViewModel` binds to this service and only ever sends intents / reads its
 * [StateFlow]s — it must never construct or hold an [AudioRecorder] itself, so there is exactly
 * one place that can be recording at a time and exactly one authoritative answer to "what state is
 * capture in right now," matching [com.craftflowtechnologies.meetingmind.core.audio.PlaybackController]'s existing pattern
 * for playback.
 *
 * Previously this service only managed the wake lock and a foreground notification whose Pause/
 * Resume buttons toggled a display-only flag — pressing them from the lock screen had no effect
 * on the actual [AudioRecorder], which lived in the ViewModel and this service had no reference
 * to. That bug is what this rewrite fixes: the notification's actions now call the same
 * [pauseRecording]/[resumeRecording]/[stopRecording] the bound UI calls, so they're real.
 *
 * [stopRecording] also persists the finished [com.craftflowtechnologies.meetingmind.core.model.Meeting] row directly from
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

    // The handler is the last line of defence: an unexpected throw in a service coroutine (a full
    // disk, a storage check) is logged instead of killing the app mid-recording. The audio keeps
    // being written by the recorder either way.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
            com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "service coroutine failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    )
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

    /** Single quiet in-flight storage/battery warning line (design spec §3.8), or null when
     * nothing is wrong. Re-evaluated on the same cadence as the crash-recovery heartbeat. */
    private val _capacityWarning = MutableStateFlow<String?>(null)
    val capacityWarning: StateFlow<String?> = _capacityWarning.asStateFlow()

    private var meetingId: String? = null
    private var meetingTitle: String = "Recording"
    private var recordingContext: RecordingContext = RecordingContext()
    private var outputFile: File? = null

    private lateinit var journalStore: RecordingJournalStore
    private var maintenanceJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
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
        const val ACTION_PAUSE = "com.craftflowtechnologies.meetingmind.meetmind.ACTION_PAUSE"
        const val ACTION_RESUME = "com.craftflowtechnologies.meetingmind.meetmind.ACTION_RESUME"
        const val ACTION_STOP = "com.craftflowtechnologies.meetingmind.meetmind.ACTION_STOP"
        /** Sent by the service to itself when capture begins, so it is a started service and keeps
         * recording after the screen that bound it goes away (Back, a swipe from Recents). */
        const val ACTION_KEEP_RECORDING = "com.craftflowtechnologies.meetingmind.meetmind.ACTION_KEEP_RECORDING"
        const val NOTIFICATION_ID_MARKS = 1002

        /** Binds a caller to the running (or freshly created) service. Binding alone starts
         * nothing: [startRecording] begins capture, starts the service so it outlives the bound
         * screen, and promotes it to foreground; binding just gets a caller a reference. */
        fun bindIntent(context: Context): Intent = Intent(context, MeetingRecordingService::class.java)

        private val _capturing = MutableStateFlow(false)
        /**
         * True while a recording is being captured (preparing, recording, paused or stopping), for code
         * that isn't bound to the service: a note that finishes meanwhile waits, and its moment and
         * snackbar come after the stop (signature moments §3.3 row 4).
         */
        val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

        /** The microphone is held: capture is starting, running or paused. */
        internal fun isLive(state: RecordingState): Boolean =
            state == RecordingState.PREPARING || state == RecordingState.RECORDING || state == RecordingState.PAUSED

        /** Capture has ended and the recording is being saved; teardown follows on its own. */
        internal fun isFinishing(state: RecordingState): Boolean =
            state == RecordingState.STOPPING || state == RecordingState.SAVING

        /** A start request (a notification action, a restart) that finds no recording to act on
         * leaves the service with nothing to do, so it stops itself instead of lingering. */
        internal fun hasWork(state: RecordingState): Boolean = isLive(state) || isFinishing(state)

        /** Work that must outlive the service: after a stop from the notification (the service
         * stops itself right after saving, which must not cancel the marks or the processing
         * queue), and recovering a recording moved aside by a new one. */
        private val afterStopScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "after-stop work failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        )
    }

    /** Saves the marks, then queues processing, exactly as the in-app stop does through the
     * processing screen. Settings are read at queue time; a failure leaves the recording saved. */
    private fun afterNotificationStop(meetingId: String, marks: List<com.craftflowtechnologies.meetingmind.core.work.Mark>) {
        val app = applicationContext
        afterStopScope.launch {
            if (marks.isNotEmpty()) {
                runCatching { com.craftflowtechnologies.meetingmind.core.work.Marks.save(MeetMindDatabase.getInstance(app), meetingId, marks) }
            }
            val queued = runCatching { com.craftflowtechnologies.meetingmind.ai.pipeline.ProcessingScheduler.enqueue(app, meetingId) }.getOrNull()
            if (queued == null) {
                com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "notification stop: processing not queued for $meetingId")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(applicationContext)
        meetingRepository = MeetingRepository(applicationContext, MeetMindDatabase.getInstance(applicationContext))
        journalStore = RecordingJournalStore(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()
        scope.launch {
            _state.collect { _capturing.value = isLive(it) || it == RecordingState.STOPPING }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startRecording starts the service (ACTION_KEEP_RECORDING), so the notification's
        // actions arrive at a running service and route to the real pause/resume/stop methods.
        when (intent?.action) {
            ACTION_KEEP_RECORDING, null -> Unit
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording { id, file, _, persisted ->
                // Stopped from the notification: the marks tapped so far still belong on the
                // recording's note, and its notes are made now, as after a stop in the app.
                val marks = com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.take()
                if (persisted && file != null) afterNotificationStop(id, marks)
            }
            else -> com.craftflowtechnologies.meetingmind.core.work.MarkActions.handle(intent?.action, durationMs.value)
        }
        // A stale action (the recording already ended) or a restart finds nothing to do.
        if (!hasWork(_state.value)) stopSelf(startId)
        // Not sticky: a restart after the process died can't pick the microphone back up. The
        // journal brings the audio back on the next launch instead.
        return START_NOT_STICKY
    }

    /** Swiped away from Recents: the recording carries on under its notification. The audio so
     * far is flushed and journaled first, in case the phone's maker kills the process anyway. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isLive(_state.value)) {
            com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "app swiped away, recording continues")
            runCatching { audioRecorder.checkpointSync() }
            writeJournal()
        } else if (!isFinishing(_state.value)) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The system ended the service while capturing: never leave the microphone on with no
        // notification. Close the recorder and keep the journal, so the next launch recovers it.
        if (isLive(_state.value)) finishForRecovery()
        _capturing.value = false
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun finishForRecovery() {
        com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "service destroyed while ${_state.value}, audio kept for recovery")
        // No trimming or converting here: that is slow, and the recovery does it on next launch.
        runCatching { audioRecorder.releaseForRecovery() }
        _state.value = RecordingState.STOPPING
        writeJournal()
        marksJob?.cancel(); marksJob = null
        maintenanceJob?.cancel(); maintenanceJob = null
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID_MARKS) }
        abandonAudioFocus()
        runCatching { androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    // ---- Capture control -----------------------------------------------------------------

    /** Starts a fresh recording. Throws (leaving [state] at [RecordingState.FAILED]) if the
     * recorder itself cannot start — e.g. the microphone is unavailable — rather than silently
     * doing nothing, so a caller can surface a real error instead of a dead Record button. */
    fun startRecording(newMeetingId: String, title: String, context: RecordingContext): File {
        // Already capturing (e.g. the permission grant and the screen both asked to start): keep
        // the recording that is running. Starting again would replace a live MediaRecorder.
        val current = outputFile
        if (current != null && (_state.value == RecordingState.RECORDING || _state.value == RecordingState.PAUSED || _state.value == RecordingState.PREPARING)) {
            Log.w(TAG, "startRecording ignored: already ${_state.value}")
            return current
        }
        // An interrupted recording not yet recovered is moved aside, never overwritten by this
        // one's journal; it is recovered in the background once capture is running.
        val parked = journalStore.parkIfOther(newMeetingId)
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

        outputFile = file
        acquireWakeLock()
        keepRunning()
        // Android can refuse to promote the service (started from the background, or a revoked
        // permission). Recording carries on while the app is open instead of crashing the app.
        try {
            startForegroundWithProperType()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not start the recording notification: ${e.message}", e)
        }
        _state.value = RecordingState.RECORDING
        writeJournal()
        startMaintenanceLoop()
        startMarks()
        if (parked) {
            val app = applicationContext
            afterStopScope.launch { RecordingAutoRecovery.recoverPending(app) }
        }
        return file
    }

    /**
     * Makes this a started service, so it keeps recording when the bound screen goes away (Back,
     * a swipe from Recents, the activity destroyed). This runs while the person is on the
     * recorder, so the app is in the foreground and the start is allowed; startForeground follows
     * at once. A plain start rather than startForegroundService: if Android refuses the
     * microphone foreground type, a startForegroundService with no startForeground would crash
     * the app a few seconds later, while a plain start just records on, and [onDestroy] still
     * closes the recorder cleanly if the system stops it.
     */
    private fun keepRunning() {
        try {
            startService(Intent(this, MeetingRecordingService::class.java).setAction(ACTION_KEEP_RECORDING))
        } catch (e: RuntimeException) {
            // IllegalStateException (background start) or SecurityException: stays bound-only.
            Log.w(TAG, "Could not start the recording service: ${e.message}", e)
        }
    }

    /** The current recording's title, for a recorder screen reopened mid-recording. */
    val liveTitle: String get() = meetingTitle

    fun pauseRecording() = pauseRecordingInternal(dueToFocusLoss = false)

    private fun pauseRecordingInternal(dueToFocusLoss: Boolean) {
        if (_state.value != RecordingState.RECORDING) return
        audioRecorder.pauseRecording()
        _state.value = RecordingState.PAUSED
        if (dueToFocusLoss) _focusInterrupted.value = true
        writeJournal()
        updateNotification()
    }

    fun resumeRecording() {
        if (_state.value != RecordingState.PAUSED) return
        audioRecorder.resumeRecording()
        _state.value = RecordingState.RECORDING
        _focusInterrupted.value = false
        writeJournal()
        updateNotification()
    }

    /** Finalizes the audio file, persists a real [com.craftflowtechnologies.meetingmind.core.model.Meeting] row for it, and
     * only then reports back and tears the service down — so [onSaved] always describes a
     * recording that genuinely exists in Room, never just a file that might still be orphaned if
     * the process died a moment later. Behavior (what happens after saving) is entirely the
     * caller's decision via [onSaved]; this method only guarantees the save itself completed.
     *
     * [onSaved]'s last argument is the guarantee: `persisted == true` means the row exists and the
     * caller may open the recording. `persisted == false` means the audio is complete but the row
     * is not, and the caller must tell the person the truth and leave the recording recoverable —
     * never navigate to a recording that has no row. */
    fun stopRecording(onSaved: (meetingId: String, file: File?, durationMs: Long, persisted: Boolean) -> Unit) {
        if (_state.value != RecordingState.RECORDING && _state.value != RecordingState.PAUSED) return
        _state.value = RecordingState.STOPPING
        val finalDurationMs = durationMs.value
        val file = audioRecorder.stopRecording()
        if (file != null) outputFile = file
        val id = meetingId

        scope.launch {
            if (id != null && file != null) {
                _state.value = RecordingState.SAVING
                writeJournal()
                val persisted = RecordingStopFinalizer.finalize(
                    persist = {
                        meetingRepository.createInitialMeeting(
                            id = id,
                            title = meetingTitle,
                            source = MeetingSource.LOCAL_RECORDING,
                            audioFilePath = file.absolutePath,
                            recordingType = recordingContext.recordingType,
                            customContext = recordingContext.customContext,
                            speakerCountPreference = recordingContext.speakerCountPreference,
                            language = recordingContext.language ?: "en",
                            noteId = recordingContext.noteId
                        )
                    },
                    // Cleared only after the row is durable; a failure keeps the journal, and the
                    // next launch offers the recording again instead of losing it.
                    clearJournal = { journalStore.clear() }
                )
                if (!persisted) {
                    _state.value = RecordingState.SAVE_FAILED
                    // Written while meetingId and outputFile are still set, so it survives teardown.
                    writeJournal()
                    onSaved(id, file, finalDurationMs, false)
                    teardown()
                    return@launch
                }
                _state.value = RecordingState.SAVED
                onSaved(id, file, finalDurationMs, true)
                teardown()
                return@launch
            }
            _state.value = RecordingState.SAVED
            journalStore.clear()
            onSaved(id.orEmpty(), file, finalDurationMs, false)
            teardown()
        }
    }

    /** Discards the in-progress recording's audio entirely. Never called for a recording that has
     * already reached [RecordingState.SAVED] — once a Meeting row exists, deleting audio is
     * [com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository.deleteMeeting]'s job, an explicit user
     * action with its own confirmation, not this method's. */
    fun discardRecording() {
        audioRecorder.discardRecording()
        com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.clear()
        _state.value = RecordingState.IDLE
        journalStore.clear()
        teardown()
    }

    private var marksJob: kotlinx.coroutines.Job? = null

    /**
     * For a work recording with other people in it, a second ongoing notification carries Key,
     * Action and Question (the recording's own notification already holds Pause and Stop, and
     * Android shows three actions at most). It is visible on the lock screen, so a moment can be
     * marked without unlocking. It says nothing about the meeting.
     */
    private fun startMarks() {
        marksJob?.cancel()
        com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.clear()
        val isWork = com.craftflowtechnologies.meetingmind.core.model.Workflows.space(recordingContext.recordingType) == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK
        if (!isWork || recordingContext.speakerCountPreference == 1) return
        marksJob = scope.launch {
            com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.marks.collect { postMarksNotification(it.size) }
        }
    }

    private fun postMarksNotification(count: Int) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Mark this moment")
            .setContentText(if (count == 0) "Key, action or question" else if (count == 1) "1 marked" else "$count marked")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        listOf(
            com.craftflowtechnologies.meetingmind.core.work.MarkKind.KEY to "⭐ Key",
            com.craftflowtechnologies.meetingmind.core.work.MarkKind.ACTION to "✓ Action",
            com.craftflowtechnologies.meetingmind.core.work.MarkKind.QUESTION to "? Question"
        ).forEachIndexed { i, (kind, label) ->
            val intent = Intent(this, MeetingRecordingService::class.java).setAction(com.craftflowtechnologies.meetingmind.core.work.MarkActions.actionOf(kind))
            builder.addAction(0, label, PendingIntent.getService(this, 11 + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID_MARKS, builder.build())
    }

    private fun teardown() {
        marksJob?.cancel(); marksJob = null
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID_MARKS)
        maintenanceJob?.cancel()
        maintenanceJob = null
        _capacityWarning.value = null
        outputFile = null
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

    // ---- Crash recovery journal (§3.7) + storage/battery warnings (§3.8) -------------------

    /** Writes the current in-flight state to `recovery.json`. Called on every state transition
     * and, while recording, at least every 10 seconds by [startMaintenanceLoop] — per spec, a
     * heartbeat gap longer than that on top of a dead journal is what tells the next app launch
     * the process actually died rather than just not having written in a while. */
    private fun writeJournal() {
        val id = meetingId ?: return
        val file = outputFile ?: return
        journalStore.write(
            RecordingJournalEntry(
                meetingId = id,
                title = meetingTitle,
                recordingType = recordingContext.recordingType.name,
                audioFilePath = file.absolutePath,
                lastKnownDurationMs = durationMs.value,
                // Markers/notes don't exist as capture data yet (design spec §3.4/§3.5 — Stage 3),
                // so this journal always reports zero of each rather than a made-up count.
                markerCount = 0,
                noteCount = 0,
                lastHeartbeatAtMs = System.currentTimeMillis(),
                state = _state.value.name,
                language = recordingContext.language,
                speakerCountPreference = recordingContext.speakerCountPreference,
                noteId = recordingContext.noteId,
                customContext = recordingContext.customContext
            )
        )
    }

    /** One loop, started when capture begins and cancelled in [teardown], doing both the journal
     * heartbeat and the periodic storage/battery check — they're on the same "while actually
     * recording or paused" condition and neither needs its own cadence, so one coroutine covers
     * both instead of running two nearly-identical loops. */
    private fun startMaintenanceLoop() {
        maintenanceJob?.cancel()
        maintenanceJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (_state.value != RecordingState.RECORDING && _state.value != RecordingState.PAUSED) break
                audioRecorder.checkpointSync()
                writeJournal()
                val freeMb = DeviceCapabilityDetector.getAvailableStorageMb()
                _capacityWarning.value = RecordingCapacity.inFlightWarning(freeMb, currentBatteryPercent())
                updateNotification()
            }
        }
    }

    /** Current battery percentage via the sticky [Intent.ACTION_BATTERY_CHANGED] broadcast — no
     * persistent [android.content.BroadcastReceiver] needed for a value only read once every 10
     * seconds. Null if the device won't report level/scale (rare), so [RecordingCapacity] can
     * skip the battery half of its check rather than warn on a value that isn't real. */
    private fun currentBatteryPercent(): Int? {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
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

    /** The recording's ongoing notification: "Recording" with the elapsed time, Pause or Resume,
     * Stop, and a tap that opens the recorder. */
    @androidx.annotation.VisibleForTesting
    internal fun buildNotification(): Notification {
        val isPaused = _state.value == RecordingState.PAUSED
        // Opens the recorder screen, which picks the running recording back up.
        val pendingIntent = com.craftflowtechnologies.meetingmind.core.notify.DeepLinks.pendingIntent(
            this, com.craftflowtechnologies.meetingmind.core.notify.DeepLink.LiveRecording
        )

        val statusText = when {
            isPaused && _focusInterrupted.value -> "Paused — an interruption stopped it. Tap Resume to continue."
            isPaused -> "$meetingTitle • Paused"
            _capacityWarning.value != null -> _capacityWarning.value!!
            else -> "$meetingTitle • Tap to open"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isPaused) "Recording paused" else "Recording")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Elapsed recorded time (pauses excluded): the chronometer counts up from this base.
            .setShowWhen(!isPaused)
            .setUsesChronometer(!isPaused)
            .setWhen(System.currentTimeMillis() - durationMs.value)

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
