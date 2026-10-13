package com.craftflowtechnologies.meetingmind.feature.moments

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentEnvironment
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentFacts
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.companion.moment.PlainReason
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
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

/** The moments store; null is the app's DataStore-backed one. Tests provide their own. */
val LocalMomentLedgerStore = androidx.compose.runtime.staticCompositionLocalOf<MomentLedgerStore?> { null }

/** What a note screen knows about its moment: the plan (null while it loads) and the note's facts. */
@Stable
class NoteMomentState {
    var plan: MomentPlan? by mutableStateOf(null)
        internal set
    var facts: NoteFacts? by mutableStateOf(null)
        internal set
    /** The First Keep overlay is showing; "Open note" (or Back) clears it, and it never comes back. */
    var overlay: Boolean by mutableStateOf(false)
        internal set

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
fun rememberNoteMoment(noteId: String, refreshKey: Any? = null): NoteMomentState {
    val context = LocalContext.current
    val session = rememberOpenSession()
    val state = remember(noteId) { NoteMomentState() }
    val provided = LocalMomentLedgerStore.current
    val store = remember(provided, context) { provided ?: MomentLedgerStore(context) }
    val shown by rememberUpdatedState(rememberCompanionVisible(CompanionPage.NOTE_READY))
    LaunchedEffect(noteId, refreshKey) {
        val facts = readNoteFacts(context, noteId) ?: return@LaunchedEffect
        state.facts = facts
        if (!session.claim()) {
            // Not a fresh open: the note just shows (a moment never replays).
            if (state.plan == null) state.plan = MomentPlan.Plain(PlainReason.NOT_FRESH)
            return@LaunchedEffect
        }
        NoteReadyEvents.dismiss(noteId)
        val now = System.currentTimeMillis()
        val clock = LocalClock(now, TimeZone.getDefault().getOffset(now).toLong())
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val retried = RetriedNotes.consume(context, noteId)
        val fromSnackbar = NoteReadyEvents.consumeSnackbarOpen(noteId)
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
        state.plan = planned
        state.overlay = planned is MomentPlan.FirstKeep
    }
    return state
}

/**
 * The id [rememberNoteMoment] plans for a note editor (`NoteEditorScreen`). The moment's facts come from a
 * recording, so a note plans on its first recording (the recordings list is ordered by creation). It plans
 * nothing (""), and waits, while the note is still loading or behind the Faith lock: a moment is claimed only
 * once it can be shown. A note with no recording has no facts, so it opens plain.
 */
fun noteMomentSubject(noteLoaded: Boolean, locked: Boolean, recordingIds: Collection<String>): String =
    if (!noteLoaded || locked) "" else recordingIds.firstOrNull().orEmpty()

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
    val plan = state.plan as? MomentPlan.FirstKeep ?: return
    val facts = state.facts ?: return
    if (!state.overlay) return
    val settings by rememberCompanionSettings()
    val context = LocalContext.current
    val userName by androidx.compose.runtime.produceState<String?>(null) {
        value = runCatching { UserPreferencesManager(context).preferencesFlow.first().userName?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
    }
    val copy = firstKeepCopy(plan, facts, actionCount, userName)
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
        key = "firstkeep:${facts.noteId}",
        onOpen = state::closeOverlay,
        onShare = { state.closeOverlay(); onShare() }
    )
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
