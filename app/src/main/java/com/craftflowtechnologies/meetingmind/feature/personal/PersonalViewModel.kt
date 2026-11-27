package com.craftflowtechnologies.meetingmind.feature.personal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotContext
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotPreview
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotRecipe
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotWorkspace
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.companion.MilestoneKind
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.personal.ChallengeDefinition
import com.craftflowtechnologies.meetingmind.core.personal.CustomChallengeConfig
import com.craftflowtechnologies.meetingmind.core.personal.GrowthEnrollment
import com.craftflowtechnologies.meetingmind.core.personal.GrowthEnrollmentKind
import com.craftflowtechnologies.meetingmind.core.personal.GrowthEnrollmentStatus
import com.craftflowtechnologies.meetingmind.core.personal.GrowthStepProgress
import com.craftflowtechnologies.meetingmind.core.personal.GuidedActivityDefinition
import com.craftflowtechnologies.meetingmind.core.personal.IdeaCollision
import com.craftflowtechnologies.meetingmind.core.personal.IdeaStage
import com.craftflowtechnologies.meetingmind.core.personal.JournalMoodVibe
import com.craftflowtechnologies.meetingmind.core.personal.JourneyDefinition
import com.craftflowtechnologies.meetingmind.core.personal.LifeChapter
import com.craftflowtechnologies.meetingmind.core.personal.PersonalDailyPlan
import com.craftflowtechnologies.meetingmind.core.personal.PersonalGrowthCatalog
import com.craftflowtechnologies.meetingmind.core.personal.PersonalGrowthIntelligence
import com.craftflowtechnologies.meetingmind.core.personal.PersonalNoteMeta
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRepository
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRhythmScheduler
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRoutine
import com.craftflowtechnologies.meetingmind.core.personal.PersonalSoundscape
import com.craftflowtechnologies.meetingmind.core.personal.PersonalVaultIntegrations
import com.craftflowtechnologies.meetingmind.core.personal.RoutineCompletion
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStep
import com.craftflowtechnologies.meetingmind.core.personal.RoutineTimeWindow
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class PersonalTab(val label: String) {
    SANCTUARY("Sanctuary"),
    JOURNAL("Journal"),
    IDEAS("Ideas"),
    GROWTH("Growth"),
    VAULT("Vault")
}

enum class JournalViewMode(val label: String) {
    STREAM("Stream"),
    VOLUMES("3D Volumes"),
    MOSAIC("Calendar")
}

enum class GrowthSubTab(val label: String) {
    JOURNEYS("Journeys"),
    ROUTINES("Routines"),
    CHALLENGES("Challenges"),
    COACHING("Coaching"),
    PLAYBOOKS("Playbooks"),
    CHAPTERS("Life & People")
}

