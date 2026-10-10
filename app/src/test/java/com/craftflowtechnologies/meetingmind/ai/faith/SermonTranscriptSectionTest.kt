package com.craftflowtechnologies.meetingmind.ai.faith

import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SermonTranscriptSectionTest {
    private fun seg(id: String, start: Long, text: String, speaker: String) =
        TranscriptSegment(id = id, meetingId = "m", speakerId = speaker, startMs = start, endMs = start + 4000, text = text)

    private val speakers = listOf(Speaker("pastor", "m", 0, "Speaker 1", "Pastor Sam", "#000"), Speaker("reader", "m", 1, "Speaker 2", "Reader", "#111"))
    private val spoken = listOf(
        seg("a", 0, "Open your Bibles to John three. For God so loved the world.", "pastor"),
        seg("b", 5000, "John three sixteen.", "reader"),
        seg("c", 10000, "Thank you. That is the heart of the gospel.", "pastor")
    )

    @Test
    fun `the transcript is a headed section at the end, split by speaker`() {
        val g = SermonNoteBuilder.build("n", "m", spoken, null, emptyList(), spoken = spoken, speakers = speakers)
        val key = SermonNoteBuilder.KEY_TRANSCRIPT
        assertEquals(setOf(key), g.endKeys)
        assertTrue(key in g.keys)
        val section = g.blocks.filter { it.sectionKey == key }
        assertEquals(g.blocks.takeLast(section.size), section)
        assertEquals(NoteBlockType.HEADING_2, section.first().type)
        assertEquals("Transcript", section.first().content.text)
        assertEquals(listOf("Pastor Sam", "Reader", "Pastor Sam"), section.drop(1).map { it.payload["speaker"] })
        assertTrue(section.drop(1).all { it.type == NoteBlockType.TRANSCRIPT_EXCERPT && it.sourceSegmentIds.isNotEmpty() })
    }

    @Test
    fun `songs left out of the spoken segments are not in the transcript`() {
        val song = seg("song", 20000, "Amazing grace how sweet the sound", "pastor")
        val g = SermonNoteBuilder.build("n", "m", spoken + song, null, emptyList(), spoken = spoken, speakers = speakers)
        assertTrue(g.blocks.none { "Amazing grace" in it.content.text })
    }

    @Test
    fun `no speech writes no transcript section`() {
        val g = SermonNoteBuilder.build("n", "m", spoken, null, emptyList(), speakers = speakers)
        assertTrue(g.blocks.none { it.sectionKey == SermonNoteBuilder.KEY_TRANSCRIPT })
    }
}
