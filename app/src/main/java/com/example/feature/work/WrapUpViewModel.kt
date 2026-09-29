package com.example.feature.work

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.ItemEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.Note
import com.example.core.model.Notebook
import com.example.core.model.NotebookSpace
import com.example.core.model.Speaker
import com.example.core.repository.NoteRepository
import com.example.core.repository.TranscriptRepository
import com.example.core.work.Channel
import com.example.core.work.FollowUpWriter
import com.example.core.work.ItemKind
import com.example.core.work.ItemStatus
import com.example.core.work.OutgoingMessage
import com.example.core.work.PeopleRepository
import com.example.core.work.Person
import com.example.core.work.SpeakerNames
import com.example.core.work.Tone
import com.example.core.work.WorkItem
import com.example.core.work.WorkRepository
import com.example.core.work.WorkSettings
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
 * The Wrap-up (docs/PLAN_PROFESSIONAL.md §4.3): a recording's findings, pre-checked, to confirm
 * in under a minute, then the follow-up. Every edit is live: renaming a speaker here renames them
 * in every item, the summary and the note at once.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WrapUpViewModel(application: Application, val meetingId: String) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val prefs = UserPreferencesManager(application)
    private val transcripts = TranscriptRepository(database)
    private val notesRepo = NoteRepository(application, database)
    val work = WorkRepository(database)
    val people = PeopleRepository(database)

    val meeting: StateFlow<MeetingEntity?> = database.meetingDao().getMeetingByIdFlow(meetingId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val items: StateFlow<List<WorkItem>> = work.observeForMeeting(meetingId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val speakers: StateFlow<List<Speaker>> = transcripts.getSpeakers(meetingId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val note: StateFlow<Note?> = meeting.flatMapLatest { m -> m?.noteId?.let { notesRepo.observeNote(it) } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val notebooks: StateFlow<List<Notebook>> = notesRepo.observeNotebooks()
        .map { list -> list.filter { it.space == NotebookSpace.WORK || it.isProject } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<WorkSettings> = prefs.workSettings.stateIn(viewModelScope, SharingStarted.Eagerly, WorkSettings())
    val everyone: StateFlow<List<Person>> = people.observePeople().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _self = MutableStateFlow<Person?>(null)
    val self: StateFlow<Person?> = _self

    /** Who the recording was with: attendees and named speakers, as people. */
    private val _with = MutableStateFlow<List<Person>>(emptyList())
    val with: StateFlow<List<Person>> = _with

    init {
        viewModelScope.launch { _self.value = people.self(prefs.preferencesFlow.first().userName) }
        viewModelScope.launch { everyone.collect { refreshWith() } }
        viewModelScope.launch { speakers.collect { refreshWith() } }
    }

    private suspend fun refreshWith() {
        val noteId = meeting.value?.noteId ?: database.meetingDao().getMeetingById(meetingId)?.noteId ?: return
        val ids = database.peopleDao().getNoteLinks(noteId).map { it.personId }.toSet()
        _with.value = ids.mapNotNull { people.get(it) }.filter { !it.isSelf }.sortedBy { it.name }
    }

    fun toggle(item: WorkItem) = viewModelScope.launch {
        work.setStatus(item.id, if (item.status == ItemStatus.OPEN) ItemStatus.DONE else ItemStatus.OPEN)
    }
    fun setText(item: WorkItem, text: String) = viewModelScope.launch { work.setText(item.id, text) }
    fun setKind(item: WorkItem, kind: ItemKind) = viewModelScope.launch { work.setKind(item.id, kind) }
    fun setDue(item: WorkItem, at: Long?, text: String?) = viewModelScope.launch { work.setDue(item.id, at, text) }
    fun setAnswer(item: WorkItem, answer: String) = viewModelScope.launch { work.setAnswer(item.id, answer) }
    fun setOwner(item: WorkItem, choice: OwnerChoice?) = viewModelScope.launch {
        when {
            choice == null -> work.setOwner(item.id)
            choice.speakerId != null -> work.setOwner(item.id, speakerId = choice.speakerId)
            choice.personId != null -> work.setOwner(item.id, personId = choice.personId)
            choice.name != null -> work.setOwner(item.id, personId = people.resolve(choice.name)?.id, name = choice.name)
        }
    }

    private var lastDismissed: ItemEntity? = null
    /** Swipe away: a wrong finding is removed, with undo. */
    fun dismiss(item: WorkItem) = viewModelScope.launch { lastDismissed = work.delete(item.id) }
    fun undoDismiss() = viewModelScope.launch { lastDismissed?.let { work.restore(it) }; lastDismissed = null }

    fun add(text: String, kind: ItemKind) = viewModelScope.launch {
        if (text.isNotBlank()) work.add(text, kind, meetingId = meetingId, ownerPersonId = if (kind == ItemKind.TASK) _self.value?.id else null)
    }

    /** Names a speaker. Their name then shows in every item, the summary and the note (§5.5). */
    fun renameSpeaker(speaker: Speaker, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) transcripts.renameSpeaker(meetingId, speaker.id, name.trim())
    }

    /** "That's me": the speaker is the app's own user. */
    fun speakerIsMe(speaker: Speaker) = viewModelScope.launch {
        val me = _self.value ?: return@launch
        transcripts.renameSpeaker(meetingId, speaker.id, me.name)
        database.peopleDao().linkSpeaker(speaker.id, me.id)
    }

    fun setProject(notebookId: String?) = viewModelScope.launch {
        meeting.value?.noteId?.let { work.setProject(it, notebookId) }
    }

    fun newProject(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val s = settings.value
        val nb = notesRepo.createNotebook(name, NotebookSpace.WORK, isProject = true, confidential = s.keepOnDevice)
        meeting.value?.noteId?.let { work.setProject(it, nb.id) }
    }

    fun done() = viewModelScope.launch { work.markReviewed(meetingId) }

    fun addContact(person: Person, email: String?, phone: String?) = viewModelScope.launch {
        people.addContact(person.id, email, phone)
        refreshWith()
    }

    fun followUp(channel: Channel, tone: Tone): OutgoingMessage {
        val all = items.value.filter { it.status != ItemStatus.DROPPED }
        val self = _self.value
        fun mine(i: WorkItem) = i.ownerIsSelf || (self != null && i.ownerPersonId == self.id) ||
            (i.ownerSpeakerId == null && i.ownerPersonId == null && i.ownerName == null)
        val tasks = all.filter { it.kind == ItemKind.TASK && it.status == ItemStatus.OPEN }
        val m = meeting.value
        return FollowUpWriter.compose(
            FollowUpWriter.Input(
                meetingTitle = m?.title.orEmpty(),
                meetingAt = m?.createdAt ?: System.currentTimeMillis(),
                recipients = with.value,
                decisions = all.filter { it.kind == ItemKind.DECISION },
                myTasks = tasks.filter(::mine),
                theirTasks = tasks.filterNot(::mine),
                questions = all.filter { it.kind == ItemKind.QUESTION && it.status == ItemStatus.OPEN },
                senderName = self?.name?.takeIf { it != "Me" },
                settings = settings.value,
                tone = tone
            ),
            channel
        )
    }

    fun markSent(channel: Channel) = viewModelScope.launch {
        work.markFollowUpSent(meetingId, channel)
        with.value.forEach { people.rememberChannel(it.id, channel) }
    }

    fun skipFollowUp() = viewModelScope.launch { work.skipFollowUp(meetingId) }

    /** Speakers still called "Speaker 1" and the like. */
    fun unnamed(list: List<Speaker>) = list.filter { SpeakerNames.isGenericLabel(it.customName.ifBlank { it.originalLabel }) }
}
