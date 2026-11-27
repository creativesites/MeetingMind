package com.craftflowtechnologies.meetingmind.feature.learn.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.feature.learn.SkyStar
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.StudyFixedColors
import kotlinx.coroutines.launch

/*
 * The night sky of Study: each concept is a star, its state shown by shape (never colour alone).
 * Mockup: design/study/Main.dc.html, "Your sky". Sizes are dp so the sky reads the same on any phone.
 */

/** The mockup's 352 x 190 box. */
private const val SkyAspect = 352f / 190f
private const val HaloAlpha = 0.18f
private val HaloRadius: Dp = 12.dp
private val CoreRadius: Dp = 4.5.dp
private val RingRadius: Dp = 3.5.dp
private val RingStroke: Dp = 1.6.dp
private val NewStroke: Dp = 1.2.dp
private val DashLength: Dp = 2.dp
private val SparkleRadius: Dp = 6.5.dp
private val LineStroke: Dp = 1.dp
private val LabelGap: Dp = 8.dp
private val DotRadius: Dp = 0.9.dp

/** Faint fixed background stars, as fractions of the box. */
private val BackgroundDots = listOf(
    0.057f to 0.842f, 0.938f to 0.126f, 0.511f to 0.926f, 0.710f to 0.063f, 0.034f to 0.21f
)

/** How an ignited star grows: 0 to 1.6 (overshoot) to 1, over 0.9 s. */
private val IgniteSpec = keyframes<Float> {
    durationMillis = 900
    0f at 0
    1.6f at 450
    1f at 900
}

/**
 * Draws [sky] on a rounded dark panel. Stars in [ignite] light up: they grow from nothing, overshoot
 * and settle, with a pulsing halo. Reduced motion (the companion's setting, or the system's) skips
 * every animation and draws the final state.
 */
@Composable
fun SkyView(
    sky: SkyState,
    ignite: Set<String>,
    modifier: Modifier = Modifier
) {
    val reduced = LocalCompanionReducedMotion.current ?: MM.motion.reduced
    val twinkle = rememberTwinkle(reduced)
    val pulse = rememberHaloPulse(reduced)
    val scales = remember { HashMap<String, Animatable<Float, AnimationVector1D>>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ignite, reduced) {
        if (reduced) return@LaunchedEffect
        ignite.filter { it !in scales }.forEach { id ->
            val anim = Animatable(0f)
            scales[id] = anim
            scope.launch { anim.animateTo(1f, IgniteSpec) }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MM.type.caption.copy(color = StudyFixedColors.Label)
    val description = skySummary(sky)

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(SkyAspect)
            .clip(MM.radius.card)
            .background(StudyFixedColors.Night)
            .semantics { contentDescription = description }
    ) {
        val w = size.width
        val h = size.height
        fun at(star: SkyStar) = Offset(star.x * w, star.y * h)

        BackgroundDots.forEach { (x, y) ->
            drawCircle(StudyFixedColors.Ink.copy(alpha = 0.45f), DotRadius.toPx(), Offset(x * w, y * h))
        }

        val byId = sky.stars.associateBy { it.conceptId }
        sky.links.forEach { link ->
            val from = byId[link.fromId] ?: return@forEach
            val to = byId[link.toId] ?: return@forEach
            drawLine(StudyFixedColors.Star.copy(alpha = 0.4f), at(from), at(to), strokeWidth = LineStroke.toPx())
        }

        sky.stars.forEach { star ->
            val igniting = !reduced && star.conceptId in ignite
            val scale = if (reduced) 1f else scales[star.conceptId]?.value ?: 1f
            drawSkyStar(
                state = star.star,
                center = at(star),
                scale = scale,
                twinkle = twinkle.value,
                pulse = if (igniting) pulse.value else null
            )
        }

        sky.stars.forEach { star ->
            val c = at(star)
            val layout: TextLayoutResult = textMeasurer.measure(star.label, labelStyle)
            val gap = LabelGap.toPx()
            val fitsRight = c.x + gap + layout.size.width <= w
            val x = if (fitsRight) c.x + gap else c.x - gap - layout.size.width
            val y = (c.y - layout.size.height / 2f).coerceIn(0f, (h - layout.size.height).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x.coerceAtLeast(0f), y))
        }
    }
}

