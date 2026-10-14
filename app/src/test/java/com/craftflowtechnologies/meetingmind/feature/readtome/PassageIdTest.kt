package com.craftflowtechnologies.meetingmind.feature.readtome

import com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.YouVersionScriptureProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassageIdTest {
    private val john = BibleBooks.byUsfm("JHN")!!

    @Test fun `a whole chapter id carries the translation and parses back to the chapter`() {
        val id = ReadToMe.passageId(ScriptureReference(john, 3), 3034)
        assertEquals("passage:3034:JHN.3", id)
        val rest = id.removePrefix("passage:")
        val parsed = YouVersionScriptureProvider.parsePassageId(rest.substringAfter(':'))!!
        assertEquals(john.usfm, parsed.book.usfm)
        assertEquals(3, parsed.chapter)
        assertNull(parsed.verseStart)
    }

    @Test fun `a verse range keeps its verses`() {
        val id = ReadToMe.passageId(ScriptureReference(john, 3, 16, 18), null)
        val parsed = YouVersionScriptureProvider.parsePassageId(id.substringAfter(':').substringAfter(':'))!!
        assertEquals(16, parsed.verseStart)
        assertEquals(18, parsed.verseEnd)
    }
}
