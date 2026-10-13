package com.craftflowtechnologies.meetingmind.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.OfferedNotebookSpaces
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.notes.LibraryChip
import com.craftflowtechnologies.meetingmind.core.notes.LibraryItem
import com.craftflowtechnologies.meetingmind.core.notes.LibraryQuery
import com.craftflowtechnologies.meetingmind.core.notes.NoteLabels
import com.craftflowtechnologies.meetingmind.core.notes.NoteSort
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar
import com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.notes.editor.NotebookDot
import com.craftflowtechnologies.meetingmind.feature.work.EmptyCompanionSize
import com.craftflowtechnologies.meetingmind.feature.work.LOAD_AHEAD
import com.craftflowtechnologies.meetingmind.feature.work.QuietLink
import com.craftflowtechnologies.meetingmind.feature.work.SkeletonRow
import com.craftflowtechnologies.meetingmind.feature.work.WorkTextField
import com.craftflowtechnologies.meetingmind.feature.work.pagedFooter
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

/** Colours a notebook can take; picked in the notebook dialog. */
val NotebookColors = listOf("#6366F1", "#A855F7", "#EC4899", "#F43F5E", "#F59E0B", "#10B981", "#06B6D4", "#0F172A")

private const val SKELETON_ROWS = 6

/**
 * The Notes library (U-2). Search comes first, then one row of chips, then the notes, loaded a page at a time.
 * Long-press a note to select; Pin, Move and Delete then apply to all of them, each with Undo. Folders are a toggle
 * on this screen, not another one.
 *
 * [onOpenFaith] and [onOpenWork] are kept so existing call sites compile; the library no longer hosts navigation
 * cards (Faith and Work have their own homes). [onRecord] starts a recording from the empty state; without it the
 * empty state opens the Create sheet through the bottom bar.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpenNote: (String) -> Unit,
    onOpenNotebook: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    onNavigateBottomNav: (BottomNavDestination) -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenFaith: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onOpenWork: (() -> Unit)? = null,
    onOpenTrash: () -> Unit = {},
    onRecord: (() -> Unit)? = null
) {
    val library = viewModel.library
    val holder = viewModel<NotesStateHolder>(key = "notes-library-" + viewModel.scope.toString())
    LaunchedEffect(viewModel) { viewModel.attach(holder.handle) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val state by library.items.collectAsState()
    val query by library.query.collectAsState()
    val searchText by library.searchText.collectAsState()
    val view by library.view.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val counts by viewModel.notebookCounts.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    val trashCount by viewModel.trashCount.collectAsState()
    val trashedNotebooks by viewModel.trashedNotebooks.collectAsState()
    val currentNotebook by viewModel.currentNotebook.collectAsState()
    val aiJobs by viewModel.aiJobs.collectAsState()
    val appIdentity = com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity.current

    val isRoot = viewModel.scope == NotesScope.All
    val isArchive = viewModel.scope == NotesScope.Archived
    val isTrash = viewModel.scope == NotesScope.Trash
    val inNotebook = viewModel.scope is NotesScope.InNotebook
    val folders = isRoot && view == LibraryView.FOLDERS && !query.isSearching && searchText.isBlank()

    var showAiMenu by remember { mutableStateOf(false) }
    var showAsk by remember { mutableStateOf(false) }
    var aiSheetOpen by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var notebookDialog by remember { mutableStateOf<NotebookDialogState?>(null) }
    var notebookMenu by remember { mutableStateOf<Notebook?>(null) }
    var noteMenu by remember { mutableStateOf<LibraryItem?>(null) }
    var confirmDeleteNotebook by remember { mutableStateOf<Notebook?>(null) }
    var moreFilters by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    val homeMode = isRoot && !folders
    val showHero = homeMode && !query.isFiltered && !query.isSearching && searchText.isBlank()
    val latestMeeting = remember(state.items) {
        state.items.filter { it.hasRecording && Workflows.space(it.note.workflow) == NotebookSpace.WORK }
            .maxByOrNull { it.note.eventDate ?: it.note.createdAt }
    }
    val companionShown = rememberCompanionVisible(CompanionPage.HOME)
    val heroBlocks by viewModel.heroBlocks.collectAsState()
    LaunchedEffect(showHero, latestMeeting?.id) { viewModel.watchHero(if (showHero) latestMeeting?.id else null) }
    // Tiles come from the hero note's blocks, so they follow edits to that note.
    val heroTiles = remember(heroBlocks, latestMeeting) {
        latestMeeting?.let { meetingTiles(heroBlocks, aiAvailable = it.note.plainText.isNotBlank()) }.orEmpty()
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun offerUndo(message: String, undo: Undo) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo.run()
        }
    }

    var selectedIds by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    val selecting = selectedIds.isNotEmpty()
    val selectedItems = state.items.filter { it.id in selectedIds }
    fun toggleSelect(id: String) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    val title = when (viewModel.scope) {
        NotesScope.All -> "Notes"
        NotesScope.Archived -> "Archived"
        NotesScope.Trash -> "Trash"
        is NotesScope.InNotebook -> currentNotebook?.name ?: "Notebook"
    }
    val startRecording: () -> Unit = onRecord ?: { onNavigateBottomNav(BottomNavDestination.NEW) }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (state.items.isNotEmpty() && last >= state.items.size - LOAD_AHEAD) library.loadMore() }
    }

    Scaffold(
        containerColor = MM.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (isRoot) AppBottomNavigationBar(current = BottomNavDestination.NOTES, onNavigate = onNavigateBottomNav) },
        floatingActionButton = {
            if (inNotebook && !selecting) PrimaryButton("Write a note", { viewModel.createNote(onOpenNote) }, leadingIcon = Icons.Rounded.Add)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            // ------------------------------------------------------------ header, or the selection bar
            if (selecting) {
                SelectionBar(
                    count = selectedIds.size, inTrash = isTrash, allPinned = selectedItems.isNotEmpty() && selectedItems.all { it.note.pinned },
                    onClose = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = state.items.map { it.id }.toSet() },
                    onPin = { pin ->
                        val items = selectedItems; selectedIds = emptySet()
                        scope.launch { offerUndo(if (pin) "Pinned ${items.size}" else "Unpinned ${items.size}", viewModel.pinAll(items, pin)) }
                    },
                    onMove = { moveOpen = true },
                    onArchive = if (isArchive || isTrash) null else ({
                        val items = selectedItems; selectedIds = emptySet()
                        scope.launch { offerUndo("Archived ${items.size}", viewModel.archiveAll(items)) }
                    }),
                    onDelete = {
                        val items = selectedItems; selectedIds = emptySet()
                        scope.launch { offerUndo(if (items.size == 1) "Moved to Trash" else "${items.size} moved to Trash", viewModel.deleteAll(items)) }
                    },
                    onRestore = { viewModel.restoreAll(selectedItems); selectedIds = emptySet() }
                )
            } else if (!homeMode) {
                ScreenHeader(
                    title = title,
                    modifier = Modifier.padding(horizontal = MM.space.l),
                    leading = if (onNavigateBack != null) ({
                        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.ink) }
                    }) else null
                ) {
                    if (isTrash && (state.items.isNotEmpty() || trashedNotebooks.isNotEmpty())) TextAction("Empty", { confirmEmptyTrash = true })
                    if (isRoot) {
                        IconButton(onClick = { library.setView(if (view == LibraryView.FOLDERS) LibraryView.LIST else LibraryView.FOLDERS) }) {
                            Icon(
                                if (view == LibraryView.FOLDERS) Icons.Rounded.Notes else Icons.Rounded.FolderOpen,
                                if (view == LibraryView.FOLDERS) "Show notes" else "Show notebooks",
                                tint = MM.colors.inkSecondary
                            )
                        }
                    }
                    SortMenu(query.sort, library::setSort)
                    currentNotebook?.let { nb ->
                        IconButton(onClick = { showAiMenu = true }) { Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, "AI for this notebook", tint = MM.colors.inkSecondary) }
                        IconButton(onClick = { notebookMenu = nb }) { Icon(Icons.Rounded.MoreVert, "Notebook options", tint = MM.colors.inkSecondary) }
                    }
                    if (isRoot && (archivedCount > 0 || trashCount > 0)) {
                        LibraryMenu(archivedCount, trashCount, onOpenArchive, onOpenTrash)
                    }
                }
            }
            if (!homeMode) {
            // ------------------------------------------------------------ search first, then the chips
            WorkTextField(
                value = searchText, onValueChange = library::setSearch,
                placeholder = if (isTrash) "Search the Trash" else if (isArchive) "Search archived notes" else "Search notes",
                icon = Icons.Rounded.Search,
                modifier = Modifier.padding(horizontal = MM.space.l).testTag("notes_search"),
                clearLabel = "Clear search"
            )
            }
            if (!homeMode) {
                if (!isTrash && !isArchive && !folders) {
                    LibraryChips(
                        query = query, showWork = NotebookSpace.WORK in appIdentity.spaces, showFaith = appIdentity.showsFaith,
                        onAll = library::showAll, onChip = library::toggleChip, onMore = { moreFilters = true },
                        modifier = Modifier.padding(horizontal = MM.space.l)
                    )
                }
            }

            // ------------------------------------------------------------ the body
            if (folders) {
                NotebookList(
                    notebooks = notebooks, counts = counts, onOpen = onOpenNotebook, onMenu = { notebookMenu = it },
                    onNew = { notebookDialog = NotebookDialogState(null, "", NotebookSpace.PERSONAL, NotebookColors.first()) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                val now = remember(state.items) { System.currentTimeMillis() }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().testTag("notes_list"), state = listState,
                    contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, bottom = MM.space.xxl)
                ) {
                    if (homeMode) {
                        item(key = "home-top") {
                            NotesHomeTopBar(
                                onSearch = { searchOpen = !searchOpen },
                                onFolders = { library.setView(LibraryView.FOLDERS) },
                                archived = archivedCount, trash = trashCount, onArchive = onOpenArchive, onTrash = onOpenTrash
                            )
                        }
                        val hero = latestMeeting.takeIf { showHero }
                        if (hero != null && companionShown) item(key = "home-hero-block") {
                            // Zuri sits partly behind the hero card: one item, so the card can cover Zuri's lower body.
                            HomeHeroBlock(hero, heroTiles, onOpen = { onOpenNote(hero.id) }, onTile = { onOpenNote(hero.id) })
                        } else {
                            item(key = "home-greeting") {
                                NotesGreeting(hasLatest = hero != null, onBubbleClick = { hero?.let { onOpenNote(it.id) } })
                            }
                            if (hero != null) item(key = "home-hero") {
                                LatestMeetingCard(
                                    hero, heroTiles, onOpen = { onOpenNote(hero.id) }, onTile = { onOpenNote(hero.id) },
                                    modifier = Modifier.padding(top = MM.space.l)
                                )
                            }
                        }
                        if (searchOpen || searchText.isNotBlank()) item(key = "home-search") {
                            WorkTextField(
                                value = searchText, onValueChange = library::setSearch, placeholder = "Search notes",
                                icon = Icons.Rounded.Search, modifier = Modifier.padding(top = MM.space.m).testTag("notes_search"),
                                clearLabel = "Clear search"
                            )
                        }
                        item(key = "home-header") {
                            NotesSectionHeader(
                                extraFilters = query.types.size + if (query.tagId != null) 1 else 0,
                                sort = query.sort, onSort = library::setSort,
                                onFilters = { moreFilters = true }, onNew = { viewModel.createNote(onOpenNote) }
                            )
                        }
                        item(key = "home-chips") {
                            HomeChips(query, showWork = NotebookSpace.WORK in appIdentity.spaces, showFaith = appIdentity.showsFaith, onAll = library::showAll, onChip = library::toggleChip)
                        }
                    }
                    if (isTrash) {
                        item(key = "trash-info") {
                            Text(
                                "Notes and notebooks here are deleted for good after ${NoteRepository.TRASH_DAYS} days. Tap one to restore it.",
                                style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(vertical = MM.space.m)
                            )
                        }
                        items(trashedNotebooks, key = { "tnb-" + it.id }) { nb ->
                            ListRow(
                                nb.name, Modifier.combinedClickable(onClick = { notebookMenu = nb }),
                                meta = daysLeft(nb.deletedAt), leading = { NotebookDot(nb.colorHex, 12) }
                            )
                        }
                    }
                    when {
                        state.isLoadingFirst && state.items.isEmpty() -> items(SKELETON_ROWS, key = { "skeleton-$it" }) { SkeletonRow() }
                        state.error != null && state.items.isEmpty() -> item(key = "error") {
                            StatusLine(StatusKind.Error, "Couldn't load your notes.", Modifier.padding(top = MM.space.l), actionLabel = "Retry", onAction = library::retry)
                        }
                        state.items.isEmpty() -> item(key = "empty") {
                            EmptyLibrary(
                                query = query, isArchive = isArchive, isTrash = isTrash, searchText = searchText,
                                onSearchTranscripts = { library.setIncludeTranscripts(true) },
                                onClear = library::clearFilters,
                                onRecord = startRecording, onWrite = { viewModel.createNote(onOpenNote) }
                            )
                        }
                        else -> {
                            items(state.items, key = { it.id }) { item ->
                                LibraryRow(
                                    item = item, now = now, query = query, inTrash = isTrash, notebooks = notebooks,
                                    selecting = selecting, isSelected = item.id in selectedIds,
                                    onClick = { if (selecting) toggleSelect(item.id) else if (isTrash) noteMenu = item else onOpenNote(item.id) },
                                    onLongClick = { if (isTrash && !selecting) noteMenu = item else toggleSelect(item.id) },
                                    latestId = if (showHero) latestMeeting?.id else null,
                                    onTogglePin = { viewModel.togglePin(item.note) }
                                )
                            }
                            pagedFooter(state, if (query.isSearching) "That's every match" else "That's everything", library::retry)
                            if (query.isSearching && !query.includeTranscripts) item(key = "more-transcripts") {
                                QuietLink("Search in transcripts too", { library.setIncludeTranscripts(true) })
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- sheets and dialogs
    if (moreFilters) MoreFiltersSheet(
        query = query, tags = tags.map { it.id to it.name },
        onType = library::toggleType, onTag = library::setTag, onClear = library::showAll, onDismiss = { moreFilters = false }
    )
    if (moveOpen) MoveSheet(
        notebooks = notebooks,
        onPick = { nb ->
            val items = selectedItems; selectedIds = emptySet(); moveOpen = false
            scope.launch { offerUndo("Moved ${items.size} to ${nb.name}", viewModel.moveAll(items, nb.id)) }
        },
        onDismiss = { moveOpen = false }
    )
    notebookDialog?.let { s ->
        NotebookDialog(
            state = s,
            onSave = { saved ->
                if (saved.existing == null) viewModel.createNotebook(saved.name.trim(), saved.space, saved.color)
                else viewModel.updateNotebook(saved.existing.copy(name = saved.name.trim(), space = saved.space, colorHex = saved.color))
                notebookDialog = null
            },
            onDismiss = { notebookDialog = null }
        )
    }
    if (showAiMenu) com.craftflowtechnologies.meetingmind.feature.notes.ai.NoteAiMenu(
        forNotebook = true,
        onPick = { tool ->
            showAiMenu = false
            if (tool == com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTool.ASK) showAsk = true else { viewModel.runAi(tool); aiSheetOpen = true }
        },
        onRelated = null,
        onDismiss = { showAiMenu = false }
    )
    if (showAsk) com.craftflowtechnologies.meetingmind.feature.notes.ai.AskDialog(
        forNotebook = true,
        onAsk = { q -> showAsk = false; viewModel.runAi(com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTool.ASK, q); aiSheetOpen = true },
        onDismiss = { showAsk = false }
    )
    val aiJob = aiJobs.firstOrNull()
    if (aiJob != null && !aiSheetOpen && currentNotebook != null) {
        // A result finished while the sheet was closed: offer it again.
        LaunchedEffect(aiJob.id, aiJob.status) {
            if (aiJob.status == com.craftflowtechnologies.meetingmind.ai.notes.NoteAiStatus.SUCCEEDED) aiSheetOpen = true
        }
    }
    if (aiSheetOpen && aiJob != null) com.craftflowtechnologies.meetingmind.feature.notes.ai.NoteAiResultSheet(
        job = aiJob,
        primaryLabel = "Save as note",
        onPrimary = { items -> aiSheetOpen = false; viewModel.saveAiResult(aiJob, items, onOpenNote) },
        onApplyOrganized = null,
        onShowSource = { p -> p.noteId?.let { aiSheetOpen = false; onOpenNote(it) } },
        onCancel = { viewModel.cancelAi(aiJob.id) },
        onClose = { aiSheetOpen = false },
        onDiscard = { aiSheetOpen = false; viewModel.dismissAi(aiJob.id) }
    )
    notebookMenu?.let { nb ->
        AlertDialog(
            onDismissRequest = { notebookMenu = null },
            containerColor = MM.colors.surfaceRaised,
            title = { Text(nb.name, style = MM.type.heading, color = MM.colors.ink) },
            text = {
                if (isTrash) Column {
                    MenuText("Restore notebook") { notebookMenu = null; viewModel.restoreNotebook(nb.id) }
                    MenuText("Delete for good", destructive = true) { notebookMenu = null; viewModel.deleteNotebookForever(nb.id) }
                } else Column {
                    MenuText("Edit name, space and colour") { notebookMenu = null; notebookDialog = NotebookDialogState(nb, nb.name, nb.space, nb.colorHex ?: NotebookColors.first()) }
                    MenuText("Archive notebook") { notebookMenu = null; viewModel.archiveNotebook(nb.id); onNavigateBack?.takeIf { !isRoot }?.invoke() }
                    if (nb.id != com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase.DEFAULT_NOTEBOOK_ID) {
                        MenuText("Delete notebook", destructive = true) { notebookMenu = null; confirmDeleteNotebook = nb }
                    }
                }
            },
            confirmButton = { TextAction("Close", { notebookMenu = null }) }
        )
    }
    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            containerColor = MM.colors.surfaceRaised,
            title = { Text("Empty the Trash?", style = MM.type.heading, color = MM.colors.ink) },
            text = { Text("Everything in the Trash is deleted for good. Recordings are never deleted this way; they stay in your library.", style = MM.type.body, color = MM.colors.inkSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.emptyTrash(); confirmEmptyTrash = false }) { Text("Empty Trash", style = MM.type.bodyStrong, color = MM.colors.danger) } },
            dismissButton = { TextAction("Cancel", { confirmEmptyTrash = false }) }
        )
    }
    confirmDeleteNotebook?.let { nb ->
        AlertDialog(
            onDismissRequest = { confirmDeleteNotebook = null },
            containerColor = MM.colors.surfaceRaised,
            title = { Text("Delete “${nb.name}”?", style = MM.type.heading, color = MM.colors.ink) },
            text = { Text("The notebook moves to the Trash. Its notes are kept and stay in All notes; restoring the notebook gathers them back.", style = MM.type.body, color = MM.colors.inkSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteNotebook(nb.id); confirmDeleteNotebook = null; if (!isRoot) onNavigateBack?.invoke() }) {
                    Text("Delete", style = MM.type.bodyStrong, color = MM.colors.danger)
                }
            },
            dismissButton = { TextAction("Cancel", { confirmDeleteNotebook = null }) }
        )
    }
    noteMenu?.let { item ->
        AlertDialog(
            onDismissRequest = { noteMenu = null },
            containerColor = MM.colors.surfaceRaised,
            title = { Text(item.note.title.ifBlank { "Untitled note" }, style = MM.type.heading, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    MenuText("Restore") { viewModel.restore(item.note); noteMenu = null }
                    MenuText("Delete for good", destructive = true) { viewModel.deleteForever(item.note); noteMenu = null }
                }
            },
            confirmButton = { TextAction("Close", { noteMenu = null }) }
        )
    }
}

// ==================================================================== pieces

/** Everything is "All"; Work, Faith and Bible add up; Recordings and Pinned narrow; "More filters" holds types and tags. */
@Composable
private fun LibraryChips(
    query: LibraryQuery, showWork: Boolean, showFaith: Boolean,
    onAll: () -> Unit, onChip: (LibraryChip) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier
) {
    val extra = query.types.size + if (query.tagId != null) 1 else 0
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        MMChip("All", selected = query.chips.isEmpty() && extra == 0, onClick = onAll)
        LibraryChip.entries
            .filter { (it != LibraryChip.WORK || showWork) && ((it != LibraryChip.FAITH && it != LibraryChip.BIBLE) || showFaith) }
            .forEach { chip -> MMChip(chip.label, selected = chip in query.chips, onClick = { onChip(chip) }) }
        MMChip(if (extra > 0) "More filters · $extra" else "More filters", selected = extra > 0, onClick = onMore)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MoreFiltersSheet(
    query: LibraryQuery, tags: List<Pair<String, String>>,
    onType: (RecordingType) -> Unit, onTag: (String?) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.padding(horizontal = MM.space.l).padding(bottom = MM.space.xl), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("More filters", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
                if (query.types.isNotEmpty() || query.tagId != null) TextAction("Clear", onClear)
            }
            Text("Type", style = MM.type.caption, color = MM.colors.inkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                RecordingType.entries.filter { it != RecordingType.CUSTOM }.forEach { type ->
                    MMChip(type.displayName, selected = type in query.types, onClick = { onType(type) })
                }
            }
            if (tags.isNotEmpty()) {
                Text("Tag", style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.s))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    tags.forEach { (id, name) -> MMChip("#$name", selected = query.tagId == id, onClick = { onTag(if (query.tagId == id) null else id) }) }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { PrimaryButton("Done", onDismiss) }
        }
    }
}

