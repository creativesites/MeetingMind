package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskList
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepeat
import com.craftflowtechnologies.meetingmind.core.ui.work.SwipeActionRow
import com.craftflowtechnologies.meetingmind.core.ui.work.TaskCheck
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What a task row needs to know beyond the task itself: every task (for subtasks), the lists, each
 * task's label names, people's names by id, and today.
 */
class TaskCtx(
    val all: List<Task>,
    val lists: List<TaskList>,
    val labels: Map<String, List<String>>,
    val people: Map<String, String>,
    val today: LocalDate,
    val zone: ZoneId
) {
    val listById: Map<String, TaskList> = lists.associateBy { it.id }
    private val children: Map<String, List<Task>> = all.filter { it.parentId != null }.groupBy { it.parentId.orEmpty() }

    fun childrenOf(taskId: String): List<Task> = children[taskId].orEmpty()

    fun taskById(id: String): Task? = all.firstOrNull { it.id == id }
}

/** Builds [TaskCtx] from the ViewModel's flows. Rebuilt when any of them change. */
@Composable
fun rememberTaskCtx(vm: TasksViewModel): TaskCtx {
    val all by vm.tasks.collectAsState()
    val lists by vm.lists.collectAsState()
    val labels by vm.labelsByTask.collectAsState()
    val people by vm.people.collectAsState()
    val today = vm.today()
    val zone = remember { ZoneId.systemDefault() }
    return remember(all, lists, labels, people, today) {
        TaskCtx(
            all = all,
            lists = lists,
            labels = labels,
            people = people.associate { it.id to it.name },
            today = today,
            zone = zone
        )
    }
}

/** Start of [day] in epoch milliseconds, in [zone]. */
internal fun startOfDayMs(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

internal fun localTimeOf(ms: Long, zone: ZoneId): LocalTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()

/** A timestamp as m:ss, for a source recording. */
internal fun formatTimestamp(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * A task row. Swipe right completes, left opens the date sheet, tap opens the task, long-press (or
 * tap while selecting) selects. Overdue dates show in the warning colour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: Task,
    ctx: TaskCtx,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onSchedule: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val list = task.listId?.let { ctx.listById[it] }
    val subtasks = ctx.childrenOf(task.id)
    val overdue = !task.done && TaskViews.dayOf(task, ctx.zone)?.isBefore(ctx.today) == true
    val due = TaskViews.dueLabel(task, ctx.today, ctx.zone)
    val labels = ctx.labels[task.id].orEmpty()
    val promisedTo = if (task.sourceItemId != null) task.personId?.let { ctx.people[it] }?.substringBefore(' ') else null

    SwipeActionRow(
        onSwipeRight = { if (!selecting) onComplete() },
        onSwipeLeft = { if (!selecting) onSchedule() },
        modifier = modifier,
        completeLabel = if (task.done) "Reopen" else "Complete",
        scheduleLabel = "Date"
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) colors.accentWash else colors.surface)
                .combinedClickable(
                    onClick = { if (selecting) onToggleSelect() else onOpen() },
                    onLongClick = onToggleSelect
                )
                .heightIn(min = MMSize.minTouch)
                .padding(horizontal = MM.space.m, vertical = MM.space.s),
            verticalAlignment = Alignment.Top
        ) {
            if (selecting) {
                SelectMark(selected)
            } else {
                TaskCheck(checked = task.done, priority = task.priority, onToggle = { onComplete() })
            }
            Column(Modifier.weight(1f).padding(start = MM.space.xs)) {
                Text(
                    text = task.title,
                    style = MM.type.bodyStrong,
                    color = if (task.done) colors.inkMuted else colors.ink,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    Modifier.padding(top = MM.space.xs),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (due != null) MetaText(due, if (overdue) colors.warning else colors.inkSecondary)
                    if (task.repeatRule != null || task.repeat != TaskRepeat.NONE) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Repeats", tint = colors.inkMuted, modifier = Modifier.size(14.dp))
                    }
                    if (subtasks.isNotEmpty()) {
                        MetaText("${subtasks.count { it.done }}/${subtasks.size}", colors.inkMuted)
                    }
                    if (promisedTo != null) MetaText("for $promisedTo", colors.accent)
                    labels.take(1).forEach { MetaText("@$it", colors.inkMuted) }
                    if (list != null) {
                        Box(Modifier.size(8.dp).background(projectColor(list.id), MM.radius.small))
                        MetaText(list.name, colors.inkSecondary)
                    }
                }
            }
        }
    }
}

/** A list of task rows with a hairline between them. Non-lazy: the pages scroll as a whole. */
@Composable
fun TaskRows(
    tasks: List<Task>,
    ctx: TaskCtx,
    selection: Set<String>,
    onOpen: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onSchedule: (Task) -> Unit,
    onToggleSelect: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val selecting = selection.isNotEmpty()
    Column(modifier.fillMaxWidth()) {
        tasks.forEachIndexed { i, task ->
            TaskRow(
                task = task,
                ctx = ctx,
                selecting = selecting,
                selected = task.id in selection,
                onOpen = { onOpen(task) },
                onComplete = { onComplete(task) },
                onSchedule = { onSchedule(task) },
                onToggleSelect = { onToggleSelect(task) }
            )
            if (i < tasks.lastIndex) HairLine()
        }
    }
}

@Composable
internal fun HairLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MM.colors.lineSoft))
}

@Composable
private fun SelectMark(selected: Boolean) {
    val colors = MM.colors
    Box(
        Modifier
            .padding(top = MM.space.xs)
            .size(22.dp)
            .background(if (selected) colors.accent else Color.Transparent, MM.radius.pill)
            .border(1.5.dp, if (selected) colors.accent else colors.inkFaint, MM.radius.pill),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.onAccent, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MetaText(text: String, color: Color) {
    Text(text = text, style = MM.type.caption, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The back arrow in a page header. */
@Composable
internal fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MM.colors.ink)
    }
}

/** An icon action in a page header. */
@Composable
internal fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = MM.colors.inkSecondary)
    }
}

/** The title row of a page: an optional back arrow, the title and subtitle, and actions on the right. */
@Composable
internal fun PageHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = MM.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).padding(horizontal = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            BackButton(onBack)
        }
        Column(Modifier.weight(1f).padding(horizontal = MM.space.xs)) {
            Text(title, style = MM.type.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MM.type.caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A quiet section label with a count, for groups inside a page. */
@Composable
internal fun GroupLabel(title: String, count: Int?) {
    Row(
        Modifier.fillMaxWidth().padding(top = MM.space.l, bottom = MM.space.xs, start = MM.space.m, end = MM.space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MM.type.overline, color = MM.colors.inkMuted, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), style = MM.type.caption, color = MM.colors.inkMuted)
    }
}
