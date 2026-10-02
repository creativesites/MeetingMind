package com.craftflowtechnologies.meetingmind.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicSpacedSchedulerTest {

    private val baseTime = 1_000_000_000L
    private val dayMs = DeterministicSpacedScheduler.DAY_MS

    @Test
    fun `initial schedule starts due immediately with zero interval`() {
        val schedule = DeterministicSpacedScheduler.initialSchedule(
            activityId = "act_1",
            sessionId = "sess_1",
            conceptId = "c_1",
            now = baseTime
        )

        assertEquals(0, schedule.intervalDays)
        assertEquals(0, schedule.repetitionCount)
        assertEquals(baseTime, schedule.dueAt)
        assertTrue(schedule.isDue(baseTime))
        assertFalse(schedule.isPaused)
    }

    @Test
    fun `successful reviews follow deterministic interval ladder 1 to 3 to 7 to 14 days`() {
        var schedule = DeterministicSpacedScheduler.initialSchedule("act_1", "sess_1", "c_1", baseTime)

        // 1st successful attempt: 1 day interval
        schedule = DeterministicSpacedScheduler.calculateNextSchedule(schedule, isCorrect = true, now = baseTime)
        assertEquals(1, schedule.intervalDays)
        assertEquals(1, schedule.repetitionCount)
        assertEquals(baseTime + 1 * dayMs, schedule.dueAt)

        // 2nd successful attempt: 3 day interval
        val time2 = baseTime + 1 * dayMs
        schedule = DeterministicSpacedScheduler.calculateNextSchedule(schedule, isCorrect = true, now = time2)
        assertEquals(3, schedule.intervalDays)
        assertEquals(2, schedule.repetitionCount)
        assertEquals(time2 + 3 * dayMs, schedule.dueAt)

        // 3rd successful attempt: 7 day interval
        val time3 = time2 + 3 * dayMs
        schedule = DeterministicSpacedScheduler.calculateNextSchedule(schedule, isCorrect = true, now = time3)
        assertEquals(7, schedule.intervalDays)
        assertEquals(3, schedule.repetitionCount)
        assertEquals(time3 + 7 * dayMs, schedule.dueAt)

        // 4th successful attempt: 14 day interval
        val time4 = time3 + 7 * dayMs
        schedule = DeterministicSpacedScheduler.calculateNextSchedule(schedule, isCorrect = true, now = time4)
        assertEquals(14, schedule.intervalDays)
        assertEquals(4, schedule.repetitionCount)
        assertEquals(time4 + 14 * dayMs, schedule.dueAt)
    }

    @Test
    fun `failed review resets repetitions and conservatively sets 1 day interval`() {
        val establishedSchedule = ReviewSchedule(
            id = "s1",
            activityId = "act_1",
            sessionId = "sess_1",
            conceptId = "c_1",
            dueAt = baseTime,
            intervalDays = 14,
            repetitionCount = 4,
            easeFactor = 2.5,
            isPaused = false,
            snoozedUntil = null,
            lastReviewedAt = baseTime - 14 * dayMs,
            createdAt = baseTime - 30 * dayMs,
            updatedAt = baseTime - 14 * dayMs
        )

        val next = DeterministicSpacedScheduler.calculateNextSchedule(
            establishedSchedule,
            isCorrect = false,
            now = baseTime
        )

        assertEquals(1, next.intervalDays)
        assertEquals(0, next.repetitionCount)
        assertEquals(baseTime + 1 * dayMs, next.dueAt)
        assertTrue(next.easeFactor < establishedSchedule.easeFactor)
    }

    @Test
    fun `snooze and pause respect due check`() {
        val schedule = ReviewSchedule(
            id = "s1",
            activityId = "act_1",
            sessionId = "sess_1",
            dueAt = baseTime,
            createdAt = baseTime,
            updatedAt = baseTime
        )

        assertTrue(schedule.isDue(baseTime))
        assertTrue(schedule.isDue(baseTime + 1000))

        // Snoozed
        val snoozed = schedule.copy(snoozedUntil = baseTime + 5000)
        assertFalse(snoozed.isDue(baseTime + 1000))
        assertTrue(snoozed.isDue(baseTime + 6000))

        // Paused
        val paused = schedule.copy(isPaused = true)
        assertFalse(paused.isDue(baseTime))
        assertFalse(paused.isDue(baseTime + 10000))
    }

    @Test
    fun `previewInterval returns deterministic preview strings for null schedule`() {
        assertEquals("<1d", DeterministicSpacedScheduler.previewInterval(null, RecallRating.AGAIN, baseTime))
        assertEquals("1d", DeterministicSpacedScheduler.previewInterval(null, RecallRating.HARD, baseTime))
        assertEquals("1d", DeterministicSpacedScheduler.previewInterval(null, RecallRating.GOOD, baseTime))
        assertEquals("3d", DeterministicSpacedScheduler.previewInterval(null, RecallRating.EASY, baseTime))
    }

    @Test
    fun `previewInterval returns 1d for AGAIN regardless of repetition history`() {
        val matureSchedule = ReviewSchedule(
            id = "s1",
            activityId = "act_1",
            sessionId = "sess_1",
            dueAt = baseTime,
            intervalDays = 21,
            repetitionCount = 5,
            easeFactor = 2.6,
            createdAt = baseTime,
            updatedAt = baseTime
        )

        assertEquals("1d", DeterministicSpacedScheduler.previewInterval(matureSchedule, RecallRating.AGAIN, baseTime))
    }

    @Test
    fun `previewInterval returns ladder intervals for sequential ratings`() {
        val initialSchedule = DeterministicSpacedScheduler.initialSchedule("act_1", "sess_1", "c_1", baseTime)
        assertEquals("1d", DeterministicSpacedScheduler.previewInterval(initialSchedule, RecallRating.GOOD, baseTime))

        val rep1 = initialSchedule.copy(repetitionCount = 1, intervalDays = 1)
        assertEquals("3d", DeterministicSpacedScheduler.previewInterval(rep1, RecallRating.GOOD, baseTime))

        val rep2 = rep1.copy(repetitionCount = 2, intervalDays = 3)
        assertEquals("7d", DeterministicSpacedScheduler.previewInterval(rep2, RecallRating.GOOD, baseTime))

        val rep3 = rep2.copy(repetitionCount = 3, intervalDays = 7)
        assertEquals("14d", DeterministicSpacedScheduler.previewInterval(rep3, RecallRating.GOOD, baseTime))
    }
}
