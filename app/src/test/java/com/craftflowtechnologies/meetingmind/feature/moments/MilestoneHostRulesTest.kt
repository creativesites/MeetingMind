package com.craftflowtechnologies.meetingmind.feature.moments

import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettings
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneKind
import com.craftflowtechnologies.meetingmind.core.companion.Moment
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedger
import com.craftflowtechnologies.meetingmind.core.companion.Presence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MilestoneHostRulesTest {

    private val clock = LocalClock(1_800_000_000_000L)
    private val empty = MomentLedger()

    @Test
    fun `a new milestone shows with the companion around`() {
        assertTrue(milestoneAllowed(MilestoneKind.TENTH_NOTE, CompanionSettings(), empty, clock))
    }

    @Test
    fun `a milestone shows once ever`() {
        val after = empty.record(Moment.Proud(MilestoneKind.TENTH_NOTE), clock)
        val muchLater = LocalClock(clock.nowMs + 400L * LocalClock.DayMs)
        assertFalse(milestoneAllowed(MilestoneKind.TENTH_NOTE, CompanionSettings(), after, muchLater))
        // Another milestone is not used up by it.
        assertTrue(milestoneAllowed(MilestoneKind.ALL_CARDS_CLEARED, CompanionSettings(), after, muchLater))
    }

    @Test
    fun `presence off shows no milestone`() {
        assertFalse(milestoneAllowed(MilestoneKind.TENTH_NOTE, CompanionSettings(presence = Presence.OFF), empty, clock))
    }

    @Test
    fun `big moments only presence still shows milestones`() {
        assertTrue(milestoneAllowed(MilestoneKind.TENTH_NOTE, CompanionSettings(presence = Presence.MOMENTS), empty, clock))
    }

    @Test
    fun `a companion hidden for now shows no milestone until it is back`() {
        val hidden = CompanionSettings(hiddenUntilMs = clock.nowMs + LocalClock.HourMs)
        assertFalse(milestoneAllowed(MilestoneKind.TENTH_NOTE, hidden, empty, clock))
        assertTrue(milestoneAllowed(MilestoneKind.TENTH_NOTE, hidden, empty, LocalClock(clock.nowMs + 2 * LocalClock.HourMs)))
    }

    @Test
    fun `no companion still gets the milestone drawn with the glyph`() {
        assertTrue(milestoneAllowed(MilestoneKind.TENTH_NOTE, CompanionSettings(form = null), empty, clock))
    }
}
