package com.craftflowtechnologies.meetingmind.core.ui.work

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlin.math.roundToInt

/**
 * A progress ring for "done of total": a track, an accent sweep, and the percentage in the centre.
 *
 * The sweep animates once, when the ring first appears and whenever its value changes, using the
 * standard motion token. With "Remove animations" on, it lands on its end state at once. It never
 * animates while idle.
 *
 * @param done items finished so far.
 * @param total items in all. Zero shows an empty ring.
 * @param size the outer diameter (46 dp in a row, 64 dp for a hero).
 */
@Composable
fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val colors = MM.colors
    val motion = MM.motion
    val target = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(target) { sweep.animateTo(target, motion.enter<Float>()) }

    val strokeWidth = 5.dp
    val percent = (target * 100).roundToInt()
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = "$done of $total done" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = strokeWidth.toPx() / 2f
            val arc = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = colors.track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = stroke
            )
            if (sweep.value > 0f) {
                drawArc(
                    color = colors.accent,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = stroke
                )
            }
        }
        Text(
            text = "$percent%",
            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink
        )
    }
}
