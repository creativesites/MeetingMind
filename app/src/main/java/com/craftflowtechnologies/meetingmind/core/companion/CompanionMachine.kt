package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the companion shows, resolved from the facts the app reported (§3.3, §10.3).
 *
 * [base], [variant] and [fix] are derived by [resolve]; the rest are facts. Invariant:
 * `base == WORRIED ⇒ fix != null` (the companion is never worried without a way out).
 */
data class CompanionUiState(
    val base: CompanionState = CompanionState.IDLE,
    val oneShot: OneShot? = null,
    val variant: CompanionVariant = CompanionVariant.Normal,
    /** Processing caption (§5.4), rendered by the host as text. */
    val caption: CaptionKey? = null,
    val fix: FixAction? = null,
    val page: CompanionPage? = null,
    val recording: RecordingFacts? = null,
    val processing: Boolean = false,
    /** A fix was tapped and the work is running again: success earns a nod, not the full hop. */
    val retrying: Boolean = false,
    val failure: Failure? = null,
    val reading: Boolean = false,
    val modelsMissing: Boolean = false,
    val lateNight: Boolean = false,
    /** The current live-whisper prompt (Z-25 experiment), only while opted in. */
    val whisper: WhisperPrompt? = null
) {
    init {
        require(base != CompanionState.WORRIED || fix != null) { "Worried needs a fix" }
    }

    /** What to draw: Worried outranks a one-shot, which outranks the base state. */
    val visual: CompanionVisual
        get() = when {
            base == CompanionState.WORRIED -> CompanionState.WORRIED
            oneShot != null -> oneShot.kind.visual
            else -> base
        }
}

/** Everything the reducer may consult besides the state and the event. */
data class ReduceContext(
    val settings: CompanionSettings = CompanionSettings(),
    val clock: LocalClock = LocalClock(0),
    val ledger: MomentLedger = MomentLedger(),
    val liveWhisper: Boolean = false,
    /** Good Friday: nothing celebrates (§2.6). */
    val goodFriday: Boolean = false
)

/** The new state, plus the moments the machine must record in the [MomentLedger]. */
data class Reduction(val state: CompanionUiState, val moments: List<Moment> = emptyList())

object CompanionReducer {

    private val WorkingStages = setOf(
        ProcessingStage.PREPARING_AUDIO, ProcessingStage.DETECTING_SPEECH, ProcessingStage.TRANSCRIBING,
        ProcessingStage.DIARIZING, ProcessingStage.CLEANING_TRANSCRIPT, ProcessingStage.ANALYZING,
        ProcessingStage.SAVING_RESULTS
    )

