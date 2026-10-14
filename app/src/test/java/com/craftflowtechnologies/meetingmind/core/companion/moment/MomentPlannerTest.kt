package com.craftflowtechnologies.meetingmind.core.companion.moment

import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.FixAction
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.Moment
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedger
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every row of the §3.3 ladder, the flag note under it, and the §3.2 open rules. */
class MomentPlannerTest {

    private val hour = LocalClock.HourMs
    private val noon = 1_791_201_600_000L
    private val clock = LocalClock(noon)
    private val budgeted = MomentFlags.Budgeted
    private val shipped = MomentFlags(firstKeepEveryNote = true, unveilOnEveryOpen = true)

    private fun ticket(
        firstEver: Boolean = false, hello: Boolean = false, sermon: Boolean = false, partial: Boolean = false,
        deferred: Boolean = false, space: NotebookSpace? = NotebookSpace.PERSONAL, readyAt: Long = noon - 60_000,
        previous: Long? = null
    ) = UnveilTicket("n1", readyAt, space, sermon, partial, firstEver, hello, deferred, previous)

    private fun facts(
        ticket: UnveilTicket? = ticket(),
        flags: MomentFlags = budgeted,
        ledger: MomentLedger = MomentLedger(),
        rotation: RotationState = RotationState(),
        device: DeviceFacts = DeviceFacts(),
        outcome: NoteOutcome = NoteOutcome.COMPLETE,
        space: NotebookSpace? = ticket?.space ?: NotebookSpace.PERSONAL,
        sermon: Boolean = ticket?.sermon == true,
        sensitive: Boolean = false, goodFriday: Boolean = false, recording: Boolean = false, retried: Boolean = false,
        audioMs: Long? = 600_000, words: Int? = 900, shown: Boolean = true, snackbar: Boolean = false,
        fresh: Boolean = true, at: LocalClock = clock
    ) = MomentFacts(
        noteId = "n1", ticket = ticket, freshOpen = fresh, outcome = outcome, sensitive = sensitive, goodFriday = goodFriday,
        space = space, sermon = sermon, audioMs = audioMs, transcriptWords = words, retried = retried,
        recordingActive = recording, device = device, companionShown = shown, nodPlayedInSnackbar = snackbar,
        clock = at, ledger = ledger, rotation = rotation, flags = flags
    )

    private fun plan(f: MomentFacts) = MomentPlanner.plan(f).plan

    // ---- Row 1: failures ----

    @Test fun `row 1 - failed, partial, fallback and models missing are plain with the fix`() {
        val cases = mapOf(
            NoteOutcome.FAILED to (PlainReason.FAILED to FixAction.RETRY),
            NoteOutcome.PARTIAL to (PlainReason.PARTIAL to FixAction.RETRY),
            NoteOutcome.AI_FALLBACK to (PlainReason.AI_FALLBACK to FixAction.REDO_ONLINE),
            NoteOutcome.MODEL_REQUIRED to (PlainReason.MODEL_REQUIRED to FixAction.DOWNLOAD_MODELS)
        )
        for (flags in listOf(budgeted, shipped)) for ((outcome, want) in cases) {
            val (p, e) = MomentPlanner.plan(facts(outcome = outcome, flags = flags))
            assertEquals(MomentPlan.Plain(want.first, want.second), p)
            assertFalse(p.announce)
            assertEquals(TicketAction.CONSUME, e.ticket)
            assertTrue(e.record.isEmpty())
        }
        assertEquals(CompanionState.WORRIED, PlainReason.FAILED.statusCompanion)
        assertEquals(CompanionState.SLEEPY, PlainReason.MODEL_REQUIRED.statusCompanion)
    }

    @Test fun `row 1 - a no-speech note is not a failure, so it does not stop at row 1`() {
        // Founder decision (2026-10-14): every note gets its moment. Zero words is a very short recording,
        // so the restraint row (7) nods, on arrival and on a reopen alike. Never plain, never the Unveil.
        val arrival = MomentPlanner.plan(facts(outcome = NoteOutcome.NO_SPEECH, words = 0))
        assertEquals(MomentPlan.Nod(), arrival.plan)
        assertEquals(TicketAction.CONSUME, arrival.effects.ticket)
        val reopen = MomentPlanner.plan(facts(ticket = null, flags = shipped, outcome = NoteOutcome.NO_SPEECH, words = 0))
        assertEquals(MomentPlan.Nod(), reopen.plan)
        assertEquals(TicketAction.NONE, reopen.effects.ticket)
    }

