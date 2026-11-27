@file:OptIn(ExperimentalLayoutApi::class)

package com.craftflowtechnologies.meetingmind.feature.learn.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.aireport.AiFeature
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource
import com.craftflowtechnologies.meetingmind.core.aireport.HiddenAfterReportLine
import com.craftflowtechnologies.meetingmind.core.aireport.ReportAiAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Where an Ask question looks. [sentence] is how the sheet names it in lines of text. */
enum class AskSheetScope(val label: String, val sentence: String) {
    LECTURE("This lecture", "this lecture"),
    COURSE("This course", "this course"),
    ALL("All my learning", "all your learning")
}

/** What a citation pill does when tapped: play the lecture from a moment here, or open a lesson. */
sealed interface AskCiteTarget {
    data class Play(val ms: Long) : AskCiteTarget
    data class OpenLesson(val noteId: String) : AskCiteTarget
}

/** One numbered source under an answer. [target] null means it is only a label (no moment, no lesson). */
data class AskCitation(
    val number: Int,
    val sourceTitle: String,
    val timeLabel: String?,
    val quote: String,
    val target: AskCiteTarget?
)

/**
 * One question and its answer, as the sheet shows it. [answer] has its [n] markers in the text.
 * [reportText] is the AI-written words a learner can report (a quoted lecture is not AI-written, so it is null).
 */
data class AskSheetTurn(
    val id: String,
    val question: String,
    val scopeSentence: String,
    val pending: Boolean = false,
    val answer: String? = null,
    val notInSource: Boolean = false,
    val canQuiz: Boolean = false,
    val unclear: Boolean = false,
    val citations: List<AskCitation> = emptyList(),
    val generalPending: Boolean = false,
    val general: String? = null,
    val error: String? = null,
    val reportText: String? = null,
    val reportNoteId: String? = null,
    val reportMeetingId: String? = null
)

/** Everything the sheet shows. [scopes] are the chips offered; [suggestions] are the starter questions. */
data class AskSheetUi(
    val title: String,
    val turns: List<AskSheetTurn>,
    val scope: AskSheetScope,
    val scopes: List<AskSheetScope>,
    val suggestions: List<String>
)

