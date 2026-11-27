package com.craftflowtechnologies.meetingmind.feature.moments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneDetector
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneKind
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentEnvironment
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentFacts
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.companion.moment.PlainReason
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.FirstKeepNote
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.FirstKeepOverlay
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.rememberOpenSession
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/**
 * Whether a First Keep overlay is on screen anywhere. Other app-wide prompts (the first-result offer) wait
 * for it, so nothing is ever drawn over the note's moment.
 */
object MomentOverlay {
    val showing = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/** The moments store; null is the app's DataStore-backed one. Tests provide their own. */
val LocalMomentLedgerStore = androidx.compose.runtime.staticCompositionLocalOf<MomentLedgerStore?> { null }

/** What a note screen knows about its moment: the plan (null while it loads) and the note's facts. */
@Stable
class NoteMomentState {
    var plan: MomentPlan? by mutableStateOf(null)
        internal set
    var facts: NoteFacts? by mutableStateOf(null)
        internal set
    private var showingOverlay: Boolean by mutableStateOf(false)

    /** The overlay is the note-opening reveal ([OpenReveal]), not a note's arrival: it closes by itself. */
    var openReveal: Boolean by mutableStateOf(false)
        internal set

    /** The First Keep overlay is showing; "Open note" (or Back) clears it, and it never comes back. */
    var overlay: Boolean
        get() = showingOverlay
        internal set(value) {
            showingOverlay = value
            MomentOverlay.showing.value = value
        }

