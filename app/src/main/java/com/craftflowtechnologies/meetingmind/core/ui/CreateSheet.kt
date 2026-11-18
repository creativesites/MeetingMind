package com.craftflowtechnologies.meetingmind.core.ui

import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent

enum class CreateAction { RECORD, NOTE, MEDIA, IMPORT, YOUTUBE }

/**
 * What the bar's New action opens. Record comes first and largest: it is still the thing people
 * reach for most, and it must stay one tap from the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(onPick: (CreateAction) -> Unit, onDismiss: () -> Unit) {
    // The navigation bar read from the app's window: inside the sheet's own window the insets can come
    // through as zero (3-button navigation on some phones), which drew the last line under the bar.
    val appNavBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        // The content pads for the navigation bar itself (below), so the sheet adds none.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        val sheetNavBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp + maxOf(appNavBar, sheetNavBar))) {
            Text("Create", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.3).sp)
            Spacer(Modifier.height(14.dp))
            Surface(onClick = { onPick(CreateAction.RECORD) }, shape = RoundedCornerShape(20.dp), color = Accent, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = OnAccent.copy(alpha = 0.16f), modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Mic, contentDescription = null, tint = OnAccent, modifier = Modifier.size(22.dp)) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Record", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = OnAccent)
                        Text("A meeting, sermon, lecture or thought", fontSize = 13.sp, color = OnAccent.copy(alpha = 0.72f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CreateTile("Write a note", Icons.Filled.EditNote, Modifier.weight(1f)) { onPick(CreateAction.NOTE) }
                CreateTile("Photos & video", Icons.Filled.PhotoLibrary, Modifier.weight(1f)) { onPick(CreateAction.MEDIA) }
                CreateTile("Import a file", Icons.Filled.FileUpload, Modifier.weight(1f)) { onPick(CreateAction.IMPORT) }
            }
            Spacer(Modifier.height(10.dp))
            // Notes from a YouTube link: its own card, so it is never hidden behind "Import".
            YouTubeCard { onPick(CreateAction.YOUTUBE) }
            Text(
                "Every recording gets its own note, so what you write and what was said stay together.",
                fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 14.dp, start = 2.dp)
            )
        }
    }
}

@Composable
private fun CreateTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = modifier) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 12.dp)) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = InkSecondary)
        }
    }
}

/**
 * "Notes from a YouTube video": second only to Record. A video frame with a play mark and a
 * scrubber, a benefit line and a "New" badge, on a soft accent-to-violet wash that follows the
 * theme (light, dark, any accent). One light sweep crosses it as the sheet opens; none with
 * reduced motion. No YouTube logo or red: the glyph is a generic play mark.
 */
@Composable
private fun YouTubeCard(onClick: () -> Unit) {
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
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 10.dp.toPx()
            }
            .testTag("create_youtube")
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
                Text("Notes from a YouTube video", style = MM.type.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("Paste a link, get study notes with timestamps", style = MM.type.secondary, color = c.inkSecondary)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.accentWash), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private const val SweepMs = 1100
