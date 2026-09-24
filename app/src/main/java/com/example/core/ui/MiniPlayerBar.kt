package com.example.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.PlaybackController
import com.example.core.audio.PlaybackPhase
import com.example.core.audio.PlaybackState
import com.example.core.common.Formatters

/** What's playing, as the player shows it: a devotional's voice, a preview, or a recording. */
private data class Artwork(val icon: ImageVector, val colors: List<Color>, val kind: String)

private fun artworkFor(state: PlaybackState): Artwork = when {
    state.recordingId?.startsWith("devotional:") == true -> Artwork(Icons.Filled.WbSunny, listOf(Color(0xFF1B1530), Color(0xFFB7791F)), "Today's devotional")
    state.recordingId?.startsWith("preview") == true -> Artwork(Icons.Filled.GraphicEq, listOf(Color(0xFF312E81), Color(0xFF7C3AED)), "Voice preview")
    state.recordingId?.startsWith("media:") == true -> Artwork(Icons.Filled.GraphicEq, listOf(Color(0xFF3A2A1A), Color(0xFFB7791F)), "Voice")
    else -> Artwork(Icons.Filled.Mic, listOf(Color(0xFF1E1B4B), Color(0xFFE11D48)), "Recording")
}

/**
 * The player that follows you around the app whenever something is loaded: a floating pill with
 * the artwork, a live equaliser, a progress ring and skip controls; tap it for the full player.
 */
@Composable
fun MiniPlayerBar(
    state: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Room left below for the floating navigation bar on the main tabs. */
    bottomOffset: Dp = 0.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val art = artworkFor(state)
    val playing = state.isPlaying
    val progress = if (state.durationMs > 0L) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        onClick = { expanded = true },
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF20F172A),
        shadowElevation = 12.dp,
        modifier = modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp + bottomOffset).fillMaxWidth().testTag("mini_player_bar")
    ) {
        Row(Modifier.padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(art.colors)), contentAlignment = Alignment.Center) {
                if (playing) Equaliser(Color.White) else Icon(art.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(state.title.ifBlank { art.kind }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("mini_player_title"))
                Text("${art.kind} · ${Formatters.formatDurationHms(state.positionMs)} / ${Formatters.formatDurationHms(state.durationMs)}",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.5.sp, maxLines = 1)
            }
            IconButton(onClick = { PlaybackController.seekBy(-10_000) }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            }
            // Play/pause inside a ring that fills as it plays.
            Box(Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onTogglePlayPause).testTag("mini_player_play_pause"), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(42.dp)) {
                    drawCircle(Color.White.copy(alpha = 0.15f), style = Stroke(3.dp.toPx()))
                    drawArc(Color(0xFFF6D365), -90f, 360f * progress, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                if (state.phase == PlaybackPhase.LOADING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onStop, modifier = Modifier.size(38.dp).testTag("mini_player_stop")) {
                Icon(Icons.Filled.Close, contentDescription = "Stop", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
    if (expanded) NowPlayingSheet(state, art, onTogglePlayPause, onStop = { expanded = false; onStop() }, onOpen = { expanded = false; onOpen() }, onDismiss = { expanded = false })
}

/** Three little bars that dance while audio plays. */
@Composable
private fun Equaliser(color: Color) {
    val t = rememberInfiniteTransition(label = "eq")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(800, easing = LinearEasing)), label = "p")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(18.dp)) {
        repeat(3) { i ->
            val h = 5f + 13f * ((kotlin.math.sin(phase + i * 1.9f) + 1f) / 2f)
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingSheet(state: PlaybackState, art: Artwork, onTogglePlayPause: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val speed by PlaybackController.speed.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1L)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF0F172A), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(art.colors)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(260.dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), center = Offset(500f, 120f))))
                if (state.isPlaying) Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { Equaliser(Color.White) }
                else Icon(art.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Text(art.kind.uppercase(), fontSize = 11.sp, letterSpacing = 1.2.sp, color = Color(0xFFF6D365), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp))
            Text(state.title.ifBlank { art.kind }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Slider(
                value = if (dragging) dragValue else state.positionMs.toFloat() / duration,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { PlaybackController.seekTo((dragValue * duration).toLong()); dragging = false },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFF6D365), inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            Row(Modifier.fillMaxWidth()) {
                Text(Formatters.formatDurationHms(if (dragging) (dragValue * duration).toLong() else state.positionMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.weight(1f))
                Text("−" + Formatters.formatDurationHms((state.durationMs - state.positionMs).coerceAtLeast(0)), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                IconButton(onClick = { PlaybackController.seekBy(-10_000) }) { Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White).clickable(onClick = onTogglePlayPause), contentAlignment = Alignment.Center) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (state.isPlaying) "Pause" else "Play", tint = Color(0xFF0F172A), modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { PlaybackController.seekBy(10_000) }) { Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.8f, 1f, 1.25f, 1.5f).forEach { s ->
                    Surface(onClick = { PlaybackController.setSpeed(s) }, shape = RoundedCornerShape(50), color = if (speed == s) Color.White else Color.White.copy(alpha = 0.1f)) {
                        Text("${if (s % 1f == 0f) s.toInt() else s}×", fontSize = 13.sp, color = if (speed == s) Color(0xFF0F172A) else Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = onOpen, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.12f), modifier = Modifier.testTag("mini_player_open")) {
                    Text("Open", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
                }
                Surface(onClick = onStop, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.12f)) {
                    Text("Stop", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
                }
            }
        }
    }
}
