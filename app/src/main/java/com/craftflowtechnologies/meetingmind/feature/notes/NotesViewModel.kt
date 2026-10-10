package com.craftflowtechnologies.meetingmind.feature.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.Tag
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NoteSort(val label: String) { UPDATED("Last edited"), CREATED("Date"), TITLE("Title") }

/**
 * The space a note is filtered under. A Work workflow (meeting, interview, decision...) is always Work, even when
 * the note sits in "My Notes" because it had no project notebook; anything else follows its notebook's space.
 */
internal fun spaceOf(note: Note, notebookSpace: NotebookSpace?): NotebookSpace? =
    if (Workflows.space(note.workflow) == NotebookSpace.WORK) NotebookSpace.WORK else notebookSpace

/** Which notes the library is showing. */
sealed interface NotesScope {
    data object All : NotesScope
    data class InNotebook(val notebookId: String) : NotesScope
    data object Archived : NotesScope
    /** Deleted notes and notebooks, kept for [NoteRepository.TRASH_DAYS] days. */
    data object Trash : NotesScope
}

/**
 * The Notes library: every note, filtered by space, notebook, tag and text, and the notebooks
 * themselves. Filtering happens here, in memory — a person's notes number in the hundreds or low
 * thousands, and in-memory filtering keeps every chip instant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(application: Application, val scope: NotesScope = NotesScope.All) : AndroidViewModel(application) {

    private val notes = NoteRepository(application, MeetMindDatabase.getInstance(application))

    val space = MutableStateFlow<NotebookSpace?>(null)
    val tagFilter = MutableStateFlow<Tag?>(null)
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(NoteSort.UPDATED)

    val notebooks: StateFlow<List<Notebook>> = notes.observeNotebooks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notebookCounts: StateFlow<Map<String, Int>> = notes.observeNotebookCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val tags: StateFlow<List<Tag>> = notes.observeTags().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archivedCount: StateFlow<Int> = notes.observeArchivedNotes().map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val source = when (scope) {
        NotesScope.All -> notes.observeNotes()
        is NotesScope.InNotebook -> notes.observeNotesInNotebook(scope.notebookId)
        NotesScope.Archived -> notes.observeArchivedNotes()
        NotesScope.Trash -> notes.observeTrashedNotes()
    }

    val trashCount: StateFlow<Int> = notes.observeTrashedNotes().map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val trashedNotebooks: StateFlow<List<Notebook>> =
        (if (scope == NotesScope.Trash) notes.observeTrashedNotebooks() else kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val tagged = tagFilter.flatMapLatest { tag -> if (tag == null) source else notes.observeNotesWithTag(tag.id) }

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val visibleNotes: StateFlow<List<Note>> = combine(tagged, notebooks, space, query, sort) { list, books, space, q, sort ->
        _loaded.value = true
        val spaceOfNotebook = books.associate { it.id to it.space }
        list.asSequence()
            .filter { scope !is NotesScope.InNotebook || it.notebookId == scope.notebookId }
            .filter { space == null || spaceOf(it, spaceOfNotebook[it.notebookId]) == space }
            .filter { n -> q.isBlank() || n.title.contains(q, ignoreCase = true) || n.plainText.contains(q, ignoreCase = true) }
            .let { seq ->
                when (sort) {
                    NoteSort.UPDATED -> seq.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
                    NoteSort.CREATED -> seq.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.eventDate ?: it.createdAt })
                    NoteSort.TITLE -> seq.sortedWith(compareByDescending<Note> { it.pinned }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { "\uFFFF" } })
                }
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentNotebook: StateFlow<Notebook?> = notebooks.map { books ->
        (scope as? NotesScope.InNotebook)?.let { nb -> books.firstOrNull { it.id == nb.notebookId } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------------------ notebook AI

    private val noteAi = com.craftflowtechnologies.meetingmind.ai.notes.NoteAiRepository(application)

    val aiJobs: StateFlow<List<com.craftflowtechnologies.meetingmind.ai.notes.NoteAiJob>> =
        ((scope as? NotesScope.InNotebook)?.let { noteAi.observe(it.notebookId) } ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun runAi(tool: com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTool, question: String? = null) {
        val nb = (scope as? NotesScope.InNotebook)?.notebookId ?: return
        viewModelScope.launch { noteAi.run(com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTarget.NOTEBOOK, nb, tool, question) }
    }

    fun cancelAi(jobId: String) = viewModelScope.launch { noteAi.cancel(jobId) }
    fun dismissAi(jobId: String) = viewModelScope.launch { noteAi.dismiss(jobId) }

    /** Keeps a notebook result as a new note in this notebook, each point still pointing at its note. */
    fun saveAiResult(job: com.craftflowtechnologies.meetingmind.ai.notes.NoteAiJob, items: List<com.craftflowtechnologies.meetingmind.ai.notes.CitedItem>, onCreated: (String) -> Unit) = viewModelScope.launch {
        val nb = (scope as? NotesScope.InNotebook)?.notebookId ?: return@launch
        val result = job.result ?: return@launch
        val name = currentNotebook.value?.name ?: "notebook"
        val date = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date())
        val type = if (job.tool == com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTool.EXTRACT_ACTIONS) com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.CHECKLIST else com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.BULLET
        val blocks = buildList {
            items.forEach { item ->
                add(com.craftflowtechnologies.meetingmind.core.model.NoteBlock(NoteRepository.newId("block"), "", 0, type,
                    com.craftflowtechnologies.meetingmind.core.notes.RichText.plain(item.detail?.let { "${item.text} — $it" } ?: item.text),
                    source = com.craftflowtechnologies.meetingmind.core.model.BlockSource.AI))
                // Where it came from, as a line under it.
                val from = item.sourceIds.mapNotNull { result.sources[it]?.label?.substringBefore(" · ") }.distinct()
                if (from.isNotEmpty()) add(com.craftflowtechnologies.meetingmind.core.model.NoteBlock(NoteRepository.newId("block"), "", 0, com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH,
                    com.craftflowtechnologies.meetingmind.core.notes.RichText.plain("From: " + from.joinToString(", ")).applyStyle(com.craftflowtechnologies.meetingmind.core.notes.InlineStyle.ITALIC, 0, 5 + from.joinToString(", ").length),
                    source = com.craftflowtechnologies.meetingmind.core.model.BlockSource.AI, indent = 1))
            }
        }
        val title = (if (job.tool == com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTool.EXTRACT_ACTIONS) "Action items" else "Summary") + " · $name · $date"
        val note = notes.createNote(title = title, notebookId = nb, initialBlocks = blocks, useTemplate = false)
        noteAi.dismiss(job.id)
        onCreated(note.id)
    }

    /** Creates an empty note (in this notebook, when inside one) and hands back its id. */
    fun createNote(onCreated: (String) -> Unit) = viewModelScope.launch {
        val note = notes.createNote(notebookId = (scope as? NotesScope.InNotebook)?.notebookId, draft = true)
        onCreated(note.id)
    }

    fun createNotebook(name: String, space: NotebookSpace, colorHex: String?) = viewModelScope.launch {
        notes.createNotebook(name, space, colorHex)
    }

    fun updateNotebook(notebook: Notebook) = viewModelScope.launch { notes.updateNotebook(notebook) }
    fun deleteNotebook(id: String) = viewModelScope.launch { notes.trashNotebook(id) }
    fun restoreNotebook(id: String) = viewModelScope.launch { notes.restoreNotebook(id) }
    fun deleteNotebookForever(id: String) = viewModelScope.launch { notes.deleteNotebook(id) }
    fun archiveNotebook(id: String) = viewModelScope.launch { notes.archiveNotebook(id) }

    /** Acts on every selected note at once; each is a single, undoable-by-Trash step. */
    fun archiveAll(ids: Collection<String>) = viewModelScope.launch { ids.forEach { notes.archiveNote(it) } }
    fun deleteAll(ids: Collection<String>) = viewModelScope.launch { ids.forEach { notes.moveToTrash(it) } }
    fun restoreAll(ids: Collection<String>) = viewModelScope.launch { ids.forEach { notes.restoreFromTrash(it) } }
    fun pinAll(ids: Collection<String>, pinned: Boolean) = viewModelScope.launch { ids.forEach { notes.setPinned(it, pinned) } }
    fun moveAll(ids: Collection<String>, notebookId: String?) = viewModelScope.launch { ids.forEach { notes.moveToNotebook(it, notebookId) } }

    fun togglePin(note: Note) = viewModelScope.launch { notes.setPinned(note.id, !note.pinned) }
    fun archive(note: Note) = viewModelScope.launch { notes.archiveNote(note.id) }
    fun unarchive(note: Note) = viewModelScope.launch { notes.unarchiveNote(note.id) }
    fun delete(note: Note) = viewModelScope.launch { notes.moveToTrash(note.id) }
    fun restore(note: Note) = viewModelScope.launch { notes.restoreFromTrash(note.id) }
    fun deleteForever(note: Note) = viewModelScope.launch { notes.deleteNote(note.id) }
    fun emptyTrash() = viewModelScope.launch { notes.emptyTrash() }
}
