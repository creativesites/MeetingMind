package com.example.feature.notes.editor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.core.audio.PlaybackController
import com.example.core.common.Formatters
import com.example.core.export.ExportFormat
import com.example.core.model.Attachment
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.InlineStyle
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onNavigateBack: () -> Unit,
    onOpenRecording: (meetingId: String, startAtMs: Long?) -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onRecordHere: (noteId: String) -> Unit,
    /** Opens straight into the photo picker, for "New → Photo or video". */
    startWithMediaPicker: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }

    val note by viewModel.note.collectAsState()
    val blocks by viewModel.blocks.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val gone by viewModel.gone.collectAsState()
    val focus by viewModel.focus.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val pendingOn by viewModel.pendingOn.collectAsState()
    val pendingOff by viewModel.pendingOff.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val backlinks by viewModel.backlinks.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val linkedTitles by viewModel.linkedTitles.collectAsState()
    val message by viewModel.message.collectAsState()

    var showInsert by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showNotebooks by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showExcerpts by remember { mutableStateOf(false) }
    var showNoteLinks by remember { mutableStateOf(false) }
    var showScriptureEntry by remember { mutableStateOf(false) }
    var verseSheetFor by remember { mutableStateOf<NoteBlock?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var blockMenuFor by remember { mutableStateOf<NoteBlock?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<ExportFormat, Boolean>?>(null) }
    var pendingCapture by remember { mutableStateOf<Pair<File, Boolean>?>(null) }

    LaunchedEffect(gone) { if (gone) onNavigateBack() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    val back = {
        scope.launch { viewModel.flush() }
        onNavigateBack()
    }
    BackHandler { back() }

    // ---- launchers
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris -> viewModel.insertMedia(uris) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> viewModel.insertMedia(uris) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingCapture?.let { (file, video) -> if (ok) viewModel.adoptCapture(file, video) else file.delete() }
        pendingCapture = null
    }
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        pendingCapture?.let { (file, video) -> if (ok) viewModel.adoptCapture(file, video) else file.delete() }
        pendingCapture = null
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val request = pendingExport
        pendingExport = null
        if (uri == null || request == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { viewModel.exportTo(request.first, request.second, it) } ?: false
            }.getOrDefault(false)
            exporting = false
            showExport = false
            snackbar.showSnackbar(if (ok) "Saved ${request.first.displayName}" else "Export failed")
        }
    }

    LaunchedEffect(loaded) {
        if (loaded && startWithMediaPicker) pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    fun openAttachment(a: Attachment) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(viewModel.shareableUri(File(a.path)), a.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }

    fun onInsert(action: InsertAction) {
        showInsert = false
        when (action) {
            InsertAction.TEXT -> viewModel.insertText(NoteBlockType.PARAGRAPH)
            InsertAction.HEADING -> viewModel.insertText(NoteBlockType.HEADING_2)
            InsertAction.BULLETS -> viewModel.insertText(NoteBlockType.BULLET)
            InsertAction.NUMBERS -> viewModel.insertText(NoteBlockType.NUMBERED)
            InsertAction.CHECKLIST -> viewModel.insertText(NoteBlockType.CHECKLIST)
            InsertAction.QUOTE -> viewModel.insertText(NoteBlockType.QUOTE)
            InsertAction.DIVIDER -> viewModel.insertDivider()
            InsertAction.PHOTO -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            InsertAction.CAMERA -> viewModel.captureTarget(video = false).let { (file, uri) -> pendingCapture = file to false; takePicture.launch(uri) }
            InsertAction.VIDEO -> viewModel.captureTarget(video = true).let { (file, uri) -> pendingCapture = file to true; captureVideo.launch(uri) }
            InsertAction.AUDIO -> pickAudio.launch("audio/*")
            InsertAction.RECORD -> scope.launch { viewModel.flush(); onRecordHere(viewModel.noteId) }
            InsertAction.EXCERPT -> showExcerpts = true
            InsertAction.NOTE_LINK -> showNoteLinks = true
            InsertAction.SCRIPTURE -> showScriptureEntry = true
        }
    }

    val listState = rememberLazyListState()
    val drag = remember { BlockDragState() }
    val titleFocus = remember { FocusRequester() }
    var titleFocused by remember { mutableStateOf(false) }
    // A brand-new, empty note opens with the caret in the title.
    LaunchedEffect(loaded) {
        if (loaded && note?.title.isNullOrBlank() && blocks.all { it.type == NoteBlockType.PARAGRAPH && it.content.isEmpty } && !startWithMediaPicker) {
            runCatching { titleFocus.requestFocus() }
        }
    }

    val serif = note?.workflow?.let { com.example.core.model.Workflows.usesSerif(it) } == true
    val words = remember(blocks) { BlockEditing.wordCount(blocks) }
    val textFocused = selection != null
    val activeStyles = remember(selection, pendingOn, pendingOff, blocks) {
        InlineStyle.entries.filter { viewModel.isActive(it) }.toSet()
    }

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                Text(
                    if (saved) "Saved" else "Saving…",
                    fontSize = 12.sp, color = InkMuted, modifier = Modifier.weight(1f)
                )
                if (textFocused || titleFocused) {
                    IconButton(onClick = viewModel::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) Ink else InkFaint) }
                    IconButton(onClick = viewModel::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) Ink else InkFaint) }
                } else {
                    IconButton(onClick = { viewModel.setPinned(!(note?.pinned ?: false)) }) {
                        Icon(if (note?.pinned == true) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = if (note?.pinned == true) "Unpin" else "Pin", tint = if (note?.pinned == true) Accent else Ink)
                    }
                    IconButton(onClick = { showExport = true }) { Icon(Icons.Filled.IosShare, contentDescription = "Export and share", tint = Ink) }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Ink) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("Export & share") }, leadingIcon = { Icon(Icons.Filled.IosShare, null) }, onClick = { showMenu = false; showExport = true })
                        DropdownMenuItem(text = { Text("Move to notebook") }, leadingIcon = { Icon(Icons.Filled.Folder, null) }, onClick = { showMenu = false; showNotebooks = true })
                        DropdownMenuItem(text = { Text("Tags") }, leadingIcon = { Icon(Icons.Outlined.Tag, null) }, onClick = { showMenu = false; showTags = true })
                        DropdownMenuItem(
                            text = { Text(if (note?.isPrivate == true) "Make not private" else "Make private") },
                            leadingIcon = { Icon(if (note?.isPrivate == true) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                            onClick = { showMenu = false; viewModel.setPrivate(note?.isPrivate != true) }
                        )
                        HorizontalDivider(color = Line)
                        DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Filled.Archive, null) }, onClick = { showMenu = false; viewModel.archive(onNavigateBack) })
                        DropdownMenuItem(text = { Text("Delete", color = Color(0xFFDC2626)) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFDC2626)) }, onClick = { showMenu = false; confirmDelete = true })
                    }
                }
            }
        },
        bottomBar = {
            Box(Modifier.imePadding().then(if (!textFocused) Modifier.navigationBarsPadding() else Modifier)) {
                if (textFocused) {
                    FormattingToolbar(
                        isActive = { style -> style in activeStyles },
                        blockType = selection?.let { s -> blocks.firstOrNull { it.id == s.blockId }?.type },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onInsert = { showInsert = true },
                        onInline = viewModel::toggleInline,
                        onLink = { showLink = true },
                        onBlockType = viewModel::toggleBlockType,
                        onIndent = viewModel::indent,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onDone = { focusManager.clearFocus(); keyboard?.hide() }
                    )
                } else if (!titleFocused) {
                    IdleEditorBar(words = words, onInsert = { showInsert = true })
                }
            }
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val currentNote = note ?: return@Scaffold
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 20.dp, bottom = 120.dp)
        ) {
            item(key = "title") {
                Column(Modifier.padding(start = 22.dp, top = 6.dp)) {
                    Box {
                        if (currentNote.title.isEmpty()) Text("Title", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = InkFaint, letterSpacing = (-0.6).sp)
                        BasicTextField(
                            value = currentNote.title,
                            onValueChange = { viewModel.setTitle(it.replace("\n", "")) },
                            textStyle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.6).sp),
                            cursorBrush = SolidColor(Accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                blocks.firstOrNull { it.type.isText }?.let { viewModel.onSelectionChanged(it.id, 0, 0) }
                                focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).onFocusChanged { titleFocused = it.isFocused }
                        )
                    }
                }
            }
            item(key = "meta") {
                NoteMeta(
                    notebookName = notebooks.firstOrNull { it.id == currentNote.notebookId }?.name ?: "No notebook",
                    date = Formatters.formatDateRelative(currentNote.eventDate ?: currentNote.createdAt),
                    workflowLabel = currentNote.workflow.displayName.takeIf { currentNote.workflow.name != "GENERAL" },
                    isPrivate = currentNote.isPrivate,
                    tags = tags.map { it.name },
                    onNotebook = { showNotebooks = true },
                    onTags = { showTags = true },
                    details = if (currentNote.workflow == com.example.core.model.RecordingType.SERMON) {
                        listOfNotNull(currentNote.metadata["speaker"], currentNote.metadata["church"]).ifEmpty { listOf("Add speaker & church") }
                    } else emptyList(),
                    onDetails = { showDetails = true }
                )
            }
            itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->
                val dragging = drag.draggingId == block.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) drag.offset else 0f
                            shadowElevation = if (dragging) 12f else 0f
                        }
                        .background(if (dragging) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                    verticalAlignment = Alignment.Top
                ) {
                    BlockHandle(
                        Modifier
                            .padding(top = if (block.type.isText) blockTopPadding(block.type) + if (block.type.name.startsWith("HEADING")) 4.dp else 1.dp else 10.dp)
                            .clickable { blockMenuFor = block }
                            .pointerInput(block.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { viewModel.beginMove(); drag.start(block.id) },
                                    onDragEnd = { drag.stop() },
                                    onDragCancel = { drag.stop() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag.dragBy(amount.y, listState, blocks) { from, to -> viewModel.move(from, to) }
                                    }
                                )
                            }
                    )
                    Box(Modifier.weight(1f)) {
                        BlockContent(
                            block = block,
                            index = index,
                            blocks = blocks,
                            serif = serif,
                            focusRequest = focus,
                            attachments = attachments,
                            recordings = recordings,
                            linkedTitle = block.payload[NoteBlock.PAYLOAD_NOTE_ID]?.let { linkedTitles[it] ?: block.payload["title"] },
                            viewModel = viewModel,
                            isOnlyBlock = blocks.size == 1,
                            onOpenAttachment = ::openAttachment,
                            onOpenRecording = onOpenRecording,
                            onOpenNote = onOpenNote,
                            onPlay = { card -> card.audioPath?.let { PlaybackController.play(context, card.meetingId, card.title, File(it)) } },
                            onOpenScripture = { verseSheetFor = it }
                        )
                    }
                }
            }
            if (backlinks.isNotEmpty()) {
                item(key = "backlinks") {
                    Column(Modifier.padding(start = 22.dp, top = 32.dp)) {
                        Text("LINKED FROM", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, letterSpacing = 0.8.sp)
                        backlinks.forEach { n ->
                            Text(
                                n.title.ifBlank { "Untitled note" }, fontSize = 15.sp, color = Accent, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp).clickable { onOpenNote(n.id) }
                            )
                        }
                    }
                }
            }
            item(key = "tail") {
                // Generous space to tap into and continue writing at the end.
                Box(
                    Modifier.fillMaxWidth().height(160.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        val last = blocks.lastOrNull()
                        if (last != null && last.type.isText) viewModel.onSelectionChanged(last.id, last.content.text.length, last.content.text.length)
                        if (last == null || !last.type.isText || last.content.text.isNotEmpty()) viewModel.insertText(NoteBlockType.PARAGRAPH)
                        else viewModel.focusBlock(last.id, 0)
                    }
                )
            }
        }
    }

    // ---- sheets and dialogs
    if (showInsert) InsertSheet(hasRecordings = recordings.isNotEmpty(), onPick = ::onInsert, onDismiss = { showInsert = false })
    if (showLink) LinkDialog(initial = viewModel.currentLink(), onSave = { viewModel.setLink(it); showLink = false }, onDismiss = { showLink = false })
    if (showTags) TagSheet(tags, allTags, onAdd = { viewModel.addTag(it) }, onRemove = { viewModel.removeTag(it.id) }, onDismiss = { showTags = false })
    if (showNotebooks) NotebookSheet(
        notebooks = notebooks,
        currentId = note?.notebookId,
        onPick = { viewModel.moveToNotebook(it.id); showNotebooks = false },
        onCreate = { name -> viewModel.createNotebookAndMove(name); showNotebooks = false },
        onDismiss = { showNotebooks = false }
    )
    if (showExport) ExportSheet(
        isPrivate = note?.isPrivate == true,
        busy = exporting,
        onSave = { format, includePrivate ->
            pendingExport = format to includePrivate
            createDocument.launch("${viewModel.fileSafeTitle()}.${format.extension}")
        },
        onShare = { format, includePrivate ->
            scope.launch {
                exporting = true
                val file = viewModel.exportForSharing(format, includePrivate)
                exporting = false
                if (file == null) { snackbar.showSnackbar("Export failed"); return@launch }
                showExport = false
                val send = Intent(Intent.ACTION_SEND)
                    .setType(format.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, viewModel.shareableUri(file))
                    .putExtra(Intent.EXTRA_SUBJECT, note?.title?.ifBlank { null } ?: "Note")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share note"))
            }
        },
        onDismiss = { showExport = false }
    )
    if (showExcerpts) ExcerptPickerSheet(load = { viewModel.excerptCandidates() }, onPick = { viewModel.insertExcerpt(it); showExcerpts = false }, onDismiss = { showExcerpts = false })
    if (showDetails) note?.let { n ->
        SermonDetailsDialog(
            speaker = n.metadata["speaker"].orEmpty(),
            church = n.metadata["church"].orEmpty(),
            onSave = { speaker, church -> viewModel.setMetadata(mapOf("speaker" to speaker.trim(), "church" to church.trim())); showDetails = false },
            onDismiss = { showDetails = false }
        )
    }
    if (showScriptureEntry) ScriptureEntryDialog(onInsert = { viewModel.insertScripture(it); showScriptureEntry = false }, onDismiss = { showScriptureEntry = false })
    verseSheetFor?.let { b ->
        val ref = b.payload["reference"]?.let { com.example.core.scripture.ScriptureReferenceParser.parse(it) }
        if (ref == null) verseSheetFor = null else {
            val meetingId = b.payload[NoteBlock.PAYLOAD_MEETING_ID]
            val at = b.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()
            com.example.feature.scripture.VerseSheet(
                reference = ref,
                heardAtMs = at,
                onPlay = if (meetingId != null && at != null) ({ onOpenRecording(meetingId, at) }) else null,
                onDismiss = { verseSheetFor = null }
            )
        }
    }
    if (showNoteLinks) NoteLinkPickerSheet(search = { viewModel.linkableNotes(it) }, onPick = { viewModel.insertNoteLink(it); showNoteLinks = false }, onDismiss = { showNoteLinks = false })
    blockMenuFor?.let { b ->
        BlockMenu(
            type = b.type,
            onTurnInto = { viewModel.turnInto(b.id, it); blockMenuFor = null },
            onMoveUp = { viewModel.moveBy(b.id, -1); blockMenuFor = null },
            onMoveDown = { viewModel.moveBy(b.id, 1); blockMenuFor = null },
            onDuplicate = { viewModel.duplicateBlock(b.id); blockMenuFor = null },
            onDelete = { viewModel.deleteBlock(b.id); blockMenuFor = null },
            onDismiss = { blockMenuFor = null }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            title = { Text("Delete this note?") },
            text = {
                Text(
                    if (recordings.isNotEmpty()) "The note's text, photos and tags are deleted. Its recordings are kept and stay in your library."
                    else "The note's text, photos and tags are deleted. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete { onNavigateBack() }
                }) { Text("Delete", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteMeta(
    notebookName: String,
    date: String,
    workflowLabel: String?,
    isPrivate: Boolean,
    tags: List<String>,
    onNotebook: () -> Unit,
    onTags: () -> Unit,
    details: List<String> = emptyList(),
    onDetails: () -> Unit = {}
) {
    // Chips here are compact; the whole row is the touch area people aim for.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
    FlowRow(
        Modifier.padding(start = 22.dp, top = 10.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MetaChip(notebookName, Icons.Filled.Folder, onNotebook)
        MetaText(date)
        workflowLabel?.let { MetaText(it) }
        details.forEach { MetaChip(it, Icons.Outlined.PersonOutline, onDetails) }
        if (isPrivate) MetaChip("Private", Icons.Filled.Lock, null)
        tags.forEach { MetaChip("#$it", null, onTags, accent = true) }
        MetaChip(if (tags.isEmpty()) "Add tag" else "+", Icons.Outlined.Tag.takeIf { tags.isEmpty() }, onTags)
    }
    }
}

@Composable
private fun MetaChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: (() -> Unit)?, accent: Boolean = false) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(50),
        color = if (accent) com.example.ui.theme.AccentWash else com.example.ui.theme.SurfaceSunk
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, tint = if (accent) Accent else InkSecondary, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
            Text(label, fontSize = 12.sp, color = if (accent) Accent else InkSecondary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(text, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
}

@Composable
private fun BlockContent(
    block: NoteBlock,
    index: Int,
    blocks: List<NoteBlock>,
    serif: Boolean,
    focusRequest: NoteEditorViewModel.FocusRequest?,
    attachments: Map<String, Attachment>,
    recordings: Map<String, RecordingCard>,
    linkedTitle: String?,
    viewModel: NoteEditorViewModel,
    isOnlyBlock: Boolean,
    onOpenAttachment: (Attachment) -> Unit,
    onOpenRecording: (String, Long?) -> Unit,
    onOpenNote: (String) -> Unit,
    onPlay: (RecordingCard) -> Unit,
    onOpenScripture: (NoteBlock) -> Unit
) {
    val attachment = block.payload[NoteBlock.PAYLOAD_ATTACHMENT_ID]?.let { attachments[it] }
    when {
        block.type.isText -> TextBlock(
            block = block,
            number = if (block.type == NoteBlockType.NUMBERED) BlockEditing.numberFor(blocks, index) else 0,
            serif = serif,
            placeholder = block.payload[NoteBlock.PAYLOAD_HINT] ?: blockPlaceholderTypes[block.type] ?: if (isOnlyBlock) "Start writing…" else null,
            focusRequest = focusRequest,
            onText = { text, cursor -> viewModel.onTextChanged(block.id, text, cursor) },
            onSelection = { s, e -> viewModel.onSelectionChanged(block.id, s, e) },
            onBackspaceAtStart = { viewModel.onBackspaceAtStart(block.id) },
            onFocusLost = { viewModel.onBlockFocusLost(block.id) },
            onToggleChecked = { viewModel.toggleChecked(block.id) }
        )
        block.type == NoteBlockType.DIVIDER -> DividerBlock()
        block.type == NoteBlockType.IMAGE -> ImageBlock(block, attachment, onOpenAttachment) { viewModel.setCaption(block.id, it) }
        block.type == NoteBlockType.VIDEO -> VideoBlock(block, attachment, onOpenAttachment) { viewModel.setCaption(block.id, it) }
        block.type == NoteBlockType.AUDIO -> AudioBlock(block, attachment) { viewModel.setCaption(block.id, it) }
        block.type == NoteBlockType.RECORDING -> RecordingBlock(
            card = block.payload[NoteBlock.PAYLOAD_MEETING_ID]?.let { recordings[it] },
            onPlay = onPlay,
            onOpen = { onOpenRecording(it.meetingId, null) }
        )
        block.type == NoteBlockType.TRANSCRIPT_EXCERPT -> ExcerptBlock(block) {
            block.payload[NoteBlock.PAYLOAD_MEETING_ID]?.let { onOpenRecording(it, block.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()) }
        }
        block.type == NoteBlockType.NOTE_LINK -> NoteLinkBlock(linkedTitle ?: "Linked note") {
            block.payload[NoteBlock.PAYLOAD_NOTE_ID]?.let(onOpenNote)
        }
        block.type == NoteBlockType.SCRIPTURE -> {
            val ref = remember(block.payload["reference"]) { block.payload["reference"]?.let { com.example.core.scripture.ScriptureReferenceParser.parse(it) } }
            if (ref == null) ScriptureBlock(block) else {
                val meetingId = block.payload[NoteBlock.PAYLOAD_MEETING_ID]
                val at = block.payload[NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()
                com.example.feature.scripture.ScriptureCard(
                    reference = ref,
                    heardAtMs = at,
                    onOpen = { onOpenScripture(block) },
                    onPlay = if (meetingId != null && at != null) ({ onOpenRecording(meetingId, at) }) else null,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Drag-to-reorder for the block list. The dragged block follows the finger; when its centre
 * passes the middle of a neighbour the two swap, and the offset is corrected so the block stays
 * under the finger.
 */
private class BlockDragState {
    var draggingId by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)

    fun start(id: String) { draggingId = id; offset = 0f }
    fun stop() { draggingId = null; offset = 0f }

    fun dragBy(dy: Float, list: LazyListState, blocks: List<NoteBlock>, move: (Int, Int) -> Unit) {
        val id = draggingId ?: return
        offset += dy
        val items = list.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == id } ?: return
        val centre = current.offset + offset + current.size / 2f
        val target = items.firstOrNull { item ->
            item.key != id && item.key is String && blocks.any { it.id == item.key } &&
                centre > item.offset && centre < item.offset + item.size
        } ?: return
        val from = blocks.indexOfFirst { it.id == id }
        val to = blocks.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        move(from, to)
        offset += (current.offset - target.offset).toFloat()
    }
}
