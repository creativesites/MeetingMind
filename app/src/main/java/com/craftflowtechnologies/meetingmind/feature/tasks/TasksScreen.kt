package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.work.ProgressRing
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the Tasks app is. The screen keeps a stack of these; Back pops it. */
sealed interface TasksRoute {
    data object Home : TasksRoute
    data class View(val view: SmartView) : TasksRoute
    data class Label(val name: String) : TasksRoute
    data class OpenList(val listId: String) : TasksRoute
    data class OpenTask(val taskId: String) : TasksRoute
    data object Plan : TasksRoute
}

/** The actions every page can take, bundled so the pages take one parameter instead of many. */
private class TasksActions(
    val open: (TasksRoute) -> Unit,
    val back: () -> Unit,
    val sheet: (TaskSheet) -> Unit,
    val openTask: (Task) -> Unit,
    val complete: (Task) -> Unit,
    val schedule: (Task) -> Unit,
    val toggleSelect: (Task) -> Unit,
    val selectAll: (List<String>) -> Unit,
    val selection: Set<String>,
    val onOpenNote: (String) -> Unit,
    val onOpenRecording: (String, Long?) -> Unit
)

/**
 * The Tasks app: a home with the day's ring and the smart views, Today with the calendar, Upcoming,
 * the lists, a task page, Plan my day. Quick add, the date sheet and bulk edit are sheets over it.
 */
@Composable
fun TasksScreen(
    vm: TasksViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenRecording: (String, Long?) -> Unit,
    /** Opens straight onto one task (from Work, a notification or a link); Back returns to Tasks home. */
    initialTaskId: String? = null,
    /** Opens straight onto one list. */
    initialListId: String? = null
) {
    val colors = MM.colors
    val ctx = rememberTaskCtx(vm)
    val scope = rememberCoroutineScope()
    val stack = remember {
        mutableStateListOf<TasksRoute>(TasksRoute.Home).apply {
            initialListId?.let { add(TasksRoute.OpenList(it)) }
            initialTaskId?.let { add(TasksRoute.OpenTask(it)) }
        }
    }
    var sheet by remember { mutableStateOf<TaskSheet?>(null) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val snackbar = remember { SnackbarHostState() }
    val undo by vm.undo.collectAsState()
    val top = stack.last()

    fun open(route: TasksRoute) {
        stack.add(route)
        selection = emptySet()
    }

    fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            selection = emptySet()
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) { vm.refreshCalendar() }

    BackHandler(enabled = selection.isNotEmpty() || stack.size > 1) {
        if (selection.isNotEmpty()) selection = emptySet() else back()
    }

    LaunchedEffect(undo) {
        val current = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message = current.message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) {
            scope.launch { vm.undoLast() }
        } else {
            vm.dismissUndo()
        }
    }

    val actions = TasksActions(
        open = { route -> open(route) },
        back = { back() },
        sheet = { sheet = it },
        openTask = { task -> open(TasksRoute.OpenTask(task.id)) },
        complete = { task -> scope.launch { vm.toggleDone(task) } },
        schedule = { task -> sheet = TaskSheet.Date(listOf(task.id)) },
        toggleSelect = { task ->
            selection = if (task.id in selection) selection - task.id else selection + task.id
        },
        selectAll = { ids -> selection = ids.toSet() },
        selection = selection,
        onOpenNote = onOpenNote,
        onOpenRecording = onOpenRecording
    )

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selection.isNotEmpty()) {
                BulkBar(
                    count = selection.size,
                    onDate = { sheet = TaskSheet.Date(selection.toList()) },
                    onPriority = { sheet = TaskSheet.Priority(selection.toList()) },
                    onMove = { sheet = TaskSheet.Move(selection.toList()) },
                    onAssign = { sheet = TaskSheet.Assign(selection.toList()) },
                    onComplete = {
                        val ids = selection.toList()
                        selection = emptySet()
                        scope.launch { vm.completeAll(ids) }
                    },
                    onClose = { selection = emptySet() }
                )
            }
        },
        floatingActionButton = {
            if (selection.isEmpty() && top != TasksRoute.Plan && top !is TasksRoute.OpenTask) {
                FloatingActionButton(
                    onClick = { sheet = TaskSheet.QuickAdd(listId = (top as? TasksRoute.OpenList)?.listId) },
                    containerColor = colors.accent,
                    contentColor = colors.onAccent
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New task")
                }
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (top) {
                TasksRoute.Home -> HomePage(vm, ctx, actions)
                is TasksRoute.View -> ViewPage(vm, ctx, top.view, actions)
                is TasksRoute.Label -> LabelPage(ctx, top.name, actions)
                is TasksRoute.OpenList -> ListPage(
                    vm = vm,
                    ctx = ctx,
                    listId = top.listId,
                    selection = selection,
                    onBack = { back() },
                    onOpen = actions.openTask,
                    onComplete = actions.complete,
                    onSchedule = actions.schedule,
                    onToggleSelect = actions.toggleSelect,
                    onSelectAll = actions.selectAll,
                    onSheet = actions.sheet
                )
                is TasksRoute.OpenTask -> TaskPage(
                    vm = vm,
                    ctx = ctx,
                    taskId = top.taskId,
                    onBack = { back() },
                    onOpenTask = { id -> open(TasksRoute.OpenTask(id)) },
                    onSheet = actions.sheet,
                    onOpenNote = onOpenNote,
                    onOpenRecording = onOpenRecording
                )
                TasksRoute.Plan -> PlanMyDayPage(
                    vm = vm,
                    ctx = ctx,
                    onBack = { back() },
                    onSheet = actions.sheet,
                    onSeeToday = {
                        stack.removeAt(stack.lastIndex)
                        open(TasksRoute.View(SmartView.TODAY))
                    }
                )
            }
        }
    }

    TaskSheetHost(
        vm = vm,
        ctx = ctx,
        sheet = sheet,
        onDismiss = { sheet = null },
        onCreatedList = { id -> open(TasksRoute.OpenList(id)) }
    )
}

