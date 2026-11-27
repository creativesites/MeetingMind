package com.craftflowtechnologies.meetingmind.feature.today.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.feature.moments.MilestoneHost
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.RhythmLine
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneDetector
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRhythm
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.displayName
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.feature.importing.LocalYouTubeSheet
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** The companion's avatar in the line: under 48 dp, so it draws still (tier T0) and never runs a clock. */
private val LineCompanionSize = 44.dp

/**
 * The companion's line on a home: one timely thing it has to say (see [HomeNudges]), with one action
 * and a way to wave it off. When it has nothing to say, the week's rhythm line (from the companion
 * session's [RhythmLine]) shows in the same place, so a home never has two companion lines. Hidden with the companion (presence, "No
 * companion", hide until tomorrow).
 *
 * [showCompanion] is off where the companion is already large on screen (Today's hero).
 */
@Composable
fun HomeCompanionLine(
    space: NotebookSpace?,
    onRecord: () -> Unit,
    onOpenNote: (String) -> Unit,
    /** Null where the home can't open a recording: a ready line then opens its note. */
    onOpenRecording: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    firstRun: Boolean = true,
    showCompanion: Boolean = true,
    /** Off where the home already lists recordings in progress (Today's "For you"). */
    showProcessing: Boolean = true,
    /** The weekly rhythm counted when there's nothing timely to say; null for none. */
    rhythm: CompanionRhythm.Kind? = rhythmFor(space)
) {
    if (!rememberCompanionVisible(CompanionPage.HOME)) return
    val vm: HomeCompanionViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refresh() }
    val facts by vm.facts.collectAsState()
    val settings by rememberCompanionSettings()
    val name = settings.form?.let { settings.displayName(it) } ?: return
    var version by remember { mutableIntStateOf(0) }
    val nudge = remember(facts, name, space, firstRun, showProcessing, onSearch != null, onOpenRecording != null, version) {
        facts?.let { f ->
            val here = f.copy(
                companionName = name, space = space, firstRun = firstRun,
                canSearch = onSearch != null, canOpenRecording = onOpenRecording != null
            )
            HomeNudges.pick(if (showProcessing) here else here.copy(processing = null, processingCount = 0), vm::seen)
        }
    }
    LaunchedEffect(nudge?.key) { nudge?.let(vm::shown) }

    // Keep the last line while it animates away.
    var shown by remember { mutableStateOf<HomeNudge?>(null) }
    if (nudge != null) shown = nudge
    val youTube = LocalYouTubeSheet.current

    // [modifier] (its padding) goes on what is drawn, so an empty line leaves no gap.
    Column {
        AnimatedVisibility(
            visible = nudge != null,
            enter = fadeIn(MM.motion.standard()) + expandVertically(MM.motion.standard()),
            exit = fadeOut(MM.motion.quick()) + shrinkVertically(MM.motion.standard())
        ) {
            shown?.let { n ->
                HomeCompanionCard(
                    nudge = n, companionName = name, showCompanion = showCompanion, modifier = modifier,
                    onAction = {
                        when (val a = n.action) {
                            NudgeAction.Record -> onRecord()
                            NudgeAction.YouTube -> youTube.open()
                            NudgeAction.Search -> onSearch?.invoke()
                            is NudgeAction.OpenNote -> onOpenNote(a.noteId)
                            is NudgeAction.OpenRecording -> onOpenRecording?.invoke(a.meetingId)
                            null -> Unit
                        }
                        if (n.kind != NudgeKind.PROCESSING) { vm.done(n); version++ }
                    },
                    onDismiss = { vm.done(n); version++ }
                )
            }
        }
        // Nothing timely: this week's quiet rhythm, if there is one (RhythmLine draws nothing for a quiet week).
        val f = facts
        if (nudge == null && f != null && rhythm != null) {
            val line = remember(f, rhythm) {
                val times = if (rhythm == CompanionRhythm.Kind.FAITH_DAYS) f.devotionalReadTimes else f.noteTimes
                CompanionRhythm.thisWeek(rhythm, times, LocalClock(f.nowMs, f.utcOffsetMs))
            }
            RhythmLine(line, modifier)
        }
    }
}

/**
 * Which rhythm a home counts: Faith counts days with the devotional, every other home counts notes kept.
 * (Learn will count study days once there is a review log to read; see the companion plan.)
 */
fun rhythmFor(space: NotebookSpace?): CompanionRhythm.Kind =
    if (space == NotebookSpace.FAITH) CompanionRhythm.Kind.FAITH_DAYS else CompanionRhythm.Kind.NOTES_KEPT

/**
 * Faith's private milestones (seven devotionals in a month, a reading plan finished), shown once ever by the
 * companion session's [MilestoneHost]. Put it last in a full-size Box; it aligns itself and pads for the bar.
 */
@Composable
fun FaithMilestones(planDone: Boolean, planDays: Int, modifier: Modifier = Modifier) {
    if (!rememberCompanionVisible(CompanionPage.HOME)) return
    val vm: HomeCompanionViewModel = viewModel()
    val facts by vm.facts.collectAsState()
    val f = facts ?: return
    val kind = remember(f, planDone, planDays) {
        MilestoneDetector.forReadingPlan(if (planDone) planDays else 0, planDays)
            ?: MilestoneDetector.forDevotionals(f.devotionalReadTimes, LocalClock(f.nowMs, f.utcOffsetMs))
    }
    MilestoneHost(kind, modifier)
}

/** The line itself, drawn from a [HomeNudge]: stateless, for previews and screenshot tests. */
@Composable
fun HomeCompanionCard(
    nudge: HomeNudge,
    companionName: String,
    showCompanion: Boolean,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    Surface(
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(1.dp, c.line),
        modifier = modifier.fillMaxWidth().testTag("home_companion_line")
    ) {
        Row(Modifier.padding(MM.space.m), verticalAlignment = Alignment.Top) {
            if (showCompanion) {
                ZuriSlot(CompanionPage.HOME, LineCompanionSize, state = nudge.state)
                Spacer(Modifier.width(MM.space.m))
            }
            Column(Modifier.weight(1f)) {
                Text(companionName, style = MM.type.caption, color = c.accent)
                Text(
                    nudge.text, style = MM.type.body, color = c.ink,
                    modifier = Modifier.padding(top = MM.space.xs).semantics { liveRegion = LiveRegionMode.Polite }
                )
                Row(
                    Modifier.padding(top = MM.space.s).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (nudge.dismissable) LineButton(nudge.dismissLabel, primary = false, tag = "home_companion_dismiss", onClick = onDismiss)
                    if (nudge.action != null && nudge.actionLabel != null) LineButton(nudge.actionLabel, primary = true, tag = "home_companion_action", onClick = onAction)
                }
            }
        }
    }
}

@Composable
private fun LineButton(label: String, primary: Boolean, tag: String, onClick: () -> Unit) {
    val c = MM.colors
    Surface(
        onClick = onClick,
        shape = MM.radius.pill,
        color = if (primary) c.accent else c.surfaceRaised,
        border = if (primary) null else BorderStroke(1.dp, c.line),
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag)
    ) {
        Row(Modifier.padding(horizontal = MM.space.l), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MM.type.bodyStrong, color = if (primary) c.onAccent else c.inkSecondary, modifier = Modifier.padding(vertical = MM.space.s))
        }
    }
}
