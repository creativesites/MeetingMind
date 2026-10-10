package com.craftflowtechnologies.meetingmind.feature.notes

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** W-3: Work notes, meeting notes included, belong in the Notes library's All view and under its Work filter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesWorkSpaceTest {

    private lateinit var app: Application
    private lateinit var database: MeetMindDatabase
    private lateinit var notes: NoteRepository

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
            .setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(2)).build()
        MeetMindDatabase.setInstanceForTest(database)
        notes = NoteRepository(app, database)
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        database.close()
    }

    /** The view model publishes on the main looper, so pump it until the list settles. */
    private fun <T> awaitUntil(read: () -> T, done: (T) -> Boolean): T {
        val deadline = System.currentTimeMillis() + 20_000
        while (true) {
            ShadowLooper.idleMainLooper()
            val value = read()
            if (done(value) || System.currentTimeMillis() > deadline) return value
            Thread.sleep(20)
        }
    }

    @Test
    fun work_and_meeting_notes_show_in_All_and_under_the_Work_filter() = runBlocking {
        val project = notes.createNotebook("Launch", NotebookSpace.WORK, isProject = true)
        // A meeting with no project lands in "My Notes" (a personal notebook), but it is still Work.
        notes.createNote(workflow = RecordingType.MEETING, title = "Standup with Ana")
        notes.createNote(workflow = RecordingType.GENERAL, title = "Launch risks", notebookId = project.id)
        notes.createNote(title = "Groceries")

        val vm = NotesViewModel(app)

        val all = awaitUntil({ vm.visibleNotes.value }) { it.size == 3 }
        assertEquals(setOf("Standup with Ana", "Launch risks", "Groceries"), all.map { it.title }.toSet())

        vm.space.value = NotebookSpace.WORK
        val work = awaitUntil({ vm.visibleNotes.value }) { it.size == 2 }
        assertEquals(setOf("Standup with Ana", "Launch risks"), work.map { it.title }.toSet())

        vm.space.value = NotebookSpace.PERSONAL
        val personal = awaitUntil({ vm.visibleNotes.value }) { it.size == 1 }
        assertEquals(listOf("Groceries"), personal.map { it.title })
    }
}
