package com.craftflowtechnologies.meetingmind.feature.learn.ui.course

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.learn.home.CourseUiState
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2200dp-xxhdpi", sdk = [34])
class CourseScreenTest {
    @get:Rule val compose = createComposeRule()

    private val log = mutableListOf<String>()

    private fun show(state: CourseUiState, dark: Boolean = false) {
        compose.setContent {
            MeetMindTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalCompanionReducedMotion provides true,
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionSettingsSource provides FakeCompanionSettings()
                ) {
                    CourseScreen(
                        state = state,
                        onBack = { log += "back" },
                        onPractise = { log += "practise" },
                        onOpenExamPlan = { log += "exam plan" },
                        onOpenLecture = { log += "lecture:${it.noteId}" },
                        onAddLecture = { log += "add" }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the counts carry their words`() {
        show(CourseSamples.biochemistry)
        compose.onNodeWithText("BIO 201").assertExists()
        compose.onNodeWithText("Biochemistry").assertExists()
        compose.onNodeWithText("7 lectures · 31 ideas · 12 solid").assertExists()
        compose.onNodeWithText("Exam in 12 days").assertExists()
        compose.onNodeWithText("4 cards due").assertExists()
    }

    @Test fun `how well you know it is said in words as well as the bar`() {
        show(CourseSamples.biochemistry)
        compose.onNodeWithText("How well you know it").assertExists()
        compose.onNodeWithContentDescription("12 solid, 9 getting there, 6 new, 4 worth a look").assertExists()
        compose.onNodeWithText("12 solid").assertExists()
        compose.onNodeWithText("9 getting there").assertExists()
        compose.onNodeWithText("6 new").assertExists()
        compose.onNodeWithText("4 worth a look").assertExists()
    }

    @Test fun `each lecture row shows its number, date, ideas and state word`() {
        show(CourseSamples.biochemistry)
        compose.onNodeWithText("07").assertExists()
        compose.onNodeWithText("Enzyme kinetics").assertExists()
        compose.onNodeWithText("Tue 12 Nov · 12 ideas").assertExists()
        compose.onNodeWithText("2 worth a look").assertExists()
        compose.onNodeWithText("Solid").assertExists()
        compose.onNodeWithText("Getting there").assertExists()
    }

    @Test fun `practise, exam plan, a lecture, add and back each fire their callback`() {
        show(CourseSamples.biochemistry)
        compose.onNodeWithText("Practise this course").performClick()
        compose.onNodeWithText("Exam plan").performClick()
        compose.onNodeWithTag("course_lecture_note-06").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("practise", "exam plan", "lecture:note-06", "add", "back"), log)
    }

    @Test fun `a course with no exam shows no exam chip`() {
        show(CourseSamples.biochemistry.copy(examInDays = null, dueCount = 0))
        compose.onNodeWithText("Exam in 12 days").assertDoesNotExist()
        compose.onNodeWithTag("course_exam_chip").assertDoesNotExist()
    }

    @Test fun `a course that is gone says so`() {
        show(CourseUiState(loaded = true, found = false, id = "missing"))
        compose.onNodeWithText("This course is not here any more").assertExists()
    }

    @Test fun `course paper`() = shot("paper", dark = false)
    @Test fun `course midnight`() = shot("midnight", dark = true)

    private fun shot(name: String, dark: Boolean) {
        show(CourseSamples.biochemistry, dark = dark)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/course_$name.png")
    }
}
