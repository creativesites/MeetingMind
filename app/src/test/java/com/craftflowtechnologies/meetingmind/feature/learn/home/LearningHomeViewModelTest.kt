package com.craftflowtechnologies.meetingmind.feature.learn.home

import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.feature.learn.ContinueLesson
import com.craftflowtechnologies.meetingmind.feature.learn.RoundSummary
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.brief
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.concept
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.course
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.format
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.goal
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.lecture
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.session
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.NOW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningHomeViewModelTest {

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val bioSession = session("s-bio", "note-bio7", "Enzyme kinetics", courseName = "BIO 201 Biochemistry")

    private fun studied(round: RoundSummary? = RoundSummary(3, 2, listOf("Enzyme kinetics"))) = StudyHomeState(
        loading = false,
        round = round,
        hasAnyLesson = true,
        continueLesson = ContinueLesson(
            sessionId = "s-bio",
            title = "Enzyme kinetics",
            courseName = "BIO 201 Biochemistry",
            stoppedAt = "2 · Inhibition",
            progress = 0.45f
        ),
        studiedDays = List(7) { it < 4 },
        todayIndex = 2
    )

    @Test fun `the home merges Study's round with Learning's courses and the lecture to continue`() {
        val source = FakeLearningHomeSource(
            courses = listOf(course("bio", "Biochemistry", code = "BIO 201", sessionIds = listOf("s-bio"), dueReviewCount = 3)),
            lectures = listOf(lecture("note-bio7", "Enzyme kinetics", sessionId = "s-bio", notebookId = "bio", concepts = 4)),
            sessions = listOf(bioSession),
            concepts = listOf(concept("c1", "s-bio", LearningMasteryState.STRONG)),
            brief = brief()
        )
        val vm = LearningHomeViewModel(MutableStateFlow(studied()), source, now = { NOW }, format = format)

        val state = vm.state.value
        assertEquals(3, state.study.round!!.dueCount)
        assertEquals("Biochemistry", state.courses.single().name)
        assertEquals("BIO 201", state.courses.single().code)
        // The continue card opens by note, not by session: the lesson route takes a note id.
        assertEquals("note-bio7", state.continueLesson!!.noteId)
        assertEquals("2 · Inhibition", state.continueLesson!!.stoppedAt)
    }

    @Test fun `the plan's done-by time is shown only when there is a round to start`() {
        val source = FakeLearningHomeSource(
            sessions = listOf(bioSession),
            lectures = listOf(lecture("note-bio7", "Enzyme kinetics", sessionId = "s-bio", notebookId = null, concepts = 2)),
            brief = brief()
        )
        val withRound = LearningHomeViewModel(MutableStateFlow(studied()), source, now = { NOW }, format = format).state.value
        val caughtUp = LearningHomeViewModel(MutableStateFlow(studied(round = null)), source, now = { NOW }, format = format).state.value

        assertNull(caughtUp.doneBy)
        assertEquals(withRound.planMinutes > 0, withRound.doneBy != null)
    }

    @Test fun `a course with cards due and an exam says both in plain words`() {
        val source = FakeLearningHomeSource(
            courses = listOf(course("bio", "Biochemistry", code = "BIO 201", sessionIds = listOf("s-bio"), dueReviewCount = 4)),
            sessions = listOf(bioSession),
            concepts = listOf(
                concept("c1", "s-bio", LearningMasteryState.STRONG),
                concept("c2", "s-bio", LearningMasteryState.STRONG),
                concept("c3", "s-bio", LearningMasteryState.LEARNING),
                concept("c4", "s-bio", LearningMasteryState.NEEDS_REVIEW)
            ),
            brief = brief(goal("g1", "bio", examInDays = 12) to 12)
        )
        val home = LearningHomeViewModel(MutableStateFlow(studied()), source, now = { NOW }, format = format).state.value

        val bio = home.courses.single()
        assertEquals(2, bio.solid)
        assertEquals(4, bio.total)
        assertEquals("4 due · exam in 12 days", bio.status)
        assertTrue(bio.needsAttention)
    }

    @Test fun `a course with nothing due and no exam is all caught up`() {
        val source = FakeLearningHomeSource(
            courses = listOf(course("econ", "Economics", code = "ECON 101", sessionIds = listOf("s-econ"))),
            brief = brief()
        )
        val home = LearningHomeViewModel(MutableStateFlow(studied()), source, now = { NOW }, format = format).state.value

        assertEquals("All caught up", home.courses.single().status)
        assertFalse(home.courses.single().needsAttention)
    }

    @Test fun `recent lectures say whether the lesson is ready and open as a lesson only when there is one`() {
        val source = FakeLearningHomeSource(
            lectures = listOf(
                lecture("note-a", "Supply shocks", sessionId = "s-a", notebookId = null, concepts = 5, updatedAt = NOW),
                lecture("note-b", "Acids and bases", sessionId = "s-b", notebookId = null, concepts = 0, updatedAt = NOW - 1),
                lecture("note-c", "Cell signalling", sessionId = null, notebookId = null, updatedAt = NOW - 2)
            )
        )
        val recent = LearningHomeViewModel(MutableStateFlow(studied()), source, now = { NOW }, format = format).state.value.recent

        assertEquals(listOf("note-a", "note-b", "note-c"), recent.map { it.noteId })
        assertEquals("Lesson ready", recent[0].statusLabel)
        assertEquals("Making lesson…", recent[1].statusLabel)
        assertTrue(recent[0].isLesson)
        assertFalse(recent[2].isLesson)
    }

    @Test fun `a new lecture from the source reaches the home without reopening it`() {
        val source = FakeLearningHomeSource()
        val vm = LearningHomeViewModel(MutableStateFlow(studied()), source, now = { NOW }, format = format)
        assertTrue(vm.state.value.recent.isEmpty())

        source.lecturesFlow.value = listOf(lecture("note-new", "Protein folding", sessionId = "s-n", notebookId = null))
        assertEquals(listOf("note-new"), vm.state.value.recent.map { it.noteId })
    }
}
