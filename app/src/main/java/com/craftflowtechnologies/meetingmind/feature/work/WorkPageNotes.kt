package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.core.notes.PagedState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.FilterChipRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteSort
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Start loading the next page when this many rows are left below the viewport (WORK_UX §2.2). */
internal const val LOAD_AHEAD = 8

internal class NotesActions(
    val onSearch: (String) -> Unit = {},
    val onToggleChip: (NoteChip) -> Unit = {},
    val onPickProject: () -> Unit = {},
    val onSort: (WorkNoteSort) -> Unit = {},
    val onOpen: (WorkNoteItem) -> Unit = {},
    val onSelect: (WorkNoteItem) -> Unit = {},
    val onPin: (WorkNoteItem) -> Unit = {},
    val onMove: (WorkNoteItem) -> Unit = {},
    val onShare: (WorkNoteItem) -> Unit = {},
    val onDelete: (WorkNoteItem) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onRecord: () -> Unit = {},
    val onWriteNote: () -> Unit = {}
)

private const val PROJECT_CHIP = "PROJECT"

/**
 * The Notes segment (WORK_UX §2.2): search and filters pinned on top, then date-grouped notes loaded a page at a
 * time. First load shows skeleton rows, never a spinner on a blank page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesContent(
    query: NotesQuery,
    state: PagedState<WorkNoteItem>,
    now: Long,
    projects: List<ProjectCard>,
    selected: Set<String>,
    listState: LazyListState,
    actions: NotesActions,
    modifier: Modifier = Modifier
) {
    val projectName = projects.firstOrNull { it.notebook.id == query.notebookId }?.notebook?.name
    val entries = remember(state.items, query.sort, query.isSearching, now / 60_000L) {
        if (query.isSearching) state.items.map { NoteEntry.Row(it) } else groupNotes(state.items, query.sort, now)
    }
    // Load the next page when we are near the end of what is loaded.
    LaunchedEffect(listState, entries.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (entries.isNotEmpty() && last >= entries.size - LOAD_AHEAD) actions.onLoadMore() }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = MM.space.l), verticalAlignment = Alignment.CenterVertically) {
            WorkTextField(
                query.search, actions.onSearch, "Search notes", Icons.Rounded.Search,
                modifier = Modifier.weight(1f).testTag("work_notes_search"), clearLabel = "Clear search"
            )
            SortMenu(query.sort, actions.onSort)
        }
        FilterChipRow(
            options = NoteChip.entries.map { it.name } + PROJECT_CHIP,
            selected = query.chips.map { it.name }.toSet() + listOfNotNull(PROJECT_CHIP.takeIf { query.notebookId != null }),
            onToggle = { key -> if (key == PROJECT_CHIP) actions.onPickProject() else actions.onToggleChip(NoteChip.valueOf(key)) },
            label = { key -> if (key == PROJECT_CHIP) (projectName ?: "Project") else NoteChip.valueOf(key).label },
            modifier = Modifier.padding(horizontal = MM.space.l)
        )
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.weight(1f)) {
            val projectColors = projects.associate { it.notebook.id to projectColor(it) }
            val fallback = MM.colors.inkFaint
            LazyColumn(
                Modifier.fillMaxSize().testTag("work_notes_list"), state = listState,
                contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, bottom = MM.space.xxl)
            ) {
                when {
                    state.isLoadingFirst && state.items.isEmpty() -> items(SKELETON_ROWS, key = { "skeleton-$it" }) { SkeletonRow() }
                    state.error != null && state.items.isEmpty() -> item(key = "error") {
                        StatusLine(StatusKind.Error, "Couldn't load your notes.", Modifier.padding(top = MM.space.l), actionLabel = "Retry", onAction = actions.onRetry)
                    }
                    state.items.isEmpty() -> item(key = "empty") {
                        if (query.isSearching || query.isFiltered) {
                            EmptyState(
                                title = if (query.isSearching) "Nothing matches “${query.search.trim()}”" else "No notes match these filters",
                                body = "Try fewer filters, or search for a different word.",
                                action = { SecondaryButton("Clear filters", actions.onClearFilters) }
                            )
                        } else {
                            EmptyState(
                                illustration = { ZuriSlot(CompanionPage.EMPTY, EmptyCompanionSize) },
                                title = "Your meeting notes live here",
                                body = "Record a meeting or write a note — we'll keep decisions and tasks linked to what was said.",
                                action = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                        PrimaryButton("Record", actions.onRecord)
                                        SecondaryButton("Write note", actions.onWriteNote)
                                    }
                                }
                            )
                        }
                    }
                    else -> {
                        items(entries, key = { it.key }) { entry ->
                            when (entry) {
                                is NoteEntry.Header -> Text(
                                    entry.label, style = MM.type.caption, color = MM.colors.inkMuted,
                                    modifier = Modifier.fillMaxWidth().padding(top = MM.space.l, bottom = MM.space.xs).semantics { heading() }
                                )
                                is NoteEntry.Row -> NoteListRow(
                                    entry.item, now, projectColors[entry.item.notebookId] ?: fallback,
                                    selecting = selected.isNotEmpty(), isSelected = entry.item.id in selected, actions = actions
                                )
                            }
                        }
                        pagedFooter(state, "That's everything", actions.onRetry)
                    }
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 6

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteListRow(item: WorkNoteItem, now: Long, color: Color, selecting: Boolean, isSelected: Boolean, actions: NotesActions) {
    val model = NoteRowModel(
        title = item.displayTitle, preview = item.preview, spaceColor = color, hasRecording = item.hasRecording,
        transcriptStatus = item.transcript, taskCount = item.openTasks, timeLabel = noteTimeLabel(item.at, now),
        pinned = item.pinned, isPrivate = item.isPrivate
    )
    Row(
        Modifier.fillMaxWidth()
            .background(if (isSelected) MM.colors.accentWash else Color.Transparent, MM.radius.small)
            .padding(horizontal = MM.space.xs)
            .semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically
    ) {
        NoteRow(
            model,
            Modifier.weight(1f).combinedClickable(
                role = Role.Button,
                onClick = { if (selecting) actions.onSelect(item) else actions.onOpen(item) },
                onLongClick = { actions.onSelect(item) }
            )
        )
        if (selecting) {
            Box(Modifier.size(MMSize.minTouch), contentAlignment = Alignment.Center) {
                if (isSelected) Icon(Icons.Rounded.Check, "Selected", tint = MM.colors.accent)
            }
        } else RowMenu(item, actions)
    }
}

@Composable
private fun RowMenu(item: WorkNoteItem, actions: NotesActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Actions for ${item.displayTitle}", tint = MM.colors.inkMuted) }
        DropdownMenu(open, { open = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.card) {
            @Composable fun entry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, run: () -> Unit) = DropdownMenuItem(
                text = { Text(label, style = MM.type.body, color = MM.colors.ink) },
                leadingIcon = { Icon(icon, null, tint = MM.colors.inkSecondary) },
                onClick = { open = false; run() }
            )
            entry(if (item.pinned) "Unpin" else "Pin", Icons.Rounded.PushPin) { actions.onPin(item) }
            entry("Move to project", Icons.Rounded.FolderOpen) { actions.onMove(item) }
            entry("Share", Icons.Rounded.Share) { actions.onShare(item) }
            entry("Delete", Icons.Rounded.Delete) { actions.onDelete(item) }
        }
    }
}

@Composable
private fun SortMenu(sort: WorkNoteSort, onSort: (WorkNoteSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort: ${sort.label}", tint = MM.colors.inkSecondary) }
        DropdownMenu(open, { open = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.card) {
            WorkNoteSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, style = if (s == sort) MM.type.bodyStrong else MM.type.body, color = MM.colors.ink) },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null, tint = MM.colors.accent) },
                    onClick = { open = false; onSort(s) }
                )
            }
        }
    }
}

@Composable
internal fun projectColor(p: ProjectCard): Color =
    p.notebook.colorHex?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() } ?: MM.colors.accent

/** Pick a project (or none). Used to move notes and to filter the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectPickerSheet(
    title: String, projects: List<ProjectCard>, selectedId: String?, noneLabel: String?,
    onPick: (String?) -> Unit, onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.padding(bottom = MM.space.l)) {
            Text(title, style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s))
            if (projects.isEmpty()) Text("No projects yet. Create one from the Projects tab.", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s))
            if (noneLabel != null) PickerRow(noneLabel, MM.colors.inkFaint, selectedId == null) { onPick(null) }
            projects.forEach { p -> PickerRow(p.notebook.name, projectColor(p), p.notebook.id == selectedId) { onPick(p.notebook.id) } }
        }
    }
}

@Composable
private fun PickerRow(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = MM.space.l)
            .padding(vertical = MM.space.s).semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(MM.space.m).background(color, MM.radius.pill))
        Text(label, style = if (selected) MM.type.bodyStrong else MM.type.body, color = MM.colors.ink, modifier = Modifier.weight(1f).padding(start = MM.space.m, top = MM.space.s, bottom = MM.space.s))
        if (selected) Icon(Icons.Rounded.Check, null, tint = MM.colors.accent)
    }
}

/** The contextual bar shown while notes are selected (long-press to start). */
@Composable
internal fun SelectionBar(count: Int, onMove: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel selection", tint = MM.colors.ink) }
        Text("$count selected", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.weight(1f))
        TextAction("Move", onMove)
        TextAction("Delete", onDelete)
    }
}
