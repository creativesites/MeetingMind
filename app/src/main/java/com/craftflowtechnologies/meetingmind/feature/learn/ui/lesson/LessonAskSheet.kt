package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import com.craftflowtechnologies.meetingmind.ai.learning.AnswerKind
import com.craftflowtechnologies.meetingmind.ai.learning.AskPassage
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import com.craftflowtechnologies.meetingmind.core.aireport.AiFeature
import com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource
import com.craftflowtechnologies.meetingmind.core.aireport.HiddenAfterReportLine
import com.craftflowtechnologies.meetingmind.core.aireport.ReportAiAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskScope
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAskTurn
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * What the Ask sheet shows: the conversation so far, the scope, starter questions and where a report points.
 */
data class LessonAskUi(
    val turns: List<LessonAskTurn>,
    val scope: LessonAskScope,
    /** The lecture's course. "This course" is offered only when there is one. */
    val courseName: String?,
    val suggestions: List<String>,
    val noteId: String,
    val meetingId: String?
)

/** Three starter questions from the lecture's own concept names. Empty names give the generic one. */
fun askSuggestions(conceptNames: List<String>): List<String> {
    val names = conceptNames.filter { it.isNotBlank() }.take(3)
    val out = mutableListOf<String>()
    names.firstOrNull()?.let { out += "What is $it?" }
    if (names.size >= 2) out += "How is ${names[0]} different from ${names[1]}?"
    names.getOrNull(2)?.let { out += "Why does $it matter?" }
    if (out.isEmpty()) out += "What were the main points?"
    return out
}

