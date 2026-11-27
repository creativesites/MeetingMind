package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.Filing
import com.craftflowtechnologies.meetingmind.core.work.FilingAction
import com.craftflowtechnologies.meetingmind.core.work.FilingCandidate
import com.craftflowtechnologies.meetingmind.core.work.FilingTarget
import com.craftflowtechnologies.meetingmind.core.work.InboxKind
import com.craftflowtechnologies.meetingmind.core.work.InboxStatus
import com.craftflowtechnologies.meetingmind.core.work.SharedPart
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.today.OutfitFamily
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Background
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.DangerWash
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.Speaker2
import com.craftflowtechnologies.meetingmind.ui.theme.Speaker3
import com.craftflowtechnologies.meetingmind.ui.theme.Speaker4
import com.craftflowtechnologies.meetingmind.ui.theme.Success
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.craftflowtechnologies.meetingmind.core.documents.DocumentReading
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxCaptureTiles
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxEmptyState
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxFiledRow
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxItemCard
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxItemScreen
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxRules
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxUndo
import com.craftflowtechnologies.meetingmind.feature.work.inbox.InboxUndoKind

/**
 * Filter categories for Work Inbox triage (#8a): All · Needs you · Voice & media · Docs & links · Filed.
 */
enum class InboxFilter(val label: String) {
    ALL("All"),
    NEEDS_ACTION("Needs you"),
    MEDIA("Voice & media"),
    DOCS("Docs & links"),
    FILED("Filed")
}

