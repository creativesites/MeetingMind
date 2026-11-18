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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.res.stringResource
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
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsStore
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.DeviceCapabilities
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.setup.SetupGuide
import com.craftflowtechnologies.meetingmind.core.setup.SetupPart
import com.craftflowtechnologies.meetingmind.core.i18n.AppLanguage
import com.craftflowtechnologies.meetingmind.core.i18n.AppLocaleManager
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How MeetingMind thinks, chosen on the setup step. Internet is pre-selected and always saved (decision D1). */
enum class SetupChoice { INTERNET, ON_DEVICE }

class OnboardingViewModel(
    application: Application,
    private val companionSettings: CompanionSettingsSource,
    /** The moments ledger for the hello First Keep; null is the app's store (tests pass their own). */
    private val momentStore: com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore? = null
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, CompanionSettingsStore(application))

    private val prefs = UserPreferencesManager(application)

    val appLanguage: StateFlow<String?> = prefs.preferencesFlow
        .map { it.appLanguage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setAppLanguage(tag: String?) {
        viewModelScope.launch {
            // Save first: applying can recreate the activity, which must then read the new value.
            prefs.setAppLanguage(tag)
            AppLocaleManager.applyLocale(getApplication(), tag)
        }
    }

    /** Who keeps the person company (Z-15). Zuri is pre-selected; null is "No companion". Saved on Continue. */
    private val _companion = MutableStateFlow<CompanionForm?>(CompanionForm.ZURI)
    val companion: StateFlow<CompanionForm?> = _companion.asStateFlow()
    fun setCompanion(form: CompanionForm?) { _companion.value = form }

    /** Writes the companion choice to the settings store; runs when the step's Continue is pressed. */
    fun commitCompanion() {
        val form = _companion.value
        viewModelScope.launch { runCatching { companionSettings.setForm(form) } }
    }

    val deviceCapabilities: DeviceCapabilities = DeviceCapabilityDetector.detect(application)

    /** What the person typed for the companion to call them. Never pre-filled from the device or an account
     * (Phase 15 §5). Staying blank is a valid choice. */
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()
    fun setUserName(name: String) { _userName.value = name }

    // Where the person is in the flow. Saved on every move, so a killed app resumes here.
    private val _step = MutableStateFlow(OnboardingStep.WELCOME)
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()
    private val _route = MutableStateFlow(EntryRoute.SAMPLE)
    val route: StateFlow<EntryRoute> = _route.asStateFlow()
    private val _sampleKind = MutableStateFlow(SampleKind.MEETING)
    val sampleKind: StateFlow<SampleKind> = _sampleKind.asStateFlow()
    private var sampleKindTouched = false
    private var moved = false

    /** The four spaces, all on by default (founder decision, 2026-11-17). */
    private val _spaces = MutableStateFlow(OnboardingSpaces.toSet())
    val spaces: StateFlow<Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>> = _spaces.asStateFlow()

    private fun restore() {
        viewModelScope.launch {
            // An unreadable store just means a fresh start, never a crash.
            val saved = runCatching { prefs.preferencesFlow.first() }.getOrNull() ?: return@launch
            // Only resume an unfinished flow of this design, and never after the person has already moved.
            if (saved.onboardingCompleted || moved || saved.onboardingVersion != ONBOARDING_VERSION) return@launch
            _route.value = EntryRoute.entries.firstOrNull { it.name == saved.onboardingEntryRoute } ?: EntryRoute.SAMPLE
            // The spaces travel in the progress record's "goals" slot (names of NotebookSpace).
            val savedSpaces = OnboardingSpaces.filter { it.name in saved.onboardingGoals }.toSet()
            if (savedSpaces.isNotEmpty()) applySpaces(savedSpaces)
            val resumed = restoredStep(saved.onboardingStep, saved.onboardingVersion)
            val steps = stepsFor(_route.value)
            _step.value = if (resumed in steps) resumed else OnboardingStep.WELCOME
            // Past the companion step, the pick was already saved on its Continue: show that one again.
            if (steps.indexOf(_step.value) > steps.indexOf(OnboardingStep.COMPANION)) {
                _companion.value = runCatching { companionSettings.settings.first().form }.getOrDefault(_companion.value)
            }
        }
    }

    private fun goTo(step: OnboardingStep) { moved = true; _step.value = step; persist() }

    private fun persist() {
        val step = _step.value.name
        val route = _route.value.name
        val spaces = _spaces.value.map { it.name }.toSet()
        viewModelScope.launch { runCatching { prefs.saveOnboardingProgress(ONBOARDING_VERSION, step, route, spaces, emptySet()) } }
    }

    /** "Get started": pick a companion first, then the spaces and the example. */
    fun startSample() { _route.value = EntryRoute.SAMPLE; goTo(OnboardingStep.COMPANION) }

    /**
     * "Record something now": pick a companion (unless that's done already, as when leaving the example),
     * then the AI choice, then the recorder.
     */
    fun startRecordNow() {
        val pickedAlready = _step.value != OnboardingStep.WELCOME && _step.value != OnboardingStep.COMPANION
        _route.value = EntryRoute.RECORD
        goTo(if (pickedAlready) OnboardingStep.SETUP else OnboardingStep.COMPANION)
    }

    fun setSampleKind(kind: SampleKind) { sampleKindTouched = true; _sampleKind.value = kind }

    fun toggleSpace(space: com.craftflowtechnologies.meetingmind.core.model.NotebookSpace) {
        applySpaces(com.craftflowtechnologies.meetingmind.feature.onboarding.toggleSpace(_spaces.value, space))
        persist()
    }

    private fun applySpaces(spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>) {
        _spaces.value = spaces
        // The example follows the spaces until the person picks one themselves.
        if (!sampleKindTouched) _sampleKind.value = sampleKindFor(spaces)
    }

    /**
     * The hello First Keep, Zuri's first reveal (signature moments §2.2, decision 1): when the example's notes
     * appear, the sample note gets its own once-ever First Keep (ledger key `peak.first.hello`), so the person
     * meets the moment every one of their recordings will get. Nothing is recorded. It plays at most once.
     */
    private val _hello = MutableStateFlow<com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan.FirstKeep?>(null)
    val hello: StateFlow<com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan.FirstKeep?> = _hello.asStateFlow()
    private var helloPlanned = false
    /** Set when Continue on the example starts the reveal: closing it then moves on. */
    private var continueAfterHello: (() -> Unit)? = null

    /** True when [playHello] has nothing left to show (it ran, or the moment was already used). */
    val helloDone: Boolean get() = helloPlanned

    fun playHello(kind: SampleKind, then: (() -> Unit)? = null) {
        if (helloPlanned) { then?.invoke(); return }
        helloPlanned = true
        continueAfterHello = then
        val app = getApplication<Application>()
        viewModelScope.launch {
            val plan = runCatching {
                val now = System.currentTimeMillis()
                val clock = com.craftflowtechnologies.meetingmind.core.companion.LocalClock(now, java.util.TimeZone.getDefault().getOffset(now).toLong())
                val space = when (kind) {
                    SampleKind.SERMON -> com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH
                    SampleKind.LECTURE -> com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING
                    SampleKind.MEETING -> com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK
                }
                val store = momentStore ?: com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore(app)
                store.issueTicket(com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilTicket.hello(SampleNoteId, now, space).copy(sermon = kind == SampleKind.SERMON))
                store.planOpen(SampleNoteId, clock) { ticket, ledger, rotation ->
                    com.craftflowtechnologies.meetingmind.core.companion.moment.MomentFacts(
                        noteId = SampleNoteId, ticket = ticket, space = space, sermon = kind == SampleKind.SERMON,
                        device = com.craftflowtechnologies.meetingmind.core.companion.moment.MomentEnvironment.read(app),
                        companionShown = _companion.value != null, clock = clock, ledger = ledger, rotation = rotation
                    )
                }.plan
            }.getOrNull()
            val firstKeep = plan as? com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan.FirstKeep
            _hello.value = firstKeep
            // Nothing to show (already used on this phone): carry straight on.
            if (firstKeep == null) continueAfterHello?.let { continueAfterHello = null; it() }
        }
    }

    fun closeHello() {
        _hello.value = null
        continueAfterHello?.let { continueAfterHello = null; it() }
    }

    fun next(steps: List<OnboardingStep>, finish: () -> Unit) {
        if (_step.value == OnboardingStep.COMPANION) commitCompanion()
        val advance = {
            val i = steps.indexOf(_step.value)
            if (i < 0 || i >= steps.lastIndex) completeOnboarding(finish) else goTo(steps[i + 1])
        }
        // Zuri's first reveal is part of onboarding: leaving the example before its notes appeared still plays it.
        if (_step.value == OnboardingStep.SAMPLE && !helloPlanned) playHello(_sampleKind.value, then = advance) else advance()
    }

    fun back(steps: List<OnboardingStep>) {
        val i = steps.indexOf(_step.value)
        if (i > 0) goTo(steps[i - 1])
    }

    /** Internet is pre-selected (decision D1); on this phone means a guided setup right after onboarding. */
    private val _setup = MutableStateFlow(SetupChoice.INTERNET)
    val setup: StateFlow<SetupChoice> = _setup.asStateFlow()
    fun setSetup(choice: SetupChoice) { _setup.value = choice }

    /** The on-phone download for this phone, as the setup guide would fetch it. Shown as a size, never a catalogue. */
    val packBytes: Long = SetupPart.entries.sumOf { part -> SetupGuide.modelsFor(part, deviceCapabilities.totalRamGb).sumOf { it.sizeBytes } }

    private fun lookFor(spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>) =
        // Faith on its own (or with Personal) suggests the warm look; everything else the professional one.
        if (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH in spaces &&
            spaces.all { it == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH || it == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL })
            com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.SANCTUARY
        else com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.PROFESSIONAL

    fun completeOnboarding(onCompleted: () -> Unit) {
        viewModelScope.launch {
            // A failed save (full or unreadable storage) must never crash or trap the person in onboarding:
            // they still go on, and anything not saved falls back to its default.
            runCatching {
                companionSettings.setForm(_companion.value)
                // The recommended models for this phone, so the on-device setup and the AI engine agree from the start.
                prefs.setSelectedAsrModel(deviceCapabilities.recommendedAsrModelId)
                prefs.setSelectedLlmModel(DeviceCapabilityDetector.recommendedLlmModelId(deviceCapabilities.totalRamGb))
                prefs.setUserName(_userName.value)
                prefs.setSpaces(_spaces.value)
                prefs.setLook(lookFor(_spaces.value))
                if (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK in _spaces.value) {
                    // The kind of work is asked when Work is first opened; until then the general set.
                    prefs.setWorkSettings(com.craftflowtechnologies.meetingmind.core.work.WorkSettings.forProfile(com.craftflowtechnologies.meetingmind.core.work.WorkProfile.GENERAL))
                }
                // The devotional and faith reminders follow the spaces just chosen.
                runCatching { com.craftflowtechnologies.meetingmind.core.navigation.SpaceSchedules.sync(getApplication()) }
                // The choice is always saved, so the stored mode is never left at a default the person didn't pick.
                prefs.setProcessingProfile(if (_setup.value == SetupChoice.ON_DEVICE) ProcessingProfile.OFFLINE else ProcessingProfile.INTERNET)
                // Large on-phone downloads wait for Wi-Fi unless the person changes it (decision D7).
                if (_setup.value == SetupChoice.ON_DEVICE) prefs.setWifiOnlyDownload(true)
            }
            runCatching { prefs.setOnboardingCompleted(true) }
            onCompleted()
        }
    }

    // Last, so every field it fills is already initialised.
    init { restore() }
}

