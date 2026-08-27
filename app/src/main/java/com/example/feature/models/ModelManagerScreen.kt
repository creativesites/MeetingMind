package com.example.feature.models

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.modelmanagement.LocalModelStorage
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.AiModelInfo
import com.example.core.model.DeviceCapabilities
import com.example.core.model.ModelCapability
import com.example.core.repository.ModelRepository
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineSoft
import com.example.ui.theme.Speaker4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val modelRepository = ModelRepository(database, LocalModelStorage(application))
    private val userPrefs = UserPreferencesManager(application)
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilityDetector.detect(application)

    val models: StateFlow<List<AiModelInfo>> = modelRepository.models.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val userPrefsState = userPrefs.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.core.datastore.AppPreferencesState()
    )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // modelId -> 0f..1f real download progress, reported live by ModelRepository/ModelDownloader.
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _downloadingModelIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingModelIds: StateFlow<Set<String>> = _downloadingModelIds.asStateFlow()

    // Stopped mid-download with bytes still on disk — the next installModel() call for this id
    // resumes via HTTP Range instead of restarting, so "Resume" here is never a lie.
    private val _pausedModelIds = MutableStateFlow<Set<String>>(emptySet())
    val pausedModelIds: StateFlow<Set<String>> = _pausedModelIds.asStateFlow()

    private val installJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    val availableStorageMb: Long get() = DeviceCapabilityDetector.getAvailableStorageMb()

    init {
        viewModelScope.launch { modelRepository.ensureCatalogSeeded() }
    }

    /** True when the device's active network is Wi-Fi (or there's no usable connectivity info to
     * say otherwise) — used to honor the "Wi-Fi only downloads" preference for real instead of
     * just storing it unused. */
    private fun isOnWifi(): Boolean {
        val connectivityManager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Attempts a real install — or, if [modelId] was previously paused, resumes it from the
     * bytes already on disk via the downloader's HTTP Range support. Honestly reports back when
     * no downloadable model source exists yet. Honors "Wi-Fi only downloads" by actually checking
     * the active network before starting, rather than only storing the preference. Also refuses a
     * *fresh* download outright when there isn't enough free storage for it, instead of letting
     * it fail deep inside a partial write — a resume is allowed through even when storage is
     * tight, since it only needs the remaining bytes, not the model's full size again. */
    fun installModel(modelId: String) {
        if (modelId in _downloadingModelIds.value) return
        if (userPrefsState.value.wifiOnlyDownload && !isOnWifi()) {
            _statusMessage.value = "Connect to Wi-Fi to download models, or turn off \"Wi-Fi only downloads\" in Settings."
            return
        }
        val resuming = modelId in _pausedModelIds.value
        if (!resuming) {
            val model = models.value.find { it.id == modelId }
            val requiredMb = (model?.sizeBytes ?: 0L) / (1024 * 1024)
            if (requiredMb > 0 && availableStorageMb < requiredMb) {
                _statusMessage.value = "Not enough free storage for this model — needs about " +
                    "${Formatters.formatBytes(model!!.sizeBytes)}, only ${Formatters.formatBytes(availableStorageMb * 1024 * 1024)} free."
                return
            }
        }
        _pausedModelIds.value = _pausedModelIds.value - modelId
        installJobs[modelId] = viewModelScope.launch {
            _downloadingModelIds.value = _downloadingModelIds.value + modelId
            if (!resuming) _downloadProgress.value = _downloadProgress.value + (modelId to 0f)
            try {
                val result = modelRepository.installModel(modelId) { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()) else 0f
                    _downloadProgress.value = _downloadProgress.value + (modelId to progress.coerceIn(0f, 1f))
                }
                when (result) {
                    is AiResult.Success -> Unit
                    else -> _statusMessage.value = result.describeFailure() ?: "This model could not be installed."
                }
            } finally {
                _downloadingModelIds.value = _downloadingModelIds.value - modelId
                // A pause (or a cancel, which clears progress itself) already set the state it
                // wants — don't let this cleanup stomp a paused row's saved progress.
                if (modelId !in _pausedModelIds.value) {
                    _downloadProgress.value = _downloadProgress.value - modelId
                }
                installJobs.remove(modelId)
            }
        }
    }

    /** Stops the in-flight download but keeps every byte written so far on disk — a slow or
     * intermittent connection (this app's primary target) shouldn't force a user to either sit
     * and wait or lose their progress. [installModel] on the same id later resumes via HTTP
     * Range instead of restarting. */
    fun pauseDownload(modelId: String) {
        installJobs[modelId]?.cancel()
        installJobs.remove(modelId)
        _downloadingModelIds.value = _downloadingModelIds.value - modelId
        _pausedModelIds.value = _pausedModelIds.value + modelId
        // _downloadProgress intentionally left as-is so the paused row keeps showing "how far".
    }

    /** Fully abandons a download: stops it (if running) and deletes whatever partial bytes are
     * on disk, reclaiming the space. Distinct from [pauseDownload], which keeps those bytes. */
    fun cancelDownload(modelId: String) {
        installJobs[modelId]?.cancel()
        installJobs.remove(modelId)
        _downloadingModelIds.value = _downloadingModelIds.value - modelId
        _pausedModelIds.value = _pausedModelIds.value - modelId
        _downloadProgress.value = _downloadProgress.value - modelId
        viewModelScope.launch { modelRepository.deleteModel(modelId) }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.deleteModel(modelId)
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun selectActiveAsrModel(modelId: String) {
        viewModelScope.launch {
            userPrefs.setSelectedAsrModel(modelId)
        }
    }

    fun selectActiveLlmModel(modelId: String) {
        viewModelScope.launch {
            userPrefs.setSelectedLlmModel(modelId)
        }
    }
}

