package com.example.feature.settings

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.core.firebase.FirebaseAuthManager
import com.example.core.firebase.FirebaseUserModel
import com.example.core.repository.MeetingRepository
import com.example.core.ui.ListRow
import com.example.core.ui.SectionCard
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val meetingRepository = MeetingRepository(application, database)
    private val authManager = FirebaseAuthManager(application)
    private val userPrefs = UserPreferencesManager(application)

    val currentUser: StateFlow<FirebaseUserModel?> = authManager.currentUser
    val authAvailability: com.example.core.firebase.AuthAvailability get() = authManager.authAvailability
    val preferencesState: StateFlow<AppPreferencesState> = userPrefs.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppPreferencesState()
    )

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    fun toggleBatterySaver(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setBatterySaverMode(enabled)
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            userPrefs.setWifiOnlyDownload(enabled)
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            meetingRepository.deleteAllMeetings()
            onComplete()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    /** [activityContext] must be an Activity context — required to show the Credential Manager UI. */
    fun signInWithGoogle(activityContext: android.content.Context) {
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = null
            val result = authManager.signInWithGoogle(activityContext)
            result.onFailure { e -> _signInError.value = e.message ?: "Sign-in failed." }
            _isSigningIn.value = false
        }
    }

    fun consumeSignInError() {
        _signInError.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val prefs by viewModel.preferencesState.collectAsState()
    val isSigningIn by viewModel.isSigningIn.collectAsState()
    val signInError by viewModel.signInError.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(signInError) {
        signInError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeSignInError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Privacy",
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = currentUser?.displayName ?: "Local User (Offline)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val subtitle = when {
                                    currentUser != null -> currentUser?.email ?: "Signed in with Google"
                                    viewModel.authAvailability is com.example.core.firebase.AuthAvailability.NotConfigured ->
                                        (viewModel.authAvailability as com.example.core.firebase.AuthAvailability.NotConfigured).reason
                                    else -> "Strictly local on-device account"
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currentUser != null) {
                            IconButton(onClick = { viewModel.signOut() }) {
                                Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
                            }
                        } else if (viewModel.authAvailability == com.example.core.firebase.AuthAvailability.Available) {
                            Button(
                                onClick = { viewModel.signInWithGoogle(context) },
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                enabled = !isSigningIn
                            ) {
                                if (isSigningIn) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Sign In with Google")
                                }
                            }
                        }
                        // When auth is NotConfigured, no button is shown at all — recording,
                        // import, and (once installed) local AI processing all work without it.
                    }
                }
            }

            // Privacy Guarantee — quiet, not a shouting green pill/box.
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

            // Preferences
            item {
                Text(
                    text = "App Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SectionCard {
                    ListRow(
                        title = "Battery Saver Mode",
                        subtitle = "Uses lightweight Whisper Tiny model",
                        icon = Icons.Default.BatterySaver,
                        trailing = {
                            Switch(
                                checked = prefs.batterySaverMode,
                                onCheckedChange = { viewModel.toggleBatterySaver(it) }
                            )
                        }
                    )
                    ListRow(
                        title = "Download Models Over Wi-Fi Only",
                        subtitle = "Saves cellular mobile data",
                        icon = Icons.Default.Wifi,
                        showDivider = false,
                        trailing = {
                            Switch(
                                checked = prefs.wifiOnlyDownload,
                                onCheckedChange = { viewModel.toggleWifiOnly(it) }
                            )
                        }
                    )
                }
            }

            // Future Integrations Preview
            item {
                Text(
                    text = "Future Integrations (Phase 2+)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SectionCard {
                    ListRow(
                        title = "Google Calendar Sync",
                        subtitle = "Auto-detect scheduled meetings (coming in Phase 2)",
                        icon = Icons.Default.CalendarMonth,
                        trailing = null
                    )
                    ListRow(
                        title = "Meeting Bots (Zoom / Meet / Teams)",
                        subtitle = "Automated bot dispatching (coming in Phase 2)",
                        icon = Icons.Default.VideoCall,
                        showDivider = false,
                        trailing = null
                    )
                }
            }

            // Danger Zone: Clear Data
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

            // About Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MeetingMind v1.0.0 (Local-First Offline MVP)",
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
