package com.craftflowtechnologies.meetingmind.feature.moments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.audio.MeetingRecordingService
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** What the snackbar says: one note, or several that waited for a recording to stop. */
sealed interface ReadyLine {
    data class One(val event: NoteReadyEvent) : ReadyLine
    data class Many(val count: Int) : ReadyLine

    companion object {
        /** The notes to announce now, without the ones the person is already looking at. Null: nothing to say. */
        fun of(events: List<NoteReadyEvent>, suppressed: (String) -> Boolean): ReadyLine? {
            val shown = events.filterNot { suppressed(it.noteId) }
            return when (shown.size) {
                0 -> null
                1 -> One(shown.single())
                else -> Many(shown.size)
            }
        }
    }
}

/**
 * The in-app "note is ready" line (signature moments §3.2, §4): "{title} is ready." with **Open** and a 40 dp
 * nod in the leading slot (the sermon line is "Your sermon notes are ready 🙏"). While a recording runs the
 * notes wait; after the stop they come as one line, "{n} notes are ready." with **Show**. A note the person
 * is already looking at (its processing or its note screen) is skipped. Sensitive notes get the plain line
 * and no companion.
 */
@Composable
fun NoteReadySnackbarHost(
    suppressed: (noteId: String) -> Boolean,
    onOpen: (noteId: String) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
    pending: StateFlow<List<NoteReadyEvent>> = NoteReadyEvents.pending,
    capturing: StateFlow<Boolean> = MeetingRecordingService.capturing,
    visibleMs: Long = 6_000L
) {
    val events by pending.collectAsState()
    val recording by capturing.collectAsState()
    val isSuppressed by rememberUpdatedState(suppressed)
    var line by remember { mutableStateOf<ReadyLine?>(null) }
    LaunchedEffect(events, recording) {
        if (recording || events.isEmpty()) return@LaunchedEffect
        val next = ReadyLine.of(NoteReadyEvents.drain(), isSuppressed) ?: return@LaunchedEffect
        line = next
    }
    LaunchedEffect(line) {
        if (line != null) { delay(visibleMs); line = null }
    }
    AnimatedVisibility(
        visible = line != null, modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        val current = line ?: return@AnimatedVisibility
        NoteReadySnackbar(
            current,
            onAction = {
                line = null
                when (current) {
                    is ReadyLine.One -> { NoteReadyEvents.markSnackbarOpen(current.event.noteId); onOpen(current.event.noteId) }
                    is ReadyLine.Many -> onShowAll()
                }
            }
        )
    }
}

/** The line itself, for the host and for screenshots. */
@Composable
fun NoteReadySnackbar(line: ReadyLine, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    val settings by rememberCompanionSettings()
    val form = settings.form
    val showCompanion = form != null && rememberCompanionVisible(CompanionPage.NOTE_READY) &&
        !(line is ReadyLine.One && line.event.sensitive)
    val text = when (line) {
        is ReadyLine.One -> if (line.event.sermon && !line.event.sensitive) stringResource(R.string.companion_moment_sermon_ready)
        else stringResource(R.string.companion_moment_ready, line.event.title)
        is ReadyLine.Many -> pluralStringResource(R.plurals.companion_moment_queued_ready, line.count, line.count)
    }
    val action = stringResource(if (line is ReadyLine.One) R.string.companion_moment_open else R.string.companion_moment_show)
    Surface(
        color = c.ink, contentColor = c.background, shape = MM.radius.card, shadowElevation = MM.elevation.floating,
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MM.space.l, vertical = MM.space.m).testTag("note_ready_snackbar")
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(start = MM.space.m, end = MM.space.xs, top = MM.space.xs, bottom = MM.space.xs)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCompanion && form != null) {
                // The nod in the leading slot (§3.5); the note then opens with the header fade only.
                var nodding by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(NodMs); nodding = false }
                Companion(form = form, state = com.craftflowtechnologies.meetingmind.core.companion.CompanionState.IDLE, size = SnackbarCompanionSize, variant = if (nodding) CompanionVariant.Nod else CompanionVariant.Normal, modifier = Modifier)
                Spacer(Modifier.width(MM.space.s))
            }
            Text(text, style = MM.type.secondary, color = c.background, modifier = Modifier.weight(1f))
            TextAction(action, onAction, Modifier.testTag("note_ready_action"))
        }
    }
}

private val SnackbarCompanionSize: Dp = 40.dp
private const val NodMs = 600L
