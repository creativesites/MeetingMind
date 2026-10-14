package com.craftflowtechnologies.meetingmind.core.companion.moment

import com.craftflowtechnologies.meetingmind.core.companion.CompanionFlags
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.FixAction
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.Moment
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedger
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/*
 * Which moment a note open gets (docs/mvp/ZURI_SIGNATURE_MOMENTS.md §3.3, §3.2, §7.2, decisions 9–10).
 * Pure: the same facts always give the same plan, so every ladder row is a JVM test.
 */

/** How the note's processing ended (§4). */
enum class NoteOutcome { COMPLETE, PARTIAL, AI_FALLBACK, FAILED, MODEL_REQUIRED, NO_SPEECH }

/**
 * What the phone says right now. Read it with `MomentEnvironment.read(context)` at open time.
 *
 * @param lowEnd `isLowRamDevice()`, or under 3 GB of RAM, or API < 28 (computed once, cached).
 * @param durationScale the system animator duration scale; 0 means animations are off (reduced motion).
 */
data class DeviceFacts(val batterySaver: Boolean = false, val lowEnd: Boolean = false, val durationScale: Float = 1f) {
    val reducedMotion: Boolean get() = durationScale <= 0f
}

/** The founder's switches for moments, as data so tests and Companion Lab can flip them. */
data class MomentFlags(
    val firstKeepEveryNote: Boolean = CompanionFlags.firstKeepEveryNote,
    val unveilOnEveryOpen: Boolean = CompanionFlags.unveilOnEveryOpen
) {
    companion object {
        /** The shipped values ([CompanionFlags]). */
        val Current = MomentFlags()
        /** Both off: the original §3.3 ladder (First Keep once ever, Unveil rationed by the ticket and budget). */
        val Budgeted = MomentFlags(firstKeepEveryNote = false, unveilOnEveryOpen = false)
    }
}

/**
 * Everything the planner needs for one open of a note screen (`MeetingDetail`, `WrapUp`).
 *
 * The **feature lane supplies** the note facts ([outcome], [sensitive], [space], [sermon], [audioMs],
 * [transcriptWords], [retried]), the app facts ([goodFriday], [recordingActive], [companionShown],
 * [nodPlayedInSnackbar]) and [device]. The **store supplies** [ticket], [ledger] and [rotation] inside
 * `MomentLedgerStore.planOpen`, so they're read and written in one transaction. [freshOpen] comes from
 * `OpenSession.claim()`.
 *
 * @param ticket the note's [UnveilTicket] if it still has one. Non-null and valid makes this open an
 *   **arrival** (the note just finished); null is a later **open**.
 * @param freshOpen false for a configuration change, process restore, a return from a sub-screen, the app
 *   resuming, or the open that already played a moment (see `OpenSession`). Such opens are always plain.
 * @param sensitive a prayer request, or a grief, funeral or memorial tag (§1.3, §5.10).
 * @param sermon a sermon recording: the quiet First Keep, or Dawn quiet.
 * @param audioMs, transcriptWords null when unknown; known and under 20 s or 30 words is a very short recording.
 * @param retried the note succeeded after the user tapped a fix (Retry, Redo online): a nod (§4).
 * @param companionShown the presence filter (`CompanionPresence.visibleOn(NOTE_READY)`) and a form chosen.
 *   False still plans; the moment renders without the character (§3.3 presence filter, decision 4).
 * @param nodPlayedInSnackbar the user came from the "{title} is ready" snackbar, which already nodded (§3.5).
 */
