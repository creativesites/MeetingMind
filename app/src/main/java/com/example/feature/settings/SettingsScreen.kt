package com.example.feature.settings

import android.app.Application
import com.example.BuildConfig
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.AppPreferencesState
import com.example.core.datastore.UserPreferencesManager
import com.example.core.repository.MeetingRepository
import com.example.core.ui.ListRow
import com.example.core.ui.SectionCard
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.SharingStarted
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

    val preferencesState: StateFlow<AppPreferencesState> = userPrefs.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppPreferencesState()
    )

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit = {},
    onNavigateBottomNav: (com.example.core.ui.BottomNavDestination) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesState.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            com.example.core.ui.AppBottomNavigationBar(
                current = com.example.core.ui.BottomNavDestination.SETTINGS,
                onNavigate = onNavigateBottomNav
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI & Models — real, functioning controls only.
            item {
                Text(
                    text = "AI & Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SectionCard {
                    ListRow(
                        title = "Manage AI Models",
                        subtitle = "Download, pause, resume, or remove on-device models",
                        icon = Icons.Default.Memory,
                        onClick = onNavigateToModels
                    )
                    ListRow(
                        title = "Download Models Over Wi-Fi Only",
                        subtitle = "Model downloads pause until you're back on Wi-Fi",
                        icon = Icons.Default.Wifi,
                        showDivider = false,
                        trailing = {
                            Switch(
                                checked = prefs.wifiOnlyDownload,
                                onCheckedChange = { viewModel.toggleWifiOnly(it) },
                                modifier = Modifier.testTag("settings_wifi_only_switch")
                            )
                        }
                    )
                }
            }

            // Transcripts
            item {
                Text(
                    text = "Transcripts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SectionCard {
                    ListRow(
                        title = "Tidy Up Filler Words",
                        subtitle = "Hides \"uh\" and \"um\" when reading. Your transcript is always stored word-for-word, so turning this off brings them straight back.",
                        icon = Icons.AutoMirrored.Filled.Notes,
                        showDivider = false,
                        trailing = {
                            Switch(
                                checked = prefs.cleanFillerWords,
                                onCheckedChange = { viewModel.toggleCleanFillerWords(it) },
                                modifier = Modifier.testTag("settings_clean_filler_switch")
                            )
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Transcript Cleanup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SectionCard {
                    ListRow(
                        title = "Conservative",
                        subtitle = "Light cleanup. Keeps your original wording.",
                        onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.CONSERVATIVE) },
                        trailing = {
                            androidx.compose.material3.RadioButton(
                                selected = prefs.transcriptCleanupMode == com.example.core.model.TranscriptCleanupMode.CONSERVATIVE,
                                onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.CONSERVATIVE) },
                                modifier = Modifier.testTag("settings_cleanup_mode_conservative")
                            )
                        }
                    )
                    ListRow(
                        title = "Moderate",
                        subtitle = "Balances readability with your original wording.",
                        onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.MODERATE) },
                        trailing = {
                            androidx.compose.material3.RadioButton(
                                selected = prefs.transcriptCleanupMode == com.example.core.model.TranscriptCleanupMode.MODERATE,
                                onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.MODERATE) },
                                modifier = Modifier.testTag("settings_cleanup_mode_moderate")
                            )
                        }
                    )
                    ListRow(
                        title = "Aggressive",
                        subtitle = "Creates the most polished transcript. May make larger wording changes.",
                        showDivider = false,
                        onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.AGGRESSIVE) },
                        trailing = {
                            androidx.compose.material3.RadioButton(
                                selected = prefs.transcriptCleanupMode == com.example.core.model.TranscriptCleanupMode.AGGRESSIVE,
                                onClick = { viewModel.setTranscriptCleanupMode(com.example.core.model.TranscriptCleanupMode.AGGRESSIVE) },
                                modifier = Modifier.testTag("settings_cleanup_mode_aggressive")
                            )
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Speaker Detection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SectionCard {
                    ListRow(
                        title = "Automatic",
                        subtitle = "MeetingMind chooses the best approach.",
                        onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.AUTO) },
                        trailing = {
                            androidx.compose.material3.RadioButton(
                                selected = prefs.diarizationStrategy == com.example.core.model.DiarizationStrategy.AUTO,
                                onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.AUTO) },
                                modifier = Modifier.testTag("settings_diarization_auto")
                            )
                        }
                    )
                    ListRow(
                        title = "Deterministic",
                        subtitle = "Uses the local speaker detection engine.",
                        onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.DETERMINISTIC) },
                        trailing = {
                            androidx.compose.material3.RadioButton(
                                selected = prefs.diarizationStrategy == com.example.core.model.DiarizationStrategy.DETERMINISTIC,
                                onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.DETERMINISTIC) },
                                modifier = Modifier.testTag("settings_diarization_deterministic")
                            )
                        }
                    )
                    ListRow(
                        title = "AI-assisted",
                        subtitle = "Uses local AI to help resolve difficult speaker assignments.",
                        showDivider = false,
                        onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.AI_ASSISTED) },
                        trailing = {
                            androidx.compose.material3.RadioButton(
                                selected = prefs.diarizationStrategy == com.example.core.model.DiarizationStrategy.AI_ASSISTED,
                                onClick = { viewModel.setDiarizationStrategy(com.example.core.model.DiarizationStrategy.AI_ASSISTED) },
                                modifier = Modifier.testTag("settings_diarization_ai_assisted")
                            )
                        }
                    )
                }
            }

            // Privacy
            item {
                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen)
                        Column {
                            Text(
                                text = "Zero-Knowledge Offline Privacy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Audio recordings and full transcripts never leave your device. All ASR transcription, diarization, summarization, and vector search execute on your local CPU.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Storage
            item {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Button(
                    onClick = { showClearDataDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Local Data & Audio", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }

            // About
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MeetingMind ${BuildConfig.VERSION_NAME} (Local-First Offline MVP)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
