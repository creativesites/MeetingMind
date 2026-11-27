package com.craftflowtechnologies.meetingmind.feature.bible

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The reader's selection bar for a passage, in light: what a person sees after tapping verses. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SelectionBarScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun selection_bar() {
        rule.setContent {
            MeetMindTheme(darkTheme = false) {
                SelectionBar(
                    reference = ScriptureReferenceParser.parse("James 1:2-8")!!,
                    insertLabel = "Guide me",
                    insertIcon = Icons.Filled.AutoStories,
                    onPrimary = {}, onSave = {}, onCopy = {}, onShare = {}, onClear = {}
                )
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/bible/selection_bar_light.png")
    }
}
