package com.example.core.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.identity.AppIdentity
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.notes.RichText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteDraftTest {

    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MeetMindDatabase::class.java).allowMainThreadQueries().build()
        notes = NoteRepository(ApplicationProvider.getApplicationContext(), db)
    }
    @After fun tearDown() = db.close()

    @Test
    fun `a blank template note has no content, one typed word does`() = runBlocking {
        val devotional = notes.createNote(RecordingType.DEVOTIONAL, draft = true)
        val blocks = notes.getDocument(devotional.id)!!.blocks
        assertTrue(blocks.any { it.type == NoteBlockType.HEADING_2 }) // template headings are there…
        assertFalse(NoteContent.hasUserContent(devotional, blocks))   // …but they aren't content

        val body = blocks.first { it.type != NoteBlockType.HEADING_2 }
        val typed = blocks.map { if (it.id == body.id) it.copy(content = RichText.plain("Grateful today")) else it }
        assertTrue(NoteContent.hasUserContent(devotional, typed))

        // Renaming a heading is the person's own writing too.
        val renamed = blocks.map { if (it.type == NoteBlockType.HEADING_2) it.copy(content = RichText.plain("My own heading")) else it }
        assertTrue(NoteContent.hasUserContent(devotional, renamed))
    }

    @Test
    fun `an event's own title isn't content, but a new title is`() = runBlocking {
        val note = notes.createNote(title = "Design review", draft = true)
        assertFalse(NoteContent.hasUserContent(note, emptyList()))
        assertTrue(NoteContent.hasUserContent(note.copy(title = "Design review — follow-up"), emptyList()))
        val scripture = NoteBlock("s", note.id, 0, NoteBlockType.SCRIPTURE, RichText.plain("John 3:16"))
        assertTrue(NoteContent.hasUserContent(note, listOf(scripture)))
        val divider = NoteBlock("d", note.id, 0, NoteBlockType.DIVIDER)
        assertFalse(NoteContent.hasUserContent(note, listOf(divider)))
    }

    @Test
    fun `drafts stay out of every list until kept`() = runBlocking {
        val draft = notes.createNote(title = "", draft = true)
        val real = notes.createNote(title = "Kept")
        assertEquals(listOf(real.id), notes.observeNotes().first().map { it.id })
        assertFalse(notes.hasUserContent(draft.id))

        notes.saveBlocks(draft.id, listOf(NoteBlock("p", draft.id, 0, NoteBlockType.PARAGRAPH, RichText.plain("Hello"))))
        assertTrue(notes.hasUserContent(draft.id))
        notes.keepDraft(draft.id)
        assertEquals(setOf(real.id, draft.id), notes.observeNotes().first().map { it.id }.toSet())
        assertFalse(NoteContent.isDraft(notes.getNote(draft.id)!!))
    }

    @Test
    fun `a tag alone keeps a draft`() = runBlocking {
        val draft = notes.createNote(draft = true)
        notes.addTag(draft.id, "ideas")
        assertTrue(notes.hasUserContent(draft.id))
    }

    @Test
    fun `identity decides which spaces and types show`() {
        val work = AppIdentity(spaces = setOf(NotebookSpace.WORK))
        assertFalse(work.showsFaith)
        assertFalse(work.allows(RecordingType.SERMON))
        assertTrue(work.allows(RecordingType.MEETING))
        val faith = AppIdentity(spaces = setOf(NotebookSpace.FAITH, NotebookSpace.PERSONAL), displayName = "Ana  Banda")
        assertTrue(faith.faithFirst)
        assertEquals("Ana", faith.firstName)
        assertEquals("AB", faith.initials)
        assertEquals(NotebookSpace.entries.toSet(), AppIdentity.parseSpaces(null))
        assertEquals(setOf(NotebookSpace.WORK), AppIdentity.parseSpaces(setOf("WORK", "NOPE")))
    }
}
