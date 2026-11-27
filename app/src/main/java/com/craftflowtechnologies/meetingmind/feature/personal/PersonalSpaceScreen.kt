package com.craftflowtechnologies.meetingmind.feature.personal

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.craftflowtechnologies.meetingmind.R
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotWorkspace
import com.craftflowtechnologies.meetingmind.core.applock.AuthPrompt
import com.craftflowtechnologies.meetingmind.core.applock.AuthResult
import com.craftflowtechnologies.meetingmind.core.applock.BiometricPromptAuthenticator
import com.craftflowtechnologies.meetingmind.core.personal.GrowthEnrollmentStatus
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRepository
import com.craftflowtechnologies.meetingmind.core.personal.PersonalSanctuaryLock
import com.craftflowtechnologies.meetingmind.feature.moments.MilestoneHost
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.navigation.AppMenuButton
import com.craftflowtechnologies.meetingmind.core.personal.ChallengeDefinition
import com.craftflowtechnologies.meetingmind.core.personal.CustomChallengeConfig
import com.craftflowtechnologies.meetingmind.core.personal.GrowthEnrollment
import com.craftflowtechnologies.meetingmind.core.personal.GuidedActivityDefinition
import com.craftflowtechnologies.meetingmind.core.personal.IdeaStage
import com.craftflowtechnologies.meetingmind.core.personal.JournalMoodVibe
import com.craftflowtechnologies.meetingmind.core.personal.JourneyDefinition
import com.craftflowtechnologies.meetingmind.core.personal.LifeChapter
import com.craftflowtechnologies.meetingmind.core.personal.PersonalGrowthCatalog
import com.craftflowtechnologies.meetingmind.core.personal.PersonalNoteMeta
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRoutine
import com.craftflowtechnologies.meetingmind.core.personal.PersonalSoundscape
import com.craftflowtechnologies.meetingmind.core.personal.RoutineCompletion
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStep
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStepKind
import com.craftflowtechnologies.meetingmind.core.personal.RoutineTimeWindow
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMe
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMeSheet
import com.craftflowtechnologies.meetingmind.feature.work.noteTimeLabel
import com.craftflowtechnologies.meetingmind.feature.work.previewOf
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The first-class Personal Space hub ("Remember · Reflect · Build · Grow · Preserve").
 *
 * Uses a clean 5-tab in-vertical floating dock (Sanctuary · Journal · Ideas · Growth · Vault)
 * so Routines, Journeys, Challenges, Coaching, Playbooks, Living Journal, Idea Studio, and Time
 * Capsules feel like one coherent personal sanctuary rather than a pile of utilities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalSpaceScreen(
    viewModel: PersonalViewModel,
    initialTab: String? = null,
    onStartRoutine: (routineId: String, minEffort: Boolean) -> Unit,
    onRecordJournal: () -> Unit,
    onRecordIdea: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenYouTube: () -> Unit,
    onHome: () -> Unit,
    onOpenPaywall: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hostActivity = remember(context) { context.findFragmentActivity() }
    val biometricAuth = remember(hostActivity) { hostActivity?.let { BiometricPromptAuthenticator(it) } }

    LaunchedEffect(initialTab) { viewModel.selectTabByName(initialTab) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshTodayIfNeeded()
        onPauseOrDispose {}
    }

    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val journalMode by viewModel.journalViewMode.collectAsStateWithLifecycle()
    val growthSubTab by viewModel.growthSubTab.collectAsStateWithLifecycle()
    val banner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val pendingMilestone by viewModel.pendingMilestone.collectAsStateWithLifecycle()
    val sessionUnlocked by PersonalSanctuaryLock.sessionUnlocked.collectAsStateWithLifecycle()

    var lockEnabled by remember { mutableStateOf(PersonalSanctuaryLock.isLockEnabled(context)) }
    var recentsShieldEnabled by remember { mutableStateOf(PersonalSanctuaryLock.isRecentsShieldEnabled(context)) }
    var lockError by remember { mutableStateOf<String?>(null) }

    // Apply FLAG_SECURE while Personal Sanctuary is visible and Recents Shield is enabled.
    DisposableEffect(hostActivity, recentsShieldEnabled) {
        val window = hostActivity?.window
        if (window != null && recentsShieldEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null && recentsShieldEnabled) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    var showIntentionDialog by remember { mutableStateOf(false) }
    var showQuickWriteSheet by remember { mutableStateOf(false) }
    var showSparkDialog by remember { mutableStateOf(false) }
    var showCapsuleDialog by remember { mutableStateOf(false) }
    var showRoutineEditor by remember { mutableStateOf(false) }
    var routineToEdit by remember { mutableStateOf<PersonalRoutine?>(null) }
    var showCustomChallengeDialog by remember { mutableStateOf(false) }
    var showLifeChapterDialog by remember { mutableStateOf(false) }
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var showSanctuaryToolsSheet by remember { mutableStateOf(false) }
    var chapterDetailTarget by remember { mutableStateOf<LifeChapter?>(null) }
    var enrollmentDetailTarget by remember { mutableStateOf<GrowthEnrollment?>(null) }
    var showLettingGoSheet by remember { mutableStateOf(false) }
    var journeyToStart by remember { mutableStateOf<JourneyDefinition?>(null) }
    var enrollmentToCheckIn by remember { mutableStateOf<GrowthEnrollment?>(null) }
    var coachingAudioId by remember { mutableStateOf<String?>(null) }
    var activeCoachingStage by remember { mutableStateOf<GuidedActivityDefinition?>(null) }
    var memoryCeremonyTarget by remember { mutableStateOf<Note?>(null) }
    var activityToAddRoutine by remember { mutableStateOf<GuidedActivityDefinition?>(null) }

    val obsidianFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { "Obsidian Vault" } ?: "Obsidian Vault"
            viewModel.pinObsidianFolder(uri.toString(), label)
        }
    }

    val dayOneJsonPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!raw.isNullOrBlank()) {
                viewModel.importDayOneJson(raw)
            } else {
                viewModel.showInfoBanner("Couldn't read that file. Pick an uncompressed Day One JSON export.")
            }
        }
    }

    fun triggerBiometricUnlock() {
        val auth = biometricAuth
        if (auth == null) {
            PersonalSanctuaryLock.markUnlocked()
            return
        }
        scope.launch {
            lockError = null
            when (val res = auth.authenticate(AuthPrompt("Unlock Personal Sanctuary", "Confirm it's you to view private journals & capsules"))) {
                AuthResult.Success -> PersonalSanctuaryLock.markUnlocked()
                AuthResult.Cancelled -> Unit
                is AuthResult.Unavailable -> {
                    // If the device has no screen lock enrolled, don't trap the user out.
                    PersonalSanctuaryLock.markUnlocked()
                    lockError = "No screen lock enrolled on this device."
                }
                is AuthResult.LockedOut -> lockError = "Too many attempts — try again in a moment."
                is AuthResult.Error -> lockError = res.message.ifBlank { "Authentication failed." }
            }
        }
    }

    val c = MM.colors

    if (lockEnabled && !sessionUnlocked) {
        SanctuaryLockGate(
            onUnlockClick = { triggerBiometricUnlock() },
            onGoToday = onHome,
            errorMessage = lockError
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = c.background,
            bottomBar = {
                PersonalDockBar(
                    current = tab,
                    onSelect = viewModel::selectTab
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .testTag("personal_space_screen")
            ) {
                ScreenHeader(
                    title = when (tab) {
                        PersonalTab.SANCTUARY -> stringResource(R.string.personal_header_sanctuary)
                        PersonalTab.JOURNAL -> stringResource(R.string.personal_header_journal)
                        PersonalTab.IDEAS -> stringResource(R.string.personal_header_ideas)
                        PersonalTab.GROWTH -> stringResource(R.string.personal_header_growth)
                        PersonalTab.VAULT -> stringResource(R.string.personal_header_vault)
                    },
                    modifier = Modifier.padding(horizontal = MM.space.l),
                    actions = {
                        IconButton(
                            onClick = { showSanctuaryToolsSheet = true },
                            modifier = Modifier.testTag("personal_sanctuary_tools_button")
                        ) {
                            Icon(
                                Icons.Rounded.Shield,
                                contentDescription = "Sanctuary Tools & Privacy",
                                tint = if (lockEnabled) c.accent else c.inkSecondary
                            )
                        }
                        TextAction(stringResource(R.string.personal_action_today), onClick = onHome)
                        AppMenuButton(focus = NotebookSpace.PERSONAL)
                    }
                )

                banner?.let { msg ->
                    Box(Modifier.padding(horizontal = MM.space.l, vertical = MM.space.xs)) {
                        StatusLine(StatusKind.Info, msg, actionLabel = stringResource(R.string.personal_action_dismiss), onAction = viewModel::dismissBanner)
                    }
                }

                when (tab) {
                    PersonalTab.SANCTUARY -> SanctuaryTabContent(
                        ui = ui,
                        onTapIntention = { showIntentionDialog = true },
                        onToggleMinEffortDay = viewModel::toggleMinEffortDay,
                        onSelectMood = viewModel::setMoodVibe,
                        onStartRoutine = onStartRoutine,
                        onEditRoutine = { routineToEdit = it; showRoutineEditor = true },
                        onSkipRoutine = viewModel::skipRoutineToday,
                        onCheckInEnrollment = { enrollmentToCheckIn = it },
                        onInspectEnrollment = { enrollmentDetailTarget = it },
                        onPlayCoaching = { activeCoachingStage = it },
                        onRecordJournal = onRecordJournal,
                        onQuickWrite = { showQuickWriteSheet = true },
                        onRecordIdea = onRecordIdea,
                        onOpenNote = onOpenNote,
                        onOpenMemoryCeremony = { memoryCeremonyTarget = it },
                        onOpenGrowthTab = {
                            viewModel.selectTab(PersonalTab.GROWTH)
                            viewModel.selectGrowthSubTab(it)
                        }
                    )
                    PersonalTab.JOURNAL -> LivingJournalTabContent(
                        ui = ui,
                        viewMode = journalMode,
                        onSelectViewMode = viewModel::selectJournalViewMode,
                        onRecordJournal = onRecordJournal,
                        onQuickWrite = { showQuickWriteSheet = true },
                        onOpenLettingGo = { showLettingGoSheet = true },
                        onOpenNote = onOpenNote
                    )
                    PersonalTab.IDEAS -> IdeaStudioTabContent(
                        ui = ui,
                        onRecordIdea = onRecordIdea,
                        onQuickSpark = { showSparkDialog = true },
                        onAdvanceStage = viewModel::advanceIdeaStage,
                        onGraduateToChapter = viewModel::graduateIdeaToChapter,
                        onConnectCollision = viewModel::connectCollidedIdeas,
                        onDismissCollision = viewModel::dismissIdeaCollision,
                        onOpenNote = onOpenNote
                    )
                    PersonalTab.GROWTH -> GrowthTabContent(
                        ui = ui,
                        subTab = growthSubTab,
                        onSelectSubTab = viewModel::selectGrowthSubTab,
                        onStartJourney = { journeyToStart = it },
                        onStartChallenge = viewModel::startChallenge,
                        onCreateCustomChallenge = { showCustomChallengeDialog = true },
                        onCheckInEnrollment = { enrollmentToCheckIn = it },
                        onInspectEnrollment = { enrollmentDetailTarget = it },
                        onStartRoutine = onStartRoutine,
                        onEditRoutine = { routineToEdit = it; showRoutineEditor = true },
                        onSkipRoutine = viewModel::skipRoutineToday,
                        onAddTemplateRoutine = viewModel::addRoutineFromTemplate,
                        onCreateCustomRoutine = { routineToEdit = null; showRoutineEditor = true },
                        onPlayCoaching = { activeCoachingStage = it },
                        onAddCoachingToRoutine = { activityToAddRoutine = it },
                        onTurnPlaybookIntoChallenge = viewModel::startChallengeFromPlaybook,
                        onCreateLifeChapter = { showLifeChapterDialog = true },
                        onOpenLifeChapter = { chapterDetailTarget = it },
                        onAddInnerCirclePerson = { showAddPersonDialog = true },
                        onAddPersonalTask = viewModel::addPersonalTask,
                        onTogglePersonalTask = viewModel::togglePersonalTask,
                        onOpenNote = onOpenNote,
                        onOpenYouTube = onOpenYouTube
                    )
                    PersonalTab.VAULT -> MemoryVaultTabContent(
                        ui = ui,
                        onSealCapsule = { showCapsuleDialog = true },
                        onTapLockedCapsule = { msg -> viewModel.showInfoBanner(msg) },
                        onOpenUnlockedCapsule = { memoryCeremonyTarget = it },
                        onOpenNote = onOpenNote,
                        onRestartRefresher = viewModel::restartAsRefresher
                    )
                }
            }
        }

        MilestoneHost(
            kind = pendingMilestone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        )
    }

    // ---- Dialogs & Sheets ----

    if (showIntentionDialog) {
        SingleInputDialog(
            title = "Today's Intention",
            subtitle = "One calm anchor stitched onto Today's Journal page.",
            initialValue = ui.dailyPlan.intentionText,
            placeholder = "Move unhurriedly and protect one deep focus block…",
            confirmLabel = "Save intention",
            onDismiss = { showIntentionDialog = false },
            onConfirm = { text ->
                showIntentionDialog = false
                viewModel.setTodayIntention(text)
            }
        )
    }

    if (showQuickWriteSheet) {
        QuickJournalSheet(
            onDismiss = { showQuickWriteSheet = false },
            onSave = { heading, text ->
                showQuickWriteSheet = false
                viewModel.saveQuickJournalEntry(heading, text)
            }
        )
    }

    if (showSparkDialog) {
        TitleBodyDialog(
            title = "Capture a Spark",
            titlePlaceholder = "Short idea title",
            bodyPlaceholder = "What's the core thought or experiment?",
            confirmLabel = "Save spark",
            onDismiss = { showSparkDialog = false },
            onConfirm = { title, body ->
                showSparkDialog = false
                viewModel.captureQuickSpark(title, body)
            }
        )
    }

    if (showCapsuleDialog) {
        SealCapsuleDialog(
            onDismiss = { showCapsuleDialog = false },
            onRecordVoice = {
                showCapsuleDialog = false
                onRecordJournal()
            },
            onSeal = { title, body, days, label ->
                showCapsuleDialog = false
                viewModel.sealTimeCapsule(title, body, days, label)
            }
        )
    }

    journeyToStart?.let { journey ->
        SingleInputDialog(
            title = "Start ${journey.title}",
            subtitle = journey.whyPrompt,
            initialValue = "",
            placeholder = "Your personal 'why' (saved privately as your anchor note)…",
            confirmLabel = "Begin Journey",
            allowBlank = true,
            onDismiss = { journeyToStart = null },
            onConfirm = { why ->
                journeyToStart = null
                viewModel.startJourney(journey, why)
            }
        )
    }

    enrollmentToCheckIn?.let { enrollment ->
        EnrollmentCheckInDialog(
            enrollment = enrollment,
            onDismiss = { enrollmentToCheckIn = null },
            onComplete = { minEffort, reflection ->
                enrollmentToCheckIn = null
                viewModel.completeEnrollmentStep(enrollment.id, minEffort, reflection)
            }
        )
    }

    activityToAddRoutine?.let { act ->
        PickRoutineDialog(
            activity = act,
            routines = ui.routines,
            onDismiss = { activityToAddRoutine = null },
            onPick = { routineId ->
                activityToAddRoutine = null
                viewModel.addGuidedActivityToRoutine(routineId, act)
            }
        )
    }

    if (showRoutineEditor) {
        RoutineEditorSheet(
            existing = routineToEdit,
            onDismiss = { showRoutineEditor = false; routineToEdit = null },
            onSave = { title, why, window, cue, soundscape, steps, reminder, minute, daysMask ->
                val editingId = routineToEdit?.id
                showRoutineEditor = false
                routineToEdit = null
                viewModel.saveCustomRoutine(
                    title = title,
                    whyDescription = why,
                    timeWindow = window,
                    anchorCue = cue,
                    soundscape = soundscape,
                    steps = steps,
                    reminderEnabled = reminder,
                    scheduledMinuteOfDay = minute,
                    existingId = editingId,
                    daysOfWeekMask = daysMask
                )
            },
            onArchive = { routine ->
                showRoutineEditor = false
                routineToEdit = null
                viewModel.archiveRoutine(routine.id, routine.title)
            }
        )
    }

    chapterDetailTarget?.let { chapter ->
        LifeChapterDetailSheet(
            chapter = chapter,
            notesFlow = viewModel.observeNotesInChapter(chapter.id),
            candidateNotes = ui.journalNotes + ui.ideaNotes,
            onOpenNote = { nId ->
                chapterDetailTarget = null
                onOpenNote(nId)
            },
            onAssignNote = { nId, chId, chTitle ->
                viewModel.assignNoteToChapter(nId, chId, chTitle)
            },
            onArchiveChapter = {
                chapterDetailTarget = null
                viewModel.archiveLifeChapter(chapter.id, chapter.title)
            },
            onDismiss = { chapterDetailTarget = null }
        )
    }

    enrollmentDetailTarget?.let { enrollment ->
        EnrollmentDetailSheet(
            enrollment = enrollment,
            loadStepProgress = { id -> viewModel.getStepProgress(id) },
            loadNote = { id -> viewModel.getNote(id) },
            onCheckIn = {
                enrollmentDetailTarget = null
                enrollmentToCheckIn = enrollment
            },
            onOpenNote = { nId ->
                enrollmentDetailTarget = null
                onOpenNote(nId)
            },
            onUpdateStatus = { status ->
                enrollmentDetailTarget = null
                viewModel.updateEnrollmentStatus(enrollment.id, enrollment.title, status)
            },
            onDismiss = { enrollmentDetailTarget = null }
        )
    }

    if (showCustomChallengeDialog) {
        CustomChallengeDialog(
            onDismiss = { showCustomChallengeDialog = false },
            onCreate = { title, days, action, minAction ->
                showCustomChallengeDialog = false
                viewModel.startCustomChallenge(title, days, action, minAction)
            }
        )
    }

    if (showLifeChapterDialog) {
        TitleBodyDialog(
            title = "Start a Life Chapter",
            titlePlaceholder = "Chapter title (e.g. Moving to Cape Town)",
            bodyPlaceholder = "What makes this season meaningful right now?",
            confirmLabel = "Create Chapter",
            onDismiss = { showLifeChapterDialog = false },
            onConfirm = { title, subtitle ->
                showLifeChapterDialog = false
                viewModel.createLifeChapter(title, subtitle)
            }
        )
    }

    if (showLettingGoSheet) {
        LettingGoSheet(onDismiss = { showLettingGoSheet = false })
    }

    if (showAddPersonDialog) {
        AddInnerCirclePersonDialog(
            onDismiss = { showAddPersonDialog = false },
            onAdd = { name, relationship, notes ->
                showAddPersonDialog = false
                viewModel.addInnerCirclePerson(name, relationship, notes)
            }
        )
    }

    activeCoachingStage?.let { act ->
        GuidedActivityStageSheet(
            activity = act,
            onDismiss = { activeCoachingStage = null },
            onPlayAudio = { coachingAudioId = ReadToMe.coachingId(act.id) },
            onAddToRoutine = {
                activeCoachingStage = null
                activityToAddRoutine = act
            },
            onSaveReflection = { reflection ->
                viewModel.saveQuickJournalEntry(act.title, reflection)
            }
        )
    }

    memoryCeremonyTarget?.let { mem ->
        MemoryUnsealCeremonySheet(
            memoryNote = mem,
            onDismiss = { memoryCeremonyTarget = null },
            onOpenOriginal = {
                memoryCeremonyTarget = null
                onOpenNote(mem.id)
            },
            onReplyToPastSelf = { replyText ->
                memoryCeremonyTarget = null
                viewModel.replyToPastMemory(mem.id, replyText, onCreated = onOpenNote)
            }
        )
    }

    if (showSanctuaryToolsSheet) {
        val lastSyncLabel = remember(ui.lastObsidianSyncMs) {
            if (ui.lastObsidianSyncMs > 0L) {
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ui.lastObsidianSyncMs))
            } else null
        }
        PersonalSanctuaryToolsSheet(
            lockEnabled = lockEnabled,
            recentsShieldEnabled = recentsShieldEnabled,
            obsidianVaultLabel = ui.obsidianVaultLabel,
            lastSyncLabel = lastSyncLabel,
            autopilotUnlocked = AutopilotWorkspace.isUnlocked(context),
            isAutopilotRunning = ui.isAutopilotRunning,
            onToggleLock = { enabled ->
                lockEnabled = enabled
                PersonalSanctuaryLock.setLockEnabled(context, enabled)
                if (enabled) PersonalSanctuaryLock.markUnlocked()
            },
            onToggleRecentsShield = { enabled ->
                recentsShieldEnabled = enabled
                PersonalSanctuaryLock.setRecentsShieldEnabled(context, enabled)
            },
            onLockNow = {
                showSanctuaryToolsSheet = false
                PersonalSanctuaryLock.lockNow()
            },
            onPickObsidianFolder = { obsidianFolderPicker.launch(null) },
            onSyncObsidianNow = { viewModel.syncObsidianVault() },
            onImportDayOneJson = { dayOneJsonPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onRunPersonalAutopilot = { recipe ->
                viewModel.runPersonalAutopilot(recipe) { noteId ->
                    showSanctuaryToolsSheet = false
                    onOpenNote(noteId)
                }
            },
            onOpenPaywall = {
                showSanctuaryToolsSheet = false
                onOpenPaywall()
            },
            onDismiss = { showSanctuaryToolsSheet = false }
        )
    }

    coachingAudioId?.let { id ->
        ReadToMeSheet(noteId = id, onDismiss = { coachingAudioId = null })
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

// ====================================================================== TAB 1: SANCTUARY (PERSONAL TODAY)

@Composable
private fun SanctuaryTabContent(
    ui: PersonalUiState,
    onTapIntention: () -> Unit,
    onToggleMinEffortDay: (Boolean) -> Unit,
    onSelectMood: (JournalMoodVibe) -> Unit,
    onStartRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (PersonalRoutine) -> Unit,
    onSkipRoutine: (String) -> Unit,
    onCheckInEnrollment: (GrowthEnrollment) -> Unit,
    onInspectEnrollment: (GrowthEnrollment) -> Unit,
    onPlayCoaching: (GuidedActivityDefinition) -> Unit,
    onRecordJournal: () -> Unit,
    onQuickWrite: () -> Unit,
    onRecordIdea: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMemoryCeremony: (Note) -> Unit,
    onOpenGrowthTab: (GrowthSubTab) -> Unit
) {
    val c = MM.colors
    val minEffortDay = ui.dailyPlan.isMinEffortDay

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("personal_sanctuary_tab"),
        contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        // 1. 3D Parallax Sanctuary Diorama + Intention
        item(key = "diorama") {
            val greeting = when (ui.currentWindow) {
                RoutineTimeWindow.MORNING -> "Good morning"
                RoutineTimeWindow.AFTERNOON -> "A calm afternoon"
                else -> "Unwind your evening"
            }
            val sub = if (ui.calmRhythmDaysThisWeek >= 2) {
                "${ui.calmRhythmDaysThisWeek} days kept for yourself this week"
            } else {
                "Small steps, kept in your own words"
            }
            SanctuaryDioramaCard(
                window = ui.currentWindow,
                greeting = greeting,
                subtitle = sub,
                intentionText = ui.dailyPlan.intentionText,
                onTapIntention = onTapIntention
            )
        }

        // 2. Resume Interrupted Routine Banner (if any)
        ui.inProgressCompletion?.let { prog ->
            val routine = ui.routines.firstOrNull { it.id == prog.routineId }
            if (routine != null) {
                item(key = "resume_routine") {
                    MMCard(onClick = { onStartRoutine(routine.id, prog.usedMinEffortMode) }) {
                        Text("IN PROGRESS", style = MM.type.overline, color = c.accent)
                        Spacer(Modifier.height(MM.space.xs))
                        Text("Resume ${routine.title}", style = MM.type.heading, color = c.ink)
                        Text(
                            "Step ${prog.currentStepIndex + 1} of ${routine.activeSteps.size} · pick up gently right where you paused",
                            style = MM.type.secondary,
                            color = c.inkSecondary
                        )
                    }
                }
            }
        }

        // 3. Today Plan Header + Low-Energy Day Switch
        item(key = "plan_header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today's Plan", style = MM.type.heading, color = c.ink)
                    Text(
                        if (minEffortDay) "Gentle low-energy pace active" else "Morning, afternoon & evening rituals",
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                }
                Text("Low-energy day", style = MM.type.caption, color = c.inkSecondary)
                Spacer(Modifier.width(MM.space.xs))
                Switch(
                    checked = minEffortDay,
                    onCheckedChange = onToggleMinEffortDay,
                    modifier = Modifier.testTag("personal_min_effort_switch")
                )
            }
        }

        // 4. Temporal Routines Spine (Morning / Afternoon / Evening)
        val orderedRoutines = ui.routines.sortedWith(
            compareBy<PersonalRoutine> { it.timeWindow.ordinal }.thenBy { it.sortOrder }
        )
        items(orderedRoutines, key = { "sanctuary_r_${it.id}" }) { routine ->
            val comp = ui.todayCompletions[routine.id]
            RoutinePlanCard(
                routine = routine,
                completion = comp,
                minEffortDay = minEffortDay,
                onStart = { min -> onStartRoutine(routine.id, min) },
                onEdit = { onEditRoutine(routine) },
                onSkip = { onSkipRoutine(routine.id) }
            )
        }

        // 5. Active Journeys & Challenges Today
        if (ui.activeJourneys.isNotEmpty() || ui.activeChallenges.isNotEmpty()) {
            item(key = "active_growth_hdr") {
                SectionHeader(
                    title = "Active Growth",
                    count = ui.activeJourneys.size + ui.activeChallenges.size,
                    actionLabel = "All",
                    onAction = { onOpenGrowthTab(GrowthSubTab.JOURNEYS) }
                )
            }
            items(ui.activeJourneys + ui.activeChallenges, key = { "enroll_${it.id}" }) { enroll ->
                ActiveEnrollmentCard(
                    enrollment = enroll,
                    onCheckIn = { onCheckInEnrollment(enroll) },
                    onInspect = { onInspectEnrollment(enroll) }
                )
            }
        } else {
            item(key = "discover_journey_prompt") {
                MMCard(onClick = { onOpenGrowthTab(GrowthSubTab.JOURNEYS) }) {
                    Text("GUIDED JOURNEYS", style = MM.type.overline, color = c.speaker3)
                    Spacer(Modifier.height(MM.space.xs))
                    Text("Start a gentle 14-day Journey", style = MM.type.heading, color = c.ink)
                    Text(
                        "Build a consistent journal, create a calmer morning, or turn ideas into action—one small step at a time.",
                        style = MM.type.secondary,
                        color = c.inkSecondary
                    )
                }
            }
        }

        // 6. Suggested 2-3 Minute Guided Coaching Activity
        item(key = "suggested_coaching") {
            val act = ui.suggestedActivity
            MMCard(onClick = { onPlayCoaching(act) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SUGGESTED FOR ${ui.currentWindow.label.uppercase()}", style = MM.type.overline, color = c.accent)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(act.title, style = MM.type.heading, color = c.ink)
                        Text("${act.durationMinutes} min · ${act.subtitle}", style = MM.type.secondary, color = c.inkSecondary)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c.accentWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Headphones, contentDescription = "Play coaching", tint = c.accent)
                    }
                }
            }
        }

        // 7. Quick Journal Check-In + Mood Vibe Strip
        item(key = "journal_checkin") {
            MMCard {
                Text("JOURNAL & SPARKS", style = MM.type.overline, color = c.inkMuted)
                Spacer(Modifier.height(MM.space.xs))
                Text("How does today feel?", style = MM.type.heading, color = c.ink)
                Spacer(Modifier.height(MM.space.s))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    items(JournalMoodVibe.entries, key = { it.name }) { vibe ->
                        FilterChip(
                            selected = ui.dailyPlan.moodVibe == vibe,
                            onClick = { onSelectMood(vibe) },
                            label = { Text(vibe.label, style = MM.type.caption) }
                        )
                    }
                }
                Spacer(Modifier.height(MM.space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton(
                        text = "Voice Journal",
                        onClick = onRecordJournal,
                        leadingIcon = Icons.Rounded.Mic,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Write",
                        onClick = onQuickWrite,
                        leadingIcon = Icons.Rounded.EditNote
                    )
                }
                Spacer(Modifier.height(MM.space.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextAction("Capture an Idea", onClick = onRecordIdea)
                    ui.dailyPlan.dayPageNoteId?.let { dayId ->
                        TextAction("Open Today's Page", onClick = { onOpenNote(dayId) })
                    }
                }
            }
        }

        // 8. "On This Day" / Memory Resurfacing
        ui.onThisDayMemory?.let { memory ->
            item(key = "on_this_day") {
                MMCard(onClick = { onOpenMemoryCeremony(memory) }) {
                    Text("A MEMORY WORTH REVISITING", style = MM.type.overline, color = c.gold)
                    Spacer(Modifier.height(MM.space.xs))
                    Text(memory.title.ifBlank { "Past journal entry" }, style = MM.type.heading, color = c.ink)
                    Text(
                        previewOf(memory.plainText),
                        style = MM.type.secondary,
                        color = c.inkSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutinePlanCard(
    routine: PersonalRoutine,
    completion: RoutineCompletion?,
    minEffortDay: Boolean,
    onStart: (minEffort: Boolean) -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit
) {
    val c = MM.colors
    val isDone = completion?.status?.isDone == true
    val isSkipped = completion?.status?.name == "SKIPPED"
    val fullMins = routine.totalMinutes(minEffort = false)
    val minMins = routine.totalMinutes(minEffort = true)

    MMCard(modifier = Modifier.testTag("routine_card_${routine.id}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${routine.timeWindow.label.uppercase()} · ${routine.activeSteps.size} STEPS · ${if (minEffortDay) "$minMins min (gentle)" else "$fullMins min"}",
                    style = MM.type.overline,
                    color = if (isDone) c.speaker3 else c.inkMuted
                )
                Spacer(Modifier.height(MM.space.xs))
                Text(routine.title, style = MM.type.heading, color = c.ink)
                if (routine.anchorCue.isNotBlank()) {
                    Text(routine.anchorCue, style = MM.type.caption, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.testTag("routine_edit_${routine.id}")) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.personal_action_edit), tint = c.inkMuted, modifier = Modifier.size(18.dp))
            }
            if (isDone) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Completed", tint = c.speaker3)
            }
        }

        Spacer(Modifier.height(MM.space.m))
        when {
            isDone -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kept today", style = MM.type.bodyStrong, color = c.speaker3)
                    Spacer(Modifier.weight(1f))
                    TextAction("Run again", onClick = { onStart(minEffortDay) })
                }
            }
            isSkipped -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rested for today", style = MM.type.secondary, color = c.inkMuted)
                    Spacer(Modifier.weight(1f))
                    TextAction("Start anyway", onClick = { onStart(minEffortDay) })
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton(
                        text = if (minEffortDay) "Start Gentle (${minMins}m)" else "Start Routine",
                        onClick = { onStart(minEffortDay) },
                        leadingIcon = Icons.Rounded.PlayArrow
                    )
                    if (!minEffortDay && minMins < fullMins) {
                        TextAction("${minMins}m gentle", onClick = { onStart(true) })
                    }
                    Spacer(Modifier.weight(1f))
                    TextAction("Rest", onClick = onSkip)
                }
            }
        }
    }
}

