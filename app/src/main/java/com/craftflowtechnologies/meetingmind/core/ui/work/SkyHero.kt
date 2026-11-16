package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.timeline.TimeOfDaySky
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.WorkShape
import java.time.LocalTime

/**
 * The sky hero: a gradient that follows the time of day (the [TimeOfDaySky] keyframes), with a sun,
 * a moon and stars, soft hills along the bottom, and a 30 dp radius. The content sits on the left
 * in white, for a time label, a greeting and one sentence.
 *
 * The body moves on an arc: the sun rises on the left, is highest at midday and sets on the right.
 * Its height follows [TimeOfDaySky.elevation]. The sky is static: nothing animates while it is shown.
 *
 * @param time the time the sky is drawn for.
 * @param height the hero's height (default 280 dp).
 * @param content the text, laid out on the left in white.
 */
@Composable
fun SkyHero(
    time: LocalTime,
    modifier: Modifier = Modifier,
    height: Dp = 280.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val sky = TimeOfDaySky.at(time.hour, time.minute)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(WorkShape.sky)
            .border(1.dp, FixedWhite.copy(alpha = 0.35f), WorkShape.sky)
            .background(Brush.verticalGradient(listOf(Color(sky.skyTop), Color(sky.skyBottom))))
    ) {
        Canvas(Modifier.fillMaxSize()) { drawSkyScene(sky) }
        CompositionLocalProvider(LocalContentColor provides FixedWhite) {
            content()
        }
    }
}

/** Star positions as fractions of the hero's width and height. They show only in the moon phases. */
private val StarFractions = listOf(
    0.08f to 0.14f, 0.21f to 0.08f, 0.39f to 0.20f, 0.55f to 0.10f,
    0.68f to 0.24f, 0.32f to 0.32f, 0.79f to 0.14f, 0.16f to 0.38f
)

private val HillHeight: Dp = 70.dp
private val TopMargin: Dp = 28.dp

/** Draws the sky's decoration: the top light, the stars, the sun or moon, and the hills. */
private fun DrawScope.drawSkyScene(sky: TimeOfDaySky) {
    val w = size.width
    val h = size.height

    // A light wash across the top third.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(FixedWhite.copy(alpha = 0.16f), Color.Transparent),
            startY = 0f,
            endY = h * 0.45f
        )
    )

    // Stars: only as visible as the sky's star value allows.
    if (sky.stars > 0f) {
        val starRadius = 1.dp.toPx()
        StarFractions.forEach { (fx, fy) ->
            drawCircle(
                color = FixedWhite.copy(alpha = 0.7f * sky.stars),
                radius = starRadius,
                center = Offset(w * fx, h * fy)
            )
        }
    }

    // The body. Horizontal position follows the arc across the right side; height follows the elevation.
    val hillPx = HillHeight.toPx()
    val horizonY = h - hillPx
    val orbRadius = (if (sky.isMoon) 40.dp else 46.dp).toPx() / 2f
    val topY = TopMargin.toPx()
    val centreX = w * (0.58f + 0.30f * sky.arc)
    val centreY = horizonY - orbRadius - sky.elevation * (horizonY - topY - orbRadius * 2f)
    val centre = Offset(centreX, centreY)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(sky.glow).copy(alpha = 0.4f), Color.Transparent),
            center = centre,
            radius = orbRadius * 2.5f
        ),
        radius = orbRadius * 2.5f,
        center = centre
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(sky.orbLight), Color(sky.orbBody), Color(sky.orbDeep)),
            center = centre,
            radius = orbRadius
        ),
        radius = orbRadius,
        center = centre
    )

    // Hills: two soft bands in the bottom 70 dp. Points are in the design's 380 x 70 space.
    fun pt(px: Float, py: Float) = Offset(px * w / 380f, horizonY + py * hillPx / 70f)
    val back = Path().apply {
        val start = pt(0f, 58f)
        moveTo(start.x, start.y)
        val c1 = pt(80f, 40f); val c2 = pt(150f, 50f); val e1 = pt(230f, 58f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        val c3 = pt(300f, 64f); val c4 = pt(340f, 50f); val e2 = pt(380f, 54f)
        cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        val br = pt(380f, 70f); lineTo(br.x, br.y)
        val bl = pt(0f, 70f); lineTo(bl.x, bl.y)
        close()
    }
    val front = Path().apply {
        val start = pt(0f, 46f)
        moveTo(start.x, start.y)
        val c1 = pt(60f, 22f); val c2 = pt(120f, 30f); val e1 = pt(190f, 44f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        val c3 = pt(250f, 56f); val c4 = pt(310f, 30f); val e2 = pt(380f, 38f)
        cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        val br = pt(380f, 70f); lineTo(br.x, br.y)
        val bl = pt(0f, 70f); lineTo(bl.x, bl.y)
        close()
    }
    drawPath(back, color = FixedWhite.copy(alpha = 0.12f))
    drawPath(front, color = FixedWhite.copy(alpha = 0.14f))
}
