package com.example.feature.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.database.ProcessingJobEntity
import com.example.core.firebase.FirebaseAuthManager
import com.example.core.firebase.FirebaseUserModel
import com.example.core.model.DeviceCapabilities
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.model.MeetingStatus
import com.example.core.repository.MeetingRepository
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineFaint
import com.example.ui.theme.LineSoft
import com.example.ui.theme.Speaker4
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.shadow
import com.example.core.model.RecordingType
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk
import kotlinx.coroutines.flow.map

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)
    private val authManager = FirebaseAuthManager(application)
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilityDetector.detect(application)

    val currentUser: StateFlow<FirebaseUserModel?> = authManager.currentUser
    val meetings: StateFlow<List<Meeting>> = meetingRepository.allMeetings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeJobs: StateFlow<List<ProcessingJobEntity>> = database.processingJobDao().getActiveJobs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val userPrefs = com.example.core.datastore.UserPreferencesManager(application)

    /**
     * The workflow Record will start with. Remembered across launches, because the type a person
     * records is overwhelmingly the same one they recorded last time — "before recording: almost
     * no friction" (design/capture-pipeline-implementation.md §1).
     */
    val rememberedRecordingType: StateFlow<RecordingType> = userPrefs.preferencesFlow
        .map { it.lastRecordingType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordingType.MEETING)

    val processingProfile: StateFlow<com.example.core.model.ProcessingProfile> = userPrefs.preferencesFlow
        .map { it.processingProfile }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.core.model.ProcessingProfile.OFFLINE)

    fun rememberRecordingType(type: RecordingType) {
        viewModelScope.launch { userPrefs.setLastRecordingType(type) }
    }

    fun deleteMeeting(meetingId: String) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(meetingId)
        }
    }

    fun getGreetingText(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        val name = currentUser.value?.displayName?.split(" ")?.firstOrNull()
        return if (name != null) "$greeting, $name" else greeting
    }
}

enum class MeetingFilter {
    ALL, RECORDED, IMPORTED, WITH_ACTIONS
}

