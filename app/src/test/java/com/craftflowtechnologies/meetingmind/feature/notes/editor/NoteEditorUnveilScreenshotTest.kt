package com.craftflowtechnologies.meetingmind.feature.notes.editor

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.moments.LocalMomentLedgerStore
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The note editor just after a fresh open, in light mode: the Unveil is mid-play over the note. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class NoteEditorUnveilScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: Application
    private lateinit var db: MeetMindDatabase
    private lateinit var store: MomentLedgerStore

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        val file = File(app.cacheDir, "editor-shot-${System.nanoTime()}.preferences_pb")
        store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file })
    }

    @After fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    @Test fun `the note editor just after a fresh open, light`() {
        val note = runBlocking { NoteRepository(app, db).createNote(RecordingType.GENERAL, title = "Lunch with Grace", useTemplate = false) }
        runBlocking {
            db.meetingDao().insertMeeting(
                MeetingEntity("m-shot", "Lunch with Grace", 0L, 18 * 60_000L, "LOCAL_RECORDING", null, "READY", 1, "en", "We planned June.", noteId = note.id)
            )
            db.transcriptDao().insertSegments(
                listOf(TranscriptSegmentEntity("m-shot-s", "m-shot", null, null, 0L, 1000L, List(60) { "word" }.joinToString(" "), null))
            )
        }
        val viewModel = NoteEditorViewModel(app, note.id)
        compose.setContent {
            MeetMindTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalMomentLedgerStore provides store,
                    LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings(sounds = false)),
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionReducedMotion provides false
                ) {
                    NoteEditorScreen(
                        viewModel = viewModel,
                        onNavigateBack = {},
                        onOpenRecording = { _, _ -> },
                        onOpenNote = {},
                        onRecordHere = {}
                    )
                }
            }
        }
        // The Unveil is planned once the note and its recording load (its polite announcement is in the tree).
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Lunch with Grace is ready.", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Freeze the clock where the Unveil has just started, then play 400 ms of it.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Lunch with Grace is ready.", useUnmergedTree = true).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/notes/editor_unveil_light.png")
    }
}
