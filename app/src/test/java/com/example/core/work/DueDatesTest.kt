package com.example.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class DueDatesTest {

    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.timeInMillis

    /** Tuesday 29 September 2026, mid-morning. */
    private val tuesday = Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, 29, 10, 30) }.timeInMillis

    private fun parse(text: String) = DueDates.parse(text, tuesday)

    @Test fun `relative days`() {
        assertEquals(day(2026, 9, 29), parse("today"))
        assertEquals(day(2026, 9, 29), parse("by end of day"))
        assertEquals(day(2026, 9, 30), parse("tomorrow"))
        assertEquals(day(2026, 10, 1), parse("the day after tomorrow"))
        assertEquals(day(2026, 10, 2), parse("in 3 days"))
        assertEquals(day(2026, 10, 13), parse("in two weeks"))
    }

    @Test fun `weekdays mean the coming one`() {
        assertEquals(day(2026, 10, 2), parse("by Friday"))
        assertEquals(day(2026, 10, 2), parse("Fri"))
        assertEquals(day(2026, 10, 5), parse("Monday"))
        // Said on a Tuesday, "Tuesday" is next week's, not today.
        assertEquals(day(2026, 10, 6), parse("Tuesday"))
    }

    @Test fun `next weekday skips this week`() {
        assertEquals(day(2026, 10, 9), parse("next Friday"))
        assertEquals(day(2026, 10, 5), parse("next Monday"))
    }

    @Test fun `weeks and months`() {
        assertEquals(day(2026, 10, 2), parse("end of the week"))
        assertEquals(day(2026, 10, 5), parse("next week"))
        assertEquals(day(2026, 9, 30), parse("end of the month"))
        assertEquals(day(2026, 10, 1), parse("next month"))
    }

    @Test fun `named dates`() {
        assertEquals(day(2026, 10, 15), parse("October 15th"))
        assertEquals(day(2026, 10, 15), parse("15 Oct"))
        assertEquals(day(2026, 10, 15), parse("2026-10-15"))
        // A date well past this year means next year.
        assertEquals(day(2027, 3, 1), parse("March 1"))
    }

    @Test fun `vague words are not days`() {
        assertNull(parse("soon"))
        assertNull(parse("after the launch"))
        assertNull(parse(""))
        assertNull(DueDates.parse(null, tuesday))
        // "May" as a word inside a sentence without a number isn't a date.
        assertNull(parse("we may need it"))
    }
}
