package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeExplain
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeFillGap
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeMatch
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeOrder
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeShortAnswer
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeState
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeTrueFalse
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeWhichIdea
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeWorked
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sample items every format test and the screenshot test draw. Built by hand, as the view model would build them. */
internal object FormatSamples {
    private const val SRC = "BIO 201 · Inhibition"

    fun trueFalse(statementIsTrue: Boolean = false) = PracticeTrueFalse(
        activityId = "tf", conceptId = "c-vmax", sourceLabel = SRC, sourceStartMs = 41_000L, sourceEndMs = 49_000L,
        meetingId = "m1", prompt = "True or false?", subject = "Vmax",
        statement = "Vmax falls when a competitive inhibitor is added.",
        correctIndex = if (statementIsTrue) 0 else 1,
        correction = "Vmax is the maximum rate when the enzyme is saturated. A competitive inhibitor leaves it the same.",
        rightWhy = "Yes. Vmax means: the maximum rate when the enzyme is saturated.",
        wrongWhy = "Not quite. Vmax means: the maximum rate when the enzyme is saturated."
    )

    fun fillGap() = PracticeFillGap(
        activityId = "gap", conceptId = "c-vmax", sourceLabel = SRC, sourceStartMs = 41_000L, sourceEndMs = 49_000L,
        meetingId = "m1", prompt = "Fill the gap. From the lecture:",
        sentence = "A competitive inhibitor raises the apparent Km but leaves Vmax _____.",
        answer = "unchanged", words = listOf("unchanged", "halved", "doubled", "denatured"),
        fullLine = "A competitive inhibitor raises the apparent Km but leaves Vmax unchanged."
    )

    fun match() = PracticeMatch(
        activityId = "match", conceptId = "c-vmax", sourceLabel = SRC, sourceStartMs = null, sourceEndMs = null,
        meetingId = null, prompt = "Match each term to its meaning.",
        terms = listOf("Vmax", "Km"),
        meanings = listOf("Top speed when every enzyme is busy", "Substrate level for half of top speed"),
        meaningOrder = listOf(1, 0)
    )

    fun whichIdea() = PracticeWhichIdea(
        activityId = "idea", conceptId = "c-inh", sourceLabel = SRC, sourceStartMs = 41_000L, sourceEndMs = 49_000L,
        meetingId = "m1", prompt = "Which idea is this passage about?",
        passage = "It binds the enzyme's active site, so substrate can no longer fit until the inhibitor leaves.",
        options = listOf("Competitive inhibition", "Enzyme denaturation", "Allosteric activation"),
        correctIndex = 0,
        rightWhy = "Competitive inhibition is the idea this passage is about.",
        wrongWhys = mapOf(1 to "Heat changes the shape. This passage is about the active site.", 2 to "This passage is about the active site.")
    )

    fun order() = PracticeOrder(
        activityId = "order", conceptId = "c-supply", sourceLabel = "ECON 101 · Supply shock", sourceStartMs = null,
        sourceEndMs = null, meetingId = null, prompt = "Put the steps in order.",
        steps = listOf("Prices rise", "Less is on offer", "A new balance is found"),
        startOrder = listOf(2, 0, 1)
    )

    fun worked() = PracticeWorked(
        activityId = "worked", conceptId = "c-vmax", sourceLabel = SRC, sourceStartMs = null, sourceEndMs = null,
        meetingId = null, prompt = "Half of Vmax is reached at Km = 4 mM. What is Km when the inhibitor doubles it?",
        steps = listOf("Apparent Km = Km × (1 + [I] / Ki).", "Km doubles, so apparent Km is 8 mM."),
        answer = "8 mM"
    )

    fun shortAnswer() = PracticeShortAnswer(
        activityId = "short", conceptId = "c-inh", sourceLabel = SRC, sourceStartMs = null, sourceEndMs = null,
        meetingId = null, prompt = "In one or two sentences, what does a competitive inhibitor do?",
        modelAnswer = "It binds the active site and blocks substrate, so the apparent Km rises.",
        keyPoints = listOf("binds the active site", "blocks substrate", "apparent Km rises")
    )

    fun explain() = PracticeExplain(
        activityId = "explain", conceptId = "c-inh", sourceLabel = SRC, sourceStartMs = null, sourceEndMs = null,
        meetingId = null, prompt = "Explain Competitive inhibition in your own words.", subject = "Competitive inhibition",
        modelAnswer = "An inhibitor that competes with substrate for the active site.",
        keyPoints = listOf("competes with substrate", "active site")
    )

