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
import com.craftflowtechnologies.meetingmind.core.work.ItemKind
import com.craftflowtechnologies.meetingmind.core.work.Prepare
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.PulseEvent
import com.craftflowtechnologies.meetingmind.core.work.SavedViews
import com.craftflowtechnologies.meetingmind.core.work.itemStatus
import com.craftflowtechnologies.meetingmind.core.work.ViewQueries
import com.craftflowtechnologies.meetingmind.core.work.XRayBuilder
import com.craftflowtechnologies.meetingmind.core.work.KanbanBoardData
import com.craftflowtechnologies.meetingmind.core.work.TimelineSection
import com.craftflowtechnologies.meetingmind.core.work.XRayGraph
import com.craftflowtechnologies.meetingmind.core.work.RiskItem
import com.craftflowtechnologies.meetingmind.core.work.KanbanCard
import com.craftflowtechnologies.meetingmind.core.work.KanbanColumnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import com.craftflowtechnologies.meetingmind.core.work.Direction
import com.craftflowtechnologies.meetingmind.core.work.FollowUpLine
import com.craftflowtechnologies.meetingmind.core.work.MeetingRow
import com.craftflowtechnologies.meetingmind.core.work.WorkPeople
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.OPEN_TASK_LIMIT
import com.craftflowtechnologies.meetingmind.core.work.WorkRepository
import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A project hub's figures, for its card. */
data class ProjectCard(val notebook: Notebook, val notes: Int, val openTasks: Int, val org: String?, val lastActivity: Long? = null)