@Composable
internal fun SortMenu(sort: NoteSort, onSort: (NoteSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort: ${sort.label}", tint = MM.colors.inkSecondary) }
        DropdownMenu(open, { open = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.card) {
            NoteSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, style = if (s == sort) MM.type.bodyStrong else MM.type.body, color = MM.colors.ink) },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null, tint = MM.colors.accent) },
                    onClick = { open = false; onSort(s) }
                )
            }
        }
    }
}

/** Archived and Trash are one tap away, not rows at the bottom of a long list. */
@Composable
internal fun LibraryMenu(archived: Int, trash: Int, onArchive: () -> Unit, onTrash: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = MM.colors.inkSecondary) }
        DropdownMenu(open, { open = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.card) {
            if (archived > 0) DropdownMenuItem(text = { Text("Archived · $archived", style = MM.type.body, color = MM.colors.ink) }, onClick = { open = false; onArchive() })
            if (trash > 0) DropdownMenuItem(text = { Text("Trash · $trash", style = MM.type.body, color = MM.colors.ink) }, onClick = { open = false; onTrash() })
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int, inTrash: Boolean, allPinned: Boolean,
    onClose: () -> Unit, onSelectAll: () -> Unit, onPin: (Boolean) -> Unit, onMove: () -> Unit,
    onArchive: (() -> Unit)?, onDelete: () -> Unit, onRestore: () -> Unit
) {
    Column(Modifier.padding(horizontal = MM.space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel selection", tint = MM.colors.ink) }
            Text("$count selected", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            TextAction("All", onSelectAll)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            if (inTrash) TextAction("Restore", onRestore) else {
                TextAction(if (allPinned) "Unpin" else "Pin", { onPin(!allPinned) })
                TextAction("Move", onMove)
                if (onArchive != null) TextAction("Archive", onArchive)
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = MMSize.minTouch), shape = MM.radius.pill) {
                    Text("Delete", style = MM.type.bodyStrong, color = MM.colors.danger)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    item: LibraryItem, now: Long, query: LibraryQuery, inTrash: Boolean, notebooks: List<Notebook>,
    selecting: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, latestId: String?, onTogglePin: () -> Unit
) {
    val note = item.note
    val c = MM.colors
    val marker = when {
        Workflows.space(note.workflow) == NotebookSpace.WORK -> c.accent
        Workflows.space(note.workflow) == NotebookSpace.FAITH -> c.gold
        else -> notebooks.firstOrNull { it.id == note.notebookId }?.colorHex
            ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() } ?: c.inkFaint
    }
    val at = if (query.sort == NoteSort.CREATED) note.eventDate ?: note.createdAt else note.updatedAt
    val model = NoteRowModel(
        title = note.title.ifBlank { "Untitled note" },
        preview = item.preview,
        spaceColor = marker,
        hasRecording = item.hasRecording,
        transcriptStatus = item.transcript,
        timeLabel = if (inTrash) daysLeft(note.deletedAt) else NoteLabels.relativeDate(at, now),
        pinned = note.pinned,
        isPrivate = note.isPrivate,
        typeIcon = typeIcon(note.workflow),
        typeLabel = note.workflow.displayName.takeIf { note.workflow != RecordingType.GENERAL }.orEmpty(),
        durationLabel = item.durationMs?.let(NoteLabels::duration).orEmpty(),
        highlight = item.highlight
    )
    Row(
        Modifier.fillMaxWidth().background(if (isSelected) c.accentWash else Color.Transparent, MM.radius.small)
            .padding(horizontal = MM.space.xs).semantics { selected = isSelected },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Icon(
                if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) c.accent else c.inkFaint, modifier = Modifier.padding(end = MM.space.s).size(MMSize.icon)
            )
        }
        if (query.isSearching) {
            NoteRow(model, Modifier.weight(1f).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick))
        } else {
            NoteCard(
                model, tile = marker, isLatest = item.id == latestId, selected = isSelected,
                showPin = !inTrash && !selecting, onTogglePin = onTogglePin,
                onClick = onClick, onLongClick = onLongClick, modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun typeIcon(type: RecordingType): ImageVector = when (type) {
    RecordingType.BIBLE_STUDY -> Icons.Rounded.AutoStories
    RecordingType.SERMON -> Icons.Rounded.RecordVoiceOver
    RecordingType.PRAYER, RecordingType.PRAYER_REQUEST, RecordingType.GRATITUDE -> Icons.Rounded.Favorite
    RecordingType.DEVOTIONAL, RecordingType.REFLECTION, RecordingType.TESTIMONY, RecordingType.JOURNAL -> Icons.Rounded.Book
    RecordingType.IDEA, RecordingType.BRAINSTORM -> Icons.Rounded.Lightbulb
    RecordingType.VOICE_MEMO, RecordingType.DICTATION -> Icons.Rounded.Mic
    RecordingType.LECTURE, RecordingType.RESEARCH -> Icons.Rounded.School
    RecordingType.PROJECT_BRIEF, RecordingType.DECISION_RECORD, RecordingType.WEEKLY_REVIEW -> Icons.Rounded.Description
    else -> if (Workflows.space(type) == NotebookSpace.WORK) Icons.Rounded.Groups else Icons.Rounded.Notes
}

/** Empty is never one thing: no notes yet, nothing found, a filter too narrow, an empty archive or Trash. */
@Composable
private fun EmptyLibrary(
    query: LibraryQuery, isArchive: Boolean, isTrash: Boolean, searchText: String,
    onSearchTranscripts: () -> Unit, onClear: () -> Unit, onRecord: () -> Unit, onWrite: () -> Unit
) {
    when {
        isTrash -> EmptyState("The Trash is empty", "Deleted notes wait here for ${NoteRepository.TRASH_DAYS} days, so a delete can always be undone.")
        isArchive && !query.isFiltered -> EmptyState("Nothing archived", "Archived notes wait here, out of the way, until you restore them.")
        query.isSearching -> EmptyState(
            title = "Nothing matches “${searchText.trim()}”",
            body = if (query.includeTranscripts) "Not in your notes or their transcripts. Try another word." else "Not in your notes. Recording transcripts aren't searched unless you ask.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    if (!query.includeTranscripts) SecondaryButton("Search in transcripts too", onSearchTranscripts)
                    TextAction("Clear", onClear)
                }
            }
        )
        query.isFiltered -> EmptyState("No notes match these filters", "Try fewer filters.", action = { SecondaryButton("Show all notes", onClear) })
        else -> EmptyState(
            illustration = { ZuriSlot(CompanionPage.EMPTY, EmptyCompanionSize) },
            title = "No notes yet — record or write one",
            body = "Every recording gets a note of its own, with the summary and what was said.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton("Record", onRecord, leadingIcon = Icons.Rounded.Mic)
                    SecondaryButton("Write a note", onWrite)
                }
            }
        )
    }
}