data class MomentFacts(
    val noteId: String,
    val ticket: UnveilTicket?,
    val freshOpen: Boolean = true,
    val outcome: NoteOutcome = NoteOutcome.COMPLETE,
    val sensitive: Boolean = false,
    val goodFriday: Boolean = false,
    val space: NotebookSpace? = ticket?.space,
    val sermon: Boolean = ticket?.sermon == true,
    val audioMs: Long? = null,
    val transcriptWords: Int? = null,
    val retried: Boolean = false,
    val recordingActive: Boolean = false,
    val device: DeviceFacts = DeviceFacts(),
    val companionShown: Boolean = true,
    val nodPlayedInSnackbar: Boolean = false,
    val clock: LocalClock,
    val ledger: MomentLedger = MomentLedger(),
    val rotation: RotationState = RotationState(),
    val flags: MomentFlags = MomentFlags.Current
) {
    val momentSpace: MomentSpace get() = MomentSpace.of(space)
    /** Faith, or a sermon: gentle, no hops, no sparkles, no haptics, no chime. */
    val quiet: Boolean get() = momentSpace == MomentSpace.FAITH || sermon
    /**
     * A recording too short to celebrate. A no-speech note is never "very short": it gets its moment like any
     * other note (founder decision, 2026-10-14). Its zero word count is not a sign of a short recording.
     */
    val veryShort: Boolean
        get() = outcome != NoteOutcome.NO_SPEECH &&
            ((audioMs != null && audioMs < ShortAudioMs) || (transcriptWords != null && transcriptWords < ShortWords))

    companion object {
        const val ShortAudioMs = 20_000L
        const val ShortWords = 30
    }
}

/** Why a note opens with no moment. */
enum class PlainReason(
    /** The companion in the StatusLine's leading slot, if any (§4). The StatusLine text is the live region. */
    val statusCompanion: CompanionState?
) {
    /** Not a fresh open (rotation, return from a sub-screen, resume, or the open already played). */
    NOT_FRESH(null),
    /** No ticket and `unveilOnEveryOpen` is off: reopening a note is plain. */
    NO_TICKET(null),
    FAILED(CompanionState.WORRIED),
    PARTIAL(CompanionState.WORRIED),
    AI_FALLBACK(CompanionState.WORRIED),
    MODEL_REQUIRED(CompanionState.SLEEPY),
    /** Prayer request, grief tag or Good Friday: no companion motion. */
    SENSITIVE(null),
    /** Animations are off: the static end state (the note) and the polite announcement. */
    REDUCED_MOTION(null),
    /** A recording is running: nothing now; the ticket waits for the stop. */
    DEFERRED(null)
}

/** The rung of the ladder (§3.3) for one open. */
sealed interface MomentPlan {
    /** Whether the host makes the one polite "… is ready" announcement for this plan (§7.8). */
    val announce: Boolean

    /**
     * The First Keep (§2). [onceEver] is one of the two once-ever plays (the hello sample note, or the
     * first real recording); [chime] is only ever true for those, and never when [quiet]. The host still
     * gates the chime by the Sounds setting, the ringer mode and Do Not Disturb (`FirstKeepChime`).
     */
    data class FirstKeep(
        val quiet: Boolean,
        val hello: Boolean,
        val onceEver: Boolean,
        val chime: Boolean,
        val space: MomentSpace,
        val companion: Boolean = true
    ) : MomentPlan {
        override val announce: Boolean get() = true
    }

    /**
     * One Unveil variant (§3.4). [durationMs] already has the space scale, familiarity and the 1.2–1.8 s
     * clamp; the system animator scale is applied by the clock. [quiet] is the sermon Dawn (no glint, eyes
     * closed, soft nod, no haptic).
     */
    data class Unveil(val variant: UnveilVariant, val durationMs: Int, val quiet: Boolean, val space: MomentSpace) : MomentPlan {
        override val announce: Boolean get() = true
    }

    /**
     * The 0.6 s nod (§3.5). [companion] false is the header fade-up only (presence off, no companion, or the
     * snackbar already nodded). [quiet] is the sermon's soft nod.
     */
    data class Nod(val companion: Boolean = true, val quiet: Boolean = false, val space: MomentSpace = MomentSpace.EVERYDAY) : MomentPlan {
        override val announce: Boolean get() = true
    }