    @Test fun `row 1 - a partial ticket is never celebrated`() {
        assertEquals(PlainReason.PARTIAL, (plan(facts(ticket = ticket(partial = true, firstEver = true), flags = shipped)) as MomentPlan.Plain).reason)
    }

    @Test fun `precedence - failed and first ever is plain, and the peak is kept`() {
        val (p, e) = MomentPlanner.plan(facts(ticket = ticket(firstEver = true), outcome = NoteOutcome.FAILED))
        assertEquals(PlainReason.FAILED, (p as MomentPlan.Plain).reason)
        assertFalse(Moment.FirstRecordingPeak in e.record)
    }

    // ---- Row 2: sacred ----

    @Test fun `row 2 - sensitive notes and Good Friday are plain, first ever included`() {
        for (flags in listOf(budgeted, shipped)) {
            assertEquals(MomentPlan.Plain(PlainReason.SENSITIVE), plan(facts(sensitive = true, ticket = ticket(firstEver = true), flags = flags)))
            assertEquals(MomentPlan.Plain(PlainReason.SENSITIVE), plan(facts(goodFriday = true, flags = flags)))
            assertEquals(MomentPlan.Plain(PlainReason.SENSITIVE), plan(facts(goodFriday = true, ticket = null, flags = flags.copy(unveilOnEveryOpen = true))))
        }
        assertTrue(MomentPlan.Plain(PlainReason.SENSITIVE).announce)
    }

    // ---- Row 3: the First Keep ----

    @Test fun `row 3 - first note ever gets the First Keep once, with the chime`() {
        val (p, e) = MomentPlanner.plan(facts(ticket = ticket(firstEver = true)))
        assertEquals(MomentPlan.FirstKeep(quiet = false, hello = false, onceEver = true, chime = true, space = MomentSpace.EVERYDAY), p)
        assertEquals(listOf<Moment>(Moment.FirstRecordingPeak), e.record)
        assertEquals(TicketAction.CONSUME, e.ticket)
        val spent = MomentLedger().record(Moment.FirstRecordingPeak, clock)
        assertTrue(plan(facts(ticket = ticket(firstEver = true), ledger = spent)) !is MomentPlan.FirstKeep)
    }

    @Test fun `row 3 - the hello sample note has its own once-ever key`() {
        val (p, e) = MomentPlanner.plan(facts(ticket = ticket(hello = true)))
        assertEquals(MomentPlan.FirstKeep(quiet = false, hello = true, onceEver = true, chime = true, space = MomentSpace.EVERYDAY), p)
        assertEquals(listOf<Moment>(Moment.FirstKeepHello), e.record)
        // It doesn't consume the real one.
        val afterHello = MomentLedger().record(Moment.FirstKeepHello, clock)
        val real = plan(facts(ticket = ticket(firstEver = true), ledger = afterHello)) as MomentPlan.FirstKeep
        assertTrue(real.onceEver && !real.hello && real.chime)
    }

    @Test fun `row 3 - faith or a sermon is the quiet First Keep, never a chime`() {
        val faith = plan(facts(ticket = ticket(firstEver = true, space = NotebookSpace.FAITH))) as MomentPlan.FirstKeep
        assertTrue(faith.quiet); assertFalse(faith.chime); assertEquals(MomentSpace.FAITH, faith.space)
        val sermon = plan(facts(ticket = ticket(firstEver = true, sermon = true, space = NotebookSpace.PERSONAL))) as MomentPlan.FirstKeep
        assertTrue(sermon.quiet); assertFalse(sermon.chime)
    }

    @Test fun `row 3 - the once-ever First Keep beats the restraint rows`() {
        val dev = DeviceFacts(batterySaver = true, lowEnd = true, durationScale = 0f)
        val p = plan(facts(ticket = ticket(firstEver = true, previous = noon - 120_000), device = dev, audioMs = 5_000, words = 3))
        assertTrue(p is MomentPlan.FirstKeep)
    }

