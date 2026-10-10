package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Fx
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import kotlin.math.max

/**
 * Zuri, the Orb (§2.1): a soft sphere with a three-bar sound sprout behind its head. A port of
 * the reference's `DRAW.orb`. Body c(50,58) r30; sprout bars at x 43.5/50/56.5, base y 30;
 * eyes (41,56)/(59,56); cheeks (33.5,65)/(66.5,65); mouth (50,65).
 */
object OrbDrawer : FormDrawer {
    private val Anchors = FxAnchors(floatArrayOf(16f, 30f, 5f, 84f, 24f, 4f, 88f, 62f, 3.5f, 12f, 64f, 3.5f), 67f, 20f, 70f, 32f)
    private val BarX = floatArrayOf(43.5f, 50f, 56.5f)
    private val RingStroke = Stroke(1.4f)

    override fun DrawScope.draw(kit: CompanionDrawKit, f: CompanionFrame, p: CompanionPalette) = with(kit) {
        val pose = f.pose
        val lod = f.lod
        // Halo behind the body, following half the hop.
        if (lod) translate(0f, pose.dy * 0.5f) { drawCircle(orbHalo, radius = 44f, center = Offset(50f, 60f)) }
        // Listening ripples: two rings (one in the static pose), never in Quiet.
        if (lod && pose.fx == Fx.RINGS && !pose.calm) {
            val n = if (f.reduced) 1 else 2
            for (i in 0 until n) {
                val k = if (f.reduced) 0.45f else ((f.t / 1.5f + i / 2f) % 1f)
                drawCircle(
                    p.accent, radius = 31f * (1f + k * 0.42f * (0.45f + f.level)), center = Offset(50f, 58f),
                    alpha = ((1f - k) * (0.18f + 0.55f * f.level)).coerceIn(0f, 1f), style = RingStroke
                )
            }
        }
        shadow(f, p, 93f)
        body(pose, 88f) {
            // The sprout, behind the body.
            rotate(pose.sproutLean, Offset(50f, 30f)) {
                for (i in 0..2) {
                    val h = max(2.5f, if (i == 0) pose.sprout0 else if (i == 1) pose.sprout1 else pose.sprout2)
                    drawRoundRect(p.deep, topLeft = Offset(BarX[i] - 2.2f, 30f - h), size = Size(4.4f, h + 4f), cornerRadius = CornerRadius(2.2f))
                }
            }
            drawCircle(orbBody, radius = 30f, center = Offset(50f, 58f))
            if (lod) rotate(-35f, Offset(39f, 44f)) { oval(p.shine, 39f, 44f, 8.5f, 4.6f) }
            if (lod && !pose.brows) {
                oval(p.blush, 33.5f, 65f, 4.2f, 2.5f)
                oval(p.blush, 66.5f, 65f, 4.2f, 2.5f)
            }
            eyes(f, p, 41f, 56f, 59f, 56f, if (lod) 3.3f else 4.4f, if (lod) 4.6f else 5.8f, if (lod) 2.4f else 3.2f)
            if (pose.brows) brows(p, 41f, 56f, 59f, 56f, 3.3f, 4.6f, 1.8f)
            mouth(f, p, 50f, 65f, 3f)
            if (pose.hands && lod) hands(p, 50f, 76f)
        }
        fx(f, p, Anchors)
    }
}
