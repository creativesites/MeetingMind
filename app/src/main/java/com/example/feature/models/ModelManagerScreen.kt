package com.example.feature.models

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
import com.example.core.ui.OfflineShieldBadge
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletSecondary
import com.example.ui.theme.WarningAmber
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

    private val installJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    val availableStorageMb: Long get() = DeviceCapabilityDetector.getAvailableStorageMb()

    init {
        viewModelScope.launch { modelRepository.ensureCatalogSeeded() }
    }

    /** Attempts a real install. Honestly reports back when no downloadable model source exists yet. */
    fun installModel(modelId: String) {
        if (modelId in _downloadingModelIds.value) return
        installJobs[modelId] = viewModelScope.launch {
            _downloadingModelIds.value = _downloadingModelIds.value + modelId
            _downloadProgress.value = _downloadProgress.value + (modelId to 0f)
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
                _downloadProgress.value = _downloadProgress.value - modelId
                installJobs.remove(modelId)
            }
        }
    }

    /** Real cancellation — stops the in-flight download coroutine. Already-verified sibling files
     * are left in place so a later retry can resume from there instead of starting over. */
    fun cancelDownload(modelId: String) {
        installJobs[modelId]?.cancel()
        installJobs.remove(modelId)
        _downloadingModelIds.value = _downloadingModelIds.value - modelId
        _downloadProgress.value = _downloadProgress.value - modelId
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    viewModel: ModelManagerViewModel,
    onNavigateBack: () -> Unit
) {
    val models by viewModel.models.collectAsState()
    val prefs by viewModel.userPrefsState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val caps = viewModel.deviceCapabilities
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MeetingMind Intelligence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("models_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    OfflineShieldBadge(modifier = Modifier.padding(end = 12.dp))
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
            // 1. Bento Telemetry Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, IndigoPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = IndigoPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = IndigoPrimaryLight, modifier = Modifier.size(20.dp))
                                }
                            }
                            Text(
                                text = "Device Performance Profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Available RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${caps.availableRamGb} GB", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Architecture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(caps.cpuArch, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Hardware Tier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(caps.devicePerformanceTier, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Free Storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    Formatters.formatBytes(viewModel.availableStorageMb * 1024 * 1024),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Header
            item {
                Text(
                    text = "MeetingMind's Intelligence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 3. Capability groups — what MeetingMind can do, in plain language first. Technical
            // model details (size, quantization, storage) are one tap away, never dumped up front.
            CAPABILITY_GROUPS.forEach { group ->
                val groupModels = models.filter { group.capability in it.capability }
                if (groupModels.isNotEmpty()) {
                    item(key = "group_${group.capability}") {
                        CapabilityGroupCard(
                            group = group,
                            models = groupModels.map { model ->
                                val liveProgress = downloadProgress[model.id]
                                if (liveProgress != null) model.copy(isDownloading = true, downloadProgress = liveProgress) else model
                            },
                            activeModelId = prefs.selectedAsrModelId,
                            onSelectActive = { viewModel.selectActiveAsrModel(it) },
                            onInstall = { viewModel.installModel(it) },
                            onCancel = { viewModel.cancelDownload(it) },
                            onDelete = { viewModel.deleteModel(it) }
                        )
                    }
                }
            }
        }
    }
}

private data class CapabilityGroupInfo(
    val capability: ModelCapability,
    val title: String,
    val plainDescription: String,
    val icon: ImageVector
)

private val CAPABILITY_GROUPS = listOf(
    CapabilityGroupInfo(
        ModelCapability.TRANSCRIPTION,
        "Speech Recognition",
        "Converts your recordings into text — entirely on this device, never sent anywhere.",
        Icons.Default.RecordVoiceOver
    ),
    CapabilityGroupInfo(
        ModelCapability.DIARIZATION,
        "Speaker Detection",
        "Figures out who said what when more than one person is speaking.",
        Icons.Default.Groups
    ),
    CapabilityGroupInfo(
        ModelCapability.SUMMARIZATION,
        "Meeting Intelligence & Ask",
        "Extracts decisions and action items, and answers questions about your recordings — grounded only in what was actually said.",
        Icons.Default.Psychology
    )
)

private enum class CapabilityStatus(val label: String, val color: @Composable () -> Color) {
    READY("Ready", { SuccessGreen }),
    DOWNLOADING("Downloading", { IndigoPrimaryLight }),
    NEEDS_DOWNLOAD("Needs Download", { WarningAmber }),
    UNAVAILABLE("Unavailable", { CyanTertiary })
}

private fun statusFor(models: List<AiModelInfo>): CapabilityStatus = when {
    models.any { it.isDownloading } -> CapabilityStatus.DOWNLOADING
    models.all { it.isInstalled } -> CapabilityStatus.READY
    models.any { it.isDownloadable } -> CapabilityStatus.NEEDS_DOWNLOAD
    else -> CapabilityStatus.UNAVAILABLE
}

@Composable
private fun CapabilityGroupCard(
    group: CapabilityGroupInfo,
    models: List<AiModelInfo>,
    activeModelId: String,
    onSelectActive: (String) -> Unit,
    onInstall: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var expanded by remember(group.capability) { mutableStateOf(true) }
    val status = statusFor(models)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = IndigoPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(group.icon, contentDescription = null, tint = IndigoPrimaryLight, modifier = Modifier.size(20.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = status.color().copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = status.color(),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = group.plainDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    models.forEach { model ->
                        BentoModelItemCard(
                            model = model,
                            isActive = activeModelId == model.id,
                            onSelectActive = { onSelectActive(model.id) },
                            onInstall = { onInstall(model.id) },
                            onCancel = { onCancel(model.id) },
                            onDelete = { onDelete(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoModelItemCard(
    model: AiModelInfo,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showTechnicalDetails by remember(model.id) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) IndigoPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            if (isActive) IndigoPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (model.isInstalled && model.capability.contains(ModelCapability.TRANSCRIPTION)) {
                        RadioButton(
                            selected = isActive,
                            onClick = onSelectActive,
                            colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimaryLight)
                        )
                    }
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                if (model.isDownloading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CircularProgressIndicator(
                            progress = { model.downloadProgress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = IndigoPrimaryLight
                        )
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp).testTag("model_cancel_${model.id}")) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel download", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                } else if (model.isInstalled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Installed",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).testTag("model_delete_${model.id}")) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete model",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (!model.isDownloadable) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Not yet available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("model_install_${model.id}")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            if (model.isDownloading) {
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = IndigoPrimaryLight
                )
                Text(
                    text = "${(model.downloadProgress * 100).toInt()}% of ${Formatters.formatBytes(model.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.clickable { showTechnicalDetails = !showTechnicalDetails },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (showTechnicalDetails) "Hide technical details" else "Technical details",
                    style = MaterialTheme.typography.labelSmall,
                    color = IndigoPrimaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = IndigoPrimaryLight,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showTechnicalDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${model.parameterCount} parameters • ${model.quantization} • ${Formatters.formatBytes(model.sizeBytes)} on disk",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = model.version,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Stored on-device only, deletable at any time",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
