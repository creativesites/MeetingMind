package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.tasks.QuickAddResult
import com.craftflowtechnologies.meetingmind.core.tasks.QuickAddSpan
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The sheets the Tasks feature can open. A screen holds at most one at a time. */
sealed interface TaskSheet {
    data class QuickAdd(val listId: String? = null) : TaskSheet
    /** [planPick]: the date goes to the Plan my day card on screen, and the plan moves on. */
    data class Date(val ids: List<String>, val planPick: Boolean = false) : TaskSheet
    data class Priority(val ids: List<String>) : TaskSheet
    data class Move(val ids: List<String>) : TaskSheet
    data class Assign(val ids: List<String>) : TaskSheet
    object NewList : TaskSheet
}

/** Shows the sheet for [sheet], if there is one. [onCreatedList] receives a list the person just made. */
@Composable
fun TaskSheetHost(
    vm: TasksViewModel,
    ctx: TaskCtx,
    sheet: TaskSheet?,
    onDismiss: () -> Unit,
    onCreatedList: (String) -> Unit
) {
    when (sheet) {
        null -> Unit
        is TaskSheet.QuickAdd -> QuickAddSheet(vm, sheet.listId, onDismiss)
        is TaskSheet.Date -> DateSheet(vm, ctx, sheet.ids, sheet.planPick, onDismiss)
        is TaskSheet.Priority -> PrioritySheet(vm, sheet.ids, onDismiss)
        is TaskSheet.Move -> MoveSheet(vm, ctx, sheet.ids, onDismiss)
        is TaskSheet.Assign -> AssignSheet(vm, sheet.ids, onDismiss)
        TaskSheet.NewList -> NewListSheet(vm, onDismiss, onCreatedList)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = MM.space.l).padding(bottom = MM.space.l),
            content = content
        )
    }
}

/** Colours each token the quick-add parser recognised, so the person sees what will be read. */
internal fun highlightQuickAdd(text: String, parsed: QuickAddResult, colors: MMColors): AnnotatedString = buildAnnotatedString {
    append(text)
    parsed.spans.forEach { span ->
        val color = when (span.kind) {
            QuickAddSpan.Kind.DATE, QuickAddSpan.Kind.TIME -> colors.success
            QuickAddSpan.Kind.PRIORITY -> colors.danger
            QuickAddSpan.Kind.LIST, QuickAddSpan.Kind.LABEL, QuickAddSpan.Kind.REPEAT -> colors.accent
        }
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), span.start, span.end)
    }
}

@Composable
private fun QuickAddSheet(vm: TasksViewModel, listId: String?, onDismiss: () -> Unit) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val parsed = vm.parseQuickAdd(text)
    val highlight = remember(colors) {
        VisualTransformation { input ->
            TransformedText(highlightQuickAdd(input.text, vm.parseQuickAdd(input.text), colors), OffsetMapping.Identity)
        }
    }
    val zone = java.time.ZoneId.systemDefault()
    val today = vm.today()

    SheetFrame(onDismiss) {
        Text("New task", style = MM.type.title, color = colors.ink)
        Spacer(Modifier.height(MM.space.m))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MM.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = highlight,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("Send API docs to James tomorrow 3pm #Acme !1 @email", style = MM.type.body, color = colors.inkMuted)
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.height(MM.space.m))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            parsed.dueAt?.let { due ->
                val day = TaskViews.dayOf(due, zone)
                val label = TaskViews.dayLabel(day, today) + if (parsed.hasTime) " ${TaskViews.timeOf(due, zone)}" else ""
                MMChip(label, selected = true, onClick = {})
            }
            parsed.listName?.let { MMChip("#$it", selected = true, onClick = {}) }
            if (parsed.priority < 4) MMChip("P${parsed.priority}", selected = true, onClick = {})
            parsed.labels.forEach { MMChip("@$it", selected = true, onClick = {}) }
            TaskViews.repeatLabel(parsed.repeatRule)?.let { MMChip(it, selected = true, onClick = {}) }
        }
        Spacer(Modifier.height(MM.space.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Type naturally: dates, #list, !priority, @label",
                style = MM.type.caption,
                color = colors.inkMuted,
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = "Add",
                enabled = parsed.title.isNotBlank(),
                onClick = {
                    scope.launch {
                        val saved = vm.quickAdd(text)
                        // A list screen adds into its own list, unless the line named another.
                        if (saved != null && listId != null && saved.listId == null) vm.moveTo(listOf(saved.id), listId)
                        onDismiss()
                    }
                }
            )
        }
    }
}

