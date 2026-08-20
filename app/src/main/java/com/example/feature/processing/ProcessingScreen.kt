package com.example.feature.processing

import android.app.Application
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.pipeline.MeetingProcessingPipeline
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.ProcessingStage
import com.example.core.ui.OfflineShieldBadge
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletSecondary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class ProcessingUiState(
    val stepTitle: String = "Initializing AI Pipeline...",
    val progressPercent: Int = 0,
    val currentStageIndex: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
    /** True when processing stopped because a required local AI model isn't installed yet.
     * The recording itself is always saved regardless — this never means the audio was lost. */
    val modelRequired: Boolean = false,
    val modelRequiredMessage: String? = null
)

class ProcessingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val pipeline = MeetingProcessingPipeline(application, database)
    private val userPrefs = UserPreferencesManager(application)

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var pipelineJob: Job? = null

    fun startPipeline(
        meetingId: String,
        audioPath: String,
        durationMs: Long,
        expectedSpeakerCount: Int?,
        onComplete: (String) -> Unit
    ) {
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            try {
                val prefs = userPrefs.preferencesFlow.first()
                val selectedModel = prefs.selectedAsrModelId
                val audioFile = File(audioPath)

                _uiState.value = ProcessingUiState(
                    stepTitle = "Preparing audio...",
                    progressPercent = 5,
                    currentStageIndex = 0
                )

                val resultMeeting = pipeline.processMeeting(
                    meetingId = meetingId,
                    audioFile = audioFile,
                    totalDurationMs = durationMs,
                    modelId = selectedModel,
                    expectedSpeakerCount = expectedSpeakerCount,
                    onProgress = { step, percent, stage ->
                        val stageIdx = when (stage) {
                            ProcessingStage.IDLE, ProcessingStage.PREPARING_AUDIO -> 0
                            ProcessingStage.DETECTING_SPEECH -> 1
                            ProcessingStage.TRANSCRIBING -> 2
                            ProcessingStage.DIARIZING -> 3
                            ProcessingStage.ANALYZING -> 4
                            ProcessingStage.SAVING_RESULTS, ProcessingStage.COMPLETED -> 5
                            ProcessingStage.FAILED, ProcessingStage.CANCELLED -> 5
                        }
                        _uiState.value = ProcessingUiState(
                            stepTitle = step,
                            progressPercent = percent,
                            currentStageIndex = stageIdx,
                            isComplete = percent >= 100
                        )
                    }
                )

                if (resultMeeting.status == com.example.core.model.MeetingStatus.MODEL_REQUIRED.name) {
                    // Never fabricate a transcript: the recording is safely saved, but no local
                    // speech recognition model is installed, so processing honestly stops here.
                    _uiState.value = ProcessingUiState(
                        stepTitle = "Recording saved",
                        progressPercent = 100,
                        currentStageIndex = 1,
                        isComplete = true,
                        modelRequired = true,
                        modelRequiredMessage = "Download the offline speech recognition model to transcribe this meeting on your device."
                    )
                } else {
                    _uiState.value = ProcessingUiState(
                        stepTitle = "All AI tasks completed successfully!",
                        progressPercent = 100,
                        currentStageIndex = 5,
                        isComplete = true
                    )
                    onComplete(meetingId)
                }
            } catch (e: Exception) {
                _uiState.value = ProcessingUiState(
                    stepTitle = "Processing error",
                    progressPercent = 0,
                    error = e.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    fun cancelPipeline() {
        pipelineJob?.cancel()
        _uiState.value = _uiState.value.copy(error = "Processing cancelled by user")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    meetingId: String,
    audioPath: String,
    durationMs: Long,
    onNavigateBack: () -> Unit,
    onProcessingComplete: (meetingId: String) -> Unit,
    onNavigateToModels: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var speakerCountStarted by remember { mutableStateOf(false) }
    var selectedSpeakerCount by remember { mutableStateOf<Int?>(null) } // null = Auto

    LaunchedEffect(meetingId, audioPath, speakerCountStarted) {
        if (speakerCountStarted) {
            viewModel.startPipeline(meetingId, audioPath, durationMs, selectedSpeakerCount) { finishedId ->
                onProcessingComplete(finishedId)
            }
        }
    }

    if (!speakerCountStarted) {
        SpeakerCountPickerScreen(
            selected = selectedSpeakerCount,
            onSelect = { selectedSpeakerCount = it },
            onStart = { speakerCountStarted = true },
            onCancel = onNavigateBack
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Local Neural Engine Pipeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.cancelPipeline()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("processing_cancel_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP BENTO CARD: Live Progress Gauge & Stage
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, IndigoPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "${state.progressPercent}%",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = IndigoPrimaryLight
                    )

                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = IndigoPrimaryLight,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Text(
                        text = state.stepTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 2. CENTER BENTO CARD: 6-Stage Pipeline Checklist
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Multi-Stage On-Device Execution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    BentoPipelineStageItem(
                        stageNumber = 1,
                        name = "Audio Preprocessing & Framing",
                        icon = Icons.Default.GraphicEq,
                        isActive = state.currentStageIndex == 0,
                        isDone = state.currentStageIndex > 0
                    )
                    BentoPipelineStageItem(
                        stageNumber = 2,
                        name = "Voice Activity Detection (VAD)",
                        icon = Icons.Default.Memory,
                        isActive = state.currentStageIndex == 1,
                        isDone = state.currentStageIndex > 1
                    )
                    BentoPipelineStageItem(
                        stageNumber = 3,
                        name = "Speech-to-Text Transcription",
                        icon = Icons.Default.AutoAwesome,
                        isActive = state.currentStageIndex == 2,
                        isDone = state.currentStageIndex > 2
                    )
                    BentoPipelineStageItem(
                        stageNumber = 4,
                        name = "Speaker Diarization (Multi-Voice)",
                        icon = Icons.Default.Group,
                        isActive = state.currentStageIndex == 3,
                        isDone = state.currentStageIndex > 3
                    )
                    BentoPipelineStageItem(
                        stageNumber = 5,
                        name = "Decisions & Action Items Extraction",
                        icon = Icons.Default.Psychology,
                        isActive = state.currentStageIndex == 4,
                        isDone = state.currentStageIndex > 4
                    )
                    BentoPipelineStageItem(
                        stageNumber = 6,
                        name = "Local Vector Embeddings & Indexing",
                        icon = Icons.Default.Search,
                        isActive = state.currentStageIndex == 5,
                        isDone = state.isComplete
                    )
                }
            }

            // 3. Bottom action(s)
            if (state.modelRequired) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Speech recognition model required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                        Text(
                            text = state.modelRequiredMessage
                                ?: "Local speech recognition is not installed yet. The recording has been saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onProcessingComplete(meetingId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View Meeting")
                            }
                            Button(
                                onClick = onNavigateToModels,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Manage Models")
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelPipeline()
                        onNavigateBack()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Cancel Pipeline Processing")
                }
            }
        }
    }
}

