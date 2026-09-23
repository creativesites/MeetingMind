package com.example.feature.notes.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.MeetMindDatabase
import com.example.core.export.ExportFormat
import com.example.core.export.NoteExportOptions
import com.example.core.export.NoteExportService
import com.example.core.model.Attachment
import com.example.core.model.AttachmentKind
import com.example.core.model.BlockSource
import com.example.core.model.Note
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.Notebook
import com.example.core.model.Tag
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/** A recording inside a note, as its card shows it. */
data class RecordingCard(
    val meetingId: String,
    val title: String,
    val durationMs: Long,
    val status: String,
    val summary: String?,
    val audioPath: String?
)

/** One transcript segment offered by the "Quote from a recording" picker. */
data class ExcerptCandidate(
    val meetingId: String,
    val meetingTitle: String,
    val segmentId: String,
    val speaker: String?,
    val startMs: Long,
    val text: String
)

/**
 * The note editor's state.
 *
 * The block list lives here and is the source of truth while the note is open. It is written to
 * the database shortly after every change, and once more when the editor closes, so nothing is
 * lost to a crash or a swipe-away. The database is read once on open; after that only the parts
 * the editor doesn't own (attachments, tags, recordings, backlinks) are observed.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NoteEditorViewModel(application: Application, val noteId: String) : AndroidViewModel(application) {

    private val database = MeetMindDatabase.getInstance(application)
    private val notes = NoteRepository(application, database)
    private val media = MediaImporter(application, notes)
    private val history = UndoHistory()

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    private val _blocks = MutableStateFlow<List<NoteBlock>>(emptyList())
    val blocks: StateFlow<List<NoteBlock>> = _blocks.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Set when the note no longer exists (deleted elsewhere); the screen closes. */
    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    /** Where the caret should go next. The counter makes a repeat request for the same spot fire. */
    data class FocusRequest(val target: FocusTarget, val serial: Long)
    private val _focus = MutableStateFlow<FocusRequest?>(null)
    val focus: StateFlow<FocusRequest?> = _focus.asStateFlow()
    private var focusSerial = 0L

    /** The block with the caret and its selection, as the formatting toolbar needs them. */
    data class Selection(val blockId: String, val start: Int, val end: Int)
    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    /** Styles switched on (or off) with no text selected; they apply to what is typed next. */
    private val _pendingOn = MutableStateFlow<Set<InlineStyle>>(emptySet())
    val pendingOn: StateFlow<Set<InlineStyle>> = _pendingOn.asStateFlow()
    private val _pendingOff = MutableStateFlow<Set<InlineStyle>>(emptySet())
    val pendingOff: StateFlow<Set<InlineStyle>> = _pendingOff.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _saved = MutableStateFlow(true)
    /** False while a change is waiting to be written. */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    val attachments: StateFlow<Map<String, Attachment>> = database.attachmentDao().observeForNote(noteId)
        .map { list -> list.associate { e -> e.id to Attachment(e.id, e.noteId, runCatching { AttachmentKind.valueOf(e.kind) }.getOrDefault(AttachmentKind.FILE), e.path, e.mimeType, e.sizeBytes, e.width, e.height, e.durationMs, e.caption, e.createdAt) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val tags: StateFlow<List<Tag>> = notes.observeDocument(noteId).map { it?.tags.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allTags: StateFlow<List<Tag>> = notes.observeTags().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notebooks: StateFlow<List<Notebook>> = notes.observeNotebooks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val backlinks: StateFlow<List<Note>> = notes.observeBacklinks(noteId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recordings: StateFlow<Map<String, RecordingCard>> = database.noteDao().observeMeetingsForNote(noteId)
        .map { list ->
            list.associate {
                it.id to RecordingCard(it.id, it.title, it.durationMs, it.status, it.summaryText, it.audioFilePath)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Titles of notes that NOTE_LINK blocks point to, kept current if a linked note is renamed. */
    val linkedTitles: StateFlow<Map<String, String>> = _blocks
        .map { blocks -> blocks.mapNotNull { it.payload[NoteBlock.PAYLOAD_NOTE_ID] }.toSet() }
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyMap())
            else combine(ids.map { id -> notes.observeNote(id).map { id to (it?.title?.ifBlank { "Untitled note" } ?: "Deleted note") } }) { it.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val doc = notes.getDocument(noteId)
            if (doc == null) { _gone.value = true; return@launch }
            _note.value = doc.note
            _blocks.value = BlockEditing.ensureTrailingParagraph(doc.blocks.sortedBy { it.position }, noteId)
            _loaded.value = true
            // Keep note-level fields (title from a recording, status) current without touching blocks.
            notes.observeNote(noteId).collect { fresh ->
                if (fresh == null) _gone.value = true
                else _note.value = _note.value?.let { mine ->
                    // The title the user is typing wins over one arriving from the database.
                    fresh.copy(title = if (titleDirty) mine.title else fresh.title)
                } ?: fresh
            }
        }
        // A recording added while the note is open (Record here) gets its card.
        viewModelScope.launch {
            recordings.collect { cards ->
                if (!_loaded.value) return@collect
                val shown = _blocks.value.mapNotNull { it.payload[NoteBlock.PAYLOAD_MEETING_ID] }.toSet()
                val missing = cards.keys.filter { it !in shown }
                if (missing.isNotEmpty()) {
                    val added = missing.map { recordingBlock(it) }
                    update(BlockEditing.insertAfter(_blocks.value, lastContentIndex(), added).blocks, recordUndo = false)
                }
            }
        }
    }

    // ------------------------------------------------------------ text

    /** The text of block [blockId] changed to [newText]; [cursor] is where the caret now is. */
    fun onTextChanged(blockId: String, newText: String, cursor: Int) {
        val blocks = _blocks.value
        val index = blocks.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = blocks[index]
        if (newText == block.content.text) return
        val content = block.content.withEditedText(newText, _pendingOn.value, _pendingOff.value)
        var result = BlockEditing.applyTextEdit(blocks, index, content, cursor)
        BlockEditing.applyShortcut(result.blocks, index, cursor)?.let { result = it }
        val structural = result.blocks.size != blocks.size || result.blocks[index].type != block.type
        history.record(blocks, typingBlockId = if (structural) null else blockId)
        update(result.blocks, recordUndo = false)
        if (structural || result.focus?.blockId != blockId) result.focus?.let { requestFocus(it) }
    }

    fun onSelectionChanged(blockId: String, start: Int, end: Int) {
        val previous = _selection.value
        _selection.value = Selection(blockId, minOf(start, end), maxOf(start, end))
        // Moving the caret somewhere else ends any "bold is on for what I type next".
        if (previous != null && (previous.blockId != blockId || previous.start != start)) {
            _pendingOn.value = emptySet()
            _pendingOff.value = emptySet()
        }
    }

    fun onBlockFocusLost(blockId: String) {
        if (_selection.value?.blockId == blockId) _selection.value = null
    }

    fun onBackspaceAtStart(blockId: String) {
        val blocks = _blocks.value
        val index = blocks.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val result = BlockEditing.backspaceAtStart(blocks, index)
        if (result.blocks != blocks) update(result.blocks)
        result.focus?.let { requestFocus(it) }
    }

    // ------------------------------------------------------------ formatting

    /** Bold, italic and the rest. With nothing selected it applies to what is typed next. */
    fun toggleInline(style: InlineStyle) {
        val sel = _selection.value ?: return
        val blocks = _blocks.value
        val index = blocks.indexOfFirst { it.id == sel.blockId }.takeIf { it >= 0 } ?: return
        val block = blocks[index]
        if (sel.start == sel.end) {
            val activeNow = isActive(style)
            if (activeNow) { _pendingOn.value -= style; _pendingOff.value += style }
            else { _pendingOff.value -= style; _pendingOn.value += style }
            return
        }
        val content = block.content.toggleStyle(style, sel.start, sel.end)
        update(blocks.replace(index, block.copy(content = content)))
    }

    /** Whether [style] shows as on in the toolbar for the current caret or selection. */
    fun isActive(style: InlineStyle): Boolean {
        val sel = _selection.value ?: return false
        if (style in _pendingOff.value) return false
        if (style in _pendingOn.value) return true
        val block = _blocks.value.firstOrNull { it.id == sel.blockId } ?: return false
        return block.content.hasStyle(style, sel.start, sel.end)
    }

    fun setLink(url: String?) {
        val sel = _selection.value ?: return
        val blocks = _blocks.value
        val index = blocks.indexOfFirst { it.id == sel.blockId }.takeIf { it >= 0 } ?: return
        val block = blocks[index]
        val (start, end) = if (sel.start == sel.end) {
            block.content.linkAt(sel.start)?.let { it.start to it.end } ?: return
        } else sel.start to sel.end
        val clean = url?.trim()?.takeIf { it.isNotEmpty() }?.let { if ("://" in it || it.startsWith("mailto:")) it else "https://$it" }
        val content = if (clean == null) block.content.removeStyle(InlineStyle.LINK, start, end)
        else block.content.applyStyle(InlineStyle.LINK, start, end, clean)
        update(blocks.replace(index, block.copy(content = content)))
    }

    fun currentLink(): String? {
        val sel = _selection.value ?: return null
        return _blocks.value.firstOrNull { it.id == sel.blockId }?.content?.linkAt(sel.start)?.url
    }

    fun toggleBlockType(type: NoteBlockType) {
        val id = _selection.value?.blockId ?: return
        val index = _blocks.value.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        update(BlockEditing.toggleType(_blocks.value, index, type))
    }

    fun focusedType(): NoteBlockType? = _selection.value?.let { sel -> _blocks.value.firstOrNull { it.id == sel.blockId }?.type }

    fun indent(delta: Int) {
        val id = _selection.value?.blockId ?: return
        val index = _blocks.value.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        update(BlockEditing.indent(_blocks.value, index, delta))
    }

    fun toggleChecked(blockId: String) {
        val index = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        update(BlockEditing.toggleChecked(_blocks.value, index))
    }

    // ------------------------------------------------------------ blocks

    fun move(from: Int, to: Int, recordUndo: Boolean = false) {
        update(BlockEditing.move(_blocks.value, from, to), recordUndo)
    }

    /** Called once when a drag starts, so the whole drag undoes as one step. */
    fun beginMove() {
        history.record(_blocks.value)
        refreshHistory()
    }

    fun moveBy(blockId: String, delta: Int) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        update(BlockEditing.move(_blocks.value, i, (i + delta).coerceIn(0, _blocks.value.lastIndex)))
    }

    fun deleteBlock(blockId: String) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = _blocks.value[i]
        update(BlockEditing.ensureTrailingParagraph(BlockEditing.delete(_blocks.value, i), noteId))
        // A deleted photo's file goes too; a deleted recording card keeps its recording.
        block.payload[NoteBlock.PAYLOAD_ATTACHMENT_ID]?.let { id ->
            if (_blocks.value.none { it.payload[NoteBlock.PAYLOAD_ATTACHMENT_ID] == id }) {
                viewModelScope.launch { notes.deleteAttachment(id) }
            }
        }
    }

    fun duplicateBlock(blockId: String) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        update(BlockEditing.duplicate(_blocks.value, i))
    }

    fun turnInto(blockId: String, type: NoteBlockType) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = _blocks.value[i]
        if (!block.type.isText) return
        update(_blocks.value.replace(i, block.copy(type = type, indent = if (type.isListItem) block.indent else 0)))
        requestFocus(FocusTarget(blockId, block.content.text.length))
    }

    fun setCaption(blockId: String, caption: String) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = _blocks.value[i]
        history.record(_blocks.value, typingBlockId = blockId)
        update(_blocks.value.replace(i, block.copy(content = RichText.plain(caption))), recordUndo = false)
    }

    /** Inserts a fresh text block of [type] after the caret's block (or at the end). */
    fun insertText(type: NoteBlockType) {
        val block = NoteBlock(id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = type)
        insert(listOf(block))
    }

    fun insertDivider() = insert(listOf(NoteBlock(id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = NoteBlockType.DIVIDER)))

    fun insertMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = uris.mapNotNull { media.import(noteId, it) }
            if (imported.size < uris.size) _message.value = "Couldn't add ${uris.size - imported.size} of the items"
            insert(imported.map { mediaBlock(it) })
        }
    }

    /** A capture target for the camera; call [adoptCapture] once it has written the file. */
    fun captureTarget(video: Boolean) = media.newCaptureTarget(noteId, if (video) "mp4" else "jpg")

    fun adoptCapture(file: File, video: Boolean) {
        viewModelScope.launch {
            val attachment = media.adopt(noteId, file, if (video) "video/mp4" else "image/jpeg")
            if (attachment != null) insert(listOf(mediaBlock(attachment)))
        }
    }

    fun shareableUri(file: File): Uri = media.shareableUri(file)

    /** Adds a Bible passage the person chose. Its text is fetched live wherever it's shown. */
    fun insertScripture(reference: com.example.core.scripture.ScriptureReference, versionId: Int? = null) {
        val blockId = NoteRepository.newId("block")
        val refId = NoteRepository.newId("scripture")
        insert(
            listOf(
                NoteBlock(
                    id = blockId, noteId = noteId, position = 0, type = NoteBlockType.SCRIPTURE,
                    content = RichText.plain(reference.display()),
                    payload = mapOf(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to refId, "reference" to reference.display()),
                    source = BlockSource.SCRIPTURE
                )
            )
        )
        viewModelScope.launch {
            notes.addScriptureRefs(
                listOf(
                    com.example.core.model.ScriptureRef(
                        refId, noteId, blockId, reference.usfm, reference.chapter, reference.verseStart, reference.verseEnd,
                        versionId, com.example.core.model.ScriptureOrigin.USER, createdAt = System.currentTimeMillis()
                    )
                )
            )
        }
    }

    fun insertNoteLink(target: Note) {
        insert(
            listOf(
                NoteBlock(
                    id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = NoteBlockType.NOTE_LINK,
                    payload = mapOf(NoteBlock.PAYLOAD_NOTE_ID to target.id, "title" to target.title)
                )
            )
        )
    }

    fun insertExcerpt(candidate: ExcerptCandidate) {
        insert(
            listOf(
                NoteBlock(
                    id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = NoteBlockType.TRANSCRIPT_EXCERPT,
                    content = RichText.plain(candidate.text),
                    payload = buildMap {
                        put(NoteBlock.PAYLOAD_MEETING_ID, candidate.meetingId)
                        put(NoteBlock.PAYLOAD_START_MS, candidate.startMs.toString())
                        candidate.speaker?.let { put(NoteBlock.PAYLOAD_SPEAKER, it) }
                    },
                    source = BlockSource.TRANSCRIPT,
                    sourceSegmentIds = listOf(candidate.segmentId)
                )
            )
        )
    }

    suspend fun excerptCandidates(): List<ExcerptCandidate> = withContext(Dispatchers.IO) {
        recordings.value.values.flatMap { card ->
            database.transcriptDao().getSegmentsForMeetingDirect(card.meetingId).map {
                ExcerptCandidate(card.meetingId, card.title, it.id, it.speakerName, it.startMs, it.cleanedText ?: it.text)
            }
        }
    }

    suspend fun linkableNotes(query: String): List<Note> {
        val all = if (query.isBlank()) notes.observeNotes().first().take(50) else notes.searchNotes(query)
        return all.filter { it.id != noteId }
    }

    private fun insert(newBlocks: List<NoteBlock>) {
        if (newBlocks.isEmpty()) return
        val blocks = _blocks.value
        val at = _selection.value?.blockId?.let { id -> blocks.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: lastContentIndex()
        val result = BlockEditing.insertAfter(blocks, at, newBlocks)
        update(result.blocks)
        result.focus?.let { requestFocus(it) }
    }

    /** The last block that isn't the empty trailing paragraph. */
    private fun lastContentIndex(): Int {
        val blocks = _blocks.value
        val last = blocks.lastIndex
        return if (last >= 0 && blocks[last].type == NoteBlockType.PARAGRAPH && blocks[last].content.isEmpty) last - 1 else last
    }

    private fun mediaBlock(a: Attachment) = NoteBlock(
        id = NoteRepository.newId("block"), noteId = noteId, position = 0,
        type = when (a.kind) { AttachmentKind.IMAGE -> NoteBlockType.IMAGE; AttachmentKind.VIDEO -> NoteBlockType.VIDEO; else -> NoteBlockType.AUDIO },
        payload = mapOf(NoteBlock.PAYLOAD_ATTACHMENT_ID to a.id)
    )

    private fun recordingBlock(meetingId: String) = NoteBlock(
        id = "block_rec_$meetingId", noteId = noteId, position = 0, type = NoteBlockType.RECORDING,
        payload = mapOf(NoteBlock.PAYLOAD_MEETING_ID to meetingId), source = BlockSource.TRANSCRIPT, sectionKey = "recording"
    )

    // ------------------------------------------------------------ undo

    fun undo() {
        history.undo(_blocks.value)?.let { update(it, recordUndo = false) }
        refreshHistory()
    }

    fun redo() {
        history.redo(_blocks.value)?.let { update(it, recordUndo = false) }
        refreshHistory()
    }

    private fun refreshHistory() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    // ------------------------------------------------------------ note fields

    private var titleDirty = false
    private var titleJob: Job? = null

    fun setTitle(title: String) {
        _note.value = _note.value?.copy(title = title)
        titleDirty = true
        _saved.value = false
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            if (!promoteDraftIfNeeded()) { titleDirty = false; _saved.value = true; return@launch }
            notes.renameNote(noteId, title)
            titleDirty = false
            _saved.value = saveJob?.isActive != true
        }
    }

    /** Workflow fields such as a sermon's speaker and church. Blank removes the field. */
    fun setMetadata(values: Map<String, String>) = viewModelScope.launch {
        val current = _note.value ?: return@launch
        val merged = (current.metadata + values).filterValues { it.isNotBlank() }
        _note.value = current.copy(metadata = merged)
        notes.updateNote(current.copy(metadata = merged))
    }

    // ---------------------------------------------------------------- prayer requests

    /** Adds a dated update to this prayer request, in the open editor's own blocks. */
    fun addPrayerUpdate(text: String) {
        if (text.isBlank()) return
        update(NoteRepository.withPrayerUpdate(_blocks.value, noteId, text, System.currentTimeMillis()))
        _message.value = "Update added"
    }

    /** Marks this request answered and opens a testimony pre-filled from it. */
    fun markAnswered(onTestimony: (String) -> Unit) = viewModelScope.launch {
        flush()
        notes.markAnswered(noteId)
        _note.value = _note.value?.copy(status = com.example.core.model.NoteStatus.ANSWERED, answeredAt = System.currentTimeMillis())
        onTestimony(notes.startTestimony(noteId).id)
    }

    fun reopenRequest() = viewModelScope.launch {
        notes.reopenRequest(noteId)
        _note.value = _note.value?.copy(status = com.example.core.model.NoteStatus.OPEN, answeredAt = null)
    }

    fun startTestimony(onCreated: (String) -> Unit) = viewModelScope.launch {
        flush()
        onCreated(notes.startTestimony(noteId).id)
    }

    fun setPinned(pinned: Boolean) = viewModelScope.launch { notes.setPinned(noteId, pinned) }
    fun setPrivate(private: Boolean) = viewModelScope.launch { notes.setPrivate(noteId, private) }
    fun moveToNotebook(notebookId: String) = viewModelScope.launch {
        notes.moveToNotebook(noteId, notebookId)
        _message.value = "Moved to ${notebooks.value.firstOrNull { it.id == notebookId }?.name ?: "notebook"}"
    }
    fun createNotebookAndMove(name: String) = viewModelScope.launch {
        val notebook = notes.createNotebook(name, com.example.core.model.NotebookSpace.PERSONAL)
        notes.moveToNotebook(noteId, notebook.id)
        _message.value = "Moved to ${notebook.name}"
    }
    fun addTag(name: String) = viewModelScope.launch { notes.addTag(noteId, name) }
    fun removeTag(tagId: String) = viewModelScope.launch { notes.removeTag(noteId, tagId) }
    fun archive(onDone: () -> Unit) = viewModelScope.launch { flush(); notes.archiveNote(noteId); onDone() }

    fun delete(onDone: (detachedRecordings: Int) -> Unit) = viewModelScope.launch {
        saveJob?.cancel()
        titleJob?.cancel()
        deleted = true
        val detached = notes.deleteNote(noteId)
        onDone(detached.size)
    }

    // ------------------------------------------------------------ export

    suspend fun exportTo(format: ExportFormat, includePrivate: Boolean, out: OutputStream): Boolean {
        flush()
        return withContext(Dispatchers.IO) {
            val workflow = _note.value?.workflow ?: com.example.core.model.RecordingType.GENERAL
            val options = NoteExportOptions(
                includePrivateSections = includePrivate,
                privateSectionKeys = com.example.core.model.Workflows.template(workflow).privateKeys,
                serifBody = com.example.core.model.Workflows.usesSerif(workflow)
            )
            runCatching { NoteExportService(getApplication(), database).export(noteId, format, out, options) }
                .getOrDefault(false)
        }
    }

    /** Writes an export into the shareable cache folder and returns the file. */
    suspend fun exportForSharing(format: ExportFormat, includePrivate: Boolean): File? {
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${fileSafeTitle()}.${format.extension}")
        val ok = file.outputStream().use { exportTo(format, includePrivate, it) }
        return file.takeIf { ok && it.length() > 0 }
    }

    fun fileSafeTitle(): String =
        (_note.value?.title?.ifBlank { null } ?: "Note").replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(60).ifBlank { "Note" }

    // ------------------------------------------------------------ saving

    private var deleted = false

    private fun update(newBlocks: List<NoteBlock>, recordUndo: Boolean = true) {
        if (recordUndo) history.record(_blocks.value)
        _blocks.value = newBlocks
        refreshHistory()
        scheduleSave()
    }

    private fun scheduleSave() {
        _saved.value = false
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            persist()
        }
    }

    private suspend fun persist() {
        if (deleted || !_loaded.value) return
        // A draft with nothing of the person's in it isn't written (PLAN_V2 F0).
        if (!promoteDraftIfNeeded()) { _saved.value = true; return }
        notes.saveBlocks(noteId, _blocks.value)
        _saved.value = titleJob?.isActive != true
    }

    /**
     * For a draft: true (and it becomes a real note) once it has content; false while it's still
     * empty. Always true for a note that isn't a draft.
     */
    private suspend fun promoteDraftIfNeeded(): Boolean {
        val note = _note.value ?: return true
        if (!com.example.core.repository.NoteContent.isDraft(note)) return true
        val hasContent = com.example.core.repository.NoteContent.hasUserContent(note, _blocks.value) || notes.hasUserContent(noteId)
        if (!hasContent) return false
        notes.keepDraft(noteId)
        _note.value = note.copy(metadata = note.metadata - com.example.core.repository.NoteContent.DRAFT - com.example.core.repository.NoteContent.DRAFT_TITLE)
        return true
    }

    /** Writes anything pending right now. */
    suspend fun flush() {
        saveJob?.cancel()
        titleJob?.cancel()
        if (deleted || !_loaded.value) return
        if (titleDirty && promoteDraftIfNeeded()) { notes.renameNote(noteId, _note.value?.title.orEmpty()); titleDirty = false }
        persist()
    }

    // ------------------------------------------------------------ note AI

    private val noteAi = com.example.ai.notes.NoteAiRepository(application)

    /** This note's AI runs: the one in flight, or the last result waiting to be used. */
    val aiJobs: StateFlow<List<com.example.ai.notes.NoteAiJob>> =
        noteAi.observe(noteId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun runAi(tool: com.example.ai.notes.NoteAiTool, question: String? = null) = viewModelScope.launch {
        flush()
        noteAi.run(com.example.ai.notes.NoteAiTarget.NOTE, noteId, tool, question)
    }

    suspend fun relatedNotes() = notes.relatedNotes(noteId)

    fun cancelAi(jobId: String) = viewModelScope.launch { noteAi.cancel(jobId) }
    fun dismissAi(jobId: String) = viewModelScope.launch { noteAi.dismiss(jobId) }

    fun applySummary(jobId: String, points: List<com.example.ai.notes.CitedItem>) {
        update(com.example.ai.notes.NoteAiApply.withSummary(_blocks.value, noteId, points))
        dismissAi(jobId); _message.value = "Summary added — Undo to remove it"
    }

    fun applyActions(jobId: String, actions: List<com.example.ai.notes.CitedItem>) {
        update(com.example.ai.notes.NoteAiApply.withActions(_blocks.value, noteId, actions))
        dismissAi(jobId); _message.value = "${actions.size} action ${if (actions.size == 1) "item" else "items"} added"
    }

    fun applyOrganized(jobId: String, result: com.example.ai.notes.NoteAiOutcome.Sections) {
        update(com.example.ai.notes.NoteAiApply.organized(_blocks.value, noteId, result))
        dismissAi(jobId); _message.value = "Organised — Undo puts it back as it was"
    }

    /** Puts the caret in [blockId] at [cursor]. */
    fun focusBlock(blockId: String, cursor: Int) = requestFocus(FocusTarget(blockId, cursor))

    private fun requestFocus(target: FocusTarget) {
        _focus.value = FocusRequest(target, ++focusSerial)
    }

    override fun onCleared() {
        // The view model's own scope is already cancelled here, so the last save runs on its own.
        if (!deleted && _loaded.value) {
            val blocks = _blocks.value
            val note = _note.value
            val title = note?.title.takeIf { titleDirty }
            closingScope.launch {
                val draft = note != null && com.example.core.repository.NoteContent.isDraft(note)
                if (draft) {
                    val hasContent = com.example.core.repository.NoteContent.hasUserContent(note!!, blocks) || notes.hasUserContent(noteId)
                    if (!hasContent) { notes.deleteNote(noteId); return@launch }
                    notes.keepDraft(noteId)
                }
                title?.let { notes.renameNote(noteId, it) }
                notes.saveBlocks(noteId, blocks)
                discardIfEmpty()
            }
        }
        super.onCleared()
    }

    /** A note opened and left without a word in it isn't kept. */
    private suspend fun discardIfEmpty() {
        if (notes.getNote(noteId) != null && !notes.hasUserContent(noteId)) notes.deleteNote(noteId)
    }

    private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().apply { this[index] = value }

    companion object {
        const val SAVE_DELAY_MS = 600L
        private val closingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
