package com.craftflowtechnologies.meetingmind.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The mark for anything AI does: a speech bubble holding three dots — an assistant you talk to,
 * not a sparkle. Drawn as an outline so it takes the tint of wherever it sits.
 */
val AiMark: ImageVector by lazy {
    ImageVector.Builder("AiMark", 24.dp, 24.dp, 24f, 24f).apply {
        // Bubble with a tail at the bottom left.
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathFillType = PathFillType.NonZero
        ) {
            moveTo(7.5f, 19.2f)
            lineTo(4.4f, 21.0f)
            lineTo(5.1f, 17.4f)
            curveTo(3.8f, 16.2f, 3.0f, 14.6f, 3.0f, 12.8f)
            curveTo(3.0f, 8.9f, 6.9f, 5.8f, 12.0f, 5.8f)
            curveTo(17.1f, 5.8f, 21.0f, 8.9f, 21.0f, 12.8f)
            curveTo(21.0f, 16.7f, 17.1f, 19.8f, 12.0f, 19.8f)
            curveTo(10.3f, 19.8f, 8.8f, 19.6f, 7.5f, 19.2f)
            close()
        }
        // Three dots.
        for (x in listOf(8.4f, 12.0f, 15.6f)) {
            path(fill = SolidColor(Color.Black)) {
                moveTo(x - 1.15f, 12.8f)
                arcToRelative(1.15f, 1.15f, 0f, true, true, 2.3f, 0f)
                arcToRelative(1.15f, 1.15f, 0f, true, true, -2.3f, 0f)
                close()
            }
        }
    }.build()
}
