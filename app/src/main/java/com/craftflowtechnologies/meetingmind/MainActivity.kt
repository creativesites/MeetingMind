package com.craftflowtechnologies.meetingmind

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.navigationBarsPadding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.craftflowtechnologies.meetingmind.core.applock.AppLockViewModel
import com.craftflowtechnologies.meetingmind.feature.applock.AppLockGate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
import com.craftflowtechnologies.meetingmind.core.audio.RecordingJournalEntry
import com.craftflowtechnologies.meetingmind.core.audio.RecordingJournalStore
import com.craftflowtechnologies.meetingmind.core.audio.RecordingState
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.MeetingSource
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository
import com.craftflowtechnologies.meetingmind.core.ui.MiniPlayerBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.craftflowtechnologies.meetingmind.core.datastore.AppPreferencesState
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.feature.importing.ImportScreen
import com.craftflowtechnologies.meetingmind.feature.importing.ImportViewModel
import com.craftflowtechnologies.meetingmind.feature.meetingdetail.MeetingDetailScreen
import com.craftflowtechnologies.meetingmind.feature.meetingdetail.MeetingDetailViewModel
import com.craftflowtechnologies.meetingmind.feature.models.ModelManagerScreen
import com.craftflowtechnologies.meetingmind.feature.models.ModelManagerViewModel
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.feature.onboarding.OnboardingScreen
import com.craftflowtechnologies.meetingmind.feature.onboarding.OnboardingViewModel
import com.craftflowtechnologies.meetingmind.feature.processing.ProcessingScreen
import com.craftflowtechnologies.meetingmind.feature.processing.ProcessingViewModel
import com.craftflowtechnologies.meetingmind.feature.recording.RecordingScreen
import com.craftflowtechnologies.meetingmind.feature.recording.RecordingViewModel
import com.craftflowtechnologies.meetingmind.feature.search.SearchScreen
import com.craftflowtechnologies.meetingmind.feature.search.SearchViewModel
import com.craftflowtechnologies.meetingmind.feature.settings.SettingsScreen
import com.craftflowtechnologies.meetingmind.feature.settings.SettingsViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.craftflowtechnologies.meetingmind.ui.theme.isDark
import java.net.URLDecoder

