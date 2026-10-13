package com.craftflowtechnologies.meetingmind.feature.today.home

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The vector illustrations behind the Today tiles, drawn in code so they stay crisp at any size.
 * Each scene takes a looping time in 0..1 and drifts slowly (clouds, light, stars, rings).
 */
typealias Scene = DrawScope.(t: Float) -> Unit

private const val TwoPi = 6.2831855f

/** Whether the system asked for no animation (animator scale 0): scenes hold still. */
@Composable
fun rememberHomeReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/** The shared looping time for one tile's scene. Read it only in the draw phase. */
@Composable
fun rememberSceneTime(periodMs: Int = 14_000): State<Float> {
    val still = remember { mutableFloatStateOf(0.3f) }
    if (rememberHomeReducedMotion()) return still
    val transition = rememberInfiniteTransition(label = "scene")
    return transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "scene_time"
    )
}

/** A soft round light: a radial gradient that fades to nothing (the watercolour and glow effect). */
private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius, center = center
    )
}

/** A rolling hill across the bottom, from [baseY] (0..1 of the height) with a slow sway. */
private fun DrawScope.hill(baseY: Float, amplitude: Float, phase: Float, color: Color, alpha: Float) {
    val path = Path()
    val w = size.width
    val h = size.height
    path.moveTo(0f, h)
    val steps = 28
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val y = h * baseY - amplitude * (0.5f + 0.5f * sin(f * TwoPi * 0.9f + phase))
        path.lineTo(w * f, y)
    }
    path.lineTo(w, h)
    path.close()
    drawPath(path, color, alpha = alpha)
}

/** Faith: a sunrise over layered lavender hills. */
val SunriseScene: Scene = { t ->
    val a = t * TwoPi
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFFF9E6D8), Color(0xFFEFDCEA), Color(0xFFDCD3F2))))
    val sun = Offset(w * 0.66f, h * 0.6f + 3f * sin(a))
    glow(sun, w * 0.55f * (1f + 0.03f * sin(a)), Color(0xFFFFF6D8), 0.95f)
    drawCircle(Color(0xFFFFF3CF), radius = w * 0.075f, center = sun)
    hill(0.70f, 12f, a * 0.5f, Color(0xFFC9BDE6), 0.75f)
    hill(0.80f, 16f, 1.3f + a * 0.4f, Color(0xFFA99BD6), 0.7f)
    hill(0.90f, 10f, 2.1f + a * 0.3f, Color(0xFF8A7CC6), 0.6f)
}

/** Devotional: warm paper, a beam of light and an open book. */
val PaperLightScene: Scene = { t ->
    val a = t * TwoPi
    val w = size.width
    val h = size.height
    drawRect(Brush.radialGradient(listOf(Color(0xFFFFFAF0), Color(0xFFEEDCC0)), center = Offset(w * 0.7f, h * 0.3f), radius = size.maxDimension * 0.9f))
    val sway = sin(a) * w * 0.03f
    val beam = Path().apply {
        moveTo(w * 0.75f + sway, 0f)
        lineTo(w * 0.35f + sway, 0f)
        lineTo(w * 0.05f, h)
        lineTo(w * 0.45f, h)
        close()
    }
    drawPath(beam, Color(0xFFFFF8EA), alpha = 0.55f + 0.1f * sin(a))
    val stroke = Stroke(width = 1.6.dp.toPx())
    // The open book sits bottom-right, below the subtitle, so the text never runs through it.
    val book = Color(0xFFA7784A).copy(alpha = 0.45f)
    val left = Path().apply {
        moveTo(w * 0.60f, h * 0.84f)
        quadraticBezierTo(w * 0.68f, h * 0.80f, w * 0.76f, h * 0.84f)
        lineTo(w * 0.76f, h * 0.96f)
        quadraticBezierTo(w * 0.68f, h * 0.92f, w * 0.60f, h * 0.96f)
        close()
    }
    val right = Path().apply {
        moveTo(w * 0.76f, h * 0.84f)
        quadraticBezierTo(w * 0.84f, h * 0.80f, w * 0.92f, h * 0.84f)
        lineTo(w * 0.92f, h * 0.96f)
        quadraticBezierTo(w * 0.84f, h * 0.92f, w * 0.76f, h * 0.96f)
        close()
    }
    drawPath(left, book, style = stroke)
    drawPath(right, book, style = stroke)
}

/** Prayer: a night sky with a crescent moon and twinkling stars. */
private val StarPoints = listOf(
    0.12f to 0.12f, 0.30f to 0.06f, 0.46f to 0.20f, 0.20f to 0.34f, 0.62f to 0.10f,
    0.08f to 0.56f, 0.52f to 0.42f, 0.90f to 0.44f, 0.36f to 0.52f, 0.70f to 0.62f
)

val NightPrayerScene: Scene = { t ->
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF241F52), Color(0xFF5A4F9A))))
    glow(Offset(w * 0.8f, h * 0.2f), w * 0.6f, Color(0xFFF6E7B8), 0.32f)
    val moon = Offset(w * 0.72f, h * 0.24f)
    drawCircle(Color(0xFFF6EED8), radius = w * 0.11f, center = moon)
    drawCircle(Color(0xFF2E2A62), radius = w * 0.10f, center = moon + Offset(w * 0.05f, -w * 0.035f))
    val star = 1.3.dp.toPx()
    StarPoints.forEachIndexed { i, (x, y) ->
        val twinkle = 0.5f + 0.5f * sin(t * TwoPi * 2f + i * 1.7f)
        drawCircle(Color.White.copy(alpha = 0.35f + 0.6f * twinkle), radius = star, center = Offset(w * x, h * y))
    }
    hill(0.86f, 14f, 0.8f, Color(0xFF1C1A44), 0.85f)
}

