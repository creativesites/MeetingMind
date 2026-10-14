package com.craftflowtechnologies.meetingmind.feature.settings

import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
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
        .map { own -> geminiCredentials.redact(own) ?: com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore.systemKey?.let { "Built in" } }
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

    private val devotionalRepository = com.craftflowtechnologies.meetingmind.core.devotional.DevotionalRepository(application)

    /** The daily devotional's profile; its notification switches are on the Notifications screen. */
    val devotional: StateFlow<com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile> = userPrefs.devotionalProfile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile()
    )

    fun setDevotional(profile: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile) {
        viewModelScope.launch { devotionalRepository.setProfile(profile) }
    }

    val reminders: StateFlow<com.craftflowtechnologies.meetingmind.core.faith.ReminderSettings> = userPrefs.reminderSettings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.craftflowtechnologies.meetingmind.core.faith.ReminderSettings()
    )

    fun setReminders(settings: com.craftflowtechnologies.meetingmind.core.faith.ReminderSettings) {
        viewModelScope.launch { userPrefs.setReminderSettings(settings) }
    }

    fun updateWork(change: (com.craftflowtechnologies.meetingmind.core.work.WorkSettings) -> com.craftflowtechnologies.meetingmind.core.work.WorkSettings) {
        viewModelScope.launch { userPrefs.updateWorkSettings(change) }
    }

    private val pushStore = com.craftflowtechnologies.meetingmind.core.circles2.SharedPrefsPushTokenStore(application)
    private val _circlesPush = kotlinx.coroutines.flow.MutableStateFlow(pushStore.wantsPush)

    /** Whether this phone asks for Circles push messages (the same switch Circles itself uses). */
    val circlesPush: StateFlow<Boolean> = _circlesPush

    fun setCirclesPush(on: Boolean) {
        _circlesPush.value = on
        viewModelScope.launch {
            val tokens = com.craftflowtechnologies.meetingmind.core.circles2.Circles2.pushTokens(getApplication())
            if (on) tokens.activate() else tokens.deactivate()
        }
    }

    fun setBibleVersion(id: Int) {
        viewModelScope.launch { userPrefs.setBibleVersionId(id) }
    }

    fun setCalendar(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setCalendarEnabled(enabled) }
    }

    fun setFaithLock(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setFaithLockEnabled(enabled) }
    }

    fun setHomeHeroDisplayMode(mode: com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode) {
        viewModelScope.launch { userPrefs.setHomeHeroDisplayMode(mode) }
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

/** Where Settings is showing. Kept as a string so it survives rotation; see [encode] and [parsePage]. */
internal sealed interface SettingsPage {
    data object Hub : SettingsPage
    data class Group(val group: SettingsGroup, val focus: SettingId? = null) : SettingsPage
    data class Notifications(val focus: SettingId? = null) : SettingsPage
    data class Devotional(val startPage: String?) : SettingsPage
    data object Reminders : SettingsPage
}

internal fun SettingsPage.encode(): String = when (this) {
    SettingsPage.Hub -> "hub"
    is SettingsPage.Group -> "group:${group.key}:${focus?.name.orEmpty()}"
    is SettingsPage.Notifications -> "notifications:${focus?.name.orEmpty()}"
    is SettingsPage.Devotional -> "devotional:${startPage.orEmpty()}"
    SettingsPage.Reminders -> "reminders"
}

internal fun parsePage(raw: String): SettingsPage {
    val p = raw.split(":")
    fun id(i: Int) = p.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { n -> SettingId.entries.firstOrNull { it.name == n } }
    return when (p[0]) {
        "group" -> SettingsGroup.fromKey(p.getOrNull(1))?.let { SettingsPage.Group(it, id(2)) } ?: SettingsPage.Hub
        "notifications" -> SettingsPage.Notifications(id(1))
        "devotional" -> SettingsPage.Devotional(p.getOrNull(1)?.takeIf { it.isNotBlank() })
        "reminders" -> SettingsPage.Reminders
        else -> SettingsPage.Hub
    }
}

private fun SettingsPage.parent(): SettingsPage = when (this) {
    SettingsPage.Hub -> SettingsPage.Hub
    is SettingsPage.Group -> SettingsPage.Hub
    is SettingsPage.Notifications -> SettingsPage.Group(SettingsGroup.ACCOUNT, SettingId.NOTIFICATIONS)
    is SettingsPage.Devotional -> SettingsPage.Group(SettingsGroup.FAITH, SettingId.DEVOTIONAL)
    SettingsPage.Reminders -> SettingsPage.Group(SettingsGroup.FAITH, SettingId.PRAYER_REMINDERS)
}

/** What the rows need from the screen: the dialogs it owns and the places it can go. */
private class SettingsActions(
    val onNavigateToModels: () -> Unit,
    val onOpenBible: () -> Unit,
    val onOpenSetup: () -> Unit,
    val onOpenDataBackup: () -> Unit,
    val onOpenAppearance: () -> Unit,
    val onOpenWork: () -> Unit,
    val onOpenIntegrations: () -> Unit,
    val onOpenRecipes: () -> Unit,
    val onOpenCompanion: () -> Unit,
    val onReplayTour: () -> Unit,
    val editName: () -> Unit,
    val pickAvatar: () -> Unit,
    val showSpaces: () -> Unit,
    val showLook: () -> Unit,
    val showBibleVersions: () -> Unit,
    val showCrash: () -> Unit,
    val askClearData: () -> Unit,
    val setCalendar: (Boolean) -> Unit,
    val setCirclesPush: (Boolean) -> Unit,
    val open: (SettingsPage) -> Unit
)

private class SettingsEnv(
    val vm: SettingsViewModel,
    val prefs: AppPreferencesState,
    val work: com.craftflowtechnologies.meetingmind.core.work.WorkSettings,
    val devotional: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile,
    val reminders: com.craftflowtechnologies.meetingmind.core.faith.ReminderSettings,
    val circlesPush: Boolean,
    val geminiKeyDisplay: String?,
    val calendarGranted: Boolean,
    val bibleVersions: List<com.craftflowtechnologies.meetingmind.core.scripture.BibleInfo>,
    val crashPresent: Boolean,
    val actions: SettingsActions
) {
    val spaces get() = prefs.identity.spaces

    /** Whether a setting exists for this person right now. Faith is always available: it hides, but search finds it. */
    fun available(entry: SettingEntry): Boolean = when (entry.needs) {
        Needs.NOTHING, Needs.FAITH_SPACE -> true
        Needs.WORK_SPACE -> com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK in spaces
        Needs.FEATURE_RECIPES -> BuildConfig.FEATURE_RECIPES
        Needs.CRASH_REPORT -> crashPresent
        Needs.DEBUG_BUILD -> BuildConfig.DEBUG
    }

    /** Whether to list a setting when browsing (not searching). */
    fun listed(entry: SettingEntry): Boolean = available(entry) && (entry.needs != Needs.FAITH_SPACE || prefs.identity.showsFaith)
}

/**
 * Settings: a search field and eight groups. A group shows its few most-used rows first and the rest under
 * Advanced. Every setting is in exactly one place ([SettingsRegistry]); nothing here is a Simple/Advanced mode.
 *
 * [startGroup] opens a group directly (a [SettingsGroup.key]); the route into this screen is unchanged.
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
    onOpenIntegrations: () -> Unit = {},
    onOpenRecipes: () -> Unit = {},
    onOpenCompanion: () -> Unit = {},
    onReplayTour: () -> Unit = {},
    startGroup: String? = null
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesState.collectAsState()
    val geminiKeyDisplay by viewModel.geminiKeyDisplay.collectAsState()
    val work by viewModel.workSettings.collectAsState()
    val devotional by viewModel.devotional.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val circlesPush by viewModel.circlesPush.collectAsState()
    var pageKey by rememberSaveable { mutableStateOf(startGroup?.let { "group:$it:" } ?: "hub") }
    val page = parsePage(pageKey)
    val go: (SettingsPage) -> Unit = { pageKey = it.encode() }
    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.Hub) { go(page.parent()) }

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSpaces by remember { mutableStateOf(false) }
    var showCrashReport by remember { mutableStateOf(false) }
    var crashReport by remember { mutableStateOf(com.craftflowtechnologies.meetingmind.core.diagnostics.CrashLog.pending(context)) }
    var showLook by remember { mutableStateOf(false) }
    var showBibleVersions by remember { mutableStateOf(false) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setAvatar(uri) }
    var calendarGranted by remember { mutableStateOf(com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvents(context).hasPermission()) }
    val calendarPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> calendarGranted = granted; if (granted) viewModel.setCalendar(true) }
    val askNotifications = com.craftflowtechnologies.meetingmind.feature.circles2.rememberNotificationAsk()
    // The translations licensed to this app, as the service reports them.
    var bibleVersions by remember { mutableStateOf<List<com.craftflowtechnologies.meetingmind.core.scripture.BibleInfo>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        bibleVersions = runCatching { com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService.library(context).bibles() }.getOrDefault(emptyList())
    }

    val actions = SettingsActions(
        onNavigateToModels, onOpenBible, onOpenSetup, onOpenDataBackup, onOpenAppearance, onOpenWork, onOpenIntegrations,
        onOpenRecipes, onOpenCompanion, onReplayTour,
        editName = { showEditNameDialog = true },
        pickAvatar = { avatarPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        showSpaces = { showSpaces = true },
        showLook = { showLook = true },
        showBibleVersions = { showBibleVersions = true },
        showCrash = { showCrashReport = true },
        askClearData = { showClearDataDialog = true },
        setCalendar = { on ->
            if (!on) viewModel.setCalendar(false)
            else if (calendarGranted) viewModel.setCalendar(true)
            else calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
        },
        setCirclesPush = { on -> if (on) askNotifications { viewModel.setCirclesPush(true) } else viewModel.setCirclesPush(false) },
        open = go
    )
    val env = SettingsEnv(viewModel, prefs, work, devotional, reminders, circlesPush, geminiKeyDisplay, calendarGranted, bibleVersions, crashReport != null, actions)

    if (showSpaces) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSpaces = false }, containerColor = MM.colors.surfaceRaised) {
            Column(Modifier.padding(horizontal = MM.space.l).padding(bottom = MM.space.xl)) {
                Text("What's MeetingMind for you?", style = MM.type.title, color = MM.colors.ink)
                Text("Only these spaces show in Home, Notes and Record.", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs, bottom = MM.space.m))
                com.craftflowtechnologies.meetingmind.core.identity.SpacesPicker(prefs.identity.spaces) { viewModel.setSpaces(it) }
            }
        }
    }
    if (showLook) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showLook = false }, containerColor = MM.colors.surfaceRaised) {
            Column(Modifier.padding(horizontal = MM.space.l).padding(bottom = MM.space.xl)) {
                Text("Look & feel", style = MM.type.title, color = MM.colors.ink, modifier = Modifier.padding(bottom = MM.space.m))
                com.craftflowtechnologies.meetingmind.core.identity.LookPicker(prefs.identity.look) { viewModel.setLook(it) }
                Text("Cards", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.padding(top = MM.space.xl, bottom = MM.space.s))
                com.craftflowtechnologies.meetingmind.core.identity.CardStyle.entries.forEach { style ->
                    SettingsRadioRow(style.label, style.description, prefs.identity.cardStyle == style, { viewModel.setCardStyle(style) }, "settings_card_${style.name.lowercase()}")
                }
            }
        }
    }
    if (showBibleVersions) {
        AlertDialog(
            onDismissRequest = { showBibleVersions = false },
            containerColor = MM.colors.surfaceRaised,
            title = { Text("Bible translation") },
            text = {
                Column {
                    if (bibleVersions.isEmpty()) Text("Connect to the internet to see the translations available.")
                    bibleVersions.forEach { v ->
                        SettingsRadioRow(v.abbreviation, v.title, v.id == prefs.bibleVersionId, { viewModel.setBibleVersion(v.id); showBibleVersions = false }, "settings_bible_${v.id}")
                    }
                    Text(
                        "More translations appear here as they become available for this app.",
                        style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.s)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showBibleVersions = false }) { Text("Done") } }
        )
    }

    Scaffold(
        containerColor = MM.colors.background,
        bottomBar = {
            if (page == SettingsPage.Hub) com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                current = com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SETTINGS,
                onNavigate = onNavigateBottomNav,
                showNewAction = true
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (page) {
                SettingsPage.Hub -> SettingsHub(env, onNavigateBack)
                is SettingsPage.Group -> SettingsGroupPage(page, env) { go(SettingsPage.Hub) }
                is SettingsPage.Notifications -> SettingsNotificationsPage(page, env) { go(page.parent()) }
                is SettingsPage.Devotional -> com.craftflowtechnologies.meetingmind.feature.devotional.DevotionalSettingsScreen(page.startPage) { go(page.parent()) }
                SettingsPage.Reminders -> SettingsRemindersPage(env) { go(page.parent()) }
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
                        style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(bottom = MM.space.m)
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
                    onClick = { viewModel.setUserName(draftName); showEditNameDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MM.colors.ink),
                    modifier = Modifier.testTag("settings_name_save_btn")
                ) { Text("Save", color = MM.colors.onInk) }
            },
            dismissButton = { TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel", color = MM.colors.inkSecondary) } }
        )
    }

    crashReport?.let { report ->
        if (showCrashReport) {
            CrashReportDialog(
                report = report,
                onClear = {
                    com.craftflowtechnologies.meetingmind.core.diagnostics.CrashLog.clear(context)
                    crashReport = null; showCrashReport = false
                },
                onClose = { showCrashReport = false }
            )
        }
    }
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Delete all meetings and audio?") },
            text = { Text("This permanently deletes every meeting on this phone: its audio recording, transcript, summary and action items. Your notes, tasks and Faith content are kept. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        viewModel.clearAllData {
                            Toast.makeText(context, "All meetings and audio deleted", Toast.LENGTH_SHORT).show()
                            go(SettingsPage.Hub)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MM.colors.danger)
                ) { Text("Delete all", color = MM.colors.onInk) }
            },
            dismissButton = { TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel", color = MM.colors.inkSecondary) } }
        )
    }
}

// ───────────────────────────── Top level ─────────────────────────────

@Composable
private fun SettingsHub(env: SettingsEnv, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, env.spaces, env.crashPresent) { SettingsRegistry.search(query, env::available) }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_hub"), contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s)) {
        item {
            ScreenHeader(
                "Settings", Modifier.statusBarsPadding(),
                leading = { IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.inkSecondary) } }
            )
        }
        item { SettingsSearchField(query) { query = it } }
        if (query.isBlank()) {
            item {
                MMCard(contentPadding = MM.space.xs) {
                    SettingsRegistry.groups.filter { it != SettingsGroup.FAITH || env.prefs.identity.showsFaith }.forEachIndexed { i, group ->
                        if (i > 0) SettingsDivider()
                        ListRow(
                            title = group.title, subtitle = group.summary,
                            modifier = Modifier.padding(horizontal = MM.space.m).testTag("settings_group_${group.key}"),
                            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MM.colors.inkMuted) },
                            onClick = { env.actions.open(SettingsPage.Group(group)) }
                        )
                    }
                }
            }
            item { VersionLine() }
        } else if (hits.isEmpty()) {
            item { Text("Nothing matches \"${query.trim()}\".", style = MM.type.body, color = MM.colors.inkSecondary, modifier = Modifier.padding(vertical = MM.space.l)) }
        } else {
            items(hits, key = { it.key }) { hit ->
                ListRow(
                    title = hit.title,
                    subtitle = hit.group.title + if (hit.place == Place.NOTIFICATIONS) " › Notifications" else if (hit.advanced) " › Advanced" else "",
                    modifier = Modifier.testTag("settings_hit_${hit.key}"),
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MM.colors.inkMuted) },
                    onClick = {
                        env.actions.open(if (hit.place == Place.NOTIFICATIONS) SettingsPage.Notifications(hit.id) else SettingsPage.Group(hit.group, hit.id))
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onChange: (String) -> Unit) {
    InsetPanel(Modifier.padding(vertical = MM.space.m)) {
        Row(Modifier.heightIn(min = MMSize.minTouch - MM.space.l), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MM.colors.inkMuted)
            Box(Modifier.weight(1f).padding(horizontal = MM.space.s)) {
                if (query.isEmpty()) Text("Search settings", style = MM.type.body, color = MM.colors.inkMuted)
                androidx.compose.foundation.text.BasicTextField(
                    value = query, onValueChange = onChange, singleLine = true,
                    textStyle = MM.type.body.copy(color = MM.colors.ink),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MM.colors.accent),
                    modifier = Modifier.fillMaxWidth().testTag("settings_search")
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear search", tint = MM.colors.inkMuted) }
        }
    }
}

@Composable
private fun VersionLine() {
    Text(
        "MeetingMind ${BuildConfig.VERSION_NAME}",
        style = MM.type.caption, color = MM.colors.inkMuted,
        modifier = Modifier.fillMaxWidth().padding(top = MM.space.xl),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

// ───────────────────────────── A group ─────────────────────────────

@Composable
private fun SettingsGroupPage(page: SettingsPage.Group, env: SettingsEnv, onBack: () -> Unit) {
    val group = page.group
    val rows = remember(group, env.spaces, env.crashPresent) { SettingsRegistry.inGroup(group).filter(env::listed) }
    val primary = rows.filter { !it.advanced }
    val advanced = rows.filter { it.advanced }
    var expanded by rememberSaveable(group) { mutableStateOf(page.focus != null && advanced.any { it.id == page.focus }) }
    var highlight by remember(page.focus) { mutableStateOf(page.focus) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(page.focus) {
        val focus = page.focus ?: return@LaunchedEffect
        val at = primary.indexOfFirst { it.id == focus }.takeIf { it >= 0 }?.let { it + 1 }
            ?: advanced.indexOfFirst { it.id == focus }.takeIf { it >= 0 }?.let { 1 + primary.size + 1 + it }
        if (at != null) listState.animateScrollToItem(at)
        kotlinx.coroutines.delay(1_800)
        highlight = null
    }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_group_page"), state = listState, contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s)) {
        item {
            ScreenHeader(
                group.title, Modifier.statusBarsPadding(),
                leading = { IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.inkSecondary) } }
            )
        }
        items(primary, key = { it.key }) { entry -> SettingBlock(entry, highlight == entry.id, env) }
        if (advanced.isNotEmpty()) {
            item(key = "advanced") { AdvancedToggle(expanded) { expanded = !expanded } }
            if (expanded) items(advanced, key = { it.key }) { entry -> SettingBlock(entry, highlight == entry.id, env) }
        }
        if (group == SettingsGroup.ABOUT) item { VersionLine() }
    }
}

@Composable
private fun SettingBlock(entry: SettingEntry, highlighted: Boolean, env: SettingsEnv) {
    val wash = if (highlighted) MM.colors.accentWash else androidx.compose.ui.graphics.Color.Transparent
    Column(Modifier.fillMaxWidth().background(wash, MM.radius.small).testTag("setting_${entry.key}")) {
        SettingBody(entry.id, env)
        SettingsDivider()
    }
}

/** Draws one setting. Exhaustive: adding a [SettingId] without drawing it fails to compile. */
@Composable
private fun SettingBody(id: SettingId, env: SettingsEnv) {
    val vm = env.vm
    val prefs = env.prefs
    val a = env.actions
    val context = LocalContext.current
    when (id) {
        SettingId.PROFILE -> Row(Modifier.fillMaxWidth().padding(vertical = MM.space.m), verticalAlignment = Alignment.CenterVertically) {
            com.craftflowtechnologies.meetingmind.core.identity.Avatar(prefs.identity, 56.dp, Modifier.testTag("settings_avatar")) { a.pickAvatar() }
            Column(Modifier.weight(1f).padding(start = MM.space.m).clickable(role = Role.Button, onClick = a.editName).testTag("settings_name_row")) {
                Text(prefs.userName?.takeIf { it.isNotBlank() } ?: "Add your name", style = MM.type.heading, color = MM.colors.ink)
                Text("Tap the photo to change it. Your name is used to greet you and in Ask AI.", style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
            if (prefs.identity.avatarPath != null) TextAction("Remove", { vm.removeAvatar() })
        }
        SettingId.SPACES -> SettingsNavRow("What it's for", prefs.identity.spaces.sortedBy { it.ordinal }.joinToString(" · ") { it.displayName }, a.showSpaces)
        SettingId.NOTIFICATIONS -> SettingsNavRow("Notifications", "Devotional, prayer, Circles, recording", { a.open(SettingsPage.Notifications()) })
        SettingId.WORK -> SettingsNavRow(
            "Work",
            listOfNotNull(env.work.profile.label, env.work.terms.organisation + " · " + env.work.terms.project, "on this phone only".takeIf { env.work.keepOnDevice }).joinToString(" · "),
            a.onOpenWork
        )
        SettingId.INTEGRATIONS -> SettingsNavRow("Integrations", "Calendar, storage, email and sharing", a.onOpenIntegrations)

        SettingId.TIDY_FILLER -> SettingsSwitchRow(
            "Tidy up filler words",
            "Hides \"uh\" and \"um\" when reading. The transcript is always stored word for word, so turning this off brings them straight back.",
            prefs.cleanFillerWords, { vm.toggleCleanFillerWords(it) }, "settings_clean_filler_switch"
        )
        SettingId.TRANSCRIPT_CLEANUP -> SettingsChoiceBlock("Transcript cleanup") {
            val mode = prefs.transcriptCleanupMode
            com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.entries.forEach { m ->
                SettingsRadioRow(
                    m.name.lowercase().replaceFirstChar { it.uppercase() },
                    when (m) {
                        com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.CONSERVATIVE -> "Light cleanup. Keeps your original wording."
                        com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.MODERATE -> "Balances readability with your original wording."
                        com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupMode.AGGRESSIVE -> "The most polished transcript. May change wording more."
                    },
                    mode == m, { vm.setTranscriptCleanupMode(m) }, "settings_cleanup_mode_${m.name.lowercase()}"
                )
            }
        }
        SettingId.SPEAKER_DETECTION -> SettingsChoiceBlock("Speaker detection") {
            com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.entries.forEach { s ->
                SettingsRadioRow(
                    when (s) {
                        com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AUTO -> "Automatic"
                        com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.DETERMINISTIC -> "Deterministic"
                        com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AI_ASSISTED -> "AI-assisted"
                    },
                    when (s) {
                        com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AUTO -> "MeetingMind chooses the best approach."
                        com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.DETERMINISTIC -> "Uses the local speaker detection engine."
                        com.craftflowtechnologies.meetingmind.core.model.DiarizationStrategy.AI_ASSISTED -> "Uses local AI to help resolve difficult speaker assignments."
                    },
                    prefs.diarizationStrategy == s, { vm.setDiarizationStrategy(s) }, "settings_diarization_${s.name.lowercase()}"
                )
            }
        }
        SettingId.RECIPES -> SettingsNavRow("Recipes & automation", "Post-meeting actions, tasks and review triggers", a.onOpenRecipes)

        SettingId.SETUP_GUIDE -> SettingsNavRow("Setup guide", "What the app needs to transcribe and summarise, and one tap to get it", a.onOpenSetup)
        SettingId.AI_ENGINE -> SettingsNavRow("AI Engine", "On-device models: download, pause, resume or remove", a.onNavigateToModels)
        SettingId.GEMINI_KEY -> GeminiApiKeyRow(env.geminiKeyDisplay, { vm.setGeminiApiKey(it) }, { vm.clearGeminiApiKey() })
        SettingId.WIFI_ONLY -> SettingsSwitchRow(
            "Download models over Wi-Fi only",
            "Off by default, so model downloads may use mobile data. Turn this on on a limited plan.",
            prefs.wifiOnlyDownload, { vm.toggleWifiOnly(it) }, "settings_wifi_only_switch"
        )
        SettingId.GEMINI_CHECK -> {
            val check by vm.keyCheck.collectAsState()
            val checking by vm.checking.collectAsState()
            GeminiKeyCheck(check, checking, onCheck = { vm.checkGeminiKey() })
        }
        SettingId.DEEPSEEK -> DeepSeekRow()

        SettingId.BIBLE_TRANSLATION -> {
            val version = env.bibleVersions.firstOrNull { it.id == prefs.bibleVersionId }
                ?.let { "${it.title} (${it.abbreviation})" }
                ?: com.craftflowtechnologies.meetingmind.core.scripture.BibleVersions.common.firstOrNull { it.id == prefs.bibleVersionId }?.let { "${it.title} (${it.abbreviation})" }
                ?: "Translation ${prefs.bibleVersionId}"
            SettingsNavRow("Bible translation", version, a.showBibleVersions)
        }
        SettingId.BIBLE_OFFLINE -> SettingsNavRow("Bible on this phone", "Read and search offline. Download an open translation from the Bible's translation menu.", a.onOpenBible)
        SettingId.DEVOTIONAL -> SettingsNavRow(
            "Devotional time & style",
            (if (env.devotional.enabled) "Every morning at %d:%02d".format(env.devotional.deliveryMinutes / 60, env.devotional.deliveryMinutes % 60) else "When you open it") +
                " · " + env.devotional.tradition.label,
            { a.open(SettingsPage.Devotional(null)) }
        )
        SettingId.PRAYER_REMINDERS -> SettingsNavRow("Prayer reminders", reminderSummary(env), { a.open(SettingsPage.Reminders) })
        SettingId.ORIGINAL_PACKS -> Column {
            OriginalsPack.entries.forEach { pack ->
                com.craftflowtechnologies.meetingmind.feature.bible.OriginalsPackRow(pack) { title, subtitle, onClick -> SettingsNavRow(title, subtitle, onClick) }
            }
        }
        SettingId.PERSONAL_TOUCH -> SettingsNavRow(
            "Personal touch", env.devotional.personalTouch.let { "${it.label} · ${it.description}" },
            { a.open(SettingsPage.Devotional("PERSONAL")) }
        )
        SettingId.FAITH_LOCK -> SettingsSwitchRow(
            "Lock Faith notes",
            "Prayers, reflections and the Faith space need your fingerprint, face or screen lock to open.",
            prefs.faithLockEnabled, { vm.setFaithLock(it) }, "settings_faith_lock"
        )

        SettingId.LOOK_AND_HOME -> SettingsNavRow("Look and home", "Light or dark, colour, text size, and which home you see", a.onOpenAppearance)
        SettingId.COMPANION -> {
            val companion by com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionSettings()
            val form = companion.form
            SettingsNavRow(
                stringResource(R.string.companion_settings_title),
                if (form == null) stringResource(R.string.companion_settings_row_subtitle_none)
                else stringResource(
                    R.string.companion_settings_row_subtitle,
                    companion.name ?: stringResource(com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster.displayName(form)),
                    stringResource(when (companion.presence) {
                        com.craftflowtechnologies.meetingmind.core.companion.Presence.AROUND -> R.string.companion_presence_around
                        com.craftflowtechnologies.meetingmind.core.companion.Presence.MOMENTS -> R.string.companion_presence_moments_long
                        com.craftflowtechnologies.meetingmind.core.companion.Presence.OFF -> R.string.companion_presence_off
                    })
                ),
                a.onOpenCompanion
            )
        }
        SettingId.HERO_MODE -> SettingsChoiceBlock("Home hero card") {
            com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode.entries.forEach { mode ->
                SettingsRadioRow(
                    when (mode) {
                        com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode.DAILY_SCRIPTURE -> "Daily scripture"
                        com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode.DAILY_QUOTE -> "Daily quote"
                        com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode.STATS -> "Progress stats & streak"
                    },
                    when (mode) {
                        com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode.DAILY_SCRIPTURE -> "An inspiring scripture banner in the Home sky card"
                        com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode.DAILY_QUOTE -> "A wisdom and faith quote in the Home sky card"
                        com.craftflowtechnologies.meetingmind.core.datastore.HomeHeroDisplayMode.STATS -> "Your streak and notes count"
                    },
                    prefs.homeHeroDisplayMode == mode, { vm.setHomeHeroDisplayMode(mode) }, "settings_hero_${mode.name.lowercase()}"
                )
            }
        }
        SettingId.LOOK_FEEL -> SettingsNavRow("Look & feel", prefs.identity.look.label + " — " + prefs.identity.look.description, a.showLook)
        SettingId.CALENDAR_ON_HOME -> SettingsSwitchRow(
            "Upcoming events on Home",
            if (prefs.calendarEnabled && !env.calendarGranted) "Allow calendar access to show them. Tap to ask again."
            else "Today's meetings and services from your phone's calendars, ready to record. Read only; nothing leaves the phone.",
            prefs.calendarEnabled && env.calendarGranted, a.setCalendar, "settings_calendar"
        )

        SettingId.APP_LOCK -> AppLockSettingRow(enabled = prefs.appLockEnabled)
        SettingId.ON_DEVICE_ONLY -> SettingsSwitchRow(
            "On-device only",
            if (prefs.processingProfile.requiresNetwork) "Off. Recordings are uploaded to Google's AI services for transcription and analysis; results are stored on this phone."
            else "On. Audio and transcripts never leave this phone: transcription, speakers, summaries and search all run on its own processor.",
            !prefs.processingProfile.requiresNetwork,
            { onDeviceOnly ->
                vm.setProcessingProfile(
                    if (onDeviceOnly) com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.OFFLINE
                    else com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.INTERNET
                )
            },
            "settings_on_device_only"
        )

        SettingId.DATA_BACKUP -> SettingsNavRow("Data & backup", "Back up, restore, export everything as Markdown, and the Trash", a.onOpenDataBackup)
        SettingId.CLEAR_DATA -> SettingsInfoText(
            "Delete all meetings & audio", "Removes every meeting, recording and transcript on this phone. Notes and tasks are kept.", danger = true, onClick = a.askClearData
        )

        SettingId.REPLAY_TOUR -> SettingsNavRow("Show me around again", "Replay the quick tour of Home", a.onReplayTour)
        SettingId.SHARE_APP -> SettingsNavRow("Share MeetingMind", "Invite friends, colleagues or study partners to try it", {
            val send = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, "Hey! I'm testing MeetingMind — the intelligent offline-first app for faith and work meetings. Check it out here: https://github.com/creativesites/MeetingMind/releases")
                type = "text/plain"
            }
            context.startActivity(android.content.Intent.createChooser(send, "Share MeetingMind"))
        })
        SettingId.CRASH_REPORT -> SettingsNavRow("Last crash report", "The app closed unexpectedly. You can copy the report to share it.", a.showCrash)
        SettingId.DIAGNOSTIC_LOG -> GeminiLogRow()
        SettingId.AUDIO_BENCHMARK -> if (BuildConfig.DEBUG) AudioBenchmarkRow()

        // Notification toggles are drawn by the Notifications screen.
        SettingId.NOTIF_DEVOTIONAL, SettingId.NOTIF_EXAMEN, SettingId.NOTIF_PRAYER_TIMES, SettingId.NOTIF_READING,
        SettingId.NOTIF_EVENING_REFLECTION, SettingId.NOTIF_MEETING_PREP, SettingId.NOTIF_CIRCLES,
        SettingId.NOTIF_WORK_MORNING, SettingId.NOTIF_WORK_PREP, SettingId.NOTIF_WORK_START, SettingId.NOTIF_WORK_WEEKLY,
        SettingId.NOTIF_RECORDING, SettingId.NOTIF_DOWNLOADS -> NotificationToggle(id, env)
    }
}

@Composable
private fun SettingsInfoText(title: String, subtitle: String, danger: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch).clickable(role = Role.Button, onClick = onClick).padding(vertical = MM.space.m)) {
        Text(title, style = MM.type.bodyStrong, color = if (danger) MM.colors.danger else MM.colors.ink)
        Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
    }
}

