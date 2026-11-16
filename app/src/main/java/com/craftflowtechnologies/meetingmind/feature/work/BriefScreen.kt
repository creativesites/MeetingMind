package com.craftflowtechnologies.meetingmind.feature.work

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.export.ExportFormat
import com.craftflowtechnologies.meetingmind.core.ui.work.CiteChip
import com.craftflowtechnologies.meetingmind.core.ui.work.SourceChip
import com.craftflowtechnologies.meetingmind.core.ui.work.WhereItRan
import com.craftflowtechnologies.meetingmind.core.work.Brief
import com.craftflowtechnologies.meetingmind.core.work.BriefAudience
import com.craftflowtechnologies.meetingmind.core.work.BriefCopy
import com.craftflowtechnologies.meetingmind.core.work.BriefEvidence
import com.craftflowtechnologies.meetingmind.core.work.BriefExport
import com.craftflowtechnologies.meetingmind.core.work.BriefKind
import com.craftflowtechnologies.meetingmind.core.work.BriefLength
import com.craftflowtechnologies.meetingmind.core.work.BriefLine
import com.craftflowtechnologies.meetingmind.core.work.BriefOptions
import com.craftflowtechnologies.meetingmind.core.work.BriefSection
import com.craftflowtechnologies.meetingmind.core.work.BriefSentence
import com.craftflowtechnologies.meetingmind.core.work.BriefStatus
import com.craftflowtechnologies.meetingmind.core.work.BriefTarget
import com.craftflowtechnologies.meetingmind.core.work.BriefTone
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Intelligence Brief (docs/PLAN_PROFESSIONAL.md D5.3; spec §R5). The sections come from the
 * database and show at once; the model's two paragraphs fill in when they arrive, if there is a
 * model. Every citation opens the quote it stands on and plays it. There is no send action: a
 * brief is copied, exported or shared, and leaves only when the person does that.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BriefScreen(
    target: BriefTarget,
    viewModel: WorkViewModel,
    onNavigateBack: () -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    /** Lets the kind chips switch to another brief. Null: only the brief's own kind shows. */
    onOpenTarget: ((BriefTarget) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MM.colors
    var options by remember(target) { mutableStateOf(BriefOptions.DEFAULT) }
    var brief by remember(target) { mutableStateOf<Brief?>(null) }
    var writing by remember(target) { mutableStateOf(true) }
    var refresh by remember(target) { mutableStateOf(0) }
    var open by remember(target) { mutableStateOf(setOf<String>()) }
    var exportMenu by remember { mutableStateOf(false) }
    var shaping by remember { mutableStateOf(false) }

    LaunchedEffect(target, options, refresh) {
        writing = true
        // Structure first, from the database; then the prose, which needs a model and may take a while.
        brief = viewModel.briefs.build(target, options, ask = false)
        brief = viewModel.briefs.build(target, options, refresh = refresh > 0)
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
    fun toggle(key: String) { open = if (key in open) open - key else open + key }

    Scaffold(containerColor = c.background) { padding ->
        val b = brief
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("brief_screen"), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.ink) }
                    Text(target.kind.label.uppercase(), style = MM.type.overline, color = c.inkMuted, modifier = Modifier.weight(1f))
                    Pill("Shape") { shaping = true }
                }
                if (b == null) {
                    Text("Reading your record…", style = MM.type.secondary, color = c.inkMuted, modifier = Modifier.padding(20.dp))
                } else {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        KindChips(target, onOpenTarget)
                        Text(b.title, style = MM.type.title, color = c.ink, modifier = Modifier.padding(top = 14.dp).testTag("brief_title"))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val attention = b.status == BriefStatus.ATTENTION
                            Text(
                                b.status.label, style = MM.type.caption, color = if (attention) c.danger else c.accent,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (attention) c.dangerWash else c.accentWash).padding(horizontal = 10.dp, vertical = 4.dp).testTag("brief_status")
                            )
                            b.progress?.let { (done, total) -> Text("$done of $total commitments done", style = MM.type.secondary, color = c.inkSecondary) }
                        }
                        b.progress?.let { (done, total) ->
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { done.toFloat() / total }, color = c.accent, trackColor = c.surfaceSunk,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            )
                        }
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            WhereItRan(onDevice = b.confidential)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (b.confidential) "Confidential: built from material that stays on this phone." else "Built from confirmed records.",
                                style = MM.type.caption, color = c.inkMuted
                            )
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Copy", filled = true) { copy(context, b, options) }
                            Pill("Share") {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, b.title)
                                    putExtra(Intent.EXTRA_TEXT, BriefCopy.plain(b, options))
                                }, "Share brief"))
                            }
                            Box {
                                Pill("Export") { exportMenu = true }
                                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                                    DropdownMenuItem(text = { Text("PDF") }, onClick = { exportMenu = false; pdf.launch(fileName(b, ExportFormat.PDF)) })
                                    DropdownMenuItem(text = { Text("Word") }, onClick = { exportMenu = false; docx.launch(fileName(b, ExportFormat.DOCX)) })
                                    DropdownMenuItem(text = { Text("Markdown") }, onClick = { exportMenu = false; markdown.launch(fileName(b, ExportFormat.MARKDOWN)) })
                                }
                            }
                        }
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (writing) "Writing the summary…" else "For ${options.audience.label.lowercase()} · ${options.tone.label.lowercase()} · ${options.language}",
                                style = MM.type.caption, color = c.inkMuted, modifier = Modifier.weight(1f)
                            )
                            Pill(if (writing) "Writing…" else "Refresh") { if (!writing) refresh++ }
                        }
                    }
                }
            }
            if (b != null) {
                // The document: serif paragraphs, the way a brief reads when it is sent.
                if (b.hasProse) {
                    item { DocHeading("Executive picture") }
                    prose(b, b.executive, open, ::toggle, onOpenMeeting)
                } else if (writing && BriefSection.EXECUTIVE in options.sections) {
                    item { DocHeading("Executive picture") }
                    item { ProseSkeleton() }
                }
                section("What changed", b.changes, b, open, ::toggle, onOpenMeeting)
                section("Decisions", b.decisions, b, open, ::toggle, onOpenMeeting)
                section("You owe", b.youOwe, b, open, ::toggle, onOpenMeeting)
                section("They owe", b.theyOwe, b, open, ::toggle, onOpenMeeting)
                section("Risks", b.risks, b, open, ::toggle, onOpenMeeting)
                section("Open questions", b.questions, b, open, ::toggle, onOpenMeeting)
                section("Next 7 days", b.next7, b, open, ::toggle, onOpenMeeting)
                if (b.recommended.isNotEmpty()) {
                    item { DocHeading("Recommended next conversation") }
                    prose(b, b.recommended, open, ::toggle, onOpenMeeting)
                }
                // No model, or the prose failed: the sections are complete, the summary is a note with a retry.
                if (!writing && !b.hasProse && b.citedItemIds.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("brief_no_prose")) {
                        Text(
                            "No written summary. It needs a model, and everything above comes straight from your record.",
                            style = MM.type.secondary, color = c.inkSecondary
                        )
                        Pill("Try again") { refresh++ }
                    }
                }
                if (b.citedItemIds.isEmpty()) item {
                    Text(
                        "Nothing on record for this yet. Confirm a recording's Wrap-up and its decisions and promises land here.",
                        style = MM.type.secondary, color = c.inkMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                if (b.evidence.isNotEmpty()) item { DocHeading("Evidence", "${b.evidence.size}") }
                if (options.footer) item {
                    Text(BriefCopy.footer(b), style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).testTag("brief_footer"))
                }
            }
        }
    }

    if (shaping) {
        ShapeSheet(
            options = options,
            onChange = { options = it },
            onDismiss = { shaping = false }
        )
    }
}

