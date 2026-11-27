package com.craftflowtechnologies.meetingmind.feature.learn.exam

import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exam plan's arithmetic: days left, the days themselves, due reviews first, and readiness in words. */
class ExamPlannerTest {
    private val now = ExamPlanner.startOfDay(1_795_000_000_000L) + 9 * 3_600_000L

    private fun idea(name: String, state: LearningMasteryState) = PlanIdea(name, state)

    @Test
    fun `days left counts whole days and never goes negative`() {
        assertEquals(12, ExamPlanner.daysUntil(now, now + 12 * ExamPlanner.DAY_MS + 1000))
        assertEquals(0, ExamPlanner.daysUntil(now, now - ExamPlanner.DAY_MS))
    }

    @Test
    fun `the plan has one day per day left, capped at fourteen`() {
        val plan = ExamPlanner.build(now, now + 30 * ExamPlanner.DAY_MS, 30, emptyList())
        assertEquals(ExamPlanner.MAX_PLANNED_DAYS, plan.days.size)
        assertEquals(30, plan.days.first().minutes)
    }

    @Test
    fun `due reviews come before ideas still being learned, and each idea lands on a day`() {
        val ideas = listOf(
            idea("Km", LearningMasteryState.NEW),
            idea("Vmax", LearningMasteryState.NEEDS_REVIEW),
            idea("Inhibition", LearningMasteryState.LEARNING),
            idea("Solid", LearningMasteryState.STRONG)
        )
        val plan = ExamPlanner.build(now, now + 3 * ExamPlanner.DAY_MS, 20, ideas)

        assertEquals(3, plan.days.size)
        assertEquals(listOf("Vmax"), plan.days[0].reviewNames)
        assertEquals(setOf("Km", "Inhibition"), plan.days.flatMap { it.newNames }.toSet())
        assertTrue(plan.days.flatMap { it.reviewNames + it.newNames }.none { it == "Solid" })
    }

    @Test
    fun `the aim is the day before the exam, and there is none when the exam is tomorrow`() {
        val far = ExamPlanner.build(now, now + 5 * ExamPlanner.DAY_MS, 30, emptyList())
        assertEquals(ExamPlanner.startOfDay(now + 5 * ExamPlanner.DAY_MS) - ExamPlanner.DAY_MS, far.doneByMs)

        val tomorrow = ExamPlanner.build(now, now + ExamPlanner.DAY_MS, 30, emptyList())
        assertNull(tomorrow.doneByMs)
        assertEquals(1, tomorrow.days.size)
    }

    @Test
    fun `readiness counts solid fully, getting there half, and the rest not at all`() {
        val ideas = listOf(
            idea("a", LearningMasteryState.STRONG),
            idea("b", LearningMasteryState.DEVELOPING),
            idea("c", LearningMasteryState.NEW),
            idea("d", LearningMasteryState.NEEDS_REVIEW)
        )
        assertEquals(37, ExamPlanner.readinessPercent(ideas))
        assertEquals(0, ExamPlanner.readinessPercent(emptyList()))
    }

    @Test
    fun `states are shown in study words, never as enum names`() {
        assertEquals("Solid", ExamPlanner.stateWord(LearningMasteryState.STRONG))
        assertEquals("Getting there", ExamPlanner.stateWord(LearningMasteryState.LEARNING))
        assertEquals("Worth a look", ExamPlanner.stateWord(LearningMasteryState.NEEDS_REVIEW))
        assertEquals("New", ExamPlanner.stateWord(LearningMasteryState.NEW))
    }
}