/** Notebooks as folders: the same screen, the other way of looking. Tap to open, long-press for options. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookList(
    notebooks: List<Notebook>, counts: Map<String, Int>, onOpen: (String) -> Unit, onMenu: (Notebook) -> Unit,
    onNew: () -> Unit, modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxWidth().testTag("notes_folders"), contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s)) {
        items(notebooks, key = { it.id }) { nb ->
            val n = counts[nb.id] ?: 0
            ListRow(
                nb.name,
                Modifier.combinedClickable(role = Role.Button, onClick = { onOpen(nb.id) }, onLongClick = { onMenu(nb) }),
                subtitle = nb.space.displayName, meta = if (n == 1) "1 note" else "$n notes",
                leading = { NotebookDot(nb.colorHex, 16) }
            )
        }
        item(key = "new-notebook") {
            Box(Modifier.padding(top = MM.space.m)) { SecondaryButton("New notebook", onNew, leadingIcon = Icons.Rounded.Add) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(notebooks: List<Notebook>, onPick: (Notebook) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.padding(bottom = MM.space.l)) {
            Text("Move to", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s))
            notebooks.forEach { nb ->
                ListRow(nb.name, Modifier.padding(horizontal = MM.space.l), leading = { NotebookDot(nb.colorHex, 12) }, onClick = { onPick(nb) })
            }
        }
    }
}

private data class NotebookDialogState(val existing: Notebook?, val name: String, val space: NotebookSpace, val color: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookDialog(state: NotebookDialogState, onSave: (NotebookDialogState) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(state) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MM.colors.surfaceRaised,
        title = { Text(if (state.existing == null) "New notebook" else "Edit notebook", style = MM.type.heading, color = MM.colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                OutlinedTextField(value = s.name, onValueChange = { s = s.copy(name = it.replace("\n", "")) }, singleLine = true, placeholder = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Text("Space", style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.s))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    OfferedNotebookSpaces.forEach { sp -> MMChip(sp.displayName, selected = s.space == sp, onClick = { s = s.copy(space = sp) }) }
                }
                Text("Colour", style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = MM.space.s))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    NotebookColors.forEach { hex ->
                        Box(
                            Modifier.size(MMSize.minTouch).combinedClickable(role = Role.RadioButton, onClick = { s = s.copy(color = hex) }),
                            contentAlignment = Alignment.Center
                        ) {
                            NotebookDot(hex, 28)
                            if (s.color == hex) Icon(Icons.Rounded.Check, "Selected", tint = MM.colors.onAccent, modifier = Modifier.size(MMSize.iconSmall))
                        }
                    }
                }
            }
        },
        confirmButton = { TextAction("Save", { onSave(s) }, enabled = s.name.isNotBlank()) },
        dismissButton = { TextAction("Cancel", onDismiss) }
    )
}

@Composable
private fun MenuText(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        label, style = MM.type.body, color = if (destructive) MM.colors.danger else MM.colors.ink,
        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable(role = Role.Button, onClick = onClick).padding(vertical = MM.space.m, horizontal = MM.space.xs)
    )
}

/** "12 days left" for something deleted at [deletedAt]. */
internal fun daysLeft(deletedAt: Long?, now: Long = System.currentTimeMillis()): String {
    if (deletedAt == null) return ""
    val purgeAt = deletedAt + NoteRepository.TRASH_DAYS * 24L * 60 * 60 * 1000
    val days = ((purgeAt - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
    return when (days) { 0L -> "deleted for good today"; 1L -> "1 day left"; else -> "$days days left" }
}
