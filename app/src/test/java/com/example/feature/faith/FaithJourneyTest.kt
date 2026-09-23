package com.example.feature.faith

import com.example.core.model.Note
import com.example.core.model.NoteStatus
import com.example.core.model.RecordingType
import com.example.core.scripture.ChapterVerse
import com.example.feature.bible.highlight
import com.example.feature.bible.paragraphsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FaithJourneyTest {

    private fun at(year: Int, month: Int, day: Int) = Calendar.getInstance().apply { clear(); set(year, month, day, 10, 0) }.timeInMillis

    private fun note(id: String, type: RecordingType, time: Long, eventDate: Long? = null) = Note(
        id = id, title = id, workflow = type, notebookId = null, createdAt = time, updatedAt = time, eventDate = eventDate,
        pinned = false, isPrivate = true, status = NoteStatus.OPEN, answeredAt = null, metadata = emptyMap()
    )

    @Test
    fun `the journey groups by month, newest first, with counts per kind`() {
        val list = listOf(
            note("s1", RecordingType.SERMON, at(2026, Calendar.SEPTEMBER, 7)),
            note("s2", RecordingType.SERMON, at(2026, Calendar.SEPTEMBER, 14)),
            note("p1", RecordingType.PRAYER, at(2026, Calendar.SEPTEMBER, 20)),
            note("d1", RecordingType.DEVOTIONAL, at(2026, Calendar.AUGUST, 3)),
            // Filed later, but it happened in July: the event date wins.
            note("t1", RecordingType.TESTIMONY, at(2026, Calendar.SEPTEMBER, 1), eventDate = at(2026, Calendar.JULY, 30))
        )
        val journey = FaithViewModel.journeyOf(list)
        assertEquals(3, journey.size)
        assertEquals(mapOf(RecordingType.SERMON to 2, RecordingType.PRAYER to 1), journey[0].counts)
        assertEquals(listOf("d1"), journey[1].notes.map { it.id })
        assertEquals(listOf("t1"), journey[2].notes.map { it.id })
        assertTrue(journey[0].label.contains("2026"))
    }

    @Test
    fun `the reader keeps a translation's paragraphs and sets poetry line by line`() {
        val verses = listOf(
            ChapterVerse(1, "When Jesus saw the crowds", paragraph = true, poetry = false),
            ChapterVerse(2, "and He began to teach them", paragraph = false, poetry = false),
            ChapterVerse(3, "Blessed are the poor in spirit,\nfor theirs is the kingdom", paragraph = false, poetry = true),
            ChapterVerse(4, "Blessed are those who mourn,\nfor they will be comforted.", paragraph = false, poetry = true),
            ChapterVerse(11, "Blessed are you when people insult you", paragraph = true, poetry = false)
        )
        assertEquals(listOf(listOf(1, 2), listOf(3), listOf(4), listOf(11)), paragraphsOf(verses).map { p -> p.map { it.number } })
    }

    @Test
    fun `search results bold the words that matched`() {
        val text = highlight("Great is Your faithfulness", listOf("faith"))
        assertEquals("Great is Your faithfulness", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals("faithfulness", text.text.substring(text.spanStyles[0].start, text.spanStyles[0].end))
    }
}
