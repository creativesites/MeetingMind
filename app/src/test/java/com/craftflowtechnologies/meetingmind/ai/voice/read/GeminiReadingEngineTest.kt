package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudPermission
import com.craftflowtechnologies.meetingmind.ai.cloud.FakeClock
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiErrorKind
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiInteractions
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiSpeechClient
import com.craftflowtechnologies.meetingmind.ai.cloud.audioBody
import com.craftflowtechnologies.meetingmind.ai.cloud.errorBody
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiReadingEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var credentials: GeminiCredentialStore
    private val clock = FakeClock()
    private lateinit var work: java.io.File

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        credentials = GeminiCredentialStore(context)
        runBlocking { credentials.setApiKey("test-key") }
        work = tmp.newFolder()
    }

    // The credential DataStore is process-wide: don't leave the test key behind for other test classes.
    @After fun tearDown() { server.shutdown(); runBlocking { credentials.clear() } }

    private fun engine(permission: CloudPermission = CloudPermission.Allowed): GeminiReadingEngine {
        val interactions = GeminiInteractions(credentials, GeminiInteractions.mediaClient(), server.url("/").toString().trimEnd('/'))
        return GeminiReadingEngine(GeminiSpeechClient(interactions, permission, sleep = clock.sleep, now = clock.now), work)
    }

    private fun request(vararg texts: String, fp: String = "fp-one-0000000000", pause: Long = 0, max: Int = 2400, pinned: String? = null) =
        GeminiSectionRequest("sec1", fp, texts.map { SpeechChunk(it, pause) }, "calm", "Kore", pinned, max)

    private fun ok() = server.enqueue(MockResponse().setResponseCode(200).setBody(audioBody()))
    /** Every request received since the last drain, oldest first. */
    private fun drain(): List<JSONObject> = generateSequence { server.takeRequest(150, java.util.concurrent.TimeUnit.MILLISECONDS) }
        .map { JSONObject(it.body.readUtf8()) }.toList()
    private fun textOf(b: JSONObject) = b.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text")
    private fun sentTexts(): List<String> = drain().map(::textOf)

    @Test fun `pieces are requested in order and joined with their pauses`() {
        repeat(3) { ok() }
        val r = runBlocking { engine().synthesize(request("One.", "Two.", "Three.", pause = 1000)) } as GeminiSectionResult.Done
        assertEquals(listOf("One.", "Two.", "Three."), sentTexts())
        assertEquals(3 * 500L + 3 * 1000L, r.audio.durationMs)
        assertEquals("gemini-3.8-flash-tts", r.model)
    }

    @Test fun `a long chunk is split to the limit in order`() {
        repeat(3) { ok() }
        val text = "Alpha beta gamma. Delta epsilon zeta. Eta theta iota."
        runBlocking { engine().synthesize(request(text, max = 20)) }
        val sent = sentTexts()
        assertTrue(sent.size >= 3 && sent.all { it.length <= 20 })
        assertEquals(text.filter { !it.isWhitespace() }, sent.joinToString("").filter { !it.isWhitespace() })
    }

    @Test fun `a failure reports where it stopped and keeps the finished pieces`() {
        ok(); ok()
        server.enqueue(MockResponse().setResponseCode(403).setBody(errorBody(403, "PERMISSION_DENIED", "denied")))
        val r = runBlocking { engine().synthesize(request("One.", "Two.", "Three.", "Four.")) } as GeminiSectionResult.Failed
        assertEquals(GeminiErrorKind.AUTH, r.error.kind)
        assertEquals(2, r.pieceIndex)
        assertEquals(2, r.piecesDone)
        assertEquals(3, server.requestCount) // Four. was never attempted
    }

    @Test fun `retrying after a failure resumes after the last good piece`() {
        ok(); ok()
        server.enqueue(MockResponse().setResponseCode(403).setBody(errorBody(403, "PERMISSION_DENIED", "denied")))
        val e = engine()
        runBlocking { e.synthesize(request("One.", "Two.", "Three.", "Four.")) }
        drain()
        ok(); ok()
        val r = runBlocking { e.synthesize(request("One.", "Two.", "Three.", "Four.")) } as GeminiSectionResult.Done
        assertEquals(2, r.reusedPieces)
        assertEquals(listOf("Three.", "Four."), sentTexts()) // only the missing pieces were requested
        assertEquals(4 * 500L, r.audio.durationMs)
    }

    @Test fun `saved pieces are not reused for different content`() {
        ok(); ok()
        val e = engine()
        runBlocking { e.synthesize(request("One.", "Two.", fp = "aaaaaaaaaaaa-0000")) }
        drain()
        ok(); ok()
        val r = runBlocking { e.synthesize(request("One.", "Two.", fp = "bbbbbbbbbbbb-0000")) } as GeminiSectionResult.Done
        assertEquals(0, r.reusedPieces)
        assertEquals(2, drain().size)
    }

    @Test fun `edited text is requested again even under the same fingerprint`() {
        ok(); ok()
        val e = engine()
        runBlocking { e.synthesize(request("One.", "Two.")) }
        drain()
        ok()
        val r = runBlocking { e.synthesize(request("One.", "Two, edited.")) } as GeminiSectionResult.Done
        assertEquals(1, r.reusedPieces)
        assertEquals(listOf("Two, edited."), sentTexts())
    }

    @Test fun `the first model that answers is kept for the rest of the section`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setBody(errorBody(503, "UNAVAILABLE", "x"))) }
        ok(); ok(); ok()
        runBlocking { engine().synthesize(request("One.", "Two.", "Three.")) }
        val models = drain().map { it.getString("model") }
        assertEquals(listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-tts", "gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts", "gemini-3.8-flash-lite-tts", "gemini-3.8-flash-lite-tts"), models)
    }

    @Test fun `a pinned model from earlier sections is the only one used`() {
        repeat(3) { ok() }
        runBlocking { engine().synthesize(request("One.", "Two.", pinned = "gemini-3.8-flash-lite-tts")) }
        val models = drain().map { it.getString("model") }
        assertTrue(models.size == 2 && models.all { it == "gemini-3.8-flash-lite-tts" })
    }

    @Test fun `a revoked permission stops the section and saves what was done`() {
        ok(); ok()
        var asked = 0
        val gated = engine(CloudPermission { if (asked++ < 2) null else "Your plan no longer includes the Gemini voice." })
        val r = runBlocking { gated.synthesize(request("One.", "Two.", "Three.")) } as GeminiSectionResult.Failed
        assertEquals(GeminiErrorKind.POLICY_BLOCKED, r.error.kind)
        assertEquals(2, r.piecesDone)
        assertEquals(2, drain().size) // the third piece was never sent
    }

    @Test fun `discard removes a section's saved pieces`() {
        ok()
        val e = engine()
        runBlocking { e.synthesize(request("One.")) }
        assertTrue(work.walkTopDown().any { it.name.startsWith("piece_") })
        e.discard("sec1")
        assertTrue(work.walkTopDown().none { it.name.startsWith("piece_") })
    }

    @Test fun `nothing to read is a failure, not a request`() {
        val r = runBlocking { engine().synthesize(request("  ")) } as GeminiSectionResult.Failed
        assertEquals(GeminiErrorKind.BAD_REQUEST, r.error.kind)
        assertEquals(0, drain().size)
    }
}
