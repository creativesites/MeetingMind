package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.ai.assistant.AskScope
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.ui.work.PulseHeaderCard
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.core.work.BriefTarget
import com.craftflowtechnologies.meetingmind.core.work.ContextHeader
import com.craftflowtechnologies.meetingmind.core.work.ContextRepository
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.Direction
import com.craftflowtechnologies.meetingmind.core.work.ItemKind
import com.craftflowtechnologies.meetingmind.core.work.ItemStatus
import com.craftflowtechnologies.meetingmind.core.work.MemoryHistory
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.SavedFilter
import com.craftflowtechnologies.meetingmind.core.work.TimelineEntry
import com.craftflowtechnologies.meetingmind.core.work.WorkPeople
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.core.work.KanbanBoardData
import com.craftflowtechnologies.meetingmind.core.work.XRayGraph
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What a context page shows beyond the person, organisation or project's own screen. */
data class ContextState(
    val header: ContextHeader,
    val history: List<List<ItemEntity>>,
    val risks: List<ItemEntity>,
    val timeline: List<TimelineEntry>,
    /** An organisation's projects: id and name. */
    val projects: List<Pair<String, String>> = emptyList(),
    /** A project's members: person id, name, role. */
    val members: List<Triple<String, String, String>> = emptyList(),
    val org: PersonEntity? = null,
    val projectProps: String? = null,
    val openItems: Int = 0,
    val projectName: String? = null
)

/**
 * The tabs of a context page. Each kind has its own set; Kanban and X-Ray stay behind the
 * project-views flag, as before.
 */
enum class ContextTab(val label: String) {
    OVERVIEW("Overview"), TIMELINE("Timeline"), NOTES("Notes & files"), KANBAN("Kanban"), XRAY("X-Ray"),
    CONVERSATIONS("Conversations"), PROJECTS("Projects"), HOW_WE_KNOW("How MeetingMind knows"),
    PEOPLE("People"), HISTORY("History");

    companion object {
        fun forType(type: ContextType, projectViews: Boolean): List<ContextTab> = when (type) {
            ContextType.PROJECT -> listOfNotNull(OVERVIEW, TIMELINE, NOTES, KANBAN.takeIf { projectViews }, XRAY.takeIf { projectViews })
            ContextType.PERSON -> listOf(CONVERSATIONS, PROJECTS, HOW_WE_KNOW)
            ContextType.ORG -> listOf(PEOPLE, PROJECTS, HISTORY)
        }
    }
}

/** The full-screen pages a context page opens over itself. */
internal enum class ContextOverlay { PREPARE, ASK }

/** Everything the tabs read, loaded once per change. */
internal class PageData(
    val type: ContextType,
    val id: String,
    val name: String,
    val state: ContextState,
    val now: Long,
    val monthOnly: Boolean,
    val items: List<ItemEntity>,
    val tasks: List<WorkTask>,
    val notes: List<Note>,
    val meetings: List<MeetingRef>,
    /** Projects this page is linked to: an organisation's projects, or the projects a person's items sit in. */
    val projects: List<Pair<String, String>>,
    /** An organisation's people. */
    val people: List<WorkPerson>,
    val person: WorkPerson?,
    val story: MemoryHistory?
)

/** What the tabs can ask the page to do. */
internal class PageActions(
    val onOpenMeeting: (String, Long?) -> Unit,
    val onOpenNote: (String) -> Unit,
    val onOpenContext: (ContextType, String) -> Unit,
    val onOpenSource: (SourceRef) -> Unit,
    val onToggle: (WorkTask) -> Unit,
    val onEdit: (WorkTask) -> Unit,
    val onNudge: (WorkTask) -> Unit,
    val onAddTask: (String) -> Unit,
    val onRecord: () -> Unit,
    val onWriteNote: () -> Unit,
    val onAddMember: () -> Unit,
    val onRemoveMember: (String) -> Unit,
    val onToggleMonth: () -> Unit
)

