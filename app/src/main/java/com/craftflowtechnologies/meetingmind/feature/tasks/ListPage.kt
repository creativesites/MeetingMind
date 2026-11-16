package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskSection
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.work.ProgressRing
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch

/**
 * A list: its progress, sections with their tasks (or a board of one column per section), a priority
 * filter, and an add-section field. Long-press a row to select; the bulk bar belongs to the screen.
 */
@Composable
fun ListPage(
    vm: TasksViewModel,
    ctx: TaskCtx,
    listId: String,
    selection: Set<String>,
    onBack: () -> Unit,
    onOpen: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onSchedule: (Task) -> Unit,
    onToggleSelect: (Task) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onSheet: (TaskSheet) -> Unit
) {
    val colors = MM.colors
    val scope = rememberCoroutineScope()
    val list = ctx.listById[listId]
    val tasks = ctx.all.filter { it.listId == listId && it.parentId == null }
    var board by rememberSaveable { mutableStateOf(false) }
    var priorityFilter by rememberSaveable { mutableStateOf(0) }
    var newSection by rememberSaveable { mutableStateOf("") }
    val sectionFlow = remember(listId) { vm.sectionsOf(listId) }
    val sections by sectionFlow.collectAsState(initial = emptyList())

    val shown = tasks.filter { priorityFilter == 0 || it.priority == priorityFilter }
    val ordered = shown.sortedWith(compareBy<Task> { it.done }.thenBy { it.position }.thenBy { it.dueAt ?: Long.MAX_VALUE })
    val groups = buildList<Pair<TaskSection?, List<Task>>> {
        add(null to ordered.filter { t -> t.sectionId == null || sections.none { it.id == t.sectionId } })
        sections.forEach { s -> add(s to ordered.filter { it.sectionId == s.id }) }
    }
    val done = tasks.count { it.done }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = list?.name ?: "List",
            subtitle = list?.let { spaceLabel(it.space) },
            onBack = onBack,
            actions = {
                HeaderAction(Icons.Rounded.CheckCircleOutline, "Select all open tasks") {
                    onSelectAll(tasks.filter { !it.done }.map { it.id })
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                ProgressRing(done = done, total = tasks.size, size = 44.dp)
                Text(
                    "$done of ${tasks.size} done",
                    style = MM.type.secondary,
                    color = colors.inkSecondary,
                    modifier = Modifier.weight(1f)
                )
                SegmentedControl(
                    options = listOf("List", "Board"),
                    selectedIndex = if (board) 1 else 0,
                    onSelect = { board = it == 1 }
                )
            }
            Spacer(Modifier.height(MM.space.s))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                MMChip("Any priority", selected = priorityFilter == 0, onClick = { priorityFilter = 0 })
                (1..4).forEach { p ->
                    MMChip("P$p", selected = priorityFilter == p, onClick = { priorityFilter = p })
                }
            }
            Spacer(Modifier.height(MM.space.m))

            if (board) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    groups.filter { it.first != null || it.second.isNotEmpty() }.forEach { (section, items) ->
                        Column(
                            Modifier
                                .width(240.dp)
                                .background(colors.canvas, MM.radius.card)
                                .padding(MM.space.s),
                            verticalArrangement = Arrangement.spacedBy(MM.space.xs)
                        ) {
                            Text(
                                "${section?.name ?: "No section"} · ${items.size}",
                                style = MM.type.bodyStrong,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            items.forEach { task ->
                                MMCard(
                                    onClick = { onOpen(task) },
                                    contentPadding = MM.space.s,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        task.title,
                                        style = MM.type.body,
                                        color = if (task.done) colors.inkMuted else colors.ink,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    TaskViews.dueLabel(task, ctx.today, ctx.zone)?.let {
                                        Text(it, style = MM.type.caption, color = colors.inkSecondary)
                                    }
                                }
                            }
                            TextAction("Add task", onClick = { onSheet(TaskSheet.QuickAdd(listId)) })
                        }
                    }
                }
            } else {
                groups.forEach { (section, items) ->
                    if (section != null || items.isNotEmpty()) {
                        GroupLabel(title = section?.name ?: "No section", count = items.size)
                        TaskRows(
                            tasks = items,
                            ctx = ctx,
                            selection = selection,
                            onOpen = onOpen,
                            onComplete = onComplete,
                            onSchedule = onSchedule,
                            onToggleSelect = onToggleSelect
                        )
                    }
                }
                Spacer(Modifier.height(MM.space.s))
                TextAction("Add task", onClick = { onSheet(TaskSheet.QuickAdd(listId)) })
            }

            Spacer(Modifier.height(MM.space.l))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                OutlinedTextField(
                    value = newSection,
                    onValueChange = { newSection = it },
                    label = { Text("New section") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextAction(
                    text = "Add",
                    enabled = newSection.isNotBlank(),
                    onClick = {
                        val name = newSection
                        newSection = ""
                        scope.launch { vm.addSection(listId, name) }
                    }
                )
            }
            Spacer(Modifier.height(MM.space.xxl))
        }
    }
}

/** The space a list belongs to, in words. */
internal fun spaceLabel(space: String): String = when (space) {
    "WORK" -> "Work"
    "PERSONAL" -> "Personal"
    "LEARNING" -> "Learning"
    else -> space.lowercase().replaceFirstChar { it.uppercase() }
}