    /** No motion: the note is simply there. [fix] is the StatusLine's action for failures (§4). */
    data class Plain(val reason: PlainReason, val fix: FixAction? = null) : MomentPlan {
        override val announce: Boolean get() = reason == PlainReason.SENSITIVE || reason == PlainReason.REDUCED_MOTION
    }
}

/** What to do with the note's ticket after planning. */
enum class TicketAction {
    /** Leave it (there is none, or the open wasn't fresh). */
    NONE,
    /** Remove it: this note's arrival has been handled (or it had expired). */
    CONSUME,
    /** Keep it, marked deferred: a recording was running (§3.3 row 4). */
    DEFER
}

/** Side effects of a plan: ledger records, the new rotation state, and the ticket. */
data class MomentEffects(
    val record: List<Moment> = emptyList(),
    val rotation: RotationState? = null,
    val ticket: TicketAction = TicketAction.NONE
)

/** A plan and its effects. Destructures like the spec's `Pair<MomentPlan, MomentEffects>`. */
data class PlannedMoment(val plan: MomentPlan, val effects: MomentEffects)

object MomentPlanner {

    /** The sermon's quiet Dawn: Faith timing, no familiarity, never advances the rotation. */
    val SermonDawnMs: Int get() = UnveilRotation.scaledMs(UnveilVariant.DAWN.baseMs, MomentSpace.FAITH.timeScale)

    /**
     * The §3.3 ladder, first matching row wins, with the flag rules of the §3.3 note and the open rules of
     * §3.2. See the class docs of [MomentFacts] for who supplies what.
     */
    fun plan(f: MomentFacts): PlannedMoment {
        val now = f.clock.nowMs
        val t = f.ticket
        if (!f.freshOpen) return PlannedMoment(MomentPlan.Plain(PlainReason.NOT_FRESH), MomentEffects())
        val arrival = t != null && t.isValid(now)
        // A ticket is consumed by any fresh open that reads it (an expired one is just cleaned up).
        val consume = if (t != null) TicketAction.CONSUME else TicketAction.NONE
        fun done(plan: MomentPlan, record: List<Moment> = emptyList(), rotation: RotationState? = null, ticket: TicketAction = consume) =
            PlannedMoment(plan, MomentEffects(record, rotation, ticket))

        // §3.2: without the every-open flag, only an arrival (a valid ticket) gets a moment.
        if (!arrival && !f.flags.unveilOnEveryOpen) return done(MomentPlan.Plain(PlainReason.NO_TICKET))

        // Row 1: never celebrate a failure.
        failure(f)?.let { return done(it) }
        // Row 2: sacred notes and Good Friday.
        if (f.sensitive || f.goodFriday) return done(MomentPlan.Plain(PlainReason.SENSITIVE))

        val space = f.momentSpace
        val quiet = f.quiet
        val shown = f.companionShown

        // Row 3: the First Keep.
        if (arrival) {
            val ticket = t!!
            val onceEver: Moment? = when {
                ticket.hello && f.ledger.allows(Moment.FirstKeepHello, f.clock) -> Moment.FirstKeepHello
                ticket.firstEver && f.ledger.allows(Moment.FirstRecordingPeak, f.clock) -> Moment.FirstRecordingPeak
                else -> null
            }
            val everyNote = f.flags.firstKeepEveryNote
            if (onceEver != null || everyNote) {
                // §2.2: a First Keep waits for a running recording to stop (row 4).
                if (f.recordingActive) return done(MomentPlan.Plain(PlainReason.DEFERRED), ticket = TicketAction.DEFER)
                if (onceEver != null) {
                    // Once ever: plays even with reduced motion (its static end state) or on a low-end phone.
                    return done(
                        MomentPlan.FirstKeep(quiet, hello = ticket.hello, onceEver = true, chime = !quiet, space = space, companion = shown),
                        record = listOf(onceEver)
                    )
                }
                // Every note (decision 9): the system and honesty guards still step down.
                if (f.device.reducedMotion) return done(MomentPlan.Plain(PlainReason.REDUCED_MOTION))
                if (f.retried) return done(nod(f, space))
                if (f.device.batterySaver || f.device.lowEnd || f.veryShort) return done(nod(f, space))
                return done(MomentPlan.FirstKeep(quiet, hello = false, onceEver = false, chime = false, space = space, companion = shown))
            }
        }

        // Row 4: a recording is running. An arrival waits; a later open just nods.
        if (f.recordingActive) {
            return if (arrival) done(MomentPlan.Plain(PlainReason.DEFERRED), ticket = TicketAction.DEFER) else done(nod(f, space))
        }
        // Row 5: reduced motion is plain plus the announcement.
        if (f.device.reducedMotion) return done(MomentPlan.Plain(PlainReason.REDUCED_MOTION))
        // Row 4, after the stop: each queued note opens with a nod. §4: a retry that succeeded nods.
        if ((arrival && t!!.deferred) || f.retried) return done(nod(f, space))

        val budgeted = !f.flags.unveilOnEveryOpen
        val budgetLeft = !budgeted || f.ledger.allows(Moment.Unveil, f.clock)
        val spend = if (budgeted) listOf<Moment>(Moment.Unveil) else emptyList()

        // Row 6: sermons are calm: Dawn quiet, or a quiet nod.
        if (f.sermon) {
            if (!shown) return done(MomentPlan.Nod(companion = false, quiet = true, space = MomentSpace.FAITH))
            return if (budgetLeft) done(MomentPlan.Unveil(UnveilVariant.DAWN, SermonDawnMs, quiet = true, space = MomentSpace.FAITH), record = spend)
            else done(MomentPlan.Nod(companion = !f.nodPlayedInSnackbar, quiet = true, space = MomentSpace.FAITH))
        }
        // Row 7: restraint. Back-to-back is a note-ready rule, and only while the First Keep isn't on every note.
        val backToBack = arrival && !f.flags.firstKeepEveryNote && t!!.backToBack
        if (f.device.batterySaver || f.device.lowEnd || f.veryShort || backToBack) return done(nod(f, space))
        // Row 8: the rationed (or, with the flag, every-open) reward.
        if (budgetLeft) {
            if (!shown) return done(MomentPlan.Nod(companion = false, space = space))
            val pick = UnveilRotation.next(f.rotation, space)
            val ms = UnveilRotation.durationMs(pick.variant, space, pick.state.seen(pick.variant))
            return done(MomentPlan.Unveil(pick.variant, ms, quiet = false, space = space), record = spend, rotation = pick.state)
        }
        // Row 9.
        return done(nod(f, space))
    }

