package com.craftflowtechnologies.meetingmind.feature.notes

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Notes library on a 6" phone: populated, searching, empty. The first note has to be on screen without scrolling. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-xxhdpi", sdk = [34])
class NotesLibraryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: MeetMindDatabase
    private val now = System.currentTimeMillis()
    private val min = 60_000L

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4)).setTransactionExecutor(java.util.concurrent.Executors.newFixedThreadPool(2)).build()
        MeetMindDatabase.setInstanceForTest(db)
    }

    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private fun note(id: String, title: String, body: String, workflow: String = "GENERAL", ago: Long, pinned: Boolean = false, private: Boolean = false, notebook: String? = "home") = runBlocking {
        db.noteDao().upsert(NoteEntity(id, title, workflow, notebook, now - ago * min, now - ago * min, null, pinned, private, "OPEN", null, "{}", null, body))
    }

    private fun seed() = runBlocking {
        db.notebookDao().upsert(NotebookEntity("home", "My notes", "PERSONAL", null, null, 1, 1, null, 0))
        note("1", "Sunday sermon: the covenant renewed", "**Key point:** God keeps his covenant. - Read Nehemiah 9 together and pray for the week ahead.", "SERMON", 30, pinned = true)
        note("2", "Product sync with Ana", "# Decisions\n- Ship the covenant-free pricing page on Friday\n- Ana owns the launch checklist", "MEETING", 150)
        note("3", "John 3 study", "Observation: born again. Question: what does the wind teach about the Spirit?", "BIBLE_STUDY", 60 * 24 + 40)
        note("4", "Ideas for the offsite", "A walking meeting, a shared playlist, lighthouse visit, a quiet hour.", "IDEA", 60 * 24 * 3)
        note("5", "Prayer journal", "private words", "JOURNAL", 60 * 24 * 5, private = true)
        note("6", "Groceries", "milk, bread, olive oil", "GENERAL", 60 * 24 * 40)
        db.meetingDao().insertMeeting(MeetingEntity("m2", "Product sync", 1, 42 * min, "LOCAL_RECORDING", null, "READY", 2, "en", null, noteId = "2"))
        db.meetingDao().insertMeeting(MeetingEntity("m1", "Sermon", 1, 51 * min, "LOCAL_RECORDING", null, "PROCESSING", 1, "en", null, noteId = "1"))
    }

    private fun await(cond: () -> Boolean) = compose.waitUntil(60_000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS); cond() }

    private fun show(vm: NotesViewModel) = compose.setContent {
        MeetMindTheme(darkTheme = false) {
            CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                NotesScreen(viewModel = vm, onOpenNote = {}, onOpenNotebook = {}, onOpenArchive = {}, onNavigateBack = null, onNavigateBottomNav = {})
            }
        }
    }

    @Test fun populated() {
        seed()
        val vm = NotesViewModel(app)
        show(vm)
        await { vm.library.items.value.items.size == 6 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/notes_library_populated.png")
        assertEquals("1", vm.library.items.value.items.first().id)   // pinned first
    }

    @Test fun searching() {
        seed()
        val vm = NotesViewModel(app)
        show(vm)
        await { vm.library.items.value.items.size == 6 }
        vm.library.setSearch("covenant")
        await { vm.library.items.value.items.map { it.id } == listOf("1", "2") }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/notes_library_search.png")
        assertTrue(vm.library.items.value.items.first().highlight.contains("covenant"))
    }

    @Test fun chipsFilter() {
        seed()
        val vm = NotesViewModel(app)
        show(vm)
        await { vm.library.items.value.items.size == 6 }
        vm.library.toggleChip(com.craftflowtechnologies.meetingmind.core.notes.LibraryChip.RECORDINGS)
        await { vm.library.items.value.items.size == 2 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/notes_library_recordings.png")
    }

    @Test fun empty() {
        val vm = NotesViewModel(app)
        show(vm)
        await { vm.library.items.value.endReached }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/notes_library_empty.png")
    }
}
