package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.notes.KeysetPager
import com.craftflowtechnologies.meetingmind.core.notes.PagedState
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteFilters
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteSort
import com.craftflowtechnologies.meetingmind.core.work.WorkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Work page reads and writes beyond the live flows of [WorkViewModel]. A fake in tests. */
interface WorkPageData {
    suspend fun notesPage(after: Cursor?, limit: Int, sort: WorkNoteSort, filters: WorkNoteFilters): List<WorkNoteItem>
    /** Ranked search results from [offset]. Each row's cursor is its rank. */
    suspend fun search(query: String, offset: Int, limit: Int): List<WorkNoteItem>
    suspend fun tasksPage(after: Cursor?, limit: Int, waiting: Boolean): List<WorkTaskRow>
    suspend fun setPinned(noteId: String, pinned: Boolean)
    suspend fun moveToProject(noteId: String, notebookId: String?)
    suspend fun trash(noteId: String)
    suspend fun restore(noteId: String)
    suspend fun toggleTask(taskId: String)
    suspend fun addTask(title: String)
    /** The note as text, for the share sheet. */
    suspend fun shareText(noteId: String): String? = null
}

/** The last segment the person chose, kept across launches. */
interface SegmentStore {
    var last: String?
}

class PrefsSegmentStore(context: Context) : SegmentStore {
    private val prefs = context.getSharedPreferences("work_state", Context.MODE_PRIVATE)
    override var last: String?
        get() = prefs.getString(KEY, null)
        set(v) { prefs.edit().putString(KEY, v).apply() }
    private companion object { const val KEY = "work_page_segment" }
}

/** One-off things the screen reacts to. */
sealed interface WorkPageEvent {
    /** Notes just moved to the Trash; the screen offers Undo. */
    data class Deleted(val notes: List<Pair<Int, WorkNoteItem>>) : WorkPageEvent
    data class Failed(val what: String) : WorkPageEvent
}

class RepoWorkPageData(private val work: WorkRepository, private val notes: NoteRepository) : WorkPageData {
    private suspend fun List<NoteEntity>.items(sort: WorkNoteSort?, cursorOf: (Int, NoteEntity) -> Cursor): List<WorkNoteItem> {
        if (isEmpty()) return emptyList()
        val ind = work.noteIndicators(map { it.id })
        return mapIndexed { i, n ->
            val x = ind[n.id]
            WorkNoteItem(
                id = n.id, title = n.title, preview = previewOf(n.plainText), notebookId = n.notebookId,
                workflow = runCatching { RecordingType.valueOf(n.workflow) }.getOrDefault(RecordingType.GENERAL),
                at = if (sort == WorkNoteSort.MEETING_DATE) (n.eventDate ?: n.updatedAt) else n.updatedAt,
                pinned = n.pinned, isPrivate = n.isPrivate, hasRecording = x?.hasRecording == true,
                transcript = when (x?.transcript) { "READY" -> NoteTranscriptStatus.Ready; "PROCESSING" -> NoteTranscriptStatus.Processing; "ERROR" -> NoteTranscriptStatus.Failed; else -> NoteTranscriptStatus.None },
                openTasks = x?.openTasks ?: 0, cursor = cursorOf(i, n)
            )
        }
    }

    override suspend fun notesPage(after: Cursor?, limit: Int, sort: WorkNoteSort, filters: WorkNoteFilters) =
        work.workNotesPage(after, limit, sort, filters).items(sort) { _, n -> work.workNoteCursor(n, sort) }

    override suspend fun search(query: String, offset: Int, limit: Int) =
        work.searchWorkNotes(query, limit, offset).items(null) { i, n -> Cursor((offset + i).toLong(), "%06d".format(offset + i)) }

    override suspend fun tasksPage(after: Cursor?, limit: Int, waiting: Boolean): List<WorkTaskRow> {
        val page = work.workTasksPage(after, limit, waiting)
        val tasks = work.toWorkTasks(page)
        return page.mapIndexed { i, e -> WorkTaskRow(tasks[i], work.workTaskCursor(e)) }
    }

    override suspend fun setPinned(noteId: String, pinned: Boolean) { notes.setPinned(noteId, pinned) }
    override suspend fun moveToProject(noteId: String, notebookId: String?) { notes.moveToNotebook(noteId, notebookId) }
    override suspend fun trash(noteId: String) { notes.moveToTrash(noteId) }
    override suspend fun restore(noteId: String) { notes.restoreFromTrash(noteId) }
    override suspend fun toggleTask(taskId: String) { work.toggle(taskId) }
    override suspend fun addTask(title: String) { work.addTask(title) }
    override suspend fun shareText(noteId: String): String? =
        notes.getDocument(noteId)?.let { com.craftflowtechnologies.meetingmind.core.notes.NoteText.markdown(it.blocks, it.note.title) }
}