private fun reminderSummary(env: SettingsEnv): String {
    val r = env.reminders
    val parts = listOfNotNull(
        r.prayerTimes.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() }.takeIf { it.isNotBlank() }?.let { "Prayer: $it" },
        "reading".takeIf { r.readingNudge }, "evening reflection".takeIf { r.eveningReflection }, "before meetings".takeIf { r.meetingPrep }
    )
    return parts.joinToString(" · ").ifBlank { "None on. Pick the gentle nudges you want." }
}

// ───────────────────────────── Notifications ─────────────────────────────

@Composable
private fun SettingsNotificationsPage(page: SettingsPage.Notifications, env: SettingsEnv, onBack: () -> Unit) {
    val rows = remember(env.spaces) { SettingsRegistry.notifications().filter { env.listed(it) || it.id == page.focus } }
    var highlight by remember(page.focus) { mutableStateOf(page.focus) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(page.focus) {
        val focus = page.focus ?: return@LaunchedEffect
        rows.indexOfFirst { it.id == focus }.takeIf { it >= 0 }?.let { listState.animateScrollToItem(it + 2) }
        kotlinx.coroutines.delay(1_800)
        highlight = null
    }
    LazyColumn(Modifier.fillMaxSize().testTag("settings_notifications"), state = listState, contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.s)) {
        item {
            ScreenHeader(
                "Notifications", Modifier.statusBarsPadding(),
                leading = { IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.inkSecondary) } }
            )
        }
        item { Text("Every kind of notification MeetingMind can send, and only the ones you turn on.", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(bottom = MM.space.s)) }
        items(rows, key = { it.key }) { entry -> SettingBlock(entry, highlight == entry.id, env) }
    }
}

