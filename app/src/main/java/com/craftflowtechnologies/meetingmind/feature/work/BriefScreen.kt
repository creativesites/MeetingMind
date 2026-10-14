package com.craftflowtechnologies.meetingmind.feature.work

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.export.ExportFormat
import com.craftflowtechnologies.meetingmind.core.work.Brief
import com.craftflowtechnologies.meetingmind.core.work.BriefEvidence
import com.craftflowtechnologies.meetingmind.core.work.BriefExport
import com.craftflowtechnologies.meetingmind.core.work.BriefLine
import com.craftflowtechnologies.meetingmind.core.work.BriefSentence
import com.craftflowtechnologies.meetingmind.core.work.BriefStatus
import com.craftflowtechnologies.meetingmind.core.work.BriefTarget
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.DangerWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Intelligence Brief (docs/PLAN_PROFESSIONAL.md D5.3). The sections come from the database and
 * show at once; the model's two pieces of prose fill in when they arrive, if there is a model.
 * Every line, and every citation in the prose, opens the quote it stands on and plays it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefScreen(target: BriefTarget, viewModel: WorkViewModel, onNavigateBack: () -> Unit, onOpenMeeting: (String, Long?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var brief by remember(target) { mutableStateOf<Brief?>(null) }
    var writing by remember(target) { mutableStateOf(true) }
    var refresh by remember(target) { mutableStateOf(0) }
    var open by remember(target) { mutableStateOf(setOf<String>()) }
    var exportMenu by remember { mutableStateOf(false) }

    LaunchedEffect(target, refresh) {
        writing = true
        // Structure first, from the database; then the prose, which needs a model and may take a while.
        brief = viewModel.briefs.build(target, ask = false)
        brief = viewModel.briefs.build(target, refresh = refresh > 0)
        writing = false
    }

    fun save(format: ExportFormat, uri: android.net.Uri?) {
        val b = brief ?: return
        if (uri == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { BriefExport.write(b, format, it) } }.isSuccess }
            Toast.makeText(context, if (ok) "Saved ${format.displayName}" else "Couldn't save it", Toast.LENGTH_SHORT).show()
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType)) { save(ExportFormat.PDF, it) }
    val docx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.DOCX.mimeType)) { save(ExportFormat.DOCX, it) }
    val markdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.MARKDOWN.mimeType)) { save(ExportFormat.MARKDOWN, it) }
    fun fileName(b: Brief, f: ExportFormat) = "${b.title.ifBlank { "Brief" }} - ${b.target.kind.label}.${f.extension}".replace('/', '-')

    Scaffold(containerColor = SurfaceBase) { padding ->
        val b = brief
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("brief_screen"), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text(target.kind.label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                }
                if (b == null) EmptyLine("Reading your record…")
                else Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(b.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val attention = b.status == BriefStatus.ATTENTION
                        Text(b.status.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (attention) Danger else Accent,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (attention) DangerWash else AccentWash).padding(horizontal = 10.dp, vertical = 4.dp).testTag("brief_status"))
                        b.progress?.let { (done, total) -> Text("$done of $total commitments done", fontSize = 12.sp, color = InkSecondary) }
                    }
                    b.progress?.let { (done, total) -> LinearProgressIndicator(progress = { done.toFloat() / total }, color = Accent, trackColor = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) }
                    if (b.confidential) Text("🔒 Built from material that stays on this phone. It prints a confidentiality line.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Share", filled = true) {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, b.title); putExtra(Intent.EXTRA_TEXT, BriefExport.markdown(b)) }, "Share brief"))
                        }
                        androidx.compose.foundation.layout.Box {
                            Pill("Export") { exportMenu = true }
                            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                                DropdownMenuItem(text = { Text("PDF") }, onClick = { exportMenu = false; pdf.launch(fileName(b, ExportFormat.PDF)) })
                                DropdownMenuItem(text = { Text("Word") }, onClick = { exportMenu = false; docx.launch(fileName(b, ExportFormat.DOCX)) })
                                DropdownMenuItem(text = { Text("Markdown") }, onClick = { exportMenu = false; markdown.launch(fileName(b, ExportFormat.MARKDOWN)) })
                            }
                        }
                        Pill(if (writing) "Writing…" else "Refresh") { if (!writing) refresh++ }
                    }
                }
            }
            if (b != null) {
                if (b.hasProse) {
                    item { WorkSectionTitle("Executive picture") }
                    prose(b, b.executive, open, { key -> open = if (key in open) open - key else open + key }, onOpenMeeting)
                }
                section("What changed", b.changes, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                section("Decisions", b.decisions, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                section("You owe", b.youOwe, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                section("They owe", b.theyOwe, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                section("Risks", b.risks, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                section("Open questions", b.questions, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                section("Next 7 days", b.next7, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                section("Decisions required", b.decisionsRequired, b, open, { open = if (it in open) open - it else open + it }, onOpenMeeting)
                if (b.recommended.isNotEmpty()) {
                    item { WorkSectionTitle("Recommended next conversation") }
                    prose(b, b.recommended, open, { key -> open = if (key in open) open - key else open + key }, onOpenMeeting)
                }
                if (!writing && !b.hasProse && b.citedItemIds.isNotEmpty()) item {
                    EmptyLine("No written summary: it needs a model, and everything above comes straight from your record.")
                }
                if (b.citedItemIds.isEmpty()) item { EmptyLine("Nothing on record for this yet. Confirm a recording's Wrap-up and its decisions and promises land here.") }
                if (b.evidence.isNotEmpty()) item { WorkSectionTitle("Evidence", "${b.evidence.size}") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String, lines: List<BriefLine>, b: Brief, open: Set<String>, toggle: (String) -> Unit, onOpenMeeting: (String, Long?) -> Unit
) {
    if (lines.isEmpty()) return
    item(key = "h-$title") { WorkSectionTitle(title, "${lines.size}") }
    items(lines.size, key = { "$title-${lines[it].itemId}-$it" }) { i ->
        val l = lines[i]
        val key = "$title-${l.itemId}-$i"
        BriefLineRow(l, b.evidence.firstOrNull { it.itemId == l.itemId }, b.evidence.indexOfFirst { it.itemId == l.itemId }.let { if (it >= 0) it + 1 else null }, key in open, { toggle(key) }, onOpenMeeting)
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.prose(b: Brief, sentences: List<BriefSentence>, open: Set<String>, toggle: (String) -> Unit, onOpenMeeting: (String, Long?) -> Unit) {
    items(sentences.size, key = { "p-${sentences[it].text.hashCode()}-$it" }) { i ->
        val s = sentences[i]
        val key = "p-${s.text.hashCode()}-$i"
        val evidence = s.cites.mapNotNull { c -> b.evidence.indexOfFirst { it.itemId == c || it.evidenceId == c }.takeIf { it >= 0 } }.distinct()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(s.text, fontSize = 15.sp, color = Ink, lineHeight = 21.sp)
            if (evidence.isNotEmpty()) FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                evidence.forEach { n -> Chip("[${n + 1}]", key in open, Accent) { toggle(key) } }
            }
            if (key in open) evidence.forEach { n -> EvidenceBlock(b.evidence[n], onOpenMeeting) }
        }
    }
}

@Composable
private fun BriefLineRow(line: BriefLine, evidence: BriefEvidence?, number: Int?, expanded: Boolean, onToggle: () -> Unit, onOpenMeeting: (String, Long?) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 8.dp).testTag("brief_line")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(line.text, fontSize = 15.sp, color = Ink)
                line.detail?.let { Text(it, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 1.dp)) }
            }
            number?.let { Text("[$it]", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(start = 8.dp)) }
        }
        if (expanded) {
            if (evidence != null) EvidenceBlock(evidence, onOpenMeeting)
            else Text("No recording behind this one.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EvidenceBlock(e: BriefEvidence, onOpenMeeting: (String, Long?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceSunk).padding(12.dp)) {
        Text("“${e.quote}”", fontSize = 14.sp, fontStyle = FontStyle.Italic, color = InkSecondary)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(listOfNotNull(e.meetingTitle?.ifBlank { null }, e.meetingAt?.let { dayLabel(it) }).joinToString(" · "), fontSize = 12.sp, color = InkMuted, modifier = Modifier.weight(1f))
            if (e.meetingId != null) PlayChip(e.startMs ?: 0L) { onOpenMeeting(e.meetingId, e.startMs) }
        }
    }
}
