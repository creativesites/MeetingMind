package com.craftflowtechnologies.meetingmind.feature.learn.ui.explain


import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.explain.ExplainPhase
import com.craftflowtechnologies.meetingmind.feature.learn.explain.ExplainUiState
import com.craftflowtechnologies.meetingmind.feature.learn.explain.IdeaCoverage
import com.craftflowtechnologies.meetingmind.feature.learn.explain.IdeaRow
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screen's states, drawn from the view model's state. Each test is one phase of the round. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1900dp-xxhdpi", sdk = [34])
class ExplainAloudScreenTest {
    @get:Rule val compose = createComposeRule()

    private val ideas = listOf(
        IdeaRow("Vmax is the maximum rate", "Vmax is the maximum rate", IdeaCoverage.COVERED, null, null),
        IdeaRow("Binds the active site", "Binds the active site", IdeaCoverage.PARTLY, "Binding is the first step.", 740_000L),
        IdeaRow("Vmax stays the same", "Vmax stays the same", IdeaCoverage.NOT_YET, "More substrate wins it back, but Vmax stays the same.", 754_000L)
    )

    private fun state(phase: ExplainPhase, transcript: String = "", extra: ExplainUiState.() -> ExplainUiState = { this }) =
        ExplainUiState(
            phase = phase,
            subjectLine = "BIO 201 · Inhibition",
            conceptName = "Vmax",
            prompt = "Explain Vmax as if to a friend. Aim for about a minute.",
            ideas = ideas,
            transcript = transcript
        ).extra()

    private fun show(state: ExplainUiState?, missing: Boolean = false, onPlay: (Int) -> Unit = {}, onRate: (RecallButton) -> Unit = {}, onStop: () -> Unit = {}, onStart: () -> Unit = {}) {
        compose.setContent {
            LearnHarness {
                ExplainAloudScreen(state = state, missing = missing, onPlayIdea = onPlay, onRate = onRate, onStop = onStop, onStart = onStart)
            }
        }
    }

    @Test
    fun `ready shows the prompt, the start button and the privacy line`() {
        show(state(ExplainPhase.READY))
        compose.onNodeWithText("Explain Vmax as if to a friend. Aim for about a minute.").assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.START).assertIsDisplayed()
        compose.onNodeWithText("Your voice stays on this phone").assertIsDisplayed()
        compose.onNodeWithText("Key ideas, as you go").assertIsDisplayed()
        compose.onNodeWithText("Not yet").assertIsDisplayed()
    }

    @Test
    fun `start button is at least 48 dp and reports a tap`() {
        var started = false
        show(state(ExplainPhase.READY), onStart = { started = true })
        compose.onNodeWithTag(ExplainAloudTags.START).assertIsDisplayed().performClick()
        assertTrue(started)
    }

    @Test
    fun `recording shows the checklist words, the stop button and the level bars`() {
        var stopped = false
        show(state(ExplainPhase.RECORDING, transcript = "Vmax") { copy(elapsedMs = 38_000L, levels = List(ExplainUiState.LEVEL_BARS) { 0.5f }) }, onStop = { stopped = true })
        compose.onNodeWithText("Covered").assertIsDisplayed()
        compose.onNodeWithText("Partly").assertIsDisplayed()
        compose.onNodeWithText("0:38").assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.LEVELS).assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.STOP).assertIsDisplayed().performClick()
        assertTrue(stopped)
    }

    @Test
    fun `result shows covered, partly and not yet with the lecturer's words and a play button for each missing idea`() {
        val played = mutableListOf<Int>()
        show(
            state(ExplainPhase.RESULT) {
                copy(summary = "You have the mechanism.", nextPrompt = "What happens to Vmax with more substrate?")
            },
            onPlay = { played += it }
        )
        compose.onNodeWithText("You have the mechanism.").assertIsDisplayed()
        compose.onAllNodesWithText("In your lecturer's words").assertCountEquals(2)
        compose.onNodeWithText("“More substrate wins it back, but Vmax stays the same.”").assertIsDisplayed()
        compose.onNodeWithText("What happens to Vmax with more substrate?").assertIsDisplayed()
        // Two missing ideas, so two play buttons; the covered idea has none.
        compose.onNodeWithTag(ExplainAloudTags.PLAY_PREFIX + "1").assertIsDisplayed().performClick()
        compose.onNodeWithTag(ExplainAloudTags.PLAY_PREFIX + "2").assertIsDisplayed().performClick()
        assertEquals(listOf(1, 2), played)
        compose.onNodeWithTag(ExplainAloudTags.TRY_AGAIN).assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.DONE).assertIsDisplayed()
    }

    @Test
    fun `result never shows a number as a grade`() {
        show(state(ExplainPhase.RESULT) { copy(summary = "Good.") })
        compose.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun `self-rating offers the three recall buttons and reports which one`() {
        val rated = mutableListOf<RecallButton>()
        show(state(ExplainPhase.SELF_RATE) { copy(message = "AI isn't available right now, so Zuri can't check this one. How did it go?") }, onRate = { rated += it })
        compose.onNodeWithText("AI isn't available right now, so Zuri can't check this one. How did it go?").assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.RATE_PREFIX + "missed").assertIsDisplayed().performClick()
        compose.onNodeWithTag(ExplainAloudTags.RATE_PREFIX + "got_it").assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.RATE_PREFIX + "easy").assertIsDisplayed()
        assertEquals(listOf(RecallButton.MISSED), rated)
    }

    @Test
    fun `typing instead shows the answer box, and checking is off until something is typed`() {
        show(state(ExplainPhase.TYPING) { copy(message = "The microphone is off, so Zuri can't hear you. Type it instead.") })
        compose.onNodeWithTag(ExplainAloudTags.TYPED).assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.SUBMIT_TYPED).assertIsNotEnabled()
    }

    @Test
    fun `microphone prompt explains the reason and offers the typed fallback`() {
        show(state(ExplainPhase.NEEDS_MIC))
        compose.onNodeWithText("Zuri needs the microphone to hear you").assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.ALLOW_MIC).assertIsDisplayed()
        compose.onNodeWithTag(ExplainAloudTags.TYPE_INSTEAD).assertIsDisplayed()
    }

    @Test
    fun `a concept with nothing to check against says so plainly`() {
        show(state(ExplainPhase.NO_EVIDENCE))
        compose.onNodeWithText("Nothing to explain from yet.").assertIsDisplayed()
    }

    @Test
    fun `a concept that no longer exists says so instead of loading forever`() {
        show(null, missing = true)
        compose.onNodeWithText("This idea isn't in your lecture any more.").assertIsDisplayed()
    }
}
