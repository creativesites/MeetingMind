package com.example.feature.processing

import androidx.compose.material3.TextButton

import androidx.compose.material3.AlertDialog

import androidx.compose.material.icons.filled.KeyboardArrowDown

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ProcessingUiState(
    val stepTitle: String = "Getting ready",
    val recordingTitle: String = "",
    val progressPercent: Int = 0,
    val currentStageIndex: Int = 0,
    /** The engine's real stage, which the type-derived rows are drawn from. */
    val stage: ProcessingStage? = null,
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
    private val meetingRepository = com.example.core.repository.MeetingRepository(application, database)

    /** Where the work runs, for the header. */
    val processingProfile: StateFlow<com.example.core.model.ProcessingProfile> = userPrefs.preferencesFlow
        .map { it.processingProfile }
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.core.model.ProcessingProfile.OFFLINE)

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var workId: UUID? = null
    private var lastMeetingId: String? = null

    /** What MeetingMind already knows about this recording — type and any speaker-count
     * preference already captured at recording/import time (or a previous attempt at this
     * screen). Used to decide whether the second-chance speaker-count prompt is needed at all. */
    suspend fun loadRecordingContext(meetingId: String): com.example.core.model.RecordingContext {
        val entity = database.meetingDao().getMeetingById(meetingId)
        val recordingType = entity?.recordingType?.let {
            runCatching { com.example.core.model.RecordingType.valueOf(it) }.getOrNull()
        } ?: com.example.core.model.RecordingType.GENERAL
        return com.example.core.model.RecordingContext(
            recordingType = recordingType,
            speakerCountPreference = entity?.speakerCountPreference,
            customContext = entity?.customContext
        )
    }

    /** Persists the second-chance prompt's answer so a retry (or reopening this screen) never
     * has to ask again. */
    fun persistSpeakerCountPreference(meetingId: String, speakerCount: Int?) {
        viewModelScope.launch { meetingRepository.updateSpeakerCountPreference(meetingId, speakerCount) }
    }

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

            val meetingTitle = database.meetingDao().getMeetingById(meetingId)?.title ?: "Recording"
            _uiState.value = ProcessingUiState(recordingTitle = meetingTitle)
            lastMeetingId = meetingId
            val id = com.example.ai.pipeline.ProcessingScheduler.enqueue(
                getApplication(), meetingId, audioPath, durationMs, expectedSpeakerCount
            ) ?: run {
                _uiState.value = _uiState.value.copy(error = "This recording's audio could not be found.")
                return@launch
            }
            workId = id
            observeWork(id, meetingId, onComplete)
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
                            stage = stage,
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
        ProcessingStage.DIARIZING, ProcessingStage.CLEANING_TRANSCRIPT -> 3
        ProcessingStage.ANALYZING -> 4
        ProcessingStage.SAVING_RESULTS, ProcessingStage.COMPLETED -> 5
        ProcessingStage.FAILED, ProcessingStage.CANCELLED -> 5
    }

    /** Stops the background work — WorkManager propagates this as coroutine cancellation inside
     * the worker, which the pipeline's NonCancellable cleanup honours. */
    fun cancelPipeline() {
        lastMeetingId?.let { com.example.ai.pipeline.ProcessingScheduler.cancel(getApplication(), it) }
        workId?.let { workManager.cancelWorkById(it) }
        _uiState.value = _uiState.value.copy(error = "Processing cancelled by user")
    }

    /** What the recording's own record says, before anything is started. */
    enum class Existing { READY, NEEDS_MODEL, FAILED, INTERRUPTED, NOT_STARTED }

    suspend fun existingOutcome(meetingId: String): Existing {
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return Existing.NOT_STARTED
        _uiState.value = _uiState.value.copy(recordingTitle = meeting.title)
        lastMeetingId = meetingId
        return when (meeting.status) {
            MeetingStatus.READY.name -> Existing.READY
            MeetingStatus.MODEL_REQUIRED.name -> Existing.NEEDS_MODEL
            MeetingStatus.PROCESSING.name -> Existing.INTERRUPTED
            MeetingStatus.ERROR.name -> {
                val job = database.processingJobDao().getJobForMeeting(meetingId).first()
                _uiState.value = _uiState.value.copy(stepTitle = "Processing stopped", error = job?.errorMessage ?: "Processing didn't finish.")
                Existing.FAILED
            }
            else -> Existing.NOT_STARTED
        }
    }

    fun showNeedsModel() {
        _uiState.value = ProcessingUiState(
            stepTitle = "Recording saved",
            recordingTitle = _uiState.value.recordingTitle,
            progressPercent = 100,
            currentStageIndex = 1,
            isComplete = true,
            modelRequired = true,
            modelRequiredMessage = "Download the offline speech recognition model to transcribe this recording on your device."
        )
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
    var selectedSpeakerCount by remember { mutableStateOf<Int?>(null) } // null = Auto/"Not sure"
    var recordingType by remember { mutableStateOf(com.example.core.model.RecordingType.GENERAL) }

    LaunchedEffect(meetingId) {
        // The rows below are drawn from the recording's type and speaker count on every path.
        viewModel.loadRecordingContext(meetingId).let { ctx ->
            recordingType = ctx.recordingType
            selectedSpeakerCount = ctx.speakerCountPreference
        }
        // 1. Work already queued or running (including after the app was closed): follow it.
        val alreadyRunning = viewModel.attachIfAlreadyRunning(meetingId) { finishedId ->
            onProcessingComplete(finishedId)
        }
        if (alreadyRunning) {
            phase = ProcessingScreenPhase.Running
            return@LaunchedEffect
        }
        // 2. Nothing running: the recording's own status says whether this already happened.
        //    A finished recording is never processed again just because this screen reopened.
        when (viewModel.existingOutcome(meetingId)) {
            ProcessingViewModel.Existing.READY -> { onProcessingComplete(meetingId); return@LaunchedEffect }
            ProcessingViewModel.Existing.NEEDS_MODEL -> { viewModel.showNeedsModel(); phase = ProcessingScreenPhase.Running; return@LaunchedEffect }
            ProcessingViewModel.Existing.FAILED -> { phase = ProcessingScreenPhase.Running; return@LaunchedEffect }
            ProcessingViewModel.Existing.INTERRUPTED -> {
                // Stopped by the system with no work left behind: carry on where it stopped.
                phase = ProcessingScreenPhase.Running
                viewModel.startPipeline(meetingId, audioPath, durationMs, null) { finishedId -> onProcessingComplete(finishedId) }
                return@LaunchedEffect
            }
            ProcessingViewModel.Existing.NOT_STARTED -> Unit
        }

        val context = viewModel.loadRecordingContext(meetingId)
        recordingType = context.recordingType
        if (context.speakerCountPreference != null) {
            // Already told MeetingMind this — at recording start, on import, or on a previous
            // attempt at this very screen — so it is never asked for twice.
            selectedSpeakerCount = context.speakerCountPreference
            phase = ProcessingScreenPhase.Running
            viewModel.startPipeline(meetingId, audioPath, durationMs, context.speakerCountPreference) { finishedId ->
                onProcessingComplete(finishedId)
            }
        } else {
            selectedSpeakerCount = context.recordingType.suggestedSpeakerCount()
            phase = ProcessingScreenPhase.Picker
        }
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
                recordingType = recordingType,
                selected = selectedSpeakerCount,
                onSelect = { selectedSpeakerCount = it },
                onStart = {
                    viewModel.persistSpeakerCountPreference(meetingId, selectedSpeakerCount)
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

    val profile by viewModel.processingProfile.collectAsState()
    ProcessingRunning(
        state = state,
        profile = profile,
        rows = com.example.core.model.Workflows.processingStageRows(recordingType, selectedSpeakerCount),
        onMinimise = onNavigateBack,
        onStop = { viewModel.cancelPipeline(); onNavigateBack() },
        onRetry = { viewModel.retry(audioPath, durationMs, selectedSpeakerCount) { finishedId -> onProcessingComplete(finishedId) } },
        onViewRecording = { onProcessingComplete(meetingId) },
        onGetModel = onNavigateToModels
    )
}

/** The processing screen once work is under way: progress ring, stage timeline, and what to do next. */
@Composable
internal fun ProcessingRunning(
    state: ProcessingUiState,
    profile: com.example.core.model.ProcessingProfile,
    rows: List<com.example.core.model.ProcessingStageRow>,
    onMinimise: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onViewRecording: () -> Unit,
    onGetModel: () -> Unit
) {
    var confirmStop by remember { mutableStateOf(false) }
    val failed = state.error != null || state.modelRequired
    // Elapsed time on this screen, so a long run visibly moves even between progress steps.
    var elapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(failed, state.isComplete) {
        val started = System.currentTimeMillis() - elapsedMs
        while (!failed && !state.isComplete) {
            elapsedMs = System.currentTimeMillis() - started
            kotlinx.coroutines.delay(1_000)
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: minimise (never cancel), then what this is and who is doing the work.
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMinimise, modifier = Modifier.testTag("processing_minimise_btn")) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimise. Processing keeps running in the background")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        state.recordingTitle.ifBlank { "Your recording" },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        if (profile == com.example.core.model.ProcessingProfile.INTERNET) "With Google's AI" else "On this phone",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.weight(0.6f))

            // The one number that matters, in a ring.
            ProgressRing(
                percent = if (state.isComplete) 100 else state.progressPercent,
                failed = failed,
                modifier = Modifier.size(196.dp)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = when {
                    state.modelRequired -> "Needs the offline model"
                    state.error != null -> "Didn't finish"
                    state.isComplete -> "Done"
                    state.isQueued -> "Waiting for another recording to finish"
                    else -> friendlyStep(state.stepTitle)
                },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2
            )
            if (!failed && !state.isComplete) {
                Text(
                    formatElapsed(elapsedMs) + " · you can leave this screen",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            // The stages, as a quiet timeline rather than a boxed checklist. Hidden once it has
            // failed: the card below says what happened, and a column of unticked steps doesn't.
            if (!failed) Column(Modifier.fillMaxWidth()) {
                val current = state.stage
                rows.forEachIndexed { i, row ->
                    val lastOrdinal = row.stages.maxOf { it.ordinal }
                    val done = state.isComplete || (current != null && current !in row.stages && current.ordinal > lastOrdinal &&
                        current != ProcessingStage.FAILED && current != ProcessingStage.CANCELLED)
                    val active = !done && !failed && current != null && current in row.stages
                    TimelineRow(
                        label = row.label,
                        detail = null,
                        done = done,
                        active = active,
                        isLast = i == rows.lastIndex
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            when {
                state.modelRequired -> OutcomeCard(
                    title = "The recording is saved",
                    message = state.modelRequiredMessage ?: "Transcribing on this phone needs the offline speech model. Download it, or switch to Internet mode in Settings.",
                    primary = "Get the model" to onGetModel,
                    secondary = "View recording" to onViewRecording
                )
                state.error != null -> OutcomeCard(
                    title = "The recording is saved",
                    message = state.error ?: "Something went wrong.",
                    primary = "Try again" to onRetry,
                    secondary = "View recording" to onViewRecording
                )
                !state.isComplete -> {
                    BackgroundHint()
                    Button(
                        onClick = onMinimise,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("processing_background_btn")
                    ) {
                        Text("Continue in background", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    TextButton(
                        onClick = { confirmStop = true },
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).testTag("processing_cancel_btn")
                    ) {
                        Text("Stop", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (confirmStop) {
                AlertDialog(
                    onDismissRequest = { confirmStop = false },
                    title = { Text("Stop processing?") },
                    text = { Text("The recording is kept. You can process it again later from the recording.") },
                    confirmButton = {
                        TextButton(onClick = { confirmStop = false; onStop() }) {
                            Text("Stop", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going") } }
                )
            }
        }
    }
}

/** A step's own words, without trailing dots and engine jargon. */
internal fun friendlyStep(step: String): String {
    val trimmed = step.trim().trimEnd('.', '…').trim()
    return when {
        trimmed.isBlank() || trimmed.startsWith("Initializing", ignoreCase = true) -> "Getting ready"
        trimmed.contains("(VAD)") -> "Finding where people speak"
        else -> trimmed
    }
}

internal fun formatElapsed(ms: Long): String {
    val total = ms / 1000
    return if (total < 60) "${total}s" else "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun ProgressRing(percent: Int, failed: Boolean, modifier: Modifier = Modifier) {
    val animated by androidx.compose.animation.core.animateFloatAsState(percent.coerceIn(0, 100) / 100f, label = "progress")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(tint, -90f, 360f * animated, false, topLeft, arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        if (failed) {
            Text("!", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = tint)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$percent", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                Text("%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, detail: String?, done: Boolean, active: Boolean, isLast: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 3.dp).size(14.dp), contentAlignment = Alignment.Center) {
                when {
                    done -> Surface(shape = CircleShape, color = primary, modifier = Modifier.size(14.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(2.dp))
                    }
                    active -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primary)
                    else -> Box(Modifier.size(10.dp).border(1.5.dp, line, CircleShape))
                }
            }
            if (!isLast) Box(Modifier.padding(vertical = 4.dp).width(1.5.dp).weight(1f).background(if (done) primary.copy(alpha = 0.6f) else line))
        }
        Column(Modifier.padding(start = 12.dp, bottom = if (isLast) 0.dp else 14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            detail?.let {
                Text(friendlyStep(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OutcomeCard(title: String, message: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = secondary.second, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) { Text(secondary.first, maxLines = 1) }
                Button(onClick = primary.second, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) { Text(primary.first, maxLines = 1) }
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
    recordingType: com.example.core.model.RecordingType,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    // A one-person-leaning type (Idea, Voice Memo, Dictation, Journal) gets copy that matches
    // what's actually about to happen — running full multi-speaker clustering on a recording
    // that's almost certainly solo would be wasted work, not just wasted words.
    val leansSolo = recordingType.suggestedSpeakerCount() == 1
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
                text = "Who's speaking?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (leansSolo) {
                    "A ${recordingType.displayName.lowercase()} like this is usually just one person — confirming skips speaker detection entirely and processes faster. Pick a different option if others spoke too."
                } else {
                    "Optional — telling the on-device speaker detector how many people spoke can improve accuracy for small meetings. Leave it on \"Not sure\" if you don't know."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                SpeakerCountChip(label = "Not sure", isSelected = selected == null) { onSelect(null) }
                SpeakerCountChip(label = "Just me", isSelected = selected == 1) { onSelect(1) }
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
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
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
/**
 * Asks, once, to be let off battery optimisation. Samsung and others otherwise pause background
 * work after a while with the screen off, which is exactly when a long offline transcription
 * runs. Shown only while that restriction is actually in place.
 */
@Composable
private fun BackgroundHint() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val power = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
    var exempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) exempt = power.isIgnoringBatteryOptimizations(context.packageName)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    if (exempt) return
    // A quiet line, not a card: it matters, but it isn't what this screen is about.
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Your phone may pause this with the screen off.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                )
            }.onFailure {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }) { Text("Allow") }
    }
}
