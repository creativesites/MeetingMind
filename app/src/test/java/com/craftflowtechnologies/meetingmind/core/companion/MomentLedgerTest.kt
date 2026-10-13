package com.craftflowtechnologies.meetingmind.core.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentLedgerTest {
    private val day = LocalClock.DayMs
    private val hour = LocalClock.HourMs
    // 2026-10-09 12:00 UTC
    private val noon = 1_791_201_600_000L

    @Test fun `once ever`() {
        val c = LocalClock(noon)
        val l = MomentLedger().record(Moment.FirstRecordingPeak, c)
        assertFalse(l.allows(Moment.FirstRecordingPeak, LocalClock(noon + 400 * day)))
        assertTrue(l.allows(Moment.Proud(MilestoneKind.FIRST_NOTE), c))
        val p = l.record(Moment.Proud(MilestoneKind.FIRST_NOTE), c)
        assertFalse(p.allows(Moment.Proud(MilestoneKind.FIRST_NOTE), LocalClock(noon + 30 * day)))
        assertTrue(p.allows(Moment.Proud(MilestoneKind.TENTH_NOTE), c))
        val a = p.record(Moment.AchievementSuggestion("plan-1"), c)
        assertFalse(a.allows(Moment.AchievementSuggestion("plan-1"), c))
        assertTrue(a.allows(Moment.AchievementSuggestion("plan-2"), c))
    }

    @Test fun `once per local day`() {
        val l = MomentLedger().record(Moment.FullCelebration, LocalClock(noon))
        assertFalse(l.allows(Moment.FullCelebration, LocalClock(noon + 11 * hour)))
        assertTrue(l.allows(Moment.FullCelebration, LocalClock(noon + 12 * hour)))
        // UTC+3: noon UTC is 15:00 local, so 9 hours later is already tomorrow there.
        val east = MomentLedger().record(Moment.FullCelebration, LocalClock(noon, 3 * hour))
        assertTrue(east.allows(Moment.FullCelebration, LocalClock(noon + 9 * hour, 3 * hour)))
    }

    @Test fun `minimum interval`() {
        val l = MomentLedger().record(Moment.TapHi, LocalClock(noon))
        assertFalse(l.allows(Moment.TapHi, LocalClock(noon + 9_999)))
        assertTrue(l.allows(Moment.TapHi, LocalClock(noon + 10_000)))
    }

    @Test fun `notifications - one a day, never in quiet hours or during a faith recording`() {
        assertTrue(MomentLedger().allows(Moment.CompanionNotification, LocalClock(noon)))
        assertFalse(MomentLedger().allows(Moment.CompanionNotification, LocalClock(noon), faithRecording = true))
        for (h in listOf(22, 23, 0, 3, 6)) {
            assertFalse("$h", MomentLedger().allows(Moment.CompanionNotification, LocalClock(noon - 12 * hour + h * hour)))
        }
        assertTrue(MomentLedger().allows(Moment.CompanionNotification, LocalClock(noon - 12 * hour + 7 * hour)))
        val sent = MomentLedger().record(Moment.CompanionNotification, LocalClock(noon))
        assertFalse(sent.allows(Moment.CompanionNotification, LocalClock(noon + 2 * hour)))
    }

    @Test fun `local clock hour and day`() {
        assertEquals(12, LocalClock(noon).hourOfDay)
        assertEquals(15, LocalClock(noon, 3 * hour).hourOfDay)
        assertEquals(7, LocalClock(noon, -5 * hour).hourOfDay)
    }

    @Test fun `daily budget - two a day, three hours apart`() {
        val u = Moment.Unveil
        var l = MomentLedger()
        assertTrue(l.allows(u, LocalClock(noon)))
        l = l.record(u, LocalClock(noon - 6 * hour))
        assertTrue(l.allows(u, LocalClock(noon - 3 * hour)))
        assertFalse(l.allows(u, LocalClock(noon - 3 * hour - 1)))
        l = l.record(u, LocalClock(noon - 3 * hour))
        assertFalse("two today already", l.allows(u, LocalClock(noon + 5 * hour)))
        assertEquals(listOf(noon - 6 * hour, noon - 3 * hour), l.history[u.key])
    }

    @Test fun `daily budget - rolls over at local midnight and keeps only the last two`() {
        val u = Moment.Unveil
        val l = MomentLedger().record(u, LocalClock(noon + 9 * hour)).record(u, LocalClock(noon + 11 * hour + 30 * 60_000))
        // 23:30 UTC; the next local day starts at 00:00, but the 3 h gap still holds until 02:30.
        assertFalse(l.allows(u, LocalClock(noon + 13 * hour)))
        assertTrue(l.allows(u, LocalClock(noon + 14 * hour + 30 * 60_000)))
        val three = l.record(u, LocalClock(noon + 15 * hour))
        assertEquals(2, three.history[u.key]!!.size)
        // UTC+3: 21:00 UTC is 00:00 local, the first play of that local day; 3 h later there is room for one more.
        val east = MomentLedger().record(u, LocalClock(noon + 9 * hour, 3 * hour))
        assertTrue(east.allows(u, LocalClock(noon + 12 * hour, 3 * hour)))
    }

    @Test fun `daily budget - a ledger with only the last time still works`() {
        val u = Moment.Unveil
        val legacy = MomentLedger(last = mapOf(u.key to noon - hour))
        assertFalse(legacy.allows(u, LocalClock(noon)))
        assertTrue(legacy.allows(u, LocalClock(noon + 2 * hour)))
        assertEquals(listOf(noon - hour, noon + 2 * hour), legacy.record(u, LocalClock(noon + 2 * hour)).history[u.key])
    }

    @Test fun `the hello first keep has its own key`() {
        val l = MomentLedger().record(Moment.FirstKeepHello, LocalClock(noon))
        assertFalse(l.allows(Moment.FirstKeepHello, LocalClock(noon + 400 * day)))
        assertTrue(l.allows(Moment.FirstRecordingPeak, LocalClock(noon)))
        assertEquals("peak.first.hello", Moment.FirstKeepHello.key)
        assertEquals("unveil", Moment.Unveil.key)
    }
}
