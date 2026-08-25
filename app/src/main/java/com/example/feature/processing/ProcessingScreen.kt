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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.ai.pipeline.MeetingProcessingWorker
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.MeetingStatus
import com.example.core.model.ProcessingStage
import com.example.core.ui.SectionCard
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ProcessingUiState(
    val stepTitle: String = "Initializing AI Pipeline...",
    val recordingTitle: String = "",
    val progressPercent: Int = 0,
    val currentStageIndex: Int = 0,
    val isComplete: Boolean = false,
    val isQueued: Boolean = false,
    val error: String? = null,
    /** True when processing stopped because a required local AI model isn't installed yet.
     * The recording itself is always saved regardless — this never means the audio was lost. */
    val modelRequired: Boolean = false,
    val modelRequiredMessage: String? = null
)

/**
 * Processing now runs as real background work ([MeetingProcessingWorker] via [WorkManager])
 * instead of a `viewModelScope` coroutine — it survives this screen (and this ViewModel) being
 * destroyed by navigation, app minimization, or the screen locking. This ViewModel only observes
 * [WorkInfo] and reflects real, typed state; it never advances processing itself.
 *
 * Critically, [uiState] is not the source of truth for "is this recording being processed" —
 * WorkManager's own persisted state is. [hasActiveWork] must always be checked before this screen
 * shows any "start processing" affordance: a plain in-memory/Compose flag reset by the process
 * being recreated (backgrounding, low memory, a cold reopen landing back on this route) would
 * otherwise let the user re-trigger [startPipeline] for a recording that is already running in
 * the background, enqueuing a second real job for the same meeting.
 */
class ProcessingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val userPrefs = UserPreferencesManager(application)
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var workId: UUID? = null
    private var lastMeetingId: String? = null

    /** True when a real, not-yet-finished WorkManager job already exists for [meetingId] —
     * ENQUEUED, BLOCKED (queued behind another recording), or RUNNING. Checked once (a snapshot,
     * not a subscription) so the caller can decide, before showing anything, whether to attach to
     * that job's live progress or offer to start a brand new one. */
    private suspend fun hasActiveWork(meetingId: String): Boolean {
        val infos = workManager.getWorkInfosByTagFlow(MeetingProcessingWorker.meetingWorkTag(meetingId)).first()
        return infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.RUNNING }
    }

    /** Entry point for the screen: attaches to an already-running job for [meetingId] if one
     * exists, without enqueuing anything new; returns false (and does nothing else) when there is
     * none, letting the caller decide whether to show the speaker-count picker and eventually call
     * [startPipeline] for a genuinely fresh run. */
    suspend fun attachIfAlreadyRunning(meetingId: String, onComplete: (String) -> Unit): Boolean {
        val infos = workManager.getWorkInfosByTagFlow(MeetingProcessingWorker.meetingWorkTag(meetingId)).first()
        val active = infos.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.RUNNING
        } ?: return false

        val meetingTitle = database.meetingDao().getMeetingById(meetingId)?.title ?: "Recording"
        _uiState.value = _uiState.value.copy(recordingTitle = meetingTitle)
        lastMeetingId = meetingId
        workId = active.id
        viewModelScope.launch { observeWork(active.id, meetingId, onComplete) }
        return true
    }

    fun startPipeline(
        meetingId: String,
        audioPath: String,
        durationMs: Long,
        expectedSpeakerCount: Int?,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch {
            // Belt-and-suspenders: even if the caller already checked, never enqueue a second
            // real job for a meeting that already has one in flight.
            if (hasActiveWork(meetingId)) {
                attachIfAlreadyRunning(meetingId, onComplete)
                return@launch
            }

            val prefs = userPrefs.preferencesFlow.first()
            val meetingTitle = database.meetingDao().getMeetingById(meetingId)?.title ?: "Recording"
            _uiState.value = ProcessingUiState(recordingTitle = meetingTitle)
            lastMeetingId = meetingId

            val inputData = workDataOf(
                MeetingProcessingWorker.KEY_MEETING_ID to meetingId,
                MeetingProcessingWorker.KEY_AUDIO_PATH to audioPath,
                MeetingProcessingWorker.KEY_DURATION_MS to durationMs,
                MeetingProcessingWorker.KEY_MODEL_ID to prefs.selectedAsrModelId,
                // Resolved (not raw) so a model the user selected and later deleted falls back to
                // one they still have installed, instead of failing with "no model installed".
                MeetingProcessingWorker.KEY_LLM_MODEL_ID to com.example.ai.modelmanagement.LlmModelResolver.resolve(
                    selectedModelId = prefs.selectedLlmModelId,
                    modelStorage = com.example.ai.modelmanagement.LocalModelStorage(getApplication())
                ),
                MeetingProcessingWorker.KEY_EXPECTED_SPEAKER_COUNT to (expectedSpeakerCount ?: -1),
                MeetingProcessingWorker.KEY_RECORDING_TITLE to meetingTitle
            )
            val request = OneTimeWorkRequestBuilder<MeetingProcessingWorker>()
                .setInputData(inputData)
                .addTag(MeetingProcessingWorker.meetingWorkTag(meetingId))
                .build()
            workId = request.id

            // Only one AI-heavy job runs at a time; a recording requested while another is
            // still processing is queued behind it rather than running concurrently.
            workManager.enqueueUniqueWork(
                MeetingProcessingWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )

            observeWork(request.id, meetingId, onComplete)
        }
    }

    private suspend fun observeWork(id: UUID, meetingId: String, onComplete: (String) -> Unit) {
        workManager.getWorkInfoByIdFlow(id).collect { info ->
            if (info == null) return@collect
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    _uiState.value = _uiState.value.copy(isQueued = true, stepTitle = "Waiting for another recording to finish processing...")
                }
                WorkInfo.State.RUNNING -> {
                    val step = info.progress.getString(MeetingProcessingWorker.KEY_PROGRESS_STEP)
                    val percent = info.progress.getInt(MeetingProcessingWorker.KEY_PROGRESS_PERCENT, _uiState.value.progressPercent)
                    val stageName = info.progress.getString(MeetingProcessingWorker.KEY_PROGRESS_STAGE)
                    val stage = stageName?.let { runCatching { ProcessingStage.valueOf(it) }.getOrNull() }
                    if (step != null && stage != null) {
                        _uiState.value = ProcessingUiState(
                            stepTitle = step,
                            recordingTitle = _uiState.value.recordingTitle,
                            progressPercent = percent,
                            currentStageIndex = stageIndexFor(stage),
                            isQueued = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isQueued = false)
                    }
                }
                WorkInfo.State.SUCCEEDED -> {
                    val status = info.outputData.getString(MeetingProcessingWorker.KEY_RESULT_STATUS)
                    if (status == MeetingStatus.MODEL_REQUIRED.name) {
                        _uiState.value = ProcessingUiState(
                            stepTitle = "Recording saved",
                            recordingTitle = _uiState.value.recordingTitle,
                            progressPercent = 100,
                            currentStageIndex = 1,
                            isComplete = true,
                            modelRequired = true,
                            modelRequiredMessage = "Download the offline speech recognition model to transcribe this recording on your device."
                        )
                    } else {
                        _uiState.value = ProcessingUiState(
                            stepTitle = "All AI tasks completed successfully!",
                            recordingTitle = _uiState.value.recordingTitle,
                            progressPercent = 100,
                            currentStageIndex = 5,
                            isComplete = true
                        )
                        onComplete(meetingId)
                    }
                }
                WorkInfo.State.FAILED -> {
                    val error = info.outputData.getString(MeetingProcessingWorker.KEY_ERROR)
                    _uiState.value = _uiState.value.copy(
                        stepTitle = "Processing failed",
                        error = error ?: "Unknown error",
                        isQueued = false
                    )
                }
                WorkInfo.State.CANCELLED -> {
                    _uiState.value = _uiState.value.copy(
                        stepTitle = "Processing cancelled",
                        error = "Processing cancelled",
                        isQueued = false
                    )
                }
            }
        }
    }

    private fun stageIndexFor(stage: ProcessingStage): Int = when (stage) {
        ProcessingStage.IDLE, ProcessingStage.PREPARING_AUDIO -> 0
        ProcessingStage.DETECTING_SPEECH -> 1
        ProcessingStage.TRANSCRIBING -> 2
        ProcessingStage.DIARIZING -> 3
        ProcessingStage.ANALYZING -> 4
        ProcessingStage.SAVING_RESULTS, ProcessingStage.COMPLETED -> 5
        ProcessingStage.FAILED, ProcessingStage.CANCELLED -> 5
    }

    /** Cancels the real background work — WorkManager propagates this as coroutine
     * cancellation inside the worker, which the pipeline's NonCancellable cleanup honors. */
    fun cancelPipeline() {
        workId?.let { workManager.cancelWorkById(it) }
        _uiState.value = _uiState.value.copy(error = "Processing cancelled by user")
    }

    /** A deliberate new attempt — only reachable from the FAILED state's Retry action, i.e. only
     * once WorkManager itself confirms nothing is still active for this meeting. */
    fun retry(audioPath: String, durationMs: Long, expectedSpeakerCount: Int?, onComplete: (String) -> Unit) {
        val meetingId = lastMeetingId ?: return
        _uiState.value = ProcessingUiState(recordingTitle = _uiState.value.recordingTitle)
        startPipeline(meetingId, audioPath, durationMs, expectedSpeakerCount, onComplete)
    }
}

