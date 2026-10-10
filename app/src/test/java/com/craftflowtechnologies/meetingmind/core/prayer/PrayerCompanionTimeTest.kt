package com.craftflowtechnologies.meetingmind.core.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PrayerCompanionTimeTest {
    @Test fun `part of day follows the local hour`() {
        assertEquals("morning", PrayerCompanion.partOfDay(7))
        assertEquals("afternoon", PrayerCompanion.partOfDay(14))
        assertEquals("evening", PrayerCompanion.partOfDay(19))
        assertEquals("night", PrayerCompanion.partOfDay(23))
        assertEquals("night", PrayerCompanion.partOfDay(2))
    }

    @Test fun `an evening session is told it is evening, not morning`() {
        val ctx = PrayerCompanion.timeContext(LocalDateTime.of(2026, 10, 10, 21, 30))
        assertTrue(ctx, ctx.contains("Saturday"))
        assertTrue(ctx, ctx.contains("9:30 PM"))
        assertTrue(ctx, ctx.contains("night where they are"))
        assertTrue(ctx, ctx.contains("don't assume it is morning"))
    }
}
