package com.craftflowtechnologies.meetingmind.feature.notes.editor

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.learn.LessonEntry
import com.craftflowtechnologies.meetingmind.feature.moments.LocalMomentLedgerStore
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import java.io.File

/**
 * The note editor shows exactly one lesson entry for a lecture note (and for a note in a Learning notebook), and none
 * for a meeting note. The Learning studio strip is gone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class NoteEditorLessonEntryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var store: MomentLedgerStore
    private lateinit var notes: NoteRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        notes = NoteRepository(context, db)
        val file = File(context.cacheDir, "lesson-entry-${System.nanoTime()}.preferences_pb")
        store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file })
    }

    // No db.close(): the compose rule disposes its content after this test.
    @After fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
    }

    /** Renders the editor for [noteId] and waits until it has loaded. Returns how many times "Open lesson" was tapped. */
    private fun editor(noteId: String, opened: MutableList<String>) {
        val viewModel = NoteEditorViewModel(context.applicationContext as android.app.Application, noteId)
        // The load runs on Dispatchers.IO and posts back to the paused main looper (TESTING.md gotcha 1).
        val deadline = System.currentTimeMillis() + 30_000
        while (!viewModel.loaded.value && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(20, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
        assertTrue("the editor loaded", viewModel.loaded.value)
        compose.setContent {
            MeetMindTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalMomentLedgerStore provides store,
                    LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings(sounds = false)),
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionReducedMotion provides true
                ) {
                    NoteEditorScreen(
                        viewModel = viewModel,
                        onNavigateBack = {},
                        onOpenRecording = { _, _ -> },
                        onOpenNote = {},
                        onRecordHere = {},
                        onOpenLesson = { opened += it }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun lessonEntries(): Int =
        compose.onAllNodesWithTag("note_open_lesson", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `a lecture note shows exactly one lesson entry and no studio strip`() {
        val note = runBlocking { notes.createNote(RecordingType.LECTURE, title = "Biology lecture 7", useTemplate = false) }
        val opened = mutableListOf<String>()
        editor(note.id, opened)

        assertEquals(1, lessonEntries())
        compose.onNodeWithText("Study this lecture").assertExists()
        assertEquals(0, compose.onAllNodesWithTag("note_learning_studio_strip", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("note_open_study_studio_button", useUnmergedTree = true).fetchSemanticsNodes().size)

        // Every note opens with its moment (the Unveil), which holds input until it ends; let it finish first.
        compose.mainClock.advanceTimeBy(8_000)
        compose.waitForIdle()
        compose.onNodeWithText("Open lesson").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(note.id), opened)
    }

    @Test
    fun `a meeting note shows no lesson entry`() {
        val note = runBlocking { notes.createNote(RecordingType.MEETING, title = "Team standup", useTemplate = false) }
        editor(note.id, mutableListOf())

        assertEquals(0, lessonEntries())
    }

    @Test
    fun `a note in a Learning notebook shows the one entry, whatever its type`() {
        val notebook = runBlocking { notes.createNotebook("Biology", NotebookSpace.LEARNING) }
        val note = runBlocking { notes.createNote(RecordingType.MEETING, title = "Filed in Biology", notebookId = notebook.id, useTemplate = false) }
        editor(note.id, mutableListOf())

        assertEquals(1, lessonEntries())
    }

    @Test
    fun `the gate is lecture and research notes, or a Learning notebook`() {
        assertTrue(LessonEntry.shows(RecordingType.LECTURE, null))
        assertTrue(LessonEntry.shows(RecordingType.RESEARCH, null))
        assertFalse(LessonEntry.shows(RecordingType.MEETING, null))
        assertTrue(LessonEntry.shows(RecordingType.MEETING, NotebookSpace.LEARNING))
        assertFalse(LessonEntry.shows(null, NotebookSpace.WORK))
    }
}
