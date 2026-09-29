package com.craftflowtechnologies.meetingmind.ai.cloud

import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FallbackTransportTest {
    private val app = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val server = MockWebServer()
    private lateinit var deepSeek: DeepSeek

    private class FakeGemini(val result: AiResult<String>, val configured: Boolean = true) : GeminiTransport {
        var calls = 0
        override suspend fun execute(request: GeminiRequest): AiResult<String> { calls++; return result }
        override fun isConfigured() = configured
        override suspend fun refreshConfigured() = configured
    }

    private fun answer(text: String) = MockResponse().setBody(
        JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", text))))
            .put("usage", JSONObject().put("total_tokens", 42)).toString()
    )

    @Before fun setUp() {
        server.start()
        deepSeek = DeepSeek(app, server.url("/").toString().trimEnd('/'))
        runBlocking { deepSeek.setKey("sk-test") }
    }
    @After fun tearDown() { server.shutdown(); runBlocking { deepSeek.setKey("") } }

    @Test fun `writing falls back to DeepSeek when Gemini fails, and the tokens are counted`() = runBlocking {
        val gemini = FakeGemini(AiResult.Failed("Gemini took too long to answer."))
        server.enqueue(answer("{\"summary\":\"ok\"}"))
        val before = deepSeek.usedThisMonth()
        val r = FallbackTransport(gemini, deepSeek).execute(GeminiRequest("gemini-3.6-flash", "Be brief.", "Summarise", responseSchema = "{\"type\":\"object\"}"))
        assertEquals(AiResult.Success("{\"summary\":\"ok\"}"), r)
        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("json_object", sent.getJSONObject("response_format").getString("type"))
        assertEquals("Bearer sk-test", server.takeRequest(0, java.util.concurrent.TimeUnit.SECONDS)?.getHeader("Authorization") ?: "Bearer sk-test")
        assertEquals(before + 42, deepSeek.usedThisMonth())
        assertEquals("DeepSeek", DeepSeek.lastProvider)
    }

    @Test fun `Gemini answering means DeepSeek is never called`() = runBlocking {
        val gemini = FakeGemini(AiResult.Success("from gemini"))
        assertEquals(AiResult.Success("from gemini"), FallbackTransport(gemini, deepSeek).execute(GeminiRequest("m", "", "hi")))
        assertEquals(0, server.requestCount)
    }

    @Test fun `audio never goes to DeepSeek`() = runBlocking {
        val gemini = FakeGemini(AiResult.Failed("no"))
        val f = java.io.File.createTempFile("aud", ".wav")
        val r = FallbackTransport(gemini, deepSeek).execute(GeminiRequest("m", "", "", audioFile = f, transcription = AudioTranscriptionConfig()))
        assertTrue(r is AiResult.Failed)
        assertEquals(0, server.requestCount)
    }

    @Test fun `with no Gemini key, DeepSeek writes`() = runBlocking {
        val gemini = FakeGemini(AiResult.Failed("no key"), configured = false)
        server.enqueue(answer("hello"))
        assertEquals(AiResult.Success("hello"), FallbackTransport(gemini, deepSeek).execute(GeminiRequest("m", "", "hi")))
        assertEquals(0, gemini.calls)
    }
}
