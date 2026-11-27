package com.craftflowtechnologies.meetingmind.core.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MilestoneDetectorTest {

    private val day = LocalClock.DayMs
    private val hour = LocalClock.HourMs

    /** Midday on an epoch day, in UTC. */
    private fun noon(epochDay: Long) = epochDay * day + 12 * hour

    @Test
    fun `the tenth note fires inside its window and nowhere else`() {
        assertNull(MilestoneDetector.forNotesKept(0))
        assertNull(MilestoneDetector.forNotesKept(9))
        assertEquals(MilestoneKind.TENTH_NOTE, MilestoneDetector.forNotesKept(10))
        assertEquals(MilestoneKind.TENTH_NOTE, MilestoneDetector.forNotesKept(14))
        assertNull(MilestoneDetector.forNotesKept(15))
        assertNull(MilestoneDetector.forNotesKept(50))
    }

    @Test
    fun `the first note is never a milestone here because First Keep celebrates it`() {
        assertNull(MilestoneDetector.forNotesKept(1))
    }

    @Test
    fun `cards are cleared only on a day something was reviewed`() {
        assertEquals(MilestoneKind.ALL_CARDS_CLEARED, MilestoneDetector.forStudy(dueNow = 0, reviewedToday = 3))
        assertNull(MilestoneDetector.forStudy(dueNow = 0, reviewedToday = 0))
        assertNull(MilestoneDetector.forStudy(dueNow = 2, reviewedToday = 12))
    }

    @Test
    fun `a reading plan is done on its last day`() {
        assertNull(MilestoneDetector.forReadingPlan(daysRead = 29, planDays = 30))
        assertEquals(MilestoneKind.READING_PLAN_DONE, MilestoneDetector.forReadingPlan(daysRead = 30, planDays = 30))
        assertNull(MilestoneDetector.forReadingPlan(daysRead = 0, planDays = 0))
    }

    @Test
    fun `seven devotional days in this calendar month count once per day`() {
        // 2026-11-18 is epoch day 20775. Six days this month, two readings on one of them: not yet.
        val clock = LocalClock(noon(20775))
        val six = listOf(20765L, 20767, 20769, 20771, 20773, 20775).map(::noon) + (noon(20775) + hour)
        assertNull(MilestoneDetector.forDevotionals(six, clock))
        val seven = six + noon(20774)
        assertEquals(MilestoneKind.SEVEN_DEVOTIONALS_IN_MONTH, MilestoneDetector.forDevotionals(seven, clock))
    }

    @Test
    fun `devotional days from last month do not count toward this month`() {
        // 2026-12-01 is epoch day 20788; the six days before it are all in November.
        val clock = LocalClock(noon(20788))
        val november = (20782L..20787L).map(::noon)
        assertNull(MilestoneDetector.forDevotionals(november + noon(20788), clock))
    }

    @Test
    fun `the month follows the local zone not UTC`() {
        // 23:30 UTC on 30 November is already 1 December at UTC+2.
        val lateNov30 = 20787 * day + 23 * hour + 30 * 60_000L
        val clock = LocalClock(lateNov30, utcOffsetMs = 2 * hour)
        assertEquals(20788L, clock.localDay)
        assertEquals(CivilDate.monthOf(20788), CivilDate.monthOf(clock.localDay))
    }

    @Test
    fun `civil dates match known calendar days`() {
        assertEquals(1970L to 1, CivilDate.yearMonth(0))
        assertEquals(1969L to 12, CivilDate.yearMonth(-1))
        assertEquals(2024L to 2, CivilDate.yearMonth(19782))
        assertEquals(2000L to 3, CivilDate.yearMonth(11017))
        assertEquals(2026L to 11, CivilDate.yearMonth(20787))
        assertEquals(2026L to 12, CivilDate.yearMonth(20788))
    }

    @Test
    fun `weeks start on Monday`() {
        // 2026-11-16 (Mon, 20773) to 2026-11-22 (Sun, 20779) is one week; 2026-11-23 (Mon) starts the next.
        val week = CivilDate.weekOf(20773)
        (20773L..20779L).forEach { assertEquals(week, CivilDate.weekOf(it)) }
        assertEquals(week + 1, CivilDate.weekOf(20780))
        assertEquals(week - 1, CivilDate.weekOf(20772))
    }
}
