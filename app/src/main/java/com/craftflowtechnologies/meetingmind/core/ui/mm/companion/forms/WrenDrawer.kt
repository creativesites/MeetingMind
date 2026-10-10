package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Fx
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Mouth
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import kotlin.math.PI
import kotlin.math.sin

/**
 * Wren, the songbird (§2.1): body c(49,60) r25, head c(62,40) r15.5, a cocked tail around
 * (34,54), a wing around (40,57), a beak, one eye at (68,37), a belly and two legs.
 * A port of the reference's `DRAW.wren`. Deliberately not a dove.
 */
object WrenDrawer : FormDrawer {
    private val Anchors = FxAnchors(floatArrayOf(14f, 30f, 4.5f, 88f, 22f, 4f, 90f, 66f, 3.5f, 12f, 72f, 3f), 70f, 14f, 74f, 26f)

    override fun DrawScope.draw(kit: CompanionDrawKit, f: CompanionFrame, p: CompanionPalette) = with(kit) {
        val pose = f.pose
        val lod = f.lod
        // Sound arcs in front of the beak while listening.
        if (lod && pose.fx == Fx.RINGS && !pose.calm) {
            val n = if (f.reduced) 1 else 3
            for (i in 0 until n) {
                val k = if (f.reduced) 0.4f else ((f.t / 1.2f + i / 3f) % 1f)
                val x = 95f - k * 9f
                val path = path(6)
                path.moveTo(x, 26f); path.quadraticTo(x - 6f, 36f, x, 46f)
                drawPath(path, p.accent, alpha = (sin(k * PI.toFloat()) * (0.15f + 0.7f * f.level)).coerceIn(0f, 1f), style = stroke(1.6f))
            }
        }
        shadow(f, p, 92f)
        body(pose, 88f) {
            rotate(pose.tail, Offset(34f, 54f)) {
                val tail = path(7)
                tail.moveTo(32f, 58f); tail.quadraticTo(16f, 44f, 15f, 24f); tail.quadraticTo(24f, 27f, 40f, 48f); tail.close()
                drawPath(tail, p.deep)
            }
            val legs = path(8)
            legs.moveTo(45f, 80f); legs.lineTo(43.5f, 89f); legs.moveTo(55f, 80f); legs.lineTo(56f, 89f)
            drawPath(legs, p.deep, style = stroke(2.4f))
            drawCircle(wrenBody, radius = 25f, center = Offset(49f, 60f))
            translate(0f, pose.headDy) {
                rotate(pose.headTilt, Offset(58f, 50f)) {
                    drawCircle(p.bodyTop, radius = 15.5f, center = Offset(62f, 40f))
                    val beak = path(9)
                    if (pose.mouth == Mouth.OPEN) {
                        beak.moveTo(76f, 37.5f); beak.lineTo(86.5f, 39.8f); beak.lineTo(76f, 42f); beak.close()
                        beak.moveTo(76f, 43f); beak.lineTo(84f, 46.5f); beak.lineTo(76f, 47f); beak.close()
                    } else {
                        beak.moveTo(76f, 38f); beak.lineTo(87f, 41.8f); beak.lineTo(76f, 45.5f); beak.close()
                    }
                    drawPath(beak, p.deep)
                    eyes(f, p, 68f, 37f, Float.NaN, 0f, if (lod) 2.8f else 3.8f, if (lod) 3.4f else 4.4f, if (lod) 2f else 2.8f, look = 0.8f)
                    if (pose.brows) {
                        val brow = path(1)
                        brow.moveTo(63.5f, 30.5f); brow.lineTo(71.5f, 33f)
                        drawPath(brow, p.eye, style = stroke(1.7f))
                    }
                    if (lod && !pose.brows) oval(p.blush, 70f, 45.5f, 3.2f, 1.9f)
                }
            }
            if (lod) oval(p.light, 57f, 69f, 13f, 10.5f, alpha = 0.9f)
            rotate(pose.wing, Offset(40f, 57f)) {
                val wing = path(10)
                wing.moveTo(28f, 60f); wing.quadraticTo(42f, 45f, 63f, 59f); wing.quadraticTo(47f, 73f, 28f, 60f); wing.close()
                drawPath(wing, p.deep)
            }
        }
        fx(f, p, Anchors)
    }
}
