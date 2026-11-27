package com.craftflowtechnologies.meetingmind.feature.meetingdetail.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each Overview page swipes sideways and scrolls down on its own, so long content is never cut off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [34])
class OverviewStepperScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aLongSummaryScrollsDownToItsEnd() {
        val longSummary = (1..40).joinToString(" ") { "Sentence $it of a long meeting summary that goes on." }
        compose.setContent {
            MeetMindTheme {
                OverviewStepper(
                    summary = longSummary,
                    openQuestionCount = 5,
                    decisions = emptyList(),
                    actionItems = emptyList(),
                    questions = emptyList(),
                    topics = emptyList(),
                    topicsLabel = "Topics",
                    onToggleActionItem = {},
                    onAddTask = {},
                    speakers = emptyList(),
                    segments = emptyList(),
                    showDecisionsStep = true,
                    showTasksStep = true,
                    showQuestionsStep = true,
                    onPlayFrom = {}
                )
            }
        }
        // The line under the summary sits far below a 640 dp screen; it is reachable only if the page scrolls.
        compose.onNodeWithText("Five open questions remain", substring = true).performScrollTo().assertIsDisplayed()
    }
}