/**
 * The one Ask sheet (Study style), used on the Learning home and in a lesson. Answers come from the chosen scope with
 * numbered citations; a citation plays the moment or opens the lesson. "Quiz me on this" starts a practice round from
 * an answer. A learner can report an AI answer; it then folds away.
 *
 * Stateless: the caller maps its own data to [AskSheetUi] and does the work in the callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskSheet(
    ui: AskSheetUi,
    onQuestion: (String) -> Unit,
    onScope: (AskSheetScope) -> Unit,
    onGeneral: (turnId: String) -> Unit,
    onQuiz: (turnId: String) -> Unit,
    onCite: (AskCiteTarget) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudyTheme {
        val c = MM.colors
        var draft by rememberSaveable { mutableStateOf("") }
        val busy = ui.turns.any { it.pending }
        val listState = rememberLazyListState()
        LaunchedEffect(ui.turns.size) {
            if (ui.turns.isNotEmpty()) listState.animateScrollToItem(ui.turns.size - 1)
        }
        val send = {
            if (!busy && draft.isNotBlank()) {
                onQuestion(draft.trim())
                draft = ""
            }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surfaceRaised,
            shape = MM.radius.sheet,
            modifier = modifier
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .navigationBarsPadding()
                    .imePadding()
                    .testTag("ask_sheet")
            ) {
                Column(Modifier.padding(horizontal = MM.space.xl), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Text(ui.title, style = MM.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
                    Text(
                        "Answers come from ${ui.scope.sentence}, with the moment each point was said.",
                        style = MM.type.caption,
                        color = c.inkMuted
                    )
                    if (ui.scopes.size > 1) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            ui.scopes.forEach { s ->
                                MMChip(
                                    label = s.label,
                                    selected = s == ui.scope,
                                    onClick = { onScope(s) },
                                    modifier = Modifier.testTag("ask_scope_${s.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.l),
                    verticalArrangement = Arrangement.spacedBy(MM.space.l)
                ) {
                    if (ui.turns.isEmpty()) item(key = "suggest") {
                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            Text("Try asking", style = MM.type.overline, color = c.inkMuted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                ui.suggestions.forEach { s -> MMChip(label = s, selected = false, onClick = { onQuestion(s) }) }
                            }
                        }
                    }
                    items(ui.turns, key = { it.id }) { turn ->
                        AskTurnView(turn = turn, onGeneral = { onGeneral(turn.id) }, onQuiz = { onQuiz(turn.id) }, onCite = onCite)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MM.space.xl, vertical = MM.space.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Ask about ${ui.scope.sentence}", style = MM.type.body, color = c.inkMuted, maxLines = 1) },
                        textStyle = MM.type.body.copy(color = c.ink),
                        maxLines = 4,
                        shape = MM.radius.card,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.accent,
                            unfocusedBorderColor = c.line,
                            cursorColor = c.accent
                        ),
                        modifier = Modifier.weight(1f).testTag("ask_input")
                    )
                    IconButton(
                        onClick = send,
                        enabled = !busy && draft.isNotBlank(),
                        modifier = Modifier
                            .size(MMSize.minTouch)
                            .clip(MM.radius.pill)
                            .background(if (!busy && draft.isNotBlank()) c.accent else c.track)
                            .testTag("ask_send")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Ask",
                            tint = if (!busy && draft.isNotBlank()) c.onAccent else c.inkMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AskTurnView(
    turn: AskSheetTurn,
    onGeneral: () -> Unit,
    onQuiz: () -> Unit,
    onCite: (AskCiteTarget) -> Unit
) {
    val c = MM.colors
    // Folded after the learner reported it (the AI-content policy); "Show" brings it back.
    var hidden by rememberSaveable(turn.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                turn.question,
                style = MM.type.body,
                color = c.ink,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(MM.radius.card)
                    .background(c.accentWash)
                    .padding(horizontal = MM.space.m, vertical = MM.space.s)
            )
        }
        when {
            turn.pending -> Text(
                "Searching ${turn.scopeSentence}…",
                style = MM.type.caption,
                color = c.inkSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("ask_searching")
            )
            hidden -> HiddenAfterReportLine(onShow = { hidden = false })
            turn.answer != null -> AnswerView(turn, onCite)
        }
        if (hidden) return@Column
        turn.error?.let { StatusLine(StatusKind.Info, it) }
        if (turn.notInSource && turn.general == null) {
            if (turn.generalPending) {
                Text("Writing a general answer…", style = MM.type.caption, color = c.inkSecondary)
            } else {
                TextAction("Answer from general knowledge", onClick = onGeneral, modifier = Modifier.testTag("ask_general"))
            }
        }
        turn.general?.let { g ->
            Column(
                Modifier.fillMaxWidth().clip(MM.radius.card).background(c.surfaceSunk).padding(MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Text("General knowledge, not from your learning", style = MM.type.overline, color = c.inkMuted)
                Text(g, style = MM.type.body, color = c.ink)
            }
        }
        if (turn.canQuiz || turn.reportText != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (turn.canQuiz) TextAction("Quiz me on this", onClick = onQuiz, modifier = Modifier.testTag("ask_quiz"))
                Spacer(Modifier.weight(1f))
                turn.reportText?.let { text ->
                    ReportAiAction(
                        source = { AiReportSource(AiFeature.ASK_LEARNING, text, noteId = turn.reportNoteId, meetingId = turn.reportMeetingId) },
                        onHide = { hidden = true }
                    )
                }
            }
        }
    }
}

/** The answer with its [n] markers in accent, then one pill per source it cites. */
@Composable
private fun AnswerView(turn: AskSheetTurn, onCite: (AskCiteTarget) -> Unit) {
    val c = MM.colors
    val answer = turn.answer.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        if (turn.unclear) {
            StatusLine(StatusKind.Warning, "Part of this comes from audio that may be unclear. Play the moment to check.")
        }
        if (turn.notInSource) {
            Text("Not in ${turn.scopeSentence}", style = MM.type.overline, color = c.inkMuted)
        }
        val accent = c.accent
        Text(
            buildAnnotatedString {
                var last = 0
                Regex("""\[(\d+(?:\s*,\s*\d+)*)]""").findAll(answer).forEach { m ->
                    append(answer.substring(last, m.range.first))
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(m.value) }
                    last = m.range.last + 1
                }
                append(answer.substring(last))
            },
            style = MM.type.body,
            color = c.ink,
            modifier = Modifier.testTag("ask_answer")
        )
        if (turn.citations.isNotEmpty()) {
            if (turn.notInSource) Text("Closest passage, to check yourself.", style = MM.type.caption, color = c.inkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                turn.citations.forEach { cite -> CitationPill(cite, onCite) }
            }
        }
    }
}

/**
 * A numbered source. With a target it is a button that plays the moment or opens the lesson; the description says which,
 * so TalkBack reads what a tap does. The target is 48 dp tall.
 */
@Composable
private fun CitationPill(cite: AskCitation, onCite: (AskCiteTarget) -> Unit) {
    val c = MM.colors
    val label = listOfNotNull("${cite.number}", cite.timeLabel).joinToString(" · ")
    val description = when (cite.target) {
        is AskCiteTarget.Play -> "Play from ${cite.timeLabel ?: "this moment"}, source ${cite.number}"
        is AskCiteTarget.OpenLesson -> "Open the lesson, source ${cite.number}: ${cite.sourceTitle}"
        null -> "Source ${cite.number}: ${cite.sourceTitle}"
    }
    Row(
        Modifier
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.pill)
            .background(c.accentWash)
            .then(
                cite.target?.let { t ->
                    Modifier.clickable(role = Role.Button, onClick = { onCite(t) })
                } ?: Modifier
            )
            .semantics { contentDescription = description }
            .padding(horizontal = MM.space.m, vertical = MM.space.xs)
            .testTag("ask_citation_${cite.number}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MM.type.caption, color = c.accent)
    }
}
