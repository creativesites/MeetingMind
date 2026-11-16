package com.craftflowtechnologies.meetingmind.core.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentReadStateTest {

    @Test
    fun rowsMapToStates() {
        assertEquals(DocumentReadState.NotStarted, DocumentReadState.from(null, null, 0))
        assertEquals(DocumentReadState.NotStarted, DocumentReadState.from("NONE", null, 0))
        assertEquals(DocumentReadState.NotStarted, DocumentReadState.from("something unknown", null, 0))
        assertEquals(DocumentReadState.Pending, DocumentReadState.from("PENDING", null, 0))
        assertEquals(DocumentReadState.Reading(null, 0), DocumentReadState.from("READING", null, 0))
        assertEquals(DocumentReadState.Reading(12, 5), DocumentReadState.from("READING", 12, 5))
        assertEquals(DocumentReadState.Locked, DocumentReadState.from("LOCKED", null, 0))
        assertEquals(DocumentReadState.Failed, DocumentReadState.from("FAILED", null, 0))
        assertEquals(DocumentReadState.NoText(3), DocumentReadState.from("NO_TEXT", 3, 3))
    }

    @Test
    fun doneWithAllPagesIsNotPartial() {
        val done = DocumentReadState.from("DONE", 12, 12) as DocumentReadState.Done
        assertFalse(done.partial)
        assertNull(done.message())
    }

    @Test
    fun doneOverTheLimitIsPartialAndSaysSo() {
        val done = DocumentReadState.from("DONE", 412, 300) as DocumentReadState.Done
        assertTrue(done.partial)
        assertEquals("Read the first 300 of 412 pages. The rest was not read.", done.message())
    }

    @Test
    fun doneWithoutAPageCountUsesThePagesRead() {
        val done = DocumentReadState.from("DONE", null, 4) as DocumentReadState.Done
        assertEquals(4, done.pageCount)
        assertFalse(done.partial)
    }

    @Test
    fun problemStatesHavePlainMessages() {
        assertEquals(
            "Can't read a locked PDF. Open it in another app, or file it as is.",
            DocumentReadState.Locked.message()
        )
        assertEquals("No readable text. Filed by name.", DocumentReadState.NoText(2).message())
        assertEquals("This file couldn't be read. Filed by name.", DocumentReadState.Failed.message())
    }

    @Test
    fun inProgressStatesSayNothingYet() {
        assertNull(DocumentReadState.Pending.message())
        assertNull(DocumentReadState.Reading(10, 3).message())
        assertNull(DocumentReadState.NotStarted.message())
    }

    @Test
    fun readStatusParsesStoredNamesAndDefaultsToNone() {
        assertEquals(ReadStatus.DONE, ReadStatus.parse("DONE"))
        assertEquals(ReadStatus.NO_TEXT, ReadStatus.parse("NO_TEXT"))
        assertEquals(ReadStatus.NONE, ReadStatus.parse(null))
        assertEquals(ReadStatus.NONE, ReadStatus.parse("done"))
    }

    @Test
    fun onlyPdfAndImageItemsAreRead() {
        assertEquals(DocumentKind.PDF, DocumentKind.fromItemKind("PDF"))
        assertEquals(DocumentKind.IMAGE, DocumentKind.fromItemKind("IMAGE"))
        assertNull(DocumentKind.fromItemKind("TEXT"))
        assertNull(DocumentKind.fromItemKind("AUDIO"))
        assertNull(DocumentKind.fromItemKind(null))
    }
}
