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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import java.io.FileOutputStream
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
    val error: String? = null
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

    fun createSampleDemoRecording(title: String = "Client Architecture Strategy Session") {
        viewModelScope.launch {
            _importState.value = _importState.value.copy(isExtracting = true, error = null)
            try {
                val meetingId = UUID.randomUUID().toString()
                val meetingDir = File(getApplication<Application>().filesDir, "meetings/$meetingId").apply { mkdirs() }
                val sampleFile = File(meetingDir, "audio.m4a")

                FileOutputStream(sampleFile).use { out ->
                    out.write(ByteArray(1024 * 128))
                }

                val meeting = meetingRepository.createInitialMeeting(
                    id = meetingId,
                    title = title,
                    source = MeetingSource.IMPORTED_AUDIO,
                    audioFilePath = sampleFile.absolutePath
                )

                _importState.value = ImportMediaState(
                    uri = Uri.fromFile(sampleFile),
                    fileName = "$title.m4a",
                    durationMs = 180000L,
                    sizeBytes = sampleFile.length(),
                    isVideo = false,
                    isExtracting = false,
                    meetingId = meeting.id,
                    extractedAudioFile = sampleFile
                )
            } catch (e: Exception) {
                _importState.value = _importState.value.copy(
                    isExtracting = false,
                    error = "Failed to generate sample recording"
                )
            }
        }
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

            // 2. Quick demo tile
            SectionCard {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "Sample Architecture Sync",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "3-minute multi-speaker sample audio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.createSampleDemoRecording() },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("import_sample_demo_btn")
                    ) {
                        Text("Load Sample", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 3. Extraction progress
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

                        Button(
                            onClick = {
                                val mId = state.meetingId
                                val file = state.extractedAudioFile
                                if (mId != null && file != null) {
                                    onStartProcessing(mId, file.absolutePath, state.durationMs)
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
