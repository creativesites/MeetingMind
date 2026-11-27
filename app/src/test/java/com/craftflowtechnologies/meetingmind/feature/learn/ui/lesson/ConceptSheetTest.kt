package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class ConceptSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        explain: ExplainState = ExplainState(),
        onExplain: (ExplainMode) -> Unit = {},
        onPlayQuote: (Long) -> Unit = {},
        onQuizMe: () -> Unit = {},
        onNotRight: () -> Unit = {}
    ) {
        compose.setContent {
            LearnHarness {
                ConceptSheet(
                    concept = StudySamples.km,
                    explain = explain,
                    onDismiss = {},
                    onPlayQuote = onPlayQuote,
                    onExplain = onExplain,
                    onQuizMe = onQuizMe,
                    onNotRight = onNotRight
                )
            }
        }
    }

    @Test
    fun `the concept shows its name, state and the lecturer's words`() {
        show()

        compose.onNodeWithText("Km").assertIsDisplayed()
        compose.onNodeWithText("Michaelis constant").assertIsDisplayed()
        compose.onNodeWithText("Getting there").assertIsDisplayed()
        compose.onNodeWithText("In your lecturer's words").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play from 14:02").assertIsDisplayed()
    }

    @Test
    fun `each explain chip calls onExplain with its mode`() {
        val asked = mutableListOf<ExplainMode>()
        show(onExplain = { asked += it })

        ExplainMode.entries.forEach { mode -> compose.onNodeWithText(mode.label).performClick() }

        assertEquals(ExplainMode.entries, asked)
    }

    @Test
    fun `a diagram lists its boxes and arrows for screen readers`() {
        show(explain = StudySamples.drawnExplanation)

        val description = "Diagram. Boxes: Substrate, Enzyme–substrate complex, Product."
        compose.onNodeWithContentDescription(description, substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Substrate to Enzyme–substrate complex, binds.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("From 14:02 and 16:40 of your lecture", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a text explanation shows the text and its source`() {
        show(explain = StudySamples.textExplanation)

        compose.onNodeWithText(StudySamples.textExplanation.text!!).assertIsDisplayed()
        compose.onNodeWithText("From 14:02 of your lecture").assertIsDisplayed()
    }

    @Test
    fun `an error explanation says what went wrong`() {
        show(explain = ExplainState(mode = ExplainMode.ANALOGY, error = "Could not make an analogy just now."))

        compose.onNodeWithText("Could not make an analogy just now.").assertIsDisplayed()
    }

    @Test
    fun `quiz me and not right call their callbacks`() {
        var quizzed = 0
        var notRight = 0
        show(onQuizMe = { quizzed++ }, onNotRight = { notRight++ })

        compose.onNodeWithText("Quiz me on this").performClick()
        compose.onNodeWithText("Not right?").performClick()

        assertEquals(1, quizzed)
        assertEquals(1, notRight)
    }

    @Test
    fun `play from the lecturer's words seeks to the quote`() {
        val played = mutableListOf<Long>()
        show(onPlayQuote = { played += it })

        compose.onNodeWithContentDescription("Play from 14:02").performClick()

        assertEquals(listOf(14 * 60_000L + 2_000L), played)
    }
}
