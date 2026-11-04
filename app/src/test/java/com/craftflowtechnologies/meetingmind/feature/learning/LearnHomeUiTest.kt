package com.craftflowtechnologies.meetingmind.feature.learning

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.CourseProperties
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class LearnHomeUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `learn home renders header, companion brief and action tiles`() {
        var recordClicked = false
        var audioCramClicked = false
        var writeClicked = false

        val sampleCourses = listOf(
            LearningRepository.LearningCourse(
                notebook = Notebook(
                    id = "nb1",
                    name = "Calculus II",
                    space = NotebookSpace.LEARNING,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                ),
                properties = CourseProperties(
                    courseCode = "MATH 201",
                    domain = SubjectDomain.MATH_STATS
                ),
                noteCount = 5,
                sessionCount = 3
            )
        )

        val sampleSessions = listOf(
            LearningSession(
                id = "s1",
                noteId = "n1",
                title = "Taylor Series",
                courseName = "Calculus II",
                subjectDomain = SubjectDomain.MATH_STATS.name
            )
        )

        compose.setContent {
            CompositionLocalProvider(
                LocalCompanionForceCanvas provides true,
                LocalCompanionReducedMotion provides true
            ) {
                MeetMindTheme {
                    LearnHomeContent(
                        activeTab = LearnTab.TODAY,
                        courses = sampleCourses,
                        sessions = sampleSessions,
                        dailyBrief = null,
                        goals = emptyList(),
                        examPriorities = emptyList(),
                        onSelectTab = {},
                        onCreateCourse = { _, _, _ -> },
                        actions = LearnHomeActions(
                            onRecordLecture = { recordClicked = true },
                            onAudioCram = { audioCramClicked = true },
                            onWriteStudyNote = { writeClicked = true }
                        )
                    )
                }
            }
        }

        compose.onNodeWithTag("learn_home").assertIsDisplayed()
        compose.onNodeWithTag("learn_hero_card").assertIsDisplayed()
        compose.onNodeWithTag("learn_record_lecture").assertIsDisplayed().performClick()
        assertTrue(recordClicked)

        compose.onNodeWithTag("learn_audio_cram").assertIsDisplayed().performClick()
        assertTrue(audioCramClicked)

        compose.onNodeWithTag("learn_write_study_note").assertIsDisplayed().performClick()
        assertTrue(writeClicked)

        compose.onNodeWithTag("learn_circles").assertIsDisplayed()
    }

    @Test
    fun `learn home tab navigation switches views`() {
        var selectedTab = LearnTab.TODAY

        compose.setContent {
            CompositionLocalProvider(
                LocalCompanionForceCanvas provides true,
                LocalCompanionReducedMotion provides true
            ) {
                MeetMindTheme {
                    LearnHomeContent(
                        activeTab = selectedTab,
                        courses = emptyList(),
                        sessions = emptyList(),
                        dailyBrief = null,
                        goals = emptyList(),
                        examPriorities = emptyList(),
                        onSelectTab = { selectedTab = it },
                        onCreateCourse = { _, _, _ -> },
                        actions = LearnHomeActions()
                    )
                }
            }
        }

        compose.onNodeWithTag("learn_tab_courses").assertIsDisplayed().performClick()
        assertTrue(selectedTab == LearnTab.COURSES)

        compose.onNodeWithTag("learn_tab_practice").assertIsDisplayed().performClick()
        assertTrue(selectedTab == LearnTab.PRACTICE)

        compose.onNodeWithTag("learn_tab_exam").assertIsDisplayed().performClick()
        assertTrue(selectedTab == LearnTab.EXAM)
    }
}
