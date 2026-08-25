package com.example.feature.importing

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioExtractor
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.repository.MeetingRepository
import com.example.core.ui.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ImportMediaState(
    val uri: Uri? = null,
    val fileName: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val isVideo: Boolean = false,
    val isExtracting: Boolean = false,
    val meetingId: String? = null,
    val extractedAudioFile: File? = null,
    val error: String? = null,
    /** Import creates the meeting the instant a file is picked (so extraction can start right
     * away), before there's been any chance to ask what it is — this is filled in afterward,
     * once a real file exists to attach context to. Defaults mirror RecordingContext's own. */
    val recordingType: com.example.core.model.RecordingType = com.example.core.model.RecordingType.GENERAL,
    val customContextText: String = "",
    val speakerCountPreference: Int? = null
)

class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val audioExtractor = AudioExtractor(application)
    private val database = MeetMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)

    private val _importState = MutableStateFlow(ImportMediaState())
    val importState: StateFlow<ImportMediaState> = _importState.asStateFlow()

    fun handleSelectedUri(uri: Uri) {
        viewModelScope.launch {
            _importState.value = _importState.value.copy(isExtracting = true, error = null)
            try {
                val meetingId = UUID.randomUUID().toString()
                val info = audioExtractor.importAndExtract(uri, meetingId)

                val meeting = meetingRepository.createInitialMeeting(
                    id = meetingId,
                    title = info.fileName.substringBeforeLast("."),
                    source = if (info.isVideo) MeetingSource.IMPORTED_VIDEO else MeetingSource.IMPORTED_AUDIO,
                    audioFilePath = info.outputFile.absolutePath
                )

                _importState.value = ImportMediaState(
                    uri = uri,
                    fileName = info.fileName,
                    durationMs = info.durationMs,
                    sizeBytes = info.sizeBytes,
                    isVideo = info.isVideo,
                    isExtracting = false,
                    meetingId = meeting.id,
                    extractedAudioFile = info.outputFile
                )
            } catch (e: Exception) {
                _importState.value = _importState.value.copy(
                    isExtracting = false,
                    error = "Failed to import file: ${e.localizedMessage}"
                )
            }
        }
    }

    fun selectRecordingType(type: com.example.core.model.RecordingType) {
        _importState.value = _importState.value.copy(recordingType = type)
    }

    fun updateCustomContext(text: String) {
        _importState.value = _importState.value.copy(customContextText = text)
    }

    fun selectSpeakerCount(count: Int?) {
        _importState.value = _importState.value.copy(speakerCountPreference = count)
    }

    /** Persists the type/speaker context the user just chose onto the meeting row import already
     * created, then hands off to processing — called right before [onStartProcessing] so the
     * pipeline sees the real context on its very first read, never a stale GENERAL default. */
    suspend fun applyRecordingContext() {
        val meetingId = _importState.value.meetingId ?: return
        val state = _importState.value
        meetingRepository.updateRecordingContext(
            meetingId,
            com.example.core.model.RecordingContext(
                recordingType = state.recordingType,
                speakerCountPreference = state.speakerCountPreference,
                customContext = state.customContextText.trim().ifBlank { null }.takeIf { state.recordingType == com.example.core.model.RecordingType.CUSTOM }
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onNavigateBack: () -> Unit,
    onStartProcessing: (meetingId: String, audioPath: String, durationMs: Long) -> Unit
) {
    val state by viewModel.importState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedUri(it) }
    }

    val formats = listOf("MP3", "WAV", "M4A", "AAC", "MP4 Video", "Screen Recs")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Import Media Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("import_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Upload dropzone — one flat, borderless, tappable surface.
            SectionCard(
                modifier = Modifier
                    .clickable { filePickerLauncher.launch("*/*") }
                    .testTag("import_picker_card")
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Select Audio or Video Recording",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Extracts audio tracks on your local device for offline AI processing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = formats.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("import_browse_btn")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse Device Files", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. Extraction progress
            if (state.isExtracting) {
                SectionCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        Text("Extracting audio stream into 16kHz PCM offline...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 4. Selected media & transcribe CTA
            state.fileName?.let { name ->
                SectionCard {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isVideo) Icons.Default.VideoFile else Icons.Default.AudioFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${Formatters.formatDurationSummary(state.durationMs)} • ${Formatters.formatBytes(state.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // What is this? / Who's speaking? — recording type and expected speaker
                        // count are first-class inputs to the whole processing pipeline (see
                        // RecordingContext); import must never silently default to a generic
                        // recording the way it used to.
                        Text(
                            text = "What is this?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        com.example.core.ui.RecordingTypeGrid(
                            selected = state.recordingType,
                            onSelect = { viewModel.selectRecordingType(it) }
                        )
                        if (state.recordingType == com.example.core.model.RecordingType.CUSTOM) {
                            OutlinedTextField(
                                value = state.customContextText,
                                onValueChange = { viewModel.updateCustomContext(it) },
                                label = { Text("What should MeetingMind focus on?") },
                                placeholder = { Text("e.g. Focus on pricing objections and next steps") },
                                modifier = Modifier.fillMaxWidth().testTag("import_custom_context_field")
                            )
                        }
                        Text(
                            text = "Who's speaking? (optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        com.example.core.ui.SpeakerCountRow(
                            selected = state.speakerCountPreference,
                            onSelect = { viewModel.selectSpeakerCount(it) }
                        )

                        Button(
                            onClick = {
                                val mId = state.meetingId
                                val file = state.extractedAudioFile
                                if (mId != null && file != null) {
                                    coroutineScope.launch {
                                        viewModel.applyRecordingContext()
                                        onStartProcessing(mId, file.absolutePath, state.durationMs)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("import_transcribe_btn")
                        ) {
                            Text(
                                "Transcribe & Process ${if (state.isVideo) "Video" else "Recording"}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
