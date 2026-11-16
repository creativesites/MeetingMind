package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.WorkPalette
import com.craftflowtechnologies.meetingmind.ui.theme.WorkShape
import java.time.LocalTime

/**
 * A meeting on the day ribbon. [past] dims it, so the day reads as "done so far".
 */
data class RibbonBlock(
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val onClick: () -> Unit,
    val past: Boolean = false
)

/** A task on the day ribbon: a small coloured dot at its time (priority colour), tappable if [onClick] is set. */
data class RibbonDot(
    val time: LocalTime,
    val color: Color,
    val onClick: (() -> Unit)? = null
)

/**
 * The day ribbon, the signature element of Work: an hour axis from [startHour] to [endHour], meetings
 * as accent blocks, tasks as priority-coloured dots, and a red now-line.
 *
 * Positions are proportional to the time, so a 45-minute meeting is visibly shorter than an hour.
 * Blocks and dots have touch targets of at least 48 dp. With [onDark] the ribbon sits on the Briefing
 * gradient and uses light lines and labels; otherwise it follows the theme. The meeting blocks use
 * the accent in light mode and the fixed indigo in dark mode, so their white text stays readable.
 *
 * @param now the current time for the red line, or null to hide it.
 * @param onDark true when the ribbon is drawn on [BriefingCard] or another dark gradient.
 */
@Composable
fun DayRibbon(
    modifier: Modifier = Modifier,
    startHour: Int = 8,
    endHour: Int = 18,
    now: LocalTime? = null,
    blocks: List<RibbonBlock> = emptyList(),
    dots: List<RibbonDot> = emptyList(),
    onDark: Boolean = false
) {
    val colors = MM.colors
    val track = if (onDark) FixedWhite.copy(alpha = 0.14f) else colors.track
    val labelColor = if (onDark) FixedWhite.copy(alpha = 0.5f) else colors.inkMuted
    val dotRing = if (onDark) Briefing.Top else colors.surface
    val blockBrush = if (onDark || colors.isDark) {
        Brush.linearGradient(listOf(WorkPalette.BlockStart, WorkPalette.BlockEnd))
    } else {
        Brush.linearGradient(listOf(colors.accent, lerp(colors.accent, Color.Black, 0.2f)))
    }
    val span = (endHour - startHour).toFloat()

    BoxWithConstraints(modifier.fillMaxWidth().height(RibbonHeight)) {
        val width = maxWidth
        // Horizontal position of a fractional hour, clamped to the axis.
        fun x(hour: Float): Dp = width * ((hour - startHour) / span).coerceIn(0f, 1f)

        // Axis.
        Box(
            Modifier
                .offset(y = AxisY)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(track)
        )

        // Meetings. The 48 dp hit area sits over the 40 dp visual block.
        blocks.forEach { block ->
            val left = x(block.start.hourFloat())
            val blockWidth = x(block.end.hourFloat()) - left
            Box(
                Modifier
                    .offset(x = left)
                    .width(blockWidth)
                    .height(TouchSize)
                    .clickable(onClick = block.onClick)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(BlockHeight)
                        .graphicsLayer { alpha = if (block.past) PastAlpha else 1f }
                        .clip(WorkShape.block)
                        .background(blockBrush)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Column {
                        Text(
                            text = block.start.clockLabel(),
                            style = MM.type.overline,
                            color = FixedWhite,
                            maxLines = 1
                        )
                        Text(
                            text = block.title,
                            style = MM.type.overline,
                            color = FixedWhite,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Tasks: a 10 dp dot with a ring in the background colour, inside a 48 dp hit area centred on the axis.
        dots.forEach { dot ->
            val tap = dot.onClick
            Box(
                Modifier
                    .offset(x = x(dot.time.hourFloat()) - TouchSize / 2f, y = AxisY + 1.dp - TouchSize / 2f)
                    .size(TouchSize)
                    .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(DotSize)
                        .clip(MM.radius.pill)
                        .background(dot.color)
                        .border(2.dp, dotRing, MM.radius.pill)
                )
            }
        }

        // Hour labels, every two hours.
        for (hour in startHour..endHour step 2) {
            Box(
                Modifier.offset(x = x(hour.toFloat()) - LabelWidth / 2f, y = LabelY).width(LabelWidth),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$hour", style = MM.type.caption, color = labelColor, maxLines = 1)
            }
        }

        // The now-line, drawn last so it sits over blocks and dots.
        now?.let { time ->
            val hour = time.hourFloat()
            if (hour >= startHour && hour <= endHour) {
                Box(
                    Modifier
                        .offset(x = x(hour) - NowDotSize / 2f, y = NowDotTop)
                        .size(NowDotSize)
                        .clip(MM.radius.pill)
                        .background(colors.recording)
                )
                Box(
                    Modifier
                        .offset(x = x(hour) - 1.dp, y = NowDotTop + NowDotSize / 2)
                        .width(2.dp)
                        .height(NowLineHeight)
                        .background(colors.recording)
                )
            }
        }
    }
}

private val RibbonHeight: Dp = 74.dp
private val TouchSize: Dp = 48.dp
private val BlockHeight: Dp = 40.dp
private val AxisY: Dp = 30.dp
private val DotSize: Dp = 10.dp
private val LabelWidth: Dp = 24.dp
private val LabelY: Dp = 56.dp
private val NowDotSize: Dp = 8.dp
private val NowDotTop: Dp = 3.dp
private val NowLineHeight: Dp = 48.dp
private const val PastAlpha = 0.45f

/** Hour of day as a fraction, for example 10:30 is 10.5. */
internal fun LocalTime.hourFloat(): Float = hour + minute / 60f

/** "10:00" style label for a block. */
private fun LocalTime.clockLabel(): String = "%02d:%02d".format(hour, minute)
