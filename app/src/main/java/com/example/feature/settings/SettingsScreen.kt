package com.example.feature.settings

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.core.database.MeetMindDatabase
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.core.datastore.AppPreferencesState
import com.example.core.datastore.UserPreferencesManager
import com.example.core.repository.MeetingRepository
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineSoft
import com.example.ui.theme.Speaker3
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [FirebaseAuthManager][com.example.core.firebase.FirebaseAuthManager] itself is untouched and
 * stays available for future use, but Settings no longer surfaces a user-profile section for it:
 * recording, import, and local AI processing all work without any account, so a sign-in/profile
 * UI here would be a real control over nothing an offline-first MVP user needs today.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)
    private val userPrefs = UserPreferencesManager(application)
    private val geminiCredentials = com.example.ai.cloud.GeminiCredentialStore(application)

    /** Redacted, so a set key can be shown as set without being put back on screen. */
    val geminiKeyDisplay: StateFlow<String?> = geminiCredentials.apiKeyFlow
        .map { geminiCredentials.redact(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch { geminiCredentials.setApiKey(key) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { geminiCredentials.clear() }
    }

    val preferencesState: StateFlow<AppPreferencesState> = userPrefs.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppPreferencesState()
    )

    fun setBibleVersion(id: Int) {
        viewModelScope.launch { userPrefs.setBibleVersionId(id) }
    }

    fun setCalendar(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setCalendarEnabled(enabled) }
    }

    fun setFaithLock(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setFaithLockEnabled(enabled) }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setWifiOnlyDownload(enabled)
        }
    }

    fun toggleCleanFillerWords(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setCleanFillerWords(enabled)
        }
    }

    fun setTranscriptCleanupMode(mode: com.example.core.model.TranscriptCleanupMode) {
        viewModelScope.launch {
            userPrefs.setTranscriptCleanupMode(mode)
        }
    }

    fun setProcessingProfile(profile: com.example.core.model.ProcessingProfile) {
        viewModelScope.launch {
            userPrefs.setProcessingProfile(profile)
        }
    }

    fun setDiarizationStrategy(strategy: com.example.core.model.DiarizationStrategy) {
        viewModelScope.launch {
            userPrefs.setDiarizationStrategy(strategy)
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            meetingRepository.deleteAllMeetings()
            onComplete()
        }
    }

    fun setSpaces(spaces: Set<com.example.core.model.NotebookSpace>) = viewModelScope.launch { userPrefs.setSpaces(spaces) }
    fun setLook(look: com.example.core.identity.LookAndFeel) = viewModelScope.launch { userPrefs.setLook(look) }
    fun setAvatar(uri: android.net.Uri) = viewModelScope.launch {
        com.example.core.identity.AvatarStore.save(getApplication(), uri)?.let { userPrefs.setAvatarPath(it) }
    }
    fun removeAvatar() = viewModelScope.launch {
        com.example.core.identity.AvatarStore.remove(getApplication()); userPrefs.setAvatarPath(null)
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            userPrefs.setUserName(name)
        }
    }
}

