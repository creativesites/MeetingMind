package com.example.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.BlockSource
import com.example.core.model.MeetingSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteLinkKind
import com.example.core.model.NoteStatus
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var notes: NoteRepository
    private lateinit var meetings: MeetingRepository
    private var now = 1_000L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        notes = NoteRepository(context, database) { now }
        meetings = MeetingRepository(context, database)
    }

    @After
    fun tearDown() = database.close()

    private fun block(noteId: String, text: String, type: NoteBlockType = NoteBlockType.PARAGRAPH) =
        NoteBlock(id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = type, content = RichText.plain(text))

    @Test
    fun `a new note starts with an empty paragraph in My Notes`() = runBlocking {
        val note = notes.createNote(title = "Ideas")
        val doc = notes.getDocument(note.id)!!

        assertEquals(MeetMindDatabase.DEFAULT_NOTEBOOK_ID, doc.note.notebookId)
        assertEquals(listOf(NoteBlockType.PARAGRAPH), doc.blocks.map { it.type })
    }

    @Test
    fun `saving blocks keeps their order, styling and searchable text`() = runBlocking {
        val note = notes.createNote(title = "Study")
        val styled = RichText.plain("Faith is the substance").applyStyle(InlineStyle.BOLD, 0, 5)
        val blocks = listOf(
            block(note.id, "Hebrews 11", NoteBlockType.HEADING_1),
            block(note.id, "").copy(content = styled),
            block(note.id, "Pray daily", NoteBlockType.CHECKLIST).copy(checked = true),
            block(note.id, "", NoteBlockType.DIVIDER)
        )
        now = 2_000L
        notes.saveBlocks(note.id, blocks)

        val doc = notes.getDocument(note.id)!!
        assertEquals(listOf(0, 1, 2, 3), doc.blocks.map { it.position })
        assertEquals(styled, doc.blocks[1].content)
        assertTrue(doc.blocks[2].checked)
        assertEquals("Hebrews 11\nFaith is the substance\nPray daily", doc.note.plainText)
        assertEquals(2_000L, doc.note.updatedAt)
        assertEquals(listOf(note.id), notes.searchNotes("substance").map { it.id })
    }

    @Test
    fun `removing a block from the list deletes it`() = runBlocking {
        val note = notes.createNote()
        val a = block(note.id, "keep")
        val b = block(note.id, "drop")
        notes.saveBlocks(note.id, listOf(a, b))
        notes.saveBlocks(note.id, listOf(a))

        assertEquals(listOf("keep"), notes.getDocument(note.id)!!.blocks.map { it.content.text })
    }

    @Test
    fun `tags are case insensitive and unused tags disappear`() = runBlocking {
        val one = notes.createNote()
        val two = notes.createNote()
        val grace = notes.addTag(one.id, "#Grace")!!
        assertEquals(grace.id, notes.addTag(two.id, "grace")!!.id)
        assertNull(notes.addTag(one.id, "  # "))

        notes.removeTag(one.id, grace.id)
        assertEquals(1, notes.observeTags().first().size)
        notes.removeTag(two.id, grace.id)
        assertTrue(notes.observeTags().first().isEmpty())
    }

    @Test
    fun `a new recording gets its own note, and its title follows the recording until renamed`() = runBlocking {
        val meeting = meetings.createInitialMeeting(title = "Recording 1", source = MeetingSource.LOCAL_RECORDING, recordingType = RecordingType.LECTURE)
        val noteId = database.meetingDao().getMeetingById(meeting.id)!!.noteId!!
        assertEquals(RecordingType.LECTURE, notes.getNote(noteId)!!.workflow)
        val blocks = notes.getDocument(noteId)!!.blocks
        assertEquals(NoteBlockType.RECORDING, blocks.first().type)
        // A lecture's own sections follow the recording, ready to fill in.
        assertTrue(blocks.any { it.sectionKey == "my_notes" })

        meetings.updateMeetingTitle(meeting.id, "Grace that holds")
        assertEquals("Grace that holds", notes.getNote(noteId)!!.title)

        notes.renameNote(noteId, "My sermon notes")
        meetings.updateMeetingTitle(meeting.id, "Something else")
        assertEquals("My sermon notes", notes.getNote(noteId)!!.title)
    }

    @Test
    fun `recording into an existing note appends a recording block`() = runBlocking {
        val note = notes.createNote(title = "Bible study")
        val meeting = meetings.createInitialMeeting(title = "Part 2", source = MeetingSource.LOCAL_RECORDING, noteId = note.id)

        val doc = notes.getDocument(note.id)!!
        assertEquals(listOf(NoteBlockType.PARAGRAPH, NoteBlockType.RECORDING), doc.blocks.map { it.type })
        assertEquals(meeting.id, doc.blocks.last().payload[NoteBlock.PAYLOAD_MEETING_ID])
        assertEquals("Bible study", notes.getNote(note.id)!!.title)
    }

    @Test
    fun `deleting a note keeps its recordings and reports them`() = runBlocking {
        val meeting = meetings.createInitialMeeting(title = "Keep my audio", source = MeetingSource.LOCAL_RECORDING)
        val noteId = database.meetingDao().getMeetingById(meeting.id)!!.noteId!!

        val detached = notes.deleteNote(noteId)

        assertEquals(listOf(meeting.id), detached)
        assertNull(notes.getNote(noteId))
        val kept = database.meetingDao().getMeetingById(meeting.id)!!
        assertNull(kept.noteId)
    }

    @Test
    fun `deleting a notebook keeps its notes`() = runBlocking {
        val faith = notes.createNotebook("Faith", NotebookSpace.FAITH)
        val note = notes.createNote(notebookId = faith.id)

        notes.deleteNotebook(faith.id)

        val kept = notes.getNote(note.id)!!
        assertNull(kept.notebookId)
    }

    @Test
    fun `My Notes cannot be deleted and comes back if missing`() = runBlocking {
        val default = notes.ensureDefaultNotebook()
        notes.deleteNotebook(default.id)
        assertEquals(default.id, notes.getNotebook(default.id)!!.id)
    }

    @Test
    fun `each space gets one notebook, created on first use`() = runBlocking {
        val first = notes.ensureSpaceNotebook(NotebookSpace.FAITH)
        val second = notes.ensureSpaceNotebook(NotebookSpace.FAITH)
        assertEquals(first.id, second.id)
        assertEquals("Faith", first.name)
    }

    @Test
    fun `links and status record a prayer lifecycle`() = runBlocking {
        val request = notes.createNote(title = "Healing for Mum")
        val testimony = notes.createNote(title = "She recovered")

        now = 5_000L
        notes.setStatus(request.id, NoteStatus.ANSWERED)
        notes.link(testimony.id, request.id, NoteLinkKind.TESTIMONY_OF)

        val answered = notes.getNote(request.id)!!
        assertEquals(NoteStatus.ANSWERED, answered.status)
        assertEquals(5_000L, answered.answeredAt)
        assertEquals(NoteLinkKind.TESTIMONY_OF, notes.getLinks(request.id).single().kind)

        notes.deleteNote(testimony.id)
        assertTrue(notes.getLinks(request.id).isEmpty())
    }

    @Test
    fun `note link blocks produce backlinks`() = runBlocking {
        val target = notes.createNote(title = "Romans 8")
        val source = notes.createNote(title = "Sermon")
        notes.saveBlocks(
            source.id,
            listOf(
                NoteBlock(
                    id = "b1", noteId = source.id, position = 0, type = NoteBlockType.NOTE_LINK,
                    payload = mapOf(NoteBlock.PAYLOAD_NOTE_ID to target.id), source = BlockSource.USER
                )
            )
        )
        assertEquals(listOf(source.id), notes.observeBacklinks(target.id).first().map { it.id })
    }

    @Test
    fun `archived notes leave the library but stay retrievable`() = runBlocking {
        val note = notes.createNote(title = "Old")
        notes.archiveNote(note.id)
        assertFalse(notes.observeNotes().first().any { it.id == note.id })
        assertTrue(notes.observeArchivedNotes().first().any { it.id == note.id })
        notes.unarchiveNote(note.id)
        assertTrue(notes.observeNotes().first().any { it.id == note.id })
    }

    @Test
    fun `scripture collections keep their order`() = runBlocking {
        val c = notes.createScriptureCollection("Promises")
        notes.addToCollection(c.id, "ROM", 8, 28, null, null)
        notes.addToCollection(c.id, "JER", 29, 11, null, null)
        val items = notes.observeCollectionItems(c.id).first()
        assertEquals(listOf("ROM", "JER"), items.map { it.bookUsfm })
        assertEquals(listOf(0, 1), items.map { it.position })
    }

    @Test
    fun `a sermon note starts from its template, private sections marked, in the Faith notebook`() = runBlocking {
        val note = notes.createNote(workflow = RecordingType.SERMON, title = "Sunday")
        val doc = notes.getDocument(note.id)!!
        val headings = doc.blocks.filter { it.type == NoteBlockType.HEADING_2 }.map { it.content.text }
        assertEquals("Scripture", headings.first())
        assertTrue("My prayer" in headings)
        assertEquals("Faith", notes.getNotebook(doc.note.notebookId!!)!!.name)
        assertFalse(doc.note.isPrivate)
        assertTrue(notes.createNote(workflow = RecordingType.PRAYER).isPrivate)
    }

    @Test
    fun `a recorded sermon's note gets only the person's own sections`() = runBlocking {
        val meeting = meetings.createInitialMeeting(title = "Sermon", source = MeetingSource.LOCAL_RECORDING, recordingType = RecordingType.SERMON)
        val noteId = database.meetingDao().getMeetingById(meeting.id)!!.noteId!!
        val keys = notes.getDocument(noteId)!!.blocks.mapNotNull { it.sectionKey }.toSet()
        assertTrue("my_notes" in keys)
        assertFalse("key_points" in keys)
    }
}
