package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeState
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the new practice formats (match, order, fill the gap, true or false, worked example) in Paper,
 * for checking against design/study/FormatMatch.dc.html and Practice.dc.html. Run with -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class PracticeFormatsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, state: PracticeState) {
        compose.setContent {
            LearnHarness(dark = false) {
                PracticeScreen(
                    state = state,
                    onAnswerChoice = {},
                    onNotSure = {},
                    onReveal = {},
                    onRate = {},
                    onContinue = {},
                    onClose = {},
                    onPlaySource = {},
                    onDone = {},
                    onOpenNextStep = {},
                    onMakeCard = {},
                    onAnswer = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/formats_${name}_paper.png")
    }

    @Test fun `true or false, answered wrong`() = shot(
        "true_false",
        FormatSamples.state(
            FormatSamples.trueFalse(),
            AnswerFeedback(chosenIndex = 0, correct = false, why = "Not quite. Vmax means: the maximum rate.", nextLine = "This one comes back in 3 questions")
        )
    )

    @Test fun `fill the gap, before the answer`() = shot("fill_gap", FormatSamples.state(FormatSamples.fillGap()))

    @Test fun `match, before the answer`() = shot("match", FormatSamples.state(FormatSamples.match()))

    @Test fun `put in order, before the answer`() = shot("order", FormatSamples.state(FormatSamples.order()))

    @Test fun `worked example, before the first step`() = shot("worked", FormatSamples.state(FormatSamples.worked()))
}
