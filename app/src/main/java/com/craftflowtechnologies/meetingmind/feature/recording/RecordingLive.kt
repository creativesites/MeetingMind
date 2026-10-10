package com.craftflowtechnologies.meetingmind.feature.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.audio.RecordingState
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

private const val LEVEL_BARS = 48

/** The icon for each marker kind (outlined rounded, DESIGN_SYSTEM section 5). */
private fun iconOf(kind: MarkKind): ImageVector = when (kind) {
    MarkKind.KEY -> Icons.Rounded.Star
    MarkKind.ACTION -> Icons.Rounded.CheckCircleOutline
    MarkKind.QUESTION -> Icons.AutoMirrored.Rounded.HelpOutline
    MarkKind.DECISION -> Icons.Rounded.Gavel
    MarkKind.SCRIPTURE -> Icons.Rounded.AutoStories
    MarkKind.NOTE -> Icons.Rounded.EditNote
    MarkKind.PRAYER -> Icons.Rounded.VolunteerActivism
}

/**
 * The live recording (R-1): paper or graphite like the rest of the app, with the timer as the
 * hero. Top to bottom: what is being recorded, the companion, the elapsed time, a quiet level, the
 * buttons for this kind of recording ([RecordingActions]), what has been marked so far, and one
 * primary control. Nothing animates at idle; the level only moves with the voice.
 */
@Composable
internal fun LiveRecordingSurface(
    type: RecordingType,
    title: String,
    hasPermission: Boolean,
    state: RecordingState,
    amplitude: Float,
    /** Live mic level for the companion, read in the draw phase only (no recomposition per frame). */
    companionLevel: () -> Float = { amplitude },
    durationMs: Long,
    capacityWarning: String?,
    onRequestPermission: () -> Unit,
    onDiscard: () -> Unit,
    onToggle: () -> Unit,
    onFinish: () -> Unit,
    marks: List<Mark> = emptyList(),
    /** A button was used. [text] is what was typed for it, already checked; null for a one-tap marker. */
    onAction: (RecordingAction, String?) -> Unit = { _, _ -> },
    onUndoMark: () -> Unit = {},
    consentReminder: Boolean = false
) {
    val c = MM.colors
    val recording = state == RecordingState.RECORDING
    val haptics = LocalHapticFeedback.current
    // The last few seconds of level, sampled steadily so the line flows at a constant pace.
    val history = remember { mutableStateListOf<Float>().apply { repeat(LEVEL_BARS) { add(0f) } } }
    val latest = rememberUpdatedState(amplitude)
    LaunchedEffect(recording) {
        while (recording) {
            history.removeAt(0); history.add(latest.value.coerceIn(0f, 1f))
            kotlinx.coroutines.delay(90)
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(type, recording, hasPermission, onDiscard)

            if (!hasPermission) {
                PermissionAsk(onRequestPermission)
                return@Column
            }

            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = MM.space.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                RecordingCompanion(type = type, recording = recording, level = companionLevel)
                Text(
                    title, style = MM.type.heading, color = c.inkSecondary, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.m)
                )
                val display = MM.type.display
                Text(
                    Formatters.formatDurationHms(durationMs),
                    style = display.copy(fontSize = display.fontSize * 1.8f, lineHeight = display.lineHeight * 1.8f, fontFeatureSettings = "tnum"),
                    color = c.ink,
                    modifier = Modifier.padding(top = MM.space.xs).testTag("record_timer").semantics { contentDescription = "Recording time ${Formatters.formatDurationHms(durationMs)}" }
                )
                Text(
                    if (recording) "Recording safely on your phone" else "Paused. Tap play to carry on.",
                    style = MM.type.secondary, color = c.inkMuted
                )
                capacityWarning?.let {
                    StatusLine(StatusKind.Warning, it, Modifier.padding(top = MM.space.s).testTag("record_capacity_warning"))
                }
                if (consentReminder) ConsentLine()
                LevelLine(history, recording, Modifier.padding(top = MM.space.l))
            }

            ActionRow(type, onAction, haptics::performHapticFeedback)
            MarkStrip(marks, onUndoMark)
            Controls(recording, onDiscard, onToggle, onFinish, haptics::performHapticFeedback)
        }
    }
}

