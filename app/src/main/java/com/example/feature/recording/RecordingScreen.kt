package com.example.feature.recording

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FiberManualRecord
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.core.model.RecordingType
import com.example.core.repository.MeetingRepository
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SuccessGreen
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
    private var recordingType: RecordingType = RecordingType.GENERAL
    private var customContext: String? = null

    fun startRecording(title: String, recordingType: RecordingType = RecordingType.GENERAL, customContext: String? = null) {
        meetingTitle = title
        this.recordingType = recordingType
        this.customContext = customContext
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
                audioFilePath = file.absolutePath,
                recordingType = recordingType,
                customContext = customContext
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
    onRecordingComplete: (meetingId: String, audioPath: String, durationMs: Long) -> Unit,
    /** True when reached via Home's Quick Record FAB — skips the type picker entirely (General
     * type, "Quick Recording" title) instead of making the user tap through it first. */
    quickStart: Boolean = false
) {
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var typeChosen by remember { mutableStateOf(quickStart) }
    var selectedType by remember { mutableStateOf(if (quickStart) RecordingType.GENERAL else RecordingType.MEETING) }
    var customContextText by remember { mutableStateOf("") }
    var meetingTitle by remember { mutableStateOf(if (quickStart) "Quick Recording" else RecordingType.MEETING.displayName) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startRecording(
                meetingTitle,
                selectedType,
                customContextText.trim().ifBlank { null }.takeIf { selectedType == RecordingType.CUSTOM }
            )
        }
    }

    val state by viewModel.recorderState.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(hasAudioPermission, typeChosen) {
        if (typeChosen && hasAudioPermission && !hasStarted) {
            hasStarted = true
            viewModel.startRecording(
                meetingTitle,
                selectedType,
                customContextText.trim().ifBlank { null }.takeIf { selectedType == RecordingType.CUSTOM }
            )
        }
    }

    if (!typeChosen) {
        RecordingTypePickerScreen(
            selected = selectedType,
            customContext = customContextText,
            onSelect = {
                selectedType = it
                meetingTitle = it.displayName
            },
            onCustomContextChange = { customContextText = it },
            onStart = { typeChosen = true },
            onQuickRecord = {
                selectedType = RecordingType.GENERAL
                meetingTitle = "Quick Recording"
                typeChosen = true
            },
            onCancel = onNavigateBack
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = { showDiscardDialog = true },
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("record_close_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close & Discard")
                    }
                },
                actions = {
                    if (state == RecorderState.RECORDING || state == RecorderState.PAUSED) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (state == RecorderState.RECORDING) RecordingRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (state == RecorderState.RECORDING) {
                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = RecordingRed, modifier = Modifier.size(10.dp))
                                }
                                Text(
                                    text = Formatters.formatDurationHms(durationMs),
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = if (state == RecorderState.RECORDING) RecordingRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                            text = "MeetingMind processes all speech 100% on your device with no internet connection needed. Please grant microphone access to capture real-time audio.",
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
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = meetingTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                WaveformCenterButton(
                    amplitude = amplitude,
                    isRecording = state == RecorderState.RECORDING,
                    isPaused = state == RecorderState.PAUSED,
                    onToggle = {
                        if (state == RecorderState.RECORDING) {
                            viewModel.pauseRecording()
                        } else if (state == RecorderState.PAUSED) {
                            viewModel.resumeRecording()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (state == RecorderState.RECORDING) "Listening…" else "Paused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (state == RecorderState.RECORDING) {
                        "Recording safely on your device"
                    } else {
                        "Tap the button to resume"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                viewModel.finishRecording { meetingId, path, dur ->
                                    onRecordingComplete(meetingId, path, dur)
                                }
                            },
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                                .testTag("record_finish_btn")
                        ) {
                            Icon(Icons.Default.Done, contentDescription = "Finish", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Text("Finish", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
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

/**
 * "What are you recording?" — shown before the mic starts. MeetingMind is a general voice-capture
 * tool, not a meeting-only recorder, so this never forces a choice: Quick Record skips straight to
 * [RecordingType.GENERAL]. The choice only ever adds focus guidance to the AI extraction prompt
 * later (see [RecordingType.focusGuidance]) — it never changes what recording itself does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingTypePickerScreen(
    selected: RecordingType,
    customContext: String,
    onSelect: (RecordingType) -> Unit,
    onCustomContextChange: (String) -> Unit,
    onStart: () -> Unit,
    onQuickRecord: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What are you recording?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "This helps MeetingMind know what to pay attention to. You can always skip and just record.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val types = RecordingType.entries.filter { it != RecordingType.GENERAL }
            types.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { type ->
                        RecordingTypeChip(
                            type = type,
                            isSelected = selected == type,
                            onClick = { onSelect(type) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            if (selected == RecordingType.CUSTOM) {
                OutlinedTextField(
                    value = customContext,
                    onValueChange = onCustomContextChange,
                    label = { Text("What should MeetingMind focus on?") },
                    placeholder = { Text("e.g. Focus on pricing objections and next steps") },
                    modifier = Modifier.fillMaxWidth().testTag("custom_context_field")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onStart,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("record_type_start_btn")
            ) {
                Text("Start Recording")
            }
            OutlinedButton(
                onClick = onQuickRecord,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("record_type_quick_btn")
            ) {
                Text("Quick Record")
            }
        }
    }
}

@Composable
private fun RecordingTypeChip(
    type: RecordingType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) IndigoPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) IndigoPrimaryLight else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = type.shortDescription,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

/**
 * The recording screen's central visual: waveform bars radiating outward from a single tappable
 * circular button (pause/resume), matching the reference design's "listening" moment — one
 * unified focal point rather than a separate telemetry card, pill badge, and controls dock.
 * Bar heights come from the real single-scalar input amplitude (never fabricated) scaled across a
 * fixed bar-count pattern, consistent with the efficiency requirement of not computing a full FFT.
 */
@Composable
fun WaveformCenterButton(
    amplitude: Float,
    isRecording: Boolean,
    isPaused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barCount = 24
    val normAmp = amplitude.coerceIn(0.08f, 1f)
    val buttonColor = if (isPaused) IndigoPrimary else RecordingRed
    val idleBarColor = MaterialTheme.colorScheme.outline
    val visualizerSize = 260.dp
    val buttonSize = 84.dp

    Box(
        modifier = modifier.size(visualizerSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val excludeRadius = buttonSize.toPx() / 2f + 14.dp.toPx()
            val barWidth = 5.dp.toPx()
            val gap = 6.dp.toPx()

            val baseHeights = listOf(
                0.25f, 0.4f, 0.65f, 0.45f, 0.85f, 1f, 0.55f, 0.75f, 0.9f, 0.45f, 0.65f, 0.35f
            )

            for (side in listOf(-1, 1)) {
                for (i in 0 until barCount) {
                    val hFactor = baseHeights[i % baseHeights.size]
                    val activeHeight = if (isRecording) {
                        (visualizerSize.toPx() * 0.4f * hFactor * normAmp).coerceIn(8f, visualizerSize.toPx() * 0.42f)
                    } else {
                        (visualizerSize.toPx() * 0.08f).coerceIn(6f, 14f)
                    }
                    val x = centerX + side * (excludeRadius + i * (barWidth + gap))
                    if (x < 0 || x > size.width) continue
                    val y = centerY - activeHeight / 2f
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = if (isRecording) {
                                listOf(RecordingRed.copy(alpha = 0.85f), IndigoPrimary.copy(alpha = 0.5f))
                            } else {
                                listOf(idleBarColor.copy(alpha = 0.4f), idleBarColor.copy(alpha = 0.15f))
                            }
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, activeHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(buttonSize + 24.dp)
                .background(buttonColor.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(buttonColor, buttonColor.copy(alpha = 0.75f)))
                    )
                    .testTag("record_pause_resume_btn")
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume recording" else "Pause recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
