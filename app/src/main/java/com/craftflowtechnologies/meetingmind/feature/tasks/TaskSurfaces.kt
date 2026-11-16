package com.craftflowtechnologies.meetingmind.feature.tasks

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.tasks.Person
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskBucket
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepeat
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRules
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.Success
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* Surfaces other features still use: the editor (Work, Notes), the due-date text, and the home peek card. */

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) OnInk else InkSecondary,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Ink else SurfaceSunk).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

fun describeDue(ms: Long, today: LocalDate = LocalDate.now()): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DAY)
    }
}

@Composable
private fun kindColor(kind: TaskKind): Color = when (kind) {
    TaskKind.TASK -> Accent
    TaskKind.APPLY -> FaithGold
    TaskKind.PRAYER -> Danger
    TaskKind.FOLLOW_UP -> Success
}

@Composable
private fun LegacyTaskRow(t: Task, person: Person?, onToggle: () -> Unit, onClick: () -> Unit) {
    val ring by animateColorAsState(if (t.done) Success else kindColor(t.kind), label = "ring")
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp).testTag("task_${t.title}"), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(if (t.done) Success else Color.Transparent)
                .border(1.8.dp, ring, CircleShape).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) { if (t.done) Icon(Icons.Filled.Check, contentDescription = "Done", tint = OnInk, modifier = Modifier.size(14.dp)) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                t.title, fontSize = 15.5.sp, color = if (t.done) InkMuted else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (t.done) TextDecoration.LineThrough else null
            )
            val meta = listOfNotNull(
                t.kind.label.takeIf { t.kind != TaskKind.TASK },
                t.dueAt?.let { describeDue(it) },
                person?.name,
                t.scripture
            )
            if (meta.isNotEmpty() || t.remindAt != null || t.repeat != TaskRepeat.NONE) {
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(meta.joinToString("  ·  "), fontSize = 12.5.sp, color = InkSecondary)
                    if (t.remindAt != null && !t.done) Icon(Icons.Filled.NotificationsNone, "Reminder", tint = InkMuted, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                    if (t.repeat != TaskRepeat.NONE) Icon(Icons.Filled.Repeat, t.repeat.label, tint = InkMuted, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
            }
        }
    }
}

/** Editing one task: title, kind, date, reminder, repeat, person, notes. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    task: Task,
    people: List<Person>,
    onSave: (Task, String?) -> Unit,
    onDelete: (() -> Unit)?,
    onOpenSource: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes) }
    var kind by remember { mutableStateOf(task.kind) }
    var due by remember { mutableStateOf(task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }) }
    var remind by remember { mutableStateOf(task.remindAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }) }
    var repeat by remember { mutableStateOf(task.repeat) }
    var personId by remember { mutableStateOf(task.personId) }
    var newPerson by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SurfaceBase) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                title, { title = it }, placeholder = { Text("What needs doing?") }, modifier = Modifier.fillMaxWidth().testTag("task_title"),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = Ink)
            )
            Section("Kind") { TaskKind.entries.forEach { k -> Chip(k.label, kind == k) { kind = k } } }
            Section("When") {
                Chip("No date", due == null) { due = null; remind = null }
                Chip("Today", due == today) { due = today }
                Chip("Tomorrow", due == today.plusDays(1)) { due = today.plusDays(1) }
                Chip("Next week", due == today.plusWeeks(1)) { due = today.plusWeeks(1) }
                val custom = due?.takeIf { it != today && it != today.plusDays(1) && it != today.plusWeeks(1) }
                Chip(custom?.format(DAY) ?: "Pick a date…", custom != null) { pickDate = true }
            }
            if (due != null) Section("Remind me") {
                Chip("No reminder", remind == null) { remind = null }
                listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0)).forEach { t -> Chip(t.format(TIME), remind == t) { remind = t } }
                val custom = remind?.takeIf { it !in listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(18, 0)) }
                Chip(custom?.format(TIME) ?: "Other time…", custom != null) { pickTime = true }
            }
            if (due != null) Section("Repeat") { TaskRepeat.entries.forEach { r -> Chip(r.label, repeat == r) { repeat = r } } }
            Section("Person") {
                Chip("No one", personId == null && newPerson.isBlank()) { personId = null; newPerson = "" }
                people.forEach { p -> Chip(p.name, personId == p.id) { personId = p.id; newPerson = "" } }
            }
            OutlinedTextField(
                newPerson, { newPerson = it; if (it.isNotBlank()) personId = null },
                placeholder = { Text("Someone new") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Person, null, tint = InkMuted) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(notes, { notes = it }, placeholder = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (onOpenSource != null) {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpenSource).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Text(if (task.meetingId != null) "Open where it was said" else "Open the note it came from", color = Accent, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = Danger) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel", color = InkSecondary) }
                Text(
                    "Save", color = OnInk, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (title.isBlank()) InkFaint else Ink)
                        .clickable(enabled = title.isNotBlank()) {
                            val dueMs = due?.atTime(remind ?: LocalTime.of(9, 0))?.atZone(zone)?.toInstant()?.toEpochMilli()
                            val remindMs = if (remind != null) dueMs else null
                            onSave(
                                task.copy(title = title, notes = notes, kind = kind, dueAt = dueMs, remindAt = remindMs, repeat = if (due == null) TaskRepeat.NONE else repeat, personId = personId),
                                newPerson.takeIf { it.isNotBlank() }
                            )
                        }.padding(horizontal = 22.dp, vertical = 11.dp).testTag("task_save")
                )
            }
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (due ?: today).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }; pickDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(remind?.hour ?: 9, remind?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false }, containerColor = SurfaceBase,
            confirmButton = { TextButton(onClick = { remind = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
/** A few open tasks, for a home screen: the next ones due, with a tick and "See all". */
@Composable
fun TasksPeekCard(title: String, kinds: Set<TaskKind>?, onOpenAll: () -> Unit, modifier: Modifier = Modifier, horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp) {
    val vm: TasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val tasks by vm.tasks.collectAsState()
    val people by vm.people.collectAsState()
    val open = tasks.filter { !it.done && (kinds == null || it.kind in kinds) }.take(3)
    if (open.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = horizontalPadding).clip(RoundedCornerShape(18.dp)).background(SurfaceRaised).border(1.dp, com.craftflowtechnologies.meetingmind.ui.theme.LineSoft, RoundedCornerShape(18.dp)).padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAll) { Text("See all", color = Accent, fontSize = 13.sp) }
        }
        open.forEach { t -> LegacyTaskRow(t, people.firstOrNull { it.id == t.personId }, onToggle = { vm.toggle(t) }, onClick = onOpenAll) }
    }
}
