package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.work.Filing
import com.craftflowtechnologies.meetingmind.core.work.FilingAction
import com.craftflowtechnologies.meetingmind.core.work.FilingCandidate
import com.craftflowtechnologies.meetingmind.core.work.FilingTarget
import com.craftflowtechnologies.meetingmind.core.work.InboxKind
import com.craftflowtechnologies.meetingmind.core.work.InboxStatus
import com.craftflowtechnologies.meetingmind.core.work.SharedPart
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

/**
 * The Work Inbox (docs/PLAN_PROFESSIONAL.md D7): what was shared into the app, each with at most
 * one proposed filing to confirm or change, or a "File to…" picker when nothing could be proposed.
 * Nothing is filed until a tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(viewModel: WorkViewModel, onNavigateBack: () -> Unit, onOpenNote: (String) -> Unit, onImportAudio: (String) -> Unit) {
    val items by viewModel.inboxItems.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<InboxItemEntity?>(null) }

    // The document scanner runs on the device and returns a PDF (and pictures); they land in the Inbox like a share.
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri?.let { uri ->
                viewModel.addToInbox(listOf(SharedPart(InboxKind.PDF, uri = uri.toString(), title = "Scan", mime = "application/pdf")))
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
    LaunchedEffect(items.map { it.id to it.status }) { items.filter { it.status == InboxStatus.NEW.name }.forEach { viewModel.proposeInbox(it) } }

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("work_inbox"), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text("Inbox", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scan() }) { Text("Scan") }
                }
                Text("Share text, links, PDFs, audio or pictures to MeetingMind and they land here. Nothing is filed until you confirm it.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            if (items.isEmpty()) item { EmptyLine("Your inbox is empty. Try sharing a page, a document or a voice note to MeetingMind from another app.") }
            items(items, key = { it.id }) { item ->
                InboxCard(
                    item,
                    onConfirm = { filing ->
                        scope.launch {
                            val ok = viewModel.fileInbox(item, filing)
                            Toast.makeText(context, if (ok) "Filed" else "Couldn't file it", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onChoose = { picking = item }, onDismiss = { viewModel.dismissInbox(item) },
                    onImportAudio = item.uri?.takeIf { item.kind == InboxKind.AUDIO.name }?.let { path -> { onImportAudio(path) } }
                )
            }
        }
    }
    picking?.let { item -> FileToSheet(item, viewModel, onDismiss = { picking = null }) { filing -> picking = null; scope.launch { viewModel.fileInbox(item, filing) } } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(item: InboxItemEntity, onConfirm: (Filing) -> Unit, onChoose: () -> Unit, onDismiss: () -> Unit, onImportAudio: (() -> Unit)?) {
    val filing = remember(item.proposedJson) { Filing.fromJson(item.proposedJson) }
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("inbox_item")) {
        Column(Modifier.padding(14.dp)) {
            Text(item.kind.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
            Text(item.title?.ifBlank { null } ?: "Untitled", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.text?.takeIf { it.isNotBlank() && it != item.title }?.let { Text(it, fontSize = 13.sp, color = InkSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            if (filing != null) {
                Column(Modifier.padding(top = 10.dp)) {
                    Text("PROPOSED", fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                    Text(describe(filing), fontSize = 14.sp, color = Ink)
                    filing.reason?.let { Text(it, fontSize = 12.sp, color = InkMuted) }
                }
            } else Text("Nothing to go on yet. Choose where it belongs.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (filing != null) Chip("Confirm", true) { onConfirm(filing) }
                Chip(if (filing != null) "Change…" else "File to…", filing == null, Ink) { onChoose() }
                onImportAudio?.let { Chip("Transcribe it") { it() } }
                Chip("Dismiss") { onDismiss() }
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
