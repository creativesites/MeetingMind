package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageSpeechTest {
    private val john = BibleBooks.byUsfm("JHN")!!
    private fun verses(n: Int) = (1..n).map { it to "verse $it text" }

    @Test fun `a short passage is one part per verse`() {
        assertEquals((1..5).map { listOf(it) }, PassageSpeech.groups((1..5).toList()))
    }

    @Test fun `a long chapter is grouped by three and every verse appears once in order`() {
        val all = (1..31).toList()
        val groups = PassageSpeech.groups(all)
        assertTrue(groups.all { it.size <= PassageSpeech.GROUP_SIZE })
        assertEquals(all, groups.flatten())
        assertEquals(1, groups.first().first())
    }

    @Test fun `sections keep each verse as its own unit, with the label before the first one only`() {
        val sections = PassageSpeech.sections(verses(2), "John chapter 3, verses 1 to 2", "b1", "John")
        assertEquals(2, sections.size)
        assertEquals("v1", sections[0].id)
        assertEquals("John 1", sections[0].title)
        assertEquals("John chapter 3, verses 1 to 2", sections[0].units.single().lead)
        assertNull(sections[1].units.single().lead)
        assertEquals("verse 2 text", sections[1].units.single().text)
    }

    @Test fun `a grouped part is titled with its range and keeps every verse`() {
        val sections = PassageSpeech.sections(verses(16), "John chapter 3", "b1", "John")
        assertEquals(16, sections.sumOf { it.units.size })
        assertTrue(sections.any { it.title == "John 1–3" })
    }

    @Test fun `firstVerse reads the number from the part id`() {
        assertEquals(16, PassageSpeech.firstVerse("v16"))
        assertEquals(16, PassageSpeech.firstVerse("v16-18"))
        assertNull(PassageSpeech.firstVerse("sec"))
        assertNull(PassageSpeech.firstVerse(null))
    }

    @Test fun `reading from a verse starts at the part that contains it`() {
        val sections = PassageSpeech.sections(verses(20), "John chapter 3", "b1", "John")
        val from = PassageSpeech.fromVerse(sections, 17)
        assertEquals(16, PassageSpeech.firstVerse(from.first().id))
        assertEquals(sections.takeLast(from.size), from)
    }

    @Test fun `no verse or a verse before the first part keeps everything`() {
        val sections = PassageSpeech.sections(verses(3), "John chapter 3", "b1", "John")
        assertEquals(sections, PassageSpeech.fromVerse(sections, null))
        assertEquals(sections, PassageSpeech.fromVerse(sections, 1))
    }

    @Test fun `the passage id is the translation and the chapter`() {
        assertEquals("passage:3034:JHN.3", PassageSpeech.passageId(ScriptureReference(john, 3), 3034))
        assertEquals("passage:0:JHN.3", PassageSpeech.passageId(ScriptureReference(john, 3), null))
    }
}
