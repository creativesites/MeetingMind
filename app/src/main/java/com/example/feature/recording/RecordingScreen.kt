package com.example.feature.recording

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioRecorder
import com.example.core.audio.MeetingRecordingService
import com.example.core.audio.RecorderState
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.model.MeetingStatus
import com.example.core.repository.MeetingRepository
import com.example.core.ui.OfflineShieldBadge
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)
    val recorder = AudioRecorder(application)

    val recorderState: StateFlow<RecorderState> = recorder.state
    val amplitude: StateFlow<Float> = recorder.amplitude
    val durationMs: StateFlow<Long> = recorder.durationMs

    private var currentMeetingId: String = UUID.randomUUID().toString()
    private var currentOutputFile: File? = null
    private var meetingTitle: String = "In-Person Discussion"

    fun startRecording(title: String) {
        meetingTitle = title
        currentMeetingId = UUID.randomUUID().toString()
        currentOutputFile = recorder.startRecording(currentMeetingId)
        MeetingRecordingService.startService(getApplication(), meetingTitle)
    }

    fun pauseRecording() {
        recorder.pauseRecording()
        MeetingRecordingService.pauseService(getApplication())
    }

    fun resumeRecording() {
        recorder.resumeRecording()
        MeetingRecordingService.resumeService(getApplication())
    }

    fun discardRecording() {
        recorder.discardRecording()
        MeetingRecordingService.stopService(getApplication())
    }

    fun finishRecording(onComplete: (meetingId: String, audioPath: String, durationMs: Long) -> Unit) {
        val finalDuration = durationMs.value
        val file = recorder.stopRecording() ?: currentOutputFile ?: return
        MeetingRecordingService.stopService(getApplication())

        viewModelScope.launch {
            meetingRepository.createInitialMeeting(
                id = currentMeetingId,
                title = meetingTitle,
                source = MeetingSource.LOCAL_RECORDING,
                audioFilePath = file.absolutePath
            )
            onComplete(currentMeetingId, file.absolutePath, finalDuration)
        }
    }

    override fun onCleared() {
        super.onCleared()
        MeetingRecordingService.stopService(getApplication())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    onNavigateBack: () -> Unit,
    onRecordingComplete: (meetingId: String, audioPath: String, durationMs: Long) -> Unit
) {
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startRecording("Live Strategy Review")
        }
    }

    val state by viewModel.recorderState.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var meetingTitle by remember { mutableStateOf("Live Strategy Review") }
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && !hasStarted) {
            hasStarted = true
            viewModel.startRecording(meetingTitle)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Live Intelligence Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { showDiscardDialog = true },
                        modifier = Modifier.testTag("record_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close & Discard")
                    }
                },
                actions = {
                    OfflineShieldBadge(modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (!hasAudioPermission) {
            // Permission request Bento Card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = IndigoPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = IndigoPrimaryLight,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Microphone Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "MeetMind processes all speech 100% on your device with no internet connection needed. Please grant microphone access to capture real-time audio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission & Start")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. TOP BENTO CARD: Session Header & Telemetry
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoRecordingPill(
                            isRecording = state == RecorderState.RECORDING,
                            isPaused = state == RecorderState.PAUSED
                        )

                        Text(
                            text = meetingTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IndigoPrimary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "16 kHz Voice Stream",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = IndigoPrimaryLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SuccessGreen.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Whisper VAD Active",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanTertiary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Lock-Screen Safe",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = CyanTertiary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 2. CENTER BENTO CARD: Big Waveform Visualizer + Glowing Timer
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (state == RecorderState.RECORDING) RecordingRed.copy(alpha = 0.4f) else IndigoPrimary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(
                            text = Formatters.formatDurationHms(durationMs),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 3.sp
                            ),
                            fontWeight = FontWeight.Bold,
                            color = if (state == RecorderState.RECORDING) RecordingRed else MaterialTheme.colorScheme.onSurface
                        )

                        BentoWaveformVisualizer(
                            amplitude = amplitude,
                            isRecording = state == RecorderState.RECORDING,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = if (state == RecorderState.RECORDING) "Listening & Buffering Audio Frames (Offline)" else "Recording Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. BOTTOM BENTO DOCK: Controls
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Discard
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { showDiscardDialog = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("record_discard_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Discard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Discard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Pause / Resume Main CTA
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    if (state == RecorderState.RECORDING) {
                                        viewModel.pauseRecording()
                                    } else if (state == RecorderState.PAUSED) {
                                        viewModel.resumeRecording()
                                    }
                                },
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state == RecorderState.PAUSED) IndigoPrimary else RecordingRed
                                    )
                                    .testTag("record_pause_resume_btn")
                            ) {
                                Icon(
                                    imageVector = if (state == RecorderState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (state == RecorderState.PAUSED) "Resume" else "Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                if (state == RecorderState.PAUSED) "Resume" else "Pause",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Finish & Transcribe
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    viewModel.finishRecording { meetingId, path, dur ->
                                        onRecordingComplete(meetingId, path, dur)
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                                    .testTag("record_finish_btn")
                            ) {
                                Icon(Icons.Default.Done, contentDescription = "Finish", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Text("Transcribe", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Live Session?") },
            text = { Text("Are you sure you want to stop and delete this recording? Audio buffers will be cleared.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardRecording()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_discard_btn")
                ) {
                    Text("Discard", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BentoRecordingPill(
    isRecording: Boolean,
    isPaused: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isRecording) RecordingRed.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isRecording) RecordingRed.copy(alpha = 0.4f) else WarningAmber.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) RecordingRed.copy(alpha = alpha) else WarningAmber
                    )
            )
            Text(
                text = if (isRecording) "RECORDING LIVE" else "PAUSED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
                color = if (isRecording) RecordingRed else WarningAmber
            )
        }
    }
}

@Composable
fun BentoWaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 28
    val normAmp = amplitude.coerceIn(0.08f, 1f)

    Canvas(
        modifier = modifier
            .height(90.dp)
            .padding(horizontal = 4.dp)
    ) {
        val totalWidth = size.width
        val barWidth = (totalWidth / (barCount * 1.6f)).coerceIn(4f, 12f)
        val gap = (totalWidth - (barWidth * barCount)) / (barCount - 1)

        val baseHeights = listOf(
            0.2f, 0.35f, 0.6f, 0.4f, 0.8f, 0.95f, 0.5f, 0.7f, 0.85f, 0.4f,
            0.6f, 0.9f, 0.75f, 0.5f, 0.65f, 0.85f, 0.45f, 0.7f, 0.9f, 0.35f,
            0.55f, 0.75f, 0.9f, 0.45f, 0.6f, 0.75f, 0.4f, 0.25f
        )

        for (i in 0 until barCount) {
            val hFactor = baseHeights[i % baseHeights.size]
            val activeHeight = if (isRecording) {
                (size.height * hFactor * normAmp * 1.2f).coerceIn(8f, size.height)
            } else {
                (size.height * 0.15f).coerceIn(6f, 14f)
            }

            val x = i * (barWidth + gap)
            val y = (size.height - activeHeight) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isRecording) listOf(RecordingRed, IndigoPrimaryLight) else listOf(IndigoPrimary.copy(alpha = 0.3f), IndigoPrimary.copy(alpha = 0.1f))
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, activeHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
