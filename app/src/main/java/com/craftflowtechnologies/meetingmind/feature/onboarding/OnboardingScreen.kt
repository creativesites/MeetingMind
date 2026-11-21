package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.core.common.DeviceCapabilityDetector
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionRoster
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsSource
import com.craftflowtechnologies.meetingmind.core.companion.CompanionSettingsStore
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.i18n.AppLanguage
import com.craftflowtechnologies.meetingmind.core.i18n.AppLocaleManager
import com.craftflowtechnologies.meetingmind.core.model.DeviceCapabilities
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.setup.SetupGuide
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
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

    /** The phone's free storage in bytes, or null when it can't be read. Read once: onboarding is short. */
    private val freeBytes: Long? = runCatching { DeviceCapabilityDetector.getAvailableStorageMb() }.getOrNull()
        ?.takeIf { it > 0 }?.let { it * 1_048_576L }

    /**
     * The models already on the phone (a reinstall can keep them). Read through the same storage the setup guide uses,
     * so the pack never counts them again and the saved speech model never replaces one.
     */
    private val modelStorage = com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(application)
    private fun isInstalled(id: String): Boolean = runCatching { modelStorage.isInstalled(id) }.getOrDefault(false)

    /** The language recordings will most likely be in: the app language picked here, else the phone's. */
    private fun startingRecordingLanguage(tag: String? = appLanguage.value): String? =
        recordingLanguageFor(tag ?: java.util.Locale.getDefault().language)

    /**
     * The on-phone download for this phone and language, as the setup guide will fetch it. Shown as a size and the
     * room it leaves, never a catalogue. Follows the language picked on the welcome.
     */
    val phonePack: StateFlow<PhonePack> = appLanguage
        .map { tag -> phonePackFor(deviceCapabilities.totalRamGb, startingRecordingLanguage(tag), freeBytes, ::isInstalled) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, phonePackFor(deviceCapabilities.totalRamGb, startingRecordingLanguage(null), freeBytes, ::isInstalled))

    private fun lookFor(spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>) =
        // Faith on its own (or with Personal) suggests the warm look; everything else the professional one.
        if (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH in spaces &&
            spaces.all { it == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.FAITH || it == com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.PERSONAL })
            com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.SANCTUARY
        else com.craftflowtechnologies.meetingmind.core.identity.LookAndFeel.PROFESSIONAL

    /** True once [completeOnboarding] has started: a second tap (two actions at once) must not finish twice. */
    private var completing = false

    /** How far [completeOnboarding] got, for tests: a save that never returns shows which one it was. */
    @Volatile internal var finishStage: String = "not started"
        private set

    fun completeOnboarding(onCompleted: () -> Unit) {
        if (completing) return
        completing = true
        finishStage = "started"
        viewModelScope.launch {
            // A failed save (full or unreadable storage) must never crash or trap the person in onboarding:
            // they still go on, and anything not saved falls back to its default.
            runCatching {
                finishStage = "companion"
                companionSettings.setForm(_companion.value)
                // The person's saved choices, read before anything below changes them.
                finishStage = "read saved"
                val saved = prefs.preferencesFlow.first()
                // Recordings start in the language the app is read in, when it's one we can record in, so the setup
                // downloads the speech model that understands it (a Bemba reader gets Omnilingual, not Parakeet).
                val language = startingRecordingLanguage()
                finishStage = "language"
                language?.let { prefs.setSelectedLanguage(it) }
                // The models for this phone and language, so the on-device setup and the AI engine agree from the
                // start. One already on the phone is kept: onboarding never pushes another model over it. The disk is
                // checked off the main thread.
                finishStage = "models"
                val ramMb = com.craftflowtechnologies.meetingmind.ai.modelmanagement.AsrModelResolver.ramMbOf(deviceCapabilities.totalRamGb)
                val (speechId, thinkerId) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val installedSpeech = com.craftflowtechnologies.meetingmind.ai.modelmanagement.AsrModelResolver.speechModels
                        .map { it.id }.filter(::isInstalled).toSet()
                    onboardingSpeechModelId(installedSpeech, saved.selectedAsrModelId, language ?: java.util.Locale.getDefault().language, ramMb) to
                        onboardingThinkerModelId(modelStorage, saved.selectedLlmModelId, deviceCapabilities.totalRamGb)
                }
                finishStage = "save models"
                prefs.setSelectedAsrModel(speechId)
                prefs.setSelectedLlmModel(thinkerId)
                finishStage = "profile"
                prefs.setUserName(_userName.value)
                prefs.setSpaces(_spaces.value)
                prefs.setLook(lookFor(_spaces.value))
                if (com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.WORK in _spaces.value) {
                    // The kind of work is asked when Work is first opened; until then the general set.
                    prefs.setWorkSettings(com.craftflowtechnologies.meetingmind.core.work.WorkSettings.forProfile(com.craftflowtechnologies.meetingmind.core.work.WorkProfile.GENERAL))
                }
                // The devotional and faith reminders follow the spaces just chosen.
                finishStage = "schedules"
                runCatching { com.craftflowtechnologies.meetingmind.core.navigation.SpaceSchedules.sync(getApplication()) }
                finishStage = "mode"
                // The choice is always saved, so the stored mode is never left at a default the person didn't pick.
                prefs.setProcessingProfile(if (_setup.value == SetupChoice.ON_DEVICE) ProcessingProfile.OFFLINE else ProcessingProfile.INTERNET)
                // Large on-phone downloads wait for Wi-Fi unless the person changes it (decision D7).
                if (_setup.value == SetupChoice.ON_DEVICE) prefs.setWifiOnlyDownload(true)
            }
            finishStage = "completed flag"
            runCatching { prefs.setOnboardingCompleted(true) }
            finishStage = "done"
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
 * permissions that matter, then "You're all set" with the first thing to do. The record route goes from the
 * companion to the AI choice and then the recorder. Every move is saved, so a killed app resumes where it was.
 * Everything can be changed later.
 *
 * Always drawn in the light palette, whatever the app's dark or light setting (founder request, 2026-11-19).
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinishOnboarding: () -> Unit,
    onRecordNow: () -> Unit = onFinishOnboarding,
    /** Picked "On this phone": onboarding ends on the guided on-device setup instead of Home. */
    onSetUpOnDevice: () -> Unit = onFinishOnboarding
) {
    AlwaysLight { OnboardingContent(viewModel, onFinishOnboarding, onRecordNow, onSetUpOnDevice) }
}

