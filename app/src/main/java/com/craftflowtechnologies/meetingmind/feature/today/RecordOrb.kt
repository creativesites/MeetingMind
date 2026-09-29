package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.Speaker3

/**
 * The record button: a glass-smooth core in the accent, breathing slowly, with a ring of light
 * turning around it and two soft ripples leaving it. Pressing it sinks it under the finger and
 * lets it spring back. The ripples pause on a phone with animations turned off (the scale
 * durations follow the system's animator scale).
 */
@Composable
fun RecordOrb(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 92.dp, tag: String = "record_orb") {
    val t = rememberInfiniteTransition(label = "orb")
    val breathe by t.animateFloat(0.97f, 1.035f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "spin")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "ripple")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.45f, stiffness = 380f), label = "press")
    val accent = Accent
    val second = Speaker3
    val onAccent = OnAccent
    val outer = size * 1.7f

    Box(
        modifier.size(outer).semantics { role = Role.Button; contentDescription = "Record" }.testTag(tag)
            .pointerInput(Unit) { detectTapGestures(onPress = { val p = androidx.compose.foundation.interaction.PressInteraction.Press(it); source.emit(p); tryAwaitRelease(); source.emit(androidx.compose.foundation.interaction.PressInteraction.Release(p)) }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(outer)) {
            val c = center
            val core = this.size.minDimension / 1.7f / 2f
            // Two ripples, half a cycle apart, growing and fading out.
            listOf(0f, 0.5f).forEach { offset ->
                val k = (ripple + offset) % 1f
                drawCircle(accent.copy(alpha = (1f - k) * 0.28f), radius = core * (1.05f + k * 0.75f), center = c, style = Stroke(width = core * 0.05f))
            }
            // A soft halo under the core.
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center = c, radius = core * 1.6f), radius = core * 1.6f, center = c)
            // The ring of light: a sweep that turns, with a bright head and a fading tail.
            rotate(spin, c) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, accent.copy(alpha = 0.15f), second.copy(alpha = 0.9f), accent, Color.Transparent), center = c),
                    radius = core * 1.12f * press, center = c, style = Stroke(width = core * 0.09f)
                )
            }
        }
        Box(
            Modifier.size(size).scale(breathe * press).clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, second.copy(alpha = 0.85f).compositeOverAccent(accent)), start = Offset(0f, 0f), end = Offset(200f, 240f))),
            contentAlignment = Alignment.Center
        ) {
            // A highlight, like light on glass.
            Canvas(Modifier.size(size)) {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), center = Offset(this.size.width * 0.34f, this.size.height * 0.28f), radius = this.size.minDimension * 0.55f))
            }
            Icon(Icons.Filled.Mic, contentDescription = null, tint = onAccent, modifier = Modifier.size(size * 0.42f))
        }
    }
}

/** Mixes this colour over [base] at its own alpha, so the gradient's far end stays close to the accent. */
private fun Color.compositeOverAccent(base: Color): Color = androidx.compose.ui.graphics.lerp(base, this.copy(alpha = 1f), 0.45f)

