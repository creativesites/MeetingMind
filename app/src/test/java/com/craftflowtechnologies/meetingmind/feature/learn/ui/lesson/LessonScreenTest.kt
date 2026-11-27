package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.LessonKitStatus
import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.LessonState
import com.craftflowtechnologies.meetingmind.feature.learn.LessonTerm
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.TimeRange
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1500dp-xxhdpi", sdk = [34])
class LessonScreenTest {
    @get:Rule val compose = createComposeRule()

    /** What the screen reported, so each test can assert on the callbacks it fired. */
    private class Calls {
        var practice = 0
        var listen = 0
        var generate = 0
        var fixAudio = 0
        var playedFrom = mutableListOf<Long>()
        val opened = mutableListOf<String>()
        val explained = mutableListOf<Pair<String, ExplainMode>>()
    }

    private val calls = Calls()

    private fun show(
        state: LessonState = StudySamples.lesson(),
        explain: ExplainState = ExplainState(),
        openConceptId: String? = null
    ) {
        compose.setContent {
            LearnHarness {
                LessonScreen(
                    state = state,
                    explain = explain,
                    openConceptId = openConceptId,
                    onBack = {},
                    onPlayFrom = { calls.playedFrom += it },
                    onTogglePlay = {},
                    onHearSummary = {},
                    onOpenConcept = { calls.opened += it },
                    onCloseConcept = {},
                    onExplain = { id, mode -> calls.explained += id to mode },
                    onQuizConcept = {},
                    onReportConcept = {},
                    onListen = { calls.listen++ },
                    onPractice = { calls.practice++ },
                    onAsk = {},
                    onGenerate = { calls.generate++ },
                    onFixAudio = { calls.fixAudio++ }
                )
            }
        }
    }

    @Test
    fun `a ready lesson shows the stressed moments and their reasons`() {
        show()

        compose.onNodeWithText("3 stressed moments").assertIsDisplayed()
        compose.onNodeWithText("What your lecturer stressed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play from 14:02").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play from 41:30").assertIsDisplayed()
        compose.onNodeWithText("Said it twice").assertIsDisplayed()
        compose.onNodeWithText("Named the exam").assertIsDisplayed()
        compose.onNodeWithText("Slowed right down").assertIsDisplayed()
    }

    @Test
    fun `tapping a stressed moment plays from its time`() {
        show()

        compose.onNodeWithContentDescription("Play from 41:30").performClick()

        assertEquals(listOf(41 * 60_000L + 30_000L), calls.playedFrom)
    }

    @Test
    fun `a term in a section opens its concept`() {
        val kit = StudySamples.kit.copy(
            summary = "A short summary.",
            sections = listOf(
                LessonSection(index = 1, title = "Terms", body = "Km", terms = listOf(LessonTerm("Km", "km")))
            ),
            stressed = emptyList(),
            hardToHear = emptyList()
        )
        show(state = StudySamples.lesson(kitOverride = kit))

        compose.onNodeWithText("Km").performClick()

        assertEquals(listOf("km"), calls.opened)
    }

    @Test
    fun `the term links are built from the section text`() {
        val text = "Km is the concentration. Vmax is the top speed, and km is not a speed."
        val annotated = linkTerms(
            text,
            listOf(LessonTerm("Km", "km"), LessonTerm("Vmax", "vmax")),
            Color.Black
        ) {}

        val links = annotated.getLinkAnnotations(0, annotated.length).map { range -> (range.item as LinkAnnotation.Clickable).tag }
        assertEquals(listOf("km", "vmax", "km"), links)
    }

    @Test
    fun `the practice pill shows the count and fires onPractice`() {
        show()

        compose.onNodeWithText("Practice · 8").performClick()

        assertEquals(1, calls.practice)
    }

    @Test
    fun `the hard-to-hear line names the minutes and fixes audio`() {
        show()

        compose.onAllNodesWithText("Minutes 52–55 were hard to hear. Questions skip that part.").assertCountEquals(1)
        // Below the fold in a 1500 dp window: invoke the click action directly rather than touch it.
        compose.onNodeWithText("Listen and fix").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, calls.fixAudio)
    }

    @Test
    fun `quick check reveals its answer inline`() {
        show()

        compose.onAllNodesWithText("Reveal").onFirst().performClick()

        compose.onNodeWithText("It grabs its substrate easily. It reaches half speed at a low concentration.")
            .assertIsDisplayed()
    }

    @Test
    fun `generating shows the caption and the title`() {
        show(state = StudySamples.lesson(status = LessonKitStatus.Generating("Finding what your lecturer stressed"), kitOverride = null))

        compose.onNodeWithText("Enzyme kinetics").assertIsDisplayed()
        compose.onNodeWithText("Finding what your lecturer stressed").assertIsDisplayed()
        compose.onNodeWithText("Making your lesson").assertIsDisplayed()
    }

    @Test
    fun `needs AI explains that AI is not set up`() {
        show(state = StudySamples.lesson(status = LessonKitStatus.NeedsAi("Turn on AI in Settings to make this lesson."), kitOverride = null))

        compose.onNodeWithText("Enzyme kinetics").assertIsDisplayed()
        compose.onNodeWithText("Set up AI to make this lesson").assertIsDisplayed()
        compose.onNodeWithText("Turn on AI in Settings to make this lesson.").assertIsDisplayed()
    }

    @Test
    fun `failed shows the message and try again regenerates`() {
        show(state = StudySamples.lesson(status = LessonKitStatus.Failed("The model stopped part way."), kitOverride = null))

        compose.onNodeWithText("This lesson didn't finish").assertIsDisplayed()
        compose.onNodeWithText("The model stopped part way.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        assertEquals(1, calls.generate)
    }

    @Test
    fun `no transcript says the lecture has no words yet`() {
        show(state = StudySamples.lesson(status = LessonKitStatus.NoTranscript, kitOverride = null))

        compose.onNodeWithText("Enzyme kinetics").assertIsDisplayed()
        compose.onNodeWithText("No words to read yet").assertIsDisplayed()
    }

    @Test
    fun `the concept sheet shows its chips and each chip explains in that mode`() {
        show(openConceptId = "km")

        compose.onNodeWithText("Explain it another way").assertIsDisplayed()
        compose.onNodeWithText("Simpler").performClick()
        compose.onNodeWithText("An analogy").performClick()

        assertEquals(listOf("km" to ExplainMode.SIMPLER, "km" to ExplainMode.ANALOGY), calls.explained)
    }

    @Test
    fun `the concept sheet is shown with the explanation it is given`() {
        show(openConceptId = "km", explain = ExplainState(mode = ExplainMode.SIMPLER, text = "Km is how hungry the enzyme is."))

        compose.onNodeWithText("Km is how hungry the enzyme is.").assertIsDisplayed()
        compose.onNodeWithText("Quiz me on this").assertIsDisplayed()
        assertTrue(calls.opened.isEmpty())
    }

    @Test
    fun `a kit with no hard-to-hear ranges shows no honest line`() {
        val kit: StudyKit = StudySamples.kit.copy(hardToHear = emptyList())
        show(state = StudySamples.lesson(kitOverride = kit))

        compose.onAllNodesWithText("Listen and fix").assertCountEquals(0)
    }

    @Test
    fun `hard to hear in several parts uses the plain summary`() {
        assertEquals(
            "Parts of this recording were hard to hear. Questions skip them.",
            hardToHearLine(listOf(TimeRange(0, 60_000), TimeRange(120_000, 180_000)))
        )
        assertEquals(null, hardToHearLine(emptyList()))
    }
}
