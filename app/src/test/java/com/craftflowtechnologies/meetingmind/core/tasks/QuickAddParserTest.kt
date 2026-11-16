package com.craftflowtechnologies.meetingmind.core.tasks

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/** The clock is pinned to Monday 16 November 2026, 10:00 UTC, and the zone to UTC. */
class QuickAddParserTest {
    private lateinit var originalZone: TimeZone
    private val now = at("2026-11-16T10:00:00Z")
    private val acme = QuickAddList("l-acme", "Acme")
    private val beta = QuickAddList("l-beta", "Beta")

    @Before fun pinZone() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun parse(input: String, lists: List<QuickAddList> = emptyList()) = QuickAddParser.parse(input, now, lists)

    @Test fun readsEveryTokenOfTheExampleLine() {
        val input = "Send API docs to James tomorrow 3pm #Acme !1 @email"
        val r = parse(input, listOf(acme, beta))
        assertEquals("Send API docs to James", r.title)
        assertEquals(at("2026-11-17T15:00:00Z"), r.dueAt)
        assertTrue(r.hasTime)
        assertEquals("l-acme", r.listId)
        assertEquals("Acme", r.listName)
        assertEquals(1, r.priority)
        assertEquals(listOf("email"), r.labels)
        assertNull(r.repeatRule)
        assertEquals(listOf("tomorrow", "3pm", "#Acme", "!1", "@email"), r.spans.map { input.substring(it.start, it.end) })
        assertEquals(
            listOf(QuickAddSpan.Kind.DATE, QuickAddSpan.Kind.TIME, QuickAddSpan.Kind.LIST, QuickAddSpan.Kind.PRIORITY, QuickAddSpan.Kind.LABEL),
            r.spans.map { it.kind }
        )
    }

    @Test fun leadingByIsPartOfTheDate() {
        val r = parse("Renew passport by Friday")
        assertEquals("Renew passport", r.title)
        assertEquals(at("2026-11-20T00:00:00Z"), r.dueAt)
        assertFalse(r.hasTime)
        assertEquals(listOf("by Friday"), r.spans.map { "Renew passport by Friday".substring(it.start, it.end) })
    }

    @Test fun relativeDates() {
        assertEquals(at("2026-11-16T00:00:00Z"), parse("Pay the water bill today").dueAt)
        assertEquals(at("2026-11-18T00:00:00Z"), parse("Plan the trip day after tomorrow").dueAt)
        assertEquals(at("2026-11-19T00:00:00Z"), parse("Book flights in 3 days").dueAt)
        assertEquals(at("2026-11-23T00:00:00Z"), parse("Filing next week").dueAt)
        assertEquals(at("2026-12-01T00:00:00Z"), parse("Submit form 2026-12-01").dueAt)
        assertEquals("Book flights", parse("Book flights in 3 days").title)
    }

    @Test fun meetDayNameIsAFullWeekday() {
        val r = parse("Meet Friday")
        assertEquals("Meet", r.title)
        assertEquals(at("2026-11-20T00:00:00Z"), r.dueAt)
    }

    @Test fun ambiguousDayWordsStayInTheTitle() {
        assertEquals("Call Sun Tzu", parse("Call Sun Tzu").title)
        assertNull(parse("Call Sun Tzu").dueAt)
        assertEquals("Read the May report", parse("Read the May report").title)
        assertNull(parse("Read the May report").dueAt)
    }

    @Test fun whenTheWholeLineIsADateTheTitleFallsBackToTheInput() {
        val r = parse("tomorrow")
        assertEquals("tomorrow", r.title)
        assertEquals(at("2026-11-17T00:00:00Z"), r.dueAt)
    }

    @Test fun readsTimesInEachForm() {
        val noon = parse("Lunch at noon")
        assertEquals("Lunch", noon.title)
        assertEquals(at("2026-11-16T12:00:00Z"), noon.dueAt)
        assertTrue(noon.hasTime)

        val dotted = parse("Standup 9.30am")
        assertEquals("Standup", dotted.title)
        assertEquals(at("2026-11-16T09:30:00Z"), dotted.dueAt)

        val twentyFourHour = parse("Review 15:00 tomorrow")
        assertEquals("Review", twentyFourHour.title)
        assertEquals(at("2026-11-17T15:00:00Z"), twentyFourHour.dueAt)

        val spacedPm = parse("Call Mum 7 pm")
        assertEquals("Call Mum", spacedPm.title)
        assertEquals(at("2026-11-16T19:00:00Z"), spacedPm.dueAt)

        assertEquals(at("2026-11-16T00:00:00Z"), parse("Midnight check at midnight").dueAt)
    }