/** The brief's kind, and the other kinds it can open. Only kinds that need no new id are offered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChips(target: BriefTarget, onOpenTarget: ((BriefTarget) -> Unit)?) {
    val kinds = listOfNotNull(target.kind, BriefKind.WEEKLY.takeIf { onOpenTarget != null && target.kind != BriefKind.WEEKLY })
    if (kinds.size < 2) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        kinds.forEach { kind ->
            Chip(kind.label, selected = kind == target.kind) { if (kind != target.kind) onOpenTarget?.invoke(BriefTarget.weekly) }
        }
    }
}

/** "Shape this brief": audience, tone, length, language, sections and footer. Changes the wording or the lines, never a fact. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShapeSheet(options: BriefOptions, onChange: (BriefOptions) -> Unit, onDismiss: () -> Unit) {
    val c = MM.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp).testTag("brief_shape_sheet")) {
            Text("Shape this brief", style = MM.type.title, color = c.ink)
            Text("Audience, tone and language change the written paragraphs. Length and sections choose which lines print. The facts never change.", style = MM.type.secondary, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
            ShapeGroup("Audience") {
                BriefAudience.entries.forEach { a -> Chip(a.label, options.audience == a) { onChange(options.copy(audience = a)) } }
            }
            ShapeGroup("Tone") {
                BriefTone.entries.forEach { t -> Chip(t.label, options.tone == t) { onChange(options.copy(tone = t)) } }
            }
            ShapeGroup("Length") {
                BriefLength.entries.forEach { l -> Chip(l.label, options.length == l) { onChange(options.copy(length = l)) } }
            }
            ShapeGroup("Language") {
                BriefOptions.LANGUAGES.forEach { lang -> Chip(lang, options.language == lang) { onChange(options.copy(language = lang)) } }
            }
            Text("SECTIONS", style = MM.type.overline, color = c.inkMuted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            BriefSection.entries.forEach { s ->
                val on = s in options.sections
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onChange(options.copy(sections = if (on) options.sections - s else options.sections + s))
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Text(s.label, style = MM.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    if (s == BriefSection.EXECUTIVE || s == BriefSection.RECOMMENDED) Text("AI", style = MM.type.caption, color = c.accent)
                }
            }
            Text(
                "The evidence appendix also turns off the quotes and playback behind each line.",
                style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp)
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Footer", style = MM.type.body, color = c.ink, modifier = Modifier.weight(1f))
                Switch(checked = options.footer, onCheckedChange = { onChange(options.copy(footer = it)) })
            }
            Text("Confidential material is never included.", style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
            Pill("Done", filled = true, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { onDismiss() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeGroup(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(label.uppercase(), style = MM.type.overline, color = MM.colors.inkMuted)
        FlowRow(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** Copies the brief with both a formatted (HTML) and a plain-text form, so either paste works. */
