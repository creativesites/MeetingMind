package com.craftflowtechnologies.meetingmind.core.ui

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

/** The "Notes from a YouTube video" card in the Create sheet: same tap target, light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class CreateSheetYouTubeCardTest {
    @get:Rule val compose = createComposeRule()

    private fun show(dark: Boolean, onPick: (CreateAction) -> Unit = {}) {
        compose.setContent { MeetMindTheme(darkTheme = dark) { CreateSheet(onPick = onPick, onDismiss = {}) } }
        compose.waitForIdle()
        // Past the entrance and the one sweep of light.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    @Test fun `the card opens Notes from a YouTube video`() {
        var picked: CreateAction? = null
        show(dark = false) { picked = it }
        compose.onNodeWithText("Paste a link, get study notes with timestamps").assertIsDisplayed()
        compose.onNodeWithText("New").assertIsDisplayed()
        compose.onNodeWithTag("create_youtube").performClick()
        assertEquals(CreateAction.YOUTUBE, picked)
    }

    @Test fun `light`() {
        show(dark = false)
        compose.onNodeWithTag("create_youtube").captureRoboImage("src/test/screenshots/create_sheet_youtube_light.png")
    }

    @Test fun `dark`() {
        show(dark = true)
        compose.onNodeWithTag("create_youtube").captureRoboImage("src/test/screenshots/create_sheet_youtube_dark.png")
    }
}
