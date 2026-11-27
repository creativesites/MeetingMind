package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.ai.learning.AnswerKind
import com.craftflowtechnologies.meetingmind.ai.learning.AskPassage
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskScope
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskTurn
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

/** The lesson's Ask sheet: answers cite the lecture, a citation plays its moment, and "Quiz me on this" leaves for practice. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class LessonAskSheetTest {
    @get:Rule val compose = createComposeRule()

    private val played = mutableListOf<Long>()
    private var quizzes = 0
    private val asked = mutableListOf<String>()

    private fun citation(number: Int, startMs: Long?, quote: String) = AskPassage(
        number = number,
        text = quote,
        evidence = LearningEvidence(startMs = startMs, endMs = startMs?.plus(9_000), quote = quote),
        sourceTitle = "Enzyme kinetics"
    )

    private fun fromSource(): LearningAnswer = LearningAnswer(
        text = "Km is the substrate concentration at half of Vmax [1].",
        kind = AnswerKind.FROM_SOURCE,
        cited = listOf(citation(1, 842_000L, "Km is not a speed. It's a concentration.")),
    )

    private fun show(turns: List<LessonAskTurn>, suggestions: List<String> = emptyList(), dark: Boolean = false) {
        compose.setContent {
            LearnHarness(dark = dark) {
                LessonAskSheet(
                    ask = LessonAskUi(
                        turns = turns,
                        scope = LessonAskScope.THIS_LECTURE,
                        courseName = null,
                        suggestions = suggestions,
                        noteId = "note-1",
                        meetingId = "meeting-1"
                    ),
                    onQuestion = { asked += it },
                    onGeneral = {},
                    onScope = {},
                    onQuiz = { quizzes++ },
                    onPlayFrom = { played += it },
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun `an answer shows its citation, and the citation plays from its moment`() {
        show(listOf(LessonAskTurn(id = "t1", question = "What is Km?", scope = LessonAskScope.THIS_LECTURE, answer = fromSource())))

        compose.onNodeWithTag("lesson_ask_answer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play from 14:02, source 1").assertIsDisplayed().performClick()

        assertEquals(listOf(842_000L), played)
    }

    @Test
    fun `a citation without a moment is a label, not a control`() {
        val untimed = LearningAnswer(
            text = "Km is a concentration [1].",
            kind = AnswerKind.FROM_SOURCE,
            cited = listOf(citation(1, null, "Km is a concentration."))
        )
        show(listOf(LessonAskTurn(id = "t1", question = "What is Km?", scope = LessonAskScope.THIS_LECTURE, answer = untimed)))

        compose.onNodeWithContentDescription("Source 1").assertIsDisplayed().performClick()

        assertTrue(played.isEmpty())
    }

    @Test
    fun `quiz me on this leaves for practice`() {
        show(listOf(LessonAskTurn(id = "t1", question = "What is Km?", scope = LessonAskScope.THIS_LECTURE, answer = fromSource())))

        compose.onNodeWithText("Quiz me on this").performClick()

        assertEquals(1, quizzes)
    }

    @Test
    fun `a starter question asks it`() {
        show(emptyList(), suggestions = listOf("What is Km?"))

        compose.onNodeWithText("TRY ASKING").assertIsDisplayed()
        compose.onNodeWithText("What is Km?").performClick()

        assertEquals(listOf("What is Km?"), asked)
    }

    @Test
    fun `the answer and its citation, in light and dark`() {
        show(listOf(LessonAskTurn(id = "t1", question = "What is Km?", scope = LessonAskScope.THIS_LECTURE, answer = fromSource())))
        compose.onNodeWithContentDescription("Play from 14:02, source 1").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/lesson_ask_paper.png")
    }

    @Test
    fun `the answer and its citation, dark`() {
        show(listOf(LessonAskTurn(id = "t1", question = "What is Km?", scope = LessonAskScope.THIS_LECTURE, answer = fromSource())), dark = true)
        compose.onNodeWithContentDescription("Play from 14:02, source 1").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/study/lesson_ask_midnight.png")
    }

    @Test
    fun `an unanswered question says it is searching`() {
        show(listOf(LessonAskTurn(id = "t1", question = "Why?", scope = LessonAskScope.THIS_LECTURE, pending = true)))

        compose.onNodeWithText("Searching this lecture…").assertIsDisplayed()
        assertEquals(0, quizzes)
    }
}
