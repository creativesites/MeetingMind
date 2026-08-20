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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
                onNavigateToImport = {
                    navController.navigate(Routes.IMPORT)
                },
                onNavigateToMeeting = { meetingId ->
                    navController.navigate(Routes.meetingDetailRoute(meetingId))
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.SEARCH)
                },
                onNavigateToModels = {
                    navController.navigate(Routes.MODELS)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        // RECORDING
        composable(Routes.RECORDING) {
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
                navArgument("meetingId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            val app = context.applicationContext as android.app.Application
            val vm = remember(meetingId) { MeetingDetailViewModel(app, meetingId) }

            MeetingDetailScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Routes.MODELS) }
            )
        }

        // SEARCH
        composable(Routes.SEARCH) {
            val vm: SearchViewModel = viewModel()
            SearchScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMeeting = { meetingId ->
                    navController.navigate(Routes.meetingDetailRoute(meetingId))
                }
            )
        }

        // AI MODELS
        composable(Routes.MODELS) {
            val vm: ModelManagerViewModel = viewModel()
            ModelManagerScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SETTINGS
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