private fun copy(context: Context, b: Brief, options: BriefOptions) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newHtmlText(b.title, BriefCopy.plain(b, options), BriefCopy.html(b, options)))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun DocHeading(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MM.type.overline, color = MM.colors.inkMuted, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = MM.type.caption, color = MM.colors.inkMuted) }
    }
}

/** Three grey bars where the paragraphs will be: the sections are already on screen. */
@Composable
private fun ProseSkeleton() {
    val c = MM.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("brief_skeleton"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(0.96f).height(12.dp).clip(MM.radius.small).background(c.surfaceSunk))
        Box(Modifier.fillMaxWidth(0.88f).height(12.dp).clip(MM.radius.small).background(c.surfaceSunk))
        Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(MM.radius.small).background(c.surfaceSunk))
    }
}

private fun LazyListScope.section(
    title: String, lines: List<BriefLine>, b: Brief, open: Set<String>, toggle: (String) -> Unit, onOpenMeeting: (String, Long?) -> Unit
) {
    if (lines.isEmpty()) return
    item(key = "h-$title") { DocHeading(title, "${lines.size}") }
    items(lines.size, key = { "$title-${lines[it].itemId}-$it" }) { i ->
        val l = lines[i]
        val key = "$title-${l.itemId}-$i"
        BriefLineRow(
            line = l,
            evidence = b.evidence.firstOrNull { it.itemId == l.itemId },
            number = b.evidence.indexOfFirst { it.itemId == l.itemId }.let { if (it >= 0) it + 1 else null },
            expanded = key in open,
            onToggle = { toggle(key) },
            onOpenMeeting = onOpenMeeting
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.prose(
    b: Brief, sentences: List<BriefSentence>, open: Set<String>, toggle: (String) -> Unit, onOpenMeeting: (String, Long?) -> Unit
) {
    items(sentences.size, key = { "p-${sentences[it].text.hashCode()}-$it" }) { i ->
        val s = sentences[i]
        val key = "p-${s.text.hashCode()}-$i"
        val evidence = s.cites.mapNotNull { cite -> b.evidence.indexOfFirst { it.itemId == cite || it.evidenceId == cite }.takeIf { it >= 0 } }.distinct()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(s.text, style = MM.type.scripture, color = MM.colors.ink)
            if (evidence.isNotEmpty()) FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                evidence.forEach { n -> CiteChip(n + 1, onClick = { toggle(key) }) }
            }
            if (key in open) evidence.forEach { n -> EvidenceBlock(b.evidence[n], onOpenMeeting) }
        }
    }
}

@Composable
private fun BriefLineRow(line: BriefLine, evidence: BriefEvidence?, number: Int?, expanded: Boolean, onToggle: () -> Unit, onOpenMeeting: (String, Long?) -> Unit) {
    val c = MM.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 8.dp).testTag("brief_line")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(line.text, style = MM.type.body, color = c.ink)
                line.detail?.let { Text(it, style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 1.dp)) }
            }
            number?.let { CiteChip(it, onClick = onToggle) }
        }
        if (expanded) {
            if (evidence != null) EvidenceBlock(evidence, onOpenMeeting)
            else Text("No recording behind this one.", style = MM.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EvidenceBlock(e: BriefEvidence, onOpenMeeting: (String, Long?) -> Unit) {
    val c = MM.colors
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(MM.radius.card).background(c.surfaceSunk).padding(12.dp)) {
        Text("“${e.quote}”", style = MM.type.scripture.copy(fontStyle = FontStyle.Italic), color = c.inkSecondary)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                listOfNotNull(e.meetingTitle?.ifBlank { null }, e.meetingAt?.let { dayLabel(it) }).joinToString(" · "),
                style = MM.type.caption, color = c.inkMuted, modifier = Modifier.weight(1f)
            )
            if (e.meetingId != null) {
                val meetingId = e.meetingId
                SourceChip(label = formatTime(e.startMs ?: 0L), onClick = { onOpenMeeting(meetingId, e.startMs) })
            }
        }
    }
}
