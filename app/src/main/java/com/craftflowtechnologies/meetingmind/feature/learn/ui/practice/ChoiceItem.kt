package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.OneShotKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay

/** How one option looks: open, the right answer or the learner's wrong pick (both after answering), or dimmed. */
private enum class OptionMark { OPEN, RIGHT, WRONG_PICK, DIMMED }

/**
 * A multiple-choice item. Tapping an option answers at once; there is no Submit. Correct and wrong are shown by an
 * icon and a word as well as colour.
 */
@Composable
internal fun ChoiceItem(
    item: PracticeItem.Choice,
    index: Int,
    feedback: AnswerFeedback?,
    onAnswerChoice: (Int) -> Unit,
    onNotSure: () -> Unit,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    val pose = rememberChoicePose(index, feedback?.correct)
    val answered = feedback != null

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = MM.space.xl),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text(item.sourceLabel, style = MM.type.caption, color = c.accent)
                    item.sourceStartMs?.let {
                        Text("From your lecture, ${formatLectureTime(it)}", style = MM.type.secondary, color = c.inkSecondary)
                    }
                }
                ZuriSlot(CompanionPage.PRACTICE, size = PracticeSize.zuri, state = pose)
            }
            Text(
                item.prompt,
                style = MM.type.title,
                color = c.ink,
                modifier = Modifier.padding(top = MM.space.l).semantics { heading() }
            )
            Column(Modifier.padding(top = MM.space.xl), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                item.options.forEachIndexed { i, label ->
                    val mark = optionMark(i, item.correctIndex, feedback)
                    ChoiceOption(
                        i = i,
                        letter = ('A' + i).toString(),
                        label = label,
                        mark = mark,
                        tag = optionTag(mark, item, feedback),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAnswerChoice(i)
                        }
                    )
                }
            }
            if (!answered) {
                Row(
                    Modifier.fillMaxWidth().padding(top = MM.space.l),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tap an answer. No submit button.", style = MM.type.secondary, color = c.inkSecondary)
                    TextAction("Not sure", onNotSure)
                }
            }
        }
        AnimatedVisibility(
            visible = answered,
            enter = slideInVertically(animationSpec = MM.motion.standard()) { it / 4 } + fadeIn(MM.motion.standard()),
            exit = fadeOut(MM.motion.quick())
        ) {
            feedback?.let { ChoiceFeedback(item, it, onContinue, onPlaySource) }
        }
    }
}

private fun optionMark(i: Int, correctIndex: Int, feedback: AnswerFeedback?): OptionMark = when {
    feedback == null -> OptionMark.OPEN
    i == correctIndex -> OptionMark.RIGHT
    i == feedback.chosenIndex -> OptionMark.WRONG_PICK
    else -> OptionMark.DIMMED
}

/** The word beside an answered option. Null while the item is open or for a dimmed option. */
private fun optionTag(mark: OptionMark, item: PracticeItem.Choice, feedback: AnswerFeedback?): String? = when (mark) {
    OptionMark.RIGHT -> if (feedback?.chosenIndex == item.correctIndex) "Correct" else "Correct answer"
    OptionMark.WRONG_PICK -> "Your answer"
    else -> null
}

@Composable
private fun ChoiceOption(
    i: Int,
    letter: String,
    label: String,
    mark: OptionMark,
    tag: String?,
    onClick: () -> Unit
) {
    val c = MM.colors
    val border = when (mark) {
        OptionMark.RIGHT -> BorderStroke(2.dp, c.accent)
        OptionMark.WRONG_PICK -> BorderStroke(2.dp, c.warning)
        else -> BorderStroke(1.5.dp, c.line)
    }
    val container = when (mark) {
        OptionMark.RIGHT -> c.accentWash
        OptionMark.WRONG_PICK -> c.warning.copy(alpha = 0.1f)
        else -> c.surface
    }
    Surface(
        onClick = onClick,
        enabled = mark == OptionMark.OPEN,
        shape = MM.radius.card,
        color = container,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PracticeSize.option)
            .alpha(if (mark == OptionMark.DIMMED) 0.55f else 1f)
            .testTag("practice_option_$i")
    ) {
        Row(
            Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            OptionMarkCircle(letter, mark)
            Column(Modifier.weight(1f)) {
                Text(label, style = MM.type.bodyStrong, color = c.ink)
                tag?.let {
                    Text(
                        it,
                        style = MM.type.caption,
                        color = if (mark == OptionMark.WRONG_PICK) c.goldInk else c.accent
                    )
                }
            }
        }
    }
}

