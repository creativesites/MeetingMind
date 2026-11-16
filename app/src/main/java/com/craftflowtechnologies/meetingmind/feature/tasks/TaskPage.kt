package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepeat
import com.craftflowtechnologies.meetingmind.core.tasks.TaskSection
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.work.SourceChip
import com.craftflowtechnologies.meetingmind.core.ui.work.TaskCheck
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * One task: complete, title, list and section, the promise and its source, dates and repeat, priority,
 * labels, "someone else is doing this", subtasks, notes and attachments. Title and notes save when
 * the field loses focus.
 */
@Composable
fun TaskPage(
    vm: TasksViewModel,
    ctx: TaskCtx,
    taskId: String,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onSheet: (TaskSheet) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenRecording: (String, Long?) -> Unit
) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    val task = ctx.taskById(taskId)
    if (task == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = MM.space.l)) {
            PageHeader(title = "Task", subtitle = null, onBack = onBack)
            EmptyState(title = "This task is gone", body = "It was deleted, or it is in the Trash.")
        }
        return
    }

    val sectionFlow = remember(task.listId) { task.listId?.let { vm.sectionsOf(it) } ?: emptyFlow<List<TaskSection>>() }
    val sections by sectionFlow.collectAsState(initial = emptyList())
    val zone = ctx.zone
    val list = task.listId?.let { ctx.listById[it] }
    val sectionName = task.sectionId?.let { id -> sections.firstOrNull { it.id == id }?.name }
    val subtasks = ctx.childrenOf(task.id)
    val who = task.personId?.let { ctx.people[it] }
    val labels = ctx.labels[task.id].orEmpty()

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = "Task",
            subtitle = list?.name,
            onBack = onBack,
            actions = {
                HeaderAction(Icons.Filled.DeleteOutline, "Delete task") {
                    scope.launch {
                        vm.deleteTasks(listOf(task.id))
                        onBack()
                    }
                }
            }
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TaskCheck(checked = task.done, priority = task.priority, onToggle = { scope.launch { vm.toggleDone(task) } })
                Spacer(Modifier.width(MM.space.xs))
                CommitField(
                    value = task.title,
                    style = MM.type.title,
                    color = if (task.done) colors.inkMuted else colors.ink,
                    placeholder = "Task",
                    singleLine = false,
                    modifier = Modifier.weight(1f),
                    onCommit = { text -> scope.launch { vm.setTitle(task.id, text) } }
                )
            }
            Spacer(Modifier.height(MM.space.s))
            MMChip(
                label = (list?.name ?: "Inbox") + (sectionName?.let { " · $it" } ?: ""),
                selected = false,
                onClick = { onSheet(TaskSheet.Move(listOf(task.id))) }
            )
            Spacer(Modifier.height(MM.space.m))

            if (task.sourceItemId != null || task.meetingId != null || task.noteId != null) {
                val openSource: () -> Unit = {
                    if (task.meetingId != null) {
                        onOpenRecording(task.meetingId, task.startMs)
                    } else if (task.noteId != null) {
                        onOpenNote(task.noteId)
                    }
                }
                HeroCard(Modifier.fillMaxWidth(), contentPadding = MM.space.m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (who != null) "Promised to $who" else "From a conversation",
                                style = MM.type.bodyStrong,
                                color = colors.ink
                            )
                            Text(
                                if (task.sourceItemId != null) "Completing this closes the promise" else "Where it came up",
                                style = MM.type.caption,
                                color = colors.inkSecondary
                            )
                        }
                        if (task.meetingId != null || task.noteId != null) {
                            SourceChip(label = task.startMs?.let { formatTimestamp(it) } ?: "Source", onClick = openSource)
                        }
                    }
                }
                Spacer(Modifier.height(MM.space.m))
            }

            FieldRow(
                icon = Icons.Rounded.DateRange,
                value = TaskViews.dueLabel(task, ctx.today, zone),
                placeholder = "No date",
                tint = colors.inkSecondary,
                onClick = { onSheet(TaskSheet.Date(listOf(task.id))) }
            )
            FieldRow(
                icon = Icons.Outlined.Notifications,
                value = task.remindAt?.let { "Reminder at ${TaskViews.timeOf(it, zone)}" },
                placeholder = "No reminder",
                tint = colors.inkSecondary,
                onClick = { onSheet(TaskSheet.Date(listOf(task.id))) }
            )
            FieldRow(
                icon = Icons.Filled.Refresh,
                value = TaskViews.repeatLabel(task.repeatRule) ?: if (task.repeat != TaskRepeat.NONE) task.repeat.label else null,
                placeholder = "Doesn't repeat",
                tint = colors.inkSecondary,
                onClick = { onSheet(TaskSheet.Date(listOf(task.id))) }
            )
            FieldRow(
                icon = Icons.Rounded.Flag,
                value = "Priority ${task.priority}",
                placeholder = "Priority",
                tint = priorityTint(task.priority),
                onClick = { onSheet(TaskSheet.Priority(listOf(task.id))) }
            )
            Row(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tag, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(MM.space.m))
                CommitField(
                    value = labels.joinToString(", "),
                    style = MM.type.body,
                    color = colors.ink,
                    placeholder = "Labels, e.g. email, calls",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    onCommit = { text ->
                        scope.launch { vm.setLabels(task.id, text.split(',')) }
                    }
                )
            }
            if (task.waitingOn) {
                FieldRow(
                    icon = Icons.Rounded.Person,
                    value = "Waiting on ${who ?: "someone"}. Tap to take it back",
                    placeholder = "",
                    tint = colors.warning,
                    onClick = { scope.launch { vm.setWaitingOn(listOf(task.id), null) } }
                )
            } else {
                FieldRow(
                    icon = Icons.Rounded.Person,
                    value = "Someone else is doing this",
                    placeholder = "Someone else is doing this",
                    tint = colors.inkSecondary,
                    onClick = { onSheet(TaskSheet.Assign(listOf(task.id))) }
                )
            }

            GroupLabel(title = "Subtasks", count = subtasks.size.takeIf { it > 0 })
            subtasks.forEach { sub ->
                Row(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
                    TaskCheck(checked = sub.done, priority = sub.priority, onToggle = { scope.launch { vm.toggleDone(sub) } })
                    Text(
                        sub.title,
                        style = MM.type.body,
                        color = if (sub.done) colors.inkMuted else colors.ink,
                        modifier = Modifier.weight(1f).clickable { onOpenTask(sub.id) }
                    )
                }
            }
            AddSubtaskField(onAdd = { title -> scope.launch { vm.addSubtask(task.id, title) } })

            GroupLabel(title = "Notes", count = null)
            CommitField(
                value = task.notes,
                style = MM.type.body,
                color = colors.ink,
                placeholder = "Add notes, links or context",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().padding(bottom = MM.space.m),
                onCommit = { text -> scope.launch { vm.setNotes(task.id, text) } }
            )

            if (task.noteId != null || task.meetingId != null) {
                GroupLabel(title = "Attached", count = null)
                task.noteId?.let { note ->
                    FieldRow(Icons.Rounded.Notes, "Note", "Note", colors.inkSecondary) { onOpenNote(note) }
                }
                task.meetingId?.let { meeting ->
                    FieldRow(Icons.Filled.Mic, "Recording", "Recording", colors.inkSecondary) { onOpenRecording(meeting, task.startMs) }
                }
            }
            Spacer(Modifier.height(MM.space.xl))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = MM.space.l, vertical = MM.space.s),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            SecondaryButton(
                text = "Reschedule",
                onClick = { onSheet(TaskSheet.Date(listOf(task.id))) },
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = if (task.done) "Mark not done" else "Complete",
                onClick = { scope.launch { vm.toggleDone(task) } },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A labelled row that opens a sheet or runs [onClick]. */
@Composable
internal fun FieldRow(
    icon: ImageVector,
    value: String?,
    placeholder: String,
    tint: Color,
    onClick: () -> Unit
) {
    val colors = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(MM.space.m))
        Text(
            text = if (value.isNullOrBlank()) placeholder else value,
            style = MM.type.body,
            color = if (value.isNullOrBlank()) colors.inkMuted else colors.ink,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Text that saves when its field loses focus. Resets when the stored value changes. */
@Composable
internal fun CommitField(
    value: String,
    style: TextStyle,
    color: Color,
    placeholder: String,
    singleLine: Boolean,
    modifier: Modifier,
    onCommit: (String) -> Unit
) {
    val colors = MM.colors
    var text by remember(value) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(colors.accent),
        singleLine = singleLine,
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused && text != value) onCommit(text)
        },
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text(placeholder, style = style, color = colors.inkMuted)
                inner()
            }
        }
    )
}

@Composable
private fun AddSubtaskField(onAdd: (String) -> Unit) {
    val colors = MM.colors
    var title by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(MM.space.xl))
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            textStyle = MM.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (title.isEmpty()) Text("Add subtask", style = MM.type.body, color = colors.inkMuted)
                    inner()
                }
            }
        )
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add subtask",
            tint = colors.accent,
            modifier = Modifier.size(22.dp).clickable {
                if (title.isNotBlank()) {
                    onAdd(title)
                    title = ""
                }
            }
        )
    }
}

internal fun priorityTint(priority: Int): Color = when (priority) {
    1 -> MM.colors.danger
    2 -> MM.colors.warning
    3 -> MM.colors.accent
    else -> MM.colors.inkFaint
}
