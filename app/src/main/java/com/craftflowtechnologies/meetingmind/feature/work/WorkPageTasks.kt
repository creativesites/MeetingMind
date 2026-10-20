package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.notes.PagedState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

internal class TasksActions(
    val onWaiting: (Boolean) -> Unit = {},
    val onAdd: (String) -> Unit = {},
    val onDone: (WorkTaskRow) -> Unit = {},
    val onEdit: (WorkTaskRow) -> Unit = {},
    val onOpenSource: (WorkTaskRow) -> Unit = {},
    val onNudge: (WorkTaskRow) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {}
)

/**
 * The Tasks segment: Mine | Waiting on, grouped by when they are due, loaded a page at a time. Each task is a
 * card with its due date as a pill, and its source meeting as a link that jumps to the moment it was said.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksContent(
    waiting: Boolean,
    state: PagedState<WorkTaskRow>,
    now: Long,
    meetingTitles: Map<String, String>,
    listState: LazyListState,
    actions: TasksActions,
    modifier: Modifier = Modifier
) {
    val entries = remember(state.items, now / 60_000L) { groupTasks(state.items, now) }
    LaunchedEffect(listState, entries.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (entries.isNotEmpty() && last >= entries.size - LOAD_AHEAD) actions.onLoadMore() }
    }
    Column(modifier.fillMaxSize()) {
        SegmentedControl(listOf("Mine", "Waiting on"), if (waiting) 1 else 0, { actions.onWaiting(it == 1) }, Modifier.padding(horizontal = MM.space.l))
        if (!waiting) QuickAdd(actions.onAdd, Modifier.padding(horizontal = MM.space.l, vertical = MM.space.m))
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("work_tasks_list"), state = listState,
                contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, bottom = MM.space.xxl)
            ) {
                when {
                    state.isLoadingFirst && state.items.isEmpty() -> items(4, key = { "skeleton-$it" }) { SkeletonRow() }
                    state.error != null && state.items.isEmpty() -> item(key = "error") {
                        StatusLine(StatusKind.Error, "Couldn't load your tasks.", Modifier.padding(top = MM.space.l), actionLabel = "Retry", onAction = actions.onRetry)
                    }
                    state.items.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            illustration = { ZuriSlot(CompanionPage.EMPTY, EmptyCompanionSize) },
                            title = if (waiting) "Nobody owes you anything" else "Nothing on your plate",
                            body = if (waiting) "When someone promises you something in a meeting, it shows here until it arrives."
                            else "Tasks you add, and ones you agree to in meetings, land here with the day they're due."
                        )
                    }
                    else -> {
                        items(entries, key = { it.key }) { entry ->
                            when (entry) {
                                is TaskEntry.Header -> SectionHeader(
                                    entry.bucket.label, Modifier.padding(top = MM.space.l, bottom = MM.space.s),
                                    count = if (state.endReached) entry.count else null
                                )
                                is TaskEntry.Row -> TaskCard(entry.row, now, waiting, meetingTitles, actions)
                            }
                        }
                        pagedFooter(state, "That's everything", actions.onRetry)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAdd(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { onAdd(text); text = "" } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WorkTextField(
            text, { text = it }, "Add a task, e.g. “Send the proposal Friday”", Icons.Rounded.Add,
            modifier = Modifier.weight(1f).testTag("work_add_task"), imeAction = ImeAction.Done, onAction = submit
        )
        if (text.isNotBlank()) TextAction("Add", submit)
    }
}

@Composable
private fun TaskCard(row: WorkTaskRow, now: Long, waiting: Boolean, titles: Map<String, String>, actions: TasksActions) {
    val t = row.task
    val due = dueLabel(t.dueAt, open = true, now = now)
    val overdue = due?.second == true
    val source = t.meetingId?.let { titles[it] }
    Row(
        Modifier.fillMaxWidth().padding(bottom = MM.space.s).background(MM.colors.surface, MM.radius.card)
            .clickable(role = Role.Button) { actions.onEdit(row) }.padding(start = MM.space.xs, end = MM.space.m, top = MM.space.xs, bottom = MM.space.xs),
        verticalAlignment = Alignment.Top
    ) {
        IconButton(onClick = { actions.onDone(row) }) {
            Icon(Icons.Rounded.RadioButtonUnchecked, "Mark done: ${t.title}", tint = if (overdue) MM.colors.danger else MM.colors.inkSecondary)
        }
        Column(Modifier.weight(1f).padding(top = MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            Text(t.title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                due?.let { (label, isOverdue) ->
                    Box(
                        Modifier.background(if (isOverdue) MM.colors.dangerWash else MM.colors.accentWash, MM.radius.pill)
                            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    ) {
                        Text(label, style = MM.type.caption, color = if (isOverdue) MM.colors.danger else MM.colors.accent)
                    }
                }
                if (waiting) t.ownerName?.let { Text("from $it", style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (source != null && t.meetingId != null) {
                Row(
                    Modifier.heightIn(min = MMSize.minTouch).clickable(role = Role.Button) { actions.onOpenSource(row) }
                        .semantics { contentDescription = "Jump to the moment in $source" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Link, null, Modifier.padding(end = MM.space.xs).size(MMSize.iconSmall), tint = MM.colors.accent)
                    Text(source, style = MM.type.caption, color = MM.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (waiting) TextAction("Nudge", { actions.onNudge(row) }, Modifier.padding(top = MM.space.s))
    }
}