    @Test fun `row 3 with the flag - every successful arrival gets the First Keep, no chime, no budget`() {
        val spent = MomentLedger().record(Moment.Unveil, LocalClock(noon - 4 * hour)).record(Moment.Unveil, LocalClock(noon - 30 * 60_000))
        val (p, e) = MomentPlanner.plan(facts(flags = shipped, ledger = spent, ticket = ticket(previous = noon - 120_000)))
        assertEquals(MomentPlan.FirstKeep(quiet = false, hello = false, onceEver = false, chime = false, space = MomentSpace.EVERYDAY), p)
        assertTrue(e.record.isEmpty())
        assertNull(e.rotation)
        assertEquals(TicketAction.CONSUME, e.ticket)
    }

    @Test fun `row 3 with the flag - first ever still records the peak and chimes`() {
        val (p, e) = MomentPlanner.plan(facts(flags = shipped, ticket = ticket(firstEver = true)))
        assertTrue((p as MomentPlan.FirstKeep).chime && p.onceEver)
        assertEquals(listOf<Moment>(Moment.FirstRecordingPeak), e.record)
    }

    @Test fun `row 3 with the flag - the system and honesty guards still step down`() {
        assertEquals(MomentPlan.Plain(PlainReason.REDUCED_MOTION), plan(facts(flags = shipped, device = DeviceFacts(durationScale = 0f))))
        assertEquals(MomentPlan.Nod(), plan(facts(flags = shipped, device = DeviceFacts(batterySaver = true))))
        assertEquals(MomentPlan.Nod(), plan(facts(flags = shipped, device = DeviceFacts(lowEnd = true))))
        assertEquals(MomentPlan.Nod(), plan(facts(flags = shipped, audioMs = 19_999)))
        assertEquals(MomentPlan.Nod(), plan(facts(flags = shipped, words = 29)))
        assertEquals(MomentPlan.Nod(), plan(facts(flags = shipped, retried = true)))
        val quiet = plan(facts(flags = shipped, ticket = ticket(space = NotebookSpace.FAITH))) as MomentPlan.FirstKeep
        assertTrue(quiet.quiet && !quiet.chime)
    }

    @Test fun `row 3 with the flag - the presence filter keeps the moment without the character`() {
        val p = plan(facts(flags = shipped, shown = false)) as MomentPlan.FirstKeep
        assertFalse(p.companion)
    }

    // ---- Row 4: recording in progress ----

    @Test fun `row 4 - a running recording defers the arrival and keeps the ticket`() {
        for (t in listOf(ticket(), ticket(firstEver = true))) for (flags in listOf(budgeted, shipped)) {
            val (p, e) = MomentPlanner.plan(facts(ticket = t, recording = true, flags = flags))
            assertEquals(MomentPlan.Plain(PlainReason.DEFERRED), p)
            assertEquals(TicketAction.DEFER, e.ticket)
            assertTrue(e.record.isEmpty())
        }
    }

    @Test fun `row 4 - after the stop, a queued note opens with a nod, but an owed First Keep still plays`() {
        assertEquals(MomentPlan.Nod(), plan(facts(ticket = ticket(deferred = true))))
        assertTrue(plan(facts(ticket = ticket(deferred = true, firstEver = true))) is MomentPlan.FirstKeep)
        assertTrue(plan(facts(ticket = ticket(deferred = true), flags = shipped)) is MomentPlan.FirstKeep)
    }

    // ---- Row 5: reduced motion ----

    @Test fun `row 5 - reduced motion is plain with the announcement`() {
        val p = plan(facts(device = DeviceFacts(durationScale = 0f)))
        assertEquals(MomentPlan.Plain(PlainReason.REDUCED_MOTION), p)
        assertTrue(p.announce)
        assertEquals(MomentPlan.Plain(PlainReason.REDUCED_MOTION), plan(facts(ticket = null, flags = shipped, device = DeviceFacts(durationScale = 0f))))
    }

    // ---- Row 6: sermons ----

    @Test fun `row 6 - a sermon is quiet Dawn and never advances the rotation`() {
        val (p, e) = MomentPlanner.plan(facts(ticket = ticket(sermon = true, space = NotebookSpace.FAITH)))
        assertEquals(MomentPlan.Unveil(UnveilVariant.DAWN, 1725, quiet = true, space = MomentSpace.FAITH), p)
        assertNull(e.rotation)
        assertEquals(listOf<Moment>(Moment.Unveil), e.record)
    }