    /** Pure: the same inputs always give the same output. */
    fun reduce(state: CompanionUiState, event: CompanionEvent, ctx: ReduceContext): Reduction {
        val now = ctx.clock.nowMs
        // A finished one-shot falls away before anything else happens.
        val s = if (state.oneShot?.isOver(now) == true) state.copy(oneShot = null) else state
        val quiet = s.recording?.quiet == true
        val canOneShot = !quiet
        fun oneShot(kind: OneShotKind) = OneShot(kind, now)
        val moments = mutableListOf<Moment>()

        val next: CompanionUiState = when (event) {
            is CompanionEvent.ScreenShown -> s.copy(page = event.page)

            is CompanionEvent.RecordingStarted -> {
                val startsQuiet = event.kind == RecordingKind.SERMON && ctx.settings.quietSermons
                val clearsFailure = s.failure?.reason == FailureReason.PERMISSION_DENIED ||
                    s.failure?.reason == FailureReason.NO_STORAGE
                s.copy(
                    recording = RecordingFacts(event.kind, event.space, quiet = startsQuiet),
                    lateNight = false,
                    whisper = null,
                    failure = if (clearsFailure) null else s.failure,
                    oneShot = if (startsQuiet) null else s.oneShot
                )
            }

            is CompanionEvent.SermonQuietChanged -> {
                val rec = s.recording
                if (rec == null) s else s.copy(
                    recording = rec.copy(quiet = event.quiet, whisperOptIn = if (event.quiet) false else rec.whisperOptIn),
                    whisper = if (event.quiet) null else s.whisper,
                    oneShot = if (event.quiet) null else s.oneShot
                )
            }

            CompanionEvent.RecordingStopped ->
                if (s.recording == null) s
                else s.copy(recording = null, whisper = null, processing = true, caption = null)

            is CompanionEvent.ProcessingStageChanged -> when (event.stage) {
                in WorkingStages -> s.copy(processing = true, caption = CaptionKey(event.stage, event.detail))
                ProcessingStage.COMPLETED -> s.copy(processing = false, caption = null)
                ProcessingStage.FAILED -> s.copy(processing = false, caption = null)
                ProcessingStage.CANCELLED ->
                    s.copy(processing = false, retrying = false, caption = CaptionKey(ProcessingStage.CANCELLED))
                else -> s
            }

            is CompanionEvent.ProcessingFailed -> s.copy(
                failure = Failure(event.reason, event.fix), processing = false, retrying = false, caption = null
            )

            is CompanionEvent.NoteReady -> {
                val cleared = s.copy(processing = false, retrying = false, caption = null, failure = null)
                val celebrationAllowed = canOneShot && !event.sensitive && !ctx.goodFriday
                val shot = when {
                    !celebrationAllowed -> null
                    event.isFirstEver && ctx.ledger.allows(Moment.FirstRecordingPeak, ctx.clock) -> {
                        moments += Moment.FirstRecordingPeak
                        moments += Moment.FullCelebration
                        OneShotKind.CELEBRATING
                    }
                    s.retrying -> OneShotKind.NOD
                    ctx.ledger.allows(Moment.FullCelebration, ctx.clock) -> {
                        moments += Moment.FullCelebration
                        OneShotKind.CELEBRATING
                    }
                    else -> OneShotKind.NOD
                }
                cleared.copy(oneShot = shot?.let(::oneShot) ?: cleared.oneShot)
            }

            CompanionEvent.RetryTapped -> {
                val failure = s.failure
                when (failure?.fix) {
                    null -> s
                    FixAction.RETRY, FixAction.REDO_ONLINE ->
                        s.copy(failure = null, processing = true, retrying = true, caption = null)
                    else -> s.copy(failure = null)
                }
            }

            CompanionEvent.ModelsMissing -> s.copy(modelsMissing = true)
            CompanionEvent.ModelsReady -> s.copy(modelsMissing = false)
            CompanionEvent.ReadAloudStarted -> s.copy(reading = true)
            CompanionEvent.ReadAloudStopped -> s.copy(reading = false)

            is CompanionEvent.Milestone -> {
                val m = Moment.Proud(event.kind)
                if (canOneShot && ctx.ledger.allows(m, ctx.clock)) {
                    moments += m
                    s.copy(oneShot = oneShot(OneShotKind.PROUD))
                } else s
            }

            is CompanionEvent.OnboardingQuestion, CompanionEvent.QuizQuestion ->
                if (canOneShot) s.copy(oneShot = oneShot(OneShotKind.CURIOUS)) else s

            is CompanionEvent.ClockTick -> s.copy(lateNight = event.hourOfDay >= 23 || event.hourOfDay < 5)

            is CompanionEvent.FormChanged ->
                if (event.form != null && canOneShot) s.copy(oneShot = oneShot(OneShotKind.NOD)) else s

            CompanionEvent.Tapped -> {
                val awake = s.copy(lateNight = false)
                if (canOneShot && s.oneShot == null && ctx.ledger.allows(Moment.TapHi, ctx.clock)) {
                    moments += Moment.TapHi
                    awake.copy(oneShot = oneShot(OneShotKind.NOD))
                } else awake
            }

            CompanionEvent.OneShotEnded -> s.copy(oneShot = null)

            is CompanionEvent.WhisperOptIn -> {
                val rec = s.recording
                if (!ctx.liveWhisper || rec == null || rec.quiet || rec.kind == RecordingKind.SERMON) s
                else s.copy(
                    recording = rec.copy(whisperOptIn = event.enabled),
                    whisper = if (event.enabled) s.whisper else null
                )
            }

            is CompanionEvent.WhisperPromptReady -> {
                val rec = s.recording
                if (ctx.liveWhisper && rec != null && rec.whisperOptIn && !rec.quiet) s.copy(whisper = event.prompt) else s
            }

            CompanionEvent.WhisperDismissed -> s.copy(whisper = null)
        }
        return Reduction(resolve(next), moments)
    }

