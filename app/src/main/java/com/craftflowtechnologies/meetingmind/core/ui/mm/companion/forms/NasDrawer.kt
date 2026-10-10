package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Mouth
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette

/**
 * Nas, the puppy (§2.1): head c(50,42) r21, body oval c(50,77) 35×27, floppy ears on pivots
 * (33,31)/(67,31), snout, nose, a soft patch over one eye, paws and a tail around (64,76).
 * A port of the reference's `DRAW.nas`. LOD-0 keeps the ears, eyes and nose.
 */
object NasDrawer : FormDrawer {
    private val Anchors = FxAnchors(floatArrayOf(12f, 26f, 4.5f, 88f, 20f, 4f, 92f, 64f, 3.5f, 9f, 66f, 3f), 68f, 10f, 76f, 22f)

    override fun DrawScope.draw(kit: CompanionDrawKit, f: CompanionFrame, p: CompanionPalette) = with(kit) {
        val pose = f.pose
        val lod = f.lod
        shadow(f, p, 93f)
        body(pose, 91f) {
            rotate(-35f - pose.tail, Offset(64f, 76f)) {
                drawRoundRect(p.deep, topLeft = Offset(63f, 72f), size = Size(16f, 6.5f), cornerRadius = CornerRadius(3.25f))
            }
            oval(nasBody, 50f, 77f, 17.5f, 13.5f)
            if (lod) oval(p.light, 50f, 80f, 9f, 8f, alpha = 0.85f)
            if (!pose.hands) {
                oval(p.light, 42.5f, 89f, 5.6f, 3.4f)
                oval(p.light, 57.5f, 89f, 5.6f, 3.4f)
            }
            translate(0f, pose.headDy) {
                rotate(pose.headTilt, Offset(50f, 60f)) {
                    drawCircle(nasHead, radius = 21f, center = Offset(50f, 42f))
                    if (lod) oval(p.deep, 59f, 40f, 7f, 6.5f, alpha = 0.28f)
                    rotatedOval(p.deep, 30.5f, 44f, 7.6f, 14.5f, pose.ear, Offset(33f, 31f))
                    rotatedOval(p.deep, 69.5f, 44f, 7.6f, 14.5f, -pose.ear, Offset(67f, 31f))
                    oval(p.light, 50f, 51f, 9.5f, 7f)
                    if (lod && !pose.brows) {
                        oval(p.blush, 37.5f, 49f, 3.4f, 2.1f)
                        oval(p.blush, 62.5f, 49f, 3.4f, 2.1f)
                    }
                    eyes(f, p, 42f, 40f, 58f, 40f, if (lod) 3.1f else 4.2f, if (lod) 3.8f else 5f, if (lod) 2.2f else 3f)
                    if (pose.brows) brows(p, 42f, 40f, 58f, 40f, 3.1f, 3.8f, 1.7f)
                    oval(p.eye, 50f, 47.6f, if (lod) 3.6f else 4.6f, if (lod) 2.6f else 3.2f)
                    if (lod) {
                        oval(p.catchLight, 48.9f, 46.8f, 1.1f, 0.6f, alpha = 0.7f)
                        val path = path(5)
                        when (pose.mouth) {
                            Mouth.OPEN -> {
                                path.moveTo(46.5f, 52.5f); path.quadraticTo(50f, 55.5f, 53.5f, 52.5f)
                                drawPath(path, p.eye, style = stroke(1.1f))
                                oval(p.tongue, 50f, 55.6f, 2.4f, 2.6f)
                            }
                            Mouth.WOBBLE -> {
                                path.moveTo(50f, 50.2f); path.lineTo(50f, 52f)
                                path.moveTo(46.8f, 54.2f); path.quadraticTo(50f, 52f, 53.2f, 54.2f)
                                drawPath(path, p.eye, style = stroke(1.1f))
                            }
                            Mouth.NONE -> {
                                path.moveTo(50f, 50.2f); path.lineTo(50f, 52.2f)
                                drawPath(path, p.eye, style = stroke(1.1f))
                            }
                            Mouth.SMILE, Mouth.O -> {
                                path.moveTo(50f, 50.2f); path.lineTo(50f, 52.4f)
                                path.moveTo(46.6f, 52.4f); path.quadraticTo(48.3f, 54.6f, 50f, 52.4f)
                                path.quadraticTo(51.7f, 54.6f, 53.4f, 52.4f)
                                drawPath(path, p.eye, style = stroke(1.1f))
                            }
                        }
                    }
                }
            }
            // Paws together under the chin (Prayerful).
            if (pose.hands) {
                for (side in 0..1) {
                    val cx = if (side == 0) 47.2f else 52.8f
                    rotate(if (side == 0) 14f else -14f, Offset(cx, 64f)) {
                        oval(p.light, cx, 64f, 4f, 6.2f)
                        drawOval(p.deep, topLeft = Offset(cx - 4f, 64f - 6.2f), size = Size(8f, 12.4f), style = stroke(0.9f))
                    }
                }
            }
        }
        fx(f, p, Anchors)
    }
}
