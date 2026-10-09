package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import android.Manifest
import android.app.Application
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.common.DeviceCapabilityDetector
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.DeviceCapabilities
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.setup.SetupGuide
import com.craftflowtechnologies.meetingmind.core.setup.SetupPart
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
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
    private val _spaces = MutableStateFlow(com.craftflowtechnologies.meetingmind.core.model.OfferedNotebookSpaces.toSet())
    val spaces: StateFlow<Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>> = _spaces.asStateFlow()
    private val _look = MutableStateFlow(com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.PROFESSIONAL)
    val look: StateFlow<com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel> = _look.asStateFlow()
    private var lookTouched = false

    fun setSpaces(spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>) {
        _spaces.value = spaces
        // Faith on its own (or with Personal) suggests the warm look, until the person picks one.
        if (!lookTouched) _look.value = if (spaces.all { it == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH || it == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL } && com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH in spaces)
            com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.SANCTUARY else com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.PROFESSIONAL
    }

    fun setLook(look: com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel) { lookTouched = true; _look.value = look }

    /** What kind of work, when Work is picked: sets the words, sections and privacy defaults. */
    private val _workProfile = MutableStateFlow<com.craftflowtechnologies.meetingmind.core.work.WorkProfile?>(null)
    val workProfile: StateFlow<com.craftflowtechnologies.meetingmind.core.work.WorkProfile?> = _workProfile.asStateFlow()
    fun setWorkProfile(profile: com.craftflowtechnologies.meetingmind.core.work.WorkProfile) { _workProfile.value = profile }

    /** Internet mode (Gemini) by default; the offline pack is the on-device alternative (decision D2). */
    private val _setup = MutableStateFlow(SetupChoice.INTERNET)
    val setup: StateFlow<SetupChoice> = _setup.asStateFlow()
    fun setSetup(choice: SetupChoice) { _setup.value = choice }
    /** A Gemini key typed during setup, saved when setup finishes. */
    private val _geminiKey = MutableStateFlow("")
    val geminiKey: StateFlow<String> = _geminiKey.asStateFlow()
    fun setGeminiKey(key: String) { _geminiKey.value = key.trim() }
    val hasBuiltInKey: Boolean get() = com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore.systemKey != null

    private val _wifiOnly = MutableStateFlow(false)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()
    fun setWifiOnly(value: Boolean) { _wifiOnly.value = value }

    /** The Bible to keep on the phone, picked from the free catalogue (which needs a connection the first time). */
    private val _bible = MutableStateFlow<com.craftflowtechnologies.meetingmind.core.scripture.HelloAoTranslation?>(null)
    val bible: StateFlow<com.craftflowtechnologies.meetingmind.core.scripture.HelloAoTranslation?> = _bible.asStateFlow()
    private val _catalog = MutableStateFlow<List<com.craftflowtechnologies.meetingmind.core.scripture.HelloAoTranslation>?>(null)
    /** Null while loading; empty when it couldn't be loaded. */
    val catalog: StateFlow<List<com.craftflowtechnologies.meetingmind.core.scripture.HelloAoTranslation>?> = _catalog.asStateFlow()
    fun setBible(t: com.craftflowtechnologies.meetingmind.core.scripture.HelloAoTranslation?) { _bible.value = t }
    fun loadCatalog() {
        if (_catalog.value?.isNotEmpty() == true) return
        _catalog.value = null
        viewModelScope.launch {
            _catalog.value = runCatching { com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService.library(getApplication()).helloAoCatalog() }.getOrDefault(emptyList())
        }
    }

    /** What the offline pack is on this phone: each job's model and size. */
    val pack = SetupPart.entries.map { it to SetupGuide.modelsFor(it, deviceCapabilities.totalRamGb) }
    val packBytes: Long = pack.sumOf { (_, models) -> models.sumOf { it.sizeBytes } }

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            prefs.setSelectedAsrModel(_selectedModel.value)
            prefs.setUserName(_userName.value)
            prefs.setSpaces(_spaces.value)
            prefs.setLook(_look.value)
            if (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK in _spaces.value) {
                prefs.setWorkSettings(com.craftflowtechnologies.meetingmind.core.work.WorkSettings.forProfile(_workProfile.value ?: com.craftflowtechnologies.meetingmind.core.work.WorkProfile.GENERAL))
            }
            prefs.setWifiOnlyDownload(_wifiOnly.value)
            _bible.value?.let { t ->
                // Becomes the reading Bible now, and downloads in the background for offline use.
                prefs.setBibleVersionId(t.intId)
                com.craftflowtechnologies.meetingmind.core.scripture.BibleDownloadWorker.enqueue(getApplication(), t.intId, _wifiOnly.value)
            }
            when (_setup.value) {
                SetupChoice.OFFLINE_PACK -> runCatching {
                    val app = getApplication<Application>()
                    SetupGuide.downloadMissing(app, SetupGuide.observe(app).first(), _wifiOnly.value)
                }
                SetupChoice.INTERNET -> {
                    prefs.setProcessingProfile(ProcessingProfile.INTERNET)
                    _geminiKey.value.takeIf { it.isNotBlank() }?.let {
                        com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore(getApplication<Application>()).setApiKey(it)
                    }
                }
                SetupChoice.LATER -> Unit
            }
            prefs.setOnboardingCompleted(true)
            onCompleted()
        }
    }
}