@Composable
private fun OnboardingContent(
    viewModel: OnboardingViewModel,
    onFinishOnboarding: () -> Unit,
    onRecordNow: () -> Unit,
    onSetUpOnDevice: () -> Unit
) {
    val step by viewModel.step.collectAsState()
    val route by viewModel.route.collectAsState()
    var forward by remember { mutableStateOf(true) }
    val userName by viewModel.userName.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val sampleKind by viewModel.sampleKind.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val companion by viewModel.companion.collectAsState()
    val pack by viewModel.phonePack.collectAsState()
    val youTube = com.craftflowtechnologies.meetingmind.feature.importing.LocalYouTubeSheet.current
    val steps = stepsFor(route)
    val index = steps.indexOf(step).coerceAtLeast(0)
    val finish = when {
        route == EntryRoute.RECORD -> onRecordNow
        setup == SetupChoice.ON_DEVICE -> onSetUpOnDevice
        else -> onFinishOnboarding
    }
    fun next() { forward = true; viewModel.next(steps, finish) }
    fun back() { forward = false; viewModel.back(steps) }
    val c = MM.colors
    val motion = MM.motion

    Box(Modifier.fillMaxSize().testTag("onboarding")) {
        // Paper, with the accent's soft light at the top; it runs under the transparent system bars.
        LightBackdrop()

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (step == OnboardingStep.WELCOME) {
                var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
                val currentLanguageTag by viewModel.appLanguage.collectAsState()
                val activeLang = AppLanguage.fromTag(currentLanguageTag)
                val displayLabel = activeLang?.nativeName ?: stringResource(R.string.settings_language_follow_system)

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MM.space.l, vertical = MM.space.s),
                    horizontalArrangement = Arrangement.End
                ) {
                    LanguagePill(displayLabel) { showLanguagePicker = true }
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
                // Back, then the progress through the steps after the welcome.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MM.space.xs, vertical = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { back() }, modifier = Modifier.testTag("onboarding_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = c.ink)
                    }
                    val total = (steps.size - 1).coerceAtLeast(1)
                    StepProgress(
                        done = index, total = total,
                        description = stringResource(R.string.onboarding_progress_step, index.coerceIn(1, total), total),
                        modifier = Modifier.weight(1f).padding(horizontal = MM.space.s)
                    )
                    // Balances the back button, so the progress sits in the middle.
                    Spacer(Modifier.width(MMSize.minTouch))
                }
                val activeCompanion = companion
                if (step != OnboardingStep.COMPANION && activeCompanion != null) {
                    OnboardingCompanionGuideBar(step = step, form = activeCompanion, userName = userName)
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    // A short slide in the direction of travel; a cut when animations are off (the motion tokens snap).
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(motion.enter()) { it / 6 * dir } + fadeIn(motion.enter())) togetherWith
                        (slideOutHorizontally(motion.standard()) { -it / 6 * dir } + fadeOut(motion.quick()))
                },
                label = "step", modifier = Modifier.weight(1f)
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = MM.space.xl)) {
                    when (s) {
                        OnboardingStep.WELCOME -> Welcome(companion)
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
                        OnboardingStep.SETUP -> SetupStep(pack, setup, viewModel::setSetup)
                        OnboardingStep.READY -> ReadyStepWithAccount(companion, userName, spaces, setup, pack)
                    }
                }
            }
            // One main action per step, pinned above the navigation bar.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.xl).padding(top = MM.space.m, bottom = MM.space.l),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (step == OnboardingStep.WELCOME) {
                    GlowButton(stringResource(R.string.onboarding_button_get_started), "onboarding_next_btn", Modifier.fillMaxWidth()) {
                        forward = true; viewModel.startSample()
                    }
                    com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction(
                        stringResource(R.string.onboarding_button_record_now),
                        onClick = { forward = true; viewModel.startRecordNow() },
                        modifier = Modifier.padding(top = MM.space.s).testTag("onboarding_record_now_btn")
                    )
                } else if (step == OnboardingStep.READY) {
                    ReadyActions(readyActionsFor(setup, phoneReady = pack.ready)) { action ->
                        when (action) {
                            ReadyAction.RECORD -> viewModel.completeOnboarding(onRecordNow)
                            // Home first, then the sheet over it: it lives above the navigation, so it survives the move.
                            ReadyAction.YOUTUBE -> viewModel.completeOnboarding { onFinishOnboarding(); youTube.open() }
                            ReadyAction.SET_UP_PHONE -> viewModel.completeOnboarding(onSetUpOnDevice)
                            ReadyAction.LOOK_AROUND -> viewModel.completeOnboarding(onFinishOnboarding)
                        }
                    }
                } else {
                    val last = index >= steps.lastIndex
                    GlowButton(
                        when {
                            step == OnboardingStep.SAMPLE -> stringResource(R.string.onboarding_button_make_it_mine)
                            last && route == EntryRoute.RECORD -> stringResource(R.string.onboarding_button_start_recording)
                            last && setup == SetupChoice.ON_DEVICE -> stringResource(R.string.onboarding_button_set_up_phone)
                            last -> stringResource(R.string.onboarding_button_start_using)
                            else -> stringResource(R.string.onboarding_button_continue)
                        },
                        "onboarding_next_btn",
                        Modifier.fillMaxWidth()
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
    val c = MM.colors
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
        Modifier.fillMaxWidth().padding(horizontal = MM.space.xl, vertical = MM.space.xs)
            .clip(MM.radius.card).background(c.surface)
            .border(MMSize.hairline, c.lineSoft, MM.radius.card)
            .padding(horizontal = MM.space.m, vertical = MM.space.s).testTag("onboarding_companion_guide"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(MMSize.minTouch).clip(MM.radius.pill).background(c.accentWash), contentAlignment = Alignment.Center) {
            com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion(form = form, state = state, size = 34.dp, variant = variant)
        }
        Spacer(Modifier.width(MM.space.m))
        Text(line, style = MM.type.secondary, color = c.inkSecondary)
    }
}