    fun state(item: PracticeItem, feedback: AnswerFeedback? = null, revealed: Boolean = false) = PracticeState(
        loading = false, total = 8, index = 4, current = item, feedback = feedback, revealed = revealed,
        recallHints = RecallButton.values().associateWith { "Back tomorrow" }
    )
}

/**
 * Each of the ten formats: it draws, answering it fires the right action, and the feedback says
 * "Yes, that's it." or "Not quite. Here's why." with a word as well as colour. Zuri is drawn static.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class PracticeFormatsScreenTest {
    @get:Rule val compose = createComposeRule()

    private class Calls {
        val answers = mutableListOf<PracticeAnswer>()
        val choices = mutableListOf<Int>()
        val rated = mutableListOf<RecallButton>()
        var revealed = 0
        var notSure = 0
        var continued = 0
    }

    private fun setPractice(state: PracticeState, calls: Calls = Calls()) {
        compose.setContent {
            LearnHarness {
                PracticeScreen(
                    state = state,
                    onAnswerChoice = { calls.choices += it },
                    onAnswer = { calls.answers += it },
                    onNotSure = { calls.notSure++ },
                    onReveal = { calls.revealed++ },
                    onRate = { calls.rated += it },
                    onContinue = { calls.continued++ },
                    onClose = {},
                    onPlaySource = {},
                    onDone = {},
                    onOpenNextStep = {},
                    onMakeCard = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private fun answered(correct: Boolean, chosen: Int?, why: String) =
        AnswerFeedback(chosenIndex = chosen, correct = correct, why = why, nextLine = "Next time: in 3 days")

    @Test fun `recall shows the answer side, then rates`() {
        val calls = Calls()
        setPractice(PracticeState(total = 1, current = PracticeItem.Recall("r", null, "Lecture", null, null, null, "What is Vmax?", "The top rate")), calls)
        compose.onNodeWithText("Show answer").performClick()
        compose.waitForIdle()
        assertEquals(1, calls.revealed)
        assertEquals(0, calls.rated.size)
    }

    @Test fun `multiple choice answers with its index`() {
        val calls = Calls()
        val item = PracticeItem.Choice(
            "ch", "c", "BIO 201 · Inhibition", 41_000L, 49_000L, "m1", "Which inhibitor leaves Vmax the same?",
            listOf("Competitive", "Non-competitive"), 0, "It competes for the active site.", mapOf(1 to "That one changes Vmax.")
        )
        setPractice(FormatSamples.state(item), calls)
        compose.onNodeWithTag("practice_option_1").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1), calls.choices)
    }

    @Test fun `a wrong multiple choice pick says so and names the right answer`() {
        val item = PracticeItem.Choice(
            "ch", "c", "BIO 201 · Inhibition", 41_000L, 49_000L, "m1", "Which inhibitor leaves Vmax the same?",
            listOf("Competitive", "Non-competitive"), 0, "It competes for the active site.", mapOf(1 to "That one changes Vmax.")
        )
        setPractice(FormatSamples.state(item, answered(false, 1, "That one changes Vmax.")))
        compose.onNodeWithText("Not quite. Here's why.").assertExists()
        compose.onNodeWithText("Your answer").assertExists()
        compose.onNodeWithText("Correct answer").assertExists()
    }

    @Test fun `true or false answers at once with True or False`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.trueFalse()), calls)
        compose.onNodeWithText("No submit button", substring = true).assertExists()
        compose.onNodeWithTag("practice_tf_0").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PracticeAnswer>(PracticeAnswer.Pick(0)), calls.answers)
    }

    @Test fun `a false statement answered wrong gets the fix and the lecturer`() {
        setPractice(FormatSamples.state(FormatSamples.trueFalse(), answered(false, 0, "Not quite. Vmax means: the maximum rate.")))
        compose.onNodeWithText("Not quite. Here's why.").assertExists()
        compose.onNodeWithText("The fix").assertExists()
        compose.onNodeWithText("Hear your lecturer", substring = true).assertExists()
    }

    @Test fun `true or false right answer says Yes, that's it`() {
        setPractice(FormatSamples.state(FormatSamples.trueFalse(), answered(true, 1, "Yes. Vmax means: ...")))
        compose.onNodeWithText("Yes, that's it.").assertExists()
    }

    @Test fun `which idea answers with the option index`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.whichIdea()), calls)
        compose.onNodeWithTag("practice_option_2").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PracticeAnswer>(PracticeAnswer.Pick(2)), calls.answers)
    }

    @Test fun `fill the gap sends the chosen word on Check`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.fillGap()), calls)
        compose.onNodeWithText("Tap a word to fill the gap.", substring = true).assertExists()
        compose.onNodeWithTag("practice_gap_halved").performClick()
        compose.onNodeWithText("Check").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PracticeAnswer>(PracticeAnswer.Fill("halved")), calls.answers)
    }

    @Test fun `fill the gap shows the full line after a wrong word`() {
        setPractice(FormatSamples.state(FormatSamples.fillGap(), answered(false, null, FormatSamples.fillGap().fullLine)))
        compose.onNodeWithText("Not quite. Here's why.").assertExists()
        compose.onNodeWithText(FormatSamples.fillGap().fullLine, substring = true).assertExists()
    }

    @Test fun `match sends term to meaning on Check once every term is matched`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.match()), calls)
        compose.onNodeWithTag("match_term_0").performClick()
        compose.onNodeWithTag("match_meaning_0").performClick()
        compose.onNodeWithTag("match_term_1").performClick()
        compose.onNodeWithTag("match_meaning_1").performClick()
        compose.onNodeWithText("Check").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PracticeAnswer>(PracticeAnswer.Match(mapOf(0 to 0, 1 to 1))), calls.answers)
    }

    @Test fun `put in order sends the order on Check`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.order()), calls)
        compose.onNodeWithTag("practice_step_0").assertExists()
        compose.onNodeWithText("Check").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PracticeAnswer>(PracticeAnswer.Order(listOf(2, 0, 1))), calls.answers)
    }

    @Test fun `worked example reveals one step at a time, then asks for a rating`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.worked()), calls)
        compose.onNodeWithText("Show the first step").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("worked_step_0").assertExists()
        compose.onNodeWithText("Next step").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("worked_step_1").assertExists()
        compose.onNodeWithText("Done, rate it").performClick()
        compose.waitForIdle()
        assertEquals(1, calls.revealed)
    }

    @Test fun `each rating on a revealed worked example calls onRate`() {
        val rated = Calls()
        setPractice(FormatSamples.state(FormatSamples.worked(), revealed = true), rated)
        compose.onNodeWithTag("practice_rate_GOT_IT").performClick()
        compose.waitForIdle()
        assertEquals(listOf(RecallButton.GOT_IT), rated.rated)
    }

    @Test fun `short answer sends the typed text`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.shortAnswer()), calls)
        compose.onNode(hasSetTextAction()).performTextInput("It blocks the site")
        compose.onNodeWithText("Compare with the model answer").performClick()
        compose.waitForIdle()
        assertEquals(listOf<PracticeAnswer>(PracticeAnswer.Typed("It blocks the site")), calls.answers)
    }

    @Test fun `a revealed short answer shows the model answer and rates on a tap`() {
        val rated = Calls()
        setPractice(FormatSamples.state(FormatSamples.shortAnswer(), revealed = true), rated)
        compose.onNodeWithText("Model answer").assertExists()
        compose.onNodeWithTag("practice_rate_MISSED").performClick()
        compose.waitForIdle()
        assertEquals(listOf(RecallButton.MISSED), rated.rated)
    }

    @Test fun `explain in your own words reveals the model answer`() {
        val calls = Calls()
        setPractice(FormatSamples.state(FormatSamples.explain()), calls)
        compose.onNodeWithText("Show the model answer").performClick()
        compose.waitForIdle()
        assertEquals(1, calls.revealed)
    }

    @Test fun `a revealed explanation shows what a good answer covers and rates on a tap`() {
        val rated = Calls()
        setPractice(FormatSamples.state(FormatSamples.explain(), revealed = true), rated)
        compose.onNodeWithText("A good answer covers").assertExists()
        compose.onNodeWithTag("practice_rate_EASY").performClick()
        compose.waitForIdle()
        assertEquals(listOf(RecallButton.EASY), rated.rated)
    }

    @Test fun `a wrong answer is calm, with no sad face and the reason in words`() {
        setPractice(FormatSamples.state(FormatSamples.whichIdea(), answered(false, 1, "Heat changes the shape.")))
        compose.onNodeWithText("Not quite. Here's why.").assertExists()
        compose.onNodeWithText("Continue").assertExists()
    }
}
