package com.example.ai.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.LocalModelStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With no local LLM installed (this test's Robolectric environment, matching a fresh app
 * install), [MediaPipeLanguageModel] must report [AiResult.ModelUnavailable] without ever
 * touching a MediaPipe LlmInference class — which is also what makes this testable on the JVM at
 * all (MediaPipe's native inference engine cannot load in Robolectric). Real generation quality
 * and correctness are NOT covered here — see docs/AI_ARCHITECTURE.md "Known Limitations".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPipeLanguageModelTest {

    @Test
    fun `reports model unavailable when no local LLM is installed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val model = MediaPipeLanguageModel(context, LocalModelStorage(context))

        val result = model.generate("Summarize this meeting")

        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `RealMeetingIntelligenceEngine propagates LLM unavailability rather than fabricating a summary`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val languageModel = MediaPipeLanguageModel(context, LocalModelStorage(context))
        val engine = RealMeetingIntelligenceEngine(languageModel, contextLengthTokens = 4096)
        val transcript = com.example.core.model.Transcript(
            meetingId = "m1",
            segments = listOf(
                com.example.core.model.TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0L, endMs = 1000L, text = "We decided to ship Friday.")
            )
        )

        val result = engine.processMeeting(transcript, "Fallback Title")

        assertTrue("Expected an honest failure, not AiResult.Success, when no LLM is installed", result !is AiResult.Success)
    }
}
