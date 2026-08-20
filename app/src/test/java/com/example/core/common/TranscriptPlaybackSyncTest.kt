package com.example.core.common

import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptPlaybackSyncTest {

    private val segments = listOf(
        TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0L, endMs = 2000L, text = "First"),
        TranscriptSegment(id = "s2", meetingId = "m1", startMs = 2000L, endMs = 5000L, text = "Second"),
        TranscriptSegment(id = "s3", meetingId = "m1", startMs = 5000L, endMs = 9000L, text = "Third")
    )

    @Test
    fun `position inside the first segment selects the first segment`() {
        assertEquals("s1", findActiveTranscriptSegment(segments, 500L)?.id)
    }

    @Test
    fun `position exactly on a segment's start boundary selects that segment, not the previous one`() {
        assertEquals("s2", findActiveTranscriptSegment(segments, 2000L)?.id)
    }

    @Test
    fun `position mid-way through the last segment selects the last segment`() {
        assertEquals("s3", findActiveTranscriptSegment(segments, 7000L)?.id)
    }

    @Test
    fun `position past the end of the last segment still selects the last segment, not null`() {
        // Audio commonly runs slightly longer than the last transcribed segment (trailing
        // silence, VAD cutoff) — the last thing that was said should stay highlighted.
        assertEquals("s3", findActiveTranscriptSegment(segments, 999_000L)?.id)
    }

    @Test
    fun `position before the first segment starts returns null, never a guessed segment`() {
        assertNull(findActiveTranscriptSegment(segments, -1L))
    }

    @Test
    fun `no segments at all returns null`() {
        assertNull(findActiveTranscriptSegment(emptyList(), 5000L))
    }

    @Test
    fun `a single segment is selected for any position at or after its start`() {
        val single = listOf(TranscriptSegment(id = "only", meetingId = "m1", startMs = 1000L, endMs = 3000L, text = "Only"))
        assertNull(findActiveTranscriptSegment(single, 500L))
        assertEquals("only", findActiveTranscriptSegment(single, 1000L)?.id)
        assertEquals("only", findActiveTranscriptSegment(single, 50_000L)?.id)
    }
}