/**
 * The Work Inbox (spec "Inbox", #8a–#8g). Capture (Voice note · Scan · Paste note · File), filters, then a card
 * per item. Opening an item shows its item screen in the same route; back returns to the list. Documents are
 * read on this phone as they arrive, and a suggestion appears when the read is done. Nothing is filed without
 * a tap on File, and every filing or dismissal can be undone from the snackbar.
 *
 * @param onOpenDocument opens the document viewer for an item at a page (null: the first page).
 * @param onOpenProject opens a project from a filed item.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onImportAudio: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit = { _, _ -> },
    onOpenProcessing: (String) -> Unit = {},
    onOpenDocument: ((String, Int?) -> Unit)? = null,
    onOpenProject: ((String) -> Unit)? = null
) {
    val openItems by viewModel.inboxItems.collectAsState()
    val filedItems by viewModel.inboxFiledItems.collectAsState()
    val linkedMeetings by viewModel.inboxLinkedMeetings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var picking by remember { mutableStateOf<InboxItemEntity?>(null) }
    var inspectingId by remember { mutableStateOf<String?>(null) }
    var activeFilter by remember { mutableStateOf(InboxFilter.ALL) }
    var showCaptureTextSheet by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<InboxUndo?>(null) }
    val companionShown = rememberCompanionVisible(CompanionPage.HOME)

    // Files picked from the device. Voice notes and video are transcribed; PDFs and images are read on the phone.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importUriToInbox(uri) { count ->
                Toast.makeText(
                    context,
                    if (count > 0) "Imported into Work Inbox" else "Couldn't import file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // The document scanner runs on the device and returns a PDF; it lands in the Inbox like a share.
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri?.let { uri ->
                viewModel.addToInbox(
                    listOf(
                        SharedPart(
                            InboxKind.PDF,
                            uri = uri.toString(),
                            title = "Document scan",
                            mime = "application/pdf",
                            source = "Document Scanner"
                        )
                    )
                )
            }
        }
    }
    fun scan() {
        val activity = context as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { Toast.makeText(context, "The document scanner isn't available on this phone", Toast.LENGTH_SHORT).show() }
    }

    fun dismiss(item: InboxItemEntity) {
        viewModel.dismissInbox(item)
        undo = InboxUndo(item.id, InboxUndoKind.DISMISS, "Dismissed")
    }

    fun fileWith(item: InboxItemEntity, filing: Filing) {
        scope.launch {
            if (viewModel.fileInbox(item, filing)) {
                undo = InboxUndo(item.id, InboxUndoKind.FILE, "Filed to ${filing.targetLabel ?: filing.action.label}")
            } else {
                Toast.makeText(context, "Couldn't file it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Anything new gets its one proposal, so a share is a confirm away from filed.
    LaunchedEffect(openItems.map { it.id to it.status }) {
        openItems.filter { it.status == InboxStatus.NEW.name }.forEach { viewModel.proposeInbox(it) }
    }

    // The Undo snackbar. A new action replaces the old one; Undo puts the item back where it was.
    LaunchedEffect(undo) {
        val current = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(current.message, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
            when (current.kind) {
                InboxUndoKind.DISMISS -> viewModel.restoreInbox(current.itemId)
                InboxUndoKind.FILE -> viewModel.unfileInbox(current.itemId)
            }
        }
        if (undo == current) undo = null
    }

    val visibleOpen = remember(openItems, activeFilter) {
        if (activeFilter == InboxFilter.FILED) emptyList() else openItems.filter { InboxRules.matches(activeFilter, it) }
    }
    fun countFor(filter: InboxFilter): Int =
        if (filter == InboxFilter.FILED) filedItems.size else openItems.count { InboxRules.matches(filter, it) }

    fun noteIdOf(item: InboxItemEntity): String? =
        linkedMeetings[item.id]?.noteId ?: runCatching {
            item.resultRefJson?.let { org.json.JSONObject(it).optString("noteId").ifBlank { null } }
        }.getOrNull()

    fun openTranscriptOf(item: InboxItemEntity): (() -> Unit)? {
        val linked = linkedMeetings[item.id]
        val mid = linked?.meetingId ?: item.meetingId ?: return null
        return {
            if (linked?.status == "PROCESSING" || item.transcriptionStatus == "TRANSCRIBING") onOpenProcessing(mid)
            else onOpenMeeting(mid, null)
        }
    }

    fun retryTranscription(item: InboxItemEntity) {
        viewModel.transcribeInboxItem(item) { ok, msg ->
            Toast.makeText(
                context,
                if (ok) "Transcribing in background…" else (msg ?: "Couldn't start transcription"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun retryRead(item: InboxItemEntity) {
        scope.launch { DocumentReading.reread(context, item.id) }
    }

    // One-tap transcription for a recording or YouTube link that has not started (the YouTube path is unchanged).
    fun transcribeActionFor(row: InboxItemEntity): (() -> Unit)? {
        val mediaOrYouTube = InboxRules.isMedia(row) || InboxRules.isYouTube(row)
        if (!mediaOrYouTube || row.transcriptionStatus != "PENDING") return null
        return { retryTranscription(row) }
    }

    // Transcription settings: the file for a recording, the URL for a YouTube link.
    fun configureTranscriptionFor(row: InboxItemEntity): (() -> Unit)? {
        val path = (row.uri?.takeIf { InboxRules.isMedia(row) } ?: row.text?.takeIf { InboxRules.isYouTube(row) }) ?: return null
        return { onImportAudio(path) }
    }

    // The item screen takes over the route while an item is open; back returns to the list.
    val inspected: InboxItemEntity? = inspectingId?.let { id ->
        openItems.firstOrNull { it.id == id } ?: filedItems.firstOrNull { it.id == id }
    }

    // The root box holds the Undo snackbar, so it shows over the item screen as well as the list.
    Box(Modifier.fillMaxSize()) {
    if (inspected != null) {
        BackHandler { inspectingId = null }
        InboxItemScreen(
            item = inspected,
            linked = linkedMeetings[inspected.id],
            viewModel = viewModel,
            onBack = { inspectingId = null },
            onChange = { picking = inspected },
            onDismiss = {
                inspectingId = null
                dismiss(inspected)
            },
            onOpenDocument = onOpenDocument?.let { open -> { page: Int? -> open(inspected.id, page) } },
            onOpenProject = onOpenProject,
            onOpenNote = noteIdOf(inspected)?.let { nid -> { inspectingId = null; onOpenNote(nid) } },
            onOpenTranscript = openTranscriptOf(inspected)?.let { open -> { inspectingId = null; open() } },
            onRetryTranscription = { retryTranscription(inspected) },
            onRetryRead = { retryRead(inspected) },
            onConfigureTranscribe = configureTranscriptionFor(inspected),
            onFailed = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        )
    } else {
        Scaffold(containerColor = Background) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("work_inbox"),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                item(key = "header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.ink)
                        }
                        if (companionShown) {
                            ZuriSlot(CompanionPage.HOME, 36.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Work Inbox", style = MM.type.title, color = MM.colors.ink)
                                if (openItems.isNotEmpty()) {
                                    Surface(shape = CircleShape, color = MM.colors.accentWash) {
                                        Text(
                                            "${openItems.size}",
                                            style = MM.type.caption,
                                            color = MM.colors.accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "Things to file. Nothing is filed without you.",
                                style = MM.type.secondary,
                                color = MM.colors.inkSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                item(key = "capture") {
                    InboxCaptureTiles(
                        onVoice = { filePicker.launch(arrayOf("audio/*", "video/*")) },
                        onScan = { scan() },
                        onPaste = { showCaptureTextSheet = true },
                        onFile = { filePicker.launch(arrayOf("*/*")) }
                    )
                }

                item(key = "filters") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InboxFilter.entries.forEach { f ->
                            FilterChip(
                                label = "${f.label} (${countFor(f)})",
                                selected = activeFilter == f,
                                onClick = { activeFilter = f }
                            )
                        }
                    }
                }

                if (activeFilter == InboxFilter.FILED) {
                    if (filedItems.isEmpty()) {
                        item(key = "filed-empty") { EmptyLine("Nothing filed yet.") }
                    }
                    items(filedItems, key = { "filed-${it.id}" }) { filed ->
                        InboxFiledRow(item = filed, onOpen = { inspectingId = filed.id })
                    }
                } else if (openItems.isEmpty()) {
                    item(key = "empty") {
                        InboxEmptyState(onSeeFiled = { activeFilter = InboxFilter.FILED })
                    }
                } else if (visibleOpen.isEmpty()) {
                    item(key = "empty-filter") { EmptyLine("Nothing in '${activeFilter.label}'.") }
                } else {
                    items(visibleOpen, key = { it.id }) { row ->
                        InboxItemCard(
                            item = row,
                            linked = linkedMeetings[row.id],
                            suggestion = InboxRules.suggestion(row),
                            onOpen = { inspectingId = row.id },
                            onFile = { filing -> fileWith(row, filing) },
                            onChange = { picking = row },
                            onDismiss = { dismiss(row) },
                            onTranscribe = transcribeActionFor(row)
                        )
                    }
                }
            }
        }
    }

    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }

    picking?.let { row ->
        FileToSheet(row, viewModel, onDismiss = { picking = null }) { filing ->
            picking = null
            fileWith(row, filing)
        }
    }

    if (showCaptureTextSheet) {
        CaptureTextToInboxSheet(
            onDismiss = { showCaptureTextSheet = false },
            onSave = { text, title, source ->
                showCaptureTextSheet = false
                viewModel.addTextToInbox(text, title, source)
                Toast.makeText(context, "Added to Work Inbox", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * Lightweight inline audio player for voice notes and imported audio inside the Work Inbox.
 */
@Composable
internal fun InboxInlineAudioPlayer(
    filePath: String,
    durationMs: Long?,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember(filePath) { mutableStateOf(false) }
    var currentPosMs by remember(filePath) { mutableIntStateOf(0) }
    var totalDurationMs by remember(filePath, durationMs) { mutableIntStateOf((durationMs ?: 0L).toInt()) }
    val playerRef = remember(filePath) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(filePath) {
        onDispose {
            runCatching {
                playerRef.value?.stop()
                playerRef.value?.release()
            }
            playerRef.value = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = playerRef.value
            if (mp != null && runCatching { mp.isPlaying }.getOrDefault(false)) {
                currentPosMs = runCatching { mp.currentPosition }.getOrDefault(0)
                val dur = runCatching { mp.duration }.getOrDefault(0)
                if (dur > 0) totalDurationMs = dur
            } else {
                isPlaying = false
            }
            delay(200L)
        }
    }

    fun togglePlay() {
        val existing = playerRef.value
        if (existing != null) {
            runCatching {
                if (existing.isPlaying) {
                    existing.pause()
                    isPlaying = false
                } else {
                    existing.start()
                    isPlaying = true
                }
            }
        } else {
            runCatching {
                val mp = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    if (duration > 0) totalDurationMs = duration
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosMs = 0
                    }
                    start()
                }
                playerRef.value = mp
                isPlaying = true
            }
        }
    }

    val progress = if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSunk)
            .border(1.dp, LineSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable { togglePlay() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                tint = FixedWhite,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = Accent,
                trackColor = Line
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatMediaDuration(currentPosMs.toLong()) ?: "0:00",
                    fontSize = 10.5.sp,
                    color = InkMuted
                )
                Text(
                    formatMediaDuration(totalDurationMs.toLong()) ?: "Voice note",
                    fontSize = 10.5.sp,
                    color = InkMuted
                )
            }
        }
    }
}

