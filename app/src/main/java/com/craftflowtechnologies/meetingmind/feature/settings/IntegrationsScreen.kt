package com.craftflowtechnologies.meetingmind.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.integrations.Capability
import com.craftflowtechnologies.meetingmind.core.integrations.DeviceCalendarProvider
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationCategory
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationProvider
import com.craftflowtechnologies.meetingmind.core.integrations.ProviderStatus
import com.craftflowtechnologies.meetingmind.core.integrations.SafStorageProvider
import com.craftflowtechnologies.meetingmind.core.integrations.ShareSheetOutputChannel
import com.craftflowtechnologies.meetingmind.core.integrations.WebhookChannel
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.DangerWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    viewModel: IntegrationsViewModel,
    onNavigateBack: () -> Unit,
    onNavigatePaywall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var providerToConfigure by remember { mutableStateOf<IntegrationProvider?>(null) }
    var secretInput by remember { mutableStateOf("") }
    var showPaywallDialog by remember { mutableStateOf(false) }

    val vaultPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Permission might already be granted
            }
            val folderName = uri.lastPathSegment?.split(":")?.lastOrNull()?.ifBlank { null } ?: "Pinned Vault"
            viewModel.setPinnedVault(uri.toString(), folderName)
        }
    }

    val deviceProviders = state.providers.filter {
        it.id in listOf(DeviceCalendarProvider.ID, ShareSheetOutputChannel.ID, SafStorageProvider.ID)
    }
    val cloudProductivityProviders = state.providers.filter {
        it.category == IntegrationCategory.TASK || it.category == IntegrationCategory.NOTE
    }
    val emailProviders = state.providers.filter { it.category == IntegrationCategory.EMAIL }
    val comingLaterProviders = state.providers.filter { it.status == ProviderStatus.COMING_LATER }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SurfaceBase,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Ink
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Integrations",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Where work enters and leaves MeetingMind. Connect your tools or see what is enabled on your device.",
                    fontSize = 14.sp,
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            }

            // Section 1: Active System Connections
            item {
                SectionHeader(title = "ACTIVE ON DEVICE")
            }

            items(deviceProviders, key = { it.id }) { provider ->
                val isEnabled = provider.id in state.enabledProviderIds
                IntegrationCard(
                    provider = provider,
                    isEnabled = isEnabled,
                    onToggleEnabled = { enabled -> viewModel.toggleProvider(provider.id, enabled) },
                    extraContent = if (provider.id == SafStorageProvider.ID) {
                        {
                            PinnedVaultSubSection(
                                vaultName = state.pinnedVaultName,
                                onPickVault = { vaultPickerLauncher.launch(null) },
                                onClearVault = { viewModel.setPinnedVault(null, null) }
                            )
                        }
                    } else null
                )
            }

            // Section 2: Connected Productivity & Team Channels (Paid Tiers)
            if (cloudProductivityProviders.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeader(title = "CONNECTED SERVICES & AUTOMATION")
                        Text(
                            text = "Connect external task managers, issue trackers, and team channels. Included with Pro, BYOK, and Church plans.",
                            fontSize = 12.sp,
                            color = InkSecondary
                        )
                    }
                }

                items(cloudProductivityProviders, key = { it.id }) { provider ->
                    val isConnected = provider.id in state.enabledProviderIds
                    ConnectedProviderCard(
                        provider = provider,
                        isConnected = isConnected,
                        canUsePaidFeatures = state.canUseConnectedIntegrations,
                        onConfigure = {
                            if (state.canUseConnectedIntegrations) {
                                secretInput = ""
                                providerToConfigure = provider
                            } else {
                                showPaywallDialog = true
                            }
                        }
                    )
                }
            }

            // Section 3: Email Connections
            if (state.isEmailFeatureEnabled && emailProviders.isNotEmpty()) {
                item {
                    SectionHeader(title = "EMAIL CORRESPONDENCE")
                }

                val isConfidential = state.currentWorkProfile == WorkProfile.CLINICAL || state.currentWorkProfile == WorkProfile.LEGAL
                if (isConfidential) {
                    item {
                        ConfidentialWarningCard(
                            profile = state.currentWorkProfile,
                            optIn = state.confidentialOptIn,
                            onOptInChanged = { viewModel.setConfidentialOptIn(it) }
                        )
                    }
                }

                items(emailProviders, key = { it.id }) { provider ->
                    val isEnabled = provider.id in state.enabledProviderIds && (!isConfidential || state.confidentialOptIn)
                    IntegrationCard(
                        provider = provider,
                        isEnabled = isEnabled,
                        onToggleEnabled = { enabled -> viewModel.toggleProvider(provider.id, enabled) },
                        restrictedByPolicy = isConfidential && !state.confidentialOptIn
                    )
                }
            }

            // Section 4: Planned Integrations
            if (comingLaterProviders.isNotEmpty()) {
                item {
                    SectionHeader(title = "PLANNED INTEGRATIONS")
                }

                items(comingLaterProviders, key = { it.id }) { provider ->
                    val isNotified = provider.id in state.notifyMeProviderIds
                    ComingLaterCard(
                        provider = provider,
                        isNotified = isNotified,
                        onToggleNotify = { viewModel.toggleNotifyMe(provider.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Paywall Dialog
    if (showPaywallDialog) {
        AlertDialog(
            onDismissRequest = { showPaywallDialog = false },
            title = {
                Text(
                    text = "Upgrade to Connect External Tools",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            },
            text = {
                Text(
                    text = "Direct sync with Todoist, GitHub, Linear, Slack, and Microsoft Teams requires a Pro, BYOK, or Church plan. Free users enjoy full device capture, share-in, and local file exports.",
                    fontSize = 14.sp,
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPaywallDialog = false
                        onNavigatePaywall()
                    }
                ) {
                    Text(
                        text = "View Plans",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaywallDialog = false }) {
                    Text(
                        text = "Not now",
                        fontSize = 14.sp,
                        color = InkMuted
                    )
                }
            },
            containerColor = SurfaceRaised
        )
    }

    // Configure Credential Dialog
    providerToConfigure?.let { provider ->
        val isWebhook = provider.id == WebhookChannel.SLACK_ID || provider.id == WebhookChannel.TEAMS_ID
        val isConnected = provider.id in state.enabledProviderIds

        AlertDialog(
            onDismissRequest = { providerToConfigure = null },
            title = {
                Text(
                    text = "Connect ${provider.name}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isWebhook) {
                            "Paste the incoming webhook URL for your team channel. It will be encrypted in Android Keystore on your device."
                        } else {
                            "Paste your API token or personal access key. It will be encrypted in Android Keystore on your device."
                        },
                        fontSize = 13.sp,
                        color = InkSecondary,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        label = { Text(if (isWebhook) "Webhook URL" else "Personal Access Token") },
                        placeholder = {
                            Text(
                                if (isWebhook) "https://hooks.slack.com/…" else "Token / Key",
                                color = InkMuted
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (isWebhook) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isConnected) {
                        TextButton(
                            onClick = {
                                viewModel.disconnectProvider(provider.id)
                                providerToConfigure = null
                            }
                        ) {
                            Text("Disconnect", color = Danger, fontWeight = FontWeight.Medium)
                        }
                    }
                    TextButton(
                        onClick = {
                            if (secretInput.isNotBlank()) {
                                viewModel.saveCredential(provider.id, secretInput.trim())
                                providerToConfigure = null
                            }
                        },
                        enabled = secretInput.isNotBlank()
                    ) {
                        Text(
                            text = "Save & Connect",
                            fontWeight = FontWeight.SemiBold,
                            color = if (secretInput.isNotBlank()) Accent else InkMuted
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToConfigure = null }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = SurfaceRaised
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = InkMuted,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun IntegrationCard(
    provider: IntegrationProvider,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    restrictedByPolicy: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .border(1.dp, LineSoft, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    provider.accountScope?.let { scope ->
                        Text(
                            text = scope,
                            fontSize = 12.sp,
                            color = InkSecondary
                        )
                    }
                }
                if (!restrictedByPolicy) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAccent,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = InkMuted,
                            uncheckedTrackColor = LineSoft
                        ),
                        modifier = Modifier.testTag("switch_${provider.id}")
                    )
                }
            }

            HorizontalDivider(color = LineSoft, thickness = 0.5.dp)

            Text(
                text = "Enables:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = InkMuted
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                provider.capabilities.forEach { cap ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isEnabled && !restrictedByPolicy) Accent else InkMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = cap.label,
                            fontSize = 13.sp,
                            color = if (isEnabled && !restrictedByPolicy) Ink else InkSecondary
                        )
                    }
                }
            }

            extraContent?.invoke()
        }
    }
}

@Composable
private fun ConnectedProviderCard(
    provider: IntegrationProvider,
    isConnected: Boolean,
    canUsePaidFeatures: Boolean,
    onConfigure: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .border(1.dp, LineSoft, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = provider.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isConnected) AccentWash else LineSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Connected" else "Not connected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isConnected) Accent else InkMuted
                            )
                        }
                    }
                    provider.accountScope?.let { scope ->
                        Text(
                            text = scope,
                            fontSize = 12.sp,
                            color = InkSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isConnected) Accent.copy(alpha = 0.5f) else LineSoft),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isConnected) Accent else Ink
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isConnected) "Configure" else "Connect",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = LineSoft, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                provider.capabilities.forEach { cap ->
                    Text(
                        text = "• " + cap.label,
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedVaultSubSection(
    vaultName: String?,
    onPickVault: () -> Unit,
    onClearVault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBase)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "PINNED VAULT (OBSIDIAN / LOCAL SYNC)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = InkMuted
        )
        Text(
            text = "Save meeting notes and transcripts directly to a folder on your device without picking every time.",
            fontSize = 12.sp,
            color = InkSecondary,
            lineHeight = 16.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vaultName ?: "No directory pinned",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (vaultName != null) Ink else InkMuted,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vaultName != null) {
                    TextButton(
                        onClick = onClearVault,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Clear", fontSize = 12.sp, color = Danger)
                    }
                }
                OutlinedButton(
                    onClick = onPickVault,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, LineSoft),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (vaultName != null) "Change" else "Choose folder",
                        fontSize = 12.sp,
                        color = Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun ComingLaterCard(
    provider: IntegrationProvider,
    isNotified: Boolean,
    onToggleNotify: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .border(1.dp, LineSoft, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    provider.accountScope?.let { scope ->
                        Text(
                            text = scope,
                            fontSize = 12.sp,
                            color = InkSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onToggleNotify,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isNotified) Accent.copy(alpha = 0.4f) else LineSoft),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isNotified) Accent else InkSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("notify_${provider.id}")
                ) {
                    Icon(
                        imageVector = if (isNotified) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isNotified) "Notified" else "Notify me",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = LineSoft, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                provider.capabilities.forEach { cap ->
                    Text(
                        text = "• " + cap.label,
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidentialWarningCard(
    profile: WorkProfile,
    optIn: Boolean,
    onOptInChanged: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DangerWash)
            .border(1.dp, Danger.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${profile.name.lowercase().replaceFirstChar { it.uppercase() }} Confidentiality Notice",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Danger
            )
            Text(
                text = "Email integration is restricted by default in your profile to prevent sensitive client or patient correspondence from being indexed. You may explicitly opt in below.",
                fontSize = 12.sp,
                color = InkSecondary,
                lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Allow scoped email reading",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink
                )
                Switch(
                    checked = optIn,
                    onCheckedChange = onOptInChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccent,
                        checkedTrackColor = Danger,
                        uncheckedThumbColor = InkMuted,
                        uncheckedTrackColor = LineSoft
                    ),
                    modifier = Modifier.testTag("confidential_optin_switch")
                )
            }
        }
    }
}
