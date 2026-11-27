package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.aireport.AiFeature
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource
import com.craftflowtechnologies.meetingmind.core.aireport.HiddenAfterReportLine
import com.craftflowtechnologies.meetingmind.core.aireport.ReportAiAction
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ai.learning.AnswerKind
import com.craftflowtechnologies.meetingmind.ai.learning.AskPassage
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.AllowanceUsedUpPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learning.AskScopeKind
import com.craftflowtechnologies.meetingmind.feature.learning.AskTurn
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.feature.learning.components.CitationSheet
import com.craftflowtechnologies.meetingmind.feature.learning.components.timestamp
import com.craftflowtechnologies.meetingmind.ui.theme.LearningTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * Ask Learning: answers from the student's own sources with numbered citations. "Not in your
 * lecture" is said plainly; general knowledge only on request and always labelled (decision D10).
 *
 * Call [LearnViewModel.openAsk] before showing it. [sessionId] is null for "all sessions".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskLearningSheet(
    viewModel: LearnViewModel,
    sessionId: String?,
    hasCourse: Boolean,
    concepts: List<LearningConcept>,
    onDismiss: () -> Unit,
    onStartPractice: () -> Unit,
    onShowInTranscript: ((LearningEvidence) -> Unit)? = null,
    initialQuestion: String? = null,
    /** Opens Plans from the allowance panel. Null when this build cannot sell plans, or the caller has no Plans route. */
    onSeePlans: (() -> Unit)? = null
) {
    val turns by viewModel.askTurns.collectAsState()
    val scope by viewModel.askScope.collectAsState()
    val practiceError by viewModel.practiceError.collectAsState()
    var draft by rememberSaveable { mutableStateOf(initialQuestion.orEmpty()) }
    var citation by remember { mutableStateOf<Pair<AskPassage, String>?>(null) }
    val listState = rememberLazyListState()
    val busy = turns.any { it.pending }
    // For "Report this": the session's note and recording, used on the phone only to keep private material from uploading.
    val detail by viewModel.activeSessionDetail.collectAsState()
    val sessionNoteId = detail?.takeIf { sessionId != null && it.session.id == sessionId }?.session?.noteId
    val sessionMeetingId = if (sessionNoteId != null) viewModel.activeSessionMeetingId.collectAsState().value else null

    LaunchedEffect(turns.size, turns.lastOrNull()?.pending, turns.lastOrNull()?.general) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MM.colors.surfaceRaised,
        shape = MM.radius.sheet
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .testTag("ask_learning")
        ) {
            Column(Modifier.padding(horizontal = LearningTheme.readingGutter), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text("Ask Learning", style = MM.type.title, color = MM.colors.ink)
                Text(
                    "Answers come from ${viewModel.askScopeLabel(scope)}, with the moment each point was said.",
                    style = MM.type.caption, color = MM.colors.inkSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    AskScopeKind.entries
                        .filter { k -> (k != AskScopeKind.SESSION || sessionId != null) && (k != AskScopeKind.COURSE || (sessionId != null && hasCourse)) }
                        .forEach { k ->
                            MMChip(label = k.label, selected = k == scope, onClick = { viewModel.setAskScope(k) }, modifier = Modifier.testTag("ask_scope_${k.name.lowercase()}"))
                        }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = LearningTheme.readingGutter, vertical = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.l)
            ) {
                if (turns.isEmpty()) item(key = "suggest") {
                    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        Text("TRY ASKING", style = MM.type.overline, color = MM.colors.inkMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            suggestions(concepts).forEach { s ->
                                MMChip(label = s, selected = false, onClick = { viewModel.ask(s) })
                            }
                        }
                    }
                }
                items(turns, key = { it.id }) { turn ->
                    TurnView(
                        turn = turn,
                        noteId = sessionNoteId,
                        meetingId = sessionMeetingId,
                        onCite = { p -> citation = p to p.sourceTitle },
                        onGeneral = { viewModel.askGenerally(turn.id) },
                        onQuiz = { viewModel.quizFromAnswer(turn) { onDismiss(); onStartPractice() } },
                        onSeePlans = onSeePlans
                    )
                }
                if (practiceError != null) item(key = "perr") { StatusLine(StatusKind.Info, practiceError!!) }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Ask about ${viewModel.askScopeLabel(scope)}", style = MM.type.body, color = MM.colors.inkMuted, maxLines = 1) },
                    textStyle = MM.type.body.copy(color = MM.colors.ink),
                    maxLines = 4,
                    shape = MM.radius.card,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!busy && draft.isNotBlank()) { viewModel.ask(draft); draft = "" } }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MM.colors.accent, unfocusedBorderColor = MM.colors.line, cursorColor = MM.colors.accent),
                    modifier = Modifier.weight(1f).testTag("ask_input")
                )
                IconButton(
                    onClick = { viewModel.ask(draft); draft = "" },
                    enabled = !busy && draft.isNotBlank(),
                    modifier = Modifier.size(MMSize.minTouch).clip(MM.radius.pill)
                        .background(if (!busy && draft.isNotBlank()) MM.colors.accent else MM.colors.track)
                        .testTag("ask_send")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask", tint = if (!busy && draft.isNotBlank()) MM.colors.onAccent else MM.colors.inkMuted)
                }
            }
        }
    }

    citation?.let { (p, title) ->
        CitationSheet(
            evidence = p.evidence,
            sourceTitle = title,
            onDismiss = { citation = null },
            resolveAudio = viewModel::citationAudioFor,
            onShowInTranscript = onShowInTranscript?.takeIf { p.evidence.segmentIds.isNotEmpty() }?.let { show ->
                { citation = null; onDismiss(); show(p.evidence) }
            }
        )
    }
}

