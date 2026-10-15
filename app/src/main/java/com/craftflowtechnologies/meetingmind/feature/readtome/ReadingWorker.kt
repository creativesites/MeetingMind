package com.craftflowtechnologies.meetingmind.feature.readtome

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingRecovery
import com.craftflowtechnologies.meetingmind.ai.voice.read.Recovery
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingUiState
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * Makes a note's Read to Me reading in the background, saving each part as it is made, so the person can listen
 * later without waiting. It never plays anything and never switches voice by itself: a failure is saved in the
 * reading and offered when the person next opens the sheet. One automatic retry, only for failures marked retryable.
 */
class ReadingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0, 0)

    override suspend fun doWork(): Result {
        val noteId = inputData.getString(KEY_NOTE_ID) ?: return Result.failure()
        val coordinator = ReadToMe.coordinator(applicationContext)
        setForeground(foreground(0, 0))
        val end = coroutineScope {
            val progress = launch {
                coordinator.state.collect { state ->
                    if (state is ReadingUiState.Working && state.noteId == noteId) setForeground(foreground(state.done, state.total))
                }
            }
            try {
                coordinator.generateInBackground(noteId)
            } finally {
                progress.cancel()
            }
        }
        return when (end) {
            is ReadingUiState.Finished -> {
                notifyReady(noteId)
                Result.success()
            }
            is ReadingUiState.Failed ->
                if (runAttemptCount < 1 && Recovery.RETRY in end.options) Result.retry() else Result.failure()
            else -> Result.failure()
        }
    }

    private fun foreground(done: Int, total: Int): ForegroundInfo {
        AppNotifications.ensureChannels(applicationContext)
        val text = if (total == 0) "Getting ready…" else "Part ${minOf(done + 1, total)} of $total"
        val notification: Notification = NotificationCompat.Builder(applicationContext, AppNotifications.CHANNEL_WORK)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Preparing Read to Me")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), done.coerceAtMost(total), total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** Tells the person the reading is ready. Quiet if notifications are off; the sheet shows it either way. */
    private fun notifyReady(noteId: String) {
        AppNotifications.ensureChannels(applicationContext)
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val notification = NotificationCompat.Builder(applicationContext, AppNotifications.CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Read to Me is ready")
            .setContentText("Your reading is saved. Open it to listen.")
            .setAutoCancel(true)
            .apply { launch?.let { setContentIntent(android.app.PendingIntent.getActivity(applicationContext, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)) } }
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_READY_ID + noteId.hashCode() % 1000, notification) }
    }

    companion object {
        const val KEY_NOTE_ID = "noteId"
        const val NOTIFICATION_ID = 2010
        private const val NOTIFICATION_READY_ID = 2100

        fun uniqueName(noteId: String) = "read-to-me:$noteId"

        /** Starts (or keeps running) the background reading for [noteId]. Safe to call again. */
        fun enqueue(context: Context, noteId: String) {
            val request = OneTimeWorkRequestBuilder<ReadingWorker>()
                .setInputData(workDataOf(KEY_NOTE_ID to noteId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            runCatching { WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(noteId), ExistingWorkPolicy.KEEP, request) }
        }
    }
}
