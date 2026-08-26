package com.example.ai.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.ai.modelmanagement.ModelCatalog
import com.example.core.database.MeetMindDatabase
import com.example.core.model.ProcessingStage
import java.io.File

/**
 * Runs [MeetingProcessingPipeline] as background work that survives the app being minimized,
 * backgrounded, or the screen locking — a real requirement once local VAD/ASR/diarization/LLM
 * inference can take minutes, not something a `viewModelScope` coroutine (tied to a Compose
 * screen's lifecycle) can honor. Reports real stage-based progress via [setForeground] and
 * [setProgress] — never an invented percentage.
 */
class MeetingProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Missing meetingId")
        )
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Missing audioPath")
        )
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0L)
        val modelId = inputData.getString(KEY_MODEL_ID) ?: ModelCatalog.parakeetTdtV3Int8.id
        val llmModelId = inputData.getString(KEY_LLM_MODEL_ID)
        val expectedSpeakerCount = inputData.getInt(KEY_EXPECTED_SPEAKER_COUNT, -1).takeIf { it > 0 }
        val recordingTitle = inputData.getString(KEY_RECORDING_TITLE) ?: "recording"
        val cleanupMode = inputData.getString(KEY_CLEANUP_MODE)?.let {
            runCatching { com.example.core.model.TranscriptCleanupMode.valueOf(it) }.getOrNull()
        } ?: com.example.core.model.TranscriptCleanupMode.CONSERVATIVE
        val diarizationStrategy = inputData.getString(KEY_DIARIZATION_STRATEGY)?.let {
            runCatching { com.example.core.model.DiarizationStrategy.valueOf(it) }.getOrNull()
        } ?: com.example.core.model.DiarizationStrategy.AUTO

        setForeground(createForegroundInfo("Preparing audio...", 5, recordingTitle))

        val database = MeetMindDatabase.getInstance(applicationContext)
        val pipeline = MeetingProcessingPipeline(applicationContext, database)

        return try {
            val resultMeeting = pipeline.processMeeting(
                meetingId = meetingId,
                audioFile = File(audioPath),
                totalDurationMs = durationMs,
                modelId = modelId,
                expectedSpeakerCount = expectedSpeakerCount,
                llmModelId = llmModelId,
                cleanupMode = cleanupMode,
                diarizationStrategy = diarizationStrategy,
                onProgress = { step, percent, stage ->
                    // onProgress is a plain (non-suspend) callback invoked from the pipeline's
                    // coroutine; runBlocking here is safe because we're already off the main
                    // thread (CoroutineWorker.doWork runs on its own dispatcher) and each call
                    // is a short, sequential Data/notification update, never a long operation.
                    kotlinx.coroutines.runBlocking {
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS_STEP to step,
                                KEY_PROGRESS_PERCENT to percent,
                                KEY_PROGRESS_STAGE to stage.name
                            )
                        )
                        setForeground(createForegroundInfo(step, percent, recordingTitle))
                    }
                }
            )
            Result.success(
                workDataOf(
                    KEY_RESULT_MEETING_ID to resultMeeting.id,
                    KEY_RESULT_STATUS to resultMeeting.status
                )
            )
        } catch (e: java.util.concurrent.CancellationException) {
            // Real cancellation, already cleaned up honestly inside the pipeline itself
            // (NonCancellable Room writes) — nothing further to do here.
            Result.failure(workDataOf(KEY_ERROR to "Cancelled"))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown processing error")))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo("Preparing audio...", 0, inputData.getString(KEY_RECORDING_TITLE) ?: "recording")

    private fun createForegroundInfo(step: String, percent: Int, recordingTitle: String): ForegroundInfo {
        createChannelIfNeeded()
        val notification = buildNotification(step, percent, recordingTitle)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(step: String, percent: Int, recordingTitle: String): Notification {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("MeetingMind")
            // Privacy: never surface transcript/summary content in a notification —
            // only the generic recording title and current processing stage.
            .setContentText("Processing \"$recordingTitle\" — $step")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (percent in 1..99) {
            builder.setProgress(100, percent, false)
        } else if (percent <= 0) {
            builder.setProgress(0, 0, true) // indeterminate: stage-based, no fake percentage
        }
        return builder.build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MeetingMind Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while MeetingMind analyzes a recording in the background"
                setShowBadge(false)
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "meetmind_processing_channel"
        const val NOTIFICATION_ID = 2001

        /** Only one AI-heavy job runs at a time — WorkManager queues subsequent requests under this name. */
        const val UNIQUE_WORK_NAME = "meetmind_ai_processing_queue"

        /** Every enqueued request for [meetingId] carries this as a WorkManager tag, so a screen
         * that only knows the meetingId (e.g. re-entered after the app was backgrounded/killed
         * and recreated) can ask "is there already a real job running for THIS recording?" via
         * [androidx.work.WorkManager.getWorkInfosByTagFlow] instead of blindly enqueuing another
         * one onto the shared [UNIQUE_WORK_NAME] chain. */
        fun meetingWorkTag(meetingId: String): String = "meetmind_processing_$meetingId"

        const val KEY_MEETING_ID = "meetingId"
        const val KEY_AUDIO_PATH = "audioPath"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_MODEL_ID = "modelId"
        const val KEY_LLM_MODEL_ID = "llmModelId"
        const val KEY_EXPECTED_SPEAKER_COUNT = "expectedSpeakerCount"
        const val KEY_RECORDING_TITLE = "recordingTitle"
        const val KEY_CLEANUP_MODE = "cleanupMode"
        const val KEY_DIARIZATION_STRATEGY = "diarizationStrategy"

        const val KEY_PROGRESS_STEP = "step"
        const val KEY_PROGRESS_PERCENT = "percent"
        const val KEY_PROGRESS_STAGE = "stage"

        const val KEY_RESULT_MEETING_ID = "resultMeetingId"
        const val KEY_RESULT_STATUS = "resultStatus"
        const val KEY_ERROR = "error"
    }
}
