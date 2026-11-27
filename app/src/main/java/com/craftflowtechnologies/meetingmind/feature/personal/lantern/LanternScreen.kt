package com.craftflowtechnologies.meetingmind.feature.personal.lantern

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.audio.RecordingState
import com.craftflowtechnologies.meetingmind.core.model.RecordingContext
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.feature.personal.GardenSerif
import com.craftflowtechnologies.meetingmind.feature.recording.RecordingViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.GardenColors
import com.craftflowtechnologies.meetingmind.ui.theme.InterFamily
import com.craftflowtechnologies.meetingmind.ui.theme.LanternPalette
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.rememberSystemAnimationScale
import kotlin.math.PI
import kotlin.math.sin

/** What the lantern is doing. */
enum class LanternPhase { NEEDS_MICROPHONE, STARTING, LISTENING, PAUSED, FAILED }

/**
 * Answering the day's question out loud: the question at the top, a warm light that answers the
 * voice, and a gentle second question once the person has been talking a while. The answer is a
 * private one-speaker Journal recording, processed into a note like any other.
 *
 * The recorder is the same service the main recorder uses, so the notification, recovery after a
 * crash and the processing that follows all behave the same.
 */
@Composable
fun LanternHost(
    question: String,
    viewModel: RecordingViewModel,
    onClose: () -> Unit,
    onComplete: (meetingId: String, audioPath: String, durationMs: Long) -> Unit,
    onWriteInstead: () -> Unit
) {
    val context = LocalContext.current
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val prefs by viewModel.preferencesState.collectAsState()
    val state by viewModel.state.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    var started by rememberSaveable { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val animate = rememberSystemAnimationScale() > 0f

    fun start() {
        started = true
        viewModel.startRecording(
            question,
            RecordingContext(recordingType = RecordingType.JOURNAL, speakerCountPreference = 1, title = question, language = prefs.selectedLanguage)
        )
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (granted) start()
    }
    LaunchedEffect(hasMic) { if (hasMic && !started && state == RecordingState.IDLE) start() }

    val recording = state == RecordingState.RECORDING || state == RecordingState.PAUSED
    fun close() {
        if (recording && durationMs >= LanternRules.SHORTEST_KEEP_MS) confirmClose = true
        else { if (recording) viewModel.discardRecording(); onClose() }
    }
    BackHandler { close() }

    val phase = when {
        !hasMic -> LanternPhase.NEEDS_MICROPHONE
        state == RecordingState.FAILED -> LanternPhase.FAILED
        state == RecordingState.PAUSED -> LanternPhase.PAUSED
        state == RecordingState.RECORDING -> LanternPhase.LISTENING
        else -> LanternPhase.STARTING
    }
    LanternContent(
        question = question,
        followUp = LanternRules.followUp(question),
        phase = phase,
        durationMs = durationMs,
        amplitude = amplitude,
        animate = animate,
        onClose = ::close,
        onAllowMicrophone = { askMic.launch(Manifest.permission.RECORD_AUDIO) },
        onTogglePause = { if (state == RecordingState.RECORDING) viewModel.pauseRecording() else viewModel.resumeRecording() },
        onKeep = { viewModel.finishRecording(onComplete = onComplete, onDiscardedOrEmpty = onClose) },
        onTypeInstead = { if (recording) viewModel.discardRecording(); onWriteInstead() },
        onRetry = { start() }
    )
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.lantern_discard_title)) },
            text = { Text(stringResource(R.string.lantern_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; viewModel.discardRecording(); onClose() }, modifier = Modifier.testTag("lantern_discard_confirm")) {
                    Text(stringResource(R.string.lantern_discard_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.lantern_discard_cancel)) } }
        )
    }
}

