package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Eyes
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Fx
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Mouth
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Pose
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Everything one frame of drawing needs. A single instance is reused per slot; the drawer fills
 * it each frame instead of allocating.
 */
class CompanionFrame {
    var pose: Pose = Pose()
    var visual: CompanionVisual? = null
    /** Animation time, seconds. */
    var t: Float = 0f
    /** Smoothed level, 0..1. */
    var level: Float = 0f
    /** Static pose: reduced motion, goldens, export. */
    var reduced: Boolean = false
    /** Full detail (≥ 32 dp). False is LOD-0: silhouette and eyes only (§2.4). */
    var lod: Boolean = true
}

/** A fixed effect anchor set for a form: sparkle spots, the thinking dots and the sleepy z's. */
class FxAnchors(
    /** x, y, size for four sparkles. */
    val sparkles: FloatArray,
    val dotsX: Float, val dotsY: Float,
    val zX: Float, val zY: Float
)

/**
 * Pre-allocated paths, cached strokes and per-palette brushes, plus the shared face and effect
 * primitives (§2.1 "shared face system"). Geometry is the visual reference's, in the 100-unit box.
 */
class CompanionDrawKit {
    private val paths = Array(12) { Path() }
    private val strokeWidths = FloatArray(24)
    private val strokes = arrayOfNulls<Stroke>(24)
    private var strokeCount = 0
    val dash: PathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3.5f))
    private val dashedStroke = Stroke(width = 2.2f, cap = StrokeCap.Round, pathEffect = dash)

    var palette: CompanionPalette? = null
        private set
    lateinit var orbBody: Brush; private set
    lateinit var orbHalo: Brush; private set
    lateinit var wrenBody: Brush; private set
    lateinit var nasBody: Brush; private set
    lateinit var nasHead: Brush; private set

    /** Rebuilds the brushes when the palette changes (never per frame). */
    fun ensure(p: CompanionPalette) {
        if (palette == p) return
        palette = p
        orbBody = Brush.radialGradient(listOf(p.bodyTop, p.bodyBottom), center = Offset(41.6f, 46f), radius = 51f)
        orbHalo = Brush.radialGradient(0f to p.halo, 0.55f to p.halo, 1f to p.halo.copy(alpha = 0f), center = Offset(50f, 60f), radius = 44f)
        wrenBody = Brush.radialGradient(listOf(p.bodyTop, p.bodyBottom), center = Offset(44f, 50f), radius = 45f)
        nasBody = Brush.radialGradient(listOf(p.bodyTop, p.bodyBottom), center = Offset(46.5f, 71.6f), radius = 28f)
        nasHead = Brush.radialGradient(listOf(p.bodyTop, p.bodyBottom), center = Offset(45.8f, 33.6f), radius = 37.8f)
    }

    fun path(i: Int): Path = paths[i].also { it.reset() }

    /** A round-capped stroke of [w] units, cached after first use. */
    fun stroke(w: Float): Stroke {
        for (i in 0 until strokeCount) if (strokeWidths[i] == w) return strokes[i]!!
        val s = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (strokeCount < strokes.size) { strokeWidths[strokeCount] = w; strokes[strokeCount] = s; strokeCount++ }
        return s
    }

    val dashed: Stroke get() = dashedStroke

    // ───────────────────────── primitives ─────────────────────────

    fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Float = 1f) =
        drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), alpha = alpha)

    fun DrawScope.oval(brush: Brush, cx: Float, cy: Float, rx: Float, ry: Float) =
        drawOval(brush, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2))

    fun DrawScope.rotatedOval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, pivot: Offset = Offset(cx, cy)) =
        rotate(deg, pivot) { oval(color, cx, cy, rx, ry) }

    /** The ground shadow; shrinks during a hop. */
    fun DrawScope.shadow(f: CompanionFrame, p: CompanionPalette, y: Float) =
        oval(p.shadow, 50f, y, 18f * (1f - f.pose.hop * 0.35f), 2.6f)

    /** The prototype's bodyT: translate(0, dy), rotate(tilt) and scale(sx, sy) about the foot. */
    inline fun DrawScope.body(pose: Pose, foot: Float, block: DrawScope.() -> Unit) {
        translate(0f, pose.dy) {
            rotate(pose.tilt, Offset(50f, foot)) {
                scale(pose.sx, pose.sy, Offset(50f, foot)) { block() }
            }
        }
    }

    /**
     * Eyes at the given centres (x0,y0)(x1,y1); pass NaN for x1 for a single eye (Wren).
     * [rx]/[ry]/[sw] are the reference's per-form sizes; [look] scales the gaze offset.
     */
    fun DrawScope.eyes(f: CompanionFrame, p: CompanionPalette, x0: Float, y0: Float, x1: Float, y1: Float, rx0: Float, ry0: Float, sw: Float, look: Float = 1f) {
        eye(f, p, x0, y0, rx0, ry0, sw, look)
        if (!x1.isNaN()) eye(f, p, x1, y1, rx0, ry0, sw, look)
    }

    private fun DrawScope.eye(f: CompanionFrame, p: CompanionPalette, cx: Float, cy: Float, rx0: Float, ry0: Float, sw: Float, look: Float) {
        val pose = f.pose
        val rx = rx0 * pose.eyeScale
        val ry = ry0 * pose.eyeScale
        val x = cx + pose.lookX * look
        val y = cy + pose.lookY * look
        when (pose.eyes) {
            Eyes.HAPPY -> {
                val path = path(0)
                path.moveTo(x - rx * 1.15f, y + ry * 0.35f)
                path.quadraticTo(x, y - ry * 1.25f, x + rx * 1.15f, y + ry * 0.35f)
                drawPath(path, p.eye, style = stroke(sw))
            }
            Eyes.CLOSED -> {
                val path = path(0)
                path.moveTo(x - rx * 1.15f, y)
                path.quadraticTo(x, y + ry * 0.95f, x + rx * 1.15f, y)
                drawPath(path, p.eye, style = stroke(sw))
            }
            Eyes.OPEN, Eyes.SOFT -> {
                val soft = pose.eyes == Eyes.SOFT
                val ery = ry * (if (soft) 0.6f else 1f) * (1f - pose.blink * 0.88f)
                val ey = y + if (soft) ry * 0.28f else 0f
                oval(p.eye, x, ey, rx, ery)
                if (f.lod && pose.blink < 0.4f) {
                    drawCircle(p.catchLight, radius = rx * 0.36f, center = Offset(x - rx * 0.3f, ey - ery * 0.42f), alpha = 0.92f)
                }
            }
        }
    }

    /** Worried brows: inner ends raised. */
    fun DrawScope.brows(p: CompanionPalette, x0: Float, y0: Float, x1: Float, y1: Float, rx: Float, ry: Float, sw: Float) {
        val path = path(1)
        path.moveTo(x0 - rx * 1.4f, y0 - ry * 1.35f); path.lineTo(x0 + rx * 1.1f, y0 - ry * 1.95f)
        path.moveTo(x1 - rx * 1.1f, y1 - ry * 1.95f); path.lineTo(x1 + rx * 1.4f, y1 - ry * 1.35f)
        drawPath(path, p.eye, style = stroke(sw))
    }

    /** The shared mouth (Zuri, Page). Dropped at LOD-0. */
    fun DrawScope.mouth(f: CompanionFrame, p: CompanionPalette, x: Float, y: Float, w: Float) {
        if (!f.lod) return
        when (f.pose.mouth) {
            Mouth.SMILE -> {
                val path = path(2)
                path.moveTo(x - w, y); path.quadraticTo(x, y + w * 0.85f, x + w, y)
                drawPath(path, p.eye, style = stroke(w * 0.5f))
            }
            Mouth.OPEN -> {
                val path = path(2)
                path.moveTo(x - w * 1.15f, y - 0.4f); path.quadraticTo(x, y + w * 2f, x + w * 1.15f, y - 0.4f); path.close()
                drawPath(path, p.eye)
                oval(p.tongue, x, y + w * 0.75f, w * 0.55f, w * 0.3f)
            }
            Mouth.O -> oval(p.eye, x + 1f, y + 0.4f, w * 0.42f, w * 0.5f)
            Mouth.WOBBLE -> {
                val path = path(2)
                path.moveTo(x - w, y + 0.8f)
                path.quadraticTo(x - w / 2, y - 0.7f, x, y + 0.3f)
                path.quadraticTo(x + w / 2, y + 1.2f, x + w, y - 0.1f)
                drawPath(path, p.eye, style = stroke(w * 0.45f))
            }
            Mouth.NONE -> Unit
        }
    }

    /** Two small folded hands (Prayerful only). */
    fun DrawScope.hands(p: CompanionPalette, x: Float, y: Float, s: Float = 1f) {
        val rx = 3.6f * s; val ry = 6.4f * s
        for (side in 0..1) {
            val cx = if (side == 0) x - 2.6f * s else x + 2.6f * s
            rotate(if (side == 0) 18f else -18f, Offset(cx, y)) {
                oval(p.bodyTop, cx, y, rx, ry)
                drawOval(p.deep, topLeft = Offset(cx - rx, y - ry), size = Size(rx * 2, ry * 2), style = stroke(0.9f))
            }
        }
    }

    fun DrawScope.sparkle(x: Float, y: Float, s: Float, color: Color, alpha: Float) {
        val c = s * 0.16f
        val path = path(3)
        path.moveTo(x, y - s)
        path.quadraticTo(x + c, y - c, x + s, y)
        path.quadraticTo(x + c, y + c, x, y + s)
        path.quadraticTo(x - c, y + c, x - s, y)
        path.quadraticTo(x - c, y - c, x, y - s)
        path.close()
        drawPath(path, color, alpha = alpha.coerceIn(0f, 1f))
    }

    fun DrawScope.heart(x: Float, y: Float, s: Float, color: Color, alpha: Float) {
        val path = path(3)
        path.moveTo(x, y + s)
        path.cubicTo(x - s * 1.7f, y, x - s * 0.7f, y - s * 1.2f, x, y - s * 0.35f)
        path.cubicTo(x + s * 0.7f, y - s * 1.2f, x + s * 1.7f, y, x, y + s)
        path.close()
        drawPath(path, color, alpha = alpha)
    }

    /** A sleepy "z" drawn as a stroke, so no font or text layout is needed. */
    private fun DrawScope.zee(x: Float, y: Float, size: Float, color: Color, alpha: Float) {
        val w = size * 0.48f; val h = size * 0.5f
        val path = path(4)
        path.moveTo(x, y - h); path.lineTo(x + w, y - h); path.lineTo(x, y); path.lineTo(x + w, y)
        drawPath(path, color, alpha = alpha.coerceIn(0f, 1f), style = stroke(size * 0.15f))
    }

    /** The effect layer (the reference's `fx()`), drawn outside the body transform. */
    fun DrawScope.fx(f: CompanionFrame, p: CompanionPalette, a: FxAnchors) {
        if (!f.lod) return
        val t = f.t
        val rm = f.reduced
        val sp = a.sparkles
        when (f.pose.fx) {
            Fx.SPARKLES -> for (i in 0..3) {
                val k = if (rm) 1f else 0.55f + 0.45f * sin(t * 5f + i * 1.9f)
                sparkle(sp[i * 3], sp[i * 3 + 1], sp[i * 3 + 2] * k, if (i % 2 == 1) p.gold else p.sparkle, if (rm) 1f else 0.45f + 0.55f * k)
            }
            Fx.SPARKLES_TWO -> for (i in 0..1) {
                val k = if (rm) 0.8f else 0.5f + 0.4f * sin(t * 3f + i * 2f)
                sparkle(sp[i * 3], sp[i * 3 + 1], sp[i * 3 + 2] * k, if (i == 1) p.gold else p.sparkle, 0.8f)
            }
            Fx.GLINT -> sparkle(sp[3], sp[4], sp[5], p.gold, 1f)
            Fx.HEART -> {
                val hy = if (rm) 0f else sin(t * 1.6f) * 1.5f
                heart(sp[3] - 2f, sp[4] + 4f + hy, 3.6f, p.tongue, 0.9f)
            }
            Fx.DOTS -> for (i in 0..2) {
                val dy = if (rm) (if (i == 1) -2.5f else 0f) else -max(0f, sin(t * 4.2f - i * 0.7f)) * 3.2f
                drawCircle(p.sparkle, radius = 2.1f, center = Offset(a.dotsX + i * 6.5f, a.dotsY + dy), alpha = if (rm) 0.8f else 0.45f + 0.55f * (-dy / 3.2f))
            }
            Fx.ZZZ -> {
                val n = if (rm) 2 else 3
                for (i in 0 until n) {
                    val q = if (rm) (if (i == 0) 0.3f else 0.68f) else ((t / 2.6f + i / 3f) % 1f)
                    val op = if (rm) 0.85f else sin(q * PI.toFloat())
                    zee(a.zX + q * 10f, a.zY - q * 16f, 6f + q * 5f, p.mute, op)
                }
            }
            Fx.RINGS, Fx.NONE -> Unit
        }
    }
}
