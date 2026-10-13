package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionLodCutoff
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Pose
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.drawCompanion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionDrawKit
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.CompanionFrame
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.FormDrawer
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/*
 * The stage's effect layers (§7.1 MomentLayers): glow, curtain, ripple, sprouts around the header; the
 * character; and the title that writes itself. Brushes and paths are made once per size, not per frame.
 */

/** Dawn's warm light: lerp(accent, gold, 0.5) at 42% (Paper) or 50% (Graphite). */
@Composable
internal fun warmLight(): Color {
    val c = MM.colors
    return lerp(c.accent, c.gold, 0.5f).copy(alpha = if (c.isDark) 0.5f else 0.42f)
}

/** The header's rect in stage pixels, or a fallback at the top centre when no layer is tagged HEADER. */
private fun DrawScope.headerRect(g: StageGeometry): Rect {
    val r = g.headerRect
    return if (r.width > 0f) r else Rect(0f, 0f, size.width, 0f)
}

/** Behind the header: Dawn's glow. */
@Composable
internal fun BoxScope.BehindHeaderLayers(scope: MomentScope, palette: CompanionPalette) {
    val m = scope.moment ?: return
    if (!m.has(StageTarget.GLOW, StageProp.ALPHA)) return
    val warm = warmLight()
    val glow = remember(warm) { Brush.radialGradient(listOf(warm, warm.copy(alpha = 0f)), center = Offset.Zero, radius = 1f) }
    Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
        val t = scope.baseMs()
        val a = m.stage(StageTarget.GLOW, StageProp.ALPHA, t, 0f)
        if (a <= 0f) return@Canvas
        val h = headerRect(scope.stage)
        val cx = h.center.x
        val cy = h.top + 64.dp.toPx() + m.stage(StageTarget.GLOW, StageProp.Y_DP, t, 0f).dp.toPx()
        // A 300 × 190 dp ellipse: a unit radial gradient scaled to the ellipse's radii.
        translate(cx, cy) {
            scale(150.dp.toPx(), 95.dp.toPx(), pivot = Offset.Zero) {
                drawCircle(glow, radius = 1f, center = Offset.Zero, alpha = a)
            }
        }
    }
}

/** Over the header: the curtain, Plant's ripple and sprouts. */
@Composable
internal fun BoxScope.FrontHeaderLayers(scope: MomentScope, palette: CompanionPalette) {
    val m = scope.moment ?: return
    val curtain = m.has(StageTarget.CURTAIN, StageProp.PROGRESS)
    val ripple = m.has(StageTarget.RIPPLE, StageProp.PROGRESS)
    val sprouts = m.has(StageTarget.SPROUTS, StageProp.PROGRESS)
    if (!curtain && !ripple && !sprouts) return
    val c = MM.colors
    val curtainColor = lerp(c.background, c.accent, 0.22f)
    val cache = remember { CurtainBrushes() }
    val accent = c.accent
    val gold = palette.gold
    val deep = palette.deep
    Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
        val t = scope.baseMs()
        val h = headerRect(scope.stage)
        if (curtain) {
            val p = m.stage(StageTarget.CURTAIN, StageProp.PROGRESS, t, 1f)
            if (p < 1f) {
                val left = h.left - 10.dp.toPx()
                val top = h.top - 8.dp.toPx()
                val w = h.width + 20.dp.toPx()
                val ht = h.height + 24.dp.toPx()
                val half = w / 2f
                val (lb, rb) = cache.brushes(half, curtainColor)
                val r = CornerRadius(14.dp.toPx())
                val a = 1f - p
                translate(left - 0.6f * half * p, top) { drawRoundRect(lb, Offset.Zero, Size(half, ht), r, alpha = a) }
                translate(left + half + 0.6f * half * p, top) { drawRoundRect(rb, Offset.Zero, Size(half, ht), r, alpha = a) }
            }
        }
        if (ripple) {
            val rp = m.stage(StageTarget.RIPPLE, StageProp.PROGRESS, t, 0f)
            if (rp > 0f && rp < 1f) {
                drawCircle(
                    accent, radius = 8.dp.toPx() + 60.dp.toPx() * rp, center = Offset(h.center.x, h.top + 54.dp.toPx()),
                    alpha = 0.45f * (1f - rp), style = cache.stroke(2.dp.toPx())
                )
            }
        }
        if (sprouts) {
            val sp = m.stage(StageTarget.SPROUTS, StageProp.PROGRESS, t, 0f)
            if (sp > 0f) for (i in 0..2) {
                val x = h.center.x + (if (i == 0) -38f else if (i == 1) 38f else 0f).dp.toPx()
                val y = h.top + (if (i == 2) 70f else 58f).dp.toPx()
                val color = if (i == 1) gold else deep
                for (b in 0..2) {
                    val bh = (if (b == 1) 8f else 5f).dp.toPx() * sp
                    val bx = x + (b - 1) * 4.dp.toPx() - 1.4.dp.toPx()
                    drawRoundRect(color, Offset(bx, y - bh), Size(2.8.dp.toPx(), bh), CornerRadius(1.4.dp.toPx()))
                }
            }
        }
    }
}

