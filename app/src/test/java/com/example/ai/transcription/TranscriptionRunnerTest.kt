package com.example.ai.transcription

import com.example.ai.cloud.AudioChunk
import com.example.ai.cloud.ChunkPlanConfig
import com.example.ai.cloud.GeminiChunkPlanner
import com.example.ai.cloud.GeminiRequest
import com.example.ai.cloud.GeminiTranscriptionEngine
import com.example.ai.cloud.GeminiTransport
import com.example.ai.common.AiResult
import com.example.ai.transcript.CanonicalWord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Resumable regions, Gemini→phone fallback and hand-overs in both directions. Nothing here touches real audio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptionRunnerTest {
    private val total = 60L * 60 * 1000 // an hour: three Gemini parts at 25 min
    private lateinit var dir: File
    private lateinit var audio: File

    @Before fun setup() {
        dir = File.createTempFile("regions", "d").also { it.delete(); it.mkdirs() }
        audio = File.createTempFile("audio", ".m4a").also { it.writeBytes(ByteArray(1000)) }
        TranscriptionRoutes.consume("m")
    }

    private fun w(text: String, s: Long, e: Long, spk: String? = null) = CanonicalWord("x", text, s, e, spk)

    /** Gemini that answers each part with one word per minute, failing on a chosen part. */
    private class FakeGemini(val failOnStartMs: Long? = null, val gate: CompletableDeferred<Unit>? = null) : GeminiTransport {
        val requested = mutableListOf<Pair<Long, Long>>()
        override suspend fun execute(request: GeminiRequest): AiResult<String> {
            requested += request.audioStartMs to request.audioEndMs
            gate?.await()
            if (request.audioStartMs == failOnStartMs) return AiResult.Failed("Network lost")
            val words = (request.audioStartMs / 60_000 until request.audioEndMs / 60_000).joinToString(",") { m ->
                "{\"word\":\"m$m\",\"startOffset\":\"0s\",\"endOffset\":\"1s\"}".let { _ -> "{\"word\":\"m$m\",\"startOffset\":\"${(m * 60_000 - request.audioStartMs) / 1000}s\",\"endOffset\":\"${(m * 60_000 - request.audioStartMs) / 1000 + 1}s\"}" }
            }
            return AiResult.Success("{\"turns\":[{\"speakerLabel\":\"A\",\"words\":[$words]}]}")
        }
        override fun isConfigured() = true
    }

    private fun runner(t: FakeGemini, local: suspend (List<LongRange>) -> List<Triple<Long, Long, List<CanonicalWord>>>): TranscriptionRunner {
        val engine = GeminiTranscriptionEngine(t, chunkConfig = ChunkPlanConfig(maxChunkMs = 25 * 60_000L, overlapMs = 20_000L))
        return TranscriptionRunner(TranscriptionCheckpoints(dir), engine) { gaps, onRegion, _ ->
            local(gaps).forEach { (s, e, words) -> onRegion(s, e, words) }
            AiResult.Success(local(gaps).flatMap { it.third })
        }
    }

    @Test fun coverageFindsWhatIsNotDone() {
        val done = listOf(DoneRegion(0, 1000, TranscriptionRoute.GEMINI, emptyList()), DoneRegion(900, 5000, TranscriptionRoute.LOCAL, emptyList()), DoneRegion(9000, 10_000, TranscriptionRoute.LOCAL, emptyList()))
        assertEquals(listOf(5000L until 9000L), Coverage.gaps(10_000, done))
        assertEquals(listOf(0L until 10_000L), Coverage.gaps(10_000, emptyList()))
        assertTrue(Coverage.gaps(10_000, listOf(DoneRegion(0, 9_000, TranscriptionRoute.LOCAL, emptyList()))).isEmpty()) // a 1s tail isn't a gap
    }

    @Test fun plannerCoversOnlyGapsWithOverlapOnBothSides() {
        val chunks = GeminiChunkPlanner.planGaps(listOf(600_000L until 900_000L), total, ChunkPlanConfig())
        assertEquals(1, chunks.size)
        assertEquals(580_000L, chunks[0].startMs)
        assertEquals(920_000L, chunks[0].endMs)
    }

    @Test fun regionFileRoundTripsAndIgnoresCutOffLines() {
        val store = TranscriptionCheckpoints(dir)
        val fp = store.fingerprint(audio, total)
        store.append("m", fp, DoneRegion(0, 1000, TranscriptionRoute.GEMINI, listOf(w("hello, world", 0, 400, "SPEAKER 1"), w("again", 500, 900))))
        File(dir, "m.regions").appendText("LOCAL\t1000\t2000\t100,200,,cut")
        val back = store.load("m", fp)
        assertEquals(1, back.size)
        assertEquals("hello, world", back[0].words[0].text)
        assertEquals("SPEAKER 1", back[0].words[0].speakerId)
        assertTrue(store.load("m", "other-fingerprint").isEmpty())
    }

    @Test fun geminiFinishesAndSavesEveryPart() = runBlocking {
        val g = FakeGemini()
        val out = runner(g) { emptyList() }.run("m", audio, total, TranscriptionRoute.GEMINI)
        assertTrue(out.complete && out.usedGemini && !out.usedLocal)
        assertEquals(3, out.regions.size)
        assertTrue(out.words().size >= 59)
    }

    @Test fun aGeminiFailureContinuesOnThePhoneFromWhatWasDone() = runBlocking {
        val g = FakeGemini(failOnStartMs = 25 * 60_000L - 20_000L) // the second part fails
        val localGaps = mutableListOf<LongRange>()
        val out = runner(g) { gaps ->
            localGaps += gaps
            gaps.map { Triple(it.first, it.last + 1, listOf(w("local", it.first + 1000, it.first + 1500))) }
        }.run("m", audio, total, TranscriptionRoute.GEMINI)
        assertTrue(out.complete)
        assertTrue(out.usedGemini && out.usedLocal)
        assertTrue(out.geminiFailure!!.contains("Network lost"))
        // The phone only did the stretch Gemini hadn't finished, not the first part again.
        assertTrue(localGaps.all { it.first >= 24 * 60_000L })
    }

    @Test fun aRestartReusesWhatWasSavedAndSendsNoPartTwice() = runBlocking {
        val first = FakeGemini(failOnStartMs = 25 * 60_000L - 20_000L)
        runner(first) { throw RuntimeException("phone unavailable") }.run("m", audio, total, TranscriptionRoute.GEMINI)
        // Second run, a new engine, everything works.
        val second = FakeGemini()
        val out = runner(second) { emptyList() }.run("m", audio, total, TranscriptionRoute.GEMINI)
        assertTrue(out.complete)
        assertTrue("part one was sent again: ${second.requested}", second.requested.none { it.first == 0L })
    }

    @Test fun switchingToThePhoneMidRunHandsOverTheRest() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val g = FakeGemini(gate = gate) // Gemini never answers until released
        val ran = mutableListOf<LongRange>()
        val job = async {
            runner(g) { gaps -> ran += gaps; gaps.map { Triple(it.first, it.last + 1, listOf(w("local", it.first + 100, it.first + 400))) } }
                .run("m", audio, total, TranscriptionRoute.GEMINI)
        }
        delay(300)
        TranscriptionRoutes.request("m", TranscriptionRoute.LOCAL)
        val out = job.await()
        assertTrue(out.complete)
        assertFalse(out.usedGemini)
        assertEquals(listOf(0L until total), ran.take(1))
    }
}
