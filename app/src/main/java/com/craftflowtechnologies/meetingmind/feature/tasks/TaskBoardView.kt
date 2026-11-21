package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.work.TaskCheck
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/** The columns a person can add straight into: the add button dates the new task for that column. */
private val addable = setOf(BoardColumn.NO_DATE, BoardColumn.TODAY, BoardColumn.UPCOMING)

/** The widest a column grows; on a tablet several columns show side by side. */
private val maxColumnWidth = 360.dp

/**
 * The Tasks board: one column per [BoardColumn], swiped sideways, with a chip strip above to jump
 * between them. Opens on Today. Each card can be ticked, opened, or moved to another column from its
 * menu (a long press opens the same menu).
 */
@Composable
internal fun TaskBoardView(
    columns: List<Pair<BoardColumn, List<Task>>>,
    ctx: TaskCtx,
    onOpen: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onMove: (Task, BoardColumn) -> Unit,
    onAdd: (BoardColumn) -> Unit,
    modifier: Modifier = Modifier
) {
    val pager = rememberPagerState(initialPage = BoardColumn.TODAY.ordinal) { columns.size }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().testTag("task_board")) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MM.space.l),
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            columns.forEachIndexed { i, (column, tasks) ->
                MMChip(
                    "${column.title} ${tasks.size}",
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } }
                )
            }
        }
        Spacer(Modifier.height(MM.space.m))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columnWidth = min(maxWidth - MM.space.l * 3, maxColumnWidth)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(columnWidth),
                pageSpacing = MM.space.m,
                contentPadding = PaddingValues(horizontal = MM.space.l),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val (column, tasks) = columns[page]
                BoardColumnView(column, tasks, ctx, onOpen, onComplete, onMove, onAdd)
            }
        }
    }
}

@Composable
private fun columnTint(column: BoardColumn): Color {
    val colors = MM.colors
    return when (column) {
        BoardColumn.NO_DATE -> colors.inkMuted
        BoardColumn.TODAY -> colors.accent
        BoardColumn.UPCOMING -> colors.inkSecondary
        BoardColumn.WAITING -> colors.warning
        BoardColumn.DONE -> colors.success
    }
}

@Composable
private fun BoardColumnView(
    column: BoardColumn,
    tasks: List<Task>,
    ctx: TaskCtx,
    onOpen: (Task) -> Unit,
    onComplete: (Task) -> Unit,
    onMove: (Task, BoardColumn) -> Unit,
    onAdd: (BoardColumn) -> Unit
) {
    val colors = MM.colors
    Surface(
        Modifier.fillMaxHeight().fillMaxWidth().testTag("board_column_${column.name}"),
        shape = MM.radius.card,
        color = colors.surfaceSunk
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = MM.space.m, end = MM.space.xs, top = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).background(columnTint(column), CircleShape))
                Spacer(Modifier.width(MM.space.s))
                Text(column.title, style = MM.type.heading, color = colors.ink)
                Spacer(Modifier.width(MM.space.s))
                Text("${tasks.size}", style = MM.type.secondary, color = colors.inkMuted)
                Spacer(Modifier.weight(1f))
                if (column in addable) {
                    IconButton(onClick = { onAdd(column) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add a task to ${column.title}", tint = colors.accent)
                    }
                } else {
                    Spacer(Modifier.height(MMSize.minTouch))
                }
            }
            if (tasks.isEmpty()) {
                Text(
                    column.empty,
                    style = MM.type.secondary,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s)
                )
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = MM.space.s, end = MM.space.s, bottom = MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                items(tasks, key = { it.id }) { task ->
                    BoardCard(task, column, ctx, onOpen = { onOpen(task) }, onComplete = { onComplete(task) }, onMove = { onMove(task, it) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardCard(
    task: Task,
    column: BoardColumn,
    ctx: TaskCtx,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onMove: (BoardColumn) -> Unit
) {
    val colors = MM.colors
    var menu by remember { mutableStateOf(false) }
    val list = task.listId?.let { ctx.listById[it] }
    val subtasks = ctx.childrenOf(task.id)
    val overdue = !task.done && TaskViews.dayOf(task, ctx.zone)?.isBefore(ctx.today) == true
    val due = TaskViews.dueLabel(task, ctx.today, ctx.zone)
    val waitingFor = if (task.waitingOn) task.personId?.let { ctx.people[it] } else null
    val label = ctx.labels[task.id].orEmpty().firstOrNull()

    Surface(
        Modifier
            .fillMaxWidth()
            .clip(MM.radius.card)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }, onClickLabel = "Open", onLongClickLabel = "Move")
            .testTag("board_card"),
        shape = MM.radius.card,
        color = colors.surface,
        border = if (colors.isDark) null else BorderStroke(MMSize.hairline, colors.line)
    ) {
        Row(Modifier.padding(start = MM.space.s, top = MM.space.s, bottom = MM.space.s), verticalAlignment = Alignment.Top) {
            TaskCheck(checked = task.done, priority = task.priority, onToggle = { onComplete() })
            Column(Modifier.weight(1f).padding(start = MM.space.xs, top = MM.space.xs)) {
                if (list != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(projectColor(list.id), MM.radius.small))
                        Spacer(Modifier.width(MM.space.xs))
                        Meta(list.name, colors.inkSecondary)
                    }
                    Spacer(Modifier.height(MM.space.xs))
                }
                Text(
                    task.title,
                    style = MM.type.bodyStrong,
                    color = if (task.done) colors.inkMuted else colors.ink,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildList {
                    if (due != null && column != BoardColumn.DONE) add(due to if (overdue) colors.warning else colors.inkSecondary)
                    if (waitingFor != null) add("with $waitingFor" to colors.warning)
                    if (subtasks.isNotEmpty()) add("${subtasks.count { it.done }}/${subtasks.size} steps" to colors.inkMuted)
                    if (label != null) add("@$label" to colors.inkMuted)
                }
                if (meta.isNotEmpty()) {
                    Row(
                        Modifier.padding(top = MM.space.xs),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        meta.forEach { (text, tint) -> Meta(text, tint) }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Move ${task.title}" }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = colors.inkMuted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    TaskBoard.targets(column).forEach { target ->
                        DropdownMenuItem(
                            text = { Text("Move to ${target.title}", style = MM.type.body, color = colors.ink) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(columnTint(target), CircleShape)) },
                            onClick = { menu = false; onMove(target) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Meta(text: String, color: Color) {
    Text(text, style = MM.type.caption, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