@Composable
private fun LanguagePill(label: String, onClick: () -> Unit) {
    val c = MM.colors
    Surface(
        onClick = onClick, shape = MM.radius.pill, color = c.surface, border = BorderStroke(MMSize.hairline, c.line),
        modifier = Modifier.heightIn(min = MMSize.minTouch).testTag("onboarding_language_btn")
    ) {
        Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Language, contentDescription = stringResource(R.string.onboarding_language_selector_hint), tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
            Spacer(Modifier.width(MM.space.s))
            Text(label, style = MM.type.bodyStrong, color = c.ink, maxLines = 1)
            Spacer(Modifier.width(MM.space.xs))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(companion: CompanionForm?) {
    val c = MM.colors
    // Shorter phones get a smaller hero, so the promise and its pills stay in view above the buttons.
    val compact = LocalConfiguration.current.screenHeightDp < 720
    Column(Modifier.fillMaxWidth().padding(top = if (compact) MM.space.xs else MM.space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        CompanionHalo(
            form = companion, size = if (compact) 148.dp else 208.dp,
            contentDescription = companion?.let { stringResource(CompanionRoster.displayName(it)) }
        )
        Text("MeetingMind", style = MM.type.overline, color = c.accent, modifier = Modifier.padding(top = MM.space.m))
        Text(
            stringResource(R.string.onboarding_headline), style = MM.type.display, color = c.ink,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.s)
        )
        Text(
            stringResource(R.string.onboarding_body), style = MM.type.body, color = c.inkSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = MM.space.m, start = MM.space.s, end = MM.space.s)
        )
        // Wraps to a second line on narrow screens instead of squeezing a pill into a column of letters.
        FlowRow(
            Modifier.padding(top = MM.space.xl, bottom = MM.space.l),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
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
    val c = MM.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MM.radius.card)
                .background(c.surface)
                .padding(MM.space.xl)
        ) {
            Text(stringResource(R.string.settings_language_title), style = MM.type.title, color = c.ink)
            Spacer(Modifier.height(MM.space.xs))
            Text(stringResource(R.string.settings_language_asr_notice), style = MM.type.secondary, color = c.inkSecondary)
            Spacer(Modifier.height(MM.space.l))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                // System default option
                val isDefault = currentTag.isNullOrBlank()
                LanguageRow(
                    title = stringResource(R.string.settings_language_follow_system),
                    subtitle = stringResource(R.string.settings_language_follow_system_summary),
                    selected = isDefault
                ) { onSelect(null) }

                Spacer(Modifier.height(MM.space.xs))

                AppLanguage.SUPPORTED.forEach { lang ->
                    val isSelected = !isDefault && currentTag.equals(lang.tag, ignoreCase = true)
                    LanguageRow(title = lang.nativeName, subtitle = lang.englishName, selected = isSelected) { onSelect(lang.tag) }
                }
            }

            Spacer(Modifier.height(MM.space.m))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction(stringResource(R.string.action_done), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun LanguageRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.small)
            .background(if (selected) c.accentWash else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = MM.space.m, vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = if (selected) MM.type.bodyStrong else MM.type.body, color = c.ink)
            Text(subtitle, style = MM.type.caption, color = c.inkMuted)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, tag: String) {
    val c = MM.colors
    Row(
        Modifier.testTag(tag).clip(MM.radius.pill).background(c.surface)
            .border(MMSize.hairline, c.line, MM.radius.pill)
            .padding(horizontal = MM.space.m, vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(MM.space.s))
        Text(label, style = MM.type.caption, color = c.inkSecondary, maxLines = 1, softWrap = false)
    }
}

/** A step's title and its one-line explanation: the display face, then the body in the secondary ink. */
@Composable
internal fun StepTitle(title: String, line: String) {
    Text(title, style = MM.type.display, color = MM.colors.ink, modifier = Modifier.padding(top = MM.space.l))
    Text(line, style = MM.type.body, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.s, bottom = MM.space.xl))
}

