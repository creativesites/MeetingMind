package com.craftflowtechnologies.meetingmind.feature.personal.sky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class SkyPatternsTest {

    private val utc = ZoneId.of("UTC")

    /** Milliseconds at a UTC wall-clock time. */
    private fun at(y: Int, mo: Int, d: Int, h: Int): Long =
        LocalDateTime.of(y, mo, d, h, 0).atZone(utc).toInstant().toEpochMilli()

    // 2026-11-03 is a Tuesday. Tuesday to Saturday: four weekdays, one weekend day.
    private val inboxDays = listOf(at(2026, 11, 3, 19), at(2026, 11, 4, 19), at(2026, 11, 5, 19), at(2026, 11, 6, 19), at(2026, 11, 7, 19))

    @Test fun `the first line says what was mentioned, how often and since when`() {
        val lines = SkyPatterns.lines("Inbox", inboxDays, utc)
        assertEquals("You've mentioned inbox in 5 reflections since 3 November.", lines.first())
    }

    @Test fun `most often on weekdays when at least sixty percent fall on weekdays`() {
        // Spread across the day, so only the days make a pattern.
        val days = listOf(at(2026, 11, 3, 8), at(2026, 11, 4, 13), at(2026, 11, 5, 19), at(2026, 11, 6, 8), at(2026, 11, 9, 13))
        val lines = SkyPatterns.lines("Inbox", days, utc)
        assertTrue(lines.contains("Most often on weekdays."))
        assertFalse(lines.any { it.contains("weekends") })
    }

    @Test fun `days and time of day make one line, not two`() {
        val lines = SkyPatterns.lines("Inbox", inboxDays, utc)
        assertEquals(listOf("You've mentioned inbox in 5 reflections since 3 November.", "Most often on weekday evenings."), lines)
    }

    @Test fun `most often at weekends when the weekend share is high`() {
        // 2026-11-07 and 2026-11-08 are a Saturday and a Sunday; a third on a Sunday the week after.
        val weekend = listOf(at(2026, 11, 7, 10), at(2026, 11, 8, 10), at(2026, 11, 14, 10))
        // All at ten in the morning, so the days and the time make one line.
        assertTrue(SkyPatterns.lines("Rest", weekend, utc).contains("Most often on weekend mornings."))
        val spread = listOf(at(2026, 11, 7, 8), at(2026, 11, 8, 14), at(2026, 11, 14, 20))
        assertTrue(SkyPatterns.lines("Rest", spread, utc).contains("Most often at weekends."))
    }

    @Test fun `no day line when the days are split evenly`() {
        val even = listOf(at(2026, 11, 3, 19), at(2026, 11, 7, 19), at(2026, 11, 4, 19), at(2026, 11, 8, 19))
        val lines = SkyPatterns.lines("Walk", even, utc)
        assertFalse(lines.any { it.contains("weekday") || it.contains("weekend") })
    }

    @Test fun `most often in the evening when at least sixty percent fall in the evening`() {
        // Evenings, split evenly between weekdays and the weekend, so only the time makes a pattern.
        val evenings = listOf(at(2026, 11, 3, 19), at(2026, 11, 7, 20), at(2026, 11, 4, 21), at(2026, 11, 8, 19))
        val lines = SkyPatterns.lines("Inbox", evenings, utc)
        assertTrue(lines.contains("Most often in the evening."))
    }

    @Test fun `no time line when the times are spread out`() {
        val spread = listOf(at(2026, 11, 3, 7), at(2026, 11, 4, 13), at(2026, 11, 5, 19), at(2026, 11, 6, 9), at(2026, 11, 7, 1))
        val lines = SkyPatterns.lines("Mixed", spread, utc)
        assertFalse(lines.any { it.startsWith("Most often in") })
    }

    @Test fun `the since date is the earliest reflection in the zone it is shown in`() {
        // 23:00 UTC on 2 November is already 3 November in Johannesburg (UTC+2).
        val times = listOf(at(2026, 11, 2, 23), at(2026, 11, 5, 9))
        val johannesburg = ZoneId.of("Africa/Johannesburg")
        assertEquals("You've mentioned rest in 2 reflections since 3 November.", SkyPatterns.lines("Rest", times, johannesburg).first())
    }

    @Test fun `lines never use judgement words or streaks`() {
        val lines = SkyPatterns.lines("Inbox", inboxDays, utc).joinToString(" ").lowercase()
        listOf("streak", "good", "bad", "failed", "missed", "should", "well done", "great").forEach {
            assertFalse("found \"$it\" in $lines", lines.contains(it))
        }
    }

    @Test fun `no reflections give no lines`() {
        assertEquals(emptyList<String>(), SkyPatterns.lines("Inbox", emptyList(), utc))
    }

    @Test fun `the month is named alone in the current year and with its year otherwise`() {
        assertEquals("November", SkyPatterns.monthName(YearMonth.of(2026, 11), YearMonth.of(2026, 11)))
        assertEquals("November 2025", SkyPatterns.monthName(YearMonth.of(2025, 11), YearMonth.of(2026, 11)))
    }
}
