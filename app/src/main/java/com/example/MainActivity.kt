package com.example

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.navigationBarsPadding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.example.core.audio.PlaybackController
import com.example.core.audio.RecordingJournalEntry
import com.example.core.audio.RecordingJournalStore
import com.example.core.audio.RecordingState
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.model.MeetingSource
import com.example.core.model.RecordingType
import com.example.core.repository.MeetingRepository
import com.example.core.ui.MiniPlayerBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.datastore.AppPreferencesState
import com.example.core.datastore.UserPreferencesManager
import com.example.feature.importing.ImportScreen
import com.example.feature.importing.ImportViewModel
import com.example.feature.meetingdetail.MeetingDetailScreen
import com.example.feature.meetingdetail.MeetingDetailViewModel
import com.example.feature.models.ModelManagerScreen
import com.example.feature.models.ModelManagerViewModel
import com.example.feature.navigation.Routes
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.onboarding.OnboardingViewModel
import com.example.feature.processing.ProcessingScreen
import com.example.feature.processing.ProcessingViewModel
import com.example.feature.recording.RecordingScreen
import com.example.feature.recording.RecordingViewModel
import com.example.feature.search.SearchScreen
import com.example.feature.search.SearchViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.ui.theme.MeetMindTheme
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.core.notify.DeepLinks.handle(intent)
        // Keep the daily devotional's timetable in step with its settings (survives updates).
        lifecycleScope.launch {
            runCatching {
                val profile = UserPreferencesManager(applicationContext).devotionalProfile.first()
                com.example.core.devotional.DevotionalScheduler.sync(applicationContext, profile)
            }
        }
        setContent {
            MeetMindTheme {
                // Who the app is for — spaces, look, name, avatar — available to every screen.
                val identityContext = androidx.compose.ui.platform.LocalContext.current
                val identity by remember { UserPreferencesManager(identityContext).preferencesFlow }
                    .collectAsState(initial = null)
                val current = identity?.identity ?: com.example.core.identity.AppIdentity()
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.core.identity.LocalAppIdentity provides current,
                    com.example.core.identity.LocalAppLook provides com.example.core.identity.AppLook.of(current.look)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MeetMindApp()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // A notification tapped while the app is already open.
        com.example.core.notify.DeepLinks.handle(intent)
    }
}

