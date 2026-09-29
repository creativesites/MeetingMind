package com.example.feature.work

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.ItemEntity
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.work.ItemKind
import com.example.core.work.ItemStatus
import com.example.core.work.MeetingRow
import com.example.core.work.PeopleRepository
import com.example.core.work.Person
import com.example.core.work.WorkItem
import com.example.core.work.WorkRepository
import com.example.core.work.WorkSettings
import com.example.core.work.WorkView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Work tab, person pages and the Work Home read from here (docs/PLAN_PROFESSIONAL.md §7).
 * Everything is a live view of the database, so a rename or a tick anywhere shows everywhere.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val prefs = UserPreferencesManager(application)
    val work = WorkRepository(database)
    val people = PeopleRepository(database)

    val settings: StateFlow<WorkSettings> = prefs.workSettings.stateIn(viewModelScope, SharingStarted.Eagerly, WorkSettings())

    private val _self = MutableStateFlow<Person?>(null)
    val self: StateFlow<Person?> = _self

    val open: StateFlow<List<WorkItem>> = work.observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val decisions: StateFlow<List<WorkItem>> = work.observeDecisions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val everyone: StateFlow<List<Person>> = people.observePeople().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val organisations: StateFlow<List<Person>> = people.observeOrganisations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recordings with findings nobody has looked at yet (Home → To review). */
    val toReview: StateFlow<List<MeetingRow>> = work.observeUnreviewedMeetingIds()
        .mapLatest { ids -> ids.mapNotNull { work.meetingRow(it) }.sortedByDescending { it.at } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Reviewed work recordings whose follow-up hasn't gone (Home → Follow-ups to send). */
    val followUps: StateFlow<List<MeetingRow>> = combine(open, toReview) { _, _ -> Unit }
        .mapLatest { work.followUpsToSend() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Meeting titles for item context lines. */
    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    val titles: StateFlow<Map<String, String>> = _titles

    init {
        viewModelScope.launch {
            val name = prefs.preferencesFlow.first().userName
            _self.value = people.self(name)
        }
        viewModelScope.launch {
            database.meetingDao().getAllMeetings().collect { list -> _titles.value = list.associate { it.id to it.title } }
        }
    }

    fun view(items: List<WorkItem>, view: WorkView): List<WorkItem> = items.filter { item ->
        val v = if (item.kind == ItemKind.TASK && item.ownerPersonId != null && item.ownerPersonId == _self.value?.id) WorkView.MY_TASKS else item.view
        v == view
    }

    fun toggle(item: WorkItem) = viewModelScope.launch {
        work.setStatus(item.id, if (item.status == ItemStatus.OPEN) ItemStatus.DONE else ItemStatus.OPEN)
    }

    fun drop(item: WorkItem) = viewModelScope.launch { work.setStatus(item.id, ItemStatus.DROPPED) }
    fun snooze(item: WorkItem, days: Int = 1) = viewModelScope.launch { work.snooze(item.id, days) }
    fun setText(item: WorkItem, text: String) = viewModelScope.launch { work.setText(item.id, text) }
    fun setKind(item: WorkItem, kind: ItemKind) = viewModelScope.launch { work.setKind(item.id, kind) }
    fun setDue(item: WorkItem, at: Long?, text: String?) = viewModelScope.launch { work.setDue(item.id, at, text) }
    fun setAnswer(item: WorkItem, answer: String) = viewModelScope.launch { work.setAnswer(item.id, answer) }

    fun setOwner(item: WorkItem, choice: OwnerChoice?) = viewModelScope.launch {
        when {
            choice == null -> work.setOwner(item.id)
            choice.speakerId != null -> work.setOwner(item.id, speakerId = choice.speakerId)
            choice.personId != null -> work.setOwner(item.id, personId = choice.personId)
            choice.name != null -> {
                // A typed name becomes a person, so it can be renamed and found later.
                val p = people.resolve(choice.name)
                work.setOwner(item.id, personId = p?.id, name = choice.name)
            }
        }
    }

    private var lastDeleted: ItemEntity? = null
    fun delete(item: WorkItem) = viewModelScope.launch { lastDeleted = work.delete(item.id) }
    fun undoDelete() = viewModelScope.launch { lastDeleted?.let { work.restore(it) }; lastDeleted = null }

    fun addTask(text: String, due: String? = null, ownerPersonId: String? = null) = viewModelScope.launch {
        if (text.isNotBlank()) work.add(text, ItemKind.TASK, ownerPersonId = ownerPersonId, dueText = due)
    }

    // People

    fun person(id: String): Flow<Person?> = people.observe(id)
    fun itemsFor(personId: String): Flow<List<WorkItem>> = work.observeForPerson(personId)
    fun members(orgId: String): Flow<List<Person>> = people.observeMembers(orgId)

    /** Notes this person is in, newest first, with their recordings' titles. */
    fun notesFor(personId: String): Flow<List<com.example.core.model.Note>> = people.observeNoteIds(personId).mapLatest { ids ->
        ids.mapNotNull { database.noteDao().getById(it) }.filter { it.archivedAt == null }
            .sortedByDescending { it.eventDate ?: it.createdAt }
            .map { with(com.example.core.repository.NoteCodec) { it.toDomain() } }
    }

    /** Who owns an item, as a person: directly, or through the speaker they were. */
    suspend fun ownerOf(item: WorkItem): Person? {
        item.ownerPersonId?.let { return people.get(it) }
        val speakerPerson = item.ownerSpeakerId?.let { sid ->
            item.meetingId?.let { m -> database.speakerDao().getSpeakersForMeetingDirect(m).firstOrNull { it.id == sid }?.personId }
        }
        return speakerPerson?.let { people.get(it) } ?: item.ownerName?.let { people.resolve(it, create = false) }
    }

    fun rememberChannel(person: Person, channel: com.example.core.work.Channel) = viewModelScope.launch { people.rememberChannel(person.id, channel) }

    fun rename(person: Person, name: String) = viewModelScope.launch { people.rename(person.id, name) }
    fun updatePerson(person: Person) = viewModelScope.launch { people.update(person) }
    fun merge(from: Person, into: Person) = viewModelScope.launch { people.merge(from.id, into.id) }
    fun deletePerson(person: Person) = viewModelScope.launch { people.delete(person.id) }
    fun setOrganisation(person: Person, orgName: String?) = viewModelScope.launch {
        val org = orgName?.trim()?.takeIf { it.isNotEmpty() }?.let { people.createOrganisation(it) }
        people.update(person.copy(orgId = org?.id))
    }

    private val state = application.getSharedPreferences("work_state", android.content.Context.MODE_PRIVATE)
    private val notSame = MutableStateFlow(state.getStringSet(NOT_SAME, emptySet()).orEmpty())

    /** "Same person?" suggestions, minus pairs the person said are different. */
    val duplicates: StateFlow<List<Pair<Person, Person>>> = combine(everyone, notSame) { _, dismissed ->
        people.likelyDuplicates().filter { (a, b) -> pairKey(a, b) !in dismissed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markDifferent(a: Person, b: Person) {
        val next = notSame.value + pairKey(a, b)
        state.edit().putStringSet(NOT_SAME, next).apply()
        notSame.value = next
    }

    private fun pairKey(a: Person, b: Person) = listOf(a.id, b.id).sorted().joinToString("|")

    private companion object { const val NOT_SAME = "not_same_people" }

    fun updateSettings(change: (WorkSettings) -> WorkSettings) = viewModelScope.launch { prefs.updateWorkSettings(change) }
}
