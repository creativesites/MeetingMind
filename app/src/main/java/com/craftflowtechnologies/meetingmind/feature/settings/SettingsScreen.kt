package com.craftflowtechnologies.meetingmind.feature.settings

import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
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
import androidx.compose.ui.res.stringResource
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.feature.applock.findFragmentActivity
import com.craftflowtechnologies.meetingmind.feature.applock.openSecuritySettings
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
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.craftflowtechnologies.meetingmind.core.datastore.AppPreferencesState
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.originals.OriginalsPack
import com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.Speaker3
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * [FirebaseAuthManager][com.craftflowtechnologies.meetingmind.core.firebase.FirebaseAuthManager] itself is untouched and
 * stays available for future use, but Settings no longer surfaces a user-profile section for it:
 * recording, import, and local AI processing all work without any account, so a sign-in/profile
 * UI here would be a real control over nothing an offline-first MVP user needs today.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)
    private val userPrefs = UserPreferencesManager(application)
    private val geminiCredentials = com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore(application)

    /** Redacted, so a set key can be shown as set without being put back on screen. */
    val geminiKeyDisplay: StateFlow<String?> = geminiCredentials.userKeyFlow
        .map { own -> geminiCredentials.redact(own) ?: com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore.systemKey?.let { "Built-in tester key" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch { geminiCredentials.setApiKey(key) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { geminiCredentials.clear() }
    }

    /** The result of "Check Gemini key": one line per service, or null before it's run. */
    private val _keyCheck = kotlinx.coroutines.flow.MutableStateFlow<List<Pair<String, String?>>?>(null)
    val keyCheck: StateFlow<List<Pair<String, String?>>?> = _keyCheck
    private val _checking = kotlinx.coroutines.flow.MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    /**
     * Tries the key for real, every service at once with its own time limit: that the
     * transcription and writing models are reachable with this key, a one-word written answer,
     * and a live voice session (no microphone). The slowest check sets the wait, not their sum.
     */
    fun checkGeminiKey() = viewModelScope.launch {
        val key = geminiCredentials.getApiKey() ?: run { _keyCheck.value = listOf("Key" to "No key saved yet."); return@launch }
        _checking.value = true
        _keyCheck.value = null
        val router = com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
        suspend fun <T> limited(ms: Long, block: suspend () -> T): T? = kotlinx.coroutines.withTimeoutOrNull(ms) { block() }
        launch(kotlinx.coroutines.Dispatchers.IO) { com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.attach(getApplication()); com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.add(GeminiKeyProbe.network()) }
        val transcription = async { limited(25_000) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { GeminiKeyProbe.model(key, router.GEMINI_TRANSCRIBE_MODEL) } } ?: "No answer within 25 seconds." }
        val text = async {
            limited(30_000) {
                val transport = com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport(geminiCredentials)
                when (val r = transport.execute(com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest(router.GEMINI_INTELLIGENCE_MODEL, "", "Reply with the single word OK.", timeoutMs = 25_000L))) {
                    is com.craftflowtechnologies.meetingmind.ai.common.AiResult.Success -> null
                    is com.craftflowtechnologies.meetingmind.ai.common.AiResult.Failed -> r.message
                    else -> "No answer."
                }
            } ?: "No answer within 30 seconds."
        }
        val live = async { limited(28_000) { runCatching { com.craftflowtechnologies.meetingmind.ai.live.GeminiLiveVoice.probe(key, timeoutMs = 22_000L) }.getOrElse { it.message ?: "Failed." } } ?: "No answer within 28 seconds." }
        try {
            com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.attach(getApplication())
            _keyCheck.value = listOf(
                "Transcription (recordings)" to transcription.await(),
                "Writing (summaries, devotionals, notes AI)" to text.await(),
                "Live voice (Pray with me)" to live.await()
            )
            _keyCheck.value?.forEach { (what, problem) -> com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.add("Key check · $what: ${problem ?: "OK"}") }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _keyCheck.value = listOf("Key" to (e.message ?: "The check failed."))
        } finally {
            _checking.value = false
        }
    }

    val workSettings: StateFlow<com.craftflowtechnologies.meetingmind.core.work.WorkSettings> = userPrefs.workSettings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.craftflowtechnologies.meetingmind.core.work.WorkSettings()
    )

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

    fun setThemeMode(mode: com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode) {
        viewModelScope.launch { userPrefs.setThemeMode(mode.name) }
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

    fun setTranscriptCleanupMode(mode: com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode) {
        viewModelScope.launch {
            userPrefs.setTranscriptCleanupMode(mode)
        }
    }

    fun setProcessingProfile(profile: com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile) {
        viewModelScope.launch {
            userPrefs.setProcessingProfile(profile)
        }
    }

    fun setDiarizationStrategy(strategy: com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy) {
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

    fun setSpaces(spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>) = viewModelScope.launch { userPrefs.setSpaces(spaces) }
    fun setLook(look: com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel) = viewModelScope.launch { userPrefs.setLook(look) }
    fun setCardStyle(style: com.craftflowtechnologies.meetingmind.core.identity.CardStyle) = viewModelScope.launch { userPrefs.setCardStyle(style) }
    fun setAvatar(uri: android.net.Uri) = viewModelScope.launch {
        com.craftflowtechnologies.meetingmind.core.identity.AvatarStore.save(getApplication(), uri)?.let { userPrefs.setAvatarPath(it) }
    }
    fun removeAvatar() = viewModelScope.launch {
        com.craftflowtechnologies.meetingmind.core.identity.AvatarStore.remove(getApplication()); userPrefs.setAvatarPath(null)
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
    onNavigateBottomNav: (com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit = {},
    onOpenBible: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onOpenDataBackup: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenWork: () -> Unit = {},
    onReplayTour: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesState.collectAsState()
    val geminiKeyDisplay by viewModel.geminiKeyDisplay.collectAsState()
    val work by viewModel.workSettings.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSpaces by remember { mutableStateOf(false) }
    var showLook by remember { mutableStateOf(false) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setAvatar(uri) }
    if (showSpaces) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSpaces = false }, containerColor = SurfaceBase) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("What's MeetingMind for you?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Only these spaces show in Home, Notes and Record.", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                com.craftflowtechnologies.meetingmind.core.identity.SpacesPicker(prefs.identity.spaces) { viewModel.setSpaces(it) }
            }
        }
    }
    if (showLook) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showLook = false }, containerColor = SurfaceBase) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Look & feel", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(bottom = 14.dp))
                com.craftflowtechnologies.meetingmind.core.identity.LookPicker(prefs.identity.look) { viewModel.setLook(it) }
                Text("Cards", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                com.craftflowtechnologies.meetingmind.core.identity.CardStyle.entries.forEach { style ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.setCardStyle(style) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = prefs.identity.cardStyle == style, onClick = { viewModel.setCardStyle(style) })
                        Column {
                            Text(style.label, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(style.description, fontSize = 12.5.sp, color = InkMuted)
                        }
                    }
                }
            }
        }
    }
    var showBibleVersions by remember { mutableStateOf(false) }
    var calendarGranted by remember { mutableStateOf(com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvents(context).hasPermission()) }
    val calendarPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> calendarGranted = granted; if (granted) viewModel.setCalendar(true) }
    // The translations licensed to this app, as the service reports them.
    var bibleVersions by remember { mutableStateOf<List<com.craftflowtechnologies.meetingmind.core.scripture.BibleInfo>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        bibleVersions = com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService.library(context).bibles()
    }

    if (showBibleVersions) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBibleVersions = false },
            containerColor = SurfaceBase,
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
        containerColor = SurfaceBase,
        bottomBar = {
            com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                current = com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SETTINGS,
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
                        com.craftflowtechnologies.meetingmind.core.identity.Avatar(prefs.identity, 56.dp, Modifier.testTag("settings_avatar")) {
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
                if (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK in prefs.identity.spaces) settingsRow {
                    SettingsNavRow(
                        title = "Work",
                        subtitle = listOfNotNull(work.profile.label, work.terms.organisation + " · " + work.terms.project, "on this phone only".takeIf { work.keepOnDevice }).joinToString(" · "),
                        onClick = onOpenWork
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
                        subtitle = (version?.let { "${it.title} (${it.abbreviation})" } ?: com.craftflowtechnologies.meetingmind.core.scripture.BibleVersions.common.firstOrNull { it.id == prefs.bibleVersionId }?.let { "${it.title} (${it.abbreviation})" } ?: "Translation ${prefs.bibleVersionId}") +
                            if (com.craftflowtechnologies.meetingmind.BuildConfig.YOUVERSION_APP_KEY.isBlank()) " · verse text isn't set up in this build" else " · from YouVersion",
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
                OriginalsPack.entries.forEach { pack ->
                    settingsRow {
                        com.craftflowtechnologies.meetingmind.feature.bible.OriginalsPackRow(pack) { title, subtitle, onClick -> SettingsNavRow(title = title, subtitle = subtitle, onClick = onClick) }
                    }
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

            settingsSection(title = "Appearance") {
                settingsRow {
                    SettingsNavRow(
                        title = "Look and home",
                        subtitle = "Colour, text size, and Today or Focus as your home",
                        onClick = onOpenAppearance
                    )
                }
                val current = runCatching { com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.valueOf(prefs.themeMode) }.getOrDefault(com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.DARK)
                com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.entries.forEach { mode ->
                    settingsRow {
                        SettingsRadioRow(
                            title = mode.label,
                            subtitle = when (mode) {
                                com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.DARK -> "Graphite: easy on the eyes, day and night"
                                com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.LIGHT -> "Paper: warm and bright"
                                com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.SYSTEM -> "Follows your phone's dark mode"
                            },
                            selected = current == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            testTag = "settings_theme_${mode.name.lowercase()}"
                        )
                    }
                }
            }

            settingsSection(title = "Your data") {
                settingsRow {
                    SettingsNavRow(
                        title = "Data & backup",
                        subtitle = "Back up, restore, export everything as Markdown, and the Trash",
                        onClick = onOpenDataBackup
                    )
                }
            }

            settingsSection(title = "AI & Models") {
                settingsRow {
                    SettingsNavRow(
                        title = "Setup guide",
                        subtitle = "What the app needs to transcribe and summarise, and one tap to get it",
                        onClick = onOpenSetup
                    )
                }
                settingsRow {
                    SettingsNavRow(
                        title = "Show me around again",
                        subtitle = "Replay the quick tour of Home",
                        onClick = onReplayTour
                    )
                }
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
                        selected = prefs.transcriptCleanupMode == com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.CONSERVATIVE,
                        onClick = { viewModel.setTranscriptCleanupMode(com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.CONSERVATIVE) },
                        testTag = "settings_cleanup_mode_conservative"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "Moderate",
                        subtitle = "Balances readability with your original wording.",
                        selected = prefs.transcriptCleanupMode == com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.MODERATE,
                        onClick = { viewModel.setTranscriptCleanupMode(com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.MODERATE) },
                        testTag = "settings_cleanup_mode_moderate"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "Aggressive",
                        subtitle = "Creates the most polished transcript. May make larger wording changes.",
                        selected = prefs.transcriptCleanupMode == com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.AGGRESSIVE,
                        onClick = { viewModel.setTranscriptCleanupMode(com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.AGGRESSIVE) },
                        testTag = "settings_cleanup_mode_aggressive"
                    )
                }
            }

            settingsSection(title = "Speaker Detection") {
                settingsRow {
                    SettingsRadioRow(
                        title = "Automatic",
                        subtitle = "MeetingMind chooses the best approach.",
                        selected = prefs.diarizationStrategy == com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AUTO,
                        onClick = { viewModel.setDiarizationStrategy(com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AUTO) },
                        testTag = "settings_diarization_auto"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "Deterministic",
                        subtitle = "Uses the local speaker detection engine.",
                        selected = prefs.diarizationStrategy == com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.DETERMINISTIC,
                        onClick = { viewModel.setDiarizationStrategy(com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.DETERMINISTIC) },
                        testTag = "settings_diarization_deterministic"
                    )
                }
                settingsRow {
                    SettingsRadioRow(
                        title = "AI-assisted",
                        subtitle = "Uses local AI to help resolve difficult speaker assignments.",
                        selected = prefs.diarizationStrategy == com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AI_ASSISTED,
                        onClick = { viewModel.setDiarizationStrategy(com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AI_ASSISTED) },
                        testTag = "settings_diarization_ai_assisted"
                    )
                }
            }

            settingsSection(title = "Privacy") {
                settingsRow { AppLockSettingRow(enabled = prefs.appLockEnabled) }
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
                                if (onDeviceOnly) com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.OFFLINE
                                else com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.INTERNET
                            )
                        },
                        testTag = "settings_on_device_only"
                    )
                }
                // Always offered: Pray with me and "New devotional → Gemini" use the key even when
                // recordings stay on the phone.
                item {
                    GeminiApiKeyRow(
                        redactedKey = geminiKeyDisplay,
                        onSave = { viewModel.setGeminiApiKey(it) },
                        onClear = { viewModel.clearGeminiApiKey() }
                    )
                }
                if (geminiKeyDisplay != null) item {
                    val check by viewModel.keyCheck.collectAsState()
                    val checking by viewModel.checking.collectAsState()
                    GeminiKeyCheck(check, checking, onCheck = { viewModel.checkGeminiKey() })
                }
                item { DeepSeekRow() }
                item { GeminiLogRow() }
                if (BuildConfig.DEBUG) item { AudioBenchmarkRow() }
            }

            settingsSection(title = "Storage") {
                item {
                    Text(
                        text = "Clear all local data & audio",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Danger,
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
                    Text("Save", color = OnInk)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
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
            HorizontalDivider(color = com.craftflowtechnologies.meetingmind.ui.theme.LineFaint, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
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
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnInk),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * The App Lock switch. Both directions run through [com.craftflowtechnologies.meetingmind.core.applock.AppLockSettings], which
 * asks Android to verify the owner first — the switch only moves once the saved preference does,
 * so a cancelled prompt leaves it where it was.
 */
@Composable
private fun AppLockSettingRow(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() } ?: return
    val appLock: com.craftflowtechnologies.meetingmind.core.applock.AppLockViewModel = androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = activity)
    val authenticator = remember(activity) { com.craftflowtechnologies.meetingmind.core.applock.BiometricPromptAuthenticator(activity) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var problem by remember { mutableStateOf<com.craftflowtechnologies.meetingmind.core.applock.AppLockChange?>(null) }
    var busy by remember { mutableStateOf(false) }

    SettingsSwitchRow(
            title = stringResource(R.string.app_lock_setting_title),
            subtitle = stringResource(R.string.app_lock_setting_subtitle) + if (enabled) " " + stringResource(R.string.app_lock_setting_on_hint) else "",
            checked = enabled,
            onCheckedChange = { wanted ->
                if (busy) return@SettingsSwitchRow
                busy = true
                scope.launch {
                    try {
                        val change = appLock.settings(authenticator).setEnabled(wanted)
                        // A cancelled prompt is a decision, not a problem: no message, the switch stays put.
                        val cancelled = change is com.craftflowtechnologies.meetingmind.core.applock.AppLockChange.NotVerified &&
                            change.result is com.craftflowtechnologies.meetingmind.core.applock.AuthResult.Cancelled
                        if (change !is com.craftflowtechnologies.meetingmind.core.applock.AppLockChange.Changed && !cancelled) problem = change
                    } finally {
                        busy = false
                    }
                }
            },
            testTag = "settings_app_lock"
        )

    val shown = problem
    val message = when (shown) {
        is com.craftflowtechnologies.meetingmind.core.applock.AppLockChange.Unavailable -> when (shown.reason) {
            com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability.NoneEnrolled -> R.string.app_lock_setup_none_enrolled
            com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability.NoHardware -> R.string.app_lock_setup_no_hardware
            com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability.HardwareUnavailable -> R.string.app_lock_setup_hardware_unavailable
            com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability.SecurityUpdateRequired -> R.string.app_lock_setup_update_required
            else -> R.string.app_lock_setup_unsupported
        }
        is com.craftflowtechnologies.meetingmind.core.applock.AppLockChange.NotVerified ->
            if (shown.result is com.craftflowtechnologies.meetingmind.core.applock.AuthResult.LockedOut) R.string.app_lock_change_locked_out else R.string.app_lock_change_error
        else -> null
    }
    if (shown != null && message != null) {
        val canOpenSettings = shown is com.craftflowtechnologies.meetingmind.core.applock.AppLockChange.Unavailable &&
            shown.reason in setOf(com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability.NoneEnrolled, com.craftflowtechnologies.meetingmind.core.applock.AppLockAvailability.NoHardware)
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text(stringResource(if (shown is com.craftflowtechnologies.meetingmind.core.applock.AppLockChange.Unavailable) R.string.app_lock_setup_title else R.string.app_lock_setting_title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                if (canOpenSettings) TextButton(onClick = { problem = null; context.openSecuritySettings() }) { Text(stringResource(R.string.app_lock_setup_open_settings)) }
                else TextButton(onClick = { problem = null }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { if (canOpenSettings) TextButton(onClick = { problem = null }) { Text(stringResource(R.string.app_lock_setup_dismiss)) } }
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
 * DeepSeek, the backup writer: when Gemini can't answer, AI text features use it. Shows this
 * month's usage against the allowance, takes a key for testing, and checks it works.
 */
@Composable
private fun DeepSeekRow() {
    val context = LocalContext.current
    val ds = remember { com.craftflowtechnologies.meetingmind.ai.cloud.DeepSeek(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val key by ds.userKeyFlow.collectAsState(initial = null)
    var used by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val builtIn = com.craftflowtechnologies.meetingmind.ai.cloud.DeepSeek.proxyUrl != null || com.craftflowtechnologies.meetingmind.ai.cloud.DeepSeek.systemKey != null
    androidx.compose.runtime.LaunchedEffect(key, status) { used = ds.usedThisMonth() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("settings_deepseek")) {
        Text("Backup AI (DeepSeek)", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(
            when {
                builtIn -> "Built in. Takes over writing — summaries, notes AI, devotionals — whenever Gemini can't answer."
                key != null -> "Key saved on this device. Takes over writing whenever Gemini can't answer."
                else -> "Add a DeepSeek key and writing features keep working when Gemini can't answer."
            },
            fontSize = 12.5.sp, color = InkMuted
        )
        val limit = com.craftflowtechnologies.meetingmind.ai.cloud.DeepSeek.MONTHLY_TOKENS
        androidx.compose.material3.LinearProgressIndicator(
            progress = { (used.toFloat() / limit).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp),
            color = Accent, trackColor = LineSoft
        )
        Text("%.2fM of %dM tokens used this month".format(used / 1_000_000.0, limit / 1_000_000), fontSize = 12.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { editing = true; draft = "" }) { Text(if (key == null) "Add key" else "Replace key") }
            if (key != null) TextButton(onClick = { scope.launch { ds.setKey("") } }) { Text("Remove", color = InkSecondary) }
            if (builtIn || key != null) TextButton(onClick = {
                status = "Checking…"
                scope.launch {
                    val r = ds.complete("", "Reply with the single word OK.", json = false, timeoutMs = 30_000L, maxTokens = 5)
                    status = if (r is com.craftflowtechnologies.meetingmind.ai.common.AiResult.Success) "✓ DeepSeek works" else "✗ ${(r as? com.craftflowtechnologies.meetingmind.ai.common.AiResult.Failed)?.message ?: (r as? com.craftflowtechnologies.meetingmind.ai.common.AiResult.ModelUnavailable)?.message}"
                }
            }) { Text("Check") }
        }
        status?.let { Text(it, fontSize = 12.5.sp, color = if (it.startsWith("✓")) com.craftflowtechnologies.meetingmind.ui.theme.SuccessGreen else if (it.startsWith("✗")) Danger else InkSecondary) }
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("DeepSeek key") },
        text = {
            OutlinedTextField(draft, { draft = it.trim() }, singleLine = true, placeholder = { Text("sk-…") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { scope.launch { ds.setKey(draft); editing = false } }, enabled = draft.startsWith("sk-")) { Text("Save") } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
    )
}

/** Developer only (debug builds): scores the sound detector against recordings and labels the team put on the phone. */
@Composable
private fun AudioBenchmarkRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !running) {
            running = true
            scope.launch {
                report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = context.getExternalFilesDir("benchmark") ?: context.filesDir
                    dir.mkdirs()
                    runCatching { com.craftflowtechnologies.meetingmind.core.audio.AudioBenchmark.runFolder(dir) + "\n\nFolder: ${dir.path}" }.getOrElse { "The benchmark stopped: ${it.message}" }
                }
                running = false
            }
        }.padding(horizontal = 16.dp, vertical = 14.dp).testTag("settings_audio_benchmark"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Run audio benchmark (developer)", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(if (running) "Scoring…" else "Scores the sound detector on recordings and labels in this app's benchmark folder.", fontSize = 12.5.sp, color = InkMuted)
        }
    }
    report?.let { r ->
        AlertDialog(onDismissRequest = { report = null }, title = { Text("Audio benchmark") },
            text = { androidx.compose.foundation.layout.Box(Modifier.height(420.dp)) { androidx.compose.foundation.lazy.LazyColumn { item { Text(r, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = InkSecondary) } } } },
            confirmButton = { TextButton(onClick = { context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Benchmark", r)); Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = { report = null }) { Text("Close") } })
    }
}

/**
 * Every Gemini call and what came of it — for testers to copy and send, so a failure arrives
 * with its real reason instead of "it didn't work".
 */
@Composable
private fun GeminiLogRow() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.attach(context); open = true }.padding(horizontal = 16.dp, vertical = 14.dp).testTag("settings_gemini_log"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Gemini log", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("What happened on each request, with Gemini's own errors. Copy it and send it to the developer.", fontSize = 12.5.sp, color = InkMuted)
        }
    }
    if (open) {
        var entries by remember { mutableStateOf(com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.entries()) }
        val report = { com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.report("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Gemini log") },
            text = {
                if (entries.isEmpty()) Text("Nothing yet. Process a recording or check your key, then look again.", color = InkMuted)
                else LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(entries.size) { i ->
                        val line = entries[i]
                        val bad = line.contains("✗") || line.contains("HTTP 4") || line.contains("HTTP 5") || line.contains("Exception") || line.contains("no transcript")
                        Text(line, fontSize = 12.sp, lineHeight = 16.sp, color = if (bad) Danger else InkSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, report())
                        runCatching { context.startActivity(android.content.Intent.createChooser(send, "Send Gemini log")) }
                    }) { Text("Share") }
                    TextButton(onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("Gemini log", report()))
                        Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                }
            },
            dismissButton = { TextButton(onClick = { com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.clear(); entries = emptyList() }) { Text("Clear", color = Danger) } }
        )
    }
}

/** "Check Gemini key": tests writing and live voice with the saved key, and says what works. */
@Composable
private fun GeminiKeyCheck(result: List<Pair<String, String?>>?, checking: Boolean, onCheck: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("settings_gemini_check")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check Gemini key", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            if (checking) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else TextButton(onClick = onCheck, modifier = Modifier.testTag("settings_gemini_check_btn")) { Text(if (result == null) "Check" else "Check again") }
        }
        Text(
            if (checking) "Checking transcription, writing and live voice together… (up to 30 seconds)" else "Tries transcription, writing and live voice with your key, so you know what works.",
            fontSize = 12.5.sp, color = InkMuted
        )
        result?.forEach { (what, problem) ->
            Row(Modifier.padding(top = 6.dp)) {
                Text(if (problem == null) "✓" else "✗", color = if (problem == null) com.craftflowtechnologies.meetingmind.ui.theme.SuccessGreen else Danger, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                Column {
                    Text(what, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text(problem ?: "Works", fontSize = 12.5.sp, color = if (problem == null) InkSecondary else Danger)
                }
            }
        }
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

/** Quick reachability checks against Gemini, for the key check in Settings. */
internal object GeminiKeyProbe {
    private val client = com.craftflowtechnologies.meetingmind.core.net.Net.base.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Which routes to Gemini work from this phone, for the log: how many IPv4 and IPv6 addresses
     * the name resolves to, and whether a connection to each kind opens within a few seconds.
     */
    fun network(): String {
        val host = "generativelanguage.googleapis.com"
        val addresses = runCatching { java.net.InetAddress.getAllByName(host).toList() }.getOrElse { return "DNS failed for $host: ${it.message}" }
        fun tryOne(a: java.net.InetAddress?): String {
            if (a == null) return "none"
            val start = System.currentTimeMillis()
            return runCatching {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(a, 443), 4_000) }
                "connects in ${System.currentTimeMillis() - start}ms"
            }.getOrElse { "fails (${it.javaClass.simpleName} after ${System.currentTimeMillis() - start}ms)" }
        }
        val v4 = addresses.filterIsInstance<java.net.Inet4Address>()
        val v6 = addresses.filterIsInstance<java.net.Inet6Address>()
        return "Network: ${v4.size} IPv4 → ${tryOne(v4.firstOrNull())}; ${v6.size} IPv6 → ${tryOne(v6.firstOrNull())}"
    }

    /** Null when [model] exists and this key may use it; otherwise why not, in words. */
    fun model(key: String, model: String): String? {
        val request = okhttp3.Request.Builder()
            .url("${com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport.DEFAULT_BASE_URL}/v1beta/models/$model?key=$key").get().build()
        return runCatching {
            client.newBuilder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> null
                    r.code == 404 -> "The model $model isn't available to this key."
                    r.code == 400 || r.code == 401 || r.code == 403 -> "Your key was rejected (HTTP ${r.code})."
                    r.code == 429 -> "Quota exceeded for now."
                    else -> "Gemini answered HTTP ${r.code}."
                }
            }
        }.getOrElse { "Couldn't reach Gemini (${it.javaClass.simpleName}): ${it.message ?: "no connection"}" }
    }
}
