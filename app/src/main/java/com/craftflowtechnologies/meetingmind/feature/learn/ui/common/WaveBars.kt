package com.craftflowtechnologies.meetingmind.feature.learn.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM

private val WaveHeight: Dp = 22.dp
private val WaveGap: Dp = 2.dp
private val WaveMinBar: Dp = 4.dp

/**
 * A lecture's sound as a row of rounded bars. Each value is 0..1 (its height). Bars before [played]
 * (0..1 of the way through) are accent; the rest are the soft track colour. Bars listed in
 * [goldIndices] are gold, for a moment the lecturer stressed. Bars sit on the baseline.
 */
@Composable
fun WaveBars(
    bars: List<Float>,
    modifier: Modifier = Modifier,
    played: Float = 0f,
    goldIndices: Set<Int> = emptySet()
) {
    val accent = MM.colors.accent
    val track = MM.colors.track
    val gold = MM.colors.gold
    Canvas(modifier.fillMaxWidth().height(WaveHeight)) {
        val n = bars.size
        if (n == 0) return@Canvas
        val gap = WaveGap.toPx()
        val minBar = WaveMinBar.toPx()
        val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val playedUpTo = (played.coerceIn(0f, 1f) * n).toInt()
        bars.forEachIndexed { i, value ->
            val level = value.coerceIn(0f, 1f)
            val barHeight = minBar + level * (size.height - minBar)
            val color = when {
                i in goldIndices -> gold
                i < playedUpTo -> accent
                else -> track
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
