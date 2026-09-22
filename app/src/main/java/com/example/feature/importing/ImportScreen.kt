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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.LineFaint
import com.example.ui.theme.LineSoft

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
    var showAllTypes by remember { mutableStateOf(false) }
    var showSpeakerPicker by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedUri(it) }
    }

    Scaffold(containerColor = Color.White) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // #5c header: a back chevron and a plain title. No app bar, no elevation.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "‹",
                    fontSize = 20.sp,
                    color = InkSecondary,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onNavigateBack)
                        .testTag("import_back_btn")
                        .wrapContentSize(Alignment.Center)
                )
                Text("Import audio", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }

            val fileName = state.fileName
            if (fileName == null) {
                // Nothing picked yet. The frame shows the screen with a file already in hand, so
                // this is the one state it does not depict: keep it to the same visual language —
                // one bordered card, the same bar-meter glyph, one action — rather than inventing
                // a differently-styled dropzone.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 22.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, LineSoft, RoundedCornerShape(20.dp))
                        .clickable { filePickerLauncher.launch("*/*") }
                        .testTag("import_picker_card")
                        .padding(horizontal = 18.dp, vertical = 17.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ImportBarGlyph()
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Choose a file", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(
                                "Audio or video, from Files or any app",
                                fontSize = 12.5.sp,
                                color = InkMuted,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Text("Browse", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = InkSecondary)
                    }
                }
            } else {
                // The picked file, as #5c shows it: glyph, name, the real facts about it, Change.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 22.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, LineSoft, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 17.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ImportBarGlyph()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                fileName,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = listOfNotNull(
                                    Formatters.formatDurationSummary(state.durationMs).takeIf { state.durationMs > 0 },
                                    Formatters.formatBytes(state.sizeBytes).takeIf { state.sizeBytes > 0 },
                                    if (state.isVideo) "video" else null
                                ).joinToString(" · "),
                                fontSize = 12.5.sp,
                                color = InkMuted,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Text(
                            "Change",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkSecondary,
                            modifier = Modifier
                                .clickable { filePickerLauncher.launch("*/*") }
                                .testTag("import_change_btn")
                        )
                    }
                }
            }

            if (state.isExtracting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                    Text("Extracting the audio track…", fontSize = 13.sp, color = InkSecondary)
                }
            }

            if (state.fileName != null) {
                SectionLabel("What is it")
                // The four the frame shows, as chips. Every other type stays reachable through
                // "More types", so importing a Sermon or a Journal is not made impossible by a
                // frame that happened to depict four.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickTypes = listOf(
                        com.example.core.model.RecordingType.MEETING,
                        com.example.core.model.RecordingType.INTERVIEW,
                        com.example.core.model.RecordingType.LECTURE,
                        com.example.core.model.RecordingType.VOICE_MEMO
                    )
                    quickTypes.forEach { type ->
                        ImportTypeChip(
                            label = type.displayName,
                            selected = state.recordingType == type,
                            onClick = { viewModel.selectRecordingType(type) }
                        )
                    }
                    ImportTypeChip(
                        label = if (state.recordingType in quickTypes) "More types" else state.recordingType.displayName,
                        selected = state.recordingType !in quickTypes,
                        onClick = { showAllTypes = !showAllTypes }
                    )
                }

                if (showAllTypes) {
                    Box(modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                        com.example.core.ui.RecordingTypeGrid(
                            selected = state.recordingType,
                            onSelect = {
                                viewModel.selectRecordingType(it)
                                showAllTypes = false
                            }
                        )
                    }
                }

                if (state.recordingType == com.example.core.model.RecordingType.CUSTOM) {
                    OutlinedTextField(
                        value = state.customContextText,
                        onValueChange = { viewModel.updateCustomContext(it) },
                        label = { Text("What should MeetingMind focus on?") },
                        placeholder = { Text("e.g. Focus on pricing objections and next steps") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                            .testTag("import_custom_context_field")
                    )
                }

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 26.dp)) {
                    ImportValueRow(
                        title = "Speakers expected",
                        subtitle = "Helps split the transcript",
                        value = state.speakerCountPreference?.toString() ?: "Not sure",
                        monospaceValue = state.speakerCountPreference != null,
                        onClick = { showSpeakerPicker = !showSpeakerPicker }
                    )
                    if (showSpeakerPicker) {
                        Box(modifier = Modifier.padding(bottom = 10.dp)) {
                            com.example.core.ui.SpeakerCountRow(
                                selected = state.speakerCountPreference,
                                onSelect = {
                                    viewModel.selectSpeakerCount(it)
                                    showSpeakerPicker = false
                                }
                            )
                        }
                    }
                    ImportValueRow(
                        title = "Language",
                        // Said honestly: the app transcribes English today, and the frame's
                        // "detected from the first minute" would be a claim about behaviour that
                        // does not exist.
                        subtitle = "English — the only language the installed models handle",
                        value = "English",
                        onClick = {}
                    )
                }

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
                    enabled = state.meetingId != null && state.extractedAudioFile != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 26.dp, bottom = 28.dp)
                        .height(50.dp)
                        .testTag("import_transcribe_btn")
                ) {
                    Text("Start processing", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = InkMuted,
        modifier = Modifier.padding(horizontal = 22.dp).padding(top = 26.dp)
    )
}

/** The bar-meter glyph #5c uses in place of a file-type icon. */
@Composable
private fun ImportBarGlyph() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(34.dp)
    ) {
        listOf(12, 26, 19, 34, 15, 24).forEach { barHeight ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(InkFaint)
            )
        }
    }
}

@Composable
private fun ImportTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Ink else Color.White,
        border = if (selected) null else BorderStroke(1.dp, Line),
        modifier = Modifier.testTag("import_type_${label.lowercase().replace(' ', '_')}")
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Color.White else InkSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun ImportValueRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    monospaceValue: Boolean = false
) {
    Column {
        HorizontalDivider(color = LineFaint)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
                Text(title, fontSize = 15.5.sp, color = Ink)
                Text(subtitle, fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                text = value,
                fontSize = 12.5.sp,
                color = Ink,
                fontFamily = if (monospaceValue) FontFamily.Monospace else null
            )
        }
    }
}
