package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.Filing
import com.craftflowtechnologies.meetingmind.core.work.FilingAction
import com.craftflowtechnologies.meetingmind.core.work.FilingCandidate
import com.craftflowtechnologies.meetingmind.core.work.FilingTarget
import com.craftflowtechnologies.meetingmind.core.work.InboxKind
import com.craftflowtechnologies.meetingmind.core.work.InboxStatus
import com.craftflowtechnologies.meetingmind.core.work.SharedPart
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.Success
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filter categories for Work Inbox triage.
 */
enum class InboxFilter(val label: String) {
    ALL("All"),
    NEEDS_ACTION("Action needed"),
    MEDIA("Media"),
    DOCS("Docs & links")
}

/**
 * The Work Inbox (docs/PLAN_PROFESSIONAL.md D7): what was shared into the app, each with clear
 * differentiation (source, date, time, media duration, transcription status), at most one proposed
 * filing to confirm or change, or a manual "File to…" picker. Nothing is filed until a tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onImportAudio: (String) -> Unit
) {
    val items by viewModel.inboxItems.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<InboxItemEntity?>(null) }
    var inspecting by remember { mutableStateOf<InboxItemEntity?>(null) }
    var activeFilter by remember { mutableStateOf(InboxFilter.ALL) }

    // The document scanner runs on the device and returns a PDF (and pictures); they land in the Inbox like a share.
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
    LaunchedEffect(items.map { it.id to it.status }) {
        items.filter { it.status == InboxStatus.NEW.name }.forEach { viewModel.proposeInbox(it) }
    }

    val filteredItems = remember(items, activeFilter) {
        when (activeFilter) {
            InboxFilter.ALL -> items
            InboxFilter.NEEDS_ACTION -> items.filter { it.status == InboxStatus.PROPOSED.name || it.transcriptionStatus == "PENDING" }
            InboxFilter.MEDIA -> items.filter { it.kind == InboxKind.AUDIO.name || it.kind == InboxKind.VIDEO.name }
            InboxFilter.DOCS -> items.filter { it.kind == InboxKind.PDF.name || it.kind == InboxKind.URL.name || it.kind == InboxKind.FILE.name }
        }
    }

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("work_inbox"),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, top = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text("Inbox", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scan() }) { Text("Scan") }
                }
                Text(
                    "Share text, links, PDFs, audio or video to MeetingMind and they land here. Nothing is filed until you confirm it.",
                    fontSize = 13.sp,
                    color = InkSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                // Quick triage filter tabs
                if (items.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InboxFilter.entries.forEach { f ->
                            val count = when (f) {
                                InboxFilter.ALL -> items.size
                                InboxFilter.NEEDS_ACTION -> items.count { it.status == InboxStatus.PROPOSED.name || it.transcriptionStatus == "PENDING" }
                                InboxFilter.MEDIA -> items.count { it.kind == InboxKind.AUDIO.name || it.kind == InboxKind.VIDEO.name }
                                InboxFilter.DOCS -> items.count { it.kind == InboxKind.PDF.name || it.kind == InboxKind.URL.name || it.kind == InboxKind.FILE.name }
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

            if (items.isEmpty()) {
                item { EmptyLine("Your inbox is empty. Try sharing a video, document, link or voice note to MeetingMind from another app.") }
            } else if (filteredItems.isEmpty()) {
                item { EmptyLine("No items matching '${activeFilter.label}'.") }
            }

            items(filteredItems, key = { it.id }) { item ->
                InboxCard(
                    item = item,
                    onConfirm = { filing ->
                        scope.launch {
                            val ok = viewModel.fileInbox(item, filing)
                            Toast.makeText(context, if (ok) "Filed" else "Couldn't file it", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onChoose = { picking = item },
                    onDismiss = { viewModel.dismissInbox(item) },
                    onTranscribe = item.uri?.takeIf { item.kind == InboxKind.AUDIO.name || item.kind == InboxKind.VIDEO.name }?.let { path ->
                        { onImportAudio(path) }
                    },
                    onOpenNote = item.meetingId?.let { mid -> { onOpenNote(mid) } },
                    onInspect = { inspecting = item }
                )
            }
        }
    }

    picking?.let { item ->
        FileToSheet(item, viewModel, onDismiss = { picking = null }) { filing ->
            picking = null
            scope.launch { viewModel.fileInbox(item, filing) }
        }
    }

    inspecting?.let { item ->
        InboxDetailSheet(
            item = item,
            viewModel = viewModel,
            onDismiss = { inspecting = null },
            onConfirm = { filing ->
                inspecting = null
                scope.launch {
                    val ok = viewModel.fileInbox(item, filing)
                    Toast.makeText(context, if (ok) "Filed" else "Couldn't file it", Toast.LENGTH_SHORT).show()
                }
            },
            onFileTo = {
                val itm = item
                inspecting = null
                picking = itm
            },
            onTranscribe = item.uri?.takeIf { item.kind == InboxKind.AUDIO.name || item.kind == InboxKind.VIDEO.name }?.let { path ->
                {
                    inspecting = null
                    onImportAudio(path)
                }
            },
            onOpenNote = item.meetingId?.let { mid ->
                {
                    inspecting = null
                    onOpenNote(mid)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(
    item: InboxItemEntity,
    onConfirm: (Filing) -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
    onTranscribe: (() -> Unit)?,
    onOpenNote: (() -> Unit)?,
    onInspect: () -> Unit
) {
    val filing = remember(item.proposedJson) { Filing.fromJson(item.proposedJson) }
    val formattedSource = remember(item.source) { formatSource(item.source) }
    val timeLabel = remember(item.createdAt) { relativeTimeLabel(item.createdAt) }
    val durationLabel = remember(item.mediaDurationMs) { formatMediaDuration(item.mediaDurationMs) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceSunk,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onInspect)
            .testTag("inbox_item")
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header: Kind badge, Source attribution pill, and relative timestamp
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KindBadge(item.kind)
                if (formattedSource != null) {
                    Spacer(Modifier.width(6.dp))
                    SourcePill(formattedSource)
                }
                Spacer(Modifier.weight(1f))
                Text(timeLabel, fontSize = 11.sp, color = InkMuted)
            }

            // Title and duration
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title?.ifBlank { null } ?: "Untitled",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (durationLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    DurationBadge(durationLabel)
                }
            }

            // Text / Transcript excerpt
            item.text?.takeIf { it.isNotBlank() && it != item.title }?.let { snippet ->
                Text(
                    snippet,
                    fontSize = 13.sp,
                    color = InkSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Post-transcription details and detection indicators
            if (item.transcriptionStatus == "COMPLETED") {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(13.dp))
                    Text("Transcribed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Success)
                    if (filing?.tasks?.isNotEmpty() == true) {
                        MetaPill("⚡ ${filing.tasks.size} ${if (filing.tasks.size == 1) "task" else "tasks"}")
                    }
                    if (filing?.decision != null) {
                        MetaPill("📌 Decision")
                    }
                }
            } else if (item.transcriptionStatus == "TRANSCRIBING") {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Transcribing audio/video…", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium)
                }
            }

            // Proposal Card
            if (filing != null) {
                Column(Modifier.padding(top = 10.dp)) {
                    Text("PROPOSED", fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                    Text(describe(filing), fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 2.dp))
                    filing.reason?.let { Text(it, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 1.dp)) }
                }
            } else {
                val hint = if (item.kind in listOf("AUDIO", "VIDEO") && item.transcriptionStatus != "COMPLETED") {
                    "Transcribe to detect tasks, decisions and notes automatically."
                } else {
                    "Nothing to go on yet. Choose where it belongs."
                }
                Text(hint, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
            }

            // Actions
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filing != null) {
                    Chip("Confirm", selected = true, color = Accent) { onConfirm(filing) }
                }
                Chip(if (filing != null) "Change…" else "File to…", selected = filing == null, color = Ink) { onChoose() }
                if (onTranscribe != null && item.transcriptionStatus == "PENDING") {
                    Chip(
                        if (item.kind == InboxKind.VIDEO.name) "Transcribe video" else "Transcribe",
                        selected = false,
                        color = Accent
                    ) { onTranscribe() }
                }
                if (onOpenNote != null && item.transcriptionStatus == "COMPLETED") {
                    Chip("View note", selected = false, color = Ink) { onOpenNote() }
                }
                Chip("Dismiss") { onDismiss() }
            }
        }
    }
}

@Composable
private fun KindBadge(kind: String) {
    val meta = kindMeta(kind)
    Surface(shape = RoundedCornerShape(6.dp), color = SurfaceBase) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(meta.icon, null, tint = InkSecondary, modifier = Modifier.size(12.dp))
            Text(meta.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkSecondary)
        }
    }
}

@Composable
private fun SourcePill(source: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = SurfaceBase) {
        Text(
            source,
            fontSize = 11.sp,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DurationBadge(duration: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = AccentWash) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Accent, modifier = Modifier.size(11.dp))
            Text(duration, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent)
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = SurfaceBase) {
        Text(text, fontSize = 11.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Ink else SurfaceSunk
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SurfaceBase else InkSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Inspection sheet displaying full item details, scrollable text/transcript, and filing actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InboxDetailSheet(
    item: InboxItemEntity,
    viewModel: WorkViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Filing) -> Unit,
    onFileTo: () -> Unit,
    onTranscribe: (() -> Unit)?,
    onOpenNote: (() -> Unit)?
) {
    val context = LocalContext.current
    val filing = remember(item.proposedJson) { Filing.fromJson(item.proposedJson) }
    val formattedSource = remember(item.source) { formatSource(item.source) }
    val timeLabel = remember(item.createdAt) { relativeTimeLabel(item.createdAt) }
    val durationLabel = remember(item.mediaDurationMs) { formatMediaDuration(item.mediaDurationMs) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KindBadge(item.kind)
                if (formattedSource != null) {
                    Spacer(Modifier.width(6.dp))
                    SourcePill(formattedSource)
                }
                Spacer(Modifier.weight(1f))
                Text(timeLabel, fontSize = 12.sp, color = InkMuted)
            }

            Text(
                item.title?.ifBlank { null } ?: "Untitled",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (durationLabel != null) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    DurationBadge(durationLabel)
                    if (item.transcriptionStatus == "COMPLETED") {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Transcribed", fontSize = 12.sp, color = Success, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Full text / transcript
            if (!item.text.isNullOrBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = LineSoft)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.transcriptionStatus == "COMPLETED") "TRANSCRIPT" else "CONTENT",
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Inbox Content", item.text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, "Copy text", tint = InkMuted, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    item.text,
                    fontSize = 14.sp,
                    color = Ink,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // Proposed Filing Details
            if (filing != null) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = LineSoft)
                Text("PROPOSED FILING", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                Text(describe(filing), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 4.dp))
                filing.reason?.let { Text("Grounding: $it", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp)) }
                if (filing.tasks.isNotEmpty()) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filing.tasks.forEach { task ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(color = Accent)
                                Spacer(Modifier.width(8.dp))
                                Text(task, fontSize = 13.sp, color = Ink)
                            }
                        }
                    }
                }
            }

            // Actions in detail sheet
            FlowRow(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filing != null) {
                    Chip("Confirm Filing", selected = true, color = Accent) { onConfirm(filing) }
                }
                Chip(if (filing != null) "Change…" else "File to…", selected = filing == null, color = Ink) { onFileTo() }
                if (onTranscribe != null && item.transcriptionStatus == "PENDING") {
                    Chip(
                        if (item.kind == InboxKind.VIDEO.name) "Transcribe video" else "Transcribe",
                        selected = false,
                        color = Accent
                    ) { onTranscribe() }
                }
                if (onOpenNote != null && item.transcriptionStatus == "COMPLETED") {
                    Chip("View Note", selected = false, color = Ink) { onOpenNote() }
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

internal fun formatSource(source: String?): String? {
    val s = source?.trim()?.ifBlank { null } ?: return null
    return when {
        s.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        s.contains("chrome", ignoreCase = true) -> "Chrome"
        s.contains("youtube", ignoreCase = true) -> "YouTube"
        s.contains("twitter", ignoreCase = true) || s.contains(".x.com", ignoreCase = true) -> "X"
        s.contains("slack", ignoreCase = true) -> "Slack"
        s.contains("photos", ignoreCase = true) -> "Photos"
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
