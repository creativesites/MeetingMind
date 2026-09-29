package com.example.feature.notes.editor

import com.example.ui.theme.Danger
import com.example.ui.theme.forTheme
import com.example.ui.theme.OnInk
import com.example.ui.theme.SurfaceBase
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.SurfaceSunk
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ViewAgenda
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
    startWithMediaPicker: Boolean = false,
    onShareCard: (com.example.feature.share.ShareRequest) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }

    val note by viewModel.note.collectAsState()

    // Private Faith notes (prayers, reflections) sit behind the optional Faith lock.
    var faithUnlocked by remember { mutableStateOf(com.example.feature.faith.FaithLock.isUnlocked()) }
    val lockNeeded = note?.let { it.isPrivate && com.example.core.model.Workflows.space(it.workflow) == com.example.core.model.NotebookSpace.FAITH } == true
    if (lockNeeded && !faithUnlocked) {
        com.example.feature.faith.FaithLockGate(onCancel = onNavigateBack) { LaunchedEffect(Unit) { faithUnlocked = true } }
        return
    }
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
    val aiEdit by viewModel.aiEdit.collectAsState()

    var showInsert by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showNotebooks by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showCopyAs by remember { mutableStateOf(false) }
    var showExcerpts by remember { mutableStateOf(false) }
    var showNoteLinks by remember { mutableStateOf(false) }
    var showScriptureEntry by remember { mutableStateOf(false) }
    var showPrayerUpdate by remember { mutableStateOf(false) }
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setCover(uri) }
    val aiJobs by viewModel.aiJobs.collectAsState()
    var showAiMenu by remember { mutableStateOf(false) }
    var showAsk by remember { mutableStateOf(false) }
    var aiSheetOpen by remember { mutableStateOf(false) }
    var related by remember { mutableStateOf<List<Pair<com.example.core.model.Note, com.example.ai.notes.RelatedNote>>?>(null) }
    var showRelated by remember { mutableStateOf(false) }
    var bibleDialog by remember { mutableStateOf<Boolean?>(null) } // null = closed; true = open in search
    var verseSheetFor by remember { mutableStateOf<NoteBlock?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
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
            InsertAction.MARKDOWN -> viewModel.insertMarkdown()
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
    // Bring a block into view when something asks for it (a citation, a new block) and it's off screen.
    LaunchedEffect(focus?.serial) {
        val target = focus?.target?.blockId ?: return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.any { it.key == target }) return@LaunchedEffect
        val index = blocks.indexOfFirst { it.id == target }.takeIf { it >= 0 } ?: return@LaunchedEffect
        val leading = 2 + (if (aiJobs.isNotEmpty() && !aiSheetOpen) 1 else 0) +
            (if (note?.workflow == com.example.core.model.RecordingType.PRAYER_REQUEST) 1 else 0)
        runCatching { listState.animateScrollToItem(leading + index) }
    }
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
        containerColor = SurfaceBase,
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
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = SurfaceBase) {
                        DropdownMenuItem(text = { Text("Cover image") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = {
                            showMenu = false
                            coverPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text("AI tools") }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, tint = Accent) }, onClick = { showMenu = false; showAiMenu = true })
                        DropdownMenuItem(text = { Text("Share as a picture") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = {
                            showMenu = false
                            note?.let { n ->
                                val body = n.plainText.lineSequence().map { it.trim() }
                                    .filter { it.isNotEmpty() && it != n.title.trim() && it !in com.example.core.model.Workflows.template(n.workflow).sections.map { s -> s.title } }
                                    .joinToString(" ").take(300).ifBlank { n.title }
                                onShareCard(com.example.feature.share.ShareRequest(
                                    com.example.core.share.ShareCardContent(n.workflow.displayName, body, n.title.takeIf { it.isNotBlank() && it != body }, null, quoted = false),
                                    theme = n.title.ifBlank { body.take(120) }
                                ))
                            }
                        })
                        DropdownMenuItem(text = { Text("Tidy into fewer blocks") }, leadingIcon = { Icon(Icons.Filled.ViewAgenda, null) }, onClick = { showMenu = false; viewModel.combineTextIntoMarkdown() })
                        DropdownMenuItem(text = { Text("Copy as…") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { showMenu = false; showCopyAs = true })
                        DropdownMenuItem(text = { Text("Export & share") }, leadingIcon = { Icon(Icons.Filled.IosShare, null) }, onClick = { showMenu = false; showExport = true })
                        DropdownMenuItem(text = { Text("Move to notebook") }, leadingIcon = { Icon(Icons.Filled.Folder, null) }, onClick = { showMenu = false; showNotebooks = true })
                        DropdownMenuItem(text = { Text("Tags") }, leadingIcon = { Icon(Icons.Outlined.Tag, null) }, onClick = { showMenu = false; showTags = true })
                        DropdownMenuItem(
                            text = { Text(if (note?.isPrivate == true) "Make not private" else "Make private") },
                            leadingIcon = { Icon(if (note?.isPrivate == true) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                            onClick = { showMenu = false; viewModel.setPrivate(note?.isPrivate != true) }
                        )
                        DropdownMenuItem(text = { Text("Version history") }, leadingIcon = { Icon(Icons.Filled.History, null) }, onClick = { showMenu = false; showVersions = true })
                        HorizontalDivider(color = Line)
                        DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Filled.Archive, null) }, onClick = { showMenu = false; viewModel.archive(onNavigateBack) })
                        DropdownMenuItem(text = { Text("Delete", color = Danger) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Danger) }, onClick = { showMenu = false; confirmDelete = true })
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
                        onAiEdit = { viewModel.startAiEdit() },
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
        // Folded headings hide their sections; positions still refer to the whole note.
        val visible = remember(blocks) { BlockEditing.visibleBlocks(blocks) }
        val indexOf = remember(blocks) { blocks.withIndex().associate { (i, b) -> b.id to i } }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 20.dp, bottom = 120.dp)
        ) {
            val coverPath = currentNote.metadata[com.example.core.repository.NoteRepository.COVER_KEY]?.let { attachments[it]?.path }
            if (coverPath != null && java.io.File(coverPath).exists()) item(key = "cover") {
                Box(Modifier.padding(start = 8.dp, bottom = 12.dp).fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp))) {
                    coil.compose.AsyncImage(
                        model = java.io.File(coverPath), contentDescription = "Cover",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                    Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(onClick = { coverPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
                            Text("Change cover", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Surface(onClick = { viewModel.removeCover() }, shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
                            Text("Remove", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            item(key = "title") {
                Column(Modifier.padding(start = 22.dp, top = 6.dp)) {
                    // The field owns what's being typed. The keyboard's word-in-progress is never
                    // reset by the saved (trimmed) title coming back from the database; an outside
                    // change (a restored version, a recording's name) shows when not typing.
                    var titleValue by remember(currentNote.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(currentNote.title, androidx.compose.ui.text.TextRange(currentNote.title.length))) }
                    if (!titleFocused && titleValue.text.trim() != currentNote.title.trim()) {
                        titleValue = androidx.compose.ui.text.input.TextFieldValue(currentNote.title, androidx.compose.ui.text.TextRange(currentNote.title.length))
                    }
                    Box {
                        if (titleValue.text.isEmpty()) Text("Title", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = InkFaint, letterSpacing = (-0.6).sp)
                        BasicTextField(
                            value = titleValue,
                            onValueChange = { next ->
                                val clean = if ('\n' in next.text) next.copy(text = next.text.replace("\n", "")) else next
                                val changed = clean.text != titleValue.text
                                titleValue = clean
                                if (changed) viewModel.setTitle(clean.text)
                            },
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
                    } else listOfNotNull(
                        // A note made from a calendar event: who was invited, and where.
                        currentNote.metadata["participants"]?.let { p -> "With " + p.split(", ").let { if (it.size <= 3) it.joinToString(", ") else it.take(2).joinToString(", ") + " +${it.size - 2}" } },
                        currentNote.metadata["location"]
                    ),
                    onDetails = { if (currentNote.workflow == com.example.core.model.RecordingType.SERMON) showDetails = true }
                )
            }
            aiJobs.firstOrNull()?.let { job ->
                if (!aiSheetOpen) item(key = "ai-banner") {
                    val working = job.status == com.example.ai.notes.NoteAiStatus.QUEUED || job.status == com.example.ai.notes.NoteAiStatus.RUNNING
                    Surface(
                        onClick = { aiSheetOpen = true }, shape = RoundedCornerShape(14.dp), color = Accent.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp).testTag("note_ai_banner")
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
                            Text(
                                when {
                                    working -> "${job.tool.label}: working…"
                                    job.status == com.example.ai.notes.NoteAiStatus.SUCCEEDED -> "${job.tool.label}: ready to view"
                                    else -> "${job.tool.label} didn't finish"
                                },
                                fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f).padding(start = 10.dp)
                            )
                            Text("View", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                        }
                    }
                }
            }
            if (currentNote.workflow == com.example.core.model.RecordingType.PRAYER_REQUEST) {
                item(key = "prayer") {
                    PrayerRequestBanner(
                        note = currentNote,
                        testimony = backlinks.firstOrNull { it.workflow == com.example.core.model.RecordingType.TESTIMONY },
                        onAddUpdate = { showPrayerUpdate = true },
                        onMarkAnswered = { viewModel.markAnswered(onOpenNote) },
                        onReopen = { viewModel.reopenRequest() },
                        onTestimony = { existing -> if (existing != null) onOpenNote(existing) else viewModel.startTestimony(onOpenNote) }
                    )
                }
            }
            // A note that arrived as dozens of one-line blocks (older pastes) is offered one tap to fix.
            val looseText = blocks.count { it.source == com.example.core.model.BlockSource.USER && it.type.isText && it.content.text.isNotBlank() }
            if (looseText >= TIDY_SUGGEST_AT) {
                item(key = "tidy") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(14.dp)).background(com.example.ui.theme.AccentWash).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("This note is split into $looseText separate lines.", fontSize = 13.5.sp, color = Ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.combineTextIntoMarkdown() }) { Text("Make it one block", color = Accent, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            itemsIndexed(visible, key = { _, b -> b.id }) { _, block ->
                val index = indexOf[block.id] ?: 0
                val dragging = drag.draggingId == block.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) drag.offset else 0f
                            shadowElevation = if (dragging) 12f else 0f
                        }
                        .background(if (dragging) com.example.ui.theme.SurfaceRaised else Color.Transparent, RoundedCornerShape(8.dp))
                        .then(if (dragging) Modifier else Modifier.animateItem()),
                    verticalAlignment = Alignment.Top
                ) {
                    // A note reads as a page: only the line you're in (and pictures, cards) shows its handle.
                    val showHandle = dragging || selection?.blockId == block.id || !(block.type.isText || block.type == NoteBlockType.MARKDOWN)
                    BlockHandle(
                        Modifier
                            .alpha(if (showHandle) 1f else 0f)
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
    val pasteTools by viewModel.pasteTools.collectAsState()
    pasteTools?.let { state ->
        val target = blocks.firstOrNull { it.id == state.blockId }
        PasteToolsSheet(
            state = state,
            block = target,
            history = target?.let { viewModel.historyOf(it) }.orEmpty(),
            onRun = viewModel::runPasteTool,
            onTidy = { viewModel.tidyWithoutAi(state.blockId) },
            onAccept = viewModel::acceptPasteTool,
            onStepBack = { viewModel.stepBack(state.blockId) },
            onOriginal = { viewModel.restoreOriginal(state.blockId) },
            onDismiss = viewModel::closePasteTools
        )
    }
    aiEdit?.let { edit ->
        AiEditSheet(
            edit = edit,
            onRun = viewModel::runAiEdit,
            onReplace = { viewModel.applyAiEdit(replace = true) },
            onInsertBelow = { viewModel.applyAiEdit(replace = false) },
            onDismiss = viewModel::dismissAiEdit
        )
    }
    if (showCopyAs) CopyAsSheet(
        onPick = { kind ->
            val (text, html) = viewModel.copyText(kind)
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            val clip = if (html != null) android.content.ClipData.newHtmlText(note?.title ?: "Note", text, html)
                else android.content.ClipData.newPlainText(note?.title ?: "Note", text)
            clipboard?.setPrimaryClip(clip)
            showCopyAs = false
            scope.launch { snackbar.showSnackbar("Copied as ${kind.label}") }
        },
        onDismiss = { showCopyAs = false }
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
    if (showScriptureEntry) ScriptureEntryDialog(
        onInsert = { refs, own, label -> viewModel.insertScriptures(refs, own, label); showScriptureEntry = false },
        onDismiss = { showScriptureEntry = false },
        onOpenBible = { search -> showScriptureEntry = false; bibleDialog = search }
    )
    if (showAiMenu) com.example.feature.notes.ai.NoteAiMenu(
        forNotebook = false,
        onPick = { tool ->
            showAiMenu = false
            if (tool == com.example.ai.notes.NoteAiTool.ASK) showAsk = true else { viewModel.runAi(tool); aiSheetOpen = true }
        },
        onRelated = {
            showAiMenu = false; showRelated = true; related = null
            scope.launch { related = viewModel.relatedNotes() }
        },
        onDismiss = { showAiMenu = false }
    )
    if (showAsk) com.example.feature.notes.ai.AskDialog(
        forNotebook = false,
        onAsk = { q -> showAsk = false; viewModel.runAi(com.example.ai.notes.NoteAiTool.ASK, q); aiSheetOpen = true },
        onDismiss = { showAsk = false }
    )
    if (showRelated) com.example.feature.notes.ai.RelatedNotesSheet(
        related = related,
        onOpen = { showRelated = false; onOpenNote(it) },
        onDismiss = { showRelated = false }
    )
    if (aiSheetOpen) {
        val job = aiJobs.firstOrNull()
        if (job == null) {
            // The job row appears a moment after the tap; until then there's nothing to show.
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); if (viewModel.aiJobs.value.isEmpty()) aiSheetOpen = false }
        } else com.example.feature.notes.ai.NoteAiResultSheet(
            job = job,
            primaryLabel = if (job.tool == com.example.ai.notes.NoteAiTool.EXTRACT_ACTIONS) "Add to note" else "Add summary to note",
            onPrimary = { items ->
                aiSheetOpen = false
                if (job.tool == com.example.ai.notes.NoteAiTool.EXTRACT_ACTIONS) viewModel.applyActions(job.id, items) else viewModel.applySummary(job.id, items)
            },
            onApplyOrganized = { result -> aiSheetOpen = false; viewModel.applyOrganized(job.id, result) },
            onShowSource = { p ->
                aiSheetOpen = false
                when {
                    blocks.any { it.id == p.id } -> viewModel.focusBlock(p.id, 0)
                    p.noteId != null && p.noteId != viewModel.noteId -> onOpenNote(p.noteId)
                    else -> scope.launch { snackbar.showSnackbar("That's from the recording: “${p.text.take(80)}…”") }
                }
            },
            onCancel = { viewModel.cancelAi(job.id) },
            onClose = { aiSheetOpen = false },
            onDiscard = { aiSheetOpen = false; viewModel.dismissAi(job.id) }
        )
    }
    if (showPrayerUpdate) PrayerUpdateDialog(
        onSave = { viewModel.addPrayerUpdate(it); showPrayerUpdate = false },
        onDismiss = { showPrayerUpdate = false }
    )
    bibleDialog?.let { search ->
        com.example.feature.bible.BibleDialog(
            onDismiss = { bibleDialog = null },
            onInsert = { ref, versionId -> viewModel.insertScripture(ref, versionId) },
            startInSearch = search
        )
    }
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
            onDismiss = { blockMenuFor = null },
            onEditMarkdown = if (b.type == NoteBlockType.MARKDOWN) ({ viewModel.editMarkdown(b.id); blockMenuFor = null }) else null,
            onAiTools = if (b.type == NoteBlockType.MARKDOWN) ({ viewModel.openPasteTools(b.id); blockMenuFor = null }) else null,
            onSplitMarkdown = if (b.type == NoteBlockType.MARKDOWN) ({ viewModel.convertToBlocks(b.id); blockMenuFor = null }) else null
        )
    }
    if (showVersions) {
        val versionList by viewModel.versionList.collectAsState()
        val currentBlocks by viewModel.blocks.collectAsState()
        VersionHistorySheet(
            versions = versionList,
            current = currentBlocks,
            load = { viewModel.loadVersion(it) },
            onRestore = { viewModel.restoreVersion(it) },
            onSaveNow = { viewModel.saveVersion() },
            onDismiss = { showVersions = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SurfaceBase,
            title = { Text("Delete this note?") },
            text = {
                Text("It moves to the Trash, where you can restore it for ${com.example.core.repository.NoteRepository.TRASH_DAYS} days. Its recordings always stay in your library.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete { onNavigateBack() }
                }) { Text("Delete", color = Danger) }
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
    val headingLevel = BlockEditing.headingLevel(block.type)
    // A heading with something under it can fold that section away.
    val section = if (headingLevel == null) 0 else remember(blocks, index) {
        var n = 0
        for (i in index + 1 until blocks.size) {
            val level = BlockEditing.headingLevel(blocks[i].type)
            if (level != null && level <= headingLevel) break
            n++
        }
        n
    }
    when {
        headingLevel != null && section > 0 -> Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                TextBlock(
                    block = block, number = 0, serif = serif,
                    placeholder = block.payload[NoteBlock.PAYLOAD_HINT] ?: blockPlaceholderTypes[block.type],
                    focusRequest = focusRequest,
                    onText = { text, cursor -> viewModel.onTextChanged(block.id, text, cursor) },
                    onSelection = { s, e -> viewModel.onSelectionChanged(block.id, s, e) },
                    onBackspaceAtStart = { viewModel.onBackspaceAtStart(block.id) },
                    onFocusLost = { viewModel.onBlockFocusLost(block.id) },
                    onToggleChecked = {}
                )
            }
            Box(Modifier.padding(top = blockTopPadding(block.type) + 2.dp)) {
                FoldChevron(block.payload[NoteBlock.PAYLOAD_FOLDED] == "1", section) { viewModel.toggleFold(block.id) }
            }
        }
        block.type == NoteBlockType.MARKDOWN -> {
            val editingId by viewModel.editingMarkdown.collectAsState()
            Column {
                MarkdownBlock(
                    block = block,
                    serif = serif,
                    editing = editingId == block.id,
                    onEdit = { open -> viewModel.editMarkdown(if (open) block.id else null) },
                    onText = { viewModel.setCaption(block.id, it) },
                    onToggleExpanded = { viewModel.toggleFold(block.id, PAYLOAD_EXPANDED) }
                )
                if (editingId != block.id && block.content.text.isNotBlank()) {
                    MarkdownFooter(
                        block = block,
                        canStepBack = block.payload[NoteBlock.PAYLOAD_HISTORY]?.let { it.length > 2 } == true,
                        onTools = { viewModel.openPasteTools(block.id) },
                        onStepBack = { viewModel.stepBack(block.id) }
                    )
                }
            }
        }
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
            onToggleChecked = { viewModel.toggleChecked(block.id) },
            onCite = { meetingId, ms -> onOpenRecording(meetingId, ms) }
        )
        block.type == NoteBlockType.DIVIDER -> DividerBlock()
        block.type == NoteBlockType.CODE -> CodeBlock(block) { viewModel.setCaption(block.id, it) }
        block.type == NoteBlockType.TABLE -> TableBlock(block)
        block.type == NoteBlockType.EMBED -> EmbedBlock(block)
        block.type == NoteBlockType.IMAGE -> ImageBlock(block, attachment, onOpenAttachment) { viewModel.setCaption(block.id, it) }
        block.type == NoteBlockType.VIDEO -> VideoBlock(block, attachment, onOpenAttachment) { viewModel.setCaption(block.id, it) }
        block.type == NoteBlockType.AUDIO -> AudioBlock(block, attachment) { viewModel.setCaption(block.id, it) }
        block.type == NoteBlockType.RECORDING -> RecordingBlock(
            card = block.payload[NoteBlock.PAYLOAD_MEETING_ID]?.let { recordings[it] },
            onPlay = onPlay,
            onOpen = { onOpenRecording(it.meetingId, null) },
            loadTranscript = { viewModel.transcriptOf(it) },
            onPlayAt = { meetingId, ms -> onOpenRecording(meetingId, ms) }
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
                    userText = block.payload[NoteBlock.PAYLOAD_USER_TEXT],
                    userLabel = block.payload[NoteBlock.PAYLOAD_USER_LABEL],
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

/** A prayer request's lifecycle: praying since, updates, answered, testimony. */
@Composable
private fun PrayerRequestBanner(
    note: com.example.core.model.Note,
    testimony: com.example.core.model.Note?,
    onAddUpdate: () -> Unit,
    onMarkAnswered: () -> Unit,
    onReopen: () -> Unit,
    onTestimony: (existingId: String?) -> Unit
) {
    val answered = note.status == com.example.core.model.NoteStatus.ANSWERED
    val gold = Color(0xFFB7791F).forTheme()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (answered) Color(0x14B7791F).forTheme() else SurfaceSunk,
        border = BorderStroke(1.dp, if (answered) gold.copy(alpha = 0.35f) else Line),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp).testTag("prayer_banner")
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (answered) "Answered ${Formatters.formatDateRelative(note.answeredAt ?: note.updatedAt)}"
                else "Praying since ${Formatters.formatDateRelative(note.createdAt)}",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (answered) gold else Ink
            )
            Text(
                if (answered) "Remember what God did — write it down while it's fresh."
                else "Add updates as things change. When it's answered, mark it and tell the story.",
                fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp)
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (answered) {
                    Surface(onClick = { onTestimony(testimony?.id) }, shape = RoundedCornerShape(50), color = Ink) {
                        Text(if (testimony != null) "Open testimony" else "Write testimony", color = OnInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    Surface(onClick = onReopen, shape = RoundedCornerShape(50), color = SurfaceBase, border = BorderStroke(1.dp, Line)) {
                        Text("Still praying", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                } else {
                    Surface(onClick = onAddUpdate, shape = RoundedCornerShape(50), color = SurfaceBase, border = BorderStroke(1.dp, Line)) {
                        Text("Add update", color = Ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    Surface(onClick = onMarkAnswered, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.testTag("prayer_mark_answered")) {
                        Text("Mark answered", color = OnInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerUpdateDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        title = { Text("Prayer update") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, minLines = 3,
                placeholder = { Text("What has changed? What are you still asking for?") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** How many separate lines of your own text before the editor offers to make them one block. */
private const val TIDY_SUGGEST_AT = 25
