package com.example.core.model

/**
 * Real lifecycle for a background AI tool run (Phase 15 §5) — mirrors [ProcessingStage]'s "no
 * fake progress" discipline. A job is always in exactly one of these states, persisted, so it
 * survives process death: a screen reopened after the app was killed mid-run reads the real
 * last-known state from Room rather than losing track of it.
 */
enum class AiJobStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * One request to run a [TranscriptAiToolType] in the background, persisted end-to-end (see
 * [com.example.core.database.AiJobEntity] / [com.example.core.repository.AiJobRepository] /
 * [com.example.ai.pipeline.AiToolWorker]). Domain-layer mirror of the Room entity.
 */
data class AiJob(
    val id: String,
    val meetingId: String,
    val toolType: TranscriptAiToolType,
    val status: AiJobStatus,
    val progressPercent: Int,
    val progressStep: String,
    val inputPayloadJson: String,
    val resultPayloadJson: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val retryCount: Int
) {
    val isActive: Boolean get() = status == AiJobStatus.QUEUED || status == AiJobStatus.RUNNING
}
