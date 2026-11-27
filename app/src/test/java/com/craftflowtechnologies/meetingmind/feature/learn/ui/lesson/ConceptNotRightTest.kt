package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.craftflowtechnologies.meetingmind.core.aireport.AiFeature
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.ConceptFlags
import com.craftflowtechnologies.meetingmind.feature.learn.ui.LearnHarness
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The concept sheet's "Not right?" panel holds the studio's concept actions: rename, hide from practice, report a
 * problem, and report the AI definition (not once the learner has edited the concept).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class ConceptNotRightTest {
    @get:Rule val compose = createComposeRule()

    private val renamed = mutableListOf<String>()
    private var hiddenToggles = 0
    private var reportToggles = 0

    private fun show(flags: ConceptFlags = ConceptFlags()) {
        compose.setContent {
            LearnHarness {
                ConceptSheet(
                    concept = StudySamples.km,
                    explain = ExplainState(),
                    onDismiss = {},
                    onPlayQuote = {},
                    onExplain = {},
                    onQuizMe = {},
                    onNotRight = {},
                    flags = flags,
                    onRename = { renamed += it },
                    onToggleHidden = { hiddenToggles++ },
                    onToggleReported = { reportToggles++ },
                    aiSource = { AiReportSource(AiFeature.LECTURE_NOTES, "Km: a definition", noteId = "note-1") }
                )
            }
        }
    }

    @Test
    fun `not right opens the concept actions, each with its callback`() {
        show()
        compose.onAllNodesWithTag("concept_rename").assertCountEquals(0)

        compose.onNodeWithText("Not right?").performClick()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithText("Hide from practice").assertIsDisplayed().performClick()
        compose.onNodeWithText("Report a problem").assertIsDisplayed().performClick()

        assertEquals(1, hiddenToggles)
        assertEquals(1, reportToggles)
    }

    @Test
    fun `rename saves the new name`() {
        show()
        compose.onNodeWithText("Not right?").performClick()
        compose.onNodeWithTag("concept_rename").performClick()

        compose.onNodeWithTag("concept_rename_field").performTextReplacement("Michaelis constant")
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf("Michaelis constant"), renamed)
    }

    @Test
    fun `hidden and reported concepts offer the way back`() {
        show(ConceptFlags(hidden = true, reported = true))
        compose.onNodeWithText("Not right?").performClick()

        compose.onNodeWithText("Show in practice again").assertIsDisplayed()
        compose.onNodeWithText("Undo report").assertIsDisplayed()
    }

    @Test
    fun `the AI definition is reportable until the learner edits it`() {
        show()
        compose.onNodeWithText("Not right?").performClick()
        compose.onNodeWithText("Report this definition").assertIsDisplayed()
    }

    @Test
    fun `an edited concept is not offered as AI content to report`() {
        show(ConceptFlags(userEdited = true))
        compose.onNodeWithText("Not right?").performClick()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onAllNodesWithText("Report this definition").assertCountEquals(0)
    }
}
