package com.craftflowtechnologies.meetingmind.feature.today.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.testCircle
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** The Circles tile on Home, in each state, and what each control does. Companion and scenes are held still (see TESTING.md). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class HomeCirclesSectionTest {
    @get:Rule val compose = createComposeRule()

    private val sections = HomeSections(faith = false, work = false, personal = true)
    private val data = BentoData(
        weekCount = 0, devotionalTitle = "", devotionalSub = "", devotionalNew = false, todayCount = 0,
        nextTitle = "Nothing next", nextWhen = "Tap to record", plan = emptyList()
    )

    private var openedCircle: String? = null
    private var circlesOpened = 0
    private var started = 0
    private var retried = 0

    private fun actions() = BentoActions(
        onFaith = {}, onDevotional = {}, onPrayer = {}, onCreate = {},
        onCircles = { circlesOpened++ }, onWork = {}, onNextUp = {}, onJournal = {},
        onOpenCircle = { openedCircle = it }, onStartCircle = { started++ }, onRetryCircles = { retried++ }
    )

    private fun show(circles: HomeCircles) {
        compose.setContent {
            MeetMindTheme {
                CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                    HomeBento(sections, data, actions(), circles = circles)
                }
            }
        }
        settle()
    }

    private fun settle() = repeat(12) {
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        Thread.sleep(40); compose.waitForIdle()
    }

    @Test fun `loading shows a quiet line and no actions`() {
        show(HomeCircles.Loading)
        compose.onNodeWithText("Loading your circles…").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("bento_circles_start").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("bento_circles_join").fetchSemanticsNodes().size)
    }

    @Test fun `with no circles, one clear line and Start or Join, which open the Circles screens`() {
        show(HomeCircles.Empty)
        compose.onNodeWithText("No circles yet").assertIsDisplayed()
        compose.onNodeWithText("Start a circle").performClick()
        assertEquals(1, started)
        compose.onNodeWithText("Join a circle").performClick()
        assertEquals(1, circlesOpened)
    }

    @Test fun `with circles, the first three are listed and each opens its own circle`() {
        val circles = listOf("c1", "c2", "c3", "c4").map { testCircle().copy(id = it, name = "Circle $it") }
        show(HomeCircles.Loaded(circles))
        compose.onNodeWithText("Circle c1").assertIsDisplayed()
        compose.onNodeWithText("Circle c3").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("bento_circle_c4").fetchSemanticsNodes().size)
        compose.onNodeWithText("Circle c2").performClick()
        assertEquals("c2", openedCircle)
    }

    @Test fun `a failure is one plain line with a retry`() {
        show(HomeCircles.Failed(CirclesFailure.offline))
        compose.onNodeWithText("You're offline. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test fun `a build with no Circles service says so`() {
        show(HomeCircles.NotConnected)
        compose.onNodeWithText("Circles isn't connected in this build.").assertIsDisplayed()
    }
}
