package com.example.feature.bible

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.datastore.UserPreferencesManager
import com.example.core.scripture.BibleBook
import com.example.core.scripture.BibleBooks
import com.example.core.scripture.BibleDownloadWorker
import com.example.core.scripture.BibleInfo
import com.example.core.scripture.BibleStore
import com.example.core.scripture.BibleVersions
import com.example.core.scripture.ChapterResult
import com.example.core.scripture.OfflineBible
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureReferenceParser
import com.example.core.scripture.ScriptureService
import com.example.core.scripture.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the reader is, and what it shows there. */
sealed interface ChapterState {
    data object Loading : ChapterState
    data class Ready(val result: ChapterResult.Found) : ChapterState
    data class Missing(val message: String, val offline: Boolean) : ChapterState
}

/** A download in progress or waiting for a connection. [unit] is "chapters" or "books". */
data class DownloadState(val bibleId: Int, val done: Int, val total: Int, val waiting: Boolean, val unit: String = "chapters")

/** What the reader shows around the text: section headings, narrations and the person's highlights. */
data class ReaderExtras(
    val headings: List<com.example.core.scripture.ChapterHeading> = emptyList(),
    val audio: List<com.example.core.scripture.ChapterAudio> = emptyList(),
    val highlights: Map<Int, String> = emptyMap()
)

/** The study sheet for a verse: cross-references and commentary. */
data class StudyState(
    val reference: ScriptureReference,
    val crossRefs: List<Pair<com.example.core.scripture.CrossRef, String?>>? = null,
    val commentaryId: String = com.example.core.scripture.HelloAo.commentaries.first().id,
    val commentary: String? = null,
    val loadingCommentary: Boolean = true,
    val offline: Boolean = false
)

data class SearchState(
    val query: String = "",
    val reference: ScriptureReference? = null,
    val hits: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false
)

