package com.craftflowtechnologies.meetingmind.core.notify

import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalScheduler
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

    @Test fun `writing is due from one minute past midnight, whatever the delivery time`() {
        assertFalse(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 0, 0)))
        assertTrue(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 0, 1)))
        assertTrue(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 4, 59)))
        assertTrue(DevotionalScheduler.isPastWriteTime(LocalDateTime.of(2026, 9, 28, 22, 0)))
    }

    @Test fun `delivery is judged on its own time`() {
        assertFalse(DevotionalScheduler.isPastDelivery(LocalDateTime.of(2026, 9, 28, 0, 5), 30))
        assertTrue(DevotionalScheduler.isPastDelivery(LocalDateTime.of(2026, 9, 28, 0, 30), 30))
    }

    @Test fun `the write alarm is set for 00 01 local time`() {
        val next = DailyAlarms.nextTrigger(LocalDateTime.of(2026, 9, 28, 14, 20), com.craftflowtechnologies.meetingmind.core.devotional.DevotionalDelivery.writeMinute())
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 1), next)
    }
}