data class PersonalUiState(
    val todayEpochDay: Long = 0L,
    val currentWindow: RoutineTimeWindow = RoutineTimeWindow.MORNING,
    val dailyPlan: PersonalDailyPlan = PersonalDailyPlan(0L),
    val routines: List<PersonalRoutine> = emptyList(),
    val todayCompletions: Map<String, RoutineCompletion> = emptyMap(),
    val inProgressCompletion: RoutineCompletion? = null,
    val activeJourneys: List<GrowthEnrollment> = emptyList(),
    val activeChallenges: List<GrowthEnrollment> = emptyList(),
    val pausedEnrollments: List<GrowthEnrollment> = emptyList(),
    val completedEnrollments: List<GrowthEnrollment> = emptyList(),
    val journalNotes: List<Note> = emptyList(),
    val ideaNotes: List<Note> = emptyList(),
    val ideaCollision: IdeaCollision? = null,
    val growthPlaybooks: List<Note> = emptyList(),
    val lifeChapters: List<LifeChapter> = emptyList(),
    val innerCirclePeople: List<PersonEntity> = emptyList(),
    val mentionsByPersonId: Map<String, List<Pair<Note, String>>> = emptyMap(),
    val personalTasks: List<Task> = emptyList(),
    val timeCapsules: List<Note> = emptyList(),
    val onThisDayMemory: Note? = null,
    val calmRhythmDaysThisWeek: Int = 0,
    val suggestedActivity: GuidedActivityDefinition = PersonalGrowthCatalog.guidedActivities.first(),
    val obsidianVaultLabel: String? = null,
    val lastObsidianSyncMs: Long = 0L,
    val isAutopilotRunning: Boolean = false,
    val isReady: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val db = MeetMindDatabase.getInstance(application)
    private val noteRepo = NoteRepository(application, db)
    private val repo = PersonalRepository(application)
    private val taskRepo = TaskRepository(db.taskDao(), db.peopleDao())

    private val _selectedTab = MutableStateFlow(PersonalTab.SANCTUARY)
    val selectedTab: StateFlow<PersonalTab> = _selectedTab.asStateFlow()

    private val _journalViewMode = MutableStateFlow(JournalViewMode.STREAM)
    val journalViewMode: StateFlow<JournalViewMode> = _journalViewMode.asStateFlow()

    private val _growthSubTab = MutableStateFlow(GrowthSubTab.JOURNEYS)
    val growthSubTab: StateFlow<GrowthSubTab> = _growthSubTab.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _pendingMilestone = MutableStateFlow<MilestoneKind?>(null)
    val pendingMilestone: StateFlow<MilestoneKind?> = _pendingMilestone.asStateFlow()

    private val _obsidianMeta = MutableStateFlow(
        if (PersonalVaultIntegrations.getPinnedObsidianUri(application) != null) {
            PersonalVaultIntegrations.getPinnedObsidianLabel(application) to PersonalVaultIntegrations.getLastSyncMs(application)
        } else {
            null to 0L
        }
    )

    private val _isAutopilotRunning = MutableStateFlow(false)

    /** Dynamic current local epoch day — refreshes cleanly across midnight via [refreshTodayIfNeeded]. */
    private val _todayDay = MutableStateFlow(repo.todayEpochDay())

    /** Pairs already linked into Story Threads or dismissed by the user so collisions never repeat. */
    private val _excludedCollisionPairs = MutableStateFlow<Set<Pair<String, String>>>(emptySet())

    init {
        viewModelScope.launch {
            runCatching { repo.ensureStarterRoutinesSeeded() }
            runCatching { _excludedCollisionPairs.value = repo.getStoryThreadLinkedPairs() }
        }
    }

    /** Call when the screen resumes or becomes visible so midnight rollover updates Today's Plan. */
    fun refreshTodayIfNeeded() {
        val current = repo.todayEpochDay()
        if (_todayDay.value != current) {
            _todayDay.value = current
        }
    }

    private val coreGrowthFlow = _todayDay.flatMapLatest { day ->
        combine(
            repo.observeActiveRoutines(),
            repo.observeCompletionsForDay(day),
            repo.observeInProgressCompletion(),
            repo.observeAllEnrollments(),
            repo.observeDailyPlan(day)
        ) { routines, completions, inProgress, enrollments, plan ->
            GrowthBundle(day, routines, completions, inProgress, enrollments, plan)
        }
    }

    private val notesFlow = _todayDay.flatMapLatest { day ->
        combine(
            repo.observeJournalNotes(),
            repo.observeIdeaNotes(),
            repo.observeGrowthPlaybooks(),
            repo.observeCompletionsSince(day - 14L),
            repo.observeLifeChapters()
        ) { journals, ideas, playbooks, recentCompletions, chapters ->
            NotesBundle(journals, ideas, playbooks, recentCompletions, chapters)
        }
    }

    private val lifeToolsFlow = combine(
        repo.observeInnerCirclePeople(),
        taskRepo.observeTasks().map { all -> all.filter { it.space == NotebookSpace.PERSONAL.name } },
        _obsidianMeta,
        _isAutopilotRunning
    ) { people, tasks, obsidian, autopilotRunning ->
        LifeToolsBundle(people, tasks, obsidian.first, obsidian.second, autopilotRunning)
    }

    val uiState: StateFlow<PersonalUiState> = combine(
        coreGrowthFlow,
        notesFlow,
        lifeToolsFlow,
        _excludedCollisionPairs
    ) { g, n, lt, excludedPairs ->
        val nowMs = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val window = RoutineTimeWindow.forHourOfDay(hour)
        val todayDay = g.todayDay

        val completionByRoutine = g.completions.associateBy { it.routineId }
        val activeJourneys = g.enrollments.filter { it.kind == GrowthEnrollmentKind.JOURNEY && it.status == GrowthEnrollmentStatus.ACTIVE }
        val activeChallenges = g.enrollments.filter { it.kind == GrowthEnrollmentKind.CHALLENGE && it.status == GrowthEnrollmentStatus.ACTIVE }
        val pausedEnrollments = g.enrollments.filter { it.status == GrowthEnrollmentStatus.PAUSED }
        val completedEnrollments = g.enrollments.filter { it.status == GrowthEnrollmentStatus.COMPLETED }

        val capsules = n.journals.filter { !it.metadata[PersonalNoteMeta.CAPSULE_UNLOCK_AT_MS].isNullOrBlank() }
        val regularJournals = n.journals.filter { it.metadata[PersonalNoteMeta.CAPSULE_UNLOCK_AT_MS].isNullOrBlank() }

        // "On This Day" or meaningful past memory (at least 3 days old so it feels reflective)
        val memoryCutoffMs = nowMs - 3L * 86_400_000L
        val memory = regularJournals.firstOrNull { it.createdAt < memoryCutoffMs && it.plainText.isNotBlank() }

        // Calm weekly rhythm: distinct days this calendar week with a completed routine or journal note.
        val weekStartDay = todayDay - repo.dayOfWeekIndex(nowMs)
        val routineDaysThisWeek = n.recentCompletions
            .filter { it.status.isDone && it.epochDay >= weekStartDay }
            .map { it.epochDay }
            .toSet()
        if (routineDaysThisWeek.size >= 5 && _pendingMilestone.value == null) {
            _pendingMilestone.value = MilestoneKind.FIRST_ROUTINE_WEEK
        }
        val activeDaysThisWeek = buildSet {
            addAll(routineDaysThisWeek)
            regularJournals.forEach { note ->
                val d = PersonalRepository.epochDayOf(note.createdAt)
                if (d >= weekStartDay) add(d)
            }
        }.size

        val suggested = when (window) {
            RoutineTimeWindow.MORNING -> PersonalGrowthCatalog.activityById("act_morning_breath")
            RoutineTimeWindow.AFTERNOON -> PersonalGrowthCatalog.activityById("act_focus_prep")
            else -> PersonalGrowthCatalog.activityById("act_evening_exhale")
        } ?: PersonalGrowthCatalog.guidedActivities.first()

        val collision = PersonalGrowthIntelligence.findBestIdeaCollision(
            ideas = n.ideas,
            excludedPairs = excludedPairs
        )

        val allPersonalNotes = regularJournals + n.ideas
        val mentionsByPerson = lt.people.associate { person ->
            person.id to repo.findMentionsForPerson(person.name, allPersonalNotes)
        }

        PersonalUiState(
            todayEpochDay = todayDay,
            currentWindow = window,
            dailyPlan = g.plan,
            routines = g.routines,
            todayCompletions = completionByRoutine,
            inProgressCompletion = g.inProgress,
            activeJourneys = activeJourneys,
            activeChallenges = activeChallenges,
            pausedEnrollments = pausedEnrollments,
            completedEnrollments = completedEnrollments,
            journalNotes = regularJournals,
            ideaNotes = n.ideas,
            ideaCollision = collision,
            growthPlaybooks = n.playbooks,
            lifeChapters = n.chapters,
            innerCirclePeople = lt.people,
            mentionsByPersonId = mentionsByPerson,
            personalTasks = lt.personalTasks,
            timeCapsules = capsules,
            onThisDayMemory = memory,
            calmRhythmDaysThisWeek = activeDaysThisWeek,
            suggestedActivity = suggested,
            obsidianVaultLabel = lt.obsidianVaultLabel,
            lastObsidianSyncMs = lt.lastObsidianSyncMs,
            isAutopilotRunning = lt.isAutopilotRunning,
            isReady = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonalUiState(todayEpochDay = _todayDay.value))

    fun selectTab(tab: PersonalTab) { _selectedTab.value = tab }

    fun selectTabByName(name: String?) {
        if (name.isNullOrBlank()) return
        PersonalTab.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { _selectedTab.value = it }
    }

    fun selectJournalViewMode(mode: JournalViewMode) { _journalViewMode.value = mode }
    fun selectGrowthSubTab(tab: GrowthSubTab) { _growthSubTab.value = tab }
    fun dismissBanner() { _statusBanner.value = null }
    fun showInfoBanner(message: String) { _statusBanner.value = message }

    fun setTodayIntention(text: String) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.setTodayIntention(text, epochDay = day, stitchIntoJournal = true)
            _statusBanner.value = "Today's intention saved to your Journal."
        }
    }

    fun toggleMinEffortDay(enabled: Boolean) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.setMinEffortDay(enabled, epochDay = day)
            _statusBanner.value = if (enabled) "Low-energy day mode on — every routine scaled to its gentle version." else "Standard routine pace restored."
        }
    }

    fun setMoodVibe(vibe: JournalMoodVibe) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.setMoodVibe(vibe, epochDay = day)
        }
    }

    fun skipRoutineToday(routineId: String) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.skipRoutineForToday(routineId, day)
            _statusBanner.value = "Rested for today — ready whenever you want it tomorrow."
        }
    }

    fun addRoutineFromTemplate(templateId: String) {
        viewModelScope.launch {
            val added = repo.addRoutineFromTemplate(templateId)
            if (added != null) {
                PersonalRhythmScheduler.sync(app, hasPersonal = true)
                _statusBanner.value = "Added \"${added.title}\" to your routines."
            }
        }
    }

    fun saveCustomRoutine(
        title: String,
        whyDescription: String,
        timeWindow: RoutineTimeWindow,
        anchorCue: String,
        soundscape: PersonalSoundscape,
        steps: List<RoutineStep>,
        reminderEnabled: Boolean,
        scheduledMinuteOfDay: Int?,
        existingId: String? = null,
        daysOfWeekMask: Int = PersonalRoutine.ALL_DAYS_MASK
    ) {
        viewModelScope.launch {
            val saved = repo.saveCustomRoutine(
                title = title,
                whyDescription = whyDescription,
                timeWindow = timeWindow,
                anchorCue = anchorCue,
                soundscape = soundscape,
                steps = steps,
                existingId = existingId,
                scheduledMinuteOfDay = scheduledMinuteOfDay,
                daysOfWeekMask = daysOfWeekMask,
                reminderEnabled = reminderEnabled
            )
            PersonalRhythmScheduler.sync(app, hasPersonal = true)
            _statusBanner.value = if (existingId != null) "Updated \"${saved.title}\"." else "Saved \"${saved.title}\"."
        }
    }

    fun archiveRoutine(routineId: String, title: String) {
        viewModelScope.launch {
            repo.archiveRoutine(routineId)
            PersonalRhythmScheduler.sync(app, hasPersonal = true)
            _statusBanner.value = "Archived \"$title\"."
        }
    }

    fun addGuidedActivityToRoutine(routineId: String, activity: GuidedActivityDefinition) {
        viewModelScope.launch {
            if (repo.appendGuidedActivityToRoutine(routineId, activity)) {
                _statusBanner.value = "Added \"${activity.title}\" to your routine."
            }
        }
    }

    fun startJourney(journey: JourneyDefinition, whyText: String) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.startJourney(journey, userWhyText = whyText, epochDay = day)
            _statusBanner.value = "Started \"${journey.title}\". Your first step is ready on Sanctuary."
        }
    }

    fun startChallenge(challenge: ChallengeDefinition) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.startChallenge(challenge, epochDay = day)
            _statusBanner.value = "Started \"${challenge.title}\"."
        }
    }

    fun completeEnrollmentStep(enrollmentId: String, usedMinEffort: Boolean, reflectionText: String) {
        val day = _todayDay.value
        viewModelScope.launch {
            val updated = repo.completeNextEnrollmentStep(enrollmentId, usedMinEffort, reflectionText, day)
            if (updated != null) {
                if (updated.status == GrowthEnrollmentStatus.COMPLETED) {
                    _statusBanner.value = "Completed \"${updated.title}\"!"
                    _pendingMilestone.value = if (updated.kind == GrowthEnrollmentKind.JOURNEY) {
                        MilestoneKind.PERSONAL_JOURNEY_DONE
                    } else {
                        MilestoneKind.PERSONAL_CHALLENGE_DONE
                    }
                } else {
                    _statusBanner.value = "Step logged for \"${updated.title}\"."
                }
            }
        }
    }

    fun updateEnrollmentStatus(enrollmentId: String, title: String, status: GrowthEnrollmentStatus) {
        viewModelScope.launch {
            repo.updateEnrollmentStatus(enrollmentId, status)
            _statusBanner.value = when (status) {
                GrowthEnrollmentStatus.PAUSED -> "Paused \"$title\". Pick it back up whenever you're ready."
                GrowthEnrollmentStatus.ACTIVE -> "Resumed \"$title\"."
                GrowthEnrollmentStatus.ABANDONED -> "Set \"$title\" aside."
                GrowthEnrollmentStatus.COMPLETED -> "Completed \"$title\"."
            }
        }
    }

    suspend fun getStepProgress(enrollmentId: String): List<GrowthStepProgress> =
        repo.getStepProgress(enrollmentId)

    suspend fun getNote(noteId: String): Note? = repo.getNote(noteId)

    fun saveQuickJournalEntry(heading: String, text: String, onSaved: (String) -> Unit = {}) {
        if (text.isBlank()) return
        val day = _todayDay.value
        viewModelScope.launch {
            val (noteId, _) = repo.stitchEntryIntoDayPage(
                epochDay = day,
                sectionHeading = heading,
                bodyText = text
            )
            _statusBanner.value = "Added to Today's Journal."
            onSaved(noteId)
        }
    }

    fun captureQuickSpark(title: String, body: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val note = repo.captureQuickSpark(title, body)
            _statusBanner.value = "Spark saved to your Idea Studio."
            onCreated(note.id)
        }
    }

    fun advanceIdeaStage(noteId: String, stage: IdeaStage) {
        viewModelScope.launch {
            repo.advanceIdeaStage(noteId, stage)
        }
    }

    fun sealTimeCapsule(title: String, body: String, daysAhead: Int, label: String) {
        if (body.isBlank()) return
        viewModelScope.launch {
            val unlockAt = System.currentTimeMillis() + daysAhead.coerceAtLeast(1) * 86_400_000L
            val created = repo.sealTimeCapsule(title, body, unlockAt, label)
            PersonalRhythmScheduler.scheduleTimeCapsuleUnlock(app, created.id, created.title, unlockAt)
            _statusBanner.value = "Time capsule sealed until $label."
        }
    }

    fun startCustomChallenge(title: String, durationDays: Int, dailyAction: String, minEffortAction: String) {
        val day = _todayDay.value
        viewModelScope.launch {
            val config = CustomChallengeConfig(
                dailyAction = dailyAction.trim().ifBlank { title.trim() },
                minEffortAction = minEffortAction.trim().ifBlank { "Take 2 minutes on a small version" },
                reflectionPrompt = "How did \"$title\" feel today?"
            )
            repo.startCustomChallenge(title, durationDays, config, day)
            _statusBanner.value = "Started custom $durationDays-day challenge: \"$title\"."
        }
    }

    fun startChallengeFromPlaybook(playbook: Note) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.startChallengeFromPlaybook(playbook, durationDays = 7, epochDay = day)
            _statusBanner.value = "Created 7-day challenge from \"${playbook.title.ifBlank { "playbook" }}\"."
        }
    }

    fun restartAsRefresher(completed: GrowthEnrollment) {
        val day = _todayDay.value
        viewModelScope.launch {
            repo.restartAsSevenDayRefresher(completed, epochDay = day)
            _statusBanner.value = "Started a 7-day refresher for \"${completed.title}\"."
        }
    }

    fun createLifeChapter(title: String, subtitle: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val ch = repo.createLifeChapter(title, subtitle)
            _statusBanner.value = "Opened new Life Chapter: \"${ch.title}\"."
        }
    }

    fun observeNotesInChapter(chapterId: String): Flow<List<Note>> =
        repo.observeNotesInChapter(chapterId)

    fun assignNoteToChapter(noteId: String, chapterId: String?, chapterTitle: String?) {
        viewModelScope.launch {
            repo.assignNoteToChapter(noteId, chapterId)
            _statusBanner.value = if (chapterTitle != null) {
                "Added note to \"$chapterTitle\"."
            } else {
                "Removed note from chapter."
            }
        }
    }

    fun archiveLifeChapter(chapterId: String, title: String) {
        viewModelScope.launch {
            repo.archiveLifeChapter(chapterId)
            _statusBanner.value = "Archived Life Chapter \"$title\"."
        }
    }

    fun graduateIdeaToChapter(ideaNoteId: String) {
        viewModelScope.launch {
            val ch = repo.graduateIdeaToLifeChapter(ideaNoteId)
            if (ch != null) {
                _statusBanner.value = "Graduated spark into Life Chapter \"${ch.title}\"."
            }
        }
    }

    fun connectCollidedIdeas(firstId: String, secondId: String) {
        viewModelScope.launch {
            repo.connectIdeasIntoThread(firstId, secondId)
            _excludedCollisionPairs.value = _excludedCollisionPairs.value +
                PersonalGrowthIntelligence.canonicalPair(firstId, secondId)
            _statusBanner.value = "Connected both sparks into a Story Thread."
        }
    }

    fun dismissIdeaCollision(firstId: String, secondId: String) {
        _excludedCollisionPairs.value = _excludedCollisionPairs.value +
            PersonalGrowthIntelligence.canonicalPair(firstId, secondId)
    }

    // ---- Track 1: Reply to Past Memory ----

    fun replyToPastMemory(pastNoteId: String, replyText: String, onCreated: (String) -> Unit = {}) {
        if (replyText.isBlank()) return
        viewModelScope.launch {
            val created = repo.replyToPastMemory(pastNoteId, replyText)
            if (created != null) {
                _statusBanner.value = "Reply linked to your memory."
                onCreated(created.id)
            }
        }
    }

    // ---- Track 2: Inner Circle & Personal Life Lists ----

    fun addInnerCirclePerson(name: String, relationship: String, notes: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val saved = repo.addInnerCirclePerson(name, relationship, notes)
            _statusBanner.value = "Added ${saved.name} to your Inner Circle."
        }
    }

    fun addPersonalTask(title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            taskRepo.save(
                Task(
                    id = "",
                    title = clean,
                    space = NotebookSpace.PERSONAL.name
                )
            )
        }
    }

    fun togglePersonalTask(taskId: String) {
        viewModelScope.launch {
            taskRepo.toggleDone(taskId)
        }
    }

    // ---- Track 3: Obsidian Vault Sync, Day One Import & Personal Pro Autopilot ----

    fun pinObsidianFolder(treeUri: String, label: String) {
        PersonalVaultIntegrations.savePinnedObsidianVault(app, treeUri, label)
        _obsidianMeta.value = label to PersonalVaultIntegrations.getLastSyncMs(app)
        _statusBanner.value = "Pinned Obsidian vault: $label."
    }

    fun syncObsidianVault() {
        viewModelScope.launch {
            val notes = uiState.value.journalNotes + uiState.value.ideaNotes + uiState.value.growthPlaybooks
            if (notes.isEmpty()) {
                _statusBanner.value = "No Personal journals or ideas to sync yet."
                return@launch
            }
            val written = PersonalVaultIntegrations.syncNotesToPinnedObsidian(app, noteRepo, notes)
            val ts = PersonalVaultIntegrations.getLastSyncMs(app)
            _obsidianMeta.value = PersonalVaultIntegrations.getPinnedObsidianLabel(app) to ts
            _statusBanner.value = if (written > 0) {
                "Synced $written Markdown ${if (written == 1) "file" else "files"} to Obsidian."
            } else {
                "Could not write to the pinned folder — try re-selecting your Obsidian folder."
            }
        }
    }

    fun importDayOneJson(rawJson: String) {
        if (rawJson.isBlank()) return
        viewModelScope.launch {
            val count = PersonalVaultIntegrations.importDayOneEntries(noteRepo, rawJson)
            _statusBanner.value = if (count > 0) {
                "Imported $count Day One ${if (count == 1) "entry" else "entries"} into your private Journal."
            } else {
                "No valid Day One entries found in that file."
            }
        }
    }

    fun runPersonalAutopilot(recipe: AutopilotRecipe, onResultNoteCreated: (String) -> Unit = {}) {
        if (_isAutopilotRunning.value) return
        viewModelScope.launch {
            _isAutopilotRunning.value = true
            try {
                val state = uiState.value
                val corpusNotes = when (recipe) {
                    AutopilotRecipe.IDEA_TO_ACTION_BLUEPRINT -> state.ideaNotes.take(3)
                    AutopilotRecipe.EVENING_STOIC_DEBRIEF -> state.journalNotes.take(2)
                    else -> (state.journalNotes.take(10) + state.ideaNotes.take(4))
                }.filter { it.plainText.isNotBlank() }

                if (corpusNotes.isEmpty()) {
                    _statusBanner.value = "Record or write at least one journal or spark before running ${recipe.label}."
                    return@launch
                }

                val combinedText = corpusNotes.joinToString("\n\n---\n\n") { n ->
                    "# ${n.title.ifBlank { n.workflow.displayName }}\n${n.plainText}"
                }
                val primary = corpusNotes.first()
                val targetWorkflow = if (recipe == AutopilotRecipe.IDEA_TO_ACTION_BLUEPRINT) RecordingType.IDEA else RecordingType.JOURNAL
                val gathered = AutopilotContext(
                    preview = AutopilotPreview(
                        sessionTitle = when (recipe) {
                            AutopilotRecipe.WEEKLY_LIFE_LETTER -> "Personal Synthesis · Recent Entries"
                            AutopilotRecipe.THERAPY_OR_COACHING_PREP -> "Session Prep · Recent Reflections"
                            else -> primary.title.ifBlank { recipe.label }
                        },
                        workflow = targetWorkflow,
                        recommendedRecipe = recipe,
                        wordCount = combinedText.split(Regex("\\s+")).count { it.isNotBlank() },
                        existingOpenTasks = state.personalTasks.count { !it.done },
                        speakerCount = 1,
                        noteId = primary.id,
                        meetingId = null,
                        notebookId = primary.notebookId,
                        stayOnDeviceOnly = false
                    ),
                    sourceText = combinedText,
                    unlocked = AutopilotWorkspace.isUnlocked(app)
                )

                when (val res = AutopilotWorkspace.runRecipe(app, gathered, recipe)) {
                    is AiResult.Success -> {
                        val noteId = AutopilotWorkspace.saveRecapAsNote(
                            context = app,
                            result = res.value,
                            workflow = targetWorkflow,
                            notebookId = primary.notebookId
                        )
                        _statusBanner.value = "${recipe.label} saved to your Personal space."
                        onResultNoteCreated(noteId)
                    }
                    is AiResult.ModelUnavailable -> {
                        _statusBanner.value = res.message
                    }
                    is AiResult.Failed -> {
                        _statusBanner.value = res.message.ifBlank { "Couldn't finish ${recipe.label} right now." }
                    }
                    is AiResult.DeviceUnsupported -> {
                        _statusBanner.value = res.reason
                    }
                    is AiResult.InsufficientMemory -> {
                        _statusBanner.value = "Not enough free memory to run ${recipe.label} right now."
                    }
                    is AiResult.InsufficientStorage -> {
                        _statusBanner.value = "Not enough free storage to run ${recipe.label} right now."
                    }
                }
            } finally {
                _isAutopilotRunning.value = false
            }
        }
    }

    private data class GrowthBundle(
        val todayDay: Long,
        val routines: List<PersonalRoutine>,
        val completions: List<RoutineCompletion>,
        val inProgress: RoutineCompletion?,
        val enrollments: List<GrowthEnrollment>,
        val plan: PersonalDailyPlan
    )

    private data class NotesBundle(
        val journals: List<Note>,
        val ideas: List<Note>,
        val playbooks: List<Note>,
        val recentCompletions: List<RoutineCompletion>,
        val chapters: List<LifeChapter>
    )

    private data class LifeToolsBundle(
        val people: List<PersonEntity>,
        val personalTasks: List<Task>,
        val obsidianVaultLabel: String?,
        val lastObsidianSyncMs: Long,
        val isAutopilotRunning: Boolean
    )
}
