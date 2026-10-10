package com.craftflowtechnologies.meetingmind.feature.today

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Today with real notes and recordings: it lists them, opens them, and switches views. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class TodayScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        runBlocking {
            val notes = NoteRepository(app, db)
            // Every sample sits inside today, however early the test runs (before 3am, "two hours ago" is yesterday).
            val startOfToday = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val now = maxOf(System.currentTimeMillis(), startOfToday + 3 * 3_600_000L)
            val rec = notes.createNote(RecordingType.MEETING, title = "Product sync", eventDate = now - 3_600_000L)
            db.meetingDao().insertMeeting(MeetingEntity("m1", "Product sync", now - 3_600_000L, 40 * 60_000L, "LOCAL_RECORDING", "/a.wav", "READY", 3, "en", null, noteId = rec.id))
            notes.createNote(RecordingType.SERMON, title = "Grace that holds", eventDate = now - 7_200_000L)
            notes.createNote(title = "Ideas for Q4", eventDate = now - 60_000L,
                initialBlocks = listOf(com.craftflowtechnologies.meetingmind.core.model.NoteBlock("b", "", 0, com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH,
                    com.craftflowtechnologies.meetingmind.core.notes.RichText.plain("Launch the referral programme before the holidays; ask Tom about pricing tiers."))), useTemplate = false)
            notes.createNote(title = "Follow-up with design", eventDate = now - 3_600_000L + 10 * 60_000L)
        }
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    /** Robolectric's main looper clock only moves when told; the view model debounces on it. */
    private fun settle(until: () -> Boolean) {
        repeat(60) {
            if (until()) return
            org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(50)
            compose.waitForIdle()
        }
    }

    /** The view chips are a LazyRow: with Inter's wider glyphs the later ones start off-screen. */
    private fun scrollToView(tag: String) {
        compose.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange) and androidx.compose.ui.test.hasScrollToNodeAction() and androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasTestTag("view_day")))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
    }

    @Test
    fun `today lists the day and opens a card`() {
        val opened = mutableListOf<String>()
        val vm = TodayViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            MeetMindTheme { androidx.compose.runtime.CompositionLocalProvider(com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas provides true, com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion provides true) {
                TodayScreen(vm, onOpenNote = { opened += it }, onOpenProcessing = {}, onRecord = {}, onRecordType = {},
                    onRecordEvent = { _, _, _, _ -> }, onSearch = {}, onNavigateBottomNav = {})
            }}
        }
        settle { vm.items.value.size == 4 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_full.png")
        assertEquals(setOf("Product sync", "Grace that holds", "Ideas for Q4", "Follow-up with design"), vm.items.value.map { it.title }.toSet())
        compose.onNodeWithTag("view_day").performClick()
        settle { vm.view.value == CalendarView.DAY && vm.items.value.isNotEmpty() }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_day.png")

        scrollToView("view_month")
        compose.onNodeWithTag("view_month").performClick()
        settle { vm.view.value == CalendarView.MONTH }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_month.png")
        scrollToView("view_river")
        compose.onNodeWithTag("view_river").performClick()
        settle { vm.view.value == CalendarView.RIVER && vm.items.value.isNotEmpty() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_river.png")
    }
}
