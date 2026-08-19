package com.example.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletSecondary
import kotlin.math.sin

@Composable
fun LiveWaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .testTag("waveform_visualizer")
    ) {
        val barWidth = (size.width / (barCount * 1.6f)).coerceAtLeast(3.dp.toPx())
        val spacing = (size.width - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing)
            val normalizedIdx = i.toFloat() / barCount.toFloat()

            val waveFactor = if (isRecording) {
                val sine = sin(normalizedIdx * 12f + pulsePhase)
                val base = 0.12f + 0.88f * amplitude
                (base * (0.4f + 0.6f * (sine + 1f) / 2f)).coerceIn(0.08f, 1f)
            } else {
                0.08f
            }

            val barHeight = (size.height * 0.85f * waveFactor).coerceAtLeast(6.dp.toPx())
            val top = centerY - (barHeight / 2f)

            val brush = Brush.verticalGradient(
                colors = if (isRecording) {
                    listOf(
                        RecordingRed.copy(alpha = 0.9f),
                        IndigoPrimary,
                        VioletSecondary.copy(alpha = 0.9f)
                    )
                } else {
                    listOf(
                        Color.Gray.copy(alpha = 0.4f),
                        Color.Gray.copy(alpha = 0.2f)
                    )
                }
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun RecordingStatusBadge(
    isRecording: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isPaused) MaterialTheme.colorScheme.surfaceVariant else RecordingRed.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPaused) MaterialTheme.colorScheme.outline else RecordingRed.copy(alpha = 0.5f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isPaused -> Color.Gray
                            isRecording -> RecordingRed.copy(alpha = alpha)
                            else -> SuccessGreen
                        }
                    )
            )
            Text(
                text = when {
                    isPaused -> "Paused"
                    isRecording -> "Recording Active"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else RecordingRed
                )
            )
        }
    }
}

@Composable
fun OfflineShieldBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SuccessGreen.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen)
            )
            Text(
                text = "100% On-Device AI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen,
                    fontSize = 11.sp
                )
            )
        }
    }
}