/** A few starting questions from the session's own concepts. */
private fun suggestions(concepts: List<LearningConcept>): List<String> {
    val names = concepts.filter { !it.isDismissed }.map { it.name }.take(3)
    val out = mutableListOf<String>()
    names.firstOrNull()?.let { out += "What is $it?" }
    if (names.size >= 2) out += "How is ${names[0]} different from ${names[1]}?"
    names.getOrNull(2)?.let { out += "Why does $it matter?" }
    if (out.isEmpty()) out += "What were the main points?"
    return out
}

@Composable
private fun TurnView(
    turn: AskTurn,
    noteId: String?,
    meetingId: String?,
    onCite: (AskPassage) -> Unit,
    onGeneral: () -> Unit,
    onQuiz: () -> Unit,
    onSeePlans: (() -> Unit)?
) {
    // Hidden after the student reported it (Play AI-content policy); "Show" brings it back.
    var hidden by rememberSaveable(turn.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                turn.question,
                style = MM.type.body,
                color = MM.colors.ink,
                modifier = Modifier.widthIn(max = 300.dp).clip(MM.radius.card).background(MM.colors.accentWash)
                    .padding(horizontal = MM.space.m, vertical = MM.space.s)
            )
        }
        when {
            turn.pending -> Text(
                "Searching ${turn.scopeLabel}…",
                style = MM.type.caption, color = MM.colors.inkSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("ask_searching")
            )
            hidden -> HiddenAfterReportLine(onShow = { hidden = false })
            turn.answer != null -> AnswerView(turn.answer, turn.scopeLabel, onCite)
        }
        if (hidden) return@Column
        turn.allowance?.let { AllowanceUsedUpPanel(it.resetsAtMs, onSeePlans) }
        turn.error?.let { StatusLine(StatusKind.Info, it) }
        if (turn.madeOnPhone && turn.answer != null) Text("Made on this phone", style = MM.type.caption, color = MM.colors.inkSecondary)
        val a = turn.answer
        if (a != null && a.kind == AnswerKind.NOT_IN_SOURCE && turn.general == null) {
            if (turn.generalPending) {
                Text("Writing a general answer…", style = MM.type.caption, color = MM.colors.inkSecondary)
            } else {
                TextAction("Answer from general knowledge", onClick = onGeneral, modifier = Modifier.testTag("ask_general"))
            }
        }
        turn.general?.let { g ->
            Column(
                Modifier.fillMaxWidth().clip(MM.radius.card).background(MM.colors.surfaceSunk).padding(MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Text("GENERAL KNOWLEDGE · NOT FROM YOUR LECTURE", style = MM.type.overline, color = LearningTheme.colors.clay)
                Text(g.text, style = LearningTheme.type.reading, color = MM.colors.ink)
            }
        }
        // Only AI-written words can be reported: a quoted (offline) answer is the lecture itself.
        val aiWritten = listOfNotNull(a?.takeIf { !it.extractive }?.text, turn.general?.text).joinToString("\n\n")
        if ((a != null && a.kind == AnswerKind.FROM_SOURCE) || aiWritten.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (a != null && a.kind == AnswerKind.FROM_SOURCE) {
                    TextAction("Quiz me on this", onClick = onQuiz, modifier = Modifier.testTag("ask_quiz"))
                }
                Spacer(Modifier.weight(1f))
                if (aiWritten.isNotBlank()) {
                    ReportAiAction(
                        source = { AiReportSource(AiFeature.ASK_LEARNING, aiWritten, noteId = noteId, meetingId = meetingId) },
                        onHide = { hidden = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerView(answer: LearningAnswer, scopeLabel: String, onCite: (AskPassage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s), modifier = Modifier.semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }) {
        if (answer.unclearSource) {
            StatusLine(StatusKind.Warning, "Part of this comes from audio that may be unclear. Play the moment to check.")
        }
        if (answer.extractive && answer.kind == AnswerKind.FROM_SOURCE) {
            Text("Here's what your lecture says:", style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        if (answer.kind == AnswerKind.NOT_IN_SOURCE) {
            Text("NOT IN ${scopeLabel.uppercase()}", style = MM.type.overline, color = LearningTheme.colors.clay)
        }
        val accent = MM.colors.accent
        Text(
            buildAnnotatedString {
                var last = 0
                Regex("""\[(\d+(?:\s*,\s*\d+)*)]""").findAll(answer.text).forEach { m ->
                    append(answer.text.substring(last, m.range.first))
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(m.value) }
                    last = m.range.last + 1
                }
                append(answer.text.substring(last))
            },
            style = LearningTheme.type.reading,
            color = MM.colors.ink,
            modifier = Modifier.testTag("ask_answer")
        )
        val sources = if (answer.kind == AnswerKind.NOT_IN_SOURCE) listOfNotNull(answer.closest) else answer.cited
        if (sources.isNotEmpty()) {
            Text(
                if (answer.kind == AnswerKind.NOT_IN_SOURCE) "CLOSEST PASSAGE" else "SOURCES",
                style = MM.type.overline, color = MM.colors.inkMuted
            )
            sources.forEach { p -> SourceRow(p, onClick = { onCite(p) }) }
        }
    }
}

@Composable
private fun SourceRow(p: AskPassage, onClick: () -> Unit) {
    val stamp = p.evidence.timestamp
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Source ${p.number}: ${p.sourceTitle}${stamp?.let { " at $it" } ?: ""}. Opens the passage" }
            .padding(vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(Modifier.size(24.dp).clip(MM.radius.pill).background(MM.colors.accentWash), contentAlignment = Alignment.Center) {
            Text("${p.number}", style = MM.type.caption, color = MM.colors.accent)
        }
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(p.sourceTitle, stamp).joinToString(" · "),
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
            Text(p.evidence.quote.ifBlank { p.text }.take(140), style = MM.type.secondary, color = MM.colors.ink, maxLines = 2)
        }
    }
}
