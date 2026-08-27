package com.example

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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.datastore.AppPreferencesState
import com.example.core.datastore.UserPreferencesManager
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
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
        setContent {
            MeetMindTheme {
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
    val navigateToPrimary: (com.example.core.ui.BottomNavDestination) -> Unit = { destination ->
        val route = when (destination) {
            com.example.core.ui.BottomNavDestination.HOME -> Routes.HOME
            com.example.core.ui.BottomNavDestination.SEARCH -> Routes.SEARCH
            com.example.core.ui.BottomNavDestination.AI_ENGINE -> Routes.MODELS
            com.example.core.ui.BottomNavDestination.SETTINGS -> Routes.SETTINGS
        }
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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
            val vm: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = vm,
                onNavigateToRecord = {
                    navController.navigate(Routes.RECORDING)
                },
                // "Quick Record" is a faster button to reach, not a different flow — it lands on
                // exactly the same type/speaker picker as the regular Record entry (see
                // RecordingScreen). Recording type and speaker count are first-class inputs to
                // processing and are never silently skipped.
                onNavigateToQuickRecord = {
                    navController.navigate(Routes.RECORDING)
                },
                onNavigateToImport = {
                    navController.navigate(Routes.IMPORT)
                },
                onNavigateToMeeting = { meetingId ->
                    navController.navigate(Routes.meetingDetailRoute(meetingId))
                },
                onNavigateToSearch = { navigateToPrimary(com.example.core.ui.BottomNavDestination.SEARCH) },
                onNavigateToModels = { navigateToPrimary(com.example.core.ui.BottomNavDestination.AI_ENGINE) },
                onNavigateToSettings = { navigateToPrimary(com.example.core.ui.BottomNavDestination.SETTINGS) }
            )
        }

        // RECORDING
        composable(route = Routes.RECORDING) {
            val vm: RecordingViewModel = viewModel()
            RecordingScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onRecordingComplete = { meetingId, audioPath, durationMs ->
                    val route = Routes.processingRoute(meetingId, audioPath, durationMs)
                    navController.navigate(route) {
                        popUpTo(Routes.RECORDING) { inclusive = true }
                    }
                }
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
                initialJumpToMs = startAtMs.takeIf { it != Routes.NO_START_AT_MS }
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
                onNavigateBottomNav = navigateToPrimary
            )
        }
    }

    if (playbackState.isActive && !isOnActiveRecordingDetail) {
        MiniPlayerBar(
            state = playbackState,
            onTogglePlayPause = { PlaybackController.togglePlayPause() },
            onStop = { PlaybackController.stop() },
            onOpen = {
                playbackState.recordingId?.let { navController.navigate(Routes.meetingDetailRoute(it)) }
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
