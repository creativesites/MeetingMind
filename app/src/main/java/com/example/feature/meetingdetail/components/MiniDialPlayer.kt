package com.example.feature.meetingdetail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Accent
import kotlin.math.min

private val TrackColor = Color(0xFFE9EDF5)

/**
 * The 52dp conic-progress mini player: present on every tab of the redesigned recording detail
 * screen and never scrolls away (docs/recording-page-implementation.md §2.1). A conic sweep in
 * [Accent] from 0 to [progress], starting at the bottom (180deg in CSS conic-gradient terms), with
 * a 4dp white inset disc holding the play/pause glyph. Tap toggles playback; long-press opens the
 * full player.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MiniDialPlayer(
    progress: Float,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = size / 2),
                onClick = onTogglePlayPause,
                onLongClick = onLongPress
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = min(this.size.width, this.size.height)
            val arcSize = Size(d, d)
            val topLeft = Offset((this.size.width - d) / 2f, (this.size.height - d) / 2f)
            // 90deg in Compose's drawArc convention (0 = 3 o'clock, clockwise) is 6 o'clock/bottom,
            // matching the design's "conic-gradient starting at 180deg (bottom)".
            drawArc(
                color = TrackColor,
                startAngle = 90f,
                sweepAngle = 360f,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            if (clamped > 0f) {
                drawArc(
                    color = Accent,
                    startAngle = 90f,
                    sweepAngle = 360f * clamped,
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            PlayPauseGlyph(isPlaying = isPlaying)
        }
    }
}

@Composable
private fun PlayPauseGlyph(isPlaying: Boolean) {
    if (isPlaying) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(width = 3.dp, height = 13.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
            Box(Modifier.size(width = 3.dp, height = 13.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
        }
    } else {
        Canvas(modifier = Modifier.size(14.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height / 2f)
                lineTo(0f, this@Canvas.size.height)
                close()
            }
            drawPath(path, color = Accent)
        }
    }
}
