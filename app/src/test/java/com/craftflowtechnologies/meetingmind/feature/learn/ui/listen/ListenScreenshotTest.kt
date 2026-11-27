package com.craftflowtechnologies.meetingmind.feature.learn.ui.listen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.StudySamples
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Listen screenshot against design/study/Listen.dc.html (always dark). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class ListenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `listen, dark`() {
        compose.setContent {
            LearnHarness(dark = true) {
                ListenScreen(
                    state = StudySamples.listen(),
                    onClose = {}, onTogglePlay = {}, onSeek = {}, onSkip = {}, onNextStressed = {},
                    onSpeed = {}, onVoice = {}, onToggleStopAtSectionEnd = {}, onToggleQuizAfter = {}
                )
            }
        }
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/study/listen_dark.png")
    }
}