/**
 * The Work page's own state (WORK_UX §2): the segment, the Notes filters and search, and the two paged lists.
 * Anything the person chose lives in [SavedStateHandle], so rotation and process death bring it back; the
 * segment is also remembered across launches. Lists are bounded keyset pages (30 rows), never a full-table flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkPageViewModel(
    private val data: WorkPageData,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val store: SegmentStore,
    private val pageSize: Int = KeysetPager.DEFAULT_PAGE_SIZE,
    private val searchDebounceMs: Long = 250
) : ViewModel() {

    // ---------------------------------------------------------------- segment

    private val _segment = MutableStateFlow<WorkSegment?>(
        (saved.get<String>(KEY_SEGMENT) ?: store.last)?.let { n -> WorkSegment.entries.firstOrNull { it.name == n } }
    )
    /** Null until the page has either a remembered segment or enough information to pick the default. */
    val segment: StateFlow<WorkSegment?> = _segment.asStateFlow()

    fun select(s: WorkSegment) {
        _segment.value = s
        saved[KEY_SEGMENT] = s.name
        store.last = s.name
        ensureLoaded(s)
    }

    /** Picks the default when nothing was remembered. Does nothing once a segment is set. */
    fun resolveDefault(meetingsToday: Int, needsYou: Int) {
        if (_segment.value != null) return
        val s = defaultSegment(meetingsToday, needsYou)
        _segment.value = s
        saved[KEY_SEGMENT] = s.name
        ensureLoaded(s)
    }

    // ---------------------------------------------------------------- notes

    private val _query = MutableStateFlow(readQuery())
    val query: StateFlow<NotesQuery> = _query.asStateFlow()

    private var notesStarted = false
    private var searchJob: Job? = null
    private val notesPager = MutableStateFlow(newNotesPager(_query.value))
    val notes: StateFlow<PagedState<WorkNoteItem>> = notesPager.flatMapLatest { it.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PagedState(isLoadingFirst = true))

    private fun readQuery() = NotesQuery(
        chips = saved.get<ArrayList<String>>(KEY_CHIPS).orEmpty().mapNotNull { n -> NoteChip.entries.firstOrNull { it.name == n } }.toSet(),
        notebookId = saved[KEY_PROJECT],
        sort = saved.get<String>(KEY_SORT)?.let { n -> WorkNoteSort.entries.firstOrNull { it.name == n } } ?: WorkNoteSort.RECENT,
        search = saved[KEY_SEARCH] ?: ""
    )

    private fun newNotesPager(q: NotesQuery): KeysetPager<WorkNoteItem> {
        val now = System.currentTimeMillis()
        return if (q.isSearching) {
            KeysetPager(viewModelScope, pageSize, { it.cursor }) { after, limit -> data.search(q.search, after?.let { it.longKey.toInt() + 1 } ?: 0, limit) }
        } else {
            val filters = q.filters(weekStart(now))
            KeysetPager(viewModelScope, pageSize, { it.cursor }) { after, limit -> data.notesPage(after, limit, q.sort, filters) }
        }
    }

    private fun applyQuery(next: NotesQuery, debounce: Boolean = false) {
        val searchChanged = next.search != _query.value.search
        _query.value = next
        saved[KEY_CHIPS] = ArrayList(next.chips.map { it.name })
        saved[KEY_PROJECT] = next.notebookId
        saved[KEY_SORT] = next.sort.name
        saved[KEY_SEARCH] = next.search
        searchJob?.cancel()
        val rebuild = { notesPager.value = newNotesPager(next); notesStarted = true; notesPager.value.loadMore() }
        if (debounce && searchChanged) searchJob = viewModelScope.launch { delay(searchDebounceMs); rebuild() } else rebuild()
    }

    fun toggleChip(chip: NoteChip) = applyQuery(_query.value.toggled(chip))
    fun setProject(notebookId: String?) = applyQuery(_query.value.copy(notebookId = notebookId))
    fun setSort(sort: WorkNoteSort) = applyQuery(_query.value.copy(sort = sort))
    fun setSearch(text: String) = applyQuery(_query.value.copy(search = text), debounce = true)
    fun clearFilters() = applyQuery(_query.value.copy(chips = emptySet(), notebookId = null, search = ""))

    fun loadMoreNotes() = notesPager.value.loadMore()
    fun retryNotes() = notesPager.value.retry()
    fun refreshNotes() = notesPager.value.refresh()

    fun togglePin(item: WorkNoteItem) = viewModelScope.launch {
        runCatching { data.setPinned(item.id, !item.pinned) }
            .onSuccess { notesPager.value.update { l -> l.map { if (it.id == item.id) it.copy(pinned = !item.pinned) else it } } }
            .onFailure { _events.tryEmit(WorkPageEvent.Failed("pin the note")) }
    }

    /** Files a note under a project (null = no project). Leaves the list when it no longer matches the project filter. */
    fun move(item: WorkNoteItem, notebookId: String?) = viewModelScope.launch {
        runCatching { data.moveToProject(item.id, notebookId) }
            .onSuccess {
                val filter = _query.value.notebookId
                notesPager.value.update { l -> if (filter != null && filter != notebookId) l.filter { it.id != item.id } else l.map { if (it.id == item.id) it.copy(notebookId = notebookId) else it } }
            }
            .onFailure { _events.tryEmit(WorkPageEvent.Failed("move the note")) }
    }

    /** Moves notes to the Trash and asks the screen to offer Undo. */
    fun delete(items: List<WorkNoteItem>) = viewModelScope.launch {
        val at = notesPager.value.state.value.items
        val removed = items.mapNotNull { i -> at.indexOfFirst { it.id == i.id }.takeIf { it >= 0 }?.let { it to i } }.sortedBy { it.first }
        val done = ArrayList<Pair<Int, WorkNoteItem>>()
        for (r in removed) { runCatching { data.trash(r.second.id) }.onSuccess { done += r } }
        if (done.size < removed.size) _events.tryEmit(WorkPageEvent.Failed("delete every note"))
        if (done.isNotEmpty()) {
            val ids = done.map { it.second.id }.toSet()
            notesPager.value.update { l -> l.filter { it.id !in ids } }
            _events.tryEmit(WorkPageEvent.Deleted(done))
        }
    }

    fun undoDelete(notes: List<Pair<Int, WorkNoteItem>>) = viewModelScope.launch {
        val back = notes.filter { runCatching { data.restore(it.second.id) }.isSuccess }
        notesPager.value.update { l ->
            val out = l.toMutableList()
            back.sortedBy { it.first }.forEach { (i, item) -> if (out.none { it.id == item.id }) out.add(i.coerceAtMost(out.size), item) }
            out
        }
    }

    suspend fun shareText(item: WorkNoteItem): String = data.shareText(item.id) ?: item.displayTitle

    // ---------------------------------------------------------------- tasks

    private val _waiting = MutableStateFlow(saved[KEY_WAITING] ?: false)
    /** False = Mine, true = Waiting on. */
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()

    private val taskPagers = listOf(false, true).associateWith { w ->
        KeysetPager(viewModelScope, pageSize, { r: WorkTaskRow -> r.cursor }) { after, limit -> data.tasksPage(after, limit, w) }
    }
    private val tasksStarted = mutableSetOf<Boolean>()
    val tasks: StateFlow<PagedState<WorkTaskRow>> = _waiting.flatMapLatest { taskPagers.getValue(it).state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PagedState(isLoadingFirst = true))

    private fun currentTasks() = taskPagers.getValue(_waiting.value)

    fun setWaiting(w: Boolean) {
        _waiting.value = w
        saved[KEY_WAITING] = w
        ensureTasks()
    }

    private fun ensureTasks() { if (tasksStarted.add(_waiting.value)) currentTasks().loadMore() }

    fun loadMoreTasks() = currentTasks().loadMore()
    fun retryTasks() = currentTasks().retry()
    fun refreshTasks() = currentTasks().refresh()

    fun completeTask(row: WorkTaskRow) = viewModelScope.launch {
        runCatching { data.toggleTask(row.task.id) }
            .onSuccess { currentTasks().update { l -> l.filter { it.task.id != row.task.id } } }
            .onFailure { _events.tryEmit(WorkPageEvent.Failed("update the task")) }
    }

    fun addTask(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        runCatching { data.addTask(title) }
            .onSuccess { taskPagers.getValue(false).refresh(); tasksStarted += false }
            .onFailure { _events.tryEmit(WorkPageEvent.Failed("add the task")) }
    }

    // ---------------------------------------------------------------- lifecycle

    private fun ensureLoaded(s: WorkSegment) {
        when (s) {
            WorkSegment.NOTES -> if (!notesStarted) { notesStarted = true; notesPager.value.loadMore() }
            WorkSegment.TASKS -> ensureTasks()
            else -> Unit
        }
    }

    /** The screen was shown again: reload what is already on screen so edits made elsewhere appear. Does nothing before the first load. */
    fun onResume() {
        if (notesStarted && (notes.value.items.isNotEmpty() || notes.value.endReached)) notesPager.value.refresh()
        if (_segment.value == WorkSegment.TASKS && tasksStarted.contains(_waiting.value)) currentTasks().refresh()
    }

    private val _events = MutableSharedFlow<WorkPageEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WorkPageEvent> = _events

    init { _segment.value?.let { ensureLoaded(it) } }

    companion object {
        internal const val KEY_SEGMENT = "work_segment"
        internal const val KEY_CHIPS = "work_chips"
        internal const val KEY_PROJECT = "work_project"
        internal const val KEY_SORT = "work_sort"
        internal const val KEY_SEARCH = "work_search"
        internal const val KEY_WAITING = "work_waiting"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val db = MeetMindDatabase.getInstance(app)
                WorkPageViewModel(RepoWorkPageData(WorkRepository(db), NoteRepository(app, db)), createSavedStateHandle(), PrefsSegmentStore(app))
            }
        }
    }
}
