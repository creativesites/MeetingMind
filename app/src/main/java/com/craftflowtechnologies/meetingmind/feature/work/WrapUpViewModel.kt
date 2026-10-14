package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.repository.NoteCodec
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.repository.TranscriptRepository
import com.craftflowtechnologies.meetingmind.core.work.Channel
import com.craftflowtechnologies.meetingmind.core.work.Finding
import com.craftflowtechnologies.meetingmind.core.work.FindingKind
import com.craftflowtechnologies.meetingmind.core.work.FollowUpLine
import com.craftflowtechnologies.meetingmind.core.work.FollowUpWriter
import com.craftflowtechnologies.meetingmind.core.work.OutgoingMessage
import com.craftflowtechnologies.meetingmind.core.work.SpeakerNames
import com.craftflowtechnologies.meetingmind.core.work.Tone
import com.craftflowtechnologies.meetingmind.core.work.WorkPeople
import com.craftflowtechnologies.meetingmind.core.work.WorkPerson
import com.craftflowtechnologies.meetingmind.core.work.WorkRepository
import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Wrap-up (docs/PLAN_PROFESSIONAL.md §4.3): a recording's findings, pre-checked, to confirm in
 * under a minute, then the follow-up. Every edit is live: naming a speaker here names them in the
 * summary, the findings and the note at once.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WrapUpViewModel(application: Application, val meetingId: String) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val prefs = UserPreferencesManager(application)
    private val transcripts = TranscriptRepository(database)
    private val notesRepo = NoteRepository(application, database)
    private val work = WorkRepository(database)
    private val people = WorkPeople(database)

    val meeting: StateFlow<MeetingEntity?> = database.meetingDao().getMeetingByIdFlow(meetingId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val speakers: StateFlow<List<Speaker>> = transcripts.getSpeakers(meetingId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val note: StateFlow<Note?> = meeting.flatMapLatest { m -> m?.noteId?.let { notesRepo.observeNote(it) } ?: flowOf(null) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val projects: StateFlow<List<Notebook>> = database.workDao().observeProjects().map { l -> l.map { with(NoteCodec) { it.toDomain() } } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<WorkSettings> = prefs.workSettings.stateIn(viewModelScope, SharingStarted.Eagerly, WorkSettings())

    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    val findings: StateFlow<List<Finding>> = _findings
    private val _self = MutableStateFlow<WorkPerson?>(null)
    val self: StateFlow<WorkPerson?> = _self
    /** Who the recording was with: guests and named speakers, as people. */
    private val _with = MutableStateFlow<List<WorkPerson>>(emptyList())
    val with: StateFlow<List<WorkPerson>> = _with

    // ---------------------------------------------------------------- changes and extra signals (D6)

    private val detector = com.craftflowtechnologies.meetingmind.core.work.ChangeDetector(
        database,
        com.craftflowtechnologies.meetingmind.core.work.DeviceChangeModels(
            application, com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(application),
            com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(application)
        ) { prefs.preferencesFlow.first().processingProfile }
    )
    private val _changes = MutableStateFlow<List<com.craftflowtechnologies.meetingmind.core.work.ChangeProposal>>(emptyList())
    /** What this recording changes, waiting for the person to confirm or turn down. */
    val changes: StateFlow<List<com.craftflowtechnologies.meetingmind.core.work.ChangeProposal>> = _changes
    private val _extras = MutableStateFlow(com.craftflowtechnologies.meetingmind.core.work.WrapUpExtras(emptyList(), emptyList()))
    val extras: StateFlow<com.craftflowtechnologies.meetingmind.core.work.WrapUpExtras> = _extras
    private val _confirmed = MutableStateFlow<Set<String>>(emptySet())
    val confirmedChanges: StateFlow<Set<String>> = _confirmed
    private val _added = MutableStateFlow<Set<String>>(emptySet())
    val added: StateFlow<Set<String>> = _added
    private val _settled = MutableStateFlow<Map<String, com.craftflowtechnologies.meetingmind.core.work.Settlement>>(emptyMap())
    val settled: StateFlow<Map<String, com.craftflowtechnologies.meetingmind.core.work.Settlement>> = _settled

    private fun dismissedIds() = com.craftflowtechnologies.meetingmind.core.work.WrapUpSignals.dismissed(getApplication(), meetingId)

    private suspend fun refreshSignals() {
        val dismissed = dismissedIds()
        val proposals = detector.detect(meetingId, dismissed)
        _changes.value = proposals
        _extras.value = com.craftflowtechnologies.meetingmind.core.work.WrapUpSignals.extras(database, meetingId, work.findings(meetingId), proposals, dismissed)
    }

    fun confirmChange(p: com.craftflowtechnologies.meetingmind.core.work.ChangeProposal) { _confirmed.value = _confirmed.value + p.id }
    fun undoChange(p: com.craftflowtechnologies.meetingmind.core.work.ChangeProposal) { _confirmed.value = _confirmed.value - p.id }
    /** "Not the same": the proposal doesn't come back. */
    fun rejectChange(p: com.craftflowtechnologies.meetingmind.core.work.ChangeProposal) = act {
        com.craftflowtechnologies.meetingmind.core.work.WrapUpSignals.dismiss(getApplication(), meetingId, p.id)
    }
    fun addSignal(id: String) { _added.value = _added.value + id }
    fun dismissSignal(id: String) = act { com.craftflowtechnologies.meetingmind.core.work.WrapUpSignals.dismiss(getApplication(), meetingId, id) }
    fun settle(id: String, s: com.craftflowtechnologies.meetingmind.core.work.Settlement) { _settled.value = _settled.value + (id to s) }

    private fun choices() = com.craftflowtechnologies.meetingmind.core.work.WrapUpChoices(
        changes = _confirmed.value, add = _added.value, dismissed = dismissedIds(), settled = _settled.value
    )

    init {
        viewModelScope.launch { _self.value = people.self(prefs.preferencesFlow.first().userName) }
        viewModelScope.launch { speakers.collect { refresh() } }
    }

    fun refresh() = viewModelScope.launch {
        _findings.value = work.findings(meetingId)
        runCatching { refreshSignals() }
        val noteId = database.meetingDao().getMeetingById(meetingId)?.noteId ?: return@launch
        _with.value = database.workDao().peopleIdsFor(noteId).mapNotNull { people.get(it) }.filter { !it.isSelf }.sortedBy { it.name }
    }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch { block(); refresh() }

    fun setText(f: Finding, text: String) = act { work.setText(f, text) }
    fun setKind(f: Finding, kind: FindingKind) = act { work.setKind(f, kind) }
    fun setDue(f: Finding, text: String?) = act { work.setDue(f, text) }
    fun setAnswer(f: Finding, answer: String) = act { work.setAnswer(f, answer) }
    fun add(text: String, kind: FindingKind) = act { work.addFinding(meetingId, text, kind) }
    fun setOwner(f: Finding, choice: OwnerChoice?) = act {
        when {
            choice == null -> work.setOwner(f, null, null)
            choice.isSelf -> {
                // "Me": link the speaker who said it to the user, or just keep it as theirs.
                work.setOwner(f, null, null)
            }
            choice.speakerId != null -> work.setOwner(f, choice.speakerId, choice.label)
            else -> work.setOwner(f, null, choice.name)
        }
    }

    private var lastDismissed: Finding? = null
    fun dismiss(f: Finding) = act { work.dismiss(f); lastDismissed = f }
    fun undoDismiss() = act { lastDismissed?.let { work.addFinding(meetingId, it.text, it.kind) }; lastDismissed = null }

    /** Names a speaker. Their name then shows in every finding, the summary and the note (§5.5). */
    fun renameSpeaker(speaker: Speaker, name: String) = act { if (name.isNotBlank()) transcripts.renameSpeaker(meetingId, speaker.id, name.trim()) }

    /** "That's me": the speaker is the app's own user. */
    fun speakerIsMe(speaker: Speaker) = act {
        val me = _self.value ?: return@act
        if (!SpeakerNames.isGenericLabel(me.name) && me.name != "Me") transcripts.renameSpeaker(meetingId, speaker.id, me.name)
        database.workDao().linkSpeaker(speaker.id, me.id)
    }

    fun setProject(notebookId: String?) = act { meeting.value?.noteId?.let { work.setProject(it, notebookId) } }

    fun newProject(name: String) = act {
        if (name.isBlank()) return@act
        val nb = notesRepo.createNotebook(name, NotebookSpace.WORK, isProject = true,
            propertiesJson = if (settings.value.keepOnDevice) "{\"confidential\":true,\"status\":\"Active\"}" else "{\"status\":\"Active\"}")
        meeting.value?.noteId?.let { work.setProject(it, nb.id) }
    }

    fun done() = viewModelScope.launch { work.confirm(meetingId, choices()) }

    fun addContact(person: WorkPerson, email: String?, phone: String?) = act { people.addContact(person.id, email, phone) }

    fun followUp(channel: Channel, tone: Tone): OutgoingMessage {
        val all = findings.value
        val tasks = all.filter { it.kind == FindingKind.ACTION || it.kind == FindingKind.FOLLOW_UP }
        val m = meeting.value
        fun line(f: Finding) = FollowUpLine(f.text, f.ownerName, f.dueAt, f.dueText)
        return FollowUpWriter.compose(
            FollowUpWriter.Input(
                meetingTitle = m?.title.orEmpty(), meetingAt = m?.createdAt ?: System.currentTimeMillis(),
                recipients = with.value,
                decisions = all.filter { it.kind == FindingKind.DECISION }.map(::line),
                myTasks = tasks.filter { it.isMine }.map(::line),
                theirTasks = tasks.filter { !it.isMine }.map(::line),
                questions = all.filter { it.kind == FindingKind.QUESTION }.map(::line),
                senderName = _self.value?.name?.takeIf { it != "Me" },
                settings = settings.value, tone = tone
            ),
            channel
        )
    }

    fun markSent(channel: Channel) = viewModelScope.launch {
        work.confirm(meetingId, choices())
        work.markFollowUpSent(meetingId, channel)
        with.value.forEach { people.rememberChannel(it.id, channel) }
    }

    fun unnamed(list: List<Speaker>) = list.filter { SpeakerNames.isGenericLabel(it.customName.ifBlank { it.originalLabel }) }
}
