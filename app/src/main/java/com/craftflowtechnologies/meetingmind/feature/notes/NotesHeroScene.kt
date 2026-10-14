package com.craftflowtechnologies.meetingmind.feature.notes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.craftflowtechnologies.meetingmind.feature.today.home.Scene
import kotlin.math.sin

private const val TwoPi = 6.2831855f

/**
 * The latest-meeting card's background: a soft sky, a sun that drifts left of the companion, and two layers of
 * hills. Drawn in code like the Today tiles, so it follows the theme's colors and stays crisp at any size.
 */
internal fun notesHeroScene(sky: Color, horizon: Color, hill: Color, sun: Color): Scene = { t ->
    val a = t * TwoPi
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(sky, horizon)))
    // The sun sits in the middle of the card, so the companion in the top-right corner never covers it.
    val center = Offset(w * 0.46f, h * 0.30f + 3f * sin(a))
    drawCircle(Brush.radialGradient(listOf(sun.copy(alpha = 0.7f), sun.copy(alpha = 0f)), center = center, radius = w * 0.42f), radius = w * 0.42f, center = center)
    drawCircle(sun.copy(alpha = 0.9f), radius = w * 0.075f, center = center)
    hillLayer(h * 0.72f, 10f, a * 0.5f, hill)
    hillLayer(h * 0.82f, 14f, 1.3f + a * 0.4f, hill.copy(alpha = hill.alpha * 0.8f))
}

/** A rolling hill across the bottom of the card, from [baseY] with a slow sway. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.hillLayer(baseY: Float, amplitude: Float, phase: Float, color: Color) {
    val path = Path()
    val w = size.width
    val h = size.height
    path.moveTo(0f, h)
    val steps = 24
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val y = baseY - amplitude * (0.5f + 0.5f * sin(f * TwoPi * 0.9f + phase))
        path.lineTo(w * f, y)
    }
    path.lineTo(w, h)
    path.close()
    drawPath(path, color)
}
