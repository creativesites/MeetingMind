package com.craftflowtechnologies.meetingmind.feature.learn.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Sizes the practice screens need that are not spacing tokens: touch targets, Zuri sizes, the progress bar. */
internal object PracticeSize {
    val header = MMSize.minTouch
    val progress = 6.dp
    val option = 60.dp
    val letter = 30.dp
    val button = 52.dp
    val rating = 68.dp
    val zuri = 72.dp
    val zuriSmall = 28.dp
    val zuriEmpty = 56.dp
    val zuriHero = 120.dp
    val pillHeight = MMSize.minTouch
}

/** The near-black pill: Continue, Show answer, Done for today. */
@Composable
internal fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    Button(
        onClick = onClick,
        shape = MM.radius.pill,
        modifier = modifier.fillMaxWidth().heightIn(min = PracticeSize.button),
        colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.onInk),
        contentPadding = PaddingValues(horizontal = MM.space.xl, vertical = MM.space.s)
    ) { Text(text, style = MM.type.bodyStrong) }
}

/** Close (X) on the left, one segment per item in the middle, "3/8" on the right. */
@Composable
internal fun PracticeHeader(index: Int, total: Int, onClose: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier.fillMaxWidth().padding(start = MM.space.s, end = MM.space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(PracticeSize.header)) {
            Icon(Icons.Rounded.Close, contentDescription = "End round", tint = c.ink)
        }
        if (total > 0) {
            ProgressSegments(index, total, Modifier.weight(1f))
            Text("${index + 1}/$total", style = MM.type.caption, color = c.inkSecondary)
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Done segments are accent, the current one ink, the rest track. Read by TalkBack as "Question 3 of 8". */
@Composable
internal fun ProgressSegments(index: Int, total: Int, modifier: Modifier = Modifier) {
    val c = MM.colors
    Row(
        modifier.semantics {
            contentDescription = "Question ${index + 1} of $total"
            progressBarRangeInfo = ProgressBarRangeInfo((index + 1).toFloat(), 1f..total.toFloat())
        },
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(total) { i ->
            val color = when {
                i < index -> c.accent
                i == index -> c.ink
                else -> c.track
            }
            Box(Modifier.weight(1f).height(PracticeSize.progress).clip(MM.radius.pill).background(color))
        }
    }
}

/** The warm gold pill that plays a moment of the lecture: "Hear your lecturer · 41:30 · 8 s". */
@Composable
internal fun LecturerPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    Surface(
        onClick = onClick,
        shape = MM.radius.pill,
        color = c.goldWash,
        modifier = modifier.heightIn(min = PracticeSize.pillHeight)
    ) {
        Row(
            Modifier.padding(horizontal = MM.space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.goldInk, modifier = Modifier.size(14.dp))
            Text(text, style = MM.type.secondary, color = c.goldInk)
        }
    }
}