private fun dayTitle(today: LocalDate): String = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))

private fun iconFor(view: SmartView): ImageVector = when (view) {
    SmartView.TODAY -> Icons.Filled.WbSunny
    SmartView.UPCOMING -> Icons.Filled.Event
    SmartView.INBOX -> Icons.Rounded.Inbox
    SmartView.WAITING -> Icons.Filled.Schedule
    SmartView.PRIORITY_1 -> Icons.Rounded.Flag
    SmartView.COMPLETED -> Icons.Filled.CheckCircle
}

@Composable
private fun HomePage(vm: TasksViewModel, ctx: TaskCtx, a: TasksActions) {
    val colors = MM.colors
    val today = ctx.today
    val zone = ctx.zone
    val labels by vm.labels.collectAsState()
    val theirs by vm.commitmentsTheirs.collectAsState()
    val meetings by vm.todayEvents.collectAsState()
    val (done, total) = TaskViews.dayProgress(ctx.all, today, zone)
    val openToday = TaskViews.dueToday(ctx.all, today, zone).size
    val pastDue = TaskViews.pastDue(ctx.all, today, zone).size
    val summary = buildList<String> {
        add("${TaskViews.count(openToday, "task")} for today")
        if (pastDue > 0) add("$pastDue past due")
        if (meetings.isNotEmpty()) add("${meetings.size} ${if (meetings.size == 1) "meeting" else "meetings"}")
    }.joinToString(" · ")

    val tiles = listOf(
        Triple(SmartView.TODAY, SmartView.TODAY.label, openToday),
        Triple(SmartView.UPCOMING, SmartView.UPCOMING.label, TaskViews.upcoming(ctx.all, today, zone).size),
        Triple(SmartView.INBOX, SmartView.INBOX.label, TaskViews.inbox(ctx.all).size),
        Triple(SmartView.WAITING, SmartView.WAITING.label, TaskViews.waiting(ctx.all).size + theirs.size),
        Triple(SmartView.PRIORITY_1, SmartView.PRIORITY_1.label, TaskViews.priorityOne(ctx.all).size),
        Triple(SmartView.COMPLETED, SmartView.COMPLETED.label, TaskViews.completed(ctx.all).size)
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MM.space.l)
    ) {
        PageHeader(title = "Tasks", subtitle = dayTitle(today), onBack = null)
        Spacer(Modifier.height(MM.space.s))
        HeroCard(Modifier.fillMaxWidth(), contentPadding = MM.space.l) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.l)) {
                ProgressRing(done = done, total = total, size = 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (total > 0 && done == total) "Today is done" else "Plan your day",
                        style = MM.type.heading,
                        color = colors.ink
                    )
                    Text(summary, style = MM.type.secondary, color = colors.inkSecondary)
                    Spacer(Modifier.height(MM.space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        PrimaryButton(text = "Plan my day", onClick = { a.open(TasksRoute.Plan) })
                        SecondaryButton(text = "Open Today", onClick = { a.open(TasksRoute.View(SmartView.TODAY)) })
                    }
                }
            }
        }
        Spacer(Modifier.height(MM.space.m))
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                pair.forEach { (view, label, count) ->
                    SmartTile(
                        label = label,
                        count = count,
                        icon = iconFor(view),
                        modifier = Modifier.weight(1f),
                        onClick = { a.open(TasksRoute.View(view)) }
                    )
                }
            }
            Spacer(Modifier.height(MM.space.s))
        }

        listOf("WORK" to "Work", "PERSONAL" to "Personal", "LEARNING" to "Learning").forEach { (space, heading) ->
            val inSpace = ctx.lists.filter { it.space == space }
            if (inSpace.isNotEmpty()) {
                GroupLabel(title = heading, count = null)
                inSpace.forEach { list ->
                    val count = ctx.all.count { it.listId == list.id && it.parentId == null && !it.done }
                    ListRow(
                        title = list.name,
                        meta = count.toString(),
                        leading = {
                            Box(Modifier.size(10.dp).background(projectColor(list.id), MM.radius.small))
                        },
                        onClick = { a.open(TasksRoute.OpenList(list.id)) }
                    )
                }
            }
        }

        GroupLabel(title = "Labels", count = null)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            labels.forEach { label ->
                MMChip("@${label.name}", selected = false, onClick = { a.open(TasksRoute.Label(label.name)) })
            }
        }
        Spacer(Modifier.height(MM.space.l))
        TextAction(
            text = "New list",
            onClick = { a.sheet(TaskSheet.NewList) }
        )
        Spacer(Modifier.height(MM.space.xxl))
        Spacer(Modifier.height(MM.space.xxl))
    }
}

