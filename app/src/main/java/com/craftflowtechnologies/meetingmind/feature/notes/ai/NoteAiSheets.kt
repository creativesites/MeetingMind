package com.craftflowtechnologies.meetingmind.feature.notes.ai

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

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
    val c = MM.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.xl)
                .padding(bottom = MM.space.xl)
                .navigationBarsPadding()
        ) {
            Text(
                if (forNotebook) "AI for this notebook" else "AI for this note",
                style = MM.type.title,
                color = c.ink
            )
            Text(
                "Works only from what's written here, and shows where each point came from. Uses your AI setting — on this phone, or Gemini in Internet mode.",
                style = MM.type.secondary,
                color = c.inkSecondary,
                modifier = Modifier.padding(top = MM.space.xs, bottom = MM.space.m)
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
    val c = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = MM.space.s, horizontal = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) c.accentWash else c.surfaceSunk,
            border = BorderStroke(MMSize.hairline, if (enabled) c.lineSoft else c.lineFaint),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) c.accent else c.inkMuted,
                    modifier = Modifier.size(MMSize.iconSmall)
                )
            }
        }
        Column(Modifier.padding(start = MM.space.m).weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = if (enabled) c.ink else c.inkMuted)
            Text(subtitle, style = MM.type.caption, color = c.inkSecondary)
        }
    }
}

@Composable
fun AskDialog(forNotebook: Boolean, onAsk: (String) -> Unit, onDismiss: () -> Unit) {
    val c = MM.colors
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        val trimmed = q.trim()
        if (trimmed.isNotEmpty()) onAsk(trimmed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = MM.radius.card,
        title = { Text(if (forNotebook) "Ask this notebook" else "Ask this note", style = MM.type.heading, color = c.ink) },
        text = {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                textStyle = MM.type.body,
                shape = MM.radius.card,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                trailingIcon = if (q.isNotEmpty()) {
                    {
                        IconButton(onClick = { q = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear question", tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
                        }
                    }
                } else null,
                placeholder = { Text(if (forNotebook) "e.g. What did we decide about the budget?" else "e.g. What were the three points on grace?", style = MM.type.body, color = c.inkMuted) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("note_ai_question")
            )
        },
        confirmButton = {
            TextButton(enabled = q.isNotBlank(), onClick = ::submit, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                Text("Ask", style = MM.type.bodyStrong, color = if (q.isNotBlank()) c.accent else c.inkMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                Text("Cancel", style = MM.type.bodyStrong, color = c.inkSecondary)
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
    val c = MM.colors
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .animateContentSize()
                .padding(horizontal = MM.space.xl)
                .padding(bottom = MM.space.l)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = c.accentWash,
                    border = BorderStroke(MMSize.hairline, c.lineSoft),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AiMark, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                    }
                }
                Text(
                    if (job.tool == NoteAiTool.ASK) job.question ?: "Ask" else job.tool.label,
                    style = MM.type.heading,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = MM.space.s).weight(1f)
                )
            }
            when (job.status) {
                NoteAiStatus.QUEUED, NoteAiStatus.RUNNING -> {
                    AiThinkingSkeleton(
                        label = "Reading your notes…",
                        sublabel = "You can close this — it carries on, and the result waits here.",
                        modifier = Modifier.padding(top = MM.space.m),
                        onStop = onCancel
                    )
                    Row(Modifier.fillMaxWidth().padding(top = MM.space.s), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                            Text("Keep in background", style = MM.type.bodyStrong, color = c.inkSecondary)
                        }
                    }
                }
                NoteAiStatus.FAILED, NoteAiStatus.CANCELLED -> {
                    AiRecoveryBanner(
                        message = job.error ?: "Stopped before finishing.",
                        modifier = Modifier.padding(top = MM.space.m),
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
                            modifier = Modifier.padding(top = MM.space.m),
                            onRetry = onRetry,
                            onDismiss = onDiscard,
                            dismissLabel = "Dismiss"
                        )
                    } else {
                        Text(
                            result.scope + (job.engine?.let { " · $it" } ?: ""),
                            style = MM.type.overline,
                            color = c.inkMuted,
                            modifier = Modifier.padding(top = MM.space.xs, bottom = MM.space.s)
                        )
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
    val c = MM.colors
    var chosen by remember(items) { mutableStateOf(items.toSet()) }
    var applying by remember(items) { mutableStateOf(false) }

    if (items.isEmpty()) {
        Text(
            if (actions) "No tasks or follow-ups are written here." else "There wasn't enough here to summarize.",
            style = MM.type.body,
            color = c.inkSecondary,
            modifier = Modifier.padding(vertical = MM.space.m)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                Text("OK", style = MM.type.bodyStrong, color = c.accent)
            }
        }
        return
    }
    if (actions) {
        Text(
            "${chosen.size} of ${items.size} selected",
            style = MM.type.caption,
            color = c.inkMuted,
            modifier = Modifier.padding(bottom = MM.space.xs)
        )
    }
    Surface(
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(MMSize.hairline, c.lineSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(horizontal = MM.space.m, vertical = MM.space.s)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            items(items) { item ->
                val checked = item in chosen
                val rowModifier = if (actions) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MMSize.minTouch)
                        .clip(MM.radius.small)
                        .toggleable(value = checked, role = Role.Checkbox) {
                            chosen = if (checked) chosen - item else chosen + item
                        }
                        .padding(vertical = MM.space.s, horizontal = MM.space.xs)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = MM.space.s, horizontal = MM.space.xs)
                }
                Row(rowModifier, verticalAlignment = Alignment.Top) {
                    if (actions) {
                        Icon(
                            if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (checked) c.accent else c.inkMuted,
                            modifier = Modifier.size(MMSize.iconSmall)
                        )
                    } else {
                        Text("•", color = c.accent, style = MM.type.bodyStrong, modifier = Modifier.width(MM.space.xl))
                    }
                    Column(Modifier.padding(start = MM.space.s).weight(1f)) {
                        Text(item.text, style = MM.type.body, color = c.ink)
                        item.detail?.let { Text(it, style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = MM.space.xs)) }
                        Citations(item.sourceIds, sources, onShowSource)
                    }
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
    Row(
        Modifier.fillMaxWidth().padding(top = MM.space.m),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDiscard, enabled = !applying, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
            Text("Discard", style = MM.type.bodyStrong, color = c.inkSecondary)
        }
        if (copyText.isNotBlank()) AiCopyButton(text = copyText)
        Surface(
            onClick = {
                if (selected.isNotEmpty() && !applying) {
                    applying = true
                    onPrimary(selected)
                }
            },
            enabled = selected.isNotEmpty() && !applying,
            shape = MM.radius.pill,
            color = if (selected.isEmpty() || applying) c.track else c.ink,
            modifier = Modifier.heightIn(min = MMSize.minTouch).semantics { role = Role.Button }.testTag("note_ai_apply")
        ) {
            Text(
                if (actions && selected.isEmpty()) "Select items" else primaryLabel,
                color = if (selected.isEmpty() || applying) c.inkMuted else c.onInk,
                style = MM.type.bodyStrong,
                modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s)
            )
        }
    }
}

