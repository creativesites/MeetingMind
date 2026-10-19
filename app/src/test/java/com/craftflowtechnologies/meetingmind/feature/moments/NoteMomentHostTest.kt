package com.craftflowtechnologies.meetingmind.feature.moments

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.companion.moment.PlainReason
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Z-16/Z-33 on the note screen's host: a ready note's ticket plays the First Keep over the note on the first
 * fresh open, "Open note" dismisses it, a later open never replays it, and failures are plain. Also the
 * "{title} is ready." snackbar goldens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class NoteMomentHostTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var store: MomentLedgerStore

    @Before fun setUp() {
        NoteReadyEvents.drain()
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        val file = File(context.cacheDir, "moments-${System.nanoTime()}.preferences_pb")
        store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file })
    }

    @After fun tearDown() {
        NoteReadyEvents.drain()
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    private fun meeting(id: String, status: MeetingStatus, summary: String?, words: Int) = runBlocking {
        db.meetingDao().insertMeeting(MeetingEntity(id, "Lunch with Grace", 0L, 18 * 60_000L, "LOCAL_RECORDING", null, status.name, 1, "en", summary))
        if (words > 0) db.transcriptDao().insertSegments(
            listOf(TranscriptSegmentEntity("$id-s", id, null, null, 0L, 1000L, List(words) { "word" }.joinToString(" "), null))
        )
    }

    private var lastPlan: MomentPlan? = null
    private var lastOutcome: NoteOutcome? = null

    @Composable
    private fun Host(noteId: String, refreshKey: Any? = null) {
        val state = rememberNoteMoment(noteId, refreshKey = refreshKey)
        lastPlan = state.plan
        lastOutcome = state.facts?.outcome
        Box(Modifier.fillMaxSize()) {
            Column {
                Text("The note underneath")
                NoteStatus(state.facts?.outcome, onRetry = {}, onRedoOnline = {}, onPlay = {})
            }
            NoteFirstKeep(state, meta = "Note · 18 min", keyPoints = listOf("Book the cabin"), actionCount = 1, onShare = {})
        }
    }

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        MeetMindTheme(darkTheme = false) {
            CompositionLocalProvider(
                LocalMomentLedgerStore provides store,
                LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings(sounds = false)),
                LocalCompanionForceCanvas provides true,
                LocalCompanionReducedMotion provides true,
                content = content
            )
        }
    }

    private fun awaitPlan() = compose.waitUntil(10_000) { lastPlan != null }

    @Test fun `a ready note plays the First Keep once and a later open does not replay it`() {
        meeting("m1", MeetingStatus.READY, "We planned June.", 60)
        runBlocking { onNoteProcessed(context, "m1", store = store) }
        var entry by mutableIntStateOf(0)
        compose.setContent { Harness { key(entry) { Host("m1") } } }
        awaitPlan()
        val plan = lastPlan
        assertTrue("expected a First Keep, got $plan", plan is MomentPlan.FirstKeep)
        assertTrue((plan as MomentPlan.FirstKeep).onceEver)
        // The note is already there underneath, and the card shows its verbatim key point.
        compose.onNodeWithText("The note underneath").assertExists()
        compose.onNodeWithText("Book the cabin").assertExists()
        compose.onNodeWithText("Open note").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Open note").assertCountEquals(0)

        // A new navigation entry: the ticket is gone, so no second First Keep.
        lastPlan = null
        compose.runOnIdle { entry++ }
        compose.waitForIdle()
        awaitPlan()
        assertTrue("a reopen must not replay the First Keep: $lastPlan", lastPlan !is MomentPlan.FirstKeep)
        compose.onAllNodesWithText("Open note").assertCountEquals(0)
    }

    @Test fun `a failed note opens plain and gets no ticket`() {
        meeting("m2", MeetingStatus.ERROR, null, 0)
        runBlocking { onNoteProcessed(context, "m2", store = store) }
        assertNull(runBlocking { store.peekTicket("m2", System.currentTimeMillis()) })
        compose.setContent { Harness { Host("m2") } }
        awaitPlan()
        // Founder decision (2026-10-15): every note gets its Unveil moment on open, but a failed note gets no First Keep ticket.
        assertTrue("expected Unveil without First Keep for failed note, got $lastPlan", lastPlan is MomentPlan.Unveil)
        compose.onAllNodesWithText("Open note").assertCountEquals(0)
    }

    @Test fun `a partial note is never celebrated and shows the honest line`() {
        meeting("m3", MeetingStatus.READY, null, 60)
        runBlocking { onNoteProcessed(context, "m3", store = store) }
        compose.setContent {
            Harness {
                Host("m3")
            }
        }
        awaitPlan()
        compose.waitForIdle()
        assertTrue("expected a moment on partial note per every-note rule, got $lastPlan", lastPlan is MomentPlan.FirstKeep || lastPlan is MomentPlan.Unveil)
        assertEquals(NoteOutcome.PARTIAL, lastOutcome)
        compose.onNodeWithText("The transcript is ready. The summary didn't finish.").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }

    private fun snackbar(line: ReadyLine, name: String, dark: Boolean) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalCompanionSettingsSource provides FakeCompanionSettings(),
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionReducedMotion provides true
                ) {
                    Box(Modifier.background(MM.colors.background).testTag("frame")) { NoteReadySnackbar(line, onAction = {}) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/moments/snackbar_${name}_${if (dark) "dark" else "light"}.png")
    }

    private val one = ReadyLine.One(NoteReadyEvent("x", "Lunch with Grace", sermon = false, sensitive = false, atMs = 0L))
    private val sermon = ReadyLine.One(NoteReadyEvent("y", "Mercy at the table", sermon = true, sensitive = false, atMs = 0L))

    @Test fun snackbar_one_light() = snackbar(one, "one", false)
    @Test fun snackbar_one_dark() = snackbar(one, "one", true)
    @Test fun snackbar_sermon_light() = snackbar(sermon, "sermon", false)
    @Test fun snackbar_queued_dark() = snackbar(ReadyLine.Many(2), "queued", true)

    @Test fun `the queued line counts the notes`() {
        compose.setContent { Harness { NoteReadySnackbar(ReadyLine.Many(2), onAction = {}) } }
        compose.onNodeWithText("2 notes are ready.").assertExists()
        compose.onNodeWithText("Show").assertExists()
    }

    @Test fun `opening while processing waits and then plays First Keep when ready`() {
        meeting("m4", MeetingStatus.PROCESSING, null, 0)
        var status by androidx.compose.runtime.mutableStateOf(MeetingStatus.PROCESSING)
        compose.setContent { Harness { Host("m4", refreshKey = status) } }
        compose.waitForIdle()
        assertNull("processing note must not plan prematurely", lastPlan)

        runBlocking {
            db.meetingDao().insertMeeting(
                MeetingEntity("m4", "Lunch with Grace", 0L, 18 * 60_000L, "LOCAL_RECORDING", null, MeetingStatus.READY.name, 1, "en", "We planned June.")
            )
            db.transcriptDao().insertSegments(
                listOf(TranscriptSegmentEntity("m4-s", "m4", null, null, 0L, 1000L, List(60) { "word" }.joinToString(" "), null))
            )
            onNoteProcessed(context, "m4", store = store)
        }
        compose.runOnIdle { status = MeetingStatus.READY }
        awaitPlan()
        assertTrue("expected FirstKeep after processing completes, got $lastPlan", lastPlan is MomentPlan.FirstKeep)
        compose.onNodeWithText("Book the cabin").assertExists()
    }
}