/**
 * The context page (docs/PLAN_PROFESSIONAL.md D5.2): one layout for a project, a person and an
 * organisation. Identity, then a status line that comes from counts, then the actions (Ask, Brief,
 * Prepare, Record or Message), then the pulse header (You owe, They owe, Open, Decided, Next), then
 * the page's tabs. Ask and Prepare open as full screens over the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextScreen(
    type: ContextType,
    id: String,
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenContext: (ContextType, String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onRecordInto: (noteId: String, type: RecordingType, title: String) -> Unit,
    /** Opens a brief for this page. */
    onOpenBrief: (BriefTarget) -> Unit = {}
) {
    val state by remember(type, id) { viewModel.contextState(type, id) }.collectAsState(initial = null)
    val pageVm: ContextPageViewModel = composeViewModel()
    val version by viewModel.itemVersion.collectAsState(initial = 0L)
    val settings by viewModel.settings.collectAsState()
    val everyone by viewModel.everyone.collectAsState()
    val orgs by viewModel.organisations.collectAsState()
    val duplicates by viewModel.duplicates.collectAsState()
    val person by remember(type, id) { viewModel.person(id) }.collectAsState(initial = null)
    val project by remember(type, id) { viewModel.project(id) }.collectAsState(initial = null)
    val tasks by remember(type, id) { if (type == ContextType.PERSON) viewModel.tasksWith(id) else viewModel.tasksIn(id) }
        .collectAsState(initial = emptyList())
    val notes by remember(type, id) { if (type == ContextType.PERSON) viewModel.notesWith(id) else viewModel.notesIn(id) }
        .collectAsState(initial = emptyList())
    val orgMembers by remember(type, id) { viewModel.members(id) }.collectAsState(initial = emptyList())

    val items by produceState(emptyList<ItemEntity>(), type, id, version) { value = pageVm.items(type, id) }
    val meetings by produceState(emptyList<MeetingRef>(), type, id, version) { value = pageVm.meetings(type, id) }
    val touched by produceState(emptyList<Pair<String, String>>(), type, id, version) { value = pageVm.projectsTouched(type, id) }
    val story by produceState<MemoryHistory?>(null, type, id, version) {
        value = viewModel.memory.history(type, id, tell = false)
        value = viewModel.memory.history(type, id, tell = true)
    }

    val projectViews = BuildConfig.FEATURE_PROJECT_VIEWS
    val tabs = ContextTab.forType(type, projectViews)
    var tab by rememberSaveable(type, id) { mutableStateOf(tabs.first()) }
    var overlay by remember { mutableStateOf<ContextOverlay?>(null) }
    var sourceRef by remember { mutableStateOf<SourceRef?>(null) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var orgOpen by remember { mutableStateOf(false) }
    var contactOpen by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var addingMember by remember { mutableStateOf(false) }
    var recordPicker by remember { mutableStateOf(false) }
    var writePicker by remember { mutableStateOf(false) }
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var savedFilter by remember { mutableStateOf<SavedFilter?>(null) }
    var monthOnly by rememberSaveable { mutableStateOf(false) }
    var confirmedSame by rememberSaveable(id) { mutableStateOf(false) }
    var kanbanRefresh by remember { mutableStateOf(0) }
    var kanbanData by remember { mutableStateOf<KanbanBoardData?>(null) }
    var xrayGraph by remember { mutableStateOf<XRayGraph?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, tab, kanbanRefresh) {
        when (tab) {
            ContextTab.KANBAN -> kanbanData = viewModel.loadProjectKanban(id)
            ContextTab.XRAY -> xrayGraph = viewModel.loadProjectXRay(id)
            else -> Unit
        }
    }

    val st = state
    val now = remember(version) { System.currentTimeMillis() }
    val name = (project?.name ?: person?.name ?: st?.projectName ?: st?.org?.name).orEmpty()
    val confidential = project?.confidential == true || person?.confidential == true
    val orgId = project?.orgId ?: person?.orgId
    val orgName = orgs.firstOrNull { it.id == orgId }?.name
    val header = st?.header
    val statusLine = header?.let { ContextStatus.of(it, ContextStatus.pastDueCount(items, now)) }
    val firstName = name.trim().substringBefore(' ')
    val uncertainWith: WorkPerson? = if (type != ContextType.PERSON) null else duplicates
        .firstOrNull { it.first.id == id || it.second.id == id }
        ?.let { if (it.first.id == id) it.second else it.first }

    val page: PageData? = st?.let {
        PageData(
            type = type, id = id, name = name, state = it, now = now, monthOnly = monthOnly,
            items = items, tasks = tasks, notes = notes, meetings = meetings,
            projects = if (type == ContextType.ORG) it.projects else touched,
            people = orgMembers, person = person, story = story
        )
    }
    val actions = PageActions(
        onOpenMeeting = onOpenMeeting,
        onOpenNote = onOpenNote,
        onOpenContext = onOpenContext,
        onOpenSource = { sourceRef = it },
        onToggle = { viewModel.toggle(it) },
        onEdit = { editing = it },
        onNudge = { nudging = it },
        onAddTask = { text -> viewModel.addTask(text, noteId = notes.firstOrNull()?.id) },
        onRecord = { recordPicker = true },
        onWriteNote = { writePicker = true },
        onAddMember = { addingMember = true },
        onRemoveMember = { pid -> scope.launch { viewModel.context.removeMember(id, pid) } },
        onToggleMonth = { monthOnly = !monthOnly }
    )

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("context_page"), contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "ctx-top") {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = MM.space.xs, end = MM.space.xs, top = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.ink) }
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = MM.colors.inkSecondary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            when (type) {
                                ContextType.PROJECT -> {
                                    listOf("Active", "On hold", "Done").filter { it != project?.status }.forEach { s ->
                                        DropdownMenuItem(text = { Text("Mark $s") }, onClick = { menu = false; project?.let { viewModel.updateProject(it, status = s) } })
                                    }
                                    DropdownMenuItem(text = { Text("Project details") }, onClick = { menu = false; detailsOpen = true })
                                }
                                ContextType.PERSON -> {
                                    DropdownMenuItem(text = { Text("Email or phone") }, onClick = { menu = false; contactOpen = true })
                                    DropdownMenuItem(text = { Text(settings.terms.organisation) }, onClick = { menu = false; orgOpen = true })
                                    DropdownMenuItem(text = { Text("Same person as…") }, onClick = { menu = false; merging = true })
                                }
                                ContextType.ORG -> DropdownMenuItem(text = { Text("About and links") }, onClick = { menu = false; detailsOpen = true })
                            }
                            if (type != ContextType.ORG) {
                                DropdownMenuItem(
                                    text = { Text(if (confidential) "Not confidential" else "Confidential — keep on this phone") },
                                    onClick = {
                                        menu = false
                                        project?.let { viewModel.updateProject(it, confidential = !it.confidential) }
                                        person?.let { viewModel.updatePerson(it.copy(confidential = !it.confidential)) }
                                    }
                                )
                            }
                            if (type == ContextType.PERSON) {
                                DropdownMenuItem(text = { Text("Remove…") }, onClick = { menu = false; removing = true })
                            }
                        }
                    }
                }
            }

            item(key = "ctx-identity") {
                val p = person
                Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        type == ContextType.PERSON && p != null -> PersonAvatar(p, 56.dp)
                        type == ContextType.PROJECT -> Avatar(initialsOf(name), 56.dp, projectColor(id))
                        else -> Avatar(initialsOf(name), 56.dp, MM.colors.accent)
                    }
                    Column(Modifier.weight(1f).padding(start = MM.space.m)) {
                        val overline = when (type) {
                            ContextType.PROJECT -> settings.terms.project.uppercase() + " · " + (project?.status.orEmpty().uppercase()) +
                                if (confidential) " · CONFIDENTIAL" else ""
                            ContextType.ORG -> settings.terms.organisation.uppercase()
                            ContextType.PERSON -> "PERSON"
                        }
                        Text(overline.trim(' ', '·'), style = MM.type.overline, color = MM.colors.inkMuted)
                        Text(
                            name.ifBlank { askLabelFor(type) },
                            style = MM.type.title,
                            color = MM.colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = when (type) {
                            ContextType.PERSON -> p?.role
                            ContextType.PROJECT -> null
                            ContextType.ORG -> st?.org?.domainsJson?.let { ContextRepository.list(it).joinToString("  ·  ") { d -> "@$d" }.ifBlank { null } }
                        }
                        sub?.let { Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (orgId != null && orgName != null && type != ContextType.ORG) {
                            Text(
                                orgName,
                                style = MM.type.secondary,
                                color = MM.colors.accent,
                                modifier = Modifier.padding(top = MM.space.xs).clickable { onOpenContext(ContextType.ORG, orgId) }
                            )
                        }
                        if (type == ContextType.PERSON) {
                            val reach = p?.let { (it.emails.take(1) + it.phones.take(1)).joinToString(" · ") }.orEmpty()
                            Text(
                                reach.ifBlank { "Add email or phone" },
                                style = MM.type.caption,
                                color = if (reach.isBlank()) MM.colors.accent else MM.colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = MM.space.xs).clickable { contactOpen = true }
                            )
                        }
                        if (type == ContextType.PROJECT) {
                            val line = projectDetailLine(st?.projectProps)
                            Text(
                                line.first,
                                style = MM.type.caption,
                                color = if (line.second) MM.colors.accent else MM.colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = MM.space.xs).clickable { detailsOpen = true }
                            )
                        }
                    }
                }
            }

            val orgAbout = st?.org?.description
            if (type == ContextType.ORG && !orgAbout.isNullOrBlank()) {
                item(key = "ctx-about") {
                    Text(
                        orgAbout,
                        style = MM.type.secondary,
                        color = MM.colors.inkSecondary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.s).clickable { detailsOpen = true }
                    )
                }
            }

            statusLine?.let { s ->
                item(key = "ctx-status") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.m).testTag("context_status"), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(s.attention, s.label)
                        Spacer(Modifier.width(MM.space.s))
                        Text(s.reason, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }

            item(key = "ctx-actions") {
                Row(
                    Modifier.fillMaxWidth().padding(top = MM.space.m).horizontalScroll(rememberScrollState()).padding(horizontal = MM.space.l),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Chip("Ask", selected = true, color = MM.colors.accent) { overlay = ContextOverlay.ASK }
                    Chip(if (confidential) "Brief (on this phone)" else "Brief") { onOpenBrief(briefTarget(type, id)) }
                    Chip("Prepare next") { overlay = ContextOverlay.PREPARE }
                    if (type == ContextType.PROJECT) Chip("Record") { recordPicker = true }
                    if (type == ContextType.PERSON) Chip("Message") {
                        // Nudge the first thing they owe; with nothing waiting, reach them by email or phone.
                        val waiting = tasks.firstOrNull { it.waitingOn && !it.done }
                        nudging = waiting
                        if (waiting == null) contactOpen = true
                    }
                }
            }

            if (confidential) item(key = "ctx-privacy") {
                Surface(
                    color = MM.colors.surfaceSunk,
                    shape = MM.radius.card,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.m).testTag("context_privacy")
                ) {
                    Text(
                        "Confidential. This stays on this phone: AI for this page runs on the phone only, and nothing here is shared to a Circle.",
                        style = MM.type.secondary,
                        color = MM.colors.inkSecondary,
                        modifier = Modifier.padding(MM.space.m)
                    )
                }
            }

            if (uncertainWith != null && !confirmedSame) item(key = "ctx-uncertain") {
                Surface(
                    color = MM.colors.surfaceSunk,
                    shape = MM.radius.card,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(top = MM.space.m).testTag("context_uncertain")
                ) {
                    Column(Modifier.padding(MM.space.m)) {
                        Text(
                            "Is ${name.ifBlank { "this person" }} the same person as ${uncertainWith.name}?",
                            style = MM.type.bodyStrong,
                            color = MM.colors.ink
                        )
                        Text(
                            "MeetingMind found a likely match in your people. Nothing is merged until you choose.",
                            style = MM.type.secondary,
                            color = MM.colors.inkSecondary,
                            modifier = Modifier.padding(top = MM.space.xs)
                        )
                        Row(Modifier.padding(top = MM.space.s), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            Chip("Confirm", selected = true, color = MM.colors.accent) { confirmedSame = true }
                            Chip("Same person as…") { merging = true }
                            Chip("Not a person") { removing = true }
                        }
                    }
                }
            }

            if (header != null) {
                item(key = "ctx-pulse") {
                    val rows = pulseRowsFor(type, header, items, firstName)
                    PulseHeaderCard(rows = rows, modifier = Modifier.padding(horizontal = MM.space.l).padding(top = MM.space.m).testTag("context_pulse"))
                }
            }

            item(key = "ctx-tabs") {
                Row(
                    Modifier.fillMaxWidth().padding(top = MM.space.l).horizontalScroll(rememberScrollState()).padding(horizontal = MM.space.l),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    tabs.forEach { t ->
                        val label = if (t == ContextTab.HOW_WE_KNOW && firstName.isNotBlank()) "How MeetingMind knows $firstName" else t.label
                        Chip(label, selected = tab == t, color = MM.colors.ink) { tab = t }
                    }
                }
            }
            if (BuildConfig.FEATURE_SAVED_VIEWS) item(key = "ctx-saved") { SavedFilterRow { savedFilter = it } }

            if (page != null) {
                when (tab) {
                    ContextTab.OVERVIEW -> projectOverviewItems(page, actions)
                    ContextTab.TIMELINE -> timelineItems(page, actions)
                    ContextTab.NOTES -> projectNotesItems(page, actions)
                    ContextTab.KANBAN -> item(key = "ctx-kanban") {
                        val data = kanbanData
                        if (data != null) {
                            KanbanBoard(
                                boardData = data,
                                onMoveCard = { card, col ->
                                    viewModel.moveKanbanCard(card, col)
                                    kanbanRefresh++
                                }
                            )
                        } else {
                            Text("Loading Kanban board...", style = MM.type.secondary, color = MM.colors.inkMuted, modifier = Modifier.padding(MM.space.l))
                        }
                    }
                    ContextTab.XRAY -> item(key = "ctx-xray") {
                        val graph = xrayGraph
                        if (graph != null) XRayView(graph = graph)
                        else Text("Assembling X-Ray entity graph...", style = MM.type.secondary, color = MM.colors.inkMuted, modifier = Modifier.padding(MM.space.l))
                    }
                    ContextTab.CONVERSATIONS -> personConversationsItems(page, actions)
                    ContextTab.PROJECTS -> projectsListItems(page, actions)
                    ContextTab.HOW_WE_KNOW -> personHowWeKnowItems(page, actions)
                    ContextTab.PEOPLE -> orgPeopleItems(page, actions)
                    ContextTab.HISTORY -> orgHistoryItems(page, actions)
                }
            }
        }
    }

    // Full-screen pages over the page.
    if (overlay == ContextOverlay.PREPARE) FullScreenOverlay(onDismiss = { overlay = null }) {
        PrepareScreen(
            target = PrepTarget.Entity(type, id),
            viewModel = viewModel,
            onNavigateBack = { overlay = null },
            onOpenMeeting = { m, at -> overlay = null; onOpenMeeting(m, at) },
            onOpenNote = { n -> overlay = null; onOpenNote(n) },
            onRecordInto = { n, t, title -> overlay = null; onRecordInto(n, t, title) }
        )
    }
    if (overlay == ContextOverlay.ASK) FullScreenOverlay(onDismiss = { overlay = null }) {
        AskScreen(
            initial = AskScope(type = type, id = id, label = name.ifBlank { askLabelFor(type) }),
            viewModel = viewModel,
            onNavigateBack = { overlay = null },
            onOpenMeeting = { m, at -> overlay = null; onOpenMeeting(m, at) },
            onOpenNote = { n -> overlay = null; onOpenNote(n) },
            confidential = confidential
        )
    }

    sourceRef?.let { s ->
        SourceSheet(
            source = s,
            onOpenInTranscript = { m, at -> sourceRef = null; onOpenMeeting(m, at) },
            onDismiss = { sourceRef = null }
        )
    }

    savedFilter?.let { f ->
        val answer by produceState<com.craftflowtechnologies.meetingmind.core.work.SavedAnswer?>(null, f) { value = viewModel.savedViews.answer(f, type to id) }
        SavedAnswerSheet(answer, f.label, viewModel, onOpenMeeting, onOpenPerson = { onOpenContext(ContextType.PERSON, it) }, onDismiss = { savedFilter = null })
    }

    if (renaming) TextDialog(
        "Rename", name,
        if (type == ContextType.PERSON) "Everywhere $firstName appears — transcripts, summaries and tasks — will use the new name." else null,
        onDismiss = { renaming = false }
    ) { new ->
        renaming = false
        project?.let { viewModel.updateProject(it, name = new) }
        person?.let { viewModel.rename(it, new) }
    }
    if (detailsOpen && type == ContextType.PROJECT) ProjectDetailsDialog(st?.projectProps.orEmpty(), onDismiss = { detailsOpen = false }) { status, start, end, props ->
        detailsOpen = false
        scope.launch { viewModel.context.setProjectDetails(id, status, start, end, props) }
    }
    if (detailsOpen && type == ContextType.ORG) st?.org?.let { org ->
        OrgDetailsDialog(org, onDismiss = { detailsOpen = false }) { domains, description, urls, props ->
            detailsOpen = false
            scope.launch { viewModel.context.setOrgDetails(id, domains, description, urls, props) }
        }
    }
    if (orgOpen) person?.let { p ->
        TextDialog(settings.terms.organisation, orgName.orEmpty(), null, onDismiss = { orgOpen = false }) { viewModel.setOrganisation(p, it); orgOpen = false }
    }
    if (contactOpen) person?.let { p ->
        ContactDialog(p, onDismiss = { contactOpen = false }) { email, phone ->
            viewModel.updatePerson(
                p.copy(
                    emails = (listOfNotNull(email?.trim()?.lowercase()?.takeIf { WorkPeople.looksLikeEmail(it) }) + p.emails).distinct(),
                    phones = (listOfNotNull(phone?.let(WorkPeople::normalisePhone)) + p.phones).distinct()
                )
            )
            contactOpen = false
        }
    }
    if (removing) person?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = false },
            containerColor = SurfaceBase,
            title = { Text(PersonRemoval.title(p.name)) },
            text = { Text(PersonRemoval.body(p.name), style = MM.type.secondary, color = MM.colors.inkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = { removing = false; viewModel.deletePerson(p); onNavigateBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MM.colors.danger)
                ) { Text(PersonRemoval.CONFIRM_LABEL) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } }
        )
    }
    if (merging) person?.let { p ->
        ModalBottomSheet(onDismissRequest = { merging = false }, containerColor = SurfaceBase) {
            Column(Modifier.navigationBarsPadding().padding(bottom = MM.space.m)) {
                Text("${p.name} is the same person as…", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s))
                everyone.filter { it.id != p.id }.take(30).forEach { other ->
                    PersonRow(other, null) {
                        viewModel.merge(p, other)
                        merging = false
                        onOpenContext(ContextType.PERSON, other.id)
                    }
                }
            }
        }
    }
    if (addingMember) PickPersonDialog(everyone.map { it.id to it.name }, onDismiss = { addingMember = false }) { pid ->
        addingMember = false
        scope.launch { viewModel.context.addMember(id, pid) }
    }
    if (recordPicker) RecordTypeSheet(
        startsFor(settings.profile).filter { Workflows.isRecordable(it.type) },
        onPick = { t ->
            recordPicker = false
            viewModel.newNote(t, id) { nid -> onRecordInto(nid, t, "${t.displayName} · $name") }
        },
        onDismiss = { recordPicker = false }
    )
    if (writePicker) RecordTypeSheet(startsFor(settings.profile), onPick = { t -> writePicker = false; viewModel.newNote(t, id, onOpenNote) }, onDismiss = { writePicker = false })
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { t ->
        WorkTaskSheet(t, viewModel, onDismiss = { editing = null }, onOpenSource = t.meetingId?.let { m -> { editing = null; onOpenMeeting(m, t.startMs) } })
    }
}

