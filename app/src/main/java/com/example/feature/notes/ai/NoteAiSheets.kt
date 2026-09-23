package com.example.feature.notes.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.ai.notes.CitedItem
import com.example.ai.notes.NoteAiJob
import com.example.ai.notes.NoteAiOutcome
import com.example.ai.notes.NoteAiStatus
import com.example.ai.notes.NoteAiTool
import com.example.ai.notes.RelatedNote
import com.example.ai.notes.SourcePassage
import com.example.core.model.Note
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk

/** The AI tools a note or notebook offers. [onRelated] is for a single note only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAiMenu(
    forNotebook: Boolean,
    onPick: (NoteAiTool) -> Unit,
    onRelated: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(if (forNotebook) "AI for this notebook" else "AI for this note", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                "Works only from what's written here, and shows where each point came from. Uses your AI setting — on this phone, or Gemini in Internet mode.",
                fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            MenuRow(Icons.Filled.Summarize, "Summarize", if (forNotebook) "The main points across these notes" else "The main points, added at the top") { onPick(NoteAiTool.SUMMARIZE) }
            MenuRow(Icons.Filled.CheckBox, "Find action items", "Tasks and follow-ups the notes actually state") { onPick(NoteAiTool.EXTRACT_ACTIONS) }
            MenuRow(Icons.Filled.QuestionAnswer, if (forNotebook) "Ask this notebook" else "Ask this note", "An answer from your notes, with where it's from") { onPick(NoteAiTool.ASK) }
            if (!forNotebook) {
                MenuRow(Icons.Filled.ViewAgenda, "Organize into sections", "Sort your own sentences under headings — Undo restores it") { onPick(NoteAiTool.ORGANIZE) }
            }
            onRelated?.let { MenuRow(Icons.Filled.Hub, "Related notes", "Notes that share verses, tags or ideas — instant, no AI needed", it) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = Accent.copy(alpha = 0.1f), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(19.dp)) }
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(subtitle, fontSize = 12.5.sp, color = InkSecondary)
        }
    }
}

@Composable
fun AskDialog(forNotebook: Boolean, onAsk: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (forNotebook) "Ask this notebook" else "Ask this note") },
        text = {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, minLines = 2,
                placeholder = { Text(if (forNotebook) "e.g. What did we decide about the budget?" else "e.g. What were the three points on grace?") },
                modifier = Modifier.fillMaxWidth().testTag("note_ai_question")
            )
        },
        confirmButton = { TextButton(enabled = q.isNotBlank(), onClick = { onAsk(q.trim()) }) { Text("Ask") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    onDiscard: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Text(
                    if (job.tool == NoteAiTool.ASK) job.question ?: "Ask" else job.tool.label,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            when (job.status) {
                NoteAiStatus.QUEUED, NoteAiStatus.RUNNING -> {
                    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        Text("Reading your notes…", color = InkSecondary, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text("You can close this — it carries on, and the result waits here.", fontSize = 12.5.sp, color = InkMuted)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) { Text("Stop") }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                }
                NoteAiStatus.FAILED, NoteAiStatus.CANCELLED -> {
                    Text(job.error ?: "Stopped.", color = InkSecondary, modifier = Modifier.padding(vertical = 18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDiscard) { Text("OK") } }
                }
                NoteAiStatus.SUCCEEDED -> {
                    val result = job.result
                    if (result == null) {
                        Text("The result couldn't be read.", color = InkSecondary, modifier = Modifier.padding(vertical = 18.dp))
                    } else {
                        Text(result.scope + (job.engine?.let { " · $it" } ?: ""), fontSize = 11.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        when (val o = result.outcome) {
                            is NoteAiOutcome.Points -> PointsResult(job, o.items, result.sources, primaryLabel, onPrimary, onShowSource, onDiscard, context)
                            is NoteAiOutcome.Answer -> AnswerResult(o, result.sources, onShowSource, onDiscard)
                            is NoteAiOutcome.Sections -> SectionsResult(o, result.sources, onApplyOrganized, onDiscard)
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
    onDiscard: () -> Unit,
    context: Context
) {
    val actions = job.tool == NoteAiTool.EXTRACT_ACTIONS
    var chosen by remember(items) { mutableStateOf(items.toSet()) }
    if (items.isEmpty()) {
        Text(if (actions) "No tasks or follow-ups are written here." else "There wasn't enough here to summarize.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDiscard) { Text("OK") } }
        return
    }
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(items) { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                if (actions) {
                    Icon(
                        if (item in chosen) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, contentDescription = null,
                        tint = if (item in chosen) Accent else InkMuted,
                        modifier = Modifier.size(22.dp).clickable { chosen = if (item in chosen) chosen - item else chosen + item }
                    )
                } else {
                    Text("•", color = Accent, fontSize = 16.sp, modifier = Modifier.width(22.dp))
                }
                Column(Modifier.padding(start = 6.dp).weight(1f)) {
                    Text(item.text, fontSize = 15.sp, lineHeight = 21.sp, color = Ink)
                    item.detail?.let { Text(it, fontSize = 12.5.sp, color = InkSecondary) }
                    Citations(item.sourceIds, sources, onShowSource)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard) { Text("Discard", color = InkSecondary) }
        TextButton(onClick = {
            val text = items.filter { !actions || it in chosen }.joinToString("\n") { (if (actions) "☐ " else "• ") + it.text + (it.detail?.let { d -> " — $d" } ?: "") }
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(job.tool.label, text))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy") }
        val selected = if (actions) items.filter { it in chosen } else items
        Surface(onClick = { if (selected.isNotEmpty()) onPrimary(selected) }, shape = RoundedCornerShape(50), color = if (selected.isEmpty()) Line else Ink, modifier = Modifier.testTag("note_ai_apply")) {
            Text(primaryLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
}

@Composable
private fun AnswerResult(answer: NoteAiOutcome.Answer, sources: Map<String, SourcePassage>, onShowSource: (SourcePassage) -> Unit, onDiscard: () -> Unit) {
    if (!answer.found) {
        Text("Your notes don't answer that. Nothing has been guessed.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
    } else {
        Text(answer.text, fontSize = 16.sp, lineHeight = 23.sp, color = Ink)
        Text("From", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        answer.sourceIds.mapNotNull { sources[it] }.forEach { p -> SourceRow(p, onShowSource) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDiscard) { Text("Done") } }
}

@Composable
private fun SectionsResult(o: NoteAiOutcome.Sections, sources: Map<String, SourcePassage>, onApply: ((NoteAiOutcome.Sections) -> Unit)?, onDiscard: () -> Unit) {
    if (o.sections.isEmpty()) {
        Text("Nothing here fitted the sections, so the note is left as it is.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDiscard) { Text("OK") } }
        return
    }
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(o.sections) { s ->
            Text(s.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            s.items.forEach { Text("• " + it.text, fontSize = 14.sp, lineHeight = 20.sp, color = InkSecondary, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
    Text("Anything not placed stays under “Other notes”. Undo restores the note exactly.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard) { Text("Discard", color = InkSecondary) }
        if (onApply != null) {
            Surface(onClick = { onApply(o) }, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.testTag("note_ai_apply")) {
                Text("Apply to note", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
    }
}

/** "From: Sunday service · Key points" chips under an item; tap to see or open the passage. */
@Composable
private fun Citations(ids: List<String>, sources: Map<String, SourcePassage>, onShowSource: (SourcePassage) -> Unit) {
    var open by remember { mutableStateOf<String?>(null) }
    val cited = ids.mapNotNull { sources[it] }
    if (cited.isEmpty()) return
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cited.take(3).forEachIndexed { i, p ->
            Surface(onClick = { open = if (open == p.id) null else p.id }, shape = RoundedCornerShape(50), color = SurfaceSunk, border = BorderStroke(1.dp, Line)) {
                Text(if (cited.size == 1) p.label.substringAfter(" · ", p.label) else "${i + 1}", fontSize = 11.sp, color = InkSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
    open?.let { id -> sources[id]?.let { SourceRow(it, onShowSource) } }
}

@Composable
private fun SourceRow(p: SourcePassage, onShowSource: (SourcePassage) -> Unit) {
    Surface(onClick = { onShowSource(p) }, shape = RoundedCornerShape(12.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                Text("Related notes", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.padding(top = 6.dp))
            when {
                related == null -> CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.padding(16.dp).size(22.dp))
                related.isEmpty() -> Text("Nothing else shares verses, tags or ideas with this note yet.", color = InkSecondary, modifier = Modifier.padding(vertical = 12.dp))
                else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(related, key = { it.first.id }) { (note, r) ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(note.id) }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                            Text(note.title.ifBlank { "Untitled" }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.reasons.joinToString(" · "), fontSize = 12.5.sp, color = InkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
