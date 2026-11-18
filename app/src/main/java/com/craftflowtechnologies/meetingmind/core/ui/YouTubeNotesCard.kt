package com.craftflowtechnologies.meetingmind.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * "Notes from a YouTube video": the one card for it everywhere (the Create sheet, Today and every
 * home), second only to Record. A video frame with a play mark and a
 * scrubber, a benefit line and a "New" badge, on a soft accent-to-violet wash that follows the
 * theme (light, dark, any accent). One light sweep crosses it as the sheet opens; none with
 * reduced motion. No YouTube logo or red: the glyph is a generic play mark.
 *
 * It fills the width. Callers pass their own testTag and padding in [modifier]; [title] and [benefit]
 * change only the words (Faith: "Sermon from a YouTube video", Learn: "Lecture from a YouTube video").
 */
@Composable
fun YouTubeNotesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Notes from a YouTube video",
    benefit: String = "Paste a link, get study notes with timestamps"
) {
    val c = MM.colors
    val motion = MM.motion
    val start = c.accent
    val end = c.speaker2
    val washAlpha = if (c.isDark) 0.20f else 0.11f
    val wash = Brush.linearGradient(listOf(start.copy(alpha = washAlpha), end.copy(alpha = washAlpha * 0.8f)))
    val edge = Brush.linearGradient(listOf(start.copy(alpha = 0.45f), end.copy(alpha = 0.30f)))
    val frame = Brush.linearGradient(listOf(start, end))
    val shape = MM.radius.card

    // Entrance: rises in just after Record; snaps into place with reduced motion.
    val enter = remember { Animatable(if (motion.reduced) 1f else 0f) }
    // One sweep of light across the card, then still.
    val sweep = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(motion.reduced) {
        if (motion.reduced) return@LaunchedEffect
        enter.animateTo(1f, motion.enter())
        sweep.animateTo(1f, tween(durationMillis = (SweepMs * motion.durationScale).toInt().coerceAtLeast(1), easing = LinearEasing))
    }
    // A light sheen in both themes: the page colour on light, the text colour (light) on dark.
    val shine = if (c.isDark) c.ink else c.surface

    Surface(
        onClick = onClick,
        shape = shape,
        color = c.surface,
        border = BorderStroke(1.dp, edge),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 10.dp.toPx()
            }
    ) {
        Row(
            Modifier
                .background(wash)
                .drawWithContent {
                    drawContent()
                    val p = sweep.value
                    if (p in 0.001f..0.999f) {
                        val band = size.width * 0.35f
                        val x = -band + (size.width + band * 2) * p
                        drawRect(
                            Brush.linearGradient(
                                listOf(shine.copy(alpha = 0f), shine.copy(alpha = if (c.isDark) 0.16f else 0.45f), shine.copy(alpha = 0f)),
                                start = Offset(x - band, 0f),
                                end = Offset(x + band, size.height)
                            )
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The video frame: a gradient screen, a bold play mark and a scrubber part-way along, with the
            // "New" sticker on its corner (beside the title it cut the title short).
            Box {
            Box(
                Modifier
                    .padding(top = 6.dp, start = 6.dp)
                    .size(width = 66.dp, height = 46.dp)
                    .clip(MM.radius.small)
                    .background(frame)
            ) {
                Box(
                    Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape).background(c.onAccent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp)
                        .height(3.dp).clip(CircleShape).background(c.onAccent.copy(alpha = 0.30f))
                ) {
                    Box(Modifier.fillMaxWidth(0.42f).fillMaxHeight().clip(CircleShape).background(c.onAccent.copy(alpha = 0.92f)))
                }
            }
            Box(
                Modifier.align(Alignment.TopStart).clip(MM.radius.pill).background(c.surface).padding(1.5.dp)
                    .clip(MM.radius.pill).background(frame).padding(horizontal = 6.dp)
            ) {
                Text("New", style = MM.type.caption, fontWeight = FontWeight.SemiBold, color = c.onAccent)
            }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                // The whole title always shows; at large text sizes it wraps to a second line.
                Text(title, style = MM.type.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(benefit, style = MM.type.secondary, color = c.inkSecondary)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.accentWash), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private const val SweepMs = 1100
