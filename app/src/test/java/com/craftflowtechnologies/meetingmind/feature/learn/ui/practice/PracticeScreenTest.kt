package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeState
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Behaviour of the practice round screens. Zuri is drawn static (gotcha 2 in agents/TESTING.md). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class PracticeScreenTest {
    @get:Rule val compose = createComposeRule()

    /** Callbacks the tests can read back. Anything not set is a no-op. */
    private class Calls {
        val answers = mutableListOf<Int>()
        val rated = mutableListOf<RecallButton>()
        var notSure = 0
        var reveal = 0
        var continued = 0
        var done = 0
        var closed = 0
        var madeCard = 0
    }

    private fun setPractice(state: PracticeState, calls: Calls = Calls()) {
        compose.setContent {
            CompositionLocalProvider(
                LocalCompanionSettingsSource provides FakeCompanionSettings(),
                LocalCompanionReducedMotion provides true,
                LocalCompanionForceCanvas provides true
            ) {
                MeetMindTheme(darkTheme = false) {
                    PracticeScreen(
                        state = state,
                        onAnswerChoice = { calls.answers += it },
                        onNotSure = { calls.notSure++ },
                        onReveal = { calls.reveal++ },
                        onRate = { calls.rated += it },
                        onContinue = { calls.continued++ },
                        onClose = { calls.closed++ },
                        onPlaySource = {},
                        onDone = { calls.done++ },
                        onOpenNextStep = {},
                        onMakeCard = { calls.madeCard++ }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `tapping an option answers at once with its index and there is no submit`() {
        val calls = Calls()
        setPractice(PracticeSamples.question, calls)
        compose.onNodeWithText("Submit", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Tap an answer. No submit button.", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("practice_option_2").performClick()
        compose.waitForIdle()
        assertEquals(listOf(2), calls.answers)
    }

    @Test fun `a right answer says Correct and Yes, that's it`() {
        setPractice(PracticeSamples.right)
        compose.onNodeWithText("Yes, that's it.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Correct", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Not quite. Here's why.", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Next time: in 3 days", useUnmergedTree = true).assertExists()
    }

    @Test fun `a wrong answer says Your answer, Correct answer and Not quite, and offers the lecturer`() {
        val calls = Calls()
        setPractice(PracticeSamples.wrong, calls)
        compose.onNodeWithText("Not quite. Here's why.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Your answer", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Correct answer", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Yes, that's it.", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Hear your lecturer · 41:30 · 8 s", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("This one comes back in 3 questions", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        assertEquals(1, calls.continued)
    }

    @Test fun `the not sure link is offered before answering`() {
        val calls = Calls()
        setPractice(PracticeSamples.question, calls)
        compose.onNodeWithText("Not sure").performClick()
        compose.waitForIdle()
        assertEquals(1, calls.notSure)
    }

    @Test fun `tapping Show answer calls onReveal`() {
        val calls = Calls()
        setPractice(PracticeSamples.recallHidden, calls)
        compose.onNodeWithText("Show answer").performClick()
        compose.waitForIdle()
        assertEquals(1, calls.reveal)
    }

    @Test fun `before the reveal there are no ratings`() {
        setPractice(PracticeSamples.recallHidden)
        compose.onNodeWithText("How did that go?", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("practice_rate_GOT_IT").assertDoesNotExist()
    }

    @Test fun `after the reveal the answer shows with the three ratings and their hints`() {
        setPractice(PracticeSamples.recallRevealed)
        compose.onNodeWithText("Answer", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("How did that go?", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("again soon", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("in 3 days", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("in a week", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Be honest. Missing it now is how it sticks.", useUnmergedTree = true).assertExists()
    }

    @Test fun `each rating button calls onRate with its RecallButton`() {
        val calls = Calls()
        setPractice(PracticeSamples.recallRevealed, calls)
        compose.onNodeWithTag("practice_rate_MISSED").performClick()
        compose.onNodeWithTag("practice_rate_GOT_IT").performClick()
        compose.onNodeWithTag("practice_rate_EASY").performClick()
        compose.waitForIdle()
        assertEquals(listOf(RecallButton.MISSED, RecallButton.GOT_IT, RecallButton.EASY), calls.rated)
    }

    @Test fun `round done shows the result and Done for today fires onDone`() {
        val calls = Calls()
        setPractice(PracticeSamples.done, calls)
        compose.onNodeWithText("Round done.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("6 of 8 on the first try · 2 new stars", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("back tomorrow", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("One good next step", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Done for today").performClick()
        compose.waitForIdle()
        assertEquals(1, calls.done)
        assertTrue(calls.madeCard == 0)
    }

    @Test fun `an empty round shows the calm message, not a question`() {
        setPractice(PracticeState(emptyMessage = "Record a lecture to practise."))
        compose.onNodeWithText("Nothing to practise yet.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Record a lecture to practise.", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("practice_option_0").assertDoesNotExist()
    }
}
