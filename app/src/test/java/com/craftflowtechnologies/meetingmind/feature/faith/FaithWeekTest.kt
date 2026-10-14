package com.craftflowtechnologies.meetingmind.feature.faith

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FaithWeekTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
    private fun at(date: String) = Instant.parse("${date}T09:00:00Z").toEpochMilli()

    @Test fun theWeekEndsTodayAndRunsSevenDays() {
        val days = FaithWeek.lastSevenDays(emptyList(), now, zone)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 8), days.first().date)
        assertEquals(LocalDate.of(2026, 10, 14), days.last().date)
    }

    @Test fun aDayIsKeptWhenAFaithNoteWasWrittenThatDay() {
        val days = FaithWeek.lastSevenDays(listOf(at("2026-10-14"), at("2026-10-11")), now, zone)
        assertTrue(days.last().kept)
        assertTrue(days.single { it.date == LocalDate.of(2026, 10, 11) }.kept)
        assertFalse(days.single { it.date == LocalDate.of(2026, 10, 12) }.kept)
    }

    @Test fun manyNotesOnOneDayCountAsOneKeptDay() {
        val days = FaithWeek.lastSevenDays(listOf(at("2026-10-13"), at("2026-10-13")), now, zone)
        assertEquals(1, FaithWeek.keptCount(days))
    }

    @Test fun notesOlderThanTheWeekDoNotCount() {
        val days = FaithWeek.lastSevenDays(listOf(at("2026-10-01")), now, zone)
        assertEquals(0, FaithWeek.keptCount(days))
    }
}
