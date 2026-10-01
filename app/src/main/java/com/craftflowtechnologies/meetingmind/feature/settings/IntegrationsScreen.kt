package com.craftflowtechnologies.meetingmind.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.integrations.Capability
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationCategory
import com.craftflowtechnologies.meetingmind.core.integrations.IntegrationProvider
import com.craftflowtechnologies.meetingmind.core.integrations.ProviderStatus
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import androidx.compose.foundation.BorderStroke
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.DangerWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
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
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    val connectedProviders = state.providers.filter {
        it.status == ProviderStatus.CONNECTED || (it.category != IntegrationCategory.EMAIL && it.status == ProviderStatus.AVAILABLE)
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
                    text = "Where work enters and leaves MeetingMind. Connect your tools or see what is enabled.",
                    fontSize = 14.sp,
                    color = InkSecondary,
                    lineHeight = 20.sp
                )
            }

            // Section 1: Active System Connections
            item {
                SectionHeader(title = "ACTIVE ON DEVICE")
            }

            items(connectedProviders, key = { it.id }) { provider ->
                val isEnabled = provider.id in state.enabledProviderIds
                IntegrationCard(
                    provider = provider,
                    isEnabled = isEnabled,
                    onToggleEnabled = { enabled -> viewModel.toggleProvider(provider.id, enabled) }
                )
            }

            // Section 2: Email Connections (if enabled or policy restricted)
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

            // Section 3: Coming Later (Founder Signal)
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
    restrictedByPolicy: Boolean = false
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