    @Test fun `row 6 - a sermon without budget gets the quiet nod`() {
        val spent = MomentLedger().record(Moment.Unveil, LocalClock(noon - 30 * 60_000))
        assertEquals(MomentPlan.Nod(quiet = true, space = MomentSpace.FAITH), plan(facts(ticket = ticket(sermon = true), ledger = spent)))
    }

    @Test fun `row 6 with the every-open flag - a sermon reopened is quiet Dawn`() {
        val p = plan(facts(ticket = null, flags = shipped, sermon = true, space = NotebookSpace.FAITH))
        assertEquals(MomentPlan.Unveil(UnveilVariant.DAWN, 1725, quiet = true, space = MomentSpace.FAITH), p)
    }

    // ---- Row 7: restraint ----

    @Test fun `row 7 - battery saver, low-end, very short and back-to-back nod`() {
        assertEquals(MomentPlan.Nod(), plan(facts(device = DeviceFacts(batterySaver = true))))
        assertEquals(MomentPlan.Nod(), plan(facts(device = DeviceFacts(lowEnd = true))))
        assertEquals(MomentPlan.Nod(), plan(facts(audioMs = 19_999)))
        assertEquals(MomentPlan.Nod(), plan(facts(words = 29)))
        assertEquals(MomentPlan.Nod(), plan(facts(ticket = ticket(readyAt = noon - 60_000, previous = noon - 60_000 - 14 * 60_000))))
        // 15 minutes apart is not back-to-back; unknown length is not short.
        assertTrue(plan(facts(ticket = ticket(readyAt = noon - 60_000, previous = noon - 60_000 - 15 * 60_000))) is MomentPlan.Unveil)
        assertTrue(plan(facts(audioMs = null, words = null)) is MomentPlan.Unveil)
    }

    @Test fun `row 7 - the retry rule nods`() {
        assertEquals(MomentPlan.Nod(), plan(facts(retried = true)))
    }

    // ---- Rows 8 and 9: the rationed Unveil ----

    @Test fun `row 8 - budget left gives the next rotation variant and spends it`() {
        val (p, e) = MomentPlanner.plan(facts())
        assertEquals(MomentPlan.Unveil(UnveilVariant.LIFT, 1500, quiet = false, space = MomentSpace.EVERYDAY), p)
        assertEquals(listOf<Moment>(Moment.Unveil), e.record)
        assertEquals(UnveilVariant.LIFT, e.rotation!!.last)
        val second = plan(facts(rotation = e.rotation!!, ledger = MomentLedger().record(Moment.Unveil, LocalClock(noon - 4 * hour))))
        assertEquals(UnveilVariant.INKLINE, (second as MomentPlan.Unveil).variant)
    }

    @Test fun `row 8 - space scale and familiarity set the duration`() {
        var r = RotationState()
        repeat(40) { r = UnveilRotation.next(r, MomentSpace.WORK).state }
        val p = plan(facts(ticket = ticket(space = NotebookSpace.WORK), rotation = r)) as MomentPlan.Unveil
        assertEquals(MomentSpace.WORK, p.space)
        assertEquals(UnveilRotation.durationMs(p.variant, MomentSpace.WORK, r.seen(p.variant) + 1), p.durationMs)
        assertTrue(p.durationMs in 1200..1800)
    }

    @Test fun `row 9 - two a day, three hours apart, then nods`() {
        val oneRecent = MomentLedger().record(Moment.Unveil, LocalClock(noon - 2 * hour))
        assertEquals(MomentPlan.Nod(), plan(facts(ledger = oneRecent)))
        val twoToday = MomentLedger().record(Moment.Unveil, LocalClock(noon - 8 * hour)).record(Moment.Unveil, LocalClock(noon - 4 * hour))
        assertEquals(MomentPlan.Nod(), plan(facts(ledger = twoToday)))
        val oneOld = MomentLedger().record(Moment.Unveil, LocalClock(noon - 4 * hour))
        assertTrue(plan(facts(ledger = oneOld)) is MomentPlan.Unveil)
    }

    @Test fun `presence off - the unveil steps down to the header fade, rotation untouched`() {
        val (p, e) = MomentPlanner.plan(facts(shown = false))
        assertEquals(MomentPlan.Nod(companion = false), p)
        assertNull(e.rotation)
        assertEquals(MomentPlan.Nod(companion = false), plan(facts(ticket = null, flags = shipped, shown = false)))
    }

