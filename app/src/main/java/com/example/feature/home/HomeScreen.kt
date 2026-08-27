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
    /** One tap, no type picker — the fastest path from "I want to record" to actually recording. */
    onNavigateToQuickRecord: () -> Unit = onNavigateToRecord
) {
    val meetings by viewModel.meetings.collectAsState()
    val activeJobs by viewModel.activeJobs.collectAsState()
    val greeting = viewModel.getGreetingText()
    val caps = viewModel.deviceCapabilities

    var selectedFilter by remember { mutableStateOf(MeetingFilter.ALL) }

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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToQuickRecord,
                containerColor = Ink,
                contentColor = Color.White,
                modifier = Modifier.testTag("home_quick_record_fab")
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Quick Record")
            }
        },
        bottomBar = {
            com.example.core.ui.AppBottomNavigationBar(
                current = com.example.core.ui.BottomNavDestination.HOME,
                onNavigate = { destination ->
                    when (destination) {
                        com.example.core.ui.BottomNavDestination.HOME -> Unit
                        com.example.core.ui.BottomNavDestination.SEARCH -> onNavigateToSearch()
                        com.example.core.ui.BottomNavDestination.AI_ENGINE -> onNavigateToModels()
                        com.example.core.ui.BottomNavDestination.SETTINGS -> onNavigateToSettings()
                    }
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
                            text = "${meetings.size} recordings",
                            fontSize = 13.sp,
                            color = InkMuted,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToSearch, modifier = Modifier.testTag("home_search_icon_btn").size(40.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "Search Recordings", tint = Ink)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    HomeActionRow(
                        title = "Record",
                        subtitle = "Start capturing on this phone",
                        icon = Icons.Default.Mic,
                        onClick = onNavigateToRecord,
                        testTag = "bento_hero_card"
                    )
                    HorizontalDivider(color = LineFaint, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
                    HomeActionRow(
                        title = "Import audio",
                        subtitle = "A file, a voice note, or a video",
                        icon = Icons.Default.FileUpload,
                        onClick = onNavigateToImport,
                        testTag = "bento_import_tile"
                    )
                }
                HorizontalDivider(color = LineSoft, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp))
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToModels)
                        .testTag("bento_telemetry_tile")
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                        Column {
                            Text("On-device AI engine", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(
                                "${caps.availableRamGb} GB RAM free · ${caps.cpuArch} · ${caps.devicePerformanceTier}",
                                fontSize = 12.sp,
                                color = InkMuted,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Text("›", fontSize = 18.sp, color = InkFaint)
                }
                HorizontalDivider(color = LineSoft, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
            }

            if (activeJobs.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 20.dp, start = 22.dp, end = 22.dp)) {
                        Text(text = "ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = InkMuted)
                        activeJobs.forEach { job ->
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(job.meetingTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${job.progressPercent}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                                }
                                Text(job.currentStep, fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                                LinearProgressIndicator(
                                    progress = { job.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = Accent,
                                    trackColor = LineSoft
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = LineSoft, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp))
                }
            }

            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Your recordings", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("${filteredMeetings.size} logged", fontSize = 12.5.sp, color = InkMuted)
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 22.dp, end = 22.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
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
                        Text(
                            text = header.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            color = InkMuted,
                            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 8.dp)
                        )
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
                Text(
                    text = "Everything stays on this phone",
                    fontSize = 12.5.sp,
                    color = InkMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).padding(horizontal = 22.dp)
                )
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