@Composable
private fun AnswerResult(answer: NoteAiOutcome.Answer, sources: Map<String, SourcePassage>, onShowSource: (SourcePassage) -> Unit, onDiscard: () -> Unit) {
    val c = MM.colors
    Column(Modifier.verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite }) {
        if (!answer.found) {
            Text(
                "Your notes don't answer that. Nothing has been guessed.",
                style = MM.type.body,
                color = c.inkSecondary,
                modifier = Modifier.padding(vertical = MM.space.m)
            )
        } else {
            Surface(
                shape = MM.radius.card,
                color = c.surfaceRaised,
                border = BorderStroke(MMSize.hairline, c.lineSoft),
                modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs)
            ) {
                Column(Modifier.padding(MM.space.l)) {
                    Text(citedAnnotatedString(answer.text, c.accent), style = MM.type.body, color = c.ink)
                }
            }
            if (answer.sourceIds.isNotEmpty()) {
                Text(
                    "From",
                    style = MM.type.overline,
                    color = c.inkMuted,
                    modifier = Modifier.padding(top = MM.space.m, bottom = MM.space.xs)
                )
                answer.sourceIds.mapNotNull { sources[it] }.forEach { p -> SourceRow(p, onShowSource) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.s),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (answer.found && answer.text.isNotBlank()) AiCopyButton(text = answer.text)
            Surface(
                onClick = onDiscard,
                shape = MM.radius.pill,
                color = c.ink,
                modifier = Modifier.heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
            ) {
                Text(
                    "Done",
                    style = MM.type.bodyStrong,
                    color = c.onInk,
                    modifier = Modifier.padding(horizontal = MM.space.xl, vertical = MM.space.s)
                )
            }
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
    val c = MM.colors
    if (o.sections.isEmpty()) {
        Text("Nothing here fitted the sections, so the note is left as it is.", style = MM.type.body, color = c.inkSecondary, modifier = Modifier.padding(vertical = MM.space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                Text("OK", style = MM.type.bodyStrong, color = c.accent)
            }
        }
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

    Surface(
        shape = MM.radius.card,
        color = c.surfaceRaised,
        border = BorderStroke(MMSize.hairline, c.lineSoft),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(horizontal = MM.space.l, vertical = MM.space.m)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            items(o.sections) { s ->
                Text(s.title, style = MM.type.heading, color = c.ink, modifier = Modifier.padding(top = MM.space.m, bottom = MM.space.xs))
                s.items.forEachIndexed { idx, it ->
                    val prefix = if (isStudyGuide && (s.key.contains("question") || s.key.contains("points"))) "${idx + 1}. " else "• "
                    Text(prefix + it.text, style = MM.type.body, color = c.inkSecondary, modifier = Modifier.padding(vertical = MM.space.xs))
                }
            }
        }
    }
    Text(
        if (isStudyGuide) "Formatted for small groups. Copy to WhatsApp, share directly, or add to your note."
        else "Anything not placed stays under “Other notes”. Undo restores the note exactly.",
        style = MM.type.caption,
        color = c.inkMuted,
        modifier = Modifier.padding(top = MM.space.s)
    )
    Row(Modifier.fillMaxWidth().padding(top = MM.space.s), horizontalArrangement = Arrangement.spacedBy(MM.space.s, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDiscard, enabled = !applying, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
            Text("Discard", style = MM.type.bodyStrong, color = c.inkSecondary)
        }
        AiCopyButton(text = formattedText)
        if (isStudyGuide) {
            TextButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, formattedText)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share discussion guide"))
                },
                modifier = Modifier.heightIn(min = MMSize.minTouch)
            ) {
                Text("Share", style = MM.type.bodyStrong, color = c.accent)
            }
        }
        if (onApply != null) {
            Surface(
                onClick = { if (!applying) { applying = true; onApply(o) } },
                enabled = !applying,
                shape = MM.radius.pill,
                color = if (applying) c.track else c.ink,
                modifier = Modifier.heightIn(min = MMSize.minTouch).semantics { role = Role.Button }.testTag("note_ai_apply")
            ) {
                Text(
                    if (isStudyGuide) "Add to note" else "Apply to note",
                    color = if (applying) c.inkMuted else c.onInk,
                    style = MM.type.bodyStrong,
                    modifier = Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s)
                )
            }
        }
    }
}

