package com.craftflowtechnologies.meetingmind.feature.learn.ui.ask

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The one Ask sheet: scope chips, cited answers that play or open, "Quiz me on this", and reporting. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class AskSheetTest {
    @get:Rule val compose = createComposeRule()

    private val scopes = mutableListOf<AskSheetScope>()
    private val cited = mutableListOf<AskCiteTarget>()
    private val quizzed = mutableListOf<String>()
    private val asked = mutableListOf<String>()

    private fun answered(target: AskCiteTarget? = AskCiteTarget.Play(842_000L)) = AskSheetTurn(
        id = "t1",
        question = "What is Km?",
        scopeSentence = "this lecture",
        answer = "Km is the substrate concentration at half of Vmax [1].",
        canQuiz = true,
        citations = listOf(AskCitation(1, "Enzyme kinetics", "14:02", "Km is not a speed.", target)),
        reportText = "Km is the substrate concentration at half of Vmax [1].",
        reportNoteId = "note-1",
        reportMeetingId = "meeting-1"
    )

    private fun ui(
        turns: List<AskSheetTurn>,
        scope: AskSheetScope = AskSheetScope.LECTURE,
        scopes: List<AskSheetScope> = listOf(AskSheetScope.LECTURE, AskSheetScope.COURSE, AskSheetScope.ALL),
        suggestions: List<String> = emptyList()
    ) = AskSheetUi(title = "Ask about this lecture", turns = turns, scope = scope, scopes = scopes, suggestions = suggestions)

    private fun show(ui: AskSheetUi, dark: Boolean = false) {
        compose.setContent {
            LearnHarness(dark = dark) {
                AskSheet(
                    ui = ui,
                    onQuestion = { asked += it },
                    onScope = { scopes += it },
                    onGeneral = {},
                    onQuiz = { quizzed += it },
                    onCite = { cited += it },
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun `switching the scope chip reports the chosen scope`() {
        show(ui(emptyList()))

        compose.onNodeWithTag("ask_scope_course").assertIsDisplayed().performClick()
        compose.onNodeWithTag("ask_scope_all").performClick()

        assertEquals(listOf(AskSheetScope.COURSE, AskSheetScope.ALL), scopes)
    }

    @Test
    fun `a single scope hides the chip row`() {
        show(ui(emptyList(), scope = AskSheetScope.ALL, scopes = listOf(AskSheetScope.ALL)))

        compose.onNodeWithText("All my learning").assertDoesNotExist()
    }

    @Test
    fun `a citation with a moment plays it`() {
        show(ui(listOf(answered())))

        compose.onNodeWithContentDescription("Play from 14:02, source 1").assertIsDisplayed().performClick()

        assertEquals(listOf<AskCiteTarget>(AskCiteTarget.Play(842_000L)), cited)
    }

    @Test
    fun `a citation to a lesson opens that lesson`() {
        show(ui(listOf(answered(AskCiteTarget.OpenLesson("note-9")))))

        compose.onNodeWithContentDescription("Open the lesson, source 1: Enzyme kinetics").assertIsDisplayed().performClick()

        assertEquals(listOf<AskCiteTarget>(AskCiteTarget.OpenLesson("note-9")), cited)
    }

    @Test
    fun `a citation without a target is only a label`() {
        show(ui(listOf(answered(target = null))))

        compose.onNodeWithContentDescription("Source 1: Enzyme kinetics").assertIsDisplayed()

        assertTrue(cited.isEmpty())
    }

    @Test
    fun `quiz me on this asks for practice from that answer`() {
        show(ui(listOf(answered())))

        compose.onNodeWithText("Quiz me on this").performClick()

        assertEquals(listOf("t1"), quizzed)
    }

    @Test
    fun `a starter question asks it`() {
        show(ui(emptyList(), suggestions = listOf("What is Km?")))

        compose.onNodeWithText("Try asking").assertIsDisplayed()
        compose.onNodeWithText("What is Km?").performClick()

        assertEquals(listOf("What is Km?"), asked)
    }

    @Test
    fun `an unanswered question says it is searching`() {
        show(ui(listOf(AskSheetTurn(id = "t2", question = "Why?", scopeSentence = "this course", pending = true))))

        compose.onNodeWithText("Searching this course…").assertIsDisplayed()
        assertTrue(quizzed.isEmpty())
    }

    @Test
    fun `the answer and its citation, in light`() {
        show(ui(listOf(answered())))
        compose.onNodeWithContentDescription("Play from 14:02, source 1").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/ask_sheet_paper.png")
    }

    @Test
    fun `the answer and its citation, in dark`() {
        show(ui(listOf(answered())), dark = true)
        compose.onNodeWithContentDescription("Play from 14:02, source 1").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/ask_sheet_midnight.png")
    }
}
