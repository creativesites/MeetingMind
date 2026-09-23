package com.example.feature.faith

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.MeetMindDatabase
import com.example.core.database.NameCount
import com.example.core.model.Attachment
import com.example.core.model.Note
import com.example.core.model.NoteStatus
import com.example.core.model.RecordingType
import com.example.core.model.ScriptureCollection
import com.example.core.model.ScriptureRef
import com.example.core.model.Workflows
import com.example.core.repository.NoteRepository
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** One month of the journey: how many of each kind, and the notes themselves. */
data class JourneyMonth(val label: String, val counts: Map<RecordingType, Int>, val notes: List<Note>)

/** Today's verse and its text, when it could be fetched. */
data class VerseOfTheDay(val reference: ScriptureReference, val passage: PassageResult?)

/**
 * The Faith space (docs/PLAN_V1.md §6): today's verse, what to pray about, the person's journey,
 * and quick ways to start each Faith note. Everything here is read from the person's own notes and
 * recordings — nothing is suggested, nothing is interpreted.
 */
class FaithViewModel(application: Application) : AndroidViewModel(application) {

    private val notes = NoteRepository(application, MeetMindDatabase.getInstance(application))
    private val scripture = ScriptureService(application)

    val faithNotes: StateFlow<List<Note>> = notes.observeNotesForWorkflows(Workflows.faith)
        .map { list -> list.sortedByDescending { it.eventDate ?: it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openRequests: StateFlow<List<Note>> = faithNotes
        .map { list -> list.filter { it.workflow == RecordingType.PRAYER_REQUEST && it.status == NoteStatus.OPEN }.sortedBy { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val answeredCount: StateFlow<Int> = faithNotes
        .map { list -> list.count { it.workflow == RecordingType.PRAYER_REQUEST && it.status == NoteStatus.ANSWERED } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Faith notes from today's date in earlier years. */
    val onThisDay: StateFlow<List<Note>> = faithNotes.map { list ->
        val today = Calendar.getInstance()
        list.filter { n ->
            val c = Calendar.getInstance().apply { timeInMillis = n.eventDate ?: n.createdAt }
            c.get(Calendar.MONTH) == today.get(Calendar.MONTH) && c.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH) &&
                c.get(Calendar.YEAR) < today.get(Calendar.YEAR)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val journey: StateFlow<List<JourneyMonth>> = faithNotes.map { list -> journeyOf(list) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val themes: StateFlow<List<NameCount>> = notes.observeThemes(Workflows.faith)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val collections: StateFlow<List<ScriptureCollection>> = notes.observeScriptureCollections()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allScripture: StateFlow<List<ScriptureRef>> = notes.observeAllScriptureRefs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val media: StateFlow<List<Attachment>> = notes.observeFaithMedia()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _votd = MutableStateFlow<VerseOfTheDay?>(null)
    val verseOfTheDay: StateFlow<VerseOfTheDay?> = _votd.asStateFlow()

    init { loadVerseOfTheDay() }

    fun loadVerseOfTheDay() = viewModelScope.launch {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val ref = scripture.verseOfTheDay(day) ?: return@launch
        _votd.value = VerseOfTheDay(ref, null)
        _votd.value = VerseOfTheDay(ref, scripture.passage(ref))
    }

    /** Starts a Faith note of [type] from its template, and hands back its id. */
    fun create(type: RecordingType, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(notes.createNote(workflow = type).id)
    }

    fun startDevotional(reference: ScriptureReference, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(notes.startDevotional(reference).id)
    }

    fun markAnswered(request: Note, onTestimony: (String) -> Unit) = viewModelScope.launch {
        notes.markAnswered(request.id)
        onTestimony(notes.startTestimony(request.id).id)
    }

    fun notesMatching(theme: String): List<Note> = faithNotes.value.filter {
        it.title.contains(theme, ignoreCase = true) || it.plainText.contains(theme, ignoreCase = true)
    }

    companion object {
        fun journeyOf(list: List<Note>): List<JourneyMonth> {
            val fmt = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            return list.groupBy { n ->
                val c = Calendar.getInstance().apply { timeInMillis = n.eventDate ?: n.createdAt }
                c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
            }.toSortedMap(compareByDescending { it }).map { (_, monthNotes) ->
                JourneyMonth(
                    label = fmt.format(java.util.Date(monthNotes.first().eventDate ?: monthNotes.first().createdAt)),
                    counts = monthNotes.groupingBy { it.workflow }.eachCount(),
                    notes = monthNotes
                )
            }
        }
    }
}
