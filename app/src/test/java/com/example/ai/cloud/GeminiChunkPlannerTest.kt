package com.example.ai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiChunkPlannerTest {

    private val config = ChunkPlanConfig()

    @Test
    fun `a recording that fits in one chunk is not split`() {
        val chunks = GeminiChunkPlanner.plan(5 * 60 * 1_000L, config)

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(5 * 60 * 1_000L, chunks[0].endMs)
    }

    @Test
    fun `consecutive chunks always overlap - concatenation is never an option`() {
        val chunks = GeminiChunkPlanner.plan(45 * 60 * 1_000L, config)

        assertTrue(chunks.size > 4)
        for (i in 0 until chunks.size - 1) {
            val overlap = GeminiChunkPlanner.overlapBetween(chunks[i], chunks[i + 1])
            assertTrue("chunks $i and ${i + 1} must share audio", overlap != null)
            assertEquals(config.overlapMs, overlap!!.last - overlap.first)
        }
    }

    @Test
    fun `the plan covers the whole recording with no gaps`() {
        val totalMs = 73 * 60 * 1_000L

        val chunks = GeminiChunkPlanner.plan(totalMs, config)

        assertEquals(0L, chunks.first().startMs)
        assertEquals(totalMs, chunks.last().endMs)
        for (i in 0 until chunks.size - 1) {
            assertTrue("gap before chunk ${i + 1}", chunks[i + 1].startMs <= chunks[i].endMs)
        }
    }

    @Test
    fun `no chunk exceeds the duration ceiling the verbatim pass has to respect`() {
        val chunks = GeminiChunkPlanner.plan(2 * 60 * 60 * 1_000L, config)

        assertTrue(chunks.all { it.durationMs <= config.maxChunkMs })
    }

    @Test
    fun `a short tail is folded back rather than uploaded as a few seconds of audio`() {
        // Diarization on a 10-second tail chunk says nothing useful, and its speakers would then
        // be unresolvable against the rest of the recording.
        val totalMs = config.maxChunkMs + (config.maxChunkMs - config.overlapMs) + 10_000L

        val chunks = GeminiChunkPlanner.plan(totalMs, config)

        assertTrue(chunks.all { it.durationMs >= config.minTailChunkMs })
        assertEquals(totalMs, chunks.last().endMs)
    }

    @Test
    fun `chunks are indexed in order`() {
        val chunks = GeminiChunkPlanner.plan(40 * 60 * 1_000L, config)

        assertEquals(chunks.indices.toList(), chunks.map { it.index })
        assertEquals(chunks.sortedBy { it.startMs }, chunks)
    }

    @Test
    fun `an empty recording produces no chunks`() {
        assertTrue(GeminiChunkPlanner.plan(0L, config).isEmpty())
        assertTrue(GeminiChunkPlanner.plan(-1L, config).isEmpty())
    }

    @Test
    fun `non-overlapping chunks report no overlap`() {
        val a = AudioChunk(0, 0L, 1_000L)
        val b = AudioChunk(1, 5_000L, 6_000L)

        assertNull(GeminiChunkPlanner.overlapBetween(a, b))
    }

    @Test
    fun `planning is deterministic`() {
        val totalMs = 37 * 60 * 1_000L

        assertEquals(GeminiChunkPlanner.plan(totalMs, config), GeminiChunkPlanner.plan(totalMs, config))
    }
}