/** The lantern as drawn: stateless, so every phase is screenshot-tested in both themes. */
@Composable
fun LanternContent(
    question: String,
    followUp: String,
    phase: LanternPhase,
    durationMs: Long,
    amplitude: Float,
    animate: Boolean,
    onClose: () -> Unit,
    onAllowMicrophone: () -> Unit,
    onTogglePause: () -> Unit,
    onKeep: () -> Unit,
    onTypeInstead: () -> Unit,
    onRetry: () -> Unit
) {
    val p = GardenColors.lantern(MM.colors.isDark)
    Box(
        Modifier.fillMaxSize()
            .drawBehind {
                drawRect(p.edge)
                val c = Offset(size.width / 2f, size.height * 0.55f)
                drawRect(Brush.radialGradient(0f to p.glow, 0.45f to p.middle, 1f to p.edge, center = c, radius = size.maxDimension * 0.75f))
            }
            .testTag("lantern_screen")
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            TopRow(p, onClose)
            Text(
                question, color = p.ink, fontFamily = GardenSerif, fontSize = 24.sp, lineHeight = 31.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 8.dp, end = 8.dp).semantics { heading() }
            )
            Spacer(Modifier.weight(1f))
            when (phase) {
                LanternPhase.NEEDS_MICROPHONE -> MicrophoneAsk(p, onAllowMicrophone, onTypeInstead)
                LanternPhase.FAILED -> Failed(p, onRetry, onTypeInstead)
                else -> {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Lantern(amplitude = if (phase == LanternPhase.LISTENING) amplitude else 0f, lit = phase != LanternPhase.STARTING, animate = animate)
                    }
                    AnimatedVisibility(
                        visible = phase == LanternPhase.LISTENING && LanternRules.showFollowUp(durationMs),
                        enter = fadeIn(tween(900)), exit = fadeOut(tween(300))
                    ) { FollowUp(p, followUp) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (phase == LanternPhase.LISTENING || phase == LanternPhase.PAUSED || phase == LanternPhase.STARTING) {
                Controls(p, phase, durationMs, onTogglePause, onKeep, onTypeInstead)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TopRow(p: LanternPalette, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(p.quiet).clickable(role = Role.Button, onClick = onClose).testTag("lantern_close"),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.lantern_close), tint = p.ink) }
        Spacer(Modifier.weight(1f))
        val privacy = stringResource(R.string.lantern_private_description)
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(p.quiet).padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics(mergeDescendants = true) { contentDescription = privacy },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = p.inkSoft, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.lantern_private), color = p.inkSoft, fontFamily = InterFamily, fontSize = 12.sp)
        }
    }
}

/** The light: a warm core that swells with the voice, and rings that leave it while someone speaks. */
@Composable
private fun Lantern(amplitude: Float, lit: Boolean, animate: Boolean) {
    val target = if (lit) LanternRules.orbScale(amplitude) else 0.82f
    val scale by animateFloatAsState(target, animationSpec = tween(if (animate) 180 else 0), label = "lantern_scale")
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "lantern_rings")
        val v by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "rings")
        v
    } else 0.35f
    val speaking = amplitude > 0.08f
    Canvas(Modifier.size(260.dp)) {
        val c = center
        val core = size.minDimension * 0.24f
        if (lit) {
            for (i in 0 until 3) {
                val f = (t + i / 3f) % 1f
                val alpha = (if (speaking) 0.55f else 0.25f) * (1f - f)
                drawCircle(GardenColors.Gold.copy(alpha = alpha), radius = core * (1f + 1.1f * f), center = c, style = Stroke(width = 2.dp.toPx()))
            }
        }
        drawCircle(
            Brush.radialGradient(listOf(GardenColors.EmberGlow.copy(alpha = if (lit) 0.55f else 0.25f), Color.Transparent), center = c, radius = core * 2.4f * scale),
            radius = core * 2.4f * scale, center = c
        )
        drawCircle(
            Brush.radialGradient(listOf(GardenColors.Ember, GardenColors.Gold, GardenColors.GoldDeep), center = c.copy(x = c.x - core * 0.15f, y = c.y - core * 0.2f), radius = core * 1.3f * scale),
            radius = core * scale, center = c
        )
        if (lit && speaking && animate) {
            for (i in 0 until 4) {
                val f = (t * 1.3f + i * 0.25f) % 1f
                val x = c.x + core * 0.6f * sin(2f * PI.toFloat() * (i * 0.27f + f * 0.3f))
                drawCircle(GardenColors.Ember.copy(alpha = 1f - f), radius = 2.5.dp.toPx(), center = Offset(x, c.y - core - f * core * 2.2f))
            }
        }
    }
}

