package com.craftflowtechnologies.meetingmind.feature.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.Notebook
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.Tag
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.notes.LibraryItem
import com.craftflowtechnologies.meetingmind.core.notes.LibraryMode
import com.craftflowtechnologies.meetingmind.core.notes.NoteLibraryStore
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The space a note is filtered under. A Work workflow (meeting, interview, decision...) is always Work, even when
 * the note sits in "My Notes" because it had no project notebook; anything else follows its notebook's space.
 * (The library's SQL applies the same rule; this is kept for callers that group notes in memory.)
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

/** Keeps the library's chips, search and view in a [SavedStateHandle] that outlives the view model (rotation, process death). */
class NotesStateHolder(val handle: SavedStateHandle) : ViewModel()

/** A step that puts a bulk action back. */
fun interface Undo { suspend fun run() }

/**
 * The Notes library: notes arrive a page at a time through [library] (chips, ranked search and sort are SQL, never an
 * in-memory filter over every note), beside the notebooks themselves and the actions on a selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(application: Application, val scope: NotesScope = NotesScope.All) : AndroidViewModel(application) {

    private val db = MeetMindDatabase.getInstance(application)
    private val notes = NoteRepository(application, db)

    /** Chips, search, sort and the one pager. Call [attach] once with the saved state; until then nothing loads. */
    val library = NotesLibrary(
        viewModelScope, NoteLibraryStore(db),
        mode = when (scope) { NotesScope.Archived -> LibraryMode.ARCHIVED; NotesScope.Trash -> LibraryMode.TRASH; else -> LibraryMode.ACTIVE },
        notebookId = (scope as? NotesScope.InNotebook)?.notebookId
    )

    /** Loads the first page from the saved choices. [onResume] keeps the Faith lock in step. */
    fun attach(handle: SavedStateHandle) { library.start(handle); onResume() }

    private val prefs = com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(application)

    /** The Faith lock is on and still locked: private and Faith notes drop out of search and their previews blank. */
    fun onResume() {
        viewModelScope.launch {
            val on = runCatching { prefs.preferencesFlow.first().faithLockEnabled }.getOrDefault(false)
            library.setHideLocked(on && !com.craftflowtechnologies.meetingmind.feature.faith.FaithLock.isUnlocked())
        }
    }

    val notebooks: StateFlow<List<Notebook>> = notes.observeNotebooks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notebookCounts: StateFlow<Map<String, Int>> = notes.observeNotebookCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val tags: StateFlow<List<Tag>> = notes.observeTags().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archivedCount: StateFlow<Int> = db.noteDao().observeArchivedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val trashCount: StateFlow<Int> = db.noteDao().observeTrashedCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val trashedNotebooks: StateFlow<List<Notebook>> =
        (if (scope == NotesScope.Trash) notes.observeTrashedNotebooks() else kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    /** Bulk actions on a selection. Each returns the step that undoes it, for the snackbar's Undo. */
    suspend fun pinAll(items: List<LibraryItem>, pinned: Boolean): Undo {
        val before = items.associate { it.id to it.note.pinned }
        viewModelScope.async { items.forEach { notes.setPinned(it.id, pinned) } }.await()
        return Undo { before.forEach { (id, was) -> notes.setPinned(id, was) } }
    }

    suspend fun moveAll(items: List<LibraryItem>, notebookId: String?): Undo {
        val before = items.associate { it.id to it.note.notebookId }
        viewModelScope.async { items.forEach { notes.moveToNotebook(it.id, notebookId) } }.await()
        return Undo { before.forEach { (id, was) -> notes.moveToNotebook(id, was) } }
    }

    suspend fun deleteAll(items: List<LibraryItem>): Undo {
        viewModelScope.async { items.forEach { notes.moveToTrash(it.id) } }.await()
        return Undo { items.forEach { notes.restoreFromTrash(it.id) } }
    }

    suspend fun archiveAll(items: List<LibraryItem>): Undo {
        viewModelScope.async { items.forEach { notes.archiveNote(it.id) } }.await()
        return Undo { items.forEach { notes.unarchiveNote(it.id) } }
    }

    fun restoreAll(items: List<LibraryItem>) = viewModelScope.launch { items.forEach { notes.restoreFromTrash(it.id) } }

    fun togglePin(note: Note) = viewModelScope.launch { notes.setPinned(note.id, !note.pinned) }
    fun archive(note: Note) = viewModelScope.launch { notes.archiveNote(note.id) }
    fun unarchive(note: Note) = viewModelScope.launch { notes.unarchiveNote(note.id) }
    fun delete(note: Note) = viewModelScope.launch { notes.moveToTrash(note.id) }
    fun restore(note: Note) = viewModelScope.launch { notes.restoreFromTrash(note.id) }
    fun deleteForever(note: Note) = viewModelScope.launch { notes.deleteNote(note.id) }
    fun emptyTrash() = viewModelScope.launch { notes.emptyTrash() }
}
