package com.craftflowtechnologies.meetingmind.feature.meetingdetail

import android.app.Application
import android.content.ComponentName
import android.os.Binder
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.hasText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.Executor

/**
 * A recording's note screen just after a fresh open (signature moments §3.2, §3.4): the Unveil plays on the
 * header and body of MeetingDetail. Captured mid-Unveil, light theme. Not a golden: a record for review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class MeetingDetailUnveilScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase
    private lateinit var store: MomentLedgerStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(Executor { }).setTaskExecutor(Executor { it.run() }).build()
        )
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        // The screen connects the app's playback service on open; no audio plays in this record.
        shadowOf(context).setComponentNameAndServiceForBindService(ComponentName(context, PlaybackService::class.java), Binder())
        val file = File(context.cacheDir, "unveil-${System.nanoTime()}.preferences_pb")
        store = MomentLedgerStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { file })
        runBlocking {
            db.meetingDao().insertMeeting(
                MeetingEntity(
                    "m-unveil", "Planning call with Grace", 0L, 18 * 60_000L, "LOCAL_RECORDING", null,
                    MeetingStatus.READY.name, 1, "en", "We agreed on June dates and will book the cabin this week."
                )
            )
            // Enough speech that the planner sees a real recording (not "no speech" and not "very short").
            db.transcriptDao().insertSegments(
                listOf(TranscriptSegmentEntity("m-unveil-s", "m-unveil", null, null, 0L, 1000L, List(60) { "word" }.joinToString(" "), null))
            )
        }
    }

    @After fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    @Test fun meeting_detail_just_after_a_fresh_open_light() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeetingDetailViewModel(app, "m-unveil")
        compose.setContent {
            MeetMindTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalMomentLedgerStore provides store,
                    LocalCompanionSettingsSource provides FakeCompanionSettings(CompanionSettings(sounds = false)),
                    LocalCompanionForceCanvas provides true
                ) {
                    MeetingDetailScreen(viewModel = vm, onNavigateBack = {})
                }
            }
        }
        // The note is loaded, then the planned Unveil is a few frames in.
        compose.waitUntilAtLeastOneExists(hasText("Planning call with Grace"), 10_000)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/moments/meeting_detail_unveil_just_after_open_light.png")
    }
}
