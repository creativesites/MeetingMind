package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The seed jar's queries and actions on an in-memory database. The clock is pinned to Monday
 * 16 November 2026, 10:00 UTC.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedJarRepositoryTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var context: Context
    private lateinit var notes: NoteRepository
    private lateinit var tasks: TaskRepository
    private lateinit var repo: SeedJarRepository
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_794_823_200_000L // 2026-11-16T10:00:00Z

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        notes = NoteRepository(context, db, clock = { now })
        tasks = TaskRepository(db.taskDao(), db.peopleDao(), clock = { now }, notebooks = db.notebookDao())
        repo = SeedJarRepository(context, db, clock = { now }, notes = notes, tasks = tasks)
    }

    @After fun tearDown() {
        db.close()
    }

    /** A note created at [at], so its age is right for the 60-day window. */
    private suspend fun noteAt(at: Long, workflow: RecordingType, title: String = "", metadata: Map<String, String> = emptyMap()): Note =
        NoteRepository(context, db, clock = { at }).createNote(
            workflow = workflow, title = title, metadata = metadata, useTemplate = false
        )

    @Test fun `to sort is Personal quick notes from the last 60 days that nobody has filed`() = runBlocking {
        val fresh = noteAt(now, RecordingType.GENERAL, "Quick thought")
        val memo = noteAt(now - day, RecordingType.VOICE_MEMO, "Voice thing")
        noteAt(now - 61 * day, RecordingType.VOICE_MEMO, "Too old")
        noteAt(now, RecordingType.IDEA, "An idea")
        // A conversation has no Faith notebook, so it lands in My Notes (Personal) and belongs in To sort.
        val talk = noteAt(now - 2 * day, RecordingType.CONVERSATION, "Conversation in My Notes")
        noteAt(now, RecordingType.GENERAL, "Already sorted", metadata = mapOf(SeedJarRepository.SORTED_KEY to "1"))
        val archived = noteAt(now, RecordingType.DICTATION, "Archived")
        notes.archiveNote(archived.id)

        val ids = repo.observeToSort().first().map { it.id }

        assertEquals(listOf(fresh.id, memo.id, talk.id), ids)
    }

    @Test fun `to sort leaves out a note whose notebook is in another space`() = runBlocking {
        val work = notes.ensureSpaceNotebook(NotebookSpace.WORK)
        val note = noteAt(now, RecordingType.GENERAL, "Filed in Work")
        notes.moveToNotebook(note.id, work.id)

        assertEquals(emptyList<String>(), repo.observeToSort().first().map { it.id })
    }

    @Test fun `ideas are newest first and leave out archived ones`() = runBlocking {
        val older = noteAt(now - 2 * day, RecordingType.IDEA, "Older idea")
        val newer = noteAt(now, RecordingType.IDEA, "Newer idea")
        val gone = noteAt(now, RecordingType.IDEA, "Archived idea")
        notes.archiveNote(gone.id)

        assertEquals(listOf(newer.id, older.id), repo.observeIdeas().first().map { it.id })
    }

    @Test fun `moving a note files it into its space and takes it off To sort`() = runBlocking {
        val note = noteAt(now, RecordingType.GENERAL, "Budget for the client")

        repo.moveTo(note.id, NotebookSpace.WORK)

        val moved = notes.getNote(note.id)!!
        assertEquals(NotebookSpace.WORK, notes.getNotebook(moved.notebookId!!)!!.space)
        assertEquals(emptyList<String>(), repo.observeToSort().first().map { it.id })
    }

    @Test fun `keeping a note here marks it sorted and takes it off To sort`() = runBlocking {
        val note = noteAt(now, RecordingType.VOICE_MEMO, "Stays put")

        repo.keepHere(note.id)

        assertEquals("1", notes.getNote(note.id)!!.metadata[SeedJarRepository.SORTED_KEY])
        assertEquals(emptyList<String>(), repo.observeToSort().first().map { it.id })
    }

    @Test fun `making a task links it to the note in Personal and takes the note off To sort`() = runBlocking {
        val note = noteAt(now, RecordingType.GENERAL, "Renew the passport")

        repo.makeTask(note.id)

        val task = tasks.observeTasks().first().single()
        assertEquals("Renew the passport", task.title)
        assertEquals(note.id, task.noteId)
        assertEquals("PERSONAL", task.space)
        assertEquals("1", notes.getNote(note.id)!!.metadata[SeedJarRepository.SORTED_KEY])
        assertEquals(emptyList<String>(), repo.observeToSort().first().map { it.id })
    }

    @Test fun `a note without a title is named by its first 80 characters`() {
        val text = "Ring the council about the bins on Thursday morning before the crew arrives at nine"
        val note = Note(
            id = "n", title = "", workflow = RecordingType.VOICE_MEMO, notebookId = null, createdAt = now, updatedAt = now,
            eventDate = null, pinned = false, isPrivate = false, status = NoteStatus.OPEN, answeredAt = null,
            metadata = emptyMap(), plainText = text
        )

        assertEquals(text.take(80).trim(), SeedJarRepository.titleOf(note, untitled = "Untitled"))
        assertEquals("Untitled", SeedJarRepository.titleOf(note.copy(plainText = "  "), untitled = "Untitled"))
    }
}
