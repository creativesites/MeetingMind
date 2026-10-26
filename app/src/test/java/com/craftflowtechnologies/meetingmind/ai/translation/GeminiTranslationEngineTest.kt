package com.craftflowtechnologies.meetingmind.ai.translation

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiTranslationEngineTest {

    private class FakeTransport(
        private val response: AiResult<String>,
        private val configured: Boolean = true
    ) : GeminiTransport {
        val requests = mutableListOf<GeminiRequest>()
        override suspend fun execute(request: GeminiRequest): AiResult<String> {
            requests += request
            return response
        }
        override fun isConfigured(): Boolean = configured
    }

    @Test
    fun `unconfigured transport returns ModelUnavailable`() = runBlocking {
        val engine = GeminiTranslationEngine(FakeTransport(AiResult.Success("{}"), configured = false))
        val result = engine.translateTranscript(
            meetingTitle = "Weekly sync",
            summaryText = "Summary",
            segments = listOf(TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0, endMs = 1000, text = "Hello")),
            sourceLanguage = "en",
            targetLanguage = "es"
        )
        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `translates title summary and segments accurately`() = runBlocking {
        val fakeJson = """
        {
          "translatedTitle": "Sincronización semanal",
          "translatedSummary": "Resumen",
          "translatedSegments": [
            {
              "segmentId": "s1",
              "translatedText": "Hola a todos"
            }
          ]
        }
        """.trimIndent()
        val transport = FakeTransport(AiResult.Success(fakeJson))
        val engine = GeminiTranslationEngine(transport)

        val result = engine.translateTranscript(
            meetingTitle = "Weekly sync",
            summaryText = "Summary",
            segments = listOf(TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0, endMs = 1000, text = "Hello everyone")),
            sourceLanguage = "en",
            targetLanguage = "es"
        )
        assertTrue(result is AiResult.Success)
        val translation = (result as AiResult.Success).value
        assertEquals("Sincronización semanal", translation.translatedTitle)
        assertEquals("Resumen", translation.translatedSummary)
        assertEquals(1, translation.translatedSegments.size)
        assertEquals("s1", translation.translatedSegments[0].segmentId)
        assertEquals("Hola a todos", translation.translatedSegments[0].translatedText)
    }

    @Test
    fun `prompt includes Southern African Englishification note for African languages`() {
        val transport = FakeTransport(AiResult.Success("{}"))
        val engine = GeminiTranslationEngine(transport)

        val prompt = engine.buildTranslationPrompt(
            meetingTitle = "Musangano",
            summaryText = "Pfupiso",
            segments = listOf(TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0, endMs = 1000, text = "Tine meeting mangwana")),
            sourceLanguage = "sna",
            targetLanguage = "en"
        )

        assertTrue(prompt.contains("Regional Englishification note"))
        assertTrue(prompt.contains("Shona, Ndebele, Bemba, Nyanja"))
        assertTrue(prompt.contains("Tine meeting mangwana"))
    }
}
