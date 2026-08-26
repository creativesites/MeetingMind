package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.core.model.AskPersonalizationContext
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import com.example.core.model.VocabularyEntry
import com.example.core.model.VocabularySource
import com.example.core.model.VocabularyTermType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [AskPersonalizationContext] (Phase 15 §8) actually reaches the Ask Meeting prompt sent
 * to the [LanguageModel] — and, critically, that an empty context adds nothing at all, so a
 * question asked with no name set and no relevant vocabulary produces the exact same prompt shape
 * this engine already had before personalization existed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealMeetingIntelligenceEngineAskPersonalizationTest {

    private class CapturingLanguageModel : LanguageModel {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            prompts += prompt
            return AiResult.Success("The answer is here.")
        }
    }

    private fun transcript() = Transcript(
        meetingId = "m1",
        segments = listOf(
            TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0L, endMs = 1000L, text = "We use Sherpa Onix for ASR.")
        )
    )

    @Test
    fun `a set user name reaches the ask prompt`() = runBlocking {
        val model = CapturingLanguageModel()
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        engine.askMeeting("What ASR engine do we use?", transcript(), emptyList(), AskPersonalizationContext(userName = "Winston"))

        assertTrue(model.prompts.single().contains("Winston"))
    }

    @Test
    fun `relevant vocabulary reaches the ask prompt with both forms`() = runBlocking {
        val model = CapturingLanguageModel()
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)
        val vocab = listOf(
            VocabularyEntry(
                id = "v1", surfaceForm = "Sherpa Onix", canonicalForm = "Sherpa-ONNX",
                type = VocabularyTermType.OTHER, confidence = 1.0f, source = VocabularySource.REPLACE_ALL,
                frequency = 1, lastConfirmedAt = 0L
            )
        )

        engine.askMeeting("What ASR engine do we use?", transcript(), emptyList(), AskPersonalizationContext(relevantVocabulary = vocab))

        val prompt = model.prompts.single()
        assertTrue(prompt.contains("Sherpa Onix"))
        assertTrue(prompt.contains("Sherpa-ONNX"))
    }

    @Test
    fun `an empty personalization context adds nothing to the prompt`() = runBlocking {
        val model = CapturingLanguageModel()
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        engine.askMeeting("What ASR engine do we use?", transcript(), emptyList())

        val prompt = model.prompts.single()
        assertFalse(prompt.contains("You are answering for"))
        assertFalse(prompt.contains("Known terminology"))
    }
}
