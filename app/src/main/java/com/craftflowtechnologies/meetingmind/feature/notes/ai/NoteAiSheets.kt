package com.craftflowtechnologies.meetingmind.feature.notes.ai

import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ai.notes.CitedItem
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiJob
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiOutcome
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiStatus
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiTool
import com.craftflowtechnologies.meetingmind.ai.notes.RelatedNote
import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiRecoveryBanner
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.core.ui.mm.citedAnnotatedString
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

/** The AI tools a note or notebook offers. [onRelated] is for a single note only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAiMenu(
    forNotebook: Boolean,
    isFaithOrSermon: Boolean = false,
    isPolishing: Boolean = false,
    onPick: (NoteAiTool) -> Unit,
    onRelated: (() -> Unit)?,
    onPolishNote: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBase
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(if (forNotebook) "AI for this notebook" else "AI for this note", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                "Works only from what's written here, and shows where each point came from. Uses your AI setting — on this phone, or Gemini in Internet mode.",
                fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            MenuRow(Icons.Filled.Summarize, "Summarize", if (forNotebook) "The main points across these notes" else "The main points, added at the top") { onPick(NoteAiTool.SUMMARIZE) }
            MenuRow(Icons.Filled.CheckBox, "Find action items", "Tasks and follow-ups the notes actually state") { onPick(NoteAiTool.EXTRACT_ACTIONS) }
            MenuRow(Icons.Filled.QuestionAnswer, if (forNotebook) "Ask this notebook" else "Ask this note", "An answer from your notes, with where it's from") { onPick(NoteAiTool.ASK) }
            if (!forNotebook) {
                onPolishNote?.let {
                    MenuRow(
                        AiMark,
                        if (isPolishing) "Polishing note…" else "Polish whole note",
                        if (isPolishing) "Formatting into clean headings, callouts, and takeaways" else "Format into clean headings, callouts, lists, and takeaways — Undo restores it",
                        enabled = !isPolishing,
                        onClick = it
                    )
                }
                if (isFaithOrSermon) {
                    MenuRow(Icons.Filled.MenuBook, "Discussion guide", "Discussion questions, application & prayer points for small group") { onPick(NoteAiTool.STUDY_GUIDE) }
                }
                MenuRow(Icons.Filled.ViewAgenda, "Organize into sections", "Sort your own sentences under headings — Undo restores it") { onPick(NoteAiTool.ORGANIZE) }
            }
            onRelated?.let { MenuRow(Icons.Filled.Hub, "Related notes", "Notes that share verses, tags or ideas — instant, no AI needed", onClick = it) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = Accent.copy(alpha = if (enabled) 0.1f else 0.05f), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = if (enabled) Accent else InkMuted, modifier = Modifier.size(19.dp)) }
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (enabled) Ink else InkMuted)
            Text(subtitle, fontSize = 12.5.sp, color = InkSecondary)
        }
    }
}

@Composable
fun AskDialog(forNotebook: Boolean, onAsk: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        val trimmed = q.trim()
        if (trimmed.isNotEmpty()) onAsk(trimmed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        title = { Text(if (forNotebook) "Ask this notebook" else "Ask this note") },
        text = {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                trailingIcon = if (q.isNotEmpty()) {
                    {
                        IconButton(onClick = { q = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear question", tint = InkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                } else null,
                placeholder = { Text(if (forNotebook) "e.g. What did we decide about the budget?" else "e.g. What were the three points on grace?") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("note_ai_question")
            )
        },
        confirmButton = {
            TextButton(enabled = q.isNotBlank(), onClick = ::submit, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                Text("Ask")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                Text("Cancel")
            }
        }
    )
}

/**
 * A run in progress, or its result. The result is shown for the person to use or leave — it is
 * never written into a note without them choosing to.
 *
 * @param onShowSource jump to a cited passage (a block in this note, or another note)
 * @param primaryLabel what the main button does with the result ("Add to note", "Save as note")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAiResultSheet(
    job: NoteAiJob,
    primaryLabel: String,
    onPrimary: (List<CitedItem>) -> Unit,
    onApplyOrganized: ((NoteAiOutcome.Sections) -> Unit)?,
    onShowSource: (SourcePassage) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBase
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .animateContentSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AiMark, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Text(
                    if (job.tool == NoteAiTool.ASK) job.question ?: "Ask" else job.tool.label,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f)
                )
            }
            when (job.status) {
                NoteAiStatus.QUEUED, NoteAiStatus.RUNNING -> {
                    AiThinkingSkeleton(
                        label = "Reading your notes…",
                        sublabel = "You can close this — it carries on, and the result waits here.",
                        modifier = Modifier.padding(top = 14.dp),
                        onStop = onCancel
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MMSize.minTouch)) { Text("Keep in background") }
                    }
                }
                NoteAiStatus.FAILED, NoteAiStatus.CANCELLED -> {
                    AiRecoveryBanner(
                        message = job.error ?: "Stopped before finishing.",
                        modifier = Modifier.padding(top = 14.dp),
                        onRetry = onRetry,
                        onDismiss = onDiscard,
                        dismissLabel = "Dismiss"
                    )
                }
                NoteAiStatus.SUCCEEDED -> {
                    val result = job.result
                    if (result == null) {
                        AiRecoveryBanner(
                            message = "The result couldn't be read.",
                            modifier = Modifier.padding(top = 14.dp),
                            onRetry = onRetry,
                            onDismiss = onDiscard,
                            dismissLabel = "Dismiss"
                        )
                    } else {
                        Text(result.scope + (job.engine?.let { " · $it" } ?: ""), fontSize = 11.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        when (val o = result.outcome) {
                            is NoteAiOutcome.Points -> PointsResult(job, o.items, result.sources, primaryLabel, onPrimary, onShowSource, onDiscard)
                            is NoteAiOutcome.Answer -> AnswerResult(o, result.sources, onShowSource, onDiscard)
                            is NoteAiOutcome.Sections -> SectionsResult(job, o, onApplyOrganized, onDiscard, context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsResult(
    job: NoteAiJob,
    items: List<CitedItem>,
    sources: Map<String, SourcePassage>,
    primaryLabel: String,
    onPrimary: (List<CitedItem>) -> Unit,
    onShowSource: (SourcePassage) -> Unit,
    onDiscard: () -> Unit
) {
    val actions = job.tool == NoteAiTool.EXTRACT_ACTIONS
    var chosen by remember(items) { mutableStateOf(items.toSet()) }
    var applying by remember(items) { mutableStateOf(false) }

    if (items.isEmpty()) {
        Text(if (actions) "No tasks or follow-ups are written here." else "There wasn't enough here to summarize.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDiscard) { Text("OK") } }
        return
    }
    if (actions) {
        Text(
            "${chosen.size} of ${items.size} selected",
            fontSize = 12.sp,
            color = InkMuted,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
    LazyColumn(Modifier.heightIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        items(items) { item ->
            val checked = item in chosen
            val rowModifier = if (actions) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MMSize.minTouch)
                    .clip(RoundedCornerShape(10.dp))
                    .toggleable(value = checked, role = Role.Checkbox) {
                        chosen = if (checked) chosen - item else chosen + item
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            } else {
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
            }
            Row(rowModifier, verticalAlignment = Alignment.Top) {
                if (actions) {
                    Icon(
                        if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (checked) Accent else InkMuted,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("•", color = Accent, fontSize = 16.sp, modifier = Modifier.width(22.dp))
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(item.text, fontSize = 15.sp, lineHeight = 21.sp, color = Ink)
                    item.detail?.let { Text(it, fontSize = 12.5.sp, color = InkSecondary) }
                    Citations(item.sourceIds, sources, onShowSource)
                }
            }
        }
    }
    val selected = if (actions) items.filter { it in chosen } else items
    val copyText = remember(selected, actions) {
        selected.joinToString("\n") {
            (if (actions) "☐ " else "• ") + it.text + (it.detail?.let { d -> " — $d" } ?: "")
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard, enabled = !applying) { Text("Discard", color = InkSecondary) }
        if (copyText.isNotBlank()) AiCopyButton(text = copyText)
        Surface(
            onClick = {
                if (selected.isNotEmpty() && !applying) {
                    applying = true
                    onPrimary(selected)
                }
            },
            enabled = selected.isNotEmpty() && !applying,
            shape = RoundedCornerShape(50),
            color = if (selected.isEmpty() || applying) Line else Ink,
            modifier = Modifier.heightIn(min = 42.dp).semantics { role = Role.Button }.testTag("note_ai_apply")
        ) {
            Text(
                if (actions && selected.isEmpty()) "Select items" else primaryLabel,
                color = OnInk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AnswerResult(answer: NoteAiOutcome.Answer, sources: Map<String, SourcePassage>, onShowSource: (SourcePassage) -> Unit, onDiscard: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite }) {
        if (!answer.found) {
            Text("Your notes don't answer that. Nothing has been guessed.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Text(citedAnnotatedString(answer.text, Accent), fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
            if (answer.sourceIds.isNotEmpty()) {
                Text("From", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                answer.sourceIds.mapNotNull { sources[it] }.forEach { p -> SourceRow(p, onShowSource) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (answer.found && answer.text.isNotBlank()) AiCopyButton(text = answer.text)
            TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = MMSize.minTouch)) { Text("Done") }
        }
    }
}

@Composable
private fun SectionsResult(
    job: NoteAiJob,
    o: NoteAiOutcome.Sections,
    onApply: ((NoteAiOutcome.Sections) -> Unit)?,
    onDiscard: () -> Unit,
    context: Context
) {
    if (o.sections.isEmpty()) {
        Text("Nothing here fitted the sections, so the note is left as it is.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDiscard) { Text("OK") } }
        return
    }
    var applying by remember(o) { mutableStateOf(false) }
    val isStudyGuide = job.tool == NoteAiTool.STUDY_GUIDE
    val formattedText = remember(o, isStudyGuide, job.question) {
        if (isStudyGuide) {
            com.craftflowtechnologies.meetingmind.ai.notes.StudyGuideFormatter.toWhatsApp(
                job.question ?: "Small-Group Discussion Guide",
                o.sections
            )
        } else {
            o.sections.joinToString("\n\n") { s ->
                s.title + "\n" + s.items.joinToString("\n") { "• " + it.text }
            }
        }
    }

    LazyColumn(Modifier.heightIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        items(o.sections) { s ->
            Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            s.items.forEachIndexed { idx, it ->
                val prefix = if (isStudyGuide && (s.key.contains("question") || s.key.contains("points"))) "${idx + 1}. " else "• "
                Text(prefix + it.text, fontSize = 14.sp, lineHeight = 20.sp, color = InkSecondary, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
    Text(
        if (isStudyGuide) "Formatted for small groups. Copy to WhatsApp, share directly, or add to your note."
        else "Anything not placed stays under “Other notes”. Undo restores the note exactly.",
        fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 10.dp)
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard, enabled = !applying) { Text("Discard", color = InkSecondary) }
        AiCopyButton(text = formattedText)
        if (isStudyGuide) {
            TextButton(onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, formattedText)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share discussion guide"))
            }) { Text("Share") }
        }
        if (onApply != null) {
            Surface(
                onClick = { if (!applying) { applying = true; onApply(o) } },
                enabled = !applying,
                shape = RoundedCornerShape(50),
                color = if (applying) Line else Ink,
                modifier = Modifier.heightIn(min = 42.dp).semantics { role = Role.Button }.testTag("note_ai_apply")
            ) {
                Text(if (isStudyGuide) "Add to note" else "Apply to note", color = OnInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/** One quiet line under an item — "Sunday service · Key points +2" — that opens the passages it came from. */