private const val STEPS = 7

/**
 * First run: a warm welcome in the brand's navy, then seven short steps — what it does, your name,
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
    val workProfile by viewModel.workProfile.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val bible by viewModel.bible.collectAsState()

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
                        3 -> SpacesStep(spaces, viewModel::setSpaces, look, viewModel::setLook, workProfile, viewModel::setWorkProfile)
                        4 -> SetupStep(viewModel, setup, viewModel::setSetup, wifiOnly, viewModel::setWifiOnly)
                        5 -> BibleStep(viewModel)
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
                            when (step) { 0 -> "Get started"; STEPS - 1 -> "Start using MeetingMind"; 2 -> if (userName.isBlank()) "Skip" else "Continue"; 5 -> if (bible == null) "Skip for now" else "Download and continue"; else -> "Continue" },
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
            "Remember every conversation.\nFast, accurate notes with Gemini — or keep everything on your phone.",
            color = Color.White.copy(alpha = 0.72f), fontSize = 18.sp, lineHeight = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)
        )
        Row(Modifier.padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(Icons.Filled.Lock, "Private by choice")
            Pill(Icons.Filled.PhoneAndroid, "Offline option")
            Pill(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, "AI notes")
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(SurfaceBase.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Feature(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, "Transcripts and summaries", "Who said what, the decisions, the action items. Ask questions about any recording.")
    Feature(Icons.Filled.EditNote, "Notes that connect", "Write, add photos and scripture. Recordings become notes you can share or export.")
    Feature(Icons.Filled.CalendarMonth, "Your day at a glance", "Today shows what's next, preps you for meetings and keeps everything on a timeline.")
}

@Composable
private fun Feature(icon: ImageVector, title: String, line: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceBase.copy(alpha = 0.06f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
    spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>, onSpaces: (Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>) -> Unit,
    look: com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel, onLook: (com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel) -> Unit,
    workProfile: com.craftflowtechnologies.meetingmind.core.work.WorkProfile?, onWorkProfile: (com.craftflowtechnologies.meetingmind.core.work.WorkProfile) -> Unit
) {
    StepTitle("What's it for?", "Pick what you'll use it for — the app shows only those. Change it any time in Settings.")
    com.craftflowtechnologies.meetingmind.core.identity.SpacesPicker(spaces, onSpaces)
    if (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK in spaces) {
        Text("What kind of work?", color = com.craftflowtechnologies.meetingmind.ui.theme.Briefing.OnBrief, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
        Text("Sets the words, templates and privacy. Doctors and lawyers get on-device only.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.craftflowtechnologies.meetingmind.core.work.WorkProfile.entries.forEach { p ->
                val on = p == workProfile
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (on) com.craftflowtechnologies.meetingmind.ui.theme.Briefing.OnBrief else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { onWorkProfile(p) }.testTag("work_profile_${p.name}")
                ) {
                    Text(p.label, color = if (on) Color.Black else com.craftflowtechnologies.meetingmind.ui.theme.Briefing.OnBrief, fontSize = 13.5.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        workProfile?.let { Text(it.description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
    Text("How should it feel?", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
    com.craftflowtechnologies.meetingmind.core.identity.LookPicker(look, onLook)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SetupStep(vm: OnboardingViewModel, choice: SetupChoice, onChoice: (SetupChoice) -> Unit, wifiOnly: Boolean, onWifiOnly: (Boolean) -> Unit) {
    StepTitle("How should the AI run?", "Recording always works. Turning recordings into transcripts and summaries needs a little AI setup.")
    ChoiceCard(
        selected = choice == SetupChoice.INTERNET, onClick = { onChoice(SetupChoice.INTERNET) },
        icon = Icons.Filled.Cloud, title = "Internet mode", badge = "Recommended",
        line = "Fast, accurate notes with Google's Gemini. Needs a connection: recordings are sent to Google Gemini for processing.",
        tag = "setup_choice_internet"
    ) {
        if (choice == SetupChoice.INTERNET) {
            val key by vm.geminiKey.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current
            Column(Modifier.padding(top = 10.dp)) {
                OutlinedTextField(
                    value = key, onValueChange = vm::setGeminiKey, singleLine = true,
                    placeholder = { Text(if (vm.hasBuiltInKey) "Optional — a tester key is built in" else "Paste your Gemini API key") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("setup_gemini_key")
                )
                Text(
                    "Get a free key at aistudio.google.com/apikey",
                    color = Brand.Cyan, fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey"))) }
                    }
                )
            }
        }
    }
    ChoiceCard(
        selected = choice == SetupChoice.OFFLINE_PACK, onClick = { onChoice(SetupChoice.OFFLINE_PACK) },
        icon = Icons.Filled.PhoneAndroid, title = "Offline pack", badge = "Most private",
        line = "Everything stays on the phone. Three pieces, one download (${SetupGuide.formatBytes(vm.packBytes)}) that keeps going in the background.",
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
            .background(SurfaceBase.copy(alpha = if (selected) 0.1f else 0.05f))
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

/** Text on the onboarding's navy: always white, whatever the app theme. */
private val OnNavy = Color.White.copy(alpha = 1f)