@Composable
private fun SmartTile(label: String, count: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = MM.colors
    MMCard(modifier = modifier, onClick = onClick, contentPadding = MM.space.m) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(MM.space.s))
        Text(count.toString(), style = MM.type.title, color = colors.ink)
        Text(label, style = MM.type.caption, color = colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ViewPage(vm: TasksViewModel, ctx: TaskCtx, view: SmartView, a: TasksActions) {
    val colors = MM.colors
    val today = ctx.today
    val zone = ctx.zone
    val theirs by vm.commitmentsTheirs.collectAsState()
    val rows = TaskViews.viewOf(view, ctx.all, today, zone)
    val subtitle = when (view) {
        SmartView.TODAY -> dayTitle(today)
        SmartView.UPCOMING -> "Next seven days, then later"
        SmartView.INBOX -> "Captured, not yet sorted"
        SmartView.WAITING -> "Promises other people owe you"
        SmartView.PRIORITY_1 -> "Flagged red"
        SmartView.COMPLETED -> "Your log"
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(title = view.label, subtitle = subtitle, onBack = { a.back() })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
        ) {
            when (view) {
                SmartView.TODAY -> TodayBody(vm, ctx, a)
                SmartView.UPCOMING -> UpcomingBody(ctx, a)
                SmartView.INBOX -> {
                    Text(
                        "Quick adds without a list land here. Give each one a list and a date, or do it now.",
                        style = MM.type.secondary,
                        color = colors.inkSecondary
                    )
                    Spacer(Modifier.height(MM.space.s))
                    TaskRows(rows, ctx, a.selection, a.openTask, a.complete, a.schedule, a.toggleSelect)
                }
                SmartView.WAITING -> {
                    TaskRows(rows, ctx, a.selection, a.openTask, a.complete, a.schedule, a.toggleSelect)
                    if (theirs.isNotEmpty()) {
                        GroupLabel(title = "Owed to you", count = theirs.size)
                        theirs.forEach { item -> CommitmentRow(item, ctx) }
                    }
                    Spacer(Modifier.height(MM.space.m))
                    Text(
                        "To hand a task to someone, open it and choose \"Someone else is doing this\". It moves here and keeps its source.",
                        style = MM.type.caption,
                        color = colors.inkMuted
                    )
                }
                SmartView.PRIORITY_1, SmartView.COMPLETED -> {
                    if (rows.isEmpty()) {
                        Text("Nothing here.", style = MM.type.secondary, color = colors.inkMuted)
                    } else {
                        TaskRows(rows, ctx, a.selection, a.openTask, a.complete, a.schedule, a.toggleSelect)
                    }
                    if (view == SmartView.COMPLETED) {
                        Spacer(Modifier.height(MM.space.m))
                        Text(
                            "Completed tasks are kept and searchable. The weekly review reads from here.",
                            style = MM.type.caption,
                            color = colors.inkMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(MM.space.xxl))
            Spacer(Modifier.height(MM.space.xxl))
        }
    }
}

@Composable
private fun LabelPage(ctx: TaskCtx, name: String, a: TasksActions) {
    val rows = TaskViews.open(ctx.all).filter { task ->
        ctx.labels[task.id].orEmpty().any { it.equals(name, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = "@$name", subtitle = TaskViews.count(rows.size, "open task"), onBack = { a.back() })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
        ) {
            if (rows.isEmpty()) {
                Text("No open tasks carry this label.", style = MM.type.secondary, color = MM.colors.inkMuted)
            } else {
                TaskRows(rows, ctx, a.selection, a.openTask, a.complete, a.schedule, a.toggleSelect)
            }
            Spacer(Modifier.height(MM.space.xxl))
        }
    }
}

/** One timeline entry on Today: a meeting from the calendar, or a task with a time. */
private data class TimelineEntry(val time: LocalTime, val meeting: CalendarEvent?, val task: Task?)

@Composable
private fun TodayBody(vm: TasksViewModel, ctx: TaskCtx, a: TasksActions) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    val today = ctx.today
    val zone: ZoneId = ctx.zone
    val meetings by vm.todayEvents.collectAsState()
    val dueToday = TaskViews.dueToday(ctx.all, today, zone)
    val past = TaskViews.pastDue(ctx.all, today, zone)
    val anytime = TaskViews.anytimeToday(ctx.all, today, zone)
    val timed = TaskViews.scheduledToday(ctx.all, today, zone)
    val (done, total) = TaskViews.dayProgress(ctx.all, today, zone)
    val openCount = dueToday.size

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
        ProgressRing(done = done, total = total, size = 56.dp)
        Text(
            if (openCount == 0) "All done. Enjoy the rest of the day." else "$openCount left · about ${openCount * TaskViews.MINUTES_PER_TASK} min of work",
            style = MM.type.secondary,
            color = colors.inkSecondary,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(MM.space.m))

    if (past.isNotEmpty()) {
        InsetPanel(Modifier.fillMaxWidth(), contentPadding = MM.space.m) {
            Text(
                "${TaskViews.count(past.size, "task")} from before today ${if (past.size == 1) "is" else "are"} still open.",
                style = MM.type.bodyStrong,
                color = colors.ink
            )
            Spacer(Modifier.height(MM.space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                SecondaryButton(
                    text = "Do today",
                    onClick = {
                        scope.launch {
                            vm.applyDate(past.map { it.id }, DateChoice(dueAt = startOfDayMs(today, zone), hasTime = false))
                        }
                    }
                )
                TextAction(text = "Reschedule", onClick = { a.sheet(TaskSheet.Date(past.map { it.id })) })
            }
        }
        Spacer(Modifier.height(MM.space.m))
    }

    GroupLabel(title = "Scheduled", count = null)
    val entries = buildList {
        meetings.forEach { add(TimelineEntry(localTimeOf(it.begin, zone), it, null)) }
        timed.forEach { t -> t.dueAt?.let { add(TimelineEntry(localTimeOf(it, zone), null, t)) } }
    }.sortedBy { it.time }
    if (entries.isEmpty()) {
        Text(
            if (vm.calendarAllowed()) "Nothing scheduled for a time today." else "Allow calendar access to see your meetings here.",
            style = MM.type.caption,
            color = colors.inkMuted
        )
    }
    entries.forEach { entry ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                entry.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MM.type.caption,
                color = colors.inkSecondary,
                modifier = Modifier.width(52.dp).padding(top = MM.space.m)
            )
            val meeting = entry.meeting
            val task = entry.task
            if (meeting != null) {
                InsetPanel(Modifier.weight(1f).padding(vertical = MM.space.xs), contentPadding = MM.space.s) {
                    Text(meeting.title, style = MM.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Meeting", style = MM.type.caption, color = colors.inkSecondary)
                }
            } else if (task != null) {
                Column(Modifier.weight(1f)) {
                    TaskRow(
                        task = task,
                        ctx = ctx,
                        selecting = a.selection.isNotEmpty(),
                        selected = task.id in a.selection,
                        onOpen = { a.openTask(task) },
                        onComplete = { a.complete(task) },
                        onSchedule = { a.schedule(task) },
                        onToggleSelect = { a.toggleSelect(task) }
                    )
                }
            }
        }
    }

    if (anytime.isNotEmpty()) {
        GroupLabel(title = "Anytime today", count = anytime.size)
        TaskRows(anytime, ctx, a.selection, a.openTask, a.complete, a.schedule, a.toggleSelect)
    }
    Spacer(Modifier.height(MM.space.l))
    Text("Swipe right to complete · left to change the date", style = MM.type.caption, color = colors.inkMuted)
}

@Composable
private fun UpcomingBody(ctx: TaskCtx, a: TasksActions) {
    val colors = MM.colors
    val today = ctx.today
    val zone = ctx.zone
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val week = TaskViews.week(today)
    val dots = TaskViews.daysWithTasks(ctx.all, today, zone)
    val groups = TaskViews.groupUpcoming(ctx.all, today, zone).filter { picked == null || it.first == picked }

    Row(Modifier.fillMaxWidth()) {
        week.forEach { day ->
            val selected = picked == day
            val selectable = day.isAfter(today)
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = selectable) { picked = if (selected) null else day },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())), style = MM.type.caption, color = colors.inkMuted)
                Spacer(Modifier.height(MM.space.xs))
                Box(
                    Modifier
                        .size(36.dp)
                        .background(if (selected) colors.accent else Color.Transparent, MM.radius.pill),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.dayOfMonth.toString(),
                        style = MM.type.bodyStrong,
                        color = when {
                            selected -> colors.onAccent
                            day == today -> colors.accent
                            else -> colors.ink
                        }
                    )
                }
                Spacer(Modifier.height(MM.space.xs))
                Box(
                    Modifier
                        .size(5.dp)
                        .background(if (day in dots) colors.accent else Color.Transparent, MM.radius.pill)
                )
            }
        }
    }
    Spacer(Modifier.height(MM.space.s))
    groups.forEach { (day, items) ->
        GroupLabel(title = if (day == null) "Later" else TaskViews.dayLabel(day, today), count = items.size)
        TaskRows(items, ctx, a.selection, a.openTask, a.complete, a.schedule, a.toggleSelect)
    }
    if (groups.isEmpty()) {
        Text("Nothing dated after today.", style = MM.type.secondary, color = colors.inkMuted)
    }
}

