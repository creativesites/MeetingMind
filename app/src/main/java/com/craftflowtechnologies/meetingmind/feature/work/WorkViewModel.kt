package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.database.FindingRow
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteCodec
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.work.AttentionRow
import com.craftflowtechnologies.meetingmind.core.work.Channel
import com.craftflowtechnologies.meetingmind.core.work.ContextRepository
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.ItemRepository
import com.craftflowtechnologies.meetingmind.core.work.ItemStatus
import com.craftflowtechnologies.meetingmind.core.work.Prepare
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.SavedViews
import com.craftflowtechnologies.meetingmind.core.work.itemStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import com.craftflowtechnologies.meetingmind.core.work.Direction
import com.craftflowtechnologies.meetingmind.core.work.FollowUpLine
import com.craftflowtechnologies.meetingmind.core.work.MeetingRow
import com.craftflowtechnologies.meetingmind.core.work.WorkPeople
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.WorkRepository
import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A project hub's figures, for its card. */
data class ProjectCard(val notebook: Notebook, val notes: Int, val openTasks: Int, val org: String?)

/**
 * The Work space, project hubs, person pages and the professional home all read from here
 * (docs/PLAN_PROFESSIONAL.md §6–7). Every list is a live view of the database, so a rename, a
 * tick or a new recording shows everywhere at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val prefs = UserPreferencesManager(application)
    private val notes = NoteRepository(application, database)
    val work = WorkRepository(database)
    val people = WorkPeople(database)

    private fun <T> Flow<T>.state(initial: T) = catch { }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val settings: StateFlow<WorkSettings> = prefs.workSettings.stateIn(viewModelScope, SharingStarted.Eagerly, WorkSettings())

    private val _self = MutableStateFlow<WorkPerson?>(null)
    val self: StateFlow<WorkPerson?> = _self

    val tasks: StateFlow<List<WorkTask>> = work.observeTasks().state(emptyList())
    val myTasks: StateFlow<List<WorkTask>> = tasks.map { l -> l.filter { !it.done && !it.waitingOn } }.state(emptyList())
    /** What people owe you: their open commitments (D4.2). */
    val theyOwe: StateFlow<List<WorkTask>> = work.observeCommitments(Direction.THEIRS).state(emptyList())
    val waitingOn: StateFlow<List<WorkTask>> = theyOwe.map { l -> l.filter { !it.done } }.state(emptyList())
    /** What you owe: your tasks, plus any commitment you made that has no task of its own. */
    val youOwe: StateFlow<List<WorkTask>> = combine(tasks, work.observeCommitments(Direction.MINE)) { t, c ->
        val taskIds = t.map { it.id }.toSet()
        t.filter { !it.waitingOn } + c.filter { it.id !in taskIds }
    }.state(emptyList())
    val toReview: StateFlow<List<MeetingRow>> = work.observeToReview().state(emptyList())
    val followUps: StateFlow<List<MeetingRow>> = work.observeFollowUps().state(emptyList())
    val decisions: StateFlow<List<FindingRow>> = work.observeDecisions().state(emptyList())
    /** Promises nobody could place, waiting to be settled or dismissed. */
    val unclear: StateFlow<List<com.craftflowtechnologies.meetingmind.core.database.ItemEntity>> = ItemRepository(database).observeUnclear().state(emptyList())
    fun settleUnclear(i: com.craftflowtechnologies.meetingmind.core.database.ItemEntity, mine: Boolean) = viewModelScope.launch { ItemRepository(database).settle(i.id, if (mine) Direction.MINE else Direction.THEIRS, null) }
    fun dismissUnclear(i: com.craftflowtechnologies.meetingmind.core.database.ItemEntity) = viewModelScope.launch { ItemRepository(database).setStatus(i.id, ItemStatus.CANCELLED) }
    val decisionLog: StateFlow<List<FindingRow>> = work.observeDecisionLog().state(emptyList())
    val openQuestions: StateFlow<List<FindingRow>> = work.observeOpenQuestions().state(emptyList())
    val recent: StateFlow<List<MeetingRow>> = work.observeRecentWork(12).state(emptyList())
    val everyone: StateFlow<List<WorkPerson>> = people.observePeople().state(emptyList())
    val organisations: StateFlow<List<WorkPerson>> = people.observeOrganisations().state(emptyList())
    val workNotes: StateFlow<List<Note>> = work.observeWorkNotes(30).map { l -> l.map { with(NoteCodec) { it.toDomain() } } }.state(emptyList())

    val projects: StateFlow<List<ProjectCard>> = combine(
        database.workDao().observeProjects(), tasks, organisations
    ) { nbs, t, orgs ->
        val nbList = nbs.map { with(NoteCodec) { it.toDomain() } }
        nbList.map { nb ->
            val count = database.workDao().observeNoteCount(nb.id).first()
            ProjectCard(nb, count, 0, orgs.firstOrNull { it.id == nb.orgId }?.name)
        }
    }.state(emptyList())

    /** Meeting titles, for the "from Acme review" line under a task. */
    val titles: StateFlow<Map<String, String>> = database.meetingDao().getAllMeetings().map { l -> l.associate { it.id to it.title } }.state(emptyMap())

    init {
        viewModelScope.launch { _self.value = people.self(prefs.preferencesFlow.first().userName) }
        // What changed is measured from the last time the Pulse was read, fixed for this session.
        viewModelScope.launch { pulseSince.value = prefs.pulseSeenAt.first() ?: (System.currentTimeMillis() - 24 * 3_600_000L) }
    }

    // ---------------------------------------------------------------- tasks

    fun toggle(t: WorkTask) = viewModelScope.launch { work.toggle(t.id) }
    fun snooze(t: WorkTask, days: Int = 1) = viewModelScope.launch { work.snooze(t.id, days) }
    fun addTask(title: String, waitingOnPersonId: String? = null, noteId: String? = null) = viewModelScope.launch {
        if (title.isNotBlank()) work.addTask(title, waitingOnPersonId, noteId)
    }
    fun resolveQuestion(q: FindingRow, answer: String?) = viewModelScope.launch { work.resolveQuestion(q.id, q.meetingId, answer) }

    // ---------------------------------------------------------------- notes and projects

    /** Starts a work note from its template (Meeting notes, 1:1, Project brief…), filed in [notebookId]. */
    fun newNote(type: RecordingType, notebookId: String? = null, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(notes.createNote(workflow = type, notebookId = notebookId, draft = true).id)
    }

    /** A project is a notebook in Work that knows it's a project (§5.1). */
    fun newProject(name: String, orgName: String?, confidential: Boolean, onCreated: (String) -> Unit) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val org = orgName?.trim()?.takeIf { it.isNotEmpty() }?.let { people.createOrganisation(it) }
        val props = JSONObject().put("status", "Active").apply {
            if (confidential || settings.value.keepOnDevice) put("confidential", true)
            org?.let { put("orgId", it.id) }
        }
        onCreated(notes.createNotebook(name, NotebookSpace.WORK, isProject = true, propertiesJson = props.toString()).id)
    }

    fun updateProject(nb: Notebook, status: String? = null, confidential: Boolean? = null, name: String? = null) = viewModelScope.launch {
        val props = runCatching { JSONObject(nb.propertiesJson) }.getOrDefault(JSONObject())
        status?.let { props.put("status", it) }
        confidential?.let { props.put("confidential", it) }
        notes.updateNotebook(nb.copy(name = name?.trim()?.ifEmpty { null } ?: nb.name, isProject = true, propertiesJson = props.toString()))
    }

    fun project(id: String): Flow<Notebook?> = database.workDao().observeProjects().map { l -> l.firstOrNull { it.id == id }?.let { with(NoteCodec) { it.toDomain() } } }
    fun notesIn(notebookId: String): Flow<List<Note>> = database.workDao().observeNotesIn(notebookId).map { l -> l.map { with(NoteCodec) { it.toDomain() } } }
    fun tasksIn(notebookId: String): Flow<List<WorkTask>> = work.observeTasksIn(notebookId)
    fun decisionsIn(notebookId: String) = database.workDao().observeDecisionsIn(notebookId)
    fun questionsIn(notebookId: String) = database.workDao().observeQuestionsIn(notebookId)

    // ---------------------------------------------------------------- people

    fun person(id: String): Flow<WorkPerson?> = people.observe(id)
    fun tasksWith(personId: String): Flow<List<WorkTask>> = work.observeTasksWith(personId)
    fun notesWith(personId: String): Flow<List<Note>> = database.workDao().observeNotesWith(personId).map { l -> l.map { with(NoteCodec) { it.toDomain() } } }
    fun decisionsWith(personId: String) = database.workDao().observeDecisionsWith(personId)
    fun members(orgId: String): Flow<List<WorkPerson>> = people.observeMembers(orgId)

    fun addPerson(name: String, role: String?, email: String?, phone: String?, org: String?, onCreated: (String) -> Unit) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val p = people.createPerson(name, role, email, phone, org)
        email?.let { people.addContact(p.id, it, null) }
        onCreated(p.id)
    }
    fun rename(p: WorkPerson, name: String) = viewModelScope.launch { people.rename(p.id, name) }
    fun updatePerson(p: WorkPerson) = viewModelScope.launch { people.update(p) }
    fun merge(from: WorkPerson, into: WorkPerson) = viewModelScope.launch { people.merge(from.id, into.id) }
    fun deletePerson(p: WorkPerson) = viewModelScope.launch { people.delete(p.id) }
    fun setOrganisation(p: WorkPerson, orgName: String?) = viewModelScope.launch {
        val org = orgName?.trim()?.takeIf { it.isNotEmpty() }?.let { people.createOrganisation(it) }
        people.update(p.copy(orgId = org?.id))
    }
    fun rememberChannel(p: WorkPerson, c: Channel) = viewModelScope.launch { people.rememberChannel(p.id, c) }

    /** Who owes a task, as a person: for a nudge. */
    suspend fun ownerOf(t: WorkTask): WorkPerson? = t.personId?.let { people.get(it) }

    fun nudgeLine(t: WorkTask) = FollowUpLine(t.title, t.ownerName, t.dueAt, null)

    // ---------------------------------------------------------------- pulse, context, prepare

    /** The one place a model for work-memory writing is chosen: sensitive material only ever gets the local one. */
    private val workModels = com.craftflowtechnologies.meetingmind.core.work.DeviceWorkModels(
        application, com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(application),
        com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(application)
    ) { prefs.preferencesFlow.first().processingProfile }
    private val packPrivacy = com.craftflowtechnologies.meetingmind.core.work.DevicePackPrivacy(application)
    val briefs = com.craftflowtechnologies.meetingmind.core.work.BriefBuilder(database, workModels, packPrivacy)
    val memory = com.craftflowtechnologies.meetingmind.core.work.MemoryRepository(database, workModels, packPrivacy)
    val prepareWriter = com.craftflowtechnologies.meetingmind.core.work.PrepareWriter(database, workModels, packPrivacy)
    val scopedAsk = com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAsk(database, workModels, packPrivacy, dateLabel = { com.craftflowtechnologies.meetingmind.core.work.Pulse.shortDate(it) })

    // ---------------------------------------------------------------- inbox (D7)

    private val inbox = com.craftflowtechnologies.meetingmind.core.work.InboxRepository(database)
    val inboxItems: StateFlow<List<com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity>> = inbox.observeOpen().state(emptyList())
    val inboxCount: StateFlow<Int> = inbox.observeOpenCount().state(0)
    private fun proposer() = com.craftflowtechnologies.meetingmind.core.work.InboxProposer(database, workModels, settings.value.keepOnDevice)
    suspend fun inboxCandidates() = proposer().candidates()
    /** Proposes one filing for an item. Nothing is filed until [fileInbox]. */
    suspend fun proposeInbox(item: com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity) = proposer().propose(item)
    suspend fun fileInbox(item: com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity, filing: com.craftflowtechnologies.meetingmind.core.work.Filing) =
        com.craftflowtechnologies.meetingmind.core.work.InboxFiler(getApplication(), database).file(item.id, filing)
    fun dismissInbox(item: com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity) = viewModelScope.launch { inbox.dismiss(item.id) }
    /** Adds what a document scan or a picker returned, the same way a share does. */
    fun addToInbox(parts: List<com.craftflowtechnologies.meetingmind.core.work.SharedPart>) = viewModelScope.launch {
        inbox.add(parts) { com.craftflowtechnologies.meetingmind.core.work.ShareIn.copy(getApplication(), it) }
    }

    val context = ContextRepository(database)
    val prepare = Prepare(database)
    val savedViews = SavedViews(database)
    private val pulseEngine = Pulse(database)
    private val calendarEvents = MutableStateFlow<List<PulseEvent>>(emptyList())
    private val pulseSince = MutableStateFlow<Long?>(null)

    /** The Work Pulse, recomputed whenever an item, its log, today's calendar or the settings change. */
    val pulse: StateFlow<PulseUi> = combine(database.itemDao().observeVersion(), calendarEvents, settings, pulseSince, inboxCount) { _, events, s, since, inbox -> listOf(events, s, since, inbox) }
        .mapLatest { list ->
            @Suppress("UNCHECKED_CAST") val events = list[0] as List<PulseEvent>; val s = list[1] as WorkSettings; val since = list[2] as Long?; val inbox = list[3] as Int
            val now = System.currentTimeMillis()
            val from = since ?: (now - 24 * 3_600_000L)
            val attention = pulseEngine.attention(now, 4, pulseEngine.projectIdsFor(events), s.quietDays)
            PulseUi(
                attention = attention, sinceLabel = sinceLabel(from, now), changes = pulseEngine.changesSince(from), today = pulseEngine.today(events),
                coldStart = database.itemDao().allLive().none { it.reviewed }, inbox = inbox
            )
        }.state(PulseUi())

    /** Today's calendar, as Pulse and Prepare want it. Only a real change is passed on. */
    fun setPulseEvents(events: List<PulseEvent>) { if (calendarEvents.value != events) calendarEvents.value = events }

    /** Ticks whenever an item or its log changes, for screens that read history. */
    val itemVersion: Flow<Long> = database.itemDao().observeVersion()

    fun markPulseSeen() = viewModelScope.launch { prefs.setPulseSeenAt(System.currentTimeMillis()) }

    /** Where a row or a change is evidenced: the recording and the moment in it. */
    suspend fun evidenceOf(itemId: String): Pair<String, Long?>? {
        val item = database.itemDao().getById(itemId) ?: return null
        val meeting = item.meetingId ?: return null
        return meeting to database.itemDao().evidenceFor(itemId).firstOrNull()?.startMs
    }

    /** A row of Needs you as a task, for the nudge sheet. */
    suspend fun asTask(row: AttentionRow): WorkTask = WorkTask(
        id = row.taskId ?: row.itemId.orEmpty(), title = row.title, dueAt = row.dueAt, doneAt = null, waitingOn = true,
        ownerName = row.personId?.let { people.get(it)?.name }, personId = row.personId, meetingId = row.meetingId, noteId = null, startMs = row.startMs,
        kind = com.craftflowtechnologies.meetingmind.core.tasks.TaskKind.TASK
    )

    private fun sinceLabel(since: Long, now: Long): String {
        val days = ((DueDates.startOfDay(now) - DueDates.startOfDay(since)) / 86_400_000L).toInt()
        return when {
            days <= 0 -> "Since earlier today"
            days == 1 -> "Since yesterday"
            days < 7 -> "Since " + java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date(since))
            else -> "Since " + Pulse.shortDate(since)
        }
    }

    /** One context page's data; refreshes with the items. */
    fun contextState(type: ContextType, id: String): Flow<ContextState> = database.itemDao().observeVersion().mapLatest {
        val items = context.items(type, id)
        ContextState(
            header = context.header(type, id), history = context.decisionHistory(type, id), risks = context.risks(type, id), timeline = context.timeline(type, id),
            projects = if (type == ContextType.ORG) context.projectsOf(id).map { it.id to it.name } else emptyList(),
            members = if (type == ContextType.PROJECT) context.members(id).map { m -> Triple(m.personId, people.get(m.personId)?.name ?: "Someone", m.role) } else emptyList(),
            org = if (type == ContextType.ORG) database.peopleDao().getById(id) else null,
            projectProps = if (type == ContextType.PROJECT) database.notebookDao().getById(id)?.propertiesJson else null,
            openItems = items.count { it.itemStatus in setOf(ItemStatus.OPEN, ItemStatus.UNCLEAR, ItemStatus.PROPOSED) },
            projectName = if (type == ContextType.PROJECT) database.notebookDao().getById(id)?.name else null
        )
    }

    private val state = application.getSharedPreferences("work_state", android.content.Context.MODE_PRIVATE)
    private val notSame = MutableStateFlow(state.getStringSet(NOT_SAME, emptySet()).orEmpty())

    /** "Same person?" suggestions, minus pairs the person said are different. */
    val duplicates: StateFlow<List<Pair<WorkPerson, WorkPerson>>> = combine(everyone, notSame) { _, dismissed ->
        people.likelyDuplicates().filter { (a, b) -> pairKey(a, b) !in dismissed }
    }.state(emptyList())

    fun markDifferent(a: WorkPerson, b: WorkPerson) {
        val next = notSame.value + pairKey(a, b)
        state.edit().putStringSet(NOT_SAME, next).apply()
        notSame.value = next
    }

    private fun pairKey(a: WorkPerson, b: WorkPerson) = listOf(a.id, b.id).sorted().joinToString("|")

    fun updateSettings(change: (WorkSettings) -> WorkSettings) = viewModelScope.launch { prefs.updateWorkSettings(change) }

    /** The "How it works" card goes once the person has closed it, or has done the loop once. */
    val introDismissed = MutableStateFlow(state.getBoolean(INTRO, false))
    fun dismissIntro() { state.edit().putBoolean(INTRO, true).apply(); introDismissed.value = true }

    private companion object {
        const val NOT_SAME = "not_same_people"
        const val INTRO = "work_intro_dismissed"
    }
}
