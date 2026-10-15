package com.craftflowtechnologies.meetingmind.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryCalculatorTest {

    private val baseTime = 1_000_000_000L
    private val dayMs = DeterministicSpacedScheduler.DAY_MS

    @Test
    fun `concepts with no attempts are in NEW state`() {
        val evaluation = MasteryCalculator.evaluate(emptyList(), null, baseTime)
        assertEquals(LearningMasteryState.NEW, evaluation.state)
        assertTrue(evaluation.reason.contains("Not yet tested"))
    }

    @Test
    fun `failed latest attempt transitions concept to NEEDS_REVIEW`() {
        val attempts = listOf(
            ActivityAttempt("1", "a1", "s1", "c1", "Good", isCorrect = true, createdAt = baseTime - 2 * dayMs),
            ActivityAttempt("2", "a1", "s1", "c1", "Wrong", isCorrect = false, createdAt = baseTime)
        )
        val evaluation = MasteryCalculator.evaluate(attempts, null, baseTime)
        assertEquals(LearningMasteryState.NEEDS_REVIEW, evaluation.state)
        assertTrue(evaluation.reason.contains("incorrect"))
    }

    @Test
    fun `overdue review transitions concept to NEEDS_REVIEW`() {
        val schedule = ReviewSchedule(
            id = "s1", activityId = "a1", sessionId = "s1", conceptId = "c1",
            dueAt = baseTime - 3 * dayMs, // 3 days overdue
            intervalDays = 7,
            createdAt = baseTime - 10 * dayMs,
            updatedAt = baseTime - 10 * dayMs
        )
        val attempts = listOf(
            ActivityAttempt("1", "a1", "s1", "c1", "Good", isCorrect = true, createdAt = baseTime - 10 * dayMs)
        )
        val evaluation = MasteryCalculator.evaluate(attempts, schedule, baseTime)
        assertEquals(LearningMasteryState.NEEDS_REVIEW, evaluation.state)
        assertTrue(evaluation.reason.contains("overdue"))
    }

    @Test
    fun `repeated recall across separate sessions transitions to DEVELOPING and STRONG`() {
        val attemptsDeveloping = listOf(
            ActivityAttempt("1", "a1", "s1", "c1", "Ans1", isCorrect = true, createdAt = baseTime - 2 * dayMs),
            ActivityAttempt("2", "a1", "s1", "c1", "Ans2", isCorrect = true, createdAt = baseTime)
        )
        val scheduleDev = ReviewSchedule(
            id = "s1", activityId = "a1", sessionId = "s1", conceptId = "c1",
            dueAt = baseTime + 3 * dayMs,
            intervalDays = 3,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        val evalDev = MasteryCalculator.evaluate(attemptsDeveloping, scheduleDev, baseTime)
        assertEquals(LearningMasteryState.DEVELOPING, evalDev.state)

        // Sustained recall across 3+ distinct days with 7+ day interval -> STRONG
        val attemptsStrong = listOf(
            ActivityAttempt("1", "a1", "s1", "c1", "Ans1", isCorrect = true, createdAt = baseTime - 14 * dayMs),
            ActivityAttempt("2", "a1", "s1", "c1", "Ans2", isCorrect = true, createdAt = baseTime - 7 * dayMs),
            ActivityAttempt("3", "a1", "s1", "c1", "Ans3", isCorrect = true, createdAt = baseTime)
        )
        val scheduleStrong = ReviewSchedule(
            id = "s1", activityId = "a1", sessionId = "s1", conceptId = "c1",
            dueAt = baseTime + 14 * dayMs,
            intervalDays = 14,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        val evalStrong = MasteryCalculator.evaluate(attemptsStrong, scheduleStrong, baseTime)
        assertEquals(LearningMasteryState.STRONG, evalStrong.state)
    }
}