/**
 * AI engine screen (Phase 15 §Part 2 / design `#6c`) — restyled onto the Ink/Accent flat-row
 * token system shared with the rest of the redesign so this reads as one app with Settings/Home,
 * not a separate Material3-default screen. This is a **visual** pass only: every feature the old
 * card-based screen had (device performance profile, per-model install/pause/resume/cancel/
 * delete with real byte progress, active-model selection per capability, technical details,
 * Wi-Fi-only/insufficient-storage refusal messaging) is preserved exactly — `#6c`'s own mockup
 * frame shows only a single "Now running" summary with no way to switch between installed models,
 * which would have been a real functionality regression if implemented literally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    viewModel: ModelManagerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateBottomNav: (com.example.core.ui.BottomNavDestination) -> Unit = {}
) {
    val models by viewModel.models.collectAsState()
    val prefs by viewModel.userPrefsState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadingModelIds by viewModel.downloadingModelIds.collectAsState()
    val pausedModelIds by viewModel.pausedModelIds.collectAsState()
    val caps = viewModel.deviceCapabilities
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeStatusMessage()
        }
    }

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data, containerColor = Ink, contentColor = Color.White) } },
        bottomBar = {
            com.example.core.ui.AppBottomNavigationBar(
                current = com.example.core.ui.BottomNavDestination.AI_ENGINE,
                onNavigate = onNavigateBottomNav
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("models_back_btn").size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkSecondary)
                    }
                }
                Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp)) {
                    Text(text = "AI engine", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp, color = Ink)
                    Text(text = "Everything runs on this phone", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 5.dp))
                }
            }

            // Device performance profile — real device info the mockup doesn't show but which
            // the old screen relied on to explain why a model is or isn't offered.
            item {
                Column(modifier = Modifier.padding(top = 22.dp, start = 22.dp, end = 22.dp)) {
                    Text(text = "THIS DEVICE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = InkMuted)
                    Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DeviceStat("RAM", "${caps.availableRamGb} GB")
                        DeviceStat("ARCH", caps.cpuArch)
                        DeviceStat("TIER", caps.devicePerformanceTier)
                        DeviceStat("FREE", Formatters.formatBytes(viewModel.availableStorageMb * 1024 * 1024))
                    }
                }
                HorizontalDivider(color = LineSoft, modifier = Modifier.padding(top = 20.dp))
            }

            CAPABILITY_GROUPS.forEach { group ->
                val groupModels = models.filter { group.capability in it.capability }
                if (groupModels.isNotEmpty()) {
                    // Only SUMMARIZATION has more than one real model to choose between (Phase 3C
                    // model tiers); every other capability still has exactly one, so its "active"
                    // selection is moot but harmless to thread through the same way.
                    val (activeModelId, onSelectActive, recommendedModelId) = when (group.capability) {
                        ModelCapability.SUMMARIZATION -> Triple(prefs.selectedLlmModelId, { id: String -> viewModel.selectActiveLlmModel(id) }, caps.recommendedLlmModelId)
                        else -> Triple(prefs.selectedAsrModelId, { id: String -> viewModel.selectActiveAsrModel(id) }, caps.recommendedAsrModelId)
                    }
                    item(key = "group_${group.capability}") {
                        CapabilityGroupSection(
                            group = group,
                            models = groupModels.map { model ->
                                val liveProgress = downloadProgress[model.id]
                                if (liveProgress != null) {
                                    model.copy(isDownloading = model.id in downloadingModelIds, downloadProgress = liveProgress)
                                } else {
                                    model
                                }
                            },
                            pausedModelIds = pausedModelIds,
                            activeModelId = activeModelId,
                            recommendedModelId = recommendedModelId,
                            onSelectActive = onSelectActive,
                            onInstall = { viewModel.installModel(it) },
                            onPause = { viewModel.pauseDownload(it) },
                            onCancel = { viewModel.cancelDownload(it) },
                            onDelete = { viewModel.deleteModel(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, letterSpacing = 0.5.sp, color = InkFaint)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 3.dp))
    }
}

private data class CapabilityGroupInfo(
    val capability: ModelCapability,
    val title: String,
    val plainDescription: String
)

private val CAPABILITY_GROUPS = listOf(
    CapabilityGroupInfo(
        ModelCapability.TRANSCRIPTION,
        "Speech recognition",
        "Converts your recordings into text — entirely on this device, never sent anywhere."
    ),
    CapabilityGroupInfo(
        ModelCapability.DIARIZATION,
        "Speaker detection",
        "Figures out who said what when more than one person is speaking."
    ),
    CapabilityGroupInfo(
        ModelCapability.SUMMARIZATION,
        "Meeting intelligence & Ask",
        "Extracts decisions and action items, and answers questions about your recordings — grounded only in what was actually said."
    )
)

private enum class CapabilityStatus(val label: String, val color: Color) {
    READY("Ready", Ink),
    DOWNLOADING("Downloading", Accent),
    PAUSED("Paused", InkMuted),
    NEEDS_DOWNLOAD("Needs download", Speaker4),
    UNAVAILABLE("Unavailable", InkMuted)
}

private fun statusFor(models: List<AiModelInfo>, pausedModelIds: Set<String>): CapabilityStatus = when {
    models.any { it.isDownloading } -> CapabilityStatus.DOWNLOADING
    models.any { it.id in pausedModelIds } -> CapabilityStatus.PAUSED
    models.all { it.isInstalled } -> CapabilityStatus.READY
    models.any { it.isDownloadable } -> CapabilityStatus.NEEDS_DOWNLOAD
    else -> CapabilityStatus.UNAVAILABLE
}

@Composable
private fun CapabilityGroupSection(
    group: CapabilityGroupInfo,
    models: List<AiModelInfo>,
    pausedModelIds: Set<String>,
    activeModelId: String,
    recommendedModelId: String? = null,
    onSelectActive: (String) -> Unit,
    onInstall: (String) -> Unit,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    // Always expanded — the old screen's accordion collapse hid whether a group even had a
    // choice worth making; a capability group is small enough (1-3 models) to stay flat.
    val status = statusFor(models, pausedModelIds)

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(group.plainDescription, fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            Text(status.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = status.color, modifier = Modifier.padding(start = 12.dp))
        }
        HorizontalDivider(color = LineSoft, modifier = Modifier.padding(top = 14.dp, start = 22.dp, end = 22.dp))

        // "Selectable" means the user actually has more than one real model to choose between
        // for this capability — offering a choice when there's only one option would imply a
        // decision that doesn't exist.
        val isSelectable = models.size > 1
        // Mirrors LlmModelResolver: the model that will really be used is the selected one when
        // it's installed, otherwise the best one that is. Showing "Active" on a model whose files
        // the user already deleted would be a lie.
        val effectiveActiveId = remember(models, activeModelId) {
            models.find { it.id == activeModelId && it.isInstalled }?.id
                ?: models.filter { it.isInstalled }.maxByOrNull { it.tier.ordinal }?.id
        }
        if (isSelectable) {
            Text(
                text = if (effectiveActiveId == null) {
                    "Download one of these to turn this on. You can switch at any time."
                } else {
                    "Pick the one that suits your phone — whichever is marked Active is the one being used."
                },
                fontSize = 12.5.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 12.dp, start = 22.dp, end = 22.dp)
            )
        }
        models.sortedBy { it.tier.ordinal }.forEach { model ->
            ModelRow(
                model = model,
                isActive = effectiveActiveId == model.id,
                isPaused = model.id in pausedModelIds,
                isSelectable = isSelectable,
                isRecommendedForDevice = recommendedModelId != null && recommendedModelId == model.id && isSelectable,
                onSelectActive = { onSelectActive(model.id) },
                onInstall = { onInstall(model.id) },
                onPause = { onPause(model.id) },
                onCancel = { onCancel(model.id) },
                onDelete = { onDelete(model.id) }
            )
        }
    }
}

@Composable
fun ModelRow(
    model: AiModelInfo,
    isActive: Boolean,
    isPaused: Boolean = false,
    isSelectable: Boolean = model.capability.contains(ModelCapability.TRANSCRIPTION),
    isRecommendedForDevice: Boolean = false,
    onSelectActive: () -> Unit,
    onInstall: () -> Unit,
    onPause: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showTechnicalDetails by remember(model.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) Accent.copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (model.isInstalled && isSelectable) {
                    RadioButton(
                        selected = isActive,
                        onClick = onSelectActive,
                        colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = InkFaint),
                        modifier = Modifier.testTag("model_select_${model.id}")
                    )
                }
                Text(
                    text = model.name,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            if (model.isDownloading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CircularProgressIndicator(
                        progress = { model.downloadProgress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Accent
                    )
                    IconButton(onClick = onPause, modifier = Modifier.size(28.dp).testTag("model_pause_${model.id}")) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause download", tint = InkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            } else if (isPaused) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onInstall,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        modifier = Modifier.testTag("model_resume_${model.id}")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp).testTag("model_cancel_${model.id}")) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel and discard download", tint = InkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            } else if (model.isInstalled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // With more than one model installed, "Installed" alone left the user
                    // unable to tell which one was actually running. State the real answer.
                    when {
                        !isSelectable -> Text(text = "Installed", fontSize = 12.5.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                        isActive -> Text(
                            text = "Active",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent,
                            modifier = Modifier.testTag("model_active_badge_${model.id}")
                        )
                        else -> Text(
                            text = "Use this",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                            modifier = Modifier
                                .clickable(onClick = onSelectActive)
                                .testTag("model_use_${model.id}")
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).testTag("model_delete_${model.id}")) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete model",
                            tint = InkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else if (!model.isDownloadable) {
                Text(text = "Not yet available", fontSize = 12.5.sp, color = InkMuted)
            } else {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    modifier = Modifier.testTag("model_install_${model.id}")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Tier chip + on-device recommendation — only shown when there's an actual choice
        // to make (isSelectable), so it never implies a comparison that doesn't exist.
        if (isSelectable) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Text(text = model.tier.displayName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                if (isRecommendedForDevice) {
                    Text(text = "· Best fit for your phone", fontSize = 11.sp, color = InkMuted)
                }
            }
            Text(
                text = model.tier.shortDescription,
                fontSize = 12.5.sp,
                color = InkSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            text = model.description,
            fontSize = 12.5.sp,
            color = InkSecondary,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        // Storage cost, visible without expanding "Technical details" — while downloading or
        // paused the progress line below already states the size, so this would be redundant.
        if (!model.isDownloading && !isPaused) {
            Text(
                text = if (model.isInstalled) "${Formatters.formatBytes(model.sizeBytes)} on this device" else "${Formatters.formatBytes(model.sizeBytes)} download",
                fontSize = 11.5.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (model.isDownloading || isPaused) {
            LinearProgressIndicator(
                progress = { model.downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isPaused) InkMuted else Accent,
                trackColor = LineSoft
            )
            Text(
                text = if (isPaused) {
                    "Paused at ${(model.downloadProgress * 100).toInt()}% of ${Formatters.formatBytes(model.sizeBytes)} — resumes from here, not from the start"
                } else {
                    "${(model.downloadProgress * 100).toInt()}% of ${Formatters.formatBytes(model.sizeBytes)}"
                },
                fontSize = 11.5.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable { showTechnicalDetails = !showTechnicalDetails },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showTechnicalDetails) "Hide technical details" else "Technical details",
                fontSize = 12.sp,
                color = Accent,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (showTechnicalDetails) {
            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${model.parameterCount} parameters · ${model.quantization} · ${Formatters.formatBytes(model.sizeBytes)} on disk",
                    fontSize = 11.sp,
                    color = InkMuted
                )
                Text(text = model.version, fontSize = 11.sp, color = InkMuted)
                Text(text = "Stored on-device only, deletable at any time", fontSize = 11.sp, color = InkMuted)
            }
        }
    }
    HorizontalDivider(color = LineSoft, modifier = Modifier.padding(start = 22.dp, end = 22.dp))
}
