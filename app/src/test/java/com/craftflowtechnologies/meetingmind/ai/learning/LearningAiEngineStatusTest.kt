package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.learning.LearningAiStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a Learning AI call reports when the cloud route is used up, missing, or fine. The fake cloud answers
 * with a fixed reply; the fake on-device model answers with a fixed reply, so no network or device is needed.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class LearningAiEngineStatusTest {

    private class FakeCloud(private val reply: AiResult<String>) : GeminiTransport {
        override fun isConfigured(): Boolean = true
        override suspend fun execute(request: GeminiRequest): AiResult<String> = reply
    }

    private class FakeLocal(private val reply: AiResult<String>) : LanguageModel {
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> = reply
    }

    private val allowanceFailure = AiResult.Failed("This month's cloud allowance is used up.", retryable = false)
    private val localAnswer = AiResult.Success("""{"answer": "Made on the phone."}""")

    @Test
    fun `a used-up allowance with an on-device model answers on the phone, quietly`() {
        val engine = LearningAiEngine(transport = FakeCloud(allowanceFailure), fallbackModel = FakeLocal(localAnswer))
        val answer = runBlocking { engine.answerGenerally("What is an enzyme?") }
        assertNotNull(answer)
        assertEquals(LearningAiStatus.Ready, engine.lastStatus)
        assertTrue(engine.lastMadeLocally)
    }

    @Test
    fun `a used-up allowance with no on-device model reports allowance used up`() {
        val engine = LearningAiEngine(transport = FakeCloud(allowanceFailure))
        val answer = runBlocking { engine.answerGenerally("What is an enzyme?") }
        assertNull(answer)
        assertTrue(engine.lastStatus is LearningAiStatus.AllowanceUsedUp)
        assertFalse(engine.lastMadeLocally)
    }

    @Test
    fun `no cloud route and no on-device model reports no AI`() {
        val engine = LearningAiEngine()
        val answer = runBlocking { engine.answerGenerally("What is an enzyme?") }
        assertNull(answer)
        assertTrue(engine.lastStatus is LearningAiStatus.NoAi)
    }

    @Test
    fun `a cloud answer is ready and not made locally`() {
        val engine = LearningAiEngine(
            transport = FakeCloud(AiResult.Success("""{"answer": "From the cloud."}""")),
            fallbackModel = FakeLocal(localAnswer)
        )
        assertNotNull(runBlocking { engine.answerGenerally("What is an enzyme?") })
        assertEquals(LearningAiStatus.Ready, engine.lastStatus)
        assertFalse(engine.lastMadeLocally)
    }

    @Test
    fun `a teach-back evaluation carries the allowance outcome of its rubric fallback`() {
        val engine = LearningAiEngine(transport = FakeCloud(allowanceFailure))
        val eval = runBlocking { engine.evaluateTeachBack(rubric = emptyList(), spokenTranscript = "anything", concept = null) }
        assertTrue(eval.status is LearningAiStatus.AllowanceUsedUp)
    }

    @Test
    fun `an ordinary cloud failure with no on-device model is a retryable failure, not an allowance`() {
        val engine = LearningAiEngine(transport = FakeCloud(AiResult.Failed("Gemini is unavailable right now.")))
        assertNull(runBlocking { engine.answerGenerally("What is an enzyme?") })
        val status = engine.lastStatus as? LearningAiStatus.Failed ?: error("expected a failure, got ${engine.lastStatus}")
        assertTrue(status.retryable)
    }
}
