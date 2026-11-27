package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The lesson's overflow menu holds the studio's secondary actions, and each one fires its callback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class LessonMenuTest {
    @get:Rule val compose = createComposeRule()

    private var openedNotes = 0
    private var toggledReviews = 0

    private fun show(reviewsPaused: Boolean = false, dark: Boolean = false) {
        compose.setContent {
            LearnHarness(dark = dark) {
                LessonScreen(
                    state = StudySamples.lesson(),
                    explain = ExplainState(),
                    openConceptId = null,
                    onBack = {},
                    onPlayFrom = {},
                    onTogglePlay = {},
                    onHearSummary = {},
                    onOpenConcept = {},
                    onCloseConcept = {},
                    onExplain = { _, _ -> },
                    onQuizConcept = {},
                    onReportConcept = {},
                    onListen = {},
                    onPractice = {},
                    onAsk = {},
                    onGenerate = {},
                    onFixAudio = {},
                    reviewsPaused = reviewsPaused,
                    onOpenNote = { openedNotes++ },
                    onTogglePauseReviews = { toggledReviews++ }
                )
            }
        }
    }

    @Test
    fun `the overflow lists the moved actions and each fires its callback`() {
        show()

        compose.onNodeWithContentDescription("Lesson options").performClick()
        compose.onNodeWithText("Open full notes").assertIsDisplayed()
        compose.onNodeWithText("Pause reviews").assertIsDisplayed()
        compose.onNodeWithText("Open full notes").performClick()
        assertEquals(1, openedNotes)

        compose.onNodeWithContentDescription("Lesson options").performClick()
        compose.onNodeWithText("Pause reviews").performClick()
        assertEquals(1, toggledReviews)
    }

    @Test
    fun `paused reviews offer to resume them`() {
        show(reviewsPaused = true)

        compose.onNodeWithContentDescription("Lesson options").performClick()
        compose.onNodeWithText("Resume reviews").assertIsDisplayed()
    }

    @Test
    fun `the overflow menu, open, in light and dark`() {
        show()
        compose.onNodeWithContentDescription("Lesson options").performClick()
        compose.onNodeWithText("Open full notes").assertIsDisplayed()
        compose.onNodeWithText("Pause reviews").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/lesson_menu_open_paper.png")
    }

    @Test
    fun `the overflow menu, open, dark`() {
        show(dark = true)
        compose.onNodeWithContentDescription("Lesson options").performClick()
        compose.onNodeWithText("Open full notes").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/lesson_menu_open_midnight.png")
    }
}