    /** Derives base, variant and fix from the facts, in the §3.3 resolution order. */
    fun resolve(s: CompanionUiState): CompanionUiState {
        val base = when {
            s.failure != null -> CompanionState.WORRIED
            s.recording != null -> CompanionState.LISTENING
            s.processing -> CompanionState.THINKING
            s.reading -> CompanionState.READING
            s.modelsMissing || s.lateNight -> CompanionState.SLEEPY
            else -> CompanionState.IDLE
        }
        val variant = when {
            base == CompanionState.LISTENING && s.recording?.quiet == true -> CompanionVariant.Quiet
            base != CompanionState.WORRIED && s.oneShot?.kind == OneShotKind.NOD -> CompanionVariant.Nod
            else -> CompanionVariant.Normal
        }
        return s.copy(base = base, variant = variant, fix = s.failure?.fix)
    }
}

/** The presence filter (§3.3): whether a slot on [page] draws the companion at all. */
object CompanionPresence {

    /** Pages that still show the companion under "Big moments". */
    private val BigMoments = setOf(
        CompanionPage.RECORDING, CompanionPage.PROCESSING, CompanionPage.NOTE_READY, CompanionPage.FIRST_RECORDING,
        CompanionPage.WORRIED, CompanionPage.MILESTONE
    )

    fun visibleOn(page: CompanionPage, settings: CompanionSettings, clock: LocalClock): Boolean {
        if (page.isChoosing) return true
        if (settings.form == null) return false
        val hidden = settings.hiddenUntilMs
        if (hidden != null && clock.nowMs < hidden) return false
        return when (settings.presence) {
            Presence.OFF -> false
            Presence.AROUND -> true
            Presence.MOMENTS -> page in BigMoments
        }
    }
}

/**
 * One per app process (§10.3): holds the companion state, applies events through
 * [CompanionReducer] and records capped moments in the ledger.
 *
 * Settings changes (presence, hidden-until, form) arrive through [settings]; [visibleOn] applies them.
 */
class CompanionMachine(
    private val scope: CoroutineScope,
    private val settings: StateFlow<CompanionSettings>,
    private val store: MomentLedgerStore? = null,
    private val clock: () -> LocalClock = { LocalClock(System.currentTimeMillis()) },
    private val liveWhisper: Boolean = CompanionFlags.liveWhisper,
    private val goodFriday: (LocalClock) -> Boolean = { false }
) {
    private val _state = MutableStateFlow(CompanionUiState())
    val state: StateFlow<CompanionUiState> = _state.asStateFlow()

    private val ledger = MutableStateFlow(MomentLedger())

    init {
        if (store != null) scope.launch {
            store.ledger.collect { stored ->
                // Keep whichever is newer, so a moment recorded a moment ago is never forgotten.
                ledger.update { mem -> MomentLedger((stored.last.keys + mem.last.keys).associateWith { k -> maxOf(stored.last[k] ?: 0, mem.last[k] ?: 0) }) }
            }
        }
    }

    fun onEvent(e: CompanionEvent) {
        val now = clock()
        val ctx = ReduceContext(settings.value, now, ledger.value, liveWhisper, goodFriday(now))
        val r = CompanionReducer.reduce(_state.value, e, ctx)
        _state.value = r.state
        if (r.moments.isNotEmpty()) {
            ledger.update { l -> r.moments.fold(l) { acc, m -> acc.record(m, now) } }
            if (store != null) scope.launch { r.moments.forEach { store.record(it, now) } }
        }
    }

    fun visibleOn(page: CompanionPage): Boolean = CompanionPresence.visibleOn(page, settings.value, clock())
}
