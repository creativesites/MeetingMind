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

    // ------------------------------------------------------------ version history (PRD_M0 §4.6)

    private val versions = com.example.core.notes.NoteVersionRepository(database, notes)
    val versionList: StateFlow<List<com.example.core.database.NoteVersionSummary>> =
        versions.observe(noteId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The note as it was opened: saved as a version when this session first changes it. */
    private var opened: com.example.core.notes.NoteSnapshot? = null
    private var sessionStarted = false
    private var lastSessionSnapshotAt = 0L

    /** Saves the note as it was when opened (once), and every [SESSION_SNAPSHOT_MS] of editing after. */
    private suspend fun sessionSnapshot() {
        val now = System.currentTimeMillis()
        if (!sessionStarted) {
            sessionStarted = true
            lastSessionSnapshotAt = now
            opened?.let { versions.snapshot(noteId, com.example.core.notes.VersionReason.EDIT_SESSION, blocks = it.blocks, title = it.title) }
        } else if (now - lastSessionSnapshotAt >= SESSION_SNAPSHOT_MS) {
            lastSessionSnapshotAt = now
            versions.snapshot(noteId, com.example.core.notes.VersionReason.EDIT_SESSION, blocks = _blocks.value)
        }
    }

    /** Saves the note as it is right now, before something changes a lot of it. */
    private fun snapshotBefore(reason: com.example.core.notes.VersionReason, label: String? = null) {
        val current = _blocks.value
        val title = _note.value?.title
        viewModelScope.launch { versions.snapshot(noteId, reason, label, blocks = current, title = title) }
    }

    fun saveVersion() = viewModelScope.launch {
        flush()
        val id = versions.snapshot(noteId, com.example.core.notes.VersionReason.MANUAL)
        _message.value = if (id != null) "Version saved" else "Nothing new to save"
    }

    suspend fun loadVersion(versionId: String) = versions.load(versionId)

    fun restoreVersion(versionId: String) = viewModelScope.launch {
        flush()
        val restored = versions.restore(versionId) ?: return@launch
        history.record(_blocks.value)
        _blocks.value = BlockEditing.ensureTrailingParagraph(restored.blocks, noteId)
        _note.value = _note.value?.copy(title = restored.title)
        refreshHistory()
        _message.value = "Restored. The version you replaced is in history too."
    }

    init {
        viewModelScope.launch {
            val doc = notes.getDocument(noteId)
            if (doc == null) { _gone.value = true; return@launch }
            _note.value = doc.note
            _blocks.value = BlockEditing.ensureTrailingParagraph(doc.blocks.sortedBy { it.position }, noteId)
            opened = com.example.core.notes.NoteSnapshot(doc.note.title, doc.blocks.sortedBy { it.position })
            _loaded.value = true
            // Keep note-level fields (title from a recording, status) current without touching blocks.
            notes.observeNote(noteId).collect { fresh ->
                if (fresh == null) _gone.value = true
                else _note.value = _note.value?.let { mine ->
                    // The title the user is typing wins over one arriving from the database.
                    // The saved title is trimmed; the one on screen keeps the space just typed.
                    fresh.copy(title = if (titleDirty || mine.title.trim() == fresh.title.trim()) mine.title else fresh.title)
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
        if (pasteAsMarkdown(blocks, index, newText)) return
        val content = block.content.withEditedText(newText, _pendingOn.value, _pendingOff.value)
        var result = BlockEditing.applyTextEdit(blocks, index, content, cursor)
        BlockEditing.applyShortcut(result.blocks, index, cursor)?.let { result = it }
        val structural = result.blocks.size != blocks.size || result.blocks[index].type != block.type
        history.record(blocks, typingBlockId = if (structural) null else blockId)
        update(result.blocks, recordUndo = false)
        if (structural || result.focus?.blockId != blockId) result.focus?.let { requestFocus(it) }
    }

    /** A Markdown paste becomes formatted blocks, as one undo step with a version saved first. */
    private fun pasteAsMarkdown(blocks: List<NoteBlock>, index: Int, newText: String): Boolean {
        val inserted = BlockEditing.insertion(blocks[index].content.text, newText)
        if (inserted.text.length < 2) return false
        val result = BlockEditing.pasteMarkdown(blocks, index, inserted, clipboardMarkdown(inserted.text)) ?: return false
        val structural = result.blocks.size != blocks.size || result.blocks[index].type != blocks[index].type
        if (structural) snapshotBefore(com.example.core.notes.VersionReason.BEFORE_PASTE)
        history.record(blocks)
        update(result.blocks, recordUndo = false)
        result.focus?.let { requestFocus(it) }
        if (structural) _message.value = "Pasted as one block · tap it to edit · Undo to take it back"
        return true
    }

    /**
     * The clipboard's formatted copy of [pasted] as Markdown, when the clipboard holds HTML for
     * the very text that arrived (a selection copied from ChatGPT, a web page, Docs). Null
     * otherwise, and whenever the clipboard can't be read.
     */
    private fun clipboardMarkdown(pasted: String): String? = runCatching {
        if ('\n' !in pasted.trim()) return null
        val clipboard = getApplication<android.app.Application>().getSystemService(android.content.ClipboardManager::class.java) ?: return null
        val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
        val html = item.htmlText?.takeIf { it.isNotBlank() } ?: return null
        fun norm(s: CharSequence) = s.toString().replace(Regex("\\s+"), " ").trim()
        val plain = item.text ?: android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT)
        if (norm(plain) != norm(pasted)) return null
        com.example.core.notes.HtmlToMarkdown.convert(html).takeIf { it.isNotBlank() }
    }.getOrNull()

    fun onSelectionChanged(blockId: String, start: Int, end: Int) {
        if (_editingMarkdown.value != null) editMarkdown(null)
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

    // ------------------------------------------------------------ AI edit on a selection

    /** Selected text (or the whole block, with nothing selected) being rewritten by AI. */
    data class AiEdit(
        val blockId: String,
        val start: Int,
        val end: Int,
        val original: String,
        val action: String? = null,
        val busy: Boolean = false,
        val result: String? = null,
        val error: String? = null
    )

    private val _aiEdit = MutableStateFlow<AiEdit?>(null)
    val aiEdit: StateFlow<AiEdit?> = _aiEdit.asStateFlow()
    private var aiEditJob: kotlinx.coroutines.Job? = null
    private val geminiTransport by lazy {
        com.example.ai.cloud.CloudAi.transport(getApplication())
    }

    /** Opens AI edit on the selection; with only a caret, on the whole block. False when there is nothing to edit. */
    fun startAiEdit(): Boolean {
        val sel = _selection.value ?: return false
        val block = _blocks.value.firstOrNull { it.id == sel.blockId } ?: return false
        val (start, end) = if (sel.end > sel.start) sel.start to sel.end else 0 to block.content.text.length
        val text = block.content.text.substring(start.coerceAtMost(block.content.text.length), end.coerceAtMost(block.content.text.length))
        if (text.isBlank()) { _message.value = "Write or select some text first"; return false }
        _aiEdit.value = AiEdit(block.id, start, end, text)
        return true
    }

    fun runAiEdit(action: String, instruction: String) {
        val edit = _aiEdit.value ?: return
        aiEditJob?.cancel()
        _aiEdit.value = edit.copy(action = action, busy = true, result = null, error = null)
        aiEditJob = viewModelScope.launch {
            val block = _blocks.value.firstOrNull { it.id == edit.blockId }
            val title = _note.value?.title.orEmpty()
            val prompt = buildString {
                append("Instruction: ").append(instruction).append("\n\n")
                if (title.isNotBlank()) append("The note is titled: ").append(title).append("\n")
                if (block != null && block.content.text.length > edit.original.length) {
                    append("The paragraph it sits in, for context only:\n").append(block.content.text).append("\n\n")
                }
                append("Text to edit:\n").append(edit.original)
            }
            val result = if (!geminiTransport.refreshConfigured()) {
                com.example.ai.common.AiResult.Failed("AI editing needs a Gemini API key. Add one in Settings.")
            } else geminiTransport.execute(
                com.example.ai.cloud.GeminiRequest(
                    modelId = com.example.ai.routing.DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                    systemInstruction = AI_EDIT_SYSTEM,
                    prompt = prompt,
                    temperature = 0.4f
                )
            )
            val current = _aiEdit.value ?: return@launch
            _aiEdit.value = when (result) {
                is com.example.ai.common.AiResult.Success -> {
                    val text = result.value.trim().removeSurrounding("```markdown", "```").removeSurrounding("```", "```").trim()
                    if (text.isBlank()) current.copy(busy = false, error = "The AI returned nothing. Try again.")
                    else current.copy(busy = false, result = text)
                }
                is com.example.ai.common.AiResult.ModelUnavailable -> current.copy(busy = false, error = result.message)
                is com.example.ai.common.AiResult.Failed -> current.copy(busy = false, error = result.message)
                else -> current.copy(busy = false, error = "AI editing isn't available right now.")
            }
        }
    }

    /** Puts the AI's text in place of the original ([replace]) or as new blocks under it. One undo step. */
    fun applyAiEdit(replace: Boolean) {
        val edit = _aiEdit.value ?: return
        val text = edit.result ?: return
        val blocks = _blocks.value
        val index = blocks.indexOfFirst { it.id == edit.blockId }.takeIf { it >= 0 } ?: run { _aiEdit.value = null; return }
        val block = blocks[index]
        snapshotBefore(com.example.core.notes.VersionReason.BEFORE_AI, edit.action)
        val result: EditResult = if (replace) {
            val end = edit.end.coerceAtMost(block.content.text.length)
            val start = edit.start.coerceAtMost(end)
            BlockEditing.pasteMarkdown(blocks, index, BlockEditing.Insertion(start, end, text)) ?: run {
                val (before, _) = block.content.splitAt(start)
                val after = block.content.splitAt(end).second
                val content = before.append(RichText.plain(text)).append(after)
                BlockEditing.applyTextEdit(blocks, index, content, before.text.length + text.length)
            }
        } else {
            BlockEditing.insertAfter(blocks, index, com.example.core.notes.MarkdownImport.parse(text, noteId))
        }
        history.record(blocks)
        update(result.blocks, recordUndo = false)
        result.focus?.let { requestFocus(it) }
        _aiEdit.value = null
        _message.value = if (replace) "Replaced · Undo puts the original back" else "Added below · Undo to remove it"
    }

    fun dismissAiEdit() {
        aiEditJob?.cancel()
        _aiEdit.value = null
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
        val checked = _blocks.value.getOrNull(index)?.checked ?: return
        viewModelScope.launch { tasks.forBlock(blockId)?.takeIf { it.done != checked }?.let { tasks.toggleDone(it.id) } }
    }

    // ------------------------------------------------------------ tasks

    private val tasks by lazy { com.example.core.tasks.TaskReminders.repository(getApplication()) }
    val people: StateFlow<List<com.example.core.tasks.Person>> by lazy {
        tasks.observePeople().stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /** A task drafted from one line of the note — the existing one if the line is already a task. */
    suspend fun taskFor(blockId: String): com.example.core.tasks.Task? {
        tasks.forBlock(blockId)?.let { return it }
        val b = _blocks.value.firstOrNull { it.id == blockId } ?: return null
        val text = b.content.text.trim().ifEmpty { return null }
        val workflow = _note.value?.workflow
        val faith = com.example.ai.faith.AskSermon.isFaith(workflow)
        val kind = when {
            b.sectionKey?.contains("apply", ignoreCase = true) == true || (faith && b.sectionKey?.contains("action") == true) -> com.example.core.tasks.TaskKind.APPLY
            workflow == com.example.core.model.RecordingType.PRAYER_REQUEST -> com.example.core.tasks.TaskKind.PRAYER
            else -> com.example.core.tasks.TaskKind.TASK
        }
        val person = com.example.core.tasks.TaskRules.personMentioned(text, tasks.allPeople())
        return com.example.core.tasks.Task(
            id = "", title = text, kind = kind, personId = person?.id, noteId = noteId, blockId = b.id,
            meetingId = b.payload[NoteBlock.PAYLOAD_MEETING_ID], startMs = b.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()
        )
    }

    fun saveTask(task: com.example.core.tasks.Task, newPersonName: String?) = viewModelScope.launch {
        val personId = newPersonName?.takeIf { it.isNotBlank() }?.let { tasks.savePerson(it).id } ?: task.personId
        tasks.save(task.copy(personId = personId))
        personId?.let { tasks.linkNote(noteId, it) }
        // A line that became a task reads as one: it gets a checkbox.
        task.blockId?.let { id ->
            val i = _blocks.value.indexOfFirst { it.id == id }
            val b = _blocks.value.getOrNull(i)
            if (b != null && b.type != NoteBlockType.CHECKLIST && b.type.isText) update(_blocks.value.toMutableList().also { it[i] = b.copy(type = NoteBlockType.CHECKLIST) })
        }
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

    // ------------------------------------------------------------ paste tools

    /** The tools sheet for one Markdown block: what ran, and its result waiting to be accepted. */
    data class PasteToolState(
        val blockId: String,
        val tool: com.example.core.notes.PasteTool? = null,
        val busy: Boolean = false,
        val progress: String? = null,
        val result: String? = null,
        val error: String? = null,
        val provider: String? = null
    )

    private val _pasteTools = MutableStateFlow<PasteToolState?>(null)
    val pasteTools: StateFlow<PasteToolState?> = _pasteTools.asStateFlow()
    private var pasteToolJob: kotlinx.coroutines.Job? = null

    fun openPasteTools(blockId: String) { _pasteTools.value = PasteToolState(blockId) }
    fun closePasteTools() { pasteToolJob?.cancel(); _pasteTools.value = null }

    fun runPasteTool(tool: com.example.core.notes.PasteTool) {
        val state = _pasteTools.value ?: return
        val block = _blocks.value.firstOrNull { it.id == state.blockId } ?: return
        val text = block.content.text
        pasteToolJob?.cancel()
        _pasteTools.value = state.copy(tool = tool, busy = true, result = null, error = null, progress = "${tool.label}…")
        pasteToolJob = viewModelScope.launch {
            val T = com.example.core.notes.PasteTool
            val pieces = if (T.rewritesWhole(tool)) T.chunks(text) else listOf(text.take(120_000))
            val done = StringBuilder()
            for ((i, piece) in pieces.withIndex()) {
                if (pieces.size > 1) _pasteTools.value = _pasteTools.value?.copy(progress = "${tool.label} · part ${i + 1} of ${pieces.size}…")
                val r = geminiTransport.execute(
                    com.example.ai.cloud.GeminiRequest(
                        modelId = com.example.ai.routing.DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                        systemInstruction = T.SYSTEM,
                        prompt = T.prompt(tool, piece),
                        temperature = 0.3f
                    )
                )
                if (r !is com.example.ai.common.AiResult.Success) {
                    val why = (r as? com.example.ai.common.AiResult.Failed)?.message ?: (r as? com.example.ai.common.AiResult.ModelUnavailable)?.message ?: "The AI couldn't do that just now."
                    _pasteTools.value = _pasteTools.value?.copy(busy = false, error = why, progress = null)
                    return@launch
                }
                if (done.isNotEmpty()) done.append("\n\n")
                done.append(T.clean(r.value))
            }
            _pasteTools.value = _pasteTools.value?.copy(busy = false, result = done.toString().trim(), progress = null, provider = com.example.ai.cloud.DeepSeek.lastProvider)
        }
    }

    /** The free tidy: chat buttons out, code boxed, tables rebuilt. No AI, instant. */
    fun tidyWithoutAi(blockId: String) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = _blocks.value[i]
        val tidy = com.example.core.notes.AiPasteCleanup.clean(block.content.text)
        if (tidy.markdown == block.content.text.trim()) { _message.value = "Already tidy"; return }
        replaceMarkdown(i, tidy.markdown, "Tidy")
        _message.value = tidy.changes.joinToString(" · ").ifBlank { "Tidied" }
    }

    fun acceptPasteTool(replace: Boolean) {
        val state = _pasteTools.value ?: return
        val result = state.result ?: return
        val i = _blocks.value.indexOfFirst { it.id == state.blockId }.takeIf { it >= 0 } ?: return
        val tool = state.tool
        if (replace && tool?.output != com.example.core.notes.PasteTool.Output.BELOW) {
            replaceMarkdown(i, result, tool?.label ?: "AI")
            _message.value = "${tool?.label ?: "Done"} · Step back from the block's tools"
        } else {
            val added = NoteBlock(id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = NoteBlockType.MARKDOWN,
                content = RichText.plain(result), sectionKey = _blocks.value[i].sectionKey)
            update(BlockEditing.insertAfter(_blocks.value, i, listOf(added)).blocks)
            _message.value = "${tool?.label ?: "Result"} added below"
        }
        _pasteTools.value = null
    }

    /** Replaces a Markdown block's text, keeping the old text in its history for Step back. */
    private fun replaceMarkdown(index: Int, text: String, label: String) {
        val block = _blocks.value[index]
        val history = historyOf(block) + (label to block.content.text)
        val payload = block.payload + (NoteBlock.PAYLOAD_HISTORY to encodeHistory(history.takeLast(8)))
        snapshotBefore(com.example.core.notes.VersionReason.BEFORE_AI, label)
        update(_blocks.value.replace(index, block.copy(content = RichText.plain(text), payload = payload)))
    }

    fun stepBack(blockId: String) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = _blocks.value[i]
        val history = historyOf(block)
        val (label, text) = history.lastOrNull() ?: return
        update(_blocks.value.replace(i, block.copy(content = RichText.plain(text), payload = block.payload + (NoteBlock.PAYLOAD_HISTORY to encodeHistory(history.dropLast(1))))))
        _message.value = "Back to before \"$label\""
        _pasteTools.value = _pasteTools.value?.copy(result = null, tool = null, error = null)
    }

    fun restoreOriginal(blockId: String) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val raw = _blocks.value[i].payload[NoteBlock.PAYLOAD_RAW] ?: return
        replaceMarkdown(i, raw, "Original")
        _message.value = "Showing the paste as it arrived · Step back to undo"
        _pasteTools.value = null
    }

    internal fun historyOf(block: NoteBlock): List<Pair<String, String>> = runCatching {
        val a = org.json.JSONArray(block.payload[NoteBlock.PAYLOAD_HISTORY] ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("tool") to o.getString("text") } }
    }.getOrDefault(emptyList())

    private fun encodeHistory(h: List<Pair<String, String>>) =
        org.json.JSONArray(h.map { (tool, text) -> org.json.JSONObject().put("tool", tool).put("text", text) }).toString()

    /** A new, empty Markdown block, opened for writing. */
    fun insertMarkdown() {
        val block = NoteBlock(id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = NoteBlockType.MARKDOWN)
        insert(listOf(block))
        _editingMarkdown.value = block.id
    }

    /** The Markdown block open as source, if any. */
    private val _editingMarkdown = MutableStateFlow<String?>(null)
    val editingMarkdown: StateFlow<String?> = _editingMarkdown.asStateFlow()
    fun editMarkdown(blockId: String?) {
        val closing = _editingMarkdown.value
        _editingMarkdown.value = blockId
        // A Markdown block closed with nothing in it goes away.
        if (closing != null && closing != blockId) {
            val i = _blocks.value.indexOfFirst { it.id == closing }
            if (i >= 0 && _blocks.value[i].type == NoteBlockType.MARKDOWN && _blocks.value[i].content.text.isBlank()) {
                update(BlockEditing.ensureTrailingParagraph(BlockEditing.delete(_blocks.value, i), noteId))
            }
        }
    }

    /** Opens a Markdown block out into ordinary blocks, one per heading, paragraph and list item. */
    fun convertToBlocks(blockId: String) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = _blocks.value[i]
        if (block.type != NoteBlockType.MARKDOWN) return
        val parsed = com.example.core.notes.MarkdownImport.parse(block.content.text, noteId).map { it.copy(sectionKey = block.sectionKey) }
        snapshotBefore(com.example.core.notes.VersionReason.BEFORE_PASTE, "Before splitting into blocks")
        update(BlockEditing.ensureTrailingParagraph(_blocks.value.subList(0, i) + parsed + _blocks.value.subList(i + 1, _blocks.value.size), noteId))
        _message.value = "Split into ${parsed.size} blocks · Undo puts it back"
    }

    /** Every run of your own text in the note becomes one Markdown block (media and recordings stay). */
    fun combineTextIntoMarkdown() {
        val blocks = _blocks.value
        if (blocks.isEmpty()) return
        val combined = BlockEditing.ensureTrailingParagraph(BlockEditing.combineIntoMarkdown(blocks, 0, blocks.lastIndex), noteId)
        if (combined.size >= blocks.size) { _message.value = "Nothing to combine"; return }
        snapshotBefore(com.example.core.notes.VersionReason.MANUAL, "Before combining")
        update(combined)
        _message.value = "${blocks.size} blocks → ${combined.size} · Undo puts them back"
    }

    /** Folds a heading's section away, or a long block down to its first lines; again to unfold. */
    fun toggleFold(blockId: String, key: String = NoteBlock.PAYLOAD_FOLDED) {
        val i = _blocks.value.indexOfFirst { it.id == blockId }.takeIf { it >= 0 } ?: return
        val block = _blocks.value[i]
        val on = block.payload[key] == "1"
        val payload = if (on) block.payload - key else block.payload + (key to "1")
        update(_blocks.value.replace(i, block.copy(payload = payload)), recordUndo = false)
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

    /** A recording's transcript, for reading it in place inside the note. */
    suspend fun transcriptOf(meetingId: String): List<com.example.core.model.TranscriptSegment> =
        com.example.core.repository.TranscriptRepository(database).getTranscriptDirect(meetingId).segments

    /** The note's cover picture: shown on its timeline card and at the top of the note. */
    fun setCover(uri: Uri) {
        viewModelScope.launch {
            val attachment = media.import(noteId, uri) ?: run { _message.value = "Couldn't use that picture"; return@launch }
            val old = _note.value?.metadata?.get(NoteRepository.COVER_KEY)
            setMetadata(mapOf(NoteRepository.COVER_KEY to attachment.id)).join()
            old?.let { dropUnusedAttachment(it) }
        }
    }

    fun removeCover() {
        viewModelScope.launch {
            val old = _note.value?.metadata?.get(NoteRepository.COVER_KEY) ?: return@launch
            setMetadata(mapOf(NoteRepository.COVER_KEY to "")).join()
            dropUnusedAttachment(old)
        }
    }

    private suspend fun dropUnusedAttachment(id: String) {
        if (_blocks.value.none { it.payload[NoteBlock.PAYLOAD_ATTACHMENT_ID] == id }) notes.deleteAttachment(id)
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
    fun insertScripture(reference: com.example.core.scripture.ScriptureReference, versionId: Int? = null) =
        insertScriptures(listOf(reference), versionId = versionId)

    /**
     * Adds passages in the order given, as one block each. [userText] is text the person typed
     * for a single passage; it is shown instead of the fetched text, labelled as theirs.
     */
    fun insertScriptures(
        references: List<com.example.core.scripture.ScriptureReference>,
        userText: String? = null,
        userLabel: String? = null,
        versionId: Int? = null
    ) {
        if (references.isEmpty()) return
        val made = references.map { reference ->
            scriptureBlock(reference, userText.takeIf { references.size == 1 }, userLabel, versionId)
        }
        insert(made.map { it.first })
        viewModelScope.launch { notes.addScriptureRefs(made.map { it.second }) }
    }

    // ------------------------------------------------------------ assistant

    val assistant by lazy { com.example.feature.assistant.AssistantSession(getApplication(), viewModelScope, noteId, assistantBridge) }

    /** The assistant's hands in this note: every change goes through [update], so it undoes like typing. */
    val assistantBridge = object : com.example.feature.assistant.EditorBridge {
        override val noteId: String get() = this@NoteEditorViewModel.noteId
        override fun blocks(): List<NoteBlock> = _blocks.value

        override fun insert(afterId: String?, blocks: List<NoteBlock>): List<String> {
            val list = _blocks.value.toMutableList()
            val after = afterId?.let { id -> list.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            // At the end, but before a trailing empty line the person left for typing.
            val at = if (afterId == com.example.feature.assistant.AppAssistantHost.FIRST) 0 else after?.plus(1) ?: list.indexOfLast { !(it.type == NoteBlockType.PARAGRAPH && it.content.isEmpty) }.plus(1)
            list.addAll(at.coerceIn(0, list.size), blocks.map { it.copy(noteId = noteId) })
            update(list)
            return blocks.map { it.id }
        }

        override fun remove(ids: Set<String>): List<Pair<Int, NoteBlock>> {
            val removed = _blocks.value.withIndex().filter { it.value.id in ids }.map { it.index to it.value }
            if (removed.isNotEmpty()) update(_blocks.value.filterNot { it.id in ids })
            return removed
        }

        override fun restore(removed: List<Pair<Int, NoteBlock>>, dropIds: Set<String>) {
            val list = _blocks.value.filterNot { it.id in dropIds }.toMutableList()
            removed.sortedBy { it.first }.forEach { (i, b) -> list.add(i.coerceIn(0, list.size), b) }
            update(list)
        }

        override fun scripture(reference: com.example.core.scripture.ScriptureReference): NoteBlock {
            val (block, ref) = scriptureBlock(reference, null, null, null)
            viewModelScope.launch { notes.addScriptureRefs(listOf(ref)) }
            return block
        }
    }

    private fun scriptureBlock(
        reference: com.example.core.scripture.ScriptureReference, userText: String?, userLabel: String?, versionId: Int?
    ): Pair<NoteBlock, com.example.core.model.ScriptureRef> {
        val blockId = NoteRepository.newId("block")
        val refId = NoteRepository.newId("scripture")
        val payload = buildMap {
            put(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID, refId)
            put("reference", reference.display())
            if (userText != null) {
                put(NoteBlock.PAYLOAD_USER_TEXT, userText)
                userLabel?.let { put(NoteBlock.PAYLOAD_USER_LABEL, it) }
            }
        }
        val block = NoteBlock(
            id = blockId, noteId = noteId, position = 0, type = NoteBlockType.SCRIPTURE,
            content = RichText.plain(reference.display()), payload = payload, source = BlockSource.SCRIPTURE
        )
        return block to com.example.core.model.ScriptureRef(
            refId, noteId, blockId, reference.usfm, reference.chapter, reference.verseStart, reference.verseEnd,
            versionId, com.example.core.model.ScriptureOrigin.USER, createdAt = System.currentTimeMillis()
        )
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
        notes.moveToTrash(noteId)
        onDone(0)
    }

    // ------------------------------------------------------------ export

    enum class CopyKind(val label: String, val hint: String) {
        MARKDOWN("Markdown", "For ChatGPT, Claude, Notion, Obsidian"),
        RICH("Formatted text", "For Google Docs, Gmail, Word"),
        WHATSAPP("WhatsApp", "Bold and lists as WhatsApp shows them"),
        PLAIN("Plain text", "Just the words")
    }

    /** The note as text for the clipboard: the text, and HTML alongside it for [CopyKind.RICH]. Private sections stay out. */
    fun copyText(kind: CopyKind): Pair<String, String?> {
        val workflow = _note.value?.workflow ?: com.example.core.model.RecordingType.GENERAL
        val hidden = com.example.core.model.Workflows.template(workflow).privateKeys
        val blocks = _blocks.value.filter { it.sectionKey == null || it.sectionKey !in hidden }
        val title = _note.value?.title
        val T = com.example.core.notes.NoteText
        return when (kind) {
            CopyKind.MARKDOWN -> T.markdown(blocks, title) to null
            CopyKind.RICH -> T.plain(blocks, title) to T.html(blocks, title)
            CopyKind.WHATSAPP -> T.whatsApp(blocks, title) to null
            CopyKind.PLAIN -> T.plain(blocks, title) to null
        }
    }

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
        runCatching { sessionSnapshot() }
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
        // The current note, not the one read before the database calls: its title may have moved on.
        _note.value = _note.value?.let { it.copy(metadata = it.metadata - com.example.core.repository.NoteContent.DRAFT - com.example.core.repository.NoteContent.DRAFT_TITLE) }
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
        snapshotBefore(com.example.core.notes.VersionReason.BEFORE_AI, "Summary")
        update(com.example.ai.notes.NoteAiApply.withSummary(_blocks.value, noteId, points))
        dismissAi(jobId); _message.value = "Summary added — Undo to remove it"
    }

    fun applyActions(jobId: String, actions: List<com.example.ai.notes.CitedItem>) {
        snapshotBefore(com.example.core.notes.VersionReason.BEFORE_AI, "Action items")
        update(com.example.ai.notes.NoteAiApply.withActions(_blocks.value, noteId, actions))
        dismissAi(jobId); _message.value = "${actions.size} action ${if (actions.size == 1) "item" else "items"} added"
    }

    fun applyOrganized(jobId: String, result: com.example.ai.notes.NoteAiOutcome.Sections) {
        snapshotBefore(com.example.core.notes.VersionReason.BEFORE_AI, "Organise")
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
                // The session's last state joins the history (skipped if nothing changed).
                if (sessionStarted) runCatching { versions.snapshot(noteId, com.example.core.notes.VersionReason.EDIT_SESSION, blocks = blocks) }
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
        private const val AI_EDIT_SYSTEM =
            "You edit a passage from the user's own notes as instructed. Reply with only the edited " +
                "passage, in Markdown where formatting helps (**bold**, '- ' bullets, '1.' numbers). " +
                "No preamble, no explanation, no quotation marks around it. Keep the user's language."
        const val SESSION_SNAPSHOT_MS = 10 * 60 * 1000L
        private val closingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
