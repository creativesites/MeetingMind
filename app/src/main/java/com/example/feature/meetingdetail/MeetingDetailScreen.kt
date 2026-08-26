package com.example.feature.meetingdetail

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.llm.MediaPipeLanguageModel
import com.example.ai.llm.RealMeetingIntelligenceEngine
import com.example.ai.modelmanagement.ModelCatalog
import com.example.core.audio.PlaybackController
import com.example.core.audio.PlaybackPhase
import com.example.core.audio.PlaybackState
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.domain.AskMeetingUseCase
import com.example.core.domain.ReprocessTranscriptCleanupUseCase
import com.example.core.model.ActionItem
import com.example.core.model.ChatMessage
import com.example.core.model.Decision
import com.example.core.model.IntelligenceProfile
import com.example.core.model.Meeting
import com.example.core.model.MeetingStatus
import com.example.core.model.Question
import com.example.core.model.RecordingType
import com.example.core.model.Topic
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import com.example.core.repository.ActionItemRepository
import com.example.core.repository.MeetingRepository
import com.example.core.repository.TranscriptRepository
import com.example.core.share.ShareContentFormatter
import com.example.core.share.ShareHelper
import com.example.core.ui.ListRow
import com.example.core.ui.SectionCard
import com.example.feature.meetingdetail.components.BottomTabBar
import com.example.feature.meetingdetail.components.MiniDialPlayer
import com.example.feature.meetingdetail.components.RecordingDetailTab
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.HeroGradientBrush
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineSoft
import com.example.ui.theme.Speaker2
import com.example.ui.theme.Speaker3
import com.example.ui.theme.Speaker4
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletSecondary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MeetingDetailViewModel(
    application: Application,
    private val meetingId: String
) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)
    private val transcriptRepository = TranscriptRepository(database)
    private val actionItemRepository = ActionItemRepository(database)
    private val modelStorage = com.example.ai.modelmanagement.LocalModelStorage(application)
    private val userPrefs = com.example.core.datastore.UserPreferencesManager(application)

    /** Built fresh per question (cheap — [MediaPipeLanguageModel] loads nothing eagerly) so Ask
     * AI always uses whichever LLM the user currently has selected, not whatever was selected the
     * moment this screen first opened. */
    private suspend fun buildAskUseCase(): AskMeetingUseCase {
        val llmModelId = com.example.ai.modelmanagement.LlmModelResolver.resolve(
            selectedModelId = userPrefs.preferencesFlow.first().selectedLlmModelId,
            modelStorage = modelStorage
        )
        val contextTokens = ModelCatalog.entries.find { it.id == llmModelId }?.contextLengthTokens ?: 4096
        return AskMeetingUseCase(
            transcriptRepository,
            RealMeetingIntelligenceEngine(
                languageModel = MediaPipeLanguageModel(getApplication(), modelStorage, modelId = llmModelId),
                contextLengthTokens = contextTokens
            )
        )
    }
    /** Display-only filler-word cleanup preference. The stored transcript is always verbatim; this
     * only decides how it is rendered, so flipping it takes effect immediately with no reprocessing. */
    val cleanFillerWords: StateFlow<Boolean> = userPrefs.preferencesFlow
        .map { it.cleanFillerWords }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Reuses the exact same pipeline object that a fresh recording's VAD/ASR/diarization run
    // would use — but cleanTranscript() (see ReprocessTranscriptCleanupUseCase) only ever touches
    // modelStorage/cleanupEngine, so calling it here never triggers audio processing.
    private val pipeline by lazy { com.example.ai.pipeline.MeetingProcessingPipeline(getApplication(), database) }
    private val reprocessCleanupUseCase by lazy {
        ReprocessTranscriptCleanupUseCase(pipeline, meetingRepository, transcriptRepository)
    }

    private val _isReprocessingCleanup = MutableStateFlow(false)
    val isReprocessingCleanup: StateFlow<Boolean> = _isReprocessingCleanup.asStateFlow()

    /** Re-cleans this meeting's already-persisted transcript with the user's currently-selected
     * [com.example.core.model.TranscriptCleanupMode] — no re-recording, re-transcription, or
     * re-diarization. [transcript] (already a live Room [Flow]) reflects the result automatically
     * once [com.example.core.repository.TranscriptRepository.updateCleanedText] persists it. */
    fun reprocessCleanup(onDone: (message: String) -> Unit) {
        viewModelScope.launch {
            _isReprocessingCleanup.value = true
            try {
                val mode = userPrefs.preferencesFlow.first().transcriptCleanupMode
                reprocessCleanupUseCase(meetingId, mode)
                onDone("Transcript re-cleaned (${mode.name.lowercase()} mode)")
            } catch (e: Exception) {
                onDone("Re-clean failed: ${e.message ?: "unknown error"}")
            } finally {
                _isReprocessingCleanup.value = false
            }
        }
    }

    /** All playback is owned by the single app-level [PlaybackController] — never a per-screen player. */
    val playbackState: StateFlow<PlaybackState> = PlaybackController.state

    fun playAudio(file: File) {
        PlaybackController.play(getApplication(), meetingId, meeting.value?.title ?: "Recording", file)
    }

    fun togglePlayPause() = PlaybackController.togglePlayPause()
    fun seekPlayback(positionMs: Long) = PlaybackController.seekTo(positionMs)

    /** Used by "jump to timestamp" actions (transcript/decisions/action items). Loads this recording into the shared player if it isn't already, then seeks and plays. */
    fun jumpToTimestamp(positionMs: Long) {
        val path = meeting.value?.audioFilePath
        if (PlaybackController.state.value.recordingId != meetingId && path != null) {
            playAudio(File(path))
        }
        PlaybackController.seekTo(positionMs)
        if (!PlaybackController.state.value.isPlaying) PlaybackController.togglePlayPause()
    }

    private val _asrModelInstalled = MutableStateFlow(
        modelStorage.isInstalled(com.example.ai.modelmanagement.ModelCatalog.parakeetTdtV3Int8.id)
    )
    val asrModelInstalled: StateFlow<Boolean> = _asrModelInstalled.asStateFlow()

    private val _llmModelInstalled = MutableStateFlow(anySummarizationModelInstalled())
    /** Whether *any* Meeting Intelligence model is really on disk. Needed so a finished recording
     * with no summary can say why — "no model installed" and "the model ran and found little" are
     * very different messages, and claiming to still be generating is a third thing that is
     * simply untrue once processing has ended. */
    val llmModelInstalled: StateFlow<Boolean> = _llmModelInstalled.asStateFlow()

    private fun anySummarizationModelInstalled(): Boolean =
        ModelCatalog.entries
            .filter { com.example.core.model.ModelCapability.SUMMARIZATION in it.capability }
            .any { modelStorage.isInstalled(it.id) }

    /** Re-checks real on-disk install state — call when the screen becomes visible again
     * (e.g. returning from the Model Manager), since a download may have finished meanwhile. */
    fun refreshModelAvailability() {
        _asrModelInstalled.value = modelStorage.isInstalled(com.example.ai.modelmanagement.ModelCatalog.parakeetTdtV3Int8.id)
        _llmModelInstalled.value = anySummarizationModelInstalled()
    }

    val meeting: StateFlow<Meeting?> = meetingRepository.getMeetingById(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val transcript: StateFlow<Transcript> = transcriptRepository.getTranscript(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Transcript(meetingId, emptyList())
    )

    val actionItems: StateFlow<List<ActionItem>> = actionItemRepository.getActionItemsForMeeting(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val decisions: StateFlow<List<Decision>> = transcriptRepository.getDecisions(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val questions: StateFlow<List<Question>> = transcriptRepository.getQuestions(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val topics: StateFlow<List<Topic>> = transcriptRepository.getTopics(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val chatMessages: StateFlow<List<ChatMessage>> = transcriptRepository.getChatMessages(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isAnswering = MutableStateFlow(false)
    val isAnswering: StateFlow<Boolean> = _isAnswering.asStateFlow()

    fun updateTitle(newTitle: String) {
        viewModelScope.launch {
            meetingRepository.updateMeetingTitle(meetingId, newTitle)
        }
    }

    fun renameSpeaker(speakerId: String, newName: String) {
        viewModelScope.launch {
            transcriptRepository.renameSpeaker(meetingId, speakerId, newName)
        }
    }

    /** Real per-speaker identity for this recording, including the persisted colour
     * (docs/recording-page-implementation.md §1.1 — speaker colour must be "persisted per
     * speaker id so they never shuffle", not re-derived on every render). */
    val speakers: StateFlow<List<com.example.core.model.Speaker>> = transcriptRepository.getSpeakers(meetingId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Document-level undo/redo (docs/recording-page-implementation.md §3.2 item 6) ---
    // A stack of already-applied, real database writes — undoing one performs the real inverse
    // write, it never just rewinds in-memory UI state. This is what lets a rewrite tool's apply
    // (phase 6) and a hand edit share one history: both push the same kind of entry.
    private sealed interface EditAction {
        data class TextEdit(val segmentId: String, val before: String, val after: String) : EditAction
        data class SpeakerReassign(
            val segmentIds: List<String>,
            val beforeSpeakerId: String?,
            val beforeSpeakerName: String?,
            val afterSpeakerId: String,
            val afterSpeakerName: String
        ) : EditAction
        data class Split(val originalId: String, val originalText: String, val originalEndMs: Long, val charOffset: Int, val newSegmentId: String) : EditAction
        data class Merge(val result: TranscriptRepository.MergeResult) : EditAction
    }

    private val _undoStack = MutableStateFlow<List<EditAction>>(emptyList())
    private val _redoStack = MutableStateFlow<List<EditAction>>(emptyList())
    val canUndo: StateFlow<Boolean> = _undoStack.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val canRedo: StateFlow<Boolean> = _redoStack.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun pushUndo(action: EditAction) {
        _undoStack.value = _undoStack.value + action
        _redoStack.value = emptyList()
    }

    /** Commits one segment's hand-corrected text immediately (autosave — §3.7 item 41) and
     * registers it as a single undo step. A no-op when the text didn't actually change, so
     * losing/regaining focus on an untouched segment never pollutes the undo stack. */
    fun editSegmentText(segmentId: String, before: String, after: String) {
        if (before == after) return
        viewModelScope.launch {
            transcriptRepository.updateSegmentText(segmentId, after)
            pushUndo(EditAction.TextEdit(segmentId, before, after))
        }
    }

    /** Splits [segmentId] at [charOffset] characters into [currentText] — [currentText] is
     * flushed to the database first (in the same launch, so there is no race with a pending
     * edit) so the split always operates on exactly what the user sees on screen. */
    fun splitSegment(segmentId: String, currentText: String, currentEndMs: Long, charOffset: Int) {
        viewModelScope.launch {
            transcriptRepository.updateSegmentText(segmentId, currentText)
            val newSegment = transcriptRepository.splitSegment(segmentId, charOffset) ?: return@launch
            pushUndo(EditAction.Split(segmentId, currentText, currentEndMs, charOffset, newSegment.id))
        }
    }

    /** Merges [segmentId] into the immediately preceding segment (same speaker only) — a no-op,
     * silently, when there is nothing eligible to merge into. [currentText] is flushed first, same
     * reasoning as [splitSegment]. */
    fun mergeSegmentWithPrevious(segmentId: String, currentText: String) {
        viewModelScope.launch {
            transcriptRepository.updateSegmentText(segmentId, currentText)
            val result = transcriptRepository.mergeSegmentWithPrevious(meetingId, segmentId) ?: return@launch
            pushUndo(EditAction.Merge(result))
        }
    }

    /** Reassigns [segmentIds] to a speaker — either [existingSpeakerId] or, when null, a brand
     * new speaker named [newSpeakerName] (docs/recording-page-implementation.md §3.2 item 9). */
    fun reassignSpeaker(
        segmentIds: List<String>,
        beforeSpeakerId: String?,
        beforeSpeakerName: String?,
        existingSpeakerId: String?,
        newSpeakerName: String?,
        newSpeakerColorHex: String
    ) {
        if (segmentIds.isEmpty()) return
        val afterId = existingSpeakerId ?: java.util.UUID.randomUUID().toString()
        val afterName = existingSpeakerId?.let { id -> speakers.value.find { it.id == id }?.customName } ?: newSpeakerName
        if (afterName.isNullOrBlank()) return
        viewModelScope.launch {
            transcriptRepository.reassignSpeaker(meetingId, segmentIds, afterId, afterName, newSpeakerColorHex)
            pushUndo(EditAction.SpeakerReassign(segmentIds, beforeSpeakerId, beforeSpeakerName, afterId, afterName))
        }
    }

    fun undo() {
        val action = _undoStack.value.lastOrNull() ?: return
        viewModelScope.launch {
            when (action) {
                is EditAction.TextEdit -> transcriptRepository.updateSegmentText(action.segmentId, action.before)
                is EditAction.SpeakerReassign -> transcriptRepository.setSegmentSpeakers(action.segmentIds, action.beforeSpeakerId, action.beforeSpeakerName)
                is EditAction.Split -> transcriptRepository.undoSplitSegment(action.originalId, action.originalText, action.originalEndMs, action.newSegmentId)
                is EditAction.Merge -> transcriptRepository.undoMergeSegment(action.result)
            }
            _undoStack.value = _undoStack.value.dropLast(1)
            _redoStack.value = _redoStack.value + action
        }
    }

    fun redo() {
        val action = _redoStack.value.lastOrNull() ?: return
        viewModelScope.launch {
            when (action) {
                is EditAction.TextEdit -> transcriptRepository.updateSegmentText(action.segmentId, action.after)
                is EditAction.SpeakerReassign -> {
                    // The speaker row created by the original apply is never deleted by undo, only
                    // the segments' pointers change — so this colour is only ever used in the
                    // (unreachable in practice) case where it's somehow gone missing.
                    val colorHex = speakers.value.find { it.id == action.afterSpeakerId }?.colorHex ?: "#6366F1"
                    transcriptRepository.reassignSpeaker(meetingId, action.segmentIds, action.afterSpeakerId, action.afterSpeakerName, colorHex)
                }
                is EditAction.Split -> transcriptRepository.splitSegment(action.originalId, action.charOffset)
                is EditAction.Merge -> transcriptRepository.mergeSegmentWithPrevious(meetingId, action.result.removed.id)
            }
            _redoStack.value = _redoStack.value.dropLast(1)
            _undoStack.value = _undoStack.value + action
        }
    }

    fun toggleActionItem(item: ActionItem) {
        viewModelScope.launch {
            actionItemRepository.toggleCompleted(item)
        }
    }

    fun addActionItem(task: String, assignee: String, deadline: String) {
        viewModelScope.launch {
            actionItemRepository.addActionItem(
                ActionItem(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    task = task,
                    assigneeSpeakerId = null,
                    assigneeName = assignee.ifBlank { null },
                    deadline = deadline.ifBlank { null },
                    // A manually-added action item is a direct user statement, not an AI
                    // inference — there is no meaningful "confidence" to attach to it.
                    confidence = null,
                    isCompleted = false
                )
            )
        }
    }

    fun deleteActionItem(id: String) {
        viewModelScope.launch {
            actionItemRepository.deleteActionItem(id)
        }
    }

    fun askQuestion(questionText: String) {
        if (questionText.isBlank()) return
        viewModelScope.launch {
            _isAnswering.value = true
            try {
                buildAskUseCase()(meetingId, questionText)
            } finally {
                _isAnswering.value = false
            }
        }
    }

    fun generateMarkdownExport(): String {
        val m = meeting.value ?: return ""
        val t = transcript.value
        val decs = decisions.value
        val actions = actionItems.value

        return buildString {
            appendLine("# ${m.title}")
            appendLine("**Date**: ${Formatters.formatDateRelative(m.createdAt)}")
            appendLine("**Duration**: ${Formatters.formatDurationHms(m.durationMs)}")
            appendLine("**Source**: ${m.source.name}")
            appendLine()
            appendLine("## Executive Summary")
            appendLine(m.summaryPreview ?: "No summary available.")
            appendLine()
            if (decs.isNotEmpty()) {
                appendLine("## Key Decisions")
                decs.forEach {
                    val confidenceSuffix = it.confidence?.let { c -> " *(Confidence: ${(c * 100).toInt()}%)*" } ?: ""
                    appendLine("- [${it.type.name}] ${it.text}$confidenceSuffix")
                }
                appendLine()
            }
            if (actions.isNotEmpty()) {
                appendLine("## Action Items")
                actions.forEach {
                    val status = if (it.isCompleted) "[x]" else "[ ]"
                    appendLine("- $status **${it.assigneeName ?: "Unassigned"}**: ${it.task} *(Due: ${it.deadline ?: "TBD"})*")
                }
                appendLine()
            }
            if (t.segments.isNotEmpty()) {
                appendLine("## Transcript")
                t.segments.forEach {
                    appendLine("**${it.speakerName ?: "Unlabeled speaker"}** [${Formatters.formatDurationHms(it.startMs)}]: ${it.text}")
                }
            }
        }
    }

    // Deliberately no onCleared() playback cleanup — playback is owned by PlaybackController /
    // PlaybackService, not this ViewModel, and must keep running after this screen is destroyed.
}

/** Full-screen overlays reachable from the Overview tab's Decisions/Action Items rows now that
 * those are no longer persistent tabs (docs/recording-page-implementation.md §2.1: "Tabs are
 * Overview · Transcript · Ask AI only — the current dynamic tab list ... is removed"). Phase 8 of
 * the redesign replaces these with the Overview stepper's own decision timeline / task steps;
 * until then this keeps the existing detail screens reachable rather than deleting them. */
private enum class DetailOverlay { ACTION_ITEMS, DECISIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(
    viewModel: MeetingDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit = {},
    onTranscribe: (meetingId: String, audioPath: String, durationMs: Long) -> Unit = { _, _, _ -> },
    /** Set when arriving from a search result: jumps straight to the Transcript tab, seeks/plays
     * audio to this position, and highlights the matching segment — a search result must land the
     * user in context, not just at the recording's Overview. */
    initialJumpToMs: Long? = null
) {
    val context = LocalContext.current
    val meeting by viewModel.meeting.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val isReprocessingCleanup by viewModel.isReprocessingCleanup.collectAsState()
    val actionItems by viewModel.actionItems.collectAsState()
    val decisions by viewModel.decisions.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val topics by viewModel.topics.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val speakers by viewModel.speakers.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val asrModelInstalled by viewModel.asrModelInstalled.collectAsState()
    val llmModelInstalled by viewModel.llmModelInstalled.collectAsState()
    val cleanFillerWords by viewModel.cleanFillerWords.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshModelAvailability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val playbackState by viewModel.playbackState.collectAsState()
    val isThisRecordingActive = playbackState.recordingId == meeting?.id
    LaunchedEffect(Unit) { PlaybackController.ensureConnected(context) }

    // The one place "which transcript segment is playing right now" gets computed — shared by the
    // player's optional lyrics-style preview and the Transcript tab's auto-scroll/highlight, so
    // there is exactly one observer of playback position driving both, not two independent ones.
    // derivedStateOf means downstream composables only recompose when the ANSWER changes (i.e.
    // playback crosses into the next segment), not on every ~200ms position tick.
    val activeSegmentState: State<TranscriptSegment?> = remember(transcript.segments) {
        derivedStateOf {
            if (!isThisRecordingActive) null
            else com.example.core.common.findActiveTranscriptSegment(transcript.segments, playbackState.positionMs)
        }
    }
    val activeSegment by activeSegmentState
    val isAnswering by viewModel.isAnswering.collectAsState()

    // Which Overview rows make sense is still driven by the recording's own IntelligenceProfile —
    // a Lecture or Idea never gets an empty "Decisions"/"Action Items" row dangling around
    // implying content that was never even asked for. The persistent tab bar itself is always
    // exactly Overview/Transcript/Ask AI (docs/recording-page-implementation.md §2.1) — Decisions
    // and Action Items are reachable as overlays from Overview instead of their own tabs.
    val intelligenceProfile = (meeting?.recordingType ?: RecordingType.GENERAL).intelligenceProfile()

    var currentTab by remember { mutableStateOf(RecordingDetailTab.OVERVIEW) }
    var overlay by remember { mutableStateOf<DetailOverlay?>(null) }
    var showFullPlayer by remember { mutableStateOf(false) }

    // Consumed exactly once per screen entry — re-composition or the user manually switching
    // tabs afterward must not keep forcing them back to this segment.
    var highlightedSegmentId by remember { mutableStateOf<String?>(null) }
    var jumpConsumed by remember { mutableStateOf(false) }
    LaunchedEffect(initialJumpToMs, transcript.segments) {
        if (jumpConsumed || initialJumpToMs == null || transcript.segments.isEmpty()) return@LaunchedEffect
        jumpConsumed = true
        currentTab = RecordingDetailTab.TRANSCRIPT
        viewModel.jumpToTimestamp(initialJumpToMs)
        highlightedSegmentId = transcript.segments
            .filter { it.startMs <= initialJumpToMs }
            .maxByOrNull { it.startMs }
            ?.id
            ?: transcript.segments.minByOrNull { kotlin.math.abs(it.startMs - initialJumpToMs) }?.id
    }

    var showEditTitleDialog by remember { mutableStateOf(false) }
    var editTitleText by remember { mutableStateOf("") }
    var showRenameSpeakerDialog by remember { mutableStateOf(false) }
    var renameSpeakerTargetId by remember { mutableStateOf("") }
    var renameSpeakerText by remember { mutableStateOf("") }
    var showAddActionDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    var pendingExportContentType by remember { mutableStateOf<com.example.core.export.ExportContentType?>(null) }
    fun writeExport(uri: android.net.Uri?, format: com.example.core.export.ExportFormat) {
        val m = meeting ?: return
        val contentType = pendingExportContentType ?: return
        if (uri == null) return
        context.contentResolver.openOutputStream(uri)?.use { out ->
            com.example.core.export.ExportManager.write(
                format = format,
                contentType = contentType,
                meeting = m,
                segments = transcript.segments,
                decisions = decisions,
                actionItems = actionItems,
                summaryText = m.summaryPreview,
                out = out
            )
        }
        Toast.makeText(context, "Saved ${contentType.displayName} as ${format.displayName}", Toast.LENGTH_SHORT).show()
    }
    val createMarkdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { writeExport(it, com.example.core.export.ExportFormat.MARKDOWN) }
    val createCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { writeExport(it, com.example.core.export.ExportFormat.CSV) }
    val createPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { writeExport(it, com.example.core.export.ExportFormat.PDF) }
    val createDocxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(com.example.core.export.ExportFormat.DOCX.mimeType)) { writeExport(it, com.example.core.export.ExportFormat.DOCX) }

    fun startExport(contentType: com.example.core.export.ExportContentType, format: com.example.core.export.ExportFormat) {
        pendingExportContentType = contentType
        showExportDialog = false
        val baseName = (meeting?.title ?: "recording").take(40).ifBlank { "recording" }
        val fileName = "${baseName}_${contentType.name.lowercase()}.${format.extension}"
        when (format) {
            com.example.core.export.ExportFormat.MARKDOWN -> createMarkdownLauncher.launch(fileName)
            com.example.core.export.ExportFormat.CSV -> createCsvLauncher.launch(fileName)
            com.example.core.export.ExportFormat.PDF -> createPdfLauncher.launch(fileName)
            com.example.core.export.ExportFormat.DOCX -> createDocxLauncher.launch(fileName)
        }
    }

    val audioPath = meeting?.audioFilePath

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            Surface(color = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNavigateBack)
                            .testTag("meeting_detail_back_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = InkSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meeting?.title ?: "Recording Details",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (currentTab) {
                                RecordingDetailTab.OVERVIEW -> meeting?.let {
                                    "${Formatters.formatDateHeader(it.createdAt)} · ${Formatters.formatDurationHms(it.durationMs)}"
                                } ?: ""
                                RecordingDetailTab.TRANSCRIPT -> "Transcript · ${transcript.segments.size} segments"
                                RecordingDetailTab.ASK_AI -> "Answers from this recording only"
                            },
                            fontSize = 11.5.sp,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("meeting_menu_btn")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = InkSecondary)
                        }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Title") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                editTitleText = meeting?.title ?: ""
                                showEditTitleDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-clean Transcript") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                            enabled = !isReprocessingCleanup,
                            onClick = {
                                menuExpanded = false
                                viewModel.reprocessCleanup { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Markdown Summary") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                val md = viewModel.generateMarkdownExport()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Recording Notes", md))
                                Toast.makeText(context, "Copied Markdown summary to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                        androidx.compose.material3.HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Share Summary") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                val m = meeting ?: return@DropdownMenuItem
                                ShareHelper.shareText(
                                    context = context,
                                    subject = m.title,
                                    text = ShareContentFormatter.summaryText(m, m.summaryPreview, decisions, actionItems),
                                    chooserTitle = "Share Summary"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Transcript") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                val m = meeting ?: return@DropdownMenuItem
                                ShareHelper.shareText(
                                    context = context,
                                    subject = m.title,
                                    text = ShareContentFormatter.transcriptText(m, transcript.segments),
                                    chooserTitle = "Share Transcript"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Action Items") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                val m = meeting ?: return@DropdownMenuItem
                                ShareHelper.shareText(
                                    context = context,
                                    subject = "Action Items — ${m.title}",
                                    text = ShareContentFormatter.actionItemsText(m, actionItems),
                                    chooserTitle = "Share Action Items"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Audio") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            enabled = meeting?.audioFilePath != null,
                            onClick = {
                                menuExpanded = false
                                val m = meeting ?: return@DropdownMenuItem
                                val path = m.audioFilePath ?: return@DropdownMenuItem
                                ShareHelper.shareAudio(context, java.io.File(path), m.title)
                            }
                        )
                        androidx.compose.material3.HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Export…") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showExportDialog = true
                            }
                        )
                    }
                    }

                    // The design's mini dial is a conic play/progress indicator, present on every
                    // tab and never scrolling away (docs/recording-page-implementation.md §2.1).
                    // Long-press reveals the full scrub player below (see showFullPlayer) — the
                    // old always-visible player's slider/lyrics-preview didn't fit the dial's
                    // "shrinks to a corner" brief, so it moved behind that disclosure instead of
                    // being deleted.
                    MiniDialPlayer(
                        progress = if (isThisRecordingActive && playbackState.durationMs > 0L) {
                            (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f,
                        isPlaying = isThisRecordingActive && playbackState.isPlaying,
                        onTogglePlayPause = {
                            if (isThisRecordingActive) {
                                viewModel.togglePlayPause()
                            } else if (audioPath != null) {
                                viewModel.playAudio(File(audioPath))
                            }
                        },
                        onLongPress = { showFullPlayer = !showFullPlayer },
                        modifier = Modifier.testTag("mini_dial_player")
                    )
                }
            }
        },
        bottomBar = {
            BottomTabBar(current = currentTab, onSelect = { currentTab = it })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Bento Audio Player Card — reflects the one shared playback session, not a
            // screen-owned player. Only surfaced on long-press of the mini dial now (see
            // showFullPlayer above); shows real live progress only when this recording is the one
            // actually loaded, otherwise a simple "Play" affordance that loads it into the shared
            // session (replacing whatever else was loaded, never running alongside it).
            AnimatedVisibility(
                visible = showFullPlayer && audioPath != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (audioPath != null) {
                    BentoAudioPlayerCard(
                        playbackState = if (isThisRecordingActive) playbackState else PlaybackState(),
                        segments = transcript.segments,
                        activeSegment = activeSegment,
                        onPlayPause = {
                            if (isThisRecordingActive) {
                                viewModel.togglePlayPause()
                            } else {
                                viewModel.playAudio(File(audioPath))
                            }
                        },
                        onSeek = { viewModel.seekPlayback(it) }
                    )
                }
            }

            // Honest "model required" banner — never shown alongside a fabricated transcript.
            if (meeting?.status == MeetingStatus.MODEL_REQUIRED) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Speech recognition model required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber
                            )
                            Text(
                                text = if (asrModelInstalled) {
                                    "The speech recognition model is now installed — transcribe this meeting on your device."
                                } else {
                                    "Download the offline speech recognition model to transcribe this meeting on your device."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (asrModelInstalled) {
                            val currentMeeting = meeting
                            TextButton(
                                onClick = {
                                    val audioPath = currentMeeting?.audioFilePath
                                    if (currentMeeting != null && audioPath != null) {
                                        onTranscribe(currentMeeting.id, audioPath, currentMeeting.durationMs)
                                    }
                                }
                            ) {
                                Text("Transcribe")
                            }
                        } else {
                            TextButton(onClick = onNavigateToModels) {
                                Text("Manage Models")
                            }
                        }
                    }
                }
            }

            // Tab Pages — the tab bar itself is [BottomTabBar] in Scaffold's bottomBar above.
            when (currentTab) {
                RecordingDetailTab.OVERVIEW -> BentoOverviewTab(
                    meeting = meeting,
                    topics = topics,
                    decisions = decisions,
                    actionItems = actionItems,
                    intelligenceProfile = intelligenceProfile,
                    onNavigateToTranscript = { currentTab = RecordingDetailTab.TRANSCRIPT },
                    onNavigateToActionItems = { overlay = DetailOverlay.ACTION_ITEMS },
                    onNavigateToDecisions = { overlay = DetailOverlay.DECISIONS },
                    llmModelInstalled = llmModelInstalled
                )
                RecordingDetailTab.TRANSCRIPT -> TranscriptTab(
                    segments = transcript.segments,
                    speakers = speakers,
                    onJumpToTimestamp = { viewModel.jumpToTimestamp(it) },
                    onRenameSpeaker = { id, name ->
                        renameSpeakerTargetId = id
                        renameSpeakerText = name
                        showRenameSpeakerDialog = true
                    },
                    onEditSegmentText = { segmentId, before, after -> viewModel.editSegmentText(segmentId, before, after) },
                    onSplitSegment = { segmentId, currentText, currentEndMs, offset -> viewModel.splitSegment(segmentId, currentText, currentEndMs, offset) },
                    onMergeSegmentWithPrevious = { segmentId, currentText -> viewModel.mergeSegmentWithPrevious(segmentId, currentText) },
                    onReassignSpeaker = { segmentIds, beforeId, beforeName, existingId, newName, colorHex ->
                        viewModel.reassignSpeaker(segmentIds, beforeId, beforeName, existingId, newName, colorHex)
                    },
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    highlightedSegmentId = highlightedSegmentId,
                    activePlaybackSegmentId = activeSegment?.id,
                    isAudioPlaying = isThisRecordingActive && playbackState.isPlaying,
                    cleanFillerWords = cleanFillerWords
                )
                RecordingDetailTab.ASK_AI -> AskAiTab(
                    chatMessages = chatMessages,
                    isAnswering = isAnswering,
                    onSendQuestion = { viewModel.askQuestion(it) },
                    onJumpToTimestamp = { viewModel.jumpToTimestamp(it) }
                )
            }
        }
    }

    // Decisions/Action Items overlays — see [DetailOverlay] above. Rendered on top of the main
    // Scaffold rather than as a fourth/fifth tab.
    if (overlay != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (overlay == DetailOverlay.ACTION_ITEMS) "Action Items" else "Decisions") },
                    navigationIcon = {
                        IconButton(onClick = { overlay = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { overlayPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(overlayPadding)) {
                when (overlay) {
                    DetailOverlay.ACTION_ITEMS -> ActionItemsTab(
                        actionItems = actionItems,
                        segments = transcript.segments,
                        onToggle = { viewModel.toggleActionItem(it) },
                        onDelete = { viewModel.deleteActionItem(it) },
                        onAddClick = { showAddActionDialog = true },
                        onJumpToTimestamp = { viewModel.jumpToTimestamp(it) }
                    )
                    DetailOverlay.DECISIONS -> DecisionsTab(
                        decisions = decisions,
                        questions = questions,
                        segments = transcript.segments,
                        onJumpToTimestamp = { viewModel.jumpToTimestamp(it) }
                    )
                    null -> Unit
                }
            }
        }
    }
    }

    // Dialogs
    if (showEditTitleDialog) {
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Edit Title") },
            text = {
                OutlinedTextField(
                    value = editTitleText,
                    onValueChange = { editTitleText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTitleText.isNotBlank()) {
                            viewModel.updateTitle(editTitleText.trim())
                        }
                        showEditTitleDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRenameSpeakerDialog) {
        AlertDialog(
            onDismissRequest = { showRenameSpeakerDialog = false },
            title = { Text("Rename Speaker") },
            text = {
                OutlinedTextField(
                    value = renameSpeakerText,
                    onValueChange = { renameSpeakerText = it },
                    label = { Text("Custom Speaker Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameSpeakerText.isNotBlank()) {
                            viewModel.renameSpeaker(renameSpeakerTargetId, renameSpeakerText.trim())
                        }
                        showRenameSpeakerDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameSpeakerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddActionDialog) {
        var taskInput by remember { mutableStateOf("") }
        var assigneeInput by remember { mutableStateOf("") }
        var deadlineInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddActionDialog = false },
            title = { Text("Add Action Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        label = { Text("Task Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = assigneeInput,
                        onValueChange = { assigneeInput = it },
                        label = { Text("Assignee (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = deadlineInput,
                        onValueChange = { deadlineInput = it },
                        label = { Text("Deadline (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (taskInput.isNotBlank()) {
                            viewModel.addActionItem(taskInput.trim(), assigneeInput.trim(), deadlineInput.trim())
                        }
                        showAddActionDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddActionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportDialog) {
        com.example.core.ui.ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { contentType, format -> startExport(contentType, format) }
        )
    }
}

@Composable
fun BentoAudioPlayerCard(
    playbackState: PlaybackState,
    segments: List<TranscriptSegment>,
    activeSegment: TranscriptSegment?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val playerState = playbackState
    var showTranscriptPreview by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary)
                ) {
                    if (playerState.phase == PlaybackPhase.LOADING) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Slider(
                    value = playerState.positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..playerState.durationMs.toFloat().coerceAtLeast(1f),
                    enabled = playerState.canSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoPrimaryLight,
                        activeTrackColor = IndigoPrimaryLight
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                Text(
                    text = "${Formatters.formatDurationHms(playerState.positionMs)} / ${Formatters.formatDurationHms(playerState.durationMs)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (segments.isNotEmpty()) {
                    IconButton(
                        onClick = { showTranscriptPreview = !showTranscriptPreview },
                        modifier = Modifier.testTag("player_transcript_toggle")
                    ) {
                        Icon(
                            Icons.Default.Subject,
                            contentDescription = if (showTranscriptPreview) "Hide transcript" else "Show transcript",
                            tint = if (showTranscriptPreview) IndigoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Lyrics-style preview: the current segment only, plus the next one dimmed as light
            // context — never the whole transcript, which belongs to the Transcript tab.
            AnimatedVisibility(
                visible = showTranscriptPreview && segments.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val currentIndex = activeSegment?.let { segments.indexOf(it) } ?: -1
                    Text(
                        text = activeSegment?.text
                            ?: if (playerState.isPlaying) "…" else "Press play to follow along.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentIndex in segments.indices && currentIndex + 1 < segments.size) {
                        Text(
                            text = segments[currentIndex + 1].text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BentoOverviewTab(
    meeting: Meeting?,
    topics: List<Topic>,
    decisions: List<Decision>,
    actionItems: List<ActionItem>,
    onNavigateToTranscript: () -> Unit,
    onNavigateToActionItems: () -> Unit,
    onNavigateToDecisions: () -> Unit,
    /** Which categories this recording type actually produces — a Lecture or Idea shows no
     * Decisions/Action Items rows at all, rather than a row pointing at a tab that doesn't exist. */
    intelligenceProfile: IntelligenceProfile = RecordingType.GENERAL.intelligenceProfile(),
    /** Real on-disk state, used only to explain an absent summary honestly. */
    llmModelInstalled: Boolean = true
) {
    if (meeting == null) return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Summary — a single quiet container, not a bordered bento card.
        item {
            SectionCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val summary = meeting.summaryPreview
                    // Never claim to still be generating once processing has actually finished —
                    // a READY recording with no summary is a real outcome with a real cause, and
                    // a permanent "Generating..." would be a progress indicator for work that
                    // already stopped.
                    val summaryPlaceholder = when {
                        meeting.status == MeetingStatus.PROCESSING -> "Generating notes on your device..."
                        meeting.status == MeetingStatus.MODEL_REQUIRED || !llmModelInstalled ->
                            "No summary yet — the Meeting Intelligence model isn't installed. Your recording and transcript are safe; install it in AI Engine to generate notes."
                        meeting.status == MeetingStatus.ERROR -> "Processing didn't finish, so no summary was generated."
                        else -> "No summary was generated for this recording."
                    }
                    Text(
                        text = summary?.takeIf { it.isNotBlank() } ?: summaryPlaceholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (summary.isNullOrBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // 2. Grouped list of rows — matches the reference's flat "AI Summary" list exactly.
        // Decisions/Action Items rows only appear when this recording type actually produces
        // them (IntelligenceProfile) — never a row pointing at empty, meaningless content.
        item {
            SectionCard {
                // An empty list is a legitimate result, not a malfunction — plenty of recordings
                // are notes or chats with nothing decided and nothing assigned. Say that plainly
                // instead of using wording that reads like the analysis fell over.
                if (intelligenceProfile.extractDecisions) {
                    ListRow(
                        title = "Decisions",
                        subtitle = decisions.firstOrNull()?.text ?: "Nothing was decided in this recording",
                        icon = Icons.Default.Psychology,
                        onClick = onNavigateToDecisions,
                        trailing = { CountTrailing(decisions.size) }
                    )
                }
                if (intelligenceProfile.extractActionItems) {
                    ListRow(
                        title = "Action Items",
                        subtitle = actionItems.firstOrNull()?.task ?: "No tasks came out of this recording",
                        icon = Icons.Default.FormatListBulleted,
                        onClick = onNavigateToActionItems,
                        trailing = { CountTrailing(actionItems.size) }
                    )
                }
                ListRow(
                    title = "Transcript",
                    subtitle = "Full text with speakers and timestamps",
                    icon = Icons.Default.Subject,
                    onClick = onNavigateToTranscript,
                    showDivider = false
                )
            }
        }

        // 3. Key points/concepts/topics — labeled to match what this recording type actually is
        // (e.g. "Key Concepts" for a Lecture, "Key Points" for an Idea), not generic "Topics".
        if (topics.isNotEmpty()) {
            item {
                SectionCard {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = intelligenceProfile.topicsLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            topics.forEach { topic ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        text = topic.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Details — duration / speakers / type, as quiet rows rather than a metric strip.
        item {
            SectionCard {
                ListRow(
                    title = "Duration",
                    icon = Icons.Default.Schedule,
                    trailing = { Text(Formatters.formatDurationHms(meeting.durationMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                ListRow(
                    title = "Speakers",
                    icon = Icons.Default.Group,
                    trailing = { Text("${meeting.participantCount} detected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                ListRow(
                    title = "Type",
                    icon = Icons.Default.GraphicEq,
                    showDivider = false,
                    trailing = { Text(meeting.recordingType.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
    }
}

@Composable
private fun CountTrailing(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Segments longer than this default to collapsed (timestamp + speaker + short preview) so a long
 * transcript stays scannable; anything at or under it defaults to expanded since there's nothing
 * to gain by hiding a one-line segment behind a tap. Either can always be toggled by tapping. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TranscriptTab(
    segments: List<TranscriptSegment>,
    speakers: List<com.example.core.model.Speaker>,
    onJumpToTimestamp: (Long) -> Unit,
    onRenameSpeaker: (speakerId: String, currentName: String) -> Unit,
    /** Autosaves one segment's hand-corrected text (docs/recording-page-implementation.md §3.7
     * item 41) — called on blur/focus-switch, not per keystroke. */
    onEditSegmentText: (segmentId: String, before: String, after: String) -> Unit,
    onSplitSegment: (segmentId: String, currentText: String, currentEndMs: Long, charOffset: Int) -> Unit,
    onMergeSegmentWithPrevious: (segmentId: String, currentText: String) -> Unit,
    onReassignSpeaker: (
        segmentIds: List<String>,
        beforeSpeakerId: String?,
        beforeSpeakerName: String?,
        existingSpeakerId: String?,
        newSpeakerName: String?,
        newSpeakerColorHex: String
    ) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    /** Set briefly when this tab is opened via a search-result deep link — scrolls to and tints
     * this one segment so the user immediately sees why it matched, without permanently marking
     * it (a manual tab switch afterward leaves it as an ordinary segment again). */
    highlightedSegmentId: String? = null,
    /** The segment [PlaybackController]'s current position falls within, for the recording that
     * is actually loaded — null when nothing is playing or a different recording is loaded. */
    activePlaybackSegmentId: String? = null,
    isAudioPlaying: Boolean = false,
    /** Display-only hesitation-word cleanup. Never applied in edit mode: the user edits and saves
     * the real stored text, so showing them a cleaned version to type over would quietly turn a
     * display preference into a permanent rewrite of their transcript. */
    cleanFillerWords: Boolean = true
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    // The toolbar is text until you need it (docs/recording-page-implementation.md §2.3) — there
    // is no persistent search field or edit button above the transcript any more; both are
    // entered from the three-word row at the bottom.
    var showSearch by remember { mutableStateOf(false) }
    var searchMatchIndex by remember { mutableStateOf(0) }
    var isEditMode by remember { mutableStateOf(false) }
    // Only the focused segment is actually editable at a time (docs/recording-page-implementation.md
    // §2.5) — everything else in fieldValues is just segments the user has already visited this
    // session, kept so switching back and forth doesn't lose an uncommitted selection/cursor.
    var focusedSegmentId by remember { mutableStateOf<String?>(null) }
    var fieldValues by remember { mutableStateOf<Map<String, androidx.compose.ui.text.input.TextFieldValue>>(emptyMap()) }
    var speakerPickerTarget by remember { mutableStateOf<TranscriptSegment?>(null) }
    // "Spotify lyrics"-style following: auto-scrolls to whichever segment is currently playing.
    var syncToAudio by rememberSaveable { mutableStateOf(true) }

    fun commitFocused() {
        val id = focusedSegmentId ?: return
        val original = segments.find { it.id == id } ?: return
        val current = fieldValues[id]?.text ?: return
        if (current != original.text) onEditSegmentText(id, original.text, current)
    }

    // Search mode (docs/recording-page-implementation.md §2.3/§3.2 item 11): the transcript stays
    // fully visible, matches are highlighted in place, next/prev step through them by seeking —
    // unlike the old behaviour, non-matching segments are never hidden.
    val searchMatches = remember(segments, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else segments.filter {
            it.text.contains(searchQuery, ignoreCase = true) || (it.speakerName?.contains(searchQuery, ignoreCase = true) == true)
        }.map { it.id }
    }
    LaunchedEffect(searchQuery) { searchMatchIndex = 0 }

    // Speaker colour: the real persisted one where a speaker row already exists, falling back to
    // the first-appearance palette only for a speaker id not yet reflected in [speakers] (e.g. one
    // reassigned moments ago, before Room's Flow has re-emitted).
    val speakerColorMap = remember(speakers, segments) {
        val byId = speakers.associate { sp ->
            sp.id to (runCatching { Color(android.graphics.Color.parseColor(sp.colorHex)) }.getOrNull() ?: Accent)
        }
        val palette = listOf(Accent, Speaker2, Speaker3, Speaker4)
        val unseen = segments.mapNotNull { it.speakerId }.distinct().filterNot { byId.containsKey(it) }
        byId + unseen.withIndex().associate { (index, id) -> id to palette[index % palette.size] }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // One-shot deep-link scroll (search result), independent of playback sync.
    LaunchedEffect(highlightedSegmentId, segments) {
        if (highlightedSegmentId == null) return@LaunchedEffect
        val index = segments.indexOfFirst { it.id == highlightedSegmentId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    // Playback-follow scroll: only re-runs when the ANSWER changes (activePlaybackSegmentId is
    // itself derived with derivedStateOf upstream), not on every ~200ms position tick, and does
    // nothing at all while the user has sync switched off.
    LaunchedEffect(activePlaybackSegmentId, syncToAudio) {
        if (!syncToAudio || activePlaybackSegmentId == null) return@LaunchedEffect
        val index = segments.indexOfFirst { it.id == activePlaybackSegmentId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    // Jump to (and seek) the current search match whenever it changes.
    LaunchedEffect(searchMatchIndex, searchMatches) {
        val id = searchMatches.getOrNull(searchMatchIndex) ?: return@LaunchedEffect
        val index = segments.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (isEditMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    color = InkSecondary,
                    // Discards only whatever is still sitting uncommitted in the focused field —
                    // every other segment touched this session already autosaved on blur.
                    modifier = Modifier
                        .clickable {
                            isEditMode = false
                            focusedSegmentId = null
                            fieldValues = emptyMap()
                        }
                        .testTag("transcript_edit_cancel_btn")
                )
                Text(
                    text = "Save",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    modifier = Modifier
                        .clickable {
                            commitFocused()
                            isEditMode = false
                            focusedSegmentId = null
                            fieldValues = emptyMap()
                        }
                        .testTag("transcript_edit_save_btn")
                )
            }
        } else if (showSearch) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search transcript…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("transcript_search_field")
                    )
                    Text(
                        text = "Done",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                        modifier = Modifier.clickable {
                            showSearch = false
                            searchQuery = ""
                        }
                    )
                }
                if (searchQuery.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchMatches.isEmpty()) "No matches" else "${searchMatchIndex + 1} of ${searchMatches.size}",
                            fontSize = 12.5.sp,
                            color = InkMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text(
                                text = "Prev",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (searchMatches.isEmpty()) InkFaint else InkSecondary,
                                modifier = Modifier.clickable(enabled = searchMatches.isNotEmpty()) {
                                    searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                }
                            )
                            Text(
                                text = "Next",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (searchMatches.isEmpty()) InkFaint else Ink,
                                modifier = Modifier.clickable(enabled = searchMatches.isNotEmpty()) {
                                    searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size
                                }
                            )
                        }
                    }
                }
            }
        }

        if (!isEditMode && (activePlaybackSegmentId != null || isAudioPlaying)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = syncToAudio,
                    onCheckedChange = { syncToAudio = it },
                    modifier = Modifier.testTag("transcript_sync_toggle")
                )
                Text(
                    text = "Sync to audio",
                    fontSize = 12.5.sp,
                    color = InkSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = if (isEditMode || showSearch) 6.dp else 28.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            items(segments, key = { it.id }) { seg ->
                val isPlaying = seg.id == activePlaybackSegmentId
                val isDeepLinkHighlighted = seg.id == highlightedSegmentId
                val isSearchMatch = showSearch && seg.id in searchMatches
                val isFocused = isEditMode && seg.id == focusedSegmentId
                val speakerColor = seg.speakerId?.let { speakerColorMap[it] } ?: InkMuted
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                val coroutineScope = rememberCoroutineScope()

                // No cards, no borders, no elevation in reading mode
                // (docs/recording-page-implementation.md §2.3) — just a 38dp mono-timestamp gutter
                // and, for the playing segment only, a 2dp Accent rule immediately left of the text.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDeepLinkHighlighted || isSearchMatch) AccentWash else Color.Transparent)
                        .drawBehind {
                            if (isPlaying) {
                                val x = 50.dp.toPx()
                                drawLine(
                                    color = Accent,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        .then(
                            if (isEditMode) {
                                Modifier.clickable(enabled = !isFocused) {
                                    commitFocused()
                                    focusedSegmentId = seg.id
                                    if (seg.id !in fieldValues) {
                                        fieldValues = fieldValues + (seg.id to androidx.compose.ui.text.input.TextFieldValue(seg.text))
                                    }
                                }
                            } else {
                                Modifier.clickable { onJumpToTimestamp(seg.startMs) }
                            }
                        )
                        .alpha(if (isEditMode && !isFocused) 0.4f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = Formatters.formatDurationHms(seg.startMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.5.sp,
                        color = if (isPlaying) Accent else InkFaint,
                        modifier = Modifier
                            .width(38.dp)
                            .padding(top = 5.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = seg.speakerName ?: "Unlabeled speaker",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                                color = speakerColor,
                                modifier = when {
                                    isFocused -> Modifier.clickable { speakerPickerTarget = seg }
                                    !isEditMode -> seg.speakerId?.let { spkId ->
                                        Modifier.clickable { onRenameSpeaker(spkId, seg.speakerName ?: "") }
                                    } ?: Modifier
                                    else -> Modifier
                                }
                            )
                            if (isFocused) {
                                Text("▾", fontSize = 11.sp, color = InkFaint)
                            }
                            if (seg.isUserEdited) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Edited by you",
                                    tint = Accent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        if (isFocused) {
                            val fieldValue = fieldValues[seg.id] ?: androidx.compose.ui.text.input.TextFieldValue(seg.text)
                            Box {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = fieldValue,
                                    onValueChange = { newValue ->
                                        val oldPos = fieldValue.selection.start
                                        val backspaceAtStart = newValue.text.length == fieldValue.text.length - 1 &&
                                            oldPos == 0 && fieldValue.selection.collapsed && newValue.selection.start == 0
                                        if (backspaceAtStart) {
                                            onMergeSegmentWithPrevious(seg.id, fieldValue.text)
                                            focusedSegmentId = null
                                            fieldValues = fieldValues - seg.id
                                        } else {
                                            fieldValues = fieldValues + (seg.id to newValue)
                                        }
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.5.sp, lineHeight = 29.sp, color = Ink),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(bringIntoViewRequester)
                                        .onFocusEvent { focusState ->
                                            if (focusState.isFocused) {
                                                coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                                            }
                                        }
                                        .testTag("transcript_segment_edit_${seg.id}")
                                )
                            }
                            if (!fieldValue.selection.collapsed) {
                                Box(modifier = Modifier.padding(top = 8.dp)) {
                                    com.example.feature.meetingdetail.components.FloatingSelectionBar(
                                        items = listOf("Fix errors", "Clarity", "Condense"),
                                        onItemClick = {
                                            Toast.makeText(context, "AI tools are coming in a later phase", Toast.LENGTH_SHORT).show()
                                        },
                                        moreLabel = "⋯",
                                        onMoreClick = {
                                            Toast.makeText(context, "AI tools are coming in a later phase", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        } else {
                            val displayText = remember(seg.text, cleanFillerWords) {
                                com.example.core.common.FillerWordCleaner.cleanIf(cleanFillerWords, seg.text)
                            }
                            Text(
                                text = displayText,
                                fontSize = 16.5.sp,
                                lineHeight = 28.sp,
                                color = if (isPlaying) Ink else InkSecondary
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(LineSoft, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }
                .padding(horizontal = 18.dp)
        ) {
            if (isEditMode) {
                val focusedField = focusedSegmentId?.let { fieldValues[it] }
                val canSplit = focusedField != null && focusedField.selection.start > 0 && focusedField.selection.start < focusedField.text.length
                TranscriptToolbarWord(label = "Undo", active = false, enabled = canUndo, modifier = Modifier.weight(1f), onClick = onUndo)
                TranscriptToolbarWord(label = "Redo", active = false, enabled = canRedo, modifier = Modifier.weight(1f), onClick = onRedo)
                TranscriptToolbarWord(
                    label = "Speaker",
                    active = false,
                    enabled = focusedSegmentId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    speakerPickerTarget = segments.find { it.id == focusedSegmentId }
                }
                TranscriptToolbarWord(label = "Split", active = false, enabled = canSplit, modifier = Modifier.weight(1f)) {
                    val id = focusedSegmentId ?: return@TranscriptToolbarWord
                    val fv = focusedField ?: return@TranscriptToolbarWord
                    val seg = segments.find { it.id == id } ?: return@TranscriptToolbarWord
                    onSplitSegment(id, fv.text, seg.endMs, fv.selection.start)
                    focusedSegmentId = null
                    fieldValues = fieldValues - id
                }
            } else {
                TranscriptToolbarWord(
                    label = "Search",
                    active = showSearch,
                    modifier = Modifier.weight(1f).testTag("transcript_search_btn")
                ) {
                    showSearch = !showSearch
                    if (!showSearch) searchQuery = ""
                }
                TranscriptToolbarWord(
                    label = "Edit",
                    active = false,
                    modifier = Modifier.weight(1f).testTag("transcript_edit_btn")
                ) {
                    showSearch = false
                    isEditMode = true
                }
                TranscriptToolbarWord(
                    label = "Tools",
                    active = !showSearch,
                    modifier = Modifier.weight(1f)
                ) {
                    // The AI tools sheet (docs/recording-page-implementation.md §2.4) is a later
                    // phase of this redesign — say so honestly rather than a dead tap target.
                    Toast.makeText(context, "AI tools are coming in a later phase", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pickerTarget = speakerPickerTarget
    if (pickerTarget != null) {
        SpeakerReassignDialog(
            target = pickerTarget,
            allSegments = segments,
            speakers = speakers,
            onDismiss = { speakerPickerTarget = null },
            onConfirm = { segmentIds, existingSpeakerId, newSpeakerName, colorHex ->
                onReassignSpeaker(segmentIds, pickerTarget.speakerId, pickerTarget.speakerName, existingSpeakerId, newSpeakerName, colorHex)
                speakerPickerTarget = null
            }
        )
    }
}

@Composable
private fun TranscriptToolbarWord(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (!enabled) InkFaint else if (active) Ink else InkSecondary
        )
    }
}

/** ▾ picker on a focused segment's speaker label (docs/recording-page-implementation.md §3.2 item
 * 9): existing speakers, a brand-new speaker, and — when the following segments share the
 * segment's original speaker — the option to reassign that whole contiguous run at once. */
@Composable
private fun SpeakerReassignDialog(
    target: TranscriptSegment,
    allSegments: List<TranscriptSegment>,
    speakers: List<com.example.core.model.Speaker>,
    onDismiss: () -> Unit,
    onConfirm: (segmentIds: List<String>, existingSpeakerId: String?, newSpeakerName: String?, colorHex: String) -> Unit
) {
    var applyToRun by remember { mutableStateOf(true) }
    var newSpeakerName by remember { mutableStateOf("") }
    var creatingNew by remember { mutableStateOf(false) }

    val runIds = remember(target, allSegments) {
        val startIndex = allSegments.indexOfFirst { it.id == target.id }
        if (startIndex < 0) listOf(target.id)
        else {
            val ids = mutableListOf(target.id)
            var i = startIndex + 1
            while (i < allSegments.size && allSegments[i].speakerId == target.speakerId) {
                ids += allSegments[i].id
                i++
            }
            ids
        }
    }
    val targetIds = if (applyToRun) runIds else listOf(target.id)
    val palette = listOf("#6366F1", "#A855F7", "#10B981", "#F59E0B")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reassign speaker") },
        text = {
            Column {
                speakers.filter { it.id != target.speakerId }.forEach { sp ->
                    Text(
                        text = sp.customName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(targetIds, sp.id, null, sp.colorHex) }
                            .padding(vertical = 12.dp)
                    )
                }
                if (!creatingNew) {
                    Text(
                        text = "New speaker…",
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creatingNew = true }
                            .padding(vertical = 12.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = newSpeakerName,
                        onValueChange = { newSpeakerName = it },
                        label = { Text("Speaker name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                if (runIds.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { applyToRun = !applyToRun },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.Checkbox(checked = applyToRun, onCheckedChange = { applyToRun = it })
                        Text("Apply to the rest of this run (${runIds.size} segments)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                Button(
                    onClick = {
                        if (newSpeakerName.isNotBlank()) {
                            val colorHex = palette[speakers.size % palette.size]
                            onConfirm(targetIds, null, newSpeakerName.trim(), colorHex)
                        }
                    },
                    enabled = newSpeakerName.isNotBlank()
                ) { Text("Create") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ActionItemsTab(
    actionItems: List<ActionItem>,
    segments: List<TranscriptSegment>,
    onToggle: (ActionItem) -> Unit,
    onDelete: (String) -> Unit,
    onAddClick: () -> Unit,
    onJumpToTimestamp: (Long) -> Unit
) {
    val segmentStartMsById = remember(segments) { segments.associate { it.id to it.startMs } }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = IndigoPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_add_action_item")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Action Item")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (actionItems.isEmpty()) {
                item {
                    Text(
                        text = "No tasks came out of this recording. Not every conversation has one — tap + to add your own.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(actionItems, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = item.isCompleted,
                                onCheckedChange = { onToggle(item) },
                                colors = CheckboxDefaults.colors(checkedColor = SuccessGreen)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.task,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Assignee: ${item.assigneeName ?: "Unassigned"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Due: ${item.deadline ?: "TBD"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            item.sourceSegmentIds.firstOrNull()?.let { segmentStartMsById[it] }?.let { ts ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IndigoPrimary.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable { onJumpToTimestamp(ts) }
                                ) {
                                    Text(
                                        text = Formatters.formatDurationHms(ts),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = IndigoPrimaryLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DecisionsTab(
    decisions: List<Decision>,
    questions: List<Question>,
    segments: List<TranscriptSegment> = emptyList(),
    onJumpToTimestamp: (Long) -> Unit = {}
) {
    val segmentStartMsById = remember(segments) { segments.associate { it.id to it.startMs } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Agreed Decisions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (decisions.isEmpty()) {
            item {
                Text(
                    text = "Nothing was decided in this recording. That's a normal result for notes and open-ended conversations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(decisions, key = { it.id }) { dec ->
                val typeColor = when (dec.type) {
                    com.example.core.model.DecisionType.DECISION -> SuccessGreen
                    com.example.core.model.DecisionType.SUGGESTION -> IndigoPrimaryLight
                    com.example.core.model.DecisionType.POSSIBILITY -> WarningAmber
                    com.example.core.model.DecisionType.DISCUSSION -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, typeColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dec.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dec.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = typeColor,
                                    fontWeight = FontWeight.Bold
                                )
                                // Never a fabricated percentage: only shown when the extraction actually provided one.
                                dec.confidence?.let { c ->
                                    Text(
                                        text = "• Confidence: ${(c * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        dec.sourceSegmentIds.firstOrNull()?.let { segmentStartMsById[it] }?.let { ts ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = typeColor.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { onJumpToTimestamp(ts) }
                            ) {
                                Text(
                                    text = Formatters.formatDurationHms(ts),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = typeColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (questions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Questions & Inquiries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(questions, key = { it.id }) { q ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = VioletSecondary)
                            Text(
                                text = q.text,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        q.answer?.let { ans ->
                            Text(
                                text = "Resolution: $ans",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskAiTab(
    chatMessages: List<ChatMessage>,
    isAnswering: Boolean,
    onSendQuestion: (String) -> Unit,
    onJumpToTimestamp: (Long) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val presetChips = listOf(
        "What did we agree on?",
        "What are the next steps?",
        "What was the budget decision?",
        "Who owns the documentation?"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (chatMessages.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = IndigoPrimary.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = IndigoPrimaryLight)
                                Text(
                                    text = "Grounded Meeting Q&A",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimaryLight
                                )
                            }
                            Text(
                                text = "Ask anything about this meeting. Answers are computed locally and strictly anchored in the transcript with timestamp citations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            items(chatMessages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    onJumpToTimestamp = onJumpToTimestamp
                )
            }

            if (isAnswering) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = IndigoPrimaryLight
                        )
                        Text(
                            text = "Analyzing transcript offline...",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndigoPrimaryLight
                        )
                    }
                }
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetChips.forEach { chip ->
                AssistChip(
                    onClick = { onSendQuestion(chip) },
                    label = { Text(chip, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask about this meeting...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ask_ai_input")
            )

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendQuestion(inputText.trim())
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(IndigoPrimary)
                    .testTag("ask_ai_send_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onJumpToTimestamp: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) IndigoPrimary else MaterialTheme.colorScheme.surface
            ),
            border = if (!message.isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                if (message.sourceTimestamps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sources:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        message.sourceTimestamps.forEach { ts ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IndigoPrimary.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { onJumpToTimestamp(ts) }
                            ) {
                                Text(
                                    text = Formatters.formatDurationHms(ts),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = IndigoPrimaryLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
