package com.example.ai.pipeline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.core.database.AiJobEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.domain.FixTerminologyUseCase
import com.example.core.domain.ReprocessTranscriptCleanupUseCase
import com.example.core.model.AiJobStatus
import com.example.core.model.TranscriptAiToolType
import com.example.core.model.TranscriptCleanupMode
import com.example.core.repository.MeetingRepository
import com.example.core.repository.TranscriptRepository
import com.example.core.repository.VocabularyRepository
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Generic background runner for the "✨ AI Tools" menu (Phase 15 §5) — reads one persisted
 * [AiJobEntity] by id and dispatches on its [AiJobEntity.toolType]. This is infrastructure plus,
 * as of Phase 15 §6, two real wired tools: [TranscriptAiToolType.CLEAN_TRANSCRIPT] (delegates to
 * the pre-existing [ReprocessTranscriptCleanupUseCase]) and [TranscriptAiToolType.FIX_TERMINOLOGY]
 * ([FixTerminologyUseCase], built new this phase — no LLM prompt contract needed, since it's a
 * deterministic exact-match replace driven by [com.example.core.repository.VocabularyRepository]'s
 * learned corrections). Every other tool type fails the job honestly with "isn't wired up yet"
 * rather than fabricating a result — the same discipline [MeetingProcessingPipeline] uses for a
 * model that isn't installed.
 *
 * Mirrors [MeetingProcessingWorker]'s shape (persisted, process-death-safe, real progress) but is
 * not a copy-paste of it: that worker is permanently specific to the meeting-processing pipeline,
 * this one is the generic dispatcher every future AI Tools run goes through.
 */
class AiToolWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure(workDataOf(KEY_ERROR to "Missing jobId"))
        val database = MeetMindDatabase.getInstance(applicationContext)
        val aiJobDao = database.aiJobDao()
        val job = aiJobDao.getById(jobId) ?: return Result.failure(workDataOf(KEY_ERROR to "Job $jobId not found"))

        val toolType = runCatching { TranscriptAiToolType.valueOf(job.toolType) }.getOrNull()
            ?: return failJob(aiJobDao, job, "Unknown tool type: ${job.toolType}")

        aiJobDao.insertOrUpdate(job.copy(status = AiJobStatus.RUNNING.name, progressPercent = 0, updatedAt = System.currentTimeMillis()))
        setProgress(workDataOf(KEY_PROGRESS_PERCENT to 0))

        return try {
            when (toolType) {
                TranscriptAiToolType.CLEAN_TRANSCRIPT -> runCleanTranscript(database, job)
                TranscriptAiToolType.FIX_TERMINOLOGY -> runFixTerminology(database, job)
                else -> failJob(aiJobDao, job, "\"${toolType.label}\" isn't wired up yet — not run.")
            }
        } catch (e: CancellationException) {
            // Real cancellation (via WorkManager or AiJobRepository.cancel) — record it honestly
            // rather than as a generic failure.
            aiJobDao.insertOrUpdate(job.copy(status = AiJobStatus.CANCELLED.name, updatedAt = System.currentTimeMillis()))
            Result.failure(workDataOf(KEY_ERROR to "Cancelled"))
        } catch (e: Exception) {
            failJob(aiJobDao, job, e.message ?: "Unknown error running ${toolType.label}")
        }
    }

    /** The one real, wired tool today: re-runs [ReprocessTranscriptCleanupUseCase] through this
     * generic, persisted job queue instead of the ad-hoc `viewModelScope` launch it previously
     * only had (see [com.example.feature.meetingdetail.MeetingDetailScreen]'s `reprocessCleanup`
     * — that call site is not yet switched over; doing so is Phase 6 UI work, this worker is
     * ready for it). */
    private suspend fun runCleanTranscript(database: MeetMindDatabase, job: AiJobEntity): Result {
        val meetingRepository = MeetingRepository(applicationContext, database)
        val transcriptRepository = TranscriptRepository(database)
        val pipeline = MeetingProcessingPipeline(applicationContext, database)
        val useCase = ReprocessTranscriptCleanupUseCase(pipeline, meetingRepository, transcriptRepository)

        val input = runCatching { JSONObject(job.inputPayloadJson) }.getOrDefault(JSONObject())
        val mode = input.optString("cleanupMode", TranscriptCleanupMode.MODERATE.name).let {
            runCatching { TranscriptCleanupMode.valueOf(it) }.getOrDefault(TranscriptCleanupMode.MODERATE)
        }

        setProgress(workDataOf(KEY_PROGRESS_PERCENT to 50))
        useCase(job.meetingId, mode)

        val resultJson = JSONObject().put("cleanupMode", mode.name).toString()
        database.aiJobDao().insertOrUpdate(
            job.copy(status = AiJobStatus.SUCCEEDED.name, progressPercent = 100, resultPayloadJson = resultJson, updatedAt = System.currentTimeMillis())
        )
        return Result.success(workDataOf(KEY_RESULT_JOB_ID to job.id))
    }

    /** Applies every learned correction to this meeting's transcript in one pass — see
     * [FixTerminologyUseCase]. */
    private suspend fun runFixTerminology(database: MeetMindDatabase, job: AiJobEntity): Result {
        val transcriptRepository = TranscriptRepository(database)
        val vocabularyRepository = VocabularyRepository(database)
        val useCase = FixTerminologyUseCase(transcriptRepository, vocabularyRepository)

        setProgress(workDataOf(KEY_PROGRESS_PERCENT to 50))
        val changes = useCase(job.meetingId)

        val resultJson = JSONObject().put("segmentsChanged", changes.size).toString()
        database.aiJobDao().insertOrUpdate(
            job.copy(status = AiJobStatus.SUCCEEDED.name, progressPercent = 100, resultPayloadJson = resultJson, updatedAt = System.currentTimeMillis())
        )
        return Result.success(workDataOf(KEY_RESULT_JOB_ID to job.id))
    }

    private suspend fun failJob(aiJobDao: com.example.core.database.AiJobDao, job: AiJobEntity, message: String): Result {
        aiJobDao.insertOrUpdate(job.copy(status = AiJobStatus.FAILED.name, errorMessage = message, updatedAt = System.currentTimeMillis()))
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    companion object {
        /** One unique work chain per job id — independent AI Tools runs (even for the same
         * meeting) don't queue behind each other the way meeting processing intentionally does. */
        fun jobWorkName(jobId: String): String = "meetmind_ai_tool_$jobId"

        const val KEY_JOB_ID = "jobId"
        const val KEY_PROGRESS_PERCENT = "percent"
        const val KEY_RESULT_JOB_ID = "resultJobId"
        const val KEY_ERROR = "error"
    }
}