/**
 * Ask about this lecture, restyled to match the concept sheet. Answers come from the lecture with numbered citations;
 * each citation is a pill that plays the lecture from that moment. "Quiz me on this" opens practice for the lecture.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonAskSheet(
    ask: LessonAskUi,
    onQuestion: (String) -> Unit,
    onGeneral: (String) -> Unit,
    onScope: (LessonAskScope) -> Unit,
    onQuiz: () -> Unit,
    onPlayFrom: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens Plans from the allowance panel. Null when this build cannot sell plans. */
    onSeePlans: (() -> Unit)? = null
) {
    StudyTheme {
        val c = MM.colors
        var draft by rememberSaveable { mutableStateOf("") }
        val busy = ask.turns.any { it.pending }
        val listState = rememberLazyListState()
        LaunchedEffect(ask.turns.size) {
            if (ask.turns.isNotEmpty()) listState.animateScrollToItem(ask.turns.size - 1)
        }
        val scopeLabel = if (ask.scope == LessonAskScope.COURSE) "this course" else "this lecture"
        val send = {
            if (!busy && draft.isNotBlank()) {
                onQuestion(draft)
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
                    .testTag("lesson_ask")
            ) {
                Column(
                    Modifier.padding(horizontal = MM.space.xl),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    Text(
                        "Ask about this lecture",
                        style = MM.type.title,
                        color = c.ink,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        "Answers come from $scopeLabel, with the moment each point was said.",
                        style = MM.type.caption,
                        color = c.inkMuted
                    )
                    // "This course" is only offered for a lecture that belongs to a course.
                    if (!ask.courseName.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            LessonAskScope.entries
                                .forEach { s ->
                                    MMChip(
                                        label = s.label,
                                        selected = s == ask.scope,
                                        onClick = { onScope(s) },
                                        modifier = Modifier.testTag("lesson_ask_scope_${s.name.lowercase()}")
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
                    if (ask.turns.isEmpty()) item(key = "suggest") {
                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            Text("TRY ASKING", style = MM.type.overline, color = c.inkMuted)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                                verticalArrangement = Arrangement.spacedBy(MM.space.s)
                            ) {
                                ask.suggestions.forEach { s ->
                                    MMChip(label = s, selected = false, onClick = { onQuestion(s) })
                                }
                            }
                        }
                    }
                    items(ask.turns, key = { it.id }) { turn ->
                        AskTurnView(
                            turn = turn,
                            ask = ask,
                            onGeneral = { onGeneral(turn.id) },
                            onQuiz = onQuiz,
                            onPlayFrom = onPlayFrom,
                            onSeePlans = onSeePlans
                        )
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
                        placeholder = { Text("Ask about $scopeLabel", style = MM.type.body, color = c.inkMuted, maxLines = 1) },
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
                        modifier = Modifier.weight(1f).testTag("lesson_ask_input")
                    )
                    IconButton(
                        onClick = send,
                        enabled = !busy && draft.isNotBlank(),
                        modifier = Modifier
                            .size(MMSize.minTouch)
                            .clip(MM.radius.pill)
                            .background(if (!busy && draft.isNotBlank()) c.accent else c.track)
                            .testTag("lesson_ask_send")
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
    turn: LessonAskTurn,
    ask: LessonAskUi,
    onGeneral: () -> Unit,
    onQuiz: () -> Unit,
    onPlayFrom: (Long) -> Unit,
    onSeePlans: (() -> Unit)?
) {
    val c = MM.colors
    // Hidden after the learner reported it (the AI-content policy); "Show" brings it back.
    var hidden by rememberSaveable(turn.id) { mutableStateOf(false) }
    val answer = turn.answer
    val aiWritten = listOfNotNull(answer?.takeIf { !it.extractive }?.text, turn.general?.text).joinToString("\n\n")
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
                "Searching ${if (turn.scope == LessonAskScope.COURSE) "this course" else "this lecture"}…",
                style = MM.type.caption,
                color = c.inkSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("lesson_ask_searching")
            )
            hidden -> HiddenAfterReportLine(onShow = { hidden = false })
            answer != null -> AnswerView(answer, onPlayFrom)
        }
        if (hidden) return@Column
        // The allowance panel sits above the answer that was still found in the lecture's own words.
        turn.allowance?.let { AllowanceUsedUpPanel(it.resetsAtMs, onSeePlans) }
        turn.error?.let { StatusLine(StatusKind.Info, it) }
        if (turn.madeOnPhone && answer != null) {
            Text("Made on this phone", style = MM.type.caption, color = c.inkMuted)
        }
        if (answer != null && answer.kind == AnswerKind.NOT_IN_SOURCE && turn.general == null) {
            if (turn.generalPending) {
                Text("Writing a general answer…", style = MM.type.caption, color = c.inkSecondary)
            } else {
                TextAction("Answer from general knowledge", onClick = onGeneral, modifier = Modifier.testTag("lesson_ask_general"))
            }
        }
        turn.general?.let { g ->
            Column(
                Modifier.fillMaxWidth().clip(MM.radius.card).background(c.surfaceSunk).padding(MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                Text("GENERAL KNOWLEDGE · NOT FROM THIS LECTURE", style = MM.type.overline, color = c.inkMuted)
                Text(g.text, style = MM.type.body, color = c.ink)
            }
        }
        if ((answer != null && answer.kind == AnswerKind.FROM_SOURCE) || aiWritten.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (answer != null && answer.kind == AnswerKind.FROM_SOURCE) {
                    TextAction("Quiz me on this", onClick = onQuiz, modifier = Modifier.testTag("lesson_ask_quiz"))
                }
                Spacer(Modifier.weight(1f))
                if (aiWritten.isNotBlank()) {
                    ReportAiAction(
                        source = { AiReportSource(AiFeature.ASK_LEARNING, aiWritten, noteId = ask.noteId, meetingId = ask.meetingId) },
                        onHide = { hidden = true }
                    )
                }
            }
        }
    }
}

/** The answer text, with its [n] markers in accent, then one citation pill per passage it cites. */
@Composable
private fun AnswerView(answer: LearningAnswer, onPlayFrom: (Long) -> Unit) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        if (answer.unclearSource) {
            StatusLine(StatusKind.Warning, "Part of this comes from audio that may be unclear. Play the moment to check.")
        }
        if (answer.kind == AnswerKind.NOT_IN_SOURCE) {
            Text("NOT IN THIS LECTURE", style = MM.type.overline, color = c.inkMuted)
        }
        val accent = c.accent
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
            style = MM.type.body,
            color = c.ink,
            modifier = Modifier.testTag("lesson_ask_answer")
        )
        val sources = if (answer.kind == AnswerKind.NOT_IN_SOURCE) listOfNotNull(answer.closest) else answer.cited
        if (sources.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sources.forEach { p -> CitationPill(p, onPlayFrom) }
            }
            if (answer.kind == AnswerKind.NOT_IN_SOURCE) {
                Text("Closest passage, to check yourself.", style = MM.type.caption, color = c.inkMuted)
            }
        }
    }
}

/**
 * A numbered citation. With a moment in the recording it plays from there; without one it is only a label.
 * The 48 dp target and the description name the moment, so TalkBack reads what a tap does.
 */
@Composable
private fun CitationPill(passage: AskPassage, onPlayFrom: (Long) -> Unit) {
    val c = MM.colors
    val ms = passage.evidence.startMs
    val time = ms?.let { formatLectureTime(it) }
    val label = listOfNotNull("${passage.number}", time).joinToString(" · ")
    Row(
        Modifier
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.pill)
            .background(c.accentWash)
            .then(
                if (ms != null) Modifier.clickable(
                    role = Role.Button,
                    onClick = { onPlayFrom(ms) }
                ) else Modifier
            )
            .semantics {
                contentDescription = if (time != null) "Play from $time, source ${passage.number}" else "Source ${passage.number}"
            }
            .padding(horizontal = MM.space.m, vertical = MM.space.xs)
            .testTag("lesson_ask_citation_${passage.number}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MM.type.caption, color = c.accent)
    }
}