/** Create: soft watercolour blooms that drift a little. */
val WatercolorScene: Scene = { t ->
    val a = t * TwoPi
    val w = size.width
    val h = size.height
    drawRect(Color(0xFFF4EFE9))
    glow(Offset(w * 0.15f + 6f * sin(a), h * 0.18f), w * 0.5f, Color(0xFFF2B8A2), 0.7f)
    glow(Offset(w * 0.85f + 6f * cos(a), h * 0.72f), w * 0.62f, Color(0xFFB9D8CF), 0.7f)
    glow(Offset(w * 0.75f, h * 0.12f + 5f * sin(a + 1f)), w * 0.36f, Color(0xFFF6DC9C), 0.75f)
}

/** Circles: rings around a point, with one member orbiting. */
val CircleRingsScene: Scene = { t ->
    val w = size.width
    val h = size.height
    val a = t * TwoPi
    drawRect(Color(0xFFEEF5F3))
    val c = Offset(w * 0.75f, h * 0.8f)
    val pulse = 1f + 0.02f * sin(a)
    val ring = Color(0xFF5FA7A0)
    val stroke = Stroke(width = 1.dp.toPx())
    listOf(0.15f, 0.30f, 0.46f).forEachIndexed { i, r ->
        drawCircle(ring.copy(alpha = 0.36f - i * 0.07f), radius = w * r * pulse, center = c, style = stroke)
    }
    drawCircle(ring.copy(alpha = 0.45f), radius = w * 0.05f, center = c)
    val orbit = Offset(c.x + cos(a) * w * 0.30f, c.y + sin(a) * w * 0.30f)
    drawCircle(Color.White, radius = 6.dp.toPx(), center = orbit)
    drawCircle(ring, radius = 6.dp.toPx(), center = orbit, style = Stroke(width = 1.5.dp.toPx()))
}

/** Work: a dot grid and a calm trend line. */
val WorkGridScene: Scene = { t ->
    val w = size.width
    val h = size.height
    drawRect(Color.White)
    val step = 14.dp.toPx()
    val dot = Color(0xFFB9B3D6)
    val dotRadius = 1.2.dp.toPx()
    var y = step / 2
    while (y < h) {
        var x = w * 0.6f
        while (x < w) {
            drawCircle(dot, radius = dotRadius, center = Offset(x, y))
            x += step
        }
        y += step
    }
    val points = listOf(0.62f to 0.74f, 0.70f to 0.60f, 0.78f to 0.66f, 0.86f to 0.44f, 1.0f to 0.50f)
    val wobble = 3f * sin(t * TwoPi)
    val line = Path()
    val area = Path()
    points.forEachIndexed { i, (fx, fy) ->
        val p = Offset(w * fx, h * fy + if (i == points.lastIndex) wobble else 0f)
        if (i == 0) { line.moveTo(p.x, p.y); area.moveTo(p.x, p.y) } else { line.lineTo(p.x, p.y); area.lineTo(p.x, p.y) }
    }
    area.lineTo(w, h)
    area.lineTo(w * 0.62f, h)
    area.close()
    drawPath(area, Brush.verticalGradient(listOf(Color(0x386D5FD0), Color(0x006D5FD0))))
    drawPath(line, Color(0x996D5FD0), style = Stroke(width = 2.dp.toPx()))
}

/** Personal: a window with light, and a plant that sways. */
val WindowHomeScene: Scene = { t ->
    val w = size.width
    val h = size.height
    val sway = sin(t * TwoPi) * 3f
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFBF1E6), Color(0xFFEEDCCB))))
    val left = w * 0.62f
    val right = w * 0.97f
    val top = h * 0.14f
    val radius = (right - left) / 2
    val arch = Path().apply {
        moveTo(left, h)
        lineTo(left, top + radius)
        arcTo(Rect(left, top, right, top + 2 * radius), 180f, 180f, false)
        lineTo(right, h)
        close()
    }
    drawPath(arch, Color(0xFFFFFAF2))
    drawPath(arch, Color(0xFFE0CDB9), style = Stroke(width = 1.5.dp.toPx()))
    val mid = (left + right) / 2
    drawLine(Color(0xFFE8D8C6), Offset(mid, top), Offset(mid, h), strokeWidth = 1.dp.toPx())
    drawLine(Color(0xFFE8D8C6), Offset(left, h * 0.5f), Offset(right, h * 0.5f), strokeWidth = 1.dp.toPx())
    val leaf = Color(0xFF9FC7B0)
    rotate(-14f + sway, pivot = Offset(w * 0.20f, h * 0.9f)) {
        drawOval(leaf, topLeft = Offset(w * 0.16f, h * 0.70f), size = androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.22f))
    }
    rotate(12f - sway, pivot = Offset(w * 0.27f, h * 0.9f)) {
        drawOval(Color(0xFF7FB59B), topLeft = Offset(w * 0.22f, h * 0.66f), size = androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.24f))
    }
}

/** Journal: ruled paper with a soft corner fold. */
val RuledPaperScene: Scene = { _ ->
    val w = size.width
    val h = size.height
    drawRect(Color(0xFFF8F4EA))
    val rule = Color(0xFFD9D2C4)
    val stroke = 1.dp.toPx()
    var y = h * 0.30f
    while (y < h) {
        drawLine(rule, Offset(0f, y), Offset(w, y), strokeWidth = stroke)
        y += 28.dp.toPx()
    }
    val fold = Path().apply { moveTo(w * 0.72f, 0f); lineTo(w, 0f); lineTo(w, h * 0.28f); close() }
    drawPath(fold, Color(0xFFE9DCC2).copy(alpha = 0.8f))
}
