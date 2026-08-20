package com.example.ai.llm

import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptChunkerTest {

    private fun segment(id: String, textLength: Int) =
        TranscriptSegment(id = id, meetingId = "m1", startMs = 0L, endMs = 1000L, text = "x".repeat(textLength))

    @Test
    fun `empty transcript produces no chunks`() {
        assertTrue(TranscriptChunker.chunk(emptyList(), contextLengthTokens = 4096).isEmpty())
    }

    @Test
    fun `a short transcript fits in a single chunk`() {
        val segments = listOf(segment("s1", 100), segment("s2", 100))

        val chunks = TranscriptChunker.chunk(segments, contextLengthTokens = 4096)

        assertEquals(1, chunks.size)
        assertEquals(2, chunks[0].segments.size)
    }

    @Test
    fun `a transcript exceeding one chunk's budget is split across multiple chunks`() {
        // Each segment ~500 chars; a small context budget forces multiple chunks.
        val segments = (1..20).map { segment("s$it", 500) }

        val chunks = TranscriptChunker.chunk(segments, contextLengthTokens = 1500)

        assertTrue("Expected more than one chunk for a transcript this long relative to the budget", chunks.size > 1)
    }

    @Test
    fun `chunking never splits a single transcript segment across chunks`() {
        val segments = (1..20).map { segment("s$it", 500) }

        val chunks = TranscriptChunker.chunk(segments, contextLengthTokens = 1500)

        val allSegmentIdsFromChunks = chunks.flatMap { it.segments.map { s -> s.id } }
        assertEquals(segments.map { it.id }, allSegmentIdsFromChunks)
        // Every original segment appears in exactly one chunk (chunk membership partitions cleanly).
        assertEquals(segments.size, allSegmentIdsFromChunks.toSet().size)
    }

    @Test
    fun `chunk indices are sequential starting at zero`() {
        val segments = (1..20).map { segment("s$it", 500) }

        val chunks = TranscriptChunker.chunk(segments, contextLengthTokens = 1500)

        assertEquals(chunks.indices.toList(), chunks.map { it.chunkIndex })
    }

    @Test
    fun `a larger real model context length produces fewer chunks for the same transcript`() {
        val segments = (1..20).map { segment("s$it", 500) }

        val smallContextChunks = TranscriptChunker.chunk(segments, contextLengthTokens = 1500)
        val largeContextChunks = TranscriptChunker.chunk(segments, contextLengthTokens = 8192)

        assertTrue(largeContextChunks.size <= smallContextChunks.size)
    }
}
