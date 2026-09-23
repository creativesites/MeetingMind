package com.example.feature.today

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.model.RecordingType
import com.example.core.repository.NoteRepository
import com.example.ui.theme.MeetMindTheme
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
            val now = System.currentTimeMillis()
            val rec = notes.createNote(RecordingType.MEETING, title = "Product sync", eventDate = now - 3_600_000L)
            db.meetingDao().insertMeeting(MeetingEntity("m1", "Product sync", now - 3_600_000L, 40 * 60_000L, "LOCAL_RECORDING", "/a.wav", "READY", 3, "en", null, noteId = rec.id))
            notes.createNote(RecordingType.SERMON, title = "Grace that holds", eventDate = now - 7_200_000L)
            notes.createNote(title = "Ideas for Q4", eventDate = now - 60_000L)
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

    @Test
    fun `today lists the day and opens a card`() {
        val opened = mutableListOf<String>()
        val vm = TodayViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent {
            MeetMindTheme {
                TodayScreen(vm, onOpenNote = { opened += it }, onOpenProcessing = {}, onRecord = {}, onRecordType = {},
                    onRecordEvent = { _, _, _, _ -> }, onSearch = {}, onNavigateBottomNav = {})
            }
        }
        settle { vm.items.value.size == 3 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_full.png")
        assertEquals(setOf("Product sync", "Grace that holds", "Ideas for Q4"), vm.items.value.map { it.title }.toSet())

        compose.onNodeWithTag("view_month").performClick()
        settle { vm.view.value == CalendarView.MONTH }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_month.png")
        compose.onNodeWithTag("view_river").performClick()
        settle { vm.view.value == CalendarView.RIVER && vm.items.value.isNotEmpty() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_river.png")
    }
}
