package com.craftflowtechnologies.meetingmind.ai.pipeline

import com.craftflowtechnologies.meetingmind.core.database.ProcessingJobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingProgressTest {
    @Test
    fun `stage ranges are ordered and never overlap`() {
        val p = ProcessingProgress
        assertEquals(35, p.asr(0f)); assertEquals(52, p.asr(1f))
        assertEquals(52, p.speakers(0f)); assertEquals(60, p.speakers(1f))
        assertEquals(60, p.reconciliation(0f)); assertEquals(62, p.reconciliation(1f))
        assertEquals(62, p.cleanup(0f)); assertEquals(68, p.cleanup(1f))
        val seq = listOf(p.asr(0f), p.asr(1f), p.speakers(0f), p.speakers(1f), p.reconciliation(0f), p.reconciliation(1f), p.cleanup(0f), p.cleanup(1f))
        assertEquals(seq.sorted(), seq)
    }

    @Test
    fun `fractions are clamped and monotone`() {
        val values = (-1..11).map { ProcessingProgress.speakers(it / 10f) }
        assertEquals(values.sorted(), values)
        assertTrue(values.all { it in 52..60 })
    }

    @Test
    fun `monotonic progress never goes backwards`() {
        val m = MonotonicProgress()
        assertEquals(40, m.next(40))
        assertEquals(40, m.next(30))
        assertEquals(55, m.next(55))
        assertEquals(100, m.next(500))
    }

    private fun job(meeting: String, done: Boolean = false, failed: Boolean = false) = ProcessingJobEntity(
        id = "job_$meeting", meetingId = meeting, meetingTitle = "t", currentStep = "x", progressPercent = 55,
        isCompleted = done, isFailed = failed, errorMessage = null, startedAt = 0L
    )

    @Test
    fun `only unfinished jobs without active work are orphans`() {
        val jobs = listOf(job("a"), job("b"), job("c", done = true), job("d", failed = true))
        val orphans = orphanedJobs(jobs, meetingIdsWithActiveWork = setOf("a"))
        assertEquals(listOf("b"), orphans.map { it.meetingId })
        val failed = orphans.single().asInterrupted()
        assertTrue(failed.isFailed)
        assertEquals(PROCESSING_INTERRUPTED_MESSAGE, failed.errorMessage)
    }
}