/**
 * Lets the user optionally tell the diarization engine how many speakers to expect. "Auto" (the
 * default) lets sherpa-onnx's clustering detect the count itself; a specific count forces exactly
 * that many speakers, which sherpa-onnx's FastClusteringConfig genuinely supports. Kept to a
 * single row of choices — the underlying engine only reliably benefits from small-meeting counts,
 * so there is no reason to expose more than this.
 */
@Composable
private fun SpeakerCountPickerScreen(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Group, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "How many speakers?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Optional — telling the on-device speaker detector how many people spoke can improve accuracy for small meetings. Leave it on Auto if you're not sure.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeakerCountChip(label = "Auto", isSelected = selected == null) { onSelect(null) }
                for (count in 2..6) {
                    SpeakerCountChip(label = "$count", isSelected = selected == count) { onSelect(count) }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Start Processing")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SpeakerCountChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) IndigoPrimary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BentoPipelineStageItem(
    stageNumber: Int,
    name: String,
    icon: ImageVector,
    isActive: Boolean,
    isDone: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                isDone -> SuccessGreen
                isActive -> IndigoPrimary.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDone) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = IndigoPrimaryLight
                    )
                } else {
                    Text(
                        text = "$stageNumber",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isActive || isDone) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isDone -> MaterialTheme.colorScheme.onSurface
                isActive -> IndigoPrimaryLight
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        )
    }
}
