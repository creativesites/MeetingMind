package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.core.model.RecordingType
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that [RecordingType] focus guidance (and a user's custom context for
 * [RecordingType.CUSTOM]) actually reaches the extraction prompt sent to the [LanguageModel] —
 * and, critically, that the grounding instruction ("only extract what the transcript supports")
 * is always present regardless of what the user typed. See MEETMIND PHASE 3A "CUSTOM RECORDING
 * CONTEXT": user context must never override the no-hallucination requirement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealMeetingIntelligenceEngineFocusTest {

    /** Captures every prompt it's asked to generate from, and always returns a minimal valid extraction/synthesis JSON so parsing never fails. */
    private class CapturingLanguageModel : LanguageModel {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            prompts += prompt
            val json = if (prompt.contains("Respond with ONLY a JSON object")) {
                """{"title":"T","summary":"S","keyPoints":[]}"""
            } else {
                """{"decisions":[],"actionItems":[],"questions":[],"followUps":[],"briefSummary":""}"""
            }
            return AiResult.Success(json)
        }
    }

    private fun transcript() = Transcript(
        meetingId = "m1",
        segments = listOf(
            TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0L, endMs = 1000L, text = "Let's discuss the roadmap.")
        )
    )

    @Test
    fun `interview focus guidance reaches the extraction prompt`() = runBlocking {
        val model = CapturingLanguageModel()
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        engine.processMeeting(transcript(), "Fallback", RecordingType.INTERVIEW, null)

        val extractionPrompt = model.prompts.first { !it.contains("Respond with ONLY a JSON object") }
        assertTrue(extractionPrompt.contains("interview", ignoreCase = true))
        assertTrue("Grounding rule must always be present", extractionPrompt.contains("ONLY information explicitly supported"))
    }

    @Test
    fun `custom context reaches the prompt and grounding rule is reiterated, never overridden`() = runBlocking {
        val model = CapturingLanguageModel()
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)
        val userContext = "Focus only on pricing objections raised by the customer"

        engine.processMeeting(transcript(), "Fallback", RecordingType.CUSTOM, userContext)

        val extractionPrompt = model.prompts.first { !it.contains("Respond with ONLY a JSON object") }
        assertTrue(extractionPrompt.contains(userContext))
        assertTrue("Custom context must not remove the grounding instruction", extractionPrompt.contains("ONLY information explicitly supported"))
        assertTrue("Custom context must explicitly restate that it never overrides grounding", extractionPrompt.contains("never overrides"))
    }

    @Test
    fun `custom type with blank context falls back to no extra guidance without breaking the prompt`() = runBlocking {
        val model = CapturingLanguageModel()
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        val result = engine.processMeeting(transcript(), "Fallback", RecordingType.CUSTOM, "   ")

        assertTrue(result is AiResult.Success)
        val extractionPrompt = model.prompts.first { !it.contains("Respond with ONLY a JSON object") }
        assertFalse(extractionPrompt.contains("The user described what to focus on"))
    }

    @Test
    fun `general type adds no focus guidance line`() = runBlocking {
        val model = CapturingLanguageModel()
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        engine.processMeeting(transcript(), "Fallback", RecordingType.GENERAL, null)

        val extractionPrompt = model.prompts.first { !it.contains("Respond with ONLY a JSON object") }
        assertTrue(extractionPrompt.contains("ONLY information explicitly supported"))
    }
}