/** One quiet line under an item — "Sunday service · Key points +2" — that opens the passages it came from. */
@Composable
private fun Citations(ids: List<String>, sources: Map<String, SourcePassage>, onShowSource: (SourcePassage) -> Unit) {
    val c = MM.colors
    var open by remember { mutableStateOf(false) }
    val cited = ids.mapNotNull { sources[it] }.distinctBy { it.id }
    if (cited.isEmpty()) return
    Row(
        Modifier
            .padding(top = MM.space.xs)
            .heightIn(min = 32.dp)
            .clip(MM.radius.small)
            .clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .padding(vertical = MM.space.xs, horizontal = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
        Text(
            cited.first().label.ifBlank { "Source" } + if (cited.size > 1) "  +${cited.size - 1}" else "",
            style = MM.type.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = MM.space.xs)
        )
    }
    if (open) {
        Column(Modifier.padding(top = MM.space.xs), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            cited.forEach { SourceRow(it, onShowSource) }
        }
    }
}

@Composable
private fun SourceRow(p: SourcePassage, onShowSource: (SourcePassage) -> Unit) {
    val c = MM.colors
    Surface(
        onClick = { onShowSource(p) },
        shape = MM.radius.small,
        color = c.surfaceSunk,
        border = BorderStroke(MMSize.hairline, c.lineSoft),
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }
    ) {
        Column(Modifier.padding(MM.space.m)) {
            Text(p.label, style = MM.type.overline, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.text, style = MM.type.secondary, color = c.inkSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
        }
    }
}

/** Notes related to this one, and why each matched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedNotesSheet(related: List<Pair<Note, RelatedNote>>?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val c = MM.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = MM.space.xl)
                .padding(bottom = MM.space.xl)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = c.accentWash,
                    border = BorderStroke(MMSize.hairline, c.lineSoft),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                    }
                }
                Text("Related notes", style = MM.type.heading, color = c.ink, modifier = Modifier.padding(start = MM.space.s))
            }
            Spacer(Modifier.padding(top = MM.space.s))
            when {
                related == null -> AiThinkingSkeleton("Finding related notes…", lines = listOf(0.84f, 0.58f), modifier = Modifier.padding(top = MM.space.s))
                related.isEmpty() -> Text("Nothing else shares verses, tags or ideas with this note yet.", style = MM.type.body, color = c.inkSecondary, modifier = Modifier.padding(vertical = MM.space.m))
                else -> LazyColumn(
                    Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    items(related, key = { it.first.id }) { (note, r) ->
                        Surface(
                            shape = MM.radius.card,
                            color = c.surfaceRaised,
                            border = BorderStroke(MMSize.hairline, c.lineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MMSize.minTouch)
                                    .clickable(role = Role.Button) { onOpen(note.id) }
                                    .padding(horizontal = MM.space.l, vertical = MM.space.m)
                            ) {
                                Text(note.title.ifBlank { "Untitled" }, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.reasons.joinToString(" · "), style = MM.type.caption, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                            }
                        }
                    }
                }
            }
        }
    }
}