private data class DateOption(val label: String, val day: LocalDate?)

@Composable
private fun DateSheet(vm: TasksViewModel, ctx: TaskCtx, ids: List<String>, planPick: Boolean, onDismiss: () -> Unit) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    val zone = ctx.zone
    val today = ctx.today
    val first = ids.firstNotNullOfOrNull { ctx.taskById(it) }

    var day by remember { mutableStateOf(first?.let { TaskViews.dayOf(it, zone) }) }
    var time by remember {
        mutableStateOf<LocalTime?>(first?.takeIf { it.hasTime }?.dueAt?.let { localTimeOf(it, zone) })
    }
    var repeat by remember { mutableStateOf(first?.repeatRule) }
    var month by remember { mutableStateOf(YearMonth.from(day ?: today)) }

    val options = listOf(
        DateOption("Today", today),
        DateOption("Tomorrow", today.plusDays(1)),
        DateOption("This weekend", today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))),
        DateOption("Next week", today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))),
        DateOption("No date", null)
    )
    val times: List<LocalTime?> = listOf(null, LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(18, 0))
    val repeats = listOf(
        "Never" to null,
        "Every day" to "FREQ=DAILY",
        "Every weekday" to "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        "Every week" to "FREQ=WEEKLY",
        "Every month" to "FREQ=MONTHLY"
    )

    SheetFrame(onDismiss) {
        Text(
            if (ids.size == 1) first?.title ?: "Date" else "${ids.size} tasks",
            style = MM.type.title,
            color = colors.ink,
            maxLines = 2
        )
        Spacer(Modifier.height(MM.space.m))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            options.forEach { option ->
                MMChip(option.label, selected = day == option.day, onClick = { day = option.day; if (option.day == null) time = null })
            }
        }
        Spacer(Modifier.height(MM.space.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = colors.inkSecondary)
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                style = MM.type.bodyStrong,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = colors.inkSecondary)
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val d = week.getOrNull(i)
                    val selected = d != null && d == day
                    Box(
                        Modifier
                            .weight(1f)
                            .height(MMSize.minTouch)
                            .background(if (selected) colors.accent else Color.Transparent, MM.radius.pill)
                            .clickable(enabled = d != null) { day = d },
                        contentAlignment = Alignment.Center
                    ) {
                        if (d != null) {
                            Text(
                                d.dayOfMonth.toString(),
                                style = MM.type.body,
                                color = when {
                                    selected -> colors.onAccent
                                    d == today -> colors.accent
                                    else -> colors.ink
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(MM.space.m))
        Text("Time", style = MM.type.overline, color = colors.inkMuted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            times.forEach { t ->
                MMChip(t?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "No time", selected = time == t, onClick = { time = t })
            }
        }
        Spacer(Modifier.height(MM.space.m))
        Text("Repeat", style = MM.type.overline, color = colors.inkMuted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            repeats.forEach { (label, rule) ->
                MMChip(label, selected = repeat == rule, onClick = { repeat = rule })
            }
        }
        Spacer(Modifier.height(MM.space.l))
        PrimaryButton(
            text = "Save date",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val picked = day
                val dueAt = picked?.let { d ->
                    val t = time
                    (if (t != null) d.atTime(t) else d.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
                }
                val choice = DateChoice(dueAt = dueAt, hasTime = picked != null && time != null, repeatRule = if (picked == null) null else repeat)
                scope.launch {
                    if (planPick) vm.decidePlan(PlanChoice.PICK, choice) else vm.applyDate(ids, choice)
                    onDismiss()
                }
            }
        )
    }
}

@Composable
private fun PrioritySheet(vm: TasksViewModel, ids: List<String>, onDismiss: () -> Unit) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    val rows = listOf(
        Triple(1, colors.danger, "Priority 1" to "Must happen today"),
        Triple(2, colors.warning, "Priority 2" to "Important"),
        Triple(3, colors.accent, "Priority 3" to "Normal"),
        Triple(4, colors.inkFaint, "Priority 4" to "Whenever")
    )
    SheetFrame(onDismiss) {
        Text("Priority", style = MM.type.title, color = colors.ink)
        Spacer(Modifier.height(MM.space.s))
        rows.forEach { (level, tint, labels) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MMSize.minTouch)
                    .clickable {
                        scope.launch {
                            vm.setPriority(ids, level)
                            onDismiss()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Flag, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(MM.space.m))
                Column {
                    Text(labels.first, style = MM.type.bodyStrong, color = colors.ink)
                    Text(labels.second, style = MM.type.caption, color = colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun MoveSheet(vm: TasksViewModel, ctx: TaskCtx, ids: List<String>, onDismiss: () -> Unit) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    SheetFrame(onDismiss) {
        Text("Move to", style = MM.type.title, color = colors.ink)
        Spacer(Modifier.height(MM.space.s))
        MoveRow("Inbox", null, colors.inkFaint) {
            scope.launch {
                vm.moveTo(ids, null)
                onDismiss()
            }
        }
        listOf("WORK" to "Work", "PERSONAL" to "Personal", "LEARNING" to "Learning").forEach { (space, heading) ->
            val inSpace = ctx.lists.filter { it.space == space }
            if (inSpace.isNotEmpty()) {
                Text(heading, style = MM.type.overline, color = colors.inkMuted, modifier = Modifier.padding(top = MM.space.s))
                inSpace.forEach { list ->
                    MoveRow(list.name, list.id, projectColor(list.id)) {
                        scope.launch {
                            vm.moveTo(ids, list.id)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveRow(label: String, listId: String?, dot: Color, onClick: () -> Unit) {
    val colors = MM.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(dot, MM.radius.small))
        Spacer(Modifier.width(MM.space.m))
        Text(label, style = MM.type.body, color = colors.ink, modifier = Modifier.weight(1f))
        if (listId == null) Text("No list", style = MM.type.caption, color = colors.inkMuted)
    }
}

@Composable
private fun AssignSheet(vm: TasksViewModel, ids: List<String>, onDismiss: () -> Unit) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    SheetFrame(onDismiss) {
        Text("Someone else is doing this", style = MM.type.title, color = colors.ink)
        Spacer(Modifier.height(MM.space.xs))
        Text(
            "It moves to Waiting on. Its source stays linked.",
            style = MM.type.caption,
            color = colors.inkMuted
        )
        Spacer(Modifier.height(MM.space.m))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Who") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MM.space.m))
        PrimaryButton(
            text = "Move to Waiting on",
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    vm.setWaitingOn(ids, name)
                    onDismiss()
                }
            }
        )
    }
}

@Composable
private fun NewListSheet(vm: TasksViewModel, onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var space by remember { mutableStateOf("WORK") }
    SheetFrame(onDismiss) {
        Text("New list", style = MM.type.title, color = colors.ink)
        Spacer(Modifier.height(MM.space.m))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MM.space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            listOf("WORK" to "Work", "PERSONAL" to "Personal", "LEARNING" to "Learning").forEach { (value, label) ->
                MMChip(label, selected = space == value, onClick = { space = value })
            }
        }
        Spacer(Modifier.height(MM.space.l))
        PrimaryButton(
            text = "Create list",
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val list = vm.createList(name, space)
                    onDismiss()
                    list?.let { onCreated(it.id) }
                }
            }
        )
    }
}
