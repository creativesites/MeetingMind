package com.craftflowtechnologies.meetingmind.feature.learn.ui.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.learn.ListenState
import com.craftflowtechnologies.meetingmind.feature.learn.ListenVoice
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.FourPointStar
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.WaveformSeekBar
import com.craftflowtechnologies.meetingmind.ui.theme.GraphiteColors
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.StudyFixedColors

/** How close, in milliseconds, a stressed moment must be before the chip says so. */
private const val StressedHintWindowMs = 20_000L

/** Seek step for the −15 and +15 buttons. */
private const val SkipMs = 15_000L

/**
 * Listen (design/study/Listen.dc.html): the lecture replay with read-along. Always dark, whatever the app theme.
 * The current line is large and washed in accent, earlier lines dim, upcoming lines muted. Tapping a line seeks to it.
 */
@Composable
fun ListenScreen(
    state: ListenState,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onNextStressed: () -> Unit,
    onSpeed: () -> Unit,
    onVoice: (ListenVoice) -> Unit,
    onToggleStopAtSectionEnd: () -> Unit,
    onToggleQuizAfter: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMMColors provides GraphiteColors) {
        StudyTheme {
            val c = MM.colors
            Column(modifier.fillMaxSize().background(c.background)) {
                ListenTopBar(state = state, onClose = onClose, onVoice = onVoice)
                ReaderHeader(state = state)
                ReadAlong(state = state, onSeek = onSeek, modifier = Modifier.weight(1f))
                StressedHint(state = state)
                PlayerControls(
                    state = state,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek,
                    onSkip = onSkip,
                    onNextStressed = onNextStressed,
                    onSpeed = onSpeed,
                    onToggleStopAtSectionEnd = onToggleStopAtSectionEnd,
                    onToggleQuizAfter = onToggleQuizAfter
                )
            }
        }
    }
}

@Composable
private fun ListenTopBar(state: ListenState, onClose: () -> Unit, onVoice: (ListenVoice) -> Unit) {
    val c = MM.colors
    // Lecturer is offered only when the lecture has an audio file.
    val voices = if (state.hasLecturerAudio) ListenVoice.entries else listOf(ListenVoice.READ_TO_ME)
    Row(
        Modifier.fillMaxWidth().padding(start = MM.space.s, end = MM.space.s, top = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.ExpandMore, contentDescription = "Close player", tint = c.ink)
        }
        Box(Modifier.weight(1f).padding(horizontal = MM.space.s), contentAlignment = Alignment.Center) {
            SegmentedControl(
                options = voices.map { it.label },
                selectedIndex = voices.indexOf(state.voice).coerceAtLeast(0),
                onSelect = { index -> onVoice(voices[index]) },
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
        Spacer(Modifier.size(MMSize.minTouch))
    }
}

@Composable
private fun ReaderHeader(state: ListenState) {
    val c = MM.colors
    val context = listOfNotNull(state.courseName, state.sectionLabel).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = MM.space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        ZuriSlot(page = CompanionPage.READ_ALOUD, size = 48.dp, state = CompanionState.READING)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (context.isNotEmpty()) {
                Text(
                    context,
                    style = MM.type.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = c.inkMuted
                )
            }
            Text(state.title, style = MM.type.heading, color = c.ink)
        }
    }
}

/** The read-along lines. The list follows [ListenState.currentLine] as the lecture plays. */
@Composable
private fun ReadAlong(state: ListenState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = MM.colors
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLine) {
        if (state.currentLine in state.lines.indices) {
            // Keep one line of the previous sentence in view, so the current line sits near the top.
            listState.animateScrollToItem((state.currentLine - 1).coerceAtLeast(0))
        }
    }
    if (state.lines.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(24.dp)) {
            Text("The words will follow along here.", style = MM.type.title, color = c.inkMuted)
        }
        return
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(state.lines) { index, line ->
            val current = index == state.currentLine
            val color = when {
                current -> c.ink
                index < state.currentLine -> c.inkFaint
                else -> c.inkMuted
            }
            Text(
                line.text,
                style = MM.type.title.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MM.radius.small)
                    .then(if (current) Modifier.background(c.accentWash) else Modifier)
                    .clickable(role = Role.Button, onClick = { onSeek(line.startMs) })
                    .padding(horizontal = MM.space.s, vertical = MM.space.xs)
            )
        }
    }
}

