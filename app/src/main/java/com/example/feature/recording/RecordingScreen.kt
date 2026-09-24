package com.example.feature.recording

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.statusBarsPadding
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
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.MeetingRecordingService
import com.example.core.audio.RecordingCapacity
import com.example.core.audio.RecordingState
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.common.Formatters
import com.example.core.model.RecordingType
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SuccessGreen
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

    private var currentMeetingId: String = UUID.randomUUID().toString()
    private var meetingTitle: String = "In-Person Discussion"
    private var recordingContext: com.example.core.model.RecordingContext = com.example.core.model.RecordingContext()

    private fun ensureBound() {
        if (isBound) return
        isBound = true
        getApplication<Application>().bindService(
            MeetingRecordingService.bindIntent(getApplication()),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun startRecording(title: String, context: com.example.core.model.RecordingContext) {
        meetingTitle = title
        this.recordingContext = context
        currentMeetingId = UUID.randomUUID().toString()
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
            if (file != null) onComplete(meetingId, file.absolutePath, duration)
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
    initialSpeakers: Int? = null
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

    fun recordingContext() = com.example.core.model.RecordingContext(
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
    val amplitude by viewModel.amplitude.collectAsState()
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
            onCancel = onNavigateBack
        )
        return
    }

    val capacityWarning by viewModel.capacityWarning.collectAsState()
    LiveRecordingSurface(
        type = selectedType,
        title = meetingTitle,
        hasPermission = hasAudioPermission,
        state = state,
        amplitude = amplitude,
        durationMs = durationMs,
        capacityWarning = capacityWarning,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onDiscard = { showDiscardDialog = true },
        onToggle = {
            if (state == RecordingState.RECORDING) viewModel.pauseRecording()
            else if (state == RecordingState.PAUSED) viewModel.resumeRecording()
        },
        onFinish = { viewModel.finishRecording { meetingId, path, dur -> onRecordingComplete(meetingId, path, dur) } }
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
    selectedSpeakerCount: Int?,
    storageLine: String,
    refuseToStart: Boolean,
    onSelect: (RecordingType) -> Unit,
    onCustomContextChange: (String) -> Unit,
    onSelectSpeakerCount: (Int?) -> Unit,
    onStart: () -> Unit,
    onQuickRecord: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    text = if (refuseToStart) "Not enough storage to start a recording ($storageLine)." else storageLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (refuseToStart) MaterialTheme.colorScheme.error else Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("record_storage_line")
                )
                Button(
                    onClick = onStart, enabled = !refuseToStart, shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("record_type_start_btn")
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = RecordingRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Record ${selected.displayName.lowercase()}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                Text("New recording", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            }
            // The fastest path: one tap, no questions.
            Surface(
                onClick = onQuickRecord, enabled = !refuseToStart, shape = RoundedCornerShape(26.dp), color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(120.dp).testTag("record_type_quick_btn")
            ) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4338CA), Color(0xFFE11D48))))) {
                    Box(Modifier.align(Alignment.CenterEnd).size(160.dp).padding(end = 0.dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)), CircleShape))
                    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Quick record", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Start now — sort it out later", fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 3.dp))
                        }
                        Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = RecordingRed, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            Text(
                "Or tell MeetingMind what it is — it listens for the right things.",
                fontSize = 14.sp, color = Color(0xFF475569), modifier = Modifier.padding(horizontal = 20.dp)
            )
            Box(Modifier.padding(horizontal = 20.dp)) { com.example.core.ui.RecordingTypeGrid(selected = selected, onSelect = onSelect) }
            if (selected == RecordingType.CUSTOM) {
                OutlinedTextField(
                    value = customContext, onValueChange = onCustomContextChange,
                    label = { Text("What should MeetingMind focus on?") },
                    placeholder = { Text("e.g. Focus on pricing objections and next steps") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("custom_context_field")
                )
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Who's speaking?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Text("Optional — it helps tell voices apart.", fontSize = 12.5.sp, color = Color(0xFF94A3B8))
                com.example.core.ui.SpeakerCountRow(selected = selectedSpeakerCount, onSelect = onSelectSpeakerCount)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Colours for the live screen, by what's being recorded. */
private fun liveColors(type: RecordingType): List<Color> = when (type) {
    in com.example.core.model.Workflows.faith -> listOf(Color(0xFF120E1F), Color(0xFF2B2140), Color(0xFF3A2A1A))
    RecordingType.LECTURE, RecordingType.RESEARCH -> listOf(Color(0xFF071A1F), Color(0xFF0E3B44), Color(0xFF0F5C5A))
    else -> listOf(Color(0xFF0B1024), Color(0xFF1E1B4B), Color(0xFF312E81))
}

/**
 * The live recording: a dark stage tinted by the kind of recording, a large timer, the sound of
 * the last seconds flowing past as a waveform, and three big controls within thumb's reach.
 */
@Composable
private fun LiveRecordingSurface(
    type: RecordingType,
    title: String,
    hasPermission: Boolean,
    state: RecordingState,
    amplitude: Float,
    durationMs: Long,
    capacityWarning: String?,
    onRequestPermission: () -> Unit,
    onDiscard: () -> Unit,
    onToggle: () -> Unit,
    onFinish: () -> Unit
) {
    val recording = state == RecordingState.RECORDING
    val accent = if (type in com.example.core.model.Workflows.faith) Color(0xFFF6D365) else Color(0xFFFB7185)
    // The last few seconds of level, sampled steadily so the wave flows at a constant pace.
    val history = remember { mutableStateListOf<Float>().apply { repeat(64) { add(0f) } } }
    val latest = androidx.compose.runtime.rememberUpdatedState(amplitude)
    LaunchedEffect(recording) {
        while (recording) {
            history.removeAt(0); history.add(latest.value.coerceIn(0f, 1f))
            kotlinx.coroutines.delay(70)
        }
    }
    val halo = androidx.compose.animation.core.rememberInfiniteTransition(label = "halo")
    val pulse by halo.animateFloat(1f, 1.18f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
    val level by androidx.compose.animation.core.animateFloatAsState(if (recording) amplitude.coerceIn(0f, 1f) else 0f, androidx.compose.animation.core.tween(120), label = "level")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(liveColors(type)))) {
        Box(Modifier.align(Alignment.TopEnd).size(320.dp).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent)), CircleShape))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDiscard, modifier = Modifier.testTag("record_close_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close & Discard", tint = Color.White)
                }
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.1f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                        Text("  ${type.displayName}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (hasPermission) Surface(shape = RoundedCornerShape(50), color = if (recording) RecordingRed.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(end = 12.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (recording) RecordingRed.copy(alpha = if (pulse > 1.09f) 1f else 0.45f) else Color.White.copy(alpha = 0.5f)))
                        Text(if (recording) "  REC" else "  PAUSED", fontSize = 11.sp, letterSpacing = 1.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!hasPermission) {
                Spacer(Modifier.weight(1f))
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = accent, modifier = Modifier.size(38.dp))
                    }
                    Text("MeetingMind needs your microphone", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
                    Text("Recordings are saved on your phone. Nothing is sent anywhere unless you turn on Internet mode.", fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = onRequestPermission, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White), modifier = Modifier.padding(top = 22.dp).height(50.dp)) {
                        Text("Allow microphone & start", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1.3f))
                return@Column
            }

            Spacer(Modifier.weight(0.7f))
            Text(title, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 32.dp))
            Text(
                Formatters.formatDurationHms(durationMs), fontSize = 64.sp, fontWeight = FontWeight.Light, color = Color.White,
                fontFamily = FontFamily.Monospace, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 6.dp)
            )
            Text(if (recording) "Listening — recording safely on your phone" else "Paused — tap to carry on", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            capacityWarning?.let {
                Text(it, fontSize = 12.sp, color = Color(0xFFFCA5A5), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp).testTag("record_capacity_warning"))
            }
            Spacer(Modifier.weight(0.4f))

            // The last seconds of sound, flowing right to left.
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp)) {
                val n = history.size
                val step = size.width / n
                val bar = step * 0.55f
                history.forEachIndexed { i, v ->
                    val h = (8f + v * size.height * 0.95f).coerceAtMost(size.height)
                    val fade = 0.25f + 0.75f * (i.toFloat() / n)
                    drawRoundRect(
                        color = (if (recording) accent else Color.White).copy(alpha = fade * (if (recording) 0.9f else 0.35f)),
                        topLeft = Offset(i * step, (size.height - h) / 2f), size = Size(bar, h), cornerRadius = CornerRadius(bar / 2f, bar / 2f)
                    )
                }
            }
            Spacer(Modifier.weight(0.6f))

            Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 28.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LiveControl("Discard", Icons.Default.Close, Color.White.copy(alpha = 0.12f), Color.White, Modifier.testTag("record_discard_btn"), onDiscard)
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(128.dp).graphicsLayer { val s = if (recording) (pulse + level * 0.25f) else 1f; scaleX = s; scaleY = s }
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = if (recording) 0.35f else 0.12f), Color.Transparent)), CircleShape))
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(88.dp).clip(CircleShape).background(if (recording) RecordingRed else Color.White).testTag("record_pause_resume_btn")
                    ) {
                        Icon(if (recording) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (recording) "Pause recording" else "Resume recording",
                            tint = if (recording) Color.White else Color(0xFF0F172A), modifier = Modifier.size(38.dp))
                    }
                }
                LiveControl("Finish", Icons.Default.Done, SuccessGreen, Color.White, Modifier.testTag("record_finish_btn"), onFinish)
            }
        }
    }
}

@Composable
private fun LiveControl(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = modifier.size(60.dp).clip(CircleShape).background(bg)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 6.dp))
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