@Composable
internal fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Ink else SurfaceBase,
        modifier = Modifier.border(1.dp, if (selected) Ink else LineSoft, RoundedCornerShape(50))
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) OnInk else InkSecondary,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureTextToInboxSheet(
    onDismiss: () -> Unit,
    onSave: (text: String, title: String?, source: String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Quick Capture") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Add Note or Link to Inbox", fontFamily = OutfitFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                Chip("Paste Clipboard", selected = false, color = Accent) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    if (clip.isNotBlank()) {
                        text = clip
                        source = "Clipboard"
                    } else {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text, meeting snippet, or URL") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Chip("Save to Inbox", selected = true, color = Accent) {
                    if (text.isNotBlank()) {
                        onSave(text, title.ifBlank { null }, source)
                    }
                }
            }
        }
    }
}

internal data class KindVisual(val icon: ImageVector, val label: String)

internal fun kindMeta(kind: String): KindVisual = when (kind.uppercase()) {
    "VIDEO" -> KindVisual(Icons.Filled.VideoFile, "Video")
    "AUDIO" -> KindVisual(Icons.Filled.AudioFile, "Audio")
    "PDF" -> KindVisual(Icons.AutoMirrored.Filled.InsertDriveFile, "PDF")
    "URL" -> KindVisual(Icons.Filled.Link, "Link")
    "IMAGE" -> KindVisual(Icons.AutoMirrored.Filled.InsertDriveFile, "Image")
    else -> KindVisual(Icons.AutoMirrored.Filled.InsertDriveFile, kind.lowercase().replaceFirstChar { it.uppercase() })
}