@Composable
private fun TopBar(type: RecordingType, recording: Boolean, hasPermission: Boolean, onDiscard: () -> Unit) {
    val c = MM.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDiscard, modifier = Modifier.size(MMSize.minTouch).testTag("record_close_btn")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close and discard", tint = c.inkSecondary)
        }
        Surface(shape = MM.radius.small, color = c.surfaceSunk) {
            Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                Spacer(Modifier.width(MM.space.xs))
                Text(type.displayName, style = MM.type.caption, color = c.ink, modifier = Modifier.testTag("record_type_chip"))
            }
        }
        Spacer(Modifier.weight(1f))
        if (hasPermission) {
            Row(Modifier.padding(end = MM.space.m), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(MM.space.s).clip(CircleShape).background(if (recording) c.recording else c.inkFaint))
                Spacer(Modifier.width(MM.space.s))
                Text(if (recording) "Recording" else "Paused", style = MM.type.caption, color = c.inkSecondary)
            }
        }
    }
}

@Composable
private fun PermissionAsk(onRequest: () -> Unit) {
    val c = MM.colors
    Column(Modifier.fillMaxSize().padding(MM.space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(MM.space.xxl * 2).clip(CircleShape).background(c.accentWash), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.icon))
        }
        Text("MeetingMind needs your microphone", style = MM.type.title, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.l))
        Text(
            "Recordings are saved on your phone. In Internet mode they're sent to Google Gemini for processing; the offline pack keeps them here.",
            style = MM.type.secondary, color = c.inkSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.s)
        )
        PrimaryButton("Allow microphone and start", onRequest, Modifier.padding(top = MM.space.xl))
    }
}

/** The last seconds of sound as one quiet line of bars. Flat when paused. */
@Composable
private fun LevelLine(history: List<Float>, recording: Boolean, modifier: Modifier = Modifier) {
    val c = MM.colors
    val live = if (recording) c.accent else c.inkFaint
    Canvas(modifier.fillMaxWidth().height(MM.space.xxl + MM.space.m).semantics { contentDescription = "Live sound level" }) {
        val n = history.size
        val step = size.width / n
        val bar = step * 0.5f
        history.forEachIndexed { i, v ->
            val h = (bar + v * (size.height - bar)).coerceAtMost(size.height)
            val fade = 0.2f + 0.8f * (i.toFloat() / n)
            drawRoundRect(
                color = live.copy(alpha = fade), topLeft = Offset(i * step + (step - bar) / 2f, (size.height - h) / 2f),
                size = Size(bar, h), cornerRadius = CornerRadius(bar / 2f, bar / 2f)
            )
        }
    }
}