/** The letter in a ring; after answering, a check (right) or a cross (wrong pick) in a filled disc. */
@Composable
private fun OptionMarkCircle(letter: String, mark: OptionMark) {
    val c = MM.colors
    val glyph: ImageVector? = when (mark) {
        OptionMark.RIGHT -> Icons.Rounded.Check
        OptionMark.WRONG_PICK -> Icons.Rounded.Close
        else -> null
    }
    val fill = when (mark) {
        OptionMark.RIGHT -> c.accent
        OptionMark.WRONG_PICK -> c.warning
        else -> Color.Transparent
    }
    val glyphTint = when (mark) {
        OptionMark.RIGHT -> c.onAccent
        OptionMark.WRONG_PICK -> c.onInk
        else -> c.inkSecondary
    }
    Box(
        Modifier
            .size(PracticeSize.letter)
            .clip(MM.radius.pill)
            .background(fill)
            .then(if (glyph == null) Modifier.border(1.5.dp, c.line, MM.radius.pill) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (glyph != null) {
            Icon(glyph, contentDescription = null, tint = glyphTint, modifier = Modifier.size(16.dp))
        } else {
            Text(letter, style = MM.type.caption, color = c.inkSecondary)
        }
    }
}

/**
 * The panel that slides up after an answer. Right: accent wash, "Yes, that's it." Wrong: a raised panel,
 * "Not quite. Here's why.", the lecturer pill and the next line with a loop icon.
 */
@Composable
private fun ChoiceFeedback(
    item: PracticeItem.Choice,
    feedback: AnswerFeedback,
    onContinue: () -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    val shape = MM.radius.card
    val right = feedback.correct
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = MM.space.m, end = MM.space.m, bottom = MM.space.l)
            .then(if (right) Modifier else with(MM.elevation) { Modifier.floatingShadow(shape) })
            .clip(shape)
            .background(if (right) c.accentWash else c.surfaceRaised)
            .then(if (right) Modifier else Modifier.border(MMSize.hairline, c.line, shape))
            .padding(MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Text(
            if (right) "Yes, that's it." else "Not quite. Here's why.",
            style = MM.type.heading,
            color = c.ink,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(feedback.why, style = MM.type.body, color = c.ink)
        if (!right) {
            item.sourceStartMs?.let { start ->
                LecturerPill(
                    text = lecturerPillText(formatLectureTime(start), item),
                    onClick = { onPlaySource(item) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Icon(Icons.Rounded.Replay, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(MMSize.iconSmall))
                Text(feedback.nextLine, style = MM.type.secondary, color = c.inkSecondary)
            }
        } else {
            Text(feedback.nextLine, style = MM.type.secondary, color = c.inkSecondary)
        }
        InkButton("Continue", onContinue)
    }
}

/** "Hear your lecturer · 41:30 · 8 s"; the length drops out when the end time is unknown. */
internal fun lecturerPillText(time: String, item: PracticeItem): String {
    val start = item.sourceStartMs ?: return "Hear your lecturer · $time"
    val end = item.sourceEndMs ?: return "Hear your lecturer · $time"
    val seconds = ((end - start) / 1000).coerceAtLeast(1)
    return "Hear your lecturer · $time · $seconds s"
}

/**
 * Zuri leans in (CURIOUS) on each new question, nods (CELEBRATING, the NOD one-shot's visual) on a right answer and
 * stays calm (IDLE) on a wrong one. No screen can fire a one-shot through the companion machine, so each one is
 * shown by switching the state for its duration.
 */
@Composable
internal fun rememberChoicePose(index: Int, correct: Boolean?): CompanionState {
    var pose by remember(index) { mutableStateOf(CompanionState.IDLE) }
    LaunchedEffect(index, correct) {
        val (shown, holdMs) = when (correct) {
            null -> CompanionState.CURIOUS to OneShotKind.CURIOUS.durationMs
            true -> CompanionState.CELEBRATING to OneShotKind.NOD.durationMs
            false -> CompanionState.IDLE to 0L
        }
        pose = shown
        if (holdMs > 0) {
            delay(holdMs)
            pose = CompanionState.IDLE
        }
    }
    return pose
}