/**
 * How MeetingMind thinks (design 1d). Two plain choices with their real trade-offs, Internet pre-selected
 * (decision D1). No model catalogue here: "On this phone" hands over to the guided setup after onboarding.
 * The backup text provider is disclosed (decision D10): text, never audio, and only in Internet mode.
 */
@Composable
private fun SetupStep(pack: PhonePack, choice: SetupChoice, onChoice: (SetupChoice) -> Unit) {
    val c = MM.colors
    val motion = MM.motion
    var compare by rememberSaveable { mutableStateOf(false) }
    StepTitle(stringResource(R.string.onboarding_setup_title), stringResource(R.string.onboarding_setup_line))
    Column(Modifier.selectableGroup()) {
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
                if (pack.ready) stringResource(R.string.onboarding_setup_device_fact_ready)
                else stringResource(R.string.onboarding_setup_device_fact_2, SetupGuide.formatBytes(pack.bytes)),
                stringResource(R.string.onboarding_setup_device_fact_3)
            )
            // The room the download leaves, or a plain warning when this phone is short of space. Nothing when it's
            // all on the phone already.
            if (!pack.ready) when (pack.fits) {
                true -> pack.freeAfterBytes?.let {
                    Text(
                        stringResource(R.string.onboarding_setup_device_fits, SetupGuide.formatBytes(it)),
                        style = MM.type.caption, color = c.inkMuted,
                        modifier = Modifier.padding(top = MM.space.s).testTag("setup_pack_fits")
                    )
                }
                false -> com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine(
                    com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind.Warning,
                    stringResource(R.string.onboarding_setup_device_tight, SetupGuide.formatBytes(pack.freeBytes ?: 0L)),
                    modifier = Modifier.padding(top = MM.space.s).testTag("setup_pack_tight")
                )
                null -> Unit
            }
        }
    }
    com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction(
        stringResource(if (compare) R.string.onboarding_setup_compare_hide else R.string.onboarding_setup_compare),
        onClick = { compare = !compare },
        modifier = Modifier.padding(top = MM.space.xs).testTag("setup_compare")
    )
    AnimatedVisibility(
        visible = compare,
        enter = fadeIn(motion.standard()) + expandVertically(motion.standard()),
        exit = fadeOut(motion.quick()) + shrinkVertically(motion.standard())
    ) {
        Column(
            Modifier.fillMaxWidth().clip(MM.radius.card).background(c.surfaceSunk).padding(MM.space.l),
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Text(stringResource(R.string.onboarding_setup_always_online), style = MM.type.secondary, color = c.inkSecondary)
            Text(stringResource(R.string.onboarding_setup_backup_disclosure), style = MM.type.secondary, color = c.inkSecondary)
            Text(stringResource(R.string.onboarding_setup_confidential), style = MM.type.secondary, color = c.inkSecondary)
        }
    }
    Text(
        stringResource(R.string.onboarding_setup_change_later), style = MM.type.caption, color = c.inkMuted,
        modifier = Modifier.padding(top = MM.space.s, bottom = MM.space.l)
    )
}

