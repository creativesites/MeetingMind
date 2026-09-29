package com.example.feature.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Gavel
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Speaker
import com.example.core.work.DueDates
import com.example.core.work.ItemKind
import com.example.core.work.ItemStatus
import com.example.core.work.Person
import com.example.core.work.WorkItem
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val Overdue = Color(0xFFDC2626)
internal val DecisionColor = Color(0xFF7C3AED)
internal val QuestionColor = Color(0xFFD97706)
internal val DoneColor = Color(0xFF059669)

@Composable
internal fun WorkSectionTitle(text: String, trailing: String? = null, top: Dp = 18.dp, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = InkMuted, modifier = Modifier.weight(1f))
        trailing?.let {
            Text(
                it, fontSize = 13.sp, color = if (onTrailing != null) Accent else InkMuted, fontWeight = FontWeight.Medium,
                modifier = if (onTrailing != null) Modifier.clickable(onClick = onTrailing).padding(4.dp) else Modifier
            )
        }
    }
}

@Composable
internal fun Avatar(initials: String, size: Dp = 36.dp, tint: Color = Accent) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Text(initials, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

/** "Today", "Tomorrow", "Fri", "15 Oct"; overdue reads as such. */
internal fun dueLabel(item: WorkItem, now: Long = System.currentTimeMillis()): Pair<String, Boolean>? {
    val at = item.dueAt ?: return item.dueText?.let { it to false }
    val today = DueDates.startOfDay(now)
    val days = ((at - today) / (24L * 60 * 60 * 1000)).toInt()
    val overdue = item.isOpen && at < today
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

internal fun kindColor(kind: ItemKind) = when (kind) {
    ItemKind.TASK -> Accent
    ItemKind.DECISION -> DecisionColor
    ItemKind.QUESTION -> QuestionColor
}

/**
 * One item. Tasks have a check circle; decisions and questions a marker. The owner is shown by
 * their current name (names are dynamic, PLAN_PROFESSIONAL.md §5.5).
 */
@Composable
internal fun ItemRow(
    item: WorkItem,
    onToggle: (() -> Unit)?,
    onClick: () -> Unit,
    context: String? = null,
    onPlay: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val done = item.status == ItemStatus.DONE || item.status == ItemStatus.ANSWERED || item.status == ItemStatus.DROPPED
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (item.kind) {
            ItemKind.TASK -> if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle
            ItemKind.DECISION -> Icons.Outlined.Gavel
            ItemKind.QUESTION -> Icons.Outlined.HelpOutline
        }
        IconButton(onClick = { onToggle?.invoke() }, enabled = onToggle != null) {
            Icon(icon, contentDescription = if (item.kind == ItemKind.TASK) (if (done) "Mark not done" else "Mark done") else null,
                tint = if (done) DoneColor else kindColor(item.kind), modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(
                item.text, fontSize = 15.sp, color = if (done) InkMuted else Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done && item.kind == ItemKind.TASK) TextDecoration.LineThrough else null
            )
            val due = dueLabel(item)
            val bits = listOfNotNull(
                item.ownerName?.let { if (item.ownerIsSelf) "You" else it }?.takeIf { item.kind != ItemKind.DECISION },
                context
            )
            if (bits.isNotEmpty() || due != null || !item.reviewed || item.answer != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (!item.reviewed) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
                        Spacer(Modifier.width(6.dp))
                    }
                    due?.let { (label, overdue) ->
                        Text(label, fontSize = 12.sp, color = if (overdue) Overdue else InkSecondary, fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal)
                        if (bits.isNotEmpty()) Text("  ·  ", fontSize = 12.sp, color = InkMuted)
                    }
                    Text(bits.joinToString("  ·  "), fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item.answer?.let { Text("→ $it", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (onPlay != null && item.sourceStartMs != null) {
            Surface(onClick = onPlay, shape = RoundedCornerShape(50), color = AccentWash) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(14.dp))
                    Text(formatTime(item.sourceStartMs), fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium)
                }
            }
        }
        action?.invoke()
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean = false, color: Color = Accent, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) color else Color.White,
        border = if (selected) null else BorderStroke(1.dp, Line)
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) Color.White else Ink, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

/** Who an item can belong to: you, a speaker in this recording, or someone you know. */
data class OwnerChoice(val label: String, val speakerId: String? = null, val personId: String? = null, val name: String? = null, val isSelf: Boolean = false)

internal fun ownerChoices(self: Person?, speakers: List<Speaker>, people: List<Person>): List<OwnerChoice> {
    val out = mutableListOf<OwnerChoice>()
    self?.let { out += OwnerChoice("Me", personId = it.id, isSelf = true) }
    speakers.forEach { s -> out += OwnerChoice(s.customName.ifBlank { s.originalLabel }, speakerId = s.id) }
    people.filter { !it.isSelf && out.none { o -> o.label.equals(it.name, true) } }.take(8).forEach { out += OwnerChoice(it.name, personId = it.id) }
    return out
}

/**
 * Edits one item: its words, what kind it is, who owns it, when it's due. Everything is optional
 * and one tap (PLAN_PROFESSIONAL.md §1, no required fields).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ItemEditSheet(
    item: WorkItem,
    owners: List<OwnerChoice>,
    onDismiss: () -> Unit,
    onText: (String) -> Unit,
    onKind: (ItemKind) -> Unit,
    onOwner: (OwnerChoice?) -> Unit,
    onDue: (Long?, String?) -> Unit,
    onAnswer: ((String) -> Unit)? = null,
    onDelete: () -> Unit,
    onPlay: (() -> Unit)? = null
) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    var typedOwner by remember(item.id) { mutableStateOf("") }
    var answer by remember(item.id) { mutableStateOf(item.answer.orEmpty()) }
    ModalBottomSheet(onDismissRequest = { if (text != item.text) onText(text); onDismiss() }, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (item.kind) { ItemKind.TASK -> "Task"; ItemKind.DECISION -> "Decision"; ItemKind.QUESTION -> "Question" },
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                if (onPlay != null && item.sourceStartMs != null) TextButton(onClick = onPlay) { Text("▶ " + formatTime(item.sourceStartMs)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove", tint = InkMuted) }
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Text("KIND", fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemKind.entries.forEach { k ->
                    Chip(when (k) { ItemKind.TASK -> "Task"; ItemKind.DECISION -> "Decision"; ItemKind.QUESTION -> "Question" }, item.kind == k, kindColor(k)) {
                        if (text != item.text) onText(text); onKind(k)
                    }
                }
            }
            if (item.kind != ItemKind.DECISION) {
                Text(if (item.kind == ItemKind.QUESTION) "ASKED BY" else "WHO", fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    owners.forEach { o ->
                        val selected = (o.speakerId != null && o.speakerId == item.ownerSpeakerId) ||
                            (o.personId != null && o.personId == item.ownerPersonId) || (o.isSelf && item.ownerIsSelf)
                        Chip(o.label, selected) { onOwner(o) }
                    }
                    Chip("Nobody yet", item.ownerName == null && item.ownerSpeakerId == null && item.ownerPersonId == null) { onOwner(null) }
                }
                OutlinedTextField(
                    value = typedOwner, onValueChange = { typedOwner = it }, placeholder = { Text("Someone else — type a name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    trailingIcon = { if (typedOwner.isNotBlank()) TextButton(onClick = { onOwner(OwnerChoice(typedOwner.trim(), name = typedOwner.trim())); typedOwner = "" }) { Text("Set") } }
                )
            }
            if (item.kind == ItemKind.TASK) {
                Text("WHEN", fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                val now = System.currentTimeMillis()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Today" to "today", "Tomorrow" to "tomorrow", "Friday" to "Friday", "Next week" to "next week").forEach { (label, words) ->
                        val at = DueDates.parse(words, now)
                        Chip(label, at != null && at == item.dueAt) { onDue(at, words) }
                    }
                    Chip("No date", item.dueAt == null && item.dueText == null) { onDue(null, null) }
                }
                item.dueText?.let { Text("Said: “$it”", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (item.kind == ItemKind.QUESTION && onAnswer != null) {
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it }, label = { Text("Answer") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    trailingIcon = { if (answer.isNotBlank() && answer != item.answer) TextButton(onClick = { onAnswer(answer) }) { Text("Save") } }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** An empty list says what will appear there, rather than nothing. */
@Composable
internal fun EmptyLine(text: String) {
    Text(text, fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
internal fun Panel(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/** Relative day for a list: "Today", "Yesterday", "Mon 28 Sep". */
internal fun dayLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    val d = DueDates.startOfDay(at); val t = DueDates.startOfDay(now)
    val days = ((t - d) / (24L * 60 * 60 * 1000)).toInt()
    return when (days) {
        0 -> "Today"
        1 -> "Yesterday"
        in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(at))
        else -> SimpleDateFormat(if (Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "d MMM" else "d MMM yyyy", Locale.getDefault()).format(Date(at))
    }
}
