package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeItem
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * A recall card: the prompt first, then "Show answer". After the reveal the answer, the lecturer pill and the three
 * plain ratings, each with its hint from [recallHints]. Rating is honest, not scored: no red, no sad Zuri.
 */
@Composable
internal fun RecallItem(
    item: PracticeItem.Recall,
    revealed: Boolean,
    recallHints: Map<RecallButton, String>,
    onReveal: () -> Unit,
    onRate: (RecallButton) -> Unit,
    onPlaySource: (PracticeItem) -> Unit
) {
    val c = MM.colors
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize().padding(horizontal = MM.space.l)) {
        Column(Modifier.weight(1f).padding(top = MM.space.xl)) {
            // Two cards peek out behind the front card, as in the mockup.
            Box(
                Modifier.padding(horizontal = 22.dp).fillMaxWidth().height(MM.space.l)
                    .clip(MM.radius.card).background(c.line)
            )
            Box(
                Modifier.padding(horizontal = 11.dp).fillMaxWidth().height(MM.space.m)
                    .clip(MM.radius.card).background(c.lineSoft)
            )
            MMCard(Modifier.weight(1f), contentPadding = MM.space.xl) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MM.space.l)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.sourceLabel, style = MM.type.caption, color = c.accent)
                        Text("Recall card", style = MM.type.caption, color = c.inkSecondary)
                    }
                    Text(
                        item.prompt,
                        style = MM.type.title,
                        color = c.ink,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (revealed) {
                        AnswerDivider()
                        Text(item.answer, style = MM.type.body, color = c.ink)
                        item.sourceStartMs?.let {
                            LecturerPill(
                                text = "Your lecturer · ${formatLectureTime(it)}",
                                onClick = { onPlaySource(item) }
                            )
                        }
                    }
                }
                if (!revealed) InkButton("Show answer", onReveal)
            }
        }

        if (revealed) {
            Column(
                Modifier.fillMaxWidth().padding(top = MM.space.l),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Text(
                    "How did that go?",
                    style = MM.type.bodyStrong,
                    color = c.inkSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    RecallButton.entries.forEach { button ->
                        RatingButton(
                            button = button,
                            hint = recallHints[button],
                            filled = button == RecallButton.GOT_IT,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRate(button)
                            }
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = MM.space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ZuriSlot(CompanionPage.PRACTICE, size = PracticeSize.zuriSmall)
            Text(
                "Be honest. Missing it now is how it sticks.",
                style = MM.type.secondary,
                color = c.inkSecondary,
                modifier = Modifier.padding(start = MM.space.s)
            )
        }
    }
}

@Composable
private fun AnswerDivider() {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(Modifier.weight(1f).height(MMSize.hairline).background(c.line))
        Text("Answer", style = MM.type.caption, color = c.inkSecondary)
        Box(Modifier.weight(1f).height(MMSize.hairline).background(c.line))
    }
}

/** One of the three ratings. "Got it" is the filled one; the hint under it says when the card comes back. */
@Composable
private fun RatingButton(
    button: RecallButton,
    hint: String?,
    filled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val c = MM.colors
    val ink = if (filled) c.onAccent else c.ink
    Surface(
        onClick = onClick,
        shape = MM.radius.card,
        color = if (filled) c.accent else c.surface,
        border = if (filled) null else BorderStroke(1.5.dp, c.line),
        modifier = modifier.heightIn(min = PracticeSize.rating).testTag("practice_rate_${button.name}")
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = MM.space.xs, vertical = MM.space.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(button.label, style = MM.type.bodyStrong, color = ink)
            if (!hint.isNullOrBlank()) {
                Text(hint, style = MM.type.caption, color = if (filled) c.onAccent else c.inkSecondary)
            }
        }
    }
}
