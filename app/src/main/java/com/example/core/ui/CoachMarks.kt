package com.example.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Brand

/**
 * Where things are on screen, for the guided tour. Screens mark what the tour can point at with
 * [coachTarget]; the overlay reads the positions back. Nothing is recorded when no tour is running
 * (no provider), so the modifier costs nothing on ordinary screens.
 */
class CoachTargets {
    val bounds = mutableStateMapOf<String, Rect>()
}

val LocalCoachTargets = staticCompositionLocalOf<CoachTargets?> { null }

fun Modifier.coachTarget(key: String): Modifier = composed {
    val targets = LocalCoachTargets.current
    if (targets == null) this else onGloballyPositioned { targets.bounds[key] = it.boundsInRoot() }
}

@Composable
fun ProvideCoachTargets(targets: CoachTargets, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalCoachTargets provides targets, content = content)

/** One stop on the tour: what to point at and what to say about it. */
data class CoachStep(val key: String, val title: String, val body: String)

/**
 * A first-run tour: dims the screen, cuts a soft spotlight around each target in turn (with a
 * pulsing brand ring), and explains it in a card placed above or below. Tap anywhere or "Next" to
 * continue; "Skip" ends it. Steps whose target isn't on screen are passed over.
 */
@Composable
fun CoachMarkOverlay(targets: CoachTargets, steps: List<CoachStep>, onDone: () -> Unit) {
    val available = steps.filter { targets.bounds[it.key]?.let { r -> r.width > 0 && r.height > 0 } == true }
    if (available.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var cardHeight by remember { mutableStateOf(0f) }
    val step = available[index.coerceIn(0, available.lastIndex)]
    val raw = targets.bounds[step.key] ?: return
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val target by animateRectAsState(
        Rect(raw.left - origin.x - pad, raw.top - origin.y - pad, raw.right - origin.x + pad, raw.bottom - origin.y + pad),
        tween(420), label = "spot"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p")
    fun next() { if (index >= available.lastIndex) onDone() else index++ }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() }
            .testTag("coach_overlay")
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0xE6060D2E))
            val r = CornerRadius(minOf(target.height, target.width) / 2f, minOf(target.height, target.width) / 2f).let { if (target.height > 120f) CornerRadius(48f, 48f) else it }
            drawRoundRect(Color.Black, topLeft = target.topLeft, size = target.size, cornerRadius = r, blendMode = BlendMode.Clear)
            val grow = 6f + pulse * 22f
            drawRoundRect(
                Brush.linearGradient(Brand.sweep, start = target.topLeft, end = target.bottomRight),
                topLeft = Offset(target.left - grow, target.top - grow), size = Size(target.width + grow * 2, target.height + grow * 2),
                cornerRadius = CornerRadius(r.x + grow, r.y + grow), style = Stroke(width = 3f), alpha = 1f - pulse
            )
            drawRoundRect(
                Brush.linearGradient(Brand.sweep, start = target.topLeft, end = target.bottomRight),
                topLeft = target.topLeft, size = target.size, cornerRadius = r, style = Stroke(width = 4f)
            )
        }
        // The card sits below the spotlight when there's room, otherwise above it.
        val below = target.bottom < heightPx * 0.58f
        val gap = with(density) { 18.dp.toPx() }
        val top = if (below) target.bottom + gap else (target.top - gap - cardHeight).coerceAtLeast(gap * 2)
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .offset { IntOffset(0, top.toInt()) }
                .onSizeChanged { cardHeight = it.height.toFloat() }
        ) {
            AnimatedContent(targetState = step, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "card") { s ->
                Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Brush.linearGradient(Brand.sweep), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("${available.indexOf(s) + 1} of ${available.size}", color = Color(0xFF64748B), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(s.title, color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Text(s.body, color = Color(0xFF475569), fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Skip tour", color = Color(0xFF64748B), fontSize = 14.sp, modifier = Modifier.clickable(onClick = onDone).padding(vertical = 8.dp).testTag("coach_skip"))
                        Surface(onClick = { next() }, shape = RoundedCornerShape(50), color = Color.Transparent, modifier = Modifier.testTag("coach_next")) {
                            Box(Modifier.background(Brush.horizontalGradient(listOf(Brand.Cyan, Brand.Indigo, Brand.Violet))).padding(horizontal = 20.dp, vertical = 10.dp)) {
                                Text(if (available.indexOf(s) == available.lastIndex) "Got it" else "Next", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