/** A plain-words summary for TalkBack, e.g. "5 shining, 4 growing, 3 new, 2 worth a look". */
internal fun skySummary(sky: SkyState): String {
    if (sky.stars.isEmpty()) return "No ideas in the sky yet"
    return listOf(
        "${sky.count(StarState.SHINING)} shining",
        "${sky.count(StarState.GROWING)} growing",
        "${sky.count(StarState.NEW)} new",
        "${sky.count(StarState.WORTH_A_LOOK)} worth a look"
    ).joinToString(", ")
}

/**
 * The key to the sky: one mark and one word per state, with the count. Marks use the same shapes as
 * the stars, drawn in the page's own colours so they read on Paper and Midnight alike.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkyLegend(sky: SkyState, modifier: Modifier = Modifier) {
    val accent = MM.colors.accent
    val muted = MM.colors.inkMuted
    val gold = MM.colors.gold
    val secondary = MM.colors.inkSecondary
    val labelStyle = MM.type.caption.copy(color = secondary)
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(MM.space.l),
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        LegendItem(StarState.SHINING, "${sky.count(StarState.SHINING)} shining", accent, labelStyle)
        LegendItem(StarState.GROWING, "${sky.count(StarState.GROWING)} growing", accent, labelStyle)
        LegendItem(StarState.NEW, "${sky.count(StarState.NEW)} new", muted, labelStyle)
        LegendItem(StarState.WORTH_A_LOOK, "${sky.count(StarState.WORTH_A_LOOK)} worth a look", gold, labelStyle)
    }
}

@Composable
private fun LegendItem(state: StarState, text: String, color: Color, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(LegendMark)) { drawLegendMark(state, color) }
        Spacer(Modifier.width(MM.space.s))
        Text(text, style = style)
    }
}

private val LegendMark: Dp = 10.dp

private fun DrawScope.drawLegendMark(state: StarState, color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    when (state) {
        StarState.SHINING -> drawCircle(color, 4.dp.toPx(), c)
        StarState.GROWING -> drawCircle(color, 3.5.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
        StarState.NEW -> drawCircle(color, 3.5.dp.toPx(), c, style = dashedStroke(1.2.dp.toPx()))
        StarState.WORTH_A_LOOK -> drawSparkle(c, 5.dp.toPx(), color)
    }
}

private fun DrawScope.drawSkyStar(
    state: StarState,
    center: Offset,
    scale: Float,
    twinkle: Float,
    pulse: Float?
) {
    if (scale <= 0f) return
    when (state) {
        StarState.SHINING -> {
            val halo = HaloAlpha * (if (pulse == null) 1f else 1f + 0.6f * pulse)
            drawCircle(StudyFixedColors.Star.copy(alpha = halo), HaloRadius.toPx() * scale, center)
            val core = if (pulse != null && scale > 1.05f) StudyFixedColors.StarCore else StudyFixedColors.Star
            drawCircle(core, CoreRadius.toPx() * scale, center)
        }
        StarState.GROWING -> drawCircle(
            StudyFixedColors.Star, RingRadius.toPx() * scale, center, style = Stroke(RingStroke.toPx())
        )
        StarState.NEW -> drawCircle(
            StudyFixedColors.Faint, RingRadius.toPx() * scale, center, style = dashedStroke(NewStroke.toPx())
        )
        StarState.WORTH_A_LOOK -> drawSparkle(
            center, SparkleRadius.toPx() * scale, StudyFixedColors.Gold.copy(alpha = twinkle)
        )
    }
}

private fun DrawScope.dashedStroke(width: Float): Stroke {
    val dash = DashLength.toPx()
    return Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
}

/** A four-point sparkle: four concave curves meeting at the points. */
private fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    val k = r * 0.18f
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + k, center.y - k, center.x + r, center.y)
        quadraticTo(center.x + k, center.y + k, center.x, center.y + r)
        quadraticTo(center.x - k, center.y + k, center.x - r, center.y)
        quadraticTo(center.x - k, center.y - k, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}

/**
 * Gold sparkles twinkle between full and 45% opacity. Under reduced motion no infinite animation
 * is created at all, so the sky stays still and the composition can go idle.
 */
@Composable
private fun rememberTwinkle(reduced: Boolean): State<Float> {
    if (reduced) return remember { mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "sky twinkle")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200), RepeatMode.Reverse),
        label = "sky twinkle"
    )
}

/** The halo pulse of stars that are lighting up. Absent under reduced motion. */
@Composable
private fun rememberHaloPulse(reduced: Boolean): State<Float> {
    if (reduced) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "sky halo")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "sky halo"
    )
}