    private fun nod(f: MomentFacts, space: MomentSpace) =
        MomentPlan.Nod(companion = f.companionShown && !f.nodPlayedInSnackbar, quiet = f.quiet, space = space)

    private fun failure(f: MomentFacts): MomentPlan.Plain? {
        val outcome = if (f.ticket?.partial == true && f.outcome == NoteOutcome.COMPLETE) NoteOutcome.PARTIAL else f.outcome
        return when (outcome) {
            // A note with no speech gets its moment like a complete one (founder decision, 2026-10-14).
            // Its "We didn't hear anyone speaking" line is the NoteStatus line, not the planner's.
            NoteOutcome.COMPLETE, NoteOutcome.NO_SPEECH -> null
            NoteOutcome.PARTIAL -> MomentPlan.Plain(PlainReason.PARTIAL, FixAction.RETRY)
            NoteOutcome.AI_FALLBACK -> MomentPlan.Plain(PlainReason.AI_FALLBACK, FixAction.REDO_ONLINE)
            NoteOutcome.FAILED -> MomentPlan.Plain(PlainReason.FAILED, FixAction.RETRY)
            NoteOutcome.MODEL_REQUIRED -> MomentPlan.Plain(PlainReason.MODEL_REQUIRED, FixAction.DOWNLOAD_MODELS)
        }
    }
}
