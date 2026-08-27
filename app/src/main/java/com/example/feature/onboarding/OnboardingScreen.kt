package com.example.feature.onboarding

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.DeviceCapabilities
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineSoft
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferencesManager(application)
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilityDetector.detect(application)

    private val _selectedModel = MutableStateFlow(deviceCapabilities.recommendedAsrModelId)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    /** What the user typed on the identity step — never pre-filled from the device name, Google
     * account, or any contact data (Phase 15 §5: "Do not assume the user's name from device/
     * account information"). Starts blank; staying blank is a valid choice, not an error. */
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun selectModel(modelId: String) {
        _selectedModel.value = modelId
    }

    fun setUserName(name: String) {
        _userName.value = name
    }

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            prefs.setSelectedAsrModel(_selectedModel.value)
            prefs.setUserName(_userName.value)
            prefs.setOnboardingCompleted(true)
            onCompleted()
        }
    }
}

/**
 * Onboarding (Phase 15 §Part 2) — restyled onto the same Ink/Accent flat-row token system as
 * Settings/AI Engine/meeting detail, so the very first thing a user sees already matches the
 * rest of the app instead of looking like a different, older design pass.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinishOnboarding: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val selectedModel by viewModel.selectedModel.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val caps = viewModel.deviceCapabilities
    val stepCount = 4

    Scaffold(containerColor = Color.White) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Step Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(stepCount) { index ->
                        Box(
                            modifier = Modifier
                                .size(width = if (index == currentStep) 24.dp else 8.dp, height = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (index == currentStep) Ink else LineSoft)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step Content
            when (currentStep) {
                0 -> OnboardingStepZero()
                1 -> OnboardingStepOne()
                2 -> OnboardingStepIdentity(
                    userName = userName,
                    onUserNameChange = { viewModel.setUserName(it) }
                )
                3 -> OnboardingStepTwo(
                    caps = caps,
                    selectedModel = selectedModel,
                    onSelectModel = { viewModel.selectModel(it) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    Text(
                        text = "Back",
                        fontSize = 15.sp,
                        color = InkSecondary,
                        modifier = Modifier
                            .clickable { currentStep-- }
                            .testTag("onboarding_back_btn")
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep < stepCount - 1) {
                            currentStep++
                        } else {
                            viewModel.completeOnboarding(onFinishOnboarding)
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    modifier = Modifier.testTag("onboarding_next_btn")
                ) {
                    Text(
                        text = if (currentStep == stepCount - 1) "Get Started" else "Continue",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepZero() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = Accent.copy(alpha = 0.10f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Accent, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Private by Default",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.7).sp,
            color = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your recordings and transcripts never leave this phone. Everything is processed and stored strictly on-device.",
            fontSize = 15.5.sp,
            color = InkSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            FeatureBullet(icon = Icons.Default.CheckCircle, title = "No Cloud Uploads", subtitle = "Audio and text stay in app-private storage")
            HorizontalDivider(color = LineSoft, modifier = Modifier.padding(vertical = 12.dp))
            FeatureBullet(icon = Icons.Default.CheckCircle, title = "No API Keys Required", subtitle = "Runs local models without third-party rate limits")
        }
    }
}

@Composable
private fun OnboardingStepOne() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = Accent.copy(alpha = 0.10f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Accent, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Your Meetings, Organized",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.7).sp,
            color = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "MeetingMind transcribes your meetings, identifies speakers, extracts key decisions, assigns action items, and allows grounded Q&A.",
            fontSize = 15.5.sp,
            color = InkSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            FeatureBullet(icon = Icons.Default.Mic, title = "Record or Import", subtitle = "Live mic recording or import audio/video files")
            HorizontalDivider(color = LineSoft, modifier = Modifier.padding(vertical = 12.dp))
            FeatureBullet(icon = Icons.Default.Memory, title = "Speaker Diarization & RAG", subtitle = "Search by speaker or ask questions with citations")
        }
    }
}

/**
 * Collects the user's own name, typed by them — never pre-filled from the device name, Google
 * account, or contacts (Phase 15 §5). Optional: leaving it blank is a valid choice and just means
 * personalization features that could use a name (e.g. Ask AI addressing them by it) don't have
 * one to use yet, rather than blocking onboarding on it. Also editable later in Settings.
 */
@Composable
private fun OnboardingStepIdentity(
    userName: String,
    onUserNameChange: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = Accent.copy(alpha = 0.10f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "What should we call you?",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.7).sp,
            color = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Used to personalize your experience — like Ask AI addressing you by name. Optional, and stored only on this device. You can change this later in Settings.",
            fontSize = 15.5.sp,
            color = InkSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            placeholder = { Text("Your name", color = InkFaint) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = LineSoft,
                cursorColor = Accent,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("onboarding_name_field")
        )
    }
}

/**
 * There is exactly one real on-device speech-to-text model in [com.example.ai.modelmanagement.ModelCatalog]
 * (Parakeet TDT 0.6B v3 INT8) — no lighter/heavier tier actually exists to offer a choice between,
 * so this step is informational rather than a picker. [onSelectModel] still runs once (with the
 * one real model id) so [OnboardingViewModel.completeOnboarding] has something real to persist.
 */
@Composable
private fun OnboardingStepTwo(
    caps: DeviceCapabilities,
    selectedModel: String,
    onSelectModel: (String) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(caps.recommendedAsrModelId) {
        onSelectModel(caps.recommendedAsrModelId)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Your On-Device Speech Model",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.6).sp,
            color = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Detected ${caps.totalRamGb} GB RAM (${caps.cpuArch}, ${caps.devicePerformanceTier}).",
            fontSize = 13.5.sp,
            color = InkMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            Column {
                Text("Parakeet TDT 0.6B v3 (INT8)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("On-device speech-to-text, ~639 MB — downloaded on the next screen", fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You'll download this model (and the others you choose to enable) after onboarding, in AI Engine.",
            fontSize = 12.5.sp,
            color = InkMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureBullet(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
        Column {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitle, fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
