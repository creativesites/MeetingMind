package com.craftflowtechnologies.meetingmind.feature.notes.editor

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.PlainReason
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.moments.LocalMomentLedgerStore
import com.craftflowtechnologies.meetingmind.feature.moments.noteMomentSubject
import com.craftflowtechnologies.meetingmind.feature.moments.rememberNoteMoment
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
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
 * The Unveil on the note editor (ZURI §3.2): a fresh open of a note plans its moment once. A return to the same
 * entry (process restore, a sub-screen coming back) plans nothing, and a locked or still-loading note plans
 * nothing until it can be shown. Uses the same planning path as the meeting detail (`rememberNoteMoment`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class NoteEditorMomentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var store: MomentLedgerStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        val file = File(context.cacheDir, "editor-moments-${System.nanoTime()}.preferences_pb")
        store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file })
    }

    // No db.close(): the compose rule disposes its content after this, and a composition still reading the
    // database would otherwise fail on a closed pool. The database is in memory and goes with the process.
    @After fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
    }

    /** A ready recording filed under [noteId], with [words] of transcript and a summary. */
    private fun recording(id: String, noteId: String, words: Int = 60) = runBlocking {
        db.meetingDao().insertMeeting(
            MeetingEntity(id, "Lunch with Grace", 0L, 18 * 60_000L, "LOCAL_RECORDING", null, "READY", 1, "en", "We planned June.", noteId = noteId)
        )
        db.transcriptDao().insertSegments(
            listOf(TranscriptSegmentEntity("$id-s", id, null, null, 0L, 1000L, List(words) { "word" }.joinToString(" "), null))
        )
    }

    private var lastPlan: MomentPlan? = null

    @Composable
    private fun Host(subject: String, refresh: Int = 0) {
        val state = rememberNoteMoment(subject, refreshKey = refresh)
        lastPlan = state.plan
        Box(Modifier.fillMaxSize()) { Text("The note underneath") }
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

    @Test fun `a fresh editor open plans an Unveil, and reloading the facts does not plan it again`() {
        recording("m1", noteId = "n1")
        val subject = noteMomentSubject(noteLoaded = true, locked = false, recordingIds = listOf("m1"))
        var refresh by mutableIntStateOf(0)
        compose.setContent { Harness { key(0) { Host(subject, refresh) } } }
        awaitPlan()
        val first = lastPlan
        assertTrue("a fresh open unveils: $first", first is MomentPlan.Unveil)

        // The facts reload on the same entry (a status change): the claim is spent, so nothing is planned again.
        compose.runOnIdle { refresh++ }
        compose.waitForIdle()
        assertTrue("the same moment, not a new one: $lastPlan", lastPlan === first)
    }

    @Test fun `a note with no recording has no moment and opens plain`() {
        val subject = noteMomentSubject(noteLoaded = true, locked = false, recordingIds = emptyList())
        assertEquals("", subject)
        compose.setContent { Harness { Host(subject) } }
        compose.waitForIdle()
        assertNull(lastPlan)
        compose.onNodeWithText("The note underneath").assertExists()
    }
}
