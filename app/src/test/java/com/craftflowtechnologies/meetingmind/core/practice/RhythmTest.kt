package com.craftflowtechnologies.meetingmind.core.practice

import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RhythmTest {
    private val utc = ZoneId.of("UTC")

    @Test fun labelsArePlainAndPerWeekIsRight() {
        assertEquals("Every day", Rhythm.DAILY.label)
        assertEquals("Weekdays", Rhythm.WEEKDAYS.label)
        assertEquals("3 times a week", Rhythm.THREE_A_WEEK.label)
        assertEquals("Once a week", Rhythm.WEEKLY.label)
        assertEquals(listOf(7, 5, 3, 1), Rhythm.entries.map { it.perWeek })
    }

    @Test fun scheduledDays() {
        assertEquals(DayOfWeek.entries.toSet(), Rhythm.DAILY.scheduledDays(DayOfWeek.TUESDAY))
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            Rhythm.WEEKDAYS.scheduledDays(DayOfWeek.SUNDAY)
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), Rhythm.THREE_A_WEEK.scheduledDays(DayOfWeek.SATURDAY))
        assertEquals(setOf(DayOfWeek.SATURDAY), Rhythm.WEEKLY.scheduledDays(DayOfWeek.SATURDAY))
    }

    @Test fun rrulesForEachRhythm() {
        assertEquals("FREQ=DAILY", Rhythm.DAILY.rrule(DayOfWeek.MONDAY))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", Rhythm.WEEKDAYS.rrule(DayOfWeek.MONDAY))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", Rhythm.THREE_A_WEEK.rrule(DayOfWeek.TUESDAY))
        assertEquals("FREQ=WEEKLY;BYDAY=TU", Rhythm.WEEKLY.rrule(DayOfWeek.TUESDAY))
    }

    @Test fun parseFallsBackToEveryDay() {
        assertEquals(Rhythm.THREE_A_WEEK, Rhythm.parse("THREE_A_WEEK"))
        assertEquals(Rhythm.DAILY, Rhythm.parse("SOMETIMES"))
        assertEquals(Rhythm.DAILY, Rhythm.parse(null))
    }

    /** The task's stepper must move a repeating practice through its own days (the multi-day BYDAY case). */
    @Test fun taskStepsThroughEachRhythmsDays() {
        // Fri 20 Nov 2026 → Mon 23 Nov for weekdays; nine o'clock kept.
        assertEquals(LocalDate.of(2026, 11, 23), nextDueAfter(Rhythm.WEEKDAYS, LocalDate.of(2026, 11, 20)))
        // Mon 16 Nov → Wed 18 Nov, then Fri 20 Nov, for three a week.
        assertEquals(LocalDate.of(2026, 11, 18), nextDueAfter(Rhythm.THREE_A_WEEK, LocalDate.of(2026, 11, 16)))
        assertEquals(LocalDate.of(2026, 11, 20), nextDueAfter(Rhythm.THREE_A_WEEK, LocalDate.of(2026, 11, 18)))
        // Fri 20 Nov → Mon 23 Nov for three a week.
        assertEquals(LocalDate.of(2026, 11, 23), nextDueAfter(Rhythm.THREE_A_WEEK, LocalDate.of(2026, 11, 20)))
        // Once a week on Tuesday: Tue 17 Nov → Tue 24 Nov.
        assertEquals(LocalDate.of(2026, 11, 24), nextDueAfter(Rhythm.WEEKLY, LocalDate.of(2026, 11, 17)))
        // Every day: Sat 21 Nov → Sun 22 Nov.
        assertEquals(LocalDate.of(2026, 11, 22), nextDueAfter(Rhythm.DAILY, LocalDate.of(2026, 11, 21)))
    }

    private fun nextDueAfter(rhythm: Rhythm, day: LocalDate): LocalDate {
        val startDay = LocalDate.of(2026, 11, 16).dayOfWeek // any start for the weekly stepper below
        val rule = rhythm.rrule(if (rhythm == Rhythm.WEEKLY) day.dayOfWeek else startDay)
        val due = day.atTime(9, 0).atZone(utc).toInstant().toEpochMilli()
        val next = TaskRules.nextOccurrence(Task("t", "practice", dueAt = due, repeatRule = rule), utc)!!
        return java.time.Instant.ofEpochMilli(next.dueAt!!).atZone(utc).toLocalDate()
    }
}