/**
 * What the "Choose how you read" step does to the devotional profile: a plan sets the source to a plan and its id;
 * "Write one with AI" sets the source to AI (Morning and Evening stays as the fallback). Nothing else changes.
 */
fun commitReading(profile: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile, choice: String) =
    if (choice == PlanChoice.AI) profile.copy(source = com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSource.AI)
    else profile.copy(source = com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSource.CLASSIC, planId = choice)

/**
 * First run: a welcome that offers an example or the recorder, then step 1 of both routes, "Pick who keeps
 * you company" (ZURI_EXPERIENCE §5.1). On the example route come the example itself (with the hello First
 * Keep), what the person captures, how the AI runs, the Bible and reading steps for Faith, and the two
 * permissions that matter. The record route goes from the companion to the AI choice and then the
 * recorder. Every move is saved, so a killed app resumes where it was. Everything can be changed later.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinishOnboarding: () -> Unit,
    onRecordNow: () -> Unit = onFinishOnboarding,
    /** Picked "On this phone": onboarding ends on the guided on-device setup instead of Home. */
    onSetUpOnDevice: () -> Unit = onFinishOnboarding
) {
    val step by viewModel.step.collectAsState()
    val route by viewModel.route.collectAsState()
    var forward by remember { mutableStateOf(true) }
    val userName by viewModel.userName.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val sampleKind by viewModel.sampleKind.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val companion by viewModel.companion.collectAsState()
    val steps = stepsFor(route)
    val index = steps.indexOf(step).coerceAtLeast(0)
    val finish = when {
        route == EntryRoute.RECORD -> onRecordNow
        setup == SetupChoice.ON_DEVICE -> onSetUpOnDevice
        else -> onFinishOnboarding
    }
    fun next() { forward = true; viewModel.next(steps, finish) }
    fun back() { forward = false; viewModel.back(steps) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Navy, Brand.NavyLift, Brand.Navy))).testTag("onboarding")) {
        // The icon's light, softly behind everything.
        Box(Modifier.size(420.dp).align(Alignment.TopEnd).graphicsLayer { translationX = 160f; translationY = -120f }
            .background(Brush.radialGradient(listOf(Brand.Violet.copy(alpha = 0.22f), Color.Transparent)), CircleShape))
        Box(Modifier.size(380.dp).align(Alignment.BottomStart).graphicsLayer { translationX = -160f; translationY = 120f }
            .background(Brush.radialGradient(listOf(Brand.Cyan.copy(alpha = 0.14f), Color.Transparent)), CircleShape))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (step == OnboardingStep.WELCOME) {
                var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
                val currentLanguageTag by viewModel.appLanguage.collectAsState()
                val activeLang = AppLanguage.fromTag(currentLanguageTag)
                val displayLabel = activeLang?.nativeName ?: stringResource(R.string.settings_language_follow_system)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                            .clickable { showLanguagePicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌐 $displayLabel ▾",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (showLanguagePicker) {
                    OnboardingLanguageDialog(
                        currentTag = currentLanguageTag,
                        onSelect = { tag ->
                            viewModel.setAppLanguage(tag)
                            showLanguagePicker = false
                        },
                        onDismiss = { showLanguagePicker = false }
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat((steps.size - 1).coerceAtLeast(1)) { i ->
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i < index) Brush.horizontalGradient(Brand.sweep) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.15f)))))
                    }
                }
                val activeCompanion = companion
                if (step != OnboardingStep.COMPANION && activeCompanion != null) {
                    OnboardingCompanionGuideBar(step = step, form = activeCompanion, userName = userName)
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
                        OnboardingStep.WELCOME -> Welcome()
                        OnboardingStep.COMPANION -> {
                            CompanionPickStep(companion, viewModel::setCompanion)
                            CompanionNameField(companion, userName, viewModel::setUserName)
                        }
                        OnboardingStep.SPACES -> SpacesStep(spaces, viewModel::toggleSpace)
                        OnboardingStep.SAMPLE -> SampleStep(
                            kind = sampleKind, onKind = viewModel::setSampleKind,
                            onViewed = { viewModel.playHello(sampleKind) },
                            onRecordInstead = { forward = true; viewModel.startRecordNow() }
                        )
                        OnboardingStep.SETUP -> SetupStep(viewModel.packBytes, setup, viewModel::setSetup)
                    }
                }
            }
            if (step == OnboardingStep.WELCOME) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GradientButton(stringResource(R.string.onboarding_button_get_started), "onboarding_next_btn", Modifier.fillMaxWidth()) { forward = true; viewModel.startSample() }
                    Text(
                        stringResource(R.string.onboarding_button_record_now), color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { forward = true; viewModel.startRecordNow() }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("onboarding_record_now_btn")
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.action_back), color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { back() }.padding(vertical = 12.dp, horizontal = 4.dp).testTag("onboarding_back_btn"))
                    Spacer(Modifier.weight(1f))
                    val last = index >= steps.lastIndex
                    GradientButton(
                        when {
                            step == OnboardingStep.SAMPLE -> stringResource(R.string.onboarding_button_make_it_mine)
                            last && route == EntryRoute.RECORD -> stringResource(R.string.onboarding_button_start_recording)
                            last && setup == SetupChoice.ON_DEVICE -> stringResource(R.string.onboarding_button_set_up_phone)
                            last -> stringResource(R.string.onboarding_button_start_using)
                            else -> stringResource(R.string.onboarding_button_continue)
                        },
                        "onboarding_next_btn"
                    ) { next() }
                }
            }
        }
        val hello by viewModel.hello.collectAsState()
        hello?.let { plan ->
            val sample = SampleContent.of(sampleKind)
            com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.FirstKeepOverlay(
                plan = plan,
                form = companion,
                note = com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment.FirstKeepNote(sample.title, "${sample.length} · ${stringResource(R.string.companion_moment_hello_example)}", sample.actions.take(3)),
                headline = stringResource(R.string.companion_moment_hello_headline),
                subline = stringResource(R.string.companion_moment_hello_sub),
                openLabel = stringResource(R.string.companion_moment_open_note),
                shareLabel = stringResource(R.string.companion_moment_hello_record),
                announcement = stringResource(R.string.companion_moment_hello_headline),
                key = "firstkeep:$SampleNoteId",
                onOpen = viewModel::closeHello,
                onShare = { viewModel.closeHello(); forward = true; viewModel.startRecordNow() }
            )
        }
    }
}

