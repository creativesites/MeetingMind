package com.example.core.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.ai.pipeline.AiToolWorker
import com.example.core.database.AiJobEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.model.AiJob
import com.example.core.model.AiJobStatus
import com.example.core.model.TranscriptAiToolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Persisted, process-death-safe, cancellable, retryable background AI Tools jobs (Phase 15 §5).
 * Every job is a real Room row before it's ever handed to WorkManager, so a screen reopened after
 * the app was killed mid-run reads the real last-known status from [getJobsForMeeting] rather than
 * losing track of it — the gap the pre-existing `viewModelScope`-only `proposeCleanup()`/
 * `reprocessCleanup()` flow has today (see docs/AI_ARCHITECTURE.md §12 for the full writeup).
 */
class AiJobRepository(context: Context, private val database: MeetMindDatabase) {
    private val aiJobDao = database.aiJobDao()
    private val workManager = WorkManager.getInstance(context)

    fun getJobsForMeeting(meetingId: String): Flow<List<AiJob>> =
        aiJobDao.getForMeeting(meetingId).map { list -> list.map(::toDomain) }.flowOn(Dispatchers.IO)

    /** Enqueues [toolType] to run against [meetingId] in the background. The Room row is written
     * as QUEUED *before* WorkManager is touched, so [getJobsForMeeting] reflects the request
     * immediately even if the worker hasn't started yet. Returns the new job's id. */
    suspend fun enqueue(meetingId: String, toolType: TranscriptAiToolType, inputPayloadJson: String = "{}"): String = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        aiJobDao.insertOrUpdate(
            AiJobEntity(
                id = jobId,
                meetingId = meetingId,
                toolType = toolType.name,
                status = AiJobStatus.QUEUED.name,
                inputPayloadJson = inputPayloadJson,
                createdAt = now,
                updatedAt = now
            )
        )
        enqueueWork(jobId)
        jobId
    }

    /** Cancels a QUEUED or RUNNING job. WorkManager cancellation is cooperative/best-effort — the
     * worker only actually stops at its next suspend checkpoint — so the Room row is marked
     * CANCELLED immediately regardless, so the UI reflects the cancellation instantly rather than
     * waiting for the worker to notice. A no-op for a job that's already finished. */
    suspend fun cancel(jobId: String) = withContext(Dispatchers.IO) {
        workManager.cancelUniqueWork(AiToolWorker.jobWorkName(jobId))
        val job = aiJobDao.getById(jobId) ?: return@withContext
        if (job.status == AiJobStatus.QUEUED.name || job.status == AiJobStatus.RUNNING.name) {
            aiJobDao.insertOrUpdate(job.copy(status = AiJobStatus.CANCELLED.name, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Re-runs a FAILED or CANCELLED job with its original input, incrementing
     * [AiJobEntity.retryCount]. A no-op for a job that's QUEUED, RUNNING, or already SUCCEEDED —
     * retrying a job that's still in flight (or already done) would just race or duplicate it. */
    suspend fun retry(jobId: String) = withContext(Dispatchers.IO) {
        val job = aiJobDao.getById(jobId) ?: return@withContext
        if (job.status != AiJobStatus.FAILED.name && job.status != AiJobStatus.CANCELLED.name) return@withContext
        aiJobDao.insertOrUpdate(
            job.copy(
                status = AiJobStatus.QUEUED.name,
                errorMessage = null,
                progressPercent = 0,
                progressStep = "",
                retryCount = job.retryCount + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        enqueueWork(jobId)
    }

    private fun enqueueWork(jobId: String) {
        val request = OneTimeWorkRequestBuilder<AiToolWorker>()
            .setInputData(workDataOf(AiToolWorker.KEY_JOB_ID to jobId))
            .addTag(jobId)
            .build()
        // REPLACE, not APPEND: a retry of the same job id should run once, not stack behind a
        // stale enqueue of itself.
        workManager.enqueueUniqueWork(AiToolWorker.jobWorkName(jobId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun toDomain(entity: AiJobEntity) = AiJob(
        id = entity.id,
        meetingId = entity.meetingId,
        toolType = runCatching { TranscriptAiToolType.valueOf(entity.toolType) }.getOrDefault(TranscriptAiToolType.CLEAN_TRANSCRIPT),
        status = runCatching { AiJobStatus.valueOf(entity.status) }.getOrDefault(AiJobStatus.FAILED),
        progressPercent = entity.progressPercent,
        progressStep = entity.progressStep,
        inputPayloadJson = entity.inputPayloadJson,
        resultPayloadJson = entity.resultPayloadJson,
        errorMessage = entity.errorMessage,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        retryCount = entity.retryCount
    )
}