/** Rich linked recording, summary, and note metadata for an Inbox item. */
data class InboxLinkedMeeting(
    val meetingId: String?,
    val noteId: String?,
    val title: String,
    val summaryText: String?,
    val transcriptPreview: String?,
    val status: String,
    val audioFilePath: String?,
    val durationMs: Long,
    val participantCount: Int,
    val actionItems: List<String> = emptyList(),
    val decisions: List<String> = emptyList()
)

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
    /** Your open tasks, soonest first. Capped (see [OPEN_TASK_LIMIT]); the "All tasks" screen reads [youOwe] for the full list. */
    val myTasks: StateFlow<List<WorkTask>> = work.observeOpenTasks().map { l -> l.filter { !it.waitingOn } }.state(emptyList())
    /** The true number of your open tasks, however many [myTasks] holds. */
    val myTaskCount: StateFlow<Int> = work.observeOpenMyTaskCount().state(0)
    /** What people owe you: their open commitments (D4.2). */
    val theyOwe: StateFlow<List<WorkTask>> = work.observeCommitments(Direction.THEIRS).state(emptyList())
    val waitingOn: StateFlow<List<WorkTask>> = theyOwe.map { l -> l.filter { !it.done } }.state(emptyList())
    /** What you owe: your tasks, plus any commitment you made that has no task of its own. */
    val youOwe: StateFlow<List<WorkTask>> = combine(tasks, work.observeCommitments(Direction.MINE)) { t, c ->
        val taskIds = t.map { it.id }.toSet()
        t.filter { !it.waitingOn } + c.filter { it.id !in taskIds }
    }.state(emptyList())
    val toReview: StateFlow<List<MeetingRow>> = work.observeToReview().state(emptyList())
    val failed: StateFlow<List<MeetingRow>> = work.observeFailed().state(emptyList())
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
        database.workDao().observeProjects(), database.workDao().observeNoteCountsByNotebook(), organisations,
        database.workDao().observeOpenTaskCountsByNotebook(), database.workDao().observeNotebookActivity()
    ) { nbs, counts, orgs, openTasks, activity ->
        val byNotebook = counts.associate { it.notebookId to it.count }
        val tasksBy = openTasks.associate { it.notebookId to it.count }
        val activityBy = activity.associate { it.notebookId to it.lastAt }
        val orgNames = orgs.associate { it.id to it.name }
        nbs.map { with(NoteCodec) { it.toDomain() } }.map { nb ->
            ProjectCard(nb, byNotebook[nb.id] ?: 0, tasksBy[nb.id] ?: 0, nb.orgId?.let { orgNames[it] }, activityBy[nb.id])
        }
    }.state(emptyList())

    /** Meeting titles, for the "from Acme review" line under a task. */
    val titles: StateFlow<Map<String, String>> = database.workDao().observeReferencedMeetingTitles()
        .map { l -> l.associate { it.id to it.title } }.distinctUntilChanged().state(emptyMap())

    // Declared before the init block below, which sets it: property initialisers run in source order.
    private val pulseSince = MutableStateFlow<Long?>(null)

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
    val scopedAsk = com.craftflowtechnologies.meetingmind.ai.assistant.ScopedAsk(
        database = database,
        models = workModels,
        privacy = packPrivacy,
        dateLabel = { com.craftflowtechnologies.meetingmind.core.work.Pulse.shortDate(it) },
        onModelFinished = { com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager.release() }
    )

    // ---------------------------------------------------------------- inbox (D7)

    // ---------------------------------------------------------------- weekly review

    private val weeklyReviews = com.craftflowtechnologies.meetingmind.core.work.WeeklyReviews(database)
    private val _review = MutableStateFlow<com.craftflowtechnologies.meetingmind.core.work.WeeklyReviewData?>(null)
    val review: StateFlow<com.craftflowtechnologies.meetingmind.core.work.WeeklyReviewData?> = _review

    fun loadReview() { viewModelScope.launch { _review.value = runCatching { weeklyReviews.build() }.getOrNull() } }

    /** Next week's plan, in one pass; the review is read again afterwards, so what's left is what's still open. */
    fun applyPlan(decisions: List<com.craftflowtechnologies.meetingmind.core.work.PlanDecision>, done: (Int) -> Unit) {
        viewModelScope.launch {
            val n = runCatching { weeklyReviews.applyPlan(decisions) }.getOrDefault(0)
            _review.value = runCatching { weeklyReviews.build() }.getOrNull()
            done(n)
        }
    }

    val nextMonday: Long get() = weeklyReviews.nextMonday()

    private val inbox = com.craftflowtechnologies.meetingmind.core.work.InboxRepository(database)
    val inboxItems: StateFlow<List<com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity>> = inbox.observeOpen().state(emptyList())
    val inboxFiledItems: StateFlow<List<com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity>> = inbox.observeFiled().state(emptyList())
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
        val added = inbox.add(parts) { com.craftflowtechnologies.meetingmind.core.work.ShareIn.copy(getApplication(), it) }
        added.forEach { proposeInbox(it) }
    }
    /** Imports a single file URI (e.g. a WhatsApp voice note, video, or PDF) directly into the Work Inbox. */
    fun importUriToInbox(uri: android.net.Uri, sourceHint: String? = null, onAdded: (Int) -> Unit = {}) = viewModelScope.launch {
        val added = com.craftflowtechnologies.meetingmind.core.work.ShareIn.importUriToInbox(getApplication(), uri, sourceHint)
        added.forEach { proposeInbox(it) }
        onAdded(added.size)
    }
    /** Adds pasted text or a link directly into the Work Inbox. */
    fun addTextToInbox(text: String, title: String? = null, source: String = "Quick Capture") = viewModelScope.launch {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@launch
        val isUrl = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        val host = if (isUrl) trimmed.substringAfter("://").substringBefore('/').removePrefix("www.") else null
        val part = com.craftflowtechnologies.meetingmind.core.work.SharedPart(
            kind = if (isUrl) com.craftflowtechnologies.meetingmind.core.work.InboxKind.URL else com.craftflowtechnologies.meetingmind.core.work.InboxKind.TEXT,
            text = trimmed,
            title = title?.trim()?.ifBlank { null } ?: host ?: trimmed.lineSequence().first().take(60),
            source = host ?: source
        )
        val added = inbox.add(listOf(part))
        added.forEach { proposeInbox(it) }
    }
    /** Starts 1-tap background transcription for an audio, video, or YouTube URL item right inside the Inbox. */
    fun transcribeInboxItem(
        item: com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) = viewModelScope.launch(Dispatchers.IO) {
        val ytParsed = com.craftflowtechnologies.meetingmind.core.youtube.YouTubeUrlParser.parse(item.text ?: item.uri)
        if (ytParsed != null) {
            try {
                inbox.updateTranscription(item.id, "TRANSCRIBING")
                val app = getApplication<Application>()
                val meetingId = java.util.UUID.randomUUID().toString()
                val initialTitle = item.title?.takeIf { it.isNotBlank() && !it.startsWith("http", true) }
                    ?: "YouTube Video (${ytParsed.videoId})"
                val meetingRepo = com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository(app, database)
                val meeting = meetingRepo.createInitialMeeting(
                    id = meetingId,
                    title = initialTitle,
                    source = com.craftflowtechnologies.meetingmind.core.model.MeetingSource.YOUTUBE_VIDEO,
                    audioFilePath = ytParsed.canonicalWatchUrl,
                    recordingType = com.craftflowtechnologies.meetingmind.core.model.RecordingType.GENERAL,
                    speakerCountPreference = 1,
                    titleSource = com.craftflowtechnologies.meetingmind.core.model.TitleSource.AI
                )
                inbox.updateTranscription(
                    id = item.id,
                    status = "TRANSCRIBING",
                    meetingId = meeting.id
                )
                com.craftflowtechnologies.meetingmind.ai.pipeline.ProcessingScheduler.enqueue(
                    context = app,
                    meetingId = meeting.id,
                    audioPath = ytParsed.canonicalWatchUrl,
                    durationMs = 0L,
                    expectedSpeakerCount = 1
                )
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(true, meeting.id) }
            } catch (e: Exception) {
                inbox.updateTranscription(item.id, "FAILED")
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(false, e.localizedMessage ?: "Failed to start YouTube extraction") }
            }
            return@launch
        }
        val path = item.uri?.takeIf { it.isNotBlank() } ?: run {
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(false, "Audio file is missing") }
            return@launch
        }
        val file = java.io.File(path)
        if (!file.exists()) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(false, "Audio file could not be found") }
            return@launch
        }
        try {
            inbox.updateTranscription(item.id, "TRANSCRIBING")
            val app = getApplication<Application>()
            val meetingId = java.util.UUID.randomUUID().toString()
            val extractor = com.craftflowtechnologies.meetingmind.core.audio.AudioExtractor(app)
            val info = extractor.importAndExtract(android.net.Uri.fromFile(file), meetingId) {}
            val importedName = (item.title?.takeIf { it.isNotBlank() } ?: info.fileName.substringBeforeLast("."))
            val meetingRepo = com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository(app, database)
            val meeting = meetingRepo.createInitialMeeting(
                id = meetingId,
                title = importedName,
                source = if (info.isVideo) com.craftflowtechnologies.meetingmind.core.model.MeetingSource.IMPORTED_VIDEO else com.craftflowtechnologies.meetingmind.core.model.MeetingSource.IMPORTED_AUDIO,
                audioFilePath = info.outputFile.absolutePath,
                titleSource = if (com.craftflowtechnologies.meetingmind.core.common.MeetingTitleGenerator.isUsableImportedTitle(importedName)) {
                    com.craftflowtechnologies.meetingmind.core.model.TitleSource.IMPORT
                } else {
                    com.craftflowtechnologies.meetingmind.core.model.TitleSource.AI
                }
            )
            inbox.updateTranscription(
                id = item.id,
                status = "TRANSCRIBING",
                meetingId = meeting.id,
                durationMs = info.durationMs.takeIf { it > 0L }
            )
            com.craftflowtechnologies.meetingmind.ai.pipeline.ProcessingScheduler.enqueue(
                context = app,
                meetingId = meeting.id,
                audioPath = info.outputFile.absolutePath,
                durationMs = info.durationMs
            )
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(true, meeting.id) }
        } catch (e: Exception) {
            inbox.updateTranscription(item.id, "FAILED")
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(false, e.localizedMessage ?: "Failed to start transcription") }
        }
    }
    /** Updates transcription status, text, meeting link and automatically re-proposes filing on completion. */
    fun updateInboxTranscription(
        id: String,
        status: String,
        transcript: String? = null,
        meetingId: String? = null,
        durationMs: Long? = null
    ) = viewModelScope.launch {
        inbox.updateTranscription(id, status, transcript, meetingId, durationMs)
        if (status == "COMPLETED" && !transcript.isNullOrBlank()) {
            inbox.get(id)?.let { updated -> proposeInbox(updated) }
        }
    }

    /**
     * Live map from Inbox item ID to its linked Meeting, Note, AI Summary, and extracted Tasks/Decisions.
     * Also reconciles any Inbox item whose linked Meeting finished processing in the background.
     */
    val inboxLinkedMeetings: StateFlow<Map<String, InboxLinkedMeeting>> = combine(
        inboxItems,
        inboxFiledItems,
        database.meetingDao().getAllMeetings()
    ) { open, filed, allMeetings ->
        val byId = allMeetings.associateBy { it.id }
        val allInbox = open + filed
        val result = mutableMapOf<String, InboxLinkedMeeting>()
        for (item in allInbox) {
            val mid = item.meetingId
            val filedNoteId = runCatching {
                item.resultRefJson?.let { org.json.JSONObject(it).optString("noteId").ifBlank { null } }
            }.getOrNull()
            val meeting = mid?.let { byId[it] }
            if (meeting != null) {
                val actions = runCatching {
                    database.actionItemDao().getActionItemsForMeetingDirect(meeting.id).map { it.task }
                }.getOrDefault(emptyList())
                val decisions = runCatching {
                    database.decisionDao().getDecisionsForMeetingDirect(meeting.id).map { it.text }
                }.getOrDefault(emptyList())
                val segments = if (meeting.status == "READY" && (item.transcriptionStatus != "COMPLETED" || item.text.isNullOrBlank())) {
                    runCatching { database.transcriptDao().getSegmentsForMeetingDirect(meeting.id) }.getOrDefault(emptyList())
                } else emptyList()
                val transcriptText = segments.joinToString("\n") { (it.cleanedText ?: it.text).trim() }.trim()
                if (meeting.status == "READY" && item.transcriptionStatus != "COMPLETED") {
                    val syncedText = transcriptText.ifBlank { item.text ?: meeting.summaryText }
                    inbox.updateTranscription(
                        id = item.id,
                        status = "COMPLETED",
                        transcript = syncedText,
                        meetingId = meeting.id,
                        durationMs = meeting.durationMs.takeIf { it > 0L }
                    )
                    inbox.get(item.id)?.let { updated ->
                        if (updated.status == com.craftflowtechnologies.meetingmind.core.work.InboxStatus.NEW.name) {
                            runCatching { proposeInbox(updated) }
                        }
                    }
                } else if (meeting.status == "ERROR" && item.transcriptionStatus == "TRANSCRIBING") {
                    inbox.updateTranscription(id = item.id, status = "FAILED")
                }
                result[item.id] = InboxLinkedMeeting(
                    meetingId = meeting.id,
                    noteId = meeting.noteId ?: filedNoteId,
                    title = meeting.title,
                    summaryText = meeting.summaryText?.takeIf { it.isNotBlank() },
                    transcriptPreview = item.text?.takeIf { it.isNotBlank() } ?: transcriptText.takeIf { it.isNotBlank() },
                    status = meeting.status,
                    audioFilePath = meeting.audioFilePath ?: item.uri,
                    durationMs = meeting.durationMs.takeIf { it > 0L } ?: item.mediaDurationMs ?: 0L,
                    participantCount = meeting.participantCount,
                    actionItems = actions,
                    decisions = decisions
                )
            } else if (filedNoteId != null) {
                result[item.id] = InboxLinkedMeeting(
                    meetingId = null,
                    noteId = filedNoteId,
                    title = item.title ?: "Filed note",
                    summaryText = null,
                    transcriptPreview = item.text,
                    status = "FILED",
                    audioFilePath = item.uri,
                    durationMs = item.mediaDurationMs ?: 0L,
                    participantCount = 0
                )
            }
        }
        result
    }.state(emptyMap())

    val context = ContextRepository(database)
    val prepare = Prepare(database)
    val savedViews = SavedViews(database)
    private val pulseEngine = Pulse(database)
    private val calendarEvents = MutableStateFlow<List<PulseEvent>>(emptyList())

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

    private companion object {
        const val NOT_SAME = "not_same_people"
    }

    // ---------------------------------------------------------------- views (W14)

    val viewQueries = ViewQueries(database)
    val xrayBuilder = XRayBuilder(database)

    suspend fun loadProjectKanban(projectId: String): KanbanBoardData = viewQueries.projectKanban(projectId)
    suspend fun loadProjectTimeline(projectId: String): List<TimelineSection> = viewQueries.projectTimeline(projectId)
    suspend fun loadProjectXRay(projectId: String): XRayGraph = xrayBuilder.build(projectId)
    suspend fun loadAllRisks(projectId: String? = null): List<RiskItem> = viewQueries.risks(projectId)

    fun moveKanbanCard(card: KanbanCard, targetCol: KanbanColumnType) = viewModelScope.launch {
        if (card.isTask) {
            val shouldBeDone = (targetCol == KanbanColumnType.DONE)
            val currentTask = database.taskDao().getById(card.id)
            val isCurrentlyDone = currentTask?.doneAt != null
            if (shouldBeDone != isCurrentlyDone) {
                work.toggle(card.id)
            }
        } else {
            val targetStatus = when (targetCol) {
                KanbanColumnType.TO_DO -> ItemStatus.OPEN
                KanbanColumnType.IN_PROGRESS -> if (card.itemKind == ItemKind.DECISION) ItemStatus.ACTIVE else ItemStatus.OPEN
                KanbanColumnType.DONE -> when (card.itemKind) {
                    ItemKind.QUESTION -> ItemStatus.ANSWERED
                    ItemKind.DECISION -> ItemStatus.ACTIVE
                    ItemKind.COMMITMENT -> ItemStatus.COMPLETED
                    else -> ItemStatus.CLOSED
                }
            }
            ItemRepository(database).setStatus(card.id, targetStatus)
        }
    }
}
