package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionSettingsSource
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes
import com.craftflowtechnologies.meetingmind.feature.learning.CourseChoice
import com.craftflowtechnologies.meetingmind.feature.settings.companion.FakeCompanionSettings
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
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
class NewSessionSheetTest {
    @get:Rule val compose = createComposeRule()

    private val courses = listOf(
        LearningFakes.course("bio", "Biochemistry", code = "BIO 201"),
        LearningFakes.course("econ", "Economics", code = "ECON 101")
    )

    private val started = mutableListOf<Pair<StartWay, CourseChoice>>()

    private fun show(presetCourseId: String? = null, courses: List<com.craftflowtechnologies.meetingmind.core.repository.LearningRepository.LearningCourse> = this.courses) {
        compose.setContent {
            MeetMindTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalCompanionReducedMotion provides true,
                    LocalCompanionForceCanvas provides true,
                    LocalCompanionSettingsSource provides FakeCompanionSettings()
                ) {
                    NewSessionSheet(
                        courses = courses,
                        presetCourseId = presetCourseId,
                        onDismiss = {},
                        onStart = { way, choice -> started += way to choice }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the four ways in and the course picker are shown`() {
        show()
        compose.onNodeWithText("Start a study session").assertExists()
        compose.onNodeWithText("Record a lecture").assertExists()
        compose.onNodeWithText("From a video link").assertExists()
        compose.onNodeWithText("Import a recording or slides").assertExists()
        compose.onNodeWithText("Write a study note").assertExists()
        compose.onNodeWithText("Which course?").assertExists()
        compose.onNodeWithText("New course").assertExists()
    }

    @Test fun `the preset course is picked and a way lands the session in it`() {
        show(presetCourseId = "econ")
        compose.onNodeWithTag("new_session_record").performClick()
        assertEquals(listOf(StartWay.RECORD to CourseChoice.Existing("econ")), started)
    }

    @Test fun `picking another course chip changes where the session goes`() {
        show(presetCourseId = "bio")
        compose.onNodeWithTag("new_session_course_econ").performClick()
        compose.onNodeWithTag("new_session_write").performClick()
        assertEquals(listOf(StartWay.WRITE to CourseChoice.Existing("econ")), started)
    }

    @Test fun `New course asks for a name, and the way carries it`() {
        show(presetCourseId = "bio")
        compose.onNodeWithTag("new_session_course_new").performClick()
        compose.onNodeWithTag("new_session_course_name").performTextInput("Chemistry")
        compose.onNodeWithTag("new_session_import").performClick()
        assertEquals(listOf(StartWay.IMPORT to CourseChoice.New("Chemistry", "")), started)
    }

    @Test fun `a new course with no name cannot start a session`() {
        show(presetCourseId = "bio")
        compose.onNodeWithTag("new_session_course_new").performClick()
        compose.onNodeWithTag("new_session_video").performClick()
        assertEquals(emptyList<Pair<StartWay, CourseChoice>>(), started)
    }

    @Test fun `with no courses the sheet starts on New course`() {
        show(courses = emptyList())
        compose.onNodeWithTag("new_session_course_name").assertExists()
        compose.onNodeWithTag("new_session_course_code").assertExists()
    }
}
