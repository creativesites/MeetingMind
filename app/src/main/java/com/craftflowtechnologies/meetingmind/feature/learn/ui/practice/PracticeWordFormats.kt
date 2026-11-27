package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.GapMarker
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeAnswer
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeFillGap
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeMatch
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeOrder
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * Fill the gap: the sentence with its gap underlined, and the word bank from the lecture's own words.
 * Tap a word, then Check. The full line is shown after the answer.
 */
@Composable
internal fun FillGapItem(
    item: PracticeFillGap,
    index: Int,
    feedback: AnswerFeedback?,
    onAnswer: (PracticeAnswer) -> Unit,
    onNotSure: () -> Unit,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    var picked by remember(index) { mutableStateOf<String?>(null) }
    val pose = rememberChoicePose(index, feedback?.correct)
    val shown = if (feedback == null) picked else item.answer
    ItemFrame(
        item = item,
        sourceLabel = item.sourceLabel,
        sourceStartMs = item.sourceStartMs,
        pose = pose,
        feedback = feedback,
        onPlaySource = onPlaySource,
        onContinue = onContinue,
        bottom = {
            picked?.let { word -> InkButton("Check", onClick = { onAnswer(PracticeAnswer.Fill(word)) }) }
            AnswerHintRow(
                if (picked == null) "Tap a word to fill the gap." else "Check when it reads right.",
                onNotSure
            )
        }
    ) {
        Text(
            item.prompt,
            style = MM.type.secondary,
            color = c.inkSecondary,
            modifier = Modifier.padding(top = MM.space.l)
        )
        Text(
            gapSentence(item.sentence, shown, c.accent),
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.s).semantics { heading() }
        )
        feedback?.takeIf { !it.correct && picked != null }?.let {
            Row(
                Modifier.padding(top = MM.space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                Text("You picked ${picked}", style = MM.type.secondary, color = c.inkSecondary)
            }
        }
        if (feedback == null) {
            Text(
                "Words come from your lecture, not a word bank.",
                style = MM.type.secondary,
                color = c.inkSecondary,
                modifier = Modifier.padding(top = MM.space.xl)
            )
        }
        WordBank(
            words = item.words,
            picked = picked,
            enabled = feedback == null,
            onPick = { picked = it },
            modifier = Modifier.padding(top = MM.space.m)
        )
    }
}

/** The sentence with the gap as an underlined word in accent: the answer once answered. */
private fun gapSentence(sentence: String, word: String?, accent: Color) = buildAnnotatedString {
    val parts = sentence.split(GapMarker, limit = 2)
    append(parts.getOrElse(0) { "" })
    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
        append(" ${word ?: "      "} ")
    }
    append(parts.getOrElse(1) { "" })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordBank(
    words: List<String>,
    picked: String?,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        words.forEach { word ->
            MMChip(
                label = word,
                selected = picked == word,
                onClick = { if (enabled) onPick(word) },
                enabled = enabled,
                modifier = Modifier.testTag("practice_gap_$word")
            )
        }
    }
}

/**
 * Match terms: tap a term, then its meaning. A matched meaning shows its term's letter; tapping a
 * matched term takes it back. Check once every term is matched. After Check, each row says whether it is right.
 */
