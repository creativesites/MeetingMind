package com.example.core.notify

import com.example.core.devotional.DevotionalScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DailyAlarmsTest {

    private val at = LocalDateTime.of(2026, 9, 28, 5, 0)

    @Test fun `next trigger is later today when the time is still ahead`() {
        assertEquals(LocalDateTime.of(2026, 9, 28, 6, 30), DailyAlarms.nextTrigger(at, 6 * 60 + 30))
    }

    @Test fun `next trigger is tomorrow once the time has passed or is now`() {
        assertEquals(LocalDateTime.of(2026, 9, 29, 4, 0), DailyAlarms.nextTrigger(at, 4 * 60))
        // Firing exactly on time must set tomorrow's alarm, not the one that just went off.
        assertEquals(LocalDateTime.of(2026, 9, 29, 5, 0), DailyAlarms.nextTrigger(at, 5 * 60))
    }

    @Test fun `minutes outside a day wrap round`() {
        assertEquals(LocalDateTime.of(2026, 9, 28, 23, 0), DailyAlarms.nextTrigger(at, -60))
        assertEquals(LocalDateTime.of(2026, 9, 29, 1, 0), DailyAlarms.nextTrigger(at, 25 * 60))
    }

    @Test fun `writing is due from ninety minutes before delivery`() {
        assertFalse(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 4, 59), 6 * 60 + 30))
        assertTrue(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 5, 0), 6 * 60 + 30))
        assertTrue(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 22, 0), 6 * 60 + 30))
    }

    @Test fun `a delivery just after midnight is written from midnight, not the evening before`() {
        assertTrue(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 0, 5), 30))
        assertFalse(DevotionalScheduler.isPastDelivery(LocalDateTime.of(2026, 9, 28, 0, 5), 30))
        assertTrue(DevotionalScheduler.isPastDelivery(LocalDateTime.of(2026, 9, 28, 0, 30), 30))
    }
}