@Composable
private fun ActiveEnrollmentCard(
    enrollment: GrowthEnrollment,
    onCheckIn: () -> Unit,
    onInspect: () -> Unit
) {
    val c = MM.colors
    val journeyDef = PersonalGrowthCatalog.journeyById(enrollment.catalogId)
    val theme = journeyDef?.landscapeTheme ?: "COASTAL_LIGHT"
    val nextStepTitle = journeyDef?.steps?.getOrNull(enrollment.currentStepOrDay)?.title
        ?: CustomChallengeConfig.fromJson(enrollment.customConfigJson)?.dailyAction
        ?: PersonalGrowthCatalog.challengeById(enrollment.catalogId)?.dailyAction
    val isPaused = enrollment.status == GrowthEnrollmentStatus.PAUSED
    MMCard(onClick = onInspect) {
        Text(
            "${enrollment.kind.name} · " + if (isPaused) "PAUSED" else "DAY ${(enrollment.currentStepOrDay + 1).coerceAtMost(enrollment.targetStepsOrDays)} OF ${enrollment.targetStepsOrDays}",
            style = MM.type.overline,
            color = if (isPaused) c.inkMuted else c.speaker3
        )
        Spacer(Modifier.height(MM.space.xs))
        Text(enrollment.title, style = MM.type.heading, color = c.ink)
        if (!nextStepTitle.isNullOrBlank()) {
            Text("Up next: $nextStepTitle", style = MM.type.caption, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(MM.space.s))
        JourneyPathVisualizer(
            completedSteps = enrollment.currentStepOrDay,
            totalSteps = enrollment.targetStepsOrDays,
            landscapeTheme = theme
        )
        Spacer(Modifier.height(MM.space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = if (isPaused) "View & Resume" else "Log Today's Step",
                onClick = if (isPaused) onInspect else onCheckIn,
                modifier = Modifier.weight(1f)
            )
            TextAction("Details", onClick = onInspect)
        }
    }
}

// ====================================================================== TAB 2: LIVING JOURNAL

@Composable
private fun LivingJournalTabContent(
    ui: PersonalUiState,
    viewMode: JournalViewMode,
    onSelectViewMode: (JournalViewMode) -> Unit,
    onRecordJournal: () -> Unit,
    onQuickWrite: () -> Unit,
    onOpenLettingGo: () -> Unit,
    onOpenNote: (String) -> Unit
) {
    val c = MM.colors
    val now = System.currentTimeMillis()
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    var searchQuery by remember { mutableStateOf("") }
    var moodFilter by remember { mutableStateOf<JournalMoodVibe?>(null) }
    var selectedVolumeMonth by remember { mutableStateOf<String?>(null) }
    var selectedCalendarDay by remember { mutableStateOf<Long?>(null) }
    val initCal = remember { Calendar.getInstance() }
    var calYear by remember { mutableStateOf(initCal.get(Calendar.YEAR)) }
    var calMonth by remember { mutableStateOf(initCal.get(Calendar.MONTH)) }

    val filteredJournals = remember(ui.journalNotes, searchQuery, moodFilter) {
        val q = searchQuery.trim().lowercase()
        ui.journalNotes.filter { note ->
            val matchesMood = moodFilter == null ||
                JournalMoodVibe.fromNameOrNull(note.metadata[PersonalNoteMeta.MOOD_VIBE]) == moodFilter
            val matchesQuery = q.isEmpty() ||
                note.title.lowercase().contains(q) ||
                note.plainText.lowercase().contains(q)
            matchesMood && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("personal_journal_tab"),
        contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        // View Switcher + Search + Mood Filter + Quick Actions
        item(key = "journal_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    JournalViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { onSelectViewMode(mode) },
                            label = { Text(mode.label, style = MM.type.caption) }
                        )
                    }
                }
                if (ui.journalNotes.isNotEmpty()) {
                    InsetPanel {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(MM.space.s))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = MM.type.body.copy(color = c.ink),
                                cursorBrush = SolidColor(c.accent),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("journal_search_input"),
                                decorationBox = { inner ->
                                    Box {
                                        if (searchQuery.isEmpty()) {
                                            Text(stringResource(R.string.personal_journal_search_placeholder), style = MM.type.body, color = c.inkMuted)
                                        }
                                        inner()
                                    }
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                TextAction("Clear", onClick = { searchQuery = "" })
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        item(key = "mood_all") {
                            FilterChip(
                                selected = moodFilter == null,
                                onClick = { moodFilter = null },
                                label = { Text("All", style = MM.type.caption) }
                            )
                        }
                        items(JournalMoodVibe.entries, key = { it.name }) { vibe ->
                            FilterChip(
                                selected = moodFilter == vibe,
                                onClick = { moodFilter = if (moodFilter == vibe) null else vibe },
                                label = { Text(vibe.label, style = MM.type.caption) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton(
                        text = "Voice Entry",
                        onClick = onRecordJournal,
                        leadingIcon = Icons.Rounded.Mic,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Add to Today",
                        onClick = onQuickWrite,
                        leadingIcon = Icons.Rounded.EditNote,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextAction("Vent & Release (let a heavy thought go)", onClick = onOpenLettingGo)
            }
        }

        if (ui.journalNotes.isEmpty()) {
            item(key = "journal_empty") {
                EmptyState(
                    title = stringResource(R.string.personal_journal_empty_title),
                    body = stringResource(R.string.personal_journal_empty_body)
                )
            }
        } else {
            when (viewMode) {
                JournalViewMode.VOLUMES -> {
                    val grouped = filteredJournals.groupBy { monthFormat.format(Date(it.eventDate ?: it.createdAt)) }
                    item(key = "volumes_shelf") {
                        SectionHeader(
                            title = "3D Bound Volumes",
                            count = grouped.size,
                            actionLabel = if (selectedVolumeMonth != null) "Show all" else null,
                            onAction = if (selectedVolumeMonth != null) { { selectedVolumeMonth = null } } else null
                        )
                        Spacer(Modifier.height(MM.space.s))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                            items(grouped.entries.toList(), key = { it.key }) { (month, notes) ->
                                val isSelected = selectedVolumeMonth == month
                                JournalVolume3DCard(
                                    title = month,
                                    subtitle = "${notes.size} ${if (notes.size == 1) "page" else "pages"} kept",
                                    entryCount = notes.size,
                                    accent = if (isSelected) c.accent else c.speaker3,
                                    onClick = {
                                        selectedVolumeMonth = if (isSelected) null else month
                                    }
                                )
                            }
                        }
                    }
                    val visible = if (selectedVolumeMonth != null) {
                        grouped[selectedVolumeMonth].orEmpty()
                    } else filteredJournals
                    items(visible, key = { "vol_note_${it.id}" }) { note ->
                        JournalEntryCard(note = note, now = now, onClick = { onOpenNote(note.id) })
                    }
                }
                JournalViewMode.MOSAIC -> {
                    val byEpochDay = filteredJournals.groupBy { note ->
                        note.metadata[PersonalNoteMeta.DAY_PAGE_EPOCH_DAY]?.toLongOrNull()
                            ?: PersonalRepository.epochDayOf(note.eventDate ?: note.createdAt)
                    }
                    item(key = "calendar_grid") {
                        JournalCalendarGridCard(
                            year = calYear,
                            monthZeroBased = calMonth,
                            todayEpochDay = ui.todayEpochDay,
                            selectedEpochDay = selectedCalendarDay,
                            notesByEpochDay = byEpochDay,
                            onPrevMonth = {
                                if (calMonth == 0) { calMonth = 11; calYear -= 1 } else { calMonth -= 1 }
                            },
                            onNextMonth = {
                                if (calMonth == 11) { calMonth = 0; calYear += 1 } else { calMonth += 1 }
                            },
                            onSelectDay = { selectedCalendarDay = it }
                        )
                    }
                    val visible = if (selectedCalendarDay != null) {
                        byEpochDay[selectedCalendarDay].orEmpty()
                    } else filteredJournals
                    if (selectedCalendarDay != null && visible.isEmpty()) {
                        item(key = "calendar_day_empty") {
                            EmptyState(
                                title = "No entries on that day",
                                body = "Tap another highlighted day or tap the selected day again to see all entries."
                            )
                        }
                    } else {
                        items(visible, key = { "mos_note_${it.id}" }) { note ->
                            JournalEntryCard(note = note, now = now, onClick = { onOpenNote(note.id) })
                        }
                    }
                }
                JournalViewMode.STREAM -> {
                    if (filteredJournals.isEmpty()) {
                        item(key = "stream_no_match") {
                            EmptyState(
                                title = "No matching entries",
                                body = "Try clearing your search or mood filter."
                            )
                        }
                    } else {
                        items(filteredJournals, key = { "stream_${it.id}" }) { note ->
                            JournalEntryCard(note = note, now = now, onClick = { onOpenNote(note.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEntryCard(note: Note, now: Long, onClick: () -> Unit) {
    val c = MM.colors
    val mood = JournalMoodVibe.fromNameOrNull(note.metadata[PersonalNoteMeta.MOOD_VIBE])
    val sourceRef = note.metadata[PersonalNoteMeta.SOURCE_NOTE_TITLE]
    MMCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(noteTimeLabel(note.updatedAt, now).uppercase(), style = MM.type.caption, color = c.inkMuted)
            if (mood != null) {
                Spacer(Modifier.width(MM.space.s))
                Text("· ${mood.label}", style = MM.type.caption, color = c.speaker3)
            }
        }
        Spacer(Modifier.height(MM.space.xs))
        Text(note.title.ifBlank { "Journal entry" }, style = MM.type.heading, color = c.ink)
        if (!sourceRef.isNullOrBlank()) {
            Text("Linked to: $sourceRef", style = MM.type.caption, color = c.accent)
        }
        if (note.plainText.isNotBlank()) {
            Spacer(Modifier.height(MM.space.xs))
            Text(
                previewOf(note.plainText),
                style = MM.type.secondary,
                color = c.inkSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ====================================================================== TAB 3: IDEA STUDIO

@Composable
private fun IdeaStudioTabContent(
    ui: PersonalUiState,
    onRecordIdea: () -> Unit,
    onQuickSpark: () -> Unit,
    onAdvanceStage: (String, IdeaStage) -> Unit,
    onGraduateToChapter: (String) -> Unit,
    onConnectCollision: (String, String) -> Unit,
    onDismissCollision: (String, String) -> Unit,
    onOpenNote: (String) -> Unit
) {
    val c = MM.colors
    var stageFilter by remember { mutableStateOf<IdeaStage?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("personal_ideas_tab"),
        contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        item(key = "idea_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton(
                        text = "Voice Spark",
                        onClick = onRecordIdea,
                        leadingIcon = Icons.Rounded.Mic,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Quick Note",
                        onClick = onQuickSpark,
                        leadingIcon = Icons.Rounded.Add,
                        modifier = Modifier.weight(1f)
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    item {
                        FilterChip(
                            selected = stageFilter == null,
                            onClick = { stageFilter = null },
                            label = { Text("All (${ui.ideaNotes.size})", style = MM.type.caption) }
                        )
                    }
                    items(IdeaStage.entries, key = { it.name }) { stage ->
                        FilterChip(
                            selected = stageFilter == stage,
                            onClick = { stageFilter = stage },
                            label = { Text(stage.label, style = MM.type.caption) }
                        )
                    }
                }
            }
        }

        // Offline Idea Collision Card
        ui.ideaCollision?.let { col ->
            item(key = "idea_collision") {
                MMCard {
                    Text("IDEA COLLISION · ${col.sharedThemes.joinToString(" · ").uppercase()}", style = MM.type.overline, color = c.gold)
                    Spacer(Modifier.height(MM.space.xs))
                    Text("Two sparks might belong together", style = MM.type.heading, color = c.ink)
                    Text(
                        "\"${col.first.title.ifBlank { "Earlier spark" }}\" + \"${col.second.title.ifBlank { "Recent spark" }}\"",
                        style = MM.type.secondary,
                        color = c.inkSecondary
                    )
                    Spacer(Modifier.height(MM.space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalAlignment = Alignment.CenterVertically) {
                        SecondaryButton(
                            text = "Link into Story Thread",
                            onClick = { onConnectCollision(col.first.id, col.second.id) }
                        )
                        TextAction(
                            text = "Not related",
                            onClick = { onDismissCollision(col.first.id, col.second.id) }
                        )
                    }
                }
            }
        }

        val filtered = ui.ideaNotes.filter { note ->
            stageFilter == null || IdeaStage.fromNameOrDefault(note.metadata[PersonalNoteMeta.IDEA_STAGE]) == stageFilter
        }
        if (filtered.isEmpty()) {
            item(key = "ideas_empty") {
                EmptyState(
                    title = stringResource(R.string.personal_ideas_empty_title),
                    body = stringResource(R.string.personal_ideas_empty_body)
                )
            }
        } else {
            items(filtered, key = { "idea_${it.id}" }) { note ->
                val stage = IdeaStage.fromNameOrDefault(note.metadata[PersonalNoteMeta.IDEA_STAGE])
                MMCard(onClick = { onOpenNote(note.id) }) {
                    Text(note.title.ifBlank { "Untitled spark" }, style = MM.type.heading, color = c.ink)
                    if (note.plainText.isNotBlank()) {
                        Spacer(Modifier.height(MM.space.xs))
                        Text(previewOf(note.plainText), style = MM.type.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(MM.space.s))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        items(IdeaStage.entries, key = { "${note.id}_${it.name}" }) { st ->
                            FilterChip(
                                selected = stage == st,
                                onClick = { if (stage != st) onAdvanceStage(note.id, st) },
                                label = { Text(st.label, style = MM.type.caption) }
                            )
                        }
                    }
                    if (stage != IdeaStage.SHIPPED) {
                        Spacer(Modifier.height(MM.space.xs))
                        TextAction("Turn into a Life Chapter", onClick = { onGraduateToChapter(note.id) })
                    }
                }
            }
        }
    }
}

// ====================================================================== TAB 4: GROWTH (JOURNEYS, ROUTINES, CHALLENGES, COACHING, PLAYBOOKS, CHAPTERS)

@Composable
private fun GrowthTabContent(
    ui: PersonalUiState,
    subTab: GrowthSubTab,
    onSelectSubTab: (GrowthSubTab) -> Unit,
    onStartJourney: (JourneyDefinition) -> Unit,
    onStartChallenge: (ChallengeDefinition) -> Unit,
    onCreateCustomChallenge: () -> Unit,
    onCheckInEnrollment: (GrowthEnrollment) -> Unit,
    onInspectEnrollment: (GrowthEnrollment) -> Unit,
    onStartRoutine: (String, Boolean) -> Unit,
    onEditRoutine: (PersonalRoutine) -> Unit,
    onSkipRoutine: (String) -> Unit,
    onAddTemplateRoutine: (String) -> Unit,
    onCreateCustomRoutine: () -> Unit,
    onPlayCoaching: (GuidedActivityDefinition) -> Unit,
    onAddCoachingToRoutine: (GuidedActivityDefinition) -> Unit,
    onTurnPlaybookIntoChallenge: (Note) -> Unit,
    onCreateLifeChapter: () -> Unit,
    onOpenLifeChapter: (LifeChapter) -> Unit,
    onAddInnerCirclePerson: () -> Unit,
    onAddPersonalTask: (String) -> Unit,
    onTogglePersonalTask: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenYouTube: () -> Unit
) {
    val c = MM.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("personal_growth_tab"),
        contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        item(key = "growth_subtabs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                items(GrowthSubTab.entries, key = { it.name }) { st ->
                    FilterChip(
                        selected = subTab == st,
                        onClick = { onSelectSubTab(st) },
                        label = { Text(st.label, style = MM.type.caption) },
                        modifier = Modifier.testTag("growth_subtab_${st.name.lowercase()}")
                    )
                }
            }
        }

        when (subTab) {
            GrowthSubTab.JOURNEYS -> {
                val pausedJourneys = ui.pausedEnrollments.filter { it.kind.name == "JOURNEY" }
                val activeAndPaused = ui.activeJourneys + pausedJourneys
                if (activeAndPaused.isNotEmpty()) {
                    item(key = "g_active_j_hdr") { SectionHeader(title = "Your Journeys", count = activeAndPaused.size) }
                    items(activeAndPaused, key = { "g_aj_${it.id}" }) { enroll ->
                        ActiveEnrollmentCard(
                            enrollment = enroll,
                            onCheckIn = { onCheckInEnrollment(enroll) },
                            onInspect = { onInspectEnrollment(enroll) }
                        )
                    }
                }
                item(key = "g_all_j_hdr") {
                    SectionHeader(title = "Guided Journeys", count = PersonalGrowthCatalog.journeys.size)
                }
                val activeIds = ui.activeJourneys.map { it.catalogId }.toSet()
                items(PersonalGrowthCatalog.journeys, key = { "cat_j_${it.id}" }) { journey ->
                    val isActive = journey.id in activeIds
                    MMCard {
                        Text("${journey.durationDays} DAYS · GUIDED JOURNEY", style = MM.type.overline, color = c.speaker3)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(journey.title, style = MM.type.heading, color = c.ink)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(journey.tagline, style = MM.type.secondary, color = c.inkSecondary)
                        Spacer(Modifier.height(MM.space.m))
                        if (isActive) {
                            Text("Currently in progress", style = MM.type.bodyStrong, color = c.speaker3)
                        } else {
                            SecondaryButton(
                                text = "Start Journey",
                                onClick = { onStartJourney(journey) }
                            )
                        }
                    }
                }
            }

            GrowthSubTab.ROUTINES -> {
                item(key = "g_my_routines_hdr") {
                    SectionHeader(
                        title = "My Routines",
                        count = ui.routines.size,
                        actionLabel = "Create custom",
                        onAction = onCreateCustomRoutine
                    )
                }
                items(ui.routines, key = { "g_r_${it.id}" }) { routine ->
                    RoutinePlanCard(
                        routine = routine,
                        completion = ui.todayCompletions[routine.id],
                        minEffortDay = ui.dailyPlan.isMinEffortDay,
                        onStart = { min -> onStartRoutine(routine.id, min) },
                        onEdit = { onEditRoutine(routine) },
                        onSkip = { onSkipRoutine(routine.id) }
                    )
                }
                val existingIds = ui.routines.map { it.id }.toSet()
                val availableTemplates = PersonalGrowthCatalog.routineTemplates.filter { it.id !in existingIds }
                if (availableTemplates.isNotEmpty()) {
                    item(key = "g_tpl_routines_hdr") {
                        SectionHeader(title = "Starter Routine Templates", count = availableTemplates.size)
                    }
                    items(availableTemplates, key = { "tpl_${it.id}" }) { tpl ->
                        MMCard {
                            Text("${tpl.timeWindow.label.uppercase()} · ${tpl.steps.size} STEPS", style = MM.type.overline, color = c.inkMuted)
                            Spacer(Modifier.height(MM.space.xs))
                            Text(tpl.title, style = MM.type.heading, color = c.ink)
                            Text(tpl.subtitle, style = MM.type.secondary, color = c.inkSecondary)
                            Spacer(Modifier.height(MM.space.m))
                            SecondaryButton(
                                text = "Add to My Routines",
                                onClick = { onAddTemplateRoutine(tpl.id) },
                                leadingIcon = Icons.Rounded.Add
                            )
                        }
                    }
                }
            }

            GrowthSubTab.CHALLENGES -> {
                val pausedChallenges = ui.pausedEnrollments.filter { it.kind.name == "CHALLENGE" }
                val activeAndPausedCh = ui.activeChallenges + pausedChallenges
                if (activeAndPausedCh.isNotEmpty()) {
                    item(key = "g_ac_hdr") { SectionHeader(title = "Your Challenges", count = activeAndPausedCh.size) }
                    items(activeAndPausedCh, key = { "g_ac_${it.id}" }) { enroll ->
                        ActiveEnrollmentCard(
                            enrollment = enroll,
                            onCheckIn = { onCheckInEnrollment(enroll) },
                            onInspect = { onInspectEnrollment(enroll) }
                        )
                    }
                }
                if (ui.activeChallenges.size >= 2) {
                    item(key = "g_overcommit") {
                        StatusLine(
                            StatusKind.Info,
                            "You have ${ui.activeChallenges.size} active challenges. Focusing on 1–2 at a time helps habits stick."
                        )
                    }
                }
                item(key = "g_all_ch_hdr") {
                    SectionHeader(
                        title = "Focused Challenges",
                        count = PersonalGrowthCatalog.challenges.size,
                        actionLabel = "Create custom",
                        onAction = onCreateCustomChallenge
                    )
                }
                val activeChIds = ui.activeChallenges.map { it.catalogId }.toSet()
                items(PersonalGrowthCatalog.challenges, key = { "cat_ch_${it.id}" }) { ch ->
                    val active = ch.id in activeChIds
                    MMCard {
                        Text("${ch.durationDays} DAYS · ${ch.category.uppercase()}", style = MM.type.overline, color = c.accent)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(ch.title, style = MM.type.heading, color = c.ink)
                        Text(ch.tagline, style = MM.type.secondary, color = c.inkSecondary)
                        Spacer(Modifier.height(MM.space.xs))
                        Text("Gentle fallback: ${ch.minEffortAction}", style = MM.type.caption, color = c.inkMuted)
                        Spacer(Modifier.height(MM.space.m))
                        if (active) {
                            Text("In progress", style = MM.type.bodyStrong, color = c.speaker3)
                        } else {
                            SecondaryButton(text = "Start Challenge", onClick = { onStartChallenge(ch) })
                        }
                    }
                }
            }

            GrowthSubTab.COACHING -> {
                item(key = "g_coaching_hdr") {
                    SectionHeader(title = "Coaching & Guided Activities", count = PersonalGrowthCatalog.guidedActivities.size)
                }
                items(PersonalGrowthCatalog.guidedActivities, key = { "act_${it.id}" }) { act ->
                    MMCard {
                        Text("${act.durationMinutes} MIN · ${act.category.label.uppercase()}", style = MM.type.overline, color = c.accent)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(act.title, style = MM.type.heading, color = c.ink)
                        Text(act.subtitle, style = MM.type.secondary, color = c.inkSecondary)
                        Spacer(Modifier.height(MM.space.m))
                        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            PrimaryButton(
                                text = "Listen",
                                onClick = { onPlayCoaching(act) },
                                leadingIcon = Icons.Rounded.Headphones
                            )
                            SecondaryButton(
                                text = "Add to Routine",
                                onClick = { onAddCoachingToRoutine(act) }
                            )
                        }
                    }
                }
            }

            GrowthSubTab.PLAYBOOKS -> {
                item(key = "g_playbooks_hdr") {
                    MMCard(onClick = onOpenYouTube) {
                        Text("MOTIVATION & LIFE SKILLS", style = MM.type.overline, color = c.gold)
                        Spacer(Modifier.height(MM.space.xs))
                        Text("Turn a YouTube talk into a personal playbook", style = MM.type.heading, color = c.ink)
                        Text(
                            "Paste any public YouTube video link to extract retention bytes, quotes, and an actionable checklist.",
                            style = MM.type.secondary,
                            color = c.inkSecondary
                        )
                    }
                }
                if (ui.growthPlaybooks.isEmpty()) {
                    item(key = "pb_empty") {
                        EmptyState(
                            title = "No playbooks yet",
                            body = "Import a Motivation or Life Skills talk from YouTube or audio to build your personal playbook shelf."
                        )
                    }
                } else {
                    items(ui.growthPlaybooks, key = { "pb_${it.id}" }) { pb ->
                        MMCard(onClick = { onOpenNote(pb.id) }) {
                            Text(pb.workflow.displayName.uppercase(), style = MM.type.overline, color = c.gold)
                            Spacer(Modifier.height(MM.space.xs))
                            Text(pb.title.ifBlank { "Personal playbook" }, style = MM.type.heading, color = c.ink)
                            Text(previewOf(pb.plainText), style = MM.type.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(MM.space.s))
                            SecondaryButton(
                                text = "Turn into 7-Day Challenge",
                                onClick = { onTurnPlaybookIntoChallenge(pb) }
                            )
                        }
                    }
                }
            }

            GrowthSubTab.CHAPTERS -> {
                item(key = "g_chapters_hdr") {
                    SectionHeader(
                        title = "Life Chapters",
                        count = ui.lifeChapters.size,
                        actionLabel = "New chapter",
                        onAction = onCreateLifeChapter
                    )
                }
                if (ui.lifeChapters.isEmpty()) {
                    item(key = "ch_empty") {
                        EmptyState(
                            title = "Name your current season",
                            body = "Group journals, sparks, and milestones around a real chapter of your life—like a move, a goal, or a fresh season."
                        )
                    }
                } else {
                    items(ui.lifeChapters, key = { "lch_${it.id}" }) { ch ->
                        MMCard(onClick = { onOpenLifeChapter(ch) }) {
                            Text(
                                "${ch.noteCount} ${if (ch.noteCount == 1) "NOTE" else "NOTES"} · LIFE CHAPTER",
                                style = MM.type.overline,
                                color = c.speaker3
                            )
                            Spacer(Modifier.height(MM.space.xs))
                            Text(ch.title, style = MM.type.heading, color = c.ink)
                            if (ch.subtitle.isNotBlank()) {
                                Text(ch.subtitle, style = MM.type.secondary, color = c.inkSecondary)
                            }
                        }
                    }
                }
                item(key = "inner_circle_section") {
                    InnerCircleSectionCard(
                        people = ui.innerCirclePeople,
                        mentionsByPersonId = ui.mentionsByPersonId,
                        onAddPerson = onAddInnerCirclePerson,
                        onOpenNote = onOpenNote
                    )
                }
                item(key = "personal_life_lists") {
                    PersonalLifeListsCard(
                        tasks = ui.personalTasks,
                        onAddTask = onAddPersonalTask,
                        onToggleTask = onTogglePersonalTask
                    )
                }
            }
        }
    }
}

// ====================================================================== TAB 5: MEMORY VAULT & TIME CAPSULES

@Composable
private fun MemoryVaultTabContent(
    ui: PersonalUiState,
    onSealCapsule: () -> Unit,
    onTapLockedCapsule: (String) -> Unit,
    onOpenUnlockedCapsule: (Note) -> Unit,
    onOpenNote: (String) -> Unit,
    onRestartRefresher: (GrowthEnrollment) -> Unit
) {
    val c = MM.colors
    val now = System.currentTimeMillis()
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("personal_vault_tab"),
        contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        item(key = "vault_header_card") {
            MMCard {
                Text("3D TIME CAPSULES", style = MM.type.overline, color = c.gold)
                Spacer(Modifier.height(MM.space.xs))
                Text("Letters to your future self", style = MM.type.heading, color = c.ink)
                Text(
                    "Seal a voice or written reflection until a future date. It stays locked privately on your phone until the day arrives.",
                    style = MM.type.secondary,
                    color = c.inkSecondary
                )
                Spacer(Modifier.height(MM.space.m))
                PrimaryButton(
                    text = "Seal a Time Capsule",
                    onClick = onSealCapsule,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (ui.timeCapsules.isNotEmpty()) {
            item(key = "capsules_hdr") { SectionHeader(title = "Your Capsules", count = ui.timeCapsules.size) }
            items(ui.timeCapsules, key = { "cap_${it.id}" }) { cap ->
                val unlockAt = cap.metadata[PersonalNoteMeta.CAPSULE_UNLOCK_AT_MS]?.toLongOrNull() ?: 0L
                val unlocked = now >= unlockAt
                val label = cap.metadata[PersonalNoteMeta.CAPSULE_LABEL].orEmpty().ifBlank { "Future date" }
                TimeCapsule3DCard(
                    title = cap.title.ifBlank { "Time Capsule" },
                    sealLabel = label,
                    isUnlocked = unlocked,
                    preview = previewOf(cap.plainText),
                    onClick = {
                        if (unlocked) {
                            onOpenUnlockedCapsule(cap)
                        } else {
                            val daysLeft = ((unlockAt - now + 86_399_999L) / 86_400_000L).coerceAtLeast(1L)
                            val dateStr = dateFmt.format(Date(unlockAt))
                            onTapLockedCapsule("Sealed until $dateStr — $daysLeft ${if (daysLeft == 1L) "day" else "days"} to go.")
                        }
                    }
                )
            }
        }

        if (ui.completedEnrollments.isNotEmpty()) {
            item(key = "milestones_hdr") { SectionHeader(title = "Completed Journeys & Challenges", count = ui.completedEnrollments.size) }
            items(ui.completedEnrollments, key = { "done_${it.id}" }) { enroll ->
                Keepsake3DCrestCard(
                    title = enroll.title,
                    kindLabel = enroll.kind.name,
                    stepsKept = enroll.targetStepsOrDays,
                    onOpenAnthology = enroll.finaleNoteId?.let { fId -> { onOpenNote(fId) } },
                    onRestartRefresher = { onRestartRefresher(enroll) }
                )
            }
        }
    }
}

// ====================================================================== IN-VERTICAL 5-TAB FLOATING DOCK

@Composable
private fun PersonalDockBar(
    current: PersonalTab,
    onSelect: (PersonalTab) -> Unit
) {
    val c = MM.colors
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MM.space.m, vertical = MM.space.s),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MM.radius.pill,
            color = c.surfaceRaised,
            border = BorderStroke(1.dp, c.line),
            shadowElevation = 6.dp
        ) {
            Row(
                Modifier.padding(horizontal = MM.space.xs, vertical = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                PersonalTab.entries.forEach { t ->
                    val selected = t == current
                    val icon: ImageVector = when (t) {
                        PersonalTab.SANCTUARY -> Icons.Rounded.SelfImprovement
                        PersonalTab.JOURNAL -> Icons.AutoMirrored.Rounded.MenuBook
                        PersonalTab.IDEAS -> Icons.Rounded.Lightbulb
                        PersonalTab.GROWTH -> Icons.Rounded.Explore
                        PersonalTab.VAULT -> Icons.Rounded.Inventory2
                    }
                    Surface(
                        onClick = { onSelect(t) },
                        shape = MM.radius.pill,
                        color = if (selected) c.accentWash else c.surfaceRaised,
                        modifier = Modifier.testTag("personal_dock_${t.name.lowercase()}")
                    ) {
                        Row(
                            Modifier.padding(horizontal = if (selected) 12.dp else 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = t.label,
                                tint = if (selected) c.accent else c.inkSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            if (selected) {
                                Spacer(Modifier.width(6.dp))
                                Text(t.label, style = MM.type.bodyStrong, color = c.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================== DIALOGS & SHEETS

@Composable
private fun SingleInputDialog(
    title: String,
    subtitle: String,
    initialValue: String,
    placeholder: String,
    confirmLabel: String,
    allowBlank: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    val c = MM.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text(title, style = MM.type.heading, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                Text(subtitle, style = MM.type.secondary, color = c.inkSecondary)
                InsetPanel {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2),
                        decorationBox = { inner ->
                            Box { if (text.isEmpty()) Text(placeholder, style = MM.type.body, color = c.inkMuted); inner() }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = allowBlank || text.isNotBlank()) {
                Text(confirmLabel, style = MM.type.bodyStrong, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MM.type.body, color = c.inkSecondary) }
        }
    )
}

@Composable
private fun TitleBodyDialog(
    title: String,
    titlePlaceholder: String,
    bodyPlaceholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var head by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val c = MM.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text(title, style = MM.type.heading, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                InsetPanel {
                    BasicTextField(
                        value = head,
                        onValueChange = { head = it },
                        textStyle = MM.type.bodyStrong.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box { if (head.isEmpty()) Text(titlePlaceholder, style = MM.type.body, color = c.inkMuted); inner() }
                        }
                    )
                }
                InsetPanel {
                    BasicTextField(
                        value = body,
                        onValueChange = { body = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2),
                        decorationBox = { inner ->
                            Box { if (body.isEmpty()) Text(bodyPlaceholder, style = MM.type.body, color = c.inkMuted); inner() }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(head, body) }, enabled = head.isNotBlank() || body.isNotBlank()) {
                Text(confirmLabel, style = MM.type.bodyStrong, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MM.type.body, color = c.inkSecondary) }
        }
    )
}

@Composable
private fun SealCapsuleDialog(
    onDismiss: () -> Unit,
    onRecordVoice: () -> Unit,
    onSeal: (title: String, body: String, daysAhead: Int, label: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val options = listOf(30 to "1 Month", 90 to "3 Months", 180 to "6 Months", 365 to "1 Year")
    var selected by remember { mutableStateOf(options[2]) }
    val c = MM.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text("Seal a Time Capsule", style = MM.type.heading, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                InsetPanel {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = MM.type.bodyStrong.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box { if (title.isEmpty()) Text("Title (e.g. To my future self)", style = MM.type.body, color = c.inkMuted); inner() }
                        }
                    )
                }
                InsetPanel {
                    BasicTextField(
                        value = body,
                        onValueChange = { body = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2),
                        decorationBox = { inner ->
                            Box { if (body.isEmpty()) Text("Write what you want your future self to hear…", style = MM.type.body, color = c.inkMuted); inner() }
                        }
                    )
                }
                Text("UNLOCK IN", style = MM.type.overline, color = c.inkMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    items(options, key = { it.first }) { opt ->
                        FilterChip(
                            selected = selected == opt,
                            onClick = { selected = opt },
                            label = { Text(opt.second, style = MM.type.caption) }
                        )
                    }
                }
                TextAction("Or record a voice reflection instead", onClick = onRecordVoice)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSeal(title, body, selected.first, selected.second) }, enabled = body.isNotBlank()) {
                Text("Seal Capsule", style = MM.type.bodyStrong, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MM.type.body, color = c.inkSecondary) }
        }
    )
}

@Composable
private fun EnrollmentCheckInDialog(
    enrollment: GrowthEnrollment,
    onDismiss: () -> Unit,
    onComplete: (usedMinEffort: Boolean, reflection: String) -> Unit
) {
    val journeyStep = PersonalGrowthCatalog.journeyById(enrollment.catalogId)?.steps?.getOrNull(enrollment.currentStepOrDay)
    val challenge = PersonalGrowthCatalog.challengeById(enrollment.catalogId)
    val customConfig = CustomChallengeConfig.fromJson(enrollment.customConfigJson)
    val stepTitle = journeyStep?.title ?: challenge?.dailyAction ?: customConfig?.dailyAction ?: "Complete today's step"
    val lesson = journeyStep?.lessonScript ?: challenge?.tagline ?: customConfig?.sourceNoteTitle?.let { "From your playbook: $it" }.orEmpty()
    val prompt = journeyStep?.reflectionPrompt ?: challenge?.reflectionPrompt ?: customConfig?.reflectionPrompt ?: "Optional reflection saved to Today's Journal"

    var reflection by remember { mutableStateOf("") }
    var minEffort by remember { mutableStateOf(false) }
    val c = MM.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text("Day ${enrollment.currentStepOrDay + 1}: $stepTitle", style = MM.type.heading, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                if (lesson.isNotBlank()) {
                    Text(lesson, style = MM.type.secondary, color = c.inkSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Used gentle minimum version", style = MM.type.caption, color = c.inkSecondary, modifier = Modifier.weight(1f))
                    Switch(checked = minEffort, onCheckedChange = { minEffort = it })
                }
                InsetPanel {
                    BasicTextField(
                        value = reflection,
                        onValueChange = { reflection = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2),
                        decorationBox = { inner ->
                            Box { if (reflection.isEmpty()) Text("$prompt (optional)", style = MM.type.body, color = c.inkMuted); inner() }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onComplete(minEffort, reflection) }) {
                Text("Mark Step Done", style = MM.type.bodyStrong, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later", style = MM.type.body, color = c.inkSecondary) }
        }
    )
}

@Composable
private fun PickRoutineDialog(
    activity: GuidedActivityDefinition,
    routines: List<PersonalRoutine>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val c = MM.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text("Add to which routine?", style = MM.type.heading, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                Text("Adds \"${activity.title}\" as a step.", style = MM.type.secondary, color = c.inkSecondary)
                routines.forEach { r ->
                    MMCard(onClick = { onPick(r.id) }, contentPadding = MM.space.m) {
                        Text(r.title, style = MM.type.bodyStrong, color = c.ink)
                        Text("${r.timeWindow.label} · ${r.activeSteps.size} steps", style = MM.type.caption, color = c.inkMuted)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", style = MM.type.body, color = c.inkSecondary) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickJournalSheet(
    onDismiss: () -> Unit,
    onSave: (heading: String, text: String) -> Unit
) {
    val prompts = listOf("Check-in", "Gratitude", "What stood out", "Evening reflection")
    var selectedHeading by remember { mutableStateOf(prompts.first()) }
    var body by remember { mutableStateOf("") }
    val c = MM.colors

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = MM.space.l)
                .padding(bottom = MM.space.xl),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text("Add to Today's Journal", style = MM.type.title, color = c.ink)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                items(prompts, key = { it }) { p ->
                    FilterChip(
                        selected = selectedHeading == p,
                        onClick = { selectedHeading = p },
                        label = { Text(p, style = MM.type.caption) }
                    )
                }
            }
            InsetPanel {
                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    textStyle = MM.type.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 3),
                    decorationBox = { inner ->
                        Box {
                            if (body.isEmpty()) Text("Write freely—it stitches straight into Today's Day Page…", style = MM.type.body, color = c.inkMuted)
                            inner()
                        }
                    }
                )
            }
            PrimaryButton(
                text = "Keep in Today's Journal",
                onClick = { onSave(selectedHeading, body) },
                enabled = body.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CustomChallengeDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, durationDays: Int, dailyAction: String, minEffortAction: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dailyAction by remember { mutableStateOf("") }
    var minAction by remember { mutableStateOf("") }
    val dayChoices = listOf(7, 14, 21, 30)
    var days by remember { mutableStateOf(7) }
    val c = MM.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        title = { Text("Create Personal Challenge", style = MM.type.heading, color = c.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                InsetPanel {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = MM.type.bodyStrong.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> Box { if (title.isEmpty()) Text("Challenge name (e.g. 7 Days of Walking)", style = MM.type.body, color = c.inkMuted); inner() } }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    dayChoices.forEach { d ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text("${d}d", style = MM.type.caption) })
                    }
                }
                InsetPanel {
                    BasicTextField(
                        value = dailyAction,
                        onValueChange = { dailyAction = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> Box { if (dailyAction.isEmpty()) Text("Daily concrete action", style = MM.type.body, color = c.inkMuted); inner() } }
                    )
                }
                InsetPanel {
                    BasicTextField(
                        value = minAction,
                        onValueChange = { minAction = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner -> Box { if (minAction.isEmpty()) Text("2-minute gentle fallback (optional)", style = MM.type.body, color = c.inkMuted); inner() } }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, days, dailyAction, minAction) }, enabled = title.isNotBlank()) {
                Text("Start Challenge", style = MM.type.bodyStrong, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = MM.type.body, color = c.inkSecondary) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LettingGoSheet(onDismiss: () -> Unit) {
    var thought by remember { mutableStateOf("") }
    var released by remember { mutableStateOf(false) }
    val c = MM.colors

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = MM.space.l)
                .padding(bottom = MM.space.xl),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text(
                if (released) "Released." else "Vent & Release",
                style = MM.type.title,
                color = c.ink
            )
            Text(
                if (released) "Nothing here was saved. Tilt your phone gently as the embers drift away."
                else "Write down a worry or frustration you don't want to keep. Releasing it lets it dissolve without saving.",
                style = MM.type.secondary,
                color = c.inkSecondary
            )
            LettingGoEmbersStage(isReleasing = released)
            if (!released) {
                InsetPanel {
                    BasicTextField(
                        value = thought,
                        onValueChange = { thought = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch * 2),
                        decorationBox = { inner ->
                            Box { if (thought.isEmpty()) Text("What are you ready to put down right now?", style = MM.type.body, color = c.inkMuted); inner() }
                        }
                    )
                }
                PrimaryButton(
                    text = "Let it go",
                    onClick = { thought = ""; released = true },
                    enabled = thought.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrimaryButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
