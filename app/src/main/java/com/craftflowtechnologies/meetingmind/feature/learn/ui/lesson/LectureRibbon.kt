package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlin.math.abs

/**
 * The whole lecture as seekable waveform bars. Played bars are accent, the rest a soft track, and stressed
 * moments are gold bars. Drag or tap to seek; [description] is what TalkBack reads.
 */
@Composable
internal fun WaveformSeekBar(
    bars: List<Float>,
    progress: Float,
    markers: List<Float>,
    description: String,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    val c = MM.colors
    val played = c.accent
    val unplayed = if (c.isDark) c.line else c.accent.copy(alpha = 0.25f)
    val gold = c.gold
    val ink = c.ink
    val seek by rememberUpdatedState(onSeekFraction)
    val p = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f)
                setProgress { fraction -> seek(fraction.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) seek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (size.width > 0) seek((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (size.width > 0) seek((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        if (bars.isEmpty()) {
            drawLine(unplayed, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        } else {
            val n = bars.size
            val gap = 2.dp.toPx()
            val barWidth = ((w - gap * (n - 1)) / n).coerceAtLeast(1f)
            val minHeight = h * 0.2f
            val corner = CornerRadius(2.dp.toPx())
            bars.forEachIndexed { i, value ->
                val x = i * (barWidth + gap)
                val fraction = (x + barWidth / 2f) / w
                val barHeight = minHeight + (h - minHeight) * value.coerceIn(0f, 1f)
                val stressed = markers.any { abs(it - fraction) <= 0.5f / n }
                val color = when {
                    stressed -> gold
                    fraction <= p -> played
                    else -> unplayed
                }
                drawRoundRect(color, Offset(x, (h - barHeight) / 2f), Size(barWidth, barHeight), corner)
            }
        }
        val playheadX = w * p
        drawLine(ink, Offset(playheadX, 0f), Offset(playheadX, h), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/**
 * The lesson's "at a glance" ribbon: the waveform, a gold star above each stressed moment, and the start, current
 * and end times in a monospaced caption. Tap or drag to seek. An empty [waveform] draws a thin line.
 */
@Composable
fun LectureRibbon(
    waveform: List<Float>,
    positionMs: Long,
    durationMs: Long,
    stressedAtMs: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val total = durationMs.coerceAtLeast(1L)
    val markers = stressedAtMs.map { (it.toFloat() / total).coerceIn(0f, 1f) }
    val mono = MM.type.caption.copy(fontFamily = FontFamily.Monospace)
    val description = "Lecture, ${formatLectureTime(positionMs)} of ${formatLectureTime(durationMs)}. " +
        "Drag or tap to move through the lecture."
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        if (markers.isNotEmpty()) StressStars(markers)
        WaveformSeekBar(
            bars = waveform,
            progress = positionMs.toFloat() / total,
            markers = markers,
            description = description,
            onSeekFraction = { onSeek((it * total).toLong()) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatLectureTime(0L), style = mono, color = c.inkMuted)
            Text(formatLectureTime(positionMs), style = mono, color = c.ink)
            Text(formatLectureTime(durationMs), style = mono, color = c.inkMuted)
        }
    }
}

/** Gold stars in a row above the bars, each at its stressed moment. */
@Composable
private fun StressStars(markers: List<Float>) {
    val gold = MM.colors.gold
    val count = markers.size
    val label = if (count == 1) "1 stressed moment" else "$count stressed moments"
    Canvas(Modifier.fillMaxWidth().height(14.dp).semantics { contentDescription = label }) {
        val radius = 6.dp.toPx()
        markers.forEach { m -> drawFourPointStar(Offset(m * size.width, size.height / 2f), radius, gold) }
    }
}
