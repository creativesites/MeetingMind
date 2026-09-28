package com.example.ai.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.common.AiResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs against a [MockWebServer], so these exercise the real request construction and the real
 * response and error handling without a credential and without touching Google's servers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiHttpTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var credentials: GeminiCredentialStore
    private lateinit var transport: GeminiHttpTransport

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        credentials = GeminiCredentialStore(context)
        transport = GeminiHttpTransport(
            credentials = credentials,
            baseUrl = server.url("/").toString().trimEnd('/')
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        runBlocking { credentials.clear() }
    }

    private fun textRequest() = GeminiRequest(
        modelId = "gemini-3.6-flash",
        systemInstruction = "You analyse meeting transcripts.",
        prompt = "Summarise this."
    )

    private fun enqueueGeneratedText(text: String) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"candidates":[{"content":{"parts":[{"text":"$text"}]}}]}"""
            )
        )
    }

    @Test
    fun `with no key stored the transport reports unavailable and sends nothing`() = runBlocking {
        val result = transport.execute(textRequest())

        assertTrue(result is AiResult.ModelUnavailable)
        assertEquals("no request may be made without a key", 0, server.requestCount)
    }

    @Test
    fun `a stored key is used and the generated text is returned`() = runBlocking {
        credentials.setApiKey("test-key-123")
        enqueueGeneratedText("The team agreed to ship on Friday.")

        val result = transport.execute(textRequest())

        assertEquals("The team agreed to ship on Friday.", (result as AiResult.Success).value)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("models/gemini-3.6-flash:generateContent"))
        assertTrue(recorded.path!!.contains("key=test-key-123"))
    }

    @Test
    fun `the system instruction and prompt are both sent`() = runBlocking {
        credentials.setApiKey("k")
        enqueueGeneratedText("ok")

        transport.execute(textRequest())

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("You analyse meeting transcripts."))
        assertTrue(body.contains("Summarise this."))
    }

    @Test
    fun `a response schema is sent as structured output, so nothing downstream parses prose`() = runBlocking {
        credentials.setApiKey("k")
        enqueueGeneratedText("{}")

        transport.execute(textRequest().copy(responseSchema = """{"type":"object"}"""))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("response_mime_type"))
        // org.json escapes the forward slash, so the media type is written "application\/json".
        assertTrue(body.replace("\\/", "/").contains("application/json"))
        assertTrue(body.contains("response_schema"))
        assertTrue(body.contains("\"type\":\"object\""))
    }

    @Test
    fun `vocabulary hints are sent as a preference, never as an instruction to substitute`() = runBlocking {
        credentials.setApiKey("k")
        enqueueGeneratedText("ok")

        transport.execute(textRequest().copy(vocabularyHints = listOf("Parakeet", "Kondwani")))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("Parakeet"))
        assertTrue(
            "the prompt must forbid replacing a clearly-heard word",
            body.contains("never replace a word you heard clearly")
        )
    }

    @Test
    fun `a rejected key produces an error that says to check Settings`() = runBlocking {
        credentials.setApiKey("bad")
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"message":"API key not valid"}}""")
        )

        val result = transport.execute(textRequest())

        val message = (result as AiResult.Failed).message
        assertTrue(message.contains("key was rejected"))
        assertTrue(message.contains("Settings"))
    }

    @Test
    fun `a quota error suggests the offline path rather than just failing`() = runBlocking {
        credentials.setApiKey("k")
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"quota"}}"""))

        val result = transport.execute(textRequest())

        assertTrue((result as AiResult.Failed).message.contains("offline"))
    }

    @Test
    fun `a server error is reported as temporary rather than as a bad request`() = runBlocking {
        credentials.setApiKey("k")
        server.enqueue(MockResponse().setResponseCode(503).setBody(""))

        val result = transport.execute(textRequest())

        assertTrue((result as AiResult.Failed).message.contains("unavailable right now"))
    }

    @Test
    fun `a response with no usable content fails rather than returning empty text`() = runBlocking {
        credentials.setApiKey("k")
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[]}"""))

        assertTrue(transport.execute(textRequest()) is AiResult.Failed)
    }

    @Test
    fun `clearing the key makes an already-constructed transport stop working immediately`() = runBlocking {
        // The credential is read per request, not captured at construction, so revoking it takes
        // effect at once rather than whenever the object happens to be recreated.
        credentials.setApiKey("k")
        enqueueGeneratedText("ok")
        assertTrue(transport.execute(textRequest()) is AiResult.Success)

        credentials.clear()

        assertTrue(transport.execute(textRequest()) is AiResult.ModelUnavailable)
    }

    @Test
    fun `isConfigured is false until a key is stored`() = runBlocking {
        assertFalse(transport.refreshConfigured())

        credentials.setApiKey("k")

        assertTrue(transport.refreshConfigured())
    }

    @Test
    fun `a redacted key never reveals more than its last few characters`() {
        val redacted = credentials.redact("AQ.SuperSecretValue9876")

        assertFalse(redacted!!.contains("SuperSecret"))
        assertTrue(redacted.endsWith("9876"))
        assertEquals(null, credentials.redact(null))
        assertEquals(null, credentials.redact("  "))
    }

    @Test
    fun `the WAV writer emits a valid 16-bit mono header for the requested slice`() {
        val target = File.createTempFile("wav_test", ".wav")
        try {
            val samples = FloatArray(1000) { 0.5f }

            transport.writeWav(target, samples, from = 100, to = 600, sampleRate = 16_000)

            val bytes = target.readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            // 500 samples at 2 bytes each, plus a 44-byte header.
            assertEquals(44 + 1000, bytes.size)
        } finally {
            target.delete()
        }
    }

    @Test
    fun `samples outside the valid range are clamped rather than wrapping to the opposite extreme`() {
        val target = File.createTempFile("wav_clamp", ".wav")
        try {
            transport.writeWav(target, floatArrayOf(2.0f, -2.0f), from = 0, to = 2, sampleRate = 16_000)

            val bytes = target.readBytes()
            val first = ((bytes[45].toInt() and 0xFF) shl 8) or (bytes[44].toInt() and 0xFF)
            val second = ((bytes[47].toInt() and 0xFF) shl 8) or (bytes[46].toInt() and 0xFF)
            assertEquals("+2.0 must clamp to full scale, not wrap", 32767, first.toShort().toInt())
            assertEquals("-2.0 must clamp to full scale, not wrap", -32767, second.toShort().toInt())
        } finally {
            target.delete()
        }
    }

    @Test
    fun `a whole recording is uploaded as it is and transcribed with the transcription config`() = runBlocking {
        credentials.setApiKey("k")
        val audio = java.io.File.createTempFile("voice", ".m4a").apply { writeBytes(ByteArray(64) { it.toByte() }); deleteOnExit() }
        server.enqueue(MockResponse().setResponseCode(200).addHeader("X-Goog-Upload-URL", server.url("/upload-here").toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"file":{"uri":"https://files/abc","name":"files/abc","state":"ACTIVE"}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"state":"ACTIVE"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"candidates":[{"content":{"parts":[
              {"audioTranscription":{"speakerLabel":"spk_1","words":[{"word":"We","startOffset":"0.100s","endOffset":"0.300s"},{"word":"ship","startOffset":"0.300s","endOffset":"0.650s"}]}},
              {"audioTranscription":{"speakerLabel":"spk_2","words":[{"word":"Friday?","startOffset":"1.2s","endOffset":"1.9s"}]}}
            ]},"finishReason":"STOP"}]}"""
        ))

        val result = transport.execute(
            GeminiRequest(
                modelId = "gemini-3.5-transcribe", systemInstruction = "ignored", prompt = "ignored",
                audioFile = audio, transcription = AudioTranscriptionConfig(), uploadWholeFile = true
            )
        )

        val start = server.takeRequest()
        assertEquals("audio/m4a", start.getHeader("X-Goog-Upload-Header-Content-Type"))
        val upload = server.takeRequest()
        assertEquals(64L, upload.bodySize)
        server.takeRequest() // file state
        val generate = server.takeRequest()
        assertTrue(generate.path!!.contains("gemini-3.5-transcribe:generateContent"))
        val body = org.json.JSONObject(generate.body.readUtf8())
        val config = body.getJSONObject("generationConfig").getJSONObject("audioTranscriptionConfig")
        assertEquals("VERBATIM", config.getString("mode"))
        assertTrue(config.getBoolean("wordTimestamp"))
        assertTrue(config.getBoolean("diarization"))
        assertFalse("custom vocabulary can't go with timestamps", config.has("customVocabulary"))
        assertFalse(body.has("system_instruction"))
        assertFalse(body.getJSONObject("generationConfig").has("response_schema"))
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(1, parts.length())
        assertEquals("audio/m4a", parts.getJSONObject(0).getJSONObject("fileData").getString("mimeType"))

        val words = GeminiTranscriptParser.parseVerbatim((result as AiResult.Success).value, chunkStartMs = 60_000L)!!
        assertEquals(listOf("We", "ship", "Friday?"), words.map { it.text })
        assertEquals(listOf(60_100L, 60_300L, 61_200L), words.map { it.startMs })
        assertEquals(listOf("spk_1", "spk_1", "spk_2"), words.map { it.speakerId })
    }

    @Test
    fun `a transcription answer with nothing in it is a failure`() = runBlocking {
        credentials.setApiKey("k")
        val audio = java.io.File.createTempFile("voice", ".wav").apply { writeBytes(ByteArray(8)); deleteOnExit() }
        server.enqueue(MockResponse().setResponseCode(200).addHeader("X-Goog-Upload-URL", server.url("/u").toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"file":{"uri":"u","name":""}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"candidates":[{"content":{"parts":[]}}]}"""))

        val result = transport.execute(GeminiRequest("gemini-3.5-transcribe", "", "", audioFile = audio, transcription = AudioTranscriptionConfig(), uploadWholeFile = true))

        assertTrue(result is AiResult.Failed)
    }

    @Test
    fun `offsets read as seconds, with or without the s`() {
        assertEquals(1250L, GeminiTranscriptParser.offsetMs("1.250s"))
        assertEquals(500L, GeminiTranscriptParser.offsetMs(0.5))
        assertEquals(null, GeminiTranscriptParser.offsetMs("soon"))
    }

    @Test
    fun `only formats the model reads directly skip decoding`() {
        assertEquals("audio/ogg", GeminiHttpTransport.directMimeType(java.io.File("AUD-20260916-WA0001.ogg")))
        assertEquals("audio/opus", GeminiHttpTransport.directMimeType(java.io.File("a.OPUS")))
        assertEquals(null, GeminiHttpTransport.directMimeType(java.io.File("video.mp4")))
    }
}