/** Pick a Bible to keep offline: a few well-known ones first, then a search across the whole catalogue. */
@Composable
private fun BibleStep(vm: OnboardingViewModel) {
    val catalog by vm.catalog.collectAsState()
    val chosen by vm.bible.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadCatalog() }
    StepTitle("Keep a Bible on your phone", "Read, search and study Scripture with no signal. Pick one now — it downloads in the background, and you can add more later in the Bible.")
    val list = catalog
    when {
        list == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = Brand.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Text("  Loading the list…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        list.isEmpty() -> {
            Text("The list needs a connection the first time. You can skip this and pick a Bible later in Faith → Bible.", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 21.sp)
            Text("Try again", color = Brand.Cyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.loadCatalog() }.padding(vertical = 12.dp))
        }
        else -> {
            val q = query.trim().lowercase()
            val popular = listOf("BSB", "WEB", "KJV", "ASV", "ENGWEBP", "ENGKJV", "eng_kjv")
            val shown = if (q.isEmpty()) {
                list.filter { t -> t.language == "eng" && t.books >= 66 }.sortedWith(compareBy({ popular.indexOf(it.shortName).let { i -> if (i < 0) 99 else i } }, { it.shortName })).take(8)
            } else {
                list.filter { t -> listOf(t.name, t.englishName, t.shortName, t.languageName, t.language).any { it.lowercase().contains(q) } }
                    .sortedWith(compareBy({ it.language != "eng" }, { it.languageName }, { it.shortName })).take(30)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search a language or version — Spanish, Swahili, KJV…", color = Color.White.copy(alpha = 0.35f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand.Cyan, unfocusedBorderColor = Color.White.copy(alpha = 0.2f), cursorColor = Brand.Cyan,
                    focusedTextColor = OnNavy, unfocusedTextColor = OnNavy
                ),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("onboarding_bible_search")
            )
            Spacer(Modifier.height(10.dp))
            shown.forEach { t ->
                val on = chosen?.id == t.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                        .background(SurfaceBase.copy(alpha = if (on) 0.1f else 0.05f))
                        .border(if (on) 1.5.dp else 1.dp, if (on) Brand.Cyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .clickable { vm.setBible(if (on) null else t) }.padding(14.dp).testTag("bible_${t.shortName}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${t.shortName} · ${t.languageName}", color = OnNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(t.name + if (t.books < 66) " · ${t.books} books" else "", color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (on) Icon(Icons.Filled.Check, contentDescription = "Chosen", tint = Brand.Cyan)
                }
            }
            if (q.isEmpty()) Text("Search above for another language or version — over a thousand are free.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    val needsNotify = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notify by remember { mutableStateOf(!needsNotify || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    // Android reports "don't ask again" only as "no rationale to show" right after a refusal, so a
    // refusal with no rationale means the system will not show the dialog again: send them to settings.
    var micDenied by remember { mutableStateOf(false) }
    var notifyDenied by remember { mutableStateOf(false) }
    val activity = context.findActivity()
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        mic = it
        micDenied = !it && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notify = it
        notifyDenied = !it && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    }

    StepTitle("Two quick permissions", "So recording and reminders work when you need them. You can change these in Android settings.")
    PermissionRow(Icons.Filled.Mic, "Microphone", "To record. In Internet mode, recordings are sent to Google Gemini; the offline pack keeps audio on this phone.", mic, "perm_mic", micDenied) {
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    PermissionRow(Icons.Filled.Notifications, "Notifications", "To tell you when a transcript is ready, setup has finished, or your devotional arrives.", notify, "perm_notify", notifyDenied) {
        if (needsNotify) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Text(
        "After this, a short tour shows you around Home.",
        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp)
    )
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, line: String, granted: Boolean, tag: String, permanentlyDenied: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceBase.copy(alpha = 0.06f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
        } else {
            val context = LocalContext.current
            // Once permanently denied, Allow would do nothing: open this app's settings page instead.
            val label = if (permanentlyDenied) "Open settings" else "Allow"
            val action: () -> Unit = if (permanentlyDenied) {
                {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
                }
            } else onAllow
            Text(label, color = Brand.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Brand.Cyan.copy(alpha = 0.6f), RoundedCornerShape(50)).clickable(onClick = action).padding(horizontal = 14.dp, vertical = 7.dp).testTag(tag))
        }
    }
}