/** The buttons for this kind of recording. Each is a 48 dp+ tile; types with typed input open a sheet first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(type: RecordingType, onAction: (RecordingAction, String?) -> Unit, haptic: (HapticFeedbackType) -> Unit) {
    val actions = remember(type) { RecordingActions.forType(type) }
    var asking by remember(type) { mutableStateOf<RecordingAction?>(null) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.s),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        actions.forEach { action ->
            ActionTile(action, Modifier.weight(1f)) {
                haptic(HapticFeedbackType.LongPress)
                if (action.input == ActionInput.NONE) onAction(action, null) else asking = action
            }
        }
    }
    asking?.let { action ->
        MarkerSheet(action, onDismiss = { asking = null }, onSave = { text -> asking = null; haptic(HapticFeedbackType.LongPress); onAction(action, text) })
    }
}

@Composable
private fun ActionTile(action: RecordingAction, modifier: Modifier, onClick: () -> Unit) {
    val c = MM.colors
    Surface(
        onClick = onClick, shape = MM.radius.card, color = c.surface,
        border = if (c.isDark) null else BorderStroke(MMSize.hairline, c.line),
        modifier = modifier.heightIn(min = MMSize.minTouch + MM.space.l).testTag("mark_${action.id}")
    ) {
        Column(Modifier.padding(vertical = MM.space.s, horizontal = MM.space.xs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(iconOf(action.kind), contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(MMSize.icon))
            Text(action.label, style = MM.type.caption, color = c.ink, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = MM.space.xs))
        }
    }
}

/** A short typed line (a note, a prayer point) or a verse reference, checked before it is kept. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerSheet(action: RecordingAction, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = MM.colors
    var text by remember { mutableStateOf("") }
    val saved = RecordingActions.accept(action, text)
    val showProblem = action.input == ActionInput.REFERENCE && text.isNotBlank() && saved == null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(bottom = MM.space.xl).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(MM.space.m)) {
            Text(if (action.input == ActionInput.REFERENCE) "Which verse?" else action.label, style = MM.type.heading, color = c.ink)
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(if (action.input == ActionInput.REFERENCE) "John 3:16" else "Type, or leave it for later") },
                isError = showProblem, singleLine = action.input == ActionInput.REFERENCE,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("marker_input")
            )
            if (showProblem) StatusLine(StatusKind.Warning, "That doesn't look like a verse reference. Try John 3:16 or Psalm 23.")
            PrimaryButton(
                "Add", onClick = { saved?.let(onSave) }, enabled = saved != null,
                leadingIcon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth().testTag("marker_save")
            )
        }
    }
}

/** What has been marked so far, newest last, with Undo for a mis-tap. Renders nothing until something is marked. */
@Composable
private fun MarkStrip(marks: List<Mark>, onUndo: () -> Unit) {
    if (marks.isEmpty()) return
    val c = MM.colors
    Row(Modifier.fillMaxWidth().padding(start = MM.space.l, end = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState(Int.MAX_VALUE)).testTag("mark_strip"),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically
        ) {
            marks.takeLast(12).forEach { m ->
                Surface(shape = MM.radius.small, color = c.surfaceSunk) {
                    Row(Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconOf(m.kind), contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                        Spacer(Modifier.width(MM.space.xs))
                        Text("${m.kind.label} ${Formatters.formatDurationHms(m.atMs)}", style = MM.type.caption, color = c.inkSecondary, maxLines = 1)
                    }
                }
            }
        }
        TextAction("Undo", onUndo, Modifier.testTag("mark_undo"))
    }
}

/** One primary control: pause or resume. Discard and Finish are quiet circles either side. */
@Composable
private fun Controls(recording: Boolean, onDiscard: () -> Unit, onToggle: () -> Unit, onFinish: () -> Unit, haptic: (HapticFeedbackType) -> Unit) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MM.space.xl, vertical = MM.space.l),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        SideControl("Discard", Icons.Rounded.Close, c.inkSecondary, Modifier.testTag("record_discard_btn")) { haptic(HapticFeedbackType.LongPress); onDiscard() }
        IconButton(
            onClick = { haptic(HapticFeedbackType.LongPress); onToggle() },
            modifier = Modifier.size(MM.space.xxl * 2 + MM.space.m).clip(CircleShape).background(c.accent).testTag("record_pause_resume_btn")
        ) {
            Icon(
                if (recording) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (recording) "Pause recording" else "Resume recording",
                tint = c.onAccent, modifier = Modifier.size(MM.space.xxl + MM.space.xs)
            )
        }
        SideControl("Finish", Icons.Rounded.Check, c.success, Modifier.testTag("record_finish_btn")) { haptic(HapticFeedbackType.LongPress); onFinish() }
    }
}

@Composable
private fun SideControl(label: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = modifier.size(MMSize.minTouch + MM.space.s).clip(CircleShape).background(MM.colors.surfaceSunk)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(MMSize.icon))
        }
        Text(label, style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
    }
}

/** A gentle reminder, with a note to share, for recordings with other people (section 6.4). */
@Composable
private fun ConsentLine() {
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(false) }
    if (hidden) return
    Row(Modifier.padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
        Text("Let people know you're recording.", style = MM.type.caption, color = MM.colors.inkSecondary)
        TextAction("Share a note", {
            val text = "I'm taking notes of this conversation with MeetingMind. The recording stays on my phone."
            context.startActivity(android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Share"
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        IconButton(onClick = { hidden = true }, modifier = Modifier.size(MMSize.minTouch)) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = MM.colors.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
        }
    }
}