@Composable
private fun SetupFacts(vararg facts: String) {
    val c = MM.colors
    Column(Modifier.padding(top = MM.space.m), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        facts.forEach { fact ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.accent, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                Spacer(Modifier.width(MM.space.s))
                Text(fact, style = MM.type.secondary, color = c.inkSecondary)
            }
        }
    }
}

@Composable
private fun ChoiceCard(selected: Boolean, onClick: () -> Unit, icon: ImageVector, title: String, badge: String?, line: String, tag: String, alwaysShowExtra: Boolean = false, extra: @Composable () -> Unit = {}) {
    val c = MM.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = MM.space.s)
            .choiceCard(c, MM.radius.card, selected, MM.elevation.floating)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(MM.space.l).testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, on = selected)
            Spacer(Modifier.width(MM.space.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = MM.type.heading, color = c.ink)
                badge?.let {
                    Text(
                        it, style = MM.type.caption, color = c.accent,
                        modifier = Modifier.padding(top = MM.space.xs).clip(MM.radius.pill).background(c.accentWash)
                            .padding(horizontal = MM.space.s, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(MM.space.s))
            ChoiceMark(selected)
        }
        Text(line, style = MM.type.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = MM.space.m))
        if (selected || alwaysShowExtra) extra()
    }
}

/**
 * The "You're all set" step with the Circles account behind its Sign in with Google. The account comes from the
 * Circles repository; if that can't be built (no Firebase in this build or a test), the step still shows, signed out.
 */
@Composable
private fun ReadyStepWithAccount(
    companion: CompanionForm?,
    userName: String,
    spaces: Set<com.craftflowtechnologies.meetingmind.core.model.NotebookSpace>,
    setup: SetupChoice,
    pack: PhonePack
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { runCatching { com.craftflowtechnologies.meetingmind.core.circles2.Circles2.repository(context) }.getOrNull() }
    val account: com.craftflowtechnologies.meetingmind.feature.circles2.AccountViewModel? = repo?.let {
        androidx.lifecycle.viewmodel.compose.viewModel(factory = com.craftflowtechnologies.meetingmind.feature.circles2.AccountViewModel.Factory(it))
    }
    val state = account?.state?.collectAsState()?.value ?: com.craftflowtechnologies.meetingmind.feature.circles2.AccountUiState()
    ReadyStep(
        companion = companion, userName = userName, spaces = spaces, setup = setup, pack = pack, account = state,
        onLink = { ctx -> account?.link(ctx) },
        onSwitchAccount = { account?.switchAccount() },
        onDismissConflict = { account?.dismissConflict() }
    )
}