@Composable
internal fun CommitmentRow(item: ItemEntity, ctx: TaskCtx) {
    val colors = MM.colors
    val who = item.ownerPersonId?.let { ctx.people[it] }
    Column(Modifier.fillMaxWidth().padding(vertical = MM.space.s)) {
        Text(item.text, style = MM.type.bodyStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(who, item.dueText).joinToString(" · ").ifEmpty { "No date" },
            style = MM.type.caption,
            color = colors.inkSecondary
        )
    }
}

@Composable
private fun BulkBar(
    count: Int,
    onDate: () -> Unit,
    onPriority: () -> Unit,
    onMove: () -> Unit,
    onAssign: () -> Unit,
    onComplete: () -> Unit,
    onClose: () -> Unit
) {
    val colors = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.ink)
            .navigationBarsPadding()
            .padding(horizontal = MM.space.s, vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Clear selection", tint = colors.onInk)
        }
        Text("$count selected", style = MM.type.bodyStrong, color = colors.onInk, modifier = Modifier.weight(1f))
        BulkAction(Icons.Rounded.DateRange, "Date", onDate)
        BulkAction(Icons.Rounded.Flag, "Priority", onPriority)
        BulkAction(Icons.Rounded.FolderOpen, "Move", onMove)
        BulkAction(Icons.Rounded.Person, "Assign", onAssign)
        BulkAction(Icons.Filled.CheckCircle, "Complete", onComplete)
    }
}

@Composable
private fun BulkAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = MM.space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = MM.colors.onInk, modifier = Modifier.size(20.dp))
        Text(label, style = MM.type.caption, color = MM.colors.onInk, maxLines = 1)
    }
}
