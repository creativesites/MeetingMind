package com.craftflowtechnologies.meetingmind.ai.diarization

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.transcript.DiarizationTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiarizationBudgetTest {
    private val turn = DiarizationTurn("s", 0, 1000)

    @Test
    fun `budget is half the duration clamped to 60s and 20min`() {
        assertEquals(60_000L, diarizationBudgetMs(0))
        assertEquals(60_000L, diarizationBudgetMs(100_000))
        assertEquals(300_000L, diarizationBudgetMs(600_000))
        assertEquals(20 * 60_000L, diarizationBudgetMs(2 * 60 * 60_000L))
    }

    @Test
    fun `cleanup and reconciliation budgets are clamped`() {
        assertEquals(2 * 60_000L, cleanupBudgetMs(0))
        assertEquals(15 * 60_000L, cleanupBudgetMs(10 * 60 * 60_000L))
        assertEquals(60_000L, reconciliationBudgetMs(0))
        assertEquals(5 * 60_000L, reconciliationBudgetMs(10 * 60 * 60_000L))
    }

    @Test
    fun `a never-returning run degrades within budget in virtual time`() = runTest {
        val outcome = runDiarizationWithBudget(60_000) { awaitCancellation() }
        assertTrue(outcome is DiarizationOutcome.Degraded)
        assertEquals(60_000L, currentTime)
    }

    @Test
    fun `success and unavailable pass through`() = runTest {
        assertEquals(DiarizationOutcome.Separated(listOf(turn)), runDiarizationWithBudget(1000) { AiResult.Success(listOf(turn)) })
        assertEquals(DiarizationOutcome.Unavailable, runDiarizationWithBudget(1000) { AiResult.ModelUnavailable("m", "none") })
        assertTrue(runDiarizationWithBudget(1000) { AiResult.Failed("boom") } is DiarizationOutcome.Degraded)
    }

    @Test
    fun `out of memory degrades instead of crashing`() = runTest {
        val outcome = runDiarizationWithBudget(1000) { throw OutOfMemoryError("oom") }
        assertTrue(outcome is DiarizationOutcome.Degraded)
    }

    @Test
    fun `caller cancellation propagates`() = runTest {
        val started = CompletableDeferred<Unit>()
        val job = async {
            runDiarizationWithBudget(60_000) { started.complete(Unit); awaitCancellation() }
        }
        started.await()
        job.cancel()
        try {
            job.await()
            fail("expected cancellation")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