internal fun relativeTimeLabel(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - timestampMs
    if (diff < 60_000L) return "Just now"
    if (diff < 3_600_000L) return "${diff / 60_000L}m ago"
    val days = ((DueDates.startOfDay(now) - DueDates.startOfDay(timestampMs)) / 86_400_000L).toInt()
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
    return when (days) {
        0 -> "Today · $time"
        1 -> "Yesterday · $time"
        in 2..6 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestampMs)) + " · $time"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs)) + " · $time"
    }
}

/** Formats the exact import date and time as a single readable string, e.g. "14 Nov 2026 · 8:45 PM". */
internal fun formatImportDateTime(timestampMs: Long, locale: Locale = Locale.getDefault()): String {
    val (datePart, timePart) = formatImportDateAndTimeParts(timestampMs, locale)
    return "$datePart · $timePart"
}

/** Returns (exactDateString, exactTimeString) for an imported item, e.g. ("14 Nov 2026", "8:45 PM"). */
internal fun formatImportDateAndTimeParts(timestampMs: Long, locale: Locale = Locale.getDefault()): Pair<String, String> {
    val date = Date(timestampMs)
    val dateStr = SimpleDateFormat("d MMM yyyy", locale).format(date)
    val timeStr = SimpleDateFormat("h:mm a", locale).format(date)
    return dateStr to timeStr
}

/** Extracts a recording date from WhatsApp filenames like `PTT-20261114-WA0005.opus` -> `14 Nov 2026`. */
internal fun parseWhatsAppDateFromFileName(nameOrPath: String?, locale: Locale = Locale.getDefault()): String? {
    val raw = nameOrPath ?: return null
    val match = Regex("""(?:PTT|AUD|VID|IMG|DOC)-(\d{4})(\d{2})(\d{2})-WA\d+""", RegexOption.IGNORE_CASE).find(raw) ?: return null
    return runCatching {
        val parsed = SimpleDateFormat("yyyyMMdd", Locale.US).parse("${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}") ?: return null
        SimpleDateFormat("d MMM yyyy", locale).format(parsed)
    }.getOrNull()
}

/** Produces a clean human-friendly headline when the stored title is a raw recorder/WhatsApp code like `PTT-20261114-WA0005`. */
internal fun formatInboxDisplayTitle(
    rawTitle: String?,
    kind: String,
    formattedSource: String?,
    linkedMeetingTitle: String? = null
): String {
    val linked = linkedMeetingTitle?.trim()?.takeIf {
        it.isNotBlank() && !isMachineFileCode(it)
    }
    if (linked != null) return linked
    val t = rawTitle?.trim()?.ifBlank { null }
    if (t != null && !isMachineFileCode(t)) return t
    return when {
        formattedSource?.contains("WhatsApp Voice", ignoreCase = true) == true -> "WhatsApp Voice Note"
        formattedSource?.contains("WhatsApp", ignoreCase = true) == true && kind.equals("AUDIO", true) -> "WhatsApp Voice Note"
        formattedSource?.contains("WhatsApp", ignoreCase = true) == true && kind.equals("VIDEO", true) -> "WhatsApp Video"
        formattedSource?.contains("Telegram", ignoreCase = true) == true && kind.equals("AUDIO", true) -> "Telegram Voice Note"
        kind.equals("AUDIO", ignoreCase = true) -> formattedSource?.let { "$it Audio" } ?: (t ?: "Voice Note")
        kind.equals("VIDEO", ignoreCase = true) -> formattedSource?.let { "$it Video" } ?: (t ?: "Imported Video")
        else -> t ?: "Untitled"
    }
}