@Composable
private fun Citations(ids: List<String>, sources: Map<String, SourcePassage>, onShowSource: (SourcePassage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cited = ids.mapNotNull { sources[it] }.distinctBy { it.id }
    if (cited.isEmpty()) return
    Row(
        Modifier
            .padding(top = 4.dp)
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = InkMuted, modifier = Modifier.size(15.dp))
        Text(
            cited.first().label.ifBlank { "Source" } + if (cited.size > 1) "  +${cited.size - 1}" else "",
            fontSize = 12.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp)
        )
    }
    if (open) cited.forEach { SourceRow(it, onShowSource) }
}

@Composable
private fun SourceRow(p: SourcePassage, onShowSource: (SourcePassage) -> Unit) {
    Surface(
        onClick = { onShowSource(p) },
        shape = RoundedCornerShape(12.dp),
        color = SurfaceSunk,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics { role = Role.Button }
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(p.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.text, fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Notes related to this one, and why each matched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedNotesSheet(related: List<Pair<Note, RelatedNote>>?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBase
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Text("Related notes", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.padding(top = 6.dp))
            when {
                related == null -> AiThinkingSkeleton("Finding related notes…", lines = listOf(0.84f, 0.58f), modifier = Modifier.padding(top = 8.dp))
                related.isEmpty() -> Text("Nothing else shares verses, tags or ideas with this note yet.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
                else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(related, key = { it.first.id }) { (note, r) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MMSize.minTouch)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { onOpen(note.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text(note.title.ifBlank { "Untitled" }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.reasons.joinToString(" · "), fontSize = 12.5.sp, color = InkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
