package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.work.ProgressRing
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch

/** Which tasks a surface shows: every task (the Tasks app) or only the Work space's (Work's Tasks). */
enum class TaskScope { ALL, WORK }

private enum class PaneChip(val label: String) {
    TODAY("Today"),
    UPCOMING("Upcoming"),
    WAITING("Waiting on"),
    PROJECTS("By project")
}

/**
 * The Tasks view for Work: Today, Upcoming, Waiting on and By project, scoped by [scope]. Tapping a
 * task calls [onOpenTask]; tapping a project calls [onOpenList]. Uses the same [TasksViewModel] as the
 * Tasks app, so both show the same data.
 */
@Composable
fun TasksPane(
    scope: TaskScope,
    onOpenTask: (String) -> Unit,
    onOpenList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val vm: TasksViewModel = viewModel()
    val ctx = rememberTaskCtx(vm)
    val coroutines = rememberCoroutineScope()
    var chip by rememberSaveable { mutableStateOf(PaneChip.TODAY) }
    var sheet by remember { mutableStateOf<TaskSheet?>(null) }
    val theirs by vm.commitmentsTheirs.collectAsState()
    LaunchedEffect(Unit) { vm.refreshCalendar() }

    val today = ctx.today
    val zone = ctx.zone
    val rows = TaskViews.scoped(ctx.all, scope, ctx.lists)
    val lists = if (scope == TaskScope.WORK) ctx.lists.filter { it.space == "WORK" } else ctx.lists
    val nothingSelected = emptySet<String>()

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaneChip.entries.forEach { option ->
                MMChip(option.label, selected = chip == option, onClick = { chip = option })
            }
        }
        Spacer(Modifier.height(MM.space.m))

        when (chip) {
            PaneChip.TODAY -> {
                val due = TaskViews.dueToday(rows, today, zone)
                val (done, total) = TaskViews.dayProgress(rows, today, zone)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                    ProgressRing(done = done, total = total, size = 44.dp)
                    Text(
                        if (due.isEmpty()) "All done for today" else "${due.size} left today",
                        style = MM.type.secondary,
                        color = colors.inkSecondary
                    )
                }
                Spacer(Modifier.height(MM.space.s))
                if (due.isEmpty()) {
                    Text("Nothing dated today.", style = MM.type.caption, color = colors.inkMuted)
                } else {
                    TaskRows(
                        tasks = due,
                        ctx = ctx,
                        selection = nothingSelected,
                        onOpen = { onOpenTask(it.id) },
                        onComplete = { task -> coroutines.launch { vm.toggleDone(task) } },
                        onSchedule = { task -> sheet = TaskSheet.Date(listOf(task.id)) },
                        onToggleSelect = { }
                    )
                }
            }
            PaneChip.UPCOMING -> {
                val groups = TaskViews.groupUpcoming(rows, today, zone)
                if (groups.isEmpty()) {
                    Text("Nothing dated after today.", style = MM.type.caption, color = colors.inkMuted)
                }
                groups.forEach { (day, items) ->
                    GroupLabel(title = if (day == null) "Later" else TaskViews.dayLabel(day, today), count = items.size)
                    TaskRows(
                        tasks = items,
                        ctx = ctx,
                        selection = nothingSelected,
                        onOpen = { onOpenTask(it.id) },
                        onComplete = { task -> coroutines.launch { vm.toggleDone(task) } },
                        onSchedule = { task -> sheet = TaskSheet.Date(listOf(task.id)) },
                        onToggleSelect = { }
                    )
                }
            }
            PaneChip.WAITING -> {
                val waiting = TaskViews.waiting(rows)
                if (waiting.isEmpty() && theirs.isEmpty()) {
                    Text("Nothing is waiting on someone else.", style = MM.type.caption, color = colors.inkMuted)
                }
                TaskRows(
                    tasks = waiting,
                    ctx = ctx,
                    selection = nothingSelected,
                    onOpen = { onOpenTask(it.id) },
                    onComplete = { task -> coroutines.launch { vm.toggleDone(task) } },
                    onSchedule = { task -> sheet = TaskSheet.Date(listOf(task.id)) },
                    onToggleSelect = { }
                )
                if (theirs.isNotEmpty()) {
                    GroupLabel(title = "Owed to you", count = theirs.size)
                    theirs.forEach { item -> CommitmentRow(item, ctx) }
                }
            }
            PaneChip.PROJECTS -> {
                if (lists.isEmpty()) {
                    Text("No lists yet.", style = MM.type.caption, color = colors.inkMuted)
                }
                lists.forEach { list ->
                    val inList = rows.filter { it.listId == list.id && it.parentId == null }
                    ListRow(
                        title = list.name,
                        meta = "${inList.count { !it.done }} open",
                        leading = {
                            Box(Modifier.size(10.dp).background(projectColor(list.id), MM.radius.small))
                        },
                        onClick = { onOpenList(list.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(MM.space.m))
        TextAction(text = "New task", onClick = { sheet = TaskSheet.QuickAdd() })
    }

    TaskSheetHost(
        vm = vm,
        ctx = ctx,
        sheet = sheet,
        onDismiss = { sheet = null },
        onCreatedList = onOpenList
    )
}