@Composable
private fun NotificationToggle(id: SettingId, env: SettingsEnv) {
    val vm = env.vm
    val dev = env.devotional
    val r = env.reminders
    val w = env.work
    val context = LocalContext.current
    fun time(m: Int) = "%d:%02d".format(m / 60, m % 60)
    when (id) {
        SettingId.NOTIF_DEVOTIONAL -> SettingsSwitchRow(
            "Daily devotional", if (dev.enabled) "A gentle notification every morning at ${time(dev.deliveryMinutes)}" else "Off. It's ready when you open it.",
            dev.enabled, { vm.setDevotional(dev.copy(enabled = it)) }, "settings_notif_devotional"
        )
        SettingId.NOTIF_EXAMEN -> SettingsSwitchRow(
            "Evening Examen", "In the evening, look back on the day with God.",
            dev.eveningExamen, { vm.setDevotional(dev.copy(eveningExamen = it)) }, "settings_notif_examen"
        )
        SettingId.NOTIF_PRAYER_TIMES -> SettingsNavRow(
            "Prayer times (Daily Office)",
            r.prayerTimes.sortedBy { it.ordinal }.joinToString(" · ") { it.label }.ifBlank { "None chosen" } + ". Times and quiet hours are in Faith.",
            { env.actions.open(SettingsPage.Reminders) }
        )
        SettingId.NOTIF_READING -> SettingsSwitchRow("Today's reading", "At 7 pm if you haven't read yet", r.readingNudge, { vm.setReminders(r.copy(readingNudge = it)) }, "settings_notif_reading")
        SettingId.NOTIF_EVENING_REFLECTION -> SettingsSwitchRow("Evening reflection", "A moment at 9 pm to notice the day", r.eveningReflection, { vm.setReminders(r.copy(eveningReflection = it)) }, "settings_notif_reflection")
        SettingId.NOTIF_MEETING_PREP -> SettingsSwitchRow("Before meetings", "A moment of prayer 15 minutes before calendar events", r.meetingPrep, { vm.setReminders(r.copy(meetingPrep = it)) }, "settings_notif_meeting_prep")
        SettingId.NOTIF_CIRCLES -> SettingsSwitchRow("Circles", "Messages and activity from your circles. You can also mute one circle in its own settings.", env.circlesPush, env.actions.setCirclesPush, "settings_notif_circles")
        SettingId.NOTIF_WORK_MORNING -> SettingsSwitchRow("Work morning briefing", "Your day ahead, on working days", w.notifyMorning, { vm.updateWork { s -> s.copy(notifyMorning = it) } }, "settings_notif_work_morning")
        SettingId.NOTIF_WORK_PREP -> SettingsSwitchRow("Work meeting prep", "A short brief before each meeting", w.notifyPrep, { vm.updateWork { s -> s.copy(notifyPrep = it) } }, "settings_notif_work_prep")
        SettingId.NOTIF_WORK_START -> SettingsSwitchRow("Starting now. Record?", "Asked when a meeting begins", w.notifyStartNow, { vm.updateWork { s -> s.copy(notifyStartNow = it) } }, "settings_notif_work_start")
        SettingId.NOTIF_WORK_WEEKLY -> SettingsSwitchRow("Work weekly review", "A look back at the week", w.notifyWeekly, { vm.updateWork { s -> s.copy(notifyWeekly = it) } }, "settings_notif_work_weekly")
        SettingId.NOTIF_RECORDING -> SettingsNavRow(
            "Recording and processing", "Progress while a recording is transcribed and summarised. Sound and visibility are set in Android's settings.",
            { openChannelSettings(context, com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.CHANNEL_WORK) }
        )
        SettingId.NOTIF_DOWNLOADS -> SettingsNavRow(
            "Model downloads", "Progress while an on-device model downloads. Sound and visibility are set in Android's settings.",
            { openChannelSettings(context, com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.CHANNEL_DOWNLOADS) }
        )
        else -> Unit
    }
}