    fun closeOverlay() { overlay = false }
}

/**
 * Plans the moment for one fresh open of a note screen (`MeetingDetail`, `WrapUp`), §3.2 and §7.2. The
 * planner and the store make every decision; this only gathers the facts: the note's outcome, sensitive
 * tags, sermon, audio length, transcript words, a retry, a recording in progress, the presence filter,
 * Good Friday and the device. A configuration change, a return from a sub-screen or the app resuming is
 * not a fresh open (`rememberOpenSession`), so it plans nothing and the note just shows.
 *
 * [refreshKey] reloads the facts (e.g. when the recording's status changes) without planning again.
 */
@Composable
fun rememberNoteMoment(
    noteId: String,
    refreshKey: Any? = null,
    /** Turn a fresh open's Unveil into the note-opening reveal ([OpenReveal]). Only the note editor asks. */
    openReveal: Boolean = false
): NoteMomentState {
    val context = LocalContext.current
    val session = rememberOpenSession()
    val state = remember(noteId) { NoteMomentState() }
    androidx.compose.runtime.DisposableEffect(state) {
        onDispose { if (state.overlay) MomentOverlay.showing.value = false }
    }
    val provided = LocalMomentLedgerStore.current
    val store = remember(provided, context) { provided ?: MomentLedgerStore(context) }
    val shown by rememberUpdatedState(rememberCompanionVisible(CompanionPage.NOTE_READY))
    val pendingReady by NoteReadyEvents.pending.collectAsState()
    val hasPendingReady = pendingReady.any { it.noteId == noteId }
    LaunchedEffect(noteId, refreshKey, hasPendingReady) {
        if (noteId.isBlank()) return@LaunchedEffect
        // A database read that fails while the note opens means no moment, never a crash (crash audit, 2026-11-17).
        val facts = runCatching { readNoteFacts(context, noteId) }.getOrNull()
            ?: runCatching { readTypedNoteFacts(context, noteId) }.getOrNull()
            ?: return@LaunchedEffect
        state.facts = facts
        if (session.claimed && !(hasPendingReady && state.plan !is MomentPlan.FirstKeep)) {
            // Not a fresh open: the note just shows (a moment never replays).
            if (state.plan == null) state.plan = MomentPlan.Plain(PlainReason.NOT_FRESH)
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val clock = LocalClock(now, TimeZone.getDefault().getOffset(now).toLong())
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val retried = RetriedNotes.consume(context, noteId)
        val fromSnackbar = NoteReadyEvents.consumeSnackbarOpen(noteId)
        // Planning consumes the ticket, so it finishes and is applied as one step. A restart of this effect
        // (the note-ready event and the refresh key often change together) must not cancel it half-way:
        // the ticket would be gone without its First Keep, and the next run would play an Unveil instead.
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            val planned = runCatching {
                store.planOpen(noteId, clock) { ticket, ledger, rotation ->
                    MomentFacts(
                        noteId = noteId, ticket = ticket, freshOpen = true, outcome = facts.outcome, sensitive = facts.sensitive,
                        goodFriday = NoteFactsRules.goodFriday(today), space = facts.space, sermon = facts.sermon,
                        audioMs = facts.audioMs, transcriptWords = facts.transcriptWords, retried = retried,
                        recordingActive = recordingActive(), device = MomentEnvironment.read(context), companionShown = shown,
                        nodPlayedInSnackbar = fromSnackbar, clock = clock, ledger = ledger, rotation = rotation
                    )
                }.plan
            }.getOrElse { MomentPlan.Plain(PlainReason.NOT_FRESH) }
            session.claim()
            NoteReadyEvents.dismiss(noteId)
            val reveal = openReveal && planned is MomentPlan.Unveil && OpenReveal.eligible(planned, facts) && OpenReveal.claim(noteId)
            state.openReveal = reveal
            state.plan = if (reveal) OpenReveal.planFor(planned as MomentPlan.Unveil) else planned
            state.overlay = state.plan is MomentPlan.FirstKeep
        }
    }
    return state
}

/**
 * The id [rememberNoteMoment] plans for a note editor (`NoteEditorScreen`). The moment's facts come from a
 * recording, so a note plans on its first recording (the recordings list is ordered by creation). A note with no
 * recording plans on the note itself (its facts come from the note, see [readTypedNoteFacts]). It plans nothing
 * (""), and waits, while the note is still loading or behind the Faith lock: a moment is claimed only once it can
 * be shown.
 */
fun noteMomentSubject(noteLoaded: Boolean, locked: Boolean, recordingIds: Collection<String>, noteId: String): String =
    if (!noteLoaded || locked) "" else recordingIds.firstOrNull() ?: noteId

/**
 * Chooses the moment subject for a note editor that may hold multiple recordings ("Record here"),
 * preferring whichever attached recording has a pending note-ready event or is the newest.
 */
fun selectNoteMomentSubject(
    noteLoaded: Boolean,
    locked: Boolean,
    recordingIds: Collection<String>,
    noteId: String,
    pendingIds: Set<String>
): String {
    if (!noteLoaded || locked) return ""
    return recordingIds.lastOrNull { it in pendingIds } ?: recordingIds.lastOrNull() ?: noteId
}

/**
 * The First Keep over the already-rendered note (§2.2): the card shows only the note's verbatim title and
 * key points. "Open note" and Back dismiss it; the note is already there underneath. [onShare] opens Create
 * with the note's title.
 */
@Composable
fun NoteFirstKeep(
    state: NoteMomentState,
    meta: String,
    keyPoints: List<String>,
    actionCount: Int,
    onShare: () -> Unit
) {
    NoteArrivalMilestone(state)
    val plan = state.plan as? MomentPlan.FirstKeep ?: return
    val facts = state.facts ?: return
    if (!state.overlay) return
    val settings by rememberCompanionSettings()
    val context = LocalContext.current
    val userName by androidx.compose.runtime.produceState<String?>(null) {
        value = runCatching { UserPreferencesManager(context).preferencesFlow.first().userName?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
    }
    val copy = if (state.openReveal) openRevealCopy(facts, actionCount) else firstKeepCopy(plan, facts, actionCount, userName)
    val closer = androidx.compose.runtime.rememberCoroutineScope()
    FirstKeepOverlay(
        plan = plan,
        form = settings.form,
        note = FirstKeepNote(facts.title, meta, keyPoints.take(3)),
        headline = copy.headline,
        subline = copy.subline,
        openLabel = stringResource(R.string.companion_moment_open_note),
        shareLabel = stringResource(R.string.companion_moment_share),
        announcement = copy.announcement,
        companionLine = if (plan.onceEver && !plan.quiet) stringResource(R.string.companion_moment_first_keep_companion_line) else null,
        sounds = settings.sounds,
        key = if (state.openReveal) "reveal:${facts.noteId}" else "firstkeep:${facts.noteId}",
        onOpen = state::closeOverlay,
        onShare = { state.closeOverlay(); onShare() },
        // The opening reveal dissolves into the note on its own, after a beat on the finished card.
        onEnd = {
            if (state.openReveal) closer.launch {
                kotlinx.coroutines.delay(OpenRevealHoldMs)
                state.closeOverlay()
            }
        }
    )
}

/**
 * The tenth-note milestone (ZURI_EXPERIENCE §3.1 Proud): once a note's arrival First Keep has been closed, the notes
 * kept so far are counted, and [MilestoneHost] shows the Proud card when the count is in
 * [MilestoneDetector.TenthNoteWindow] (once ever). Never after the opening reveal, which is a later open, not an
 * arrival, and never for a sensitive note. A count that can't be read means no card.
 */
@Composable
private fun NoteArrivalMilestone(state: NoteMomentState) {
    val context = LocalContext.current
    var arrived by remember(state) { mutableStateOf(false) }
    val arriving = state.overlay && !state.openReveal && state.plan is MomentPlan.FirstKeep && state.facts?.sensitive == false
    LaunchedEffect(arriving) { if (arriving) arrived = true }
    if (!arrived || state.overlay) return
    val kind by androidx.compose.runtime.produceState<MilestoneKind?>(null, state) {
        value = runCatching {
            MilestoneDetector.forNotesKept(MeetMindDatabase.getInstance(context).noteDao().observeWrittenCount().first())
        }.getOrNull()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { MilestoneHost(kind) }
}

/** How long the opening reveal rests on its finished card before showing the note. */
private const val OpenRevealHoldMs = 900L

/** The opening reveal's lines, by the note's type ([OpenReveal.kindOf]). */
@Composable
private fun openRevealCopy(facts: NoteFacts, actionCount: Int): FirstKeepCopy {
    val (headline, subline) = when (OpenReveal.kindOf(facts.type)) {
        OpenReveal.Kind.MEETING -> stringResource(R.string.moment_reveal_meeting_headline) to
            if (actionCount > 0) pluralStringResource(R.plurals.moment_reveal_meeting_sub_actions, actionCount, actionCount)
            else stringResource(R.string.moment_reveal_meeting_sub)
        OpenReveal.Kind.INTERVIEW -> stringResource(R.string.moment_reveal_interview_headline) to stringResource(R.string.moment_reveal_interview_sub)
        OpenReveal.Kind.LECTURE -> stringResource(R.string.moment_reveal_lecture_headline) to stringResource(R.string.moment_reveal_lecture_sub)
        OpenReveal.Kind.RESEARCH -> stringResource(R.string.moment_reveal_research_headline) to stringResource(R.string.moment_reveal_research_sub)
        OpenReveal.Kind.SERMON -> stringResource(R.string.moment_reveal_sermon_headline) to stringResource(R.string.companion_moment_first_keep_sub_faith)
        OpenReveal.Kind.FAITH -> stringResource(R.string.moment_reveal_faith_headline) to stringResource(R.string.moment_reveal_faith_sub)
        OpenReveal.Kind.IDEAS -> stringResource(R.string.moment_reveal_ideas_headline) to stringResource(R.string.moment_reveal_ideas_sub)
        OpenReveal.Kind.PERSONAL -> stringResource(R.string.moment_reveal_personal_headline) to stringResource(R.string.companion_moment_first_keep_sub)
    }
    return FirstKeepCopy(headline, subline, stringResource(R.string.companion_moment_ready, facts.title))
}

private data class FirstKeepCopy(val headline: String, val subline: String, val announcement: String)

/** The §9 lines for a First Keep. {name} only in the once-ever headline, and only if the person gave one. */
@Composable
private fun firstKeepCopy(plan: MomentPlan.FirstKeep, facts: NoteFacts, actionCount: Int, userName: String?): FirstKeepCopy {
    val headline = when {
        plan.hello -> stringResource(R.string.companion_moment_hello_headline)
        plan.onceEver && facts.sermon -> stringResource(R.string.companion_moment_first_keep_headline_sermon)
        plan.onceEver && userName != null -> stringResource(R.string.companion_moment_first_keep_headline_named, userName)
        plan.onceEver -> stringResource(R.string.companion_moment_first_keep_headline)
        facts.sermon -> stringResource(R.string.companion_moment_keep_headline_sermon)
        else -> stringResource(R.string.companion_moment_keep_headline)
    }
    val subline = when {
        plan.hello -> stringResource(R.string.companion_moment_hello_sub)
        plan.quiet -> stringResource(R.string.companion_moment_first_keep_sub_faith)
        plan.space == MomentSpace.WORK && actionCount > 0 -> pluralStringResource(R.plurals.companion_moment_first_keep_sub_actions, actionCount, actionCount)
        plan.space == MomentSpace.STUDY -> stringResource(R.string.companion_moment_first_keep_sub_study)
        else -> stringResource(R.string.companion_moment_first_keep_sub)
    }
    val announcement = if (plan.onceEver && !plan.hello) stringResource(R.string.companion_moment_first_keep_announcement, facts.title)
    else stringResource(R.string.companion_moment_ready, facts.title)
    return FirstKeepCopy(headline, subline, announcement)
}

/** The one polite line for an Unveil, a nod or a plain open (§7.8). */
@Composable
fun noteReadyAnnouncement(title: String): String = stringResource(R.string.companion_moment_ready, title)

/**
 * The honest line above the note for outcomes the note screen doesn't already explain (§4): a partial note,
 * the on-device fallback and no speech. The companion sits in the leading slot when there is a fix
 * (Worried); no speech has none. It is the StatusLine's own live region that announces it.
 */
@Composable
fun NoteStatus(outcome: NoteOutcome?, modifier: Modifier = Modifier, onRetry: () -> Unit, onRedoOnline: () -> Unit, onPlay: () -> Unit) {
    val (text, action, onAction, companion) = when (outcome) {
        NoteOutcome.PARTIAL -> Quad(stringResource(R.string.companion_moment_partial), stringResource(R.string.companion_moment_try_again), onRetry, true)
        NoteOutcome.AI_FALLBACK -> Quad(stringResource(R.string.companion_error_online_fallback), stringResource(R.string.companion_action_redo_online), onRedoOnline, true)
        NoteOutcome.NO_SPEECH -> Quad(stringResource(R.string.companion_moment_no_speech), stringResource(R.string.companion_moment_play_it), onPlay, false)
        else -> return
    }
    Row(modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
        if (companion) {
            ZuriSlot(CompanionPage.WORRIED, StatusCompanionSize, state = CompanionState.WORRIED)
            Spacer(Modifier.width(MM.space.s))
        }
        StatusLine(StatusKind.Warning, text, Modifier.weight(1f), actionLabel = action, onAction = onAction)
    }
}

/** The companion in a failure line's leading slot: Worried with a fix, Sleepy when models are missing (§4). */
@Composable
fun FailureCompanion(modelsMissing: Boolean, modifier: Modifier = Modifier) {
    ZuriSlot(
        CompanionPage.WORRIED, StatusCompanionSize, modifier,
        state = if (modelsMissing) CompanionState.SLEEPY else CompanionState.WORRIED, endGap = MM.space.s
    )
}

private val StatusCompanionSize = 32.dp

private data class Quad(val text: String, val action: String, val onAction: () -> Unit, val companion: Boolean)
