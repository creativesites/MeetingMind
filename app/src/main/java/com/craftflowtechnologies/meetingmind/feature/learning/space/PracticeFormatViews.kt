package com.craftflowtechnologies.meetingmind.feature.learning.space

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * The views for the practice formats that need more than options or a text field: put in order,
 * match, worked example, and the four-stage ladder shown in Mastery and the practice summary.
 * MM tokens only.
 */

@StringRes
internal fun formatLabelRes(format: PracticeFormat): Int = when (format) {
    PracticeFormat.RECALL -> R.string.practice_format_recall
    PracticeFormat.CHOICE -> R.string.practice_format_choice
    PracticeFormat.TRUE_FALSE -> R.string.practice_format_true_false
    PracticeFormat.MATCH -> R.string.practice_format_match
    PracticeFormat.CLOZE -> R.string.practice_format_cloze
    PracticeFormat.SCENARIO -> R.string.practice_format_scenario
    PracticeFormat.ORDER -> R.string.practice_format_order
    PracticeFormat.WORKED -> R.string.practice_format_worked
    PracticeFormat.SHORT -> R.string.practice_format_short
    PracticeFormat.EXPLAIN -> R.string.practice_format_explain
}

@StringRes
internal fun stageLabelRes(stage: PracticeStage): Int = when (stage) {
    PracticeStage.RECOGNISE -> R.string.practice_stage_recognise
    PracticeStage.RECALL -> R.string.practice_stage_recall
    PracticeStage.APPLY -> R.string.practice_stage_apply
    PracticeStage.EXPLAIN -> R.string.practice_stage_explain
}

/**
 * Recognise · Recall · Apply · Explain for one concept: filled for each stage shown with a correct
 * answer, outlined for the next one. Read by TalkBack as one sentence.
 */
@Composable
internal fun StageLadder(reached: Set<PracticeStage>, next: PracticeStage?, modifier: Modifier = Modifier) {
    val names = PracticeStage.entries.map { stringResource(stageLabelRes(it)) }
    val none = stringResource(R.string.practice_stage_none_yet)
    val allShown = stringResource(R.string.practice_stage_all_shown)
    val shown = PracticeStage.entries.filter { it in reached }.joinToString(", ") { names[it.ordinal] }
    val description = stringResource(
        R.string.practice_stage_ladder_description,
        shown.ifEmpty { none },
        next?.let { names[it.ordinal] } ?: allShown
    )
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }.testTag("stage_ladder"),
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        PracticeStage.entries.forEach { stage ->
            val done = stage in reached
            val isNext = stage == next
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MM.radius.pill)
                        .background(if (done) MM.colors.accent else MM.colors.track)
                        .then(if (isNext) Modifier.border(MMSize.hairline, MM.colors.accent, MM.radius.pill) else Modifier)
                )
                Text(
                    names[stage.ordinal],
                    style = MM.type.caption,
                    color = when {
                        done -> MM.colors.ink
                        isNext -> MM.colors.accent
                        else -> MM.colors.inkMuted
                    },
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Put in order: unplaced steps (shuffled) below, the student's order above. Tapping a placed step
 * takes it back. After checking, each placed step says whether it is in place.
 */
@Composable
internal fun OrderBoard(
    steps: List<String>,
    displayOrder: List<Int>,
    placed: List<Int>,
    revealed: Boolean,
    onPlace: (Int) -> Unit,
    onUnplace: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        if (!revealed) {
            Text(stringResource(R.string.practice_order_hint), style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        if (placed.isNotEmpty()) {
            Text(stringResource(R.string.practice_order_your_order).uppercase(), style = MM.type.overline, color = MM.colors.inkMuted)
            placed.forEachIndexed { position, stepIndex ->
                AnswerOption(
                    letter = (position + 1).toString(),
                    text = steps[stepIndex],
                    state = when {
                        !revealed -> OptionState.SELECTED
                        stepIndex == position -> OptionState.CORRECT
                        else -> OptionState.CHOSEN_WRONG
                    },
                    note = when {
                        !revealed -> null
                        stepIndex == position -> stringResource(R.string.practice_order_in_place)
                        else -> stringResource(R.string.practice_order_belongs_at, stepIndex + 1)
                    },
                    role = Role.Button,
                    onClick = { if (!revealed) onUnplace(stepIndex) }
                )
            }
        }
        val remaining = displayOrder.filter { it !in placed }
        if (remaining.isNotEmpty() && !revealed) {
            if (placed.isNotEmpty()) Box(Modifier.height(MM.space.xs))
            remaining.forEach { stepIndex ->
                AnswerOption(
                    letter = "+",
                    text = steps[stepIndex],
                    state = OptionState.IDLE,
                    role = Role.Button,
                    onClick = { onPlace(stepIndex) }
                )
            }
        }
    }
}

/**
 * Matching: terms as chips (lettered), meanings as rows. Tap a term, then a meaning. A matched
 * meaning shows its term's letter; tapping it again takes the match back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchBoard(
    pairs: List<MatchPair>,
    displayOrder: List<Int>,
    matches: Map<Int, Int>,
    activeTerm: Int?,
    revealed: Boolean,
    onPickTerm: (Int) -> Unit,
    onPickMeaning: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        if (!revealed) {
            Text(stringResource(R.string.practice_match_hint), style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            pairs.forEachIndexed { i, pair ->
                MMChip(
                    label = "${'A' + i}  ${pair.term}",
                    selected = activeTerm == i,
                    onClick = { onPickTerm(i) },
                    enabled = !revealed,
                    modifier = Modifier.testTag("match_term_$i")
                )
            }
        }
        val tapToMatch = stringResource(R.string.practice_match_tap_to_match)
        val matched = stringResource(R.string.practice_match_matched)
        displayOrder.forEach { meaningIndex ->
            val term = matches.entries.firstOrNull { it.value == meaningIndex }?.key
            AnswerOption(
                letter = term?.let { ('A' + it).toString() } ?: "·",
                text = pairs[meaningIndex].meaning,
                state = when {
                    !revealed && term != null -> OptionState.SELECTED
                    !revealed -> OptionState.IDLE
                    term == meaningIndex -> OptionState.CORRECT
                    else -> OptionState.CHOSEN_WRONG
                },
                note = when {
                    !revealed && term == null -> tapToMatch
                    !revealed -> null
                    term == meaningIndex -> matched
                    else -> stringResource(R.string.practice_match_belongs_to, pairs[meaningIndex].term)
                },
                role = Role.Button,
                onClick = { if (!revealed) onPickMeaning(meaningIndex) }
            )
        }
    }
}

/** A worked example's solution, one step at a time. */
@Composable
internal fun WorkedSteps(steps: List<String>, shown: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        steps.take(shown).forEachIndexed { i, step ->
            InsetPanel(Modifier.fillMaxWidth().testTag("worked_step_$i")) {
                Text(stringResource(R.string.practice_worked_step, i + 1), style = MM.type.caption, color = MM.colors.accent)
                Text(step, style = MM.type.body, color = MM.colors.ink)
            }
        }
    }
}