@Composable
fun MeetMindApp() {
    val navController = rememberNavController()
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
    var recoveryEntry by remember { mutableStateOf<RecordingJournalEntry?>(null) }
    LaunchedEffect(Unit) {
        val entry = journalStore.read()
        if (entry != null && (entry.state == RecordingState.RECORDING.name || entry.state == RecordingState.PAUSED.name)) {
            recoveryEntry = entry
        }
    }

    LaunchedEffect(Unit) { PlaybackController.ensureConnected(context) }
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
    val noteRepository = remember { com.example.core.repository.NoteRepository(context, MeetMindDatabase.getInstance(context)) }
    val navigateToPrimary: (com.example.core.ui.BottomNavDestination) -> Unit = { destination ->
        if (destination == com.example.core.ui.BottomNavDestination.NEW) {
            // New is an action, not one of the saved primary tabs: it opens the Create sheet
            // rather than switching destinations.
            showCreateSheet = true
        } else {
            val route = when (destination) {
                com.example.core.ui.BottomNavDestination.HOME -> Routes.HOME
                com.example.core.ui.BottomNavDestination.NOTES -> Routes.NOTES
                com.example.core.ui.BottomNavDestination.SEARCH -> Routes.SEARCH
                com.example.core.ui.BottomNavDestination.SETTINGS -> Routes.SETTINGS
                com.example.core.ui.BottomNavDestination.NEW -> Routes.HOME
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
        com.example.core.notify.AppNotifications.ensureChannels(context)
        com.example.ai.pipeline.ProcessingScheduler.resumeInterrupted(context)
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
    val deepLink by com.example.core.notify.DeepLinks.pending.collectAsState()
    LaunchedEffect(deepLink, prefsState?.onboardingCompleted) {
        if (deepLink == null || prefsState?.onboardingCompleted != true) return@LaunchedEffect
        when (val link = com.example.core.notify.DeepLinks.consume()) {
            is com.example.core.notify.DeepLink.Processing -> openProcessing(link.meetingId)
            is com.example.core.notify.DeepLink.Recording -> navController.navigate(Routes.meetingDetailRoute(link.meetingId))
            com.example.core.notify.DeepLink.Models -> navController.navigate(Routes.MODELS) { launchSingleTop = true }
            com.example.core.notify.DeepLink.Bible -> navController.navigate(Routes.bibleRoute()) { launchSingleTop = true }
            com.example.core.notify.DeepLink.Devotional -> navController.navigate(Routes.DEVOTIONAL) { launchSingleTop = true }
            null -> Unit
        }
    }

    val activeProcessing = com.example.core.ui.rememberActiveProcessing()
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
            val vm: com.example.feature.today.TodayViewModel = viewModel()
            com.example.feature.today.TodayScreen(
                viewModel = vm,
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenProcessing = openProcessing,
                onRecord = { navController.navigate(Routes.RECORDING) },
                onRecordType = { navController.navigate(Routes.recordTypeRoute(it)) },
                onRecordEvent = { noteId, type, title, speakers -> navController.navigate(Routes.recordEventRoute(noteId, type, title, speakers)) },
                onSearch = { navigateToPrimary(com.example.core.ui.BottomNavDestination.SEARCH) },
                onNavigateBottomNav = navigateToPrimary,
                onOpenDevotional = { navController.navigate(Routes.DEVOTIONAL) }
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
                initialSpeakers = backStackEntry.arguments?.getInt("speakers")?.takeIf { it > 0 }
            )
        }

        // FAITH — one view model shared by the space and its two sub-pages, all behind the optional lock.
        composable(Routes.FAITH) {
            val vm: com.example.feature.faith.FaithViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.example.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.example.feature.faith.FaithScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                    onRecordSermon = { navController.navigate(Routes.recordTypeRoute(RecordingType.SERMON)) },
                    onOpenJourney = { navController.navigate(Routes.FAITH_JOURNEY) },
                    onOpenScripture = { navController.navigate(Routes.FAITH_SCRIPTURE) },
                    onOpenBible = { navController.navigate(Routes.bibleRoute()) },
                    onSearchBible = { navController.navigate(Routes.bibleRoute(search = true)) },
                    onReadPassage = { navController.navigate(Routes.bibleRoute(it.passageId())) },
                    onOpenDevotional = { navController.navigate(Routes.DEVOTIONAL) }
                )
            }
        }
        composable(Routes.DEVOTIONAL) {
            val vm: com.example.feature.devotional.DevotionalViewModel = viewModel()
            com.example.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.example.feature.devotional.DevotionalScreen(
                    viewModel = vm,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                    onReadPassage = { navController.navigate(Routes.bibleRoute(it.passageId())) }
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
            val vm: com.example.feature.bible.BibleViewModel = viewModel()
            val ref = backStackEntry.arguments?.getString("ref")?.let { com.example.core.scripture.YouVersionScriptureProvider.parsePassageId(it) }
            com.example.feature.bible.BibleScreen(
                viewModel = vm,
                initialReference = ref,
                onNavigateBack = { navController.popBackStack() },
                onStartNote = { r -> vm.startDevotional(r) { navController.navigate(Routes.noteRoute(it)) } },
                startInSearch = backStackEntry.arguments?.getBoolean("search") == true
            )
        }
        composable(Routes.FAITH_JOURNEY) {
            val vm: com.example.feature.faith.FaithViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.example.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.example.feature.faith.FaithJourneyScreen(vm, onNavigateBack = { navController.popBackStack() }, onOpenNote = { navController.navigate(Routes.noteRoute(it)) })
            }
        }
        composable(Routes.FAITH_SCRIPTURE) {
            val vm: com.example.feature.faith.FaithViewModel = viewModel(viewModelStoreOwner = context as ComponentActivity)
            com.example.feature.faith.FaithLockGate(onCancel = { navController.popBackStack() }) {
                com.example.feature.faith.FaithScriptureScreen(vm, onNavigateBack = { navController.popBackStack() }, onOpenNote = { navController.navigate(Routes.noteRoute(it)) })
            }
        }

        // NOTES
        composable(Routes.NOTES) {
            val app = context.applicationContext as android.app.Application
            val vm = remember { com.example.feature.notes.NotesViewModel(app, com.example.feature.notes.NotesScope.All) }
            com.example.feature.notes.NotesScreen(
                viewModel = vm,
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onOpenNotebook = { navController.navigate(Routes.notebookRoute(it)) },
                onOpenArchive = { navController.navigate(Routes.NOTES_ARCHIVE) },
                onNavigateBack = null,
                onNavigateBottomNav = navigateToPrimary,
                onOpenFaith = { navController.navigate(Routes.FAITH) }
            )
        }
        composable(Routes.NOTEBOOK, arguments = listOf(navArgument("notebookId") { type = NavType.StringType })) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getString("notebookId").orEmpty()
            val app = context.applicationContext as android.app.Application
            val vm = remember(notebookId) { com.example.feature.notes.NotesViewModel(app, com.example.feature.notes.NotesScope.InNotebook(notebookId)) }
            com.example.feature.notes.NotesScreen(
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
            val vm = remember { com.example.feature.notes.NotesViewModel(app, com.example.feature.notes.NotesScope.Archived) }
            com.example.feature.notes.NotesScreen(
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
            val vm: com.example.feature.notes.editor.NoteEditorViewModel = viewModel(
                key = "note-$noteId",
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                        com.example.feature.notes.editor.NoteEditorViewModel(app, noteId) as T
                }
            )
            com.example.feature.notes.editor.NoteEditorScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onOpenRecording = { meetingId, startAtMs -> navController.navigate(Routes.meetingDetailRoute(meetingId, startAtMs)) },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onRecordHere = { navController.navigate(Routes.recordIntoNoteRoute(it)) },
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
                    navController.navigate(Routes.meetingDetailRoute(finishedMeetingId)) {
                        popUpTo(Routes.PROCESSING) { inclusive = true }
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
                onTranscribe = { transcribeMeetingId, audioPath, durationMs ->
                    navController.navigate(Routes.processingRoute(transcribeMeetingId, audioPath, durationMs))
                },
                initialJumpToMs = startAtMs.takeIf { it != Routes.NO_START_AT_MS },
                onOpenNote = { noteId ->
                    // Came here from that note: go back to it rather than stacking a second copy.
                    if (navController.previousBackStackEntry?.arguments?.getString("noteId") == noteId) navController.popBackStack()
                    else navController.navigate(Routes.noteRoute(noteId))
                }
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
                onNavigateBottomNav = navigateToPrimary
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
                onOpenBible = { navController.navigate(Routes.bibleRoute()) }
            )
        }
    }

    // The minimised processing screen, on the main tabs and in the notes library.
    val showPill = activeProcessing != null && !onProcessingScreen &&
        (currentRoute in routesWithNav || currentRoute == Routes.NOTEBOOK || currentRoute == Routes.MODELS)
    com.example.core.ui.ProcessingPill(
        active = activeProcessing.takeIf { showPill },
        onOpen = openProcessing,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = (if (currentRoute in routesWithNav) 78.dp else 16.dp) + (if (playbackState.isActive) 64.dp else 0.dp))
    )

    if (showCreateSheet) {
        com.example.core.ui.CreateSheet(
            onPick = { action ->
                showCreateSheet = false
                when (action) {
                    com.example.core.ui.CreateAction.RECORD -> navController.navigate(Routes.RECORDING)
                    com.example.core.ui.CreateAction.NOTE -> openNewNote(false)
                    com.example.core.ui.CreateAction.MEDIA -> openNewNote(true)
                    com.example.core.ui.CreateAction.IMPORT -> navController.navigate(Routes.IMPORT)
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
                        id.startsWith("devotional:") -> navController.navigate(Routes.DEVOTIONAL) { launchSingleTop = true }
                        id.startsWith("preview") -> Unit
                        else -> navController.navigate(Routes.meetingDetailRoute(id))
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    recoveryEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { /* Never auto-dismiss into a silent discard — spec §3.7. Back
                gesture just closes this composition's state; the same journal is read again and
                re-prompted on the next app launch since nothing here has cleared it. */ recoveryEntry = null },
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
                        recoveryEntry = null
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
                    recoveryEntry = null
                }) { Text("Delete") }
            }
        )
    }
    }
}
