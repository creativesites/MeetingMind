package com.craftflowtechnologies.meetingmind.core.practice

import com.craftflowtechnologies.meetingmind.core.practice.PracticeGrowth.DayState
import com.craftflowtechnologies.meetingmind.core.practice.PracticeGrowth.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pinned dates: Monday 16 November 2026 is the start of the week used throughout. */
class PracticeGrowthTest {
    private val mon = LocalDate.of(2026, 11, 16)
    private val wed = LocalDate.of(2026, 11, 18)

    @Test fun stageFollowsTheKeptCount() {
        assertEquals(Stage.SEED, PracticeGrowth.stage(0, bloomed = false))
        assertEquals(Stage.SPROUT, PracticeGrowth.stage(1, bloomed = false))
        assertEquals(Stage.SPROUT, PracticeGrowth.stage(2, bloomed = false))
        assertEquals(Stage.LEAFY, PracticeGrowth.stage(3, bloomed = false))
        assertEquals(Stage.LEAFY, PracticeGrowth.stage(5, bloomed = false))
        assertEquals(Stage.BUDDING, PracticeGrowth.stage(6, bloomed = false))
        assertEquals(Stage.BUDDING, PracticeGrowth.stage(40, bloomed = false))
    }

    @Test fun bloomedIsBloomWhateverTheCount() {
        assertEquals(Stage.BLOOM, PracticeGrowth.stage(0, bloomed = true))
        assertEquals(Stage.BLOOM, PracticeGrowth.stage(9, bloomed = true))
    }

    @Test fun aPlantStartedTodayIsNotResting() {
        assertFalse(PracticeGrowth.resting(null, startedOn = wed, rhythm = Rhythm.DAILY, today = wed))
    }

    @Test fun restingCountsSevenDaysFromTheLastKeptDay() {
        // Kept six days ago: still inside the last seven days (today-6 .. today).
        assertFalse(PracticeGrowth.resting(wed.minusDays(6), wed.minusDays(30), Rhythm.DAILY, wed))
        // Kept seven days ago: nothing in the last seven days, so resting.
        assertTrue(PracticeGrowth.resting(wed.minusDays(7), wed.minusDays(30), Rhythm.DAILY, wed))
        assertFalse(PracticeGrowth.resting(wed, wed.minusDays(30), Rhythm.DAILY, wed))
    }

    @Test fun restingWithNoDayKeptCountsFromTheStart() {
        assertFalse(PracticeGrowth.resting(null, startedOn = wed.minusDays(6), rhythm = Rhythm.THREE_A_WEEK, today = wed))
        assertTrue(PracticeGrowth.resting(null, startedOn = wed.minusDays(7), rhythm = Rhythm.THREE_A_WEEK, today = wed))
    }

    @Test fun aWeeklyPracticeRestsAfterFourteenDays() {
        assertFalse(PracticeGrowth.resting(wed.minusDays(13), wed.minusDays(60), Rhythm.WEEKLY, wed))
        assertTrue(PracticeGrowth.resting(wed.minusDays(14), wed.minusDays(60), Rhythm.WEEKLY, wed))
        // A daily practice with the same gap is resting already.
        assertTrue(PracticeGrowth.resting(wed.minusDays(14), wed.minusDays(60), Rhythm.DAILY, wed))
    }

    @Test fun weekStripShowsMondayToSundayWithEachState() {
        // Started Monday, three a week (Mon, Wed, Fri). Kept Monday and Tuesday (Tuesday is not asked for).
        val week = PracticeGrowth.week(setOf(mon, mon.plusDays(1)), Rhythm.THREE_A_WEEK, startedOn = mon, today = wed)
        assertEquals(7, week.size)
        assertEquals(mon, week.first().date)
        assertEquals(mon.plusDays(6), week.last().date)
        assertEquals(listOf(DayState.KEPT, DayState.KEPT, DayState.TODAY, DayState.OFF, DayState.UPCOMING, DayState.OFF, DayState.OFF), week.map { it.state })
        assertEquals(listOf(true, false, true, false, true, false, false), week.map { it.scheduled })
        assertEquals("MTWTFSS", week.map { it.letter }.joinToString(""))
    }

    @Test fun aMissedScheduledDayIsMissedOnlyFromTheStart() {
        // Started Tuesday, every day. Monday is before the start, so it is OFF, not MISSED.
        val week = PracticeGrowth.week(emptySet(), Rhythm.DAILY, startedOn = mon.plusDays(1), today = wed)
        assertEquals(DayState.OFF, week[0].state)
        assertEquals(DayState.MISSED, week[1].state)
        assertEquals(DayState.TODAY, week[2].state)
    }

    @Test fun keptThisWeekCountsEveryKeptDayInTheWeekOnly() {
        val days = setOf(mon.minusDays(1), mon, wed, mon.plusDays(6), mon.plusDays(7))
        // Monday, Wednesday and Sunday (22 Nov) are in this week; the 15th and 23rd are not.
        assertEquals(3, PracticeGrowth.keptThisWeek(days, wed))
    }

    @Test fun windowCountsScheduledDaysFromStartToTodayInclusive() {
        val start = LocalDate.of(2026, 11, 2) // Monday
        // Scheduled Mon, Wed, Fri from 2 to 18 Nov: Mon 2, 9, 16; Wed 4, 11, 18; Fri 6, 13 = 8.
        // Kept: 2, 4, 16 and 17 (17 is not scheduled but counts); 1 Nov is before the start.
        val days = setOf(LocalDate.of(2026, 11, 1), start, LocalDate.of(2026, 11, 4), mon, LocalDate.of(2026, 11, 17))
        assertEquals(4 to 8, PracticeGrowth.window(days, Rhythm.THREE_A_WEEK, start, wed))
    }

    @Test fun checkInDueOnlyOnceTheTimeHasCome() {
        assertFalse(PracticeGrowth.checkInDue(null, 100L))
        assertFalse(PracticeGrowth.checkInDue(200L, 199L))
        assertTrue(PracticeGrowth.checkInDue(200L, 200L))
    }

    @Test fun checkInTimeIsWeeksAhead() {
        assertEquals(null, PracticeGrowth.checkInAt(1_000L, 0))
        assertEquals(1_000L + 2 * 7 * 24 * 60 * 60 * 1000L, PracticeGrowth.checkInAt(1_000L, 2))
    }

    @Test fun rhythmLineNeverSaysStreak() {
        assertEquals("Not yet this week", PracticeGrowth.rhythmLine(0))
        assertEquals("1 day this week", PracticeGrowth.rhythmLine(1))
        assertEquals("4 days this week", PracticeGrowth.rhythmLine(4))
        listOf(0, 1, 4).forEach { assertFalse(PracticeGrowth.rhythmLine(it).contains("streak", ignoreCase = true)) }
    }
}
