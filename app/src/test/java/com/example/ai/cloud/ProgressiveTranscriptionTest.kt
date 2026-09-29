package com.example.ai.cloud

import com.example.ai.common.AiResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressiveTranscriptionTest {

    /** Answers each part with three timed words; slower for the first part, to test ordering. */
    private class PartTransport(val failPart: Int? = null) : GeminiTransport {
        val inFlight = AtomicInteger(0)
        var maxInFlight = 0
        override fun isConfigured() = true
        override suspend fun refreshConfigured() = true
        override suspend fun execute(request: GeminiRequest): AiResult<String> {
            val now = inFlight.incrementAndGet()
            synchronized(this) { maxInFlight = maxOf(maxInFlight, now) }
            try {
                val part = (request.audioStartMs / 60_000).toInt()
                delay(if (part == 0) 200 else 50)
                if (part == failPart) return AiResult.Failed("quota")
                val words = (0 until 3).joinToString(",") { w ->
                    val s = 1 + w * 0.5
                    """{"word":"part$part-w$w","startOffset":"${s}s","endOffset":"${s + 0.4}s"}"""
                }
                return AiResult.Success("""{"turns":[{"speakerLabel":"spk_1","words":[$words]}],"text":""}""")
            } finally { inFlight.decrementAndGet() }
        }
    }

    private val file = java.io.File.createTempFile("rec", ".m4a")

    @Test fun `parts run two at a time and each finished prefix is handed on in order`() = runBlocking {
        val t = PartTransport()
        val partials = mutableListOf<Pair<Int, Int>>()
        val r = GeminiTranscriptionEngine(t, chunkConfig = ChunkPlanConfig(maxChunkMs = 60_000, overlapMs = 0, minTailChunkMs = 10_000))
            .transcribe(file, 4 * 60_000L, onPartial = { words, done, _ -> partials += done to words.size })
        assertTrue(r is AiResult.Success)
        assertEquals(2, t.maxInFlight)
        assertEquals(listOf(1, 2, 3), partials.map { it.first })
        // Each partial holds exactly the parts done so far, in order.
        assertEquals(listOf(3, 6, 9), partials.map { it.second })
        assertEquals(12, (r as AiResult.Success).value.words.size)
        assertEquals("part0-w0", r.value.words.first().text)
    }

    @Test fun `a failed part fails the run, saying which part`() = runBlocking {
        val r = GeminiTranscriptionEngine(PartTransport(failPart = 2), chunkConfig = ChunkPlanConfig(maxChunkMs = 60_000, overlapMs = 0, minTailChunkMs = 10_000))
            .transcribe(file, 4 * 60_000L)
        assertTrue((r as AiResult.Failed).message.contains("part 3 of 4"))
    }
}
