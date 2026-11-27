package com.craftflowtechnologies.meetingmind.feature.learn.home

import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.NOW
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.concept
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.course
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.format
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.lecture
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningFakes.session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningHomeModelTest {

    @Test fun `course status says due and exam in plain words, and falls back to caught up`() {
        assertEquals("4 due · exam in 12 days", courseStatus(hasLectures = true, dueCount = 4, examInDays = 12))
        assertEquals("4 due", courseStatus(hasLectures = true, dueCount = 4, examInDays = null))
        assertEquals("Exam in 1 day", courseStatus(hasLectures = true, dueCount = 0, examInDays = 1))
        assertEquals("Exam today", courseStatus(hasLectures = true, dueCount = 0, examInDays = 0))
        assertEquals("All caught up", courseStatus(hasLectures = true, dueCount = 0, examInDays = null))
        assertEquals("No lectures yet", courseStatus(hasLectures = false, dueCount = 0, examInDays = null))
    }

    @Test fun `a lecture's state is one word, and a worth-a-look idea is never hidden`() {
        assertEquals("2 worth a look", CourseBuilder.lectureStateWord(listOf(StarState.SHINING, StarState.WORTH_A_LOOK, StarState.WORTH_A_LOOK)))
        assertEquals("Solid", CourseBuilder.lectureStateWord(listOf(StarState.SHINING, StarState.SHINING)))
        assertEquals("Getting there", CourseBuilder.lectureStateWord(listOf(StarState.SHINING, StarState.GROWING)))
        assertEquals("New", CourseBuilder.lectureStateWord(listOf(StarState.NEW)))
        assertEquals("New", CourseBuilder.lectureStateWord(emptyList()))
    }

    @Test fun `a course counts its ideas by state and lists its lectures newest first with the course's numbers`() {
        val inputs = LearningInputs(
            courses = listOf(course("bio", "Biochemistry", code = "BIO 201", sessionIds = listOf("s1", "s2", "s3"), dueReviewCount = 4)),
            lectures = listOf(
                lecture("n1", "Amino acids", sessionId = "s1", notebookId = "bio", updatedAt = NOW - 2_000),
                lecture("n2", "Protein folding", sessionId = "s2", notebookId = "bio", updatedAt = NOW - 1_000),
                lecture("n3", "Enzyme kinetics", sessionId = "s3", notebookId = "bio", updatedAt = NOW)
            ),
            sessions = listOf(session("s1", "n1", "A"), session("s2", "n2", "B"), session("s3", "n3", "C")),
            concepts = listOf(
                concept("a", "s1", LearningMasteryState.LEARNING),
                concept("b", "s2", LearningMasteryState.STRONG),
                concept("c", "s2", LearningMasteryState.STRONG),
                concept("d", "s3", LearningMasteryState.NEEDS_REVIEW),
                concept("e", "s3", LearningMasteryState.NEW)
            ),
            brief = null
        )
        val state = CourseBuilder.build("bio", inputs, NOW, format)

        assertTrue(state.found)
        assertEquals("BIO 201", state.code)
        assertEquals(3, state.lectureCount)
        assertEquals(5, state.ideaCount)
        assertEquals(2, state.solid)
        assertEquals(1, state.growing)
        assertEquals(1, state.new)
        assertEquals(1, state.worth)
        assertNull(state.examInDays)
        assertEquals(listOf("Enzyme kinetics", "Protein folding", "Amino acids"), state.lectures.map { it.title })
        assertEquals(listOf(3, 2, 1), state.lectures.map { it.number })
        assertEquals(listOf("1 worth a look", "Solid", "Getting there"), state.lectures.map { it.stateWord })
    }

    @Test fun `a course that is gone reports not found rather than an empty course`() {
        val state = CourseBuilder.build(
            "missing",
            LearningInputs(emptyList(), emptyList(), emptyList(), emptyList(), null),
            NOW,
            format
        )
        assertTrue(state.loaded)
        assertFalse(state.found)
    }

    @Test fun `the home shows no plan time when there is nothing to start`() {
        val built = LearningHomeBuilder.build(
            study = com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState(loading = false, hasAnyLesson = false),
            inputs = LearningInputs(emptyList(), emptyList(), emptyList(), emptyList(), null),
            now = NOW,
            format = format
        )
        assertNull(built.doneBy)
        assertTrue(built.courses.isEmpty())
        assertTrue(built.recent.isEmpty())
        assertEquals(false, built.hasAnyLesson)
    }
}