// FragmentActivity (a ComponentActivity) because BiometricPrompt needs one — see docs/APP_LOCK.md.
class MainActivity : FragmentActivity() {
    private val appLock: AppLockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.craftflowtechnologies.meetingmind.core.notify.DeepLinks.handle(intent)
        // The database opens (and migrates) before anything reads it. If it can't, the recovery
        // screen offers the data back instead of the app crashing or wiping it (PRD_M0 §4.1).
        val database = androidx.compose.runtime.mutableStateOf<com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard.OpenResult?>(null)
        fun openDatabase() {
            database.value = null
            lifecycleScope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard.open(applicationContext)
                }
                database.value = result
                if (result is com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard.OpenResult.Ok) startBackgroundSync()
            }
        }
        openDatabase()
        // Recents preview and screenshots are hidden while App Lock is on (or not yet known).
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(appLock.ready, appLock.state) { ready, state ->
                !ready || state != com.craftflowtechnologies.meetingmind.core.applock.AppLockState.Disabled
            }.collect { applyScreenPrivacy(it) }
        }
        setContent {
            // Dark unless the person chose otherwise (Settings > Appearance).
            val themePrefs by remember { UserPreferencesManager(applicationContext).preferencesFlow }.collectAsState(initial = null)
            val themeMode = runCatching { com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.valueOf(themePrefs?.themeMode ?: "DARK") }.getOrDefault(com.craftflowtechnologies.meetingmind.ui.theme.ThemeMode.DARK)
            val dark = themeMode.isDark()
            androidx.compose.runtime.LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent) else androidx.activity.SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent) else androidx.activity.SystemBarStyle.light(transparent, transparent)
                )
            }
            MeetMindTheme(darkTheme = dark, appearance = themePrefs?.appearance ?: com.craftflowtechnologies.meetingmind.ui.theme.Appearance()) {
                // Who the app is for — spaces, look, name, avatar — available to every screen.
                val identityContext = androidx.compose.ui.platform.LocalContext.current
                val identity by remember { UserPreferencesManager(identityContext).preferencesFlow }
                    .collectAsState(initial = null)
                val current = identity?.identity ?: com.craftflowtechnologies.meetingmind.core.identity.AppIdentity()
                androidx.compose.runtime.CompositionLocalProvider(
                    com.craftflowtechnologies.meetingmind.core.identity.LocalAppIdentity provides current,
                    com.craftflowtechnologies.meetingmind.core.identity.LocalAppLook provides com.craftflowtechnologies.meetingmind.core.identity.AppLook.of(current.look)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Held above the lock gate so locking never loses the back stack.
                        val navController = rememberNavController()
                        AppLockGate(appLock) {
                            when (val opened = database.value) {
                                null -> Unit
                                is com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard.OpenResult.Failed ->
                                    com.craftflowtechnologies.meetingmind.feature.settings.DatabaseRecoveryScreen(opened.message, onRetry = { openDatabase() })
                                com.craftflowtechnologies.meetingmind.core.database.DatabaseGuard.OpenResult.Ok -> MeetMindApp(navController)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Keeps the devotional's and reminders' timetables in step with their settings (survives updates). */
    private fun startBackgroundSync() {
        lifecycleScope.launch {
            runCatching {
                val profile = UserPreferencesManager(applicationContext).devotionalProfile.first()
                com.craftflowtechnologies.meetingmind.core.devotional.DevotionalScheduler.sync(applicationContext, profile)
                com.craftflowtechnologies.meetingmind.core.faith.ReminderScheduler.sync(applicationContext, UserPreferencesManager(applicationContext).reminderSettings.first())
                com.craftflowtechnologies.meetingmind.core.widget.Widgets.refresh(applicationContext)
            }
            runCatching { com.craftflowtechnologies.meetingmind.core.backup.BackupScheduler.sync(applicationContext) }
            // Anything in the Trash longer than 30 days is deleted for good.
            runCatching {
                com.craftflowtechnologies.meetingmind.core.repository.NoteRepository(applicationContext, com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase.getInstance(applicationContext)).purgeTrash()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.controller.onAppForegrounded()
    }

    override fun onStop() {
        // Rotation stops and restarts the activity too; that is not "leaving the app".
        appLock.controller.onAppBackgrounded(isChangingConfigurations)
        super.onStop()
    }

    /**
     * Keeps private content out of the recents thumbnail. Android 13+ has a switch for exactly that
     * (screenshots stay allowed); earlier versions only have FLAG_SECURE, which also blocks
     * screenshots and screen recording — applied only while App Lock is on.
     */
    private fun applyScreenPrivacy(protect: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            setRecentsScreenshotEnabled(!protect)
        } else if (protect) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // A notification tapped while the app is already open.
        com.craftflowtechnologies.meetingmind.core.notify.DeepLinks.handle(intent)
    }
}

/** The unfinished-recording check happens once per process, however often the UI is re-composed (rotation, unlocking). */
private object RecordingRecovery {
    val entry = kotlinx.coroutines.flow.MutableStateFlow<RecordingJournalEntry?>(null)
    @Volatile var checked = false
}

@Composable
fun MeetMindApp(navController: NavHostController = rememberNavController()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsManager = remember { UserPreferencesManager(context) }
    val prefsState by prefsManager.preferencesFlow.collectAsState(initial = null)

    // Request Audio & Notification permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    if (prefsState == null) return

    val startRoute = if (prefsState?.onboardingCompleted == true) Routes.HOME else Routes.ONBOARDING

    // Crash recovery (design spec §3.7): a journal left behind in RECORDING/PAUSED state means
    // the process died mid-capture rather than stopping cleanly — MeetingRecordingService clears
    // the journal on every normal stop/discard, so its mere presence in one of those two states
    // is itself the signal, checked once per app launch.
    val journalStore = remember { RecordingJournalStore(context) }
    val recoveryMeetingRepository = remember { MeetingRepository(context, MeetMindDatabase.getInstance(context)) }
    val recoveryScope = rememberCoroutineScope()
    val recoveryEntry by RecordingRecovery.entry.collectAsState()
    LaunchedEffect(Unit) {
        if (RecordingRecovery.checked) return@LaunchedEffect
        RecordingRecovery.checked = true
        val entry = journalStore.read()
        if (entry != null && (entry.state == RecordingState.RECORDING.name || entry.state == RecordingState.PAUSED.name)) {
            RecordingRecovery.entry.value = entry
        }
    }

    LaunchedEffect(Unit) { PlaybackController.ensureConnected(context) }
    // People is built from history, once, after the work schema arrives (PLAN_PROFESSIONAL.md §5.1).
    LaunchedEffect(Unit) { com.craftflowtechnologies.meetingmind.core.work.WorkStartup.run(context) }
    val playbackState by PlaybackController.state.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    // Suppress the mini-player on the detail screen for the exact recording that's playing —
    // that screen already shows full playback controls, so this would just be a duplicate.
    val isOnActiveRecordingDetail = playbackState.recordingId != null &&
        currentRoute == Routes.MEETING_DETAIL &&
        currentBackStackEntry?.arguments?.getString("meetingId") == playbackState.recordingId

    // Standard bottom-nav-style switch between the app's 4 primary destinations: reuses a saved
    // instance of the target (and saves the current one) via the app's single Home back-stack
    // entry, so repeatedly tapping tabs never piles up duplicate entries or re-navigate() is
    // treated as no-op churn — the usual popUpTo/launchSingleTop/restoreState pattern.
    var showCreateSheet by remember { mutableStateOf(false) }
    val noteRepository = remember { com.craftflowtechnologies.meetingmind.core.repository.NoteRepository(context, MeetMindDatabase.getInstance(context)) }
    val navigateToPrimary: (com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit = { destination ->
        if (destination == com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.NEW) {
            // New is an action, not one of the saved primary tabs: it opens the Create sheet
            // rather than switching destinations.
            showCreateSheet = true
        } else {
            val route = when (destination) {
                com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME -> Routes.HOME
                com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.NOTES -> Routes.NOTES
                com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SEARCH -> Routes.SEARCH
                com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SETTINGS -> Routes.SETTINGS
                com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.NEW -> Routes.HOME
            }
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val openNewNote: (openMediaPicker: Boolean) -> Unit = { media ->
        recoveryScope.launch {
            val note = noteRepository.createNote(draft = true)
            navController.navigate(Routes.noteRoute(note.id, media))
        }
    }

    // Anything Android stopped mid-way (a force-stop, an app update) is picked up again.
    LaunchedEffect(Unit) {
        com.craftflowtechnologies.meetingmind.core.notify.AppNotifications.ensureChannels(context)
        com.craftflowtechnologies.meetingmind.ai.pipeline.ProcessingScheduler.resumeInterrupted(context)
    }

    val openProcessing: (String) -> Unit = { meetingId ->
        recoveryScope.launch {
            val meeting = MeetMindDatabase.getInstance(context).meetingDao().getMeetingById(meetingId) ?: return@launch
            navController.navigate(Routes.processingRoute(meetingId, meeting.audioFilePath.orEmpty(), meeting.durationMs)) {
                launchSingleTop = true
            }
        }
    }

    // Notification taps.
    val deepLink by com.craftflowtechnologies.meetingmind.core.notify.DeepLinks.pending.collectAsState()
    LaunchedEffect(deepLink, prefsState?.onboardingCompleted) {
        if (deepLink == null || prefsState?.onboardingCompleted != true) return@LaunchedEffect
        when (val link = com.craftflowtechnologies.meetingmind.core.notify.DeepLinks.consume()) {
            is com.craftflowtechnologies.meetingmind.core.notify.DeepLink.Processing -> openProcessing(link.meetingId)
            is com.craftflowtechnologies.meetingmind.core.notify.DeepLink.Recording -> navController.navigate(Routes.meetingDetailRoute(link.meetingId))
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.Models -> navController.navigate(Routes.MODELS) { launchSingleTop = true }
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.Bible -> navController.navigate(Routes.bibleRoute()) { launchSingleTop = true }
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.Devotional -> navController.navigate(Routes.devotionalRoute()) { launchSingleTop = true }
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.PrayerList -> navController.navigate(Routes.PRAYER_LIST) { launchSingleTop = true }
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.ReadingPlans -> navController.navigate(Routes.PLANS) { launchSingleTop = true }
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.Home -> Unit
            com.craftflowtechnologies.meetingmind.core.notify.DeepLink.Tasks -> navController.navigate(Routes.TASKS) { launchSingleTop = true }
            null -> Unit
        }
    }

    val activeProcessing = com.craftflowtechnologies.meetingmind.core.ui.rememberActiveProcessing()
    val routesWithNav = setOf(Routes.HOME, Routes.NOTES, Routes.SEARCH, Routes.SETTINGS)
    val onProcessingScreen = currentRoute == Routes.PROCESSING

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = startRoute
    ) {
        // ONBOARDING
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel()
            OnboardingScreen(
                viewModel = vm,
                onFinishOnboarding = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // HOME
        composable(Routes.HOME) {
            // Home is the Today hub: the day, the calendar and the timeline (PLAN_V2 F1).
            val vm: com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel = viewModel()
            val setupVm: com.craftflowtechnologies.meetingmind.feature.setup.SetupViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            val setupState by setupVm.state.collectAsState()
            val setupSnoozedUntil by setupVm.snoozedUntil.collectAsState()
            val homeStyle = com.craftflowtechnologies.meetingmind.ui.theme.LocalAppearance.current.homeStyle
            if (homeStyle == com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle.PROFESSIONAL) com.craftflowtechnologies.meetingmind.feature.work.ProfessionalHome(
                today = vm,
                work = viewModel(viewModelStoreOwner = context as ComponentActivity),
                onRecord = { navController.navigate(Routes.RECORDING) },
                onRecordType = { navController.navigate(Routes.recordTypeRoute(it)) },
                onRecordEvent = { noteId, type, title, speakers -> navController.navigate(Routes.recordEventRoute(noteId, type, title, speakers)) },
                onNewNote = { openNewNote(false) },
                onImport = { navController.navigate(Routes.IMPORT) },
                onSearch = { navigateToPrimary(com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SEARCH) },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenProcessing = openProcessing,
                onOpenMeeting = { id, at -> navController.navigate(Routes.meetingDetailRoute(id, at)) },
                onOpenDevotional = { navController.navigate(Routes.devotionalRoute()) },
                onOpenStories = { navController.navigate(Routes.storiesRoute(it?.name)) },
                onOpenWork = { navController.navigate(Routes.WORK) },
                onOpenAll = { navController.navigate(Routes.workAllRoute(it.name)) },
                onOpenWrapUp = { id, compose -> navController.navigate(Routes.wrapUpRoute(id, compose)) },
                onOpenProject = { navController.navigate(Routes.projectRoute(it)) },
                onOpenPerson = { navController.navigate(Routes.workPersonRoute(it)) },
                onCustomize = { navController.navigate(Routes.APPEARANCE) },
                onNavigateBottomNav = navigateToPrimary
            ) else if (homeStyle != com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle.TODAY) com.craftflowtechnologies.meetingmind.feature.today.FocusHome(
                rich = homeStyle == com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle.CALM,
                onOpenStories = { navController.navigate(Routes.storiesRoute(it?.name)) },
                viewModel = vm,
                onRecord = { navController.navigate(Routes.RECORDING) },
                onNewNote = { openNewNote(false) },
                onSearch = { navigateToPrimary(com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SEARCH) },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenProcessing = openProcessing,
                onOpenDevotional = { navController.navigate(Routes.devotionalRoute()) },
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onCustomize = { navController.navigate(Routes.APPEARANCE) },
                onNavigateBottomNav = navigateToPrimary
            ) else com.craftflowtechnologies.meetingmind.feature.today.TodayScreen(
                viewModel = vm,
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenProcessing = openProcessing,
                onRecord = { navController.navigate(Routes.RECORDING) },
                onRecordType = { navController.navigate(Routes.recordTypeRoute(it)) },
                onRecordEvent = { noteId, type, title, speakers -> navController.navigate(Routes.recordEventRoute(noteId, type, title, speakers)) },
                onSearch = { navigateToPrimary(com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SEARCH) },
                onNavigateBottomNav = navigateToPrimary,
                onOpenDevotional = { navController.navigate(Routes.devotionalRoute()) },
                onOpenStories = { navController.navigate(Routes.storiesRoute(it?.name)) },
                onNewNote = { openNewNote(false) },
                onImport = { navController.navigate(Routes.IMPORT) },
                setup = setupState,
                setupSnoozed = setupSnoozedUntil > System.currentTimeMillis(),
                onSetUp = { setupVm.setUp() },
                onOpenSetup = { navController.navigate(Routes.SETUP) },
                onSnoozeSetup = { setupVm.snooze() },
                tourEnabled = true
            )
        }

        composable(Routes.SETUP) {
            val vm: com.craftflowtechnologies.meetingmind.feature.setup.SetupViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.setup.SetupScreen(
                vm, onBack = { navController.popBackStack() },
                onInternetMode = { navController.navigate(Routes.SETTINGS) },
                onAdvanced = { navController.navigate(Routes.MODELS) }
            )
        }

        // RECORDING
        composable(
            route = Routes.RECORDING_PATTERN,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("speakers") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val vm: RecordingViewModel = viewModel()
            RecordingScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onRecordingComplete = { meetingId, audioPath, durationMs ->
                    val route = Routes.processingRoute(meetingId, audioPath, durationMs)
                    navController.navigate(route) {
                        popUpTo(Routes.RECORDING_PATTERN) { inclusive = true }
                    }
                },
                targetNoteId = backStackEntry.arguments?.getString("noteId"),
                initialType = backStackEntry.arguments?.getString("type")?.let { t -> RecordingType.entries.firstOrNull { it.name == t } },
                initialTitle = backStackEntry.arguments?.getString("title")?.takeIf { it.isNotBlank() },
                initialSpeakers = backStackEntry.arguments?.getInt("speakers")?.takeIf { it > 0 },
                setupNotice = {
                    val setupVm: com.craftflowtechnologies.meetingmind.feature.setup.SetupViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
                    val setup by setupVm.state.collectAsState()
                    setup?.takeIf { !it.offlineReady && (!it.internetReady || it.thinkingOnly) }?.let { st ->
                        com.craftflowtechnologies.meetingmind.feature.setup.SetupBanner(st, onClick = { navController.navigate(Routes.SETUP) })
                    }
                }
            )
        }

        // FAITH — one view model shared by the space and its two sub-pages, all behind the optional lock.
        composable(Routes.FAITH) {
            val vm: com.craftflowtechnologies.meetingmind.feature.faith.FaithViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.faith.FaithScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                    onRecordSermon = { navController.navigate(Routes.recordTypeRoute(RecordingType.SERMON)) },
                    onOpenJourney = { navController.navigate(Routes.FAITH_JOURNEY) },
                    onOpenScripture = { navController.navigate(Routes.FAITH_SCRIPTURE) },
                    onOpenBible = { navController.navigate(Routes.bibleRoute()) },
                    onSearchBible = { navController.navigate(Routes.bibleRoute(search = true)) },
                    onReadPassage = { navController.navigate(Routes.bibleRoute(it.passageId())) },
                    onOpenDevotional = { navController.navigate(Routes.devotionalRoute()) },
                    onShare = { req -> com.craftflowtechnologies.meetingmind.feature.share.ShareRequests.pending = req; navController.navigate(Routes.SHARE) },
                    onPrayWithMe = { navController.navigate(Routes.prayRoute()) },
                    onOpenPlans = { navController.navigate(Routes.PLANS) },
                    onOpenPrayerList = { navController.navigate(Routes.PRAYER_LIST) },
                    onOpenTasks = { navController.navigate(Routes.TASKS) }
                )
            }
        }
        composable(
            Routes.STORIES,
            arguments = listOf(navArgument("start") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val vm: com.craftflowtechnologies.meetingmind.feature.stories.StoriesViewModel = viewModel()
            val start = backStackEntry.arguments?.getString("start")?.let { s -> com.craftflowtechnologies.meetingmind.feature.stories.StoryKind.entries.firstOrNull { it.name == s } }
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.stories.StoriesScreen(
                    viewModel = vm,
                    startAt = start,
                    onClose = { navController.popBackStack() },
                    onOpen = { o ->
                        navController.popBackStack()
                        when (o) {
                            com.craftflowtechnologies.meetingmind.feature.stories.StoryOpen.Devotional -> navController.navigate(Routes.devotionalRoute())
                            is com.craftflowtechnologies.meetingmind.feature.stories.StoryOpen.Note -> navController.navigate(Routes.noteRoute(o.id))
                            is com.craftflowtechnologies.meetingmind.feature.stories.StoryOpen.Recording -> navController.navigate(Routes.meetingDetailRoute(o.id))
                            is com.craftflowtechnologies.meetingmind.feature.stories.StoryOpen.Passage -> navController.navigate(Routes.bibleRoute(o.reference.passageId()))
                        }
                    },
                    onPray = { navController.popBackStack(); navController.navigate(Routes.devotionalRoute("prayer")) },
                    onShare = { story ->
                        story.share?.let { c ->
                            com.craftflowtechnologies.meetingmind.feature.share.ShareRequests.pending = com.craftflowtechnologies.meetingmind.feature.share.ShareRequest(c, theme = story.title ?: story.body.take(160), background = story.background)
                            navController.navigate(Routes.SHARE)
                        }
                    }
                )
            }
        }
        composable(Routes.PRAY, arguments = listOf(navArgument("mode") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val vm: com.craftflowtechnologies.meetingmind.feature.prayer.PrayWithMeViewModel = viewModel()
            val mode = entry.arguments?.getString("mode")?.let { m -> com.craftflowtechnologies.meetingmind.core.prayer.PrayMode.entries.firstOrNull { it.name == m } }
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.prayer.PrayWithMeScreen(
                    viewModel = vm, startMode = mode,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }
        composable(Routes.PLANS) {
            val vm: com.craftflowtechnologies.meetingmind.feature.faith.FaithExtrasViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.faith.ReadingPlansScreen(vm, onNavigateBack = { navController.popBackStack() }, onRead = { navController.navigate(Routes.bibleRoute(it.passageId())) })
        }
        composable(Routes.APPEARANCE) {
            val vm: com.craftflowtechnologies.meetingmind.feature.settings.AppearanceViewModel = viewModel()
            com.craftflowtechnologies.meetingmind.feature.settings.AppearanceScreen(vm, onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.TASKS) {
            val vm: com.craftflowtechnologies.meetingmind.feature.tasks.TasksViewModel = viewModel()
            com.craftflowtechnologies.meetingmind.feature.tasks.TasksScreen(
                vm, onNavigateBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenRecording = { id, at -> navController.navigate(Routes.meetingDetailRoute(id, at)) }
            )
        }
        composable(Routes.PRAYER_LIST) {
            val vm: com.craftflowtechnologies.meetingmind.feature.faith.FaithExtrasViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.faith.PrayerListScreen(vm, onNavigateBack = { navController.popBackStack() })
            }
        }
        composable(Routes.SHARE) {
            val request = remember { com.craftflowtechnologies.meetingmind.feature.share.ShareRequests.pending }
            if (request == null) LaunchedEffect(Unit) { navController.popBackStack() }
            else com.craftflowtechnologies.meetingmind.feature.share.ShareStudioScreen(request = request, onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Routes.STUDY,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("meeting") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("ref") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            val noteId = entry.arguments?.getString("noteId").orEmpty()
            val app = context.applicationContext as android.app.Application
            val editor: com.craftflowtechnologies.meetingmind.feature.notes.editor.NoteEditorViewModel = viewModel(
                key = "study-note-$noteId",
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        com.craftflowtechnologies.meetingmind.feature.notes.editor.NoteEditorViewModel(app, noteId) as T
                }
            )
            val study: com.craftflowtechnologies.meetingmind.feature.study.StudyWorkspaceViewModel = viewModel()
            com.craftflowtechnologies.meetingmind.feature.study.StudyWorkspaceScreen(
                editor = editor, vm = study, noteId = noteId,
                meetingId = entry.arguments?.getString("meeting"),
                initialRef = entry.arguments?.getString("ref")?.let { com.craftflowtechnologies.meetingmind.core.scripture.YouVersionScriptureProvider.parsePassageId(it) },
                onNavigateBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenRecording = { m, ms -> navController.navigate(Routes.meetingDetailRoute(m, ms)) }
            )
        }
        composable(Routes.DEVOTIONAL_ARCHIVE) {
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.devotional.DevotionalArchiveScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(Routes.pastDevotionalRoute(it)) }
                )
            }
        }
        composable(Routes.DEVOTIONAL, arguments = listOf(
            navArgument("play") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("note") { type = NavType.StringType; nullable = true; defaultValue = null }
        )) { entry ->
            val vm: com.craftflowtechnologies.meetingmind.feature.devotional.DevotionalViewModel = viewModel()
            val past = entry.arguments?.getString("note")
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.devotional.DevotionalScreen(
                    viewModel = vm,
                    pastNoteId = past,
                    onArchive = { navController.navigate(Routes.DEVOTIONAL_ARCHIVE) },
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                    onReadPassage = { navController.navigate(Routes.bibleRoute(it.passageId())) },
                    onShare = { req -> com.craftflowtechnologies.meetingmind.feature.share.ShareRequests.pending = req; navController.navigate(Routes.SHARE) },
                    playOnOpen = entry.arguments?.getString("play"),
                    onLive = { mode -> navController.navigate(Routes.prayRoute(mode.name)) }
                )
            }
        }
        composable(
            Routes.BIBLE,
            arguments = listOf(
                navArgument("ref") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("search") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val vm: com.craftflowtechnologies.meetingmind.feature.bible.BibleViewModel = viewModel()
            val ref = backStackEntry.arguments?.getString("ref")?.let { com.craftflowtechnologies.meetingmind.core.scripture.YouVersionScriptureProvider.parsePassageId(it) }
            // "Study this": pick a method, get a study note with the passage open beside it.
            var studyFor by remember { mutableStateOf<com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference?>(null) }
            val studyScope = rememberCoroutineScope()
            studyFor?.let { passage ->
                com.craftflowtechnologies.meetingmind.feature.study.StudyTemplateSheet(passage, onPick = { t ->
                    studyFor = null
                    studyScope.launch {
                        val note = com.craftflowtechnologies.meetingmind.core.repository.NoteRepository(context, com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase.getInstance(context)).startStudy(t, passage)
                        navController.navigate(Routes.studyRoute(note.id, passageId = passage.passageId()))
                    }
                }, onDismiss = { studyFor = null })
            }
            com.craftflowtechnologies.meetingmind.feature.bible.BibleScreen(
                viewModel = vm,
                initialReference = ref,
                onNavigateBack = { navController.popBackStack() },
                onStartNote = { r -> studyFor = r },
                startInSearch = backStackEntry.arguments?.getBoolean("search") == true
            )
        }
        composable(Routes.FAITH_JOURNEY) {
            val vm: com.craftflowtechnologies.meetingmind.feature.faith.FaithViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.faith.FaithJourneyScreen(vm, onNavigateBack = { navController.popBackStack() }, onOpenNote = { navController.navigate(Routes.noteRoute(it)) })
            }
        }
        composable(Routes.FAITH_SCRIPTURE) {
            val vm: com.craftflowtechnologies.meetingmind.feature.faith.FaithViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.craftflowtechnologies.meetingmind.feature.faith.FaithScriptureScreen(vm, onNavigateBack = { navController.popBackStack() }, onOpenNote = { navController.navigate(Routes.noteRoute(it)) })
            }
        }

        // NOTES
        composable(Routes.NOTES) {
            val app = context.applicationContext as android.app.Application
            val vm = remember { com.craftflowtechnologies.meetingmind.feature.notes.NotesViewModel(app, com.craftflowtechnologies.meetingmind.feature.notes.NotesScope.All) }
            com.craftflowtechnologies.meetingmind.feature.notes.NotesScreen(
                viewModel = vm,
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenNotebook = { navController.navigate(Routes.notebookRoute(it)) },
                onOpenArchive = { navController.navigate(Routes.NOTES_ARCHIVE) },
                onNavigateBack = null,
                onNavigateBottomNav = navigateToPrimary,
                onOpenFaith = { navController.navigate(Routes.FAITH) },
                onOpenWork = { navController.navigate(Routes.WORK) },
                onOpenTrash = { navController.navigate(Routes.NOTES_TRASH) }
            )
        }

        // WORK (docs/PLAN_PROFESSIONAL.md §6–7)
        composable(Routes.WORK) {
            val vm: com.craftflowtechnologies.meetingmind.feature.work.WorkViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            val today: com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel = viewModel()
            com.craftflowtechnologies.meetingmind.feature.work.WorkSpaceScreen(
                viewModel = vm, today = today,
                onNavigateBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenMeeting = { id, at -> navController.navigate(Routes.meetingDetailRoute(id, at)) },
                onRecordType = { navController.navigate(Routes.recordTypeRoute(it)) },
                onRecordEvent = { noteId, type, title, speakers -> navController.navigate(Routes.recordEventRoute(noteId, type, title, speakers)) },
                onOpenWrapUp = { id, compose -> navController.navigate(Routes.wrapUpRoute(id, compose)) },
                onOpenPerson = { navController.navigate(Routes.workPersonRoute(it)) },
                onOpenProject = { navController.navigate(Routes.projectRoute(it)) },
                onOpenAll = { navController.navigate(Routes.workAllRoute(it.name)) },
                onOpenSettings = { navController.navigate(Routes.WORK_SETTINGS) },
                onSearch = { navigateToPrimary(com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.SEARCH) }
            )
        }
        composable(Routes.WORK_ALL, arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "MINE" })) { entry ->
            val vm: com.craftflowtechnologies.meetingmind.feature.work.WorkViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.work.WorkAllScreen(
                viewModel = vm,
                initial = runCatching { com.craftflowtechnologies.meetingmind.feature.work.WorkTab.valueOf(entry.arguments?.getString("tab").orEmpty()) }.getOrDefault(com.craftflowtechnologies.meetingmind.feature.work.WorkTab.MINE),
                onNavigateBack = { navController.popBackStack() },
                onOpenMeeting = { id, at -> navController.navigate(Routes.meetingDetailRoute(id, at)) },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenPerson = { navController.navigate(Routes.workPersonRoute(it)) }
            )
        }
        composable(Routes.WORK_PERSON, arguments = listOf(navArgument("personId") { type = NavType.StringType })) { entry ->
            val vm: com.craftflowtechnologies.meetingmind.feature.work.WorkViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.work.WorkPersonScreen(
                viewModel = vm, personId = entry.arguments?.getString("personId").orEmpty(),
                onNavigateBack = { navController.popBackStack() },
                onOpenPerson = { navController.navigate(Routes.workPersonRoute(it)) },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenMeeting = { id, at -> navController.navigate(Routes.meetingDetailRoute(id, at)) }
            )
        }
        composable(Routes.PROJECT, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            val vm: com.craftflowtechnologies.meetingmind.feature.work.WorkViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.work.ProjectScreen(
                viewModel = vm, projectId = entry.arguments?.getString("projectId").orEmpty(),
                onNavigateBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenMeeting = { id, at -> navController.navigate(Routes.meetingDetailRoute(id, at)) },
                onRecordInto = { noteId, type, title -> navController.navigate(Routes.recordEventRoute(noteId, type, title, null)) }
            )
        }
        composable(Routes.WRAP_UP, arguments = listOf(
            navArgument("meetingId") { type = NavType.StringType },
            navArgument("compose") { type = NavType.BoolType; defaultValue = false }
        )) { entry ->
            val meetingId = entry.arguments?.getString("meetingId").orEmpty()
            val app = context.applicationContext as android.app.Application
            val vm = remember(meetingId) { com.craftflowtechnologies.meetingmind.feature.work.WrapUpViewModel(app, meetingId) }
            com.craftflowtechnologies.meetingmind.feature.work.WrapUpScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onDone = { noteId ->
                    navController.navigate(noteId?.let { Routes.noteRoute(it) } ?: Routes.meetingDetailRoute(meetingId)) {
                        popUpTo(Routes.WRAP_UP) { inclusive = true }
                    }
                },
                onPlay = { at -> navController.navigate(Routes.meetingDetailRoute(meetingId, at)) },
                onOpenPerson = { navController.navigate(Routes.workPersonRoute(it)) },
                startComposing = entry.arguments?.getBoolean("compose") == true
            )
        }
        composable(Routes.WORK_SETTINGS) {
            val vm: com.craftflowtechnologies.meetingmind.feature.work.WorkViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.craftflowtechnologies.meetingmind.feature.work.WorkSettingsScreen(vm, onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.DATA_BACKUP) {
            com.craftflowtechnologies.meetingmind.feature.settings.DataBackupScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenTrash = { navController.navigate(Routes.NOTES_TRASH) }
            )
        }
        composable(Routes.NOTES_TRASH) {
            val app = context.applicationContext as android.app.Application
            val vm = remember { com.craftflowtechnologies.meetingmind.feature.notes.NotesViewModel(app, com.craftflowtechnologies.meetingmind.feature.notes.NotesScope.Trash) }
            com.craftflowtechnologies.meetingmind.feature.notes.NotesScreen(
                viewModel = vm,
                onOpenNote = {},
                onOpenNotebook = {},
                onOpenArchive = {},
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = navigateToPrimary
            )
        }
        composable(Routes.NOTEBOOK, arguments = listOf(navArgument("notebookId") { type = NavType.StringType })) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getString("notebookId").orEmpty()
            val app = context.applicationContext as android.app.Application
            val vm = remember(notebookId) { com.craftflowtechnologies.meetingmind.feature.notes.NotesViewModel(app, com.craftflowtechnologies.meetingmind.feature.notes.NotesScope.InNotebook(notebookId)) }
            com.craftflowtechnologies.meetingmind.feature.notes.NotesScreen(
                viewModel = vm,
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenNotebook = { navController.navigate(Routes.notebookRoute(it)) },
                onOpenArchive = { navController.navigate(Routes.NOTES_ARCHIVE) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = navigateToPrimary
            )
        }
        composable(Routes.NOTES_ARCHIVE) {
            val app = context.applicationContext as android.app.Application
            val vm = remember { com.craftflowtechnologies.meetingmind.feature.notes.NotesViewModel(app, com.craftflowtechnologies.meetingmind.feature.notes.NotesScope.Archived) }
            com.craftflowtechnologies.meetingmind.feature.notes.NotesScreen(
                viewModel = vm,
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenNotebook = { navController.navigate(Routes.notebookRoute(it)) },
                onOpenArchive = {},
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = navigateToPrimary
            )
        }
        composable(
            Routes.NOTE,
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("media") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId").orEmpty()
            val media = backStackEntry.arguments?.getBoolean("media") ?: false
            val app = context.applicationContext as android.app.Application
            val vm: com.craftflowtechnologies.meetingmind.feature.notes.editor.NoteEditorViewModel = viewModel(
                key = "note-$noteId",
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        com.craftflowtechnologies.meetingmind.feature.notes.editor.NoteEditorViewModel(app, noteId) as T
                }
            )
            com.craftflowtechnologies.meetingmind.feature.notes.editor.NoteEditorScreen(
                viewModel = vm,
                onShareCard = { req -> com.craftflowtechnologies.meetingmind.feature.share.ShareRequests.pending = req; navController.navigate(Routes.SHARE) },
                onNavigateBack = { navController.popBackStack() },
                onOpenRecording = { meetingId, startAtMs -> navController.navigate(Routes.meetingDetailRoute(meetingId, startAtMs)) },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onRecordHere = { navController.navigate(Routes.recordIntoNoteRoute(it)) },
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                startWithMediaPicker = media
            )
        }

        // IMPORT
        composable(Routes.IMPORT) {
            val vm: ImportViewModel = viewModel()
            ImportScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onStartProcessing = { meetingId, audioPath, durationMs ->
                    val route = Routes.processingRoute(meetingId, audioPath, durationMs)
                    navController.navigate(route) {
                        popUpTo(Routes.IMPORT) { inclusive = true }
                    }
                }
            )
        }

        // PROCESSING
        composable(
            route = Routes.PROCESSING,
            arguments = listOf(
                navArgument("meetingId") { type = NavType.StringType },
                navArgument("audioPath") { type = NavType.StringType },
                navArgument("durationMs") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            val rawPath = backStackEntry.arguments?.getString("audioPath") ?: ""
            val audioPath = try { URLDecoder.decode(rawPath, "UTF-8") } catch (e: Exception) { rawPath }
            val durationMs = backStackEntry.arguments?.getLong("durationMs") ?: 0L

            val vm: ProcessingViewModel = viewModel()
            ProcessingScreen(
                viewModel = vm,
                meetingId = meetingId,
                audioPath = audioPath,
                durationMs = durationMs,
                onNavigateBack = { navController.popBackStack() },
                onProcessingComplete = { finishedMeetingId ->
                    // A work recording with findings opens its Wrap-up (PLAN_PROFESSIONAL.md §4.3);
                    // anything else opens as before.
                    recoveryScope.launch {
                        val wrapUp = com.craftflowtechnologies.meetingmind.core.work.WorkRepository(MeetMindDatabase.getInstance(context)).wantsWrapUp(finishedMeetingId)
                        navController.navigate(if (wrapUp) Routes.wrapUpRoute(finishedMeetingId) else Routes.meetingDetailRoute(finishedMeetingId)) {
                            popUpTo(Routes.PROCESSING) { inclusive = true }
                        }
                    }
                },
                onNavigateToModels = { navController.navigate(Routes.MODELS) }
            )
        }

        // MEETING DETAIL
        composable(
            route = Routes.MEETING_DETAIL,
            arguments = listOf(
                navArgument("meetingId") { type = NavType.StringType },
                navArgument("startAtMs") { type = NavType.LongType; defaultValue = Routes.NO_START_AT_MS }
            )
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            val startAtMs = backStackEntry.arguments?.getLong("startAtMs") ?: Routes.NO_START_AT_MS
            val app = context.applicationContext as android.app.Application
            val vm = remember(meetingId) { MeetingDetailViewModel(app, meetingId) }

            MeetingDetailScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Routes.MODELS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onTranscribe = { transcribeMeetingId, audioPath, durationMs ->
                    navController.navigate(Routes.processingRoute(transcribeMeetingId, audioPath, durationMs))
                },
                initialJumpToMs = startAtMs.takeIf { it != Routes.NO_START_AT_MS },
                onOpenNote = { noteId ->
                    // Came here from that note: go back to it rather than stacking a second copy.
                    if (navController.previousBackStackEntry?.arguments?.getString("noteId") == noteId) navController.popBackStack()
                    else navController.navigate(Routes.noteRoute(noteId))
                },
                onStudy = { noteId, mId -> navController.navigate(Routes.studyRoute(noteId, meetingId = mId)) }
            )
        }

        // SEARCH
        composable(Routes.SEARCH) {
            val vm: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMeeting = { meetingId, startAtMs ->
                    navController.navigate(Routes.meetingDetailRoute(meetingId, startAtMs))
                },
                onNavigateToNote = { navController.navigate(Routes.noteRoute(it)) },
                onNavigateBottomNav = navigateToPrimary,
                onOpenPassage = { navController.navigate(Routes.bibleRoute(it)) },
                onOpenTasks = { navController.navigate(Routes.TASKS) }
            )
        }

        // AI MODELS
        composable(Routes.MODELS) {
            val vm: ModelManagerViewModel = viewModel()
            ModelManagerScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateBottomNav = navigateToPrimary
            )
        }

        // SETTINGS
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Routes.MODELS) },
                onNavigateBottomNav = navigateToPrimary,
                onOpenBible = { navController.navigate(Routes.bibleRoute()) },
                onOpenSetup = { navController.navigate(Routes.SETUP) },
                onOpenDataBackup = { navController.navigate(Routes.DATA_BACKUP) },
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                onReplayTour = {
                    recoveryScope.launch {
                        com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(context).setTourCompleted(false)
                        navigateToPrimary(com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME)
                    }
                }
            )
        }
    }

    // The minimised processing screen, on the main tabs and in the notes library.
    val showPill = activeProcessing != null && !onProcessingScreen &&
        (currentRoute in routesWithNav || currentRoute == Routes.NOTEBOOK || currentRoute == Routes.MODELS)
    com.craftflowtechnologies.meetingmind.core.ui.ProcessingPill(
        active = activeProcessing.takeIf { showPill },
        onOpen = openProcessing,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = (if (currentRoute in routesWithNav) 78.dp else 16.dp) + (if (playbackState.isActive) 72.dp else 0.dp))
    )

    if (showCreateSheet) {
        com.craftflowtechnologies.meetingmind.core.ui.CreateSheet(
            onPick = { action ->
                showCreateSheet = false
                when (action) {
                    com.craftflowtechnologies.meetingmind.core.ui.CreateAction.RECORD -> navController.navigate(Routes.RECORDING)
                    com.craftflowtechnologies.meetingmind.core.ui.CreateAction.NOTE -> openNewNote(false)
                    com.craftflowtechnologies.meetingmind.core.ui.CreateAction.MEDIA -> openNewNote(true)
                    com.craftflowtechnologies.meetingmind.core.ui.CreateAction.IMPORT -> navController.navigate(Routes.IMPORT)
                }
            },
            onDismiss = { showCreateSheet = false }
        )
    }

    if (playbackState.isActive && !isOnActiveRecordingDetail) {
        MiniPlayerBar(
            state = playbackState,
            onTogglePlayPause = { PlaybackController.togglePlayPause() },
            onStop = { PlaybackController.stop() },
            onOpen = {
                playbackState.recordingId?.let { id ->
                    when {
                        id.startsWith("devotional:") -> navController.navigate(Routes.devotionalRoute()) { launchSingleTop = true }
                        id.startsWith("preview") -> Unit
                        else -> navController.navigate(Routes.meetingDetailRoute(id))
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            bottomOffset = if (currentRoute in routesWithNav) 72.dp else 0.dp
        )
    }

    recoveryEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { /* Never auto-dismiss into a silent discard — spec §3.7. Back
                gesture just closes this composition's state; the same journal is read again and
                re-prompted on the next app launch since nothing here has cleared it. */ RecordingRecovery.entry.value = null },
            title = { Text("We found an unfinished recording.") },
            text = {
                Text(
                    "MeetingMind closed unexpectedly while recording \"${entry.title}\" " +
                        "(about ${Formatters.formatDurationHms(entry.lastKnownDurationMs)} captured). " +
                        "Continuing this recording isn't supported yet, but you can save what was " +
                        "already captured or delete it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    recoveryScope.launch {
                        val recordingType = try {
                            RecordingType.valueOf(entry.recordingType)
                        } catch (e: Exception) {
                            RecordingType.GENERAL
                        }
                        recoveryMeetingRepository.createInitialMeeting(
                            id = entry.meetingId,
                            title = entry.title,
                            source = MeetingSource.LOCAL_RECORDING,
                            audioFilePath = entry.audioFilePath,
                            recordingType = recordingType
                        )
                        journalStore.clear()
                        RecordingRecovery.entry.value = null
                        navController.navigate(
                            Routes.processingRoute(entry.meetingId, entry.audioFilePath, entry.lastKnownDurationMs)
                        )
                    }
                }) { Text("Save recording") }
            },
            dismissButton = {
                TextButton(onClick = {
                    java.io.File(entry.audioFilePath).delete()
                    journalStore.clear()
                    RecordingRecovery.entry.value = null
                }) { Text("Delete") }
            }
        )
    }
    }
}
