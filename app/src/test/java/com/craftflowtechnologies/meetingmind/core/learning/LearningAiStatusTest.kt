package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.ai.cloud.UsageState
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.learning.StudyKitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningAiStatusTest {

    private val fresh = UsageState()
    private val usedUp = UsageState(known = true, exhausted = true, periodEndMs = 1_800_000_000_000L)

    @Test
    fun `a successful call is ready`() {
        assertEquals(LearningAiStatus.Ready, LearningAiStatus.of(AiResult.Success("ok"), fresh))
    }

    @Test
    fun `a failure with the managed meter used up is an allowance, with its reset time`() {
        assertEquals(
            LearningAiStatus.AllowanceUsedUp(1_800_000_000_000L),
            LearningAiStatus.of(AiResult.Failed("Gemini said no.", retryable = true), usedUp)
        )
    }

    @Test
    fun `the allowance message from a route counts even when the meter is not known`() {
        val status = LearningAiStatus.of(AiResult.Failed("This month's built-in AI allowance is used up.", retryable = false), fresh)
        assertEquals(LearningAiStatus.AllowanceUsedUp(0L), status)
    }

    @Test
    fun `an ordinary failure stays a failure and keeps its retryable flag`() {
        assertEquals(
            LearningAiStatus.Failed("Gemini is unavailable right now.", retryable = true),
            LearningAiStatus.of(AiResult.Failed("Gemini is unavailable right now.", retryable = true), fresh)
        )
        assertEquals(
            LearningAiStatus.Failed("Your key was rejected.", retryable = false),
            LearningAiStatus.of(AiResult.Failed("Your key was rejected.", retryable = false), fresh)
        )
    }

    @Test
    fun `a missing model is no AI, with its message`() {
        assertEquals(
            LearningAiStatus.NoAi("No model is installed."),
            LearningAiStatus.of(AiResult.ModelUnavailable("local", "No model is installed."), fresh)
        )
    }

    @Test
    fun `outcomes use a fixed analytics vocabulary`() {
        assertEquals("ready", LearningAiStatus.Ready.outcome)
        assertEquals("no_ai", LearningAiStatus.NoAi("x").outcome)
        assertEquals("allowance_used_up", LearningAiStatus.AllowanceUsedUp().outcome)
        assertEquals("failed", LearningAiStatus.Failed("x", retryable = true).outcome)
    }

    @Test
    fun `plans are offered only when billing is configured`() {
        var opened = 0
        assertNull(plansAction(billingConfigured = false) { opened++ })
        plansAction(billingConfigured = true) { opened++ }?.invoke()
        assertEquals(1, opened)
    }

    @Test
    fun `a study kit allowance maps to the allowance status`() {
        val status = StudyKitResult.AllowanceUsedUp(42L).toLearningStatus()
        assertEquals(LearningAiStatus.AllowanceUsedUp(42L), status)
        assertNotNull(status)
        assertTrue(StudyKitResult.NoTranscript.toLearningStatus() == LearningAiStatus.Ready)
    }
}
