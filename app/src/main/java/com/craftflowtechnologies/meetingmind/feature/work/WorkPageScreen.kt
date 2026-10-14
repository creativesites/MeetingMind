package com.craftflowtechnologies.meetingmind.feature.work

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.calendar.UpNext
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.timeline.DeepTarget
import com.craftflowtechnologies.meetingmind.core.timeline.ItemKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.work.BriefTarget
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.TabSlot
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.feature.today.UpNextTile
import com.craftflowtechnologies.meetingmind.feature.today.timeFormat
import com.craftflowtechnologies.meetingmind.feature.today.timeLabel
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Work page (WORK_UX §2): a light header, four sticky segments (Today, Notes, Tasks, Projects) and, under
 * them, what the person came for. It replaces the old Work space and the Work content of the professional home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPageScreen(
    viewModel: WorkViewModel,
    page: WorkPageViewModel,
    today: TodayViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onRecordType: (RecordingType) -> Unit,
    onRecordEvent: (noteId: String, type: RecordingType, title: String, speakers: Int?) -> Unit,
    onOpenWrapUp: (String, Boolean) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenAll: (WorkTab) -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onOpenBrief: (BriefTarget) -> Unit = {},
    onOpenInbox: () -> Unit = {},
    onOpenWeeklyReview: () -> Unit = {},
    onOpenPerson: (String) -> Unit = {},
    /** Set when Work is the bar's fourth slot: the page then carries the bar, with Work current. */
    onNavigateBottomNav: ((com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit)? = null
) {
    val segment by page.segment.collectAsState()
    val query by page.query.collectAsState()
    val notes by page.notes.collectAsState()
    val tasks by page.tasks.collectAsState()
    val waiting by page.waiting.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val myTasks by viewModel.myTasks.collectAsState()
    val toReview by viewModel.toReview.collectAsState()
    val followUps by viewModel.followUps.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val recentNotes by viewModel.workNotes.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val inboxCount by viewModel.inboxCount.collectAsState()
    val pulse by viewModel.pulse.collectAsState()
    val upNext by today.upNext.collectAsState()
    val now by today.now.collectAsState()
    val todayItems by today.today.collectAsState()
    val terms = settings.terms
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recordPicker by remember { mutableStateOf(false) }
    var writePicker by remember { mutableStateOf(false) }
    var newProject by remember { mutableStateOf(false) }
    var filterPicker by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<List<WorkNoteItem>>(emptyList()) }
    var preparing by remember { mutableStateOf<PrepTarget?>(null) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    var savedViews by remember { mutableStateOf(false) }
    var savedFilter by remember { mutableStateOf<com.craftflowtechnologies.meetingmind.core.work.SavedFilter?>(null) }
    var selectedIds by rememberSaveable(stateSaver = listSaver<Set<String>, String>({ it.toList() }, { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    val snackbar = remember { SnackbarHostState() }

    // Each segment keeps its own scroll position through navigation and rotation.
    val todayList = rememberLazyListState()
    val notesList = rememberLazyListState()
    val tasksList = rememberLazyListState()
    val projectsList = rememberLazyListState()

    // A Prep notification opens the sheet here, for the event it named.
    val pendingPrep by com.craftflowtechnologies.meetingmind.core.work.PendingPrepare.event.collectAsState()
    pendingPrep?.let { e -> PrepareSheet(PrepTarget.Event(e), viewModel, onOpenMeeting, onDismiss = { com.craftflowtechnologies.meetingmind.core.work.PendingPrepare.event.value = null }) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { page.onResume(); today.tick() }
    // Pulse reads today's calendar to say what is still open from last time.
    LaunchedEffect(todayItems, now / 60_000) {
        viewModel.setPulseEvents(todayItems.mapNotNull { (it.target as? DeepTarget.Event)?.event }.filter { it.end >= now && !it.allDay }.map(::pulseEventOf))
    }
    LaunchedEffect(Unit) {
        page.events.collect { e ->
            when (e) {
                is WorkPageEvent.Deleted -> {
                    val n = e.notes.size
                    val r = snackbar.showSnackbar(if (n == 1) "Moved to Trash" else "Moved $n notes to Trash", actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) page.undoDelete(e.notes)
                }
                is WorkPageEvent.Failed -> snackbar.showSnackbar("Couldn't ${e.what}.", duration = SnackbarDuration.Short)
            }
        }
    }

    // ---------------------------------------------------------------- today's figures
    val endOfToday = DueDates.startOfDay(now) + 86_400_000L
    val meetings = remember(todayItems) { todayItems.filter { it.kind == ItemKind.EVENT && !it.allDay && it.target is DeepTarget.Event } }
    val needs = remember(myTasks, toReview, followUps, titles, now / 60_000) {
        buildList {
            val due = myTasks.filter { it.dueAt != null && it.dueAt < endOfToday }.sortedBy { it.dueAt }
            val (late, soon) = due.partition { it.isOverdue(now) }
            fun task(t: WorkTask) = NeedsItem("t-${t.id}", t.title, listOfNotNull(dueLabel(t.dueAt, true, now)?.first, t.meetingId?.let { titles[it] }).joinToString(" · "), "Done") { viewModel.toggle(t) }
            late.forEach { add(task(it)) }
            toReview.forEach { r -> add(NeedsItem("r-${r.meetingId}", r.title, "${r.type.displayName} · ${dayLabel(r.at, now)} · ready to wrap up", "Review") { onOpenWrapUp(r.meetingId, false) }) }
            followUps.forEach { r -> add(NeedsItem("f-${r.meetingId}", r.title, "${r.type.displayName} · ${dayLabel(r.at, now)} · follow-up not sent", "Send") { onOpenWrapUp(r.meetingId, true) }) }
            soon.forEach { add(task(it)) }
        }
    }

    // Open on Today when there are meetings or things that need the person; otherwise on Notes. Waits briefly for the data.
    val liveMeetings by rememberUpdatedState(meetings.size)
    val liveNeeds by rememberUpdatedState(needs.size)
    LaunchedEffect(segment) {
        if (segment == null) {
            val hit = withTimeoutOrNull(DEFAULT_WAIT_MS) { snapshotFlow { liveMeetings to liveNeeds }.first { it.first > 0 || it.second > 0 } }
            page.resolveDefault(hit?.first ?: liveMeetings, hit?.second ?: liveNeeds)
        }
    }

    val fmt = timeFormat()
    val projectColors = projects.associate { it.notebook.id to projectColor(it) }
    val fallbackColor = MM.colors.inkFaint
    val todayModel = run {
        val heroEvent = (upNext as? UpNextTile.Event)
        val hero: TodayHero? = when {
            heroEvent != null -> {
                val e = heroEvent.event
                val line = pulse.today.firstOrNull { it.event.key == e.key }
                val last = heroEvent.prep?.lastTitle?.let { "Last time: $it" } ?: line?.withLabel?.let { "Last time: with $it" }
                val open = line?.open?.takeIf { it > 0 }?.let { "$it open ${if (it == 1) "item" else "items"}" }
                val lastTime = listOfNotNull(last, open).joinToString(" · ").ifEmpty { if (line?.firstMeeting == true) "Nothing on record with them yet" else null }
                val others = e.otherPeople
                TodayHero.Meeting(
                    whenLabel = UpNext.whenLabel(e, now, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)), title = e.title,
                    withLine = if (others.isEmpty()) null else "With " + others.take(3).joinToString(", ") + if (others.size > 3) " +${others.size - 3}" else "",
                    lastTime = lastTime,
                    onPrepare = { preparing = PrepTarget.Event(pulseEventOf(e)) },
                    onRecord = { today.noteForEvent(e) { id, type -> onRecordEvent(id, type, e.title, UpNext.speakerCount(e)) } }
                )
            }
            needs.isNotEmpty() -> TodayHero.Attention(needs.first())
            else -> null
        }
        fun row(item: com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem): ScheduleRow {
            val e = (item.target as DeepTarget.Event).event
            return ScheduleRow(item.id, item.title, e.otherPeople.take(3).joinToString(", ").ifEmpty { e.location }, timeLabel(item, fmt)) { today.noteForEvent(e) { id, _ -> onOpenNote(id) } }
        }
        TodayModel(
            hero = hero,
            needs = if (hero is TodayHero.Attention) needs.drop(1) else needs,
            needsTotal = needs.size,
            upcoming = meetings.filter { (it.end ?: it.start) >= now }.map(::row),
            earlier = meetings.filter { (it.end ?: it.start) < now }.map(::row),
            recent = recentNotes.take(RECENT_NOTES).map { n ->
                RecentNote(n.id, NoteRowModel(
                    title = n.title.ifBlank { n.workflow.displayName }, preview = previewOf(n.plainText), spaceColor = projectColors[n.notebookId] ?: fallbackColor,
                    timeLabel = noteTimeLabel(n.eventDate ?: n.updatedAt, now), pinned = n.pinned, isPrivate = n.isPrivate
                )) { onOpenNote(n.id) }
            }
        )
    }

    // ---------------------------------------------------------------- actions
    val selecting = selectedIds.isNotEmpty()
    BackHandler(enabled = selecting) { selectedIds = emptySet() }
    fun share(item: WorkNoteItem) = scope.launch {
        val text = page.shareText(item)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, item.displayTitle).putExtra(Intent.EXTRA_TEXT, text), null))
    }
    val notesActions = remember(page) {
        NotesActions(
            onSearch = page::setSearch, onToggleChip = page::toggleChip, onPickProject = { filterPicker = true }, onSort = page::setSort,
            onOpen = { onOpenNote(it.id) }, onSelect = { n -> selectedIds = if (n.id in selectedIds) selectedIds - n.id else selectedIds + n.id },
            onPin = { page.togglePin(it) }, onMove = { moving = listOf(it) }, onShare = { share(it) }, onDelete = { page.delete(listOf(it)) },
            onLoadMore = page::loadMoreNotes, onRetry = page::retryNotes, onRefresh = page::refreshNotes, onClearFilters = page::clearFilters,
            onRecord = { recordPicker = true }, onWriteNote = { writePicker = true }
        )
    }
    val tasksActions = remember(page, titles) {
        TasksActions(
            onWaiting = page::setWaiting, onAdd = { page.addTask(it) }, onDone = { page.completeTask(it) }, onEdit = { editing = it.task },
            onOpenSource = { r -> r.task.meetingId?.let { onOpenMeeting(it, r.task.startMs) } }, onNudge = { nudging = it.task },
            onLoadMore = page::loadMoreTasks, onRetry = page::retryTasks, onRefresh = page::refreshTasks
        )
    }
    val selectedNotes = notes.items.filter { it.id in selectedIds }

    val addActions = listOf(
        WorkMenuAction("Record meeting") { recordPicker = true },
        WorkMenuAction("Write note") { writePicker = true },
        WorkMenuAction("Add task") { page.select(WorkSegment.TASKS); page.setWaiting(false) },
        WorkMenuAction("Import", onClick = onImport)
    )
    val moreActions = listOfNotNull(
        WorkMenuAction("Inbox", inboxCount, onOpenInbox),
        WorkMenuAction("Weekly review", onClick = onOpenWeeklyReview),
        WorkMenuAction("Brief") { onOpenBrief(BriefTarget.weekly) },
        WorkMenuAction("People & ${terms.organisations.lowercase()}") { onOpenAll(WorkTab.PEOPLE) },
        WorkMenuAction("Decision log") { onOpenAll(WorkTab.DECISIONS) },
        WorkMenuAction("Open questions") { onOpenAll(WorkTab.QUESTIONS) },
        WorkMenuAction("Risks") { onOpenAll(WorkTab.RISKS) },
        if (com.craftflowtechnologies.meetingmind.BuildConfig.FEATURE_SAVED_VIEWS) WorkMenuAction("Saved views") { savedViews = true } else null,
        WorkMenuAction("Work settings", onClick = onOpenSettings)
    )

    Scaffold(
        containerColor = MM.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (onNavigateBottomNav != null && settings.tabSlot == TabSlot.WORK) {
                com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                    current = com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SEARCH, onNavigate = onNavigateBottomNav
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().testTag("work_page")) {
            Column(Modifier.padding(horizontal = MM.space.l)) {
                if (selecting) {
                    SelectionBar(selectedIds.size, onMove = { moving = selectedNotes }, onDelete = { page.delete(selectedNotes); selectedIds = emptySet() }, onClose = { selectedIds = emptySet() })
                } else {
                    WorkPageHeader(
                        contextLine = contextLine(now, meetings.size, needs.size), addActions = addActions, moreActions = moreActions,
                        onSearch = onSearch, onBack = if (onNavigateBottomNav == null) onNavigateBack else null
                    )
                }
                SegmentedControl(WorkSegment.entries.map { it.label }, segment?.ordinal ?: -1, { page.select(WorkSegment.entries[it]) })
            }
            Box(Modifier.weight(1f)) {
                when (segment) {
                    null -> Unit
                    WorkSegment.TODAY -> TodayContent(
                        todayModel, todayList, onAllNotes = { page.select(WorkSegment.NOTES) },
                        onRecord = { recordPicker = true }, onWriteNote = { writePicker = true }
                    )
                    WorkSegment.NOTES -> NotesContent(query, notes, now, projects, selectedIds, notesList, notesActions)
                    WorkSegment.TASKS -> TasksContent(waiting, tasks, now, titles, tasksList, tasksActions)
                    WorkSegment.PROJECTS -> ProjectsContent(
                        projects, terms.project, terms.projects, now, projectsList,
                        onOpen = { onOpenProject(it.notebook.id) }, onNew = { newProject = true }
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- sheets
    if (recordPicker) RecordTypeSheet(
        startsFor(settings.profile).filter { Workflows.isRecordable(it.type) },
        onPick = { recordPicker = false; onRecordType(it) }, onDismiss = { recordPicker = false }
    )
    if (writePicker) RecordTypeSheet(
        startsFor(settings.profile), title = "What are you writing?",
        onPick = { writePicker = false; viewModel.newNote(it, null, onOpenNote) }, onDismiss = { writePicker = false }
    )
    if (newProject) NewProjectSheet(terms.project, terms.organisation, settings.keepOnDevice, onDismiss = { newProject = false }) { name, org, conf ->
        newProject = false; viewModel.newProject(name, org, conf, onOpenProject)
    }
    if (filterPicker) ProjectPickerSheet(
        "Filter by ${terms.project.lowercase()}", projects, query.notebookId, noneLabel = "All ${terms.projects.lowercase()}",
        onPick = { filterPicker = false; page.setProject(it) }, onDismiss = { filterPicker = false }
    )
    if (moving.isNotEmpty()) ProjectPickerSheet(
        "Move to ${terms.project.lowercase()}", projects, moving.singleOrNull()?.notebookId, noneLabel = "No ${terms.project.lowercase()}",
        onPick = { id -> moving.forEach { page.move(it, id) }; moving = emptyList(); selectedIds = emptySet() }, onDismiss = { moving = emptyList() }
    )
    preparing?.let { PrepareSheet(it, viewModel, onOpenMeeting, onDismiss = { preparing = null }) }
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { t ->
        WorkTaskSheet(t, viewModel, onDismiss = { editing = null; page.refreshTasks() },
            onOpenSource = { t.meetingId?.let { m -> editing = null; onOpenMeeting(m, t.startMs) } ?: t.noteId?.let { n -> editing = null; onOpenNote(n) } })
    }
    if (savedViews) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { savedViews = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.padding(bottom = MM.space.l)) { SavedFilterRow { savedViews = false; savedFilter = it } }
    }
    savedFilter?.let { f ->
        val answer by androidx.compose.runtime.produceState<com.craftflowtechnologies.meetingmind.core.work.SavedAnswer?>(null, f) { value = viewModel.savedViews.answer(f) }
        SavedAnswerSheet(answer, f.label, viewModel, onOpenMeeting, onOpenPerson, onDismiss = { savedFilter = null })
    }
}

private const val DEFAULT_WAIT_MS = 400L
private const val RECENT_NOTES = 5

private fun pulseEventOf(e: CalendarEvent) =
    PulseEvent(e.key, e.title, e.begin, e.end, e.otherPeople, e.attendees.filter { a -> !a.isSelf }.mapNotNull { a -> a.email })
