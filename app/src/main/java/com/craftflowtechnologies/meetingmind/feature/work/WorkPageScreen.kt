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
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
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
import com.craftflowtechnologies.meetingmind.feature.today.timeLabel
import com.craftflowtechnologies.meetingmind.feature.work.hub.PulseFacts
import com.craftflowtechnologies.meetingmind.feature.work.hub.NeedCandidate
import com.craftflowtechnologies.meetingmind.feature.work.hub.NeedRank
import com.craftflowtechnologies.meetingmind.feature.work.hub.PulseMoment
import com.craftflowtechnologies.meetingmind.feature.work.hub.WorkSearchPane
import com.craftflowtechnologies.meetingmind.feature.work.hub.pulseMoment
import com.craftflowtechnologies.meetingmind.feature.work.hub.rankNeeds
import com.craftflowtechnologies.meetingmind.feature.work.hub.weeklyReviewDue
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

    val moment = pulseMoment(
        PulseFacts(
            now = now,
            meetingsToday = calEvents.size,
            meetingsEndedToday = calEvents.count { it.end < now },
            nextMeetingStart = calEvents.filter { it.end >= now }.minOfOrNull { it.begin },
            prepLeadMs = settings.prepLeadMinutes * 60_000L,
            unreviewed = toReview.size,
            weeklyReviewDue = weeklyReviewDue(settings, now),
            coldStart = pulse.coldStart
        )
    )

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

    fun countLine(n: Int, one: String, many: String): String = if (n <= 0) "None" else "$n ${if (n == 1) one else many}"

    val hero: PulseHero? = when (moment) {
        PulseMoment.BEFORE_MEETING -> nextEvent?.let { e ->
            val line = pulse.today.firstOrNull { it.event.key == e.key }
            val others = e.otherPeople
            PulseHero.Next(
                whenLabel = UpNext.whenLabel(e, now, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)),
                title = e.title,
                withLine = if (others.isEmpty()) null else "With " + others.take(3).joinToString(", ") + if (others.size > 3) " +${others.size - 3}" else "",
                lines = listOf(
                    "You owe" to countLine(line?.youOwe ?: 0, "item", "items"),
                    "They owe" to countLine(line?.theyOwe ?: 0, "item", "items"),
                    "Still open" to countLine((line?.questions ?: 0) + (line?.decisionsNeeded ?: 0), "item", "items")
                ),
                blocks = calEvents.map { ev ->
                    RibbonBlock(
                        start = localTimeOf(ev.begin), end = localTimeOf(ev.end), title = ev.title,
                        onClick = { today.noteForEvent(ev) { id, _ -> onOpenNote(id) } }, past = ev.end < now
                    )
                },
                now = localTimeOf(now),
                onPrepare = { preparing = PrepTarget.Event(pulseEventOf(e)) },
                onRecord = { today.noteForEvent(e) { id, type -> onRecordEvent(id, type, e.title, UpNext.speakerCount(e)) } }
            )
        }
        PulseMoment.AFTER_MEETINGS -> toReview.firstOrNull()?.let { r ->
            PulseHero.ToReview(
                headline = "To review · ${r.title}",
                sub = "${r.type.displayName} · ${dayLabel(r.at, now)} · check the findings, then the follow-up is ready",
                onReview = { onOpenWrapUp(r.meetingId, false) }
            )
        }
        PulseMoment.WEEKLY_REVIEW -> PulseHero.Weekly(onStart = onOpenWeeklyReview)
        PulseMoment.FIRST_DAY -> PulseHero.FirstDay(
            calendarLine = calEvents.filter { it.begin >= now }.minByOrNull { it.begin }
                ?.let { e -> "${e.title} · ${fmt.format(Date(e.begin))}" },
            onRecord = { recordPicker = true },
            onWrite = { writePicker = true },
            onInbox = onOpenInbox
        )
        PulseMoment.QUIET -> null
    }

    // ---------------------------------------------------------------- needs you
    val needCandidates = buildList<NeedCandidate<PulseNeed>> {
        failed.forEach { r ->
            add(NeedCandidate(NeedRank.FAILED, PulseNeed(
                key = "x-${r.meetingId}", kind = "Didn't finish processing", title = r.title,
                line = "${r.type.displayName} · ${dayLabel(r.at, now)} · the audio is safe",
                verb = "Try again", tone = AttentionTone.DANGER, icon = Icons.Filled.Warning,
                onVerb = { onOpenProcessing(r.meetingId) }, onOpen = { onOpenMeeting(r.meetingId, null) }
            )))
        }
        pulse.attention.forEach { row ->
            val past = (row.dueAt ?: Long.MAX_VALUE) < startOfToday
            when (row.kind) {
                AttentionKind.YOU_OWE -> add(NeedCandidate(NeedRank.YOU_OWE_DUE, PulseNeed(
                    key = "o-${row.itemId ?: row.taskId}", kind = "You owe", title = row.title, line = row.detail,
                    verb = if (row.taskId != null) "Done" else "Open", tone = AttentionTone.ACCENT, icon = Icons.Filled.AddTask,
                    onVerb = {
                        if (row.taskId != null) scope.launch { viewModel.toggle(viewModel.asTask(row)) } else openAttention(row)
                    },
                    onOpen = { openAttention(row) }
                )))
                AttentionKind.THEY_OWE -> {
                    val nudge = AttentionAction.NUDGE in row.actions
                    add(NeedCandidate(if (past) NeedRank.THEY_OWE_PAST else NeedRank.THEY_OWE_OPEN, PulseNeed(
                        key = "w-${row.itemId ?: row.taskId}", kind = "Waiting on", title = row.title, line = row.detail,
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
                AttentionKind.QUIET -> add(NeedCandidate(NeedRank.QUIET, PulseNeed(
                    key = "q-${row.entityId}", kind = "Gone quiet", title = row.title, line = row.detail,
                    verb = "Open", tone = AttentionTone.NEUTRAL, icon = Icons.Filled.EventNote,
                    onVerb = { openEntity(row) }, onOpen = { openEntity(row) }
                )))
            }
        }
        if (inboxCount > 0) add(NeedCandidate(NeedRank.INBOX, PulseNeed(
            key = "inbox", kind = "Inbox",
            title = if (inboxCount == 1) "1 item in your Inbox" else "$inboxCount items in your Inbox",
            line = "Waiting to be filed or transcribed", verb = "Review", tone = AttentionTone.ACCENT, icon = Icons.Rounded.Inbox,
            onVerb = onOpenInbox, onOpen = onOpenInbox
        )))
    }
    val ranked = rankNeeds(needCandidates, limit = 4)
    val needs = ranked.map { it.value }

    // Since yesterday: the changes the Pulse measured, each one a sentence that opens its evidence.
    val changes = pulse.changes.flatMap { group ->
        group.lines.map { line ->
            PulseChange(line.text) {
                line.itemId?.let { id -> scope.launch { viewModel.evidenceOf(id)?.let { (m, at) -> onOpenMeeting(m, at) } } }
            }
        }
    }

    fun scheduleRow(item: com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem): ScheduleRow {
        val e = (item.target as DeepTarget.Event).event
        return ScheduleRow(item.id, item.title, e.otherPeople.take(3).joinToString(", ").ifEmpty { e.location }, timeLabel(item, fmt)) {
            today.noteForEvent(e) { id, _ -> onOpenNote(id) }
        }
    }
    val upcomingRows = meetings.filter { (it.end ?: it.start) >= now }.map { scheduleRow(it) }
    val earlierRows = meetings.filter { (it.end ?: it.start) < now }.map { scheduleRow(it) }
    val pulseModel = run {
        val firstName = self?.name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        val greeting = (when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }) + (firstName?.let { ", $it" } ?: "")
        val quiet = needs.isEmpty() && hero == null
        PulseModel(
            headline = if (quiet) "Nothing needs you today" else greeting,
            sentence = when {
                needs.isNotEmpty() -> "${needs.first().title} is the one to look at first."
                moment == PulseMoment.FIRST_DAY -> "Record a meeting, and tomorrow's Pulse knows what was said."
                else -> null
            },
            hero = hero,
            needs = needs,
            needsTotal = needCandidates.size,
            sinceLabel = pulse.sinceLabel,
            changes = changes,
            today = upcomingRows,
            earlier = earlierRows,
            onAsk = { asking = true }
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
                        onSearch = { searching = true }, inboxCount = inboxCount, onOpenInbox = onOpenInbox
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
                        WorkSegment.PULSE -> PulsePane(pulseModel, pulseList)
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
    WorkSegment.PULSE -> Icons.Filled.EventNote
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