private fun isMachineFileCode(name: String): Boolean {
    val base = name.substringBeforeLast('.')
    return Regex("""^(?:PTT|AUD|VID|IMG|DOC)-\d{8}-WA\d+$""", RegexOption.IGNORE_CASE).matches(base) ||
        Regex("""^(?:AUD|VID|REC|VN|VOICE)[_-]?\d{4,}$""", RegexOption.IGNORE_CASE).matches(base)
}

private fun inferDisplaySourceFromItem(item: InboxItemEntity): String? {
    val combined = listOfNotNull(item.title, item.uri).joinToString(" ")
    return when {
        Regex("""\bPTT-\d{8}-WA\d+""", RegexOption.IGNORE_CASE).containsMatchIn(combined) -> "WhatsApp Voice Note"
        Regex("""\bAUD-\d{8}-WA\d+""", RegexOption.IGNORE_CASE).containsMatchIn(combined) -> "WhatsApp Audio"
        Regex("""\bVID-\d{8}-WA\d+""", RegexOption.IGNORE_CASE).containsMatchIn(combined) -> "WhatsApp Video"
        combined.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        combined.contains("telegram", ignoreCase = true) -> "Telegram"
        else -> null
    }
}

internal fun formatSource(source: String?): String? {
    val s = source?.trim()?.ifBlank { null } ?: return null
    if (s.equals("Shared", ignoreCase = true)) return null
    return when {
        s.equals("WhatsApp Voice Note", ignoreCase = true) -> "WhatsApp Voice Note"
        s.equals("WhatsApp Audio", ignoreCase = true) -> "WhatsApp Audio"
        s.equals("WhatsApp Video", ignoreCase = true) -> "WhatsApp Video"
        s.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        s.contains("telegram", ignoreCase = true) -> if (s.contains("voice", ignoreCase = true)) "Telegram Voice Note" else "Telegram"
        s.contains("chrome", ignoreCase = true) -> "Chrome"
        s.contains("youtube", ignoreCase = true) -> "YouTube"
        s.contains("twitter", ignoreCase = true) || s.contains(".x.com", ignoreCase = true) -> "X"
        s.contains("slack", ignoreCase = true) -> "Slack"
        s.contains("gmail", ignoreCase = true) || s.contains("android.gm", ignoreCase = true) -> "Gmail"
        s.contains("teams", ignoreCase = true) -> "Teams"
        s.contains("drive", ignoreCase = true) || s.contains("apps.docs", ignoreCase = true) -> "Google Drive"
        s.contains("photos", ignoreCase = true) -> "Photos"
        s.contains("recorder", ignoreCase = true) -> "Voice Recorder"
        s.contains("scanner", ignoreCase = true) -> "Document Scanner"
        s.contains('.') && !s.contains('/') -> s.removePrefix("www.")
        s.startsWith("http", ignoreCase = true) -> s.substringAfter("://").substringBefore('/').removePrefix("www.")
        else -> s.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

internal fun formatMediaDuration(ms: Long?): String? {
    val total = ms ?: return null
    if (total <= 0) return null
    val s = total / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** The manual picker: a note in a project, or on an organisation's or person's page. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FileToSheet(item: InboxItemEntity, viewModel: WorkViewModel, onDismiss: () -> Unit, onPick: (Filing) -> Unit) {
    val candidates by produceState<List<FilingCandidate>>(emptyList()) { value = viewModel.inboxCandidates() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("File to…", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (candidates.isEmpty()) Text("Create a project or add people first, or file it as a plain note.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(vertical = 8.dp))
            listOf(FilingTarget.PROJECT to "Projects", FilingTarget.ORG to "Organisations", FilingTarget.PERSON to "People").forEach { (t, label) ->
                val list = candidates.filter { it.target == t }
                if (list.isNotEmpty()) {
                    Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = InkMuted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.take(30).forEach { c -> Chip(c.name + if (c.confidential) " 🔒" else "") { onPick(Filing(FilingAction.NOTE, c.target, c.id, c.name, title = item.title)) } }
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp)) { Chip("Just a note") { onPick(Filing(FilingAction.NOTE, title = item.title)) } }
        }
    }
}
