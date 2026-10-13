package com.craftflowtechnologies.meetingmind.ai.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.Wav
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Fake time: sleeping advances the clock, so retry caps are deterministic and tests never wait. */
internal class FakeClock {
    var t = 0L
    val sleeps = mutableListOf<Long>()
    val sleep: suspend (Long) -> Unit = { sleeps += it; t += it }
    val now: () -> Long = { t }
}

internal fun audioBody(ms: Int = 500, mime: String = "audio/wav", rate: Int = 24000, raw: Boolean = false): String {
    val pcm = Pcm(ShortArray(rate * ms / 1000) { (it % 100).toShort() }, rate)
    val bytes = if (raw) Wav.bytes(pcm).drop(44).toByteArray() else Wav.bytes(pcm)
    val data = Base64.getEncoder().encodeToString(bytes)
    return """{"steps":[{"type":"model_output","content":[{"type":"audio","data":"$data","mime_type":"$mime"}]}]}"""
}

internal fun errorBody(code: Int, status: String, message: String, retryDelay: String? = null): String {
    val details = retryDelay?.let { ""","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"$it"}]""" }.orEmpty()
    return """{"error":{"code":$code,"message":"$message","status":"$status"$details}}"""
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiSpeechClientTest {
    private lateinit var server: MockWebServer
    private lateinit var credentials: GeminiCredentialStore
    private val clock = FakeClock()

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        credentials = GeminiCredentialStore(context)
        runBlocking { credentials.setApiKey("test-key") }
    }

    @After fun tearDown() { server.shutdown() }

    private fun interactions(client: OkHttpClient = GeminiInteractions.mediaClient()) =
        GeminiInteractions(credentials, client, server.url("/").toString().trimEnd('/'))

    private fun client(
        permission: CloudPermission = CloudPermission.Allowed,
        retry: RetryPolicy = RetryPolicy(),
        interactions: GeminiInteractions = interactions()
    ) = GeminiSpeechClient(interactions, permission, retry = retry, sleep = clock.sleep, now = clock.now)

    private fun speak(c: GeminiSpeechClient, text: String = "Hello there.", voice: String = "Kore", only: String? = null) =
        runBlocking { c.speak(text, "warm and calm", voice, only) }

    private fun failure(r: GeminiResult<SpeechClip>) = (r as GeminiResult.Failure).error
    private fun enqueue(code: Int, body: String, vararg headers: Pair<String, String>) =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body).also { r -> headers.forEach { r.setHeader(it.first, it.second) } })

    // --- request and response ---

    @Test fun `a request carries the key, model, voice and style`() {
        enqueue(200, audioBody())
        val r = speak(client())
        assertTrue(r is GeminiResult.Success)
        val req = server.takeRequest()
        assertEquals("/v1beta/interactions", req.path)
        assertEquals("test-key", req.getHeader("x-goog-api-key"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("gemini-3.8-flash-tts", body.getString("model"))
        val content = body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0)
        assertEquals("Hello there.", content.getString("text"))
        assertEquals("warm and calm", content.getJSONArray("annotations").getJSONObject(0).getString("style"))
        assertEquals("Kore", body.getJSONObject("generation_config").getJSONArray("speech_config").getJSONObject(0).getString("voice"))
        assertEquals("audio", body.getJSONObject("response_format").getString("type"))
        assertEquals("gemini-3.8-flash-tts", (r as GeminiResult.Success).value.model)
        assertEquals(500L, r.value.audio.durationMs)
    }

    @Test fun `raw L16 audio uses the rate the answer names`() {
        enqueue(200, audioBody(ms = 500, mime = "audio/l16;codec=pcm;rate=16000", rate = 16000, raw = true))
        val r = speak(client()) as GeminiResult.Success
        assertEquals(16000, r.value.audio.sampleRate)
        assertEquals(500L, r.value.audio.durationMs)
    }

    // --- nothing invalid is sent ---

    @Test fun `invalid requests never reach the network`() {
        val c = client()
        assertEquals(GeminiErrorKind.BAD_REQUEST, failure(speak(c, text = "  ")).kind)
        assertEquals(GeminiErrorKind.BAD_REQUEST, failure(speak(c, voice = "NotAVoice")).kind)
        assertEquals(GeminiErrorKind.BAD_REQUEST, failure(speak(c, text = "x".repeat(GeminiSpeechClient.MAX_TEXT_CHARS + 1))).kind)
        assertEquals(0, server.requestCount)
    }

    @Test fun `every voice on the official list is accepted and custom voice ids pass`() {
        assertEquals(30, GeminiSpeechClient.PREBUILT_VOICES.size)
        assertTrue(GeminiSpeechClient.isKnownVoice("Sulafat"))
        assertTrue(GeminiSpeechClient.isKnownVoice("voice_abc"))
        assertFalse(GeminiSpeechClient.isKnownVoice("kore"))
    }

    @Test fun `no key sends nothing`() {
        runBlocking { credentials.clear() }
        val e = failure(speak(client()))
        assertEquals(GeminiErrorKind.NO_KEY, e.kind)
        assertTrue(e.needsUser)
        assertEquals(0, server.requestCount)
    }

    // --- the app's own rules come first ---

    @Test fun `a blocked permission stops the request before it is sent`() {
        val e = failure(speak(client(permission = CloudPermission { "Offline mode" })))
        assertEquals(GeminiErrorKind.POLICY_BLOCKED, e.kind)
        assertEquals("Offline mode", e.message)
        assertFalse(e.retryable)
        assertEquals(0, server.requestCount)
    }

    @Test fun `permission is asked before every request, so a revoked plan stops a reading part-way`() {
        var allowed = true
        val c = client(permission = CloudPermission { if (allowed) null else "Your plan no longer includes the Gemini voice." })
        enqueue(200, audioBody())
        assertTrue(speak(c) is GeminiResult.Success)
        allowed = false
        assertEquals(GeminiErrorKind.POLICY_BLOCKED, failure(speak(c)).kind)
        assertEquals(1, server.requestCount)
    }

    // --- errors only the person can fix stop at once ---

    @Test fun `auth errors are not retried and need the person`() {
        enqueue(403, errorBody(403, "PERMISSION_DENIED", "Your API key was reported as leaked."))
        val e = failure(speak(client()))
        assertEquals(GeminiErrorKind.AUTH, e.kind)
        assertTrue(e.needsUser)
        assertEquals(1, server.requestCount)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test fun `a 400 for an invalid key is an auth error`() {
        enqueue(400, errorBody(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."))
        assertEquals(GeminiErrorKind.AUTH, failure(speak(client())).kind)
    }

    @Test fun `a long quota wait is quota exhausted, not retried`() {
        enqueue(429, errorBody(429, "RESOURCE_EXHAUSTED", "You exceeded your current quota.", retryDelay = "3600s"))
        val e = failure(speak(client()))
        assertEquals(GeminiErrorKind.QUOTA_EXHAUSTED, e.kind)
        assertTrue(e.needsUser)
        assertEquals(1, server.requestCount)
    }

    @Test fun `402 is a billing problem`() {
        enqueue(402, errorBody(402, "FAILED_PRECONDITION", "Prepay credits depleted."))
        assertEquals(GeminiErrorKind.BILLING, failure(speak(client())).kind)
    }

    // --- retries are bounded and backed off ---

    @Test fun `a short rate limit is waited out using Retry-After`() {
        enqueue(429, errorBody(429, "RESOURCE_EXHAUSTED", "Too many requests."), "Retry-After" to "2")
        enqueue(200, audioBody())
        assertTrue(speak(client()) is GeminiResult.Success)
        assertEquals(2, server.requestCount)
        assertEquals(1, clock.sleeps.size)
        assertTrue(clock.sleeps[0] >= 2000)
    }

    @Test fun `rate limiting that never ends stops after the attempts and does not change model`() {
        repeat(5) { enqueue(429, errorBody(429, "RESOURCE_EXHAUSTED", "Too many requests.")) }
        val e = failure(speak(client()))
        assertEquals(GeminiErrorKind.RATE_LIMITED, e.kind)
        assertTrue(e.retriesExhausted)
        assertEquals(3, server.requestCount)
        assertEquals(2, clock.sleeps.size)
        assertTrue(clock.sleeps[1] > clock.sleeps[0]) // backoff grows
    }

    @Test fun `a transient server error is retried and succeeds on the same model`() {
        enqueue(500, errorBody(500, "INTERNAL", "Internal error."))
        enqueue(200, audioBody())
        val r = speak(client()) as GeminiResult.Success
        assertEquals("gemini-3.8-flash-tts", r.value.model)
        assertEquals(2, server.requestCount)
    }

    @Test fun `a persistent server error falls back to the lite model`() {
        repeat(3) { enqueue(503, errorBody(503, "UNAVAILABLE", "Overloaded.")) }
        enqueue(200, audioBody())
        val r = speak(client()) as GeminiResult.Success
        assertEquals("gemini-3.8-flash-lite-tts", r.value.model)
        assertEquals(4, server.requestCount)
    }

    @Test fun `a bad request tries the other model once, then fails without retries`() {
        enqueue(400, errorBody(400, "INVALID_ARGUMENT", "Request contains an invalid argument."))
        enqueue(400, errorBody(400, "INVALID_ARGUMENT", "Request contains an invalid argument."))
        val e = failure(speak(client()))
        assertEquals(GeminiErrorKind.BAD_REQUEST, e.kind)
        assertEquals(2, server.requestCount)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test fun `an unknown model moves on to the next`() {
        enqueue(404, errorBody(404, "NOT_FOUND", "models/gemini-3.8-flash-tts is not found"))
        enqueue(200, audioBody())
        assertEquals("gemini-3.8-flash-lite-tts", (speak(client()) as GeminiResult.Success).value.model)
    }

    @Test fun `a pinned model is the only one tried`() {
        repeat(3) { enqueue(503, errorBody(503, "UNAVAILABLE", "Overloaded.")) }
        enqueue(200, audioBody())
        val e = failure(speak(client(), only = "gemini-3.8-flash-tts"))
        assertEquals(GeminiErrorKind.SERVER, e.kind)
        assertEquals(3, server.requestCount)
        for (i in 0 until 3) assertEquals("gemini-3.8-flash-tts", JSONObject(server.takeRequest().body.readUtf8()).getString("model"))
    }

    @Test fun `the total time cap stops retrying`() {
        repeat(5) { enqueue(503, errorBody(503, "UNAVAILABLE", "Overloaded.")) }
        val e = failure(speak(client(retry = RetryPolicy(baseDelayMs = 4000, totalCapMs = 5000))))
        assertEquals(GeminiErrorKind.SERVER, e.kind)
        assertTrue(e.retriesExhausted)
        assertTrue(server.requestCount < 3)
    }

    // --- network trouble ---

    @Test fun `a timeout is retried then reported`() {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) }
        val fast = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val e = failure(speak(client(interactions = interactions(fast))))
        assertEquals(GeminiErrorKind.TIMEOUT, e.kind)
        assertTrue(e.retriesExhausted)
        assertEquals(3, server.requestCount)
    }

    @Test fun `a dropped connection is a network error`() {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val noRetry = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        val e = failure(speak(client(interactions = interactions(noRetry))))
        assertEquals(GeminiErrorKind.NETWORK, e.kind)
        assertTrue(e.retryable)
    }

    // --- bad answers ---

    @Test fun `an answer that is not JSON is malformed`() {
        repeat(6) { enqueue(200, "<html>oops</html>") }
        val e = failure(speak(client()))
        assertEquals(GeminiErrorKind.MALFORMED, e.kind)
        assertEquals(6, server.requestCount) // three tries on each model
    }

    @Test fun `an answer with no audio is malformed`() {
        repeat(6) { enqueue(200, """{"steps":[{"type":"model_output","content":[{"type":"text","data":"hi"}]}]}""") }
        assertEquals(GeminiErrorKind.MALFORMED, failure(speak(client())).kind)
    }

    @Test fun `undecodable base64 is malformed`() {
        repeat(6) { enqueue(200, """{"steps":[{"content":[{"type":"audio","data":"@@@not-base64@@@","mime_type":"audio/wav"}]}]}""") }
        assertEquals(GeminiErrorKind.MALFORMED, failure(speak(client())).kind)
    }

    @Test fun `audio that is not a WAV is empty audio`() {
        val junk = Base64.getEncoder().encodeToString(ByteArray(64) { 1 })
        repeat(6) { enqueue(200, """{"steps":[{"content":[{"type":"audio","data":"$junk","mime_type":"audio/wav"}]}]}""") }
        assertEquals(GeminiErrorKind.EMPTY_AUDIO, failure(speak(client())).kind)
    }

    @Test fun `audio far too short for the text is rejected`() {
        repeat(6) { enqueue(200, audioBody(ms = 300)) }
        val e = failure(speak(client(), text = "word ".repeat(100)))
        assertEquals(GeminiErrorKind.EMPTY_AUDIO, e.kind)
    }

    @Test fun `error responses without a JSON body are still classified`() {
        enqueue(401, "Unauthorized")
        assertEquals(GeminiErrorKind.AUTH, failure(speak(client())).kind)
    }

    @Test fun `legacy callers still get the old message text`() {
        enqueue(403, errorBody(403, "PERMISSION_DENIED", "denied"))
        val r = runBlocking { interactions().media(GeminiInteractions.speechRequest("m", "Hi.", "", "Kore"), "audio") }
        assertEquals("Gemini 403: denied", (r as com.craftflowtechnologies.meetingmind.ai.common.AiResult.Failed).message)
        assertNotNull(r)
    }
}
