package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** The getting-started card: the companion says hello to someone new, and each step opens its thing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GettingStartedCardTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `someone new is greeted by the companion and steps open`() {
        var recorded = 0
        var hidden = 0
        compose.setContent {
            MeetMindTheme {
                GettingStartedCard(
                    GettingStarted(isNew = true, showsFaith = true),
                    onRecord = { recorded++ }, onNote = {}, onCalendar = {}, onDevotional = {}, onDismiss = { hidden++ }
                )
            }
        }
        compose.onNodeWithText("Let's make it yours", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("0 of 4 done", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("getting_started").captureRoboImage("build/outputs/roborazzi/getting_started_new.png")
        compose.onNodeWithTag("start_record").performClick()
        compose.onNodeWithTag("getting_started_hide").performClick()
        assertEquals(1, recorded)
        assertEquals(1, hidden)
    }

    @Test fun `a finished step is ticked and can't be tapped again`() {
        var recorded = 0
        compose.setContent {
            MeetMindTheme(darkTheme = true) {
                GettingStartedCard(
                    GettingStarted(recorded = true, isNew = false),
                    onRecord = { recorded++ }, onNote = {}, onCalendar = {}, onDevotional = {}, onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Getting started", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1 of 3 done", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("start_record").performClick()
        assertEquals(0, recorded)
        compose.onNodeWithTag("getting_started").captureRoboImage("build/outputs/roborazzi/getting_started_dark.png")
    }
}
