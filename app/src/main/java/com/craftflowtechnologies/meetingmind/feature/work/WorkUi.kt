package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.Finding
import com.craftflowtechnologies.meetingmind.core.work.FindingKind
import com.craftflowtechnologies.meetingmind.core.work.PersonKind
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.Success
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Shared pieces for the Work space (docs/PLAN_PROFESSIONAL.md §6–7). Everything reads the theme
 * tokens, so work looks right in Paper and in Graphite.
 */

/** Decisions read in violet and questions in amber everywhere in Work. */
internal val DecisionTint: Color @Composable get() = LocalMMColors.current.speaker2
internal val QuestionTint: Color @Composable get() = LocalMMColors.current.speaker4

@Composable
internal fun WorkSectionTitle(text: String, trailing: String? = null, top: Dp = 26.dp, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = top, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = InkMuted, modifier = Modifier.weight(1f))
        trailing?.let {
            Text(
                it, fontSize = 13.sp, color = if (onTrailing != null) Accent else InkMuted, fontWeight = FontWeight.Medium,
                modifier = if (onTrailing != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTrailing).padding(horizontal = 8.dp, vertical = 4.dp) else Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
internal fun Avatar(initials: String, size: Dp = 36.dp, tint: Color = Accent) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(initials, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
internal fun PersonAvatar(p: WorkPerson, size: Dp = 36.dp) = Avatar(p.initials, size, if (p.kind == PersonKind.ORG) DecisionTint else Accent)

@Composable
internal fun Chip(label: String, selected: Boolean = false, color: Color = Accent, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) color else SurfaceBase,
        border = if (selected) null else BorderStroke(1.dp, Line)
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) (if (color == Accent) OnAccent else OnInk) else Ink, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

/** "Today", "Tomorrow", "Fri", "15 Oct"; overdue reads as such. */
internal fun dueLabel(dueAt: Long?, open: Boolean, now: Long = System.currentTimeMillis()): Pair<String, Boolean>? {
    val at = dueAt ?: return null
    val today = DueDates.startOfDay(now)
    val days = ((DueDates.startOfDay(at) - today) / 86_400_000L).toInt()
    val overdue = open && days < 0
    val label = when {
        overdue && days == -1 -> "Yesterday"
        overdue -> "${-days}d overdue"
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        days in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(at))
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(at))
    }
    return label to overdue
}

internal fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** "Today", "Yesterday", "Monday", "3 Sep". */
internal fun dayLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((DueDates.startOfDay(now) - DueDates.startOfDay(at)) / 86_400_000L).toInt()
    return when (days) {
        0 -> "Today"
        1 -> "Yesterday"
        in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(at))
        else -> SimpleDateFormat(
            if (Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "d MMM" else "d MMM yyyy",
            Locale.getDefault()
        ).format(Date(at))
    }
}

