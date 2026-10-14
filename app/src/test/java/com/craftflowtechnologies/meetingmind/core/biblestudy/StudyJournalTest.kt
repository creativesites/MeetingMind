package com.craftflowtechnologies.meetingmind.core.biblestudy

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyJournalTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository
    private val passage = ScriptureReferenceParser.parse("John 15:1-8")!!

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        notes = NoteRepository(app, db)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private fun fresh(kind: SessionKind = SessionKind.TODAY) = SessionState(kind, SessionPlan.steps(kind))
    private fun journalNotes() = runBlocking { StudyJournal(notes).observe().first() }

    @Test fun `an untouched session leaves nothing behind`() = runBlocking {
        val session = JournalSession(notes, passage, SessionKind.TODAY)
        // Walking through every step without writing, and writing only spaces, create no note.
        var s = fresh()
        repeat(8) { s = s.next(); session.onChanged(s) }
        session.onChanged(s.answer("q1", "   "))
        assertNull(session.noteId)
        assertTrue(journalNotes().isEmpty())
        assertTrue(notes.observeNotes().first().isEmpty())
    }

    @Test fun `the first keystroke creates the entry with the words already in it, never a blank note`() = runBlocking {
        val session = JournalSession(notes, passage, SessionKind.SOAP, need = null)
        val s = fresh(SessionKind.SOAP).withPrompt("observation", "What do you notice?").answer("observation", "T")
        session.onChanged(s)
        assertNotNull(session.noteId)
        val id = session.noteId!!
        val doc = notes.getDocument(id)!!
        assertEquals(RecordingType.BIBLE_STUDY, doc.note.workflow)
        assertEquals("John 15:1–8 · SOAP", doc.note.title)
        assertEquals(passage.passageId(), doc.note.metadata[JournalMeta.PASSAGE_REF])
        assertEquals("soap", doc.note.metadata[JournalMeta.SESSION_KIND])
        assertFalse("not a draft", doc.note.metadata.containsKey("draft"))
        val blocks = doc.blocks.sortedBy { it.position }
        assertEquals(NoteBlockType.SCRIPTURE, blocks.first().type)
        assertEquals(listOf("What do you notice?", "T"), blocks.drop(1).map { it.content.text })
        assertTrue("no empty paragraph", blocks.none { it.type == NoteBlockType.PARAGRAPH && it.content.text.isBlank() })
        assertEquals(1, doc.scriptureRefs.size)
        assertEquals(1, journalNotes().size)
    }

    @Test fun `more typing keeps updating the same entry`() = runBlocking {
        val session = JournalSession(notes, passage, SessionKind.TODAY, Need.TIRED)
        var s = fresh().answer("q1", "A")
        session.onChanged(s)
        val id = session.noteId!!
        s = s.answer("q1", "A vine and its branches").answer("prayer", "Abide in me")
        session.onChanged(s)
        assertEquals(id, session.noteId)
        assertEquals(1, journalNotes().size)
        val doc = notes.getDocument(id)!!
        assertEquals("tired", doc.note.metadata[JournalMeta.NEED])
        assertTrue(doc.blocks.any { it.content.text == "A vine and its branches" })
        assertTrue(doc.blocks.any { it.content.text == "Abide in me" })
        assertEquals("one reference, not one per save", 1, doc.scriptureRefs.size)
        // Clearing everything leaves the entry the person already made (nothing is deleted behind their back).
        session.onChanged(s.answer("q1", "").answer("prayer", ""))
        assertEquals(1, journalNotes().size)
    }

    @Test fun `the entry shows on the passage next time`() = runBlocking {
        val session = JournalSession(notes, passage, SessionKind.TODAY)
        session.onChanged(fresh().answer("q1", "Fruit"))
        val here = StudyJournal(notes).onPassage(ScriptureReferenceParser.parse("John 15")!!)
        assertEquals(1, here.size)
        assertTrue(StudyJournal(notes).onPassage(ScriptureReferenceParser.parse("John 3")!!).isEmpty())
    }

    @Test fun `add to an existing note appends the answers as blocks`() = runBlocking {
        val sermon = notes.createNote(workflow = RecordingType.SERMON, title = "Sunday: the vine")
        val before = notes.getDocument(sermon.id)!!.blocks.size
        val session = JournalSession(notes, passage, SessionKind.TODAY)
        val s = fresh().withPrompt("q1", "What stands out?").answer("q1", "Remaining").answer("live", "Call Sam")
        assertTrue(session.addToExisting(sermon.id, s))
        val doc = notes.getDocument(sermon.id)!!
        val texts = doc.blocks.sortedBy { it.position }.map { it.content.text }
        assertTrue(doc.blocks.size > before)
        assertTrue(texts.contains("Bible study · John 15:1–8"))
        assertEquals(listOf("What stands out?", "Remaining", "Live it today", "Call Sam"), texts.takeLast(4))
        assertEquals("the existing note is still a sermon", RecordingType.SERMON, doc.note.workflow)
        // Nothing written: nothing to add.
        assertFalse(JournalSession(notes, passage, SessionKind.TODAY).addToExisting(sermon.id, fresh()))
    }
}
