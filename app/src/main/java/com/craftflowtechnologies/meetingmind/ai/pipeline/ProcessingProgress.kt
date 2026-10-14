package com.craftflowtechnologies.meetingmind.ai.pipeline

import com.craftflowtechnologies.meetingmind.core.database.ProcessingJobEntity
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage

/**
 * Where each processing stage sits on the 0..100 bar. No two stages share a range, so the bar
 * never parks on one number while different work is happening.
 */
object ProcessingProgress {
    const val ASR_START = 35
    const val ASR_END = 52
    const val CLOUD_ASR_START = 25
    const val SPEAKERS_START = 52
    const val SPEAKERS_END = 60
    const val RECONCILE_START = 60
    const val RECONCILE_END = 62
    const val CLEANUP_START = 62
    const val CLEANUP_END = 68

    private fun lerp(from: Int, to: Int, fraction: Float): Int =
        (from + (to - from) * fraction.coerceIn(0f, 1f)).toInt()

    fun asr(fraction: Float) = lerp(ASR_START, ASR_END, fraction)
    fun cloudAsr(fraction: Float) = lerp(CLOUD_ASR_START, ASR_END, fraction)
    fun speakers(fraction: Float) = lerp(SPEAKERS_START, SPEAKERS_END, fraction)
    fun reconciliation(fraction: Float) = lerp(RECONCILE_START, RECONCILE_END, fraction)
    fun cleanup(fraction: Float) = lerp(CLEANUP_START, CLEANUP_END, fraction)
}

/** Never lets the reported percent go backwards within one run. */
class MonotonicProgress {
    private var high = 0

    @Synchronized
    fun next(percent: Int): Int {
        high = maxOf(high, percent.coerceIn(0, 100))
        return high
    }
}

const val PROCESSING_INTERRUPTED_MESSAGE = "Processing was interrupted — tap to retry"

/**
 * Pure decision for the orphan sweep: a job row that is neither completed nor failed, whose
 * recording has no queued or running work, will never move again and must be failed.
 */
fun orphanedJobs(jobs: List<ProcessingJobEntity>, meetingIdsWithActiveWork: Set<String>): List<ProcessingJobEntity> =
    jobs.filter { !it.isCompleted && !it.isFailed && it.meetingId !in meetingIdsWithActiveWork }

fun ProcessingJobEntity.asInterrupted(message: String = PROCESSING_INTERRUPTED_MESSAGE): ProcessingJobEntity =
    copy(isFailed = true, isCompleted = false, errorMessage = message, currentStep = "Processing stopped", stage = ProcessingStage.FAILED.name)
