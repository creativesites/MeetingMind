package com.craftflowtechnologies.meetingmind.feature.work

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.craftflowtechnologies.meetingmind.core.ui.work.AttentionTone
import com.craftflowtechnologies.meetingmind.core.ui.work.RibbonBlock
import com.craftflowtechnologies.meetingmind.core.ui.work.VerticalBar
import com.craftflowtechnologies.meetingmind.core.ui.work.VerticalBarItem
import com.craftflowtechnologies.meetingmind.core.work.AttentionAction
import com.craftflowtechnologies.meetingmind.core.work.AttentionKind
import com.craftflowtechnologies.meetingmind.core.work.AttentionRow as PulseAttention
import com.craftflowtechnologies.meetingmind.core.work.BriefTarget
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.feature.today.UpNextTile
import com.craftflowtechnologies.meetingmind.feature.today.timeFormat
import com.craftflowtechnologies.meetingmind.feature.work.hub.NeedCandidate
import com.craftflowtechnologies.meetingmind.feature.work.hub.NeedRank
import com.craftflowtechnologies.meetingmind.feature.work.hub.WorkSearchPane
import com.craftflowtechnologies.meetingmind.feature.work.hub.rankNeeds
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The Work vertical (design/work-vertical-design-spec.md §R2, §5.1, §6). It opens on Pulse, the follow-through for
 * today. The in-vertical bottom bar lists the panes (Pulse, Tasks, Notes, Projects, Decisions) with Home first. The
 * global app bar is not drawn here. The header carries Work search, the Inbox (its only door besides the Pulse row),
 * ＋ and ⋯.
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
    onOpenProcessing: (String) -> Unit = {},
    onOpenCircle: (String) -> Unit = {},
    onOpenCircles: () -> Unit = {},
    onStartCircle: () -> Unit = {},
    onOpenPaywall: (com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger) -> Unit = {},
    /** No longer drawn inside Work (the in-vertical bar replaces it). Kept so existing callers still compile. */
    @Suppress("UNUSED_PARAMETER") onNavigateBottomNav: ((com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit)? = null,
    /** Home in the in-vertical bar. Defaults to [onNavigateBack], which is where Work was opened from. */
    onHome: (() -> Unit)? = null,
    /** Opens the full task page in Tasks. Null keeps the in-place task editor. */
    onOpenTaskPage: ((String) -> Unit)? = null,
    /** Opens a Tasks list (a project's tasks) in Tasks. Null opens the project page instead. */
    onOpenTaskList: ((String) -> Unit)? = null
) {
    val segment by page.segment.collectAsState()
    val query by page.query.collectAsState()
    val notes by page.notes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val toReview by viewModel.toReview.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val inboxCount by viewModel.inboxCount.collectAsState()
    val pulse by viewModel.pulse.collectAsState()
    val self by viewModel.self.collectAsState()
    val upNext by today.upNext.collectAsState()
    val now by today.now.collectAsState()
    val todayItems by today.today.collectAsState()
    val myTasks by viewModel.myTasks.collectAsState()
    val waitingOn by viewModel.waitingOn.collectAsState()
    val followUps by viewModel.followUps.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val people by viewModel.everyone.collectAsState()
    val recentRows by viewModel.recentWork.collectAsState()
    val terms = settings.terms
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recordPicker by remember { mutableStateOf(false) }
    var writePicker by remember { mutableStateOf(false) }
    var showAutopilotSheet by remember { mutableStateOf(false) }
    var showWhatWork by remember { mutableStateOf(false) }
    var newProject by remember { mutableStateOf(false) }
    var filterPicker by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<List<WorkNoteItem>>(emptyList()) }
    var preparing by remember { mutableStateOf<PrepTarget?>(null) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    var asking by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable(stateSaver = listSaver<Set<String>, String>({ it.toList() }, { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    val snackbar = remember { SnackbarHostState() }

    val notesList = rememberLazyListState()
    val pulseList = rememberLazyListState()

    // A Prep notification opens the sheet here, for the event it named.
    val pendingPrep by com.craftflowtechnologies.meetingmind.core.work.PendingPrepare.event.collectAsState()
    pendingPrep?.let { e -> PrepareSheet(PrepTarget.Event(e), viewModel, onOpenMeeting, onDismiss = { com.craftflowtechnologies.meetingmind.core.work.PendingPrepare.event.value = null }) }

    // Another screen (Home's Task button) can ask for a pane; it is taken once and then cleared.
    val requestedSegment by PendingWorkSegment.request.collectAsState()
    LaunchedEffect(requestedSegment) { requestedSegment?.let { page.select(it); PendingWorkSegment.request.value = null } }

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

    // ---------------------------------------------------------------- today
    val startOfToday = DueDates.startOfDay(now)
    val meetings = remember(todayItems) { todayItems.filter { it.kind == ItemKind.EVENT && !it.allDay && it.target is DeepTarget.Event } }
    val calEvents = remember(meetings) { meetings.mapNotNull { (it.target as? DeepTarget.Event)?.event } }
    val fmt = timeFormat()
    val heroEvent = upNext as? UpNextTile.Event
    val nextEvent: CalendarEvent? = heroEvent?.event ?: calEvents.filter { it.end >= now }.minByOrNull { it.begin }

    fun openTask(id: String) {
        onOpenTaskPage?.invoke(id) ?: viewModel.tasks.value.firstOrNull { it.id == id }?.let { editing = it }
    }
    fun openEntity(row: PulseAttention) {
        val id = row.entityId ?: return
        when (row.entityType) {
            ContextType.PROJECT -> onOpenProject(id)
            ContextType.PERSON, ContextType.ORG -> onOpenPerson(id)
            null -> Unit
        }
    }
    fun openAttention(row: PulseAttention) {
        val meeting = row.meetingId
        when {
            meeting != null -> onOpenMeeting(meeting, row.startMs)
            row.entityId != null -> openEntity(row)
            else -> page.select(WorkSegment.TASKS)
        }
    }

    fun openRecent(row: WorkRecent.Row) {
        when (WorkRecent.targetOf(row)) {
            WorkRecent.Target.WRAP_UP -> onOpenWrapUp(row.meetingId, false)
            WorkRecent.Target.PROCESSING -> onOpenProcessing(row.meetingId)
        }
    }

    // ---------------------------------------------------------------- now: the next meeting, or a free day
    val upcoming = calEvents.filter { it.end >= now }.sortedBy { it.begin }
    val workNow: WorkNow = nextEvent?.let { e ->
        val others = e.otherPeople
        val then = upcoming.firstOrNull { it.key != e.key && it.begin >= e.begin }
        WorkNow.Next(
            whenLabel = UpNext.whenLabel(e, now, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)),
            title = e.title.ifBlank { "Untitled" },
            withLine = if (others.isNotEmpty()) {
                "With " + others.take(3).joinToString(", ") + (if (others.size > 3) " +${others.size - 3}" else "")
            } else {
                e.location?.takeIf { it.isNotBlank() }
            },
            blocks = calEvents.map { ev ->
                RibbonBlock(
                    start = localTimeOf(ev.begin), end = localTimeOf(ev.end), title = ev.title,
                    onClick = { today.noteForEvent(ev) { id, _ -> onOpenNote(id) } }, past = ev.end < now
                )
            },
            now = localTimeOf(now),
            thenLine = then?.let { "Then: ${it.title.ifBlank { "Untitled" }} · ${fmt.format(Date(it.begin))}" },
            onRecord = { today.noteForEvent(e) { id, type -> onRecordEvent(id, type, e.title, UpNext.speakerCount(e)) } },
            onPrepare = { preparing = PrepTarget.Event(pulseEventOf(e)) }
        )
    } ?: WorkNow.Free(
        doneLine = WorkHomeRules.doneLine(calEvents.count { it.end < now }),
        onRecord = { recordPicker = true },
        onWrite = { writePicker = true }
    )

    // ---------------------------------------------------------------- needs you
    val needCandidates = buildList<NeedCandidate<PulseNeed>> {
        failed.forEach { r ->
            add(NeedCandidate(NeedRank.FAILED, PulseNeed(
                key = "x-${r.meetingId}", kind = "Didn't finish processing", title = r.title,
                line = "${r.type.displayName} · ${dayLabel(r.at, now)} · the audio is safe",
                verb = "Try again", tone = AttentionTone.DANGER, icon = Icons.Filled.Warning,
                onVerb = { onOpenProcessing(r.meetingId) }, onOpen = { onOpenProcessing(r.meetingId) }
            )))
        }
        // A finished recording whose tasks and decisions nobody has checked yet: the Wrap-up.
        toReview.forEach { r ->
            add(NeedCandidate(NeedRank.YOU_OWE_DUE, PulseNeed(
                key = "r-${r.meetingId}", kind = "To review", title = r.title,
                line = "${r.type.displayName} · ${dayLabel(r.at, now)} · check its tasks and decisions",
                verb = "Review", tone = AttentionTone.ACCENT, icon = Icons.Filled.CheckCircle,
                onVerb = { onOpenWrapUp(r.meetingId, false) }, onOpen = { onOpenWrapUp(r.meetingId, false) }
            )))
        }
        followUps.forEach { r ->
            add(NeedCandidate(NeedRank.DECISION, PulseNeed(
                key = "f-${r.meetingId}", kind = "Follow-up to send", title = r.title,
                line = "${dayLabel(r.at, now)} · the recap is drafted",
                verb = "Send", tone = AttentionTone.ACCENT, icon = Icons.AutoMirrored.Filled.Send,
                onVerb = { onOpenWrapUp(r.meetingId, true) }, onOpen = { onOpenWrapUp(r.meetingId, true) }
            )))
        }
        pulse.attention.forEach { row ->
            val past = (row.dueAt ?: Long.MAX_VALUE) < startOfToday
            when (row.kind) {
                // Your own tasks are listed under Tasks, so only a promise with no task of its own is a need.
                AttentionKind.YOU_OWE -> if (row.taskId == null) add(NeedCandidate(NeedRank.YOU_OWE_DUE, PulseNeed(
                    key = "o-${row.itemId}", kind = "You promised", title = row.title, line = row.detail,
                    verb = "Open", tone = AttentionTone.ACCENT, icon = Icons.Filled.AddTask,
                    onVerb = { openAttention(row) }, onOpen = { openAttention(row) }
                )))
                AttentionKind.THEY_OWE -> if (past) {
                    val nudge = AttentionAction.NUDGE in row.actions
                    add(NeedCandidate(NeedRank.THEY_OWE_PAST, PulseNeed(
                        key = "w-${row.itemId ?: row.taskId}", kind = "Overdue from someone", title = row.title, line = row.detail,
                        verb = if (nudge) "Nudge" else "Open", tone = AttentionTone.WARNING, icon = Icons.Filled.Groups,
                        onVerb = { if (nudge) scope.launch { nudging = viewModel.asTask(row) } else openAttention(row) },
                        onOpen = { openAttention(row) }
                    )))
                }
                AttentionKind.DECISION_NEEDED -> add(NeedCandidate(NeedRank.DECISION, PulseNeed(
                    key = "d-${row.itemId}", kind = "Decision needed", title = row.title, line = row.detail,
                    verb = "Review", tone = AttentionTone.ACCENT, icon = Icons.Filled.CheckCircle,
                    onVerb = { openAttention(row) }, onOpen = { openAttention(row) }
                )))
                // "Gone quiet" was a guess, not a need; projects show their own state.
                AttentionKind.QUIET -> Unit
            }
        }
        if (inboxCount > 0) add(NeedCandidate(NeedRank.INBOX, PulseNeed(
            key = "inbox", kind = "Inbox",
            title = if (inboxCount == 1) "1 item in your Inbox" else "$inboxCount items in your Inbox",
            line = "Waiting to be filed or transcribed", verb = "Review", tone = AttentionTone.ACCENT, icon = Icons.Rounded.Inbox,
            onVerb = onOpenInbox, onOpen = onOpenInbox
        )))
    }
    val needs = rankNeeds(needCandidates, limit = WorkHomeRules.NEEDS_SHOWN).map { it.value }

    // ---------------------------------------------------------------- tasks, projects, people
    val endOfToday = startOfToday + 86_400_000L
    val dueTasks = WorkHomeRules.todayTasks(myTasks, now, endOfToday)
    val homeModel = WorkHomeModel(
        now = workNow,
        needs = needs,
        needsTotal = needCandidates.size,
        tasks = dueTasks.take(WorkHomeRules.TASKS_SHOWN).map { t ->
            val overdue = t.isOverdue(now)
            val due = t.dueAt ?: now
            val whenLine = when {
                overdue -> "Overdue · " + dayLabel(due, now)
                com.craftflowtechnologies.meetingmind.feature.work.home.WorkTodayTaskFormat.isTimed(due) -> "Due " + fmt.format(Date(due))
                else -> "Due today"
            }
            WorkHomeTask(
                id = t.id, title = t.title.ifBlank { "Untitled" },
                line = listOfNotNull(whenLine, t.meetingId?.let { titles[it] }).joinToString(" · "),
                overdue = overdue,
                onToggle = { viewModel.toggle(t) },
                onOpen = { openTask(t.id) }
            )
        },
        tasksTotal = dueTasks.size,
        waitingLine = WorkHomeRules.waitingLine(waitingOn),
        onAllTasks = { page.select(WorkSegment.TASKS) },
        onWaiting = { onOpenAll(WorkTab.WAITING) },
        projectsTitle = terms.projects,
        projects = projects.filter { it.notebook.isProject }
            .sortedByDescending { it.lastActivity ?: 0L }.take(WorkHomeRules.PROJECTS_SHOWN).map { p ->
                WorkHomeLink(
                    id = p.notebook.id, name = p.notebook.name.ifBlank { "Untitled" },
                    line = listOfNotNull(
                        p.org,
                        if (p.openTasks > 0) "${p.openTasks} open ${if (p.openTasks == 1) "task" else "tasks"}" else null,
                        p.lastActivity?.let { "Active " + dayLabel(it, now) }
                    ).joinToString(" · ").ifBlank { null },
                    onOpen = { onOpenProject(p.notebook.id) }
                )
            },
        onAllProjects = { page.select(WorkSegment.PROJECTS) },
        peopleTitle = if (terms.person == "Contact") "People" else terms.people,
        people = run {
            val owedToMe = waitingOn.mapNotNull { it.personId }.toSet()
            val iOwe = myTasks.mapNotNull { it.personId }.toSet()
            // People you owe or who owe you come first: they are the ones worth a tap today.
            people.sortedByDescending { (it.id in owedToMe || it.id in iOwe) }.take(WorkHomeRules.PEOPLE_SHOWN).map { person ->
                WorkHomeLink(
                    id = person.id, name = person.name.trim().substringBefore(' ').ifBlank { person.name },
                    line = when {
                        person.id in owedToMe -> "owes you"
                        person.id in iOwe -> "you owe"
                        else -> null
                    },
                    onOpen = { onOpenPerson(person.id) }
                )
            }
        },
        onAllPeople = { onOpenAll(WorkTab.PEOPLE) },
        onAsk = { asking = true }
    )

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
    val selectedNotes = notes.items.filter { it.id in selectedIds }

    val addActions = listOf(
        WorkMenuAction("Record meeting") { recordPicker = true },
        WorkMenuAction("Write note") { writePicker = true },
        WorkMenuAction("Add task") { page.select(WorkSegment.TASKS) },
        WorkMenuAction("Import", onClick = onImport),
        WorkMenuAction("Add to Inbox", onClick = onOpenInbox)
    )
    val moreActions = listOf(
        WorkMenuAction("Ask about my work") { asking = true },
        WorkMenuAction("Weekly review", onClick = onOpenWeeklyReview),
        WorkMenuAction("Autopilot · Pro") { showAutopilotSheet = true },
        WorkMenuAction("Team circles", onClick = onOpenCircles),
        WorkMenuAction("Work settings", onClick = onOpenSettings),
        WorkMenuAction("What Work does") { showWhatWork = true }
    )
    val workMenu: () -> com.craftflowtechnologies.meetingmind.core.navigation.MenuSection = {
        workMenuSection(
            onAsk = { asking = true },
            onInbox = onOpenInbox,
            onTasks = { page.select(WorkSegment.TASKS) },
            onWeeklyReview = onOpenWeeklyReview,
            onAutopilot = { showAutopilotSheet = true },
            onCircles = onOpenCircles,
            onSettings = onOpenSettings,
            onWhatWork = { showWhatWork = true }
        )
    }
    val barItems = WorkSegment.entries.map { VerticalBarItem(it.barKey, it.label, it.barIcon()) }

    Scaffold(
        containerColor = MM.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            VerticalBar(
                items = barItems,
                selected = segment.barKey,
                onSelect = { key ->
                    WorkSegment.entries.firstOrNull { it.barKey == key }?.let { s -> searching = false; page.select(s) }
                },
                homeItem = onHome ?: onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().testTag("work_page")) {
            Column(Modifier.padding(horizontal = MM.space.l)) {
                if (selecting) {
                    SelectionBar(selectedIds.size, onMove = { moving = selectedNotes }, onDelete = { page.delete(selectedNotes); selectedIds = emptySet() }, onClose = { selectedIds = emptySet() })
                } else if (!searching) {
                    WorkPageHeader(
                        contextLine = contextLine(now, meetings.size, needs.size), addActions = addActions, moreActions = moreActions,
                        onSearch = { searching = true }, inboxCount = inboxCount, onOpenInbox = onOpenInbox,
                        menuSection = workMenu
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    searching -> WorkSearchPane(
                        viewModel = viewModel, page = page,
                        onOpenNote = onOpenNote, onOpenMeeting = onOpenMeeting, onOpenPerson = onOpenPerson,
                        onOpenTask = ::openTask, onAsk = { asking = true }, onSearchEverything = onSearch,
                        onClose = { searching = false }
                    )
                    else -> when (segment) {
                        WorkSegment.PULSE -> WorkHomePane(homeModel, pulseList, hasRecent = recentRows.isNotEmpty()) {
                            // Every Work recording in every state, so the one just recorded is here straight away.
                            WorkRecentSection(recentRows, onOpen = ::openRecent, onAll = { page.select(WorkSegment.NOTES) })
                        }
                        WorkSegment.TASKS -> WorkTasksPane(
                            onOpenTask = ::openTask,
                            onOpenList = { id -> onOpenTaskList?.invoke(id) ?: onOpenProject(id) }
                        )
                        WorkSegment.NOTES -> NotesContent(query, notes, now, projects, selectedIds, notesList, notesActions)
                        WorkSegment.PROJECTS -> WorkProjectsPane(
                            viewModel = viewModel, projectWord = terms.project, initialTab = ProjectsTab.PROJECTS, now = now,
                            onOpenProject = onOpenProject, onOpenPerson = onOpenPerson, onNewProject = { newProject = true }
                        )
                        WorkSegment.DECISIONS -> WorkDecisionsPane(viewModel = viewModel, onOpenMeeting = onOpenMeeting)
                    }
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
    if (asking) ScopedAskSheet(com.craftflowtechnologies.meetingmind.ai.assistant.AskScope(), viewModel, onOpenMeeting, onOpenNote, onDismiss = { asking = false })
    preparing?.let { PrepareSheet(it, viewModel, onOpenMeeting, onDismiss = { preparing = null }) }
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { t ->
        WorkTaskSheet(t, viewModel, onDismiss = { editing = null },
            onOpenSource = { t.meetingId?.let { m -> editing = null; onOpenMeeting(m, t.startMs) } ?: t.noteId?.let { n -> editing = null; onOpenNote(n) } })
    }
    if (showWhatWork) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showWhatWork = false }, containerColor = MM.colors.surfaceRaised, shape = MM.radius.sheet) {
        Column(Modifier.padding(horizontal = MM.space.l).padding(bottom = MM.space.xl), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MM.space.s)) {
            Text("What Work does", style = MM.type.heading, color = MM.colors.ink)
            Text(
                "Work turns your meetings into the promises, decisions and people you can find again. It remembers what was said and when, and it shows only what needs you. Your recordings stay on this phone unless you turn on a cloud model.",
                style = MM.type.secondary, color = MM.colors.inkSecondary
            )
        }
    }
    if (showAutopilotSheet) {
        com.craftflowtechnologies.meetingmind.feature.automation.AutopilotSheet(
            target = com.craftflowtechnologies.meetingmind.feature.automation.AutopilotTarget.LatestSession,
            workOnly = true,
            onDismiss = { showAutopilotSheet = false },
            onOpenPaywall = { trigger ->
                showAutopilotSheet = false
                onOpenPaywall(trigger)
            },
            onOpenNote = { nId ->
                showAutopilotSheet = false
                onOpenNote(nId)
            }
        )
    }
}

/** A pane's bar icon. */
private fun WorkSegment.barIcon(): ImageVector = when (this) {
    WorkSegment.PULSE -> Icons.Rounded.Today
    WorkSegment.TASKS -> Icons.Rounded.Check
    WorkSegment.NOTES -> Icons.Rounded.Notes
    WorkSegment.PROJECTS -> Icons.Rounded.FolderOpen
    WorkSegment.DECISIONS -> Icons.Rounded.Flag
}

private fun localTimeOf(ms: Long): LocalTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()

/** A segment another screen asks the Work page to open. Set it, then open Work; the page takes it and clears it. */
object PendingWorkSegment {
    val request = MutableStateFlow<WorkSegment?>(null)
}

private fun pulseEventOf(e: CalendarEvent) =
    PulseEvent(e.key, e.title, e.begin, e.end, e.otherPeople, e.attendees.filter { a -> !a.isSelf }.mapNotNull { a -> a.email })


/**
 * Work's own group in the shared app menu (core/navigation/AppMenu): opened from the Work header, it leads the
 * sheet, the rest of the app follows, and Settings is one tap away in the sheet header.
 */
internal fun workMenuSection(
    onAsk: () -> Unit,
    onInbox: () -> Unit,
    onTasks: () -> Unit,
    onWeeklyReview: () -> Unit,
    onAutopilot: () -> Unit,
    onCircles: () -> Unit,
    onSettings: () -> Unit,
    onWhatWork: () -> Unit
): com.craftflowtechnologies.meetingmind.core.navigation.MenuSection {
    fun row(id: String, label: String, icon: com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon, action: () -> Unit, hint: String? = null) =
        com.craftflowtechnologies.meetingmind.core.navigation.MenuEntry(id, label, icon, com.craftflowtechnologies.meetingmind.core.navigation.MenuTarget.Run(action), hint)
    return com.craftflowtechnologies.meetingmind.core.navigation.MenuSection(
        "work", "Work", listOf(
            row("work.ask", "Ask about my work", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.AI, onAsk, "Answers from your meetings, with the moment each was said"),
            row("work.inbox", "Inbox", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.INBOX, onInbox, "Voice notes, files and scans to file"),
            row("work.tasks", "Tasks", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.TASKS, onTasks),
            row("work.review", "Weekly review", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.REVIEW, onWeeklyReview),
            row("work.autopilot", "Autopilot", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.PLAN, onAutopilot, "Drafts after each recording · Pro"),
            row("work.circles", "Team circles", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.CIRCLES, onCircles),
            row("work.settings", "Work settings", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.SETTINGS, onSettings, "Profile, words, rhythm, privacy"),
            row("work.about", "What Work does", com.craftflowtechnologies.meetingmind.core.navigation.MenuIcon.PULSE, onWhatWork)
        ),
        com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK
    )
}