/** The onboarding example's id for the moments ledger and ticket. It is never a real note. */
internal const val SampleNoteId = "onboarding.sample"

@Composable
private fun OnboardingCompanionGuideBar(step: OnboardingStep, form: CompanionForm, userName: String) {
    val companionName = stringResource(CompanionRoster.displayName(form))
    val state = when (step) {
        OnboardingStep.SAMPLE -> com.craftflowtechnologies.meetingmind.core.companion.CompanionState.LISTENING
        OnboardingStep.SETUP -> com.craftflowtechnologies.meetingmind.core.companion.CompanionState.THINKING
        else -> com.craftflowtechnologies.meetingmind.core.companion.CompanionState.IDLE
    }
    val variant = when (step) {
        OnboardingStep.SPACES -> com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant.Nod
        else -> com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant.Normal
    }
    val line = when (step) {
        OnboardingStep.SPACES -> if (userName.isNotBlank()) stringResource(R.string.onboarding_guide_spaces_named, userName) else stringResource(R.string.onboarding_guide_spaces)
        OnboardingStep.SAMPLE -> stringResource(R.string.onboarding_guide_sample, companionName)
        OnboardingStep.SETUP -> stringResource(R.string.onboarding_guide_setup)
        else -> ""
    }
    if (line.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(SurfaceBase.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp).testTag("onboarding_companion_guide"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion(form = form, state = state, size = 34.dp, variant = variant)
        Spacer(Modifier.width(10.dp))
        Text(line, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun GradientButton(label: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Color.Transparent, modifier = modifier.testTag(tag)) {
        Row(Modifier.background(Brush.horizontalGradient(listOf(Brand.Cyan, Brand.Indigo, Brand.Violet))).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Welcome() {
    val t = rememberInfiniteTransition(label = "glow")
    val glow by t.animateFloat(0.85f, 1.12f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "g")
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The launcher icon on the brand's soft light.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            Box(Modifier.size(160.dp).graphicsLayer { scaleX = glow; scaleY = glow }.background(Brush.radialGradient(listOf(Brand.Indigo.copy(alpha = 0.45f), Color.Transparent)), CircleShape))
            com.craftflowtechnologies.meetingmind.core.ui.AppIconTile(size = 104.dp)
        }
        Text("MeetingMind", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 4.dp))
        Text(stringResource(R.string.onboarding_headline), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, lineHeight = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        Text(
            stringResource(R.string.onboarding_body),
            color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
        )
        // Wraps to a second line on narrow screens instead of squeezing a pill into a column of letters.
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill(Icons.Filled.Lock, stringResource(R.string.onboarding_pill_private), "welcome_pill_private")
            Pill(Icons.Filled.PhoneAndroid, stringResource(R.string.onboarding_pill_offline), "welcome_pill_offline")
            Pill(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, stringResource(R.string.onboarding_pill_ai), "welcome_pill_ai")
        }
    }
}

@Composable
private fun OnboardingLanguageDialog(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceBase)
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = com.craftflowtechnologies.meetingmind.ui.theme.Ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_language_asr_notice),
                fontSize = 12.5.sp,
                color = com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // System default option
                val isDefault = currentTag.isNullOrBlank()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDefault) com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised else Color.Transparent)
                        .clickable { onSelect(null) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_language_follow_system),
                            fontSize = 15.sp,
                            fontWeight = if (isDefault) FontWeight.SemiBold else FontWeight.Normal,
                            color = com.craftflowtechnologies.meetingmind.ui.theme.Ink
                        )
                        Text(
                            text = stringResource(R.string.settings_language_follow_system_summary),
                            fontSize = 12.sp,
                            color = com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
                        )
                    }
                    if (isDefault) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = com.craftflowtechnologies.meetingmind.ui.theme.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                AppLanguage.SUPPORTED.forEach { lang ->
                    val isSelected = !isDefault && currentTag.equals(lang.tag, ignoreCase = true)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised else Color.Transparent)
                            .clickable { onSelect(lang.tag) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = lang.nativeName,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = com.craftflowtechnologies.meetingmind.ui.theme.Ink
                            )
                            Text(
                                text = lang.englishName,
                                fontSize = 12.sp,
                                color = com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = com.craftflowtechnologies.meetingmind.ui.theme.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.action_done),
                        color = com.craftflowtechnologies.meetingmind.ui.theme.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, tag: String) {
    Row(Modifier.testTag(tag).clip(RoundedCornerShape(50)).background(SurfaceBase.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Brand.Cyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun StepTitle(title: String, line: String) {
    Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, lineHeight = 34.sp, modifier = Modifier.padding(top = 12.dp))
    Text(line, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
}

/**
 * How MeetingMind thinks (design 1d). Two plain choices with their real trade-offs, Internet pre-selected
 * (decision D1). No model catalogue here: "On this phone" hands over to the guided setup after onboarding.
 * The backup text provider is disclosed (decision D10): text, never audio, and only in Internet mode.
 */
@Composable
private fun SetupStep(packBytes: Long, choice: SetupChoice, onChoice: (SetupChoice) -> Unit) {
    var compare by rememberSaveable { mutableStateOf(false) }
    StepTitle(stringResource(R.string.onboarding_setup_title), stringResource(R.string.onboarding_setup_line))
    ChoiceCard(
        selected = choice == SetupChoice.INTERNET, onClick = { onChoice(SetupChoice.INTERNET) },
        icon = Icons.Filled.Cloud, title = stringResource(R.string.onboarding_setup_internet),
        badge = stringResource(R.string.onboarding_setup_internet_badge),
        line = stringResource(R.string.onboarding_setup_internet_line),
        tag = "setup_choice_internet", alwaysShowExtra = true
    ) {
        SetupFacts(
            stringResource(R.string.onboarding_setup_internet_fact_1),
            stringResource(R.string.onboarding_setup_internet_fact_2),
            stringResource(R.string.onboarding_setup_internet_fact_3)
        )
    }
    ChoiceCard(
        selected = choice == SetupChoice.ON_DEVICE, onClick = { onChoice(SetupChoice.ON_DEVICE) },
        icon = Icons.Filled.PhoneAndroid, title = stringResource(R.string.onboarding_setup_device),
        badge = stringResource(R.string.onboarding_setup_device_badge),
        line = stringResource(R.string.onboarding_setup_device_line),
        tag = "setup_choice_device", alwaysShowExtra = true
    ) {
        SetupFacts(
            stringResource(R.string.onboarding_setup_device_fact_1),
            stringResource(R.string.onboarding_setup_device_fact_2, SetupGuide.formatBytes(packBytes)),
            stringResource(R.string.onboarding_setup_device_fact_3)
        )
    }
    Text(
        stringResource(if (compare) R.string.onboarding_setup_compare_hide else R.string.onboarding_setup_compare),
        color = Brand.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
            .clickable { compare = !compare }.padding(vertical = 12.dp).testTag("setup_compare")
    )
    androidx.compose.animation.AnimatedVisibility(visible = compare) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceBase.copy(alpha = 0.06f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.onboarding_setup_always_online), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
            Text(stringResource(R.string.onboarding_setup_backup_disclosure), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
            Text(stringResource(R.string.onboarding_setup_confidential), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
    Text(
        stringResource(R.string.onboarding_setup_change_later), color = Color.White.copy(alpha = 0.5f), fontSize = 12.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun SetupFacts(vararg facts: String) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        facts.forEach { fact ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Brand.Cyan, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(fact, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ChoiceCard(selected: Boolean, onClick: () -> Unit, icon: ImageVector, title: String, badge: String?, line: String, tag: String, alwaysShowExtra: Boolean = false, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(SurfaceBase.copy(alpha = if (selected) 0.1f else 0.05f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Brush.linearGradient(Brand.sweep) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(16.dp).testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) Brand.Cyan else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            badge?.let { Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Brand.Blue, Brand.Violet))).padding(horizontal = 9.dp, vertical = 3.dp)) }
        }
        Text(line, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        if (selected || alwaysShowExtra) extra()
    }
}

/** Text on the onboarding's navy: always white, whatever the app theme. */
internal val OnNavy = Color.White.copy(alpha = 1f)