/** The curtain halves' feathered brushes, rebuilt only when the width or colour changes. */
private class CurtainBrushes {
    private var half = -1f
    private var color = Color.Unspecified
    private var strokeWidth = -1f
    private var strokeCache: Stroke? = null
    private var cached: Pair<Brush, Brush>? = null

    fun brushes(h: Float, c: Color): Pair<Brush, Brush> {
        if (h != half || c != color) {
            half = h; color = c
            val clear = c.copy(alpha = 0f)
            cached = Brush.horizontalGradient(0f to c, 0.82f to c, 1f to clear, startX = 0f, endX = h) to
                Brush.horizontalGradient(0f to clear, 0.18f to c, 1f to c, startX = 0f, endX = h)
        }
        return cached!!
    }

    fun stroke(w: Float): Stroke {
        if (w != strokeWidth) { strokeWidth = w; strokeCache = Stroke(w) }
        return strokeCache!!
    }
}

/** The Unveil character, placed around the header from the COMPANION tracks (72 dp × SCALE). */
@Composable
internal fun BoxScope.HeaderCompanion(scope: MomentScope, form: CompanionForm, palette: CompanionPalette, titleWords: Int) {
    val m = scope.moment ?: return
    val box = MomentSpecs.UnveilCompanionDp.dp
    MomentCharacter(
        m, scope.clock, form, palette, box, titleWords = titleWords,
        modifier = Modifier.graphicsLayer {
            val t = scope.baseMs()
            val a = m.stage(StageTarget.COMPANION, StageProp.ALPHA, t, 0f)
            alpha = a.coerceIn(0f, 1f)
            val g = scope.stage.headerRect
            val root = scope.stage.root
            val cx = if (g.width > 0f) g.center.x else (root?.size?.width ?: 0) / 2f
            val top = if (g.width > 0f) g.top else 0f
            val dir = if (scope.rtl) -1f else 1f
            val half = box.toPx() / 2f
            translationX = cx + m.stage(StageTarget.COMPANION, StageProp.X_DP, t, 0f).dp.toPx() * dir - half
            translationY = top + m.stage(StageTarget.COMPANION, StageProp.Y_DP, t, 0f).dp.toPx() - half
            val s = m.stage(StageTarget.COMPANION, StageProp.SCALE, t, 1f)
            scaleX = s; scaleY = s
        }
    )
}

/**
 * Draws the character for a moment at [size]: the moment pose at the clock's time (or the end pose with no
 * clock), or [staticPose] when given (reduced motion). Decorative.
 */
@Composable
internal fun MomentCharacter(
    moment: ResolvedMoment,
    clock: MomentClock?,
    form: CompanionForm,
    palette: CompanionPalette,
    size: Dp,
    modifier: Modifier = Modifier,
    titleWords: Int = 5,
    staticPose: Pose? = null,
    mini: Boolean = false
) {
    val kit = remember { CompanionDrawKit() }
    val frame = remember { CompanionFrame() }
    val pose = remember { Pose() }
    val drawer = FormDrawer.of(if (mini) CompanionForm.PAGE else form)
    val lod = size >= CompanionLodCutoff
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val t = if (clock == null) moment.spec.baseMs.toFloat() else moment.baseMsAt(clock.timeMs)
        val p = when {
            mini -> MomentPose.miniInto(pose, moment, t, staticPose != null)
            staticPose != null -> pose.set(staticPose)
            else -> MomentPose.poseInto(pose, moment, t, titleWords)
        }
        frame.pose = p
        frame.visual = MomentPose.drawnVisual(if (mini) MomentVisual.IDLE else moment.visual(t))
        frame.t = t / 1000f
        frame.level = 0f
        frame.reduced = staticPose != null
        frame.lod = lod
        drawCompanion(kit, frame, drawer, palette)
    }
}

/**
 * The note title as real text that the Inkline variant writes word by word, with its caret, underline
 * and Dawn's glint. Outside a moment (or once written) it is plain text. Words past the eighth appear
 * together (§3.4). Semantics are the text's own.
 */