    @Test fun `snackbar path - the nod already played, the note opens with the header fade only`() {
        assertEquals(MomentPlan.Nod(companion = false), plan(facts(device = DeviceFacts(batterySaver = true), snackbar = true)))
    }

    // ---- §3.2: the ticket rule and opens ----

    @Test fun `flag off - no ticket, or an expired one, is plain`() {
        assertEquals(MomentPlan.Plain(PlainReason.NO_TICKET), plan(facts(ticket = null)))
        val (p, e) = MomentPlanner.plan(facts(ticket = ticket(readyAt = noon - 25 * hour)))
        assertEquals(MomentPlan.Plain(PlainReason.NO_TICKET), p)
        assertEquals("the expired ticket is cleaned up", TicketAction.CONSUME, e.ticket)
    }

    @Test fun `flag on - every fresh open gets the next variant with no budget`() {
        val spent = MomentLedger().record(Moment.Unveil, LocalClock(noon - 8 * hour)).record(Moment.Unveil, LocalClock(noon - 10 * 60_000))
        var r = RotationState()
        val seen = mutableListOf<UnveilVariant>()
        repeat(12) {
            val (p, e) = MomentPlanner.plan(facts(ticket = null, flags = shipped, ledger = spent, rotation = r))
            val u = p as MomentPlan.Unveil
            seen += u.variant
            assertTrue("nothing is recorded with the flag on", e.record.isEmpty())
            assertEquals(TicketAction.NONE, e.ticket)
            r = e.rotation!!
        }
        assertEquals(UnveilRotation.preview(RotationState(), MomentSpace.EVERYDAY, 12), seen)
    }

    @Test fun `flag on - opens skip the back-to-back guard but keep the system guards`() {
        assertEquals(MomentPlan.Nod(), plan(facts(ticket = null, flags = shipped, device = DeviceFacts(lowEnd = true))))
        assertEquals(MomentPlan.Nod(), plan(facts(ticket = null, flags = shipped, recording = true)))
        assertTrue(plan(facts(ticket = null, flags = shipped)) is MomentPlan.Unveil)
    }

    @Test fun `unveil every open with the first keep flag off - arrivals unveil without a budget`() {
        val flags = MomentFlags(firstKeepEveryNote = false, unveilOnEveryOpen = true)
        val spent = MomentLedger().record(Moment.Unveil, LocalClock(noon - 8 * hour)).record(Moment.Unveil, LocalClock(noon - 10 * 60_000))
        assertTrue(plan(facts(flags = flags, ledger = spent)) is MomentPlan.Unveil)
        // The back-to-back guard still applies to arrivals while the First Keep isn't on every note.
        assertEquals(MomentPlan.Nod(), plan(facts(flags = flags, ticket = ticket(previous = noon - 120_000))))
    }

    @Test fun `an open that isn't fresh is always plain and touches nothing`() {
        for (flags in listOf(budgeted, shipped)) for (t in listOf(null, ticket(firstEver = true))) {
            val (p, e) = MomentPlanner.plan(facts(ticket = t, flags = flags, fresh = false))
            assertEquals(MomentPlan.Plain(PlainReason.NOT_FRESH), p)
            assertFalse(p.announce)
            assertEquals(MomentEffects(), e)
        }
    }

    @Test fun `the first keep arrival then a reopen - first keep, then the rotation`() {
        val (first, e1) = MomentPlanner.plan(facts(flags = shipped))
        assertTrue(first is MomentPlan.FirstKeep)
        assertEquals(TicketAction.CONSUME, e1.ticket)
        // The ticket is gone; the next fresh open (a new navigation entry) unveils.
        val again = plan(facts(ticket = null, flags = shipped))
        assertEquals(UnveilVariant.LIFT, (again as MomentPlan.Unveil).variant)
    }

    @Test fun `the planner is pure`() {
        val f = facts(flags = shipped, ticket = null, rotation = UnveilRotation.next(RotationState(), MomentSpace.STUDY).state, space = NotebookSpace.LEARNING)
        assertEquals(MomentPlanner.plan(f), MomentPlanner.plan(f))
    }
}
