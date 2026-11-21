package com.craftflowtechnologies.meetingmind.feature.moments

import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.companion.moment.PlainReason
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenRevealTest {

    private val unveil = MomentPlan.Unveil(UnveilVariant.LIFT, durationMs = 1500, quiet = false, space = MomentSpace.STUDY)

    private fun facts(
        type: RecordingType = RecordingType.LECTURE,
        outcome: NoteOutcome = NoteOutcome.COMPLETE,
        sensitive: Boolean = false
    ) = NoteFacts(
        noteId = "n1", title = "Cell biology", outcome = outcome, type = type, space = NotebookSpace.LEARNING,
        sermon = type == RecordingType.SERMON, sensitive = sensitive, audioMs = 60_000L, transcriptWords = 500, onDevice = true
    )

    @Before
    fun reset() = OpenReveal.resetForTest()

    @Test
    fun `a finished note opened with an Unveil gets the reveal`() {
        assertTrue(OpenReveal.eligible(unveil, facts()))
    }

    @Test
    fun `sensitive, unfinished or motionless opens keep their own plan`() {
        assertFalse(OpenReveal.eligible(unveil, facts(sensitive = true)))
        assertFalse(OpenReveal.eligible(unveil, facts(outcome = NoteOutcome.PARTIAL)))
        assertFalse(OpenReveal.eligible(MomentPlan.Plain(PlainReason.NOT_FRESH), facts()))
        assertFalse(OpenReveal.eligible(MomentPlan.Nod(), facts()))
        assertFalse(OpenReveal.eligible(null, facts()))
    }

    @Test
    fun `each note reveals once per session`() {
        assertTrue(OpenReveal.claim("n1"))
        assertFalse(OpenReveal.claim("n1"))
        assertTrue(OpenReveal.claim("n2"))
    }

    @Test
    fun `the reveal is never the once ever play and never chimes`() {
        val plan = OpenReveal.planFor(unveil.copy(quiet = true, space = MomentSpace.FAITH))
        assertFalse(plan.onceEver)
        assertFalse(plan.chime)
        assertFalse(plan.hello)
        assertTrue(plan.quiet)
        assertEquals(MomentSpace.FAITH, plan.space)
    }

    @Test
    fun `note types map to their wording`() {
        assertEquals(OpenReveal.Kind.MEETING, OpenReveal.kindOf(RecordingType.STANDUP))
        assertEquals(OpenReveal.Kind.LECTURE, OpenReveal.kindOf(RecordingType.LECTURE))
        assertEquals(OpenReveal.Kind.SERMON, OpenReveal.kindOf(RecordingType.SERMON))
        assertEquals(OpenReveal.Kind.FAITH, OpenReveal.kindOf(RecordingType.DEVOTIONAL))
        assertEquals(OpenReveal.Kind.IDEAS, OpenReveal.kindOf(RecordingType.BRAINSTORM))
        assertEquals(OpenReveal.Kind.PERSONAL, OpenReveal.kindOf(RecordingType.VOICE_MEMO))
        RecordingType.entries.forEach { OpenReveal.kindOf(it) }
    }
}
