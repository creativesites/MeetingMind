package com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.craftflowtechnologies.meetingmind.ui.theme.StudyFixedColors

/**
 * Small drawn glyphs for Study: the gold four-point star (stressed moments) and Zuri's orb (the Ask button).
 * Drawn in code so they follow the mockups exactly and need no image assets.
 */

/** Draws a four-point star of [radius] around [center]. The star's points sit on the radius. */
internal fun DrawScope.drawFourPointStar(center: Offset, radius: Float, color: Color) {
    val s = radius / 6f
    val points = listOf(
        0f to -6f, 1.6f to -1.6f, 6f to 0f, 1.6f to 1.6f,
        0f to 6f, -1.6f to 1.6f, -6f to 0f, -1.6f to -1.6f
    )
    val path = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            val px = center.x + x * s
            val py = center.y + y * s
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, color)
}

/** A gold four-point star, for "stressed" marks and the stressed-moment chips. */
@Composable
internal fun FourPointStar(color: Color, dimension: Dp, modifier: Modifier = Modifier, description: String? = null) {
    val described = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.size(dimension).then(described)) {
        drawFourPointStar(center = Offset(size.width / 2f, size.height / 2f), radius = size.minDimension / 2f, color = color)
    }
}

/** Zuri's orb: a teal sphere with two eyes, the mockups' Ask glyph. Colours are the fixed Study orb palette. */
@Composable
internal fun ZuriOrbGlyph(dimension: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(dimension)) {
        val s = size.minDimension / 100f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(StudyFixedColors.OrbLight, StudyFixedColors.OrbMid, StudyFixedColors.OrbDeep),
                center = Offset(36f * s, 30f * s),
                radius = 80f * s
            ),
            radius = 40f * s,
            center = Offset(50f * s, 52f * s)
        )
        drawOval(StudyFixedColors.Night, topLeft = Offset(34f * s, 40f * s), size = Size(12f * s, 16f * s))
        drawOval(StudyFixedColors.Night, topLeft = Offset(54f * s, 40f * s), size = Size(12f * s, 16f * s))
    }
}
