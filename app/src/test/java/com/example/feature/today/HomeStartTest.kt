package com.example.feature.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.setup.SetupGuide
import com.example.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
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

/** Home on day one: record up front, the setup card, getting started, and the tour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class HomeStartTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private fun settle() = repeat(12) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        Thread.sleep(40); compose.waitForIdle()
    }

    @Test fun `a new person sees record first, what to set up, and first steps`() {
        val vm = TodayViewModel(app)
        var recorded = 0
        var setUp = 0
        val thinkingOnly = SetupGuide.compute(8f, { it == com.example.ai.modelmanagement.ModelCatalog.qwen25_1_5bInstruct.id })
        compose.setContent {
            MeetMindTheme {
                TodayScreen(vm, onOpenNote = {}, onOpenProcessing = {}, onRecord = { recorded++ }, onRecordType = {}, onRecordEvent = { _, _, _, _ -> },
                    onSearch = {}, onNavigateBottomNav = {}, setup = thinkingOnly, onSetUp = { setUp++ })
            }
        }
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_new_user.png")
        compose.onNodeWithTag("home_record").performClick()
        compose.onNodeWithTag("setup_one_tap").performClick()
        assertEquals(1, recorded); assertEquals(1, setUp)
        assertTrue(vm.gettingStarted.value?.isNew == true)
    }

    @Test fun `the tour spotlights record first, and finishing it is remembered`() {
        val vm = TodayViewModel(app)
        compose.setContent {
            MeetMindTheme {
                TodayScreen(vm, onOpenNote = {}, onOpenProcessing = {}, onRecord = {}, onRecordType = {}, onRecordEvent = { _, _, _, _ -> },
                    onSearch = {}, onNavigateBottomNav = {}, setup = SetupGuide.compute(8f, { false }), tourEnabled = true)
            }
        }
        settle()
        compose.mainClock.advanceTimeBy(1500)
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_tour_record.png")
        compose.onNodeWithTag("coach_next").performClick()
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_tour_new.png")
        compose.onNodeWithTag("coach_skip").performClick()
        settle()
        runBlocking { assertTrue(UserPreferencesManager(app).preferencesFlow.first().tourCompleted) }
    }
}
