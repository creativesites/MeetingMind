package com.example.feature.onboarding

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.DeviceCapabilities
import com.example.core.model.ProcessingProfile
import com.example.core.setup.SetupGuide
import com.example.core.setup.SetupPart
import com.example.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How the person wants the app's AI to run, chosen on the setup step. */
enum class SetupChoice { OFFLINE_PACK, INTERNET, LATER }

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferencesManager(application)
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilityDetector.detect(application)

    private val _selectedModel = MutableStateFlow(deviceCapabilities.recommendedAsrModelId)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    /** What the user typed on the identity step — never pre-filled from the device name, Google
     * account, or any contact data (Phase 15 §5). Staying blank is a valid choice. */
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun selectModel(modelId: String) { _selectedModel.value = modelId }
    fun setUserName(name: String) { _userName.value = name }

    /** What the app is for, and how it feels (PLAN_V2 F0). Everything on by default. */
    private val _spaces = MutableStateFlow(com.example.core.model.NotebookSpace.entries.toSet())
    val spaces: StateFlow<Set<com.example.core.model.NotebookSpace>> = _spaces.asStateFlow()
    private val _look = MutableStateFlow(com.example.core.identity.LookAndFeel.PROFESSIONAL)
    val look: StateFlow<com.example.core.identity.LookAndFeel> = _look.asStateFlow()
    private var lookTouched = false

    fun setSpaces(spaces: Set<com.example.core.model.NotebookSpace>) {
        _spaces.value = spaces
        // Faith on its own (or with Personal) suggests the warm look, until the person picks one.
        if (!lookTouched) _look.value = if (spaces.all { it == com.example.core.model.NotebookSpace.FAITH || it == com.example.core.model.NotebookSpace.PERSONAL } && com.example.core.model.NotebookSpace.FAITH in spaces)
            com.example.core.identity.LookAndFeel.SANCTUARY else com.example.core.identity.LookAndFeel.PROFESSIONAL
    }

    fun setLook(look: com.example.core.identity.LookAndFeel) { lookTouched = true; _look.value = look }

    /** The offline pack by default: most people want it to just work, privately. */
    private val _setup = MutableStateFlow(SetupChoice.OFFLINE_PACK)
    val setup: StateFlow<SetupChoice> = _setup.asStateFlow()
    fun setSetup(choice: SetupChoice) { _setup.value = choice }

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()
    fun setWifiOnly(value: Boolean) { _wifiOnly.value = value }

    /** What the offline pack is on this phone: each job's model and size. */
    val pack = SetupPart.entries.map { it to SetupGuide.modelsFor(it, deviceCapabilities.totalRamGb) }
    val packBytes: Long = pack.sumOf { (_, models) -> models.sumOf { it.sizeBytes } }

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            prefs.setSelectedAsrModel(_selectedModel.value)
            prefs.setUserName(_userName.value)
            prefs.setSpaces(_spaces.value)
            prefs.setLook(_look.value)
            prefs.setWifiOnlyDownload(_wifiOnly.value)
            when (_setup.value) {
                SetupChoice.OFFLINE_PACK -> runCatching {
                    val app = getApplication<Application>()
                    SetupGuide.downloadMissing(app, SetupGuide.observe(app).first(), _wifiOnly.value)
                }
                SetupChoice.INTERNET -> prefs.setProcessingProfile(ProcessingProfile.INTERNET)
                SetupChoice.LATER -> Unit
            }
            prefs.setOnboardingCompleted(true)
            onCompleted()
        }
    }
}

private const val STEPS = 6

