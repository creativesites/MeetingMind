package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.ai.assistant.AskScope
import com.craftflowtechnologies.meetingmind.core.work.ContextHeader
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** The pure logic behind the context pages, Prepare and Ask. No database, no Compose. */
class ContextPageLogicTest {
    private val now = 1_700_000_000_000L

    private fun header(youOwe: Int = 0, theyOwe: Int = 0, open: Int = 0, decided: Int = 0) = ContextHeader(
        youOwe = youOwe, theyOwe = theyOwe, open = open, decided = decided,
        next = null, nextAt = null, lastMeeting = null
    )

    // ---- status line from counts

    @Test fun `past due items make the status need attention, with the count as the reason`() {
        val s = ContextStatus.of(header(youOwe = 2), pastDue = 1)
        assertTrue(s.attention)
        assertEquals("Needs attention", s.label)
        assertEquals("1 item is past due", s.reason)
        assertEquals("3 items are past due", ContextStatus.of(header(), pastDue = 3).reason)
    }

    @Test fun `nothing past due is on track, and the reason only states the counts`() {
        val s = ContextStatus.of(header(youOwe = 1, theyOwe = 2, open = 3), pastDue = 0)
        assertFalse(s.attention)
        assertEquals("On track", s.label)
        assertEquals("1 you owe · 2 they owe · 3 open", s.reason)
    }

    @Test fun `only decisions and nothing open is on track`() {
        val s = ContextStatus.of(header(decided = 2), pastDue = 0)
        assertFalse(s.attention)
        assertEquals("On track", s.label)
        assertEquals("2 decided, nothing open", s.reason)
    }

    @Test fun `an empty page says nothing is open`() {
        val s = ContextStatus.of(header(), pastDue = 0)
        assertFalse(s.attention)
        assertEquals("Nothing open", s.label)
    }

    @Test fun `an item is past due only when still open and its due time has passed`() {
        assertTrue(ContextStatus.isPastDue(ItemStatus.OPEN, now - 1, now))
        assertTrue(ContextStatus.isPastDue(ItemStatus.UNCLEAR, now - 1, now))
        assertFalse(ContextStatus.isPastDue(ItemStatus.COMPLETED, now - 1, now))
        assertFalse(ContextStatus.isPastDue(ItemStatus.OPEN, null, now))
        assertFalse(ContextStatus.isPastDue(ItemStatus.OPEN, now + 1, now))
    }

    // ---- suggestion to structured query

    @Test fun `suggested questions map to their structured queries, ignoring case and punctuation`() {
        assertEquals(InstantQuery.WAITING_FOR, instantQueryFor("What am I waiting for?"))
        assertEquals(InstantQuery.PROMISED, instantQueryFor("  what have I promised  "))
        assertEquals(InstantQuery.DECISIONS_THIS_MONTH, instantQueryFor("What did we decide this month?"))
        assertEquals(InstantQuery.CHANGED_THIS_WEEK, instantQueryFor("What changed since last week?"))
    }

    @Test fun `free questions and model-only suggestions have no structured query`() {
        assertNull(instantQueryFor("What did we decide most recently?"))
        assertNull(instantQueryFor("Why is Sarah late?"))
        assertNull(instantQueryFor("   "))
    }

    @Test fun `exactly the four instant suggestions run a query, and each maps back to itself`() {
        val all = suggestedQuestions()
        val instant = all.filter { it.instant != null }
        assertEquals(4, instant.size)
        assertEquals(2, all.count { it.instant == null })
        instant.forEach { assertEquals(it.instant, instantQueryFor(it.text)) }
    }

    // ---- scope and time range

    @Test fun `this month limits the scope from the first of the month and keeps the entity`() {
        val base = AskScope(type = ContextType.PROJECT, id = "p1", label = "Acme")
        val month = scopeFor(base, thisMonth = true, now = now)
        assertEquals(monthStart(now), month.from)
        assertNull(month.to)
        assertEquals("p1", month.id)
        assertNull(scopeFor(base, thisMonth = false, now = now).from)
    }

    @Test fun `the month start is midnight on the first day of the same month`() {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStart(now) }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue(monthStart(now) <= now)
    }

    // ---- removing a person asks first

    @Test fun `the removal prompt names the person and says what stays`() {
        assertEquals("Remove Sarah Chen?", PersonRemoval.title("Sarah Chen"))
        assertEquals("Remove this person?", PersonRemoval.title(""))
        val body = PersonRemoval.body("Sarah Chen")
        assertTrue(body.startsWith("Sarah Chen"))
        assertTrue(body.contains("stay as they are"))
        assertEquals("Remove", PersonRemoval.CONFIRM_LABEL)
    }
}
