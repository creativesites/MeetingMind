package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/**
 * Page, the folio (§2.1): a rounded page 28..72 × 20..82 with a folded top-right corner (fold
 * 12–17), a ribbon, eyes at (42,40)/(56,40) and three text lines at y 58/65/72 that become the
 * waveform while listening and write themselves while thinking. A port of the reference's
 * `DRAW.folio`. The paper stays light in both themes.
 */
object PageDrawer : FormDrawer {
    private val Anchors = FxAnchors(floatArrayOf(16f, 26f, 4.5f, 86f, 20f, 4f, 88f, 60f, 3.5f, 14f, 66f, 3f), 68f, 10f, 74f, 22f)
    private val LineY = floatArrayOf(58f, 65f, 72f)
    private val LineLen = floatArrayOf(28f, 22f, 15f)

    override fun DrawScope.draw(kit: CompanionDrawKit, f: CompanionFrame, p: CompanionPalette) = with(kit) {
        val pose = f.pose
        val lod = f.lod
        val visual = f.visual
        val fold = pose.fold
        shadow(f, p, 95f)
        body(pose, 84f) {
            rotate(pose.pageLean, Offset(50f, 84f)) {
                val ribbon = path(6)
                ribbon.moveTo(36f, 78f); ribbon.lineTo(44f, 78f); ribbon.lineTo(44f, 94f); ribbon.lineTo(40f, 90f); ribbon.lineTo(36f, 94f); ribbon.close()
                drawPath(ribbon, p.accent)
                val page = path(7)
                page.moveTo(32f, 20f); page.lineTo(72f - fold, 20f); page.lineTo(72f, 20f + fold); page.lineTo(72f, 78f)
                page.quadraticTo(72f, 82f, 68f, 82f); page.lineTo(32f, 82f); page.quadraticTo(28f, 82f, 28f, 78f)
                page.lineTo(28f, 24f); page.quadraticTo(28f, 20f, 32f, 20f); page.close()
                drawPath(page, p.paper)
                drawPath(page, p.paperLine, style = stroke(1.4f))
                val corner = path(8)
                corner.moveTo(72f - fold, 20f); corner.lineTo(72f - fold, 20f + fold - 3f)
                corner.quadraticTo(72f - fold, 20f + fold, 72f - fold + 3f, 20f + fold); corner.lineTo(72f, 20f + fold); corner.close()
                drawPath(corner, p.light)
                drawPath(corner, p.paperLine, style = stroke(1.4f))
                if (lod && !pose.brows) {
                    oval(p.blush, 36.5f, 47f, 3.2f, 2f)
                    oval(p.blush, 61.5f, 47f, 3.2f, 2f)
                }
                eyes(f, p, 42f, 40f, 56f, 40f, if (lod) 2.9f else 3.8f, if (lod) 3.9f else 5f, if (lod) 2.1f else 2.8f)
                if (pose.brows) brows(p, 42f, 40f, 56f, 40f, 2.9f, 3.9f, 1.6f)
                mouth(f, p, 49f, 47.5f, 2.6f)
                if (lod) lines(kit, f, p, visual)
            }
        }
        fx(f, p, Anchors)
    }

    private fun DrawScope.lines(kit: CompanionDrawKit, f: CompanionFrame, p: CompanionPalette, visual: Any?) = with(kit) {
        val pose = f.pose
        val sw = 2.2f
        when {
            pose.hands -> hands(p, 49f, 66f, 0.9f)
            visual == CompanionState.LISTENING && !pose.calm -> for (i in 0..2) {
                val path = path(9)
                var x = 35f
                var first = true
                while (x <= 63.01f) {
                    val env = sin(PI.toFloat() * (x - 35f) / 28f)
                    var a = if (f.reduced) 0.6f * f.level * 3.4f else f.level * 3.4f * (0.6f + 0.4f * sin(f.t * 3f + i))
                    if (pose.calm) a *= 0.25f
                    val yy = LineY[i] + sin(x * 0.55f + (if (f.reduced) 0f else f.t * 8f) + i * 1.3f) * a * env
                    if (first) path.moveTo(x, yy) else path.lineTo(x, yy)
                    first = false
                    x += 1.4f
                }
                drawPath(path, p.lines, style = stroke(sw))
            }
            visual == CompanionState.THINKING -> {
                val total = if (f.reduced) 44f else (f.t * 16f) % 80f
                var rem = total
                var cx = 35f; var cy = 58f
                for (i in 0..2) {
                    val l = max(0f, min(LineLen[i], rem))
                    rem -= LineLen[i]
                    if (l > 0f) {
                        drawLine(p.lines, Offset(35f, LineY[i]), Offset(35f + l, LineY[i]), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        cx = 35f + l; cy = LineY[i]
                    }
                }
                val caretAlpha = if (f.reduced) 1f else if ((f.t * 3f).toInt() % 2 == 1) 1f else 0.25f
                drawRoundRect(p.accent, topLeft = Offset(cx + 2f, cy - 3.2f), size = Size(1.6f, 6.4f), cornerRadius = CornerRadius(0.8f), alpha = caretAlpha)
            }
            else -> {
                val worried = pose.brows
                val op = if (visual == CompanionState.SLEEPY) 0.35f else if (worried) 0.55f else 1f
                for (i in 0..2) {
                    val path = path(9)
                    path.moveTo(35f, LineY[i]); path.lineTo(35f + LineLen[i], LineY[i])
                    drawPath(path, p.lines, alpha = op, style = if (worried) dashed else stroke(sw))
                }
            }
        }
    }
}
