package com.example.ai.pipeline

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.MeetingStatus
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The one place that starts, finds and resumes a recording's processing.
 *
 * Processing is WorkManager work, so it outlives the screen, the app being in the background and
 * the process being killed: WorkManager re-runs interrupted work by itself, and the recogniser's
 * checkpoints ([com.example.ai.asr.AsrCheckpointStore]) mean a re-run continues rather than
 * starting over.
 *
 * The recording's own status is the other half of the record. [enqueue] marks it PROCESSING, the
 * pipeline marks it READY, ERROR or MODEL_REQUIRED at the end. So "PROCESSING with no work
 * scheduled" can only mean the work was lost (a force-stop, an update installed mid-run), and
 * [resumeInterrupted] puts it back — nothing is ever started twice, and a finished recording is
 * never processed again because someone reopened a screen.
 */
object ProcessingScheduler {

    private val ACTIVE_STATES = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)

    suspend fun activeWork(context: Context, meetingId: String): WorkInfo? =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(MeetingProcessingWorker.meetingWorkTag(meetingId)).first()
            .firstOrNull { it.state in ACTIVE_STATES }

    /**
     * Queues processing for [meetingId] unless it is already queued or running, and returns the
     * work id either way. Settings (engines, models, Internet mode) are read now, at queue time.
     */
    suspend fun enqueue(
        context: Context,
        meetingId: String,
        audioPath: String? = null,
        durationMs: Long? = null,
        expectedSpeakerCount: Int? = null
    ): UUID? {
        activeWork(context, meetingId)?.let { return it.id }

        val database = MeetMindDatabase.getInstance(context)
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return null
        val path = audioPath ?: meeting.audioFilePath ?: return null
        val duration = durationMs?.takeIf { it > 0 } ?: meeting.durationMs.takeIf { it > 0 } ?: probeDurationMs(path)
        val speakers = expectedSpeakerCount ?: meeting.speakerCountPreference
        val prefs = UserPreferencesManager(context).preferencesFlow.first()

        val input = workDataOf(
            MeetingProcessingWorker.KEY_MEETING_ID to meetingId,
            MeetingProcessingWorker.KEY_AUDIO_PATH to path,
            MeetingProcessingWorker.KEY_DURATION_MS to duration,
            MeetingProcessingWorker.KEY_MODEL_ID to prefs.selectedAsrModelId,
            // Resolved (not raw) so a model the user selected and later deleted falls back to one
            // they still have, instead of failing with "no model installed".
            MeetingProcessingWorker.KEY_LLM_MODEL_ID to com.example.ai.modelmanagement.LlmModelResolver.resolve(
                selectedModelId = prefs.selectedLlmModelId,
                modelStorage = com.example.ai.modelmanagement.LocalModelStorage(context)
            ),
            MeetingProcessingWorker.KEY_EXPECTED_SPEAKER_COUNT to (speakers ?: -1),
            MeetingProcessingWorker.KEY_RECORDING_TITLE to meeting.title,
            MeetingProcessingWorker.KEY_CLEANUP_MODE to prefs.transcriptCleanupMode.name,
            MeetingProcessingWorker.KEY_DIARIZATION_STRATEGY to prefs.diarizationStrategy.name,
            MeetingProcessingWorker.KEY_PROCESSING_PROFILE to prefs.processingProfile.name
        )
        val request = OneTimeWorkRequestBuilder<MeetingProcessingWorker>()
            .setInputData(input)
            .addTag(MeetingProcessingWorker.meetingWorkTag(meetingId))
            .addTag(MeetingProcessingWorker.ALL_PROCESSING_TAG)
            // A transient failure (the model file briefly locked, memory pressure) is retried
            // with a growing delay rather than failing the recording outright.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        database.meetingDao().updateMeeting(
            meeting.copy(status = MeetingStatus.PROCESSING.name, durationMs = maxOf(meeting.durationMs, duration), updatedAt = System.currentTimeMillis())
        )
        // One AI-heavy job at a time; later recordings queue behind the current one.
        WorkManager.getInstance(context).enqueueUniqueWork(
            MeetingProcessingWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        return request.id
    }

    /**
     * Re-queues any recording left PROCESSING with no work behind it. Called when the app starts;
     * harmless to call repeatedly. Returns the recordings it resumed.
     */
    suspend fun resumeInterrupted(context: Context): List<String> {
        val database = MeetMindDatabase.getInstance(context)
        val stuck = database.meetingDao().getMeetingsWithStatus(MeetingStatus.PROCESSING.name)
        return stuck.mapNotNull { meeting ->
            if (activeWork(context, meeting.id) != null) return@mapNotNull null
            if (meeting.audioFilePath == null || !File(meeting.audioFilePath).exists()) {
                database.meetingDao().updateMeeting(meeting.copy(status = MeetingStatus.ERROR.name))
                return@mapNotNull null
            }
            enqueue(context, meeting.id)?.let { meeting.id }
        }
    }

    fun cancel(context: Context, meetingId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(MeetingProcessingWorker.meetingWorkTag(meetingId))
    }

    private fun probeDurationMs(path: String): Long = runCatching {
        MediaMetadataRetriever().run {
            setDataSource(path)
            val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            release()
            d
        }
    }.getOrDefault(0L)
}