@Composable
private fun FollowUp(p: LanternPalette, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp)).background(p.quiet).border(1.dp, p.line, RoundedCornerShape(22.dp))
            .padding(16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("lantern_follow_up"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.lantern_follow_up_label).uppercase(), color = p.inkSoft, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(text, color = p.ink, fontFamily = GardenSerif, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Controls(p: LanternPalette, phase: LanternPhase, durationMs: Long, onTogglePause: () -> Unit, onKeep: () -> Unit, onTypeInstead: () -> Unit) {
    val seconds = (durationMs / 1000).toInt()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.lantern_elapsed, seconds / 60, seconds % 60), color = p.ink, fontFamily = GardenSerif, fontSize = 20.sp)
        Text(
            stringResource(
                when (phase) {
                    LanternPhase.PAUSED -> R.string.lantern_paused
                    LanternPhase.STARTING -> R.string.lantern_starting
                    else -> R.string.lantern_listening
                }
            ),
            color = p.inkSoft, fontFamily = InterFamily, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val pauseLabel = stringResource(if (phase == LanternPhase.PAUSED) R.string.lantern_resume else R.string.lantern_pause)
            RoundButton(p, enabled = phase != LanternPhase.STARTING, label = pauseLabel, tag = "lantern_pause", onClick = onTogglePause) {
                Icon(if (phase == LanternPhase.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null, tint = p.ink)
            }
            val keep = stringResource(R.string.lantern_keep_description)
            Row(
                Modifier.heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp)).background(GardenColors.Gold)
                    .clickable(enabled = phase != LanternPhase.STARTING, role = Role.Button, onClickLabel = keep, onClick = onKeep)
                    .padding(horizontal = 26.dp).testTag("lantern_keep"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = GardenColors.OnGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lantern_keep), color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            val typeLabel = stringResource(R.string.lantern_type_instead)
            if (LanternRules.canTypeInstead(durationMs)) {
                RoundButton(p, enabled = true, label = typeLabel, tag = "lantern_type", onClick = onTypeInstead) {
                    Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = p.ink)
                }
            } else Spacer(Modifier.size(56.dp))
        }
    }
}

@Composable
private fun RoundButton(p: LanternPalette, enabled: Boolean, label: String, tag: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(p.quiet).border(1.dp, p.line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }.testTag(tag),
        contentAlignment = Alignment.Center
    ) { icon() }
}

@Composable
private fun MicrophoneAsk(p: LanternPalette, onAllow: () -> Unit, onWrite: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("lantern_mic_ask"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Lantern(amplitude = 0f, lit = false, animate = false)
        Text(stringResource(R.string.lantern_mic_title), color = p.ink, fontFamily = GardenSerif, fontSize = 20.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
        Text(stringResource(R.string.lantern_mic_body), color = p.inkSoft, fontFamily = InterFamily, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        PrimaryPill(stringResource(R.string.lantern_mic_allow), Icons.Outlined.Mic, "lantern_allow_mic", onAllow)
        TextButton(onClick = onWrite) { Text(stringResource(R.string.lantern_mic_write), color = p.ink, fontFamily = InterFamily) }
    }
}

@Composable
private fun Failed(p: LanternPalette, onRetry: () -> Unit, onWrite: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("lantern_failed"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.lantern_failed), color = p.ink, fontFamily = InterFamily, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        PrimaryPill(stringResource(R.string.lantern_try_again), Icons.Outlined.Mic, "lantern_retry", onRetry)
        TextButton(onClick = onWrite) { Text(stringResource(R.string.lantern_mic_write), color = p.ink, fontFamily = InterFamily) }
    }
}

@Composable
private fun PrimaryPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(GardenColors.Gold)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = GardenColors.OnGold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = GardenColors.OnGold, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
