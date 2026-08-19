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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.common.Formatters
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.AiModelInfo
import com.example.core.model.DeviceCapabilities
import com.example.core.repository.ModelRepository
import com.example.core.ui.OfflineShieldBadge
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.IndigoPrimaryLight
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val modelRepository = ModelRepository()
    private val userPrefs = UserPreferencesManager(application)
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilityDetector.detect(application)

    val models: StateFlow<List<AiModelInfo>> = modelRepository.models
    val userPrefsState = userPrefs.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.core.datastore.AppPreferencesState()
    )

    fun toggleModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.toggleInstall(modelId)
        }
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
    val caps = viewModel.deviceCapabilities

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Neural Engine Manager",
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
                        }
                    }
                }
            }

            // 2. Models Section Header
            item {
                Text(
                    text = "On-Device Speech & LLM Models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 3. Model Bento Items
            items(models, key = { it.id }) { model ->
                BentoModelItemCard(
                    model = model,
                    isActive = prefs.selectedAsrModelId == model.id,
                    onSelectActive = { viewModel.selectActiveAsrModel(model.id) },
                    onToggleInstall = { viewModel.toggleModel(model.id) }
                )
            }
        }
    }
}

@Composable
fun BentoModelItemCard(
    model: AiModelInfo,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onToggleInstall: () -> Unit
) {
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
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    if (model.isInstalled && model.capability.contains(com.example.core.model.ModelCapability.TRANSCRIPTION)) {
                        RadioButton(
                            selected = isActive,
                            onClick = onSelectActive,
                            colors = RadioButtonDefaults.colors(selectedColor = IndigoPrimaryLight)
                        )
                    }
                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${model.parameterCount} • ${model.quantization} • ${Formatters.formatBytes(model.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (model.isDownloading) {
                    CircularProgressIndicator(
                        progress = { model.downloadProgress },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = IndigoPrimaryLight
                    )
                } else if (model.isInstalled) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ACTIVE READY",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onToggleInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            }
        }
    }
}
