package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.learn.AnswerFeedback
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * The parts every format shares: the question frame (source, lecture time, Zuri, scrolling body, the
 * controls that wait for an answer), the feedback panel, option rows and the three recall ratings.
 * Correct and wrong always carry an icon and a word as well as colour. Zuri is never sad.
 */

/** How one answer option looks once the item is answered. */
internal enum class AnswerMark { OPEN, RIGHT, WRONG_PICK, DIMMED }

/**
 * The question frame. [body] scrolls. [bottom] holds the controls that wait for an answer (Check,
 * "Not sure"); it is hidden once there is feedback, which slides up from the bottom.
 */
@Composable
internal fun ItemFrame(
    item: PracticeItem,
    sourceLabel: String,
    sourceStartMs: Long?,
    pose: CompanionState,
    feedback: AnswerFeedback?,
    onPlaySource: (PracticeItem) -> Unit,
    onContinue: () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) {
    val c = MM.colors
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
                    Text(sourceLabel, style = MM.type.caption, color = c.accent)
                    sourceStartMs?.let {
                        Text("From your lecture, ${formatLectureTime(it)}", style = MM.type.secondary, color = c.inkSecondary)
                    }
                }
                ZuriSlot(CompanionPage.PRACTICE, size = PracticeSize.zuri, state = pose)
            }
            body()
        }
        if (feedback == null) {
            Column(
                Modifier.fillMaxWidth().padding(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.s),
                content = bottom
            )
        }
        AnimatedVisibility(
            visible = feedback != null,
            enter = slideInVertically(animationSpec = MM.motion.standard()) { it / 4 } + fadeIn(MM.motion.standard()),
            exit = fadeOut(MM.motion.quick())
        ) {
            feedback?.let { AnswerPanel(item, it, onPlaySource, onContinue) }
        }
    }
}

/**
 * The panel after an answer: "Yes, that's it." on a right answer (accent wash), "Not quite. Here's why."
 * on a wrong one (raised), the reason, "Hear your lecturer" when there is a source time, the next
 * line, and Continue.
 */
@Composable
internal fun AnswerPanel(
    item: PracticeItem,
    feedback: AnswerFeedback,
    onPlaySource: (PracticeItem) -> Unit,
    onContinue: () -> Unit
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            Icon(
                if (right) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (right) c.accent else c.inkSecondary,
                modifier = Modifier.size(MMSize.iconSmall)
            )
            Text(
                if (right) "Yes, that's it." else "Not quite. Here's why.",
                style = MM.type.heading,
                color = c.ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Text(feedback.why, style = MM.type.body, color = c.ink)
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
        InkButton("Continue", onContinue)
    }
}

/** One answer option: a letter or an icon, the words, and a tag after answering. Tapping it answers when [enabled]. */
@Composable
internal fun PracticeOption(
    label: String,
    mark: AnswerMark,
    tag: String?,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    letter: String? = null,
    selected: Boolean = false,
    enabled: Boolean = mark == AnswerMark.OPEN
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    val right = mark == AnswerMark.RIGHT
    val wrong = mark == AnswerMark.WRONG_PICK
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = MM.radius.card,
        color = if (right) c.accentWash else c.surface,
        border = BorderStroke(
            if (right || wrong || selected) 2.dp else 1.5.dp,
            when {
                right -> c.accent
                wrong -> c.ink
                selected -> c.accent
                else -> c.line
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PracticeSize.option)
            .alpha(if (mark == AnswerMark.DIMMED) 0.5f else 1f)
            .testTag(testTag)
    ) {
        Row(
            Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Box(
                Modifier.size(PracticeSize.letter).clip(MM.radius.pill).background(if (right || wrong) c.surfaceSunk else c.track),
                contentAlignment = Alignment.Center
            ) {
                when {
                    right -> Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    wrong -> Icon(Icons.Rounded.Close, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                    letter != null -> Text(letter, style = MM.type.caption, color = c.inkSecondary)
                }
            }
            Text(label, style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            tag?.let { Text(it, style = MM.type.caption, color = c.inkSecondary) }
        }
    }
}

/** The three recall buttons. "Got it" is filled; each hint says when the item comes back. */
@Composable
internal fun RatingRow(hints: Map<RecallButton, String>, onRate: (RecallButton) -> Unit) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        Text("How did that go?", style = MM.type.bodyStrong, color = c.inkSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            RecallButton.entries.forEach { button ->
                val filled = button == RecallButton.GOT_IT
                val ink = if (filled) c.onAccent else c.ink
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRate(button)
                    },
                    shape = MM.radius.card,
                    color = if (filled) c.accent else c.surface,
                    border = if (filled) null else BorderStroke(1.5.dp, c.line),
                    modifier = Modifier.weight(1f).heightIn(min = PracticeSize.rating).testTag("practice_rate_${button.name}")
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = MM.space.xs, vertical = MM.space.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(button.label, style = MM.type.bodyStrong, color = ink)
                        hints[button]?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MM.type.caption, color = if (filled) c.onAccent else c.inkSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** A line that asks the learner to answer, with "Not sure" beside it. Used under option formats. */
@Composable
internal fun AnswerHintRow(hint: String, onNotSure: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(hint, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f))
        TextAction("Not sure", onNotSure)
    }
}
