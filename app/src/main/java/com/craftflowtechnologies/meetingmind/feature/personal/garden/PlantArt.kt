package com.craftflowtechnologies.meetingmind.feature.personal.garden

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors

/**
 * How a plant looks. Kept apart from the practice data so the drawing can be previewed and tested
 * on its own: [leaves] 0 to 4, then a bud or a bloom on top.
 */
@Immutable
data class PlantLook(
    val leaves: Int,
    val bud: Boolean = false,
    val bloom: Boolean = false,
    /** Not tended for a while: drier, drooping leaves. A plant never dies. */
    val resting: Boolean = false,
    /** Tended today: a soft glow around the top. */
    val glowing: Boolean = false
) {
    val isSeed: Boolean get() = leaves == 0 && !bud && !bloom

    /**
     * How tall the drawing is on the design board, from the root to the highest leaf or petal, so a
     * plant's box can hug it and its label sits just above it.
     */
    val boardHeight: Float get() {
        if (isSeed) return 34f
        val n = leaves.coerceIn(0, 4)
        val stemTop = PLANT_H - (30f + n * 18f) - if (bud || bloom) 12f else 0f
        val flowerTop = stemTop - if (bloom) 20f else if (bud) 14f else 0f
        val leafTop = if (n == 0) PLANT_H else PLANT_H - 25f - (n - 1) * 18f - 27f * (1f - 0.08f * (n - 1))
        return PLANT_H - minOf(flowerTop, leafTop) + 6f
    }
}

/** The plant's design board height, shared with [PlantLook.boardHeight]. */
internal const val PLANT_H = 160f

/** The plant's design board. */
private const val W = 96f
private const val H = PLANT_H

private val LEAF_COLOURS = listOf(GardenColors.Leaf1, GardenColors.Leaf2, GardenColors.Leaf3, GardenColors.Leaf4)

/**
 * A plant in the garden, rooted at the bottom centre of its box. It sways gently unless [animate]
 * is off; give each plant a different [swayOffset] so they don't move in step.
 */
@Composable
fun Plant(look: PlantLook, animate: Boolean, modifier: Modifier = Modifier, swayOffset: Int = 0) {
    val angle = if (animate) {
        val transition = rememberInfiniteTransition(label = "sway")
        val a by transition.animateFloat(
            initialValue = -2f, targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(2600 + swayOffset * 450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sway_angle"
        )
        if (look.resting) a * 0.4f else a
    } else 0f
    Canvas(
        modifier.graphicsLayer {
            rotationZ = angle
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) { drawPlant(look) }
}

/**
 * Draws [look] scaled into the canvas, keeping the design's proportions and the root at the bottom.
 * The box may be shorter than the whole board: a box [PlantLook.boardHeight] tall holds the plant.
 */
fun DrawScope.drawPlant(look: PlantLook) {
    val s = minOf(size.width / W, size.height / look.boardHeight)
    val left = (size.width - W * s) / 2f
    val top = size.height - H * s
    withTransform({ translate(left, top); scale(s, s, pivot = Offset.Zero) }) {
        if (look.isSeed) {
            drawSeed()
            return@withTransform
        }
        val stemTop = H - (30f + look.leaves * 18f) - if (look.bud || look.bloom) 12f else 0f
        if (look.glowing) {
            val c = Offset(W / 2f, stemTop + 10f)
            drawCircle(Brush.radialGradient(listOf(GardenColors.Gold.copy(alpha = 0.45f), Color.Transparent), center = c, radius = 46f), radius = 46f, center = c)
        }
        val stem = Path().apply {
            moveTo(W / 2f, H)
            cubicTo(W / 2f, H - 30f, W / 2f + 2f, stemTop + 40f, W / 2f, stemTop)
        }
        drawPath(stem, if (look.resting) GardenColors.StemResting else GardenColors.Stem, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        for (i in 0 until look.leaves.coerceIn(0, 4)) {
            val side = if (i % 2 == 0) -1f else 1f
            val attach = Offset(W / 2f, H - 25f - i * 18f)
            val colour = if (look.resting) GardenColors.LeafResting else LEAF_COLOURS[i]
            drawLeaf(attach, side, scale = 1f - 0.08f * i, droop = if (look.resting) 12f else 0f, colour = colour)
        }
        if (look.bloom) drawBloom(Offset(W / 2f, stemTop)) else if (look.bud) drawBud(Offset(W / 2f, stemTop), look.resting)
    }
}

private fun DrawScope.drawLeaf(a: Offset, side: Float, scale: Float, droop: Float, colour: Color) {
    fun p(dx: Float, dy: Float) = Offset(a.x + dx * side * scale, a.y + dy * scale)
    val tipDrop = droop
    val leaf = Path().apply {
        moveTo(a.x, a.y)
        val c1 = p(18f, 0f); val c2 = p(30f, -11f + tipDrop); val tip = p(32f, -27f + tipDrop)
        cubicTo(c1.x, c1.y, c2.x, c2.y, tip.x, tip.y)
        val c3 = p(15f, -27f + tipDrop); val c4 = p(3f, -17f)
        cubicTo(c3.x, c3.y, c4.x, c4.y, a.x, a.y)
        close()
    }
    drawPath(leaf, colour)
}

private fun DrawScope.drawBud(c: Offset, resting: Boolean) {
    drawOval(if (resting) GardenColors.GoldDeep.copy(alpha = 0.7f) else GardenColors.Gold, topLeft = Offset(c.x - 9f, c.y - 13f), size = Size(18f, 26f))
    val sepal = Path().apply {
        moveTo(c.x - 9f, c.y + 4f)
        cubicTo(c.x - 6f, c.y + 12f, c.x + 6f, c.y + 12f, c.x + 9f, c.y + 4f)
        close()
    }
    drawPath(sepal, if (resting) GardenColors.LeafResting else GardenColors.Leaf2)
}

private fun DrawScope.drawBloom(c: Offset) {
    val petals = listOf(GardenColors.Gold, GardenColors.GoldSoft, GardenColors.GoldDeep)
    petals.forEachIndexed { i, colour ->
        rotate(degrees = i * 60f, pivot = c) {
            drawOval(colour, topLeft = Offset(c.x - 9f, c.y - 18f), size = Size(18f, 36f))
        }
    }
    drawCircle(GardenColors.BloomCore, radius = 7f, center = c)
}

private fun DrawScope.drawSeed() {
    drawOval(GardenColors.Mound, topLeft = Offset(W / 2f - 30f, H - 16f), size = Size(60f, 22f))
    drawOval(GardenColors.Seed, topLeft = Offset(W / 2f - 8f, H - 20f), size = Size(16f, 11f))
    val shoot = Path().apply {
        moveTo(W / 2f + 2f, H - 18f)
        cubicTo(W / 2f + 3f, H - 26f, W / 2f + 8f, H - 30f, W / 2f + 12f, H - 30f)
    }
    drawPath(shoot, GardenColors.Stem, style = Stroke(width = 2f, cap = StrokeCap.Round))
}