/**
 * First run: a warm welcome in the brand's navy, then five short steps — what it does, your name,
 * what it's for, how the AI runs (the offline pack is explained as three jobs so nobody stops at
 * one model), and the two permissions that matter. Everything can be changed later.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinishOnboarding: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val userName by viewModel.userName.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val look by viewModel.look.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()

    fun next() { forward = true; if (step < STEPS - 1) step++ else viewModel.completeOnboarding(onFinishOnboarding) }
    fun back() { forward = false; if (step > 0) step-- }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyLift, Brand.Navy))).testTag("onboarding")) {
        // The icon's light, softly behind everything.
        Box(Modifier.size(420.dp).align(Alignment.TopEnd).graphicsLayer { translationX = 160f; translationY = -120f }
            .background(Brush.radialGradient(listOf(Brand.Violet.copy(alpha = 0.22f), Color.Transparent)), CircleShape))
        Box(Modifier.size(380.dp).align(Alignment.BottomStart).graphicsLayer { translationX = -160f; translationY = 120f }
            .background(Brush.radialGradient(listOf(Brand.Cyan.copy(alpha = 0.14f), Color.Transparent)), CircleShape))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (step > 0) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(STEPS - 1) { i ->
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i < step) Brush.horizontalGradient(Brand.sweep) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.15f)))))
                    }
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(320)) { it / 5 * dir } + fadeIn(tween(320))) togetherWith (slideOutHorizontally(tween(220)) { -it / 5 * dir } + fadeOut(tween(180)))
                },
                label = "step", modifier = Modifier.weight(1f)
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    when (s) {
                        0 -> Welcome()
                        1 -> WhatItDoes()
                        2 -> NameStep(userName, viewModel::setUserName)
                        3 -> SpacesStep(spaces, viewModel::setSpaces, look, viewModel::setLook)
                        4 -> SetupStep(viewModel, setup, viewModel::setSetup, wifiOnly, viewModel::setWifiOnly)
                        else -> PermissionsStep()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) Text("Back", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { back() }.padding(vertical = 12.dp, horizontal = 4.dp).testTag("onboarding_back_btn"))
                Spacer(Modifier.weight(1f))
                Surface(onClick = { next() }, shape = RoundedCornerShape(50), color = Color.Transparent, modifier = Modifier.testTag("onboarding_next_btn")) {
                    Row(Modifier.background(Brush.horizontalGradient(listOf(Brand.Cyan, Brand.Indigo, Brand.Violet))).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (step) { 0 -> "Get started"; STEPS - 1 -> "Start using MeetingMind"; 2 -> if (userName.isBlank()) "Skip" else "Continue"; else -> "Continue" },
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    val t = rememberInfiniteTransition(label = "glow")
    val glow by t.animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "g")
    Column(Modifier.fillMaxWidth().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Box(Modifier.size(220.dp).graphicsLayer { scaleX = glow; scaleY = glow }.background(Brush.radialGradient(listOf(Brand.Indigo.copy(alpha = 0.45f), Color.Transparent)), CircleShape))
            Image(painterResource(R.drawable.brand_mark), contentDescription = "MeetingMind", modifier = Modifier.size(width = 150.dp, height = 128.dp))
        }
        Text("MeetingMind", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(top = 18.dp))
        Text(
            "Remember every conversation.\nPrivately, on your phone.",
            color = Color.White.copy(alpha = 0.72f), fontSize = 18.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
        )
        Row(Modifier.padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(Icons.Filled.Lock, "Private")
            Pill(Icons.Filled.PhoneAndroid, "Works offline")
            Pill(Icons.Filled.AutoAwesome, "AI notes")
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Brand.Cyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
    }
}

@Composable
private fun StepTitle(title: String, line: String) {
    Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, lineHeight = 34.sp, modifier = Modifier.padding(top = 12.dp))
    Text(line, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
}

@Composable
private fun WhatItDoes() {
    StepTitle("Here's what it does", "Press record. MeetingMind listens, writes it all down, and hands you what matters.")
    Feature(Icons.Filled.Mic, "Record anything", "Meetings, lectures, sermons, calls, voice notes — even with the screen off.")
    Feature(Icons.Filled.AutoAwesome, "Transcripts and summaries", "Who said what, the decisions, the action items. Ask questions about any recording.")
    Feature(Icons.Filled.EditNote, "Notes that connect", "Write, add photos and scripture. Recordings become notes you can share or export.")
    Feature(Icons.Filled.CalendarMonth, "Your day at a glance", "Today shows what's next, preps you for meetings and keeps everything on a timeline.")
}

@Composable
private fun Feature(icon: ImageVector, title: String, line: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Brand.Blue, Brand.Violet))), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(line, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit) {
    StepTitle("What should we call you?", "For greetings, and so Ask AI can address you. Optional, and it stays on this phone.")
    OutlinedTextField(
        value = name, onValueChange = onName, singleLine = true,
        placeholder = { Text("Your first name", color = Color.White.copy(alpha = 0.35f)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Brand.Cyan, unfocusedBorderColor = Color.White.copy(alpha = 0.2f), cursorColor = Brand.Cyan,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_field")
    )
}

@Composable
private fun SpacesStep(
    spaces: Set<com.example.core.model.NotebookSpace>, onSpaces: (Set<com.example.core.model.NotebookSpace>) -> Unit,
    look: com.example.core.identity.LookAndFeel, onLook: (com.example.core.identity.LookAndFeel) -> Unit
) {
    StepTitle("What's it for?", "Pick what you'll use it for — the app shows only those. Change it any time in Settings.")
    com.example.core.identity.SpacesPicker(spaces, onSpaces)
    Text("How should it feel?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
    com.example.core.identity.LookPicker(look, onLook)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SetupStep(vm: OnboardingViewModel, choice: SetupChoice, onChoice: (SetupChoice) -> Unit, wifiOnly: Boolean, onWifiOnly: (Boolean) -> Unit) {
    StepTitle("How should the AI run?", "Recording always works. Turning recordings into transcripts and summaries needs a little AI setup.")
    ChoiceCard(
        selected = choice == SetupChoice.OFFLINE_PACK, onClick = { onChoice(SetupChoice.OFFLINE_PACK) },
        icon = Icons.Filled.PhoneAndroid, title = "Offline pack", badge = "Recommended",
        line = "Private and free. Three pieces, one download (${SetupGuide.formatBytes(vm.packBytes)}) that keeps going in the background.",
        tag = "setup_choice_offline"
    ) {
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.pack.forEach { (part, models) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${part.title}: ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(part.job.removeSuffix("."), color = Color.White.copy(alpha = 0.65f), fontSize = 12.5.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(SetupGuide.formatBytes(models.sumOf { it.sizeBytes }), color = Color.White.copy(alpha = 0.45f), fontSize = 11.5.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wait for Wi-Fi", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = wifiOnly, onCheckedChange = onWifiOnly, colors = SwitchDefaults.colors(checkedTrackColor = Brand.Indigo))
            }
        }
    }
    ChoiceCard(
        selected = choice == SetupChoice.INTERNET, onClick = { onChoice(SetupChoice.INTERNET) },
        icon = Icons.Filled.Cloud, title = "Internet mode", badge = null,
        line = "No big downloads — Gemini does the work. Needs a connection and your own Gemini API key (add it in Settings). Recordings are sent to Google.",
        tag = "setup_choice_internet"
    )
    ChoiceCard(
        selected = choice == SetupChoice.LATER, onClick = { onChoice(SetupChoice.LATER) },
        icon = Icons.Filled.Schedule, title = "Decide later", badge = null,
        line = "Look around first. Home will remind you — you'll need this before recordings can be transcribed.",
        tag = "setup_choice_later"
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ChoiceCard(selected: Boolean, onClick: () -> Unit, icon: ImageVector, title: String, badge: String?, line: String, tag: String, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (selected) 0.1f else 0.05f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Brush.linearGradient(Brand.sweep) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(16.dp).testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) Brand.Cyan else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            badge?.let { Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Brand.Blue, Brand.Violet))).padding(horizontal = 9.dp, vertical = 3.dp)) }
        }
        Text(line, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        if (selected) extra()
    }
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    val needsNotify = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notify by remember { mutableStateOf(!needsNotify || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = it }

    StepTitle("Two quick permissions", "So recording and reminders work when you need them. You can change these in Android settings.")
    PermissionRow(Icons.Filled.Mic, "Microphone", "To record. Audio stays on this phone unless you choose Internet mode.", mic, "perm_mic") {
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    PermissionRow(Icons.Filled.Notifications, "Notifications", "To tell you when a transcript is ready, setup has finished, or your devotional arrives.", notify, "perm_notify") {
        if (needsNotify) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Text(
        "After this, a short tour shows you around Home.",
        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp)
    )
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, line: String, granted: Boolean, tag: String, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Brand.Blue, Brand.Violet))), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(line, color = Color.White.copy(alpha = 0.65f), fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) Box(Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Brand.Cyan, Brand.Violet))), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = "Allowed", tint = Color.White, modifier = Modifier.size(18.dp))
        } else Text("Allow", color = Brand.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Brand.Cyan.copy(alpha = 0.6f), RoundedCornerShape(50)).clickable(onClick = onAllow).padding(horizontal = 14.dp, vertical = 7.dp).testTag(tag))
    }
}
