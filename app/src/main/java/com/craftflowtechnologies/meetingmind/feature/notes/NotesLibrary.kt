package com.craftflowtechnologies.meetingmind.feature.notes

import androidx.lifecycle.SavedStateHandle
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.notes.KeysetPager
import com.craftflowtechnologies.meetingmind.core.notes.LibraryChip
import com.craftflowtechnologies.meetingmind.core.notes.LibraryData
import com.craftflowtechnologies.meetingmind.core.notes.LibraryItem
import com.craftflowtechnologies.meetingmind.core.notes.LibraryMode
import com.craftflowtechnologies.meetingmind.core.notes.LibraryQuery
import com.craftflowtechnologies.meetingmind.core.notes.NoteSort
import com.craftflowtechnologies.meetingmind.core.notes.PagedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the library shows notes or the notebooks that hold them. */
enum class LibraryView { LIST, FOLDERS }

/**
 * The Notes library's state (U-2), free of Android so it can be tested with a fake [LibraryData]: the chips, search
 * text and sort the person chose, and one keyset pager over what they ask for. What was chosen lives in
 * [SavedStateHandle], so rotation and process death bring it back. Search is debounced; any change to a note reloads
 * the first page while the rows on screen stay put until it lands.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotesLibrary(
    private val scope: CoroutineScope,
    private val data: LibraryData,
    private val mode: LibraryMode = LibraryMode.ACTIVE,
    private val notebookId: String? = null,
    saved: SavedStateHandle? = null,
    hideLocked: Boolean = false,
    private val pageSize: Int = KeysetPager.DEFAULT_PAGE_SIZE,
    private val searchDebounceMs: Long = 250
) {
    private var saved: SavedStateHandle = saved ?: SavedStateHandle()
    private var hideLocked = hideLocked
    private var started = false
    private var searchJob: Job? = null

    private val _query = MutableStateFlow(LibraryQuery(mode = mode, notebookId = notebookId, hideLocked = hideLocked))
    /** What is being listed right now (the search text lags the field by the debounce). */
    val query: StateFlow<LibraryQuery> = _query.asStateFlow()

    private val _searchText = MutableStateFlow("")
    /** The text in the search field, immediately. */
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _view = MutableStateFlow(LibraryView.LIST)
    val view: StateFlow<LibraryView> = _view.asStateFlow()

    private val pager = MutableStateFlow(newPager(_query.value))
    val items: StateFlow<PagedState<LibraryItem>> = pager.flatMapLatest { it.state }
        .stateIn(scope, SharingStarted.Eagerly, PagedState(isLoadingFirst = true))

    init {
        if (saved != null) start(saved)
    }

    /** Takes the person's saved choices (or keeps the defaults) and loads the first page. Safe to call once. */
    fun start(handle: SavedStateHandle = saved) {
        if (started) return
        started = true
        saved = handle
        _query.value = read()
        _searchText.value = _query.value.search
        _view.value = handle.get<String>(KEY_VIEW)?.let { n -> LibraryView.entries.firstOrNull { it.name == n } } ?: LibraryView.LIST
        pager.value = newPager(_query.value)
        pager.value.loadMore()
        scope.launch { data.changes().drop(1).debounce(RELOAD_DEBOUNCE_MS).collect { pager.value.refresh() } }
    }

    private fun read(): LibraryQuery = LibraryQuery(
        mode = mode, notebookId = notebookId, hideLocked = hideLocked,
        chips = saved.get<ArrayList<String>>(KEY_CHIPS).orEmpty().mapNotNull { n -> LibraryChip.entries.firstOrNull { it.name == n } }.toSet(),
        types = saved.get<ArrayList<String>>(KEY_TYPES).orEmpty().mapNotNull { n -> RecordingType.entries.firstOrNull { it.name == n } }.toSet(),
        tagId = saved[KEY_TAG],
        sort = saved.get<String>(KEY_SORT)?.let { n -> NoteSort.entries.firstOrNull { it.name == n } } ?: NoteSort.UPDATED,
        search = saved[KEY_SEARCH] ?: "",
        includeTranscripts = saved[KEY_TRANSCRIPTS] ?: false
    )

    private fun newPager(q: LibraryQuery) =
        KeysetPager<LibraryItem>(scope, pageSize, { it.cursor }) { after: Cursor?, limit -> data.page(q, after, limit) }

    private fun apply(wanted: LibraryQuery, debounce: Boolean = false) {
        // A chip tapped while a search is still waiting out its pause takes that search along.
        val next = if (debounce) wanted else wanted.copy(search = _searchText.value)
        val searchChanged = next.search != _query.value.search
        saved[KEY_CHIPS] = ArrayList(next.chips.map { it.name })
        saved[KEY_TYPES] = ArrayList(next.types.map { it.name })
        saved[KEY_TAG] = next.tagId
        saved[KEY_SORT] = next.sort.name
        saved[KEY_SEARCH] = next.search
        saved[KEY_TRANSCRIPTS] = next.includeTranscripts
        searchJob?.cancel()
        val rebuild = { _query.value = next; pager.value = newPager(next); pager.value.loadMore() }
        if (debounce && searchChanged && next.search.isNotBlank()) searchJob = scope.launch { delay(searchDebounceMs); rebuild() } else rebuild()
    }

    fun toggleChip(chip: LibraryChip) = apply(_query.value.toggled(chip))
    /** "All": no chip, no type, no tag. The search text stays. */
    fun showAll() = apply(_query.value.copy(chips = emptySet(), types = emptySet(), tagId = null))
    fun toggleType(type: RecordingType) = apply(_query.value.let { q -> q.copy(types = if (type in q.types) q.types - type else q.types + type) })
    fun setTag(tagId: String?) = apply(_query.value.copy(tagId = tagId))
    fun setSort(sort: NoteSort) = apply(_query.value.copy(sort = sort))
    fun setIncludeTranscripts(on: Boolean) = apply(_query.value.copy(includeTranscripts = on))
    fun clearFilters() { _searchText.value = ""; apply(_query.value.clearedFilters()) }

    /** Typing in the search field: the field updates at once, the list after the debounce (or at once when cleared). */
    fun setSearch(text: String) {
        _searchText.value = text
        if (text.isNotBlank() && _view.value == LibraryView.FOLDERS) setView(LibraryView.LIST)
        apply(_query.value.copy(search = text), debounce = true)
    }

    fun setView(view: LibraryView) { _view.value = view; saved[KEY_VIEW] = view.name }

    /** The Faith lock changed (locked, unlocked or switched off): relist so locked notes appear or disappear. */
    fun setHideLocked(hide: Boolean) {
        if (hide == hideLocked) return
        hideLocked = hide
        apply(_query.value.copy(hideLocked = hide))
    }

    fun loadMore() = pager.value.loadMore()
    fun retry() = pager.value.retry()
    fun refresh() = pager.value.refresh()

    companion object {
        const val RELOAD_DEBOUNCE_MS = 150L
        internal const val KEY_CHIPS = "notes.chips"
        internal const val KEY_TYPES = "notes.types"
        internal const val KEY_TAG = "notes.tag"
        internal const val KEY_SORT = "notes.sort"
        internal const val KEY_SEARCH = "notes.search"
        internal const val KEY_TRANSCRIPTS = "notes.transcripts"
        internal const val KEY_VIEW = "notes.view"
    }
}