/** [Checking] is a brief, real "is a job for this meeting already running?" lookup — never
 * skipped — so a screen re-entered after the app was backgrounded/recreated never shows the
 * speaker-count picker (and its "start processing" action) while a real job is already in flight
 * for the same recording. Only [Picker] can lead to a fresh [ProcessingViewModel.startPipeline] call. */
private enum class ProcessingScreenPhase { Checking, Picker, Running }

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
    var phase by remember(meetingId) { mutableStateOf(ProcessingScreenPhase.Checking) }
    var selectedSpeakerCount by remember { mutableStateOf<Int?>(null) } // null = Auto

    LaunchedEffect(meetingId) {
        val alreadyRunning = viewModel.attachIfAlreadyRunning(meetingId) { finishedId ->
            onProcessingComplete(finishedId)
        }
        phase = if (alreadyRunning) ProcessingScreenPhase.Running else ProcessingScreenPhase.Picker
    }

    when (phase) {
        ProcessingScreenPhase.Checking -> {
            Scaffold { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            return
        }
        ProcessingScreenPhase.Picker -> {
            SpeakerCountPickerScreen(
                selected = selectedSpeakerCount,
                onSelect = { selectedSpeakerCount = it },
                onStart = {
                    phase = ProcessingScreenPhase.Running
                    viewModel.startPipeline(meetingId, audioPath, durationMs, selectedSpeakerCount) { finishedId ->
                        onProcessingComplete(finishedId)
                    }
                },
                onCancel = onNavigateBack
            )
            return
        }
        ProcessingScreenPhase.Running -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.recordingTitle.ifBlank { "Processing Recording" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
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
            // 1. Live progress gauge & stage — one quiet container.
            SectionCard {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "${state.progressPercent}%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
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

            // 2. 6-stage pipeline checklist — a grouped list, not stacked bento tiles.
            SectionCard {
                Column(modifier = Modifier.padding(top = 18.dp, start = 18.dp, end = 18.dp, bottom = 4.dp)) {
                    Text(
                        text = "Multi-Stage On-Device Execution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                PipelineStageRow(
                    stageNumber = 1,
                    name = "Audio Preprocessing & Framing",
                    isActive = state.currentStageIndex == 0,
                    isDone = state.currentStageIndex > 0
                )
                PipelineStageRow(
                    stageNumber = 2,
                    name = "Voice Activity Detection (VAD)",
                    isActive = state.currentStageIndex == 1,
                    isDone = state.currentStageIndex > 1
                )
                PipelineStageRow(
                    stageNumber = 3,
                    name = "Speech-to-Text Transcription",
                    isActive = state.currentStageIndex == 2,
                    isDone = state.currentStageIndex > 2
                )
                PipelineStageRow(
                    stageNumber = 4,
                    name = "Speaker Diarization (Multi-Voice)",
                    isActive = state.currentStageIndex == 3,
                    isDone = state.currentStageIndex > 3
                )
                PipelineStageRow(
                    stageNumber = 5,
                    name = "Decisions & Action Items Extraction",
                    isActive = state.currentStageIndex == 4,
                    isDone = state.currentStageIndex > 4
                )
                PipelineStageRow(
                    stageNumber = 6,
                    name = "Local Vector Embeddings & Indexing",
                    isActive = state.currentStageIndex == 5,
                    isDone = state.isComplete,
                    showDivider = false
                )
            }

            // 3. Bottom action(s)
            if (state.modelRequired) {
                SectionCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Speech recognition model required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = state.modelRequiredMessage
                                ?: "Local speech recognition is not installed yet. The recording has been saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onProcessingComplete(meetingId) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View Recording")
                            }
                            Button(
                                onClick = onNavigateToModels,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Manage Models")
                            }
                        }
                    }
                }
            } else if (state.error != null) {
                SectionCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Processing failed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back")
                            }
                            Button(
                                // A deliberate new attempt: only reachable here, once WorkManager
                                // itself has already reported this job as finished (FAILED), so
                                // there is no risk of this creating a second concurrent job.
                                onClick = { viewModel.retry(audioPath, durationMs, selectedSpeakerCount) { finishedId -> onProcessingComplete(finishedId) } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry")
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
            Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
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
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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

/**
 * One row of the pipeline checklist inside a [SectionCard]: a leading status circle (number,
 * spinner, or checkmark) plus the stage name — matching [ListRow]'s spacing/divider rhythm even
 * though the leading slot needs a custom composable rather than a static icon.
 */
@Composable
private fun PipelineStageRow(
    stageNumber: Int,
    name: String,
    isActive: Boolean,
    isDone: Boolean,
    showDivider: Boolean = true
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isDone -> SuccessGreen
                    isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
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
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "$stageNumber",
                            style = MaterialTheme.typography.labelSmall,
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
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp),
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}
