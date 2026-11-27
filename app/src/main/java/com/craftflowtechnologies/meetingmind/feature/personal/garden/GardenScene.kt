package com.craftflowtechnologies.meetingmind.feature.personal.garden

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.GardenPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The garden is designed on a 390 by 640 board; everything scales from it. */
private const val DESIGN_W = 390f
private const val DESIGN_H = 640f

/** Fixed star positions on the design board, so the sky is the same every time: x, y, radius, twinkle offset. */
private val STARS = listOf(
    floatArrayOf(30f, 40f, 1.2f, 0f), floatArrayOf(88f, 22f, 0.9f, 0.4f), floatArrayOf(140f, 60f, 1.4f, 0.2f),
    floatArrayOf(214f, 30f, 1.0f, 0.7f), floatArrayOf(258f, 84f, 1.1f, 0.5f), floatArrayOf(362f, 140f, 1.0f, 0.9f),
    floatArrayOf(22f, 128f, 1.0f, 0.1f), floatArrayOf(190f, 118f, 0.8f, 0.6f), floatArrayOf(330f, 20f, 1.2f, 0.3f),
    floatArrayOf(110f, 170f, 0.9f, 0.8f), floatArrayOf(300f, 200f, 1.0f, 0.15f), floatArrayOf(60f, 250f, 0.8f, 0.55f)
)

/** Where fireflies hover, on the design board, and how each one drifts: x, y, speed, phase. */
private val FLIES = listOf(
    floatArrayOf(60f, 410f, 1.0f, 0f), floatArrayOf(150f, 380f, 0.8f, 0.2f), floatArrayOf(250f, 430f, 1.1f, 0.45f),
    floatArrayOf(330f, 395f, 0.9f, 0.6f), floatArrayOf(110f, 470f, 1.2f, 0.33f), floatArrayOf(290f, 480f, 0.7f, 0.8f),
    floatArrayOf(200f, 455f, 1.0f, 0.12f), floatArrayOf(30f, 440f, 0.9f, 0.7f)
)

/**
 * The painted garden behind the Personal home: sky, sun or moon, stars, three hills and the
 * fireflies. [fireflies] is how many reflections the person kept this week (at most eight show).
 * With [animate] off (reduced motion, tests) every light holds still.
 */
@Composable
fun GardenScene(
    palette: GardenPalette,
    fireflies: Int,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "garden")
        val v by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
            label = "garden_t"
        )
        v
    } else 0.3f
    val hills = remember { HillPaths() }
    Canvas(modifier) {
        val sx = size.width / DESIGN_W
        val sy = size.height / DESIGN_H
        drawRect(
            Brush.verticalGradient(
                0f to palette.skyTop, 0.48f to palette.skyMid, 0.78f to palette.skyLow, 1f to palette.skyLow
            )
        )
        if (palette.stars > 0f) drawStars(palette.stars, t, sx, sy)
        drawSun(palette, sx, sy)
        hills.draw(this, palette, sx, sy)
        if (palette.fireflies > 0f && fireflies > 0) drawFireflies(fireflies.coerceAtMost(FLIES.size), palette.fireflies, t, sx, sy)
    }
}

private fun DrawScope.drawStars(strength: Float, t: Float, sx: Float, sy: Float) {
    STARS.forEach { (x, y, r, off) ->
        val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t + off))).toFloat()
        drawCircle(GardenColors.Star.copy(alpha = strength * twinkle), radius = r * 1.3f * sx, center = Offset(x * sx, y * sy))
    }
}

private fun DrawScope.drawSun(p: GardenPalette, sx: Float, sy: Float) {
    val c = Offset(p.sunX * size.width, p.sunY * size.height)
    val r = 32f * sx
    drawCircle(Brush.radialGradient(listOf(p.sunGlow, Color.Transparent), center = c, radius = r * 3.2f), radius = r * 3.2f, center = c)
    drawCircle(p.sun, radius = r, center = c)
}

private fun DrawScope.drawFireflies(count: Int, strength: Float, t: Float, sx: Float, sy: Float) {
    for (i in 0 until count) {
        val (x, y, speed, phase) = FLIES[i]
        val a = 2f * PI.toFloat() * (t * speed + phase)
        val c = Offset((x + 10f * sin(a)) * sx, (y - 12f * (0.5f + 0.5f * cos(a))) * sy)
        val glow = strength * (0.35f + 0.65f * (0.5f + 0.5f * sin(a * 1.7f)))
        drawCircle(Brush.radialGradient(listOf(GardenColors.EmberGlow.copy(alpha = 0.75f * glow), Color.Transparent), center = c, radius = 9f * sx), radius = 9f * sx, center = c)
        drawCircle(GardenColors.Ember.copy(alpha = glow), radius = 2.4f * sx, center = c)
    }
}

/** The three hills, rebuilt only when the size changes. */
private class HillPaths {
    private var key = Offset.Zero
    private val far = Path()
    private val near = Path()
    private val ground = Path()

    fun draw(scope: DrawScope, p: GardenPalette, sx: Float, sy: Float) = with(scope) {
        val k = Offset(sx, sy)
        if (k != key) {
            key = k
            build(far, sx, sy, size.height, floatArrayOf(0f, 450f, 60f, 400f, 120f, 420f, 170f, 390f, 230f, 355f, 300f, 400f, 390f, 370f))
            build(near, sx, sy, size.height, floatArrayOf(0f, 500f, 80f, 460f, 150f, 490f, 220f, 460f, 290f, 430f, 340f, 470f, 390f, 450f))
            build(ground, sx, sy, size.height, floatArrayOf(0f, 560f, 90f, 535f, 300f, 535f, 390f, 555f))
        }
        drawPath(far, p.hillFar)
        drawPath(near, p.hillNear)
        drawPath(ground, p.ground)
    }

    /** A start point then cubic segments (control, control, end), closed down to the bottom. */
    private fun build(path: Path, sx: Float, sy: Float, bottom: Float, pts: FloatArray) {
        path.reset()
        path.moveTo(pts[0] * sx, pts[1] * sy)
        var i = 2
        while (i + 5 < pts.size) {
            path.cubicTo(pts[i] * sx, pts[i + 1] * sy, pts[i + 2] * sx, pts[i + 3] * sy, pts[i + 4] * sx, pts[i + 5] * sy)
            i += 6
        }
        path.lineTo(DESIGN_W * sx, bottom)
        path.lineTo(0f, bottom)
        path.close()
    }
}
