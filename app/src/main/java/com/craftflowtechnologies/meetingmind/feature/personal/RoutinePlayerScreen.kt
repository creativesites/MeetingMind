package com.craftflowtechnologies.meetingmind.feature.personal

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.personal.PersonalGrowthCatalog
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRepository
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRoutine
import com.craftflowtechnologies.meetingmind.core.personal.PersonalSoundscape
import com.craftflowtechnologies.meetingmind.core.personal.RoutineCompletion
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStep
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStepKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMe
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMeSheet
import com.craftflowtechnologies.meetingmind.feature.work.previewOf
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RoutinePlayerUiState(
    val routine: PersonalRoutine? = null,
    val completion: RoutineCompletion? = null,
    val steps: List<RoutineStep> = emptyList(),
    val stepIndex: Int = 0,
    val minEffortMode: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val activeSoundscape: PersonalSoundscape = PersonalSoundscape.NONE,
    val memoryForStep: Note? = null,
    val reflectionDrafts: Map<String, String> = emptyMap(),
    val savedJournalNoteId: String? = null,
    val loading: Boolean = true
) {
    val currentStep: RoutineStep? get() = steps.getOrNull(stepIndex)
}

class RoutinePlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PersonalRepository(application)

    private val _ui = MutableStateFlow(RoutinePlayerUiState())
    val ui: StateFlow<RoutinePlayerUiState> = _ui.asStateFlow()

    private var loadedKey: String? = null

    fun load(routineId: String, minEffort: Boolean) {
        val key = "$routineId:$minEffort"
        if (loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            repo.ensureStarterRoutinesSeeded()
            val routine = repo.getRoutine(routineId)
            if (routine == null) {
                _ui.value = RoutinePlayerUiState(loading = false)
                return@launch
            }
            val activeSteps = routine.activeSteps
            val completion = repo.startOrResumeRoutine(routineId, minEffortMode = minEffort)
            val resumeIdx = completion.currentStepIndex.coerceIn(0, (activeSteps.size - 1).coerceAtLeast(0))
            val memory = runCatching { repo.getRecentMemoryForRoutine() }.getOrNull()
            _ui.value = RoutinePlayerUiState(
                routine = routine,
                completion = completion,
                steps = activeSteps,
                stepIndex = resumeIdx,
                minEffortMode = completion.usedMinEffortMode,
                activeSoundscape = routine.soundscape,
                memoryForStep = memory,
                savedJournalNoteId = completion.reflectionNoteId,
                loading = false
            )
        }
    }

    fun toggleMinEffort() {
        val cur = _ui.value
        val nextMin = !cur.minEffortMode
        val updatedComp = cur.completion?.copy(usedMinEffortMode = nextMin)
        _ui.value = cur.copy(minEffortMode = nextMin, completion = updatedComp)
        updatedComp?.let { viewModelScope.launch { repo.saveRoutineProgress(it) } }
    }

    fun togglePause() {
        _ui.value = _ui.value.copy(isPaused = !_ui.value.isPaused)
    }

    fun cycleSoundscape() {
        val cur = _ui.value
        val entries = PersonalSoundscape.entries
        val next = entries[(cur.activeSoundscape.ordinal + 1) % entries.size]
        _ui.value = cur.copy(activeSoundscape = next)
    }

    fun updateDraft(stepId: String, text: String) {
        _ui.value = _ui.value.copy(reflectionDrafts = _ui.value.reflectionDrafts + (stepId to text))
    }

    fun goToPreviousStep() {
        val cur = _ui.value
        if (cur.stepIndex <= 0 || cur.isFinished) return
        val prevIdx = cur.stepIndex - 1
        val updated = cur.completion?.copy(currentStepIndex = prevIdx)
        _ui.value = cur.copy(stepIndex = prevIdx, completion = updated, isPaused = false)
        updated?.let { viewModelScope.launch { repo.saveRoutineProgress(it) } }
    }

    fun completeCurrentStep(skipped: Boolean = false) {
        val cur = _ui.value
        val routine = cur.routine ?: return
        val step = cur.currentStep ?: return
        val completion = cur.completion ?: return
        val draft = cur.reflectionDrafts[step.id].orEmpty().trim()

        viewModelScope.launch {
            var noteId = cur.savedJournalNoteId
            if (!skipped && draft.isNotBlank()) {
                if (step.kind == RoutineStepKind.INTENTION) {
                    val plan = repo.setTodayIntention(draft, stitchIntoJournal = true)
                    noteId = plan.dayPageNoteId ?: noteId
                } else {
                    val (stitchedId, _) = repo.stitchEntryIntoDayPage(
                        sectionHeading = "${routine.title}: ${step.effectiveTitle(cur.minEffortMode)}",
                        bodyText = draft,
                        sourceRoutineId = routine.id
                    )
                    noteId = stitchedId
                }
            }

            val nextDoneIds = if (skipped) completion.completedStepIds else (completion.completedStepIds + step.id).distinct()
            val nextIdx = cur.stepIndex + 1
            val elapsedAdd = if (skipped) 0 else step.effectiveDurationSeconds(cur.minEffortMode)

            if (nextIdx >= cur.steps.size) {
                val finished = repo.completeRoutine(
                    completion.copy(
                        currentStepIndex = cur.steps.lastIndex.coerceAtLeast(0),
                        completedStepIds = nextDoneIds,
                        actualDurationSeconds = completion.actualDurationSeconds + elapsedAdd
                    ),
                    reflectionNoteId = noteId
                )
                _ui.value = cur.copy(
                    completion = finished,
                    savedJournalNoteId = noteId,
                    isFinished = true
                )
            } else {
                val progressed = completion.copy(
                    currentStepIndex = nextIdx,
                    completedStepIds = nextDoneIds,
                    actualDurationSeconds = completion.actualDurationSeconds + elapsedAdd,
                    reflectionNoteId = noteId
                )
                repo.saveRoutineProgress(progressed)
                _ui.value = cur.copy(
                    completion = progressed,
                    stepIndex = nextIdx,
                    savedJournalNoteId = noteId,
                    isPaused = false
                )
            }
        }
    }
}