/**
 * Settings screen (Phase 15 §Part 2 / design `#6d`) — restyled onto the Ink/Accent flat-row
 * token system shared with AI Engine and the meeting detail screen. Visual pass only: every
 * existing row (Wi-Fi-only downloads, filler-word cleanup, transcript cleanup mode, speaker
 * detection strategy, privacy notice, clear-data) is kept, grouped exactly as before, in
 * `#6d`'s labelled-row-group pattern instead of Material3 cards. Adds a Profile section with the
 * user's own name (already collected at onboarding, but never editable afterward) — `#6d`'s
 * mockup doesn't show one, but there was nowhere else in the app to change it once set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit = {},
    onNavigateBottomNav: (com.example.core.ui.BottomNavDestination) -> Unit = {},
    onOpenBible: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesState.collectAsState()
    val geminiKeyDisplay by viewModel.geminiKeyDisplay.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSpaces by remember { mutableStateOf(false) }
    var showLook by remember { mutableStateOf(false) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setAvatar(uri) }
    if (showSpaces) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSpaces = false }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("What's MeetingMind for you?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Only these spaces show in Home, Notes and Record.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                com.example.core.identity.SpacesPicker(prefs.identity.spaces) { viewModel.setSpaces(it) }
            }
        }
    }
    if (showLook) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showLook = false }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Look & feel", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(bottom = 14.dp))
                com.example.core.identity.LookPicker(prefs.identity.look) { viewModel.setLook(it) }
            }
        }
    }
    var showBibleVersions by remember { mutableStateOf(false) }
    var calendarGranted by remember { mutableStateOf(com.example.core.calendar.CalendarEvents(context).hasPermission()) }
    val calendarPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> calendarGranted = granted; if (granted) viewModel.setCalendar(true) }
    // The translations licensed to this app, as the service reports them.
    var bibleVersions by remember { mutableStateOf<List<com.example.core.scripture.BibleInfo>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        bibleVersions = com.example.core.scripture.ScriptureService.library(context).bibles()
    }

    if (showBibleVersions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBibleVersions = false },
            containerColor = Color.White,
            title = { Text("Bible translation") },
            text = {
                Column {
                    if (bibleVersions.isEmpty()) Text("Connect to the internet to see the translations available.")
                    bibleVersions.forEach { v ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.setBibleVersion(v.id); showBibleVersions = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(selected = v.id == prefs.bibleVersionId, onClick = { viewModel.setBibleVersion(v.id); showBibleVersions = false })
                            Column {
                                Text(v.abbreviation, fontWeight = FontWeight.SemiBold)
                                Text(v.title, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        "More translations (NIV, KJV, ESV…) appear here once their licence is accepted for this app on platform.youversion.com.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showBibleVersions = false }) { Text("Done") } }
        )
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            com.example.core.ui.AppBottomNavigationBar(
                current = com.example.core.ui.BottomNavDestination.SETTINGS,
                onNavigate = onNavigateBottomNav,
                showNewAction = true
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_btn").size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkSecondary)
                    }
                }
                Text(
                    text = "Settings",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.7).sp,
                    color = Ink,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 2.dp)
                )
            }

            settingsSection(title = "Profile") {
                settingsRow {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.example.core.identity.Avatar(prefs.identity, 56.dp, Modifier.testTag("settings_avatar")) {
                            avatarPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(prefs.userName?.takeIf { it.isNotBlank() } ?: "Add your name", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("Tap the photo to change it", fontSize = 12.5.sp, color = InkMuted)
                        }
                        if (prefs.identity.avatarPath != null) androidx.compose.material3.TextButton(onClick = { viewModel.removeAvatar() }) { Text("Remove", color = InkMuted) }
                    }
                }
                settingsRow {
                    SettingsNavRow(
                        title = "What it's for",
                        subtitle = prefs.identity.spaces.sortedBy { it.ordinal }.joinToString(" · ") { it.displayName },
                        onClick = { showSpaces = true }
                    )
                }
                settingsRow {
                    SettingsNavRow(title = "Look & feel", subtitle = prefs.identity.look.label + " — " + prefs.identity.look.description, onClick = { showLook = true })
                }
                settingsRow {
                    SettingsValueRow(
                        title = "Name",
                        value = prefs.userName?.takeIf { it.isNotBlank() } ?: "Not set",
                        subtitle = "Used to personalize your experience, like Ask AI addressing you by name.",
                        onClick = { showEditNameDialog = true },
                        modifier = Modifier.testTag("settings_name_row")
                    )
                }
            }

            settingsSection(title = "Calendar") {
                settingsRow {
                    SettingsSwitchRow(
                        title = "Upcoming events on Home",
                        subtitle = if (prefs.calendarEnabled && !calendarGranted) "Allow calendar access to show them — tap to ask again"
                            else "Today's meetings and services from your phone's calendars, ready to record. Read only; nothing leaves the phone.",
                        checked = prefs.calendarEnabled && calendarGranted,
                        onCheckedChange = { on ->
                            if (!on) viewModel.setCalendar(false)
                            else if (calendarGranted) viewModel.setCalendar(true)
                            else calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
                        },
                        testTag = "settings_calendar"
                    )
                }
            }

            settingsSection(title = "Bible & Faith") {
                settingsRow {
                    val version = bibleVersions.firstOrNull { it.id == prefs.bibleVersionId }
                    SettingsNavRow(
                        title = "Bible translation",
                        subtitle = (version?.let { "${it.title} (${it.abbreviation})" } ?: com.example.core.scripture.BibleVersions.common.firstOrNull { it.id == prefs.bibleVersionId }?.let { "${it.title} (${it.abbreviation})" } ?: "Translation ${prefs.bibleVersionId}") +
                            if (com.example.BuildConfig.YOUVERSION_APP_KEY.isBlank()) " · verse text isn't set up in this build" else " · from YouVersion",
                        onClick = { showBibleVersions = true }
                    )
                }
                settingsRow {
                    SettingsNavRow(
                        title = "Bible on this phone",
                        subtitle = "Read and search offline — download an open translation from the Bible's translation menu",
                        onClick = onOpenBible
                    )
                }
                settingsRow {
                    SettingsSwitchRow(
                        title = "Lock Faith notes",
                        subtitle = "Prayers, reflections and the Faith space need your fingerprint, face or screen lock to open.",
                        checked = prefs.faithLockEnabled,
                        onCheckedChange = { viewModel.setFaithLock(it) },
                        testTag = "settings_faith_lock"
                    )
                }
            }

            settingsSection(title = "AI & Models") {
                settingsRow {
                    SettingsNavRow(
                        title = "AI Engine",
                        subtitle = "On-device models: download, pause, resume or remove",
                        onClick = onNavigateToModels
                    )
                }
                settingsRow {
                    SettingsSwitchRow(
                        title = "Download Models Over Wi-Fi Only",
                        subtitle = "Off by default — model downloads use mobile data too, unless you turn this on.",
                        checked = prefs.wifiOnlyDownload,
                        onCheckedChange = { viewModel.toggleWifiOnly(it) },
                        testTag = "settings_wifi_only_switch"
                    )
                }
            }

            settingsSection(title = "Transcripts") {
                settingsRow {
                    SettingsSwitchRow(
                        title = "Tidy Up Filler Words",
                        subtitle = "Hides \"uh\" and \"um\" when reading. Your transcript is always stored word-for-word, so turning this off brings them straight back.",
                        checked = prefs.cleanFillerWords,
                        onCheckedChange = { viewModel.toggleCleanFillerWords(it) },
                        testTag = "settings_clean_filler_switch"
                    )
                }
            }

            settingsSection(title = "Transcript Cleanup") {
                settingsRow {
                    SettingsRadioRow(
                        title = "Conservative",
                        subtitle = "Light cleanup. Keeps your original wording.",
                        selected = prefs.transcriptCleanupMode == com.example.core.model.TranscriptCleanupMode.CONSERVATIVE,
                        onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.CONSERVATIVE) },
                        testTag = "settings_cleanup_mode_conservative"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "Moderate",
                        subtitle = "Balances readability with your original wording.",
                        selected = prefs.transcriptCleanupMode == com.example.core.model.TranscriptCleanupMode.MODERATE,
                        onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.MODERATE) },
                        testTag = "settings_cleanup_mode_moderate"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "Aggressive",
                        subtitle = "Creates the most polished transcript. May make larger wording changes.",
                        selected = prefs.transcriptCleanupMode == com.example.core.model.TranscriptCleanupMode.AGGRESSIVE,
                        onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.AGGRESSIVE) },
                        testTag = "settings_cleanup_mode_aggressive"
                    )
                }
            }

            settingsSection(title = "Speaker Detection") {
                settingsRow {
                    SettingsRadioRow(
                        title = "Automatic",
                        subtitle = "MeetingMind chooses the best approach.",
                        selected = prefs.diarizationStrategy == com.example.core.model.DiarizationStrategy.AUTO,
                        onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.AUTO) },
                        testTag = "settings_diarization_auto"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "Deterministic",
                        subtitle = "Uses the local speaker detection engine.",
                        selected = prefs.diarizationStrategy == com.example.core.model.DiarizationStrategy.DETERMINISTIC,
                        onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.DETERMINISTIC) },
                        testTag = "settings_diarization_deterministic"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "AI-assisted",
                        subtitle = "Uses local AI to help resolve difficult speaker assignments.",
                        selected = prefs.diarizationStrategy == com.example.core.model.DiarizationStrategy.AI_ASSISTED,
                        onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.AI_ASSISTED) },
                        testTag = "settings_diarization_ai_assisted"
                    )
                }
            }

            settingsSection(title = "Privacy") {
                // #6d expresses this as one switch with a line of consequence, not a pair of
                // radio buttons: "on-device only, yes or no" is the decision the user is actually
                // making, and the hint says what turning it off means before they do it.
                settingsRow {
                    SettingsSwitchRow(
                        title = "On-device only",
                        subtitle = if (prefs.processingProfile.requiresNetwork) {
                            "Off. Recordings are uploaded to Google's AI services for transcription and analysis; results are stored on this phone."
                        } else {
                            "On. Audio and transcripts never leave this phone — transcription, speakers, summaries and search all run on its own CPU."
                        },
                        checked = !prefs.processingProfile.requiresNetwork,
                        onCheckedChange = { onDeviceOnly ->
                            viewModel.setProcessingProfile(
                                if (onDeviceOnly) com.example.core.model.ProcessingProfile.OFFLINE
                                else com.example.core.model.ProcessingProfile.INTERNET
                            )
                        },
                        testTag = "settings_on_device_only"
                    )
                }
                if (prefs.processingProfile.requiresNetwork) {
                    item {
                        GeminiApiKeyRow(
                            redactedKey = geminiKeyDisplay,
                            onSave = { viewModel.setGeminiApiKey(it) },
                            onClear = { viewModel.clearGeminiApiKey() }
                        )
                    }
                }
            }

            settingsSection(title = "Storage") {
                item {
                    Text(
                        text = "Clear all local data & audio",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearDataDialog = true }
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                }
            }

            item {
                Text(
                    text = "MeetingMind ${BuildConfig.VERSION_NAME} · no account, no sync",
                    fontSize = 12.5.sp,
                    color = InkMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).padding(horizontal = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showEditNameDialog) {
        var draftName by remember { mutableStateOf(prefs.userName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Your name") },
            text = {
                Column {
                    Text(
                        "Used to personalize your experience, like Ask AI addressing you by name. Stored only on this device.",
                        fontSize = 13.sp,
                        color = InkSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_name_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setUserName(draftName)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    modifier = Modifier.testTag("settings_name_save_btn")
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel", color = InkSecondary) }
            }
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Delete All Data?") },
            text = { Text("This will permanently delete all meeting audio recordings, transcripts, summaries, and action items from your device. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        viewModel.clearAllData {
                            Toast.makeText(context, "All local data cleared", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = InkSecondary)
                }
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsSection(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    item {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = InkMuted,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 8.dp)
        )
    }
    content()
    item { HorizontalDivider(color = LineSoft, modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsRow(content: @Composable () -> Unit) {
    item {
        Column {
            content()
            HorizontalDivider(color = com.example.ui.theme.LineFaint, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, color = Ink)
            Text(subtitle, fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", fontSize = 18.sp, color = InkFaint, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsValueRow(title: String, value: String, subtitle: String? = null, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, color = Ink)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text(value, fontSize = 13.sp, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.5.sp, color = Ink)
            Text(subtitle, fontSize = 12.5.sp, color = InkMuted, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingsRadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.5.sp, color = Ink)
            Text(subtitle, fontSize = 12.5.sp, color = InkMuted, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = InkFaint),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * Where the user enters their own Gemini API key.
 *
 * The key is stored on this device only — it is not in the app's source, its build, or the
 * installable it came from, all of which are public. That is stated on the row rather than left
 * implicit, because "where does my API key go" is a fair thing to want answered before typing one
 * into a phone.
 *
 * The field is a password field and the stored value is only ever shown redacted, so a key cannot
 * be read back off the screen once saved.
 */
@Composable
private fun GeminiApiKeyRow(
    redactedKey: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Text(text = "Gemini API key", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(
            text = if (redactedKey != null) {
                "Saved on this device: $redactedKey"
            } else {
                "Internet mode needs your own Gemini API key. It is stored on this device only — never in the app or its source."
            },
            fontSize = 12.5.sp,
            color = InkSecondary,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                placeholder = { Text("Paste your key", fontSize = 13.sp, color = InkMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .testTag("settings_gemini_key_field")
            )
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onSave(draft)
                        draft = ""
                        editing = false
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("settings_gemini_key_save")
                ) { Text("Save", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = { draft = ""; editing = false }) {
                    Text("Cancel", color = InkSecondary, fontSize = 13.sp)
                }
            }
        } else {
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { editing = true },
                    modifier = Modifier.testTag("settings_gemini_key_edit")
                ) {
                    Text(
                        text = if (redactedKey != null) "Replace key" else "Add key",
                        color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                if (redactedKey != null) {
                    TextButton(onClick = onClear, modifier = Modifier.testTag("settings_gemini_key_clear")) {
                        Text("Remove", color = InkSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