/** Opens a full-screen page over the current one. Back and the close control both dismiss it. */
@Composable
private fun FullScreenOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = SurfaceBase) { content() }
    }
}

/** A plain row: a title, an optional detail under it, and the whole row is the tap target. */
@Composable
internal fun PageRow(title: String, detail: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MM.space.l, vertical = MM.space.s)
    ) {
        Text(title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

/** A note in a list: its kind tile, title and kind with the day. */
@Composable
internal fun WorkNoteRow(note: Note, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = MM.space.l, vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkTypeTile(note.workflow, note.title)
        Column(Modifier.padding(start = MM.space.m).weight(1f)) {
            Text(note.title.ifBlank { note.workflow.displayName }, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note.workflow.displayName + " · " + dayLabel(note.eventDate ?: note.createdAt), style = MM.type.caption, color = MM.colors.inkMuted)
        }
    }
}

@Composable
private fun StatusPill(attention: Boolean, label: String) {
    Surface(color = MM.colors.surfaceSunk, shape = MM.radius.pill) {
        Text(
            label,
            style = MM.type.caption,
            color = if (attention) MM.colors.warning else MM.colors.success,
            modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
        )
    }
}

/** The value of one pulse row: a count, and the first thing behind it. */
@Composable
private fun PulseValue(count: Int, detail: String?) {
    Text(
        text = when {
            count == 0 -> "Nothing"
            detail == null -> "$count"
            else -> "$count · $detail"
        },
        style = MM.type.secondary,
        color = MM.colors.ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * The five pulse rows. Counts come from the header; the detail is the first item behind each count,
 * so the header never shows a number it cannot explain.
 */
private fun pulseRowsFor(type: ContextType, header: ContextHeader, items: List<ItemEntity>, firstName: String): List<Pair<String, @Composable () -> Unit>> {
    val mine = items.filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.MINE.name && ContextStatus.isOpen(it.itemStatus) }
        .sortedBy { it.dueAt ?: Long.MAX_VALUE }
    val theirs = items.filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.THEIRS.name && ContextStatus.isOpen(it.itemStatus) }
        .sortedBy { it.dueAt ?: Long.MAX_VALUE }
    val open = items.filter { it.itemKind != ItemKind.COMMITMENT && ContextStatus.isOpen(it.itemStatus) }
    val decided = items.filter { it.itemKind == ItemKind.DECISION && it.itemStatus == ItemStatus.ACTIVE }.maxByOrNull { it.createdAt }
    val youOwe: @Composable () -> Unit = { PulseValue(header.youOwe, mine.firstOrNull()?.text) }
    val theyOwe: @Composable () -> Unit = { PulseValue(header.theyOwe, theirs.firstOrNull()?.text) }
    val openValue: @Composable () -> Unit = { PulseValue(header.open, open.firstOrNull()?.text) }
    val decidedValue: @Composable () -> Unit = { PulseValue(header.decided, decided?.text) }
    val next: @Composable () -> Unit = {
        val nextText = header.next?.let { n -> header.nextAt?.let { "$n · ${Pulse.shortDate(it)}" } ?: n }
        Text(nextText ?: "Nothing dated", style = MM.type.secondary, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val theyLabel = if (type == ContextType.PERSON && firstName.isNotBlank()) "$firstName owes" else "They owe"
    return listOf(
        "You owe" to youOwe,
        theyLabel to theyOwe,
        "Open" to openValue,
        "Decided" to decidedValue,
        "Next" to next
    )
}

/** The status and dates of a project, and whether they are set yet (so the line can ask for them). */
private fun projectDetailLine(propsJson: String?): Pair<String, Boolean> {
    val json = runCatching { JSONObject(propsJson.orEmpty()) }.getOrDefault(JSONObject())
    val dates = listOf(json.optString("start"), json.optString("end")).filter { it.isNotBlank() }
    val parts = listOfNotNull(json.optString("status").ifBlank { null }, dates.takeIf { it.isNotEmpty() }?.joinToString(" → "))
    return if (parts.isEmpty()) "Add status and dates" to true else parts.joinToString("  ·  ") to false
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private fun briefTarget(type: ContextType, id: String) = when (type) {
    ContextType.PROJECT -> BriefTarget.forProject(id)
    ContextType.ORG -> BriefTarget.forClient(id)
    ContextType.PERSON -> BriefTarget.forPerson(id)
}

private fun askLabelFor(type: ContextType) = when (type) {
    ContextType.PROJECT -> "This project"
    ContextType.ORG -> "This organisation"
    ContextType.PERSON -> "This person"
}

@Composable
private fun OrgDetailsDialog(org: PersonEntity, onDismiss: () -> Unit, onSave: (List<String>, String, List<String>, Map<String, String>) -> Unit) {
    var domains by remember { mutableStateOf(ContextRepository.list(org.domainsJson).joinToString(", ")) }
    var description by remember { mutableStateOf(org.description) }
    var urls by remember { mutableStateOf(ContextRepository.list(org.urlsJson).joinToString("\n")) }
    var props by remember { mutableStateOf(ContextRepository.map(org.propertiesJson).entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase, title = { Text("About ${org.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(domains, { domains = it }, label = { Text("Email domains (comma separated)") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2)
                OutlinedTextField(urls, { urls = it }, label = { Text("Links (one per line)") }, minLines = 2)
                OutlinedTextField(props, { props = it }, label = { Text("Properties (name: value, one per line)") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(domains.split(',', ' ').filter { it.isNotBlank() }, description, urls.lines(), parseProps(props)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProjectDetailsDialog(propsJson: String, onDismiss: () -> Unit, onSave: (String, String, String, Map<String, String>) -> Unit) {
    val json = remember { runCatching { JSONObject(propsJson) }.getOrDefault(JSONObject()) }
    var status by remember { mutableStateOf(json.optString("status", "Active")) }
    var start by remember { mutableStateOf(json.optString("start")) }
    var end by remember { mutableStateOf(json.optString("end")) }
    var props by remember { mutableStateOf(json.optJSONObject("custom")?.let { o -> o.keys().asSequence().joinToString("\n") { "$it: ${o.optString(it)}" } }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase, title = { Text("Project details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(status, { status = it }, label = { Text("Status") }, singleLine = true)
                OutlinedTextField(start, { start = it }, label = { Text("Start (e.g. 2026-10-01)") }, singleLine = true)
                OutlinedTextField(end, { end = it }, label = { Text("End") }, singleLine = true)
                OutlinedTextField(props, { props = it }, label = { Text("Properties (name: value, one per line)") }, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status.trim(), start.trim(), end.trim(), parseProps(props)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PickPersonDialog(people: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBase, title = { Text("Add a member") },
        text = {
            Column {
                people.take(30).forEach { (pid, pname) ->
                    Text(pname, style = MM.type.bodyStrong, color = MM.colors.ink, modifier = Modifier.fillMaxWidth().clickable { onPick(pid) }.padding(vertical = MM.space.s))
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun parseProps(text: String): Map<String, String> =
    text.lines().mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { it[0].trim() to it[1].trim() } }.toMap()

/** The old person route: an organisation gets the organisation page, anyone else the person page. */
@Composable
fun PersonContextScreen(
    viewModel: WorkViewModel,
    personId: String,
    onNavigateBack: () -> Unit,
    onOpenContext: (ContextType, String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenBrief: (BriefTarget) -> Unit = {}
) {
    val type by produceState<ContextType?>(null, personId) {
        value = if (viewModel.people.get(personId)?.kind == com.craftflowtechnologies.meetingmind.core.work.PersonKind.ORG) ContextType.ORG else ContextType.PERSON
    }
    type?.let { ContextScreen(it, personId, viewModel, onNavigateBack, onOpenContext, onOpenNote, onOpenMeeting, onRecordInto = { _, _, _ -> }, onOpenBrief = onOpenBrief) }
}
