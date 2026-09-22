package com.example.ai.cloud

import com.example.ai.common.AiResult
import com.example.core.model.ProcessingProfile
import com.example.ai.routing.AiRoute
import com.example.ai.routing.DefaultAiModelRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The cloud pipeline's degradation contract (overhaul brief §37/§38): a failure downstream never
 * discards a successful result upstream.
 *
 * Runs under Robolectric purely so `org.json` is the real implementation rather than the stub the
 * plain JVM unit-test runtime provides — there is no Android dependency in the engine itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiTranscriptionEngineTest {

    private val audio = File("unused-in-these-tests.m4a")

    /** Answers verbatim requests from a script and smart requests from another, with no network. */
    private class ScriptedTransport(
        private val verbatim: (GeminiRequest) -> AiResult<String>,
        private val smart: (GeminiRequest) -> AiResult<String> = { AiResult.Success("") },
        private val configured: Boolean = true
    ) : GeminiTransport {
        val requests = mutableListOf<GeminiRequest>()
        override suspend fun execute(request: GeminiRequest): AiResult<String> {
            requests += request
            return if (request.systemInstruction.startsWith("You are a verbatim")) verbatim(request) else smart(request)
        }
        override fun isConfigured() = configured
    }

    private fun verbatimJson(vararg words: Triple<String, Long, Long>): String {
        val items = words.joinToString(",") { (text, start, end) ->
            """{"text":"$text","startMs":$start,"endMs":$end,"speaker":"SPEAKER_0"}"""
        }
        return """{"words":[$items]}"""
    }

    private val threeWords = verbatimJson(
        Triple("we", 0L, 400L), Triple("ship", 400L, 800L), Triple("friday", 800L, 1_200L)
    )

    @Test
    fun `an unconfigured transport reports unavailable instead of failing mid-upload`() = runBlocking {
        val engine = GeminiTranscriptionEngine(UnconfiguredGeminiTransport())

        val result = engine.transcribe(audio, 60_000L)

        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `both passes succeeding produces a fused transcript`() = runBlocking {
        val transport = ScriptedTransport(
            verbatim = { AiResult.Success(threeWords) },
            smart = { AiResult.Success("We ship Friday.") }
        )

        val result = GeminiTranscriptionEngine(transport).transcribe(audio, 60_000L)

        val value = (result as AiResult.Success).value
        assertTrue(value.smartPassApplied)
        assertEquals("We ship Friday.", value.words.joinToString(" ") { it.text })
    }

    @Test
    fun `a failed smart pass keeps the verbatim transcript`() = runBlocking {
        val transport = ScriptedTransport(
            verbatim = { AiResult.Success(threeWords) },
            smart = { AiResult.Failed("quota exceeded") }
        )

        val result = GeminiTranscriptionEngine(transport).transcribe(audio, 60_000L)

        val value = (result as AiResult.Success).value
        assertFalse(value.smartPassApplied)
        assertEquals("we ship friday", value.words.joinToString(" ") { it.text })
        assertTrue(value.degradedReason!!.contains("verbatim transcript is unaffected"))
    }

    @Test
    fun `a smart pass that disagrees too broadly keeps the verbatim transcript`() = runBlocking {
        val transport = ScriptedTransport(
            verbatim = { AiResult.Success(threeWords) },
            smart = { AiResult.Success("Entirely unrelated prose about something else.") }
        )

        val result = GeminiTranscriptionEngine(transport).transcribe(audio, 60_000L)

        val value = (result as AiResult.Success).value
        assertFalse(value.smartPassApplied)
        assertEquals("we ship friday", value.words.joinToString(" ") { it.text })
    }

    @Test
    fun `a failed verbatim pass fails honestly rather than returning a transcript with a hole`() = runBlocking {
        val transport = ScriptedTransport(verbatim = { AiResult.Failed("upload timed out") })

        val result = GeminiTranscriptionEngine(transport).transcribe(audio, 60_000L)

        assertTrue(result is AiResult.Failed)
        assertTrue((result as AiResult.Failed).message.contains("upload timed out"))
    }

    @Test
    fun `an unreadable verbatim response is a failure, never a partial guess`() = runBlocking {
        val transport = ScriptedTransport(verbatim = { AiResult.Success("this is not json at all") })

        val result = GeminiTranscriptionEngine(transport).transcribe(audio, 60_000L)

        assertTrue(result is AiResult.Failed)
    }

    @Test
    fun `a long recording is chunked and every chunk is requested`() = runBlocking {
        val transport = ScriptedTransport(
            verbatim = { AiResult.Success(threeWords) },
            smart = { AiResult.Success("We ship Friday.") }
        )
        val totalMs = 30 * 60 * 1_000L
        val expectedChunks = GeminiChunkPlanner.plan(totalMs).size

        val result = GeminiTranscriptionEngine(transport).transcribe(audio, totalMs)

        assertEquals(expectedChunks, (result as AiResult.Success).value.chunkCount)
        assertEquals(
            expectedChunks,
            transport.requests.count { it.systemInstruction.startsWith("You are a verbatim") }
        )
    }

    @Test
    fun `the verbatim pass asks for structured output and passes vocabulary through`() = runBlocking {
        val transport = ScriptedTransport(verbatim = { AiResult.Success(threeWords) })

        GeminiTranscriptionEngine(transport).transcribe(audio, 60_000L, vocabularyHints = listOf("Parakeet"))

        val request = transport.requests.first()
        assertEquals(GeminiTranscriptParser.VERBATIM_SCHEMA, request.responseSchema)
        assertEquals(listOf("Parakeet"), request.vocabularyHints)
        assertEquals(DefaultAiModelRouter.GEMINI_TRANSCRIBE_MODEL, request.modelId)
    }

    @Test
    fun `chunk-relative timestamps are converted to recording time`() {
        val words = GeminiTranscriptParser.parseVerbatim(threeWords, chunkStartMs = 100_000L)!!

        assertEquals(100_000L, words.first().startMs)
        assertEquals(101_200L, words.last().endMs)
    }

    @Test
    fun `offline mode has no cloud route at all`() {
        // Privacy here is structural: an offline run cannot reach a Gemini engine by taking a
        // wrong branch, because it is never given a route it could use.
        val offlineRoutes = DefaultAiModelRouter.routesFor(ProcessingProfile.OFFLINE)

        assertTrue(offlineRoutes.none { DefaultAiModelRouter.route(it).requiresNetwork })
        assertFalse(AiRoute.GEMINI_TRANSCRIPTION_VERBATIM in offlineRoutes)
        assertFalse(AiRoute.GEMINI_INTELLIGENCE in offlineRoutes)
    }

    @Test
    fun `a long recording is fused chunk by chunk, not in one pass`() {
        // Alignment is quadratic, so one pass over a long recording exceeds the fusion engine's
        // own size guard and silently comes back verbatim. Chunk-sized passes keep every table
        // small however long the meeting is.
        val chunks = listOf(AudioChunk(0, 0L, 10_000L), AudioChunk(1, 8_000L, 18_000L))
        val words = listOf(
            com.example.ai.transcript.CanonicalWord("v0", "we", 0L, 400L),
            com.example.ai.transcript.CanonicalWord("v1", "ship", 400L, 800L),
            com.example.ai.transcript.CanonicalWord("v2", "sounds", 12_000L, 12_400L),
            com.example.ai.transcript.CanonicalWord("v3", "good", 12_400L, 12_800L)
        )

        val fused = GeminiTranscriptionEngine(UnconfiguredGeminiTransport()).fusePerChunk(
            words, chunks, mapOf(0 to "We ship.", 1 to "Sounds good.")
        )

        assertEquals("We ship. Sounds good.", fused.words.joinToString(" ") { it.text })
        assertEquals(listOf("w0", "w1", "w2", "w3"), fused.words.map { it.id })
    }

    @Test
    fun `a chunk whose smart pass failed keeps its verbatim words while the others are polished`() {
        val chunks = listOf(AudioChunk(0, 0L, 10_000L), AudioChunk(1, 8_000L, 18_000L))
        val words = listOf(
            com.example.ai.transcript.CanonicalWord("v0", "we", 0L, 400L),
            com.example.ai.transcript.CanonicalWord("v1", "ship", 400L, 800L),
            com.example.ai.transcript.CanonicalWord("v2", "sounds", 12_000L, 12_400L),
            com.example.ai.transcript.CanonicalWord("v3", "good", 12_400L, 12_800L)
        )

        val fused = GeminiTranscriptionEngine(UnconfiguredGeminiTransport()).fusePerChunk(
            words, chunks, mapOf(0 to "We ship.")
        )

        assertEquals("We ship. sounds good", fused.words.joinToString(" ") { it.text })
    }

    @Test
    fun `a word in the overlap region is fused exactly once`() {
        val chunks = listOf(AudioChunk(0, 0L, 10_000L), AudioChunk(1, 8_000L, 18_000L))
        val words = listOf(
            com.example.ai.transcript.CanonicalWord("v0", "we", 9_000L, 9_400L),
            com.example.ai.transcript.CanonicalWord("v1", "ship", 9_400L, 9_800L)
        )

        val fused = GeminiTranscriptionEngine(UnconfiguredGeminiTransport()).fusePerChunk(
            words, chunks, mapOf(0 to "We ship.", 1 to "We ship.")
        )

        assertEquals(2, fused.words.size)
    }
}