/** A gold chip when a stressed moment is coming within the next 20 seconds. */
@Composable
private fun StressedHint(state: ListenState) {
    val next = state.stressedAtMs.firstOrNull { it >= state.positionMs } ?: return
    val ahead = next - state.positionMs
    if (ahead > StressedHintWindowMs) return
    val c = MM.colors
    val seconds = ((ahead + 999L) / 1000L).coerceAtLeast(1L)
    Row(
        Modifier
            .padding(start = 22.dp, top = MM.space.s)
            .clip(MM.radius.card)
            .background(c.surfaceRaised)
            .padding(horizontal = MM.space.m, vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        FourPointStar(color = StudyFixedColors.Gold, dimension = 14.dp)
        Text("Stressed moment in $seconds s", style = MM.type.caption, color = c.goldInk)
    }
}

@Composable
private fun PlayerControls(
    state: ListenState,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onNextStressed: () -> Unit,
    onSpeed: () -> Unit,
    onToggleStopAtSectionEnd: () -> Unit,
    onToggleQuizAfter: () -> Unit
) {
    val c = MM.colors
    val duration = state.durationMs.coerceAtLeast(1L)
    val position = state.positionMs.coerceIn(0L, duration)
    val markers = state.stressedAtMs.map { (it.toFloat() / duration).coerceIn(0f, 1f) }
    Column(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = MM.space.l, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        WaveformSeekBar(
            bars = state.waveform,
            progress = position.toFloat() / duration,
            markers = markers,
            description = "Lecture position, ${formatLectureTime(position)} of ${formatLectureTime(duration)}",
            onSeekFraction = { onSeek((it * duration).toLong()) },
            height = 34.dp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatLectureTime(position), style = MM.type.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.inkMuted)
            Text(
                "−" + formatLectureTime((duration - position).coerceAtLeast(0L)),
                style = MM.type.caption.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = c.inkMuted
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SpeedPill(speed = state.speed, onClick = onSpeed)
            SkipButton(description = "Back 15 seconds", forward = false, onClick = { onSkip(-SkipMs) })
            PlayButton(isPlaying = state.isPlaying, onClick = onTogglePlay)
            SkipButton(description = "Forward 15 seconds", forward = true, onClick = { onSkip(SkipMs) })
            NextStressedButton(onClick = onNextStressed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s, Alignment.CenterHorizontally)) {
            MMChip(label = "Stop at end of section", selected = state.stopAtSectionEnd, onClick = onToggleStopAtSectionEnd)
            MMChip(label = "Quiz me after", selected = state.quizAfter, onClick = onToggleQuizAfter)
        }
    }
}

/** "1×", "1.25×": the playback speed; tap to change it. */
internal fun speedLabel(speed: Float): String {
    val number = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "$number×"
}

@Composable
private fun SpeedPill(speed: Float, onClick: () -> Unit) {
    val c = MM.colors
    Box(
        Modifier
            .heightIn(min = MMSize.minTouch)
            .widthIn(min = 56.dp)
            .clip(MM.radius.pill)
            .border(MMSize.hairline, c.line, MM.radius.pill)
            .clickable(role = Role.Button, onClickLabel = "Change speed", onClick = onClick)
            .padding(horizontal = MM.space.m),
        contentAlignment = Alignment.Center
    ) {
        Text(speedLabel(speed), style = MM.type.bodyStrong, color = c.ink)
    }
}

@Composable
private fun SkipButton(description: String, forward: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    Box(
        Modifier
            .size(52.dp)
            .clip(MM.radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Replay,
            contentDescription = null,
            tint = c.ink,
            modifier = Modifier.size(30.dp).graphicsLayer { scaleX = if (forward) -1f else 1f }
        )
        Text("15", style = MM.type.overline.copy(fontWeight = FontWeight.Bold), color = c.ink)
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    Box(
        Modifier
            .size(76.dp)
            .clip(MM.radius.pill)
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = c.onAccent,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun NextStressedButton(onClick: () -> Unit) {
    val c = MM.colors
    Box(
        Modifier
            .width(56.dp)
            .height(48.dp)
            .clip(MM.radius.pill)
            .border(MMSize.hairline, c.line, MM.radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Next stressed moment" },
        contentAlignment = Alignment.Center
    ) {
        FourPointStar(color = StudyFixedColors.Gold, dimension = 16.dp)
    }
}