/**
 * Full-screen Fabulous-inspired Guided Routine Execution Mode.
 * Features step-by-step progression (forward & back), 3D companion breathing pacer with eyes-closed
 * haptic cues, procedural ambient soundscapes, soft step-complete bell, Read-to-Me TTS coaching
 * audio, inline memory resurfacing, inline voice/text capture stitched into Today's Journal, and a
 * warm completion ceremony.
 */
@Composable
fun RoutinePlayerScreen(
    routineId: String,
    initialMinEffort: Boolean,
    viewModel: RoutinePlayerViewModel,
    onClose: () -> Unit,
    onOpenVoiceRecorderForJournal: () -> Unit,
    onOpenNote: (String) -> Unit
) {
    LaunchedEffect(routineId, initialMinEffort) {
        viewModel.load(routineId, initialMinEffort)
    }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = MM.colors
    var coachingAudioId by remember { mutableStateOf<String?>(null) }

    val soundscapePlayer = remember { PersonalSoundscapePlayer() }
    val shouldPlaySoundscape = !ui.loading && !ui.isFinished && !ui.isPaused && coachingAudioId == null
    LaunchedEffect(ui.activeSoundscape, shouldPlaySoundscape) {
        soundscapePlayer.setSoundscape(ui.activeSoundscape, playing = shouldPlaySoundscape)
    }
    DisposableEffect(Unit) {
        onDispose { soundscapePlayer.stop() }
    }

    BackHandler { onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .testTag("routine_player_screen")
    ) {
        val routine = ui.routine
        val step = ui.currentStep

        // Top Bar with step progress bar
        Column(Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("routine_player_close")) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close routine", tint = c.ink)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        routine?.title ?: "Routine",
                        style = MM.type.bodyStrong,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!ui.isFinished && ui.steps.isNotEmpty()) {
                        Text(
                            "Step ${ui.stepIndex + 1} of ${ui.steps.size}" + if (ui.minEffortMode) " · Gentle mode" else "",
                            style = MM.type.caption,
                            color = c.inkMuted
                        )
                    }
                }
                if (!ui.isFinished && ui.steps.isNotEmpty()) {
                    TextAction(
                        text = if (ui.minEffortMode) "Full" else "Gentle",
                        onClick = viewModel::toggleMinEffort
                    )
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            if (ui.steps.isNotEmpty() && !ui.isFinished) {
                Row(
                    Modifier.fillMaxWidth().padding(top = MM.space.xs),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ui.steps.forEachIndexed { idx, _ ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(MM.radius.small)
                                .background(if (idx <= ui.stepIndex) c.accent else c.track)
                        )
                    }
                }
            }
        }

        when {
            ui.loading -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Preparing your space…", style = MM.type.secondary, color = c.inkSecondary)
                }
            }
            routine == null -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Routine not found", style = MM.type.heading, color = c.ink)
                        Spacer(Modifier.height(MM.space.m))
                        PrimaryButton("Go back", onClick = onClose)
                    }
                }
            }
            ui.isFinished -> {
                RoutineCompletionView(
                    routine = routine,
                    completedCount = ui.completion?.completedStepIds?.size ?: ui.steps.size,
                    totalCount = ui.steps.size,
                    usedMinEffort = ui.minEffortMode,
                    savedNoteId = ui.savedJournalNoteId,
                    onOpenNote = onOpenNote,
                    onDone = onClose,
                    modifier = Modifier.weight(1f)
                )
            }
            step != null -> {
                RoutineStepStage(
                    routine = routine,
                    step = step,
                    canGoBack = ui.stepIndex > 0,
                    minEffort = ui.minEffortMode,
                    isPaused = ui.isPaused,
                    activeSoundscape = ui.activeSoundscape,
                    memoryForStep = ui.memoryForStep,
                    draft = ui.reflectionDrafts[step.id].orEmpty(),
                    onDraftChange = { viewModel.updateDraft(step.id, it) },
                    onTogglePause = viewModel::togglePause,
                    onCycleSoundscape = viewModel::cycleSoundscape,
                    onTimerFinished = { soundscapePlayer.playGentleBell() },
                    onPlayCoachingAudio = { actId -> coachingAudioId = ReadToMe.coachingId(actId) },
                    onRecordVoice = onOpenVoiceRecorderForJournal,
                    onOpenMemory = onOpenNote,
                    onPrevious = viewModel::goToPreviousStep,
                    onComplete = { viewModel.completeCurrentStep(skipped = false) },
                    onSkip = { viewModel.completeCurrentStep(skipped = true) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    coachingAudioId?.let { id ->
        ReadToMeSheet(noteId = id, onDismiss = { coachingAudioId = null })
    }
}

@Composable
private fun RoutineStepStage(
    routine: PersonalRoutine,
    step: RoutineStep,
    canGoBack: Boolean,
    minEffort: Boolean,
    isPaused: Boolean,
    activeSoundscape: PersonalSoundscape,
    memoryForStep: Note?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onTogglePause: () -> Unit,
    onCycleSoundscape: () -> Unit,
    onTimerFinished: () -> Unit,
    onPlayCoachingAudio: (String) -> Unit,
    onRecordVoice: () -> Unit,
    onOpenMemory: (String) -> Unit,
    onPrevious: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    val totalSec = step.effectiveDurationSeconds(minEffort)
    var remainingSec by remember(step.id, minEffort) { mutableIntStateOf(totalSec) }
    var bellPlayed by remember(step.id, minEffort) { mutableStateOf(false) }

    LaunchedEffect(step.id, minEffort, isPaused) {
        while (!isPaused && remainingSec > 0) {
            delay(1_000L)
            remainingSec = (remainingSec - 1).coerceAtLeast(0)
        }
        if (remainingSec == 0 && !bellPlayed) {
            bellPlayed = true
            onTimerFinished()
        }
    }

    val activityDef = step.guidedActivityId?.let { PersonalGrowthCatalog.activityById(it) }

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.xl, vertical = MM.space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            // Visual centerpiece tailored to step kind
            when (step.kind) {
                RoutineStepKind.BREATHING -> {
                    Breathing3DPacer(isRunning = !isPaused)
                }
                else -> {
                    ZuriSlot(
                        page = CompanionPage.HOME,
                        state = when (step.kind) {
                            RoutineStepKind.VOICE_REFLECTION -> CompanionState.LISTENING
                            RoutineStepKind.COACHING_AUDIO -> CompanionState.READING
                            RoutineStepKind.MEMORY -> CompanionState.NOSTALGIC
                            else -> CompanionState.IDLE
                        },
                        size = 112.dp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(step.kind.label.uppercase(), style = MM.type.overline, color = c.accent)
                Spacer(Modifier.height(MM.space.xs))
                Text(
                    step.effectiveTitle(minEffort),
                    style = MM.type.title,
                    color = c.ink
                )
                if (routine.anchorCue.isNotBlank() && step.position == 0) {
                    Spacer(Modifier.height(MM.space.xs))
                    Text(routine.anchorCue, style = MM.type.caption, color = c.inkMuted)
                }
            }

            // Timer + Ambient Soundscape controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onTogglePause,
                    shape = MM.radius.pill,
                    color = c.surfaceRaised,
                    modifier = Modifier.testTag("routine_timer_pill")
                ) {
                    Row(
                        Modifier.padding(horizontal = MM.space.l, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                    ) {
                        Icon(
                            if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (isPaused) "Resume timer" else "Pause timer",
                            tint = c.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            formatTimer(remainingSec),
                            style = MM.type.heading,
                            color = c.ink
                        )
                    }
                }

                Surface(
                    onClick = onCycleSoundscape,
                    shape = MM.radius.pill,
                    color = if (activeSoundscape == PersonalSoundscape.NONE) c.surfaceRaised else c.accentWash,
                    modifier = Modifier.testTag("routine_soundscape_pill")
                ) {
                    Row(
                        Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                    ) {
                        Icon(
                            if (activeSoundscape == PersonalSoundscape.NONE) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = "Switch ambient soundscape",
                            tint = if (activeSoundscape == PersonalSoundscape.NONE) c.inkMuted else c.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            activeSoundscape.label,
                            style = MM.type.caption,
                            color = if (activeSoundscape == PersonalSoundscape.NONE) c.inkSecondary else c.ink
                        )
                    }
                }
            }

            // Memory Glance card for MEMORY steps
            if (step.kind == RoutineStepKind.MEMORY && memoryForStep != null) {
                MMCard(onClick = { onOpenMemory(memoryForStep.id) }) {
                    Text("A PAST MOMENT TO REVISIT", style = MM.type.overline, color = c.gold)
                    Spacer(Modifier.height(MM.space.xs))
                    Text(memoryForStep.title.ifBlank { "Past journal note" }, style = MM.type.heading, color = c.ink)
                    if (memoryForStep.plainText.isNotBlank()) {
                        Spacer(Modifier.height(MM.space.xs))
                        Text(
                            previewOf(memoryForStep.plainText),
                            style = MM.type.secondary,
                            color = c.inkSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Guided audio card (Read-to-Me TTS for bundled scripts)
            if (activityDef != null) {
                MMCard {
                    Text("GUIDED COACHING", style = MM.type.overline, color = c.accent)
                    Spacer(Modifier.height(MM.space.xs))
                    Text(activityDef.spokenScript, style = MM.type.body, color = c.inkSecondary)
                    Spacer(Modifier.height(MM.space.m))
                    SecondaryButton(
                        text = "Listen with Read-to-Me",
                        onClick = { onPlayCoachingAudio(activityDef.id) },
                        leadingIcon = Icons.Rounded.Headphones
                    )
                }
            }

            // Inline Journal / Intention Capture
            if (step.kind.capturesJournal || step.promptText.isNotBlank()) {
                MMCard {
                    if (step.promptText.isNotBlank()) {
                        Text(step.promptText, style = MM.type.bodyStrong, color = c.ink)
                        Spacer(Modifier.height(MM.space.s))
                    }
                    InsetPanel {
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            textStyle = MM.type.body.copy(color = c.ink),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MMSize.minTouch * 2)
                                .testTag("routine_step_input"),
                            decorationBox = { inner ->
                                Box {
                                    if (draft.isEmpty()) {
                                        Text(
                                            "Write a few honest words (saved to Today's Journal)…",
                                            style = MM.type.body,
                                            color = c.inkMuted
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    if (step.kind == RoutineStepKind.VOICE_REFLECTION) {
                        Spacer(Modifier.height(MM.space.s))
                        SecondaryButton(
                            text = "Or speak with Voice Recorder",
                            onClick = onRecordVoice,
                            leadingIcon = Icons.Rounded.Mic
                        )
                    }
                }
            }
        }

        // Bottom Controls
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MM.space.l, vertical = MM.space.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            if (canGoBack) {
                IconButton(onClick = onPrevious, modifier = Modifier.testTag("routine_prev_step")) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous step", tint = c.inkSecondary)
                }
            }
            TextAction(text = "Skip step", onClick = onSkip, modifier = Modifier.testTag("routine_skip_step"))
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Done · Next",
                onClick = onComplete,
                leadingIcon = Icons.Rounded.SkipNext,
                modifier = Modifier.testTag("routine_complete_step")
            )
        }
    }
}

@Composable
private fun RoutineCompletionView(
    routine: PersonalRoutine,
    completedCount: Int,
    totalCount: Int,
    usedMinEffort: Boolean,
    savedNoteId: String?,
    onOpenNote: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MM.colors
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MM.space.xl, vertical = MM.space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.l)
    ) {
        ZuriSlot(
            page = CompanionPage.HOME,
            state = CompanionState.CELEBRATING,
            size = 132.dp
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.speaker3.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, contentDescription = "Completed", tint = c.speaker3)
        }
        Text("Well kept.", style = MM.type.display, color = c.ink)
        Text(
            buildString {
                append("You finished ${routine.title} ($completedCount of $totalCount steps)")
                if (usedMinEffort) append(" using the gentle version—showing up on low-energy days matters most.")
                else append(".")
            },
            style = MM.type.secondary,
            color = c.inkSecondary
        )

        if (savedNoteId != null) {
            SecondaryButton(
                text = "Open Today's Journal Page",
                onClick = { onOpenNote(savedNoteId) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        PrimaryButton(
            text = "Back to Sanctuary",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().testTag("routine_finish_done")
        )
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