    @Test fun ambiguousTimesStayInTheTitle() {
        val bareColon = parse("Check 9:30 numbers")
        assertEquals("Check 9:30 numbers", bareColon.title)
        assertNull(bareColon.dueAt)
        assertFalse(bareColon.hasTime)

        val bareHour = parse("Call at 3")
        assertEquals("Call at 3", bareHour.title)
        assertFalse(bareHour.hasTime)
    }

    @Test fun listNamesMatchFuzzily() {
        assertEquals("l-acme", parse("Draft brief #Acm", listOf(acme, beta)).listId)
        assertEquals("l-acme", parse("Draft brief #acne", listOf(acme, beta)).listId)
        assertEquals("l-beta", parse("Draft brief #Be", listOf(acme, beta)).listId)
        assertEquals("Draft brief", parse("Draft brief #Acm", listOf(acme, beta)).title)
    }

    @Test fun unknownOrAmbiguousListStaysInTheTitle() {
        val unknown = parse("Draft brief #Zeta", listOf(acme, beta))
        assertNull(unknown.listId)
        assertEquals("Draft brief #Zeta", unknown.title)

        // "Ac" starts both lists equally, so it names neither.
        val tie = parse("Draft #Ac", listOf(acme, QuickAddList("l-acorn", "Acorn")))
        assertNull(tie.listId)
        assertEquals("Draft #Ac", tie.title)
    }

    @Test fun matchListPicksTheBestScoreOrNothing() {
        assertEquals(acme, QuickAddParser.matchList("ACME", listOf(acme, beta)))
        assertNull(QuickAddParser.matchList("", listOf(acme, beta)))
        assertNull(QuickAddParser.matchList("xyz", listOf(acme, beta)))
    }

    @Test fun priorityOnlyWhenOneToFour() {
        val two = parse("Fix login !2")
        assertEquals(2, two.priority)
        assertEquals("Fix login", two.title)

        val five = parse("Fix login !5")
        assertEquals(4, five.priority)
        assertEquals("Fix login !5", five.title)
    }

    @Test fun labelsAreKeptOnceWithoutCase() {
        val r = parse("Ask Sam @Finance @finance @ops")
        assertEquals("Ask Sam", r.title)
        assertEquals(listOf("Finance", "ops"), r.labels)
    }

    @Test fun everyDayRepeatsWithNoDate() {
        val r = parse("Water plants every day")
        assertEquals("Water plants", r.title)
        assertEquals("FREQ=DAILY", r.repeatRule)
        assertNull(r.dueAt)
        assertEquals(TaskRepeat.DAILY, QuickAddParser.legacyRepeat(r.repeatRule))
    }

    @Test fun everyWeekdayAndMonth() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", parse("Stand up every weekday").repeatRule)
        val monthly = parse("Pay rent on the 1st every month")
        assertEquals("FREQ=MONTHLY", monthly.repeatRule)
        assertEquals("Pay rent on the 1st", monthly.title)
        assertEquals(TaskRepeat.MONTHLY, QuickAddParser.legacyRepeat(monthly.repeatRule))
    }

    @Test fun everyWeekdayNameStartsOnThatDay() {
        val r = parse("Team sync every monday")
        assertEquals("Team sync", r.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", r.repeatRule)
        // The reference is a Monday, so the next Monday is a week away.
        assertEquals(at("2026-11-23T00:00:00Z"), r.dueAt)
        assertEquals(TaskRepeat.WEEKLY, QuickAddParser.legacyRepeat(r.repeatRule))
    }

    @Test fun everyWithoutARepeatWordIsText() {
        val r = parse("Every morning walk")
        assertEquals("Every morning walk", r.title)
        assertNull(r.repeatRule)
    }

    @Test fun timeWithNoDayMeansToday() {
        val r = parse("Gym every weekday at 6am")
        assertEquals("Gym", r.title)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", r.repeatRule)
        assertEquals(at("2026-11-16T06:00:00Z"), r.dueAt)
    }

    @Test fun plainLineHasNoFields() {
        val r = parse("Read Romans 8")
        assertEquals("Read Romans 8", r.title)
        assertNull(r.dueAt)
        assertNull(r.listId)
        assertEquals(4, r.priority)
        assertTrue(r.labels.isEmpty())
        assertTrue(r.spans.isEmpty())
    }
}
