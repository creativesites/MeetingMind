package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.testCircle
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.feature.today.home.HomeCircles
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * The Today home with the Circles tile in each state. Record with -Proborazzi.test.record=true, then look at the PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class HomeCirclesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MeetMindDatabase
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
    }
    @After fun tearDown() { MeetMindDatabase.setInstanceForTest(null); db.close() }

    private fun settle() = repeat(12) {
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        Thread.sleep(40); compose.waitForIdle()
    }

    private fun home(circles: HomeCircles, file: String) {
        val vm = TodayViewModel(app)
        compose.setContent {
            MeetMindTheme {
                CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                    TodayScreen(
                        vm, onOpenNote = {}, onOpenProcessing = {}, onRecord = {}, onRecordType = {}, onRecordEvent = { _, _, _, _ -> },
                        onSearch = {}, onNavigateBottomNav = {},
                        homeCircles = circles
                    )
                }
            }
        }
        settle()
        compose.onNodeWithTag("today_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("bento_circles"))
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$file.png")
    }

    @Test fun `home circles loading`() = home(HomeCircles.Loading, "home_circles_loading")

    @Test fun `home circles empty`() = home(HomeCircles.Empty, "home_circles_empty")

    @Test fun `home circles loaded`() = home(
        HomeCircles.Loaded(
            listOf("Tuesday Night" to "c1", "Grace Group" to "c2", "Morning Prayer" to "c3", "Youth Circle" to "c4")
                .map { (name, id) -> testCircle().copy(id = id, name = name) }
        ),
        "home_circles_loaded"
    )

    @Test fun `home circles failed`() = home(HomeCircles.Failed(CirclesFailure.offline), "home_circles_failed")
}
