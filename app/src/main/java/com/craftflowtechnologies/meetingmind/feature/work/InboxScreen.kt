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

/**
 * Filter categories for Work Inbox triage.
 */
enum class InboxFilter(val label: String) {
    ALL("All"),
    NEEDS_ACTION("Action needed"),
    MEDIA("Voice & Media"),
    DOCS("Docs & links"),
    FILED("Filed")
}

/**
 * The Work Inbox (docs/PLAN_PROFESSIONAL.md D7): the front door for WhatsApp voice notes, recordings,
 * videos, documents, links, and shared notes. Every item displays its exact origin, import date and time,
 * inline audio playback, automatic transcription status, AI summary, and direct links to its Note and Transcript.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onImportAudio: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit = { _, _ -> },
    onOpenProcessing: (String) -> Unit = {}
) {
    val openItems by viewModel.inboxItems.collectAsState()
    val filedItems by viewModel.inboxFiledItems.collectAsState()
    val linkedMeetings by viewModel.inboxLinkedMeetings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<InboxItemEntity?>(null) }
    var inspecting by remember { mutableStateOf<InboxItemEntity?>(null) }
    var activeFilter by remember { mutableStateOf(InboxFilter.ALL) }
    var showCaptureTextSheet by remember { mutableStateOf(false) }
    val companionShown = rememberCompanionVisible(CompanionPage.HOME)

    // File picker for importing WhatsApp voice notes, audio, video, or PDFs directly into the Work Inbox.
    val inboxFilePicker = rememberLauncherForActivityResult(
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

    // Anything new gets its one proposal, so a share is a confirm away from filed.
    LaunchedEffect(openItems.map { it.id to it.status }) {
        openItems.filter { it.status == InboxStatus.NEW.name }.forEach { viewModel.proposeInbox(it) }
    }

    val filteredItems = remember(openItems, filedItems, activeFilter) {
        when (activeFilter) {
            InboxFilter.ALL -> openItems
            InboxFilter.NEEDS_ACTION -> openItems.filter { it.status == InboxStatus.PROPOSED.name || it.transcriptionStatus == "PENDING" }
            InboxFilter.MEDIA -> openItems.filter { it.kind == InboxKind.AUDIO.name || it.kind == InboxKind.VIDEO.name }
            InboxFilter.DOCS -> openItems.filter { it.kind == InboxKind.PDF.name || it.kind == InboxKind.URL.name || it.kind == InboxKind.FILE.name || it.kind == InboxKind.TEXT.name }
            InboxFilter.FILED -> filedItems
        }
    }

    val pendingTranscriptions = remember(openItems) { openItems.count { it.transcriptionStatus == "PENDING" } }
    val completedTranscriptions = remember(openItems) { openItems.count { it.transcriptionStatus == "COMPLETED" } }
    val readyProposals = remember(openItems) { openItems.count { it.status == InboxStatus.PROPOSED.name } }

    Scaffold(containerColor = Background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("work_inbox"),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item(key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink)
                        }
                        if (companionShown) {
                            ZuriSlot(CompanionPage.HOME, 36.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Work Inbox",
                                    fontFamily = OutfitFamily,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink
                                )
                                if (openItems.isNotEmpty()) {
                                    Surface(shape = CircleShape, color = AccentWash) {
                                        Text(
                                            "${openItems.size}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "Voice notes, WhatsApp shares, recordings, docs & links — with full source, date, time, AI summary and note links.",
                                fontFamily = InterFamily,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = InkSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Quick Capture / Import Bar
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InboxQuickCaptureTile(
                            icon = Icons.Filled.GraphicEq,
                            label = "Voice Note / File",
                            subtitle = "WhatsApp, audio, video",
                            tint = Speaker3,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                inboxFilePicker.launch(arrayOf("audio/*", "video/*", "application/ogg", "application/pdf", "*/*"))
                            }
                        )
                        InboxQuickCaptureTile(
                            icon = Icons.Filled.DocumentScanner,
                            label = "Scan Doc",
                            subtitle = "Camera to PDF",
                            tint = Accent,
                            modifier = Modifier.weight(1f),
                            onClick = { scan() }
                        )
                        InboxQuickCaptureTile(
                            icon = Icons.Filled.ContentPaste,
                            label = "Paste Note",
                            subtitle = "Text or URL",
                            tint = Speaker2,
                            modifier = Modifier.weight(1f),
                            onClick = { showCaptureTextSheet = true }
                        )
                    }

                    // Live Triage Status Strip
                    if (openItems.isNotEmpty()) {
                        InboxTriageSummaryStrip(
                            totalOpen = openItems.size,
                            readyProposals = readyProposals,
                            pendingMedia = pendingTranscriptions,
                            transcribedMedia = completedTranscriptions,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }

                    // Filter tabs
                    if (openItems.isNotEmpty() || filedItems.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InboxFilter.entries.forEach { f ->
                                val count = when (f) {
                                    InboxFilter.ALL -> openItems.size
                                    InboxFilter.NEEDS_ACTION -> openItems.count { it.status == InboxStatus.PROPOSED.name || it.transcriptionStatus == "PENDING" }
                                    InboxFilter.MEDIA -> openItems.count { it.kind == InboxKind.AUDIO.name || it.kind == InboxKind.VIDEO.name }
                                    InboxFilter.DOCS -> openItems.count { it.kind == InboxKind.PDF.name || it.kind == InboxKind.URL.name || it.kind == InboxKind.FILE.name || it.kind == InboxKind.TEXT.name }
                                    InboxFilter.FILED -> filedItems.size
                                }
                                FilterChip(
                                    label = "${f.label} ($count)",
                                    selected = activeFilter == f,
                                    onClick = { activeFilter = f }
                                )
                            }
                        }
                    }
                }
            }

            if (openItems.isEmpty() && activeFilter != InboxFilter.FILED) {
                item(key = "empty") {
                    InboxEmptyCard(
                        onImportFile = {
                            inboxFilePicker.launch(arrayOf("audio/*", "video/*", "application/ogg", "application/pdf", "*/*"))
                        },
                        onScan = { scan() },
                        onPaste = { showCaptureTextSheet = true }
                    )
                }
            } else if (filteredItems.isEmpty()) {
                item(key = "empty-filter") {
                    EmptyLine("No items in '${activeFilter.label}'.")
                }
            }

            items(filteredItems, key = { it.id }) { item ->
                val linked = linkedMeetings[item.id]
                val resolvedNoteId = linked?.noteId ?: remember(item.resultRefJson) {
                    runCatching { item.resultRefJson?.let { org.json.JSONObject(it).optString("noteId").ifBlank { null } } }.getOrNull()
                }
                val resolvedMeetingId = linked?.meetingId ?: item.meetingId

                InboxCard(
                    item = item,
                    linked = linked,
                    onConfirm = { filing ->
                        scope.launch {
                            val ok = viewModel.fileInbox(item, filing)
                            Toast.makeText(context, if (ok) "Filed to ${filing.targetLabel ?: filing.action.label}" else "Couldn't file it", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onChoose = { picking = item },
                    onDismiss = { viewModel.dismissInbox(item) },
                    onQuickTranscribe = if (item.kind == InboxKind.AUDIO.name || item.kind == InboxKind.VIDEO.name) {
                        {
                            viewModel.transcribeInboxItem(item) { ok, msg ->
                                Toast.makeText(
                                    context,
                                    if (ok) "Transcribing voice note in background…" else (msg ?: "Couldn't start transcription"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else null,
                    onConfigureTranscribe = item.uri?.takeIf { item.kind == InboxKind.AUDIO.name || item.kind == InboxKind.VIDEO.name }?.let { path ->
                        { onImportAudio(path) }
                    },
                    onOpenNote = resolvedNoteId?.let { nid -> { onOpenNote(nid) } },
                    onOpenTranscript = resolvedMeetingId?.let { mid ->
                        {
                            if (linked?.status == "PROCESSING" || item.transcriptionStatus == "TRANSCRIBING") {
                                onOpenProcessing(mid)
                            } else {
                                onOpenMeeting(mid, null)
                            }
                        }
                    },
                    onInspect = { inspecting = item }
                )
            }
        }
    }

    picking?.let { item ->
        FileToSheet(item, viewModel, onDismiss = { picking = null }) { filing ->
            picking = null
            scope.launch {
                val ok = viewModel.fileInbox(item, filing)
                Toast.makeText(context, if (ok) "Filed to ${filing.targetLabel ?: filing.action.label}" else "Couldn't file it", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inspecting?.let { item ->
        val currentItem = openItems.firstOrNull { it.id == item.id }
            ?: filedItems.firstOrNull { it.id == item.id }
            ?: item
        val linked = linkedMeetings[currentItem.id]
        val resolvedNoteId = linked?.noteId ?: runCatching {
            currentItem.resultRefJson?.let { org.json.JSONObject(it).optString("noteId").ifBlank { null } }
        }.getOrNull()
        val resolvedMeetingId = linked?.meetingId ?: currentItem.meetingId

        InboxDetailSheet(
            item = currentItem,
            linked = linked,
            onDismiss = { inspecting = null },
            onConfirm = { filing ->
                inspecting = null
                scope.launch {
                    val ok = viewModel.fileInbox(currentItem, filing)
                    Toast.makeText(context, if (ok) "Filed to ${filing.targetLabel ?: filing.action.label}" else "Couldn't file it", Toast.LENGTH_SHORT).show()
                }
            },
            onFileTo = {
                val itm = currentItem
                inspecting = null
                picking = itm
            },
            onQuickTranscribe = if (currentItem.kind == InboxKind.AUDIO.name || currentItem.kind == InboxKind.VIDEO.name) {
                {
                    viewModel.transcribeInboxItem(currentItem) { ok, msg ->
                        Toast.makeText(
                            context,
                            if (ok) "Transcribing in background…" else (msg ?: "Couldn't start transcription"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else null,
            onConfigureTranscribe = currentItem.uri?.takeIf { currentItem.kind == InboxKind.AUDIO.name || currentItem.kind == InboxKind.VIDEO.name }?.let { path ->
                {
                    inspecting = null
                    onImportAudio(path)
                }
            },
            onOpenNote = resolvedNoteId?.let { nid ->
                {
                    inspecting = null
                    onOpenNote(nid)
                }
            },
            onOpenTranscript = resolvedMeetingId?.let { mid ->
                {
                    inspecting = null
                    if (linked?.status == "PROCESSING" || currentItem.transcriptionStatus == "TRANSCRIBING") {
                        onOpenProcessing(mid)
                    } else {
                        onOpenMeeting(mid, null)
                    }
                }
            }
        )
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

@Composable
private fun InboxQuickCaptureTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceBase)
            .border(1.dp, LineSoft, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            fontFamily = InterFamily,
            fontSize = 11.sp,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InboxTriageSummaryStrip(
    totalOpen: Int,
    readyProposals: Int,
    pendingMedia: Int,
    transcribedMedia: Int,
    modifier: Modifier = Modifier
) {
    val parts = buildList {
        add("$totalOpen in inbox")
        if (readyProposals > 0) add("$readyProposals ready to confirm")
        if (pendingMedia > 0) add("$pendingMedia awaiting transcription")
        if (transcribedMedia > 0) add("$transcribedMedia transcribed")
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AccentWash)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, null, tint = Accent, modifier = Modifier.size(16.dp))
        Text(
            parts.joinToString(" · "),
            fontFamily = InterFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InboxEmptyCard(
    onImportFile: () -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceBase)
            .border(1.dp, LineSoft, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZuriSlot(CompanionPage.EMPTY, 84.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your Work Inbox is clear",
            fontFamily = OutfitFamily,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink
        )
        Text(
            "Share a WhatsApp voice note, video, PDF, or link from any app to MeetingMind — or import one right here. We'll track where it came from, when it arrived, transcribe it, and link its AI summary to your notes.",
            fontFamily = InterFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = InkSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("Import Voice Note / File", selected = true, color = Accent, onClick = onImportFile)
            Chip("Scan Doc", selected = false, color = Ink, onClick = onScan)
            Chip("Paste", selected = false, color = Ink, onClick = onPaste)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(
    item: InboxItemEntity,
    linked: InboxLinkedMeeting?,
    onConfirm: (Filing) -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
    onQuickTranscribe: (() -> Unit)?,
    onConfigureTranscribe: (() -> Unit)?,
    onOpenNote: (() -> Unit)?,
    onOpenTranscript: (() -> Unit)?,
    onInspect: () -> Unit
) {
    val filing = remember(item.proposedJson) { Filing.fromJson(item.proposedJson) }
    val formattedSource = remember(item.source, item.uri, item.title) {
        formatSource(item.source) ?: inferDisplaySourceFromItem(item)
    }
    val timeLabel = remember(item.createdAt) { relativeTimeLabel(item.createdAt) }
    val exactDateTime = remember(item.createdAt) { formatImportDateTime(item.createdAt) }
    val (importDatePart, importTimePart) = remember(item.createdAt) { formatImportDateAndTimeParts(item.createdAt) }
    val effectiveDurationMs = linked?.durationMs?.takeIf { it > 0L } ?: item.mediaDurationMs
    val durationLabel = remember(effectiveDurationMs) { formatMediaDuration(effectiveDurationMs) }
    val displayTitle = remember(item.title, item.kind, formattedSource, linked?.title) {
        formatInboxDisplayTitle(item.title, item.kind, formattedSource, linked?.title)
    }
    val originalFileDate = remember(item.title, item.uri) {
        parseWhatsAppDateFromFileName(item.title ?: item.uri)
    }
    val isTranscribed = item.transcriptionStatus == "COMPLETED" || linked?.status == "READY"
    val isTranscribing = item.transcriptionStatus == "TRANSCRIBING" || linked?.status == "PROCESSING"
    val isFailed = item.transcriptionStatus == "FAILED" || linked?.status == "ERROR"
    val playablePath = remember(linked?.audioFilePath, item.uri, item.kind) {
        if (item.kind == InboxKind.AUDIO.name || item.kind == InboxKind.VIDEO.name) {
            (linked?.audioFilePath ?: item.uri)?.takeIf { File(it).exists() }
        } else null
    }
    val summaryText = linked?.summaryText
    val transcriptText = remember(item.text, linked?.transcriptPreview, item.title) {
        (item.text?.takeIf { it.isNotBlank() && it != item.title } ?: linked?.transcriptPreview)
    }
    val detectedTasks = remember(filing?.tasks, linked?.actionItems) {
        (filing?.tasks.orEmpty() + linked?.actionItems.orEmpty()).distinct()
    }
    val detectedDecisions = remember(filing?.decision, linked?.decisions) {
        (listOfNotNull(filing?.decision) + linked?.decisions.orEmpty()).distinct()
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceBase,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .border(1.dp, if (isTranscribed) Speaker3.copy(alpha = 0.35f) else LineSoft, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onInspect)
            .testTag("inbox_item")
    ) {
        Column(Modifier.padding(16.dp)) {
            // Top Row: Kind badge, Source Attribution ("From WhatsApp"), and relative timestamp
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KindBadge(item.kind, formattedSource)
                if (formattedSource != null) {
                    Spacer(Modifier.width(6.dp))
                    SourcePill(formattedSource)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    timeLabel,
                    fontFamily = InterFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = InkMuted
                )
            }

            // Title and Duration
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayTitle,
                    fontFamily = OutfitFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (durationLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    DurationBadge(durationLabel)
                }
            }

            // Explicit Provenance Strip: Imported From + Exact Date + Exact Time
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceSunk)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.CalendarToday, null, tint = InkMuted, modifier = Modifier.size(12.dp))
                    Text(
                        importDatePart,
                        fontFamily = InterFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.AccessTime, null, tint = InkMuted, modifier = Modifier.size(12.dp))
                    Text(
                        importTimePart,
                        fontFamily = InterFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkSecondary
                    )
                }
                if (originalFileDate != null && originalFileDate != importDatePart) {
                    Text(
                        "· Rec $originalFileDate",
                        fontFamily = InterFamily,
                        fontSize = 11.sp,
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (formattedSource != null) {
                    Text(
                        "· via $formattedSource",
                        fontFamily = InterFamily,
                        fontSize = 11.sp,
                        color = InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Inline Audio Player for Voice Notes / Audio / Video
            if (playablePath != null) {
                InboxInlineAudioPlayer(
                    filePath = playablePath,
                    durationMs = effectiveDurationMs,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // Transcription & Summary Status
            if (isTranscribed) {
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(14.dp))
                    Text(
                        if (summaryText != null) "Transcribed & Summarized" else "Transcribed",
                        fontFamily = InterFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Success
                    )
                    if (detectedTasks.isNotEmpty()) {
                        MetaPill("⚡ ${detectedTasks.size} ${if (detectedTasks.size == 1) "task" else "tasks"}")
                    }
                    if (detectedDecisions.isNotEmpty()) {
                        MetaPill("📌 ${detectedDecisions.size} ${if (detectedDecisions.size == 1) "decision" else "decisions"}")
                    }
                }

                // AI Summary Box properly linked to the Note
                if (!summaryText.isNullOrBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentWash.copy(alpha = 0.55f))
                            .border(1.dp, Accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, null, tint = Accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "AI SUMMARY",
                                fontFamily = InterFamily,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Accent,
                                modifier = Modifier.weight(1f)
                            )
                            if (onOpenNote != null) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(onClick = onOpenNote)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Linked Note", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent, modifier = Modifier.size(12.dp).padding(start = 2.dp))
                                }
                            }
                        }
                        Text(
                            summaryText,
                            fontFamily = InterFamily,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Ink,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Transcript excerpt linked to the Recording/Transcript
                if (!transcriptText.isNullOrBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceSunk)
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Notes, null, tint = InkMuted, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "TRANSCRIPT",
                                fontFamily = InterFamily,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkMuted,
                                modifier = Modifier.weight(1f)
                            )
                            if (onOpenTranscript != null) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(onClick = onOpenTranscript)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Full transcript", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent, modifier = Modifier.size(12.dp).padding(start = 2.dp))
                                }
                            }
                        }
                        Text(
                            transcriptText,
                            fontFamily = InterFamily,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = InkSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else if (isTranscribing) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentWash)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Transcribing & generating AI summary…",
                            fontFamily = InterFamily,
                            fontSize = 12.5.sp,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (onOpenTranscript != null) {
                            Text(
                                "Progress →",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Accent,
                                modifier = Modifier.clickable(onClick = onOpenTranscript)
                            )
                        }
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape),
                        color = Accent,
                        trackColor = SurfaceBase
                    )
                }
            } else if (isFailed) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerWash)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Transcription didn't finish. Tap Transcribe to retry.",
                        fontSize = 12.sp,
                        color = Danger,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Non-media text / link snippet
                transcriptText?.let { snippet ->
                    Text(
                        snippet,
                        fontFamily = InterFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = InkSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Proposed Filing Card
            if (item.status == InboxStatus.FILED.name) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSunk)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(14.dp))
                    Text(
                        filing?.let { "Filed: ${describe(it)}" } ?: "Filed in Work",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkSecondary
                    )
                }
            } else if (filing != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceSunk)
                        .padding(12.dp)
                ) {
                    Text(
                        "PROPOSED FILING",
                        fontSize = 10.sp,
                        letterSpacing = 0.9.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkMuted
                    )
                    Text(
                        describe(filing),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    filing.reason?.let {
                        Text(it, fontSize = 11.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            } else {
                val hint = if (item.kind in listOf("AUDIO", "VIDEO") && !isTranscribed) {
                    "Tap Transcribe to generate an AI summary, full transcript, and linked note."
                } else {
                    "Choose a project, person, or organisation to file this item."
                }
                Text(
                    hint,
                    fontFamily = InterFamily,
                    fontSize = 12.5.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Action Buttons Row
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.status != InboxStatus.FILED.name) {
                    if (filing != null) {
                        Chip("Confirm", selected = true, color = Accent) { onConfirm(filing) }
                    }
                    if (onQuickTranscribe != null && !isTranscribed && !isTranscribing) {
                        Chip(
                            if (item.kind == InboxKind.VIDEO.name) "Transcribe video" else "Transcribe",
                            selected = filing == null,
                            color = Accent
                        ) { onQuickTranscribe() }
                        if (onConfigureTranscribe != null) {
                            Chip("Options…", selected = false, color = Ink) { onConfigureTranscribe() }
                        }
                    }
                    Chip(if (filing != null) "Change…" else "File to…", selected = filing == null && onQuickTranscribe == null, color = Ink) { onChoose() }
                }
                if (onOpenNote != null && (isTranscribed || item.status == InboxStatus.FILED.name)) {
                    Chip("View note", selected = false, color = Accent) { onOpenNote() }
                }
                if (onOpenTranscript != null && isTranscribed) {
                    Chip("Transcript", selected = false, color = Ink) { onOpenTranscript() }
                }
                if (item.status != InboxStatus.FILED.name) {
                    Chip("Dismiss") { onDismiss() }
                }
            }
        }
    }
}

/**
 * Lightweight inline audio player for voice notes and imported audio inside the Work Inbox.
 */
@Composable
private fun InboxInlineAudioPlayer(
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
private fun KindBadge(kind: String, source: String? = null) {
    val isVoiceNote = kind.equals("AUDIO", ignoreCase = true) && source?.contains("WhatsApp", ignoreCase = true) == true
    val meta = if (isVoiceNote) KindVisual(Icons.Filled.GraphicEq, "Voice Note") else kindMeta(kind)
    val tint = when (kind.uppercase()) {
        "AUDIO" -> if (isVoiceNote) Speaker3 else Accent
        "VIDEO" -> Accent
        "PDF" -> Speaker4
        "URL" -> Speaker2
        else -> InkSecondary
    }
    Surface(shape = RoundedCornerShape(8.dp), color = tint.copy(alpha = 0.12f)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(meta.icon, null, tint = tint, modifier = Modifier.size(12.dp))
            Text(meta.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

@Composable
private fun SourcePill(source: String) {
    val isWhatsApp = source.contains("WhatsApp", ignoreCase = true)
    val bg = if (isWhatsApp) Speaker3.copy(alpha = 0.12f) else SurfaceSunk
    val fg = if (isWhatsApp) Speaker3 else InkSecondary
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            "From $source",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DurationBadge(duration: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = AccentWash) {
        Row(
            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(11.dp))
            Text(duration, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = SurfaceSunk) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = InkSecondary, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

/**
 * Inspection sheet displaying full item provenance (imported from, date, time, file),
 * inline audio player, AI summary, scrollable transcript, and filing actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InboxDetailSheet(
    item: InboxItemEntity,
    linked: InboxLinkedMeeting?,
    onDismiss: () -> Unit,
    onConfirm: (Filing) -> Unit,
    onFileTo: () -> Unit,
    onQuickTranscribe: (() -> Unit)?,
    onConfigureTranscribe: (() -> Unit)?,
    onOpenNote: (() -> Unit)?,
    onOpenTranscript: (() -> Unit)?
) {
    val context = LocalContext.current
    val filing = remember(item.proposedJson) { Filing.fromJson(item.proposedJson) }
    val formattedSource = remember(item.source, item.uri, item.title) {
        formatSource(item.source) ?: inferDisplaySourceFromItem(item)
    }
    val timeLabel = remember(item.createdAt) { relativeTimeLabel(item.createdAt) }
    val (importDatePart, importTimePart) = remember(item.createdAt) { formatImportDateAndTimeParts(item.createdAt) }
    val effectiveDurationMs = linked?.durationMs?.takeIf { it > 0L } ?: item.mediaDurationMs
    val durationLabel = remember(effectiveDurationMs) { formatMediaDuration(effectiveDurationMs) }
    val displayTitle = remember(item.title, item.kind, formattedSource, linked?.title) {
        formatInboxDisplayTitle(item.title, item.kind, formattedSource, linked?.title)
    }
    val isTranscribed = item.transcriptionStatus == "COMPLETED" || linked?.status == "READY"
    val isTranscribing = item.transcriptionStatus == "TRANSCRIBING" || linked?.status == "PROCESSING"
    val playablePath = remember(linked?.audioFilePath, item.uri, item.kind) {
        if (item.kind == InboxKind.AUDIO.name || item.kind == InboxKind.VIDEO.name) {
            (linked?.audioFilePath ?: item.uri)?.takeIf { File(it).exists() }
        } else null
    }
    val summaryText = linked?.summaryText
    val transcriptText = item.text?.takeIf { it.isNotBlank() } ?: linked?.transcriptPreview
    val detectedTasks = remember(filing?.tasks, linked?.actionItems) {
        (filing?.tasks.orEmpty() + linked?.actionItems.orEmpty()).distinct()
    }
    val detectedDecisions = remember(filing?.decision, linked?.decisions) {
        (listOfNotNull(filing?.decision) + linked?.decisions.orEmpty()).distinct()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KindBadge(item.kind, formattedSource)
                if (formattedSource != null) {
                    Spacer(Modifier.width(6.dp))
                    SourcePill(formattedSource)
                }
                Spacer(Modifier.weight(1f))
                Text(timeLabel, fontSize = 12.sp, color = InkMuted)
            }

            Text(
                displayTitle,
                fontFamily = OutfitFamily,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(top = 10.dp)
            )

            // Provenance Metadata Table (Imported from, Date, Time, Duration, Original File)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceSunk)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProvenanceRow("Imported from", formattedSource ?: "Shared / Imported")
                ProvenanceRow("Date imported", importDatePart)
                ProvenanceRow("Time imported", importTimePart)
                if (durationLabel != null) {
                    ProvenanceRow("Duration", durationLabel)
                }
                item.uri?.let { path ->
                    val rawName = File(path).name.substringAfter('_')
                    if (rawName.isNotBlank()) {
                        ProvenanceRow("Source file", rawName)
                    }
                }
            }

            if (playablePath != null) {
                InboxInlineAudioPlayer(
                    filePath = playablePath,
                    durationMs = effectiveDurationMs,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            // Linked Note & Transcript Quick Bar
            if (onOpenNote != null || (onOpenTranscript != null && isTranscribed)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpenNote != null) {
                        Surface(
                            onClick = onOpenNote,
                            shape = RoundedCornerShape(12.dp),
                            color = AccentWash,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Notes, null, tint = Accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Linked Note", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                            }
                        }
                    }
                    if (onOpenTranscript != null && isTranscribed) {
                        Surface(
                            onClick = onOpenTranscript,
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceSunk,
                            modifier = Modifier.weight(1f).border(1.dp, LineSoft, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.GraphicEq, null, tint = Ink, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Transcript", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            }
                        }
                    }
                }
            }

            // AI Summary Section
            if (!summaryText.isNullOrBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = LineSoft)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, null, tint = Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "AI SUMMARY",
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("AI Summary", summaryText))
                            Toast.makeText(context, "Summary copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, "Copy summary", tint = InkMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    summaryText,
                    fontSize = 14.sp,
                    color = Ink,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Full text / transcript
            if (!transcriptText.isNullOrBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = LineSoft)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isTranscribed) "FULL TRANSCRIPT" else "CONTENT",
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Inbox Content", transcriptText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, "Copy text", tint = InkMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    transcriptText,
                    fontSize = 14.sp,
                    color = Ink,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Detected Tasks & Decisions
            if (detectedTasks.isNotEmpty() || detectedDecisions.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = LineSoft)
                Text("DETECTED IN THIS ITEM", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detectedDecisions.forEach { d ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("📌", fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(d, fontSize = 13.5.sp, color = Ink, fontWeight = FontWeight.Medium)
                        }
                    }
                    detectedTasks.forEach { task ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(color = Accent)
                            Spacer(Modifier.width(8.dp))
                            Text(task, fontSize = 13.5.sp, color = Ink)
                        }
                    }
                }
            }

            // Proposed Filing Details
            if (filing != null) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = LineSoft)
                Text("PROPOSED FILING", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                Text(describe(filing), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 4.dp))
                filing.reason?.let { Text("Grounding: $it", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp)) }
            }

            // Actions in detail sheet
            FlowRow(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.status != InboxStatus.FILED.name) {
                    if (filing != null) {
                        Chip("Confirm Filing", selected = true, color = Accent) { onConfirm(filing) }
                    }
                    if (onQuickTranscribe != null && !isTranscribed && !isTranscribing) {
                        Chip(
                            if (item.kind == InboxKind.VIDEO.name) "Transcribe video" else "Transcribe",
                            selected = filing == null,
                            color = Accent
                        ) { onQuickTranscribe() }
                        if (onConfigureTranscribe != null) {
                            Chip("Configure…", selected = false, color = Ink) { onConfigureTranscribe() }
                        }
                    }
                    Chip(if (filing != null) "Change…" else "File to…", selected = filing == null && onQuickTranscribe == null, color = Ink) { onFileTo() }
                }
                if (onOpenNote != null && (isTranscribed || item.status == InboxStatus.FILED.name)) {
                    Chip("View Note", selected = false, color = Ink) { onOpenNote() }
                }
            }
        }
    }
}

@Composable
private fun ProvenanceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = InkMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun describe(f: Filing): String = when (f.action) {
    FilingAction.NOTE -> "Keep as a note" + (f.targetLabel?.let { " in $it" } ?: "")
    FilingAction.TASKS -> "Make ${f.tasks.size} ${if (f.tasks.size == 1) "task" else "tasks"}: " + f.tasks.joinToString("; ") + (f.targetLabel?.let { " ($it)" } ?: "")
    FilingAction.DECISION -> "Record decision: ${f.decision}" + (f.targetLabel?.let { " ($it)" } ?: "")
    FilingAction.CONTACT -> "Add ${f.contactName ?: f.contactEmail} to people"
}

private data class KindVisual(val icon: ImageVector, val label: String)

private fun kindMeta(kind: String): KindVisual = when (kind.uppercase()) {
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