@Composable
fun MomentScope.MomentTitle(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = MM.colors.ink) {
    val m = moment
    val words = remember(text) { TitleWords() }
    val accent = MM.colors.accent
    val gold = MM.colors.gold
    val writes = m != null && m.has(StageTarget.TITLE_WORDS, StageProp.PROGRESS)
    val glints = m != null && m.has(StageTarget.GLINT, StageProp.ALPHA) && m.spec.kind == MomentKind.UNVEIL
    val decorated = writes || glints || (m != null && m.has(StageTarget.UNDERLINE, StageProp.PROGRESS))
    val drawModifier = if (!decorated) Modifier else Modifier.drawWithContent {
        val layout = words.layout
        val t = baseMs()
        val mm = m!!
        val tp = if (writes) mm.stage(StageTarget.TITLE_WORDS, StageProp.PROGRESS, t, 1f) else 1f
        if (layout == null || tp >= 1f) drawContent() else {
            val n = words.count
            val q = minOf(n, 8).coerceAtLeast(1)
            var lastVisible = -1
            for (i in 0 until n) {
                val o = (tp * q - minOf(i, q - 1)).coerceIn(0f, 1f)
                if (o <= 0f) continue
                lastVisible = i
                val r = words.rect(i)
                clipRect(r.left - 2f, r.top, r.right + 2f, r.bottom) {
                    translate(0f, 4.dp.toPx() * (1f - o)) { drawText(layout, color = color, alpha = o) }
                }
            }
            val caret = mm.stage(StageTarget.CARET, StageProp.ALPHA, t, 0f)
            if (caret > 0f) {
                val line = if (lastVisible < 0) Rect(0f, 0f, 0f, layout.getLineBottom(0)) else words.rect(lastVisible)
                val x = if (lastVisible < 0) 0f else line.right + 3.dp.toPx()
                drawRect(accent, Offset(x, line.top + 3.dp.toPx()), Size(2.dp.toPx(), (line.height - 6.dp.toPx()).coerceAtLeast(1f)), alpha = caret)
            }
        }
        if (layout != null) {
            val ul = mm.stage(StageTarget.UNDERLINE, StageProp.ALPHA, t, 0f)
            if (ul > 0f) {
                val w = minOf(layout.size.width.toFloat(), 260.dp.toPx()) * mm.stage(StageTarget.UNDERLINE, StageProp.PROGRESS, t, 0f)
                val x0 = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - w else 0f
                drawRect(accent, Offset(x0, layout.size.height + 2.dp.toPx()), Size(w, 2.dp.toPx()), alpha = ul)
            }
            if (glints && words.count > 0) {
                val g = mm.stage(StageTarget.GLINT, StageProp.ALPHA, t, 0f)
                if (g > 0f) {
                    val r = words.rect(words.count - 1)
                    words.sparkle(this, r.right + 12.dp.toPx(), r.top, 8.dp.toPx(), gold, g)
                }
            }
        }
    }
    Text(text, style = style, color = color, modifier = modifier.then(drawModifier), onTextLayout = { words.update(it, text) })
}

/** Word boxes of the laid-out title, computed once per layout. */
private class TitleWords {
    var layout: TextLayoutResult? = null
        private set
    private var rects: Array<Rect> = emptyArray()
    val count: Int get() = rects.size
    private val path = Path()

    fun rect(i: Int): Rect = rects[i]

    fun update(l: TextLayoutResult, text: String) {
        layout = l
        val out = ArrayList<Rect>()
        var i = 0
        val visible = l.layoutInput.text.length
        while (i < text.length && i < visible) {
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || i >= visible) break
            val start = i
            while (i < text.length && !text[i].isWhitespace()) i++
            val a = l.getBoundingBox(start)
            val b = l.getBoundingBox((i - 1).coerceAtMost(visible - 1))
            out += if (l.getLineForOffset(start) == l.getLineForOffset(i - 1)) {
                Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            } else Rect(0f, a.top, l.size.width.toFloat(), b.bottom)
        }
        rects = out.toTypedArray()
    }

    /** The four-point sparkle of the visual reference, centred at (x, y). */
    fun sparkle(scope: DrawScope, x: Float, y: Float, s: Float, color: Color, alpha: Float) {
        val c = s * 0.16f
        path.reset()
        path.moveTo(x, y - s)
        path.quadraticTo(x + c, y - c, x + s, y)
        path.quadraticTo(x + c, y + c, x, y + s)
        path.quadraticTo(x - c, y + c, x - s, y)
        path.quadraticTo(x - c, y - c, x, y - s)
        path.close()
        scope.drawPath(path, color, alpha = alpha.coerceIn(0f, 1f))
    }
}

/** A reusable sparkle path for stage layers. */
internal class SparklePath {
    private val path = Path()
    private val bar = Path()

    fun DrawScope.sparkle(x: Float, y: Float, s: Float, color: Color, alpha: Float, rotation: Float = 0f) {
        val c = s * 0.16f
        path.reset()
        path.moveTo(x, y - s)
        path.quadraticTo(x + c, y - c, x + s, y)
        path.quadraticTo(x + c, y + c, x, y + s)
        path.quadraticTo(x - c, y + c, x - s, y)
        path.quadraticTo(x - c, y - c, x, y - s)
        path.close()
        rotate(rotation, Offset(x, y)) { drawPath(path, color, alpha = alpha.coerceIn(0f, 1f)) }
    }

    /** Study's sparkle: three tiny sprout bars (6/10/6 units), [unit] px per unit. */
    fun DrawScope.bars(x: Float, y: Float, unit: Float, scale: Float, color: Color, alpha: Float, rotation: Float) {
        rotate(rotation, Offset(x, y)) {
            for (b in 0..2) {
                val h = (if (b == 1) 10f else 6f) * unit * scale
                val w = 3.2f * unit * scale
                drawRoundRect(color, Offset(x + (b - 1) * 5f * unit * scale - w / 2f, y - h / 2f), Size(w, h), CornerRadius(w / 2f), alpha = alpha)
            }
        }
    }
}
