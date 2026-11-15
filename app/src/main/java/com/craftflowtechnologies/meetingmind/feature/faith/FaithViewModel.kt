package com.craftflowtechnologies.meetingmind.feature.faith

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Circles2
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult
import com.craftflowtechnologies.meetingmind.core.circles2.CreatedPost
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NameCount
import com.craftflowtechnologies.meetingmind.core.model.Attachment
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteLinkKind
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.ScriptureCollection
import com.craftflowtechnologies.meetingmind.core.model.ScriptureRef
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.repository.NoteContent
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Loaded draft/existing story passed into [TestimonyStudioSheet]. */
data class TestimonyStudioSeed(
    val noteId: String,
    val title: String,
    val beats: TestimonyBeats,
    val category: TestimonyCategory?,
    val verseRef: String?,
    val verseText: String?,
    val storyDateMs: Long,
    val linkedPrayerId: String?,
    val linkedPrayerTitle: String?,
    val isDraft: Boolean,
    val isInitiallyBlank: Boolean
)

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
    private val testimonyRefreshTick = MutableStateFlow(0)

    val draftTestimonyNotes: StateFlow<List<Note>> = notes.observeDraftsForWorkflow(RecordingType.TESTIMONY)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _personalTestimonies = MutableStateFlow<List<TestimonyStory>>(emptyList())
    val personalTestimonies: StateFlow<List<TestimonyStory>> = _personalTestimonies.asStateFlow()

    private val _joinedCircles = MutableStateFlow<List<Circle>>(emptyList())
    val joinedCircles: StateFlow<List<Circle>> = _joinedCircles.asStateFlow()

    private val _circleTestimonies = MutableStateFlow<List<TestimonyStory>>(emptyList())
    val circleTestimonies: StateFlow<List<TestimonyStory>> = _circleTestimonies.asStateFlow()

    private var circleCollectorJob: Job? = null
    private val circlePostsJobs = mutableMapOf<String, Job>()
    private val circlePostsMap = mutableMapOf<String, List<TestimonyStory>>()

    /** Today's devotional, when it has been written. */
    val todayDevotional: kotlinx.coroutines.flow.StateFlow<com.craftflowtechnologies.meetingmind.core.devotional.DailyDevotional?> =
        com.craftflowtechnologies.meetingmind.core.devotional.DevotionalRepository(application).observe(com.craftflowtechnologies.meetingmind.core.devotional.LocalDay.today())
            .kotlinx_catch()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val scripture = ScriptureService(application)

    /** How the person reads their devotional (their plan), for the line under Today's devotional. */
    val devotionalProfile: kotlinx.coroutines.flow.StateFlow<com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile> =
        com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(application).devotionalProfile
            .catch { }
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile())

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

    /** Today's memory verse and how many are saved; null until one is saved. */
    private val _memory = MutableStateFlow<Pair<VerseOfTheDay, Int>?>(null)
    val memoryVerse: StateFlow<Pair<VerseOfTheDay, Int>?> = _memory.asStateFlow()

    init {
        loadVerseOfTheDay()
        viewModelScope.launch {
            notes.observeScriptureCollections().collect { cols ->
                val col = cols.firstOrNull { it.name.equals(com.craftflowtechnologies.meetingmind.core.faith.MemoryVerses.COLLECTION, ignoreCase = true) }
                if (col == null) { _memory.value = null; return@collect }
                val items = notes.observeCollectionItems(col.id).first()
                val i = com.craftflowtechnologies.meetingmind.core.faith.MemoryVerses.indexFor(java.time.LocalDate.now().toEpochDay(), items.size)
                val item = items.getOrNull(i) ?: run { _memory.value = null; return@collect }
                val ref = com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks.byUsfm(item.bookUsfm)?.let { ScriptureReference(it, item.chapter, item.verseStart, item.verseEnd) } ?: return@collect
                _memory.value = VerseOfTheDay(ref, null) to items.size
                _memory.value = VerseOfTheDay(ref, scripture.passage(ref)) to items.size
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            combine(faithNotes, draftTestimonyNotes, testimonyRefreshTick) { active, drafts, _ ->
                active to drafts
            }.collect { (active, drafts) ->
                val allNotes = (active.filter { it.workflow == RecordingType.TESTIMONY } + drafts)
                    .distinctBy { it.id }
                val prayersById = active.filter { it.workflow == RecordingType.PRAYER_REQUEST }.associateBy { it.id }
                val stories = allNotes.map { n ->
                    val doc = notes.getDocument(n.id)
                    val links = notes.observeLinks(n.id).first()
                    val prayerId = n.metadata[TestimonyMeta.PRAYER_ID]?.takeIf { it.isNotBlank() }
                        ?: links.firstOrNull { it.kind == NoteLinkKind.TESTIMONY_OF }?.let {
                            if (it.fromNoteId == n.id) it.toNoteId else it.fromNoteId
                        }
                    val linkedPrayer = prayerId?.let { id -> prayersById[id] ?: notes.getNote(id) }
                    val hasAudio = notes.observeRecordingIds(n.id).first().isNotEmpty()
                    TestimonyMapper.fromNote(
                        note = doc?.note ?: n,
                        blocks = doc?.blocks.orEmpty(),
                        linkedPrayer = linkedPrayer,
                        hasAudio = hasAudio
                    )
                }.sortedByDescending { it.storyDateMs }
                _personalTestimonies.value = stories
            }
        }
    }

    /** Adds a verse to the memory verses collection, making the collection if needed. */
    fun memorise(ref: ScriptureReference) = viewModelScope.launch {
        val cols = notes.observeScriptureCollections().first()
        val col = cols.firstOrNull { it.name.equals(com.craftflowtechnologies.meetingmind.core.faith.MemoryVerses.COLLECTION, ignoreCase = true) }
            ?: notes.createScriptureCollection(com.craftflowtechnologies.meetingmind.core.faith.MemoryVerses.COLLECTION)
        notes.addToCollection(col.id, ref.usfm, ref.chapter, ref.verseStart, ref.verseEnd, null)
    }

    fun loadVerseOfTheDay() = viewModelScope.launch {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val ref = scripture.verseOfTheDay(day) ?: return@launch
        _votd.value = VerseOfTheDay(ref, null)
        _votd.value = VerseOfTheDay(ref, scripture.passage(ref))
    }

    /** Starts a Faith note of [type] from its template, and hands back its id. */
    fun create(type: RecordingType, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(notes.createNote(workflow = type, draft = true).id)
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

    fun trashNote(id: String) = viewModelScope.launch {
        notes.archiveNote(id)
    }

    fun deleteNoteForever(id: String) = viewModelScope.launch {
        notes.deleteNote(id)
    }

    /** Subscribes to joined Circles so the "In My Circles" tab and share sheet are populated. */
    fun loadCircleTestimonies() {
        if (circleCollectorJob?.isActive == true) return
        circleCollectorJob = viewModelScope.launch {
            val repo = runCatching { Circles2.repository(getApplication()) }.getOrNull() ?: return@launch
            repo.circles().collect { state ->
                val circles = (state as? com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState.Ready)?.circles.orEmpty()
                _joinedCircles.value = circles
                val activeIds = circles.map { it.id }.toSet()
                circlePostsJobs.keys.filter { it !in activeIds }.toList().forEach { staleId ->
                    circlePostsJobs.remove(staleId)?.cancel()
                    circlePostsMap.remove(staleId)
                }
                circles.forEach { circle ->
                    if (!circlePostsJobs.containsKey(circle.id)) {
                        circlePostsJobs[circle.id] = viewModelScope.launch {
                            repo.data.observePosts(circle.id).collect { postState ->
                                if (postState is DataState.Ready) {
                                    val mapped = postState.value
                                        .filter { !it.deleted && it.type == PostType.Testimony }
                                        .map { post -> TestimonyMapper.fromCirclePost(post, circle) }
                                    circlePostsMap[circle.id] = mapped
                                    _circleTestimonies.value = circlePostsMap.values.flatten().sortedByDescending { it.storyDateMs }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Persists Amen celebration on a personal note or Circle testimony post. */
    fun toggleRejoice(story: TestimonyStory) = viewModelScope.launch {
        val noteId = story.noteId
        if (story.isPersonal && noteId != null) {
            val current = notes.getNote(noteId)
            if (current != null) {
                val count = current.metadata[TestimonyMeta.REJOICE_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val alreadyRejoiced = current.metadata[TestimonyMeta.USER_REJOICED] == "1" || count > 0
                val nextRejoiced = !alreadyRejoiced
                val nextCount = if (nextRejoiced) count + 1 else (count - 1).coerceAtLeast(0)
                notes.updateNote(
                    current.copy(
                        metadata = current.metadata + mapOf(
                            TestimonyMeta.REJOICE_COUNT to nextCount.toString(),
                            TestimonyMeta.USER_REJOICED to if (nextRejoiced) "1" else "0",
                            TestimonyMeta.LAST_REJOICED_AT to System.currentTimeMillis().toString()
                        )
                    )
                )
            }
            testimonyRefreshTick.value += 1
        } else if (!story.isPersonal && story.circleId != null && story.postId != null) {
            runCatching { Circles2.repository(getApplication()).api.react(story.circleId, story.postId, ReactionKind.Amen) }
        }
    }

    /** Increments the gratitude/Amen milestone counter without toggling off (e.g. revisiting an old testimony). */
    fun celebrateAgain(story: TestimonyStory) = viewModelScope.launch {
        val noteId = story.noteId ?: return@launch
        val current = notes.getNote(noteId)
        if (current != null) {
            val count = current.metadata[TestimonyMeta.REJOICE_COUNT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            notes.updateNote(
                current.copy(
                    metadata = current.metadata + mapOf(
                        TestimonyMeta.REJOICE_COUNT to (count + 1).toString(),
                        TestimonyMeta.USER_REJOICED to "1",
                        TestimonyMeta.LAST_REJOICED_AT to System.currentTimeMillis().toString()
                    )
                )
            )
        }
        testimonyRefreshTick.value += 1
    }

    /** Opens a new draft, resumes an existing note/draft, or starts a testimony linked to a prayer request. */
    suspend fun prepareStudioSeed(noteId: String? = null, linkedPrayerId: String? = null): TestimonyStudioSeed =
        withContext(Dispatchers.IO) {
            val targetNote: Note
            val isInitiallyBlank: Boolean
            var prayerTitle: String? = null
            if (noteId != null) {
                targetNote = notes.getNote(noteId) ?: notes.createNote(workflow = RecordingType.TESTIMONY, draft = true)
                isInitiallyBlank = false
            } else if (linkedPrayerId != null) {
                val prayer = notes.getNote(linkedPrayerId)
                prayerTitle = prayer?.title
                notes.markAnswered(linkedPrayerId)
                val started = notes.startTestimony(linkedPrayerId)
                val asDraft = started.copy(
                    metadata = started.metadata +
                        (NoteContent.DRAFT to "1") +
                        (NoteContent.DRAFT_TITLE to started.title) +
                        (TestimonyMeta.PRAYER_ID to linkedPrayerId)
                )
                notes.updateNote(asDraft)
                targetNote = asDraft
                isInitiallyBlank = false
            } else {
                val existingBlank = notes.observeDraftsForWorkflow(RecordingType.TESTIMONY).first()
                    .firstOrNull { !notes.hasUserContent(it.id) }
                targetNote = existingBlank ?: notes.createNote(workflow = RecordingType.TESTIMONY, draft = true)
                isInitiallyBlank = true
            }

            val doc = notes.getDocument(targetNote.id)
            val note = doc?.note ?: targetNote
            val beats = TestimonyBeats.fromBlocks(doc?.blocks.orEmpty())
            val resolvedPrayerId = linkedPrayerId ?: note.metadata[TestimonyMeta.PRAYER_ID]?.takeIf { it.isNotBlank() }
            if (prayerTitle == null && resolvedPrayerId != null) {
                prayerTitle = notes.getNote(resolvedPrayerId)?.title
            }

            TestimonyStudioSeed(
                noteId = note.id,
                title = note.title,
                beats = beats,
                category = TestimonyCategory.fromKey(note.metadata[TestimonyMeta.CATEGORY]),
                verseRef = note.metadata[TestimonyMeta.VERSE_REF]?.takeIf { it.isNotBlank() },
                verseText = note.metadata[TestimonyMeta.VERSE_TEXT]?.takeIf { it.isNotBlank() },
                storyDateMs = note.eventDate ?: note.createdAt,
                linkedPrayerId = resolvedPrayerId,
                linkedPrayerTitle = prayerTitle,
                isDraft = NoteContent.isDraft(note),
                isInitiallyBlank = isInitiallyBlank && note.title.isBlank() && !beats.hasAnyContent
            )
        }

    /** Looks up a verse preview text for a typed Scripture reference (e.g. "Psalm 23:1-3"). */
    suspend fun lookupScripturePassage(referenceText: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
        val parsed = com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser.parse(referenceText)
            ?: return@withContext null
        val verseText = when (val result = runCatching { scripture.passage(parsed) }.getOrNull()) {
            is PassageResult.Found -> result.passage.text.takeIf { it.isNotBlank() }
            else -> null
        }
        parsed.display() to verseText
    }

    /**
     * Auto-saves or publishes a Testimony from [TestimonyStudioSheet]. Runs in [ioClosingScope] with
     * [NonCancellable] when called from [saveStudioOnExitNonBlocking] so Back / Close never loses typed words.
     */
    suspend fun saveStudioStory(
        noteId: String,
        title: String,
        beats: TestimonyBeats,
        category: TestimonyCategory?,
        verseRef: String?,
        verseText: String?,
        storyDateMs: Long,
        linkedPrayerId: String?,
        publish: Boolean
    ) = withContext(Dispatchers.IO + NonCancellable) {
        val doc = notes.getDocument(noteId) ?: return@withContext
        val mergedBlocks = TestimonyBeats.toBlocks(noteId, doc.blocks, beats)
        notes.saveBlocks(noteId, mergedBlocks)

        val current = notes.getNote(noteId) ?: doc.note
        var nextMeta = current.metadata.toMutableMap()
        if (category != null) nextMeta[TestimonyMeta.CATEGORY] = category.key else nextMeta.remove(TestimonyMeta.CATEGORY)
        if (!verseRef.isNullOrBlank()) nextMeta[TestimonyMeta.VERSE_REF] = verseRef.trim() else nextMeta.remove(TestimonyMeta.VERSE_REF)
        if (!verseText.isNullOrBlank()) nextMeta[TestimonyMeta.VERSE_TEXT] = verseText.trim() else nextMeta.remove(TestimonyMeta.VERSE_TEXT)
        if (!linkedPrayerId.isNullOrBlank()) nextMeta[TestimonyMeta.PRAYER_ID] = linkedPrayerId
        if (publish) {
            nextMeta.remove(NoteContent.DRAFT)
            nextMeta.remove(NoteContent.DRAFT_TITLE)
        }
        val effectiveTitle = title.trim().ifBlank {
            if (publish && beats.hasAnyContent) "Testimony of God's Faithfulness" else title.trim()
        }
        notes.updateNote(
            current.copy(
                title = effectiveTitle,
                eventDate = storyDateMs,
                metadata = nextMeta
            )
        )
        if (publish) notes.keepDraft(noteId)
        testimonyRefreshTick.value += 1
    }

    /** Non-blocking flush when dismissing [TestimonyStudioSheet] or pausing the app. */
    fun flushStudioOnExit(
        noteId: String,
        title: String,
        beats: TestimonyBeats,
        category: TestimonyCategory?,
        verseRef: String?,
        verseText: String?,
        storyDateMs: Long,
        linkedPrayerId: String?,
        wasPublished: Boolean,
        onComplete: () -> Unit = {}
    ) {
        ioClosingScope.launch {
            val hasMeaningfulData = title.isNotBlank() ||
                beats.hasAnyContent ||
                category != null ||
                !verseRef.isNullOrBlank() ||
                !linkedPrayerId.isNullOrBlank()
            if (hasMeaningfulData || wasPublished) {
                saveStudioStory(
                    noteId = noteId,
                    title = title,
                    beats = beats,
                    category = category,
                    verseRef = verseRef,
                    verseText = verseText,
                    storyDateMs = storyDateMs,
                    linkedPrayerId = linkedPrayerId,
                    publish = wasPublished
                )
            } else {
                // Untouched blank draft shell: clean up silently
                val hasContent = notes.hasUserContent(noteId)
                if (!hasContent) notes.deleteNote(noteId)
            }
            withContext(Dispatchers.Main.immediate) { onComplete() }
        }
    }

    fun discardDraftTestimony(noteId: String) = viewModelScope.launch {
        notes.deleteNote(noteId)
        testimonyRefreshTick.value += 1
    }

    /** Publishes a personal testimony to a chosen Circle with one tap and records the Circle badge locally. */
    fun shareTestimonyToCircle(
        circle: Circle,
        story: TestimonyStory,
        anonymous: Boolean = false,
        onResult: (CirclesResult<CreatedPost>) -> Unit
    ) = viewModelScope.launch {
        val repo = runCatching { Circles2.repository(getApplication()) }.getOrNull()
        if (repo == null) {
            onResult(CirclesResult.Err(com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure.notConnected))
            return@launch
        }
        val title = story.title.ifBlank { "Testimony of God's Faithfulness" }
        val bodyText = buildString {
            append(title)
            val content = story.fullText.ifBlank { story.excerpt }
            if (content.isNotBlank()) {
                append("\n\n")
                append(content)
            }
        }
        val result = repo.createPost(
            circleId = circle.id,
            body = bodyText.take(4000),
            type = PostType.Testimony,
            anonymous = anonymous,
            verseRef = story.verseRef
        )
        if (result is CirclesResult.Ok && story.noteId != null) {
            val current = notes.getNote(story.noteId)
            if (current != null) {
                val existing = current.metadata[TestimonyMeta.SHARED_CIRCLES]
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                val updated = (existing + circle.name).distinct().joinToString(", ")
                notes.updateNote(current.copy(metadata = current.metadata + (TestimonyMeta.SHARED_CIRCLES to updated)))
            }
            testimonyRefreshTick.value += 1
        }
        onResult(result)
    }

    companion object {
        private val ioClosingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

private fun <T> kotlinx.coroutines.flow.Flow<T?>.kotlinx_catch(): kotlinx.coroutines.flow.Flow<T?> =
    kotlinx.coroutines.flow.flow { try { collect { emit(it) } } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; emit(null) } }
