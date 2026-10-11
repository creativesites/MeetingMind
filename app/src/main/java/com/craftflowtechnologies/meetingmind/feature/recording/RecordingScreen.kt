package com.craftflowtechnologies.meetingmind.feature.recording

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.audio.MeetingRecordingService
import com.craftflowtechnologies.meetingmind.core.audio.RecordingCapacity
import com.craftflowtechnologies.meetingmind.core.audio.RecordingState
import com.craftflowtechnologies.meetingmind.core.common.DeviceCapabilityDetector
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Binds to [MeetingRecordingService] rather than owning an `AudioRecorder` directly (Phase 15
 * §Part 2 / design capture-pipeline spec §3.2) — this ViewModel only ever sends intents to the
 * service and relays its [StateFlow]s back to the UI. [android.content.Context.bindService] is
 * asynchronous, so [state]/[amplitude]/[durationMs]/[focusInterrupted] are built with
 * [flatMapLatest] over the (possibly still-null) bound-service reference: they read as [IDLE]
 * before binding resolves and automatically switch to the real service flows the moment it does,
 * with no separate "waiting to bind" state the UI needs to know about.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val _boundService = MutableStateFlow<MeetingRecordingService?>(null)
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            _boundService.value = (binder as? MeetingRecordingService.LocalBinder)?.service
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            _boundService.value = null
        }
    }

    val state: StateFlow<RecordingState> = _boundService
        .flatMapLatest { it?.state ?: flowOf(RecordingState.IDLE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingState.IDLE)
    val amplitude: StateFlow<Float> = _boundService
        .flatMapLatest { it?.amplitude ?: flowOf(0f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val durationMs: StateFlow<Long> = _boundService
        .flatMapLatest { it?.durationMs ?: flowOf(0L) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val focusInterrupted: StateFlow<Boolean> = _boundService
        .flatMapLatest { it?.focusInterrupted ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val capacityWarning: StateFlow<String?> = _boundService
        .flatMapLatest { it?.capacityWarning ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Taps during recording: key moments, actions, questions (docs/PLAN_PROFESSIONAL.md §4.2). */
    // Kept in one place (RecordingMarks) so a tap on the notification or the lock screen shows here too.
    val marks: StateFlow<List<com.craftflowtechnologies.meetingmind.core.work.Mark>> = com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.marks

    fun mark(kind: com.craftflowtechnologies.meetingmind.core.work.MarkKind, text: String? = null) {
        com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.add(kind, durationMs.value, text)
    }

    /** Takes back the last marker (a mis-tap). */
    fun undoMark() {
        com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.undoLast()
    }

    val workSettings: StateFlow<com.craftflowtechnologies.meetingmind.core.work.WorkSettings> = com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(application).workSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.craftflowtechnologies.meetingmind.core.work.WorkSettings())

    private var currentMeetingId: String = UUID.randomUUID().toString()
    private var meetingTitle: String = "In-Person Discussion"
    private var recordingContext: com.craftflowtechnologies.meetingmind.core.model.RecordingContext = com.craftflowtechnologies.meetingmind.core.model.RecordingContext()

    private fun ensureBound() {
        if (isBound) return
        isBound = true
        getApplication<Application>().bindService(
            MeetingRecordingService.bindIntent(getApplication()),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun startRecording(title: String, context: com.craftflowtechnologies.meetingmind.core.model.RecordingContext) {
        meetingTitle = title
        this.recordingContext = context
        currentMeetingId = UUID.randomUUID().toString()
        com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.clear()
        ensureBound()
        viewModelScope.launch {
            val service = _boundService.filterNotNull().first()
            try {
                service.startRecording(currentMeetingId, title, context)
            } catch (e: Exception) {
                // service.state already reflects RecordingState.FAILED — the UI reads that
                // directly rather than this call site needing its own error channel.
            }
        }
    }

    fun pauseRecording() {
        _boundService.value?.pauseRecording()
    }

    fun resumeRecording() {
        _boundService.value?.resumeRecording()
    }

    fun discardRecording() {
        _boundService.value?.discardRecording()
    }

    fun finishRecording(onComplete: (meetingId: String, audioPath: String, durationMs: Long) -> Unit) {
        val service = _boundService.value ?: return
        service.stopRecording { meetingId, file, duration ->
            val marks = com.craftflowtechnologies.meetingmind.core.work.RecordingMarks.take()
            viewModelScope.launch {
                // The recording exists now, so its marks can be kept on its note.
                if (file != null) runCatching { com.craftflowtechnologies.meetingmind.core.work.Marks.save(com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase.getInstance(getApplication()), meetingId, marks) }
                if (file != null) onComplete(meetingId, file.absolutePath, duration)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(connection)
            isBound = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    onNavigateBack: () -> Unit,
    onRecordingComplete: (meetingId: String, audioPath: String, durationMs: Long) -> Unit,
    /** Set when recording from inside a note, so the recording joins that note. */
    targetNoteId: String? = null,
    /** Pre-selects the type (Faith → Record sermon); the picker still shows so it can be changed. */
    initialType: RecordingType? = null,
    /** A title that came with the request (a calendar event's); kept when the type changes. */
    initialTitle: String? = null,
    /** Speaker count from the event's guest list; the person can still change it. */
    initialSpeakers: Int? = null,
    /** Shown under Quick record while offline setup isn't finished (the setup banner). */
    setupNotice: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Home's Quick Record FAB and the main Record entry both land here and both go through this
    // same picker — "quick" only ever meant "fewer taps to reach the button", never "skip telling
    // MeetingMind what this recording is." Recording type and speaker count are first-class inputs
    // to the whole processing pipeline (see RecordingContext); silently defaulting them was exactly
    // the "treats every recording like a meeting" problem this phase exists to fix.
    var typeChosen by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(initialType ?: RecordingType.MEETING) }
    var customContextText by remember { mutableStateOf("") }
    var meetingTitle by remember { mutableStateOf(initialTitle ?: (initialType ?: RecordingType.MEETING).displayName) }
    // Null = unspecified/"Not sure". Tracks whether the user has touched this control themselves
    // so a type-based suggestion never silently overwrites a choice they already made.
    var selectedSpeakerCount by remember { mutableStateOf(initialSpeakers ?: (initialType ?: RecordingType.MEETING).suggestedSpeakerCount()) }
    var speakerCountTouched by remember { mutableStateOf(initialSpeakers != null) }

    fun recordingContext() = com.craftflowtechnologies.meetingmind.core.model.RecordingContext(
        recordingType = selectedType,
        speakerCountPreference = selectedSpeakerCount,
        customContext = customContextText.trim().ifBlank { null }.takeIf { selectedType == RecordingType.CUSTOM },
        noteId = targetNoteId
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.startRecording(meetingTitle, recordingContext())
        }
    }

    val state by viewModel.state.collectAsState()
    val amplitudeState = viewModel.amplitude.collectAsState()
    val amplitude = amplitudeState.value
    val durationMs by viewModel.durationMs.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }

    LaunchedEffect(hasAudioPermission, typeChosen) {
        if (typeChosen && hasAudioPermission && !hasStarted) {
            hasStarted = true
            viewModel.startRecording(meetingTitle, recordingContext())
        }
    }

    if (!typeChosen) {
        // Pre-flight storage check (design spec §3.8) — computed once per screen entry rather
        // than polled, since nothing changes while the user is just picking a recording type.
        val availableStorageMb = remember { DeviceCapabilityDetector.getAvailableStorageMb() }
        val storageLine = remember(availableStorageMb) { RecordingCapacity.formatStorageLine(availableStorageMb) }
        val refuseToStart = remember(availableStorageMb) { RecordingCapacity.shouldRefuseToStart(availableStorageMb) }
        RecordingTypePickerScreen(
            selected = selectedType,
            customContext = customContextText,
            selectedSpeakerCount = selectedSpeakerCount,
            storageLine = storageLine,
            refuseToStart = refuseToStart,
            onSelect = {
                selectedType = it
                if (initialTitle == null) meetingTitle = it.displayName
                if (!speakerCountTouched) selectedSpeakerCount = it.suggestedSpeakerCount()
            },
            onCustomContextChange = { customContextText = it },
            onSelectSpeakerCount = {
                speakerCountTouched = true
                selectedSpeakerCount = it
            },
            onStart = { typeChosen = true },
            onQuickRecord = {
                selectedType = RecordingType.GENERAL
                meetingTitle = "Quick Recording"
                if (!speakerCountTouched) selectedSpeakerCount = RecordingType.GENERAL.suggestedSpeakerCount()
                typeChosen = true
            },
            onCancel = onNavigateBack,
            setupNotice = setupNotice
        )
        return
    }

    val capacityWarning by viewModel.capacityWarning.collectAsState()
    val marks by viewModel.marks.collectAsState()
    val workSettings by viewModel.workSettings.collectAsState()
    val isWork = com.craftflowtechnologies.meetingmind.core.model.Workflows.space(selectedType) == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK
    LiveRecordingSurface(
        type = selectedType,
        title = meetingTitle,
        hasPermission = hasAudioPermission,
        state = state,
        amplitude = amplitude,
        companionLevel = { amplitudeState.value },
        durationMs = durationMs,
        capacityWarning = capacityWarning,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onDiscard = { showDiscardDialog = true },
        onToggle = {
            if (state == RecordingState.RECORDING) viewModel.pauseRecording()
            else if (state == RecordingState.PAUSED) viewModel.resumeRecording()
        },
        onFinish = { viewModel.finishRecording { meetingId, path, dur -> onRecordingComplete(meetingId, path, dur) } },
        marks = marks,
        onAction = { action, text -> viewModel.mark(action.kind, text) },
        onUndoMark = viewModel::undoMark,
        consentReminder = isWork && workSettings.consentReminder && selectedSpeakerCount != 1
    )

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
                    colors = ButtonDefaults.buttonColors(containerColor = MM.colors.danger, contentColor = MM.colors.onInk),
                    modifier = Modifier.testTag("confirm_discard_btn")
                ) {
                    Text("Discard")
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
 * "What are you recording?" - shown before the mic starts. MeetingMind is a general voice-capture
 * tool, not a meeting-only recorder, so this never forces a choice: Quick Record skips straight to
 * [RecordingType.GENERAL]. The choice only ever adds focus guidance to the AI extraction prompt
 * later (see [RecordingType.focusGuidance]) and picks which buttons the live screen shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingTypePickerScreen(
    selected: RecordingType,
    customContext: String,
    selectedSpeakerCount: Int?,
    storageLine: String,
    refuseToStart: Boolean,
    onSelect: (RecordingType) -> Unit,
    onCustomContextChange: (String) -> Unit,
    onSelectSpeakerCount: (Int?) -> Unit,
    onStart: () -> Unit,
    onQuickRecord: () -> Unit,
    onCancel: () -> Unit,
    setupNotice: @Composable () -> Unit = {}
) {
    val c = MM.colors
    Scaffold(
        containerColor = c.background,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(horizontal = MM.space.l, vertical = MM.space.m),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Text(
                    text = if (refuseToStart) "Not enough storage to start a recording ($storageLine)." else storageLine,
                    style = MM.type.caption,
                    color = if (refuseToStart) c.danger else c.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("record_storage_line")
                )
                PrimaryButton(
                    text = "Record ${selected.displayName.lowercase()}", onClick = onStart, enabled = !refuseToStart,
                    leadingIcon = Icons.Rounded.FiberManualRecord,
                    modifier = Modifier.fillMaxWidth().testTag("record_type_start_btn")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MM.space.l)
        ) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = MM.space.s, end = MM.space.l), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.size(MMSize.minTouch)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = c.inkSecondary)
                }
                Text("New recording", style = MM.type.title, color = c.ink)
            }
            // The fastest path: one tap, no questions. The screen's one hero.
            HeroCard(
                onClick = if (refuseToStart) null else onQuickRecord,
                modifier = Modifier.padding(horizontal = MM.space.l).testTag("record_type_quick_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Quick record", style = MM.type.heading, color = c.ink)
                        Text("Start now, sort it out later", style = MM.type.secondary, color = c.inkSecondary)
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(MMSize.minTouch).clip(CircleShape).background(c.accent),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Mic, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(MMSize.icon)) }
                }
            }
            androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = MM.space.l)) { setupNotice() }
            Text(
                "Or tell MeetingMind what it is. It listens for the right things.",
                style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(horizontal = MM.space.l)
            )
            androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = MM.space.l)) { com.craftflowtechnologies.meetingmind.core.ui.RecordingTypeGrid(selected = selected, onSelect = onSelect) }
            if (selected == RecordingType.CUSTOM) {
                OutlinedTextField(
                    value = customContext, onValueChange = onCustomContextChange,
                    label = { Text("What should MeetingMind focus on?") },
                    placeholder = { Text("e.g. Focus on pricing objections and next steps") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MM.space.l).testTag("custom_context_field")
                )
            }
            Column(Modifier.padding(horizontal = MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text("Who's speaking?", style = MM.type.heading, color = c.ink)
                Text("Optional. It helps tell voices apart.", style = MM.type.caption, color = c.inkMuted)
                com.craftflowtechnologies.meetingmind.core.ui.SpeakerCountRow(selected = selectedSpeakerCount, onSelect = onSelectSpeakerCount)
            }
            Spacer(Modifier.height(MM.space.s))
        }
    }
}