/**
 * The Bible reader and search (docs/PLAN_V1.md §7): pick a translation, move through books and
 * chapters, select verses, search a downloaded translation, and keep a translation on the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BibleViewModel(application: Application) : AndroidViewModel(application) {

    private val service = ScriptureService(application)
    private val library = ScriptureService.library(application)
    private val store = BibleStore.get(application)
    private val prefs = UserPreferencesManager(application)
    private val place = application.getSharedPreferences("bible_reader", Context.MODE_PRIVATE)

    private val _bibles = MutableStateFlow<List<BibleInfo>>(emptyList())
    val bibles: StateFlow<List<BibleInfo>> = _bibles.asStateFlow()

    private val _current = MutableStateFlow<BibleInfo?>(null)
    val current: StateFlow<BibleInfo?> = _current.asStateFlow()

    private val _offline = MutableStateFlow<List<OfflineBible>>(emptyList())
    val offline: StateFlow<List<OfflineBible>> = _offline.asStateFlow()

    private val _book = MutableStateFlow(BibleBooks.byUsfm(place.getString("book", "JHN") ?: "JHN") ?: BibleBooks.all[42])
    val book: StateFlow<BibleBook> = _book.asStateFlow()

    private val _chapter = MutableStateFlow(place.getInt("chapter", 1))
    val chapter: StateFlow<Int> = _chapter.asStateFlow()

    private val _content = MutableStateFlow<ChapterState>(ChapterState.Loading)
    val content: StateFlow<ChapterState> = _content.asStateFlow()

    /** Selected verse numbers in the open chapter: one contiguous run. */
    private val _selection = MutableStateFlow<IntRange?>(null)
    val selection: StateFlow<IntRange?> = _selection.asStateFlow()

    /** A verse to bring into view when the chapter opens (from a search hit or a reference). */
    private val _focus = MutableStateFlow<Int?>(null)
    val focus: StateFlow<Int?> = _focus.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _canSearch = MutableStateFlow(false)
    val canSearch: StateFlow<Boolean> = _canSearch.asStateFlow()

    val configured: Boolean get() = service.isConfigured

    /** Progress of the current translation's download, when one is running or queued. */
    val download: StateFlow<DownloadState?> = _current.flatMapLatest { info ->
        if (info == null) flowOf(null)
        else WorkManager.getInstance(application).getWorkInfosForUniqueWorkFlow(BibleDownloadWorker.uniqueName(info.id)).map { list ->
            val w = list.firstOrNull { !it.state.isFinished } ?: return@map null.also { refreshOffline() }
            DownloadState(
                info.id,
                w.progress.getInt(BibleDownloadWorker.KEY_DONE, 0),
                w.progress.getInt(BibleDownloadWorker.KEY_TOTAL, if (com.example.core.scripture.HelloAo.isHelloAo(info.id)) 66 else 1189),
                waiting = w.state == WorkInfo.State.ENQUEUED,
                unit = w.progress.getString(BibleDownloadWorker.KEY_UNIT) ?: if (com.example.core.scripture.HelloAo.isHelloAo(info.id)) "books" else "chapters"
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var anchor: Int? = null
    private var searchJob: Job? = null
    private var started = false

    /** Opens the reader, at [reference] when given, else where the person last left off. */
    fun start(reference: ScriptureReference?) {
        if (started && reference == null) return
        started = true
        viewModelScope.launch {
            refreshOffline()
            val preferred = prefs.preferencesFlow.first().bibleVersionId
            val list = library.bibles()
            _bibles.value = list
            _current.value = list.firstOrNull { it.id == preferred }
                ?: library.info(preferred)
                ?: list.firstOrNull { it.id == BibleVersions.DEFAULT_ID }
                ?: list.firstOrNull()
            refreshCanSearch()
            if (reference != null) open(reference.book, reference.chapter, reference.verseStart, reference.verseEnd ?: reference.verseStart)
            else load()
        }
    }

    fun chooseBible(info: BibleInfo) {
        _current.value = info
        viewModelScope.launch {
            service.setDefaultVersion(info.id)
            refreshCanSearch()
            load()
            _search.value.query.takeIf { it.isNotBlank() }?.let { runSearch(it) }
        }
    }

    fun open(book: BibleBook, chapter: Int, verseStart: Int? = null, verseEnd: Int? = null) {
        _book.value = book
        _chapter.value = chapter.coerceIn(1, book.chapterCount)
        place.edit().putString("book", book.usfm).putInt("chapter", _chapter.value).apply()
        _selection.value = verseStart?.let { it..(verseEnd ?: it).coerceAtLeast(it) }
        anchor = verseStart
        _focus.value = verseStart
        load()
    }

    fun next() {
        val b = _book.value
        if (_chapter.value < b.chapterCount) open(b, _chapter.value + 1)
        else nextBook(b, +1)?.let { open(it, 1) }
    }

    fun previous() {
        val b = _book.value
        if (_chapter.value > 1) open(b, _chapter.value - 1)
        else nextBook(b, -1)?.let { open(it, it.chapterCount) }
    }

    fun hasNext() = _chapter.value < _book.value.chapterCount || nextBook(_book.value, +1) != null
    fun hasPrevious() = _chapter.value > 1 || nextBook(_book.value, -1) != null

    private fun nextBook(from: BibleBook, step: Int): BibleBook? {
        val info = _current.value
        var i = BibleBooks.all.indexOf(from) + step
        while (i in BibleBooks.all.indices) {
            val candidate = BibleBooks.all[i]
            if (info == null || info.has(candidate)) return candidate
            i += step
        }
        return null
    }

    fun reload() = load()

    private fun load() {
        val info = _current.value ?: run {
            _content.value = ChapterState.Missing(
                if (configured) "Couldn't reach the Bible service. Check your connection and try again." else "The Bible isn't set up in this build yet.",
                offline = configured
            )
            return
        }
        val book = _book.value
        val chapter = _chapter.value
        _content.value = ChapterState.Loading
        viewModelScope.launch {
            _content.value = when (val r = library.chapter(info.id, book, chapter)) {
                is ChapterResult.Found -> ChapterState.Ready(r)
                is ChapterResult.Unavailable -> ChapterState.Missing(r.message, r.reason == com.example.core.scripture.PassageResult.Reason.OFFLINE)
            }
            loadExtras(info.id, book, chapter)
        }
    }

    /** Taps build one contiguous run: the first tap marks a verse, the next extends to it. */
    fun tapVerse(number: Int) {
        val current = _selection.value
        _focus.value = null
        _selection.value = when {
            current == null -> { anchor = number; number..number }
            current.first == number && current.last == number -> { anchor = null; null }
            anchor != null && current.first == current.last -> minOf(anchor!!, number)..maxOf(anchor!!, number)
            else -> { anchor = number; number..number }
        }
    }

    fun clearSelection() { _selection.value = null; anchor = null }

    fun selectedReference(): ScriptureReference? {
        val range = _selection.value ?: return null
        return ScriptureReference(_book.value, _chapter.value, range.first, range.last.takeIf { it != range.first })
    }

    fun onQuery(query: String) {
        _search.value = _search.value.copy(query = query, reference = ScriptureReferenceParser.parse(query))
        searchJob?.cancel()
        if (query.trim().length < 2) { _search.value = _search.value.copy(hits = emptyList(), searched = false, searching = false); return }
        searchJob = viewModelScope.launch { delay(250); runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        val info = _current.value ?: return
        if (!library.canSearch(info.id)) { _search.value = _search.value.copy(hits = emptyList(), searched = false); return }
        _search.value = _search.value.copy(searching = true)
        val hits = library.search(info.id, query, 300)
        _search.value = _search.value.copy(hits = hits, searching = false, searched = true)
    }

    /** Starts a devotional on the selected verses and hands back the new note's id. */
    fun startDevotional(reference: ScriptureReference, onCreated: (String) -> Unit) = viewModelScope.launch {
        val notes = com.example.core.repository.NoteRepository(getApplication(), com.example.core.database.MeetMindDatabase.getInstance(getApplication()))
        onCreated(notes.startDevotional(reference).id)
    }

    fun downloadCurrent(wifiOnly: Boolean = false) {
        val info = _current.value ?: return
        BibleDownloadWorker.enqueue(getApplication(), info.id, wifiOnly)
    }

    fun download(info: BibleInfo) = BibleDownloadWorker.enqueue(getApplication(), info.id, wifiOnly = false)

    fun pauseDownload() { _current.value?.let { BibleDownloadWorker.stop(getApplication(), it.id) } }

    fun removeDownload(bibleId: Int) {
        BibleDownloadWorker.stop(getApplication(), bibleId)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(bibleId) }
            refreshOffline(); refreshCanSearch()
        }
    }

    // ---------------------------------------------------------------- F5: headings, audio, highlights, study

    private val _extras = MutableStateFlow(ReaderExtras())
    val extras: StateFlow<ReaderExtras> = _extras.asStateFlow()

    private suspend fun loadExtras(bibleId: Int, book: BibleBook, chapter: Int) {
        val headings = runCatching { library.headings(bibleId, book, chapter) }.getOrDefault(emptyList())
        val highlights = withContext(Dispatchers.IO) { runCatching { store.highlights(book, chapter) }.getOrDefault(emptyMap()) }
        _extras.value = ReaderExtras(headings, emptyList(), highlights)
        val audio = runCatching { library.audio(bibleId, book, chapter) }.getOrDefault(emptyList())
        if (_book.value == book && _chapter.value == chapter) _extras.value = _extras.value.copy(audio = audio)
    }

    private val _narrator = MutableStateFlow(place.getString("narrator", null))
    val narrator: StateFlow<String?> = _narrator.asStateFlow()

    fun chooseNarrator(name: String) { _narrator.value = name; place.edit().putString("narrator", name).apply() }

    private fun playbackId() = "bible:${_current.value?.id}:${_book.value.usfm}:${_chapter.value}"

    private fun chosenAudio(): com.example.core.scripture.ChapterAudio? {
        val all = _extras.value.audio
        return all.firstOrNull { it.narrator == _narrator.value } ?: all.firstOrNull()
    }

    /** The verse being read aloud right now, for highlighting and following. */
    val readingVerse: StateFlow<Int?> = kotlinx.coroutines.flow.combine(com.example.core.audio.PlaybackController.state, _extras, _narrator) { p, _, _ ->
        if (p.recordingId != playbackId() || p.phase != com.example.core.audio.PlaybackPhase.PLAYING && p.phase != com.example.core.audio.PlaybackPhase.PAUSED) null
        else chosenAudio()?.verseAt(p.positionMs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val audioPlaying: StateFlow<Boolean> = com.example.core.audio.PlaybackController.state.map { it.recordingId == playbackId() && it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Plays the chapter aloud (from [verse] when given); tapping again pauses. */
    fun listen(verse: Int? = null) {
        val audio = chosenAudio() ?: return
        val state = com.example.core.audio.PlaybackController.state.value
        if (verse == null && state.recordingId == playbackId() && state.isPlaying) { com.example.core.audio.PlaybackController.pause(); return }
        val title = "${if (_book.value.usfm == "PSA") "Psalm" else _book.value.name} ${_chapter.value} · ${_current.value?.abbreviation ?: ""}"
        val at = verse?.let { audio.startOf(it) }
        com.example.core.audio.PlaybackController.playUriAt(getApplication(), playbackId(), title, android.net.Uri.parse(audio.url), at ?: if (state.recordingId == playbackId()) null else 0L)
    }

    /** Colours the selected verses ([color] null clears them). */
    fun highlight(color: String?) {
        val range = _selection.value ?: return
        val book = _book.value; val chapter = _chapter.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.setHighlight(book, chapter, range, color) }
            _extras.value = _extras.value.copy(highlights = withContext(Dispatchers.IO) { store.highlights(book, chapter) })
            clearSelection()
        }
    }

    private val _study = MutableStateFlow<StudyState?>(null)
    val study: StateFlow<StudyState?> = _study.asStateFlow()

    /** Opens the study sheet for the selected (or given) verse. */
    fun openStudy(reference: ScriptureReference? = selectedReference()) {
        val ref = reference ?: return
        val commentaryId = place.getString("commentary", null) ?: com.example.core.scripture.HelloAo.commentaries.first().id
        _study.value = StudyState(ref, commentaryId = commentaryId)
        viewModelScope.launch {
            val verse = ref.verseStart ?: 1
            val refs = library.crossRefs(ref.book, ref.chapter)
            val mine = refs?.filter { it.fromVerse in verse..(ref.verseEnd ?: verse) }?.sortedByDescending { it.score }?.distinctBy { it.to }?.take(12)
            val bibleId = _current.value?.id
            val withText = mine?.map { r ->
                r to (bibleId?.let { id -> (runCatching { library.passage(r.to, id) }.getOrNull() as? com.example.core.scripture.PassageResult.Found)?.passage?.text })
            }
            _study.value = _study.value?.copy(crossRefs = withText ?: emptyList(), offline = refs == null)
        }
        loadCommentary(commentaryId)
    }

    fun chooseCommentary(id: String) {
        place.edit().putString("commentary", id).apply()
        loadCommentary(id)
    }

    private fun loadCommentary(id: String) {
        val s = _study.value ?: return
        _study.value = s.copy(commentaryId = id, loadingCommentary = true, commentary = null)
        viewModelScope.launch {
            val entries = library.commentary(id, s.reference.book, s.reference.chapter)
            val verse = s.reference.verseStart ?: 1
            // An entry speaks to its verse and on to the next entry.
            val text = entries?.lastOrNull { it.verse <= verse }?.text ?: entries?.firstOrNull()?.text
            _study.value = _study.value?.copy(commentary = text ?: if (entries == null) null else "", loadingCommentary = false, offline = _study.value?.offline == true || entries == null)
        }
    }

    fun closeStudy() { _study.value = null }

    /** The free catalogue, for "More translations". */
    private val _catalog = MutableStateFlow<List<com.example.core.scripture.HelloAoTranslation>>(emptyList())
    val catalog: StateFlow<List<com.example.core.scripture.HelloAoTranslation>> = _catalog.asStateFlow()

    fun loadCatalog() = viewModelScope.launch { _catalog.value = runCatching { library.helloAoCatalog() }.getOrDefault(emptyList()) }

    fun chooseTranslation(t: com.example.core.scripture.HelloAoTranslation) = chooseBible(t.info())

    fun downloadTranslation(t: com.example.core.scripture.HelloAoTranslation) = BibleDownloadWorker.enqueue(getApplication(), t.intId, wifiOnly = false)

    private fun refreshOffline() {
        viewModelScope.launch {
            _offline.value = withContext(Dispatchers.IO) { store.offlineBibles() }
            refreshCanSearch()
        }
    }

    private suspend fun refreshCanSearch() {
        _canSearch.value = _current.value?.let { library.canSearch(it.id) } ?: false
    }
}
