package com.craftflowtechnologies.meetingmind.core.companion.moment

import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fresh-open decision: one claim per navigation entry, and the planner plays only on a claim (§3.2). */
class OpenSessionTest {
    private val clock = LocalClock(1_791_201_600_000L)

    /** One open of a note screen, planned the way `rememberNoteMoment` plans it: no ticket, shipped flags. */
    private fun open(session: OpenSession, rotation: RotationState = RotationState()): PlannedMoment =
        MomentPlanner.plan(
            MomentFacts(
                noteId = "note-1", ticket = null, freshOpen = session.claim(), clock = clock,
                rotation = rotation, flags = MomentFlags.Current
            )
        )

    @Test fun `claim is true exactly once per entry`() {
        val session = OpenSession()
        assertTrue(session.claim())
        assertFalse(session.claim())
        assertFalse(session.claim())
        assertTrue(session.claimed)
    }

    @Test fun `an entry restored with its saved claim never plays again`() {
        assertFalse(OpenSession(claimed = true).claim())
    }

    @Test fun `a fresh open plays the Unveil, and returning to the same entry plays nothing`() {
        val session = OpenSession()
        val first = open(session)
        assertTrue("a fresh open unveils: ${first.plan}", first.plan is MomentPlan.Unveil)
        assertTrue("the rotation advances on the open", first.effects.rotation != null)

        // Back from a sub-screen, a configuration change or the app resuming: the same entry, already claimed.
        val back = open(session, rotation = first.effects.rotation!!)
        assertEquals(MomentPlan.Plain(PlainReason.NOT_FRESH), back.plan)
        assertEquals(MomentEffects(), back.effects)
    }

    @Test fun `a new navigation into the note is a fresh open with the next variant`() {
        val first = open(OpenSession())
        val second = open(OpenSession(), rotation = first.effects.rotation!!)
        assertTrue("a new entry unveils again: ${second.plan}", second.plan is MomentPlan.Unveil)
        assertNotEquals(
            "no variant plays twice in a row",
            (first.plan as MomentPlan.Unveil).variant,
            (second.plan as MomentPlan.Unveil).variant
        )
    }
}
