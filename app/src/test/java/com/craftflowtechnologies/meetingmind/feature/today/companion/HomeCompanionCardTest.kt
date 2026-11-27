package com.craftflowtechnologies.meetingmind.feature.today.companion

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The companion's line on a home: what it says, its one action, and waving it away. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class HomeCompanionCardTest {

    @get:Rule val compose = createComposeRule()

    private val ready = HomeNudge(
        key = "ready:m1", kind = NudgeKind.NOTE_READY, state = CompanionState.PROUD,
        text = "Your notes from “Budget review” are ready.", action = NudgeAction.OpenRecording("m1"), actionLabel = "Open"
    )

    @Test fun `the line shows its text and runs its action`() {
        var opened = 0
        var dismissed = 0
        compose.setContent {
            MeetMindTheme {
                HomeCompanionCard(ready, "Zuri", showCompanion = true, onAction = { opened++ }, onDismiss = { dismissed++ }, modifier = Modifier.padding(16.dp))
            }
        }
        compose.onNodeWithText("Zuri", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Your notes from “Budget review” are ready.", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("home_companion_line").captureRoboImage("build/outputs/roborazzi/home_companion_line_ready.png")
        compose.onNodeWithTag("home_companion_action").performClick()
        compose.onNodeWithTag("home_companion_dismiss").performClick()
        assertEquals(1, opened)
        assertEquals(1, dismissed)
    }

    @Test fun `a line that can't be waved away has no Not now`() {
        val processing = HomeNudge(
            key = "processing:m2", kind = NudgeKind.PROCESSING, state = CompanionState.THINKING,
            text = "I'm writing up “Standup”. I'll let you know when it's ready.", action = null, actionLabel = null, dismissable = false
        )
        compose.setContent { MeetMindTheme { HomeCompanionCard(processing, "Zuri", showCompanion = false, onAction = {}, onDismiss = {}) } }
        compose.onNodeWithTag("home_companion_line").assertIsDisplayed()
        compose.onAllNodesWithTag("home_companion_dismiss").assertCountEquals(0)
        compose.onAllNodesWithTag("home_companion_action").assertCountEquals(0)
    }

    @Test fun `a tip says Got it in dark mode too`() {
        val tip = HomeNudge(
            key = "tip:YOUTUBE", kind = NudgeKind.TIP, state = CompanionState.CURIOUS, text = Tip.YOUTUBE.text,
            action = NudgeAction.YouTube, actionLabel = "Try it", dismissLabel = "Got it", tip = Tip.YOUTUBE
        )
        compose.setContent { MeetMindTheme(darkTheme = true) { HomeCompanionCard(tip, "Zuri", showCompanion = true, onAction = {}, onDismiss = {}, modifier = Modifier.padding(16.dp)) } }
        compose.onNodeWithText("Got it", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Try it", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("home_companion_line").captureRoboImage("build/outputs/roborazzi/home_companion_line_tip_dark.png")
    }
}
