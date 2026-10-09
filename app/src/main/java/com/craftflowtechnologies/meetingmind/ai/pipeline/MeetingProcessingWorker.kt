package com.craftflowtechnologies.meetingmind.ai.pipeline

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
            runCatching { com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.valueOf(it) }.getOrNull()
        } ?: com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.CONSERVATIVE
        val diarizationStrategy = inputData.getString(KEY_DIARIZATION_STRATEGY)?.let {
            runCatching { com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.valueOf(it) }.getOrNull()
        } ?: com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AUTO
        // Defaults to the private profile: a missing or unreadable value must never result in a
        // recording being uploaded. See ProcessingProfile.fromNameOrDefault.
        // Confidential work never leaves the phone, whatever Internet mode says (PLAN_PROFESSIONAL.md §6.4).
        val processingProfile = com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy.forMeeting(
            applicationContext, meetingId,
            com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.fromNameOrDefault(inputData.getString(KEY_PROCESSING_PROFILE))
        )

        AppNotifications.ensureChannels(applicationContext)
        this.meetingId = meetingId
        this.title = recordingTitle
        setForeground(createForegroundInfo("Preparing audio…", 0))

        // A foreground service keeps the process alive, but with the screen off the CPU still
        // sleeps. Offline transcription is minutes of pure CPU work, so it holds a partial wake
        // lock for as long as it runs — capped, so a hung run can never drain the battery.
        val wakeLock = (applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeetingMind:processing")
            .apply { setReferenceCounted(false); acquire(WAKE_LOCK_TIMEOUT_MS) }

        val database = MeetMindDatabase.getInstance(applicationContext)
        val pipeline = MeetingProcessingPipeline(applicationContext, database)
        var lastForegroundUpdate = 0L

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
                processingProfile = processingProfile,
                onProgress = { step, percent, stage ->
                    // onProgress is a plain (non-suspend) callback invoked from the pipeline's
                    // coroutine; runBlocking is safe here because doWork is already off the main
                    // thread and each call is a short progress/notification update.
                    kotlinx.coroutines.runBlocking {
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS_STEP to step,
                                KEY_PROGRESS_PERCENT to percent,
                                KEY_PROGRESS_STAGE to stage.name,
                                KEY_MEETING_ID to meetingId,
                                KEY_RECORDING_TITLE to recordingTitle
                            )
                        )
                        // The notification is refreshed at most once a second; Android drops
                        // faster updates anyway and they cost battery.
                        val now = System.currentTimeMillis()
                        if (now - lastForegroundUpdate > 1_000) {
                            lastForegroundUpdate = now
                            setForeground(createForegroundInfo(step, percent))
                        }
                    }
                }
            )
            val outcome = when (resultMeeting.status) {
                MeetingStatus.READY.name -> AppNotifications.Outcome.READY
                MeetingStatus.MODEL_REQUIRED.name -> AppNotifications.Outcome.NEEDS_MODEL
                else -> AppNotifications.Outcome.FAILED
            }
            AppNotifications.processingFinished(applicationContext, meetingId, recordingTitle, outcome)
            Result.success(
                workDataOf(
                    KEY_RESULT_MEETING_ID to resultMeeting.id,
                    KEY_RESULT_STATUS to resultMeeting.status
                )
            )
        } catch (e: java.util.concurrent.CancellationException) {
            // Stopped on purpose (or by the system): the pipeline has already cleaned up.
            if (isStopped && stopReasonIsSystem()) {
                // Never leave a frozen percentage: say it was interrupted. WorkManager re-runs the
                // job (resuming from the ASR checkpoint), and the re-run overwrites this row.
                markJobInterrupted(database, meetingId, overrideFailed = true)
                throw e
            }
            Result.failure(workDataOf(KEY_ERROR to "Cancelled"))
        } catch (t: Throwable) {
            // Includes OutOfMemoryError and other Errors the pipeline's own handler may not have seen.
            markJobInterrupted(database, meetingId)
            AppNotifications.processingFinished(applicationContext, meetingId, recordingTitle, AppNotifications.Outcome.FAILED, t.message)
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "Unknown processing error")))
        } finally {
            runCatching { if (wakeLock.isHeld) wakeLock.release() }
        }
    }

    /** Fails this recording's job row (if it is still unfinished) so the UI stops showing progress. */
    private suspend fun markJobInterrupted(database: MeetMindDatabase, meetingId: String, overrideFailed: Boolean = false) {
        withContext(NonCancellable) {
            runCatching {
                val dao = database.processingJobDao()
                val job = dao.getJobForMeeting(meetingId).first()
                // A real failure the pipeline already recorded keeps its own message; only the
                // system-stop path replaces the pipeline's generic "cancelled" row.
                if (job != null && !job.isCompleted && (overrideFailed || !job.isFailed)) {
                    dao.insertOrUpdateJob(job.asInterrupted())
                }
            }
        }
    }

    private var meetingId: String = ""
    private var title: String = "recording"

    /** True when Android, not the user, stopped the work — WorkManager will run it again. */
    private fun stopReasonIsSystem(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && stopReason != android.app.job.JobParameters.STOP_REASON_CANCELLED_BY_APP &&
            stopReason != android.app.job.JobParameters.STOP_REASON_USER

    override suspend fun getForegroundInfo(): ForegroundInfo {
        this.title = inputData.getString(KEY_RECORDING_TITLE) ?: "recording"
        this.meetingId = inputData.getString(KEY_MEETING_ID).orEmpty()
        return createForegroundInfo("Preparing audio…", 0)
    }

    private suspend fun createForegroundInfo(step: String, percent: Int): ForegroundInfo {
        AppNotifications.ensureChannels(applicationContext)
        val waiting = runCatching {
            WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).first()
                .count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        }.getOrDefault(0)
        val notification = AppNotifications.processingProgress(
            context = applicationContext,
            meetingId = meetingId,
            title = title,
            step = step,
            percent = percent,
            queuedBehind = waiting,
            cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NOTIFICATION_ID = AppNotifications.ID_PROCESSING
        /** Six hours: far longer than any real run, short enough that a hang can't drain the battery. */
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
        /** On every processing request, so "is anything processing?" is one query. */
        const val ALL_PROCESSING_TAG = "meetmind_processing"

        /** Only one AI-heavy job runs at a time — WorkManager queues subsequent requests under this name. */
        const val UNIQUE_WORK_NAME = "meetmind_ai_processing_queue"

        /** Every enqueued request for [meetingId] carries this as a WorkManager tag, so a screen
         * that only knows the meetingId (e.g. re-entered after the app was backgrounded/killed
         * and recreated) can ask "is there already a real job running for THIS recording?" via
         * [androidx.work.WorkManager.getWorkInfosByTagFlow] instead of blindly enqueuing another
         * one onto the shared [UNIQUE_WORK_NAME] chain. */
        fun meetingWorkTag(meetingId: String): String = "meetmind_processing_$meetingId"

        const val KEY_PROCESSING_PROFILE = "processingProfile"
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