@Composable
internal fun MatchItem(
    item: PracticeMatch,
    index: Int,
    feedback: AnswerFeedback?,
    onAnswer: (PracticeAnswer) -> Unit,
    onNotSure: () -> Unit,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    var active by remember(index) { mutableStateOf<Int?>(null) }
    var matches by remember(index) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val pose = rememberChoicePose(index, feedback?.correct)
    val meaningOwner = matches.entries.associate { (term, meaning) -> meaning to term }
    ItemFrame(
        item = item,
        sourceLabel = item.sourceLabel,
        sourceStartMs = item.sourceStartMs,
        pose = pose,
        feedback = feedback,
        onPlaySource = onPlaySource,
        onContinue = onContinue,
        bottom = {
            if (matches.size == item.terms.size) {
                InkButton("Check", onClick = { onAnswer(PracticeAnswer.Match(matches)) })
            }
            AnswerHintRow(
                if (active == null) "${matches.size} of ${item.terms.size} matched. Tap a term first."
                else "Now tap the meaning it goes with.",
                onNotSure
            )
        }
    ) {
        Text(
            item.prompt,
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.l).semantics { heading() }
        )
        Row(
            Modifier.fillMaxWidth().padding(top = MM.space.xl),
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                item.terms.forEachIndexed { i, term ->
                    val termRight = feedback?.let { matches[i] == i }
                    PracticeOption(
                        label = term,
                        mark = when (termRight) {
                            null -> AnswerMark.OPEN
                            true -> AnswerMark.RIGHT
                            false -> AnswerMark.WRONG_PICK
                        },
                        tag = null,
                        testTag = "match_term_$i",
                        letter = ('A' + i).toString(),
                        selected = active == i,
                        enabled = feedback == null,
                        onClick = {
                            if (matches.containsKey(i)) {
                                matches = matches - i
                                active = i
                            } else {
                                active = if (active == i) null else i
                            }
                        }
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                item.meaningOrder.forEach { meaning ->
                    val owner = meaningOwner[meaning]
                    val rowRight = feedback?.let { owner == meaning }
                    PracticeOption(
                        label = item.meanings[meaning],
                        mark = when (rowRight) {
                            null -> AnswerMark.OPEN
                            true -> AnswerMark.RIGHT
                            false -> AnswerMark.WRONG_PICK
                        },
                        tag = null,
                        testTag = "match_meaning_$meaning",
                        letter = owner?.let { ('A' + it).toString() } ?: "·",
                        enabled = feedback == null,
                        onClick = {
                            val term = active
                            if (term != null) {
                                matches = matches.filterValues { it != meaning } + (term to meaning)
                                active = null
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Put in order: each step has up and down arrows (48 dp). Starts shuffled. Check when it reads right.
 * After Check, each step says whether it is in place, or where it belongs.
 */
@Composable
internal fun OrderItem(
    item: PracticeOrder,
    index: Int,
    feedback: AnswerFeedback?,
    onAnswer: (PracticeAnswer) -> Unit,
    onNotSure: () -> Unit,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    var order by remember(index) { mutableStateOf(item.startOrder) }
    val pose = rememberChoicePose(index, feedback?.correct)
    ItemFrame(
        item = item,
        sourceLabel = item.sourceLabel,
        sourceStartMs = item.sourceStartMs,
        pose = pose,
        feedback = feedback,
        onPlaySource = onPlaySource,
        onContinue = onContinue,
        bottom = {
            InkButton("Check", onClick = { onAnswer(PracticeAnswer.Order(order)) })
            AnswerHintRow("Move the steps with the arrows. Check when it reads right.", onNotSure)
        }
    ) {
        Text(
            item.prompt,
            style = MM.type.title,
            color = c.ink,
            modifier = Modifier.padding(top = MM.space.l).semantics { heading() }
        )
        Column(Modifier.padding(top = MM.space.xl), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
            order.forEachIndexed { position, stepIndex ->
                val inPlace = stepIndex == position
                Surface(
                    shape = MM.radius.card,
                    color = if (feedback != null && inPlace) c.accentWash else c.surface,
                    border = BorderStroke(1.5.dp, if (feedback != null && inPlace) c.accent else c.line),
                    modifier = Modifier.fillMaxWidth().heightIn(min = PracticeSize.option).testTag("practice_step_$position")
                ) {
                    Row(
                        Modifier.padding(start = MM.space.m, end = MM.space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(PracticeSize.letter).clip(MM.radius.pill).background(c.track),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${position + 1}", style = MM.type.caption, color = c.ink)
                        }
                        Spacer(Modifier.width(MM.space.m))
                        Text(item.steps[stepIndex], style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f).padding(vertical = MM.space.s))
                        if (feedback != null) {
                            Icon(
                                if (inPlace) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = null,
                                tint = if (inPlace) c.accent else c.inkSecondary,
                                modifier = Modifier.size(MMSize.iconSmall)
                            )
                            Spacer(Modifier.width(MM.space.xs))
                            Text(
                                if (inPlace) "In place" else "Belongs at ${stepIndex + 1}",
                                style = MM.type.caption,
                                color = c.inkSecondary
                            )
                        } else {
                            IconButton(
                                onClick = { order = order.swap(position, position - 1) },
                                enabled = position > 0,
                                modifier = Modifier.size(PracticeSize.header)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = c.inkSecondary)
                            }
                            IconButton(
                                onClick = { order = order.swap(position, position + 1) },
                                enabled = position < order.lastIndex,
                                modifier = Modifier.size(PracticeSize.header)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = c.inkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<Int>.swap(a: Int, b: Int): List<Int> {
    if (a !in indices || b !in indices) return this
    val out = toMutableList()
    out[a] = this[b]
    out[b] = this[a]
    return out
}
