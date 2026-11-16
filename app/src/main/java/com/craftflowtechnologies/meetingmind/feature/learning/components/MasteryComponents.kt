package com.craftflowtechnologies.meetingmind.feature.learning.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.ui.theme.LearningTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * The five qualitative learning states (PLAN_LEARNING §5.3). They are never shown as a percentage.
 * Each state has its own shape and fill as well as a colour and a label, so the meaning never rests
 * on colour alone (design spec §11).
 */
val MasteryDisplayOrder = listOf(
    LearningMasteryState.NEEDS_REVIEW,
    LearningMasteryState.LEARNING,
    LearningMasteryState.DEVELOPING,
    LearningMasteryState.STRONG,
    LearningMasteryState.NEW
)

@Composable
fun masteryColor(state: LearningMasteryState): Color = when (state) {
    LearningMasteryState.NEW -> MM.colors.inkMuted
    LearningMasteryState.LEARNING -> MM.colors.accent
    LearningMasteryState.DEVELOPING -> MM.colors.accent
    LearningMasteryState.STRONG -> MM.colors.success
    LearningMasteryState.NEEDS_REVIEW -> LearningTheme.colors.clay
}

/** What a student reads under a state when there is no recorded reason yet. */
fun LearningMasteryState.plainReason(): String = when (this) {
    LearningMasteryState.NEW -> "Not tested yet."
    LearningMasteryState.LEARNING -> "Early recall. Reviews are spaced out from here."
    LearningMasteryState.DEVELOPING -> "Recalled on more than one day."
    LearningMasteryState.STRONG -> "Recalled correctly over a week or more."
    LearningMasteryState.NEEDS_REVIEW -> "Missed recently, or a review is late."
}

/**
 * ○ New · ◔ Learning · ◑ Developing · ● with a tick Strong · ◇ with ! Needs review.
 *
 * Pass [contentDescription] = null when a visible label sits next to the glyph, so TalkBack does
 * not read the state twice.
 */
@Composable
fun MasteryGlyph(
    state: LearningMasteryState,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    contentDescription: String? = "Learning state: ${state.label}"
) {
    val color = masteryColor(state)
    val mark = MM.colors.surface
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Canvas(modifier.size(size).then(semanticsModifier)) {
        val stroke = 1.5.dp.toPx()
        val r = this.size.minDimension / 2f - stroke / 2f
        val box = Size(r * 2f, r * 2f)
        val topLeft = Offset(center.x - r, center.y - r)
        when (state) {
            LearningMasteryState.NEW -> drawCircle(color, r, style = Stroke(stroke))
            LearningMasteryState.LEARNING -> {
                drawCircle(color, r, style = Stroke(stroke))
                drawArc(color, -90f, 90f, useCenter = true, topLeft = topLeft, size = box)
            }
            LearningMasteryState.DEVELOPING -> {
                drawCircle(color, r, style = Stroke(stroke))
                drawArc(color, -90f, 180f, useCenter = true, topLeft = topLeft, size = box)
            }
            LearningMasteryState.STRONG -> {
                drawCircle(color, r + stroke / 2f)
                val tick = Path().apply {
                    moveTo(center.x - r * 0.45f, center.y + r * 0.02f)
                    lineTo(center.x - r * 0.1f, center.y + r * 0.38f)
                    lineTo(center.x + r * 0.5f, center.y - r * 0.35f)
                }
                drawPath(tick, mark, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            LearningMasteryState.NEEDS_REVIEW -> {
                val d = Path().apply {
                    moveTo(center.x, center.y - r)
                    lineTo(center.x + r, center.y)
                    lineTo(center.x, center.y + r)
                    lineTo(center.x - r, center.y)
                    close()
                }
                drawPath(d, color, style = Stroke(stroke))
                drawLine(
                    color, Offset(center.x, center.y - r * 0.45f), Offset(center.x, center.y + r * 0.1f),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
                drawCircle(color, stroke * 0.6f, Offset(center.x, center.y + r * 0.4f))
            }
        }
    }
}

/** Glyph, label and (optionally) the plain reason the state was given. */
@Composable
fun MasteryLabel(
    state: LearningMasteryState,
    modifier: Modifier = Modifier,
    reason: String? = null
) {
    Column(
        modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            MasteryGlyph(state, contentDescription = null)
            Text(state.label, style = MM.type.caption, color = masteryColor(state))
        }
        if (!reason.isNullOrBlank()) {
            Text(reason, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    }
}

/**
 * A stacked bar of concept counts per state. Segment widths are counts, never a score, and the
 * legend always shows the numbers.
 */
@Composable
fun MasteryBar(
    counts: Map<LearningMasteryState, Int>,
    modifier: Modifier = Modifier
) {
    val parts = MasteryDisplayOrder.mapNotNull { s -> counts[s]?.takeIf { it > 0 }?.let { s to it } }
    val total = parts.sumOf { it.second }
    val description = if (total == 0) "No concepts yet" else
        parts.joinToString(", ") { (s, n) -> "$n ${s.label}" }
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MM.radius.pill)
            .background(MM.colors.track)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        parts.forEach { (state, n) ->
            Box(
                Modifier
                    .weight(n.toFloat())
                    .fillMaxHeight()
                    .background(barColor(state))
            )
        }
    }
}

@Composable
private fun barColor(state: LearningMasteryState): Color = when (state) {
    LearningMasteryState.NEW -> MM.colors.inkFaint
    LearningMasteryState.LEARNING -> MM.colors.accent.copy(alpha = 0.45f)
    else -> masteryColor(state)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MasteryLegend(
    counts: Map<LearningMasteryState, Int>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        MasteryDisplayOrder.forEach { state ->
            val n = counts[state] ?: 0
            if (n > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                    modifier = Modifier.semantics(mergeDescendants = true) {}
                ) {
                    MasteryGlyph(state, size = 12.dp, contentDescription = null)
                    Text("$n ${state.label}", style = MM.type.caption, color = MM.colors.inkSecondary)
                }
            }
        }
    }
}