/** Android owns the switch for a foreground-service channel, so the row opens that channel's own settings. */
private fun openChannelSettings(context: android.content.Context, channelId: String) {
    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
    } else {
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
    }
    runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ───────────────────────────── Prayer reminders ─────────────────────────────

@Composable
private fun SettingsRemindersPage(env: SettingsEnv, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag("settings_reminders").verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l, vertical = MM.space.s)) {
        ScreenHeader(
            "Prayer reminders", Modifier.statusBarsPadding(),
            leading = { IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.inkSecondary) } }
        )
        Text(
            "Gentle nudges, only the ones you choose. Your daily devotional has its own time under Devotional time & style.",
            style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(bottom = MM.space.s)
        )
        com.craftflowtechnologies.meetingmind.feature.faith.RemindersBody(env.reminders) { env.vm.setReminders(it) }
        Spacer(Modifier.height(MM.space.xl))
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
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("settings_deepseek")) {
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
        }.padding(vertical = 14.dp).testTag("settings_audio_benchmark"),
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
        Modifier.fillMaxWidth().clickable { com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.attach(context); open = true }.padding(vertical = 14.dp).testTag("settings_gemini_log"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Gemini log", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("What happened on each request, with Gemini's own errors. Copy it to share when you report a problem.", fontSize = 12.5.sp, color = InkMuted)
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
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("settings_gemini_check")) {
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

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = "Gemini API key", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(
            text = if (redactedKey != null) {
                "Saved on this device: $redactedKey"
            } else {
                "Internet mode needs a Gemini API key. It stays on this phone and is never part of the app."
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
