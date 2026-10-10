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
}