@Composable
internal fun PlayChip(ms: Long, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = AccentWash) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(14.dp))
            Text(formatTime(ms), fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun CheckCircle(done: Boolean, overdue: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .then(if (done) Modifier.background(Success) else Modifier.border(1.8.dp, if (overdue) Danger else Accent, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { if (done) Icon(Icons.Filled.Check, "Mark not done", tint = OnInk, modifier = Modifier.size(15.dp)) }
}

/** One work task: tick, words, when, who, where it came from. */
@Composable
internal fun TaskLine(t: WorkTask, context: String?, onToggle: () -> Unit, onClick: () -> Unit, onPlay: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    val due = dueLabel(t.dueAt, !t.done)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(t.done, due?.second == true, onToggle)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(t.title, fontSize = 15.sp, color = if (t.done) InkMuted else Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
                textDecoration = if (t.done) TextDecoration.LineThrough else null)
            val bits = listOfNotNull(t.ownerName?.takeIf { t.waitingOn }, context)
            if (due != null || bits.isNotEmpty()) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                due?.let { (label, overdue) ->
                    Text(label, fontSize = 12.sp, color = if (overdue) Danger else InkSecondary, fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal)
                    if (bits.isNotEmpty()) Text("  ·  ", fontSize = 12.sp, color = InkMuted)
                }
                Text(bits.joinToString("  ·  "), fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onPlay != null && t.startMs != null) PlayChip(t.startMs, onPlay)
        action?.invoke()
    }
}

/** A decision or question from a recording, with the recording and the moment. */
@Composable
internal fun FindingLine(kind: FindingKind, text: String, context: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (kind == FindingKind.QUESTION) Icons.Outlined.HelpOutline else Icons.Outlined.Gavel, null,
            tint = if (kind == FindingKind.QUESTION) QuestionTint else DecisionTint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(text, fontSize = 15.sp, color = Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
            context?.let { Text(it, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun PersonRow(person: WorkPerson, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(person)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(person.name, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(person.role, subtitle, person.lastSeenAt?.let { "Last ${dayLabel(it).lowercase()}" }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (person.confidential) Text("🔒", fontSize = 12.sp)
    }
}

/** An empty section says what will appear there, rather than nothing. */
@Composable
internal fun EmptyLine(text: String) {
    Text(text, fontSize = 14.sp, color = InkSecondary, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
}

/** Who a finding can belong to: you, a speaker in the recording, or someone typed. */
data class OwnerChoice(val label: String, val speakerId: String? = null, val name: String? = null, val isSelf: Boolean = false)

internal fun ownerChoices(speakers: List<Speaker>, selfName: String?): List<OwnerChoice> =
    listOf(OwnerChoice("Me", isSelf = true, name = selfName)) +
        speakers.map { OwnerChoice(it.customName.ifBlank { it.originalLabel }, speakerId = it.id) }

/**
 * Edits one finding in the Wrap-up: its words, what it is, who owns it, when it's due. Everything
 * optional, and one tap (PLAN_PROFESSIONAL.md §1).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FindingEditSheet(
    f: Finding,
    owners: List<OwnerChoice>,
    onDismiss: () -> Unit,
    onText: (String) -> Unit,
    onKind: (FindingKind) -> Unit,
    onOwner: (OwnerChoice?) -> Unit,
    onDue: (String?) -> Unit,
    onAnswer: (String) -> Unit,
    onDelete: () -> Unit,
    onPlay: (() -> Unit)?
) {
    var text by remember(f.id) { mutableStateOf(f.text) }
    var typedOwner by remember(f.id) { mutableStateOf("") }
    var answer by remember(f.id) { mutableStateOf(f.answer.orEmpty()) }
    ModalBottomSheet(onDismissRequest = { if (text != f.text) onText(text); onDismiss() }, containerColor = SurfaceBase) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kindLabel(f.kind), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                if (onPlay != null && f.startMs != null) TextButton(onClick = onPlay) { Text("▶ " + formatTime(f.startMs)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove", tint = InkMuted) }
            }
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Label("What it is")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FindingKind.entries.forEach { k -> Chip(kindLabel(k), f.kind == k, Ink) { if (text != f.text) onText(text); onKind(k) } }
            }
            if (f.kind == FindingKind.ACTION || f.kind == FindingKind.FOLLOW_UP) {
                Label("Who")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    owners.forEach { o ->
                        val selected = (o.speakerId != null && o.speakerId == f.ownerSpeakerId) || (o.isSelf && f.ownerIsSelf)
                        Chip(o.label, selected) { onOwner(o) }
                    }
                    Chip("Nobody yet", !f.hasOwner) { onOwner(null) }
                }
                OutlinedTextField(
                    typedOwner, { typedOwner = it }, placeholder = { Text("Someone else — type a name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    trailingIcon = { if (typedOwner.isNotBlank()) TextButton(onClick = { onOwner(OwnerChoice(typedOwner.trim(), name = typedOwner.trim())); typedOwner = "" }) { Text("Set") } }
                )
                Label("When")
                val now = System.currentTimeMillis()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Today" to "today", "Tomorrow" to "tomorrow", "Friday" to "Friday", "Next week" to "next week").forEach { (label, words) ->
                        val at = DueDates.parse(words, now)
                        Chip(label, at != null && at == f.dueAt) { onDue(words) }
                    }
                    Chip("No date", f.dueAt == null && f.dueText == null) { onDue(null) }
                }
                f.dueText?.takeIf { it.isNotBlank() }?.let { Text("Said: “$it”", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (f.kind == FindingKind.QUESTION) {
                OutlinedTextField(
                    answer, { answer = it }, label = { Text("Answer, if you have it") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    trailingIcon = { if (answer.isNotBlank() && answer != f.answer) TextButton(onClick = { onAnswer(answer) }) { Text("Save") } }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

internal fun kindLabel(k: FindingKind) = when (k) {
    FindingKind.ACTION -> "Task"
    FindingKind.FOLLOW_UP -> "Follow-up"
    FindingKind.DECISION -> "Decision"
    FindingKind.QUESTION -> "Question"
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
internal fun Panel(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
internal fun Dot(color: Color, size: Dp = 6.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
internal fun Gap(w: Dp) = Spacer(Modifier.width(w))
