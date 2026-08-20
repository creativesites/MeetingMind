package com.example.core.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class MeetingRecordingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTitle: String = "Active Meeting"
    private var isPaused: Boolean = false
    private var recordingStartTimeMs: Long = System.currentTimeMillis()

    companion object {
        const val CHANNEL_ID = "meetmind_recording_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.meetmind.ACTION_START"
        const val ACTION_PAUSE = "com.example.meetmind.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.meetmind.ACTION_RESUME"
        const val ACTION_STOP = "com.example.meetmind.ACTION_STOP"
        const val EXTRA_MEETING_TITLE = "EXTRA_MEETING_TITLE"

        fun startService(context: Context, meetingTitle: String = "Active Meeting") {
            val intent = Intent(context, MeetingRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MEETING_TITLE, meetingTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, MeetingRecordingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, MeetingRecordingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MeetingRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(true)
                stopSelf()
            }
            ACTION_PAUSE -> {
                isPaused = true
                updateNotification()
            }
            ACTION_RESUME -> {
                isPaused = false
                acquireWakeLock()
                updateNotification()
            }
            else -> {
                currentTitle = intent?.getStringExtra(EXTRA_MEETING_TITLE) ?: "Recording Meeting"
                isPaused = false
                recordingStartTimeMs = System.currentTimeMillis()
                acquireWakeLock()
                startForegroundWithProperType()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

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
                Log.d("MeetingRecordingService", "WakeLock acquired for background & locked recording")
            }
        } catch (e: Exception) {
            Log.e("MeetingRecordingService", "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("MeetingRecordingService", "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e("MeetingRecordingService", "Failed to release wake lock", e)
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
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isPaused) "Recording paused (offline)" else "Continuous recording active • Tap to open"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeetingMind • $currentTitle")
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