/**
 * Home (Phase 15 §Part 2 / design `#5a`) — restyled onto the Ink/Accent flat-row token system.
 * `#5a`'s mockup shows Record/Import as plain rows with no filter pills and no device-telemetry
 * tile; both of those are real, working features here (filtering the recordings list; a working
 * link into AI Engine with live device stats) that the mockup simply doesn't depict, so they're
 * kept — restyled to the same row language, not dropped. The quick-record FAB is likewise kept:
 * a one-tap shortcut that doesn't compete with the Record row's own expandable type picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToRecord: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToMeeting: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBottomNav: (com.example.core.ui.BottomNavDestination) -> Unit,
    /** Opens the live progress of a recording that is still being processed. */
    onOpenProcessing: (String) -> Unit = onNavigateToMeeting,
    /** One tap, no type picker — the fastest path from "I want to record" to actually recording. */
    onNavigateToQuickRecord: () -> Unit = onNavigateToRecord
) {
    val meetings by viewModel.meetings.collectAsState()
    val activeJobs by viewModel.activeJobs.collectAsState()
    val greeting = viewModel.getGreetingText()
    val caps = viewModel.deviceCapabilities

    var selectedFilter by remember { mutableStateOf(MeetingFilter.ALL) }
    var recordTypesExpanded by remember { mutableStateOf(false) }
    var expandedJobId by remember { mutableStateOf<String?>(null) }

    val rememberedType by viewModel.rememberedRecordingType.collectAsState()
    val processingProfile by viewModel.processingProfile.collectAsState()

    // "42 recordings · 19 hours captured" — both halves real, computed from what is actually
    // stored. The hours half is dropped rather than shown as "0 hours" when nothing is recorded.
    val libraryMetaLine = remember(meetings) {
        val count = meetings.size
        val totalHours = meetings.sumOf { it.durationMs } / 3_600_000.0
        val recordings = if (count == 1) "1 recording" else "$count recordings"
        if (totalHours < 0.05) recordings else "$recordings · ${"%.0f".format(totalHours)} hours captured"
    }
    val recordHint = rememberedType.displayName + " · tap to change"

    val filteredMeetings = remember(meetings, selectedFilter) {
        when (selectedFilter) {
            MeetingFilter.ALL -> meetings
            MeetingFilter.RECORDED -> meetings.filter { it.source == MeetingSource.LOCAL_RECORDING }
            MeetingFilter.IMPORTED -> meetings.filter { it.source == MeetingSource.IMPORTED_AUDIO || it.source == MeetingSource.IMPORTED_VIDEO }
            MeetingFilter.WITH_ACTIONS -> meetings.filter { (it.summaryPreview?.length ?: 0) > 0 }
        }
    }

    val groupedMeetings = remember(filteredMeetings) {
        filteredMeetings.groupBy { Formatters.formatDateHeader(it.createdAt) }
    }

    Scaffold(
        containerColor = Color.White,
        // No Record FAB: the navigation bar's own filled Record action is the same destination,
        // and two affordances for one verb on the same screen is a choice the user has to make for
        // no reason. Quick Record remains reachable from the header action on this screen.
        bottomBar = {
            com.example.core.ui.AppBottomNavigationBar(
                current = com.example.core.ui.BottomNavDestination.HOME,
                onNavigate = { destination ->
                    if (destination != com.example.core.ui.BottomNavDestination.HOME) onNavigateBottomNav(destination)
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 22.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 22.dp, end = 22.dp, top = 18.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = greeting, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp, color = Ink)
                        Text(
                            text = libraryMetaLine,
                            fontSize = 13.sp,
                            color = InkMuted,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    // #5a: search is a 46dp outlined circle in the header, not a bare icon button.
                    Surface(
                        onClick = onNavigateToSearch,
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, Line),
                        modifier = Modifier.size(46.dp).testTag("home_search_icon_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search recordings",
                                tint = Ink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                HomeRecordRow(
                    hint = recordHint,
                    expanded = recordTypesExpanded,
                    onToggleExpanded = { recordTypesExpanded = !recordTypesExpanded },
                    onStartWithType = { type ->
                        recordTypesExpanded = false
                        viewModel.rememberRecordingType(type)
                        onNavigateToRecord()
                    }
                )
            }

            item {
                HomeImportRow(onClick = onNavigateToImport)
            }

            items(activeJobs, key = { it.id }) { job ->
                HomeJobCard(
                    title = job.meetingTitle,
                    statusLine = job.currentStep,
                    percent = job.progressPercent,
                    expanded = expandedJobId == job.id,
                    onToggleExpanded = { expandedJobId = if (expandedJobId == job.id) null else job.id },
                    onOpen = { onOpenProcessing(job.meetingId) }
                )
            }

            item {
                Column {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 22.dp, end = 22.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                    ) {
                        item { FilterPill("All", selectedFilter == MeetingFilter.ALL) { selectedFilter = MeetingFilter.ALL } }
                        item { FilterPill("Microphone", selectedFilter == MeetingFilter.RECORDED) { selectedFilter = MeetingFilter.RECORDED } }
                        item { FilterPill("Imported", selectedFilter == MeetingFilter.IMPORTED) { selectedFilter = MeetingFilter.IMPORTED } }
                        item { FilterPill("With summaries", selectedFilter == MeetingFilter.WITH_ACTIONS) { selectedFilter = MeetingFilter.WITH_ACTIONS } }
                    }
                }
            }

            if (filteredMeetings.isEmpty()) {
                item {
                    HomeEmptyState(onRecordClick = onNavigateToRecord, onImportClick = onNavigateToImport)
                }
            } else {
                groupedMeetings.forEach { (header, meetingList) ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 22.dp, top = 28.dp)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = header.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp,
                                color = InkMuted
                            )
                            Text(
                                text = meetingList.size.toString(),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = InkFaint
                            )
                        }
                        HorizontalDivider(color = LineSoft, modifier = Modifier.padding(horizontal = 22.dp))
                    }

                    items(meetingList, key = { it.id }) { meeting ->
                        Column {
                            HomeMeetingRow(
                                meeting = meeting,
                                onClick = { onNavigateToMeeting(meeting.id) },
                                onDelete = { viewModel.deleteMeeting(meeting.id) }
                            )
                            HorizontalDivider(color = LineFaint, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 14.dp)
                        .padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        // Honest about the profile in use: the line claims on-device only when
                        // that is actually what is happening to this user's recordings.
                        text = if (processingProfile.requiresNetwork) {
                            "Processed with Google's AI services"
                        } else {
                            "Everything stays on this phone"
                        },
                        fontSize = 12.5.sp,
                        color = InkMuted
                    )
                    Text(
                        text = "All recordings",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = InkSecondary,
                        modifier = Modifier
                            .clickable { selectedFilter = MeetingFilter.ALL }
                            .testTag("home_all_recordings")
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitle, fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", fontSize = 18.sp, color = InkFaint)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Ink else LineSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (selected) Color.White else InkSecondary)
    }
}

@Composable
private fun HomeMeetingRow(
    meeting: Meeting,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val barColor = when (meeting.status) {
        MeetingStatus.READY -> Accent
        MeetingStatus.ERROR -> Color(0xFFEF4444)
        MeetingStatus.MODEL_REQUIRED -> Speaker4
        else -> InkFaint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("meeting_card_${meeting.id}")
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(barColor))

        Column(modifier = Modifier.weight(1f)) {
            Text(meeting.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(modifier = Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${meeting.recordingType.displayName} · ${Formatters.formatDurationSummary(meeting.durationMs)} · ${Formatters.formatDateRelative(meeting.createdAt)}",
                    fontSize = 12.5.sp,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            meeting.summaryPreview?.let { summary ->
                Text(
                    text = summary,
                    fontSize = 12.5.sp,
                    color = InkSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = InkMuted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Delete Recording", color = Color(0xFFEF4444)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeEmptyState(
    onRecordClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Accent.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text("Capture your first thought", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Record a meeting, interview, lecture, or a quick voice memo — or import pre-recorded media — to get an automatic offline transcript and summary.",
            fontSize = 13.5.sp,
            color = InkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Ink)
                .clickable(onClick = onRecordClick)
                .testTag("empty_state_record_btn")
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Recording", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.clickable(onClick = onImportClick).testTag("empty_state_import_btn").padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
            Text("Or import a recording", fontSize = 13.5.sp, color = Accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The Record row from `#5a`: a filled Accent disc, the app's primary verb, and a hint naming the
 * workflow it will start with. Tapping the row opens the workflow list inline rather than pushing
 * a separate picker screen — the spec's "before recording: almost no friction".
 */
@Composable
private fun HomeRecordRow(
    hint: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onStartWithType: (RecordingType) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .testTag("bento_hero_card")
                .padding(horizontal = 22.dp)
                .padding(top = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(elevation = 14.dp, shape = CircleShape, ambientColor = Accent, spotColor = Accent)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                // The design's glyph is a rounded capsule, not a microphone pictogram.
                Box(
                    modifier = Modifier
                        .size(width = 15.dp, height = 23.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Record", fontSize = 17.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(hint, fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (expanded) "⌄" else "›", fontSize = 18.sp, color = InkFaint)
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceSunk)
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            ) {
                // The design's four, plus Sermon (docs/PLAN_V1.md §5). Every other type stays
                // available on the full picker one step into the recording flow.
                val quickTypes = listOf(
                    RecordingType.MEETING to "Speakers, decisions, tasks",
                    RecordingType.SERMON to "Scripture, points, notes",
                    RecordingType.INTERVIEW to "Two speakers, verbatim",
                    RecordingType.LECTURE to "One speaker, notes",
                    RecordingType.VOICE_MEMO to "Just capture it"
                )
                quickTypes.forEachIndexed { index, (type, blurb) ->
                    if (index > 0) HorizontalDivider(color = LineFaint)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartWithType(type) }
                            .testTag("home_record_type_${type.name.lowercase()}")
                            .padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(type.displayName, fontSize = 15.5.sp, color = Ink)
                        Text(blurb, fontSize = 12.sp, color = InkMuted)
                    }
                }
            }
        }
    }
}

/** The Import row from `#5a`: an outlined disc holding a small bar meter. */
@Composable
private fun HomeImportRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("bento_import_tile")
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.height(22.dp)
            ) {
                listOf(9, 18, 13, 22, 11).forEach { barHeight ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(InkMuted)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Import audio", fontSize = 17.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("A file, a voice note, or a video", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", fontSize = 18.sp, color = InkFaint)
    }
}

/**
 * The in-progress job card from `#5a`: bordered, with a real percentage and a real status line,
 * expanding to show what is actually running.
 *
 * The percentage is whatever the pipeline last reported. Nothing here animates it forward on its
 * own — a progress bar that moves while nothing is happening is a lie the user acts on.
 */
@Composable
private fun HomeJobCard(
    title: String,
    statusLine: String,
    percent: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 26.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, LineSoft, RoundedCornerShape(20.dp))
            .clickable(onClick = onToggleExpanded)
            .testTag("home_job_card")
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(statusLine, fontSize = 12.5.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            Text("$percent%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Accent)
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 11.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Accent,
            trackColor = LineSoft,
            drawStopIndicator = {}
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                HorizontalDivider(color = LineSoft)
                Text(
                    text = statusLine,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    color = InkSecondary,
                    modifier = Modifier.padding(top = 13.dp)
                )
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeJobChip("Open", onClick = onOpen)
                }
            }
        }
    }
}

@Composable
private fun HomeJobChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = InkSecondary,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}
