package com.craftflowtechnologies.meetingmind.core.faith

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyAndPassageTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        notes = NoteRepository(app, db)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private fun ref(s: String) = ScriptureReferenceParser.parse(s)!!

    @Test fun `a study note has the passage on top and the method's sections`() = runBlocking {
        val note = notes.startStudy(StudyTemplate.SOAP, ref("John 15:1-8"))
        val doc = notes.getDocument(note.id)!!
        assertEquals(RecordingType.BIBLE_STUDY, doc.note.workflow)
        assertEquals(NoteBlockType.SCRIPTURE, doc.blocks.sortedBy { it.position }.first().type)
        assertEquals(listOf("Scripture", "Observation", "Application", "Prayer"),
            doc.blocks.sortedBy { it.position }.filter { it.type == NoteBlockType.HEADING_2 }.map { it.content.text })
        assertEquals(1, doc.scriptureRefs.size)
    }

    @Test fun `notes on a passage count overlapping verses, not other verses or trashed notes`() = runBlocking {
        val soap = notes.startStudy(StudyTemplate.SOAP, ref("John 15:1-8"))
        notes.startStudy(StudyTemplate.WORD, ref("John 15:12-17"))
        val chapter = notes.startStudy(StudyTemplate.BOOK_OVERVIEW, ref("John 15"))
        val gone = notes.startStudy(StudyTemplate.INDUCTIVE, ref("John 15:5"))
        notes.moveToTrash(gone.id)
        val devo = notes.startDevotional(ref("John 15:4-6"))

        val links = notes.notesOnPassage(ref("John 15:5"))
        // A devotional you started by hand is one of your notes; generated daily ones are "Devotionals".
        assertEquals(setOf(soap.id, chapter.id, devo.id), links.myNotes.map { it.noteId }.toSet())
        assertTrue(links.sermons.isEmpty())
        // Asking from a note leaves that note out.
        assertTrue(notes.notesOnPassage(ref("John 15:5"), excludeNoteId = soap.id).myNotes.none { it.noteId == soap.id })
    }
}
