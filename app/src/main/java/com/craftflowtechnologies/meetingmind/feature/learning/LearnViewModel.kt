package com.craftflowtechnologies.meetingmind.feature.learning

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.ai.learning.MistakeExplanation
import com.craftflowtechnologies.meetingmind.ai.learning.SimpleExplanation
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackEvaluation
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingCoordinator
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.CourseProperties
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.ExamPriorityItem
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.NoteDocument
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.work.Marks
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class LearnTab { TODAY, COURSES, PRACTICE, EXAM }

enum class PracticeMode { FLASHCARD, WORKED_PROBLEM, TEACH_BACK }

enum class PracticeArenaFilter(val label: String) {
    ALL("All Mixed"),
    FLASHCARDS("Flashcards"),
    PROBLEMS_QUIZ("Quiz & Problems"),
    TEACH_BACK("Voice Teach-Back");

    fun matchingTypes(): Set<LearningActivityType>? = when (this) {
        ALL -> null
        FLASHCARDS -> setOf(LearningActivityType.RECALL)
        PROBLEMS_QUIZ -> setOf(LearningActivityType.WORKED_PROBLEM, LearningActivityType.MULTIPLE_CHOICE, LearningActivityType.APPLICATION)
        TEACH_BACK -> setOf(LearningActivityType.TEACH_BACK)
    }
}

data class PracticeSessionStats(
    val attemptedCount: Int = 0,
    val correctCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val masteryUpgrades: Int = 0
) {
    val accuracyPercent: Int
        get() = if (attemptedCount > 0) ((correctCount * 100f) / attemptedCount).toInt() else 0
}

class LearnViewModel @JvmOverloads constructor(
    application: Application,
    private val database: MeetMindDatabase = MeetMindDatabase.getInstance(application),
    val learningRepo: LearningRepository = LearningRepository(database),
    val noteRepo: NoteRepository = NoteRepository(application, database),
    val aiEngine: LearningAiEngine = LearningAiEngine(
        transport = com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(application),
        fallbackModelProvider = {
            com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory(
                context = application,
                modelStorage = com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(application),
                geminiTransport = com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(application)
            ).resolveLocal(
                capability = com.craftflowtechnologies.meetingmind.core.model.ModelCapability.SYNTHESIS,
                preferredTier = com.craftflowtechnologies.meetingmind.core.model.ModelTier.RECOMMENDED
            )?.languageModel
        },
        preferLocal = {
            com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(application)
                .preferencesFlow.first().processingProfile ==
                com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.OFFLINE
        },
        onLocalFinished = {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager.release()
                }
            }
        }
    ),
    private val readingCoordinator: ReadingCoordinator = ReadToMe.coordinator(application)
) : AndroidViewModel(application) {

    private val _isLoadingSession = MutableStateFlow(false)
    val isLoadingSession: StateFlow<Boolean> = _isLoadingSession.asStateFlow()

    private val _sessionLoadError = MutableStateFlow<String?>(null)
    val sessionLoadError: StateFlow<String?> = _sessionLoadError.asStateFlow()

    private val _activeTab = MutableStateFlow(LearnTab.TODAY)
    val activeTab: StateFlow<LearnTab> = _activeTab.asStateFlow()

    val courses: StateFlow<List<LearningRepository.LearningCourse>> = learningRepo.observeCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<LearningSession>> = learningRepo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lectureNotes: StateFlow<List<LearningRepository.LectureNoteSummary>> = learningRepo.observeLectureNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyBrief: StateFlow<LearningRepository.DailyCompanionBrief?> = learningRepo.observeDailyBrief()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val goals: StateFlow<List<LearningGoal>> = learningRepo.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _examPriorities = MutableStateFlow<List<ExamPriorityItem>>(emptyList())
    val examPriorities: StateFlow<List<ExamPriorityItem>> = _examPriorities.asStateFlow()

    private val _selectedExamFilterId = MutableStateFlow<String?>(null)
    val selectedExamFilterId: StateFlow<String?> = _selectedExamFilterId.asStateFlow()

    // Session Studio state (including linked note document & transcript segments)
    private val _activeSessionDetail = MutableStateFlow<LearningRepository.LearningSessionDetail?>(null)
    val activeSessionDetail: StateFlow<LearningRepository.LearningSessionDetail?> = _activeSessionDetail.asStateFlow()

    private val _activeSessionDocument = MutableStateFlow<NoteDocument?>(null)
    val activeSessionDocument: StateFlow<NoteDocument?> = _activeSessionDocument.asStateFlow()

    private val _activeSessionSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val activeSessionSegments: StateFlow<List<TranscriptSegment>> = _activeSessionSegments.asStateFlow()

    private val _activeSessionMeetingId = MutableStateFlow<String?>(null)
    val activeSessionMeetingId: StateFlow<String?> = _activeSessionMeetingId.asStateFlow()

    private val _isSynthesizingSession = MutableStateFlow(false)
    val isSynthesizingSession: StateFlow<Boolean> = _isSynthesizingSession.asStateFlow()

    private var documentObserveJob: Job? = null

    // Practice Arena state
    private val _practiceFilter = MutableStateFlow(PracticeArenaFilter.ALL)
    val practiceFilter: StateFlow<PracticeArenaFilter> = _practiceFilter.asStateFlow()

    private val _practiceCourseId = MutableStateFlow<String?>(null)
    val practiceCourseId: StateFlow<String?> = _practiceCourseId.asStateFlow()

    private val _practiceSessionId = MutableStateFlow<String?>(null)
    val practiceSessionId: StateFlow<String?> = _practiceSessionId.asStateFlow()

    private val _practiceStats = MutableStateFlow(PracticeSessionStats())
    val practiceStats: StateFlow<PracticeSessionStats> = _practiceStats.asStateFlow()

    private val _missedActivityIds = MutableStateFlow<Set<String>>(emptySet())
    val missedActivityIds: StateFlow<Set<String>> = _missedActivityIds.asStateFlow()

    private val _savedConceptExplanationIds = MutableStateFlow<Set<String>>(emptySet())
    val savedConceptExplanationIds: StateFlow<Set<String>> = _savedConceptExplanationIds.asStateFlow()

    private val _isLoadingPractice = MutableStateFlow(false)
    val isLoadingPractice: StateFlow<Boolean> = _isLoadingPractice.asStateFlow()

    private val _practiceActivities = MutableStateFlow<List<LearningActivity>>(emptyList())
    val practiceActivities: StateFlow<List<LearningActivity>> = _practiceActivities.asStateFlow()

    private val _currentActivityIndex = MutableStateFlow(0)
    val currentActivityIndex: StateFlow<Int> = _currentActivityIndex.asStateFlow()

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _lastOutcome = MutableStateFlow<LearningRepository.AttemptOutcome?>(null)
    val lastOutcome: StateFlow<LearningRepository.AttemptOutcome?> = _lastOutcome.asStateFlow()

    private val _mistakeExplanation = MutableStateFlow<MistakeExplanation?>(null)
    val mistakeExplanation: StateFlow<MistakeExplanation?> = _mistakeExplanation.asStateFlow()

    private val _teachBackEvaluation = MutableStateFlow<TeachBackEvaluation?>(null)
    val teachBackEvaluation: StateFlow<TeachBackEvaluation?> = _teachBackEvaluation.asStateFlow()

    private val _isEvaluating = MutableStateFlow(false)
    val isEvaluating: StateFlow<Boolean> = _isEvaluating.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    // Cram sheet generator state
    private val _cramSheetMarkdown = MutableStateFlow<String?>(null)
    val cramSheetMarkdown: StateFlow<String?> = _cramSheetMarkdown.asStateFlow()

    private val _isGeneratingCramSheet = MutableStateFlow(false)
    val isGeneratingCramSheet: StateFlow<Boolean> = _isGeneratingCramSheet.asStateFlow()

    // Concept Visuals & Diagram Images
    private val _conceptVisuals = MutableStateFlow<Map<String, LearningVisualSpec>>(emptyMap())
    val conceptVisuals: StateFlow<Map<String, LearningVisualSpec>> = _conceptVisuals.asStateFlow()

    private val _generatingVisualConceptId = MutableStateFlow<String?>(null)
    val generatingVisualConceptId: StateFlow<String?> = _generatingVisualConceptId.asStateFlow()

    private val _visualError = MutableStateFlow<String?>(null)
    val visualError: StateFlow<String?> = _visualError.asStateFlow()

    // Explain Simply (ELI12)
    private val _simpleExplanations = MutableStateFlow<Map<String, SimpleExplanation>>(emptyMap())
    val simpleExplanations: StateFlow<Map<String, SimpleExplanation>> = _simpleExplanations.asStateFlow()

    private val _explainingSimpleConceptId = MutableStateFlow<String?>(null)
    val explainingSimpleConceptId: StateFlow<String?> = _explainingSimpleConceptId.asStateFlow()

    // 3-Tier Socratic Hint Ladder (for PracticeArenaScreen)
    private val _hintLadder = MutableStateFlow<List<String>>(emptyList())
    val hintLadder: StateFlow<List<String>> = _hintLadder.asStateFlow()

    private val _revealedHintCount = MutableStateFlow(0)
    val revealedHintCount: StateFlow<Int> = _revealedHintCount.asStateFlow()

    private val _isLoadingHints = MutableStateFlow(false)
    val isLoadingHints: StateFlow<Boolean> = _isLoadingHints.asStateFlow()

    // Focus Study Timer (Pomodoro)
    private val _studyTimerSecondsLeft = MutableStateFlow(25 * 60)
    val studyTimerSecondsLeft: StateFlow<Int> = _studyTimerSecondsLeft.asStateFlow()

    private val _isStudyTimerRunning = MutableStateFlow(false)
    val isStudyTimerRunning: StateFlow<Boolean> = _isStudyTimerRunning.asStateFlow()

    private val _studyTimerTotalSeconds = MutableStateFlow(25 * 60)
    val studyTimerTotalSeconds: StateFlow<Int> = _studyTimerTotalSeconds.asStateFlow()

    private var studyTimerJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { learningRepo.syncLectureNotesToSessions() }
            refreshExamPriorities()
        }
    }

    fun setTab(tab: LearnTab) {
        _activeTab.value = tab
    }

    fun syncLectures() {
        viewModelScope.launch {
            runCatching { learningRepo.syncLectureNotesToSessions() }
            refreshExamPriorities(_selectedExamFilterId.value)
        }
    }

    fun refreshExamPriorities(goalOrCourseId: String? = null) {
        _selectedExamFilterId.value = goalOrCourseId
        viewModelScope.launch {
            val ranked = learningRepo.computeExamPriorityMatrix(goalOrCourseId)
            _examPriorities.value = ranked
        }
    }

    fun loadSessionDetail(sessionIdOrNoteId: String) {
        viewModelScope.launch {
            _isLoadingSession.value = true
            _sessionLoadError.value = null
            try {
                var detail = learningRepo.getSessionDetail(sessionIdOrNoteId)
                if (detail == null) {
                    val sessionEntity = database.learningSessionDao().getByNoteId(sessionIdOrNoteId)
                    if (sessionEntity != null) {
                        detail = learningRepo.getSessionDetail(sessionEntity.id)
                    } else {
                        val note = noteRepo.getNote(sessionIdOrNoteId)
                        val meeting = withContext(Dispatchers.IO) {
                            database.meetingDao().getMeetingByNoteId(sessionIdOrNoteId)
                        }
                        val title = note?.title?.ifBlank { meeting?.title.orEmpty() }?.ifBlank { "Study Session" } ?: "Study Session"
                        val session = learningRepo.ensureSessionForNote(
                            noteId = sessionIdOrNoteId,
                            meetingId = meeting?.id,
                            title = title
                        )
                        detail = learningRepo.getSessionDetail(session.id)
                    }
                }
                if (detail != null) {
                    // If the note has blocks/text/transcript and 0 activities, bootstrap materials automatically
                    val noteDoc = noteRepo.getDocument(detail.session.noteId)
                    val meeting = withContext(Dispatchers.IO) {
                        detail!!.session.meetingId?.let { database.meetingDao().getMeetingById(it) }
                            ?: database.meetingDao().getMeetingByNoteId(detail!!.session.noteId)
                    }
                    val segments = withContext(Dispatchers.IO) {
                        meeting?.id?.let { database.transcriptDao().getSegmentsForMeetingDirect(it) }
                            ?.map {
                                TranscriptSegment(
                                    id = it.id,
                                    meetingId = it.meetingId,
                                    speakerId = it.speakerId,
                                    speakerName = it.speakerName,
                                    startMs = it.startMs,
                                    endMs = it.endMs,
                                    text = it.cleanedText ?: it.text,
                                    confidence = it.confidence
                                )
                            }.orEmpty()
                    }
                    _activeSessionMeetingId.value = meeting?.id ?: detail.session.meetingId
                    _activeSessionSegments.value = segments
                    _activeSessionDocument.value = noteDoc

                    if (detail.activities.isEmpty() && (
                            noteDoc?.blocks?.isNotEmpty() == true ||
                                !noteDoc?.note?.plainText.isNullOrBlank() ||
                                segments.isNotEmpty()
                            )
                    ) {
                        detail = learningRepo.bootstrapSessionMaterials(detail.session.id, allowDefaultFallback = true) ?: detail
                    }

                    // Observe live changes to the note document while Session Studio is open
                    documentObserveJob?.cancel()
                    val targetNoteId = detail.session.noteId
                    documentObserveJob = viewModelScope.launch {
                        noteRepo.observeDocument(targetNoteId).collect { doc ->
                            _activeSessionDocument.value = doc
                        }
                    }
                }
                _activeSessionDetail.value = detail
                if (detail == null) {
                    _sessionLoadError.value = "Study session not found"
                }
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to load study session", e)
                _sessionLoadError.value = "Could not open this study session. Please try again."
            } finally {
                _isLoadingSession.value = false
            }
        }
    }

    fun regenerateSessionMaterials(sessionId: String) {
        viewModelScope.launch {
            _isSynthesizingSession.value = true
            try {
                val detail = learningRepo.getSessionDetail(sessionId) ?: return@launch
                val segments = _activeSessionSegments.value
                if (segments.isNotEmpty()) {
                    val marks = withContext(Dispatchers.IO) {
                        val mId = _activeSessionMeetingId.value ?: detail.session.meetingId
                        if (mId != null) Marks.forMeeting(database, mId) else emptyList()
                    }
                    val synthesis = aiEngine.synthesizeLecture(
                        noteId = detail.session.noteId,
                        sessionId = sessionId,
                        segments = segments,
                        domain = detail.session.domain,
                        marks = marks
                    )
                    learningRepo.saveConceptsAndActivities(sessionId, synthesis.concepts, synthesis.activities)
                    learningRepo.bootstrapSessionMaterials(sessionId, force = false, allowDefaultFallback = true)
                } else {
                    learningRepo.bootstrapSessionMaterials(sessionId, force = true, allowDefaultFallback = true)
                }
                _activeSessionDetail.value = learningRepo.getSessionDetail(sessionId)
                refreshExamPriorities(_selectedExamFilterId.value)
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to synthesize session materials", e)
            } finally {
                _isSynthesizingSession.value = false
            }
        }
    }

    fun addCustomConcept(
        sessionId: String,
        name: String,
        definition: String,
        formulaLatex: String? = null,
        importance: ExamImportance = ExamImportance.HIGH_YIELD
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val concept = LearningConcept(
                id = "concept_${UUID.randomUUID()}",
                sessionId = sessionId,
                name = name.trim(),
                definition = definition.trim().ifBlank { "Key concept in this study session." },
                state = LearningMasteryState.DEVELOPING,
                examImportance = importance,
                formulaLatex = formulaLatex?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
                isUserEdited = true
            )
            val card = LearningActivity(
                id = "act_${UUID.randomUUID()}",
                sessionId = sessionId,
                conceptId = concept.id,
                type = LearningActivityType.RECALL,
                prompt = "Define **${concept.name}** and explain its significance.",
                expectedAnswer = buildString {
                    append(concept.definition)
                    if (!concept.formulaLatex.isNullOrBlank()) append("\nFormula: ${concept.formulaLatex}")
                },
                difficulty = if (importance != ExamImportance.NORMAL) "HARD" else "MEDIUM",
                isDiagnostic = true,
                createdAt = now,
                updatedAt = now
            )
            learningRepo.saveConceptsAndActivities(sessionId, listOf(concept), listOf(card))
            _activeSessionDetail.value = learningRepo.getSessionDetail(sessionId)
            refreshExamPriorities(_selectedExamFilterId.value)
        }
    }

    fun toggleConceptImportance(concept: LearningConcept) {
        viewModelScope.launch {
            val nextImportance = when (concept.examImportance) {
                ExamImportance.NORMAL -> ExamImportance.HIGH_YIELD
                ExamImportance.HIGH_YIELD -> ExamImportance.LECTURER_FLAGGED
                ExamImportance.LECTURER_FLAGGED -> ExamImportance.NORMAL
            }
            val updated = concept.copy(
                examImportance = nextImportance,
                updatedAt = System.currentTimeMillis(),
                isUserEdited = true
            )
            learningRepo.saveConceptsAndActivities(concept.sessionId, listOf(updated), emptyList(), createInitialSchedules = false)
            if (_activeSessionDetail.value?.session?.id == concept.sessionId) {
                _activeSessionDetail.value = learningRepo.getSessionDetail(concept.sessionId)
            }
            refreshExamPriorities(_selectedExamFilterId.value)
        }
    }

    fun createCourse(
        name: String,
        courseCode: String = "",
        domain: SubjectDomain = SubjectDomain.GENERAL,
        instructor: String = "",
        term: String = "",
        colorHex: String? = null,
        icon: String? = null,
        noteIdsToAssign: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val props = CourseProperties(
                courseCode = courseCode.trim(),
                lecturer = instructor.trim(),
                syllabusTerms = if (term.isNotBlank()) listOf(term.trim()) else emptyList(),
                domain = domain
            )
            val created = learningRepo.createOrUpdateCourse(
                name = name.trim(),
                properties = props,
                colorHex = colorHex,
                icon = icon
            )
            if (noteIdsToAssign.isNotEmpty()) {
                learningRepo.assignNotesToCourse(created.notebook.id, noteIdsToAssign)
                refreshActiveSessionIfMatched(noteIdsToAssign)
            }
        }
    }

    fun createCourseFromNotes(
        noteIds: List<String>,
        courseName: String? = null,
        courseCode: String = "",
        domain: SubjectDomain? = null,
        instructor: String = "",
        term: String = "",
        targetGrade: String = "",
        onCreated: ((LearningRepository.LearningCourse) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val course = learningRepo.createCourseFromNotes(
                noteIds = noteIds,
                courseName = courseName,
                courseCode = courseCode,
                instructor = instructor,
                term = term,
                targetGrade = targetGrade,
                domain = domain
            )
            refreshActiveSessionIfMatched(noteIds)
            refreshExamPriorities(_selectedExamFilterId.value)
            if (onCreated != null) {
                withContext(Dispatchers.Main) { onCreated(course) }
            }
        }
    }

    fun assignNoteToCourse(noteId: String, courseNotebookId: String) {
        viewModelScope.launch {
            learningRepo.assignNotesToCourse(courseNotebookId, listOf(noteId))
            refreshActiveSessionIfMatched(listOf(noteId))
            refreshExamPriorities(_selectedExamFilterId.value)
        }
    }

    private suspend fun refreshActiveSessionIfMatched(noteIds: List<String>) {
        val currentSession = _activeSessionDetail.value?.session ?: return
        if (currentSession.noteId in noteIds) {
            _activeSessionDetail.value = learningRepo.getSessionDetail(currentSession.id)
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            noteRepo.deleteNotebook(courseId)
        }
    }

    fun createOrUpdateGoal(
        id: String = "",
        title: String,
        examDateMs: Long,
        notebookId: String? = null,
        targetGrade: String = "A",
        dailyMinutesTarget: Int = 30,
        sessionIds: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val goal = LearningGoal(
                id = id.ifBlank { java.util.UUID.randomUUID().toString() },
                title = title.trim(),
                examDateMs = examDateMs,
                notebookId = notebookId,
                examFormat = targetGrade,
                dailyMinutesTarget = dailyMinutesTarget,
                sessionIds = sessionIds,
                createdAt = now,
                updatedAt = now
            )
            learningRepo.saveGoal(goal)
            refreshExamPriorities(notebookId ?: id)
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            learningRepo.deleteGoal(goalId)
            refreshExamPriorities()
        }
    }

    fun createStudyNote(
        title: String,
        domain: SubjectDomain = SubjectDomain.GENERAL,
        courseNotebookId: String? = null,
        onCreated: (noteId: String) -> Unit
    ) {
        viewModelScope.launch {
            val note = noteRepo.createNote(
                workflow = RecordingType.LECTURE,
                title = title.ifBlank { "Study Note" },
                notebookId = courseNotebookId
            )
            val session = learningRepo.ensureSessionForNote(
                noteId = note.id,
                title = note.title,
                domain = domain
            )
            withContext(Dispatchers.Main) {
                onCreated(note.id)
            }
        }
    }

    fun toggleSessionPause(sessionId: String) {
        viewModelScope.launch {
            learningRepo.toggleSessionPause(sessionId)
            val updated = learningRepo.getSessionDetail(sessionId)
            if (updated != null) {
                _activeSessionDetail.value = updated
            }
        }
    }

    fun startPracticeSession(activities: List<LearningActivity>) {
        _practiceActivities.value = activities
        _currentActivityIndex.value = 0
        _isFlipped.value = false
        _lastOutcome.value = null
        _mistakeExplanation.value = null
        _teachBackEvaluation.value = null
        _hintLadder.value = emptyList()
        _revealedHintCount.value = 0
        _practiceStats.value = PracticeSessionStats()
        _missedActivityIds.value = emptySet()
        _activeTab.value = LearnTab.PRACTICE
    }

    fun loadWeakConceptsPractice(concepts: List<LearningConcept>, onReady: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoadingPractice.value = true
            try {
                val conceptIds = concepts.map { it.id }.toSet()
                val sessionIds = concepts.map { it.sessionId }.distinct()
                val matchedActivities = mutableListOf<LearningActivity>()
                for (sId in sessionIds) {
                    val deck = learningRepo.getPracticeDeck(sessionId = sId)
                    matchedActivities.addAll(deck.filter { it.conceptId in conceptIds })
                }
                val finalDeck = matchedActivities.ifEmpty {
                    learningRepo.getPracticeDeck(onlyDue = false)
                }
                startPracticeSession(finalDeck)
                if (onReady != null) {
                    withContext(Dispatchers.Main) { onReady() }
                }
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to load weak concept practice", e)
            } finally {
                _isLoadingPractice.value = false
            }
        }
    }

    fun loadPracticeByFilter(
        filter: PracticeArenaFilter = _practiceFilter.value,
        sessionId: String? = _practiceSessionId.value,
        courseId: String? = _practiceCourseId.value,
        activityId: String? = null,
        onlyDue: Boolean = false
    ) {
        _practiceFilter.value = filter
        _practiceSessionId.value = sessionId
        _practiceCourseId.value = courseId
        viewModelScope.launch {
            _isLoadingPractice.value = true
            try {
                val deck = learningRepo.getPracticeDeck(
                    activityTypes = filter.matchingTypes(),
                    sessionId = sessionId,
                    courseNotebookId = courseId,
                    onlyDue = onlyDue
                )
                val ordered = if (!activityId.isNullOrBlank()) {
                    val target = deck.firstOrNull { it.id == activityId }
                    if (target != null) listOf(target) + deck.filterNot { it.id == activityId } else deck
                } else {
                    deck
                }
                startPracticeSession(ordered)
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to load practice deck", e)
            } finally {
                _isLoadingPractice.value = false
            }
        }
    }

    fun ensurePracticeLoaded(sessionId: String? = null, activityId: String? = null) {
        if (!sessionId.isNullOrBlank() || !activityId.isNullOrBlank() || _practiceActivities.value.isEmpty()) {
            loadPracticeByFilter(
                filter = _practiceFilter.value,
                sessionId = sessionId?.takeIf { it.isNotBlank() },
                courseId = _practiceCourseId.value,
                activityId = activityId?.takeIf { it.isNotBlank() },
                onlyDue = false
            )
        }
    }

    fun generateQuickTopicDeck(
        topic: String,
        domain: SubjectDomain = SubjectDomain.GENERAL,
        courseNotebookId: String? = null,
        onReady: (() -> Unit)? = null
    ) {
        val cleanTopic = topic.trim().ifBlank { "${domain.displayName} Essentials" }
        viewModelScope.launch {
            _isLoadingPractice.value = true
            try {
                val resolvedDomain = if (domain == SubjectDomain.GENERAL) {
                    SubjectDomain.detect(cleanTopic, cleanTopic)
                } else {
                    domain
                }
                val note = noteRepo.createNote(
                    workflow = RecordingType.LECTURE,
                    title = cleanTopic,
                    notebookId = courseNotebookId
                )
                val session = learningRepo.ensureSessionForNote(
                    noteId = note.id,
                    title = cleanTopic,
                    domain = resolvedDomain
                )
                val syntheticSegment = TranscriptSegment(
                    id = "seg_${UUID.randomUUID()}",
                    meetingId = "quick_${session.id}",
                    startMs = 0L,
                    endMs = 60_000L,
                    text = "Core university lecture on $cleanTopic in ${resolvedDomain.displayName}. Key definitions, formulas, worked examples, and exam applications for $cleanTopic."
                )
                val synthesis = runCatching {
                    aiEngine.synthesizeLecture(
                        noteId = note.id,
                        sessionId = session.id,
                        segments = listOf(syntheticSegment),
                        domain = resolvedDomain,
                        marks = emptyList()
                    )
                }.getOrNull()
                if (synthesis != null && synthesis.concepts.isNotEmpty()) {
                    learningRepo.saveConceptsAndActivities(session.id, synthesis.concepts, synthesis.activities)
                }
                learningRepo.bootstrapSessionMaterials(session.id, force = false, allowDefaultFallback = true)
                val deck = learningRepo.getPracticeDeck(
                    activityTypes = _practiceFilter.value.matchingTypes(),
                    sessionId = session.id
                )
                startPracticeSession(deck)
                refreshExamPriorities(_selectedExamFilterId.value)
                if (onReady != null) {
                    withContext(Dispatchers.Main) { onReady() }
                }
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to generate quick topic deck", e)
            } finally {
                _isLoadingPractice.value = false
            }
        }
    }

    fun flipCard() {
        _isFlipped.value = !_isFlipped.value
    }

    fun recordRecallAttempt(
        userResponse: String,
        isCorrect: Boolean,
        rating: RecallRating? = null
    ) {
        val activities = _practiceActivities.value
        val currentIndex = _currentActivityIndex.value
        if (currentIndex !in activities.indices) return

        val activity = activities[currentIndex]
        viewModelScope.launch {
            val outcome = learningRepo.recordAttempt(
                activityId = activity.id,
                userResponse = userResponse,
                isCorrect = isCorrect,
                rating = rating
            )
            _lastOutcome.value = outcome
            val isMissed = !isCorrect || rating == RecallRating.AGAIN || rating == RecallRating.HARD
            if (isMissed) {
                _missedActivityIds.value = _missedActivityIds.value + activity.id
            }
            updatePracticeStats(isCorrect, outcome.masteryEvaluation?.state == LearningMasteryState.STRONG)

            if (!isCorrect) {
                // Pre-generate mistake explanation
                explainMistake(activity, userResponse)
            }
            if (_activeSessionDetail.value?.session?.id == activity.sessionId) {
                _activeSessionDetail.value = learningRepo.getSessionDetail(activity.sessionId)
            }
            refreshExamPriorities(_selectedExamFilterId.value)
        }
    }

    private fun updatePracticeStats(isCorrect: Boolean, upgradedToStrong: Boolean) {
        val prev = _practiceStats.value
        val nextStreak = if (isCorrect) prev.currentStreak + 1 else 0
        _practiceStats.value = prev.copy(
            attemptedCount = prev.attemptedCount + 1,
            correctCount = prev.correctCount + (if (isCorrect) 1 else 0),
            currentStreak = nextStreak,
            bestStreak = maxOf(prev.bestStreak, nextStreak),
            masteryUpgrades = prev.masteryUpgrades + (if (upgradedToStrong) 1 else 0)
        )
    }

    fun previousCard() {
        if (_currentActivityIndex.value > 0) {
            _hintLadder.value = emptyList()
            _revealedHintCount.value = 0
            _currentActivityIndex.value -= 1
            _isFlipped.value = false
            _lastOutcome.value = null
            _mistakeExplanation.value = null
            _teachBackEvaluation.value = null
        }
    }

    fun nextCard() {
        val activities = _practiceActivities.value
        _hintLadder.value = emptyList()
        _revealedHintCount.value = 0
        if (_currentActivityIndex.value + 1 < activities.size) {
            _currentActivityIndex.value += 1
            _isFlipped.value = false
            _lastOutcome.value = null
            _mistakeExplanation.value = null
            _teachBackEvaluation.value = null
        } else {
            // Advance past last index so PracticeArenaScreen shows the completion summary!
            _currentActivityIndex.value = activities.size
            _isFlipped.value = false
            _lastOutcome.value = null
            _mistakeExplanation.value = null
            _teachBackEvaluation.value = null
        }
    }

    fun restartPracticeDeck(weakOnly: Boolean = false) {
        val current = _practiceActivities.value
        if (current.isEmpty()) {
            loadPracticeByFilter()
            return
        }
        if (weakOnly) {
            val missedIds = _missedActivityIds.value
            val missedDeck = current.filter { it.id in missedIds }
            if (missedDeck.isNotEmpty()) {
                startPracticeSession(missedDeck)
                return
            }
        }
        startPracticeSession(if (weakOnly && current.size > 1) current.shuffled() else current)
    }

    fun requestMistakeExplanation(activity: LearningActivity, studentAnswer: String = "") {
        explainMistake(activity, studentAnswer)
    }

    fun explainMistake(activity: LearningActivity, studentAnswer: String = "") {
        viewModelScope.launch {
            _isEvaluating.value = true
            val answer = studentAnswer.ifBlank { activity.expectedAnswer.ifBlank { "Incorrect attempt" } }
            val explanation = aiEngine.explainMistake(activity, answer)
            _mistakeExplanation.value = explanation
            _isEvaluating.value = false
        }
    }

    fun setVoiceRecording(recording: Boolean) {
        _isRecordingVoice.value = recording
    }

    fun setMicLevel(level: Float) {
        _micLevel.value = level.coerceIn(0f, 1f)
    }

    fun evaluateTeachBack(activity: LearningActivity, spokenTranscript: String) {
        viewModelScope.launch {
            _isEvaluating.value = true
            val eval = aiEngine.evaluateTeachBack(
                rubric = activity.rubric,
                spokenTranscript = spokenTranscript,
                concept = null
            )
            _teachBackEvaluation.value = eval
            _isEvaluating.value = false

            // Record as attempt if evaluated
            val isSuccess = eval.scorePercent >= 60
            val outcome = learningRepo.recordAttempt(
                activityId = activity.id,
                userResponse = spokenTranscript,
                isCorrect = isSuccess,
                rating = if (isSuccess) RecallRating.GOOD else RecallRating.HARD,
                feedback = eval.summary
            )
            updatePracticeStats(isSuccess, outcome.masteryEvaluation?.state == LearningMasteryState.STRONG)
            if (_activeSessionDetail.value?.session?.id == activity.sessionId) {
                _activeSessionDetail.value = learningRepo.getSessionDetail(activity.sessionId)
            }
            refreshExamPriorities(_selectedExamFilterId.value)
        }
    }

    fun generateCramSheet(sessionIds: List<String>, title: String, onGenerated: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _isGeneratingCramSheet.value = true
            val resolvedIds = sessionIds.ifEmpty {
                sessions.value.map { it.id }
            }
            val markdown = learningRepo.buildCramSheetMarkdown(resolvedIds, title)
            _cramSheetMarkdown.value = markdown
            _isGeneratingCramSheet.value = false
            onGenerated?.invoke(markdown)
        }
    }

    fun saveCramSheetAsNote(
        title: String,
        courseNotebookId: String?,
        markdown: String,
        onSaved: (noteId: String) -> Unit
    ) {
        viewModelScope.launch {
            val note = noteRepo.createNote(
                workflow = RecordingType.LECTURE,
                title = title.ifBlank { "Exam Cram Sheet" },
                notebookId = courseNotebookId
            )
            val parsedBlocks = com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport.parse(
                markdown = markdown,
                noteId = note.id,
                source = com.craftflowtechnologies.meetingmind.core.model.BlockSource.AI
            )
            val blocks = parsedBlocks.ifEmpty {
                listOf(
                    com.craftflowtechnologies.meetingmind.core.model.NoteBlock(
                        id = NoteRepository.newId("block"),
                        noteId = note.id,
                        position = 0,
                        type = com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH,
                        content = com.craftflowtechnologies.meetingmind.core.notes.RichText.plain(markdown)
                    )
                )
            }
            noteRepo.saveBlocks(note.id, blocks)
            learningRepo.ensureSessionForNote(noteId = note.id, title = note.title)
            withContext(Dispatchers.Main) {
                onSaved(note.id)
            }
        }
    }

    fun readSessionAloud(noteId: String) {
        readingCoordinator.start(noteId)
    }

    fun generateVisualForConcept(
        concept: LearningConcept,
        domain: SubjectDomain,
        preferredKind: LearningVisualKind = LearningVisualKind.AUTO
    ) {
        viewModelScope.launch {
            _generatingVisualConceptId.value = concept.id
            _visualError.value = null
            try {
                if (preferredKind == LearningVisualKind.AI_IMAGE) {
                    when (val result = aiEngine.generateDiagramImage(getApplication(), concept.name, concept.definition, domain)) {
                        is AiResult.Success -> {
                            val spec = LearningVisualSpec(
                                id = concept.id,
                                title = concept.name,
                                subtitle = "AI Diagram Illustration",
                                kind = LearningVisualKind.AI_IMAGE,
                                imagePath = result.value,
                                caption = concept.definition.take(140)
                            )
                            _conceptVisuals.value = _conceptVisuals.value + (concept.id to spec)
                        }
                        is AiResult.ModelUnavailable -> {
                            _visualError.value = result.message
                        }
                        is AiResult.Failed -> {
                            _visualError.value = result.message
                        }
                        else -> {
                            _visualError.value = "Failed to generate diagram image"
                        }
                    }
                } else {
                    val spec = aiEngine.generateConceptVisual(
                        context = getApplication(),
                        conceptTitle = concept.name,
                        definition = concept.definition,
                        domain = domain,
                        preferredKind = preferredKind,
                        formulaLatex = concept.formulaLatex
                    )
                    _conceptVisuals.value = _conceptVisuals.value + (concept.id to spec)
                }
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to generate visual for ${concept.name}", e)
                _visualError.value = e.message ?: "Failed to generate visual"
            } finally {
                _generatingVisualConceptId.value = null
            }
        }
    }

    fun explainConceptSimply(concept: LearningConcept, domain: SubjectDomain) {
        viewModelScope.launch {
            _explainingSimpleConceptId.value = concept.id
            try {
                val explanation = aiEngine.explainSimply(
                    context = getApplication(),
                    conceptTitle = concept.name,
                    definition = concept.definition,
                    domain = domain
                )
                _simpleExplanations.value = _simpleExplanations.value + (concept.id to explanation)
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to explain concept simply: ${concept.name}", e)
            } finally {
                _explainingSimpleConceptId.value = null
            }
        }
    }

    fun saveSimpleExplanationToNote(concept: LearningConcept, explanation: SimpleExplanation) {
        viewModelScope.launch {
            try {
                val sessionDetail = _activeSessionDetail.value
                    ?: learningRepo.getSessionDetail(concept.sessionId)
                    ?: return@launch
                val noteId = sessionDetail.session.noteId
                val existingDoc = noteRepo.getDocument(noteId) ?: return@launch
                val body = buildString {
                    if (explanation.analogy.isNotBlank()) {
                        append("Analogy: ${explanation.analogy.trim()}\n")
                    }
                    if (explanation.everydayExample.isNotBlank()) {
                        append("Example: ${explanation.everydayExample.trim()}\n")
                    }
                    if (explanation.keyTakeaway.isNotBlank()) {
                        append("Takeaway: ${explanation.keyTakeaway.trim()}")
                    }
                }.trim()
                if (body.isBlank()) return@launch
                val newBlock = com.craftflowtechnologies.meetingmind.core.model.NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = noteId,
                    position = existingDoc.blocks.size,
                    type = com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.CALLOUT,
                    content = com.craftflowtechnologies.meetingmind.core.notes.RichText.plain(body),
                    source = com.craftflowtechnologies.meetingmind.core.model.BlockSource.ENRICHED,
                    payload = mapOf(
                        com.craftflowtechnologies.meetingmind.core.model.NoteBlock.PAYLOAD_CALLOUT_KIND to "DEFINITION",
                        com.craftflowtechnologies.meetingmind.core.model.NoteBlock.PAYLOAD_CALLOUT_TITLE to "Intuition: ${concept.name}",
                        com.craftflowtechnologies.meetingmind.core.model.NoteBlock.PAYLOAD_UNPACKED to "1"
                    )
                )
                noteRepo.saveBlocks(noteId, existingDoc.blocks + newBlock)
                _savedConceptExplanationIds.value = _savedConceptExplanationIds.value + concept.id
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to save simple explanation to note", e)
            }
        }
    }

    fun requestNextHint(activity: LearningActivity, domain: SubjectDomain = SubjectDomain.GENERAL) {
        viewModelScope.launch {
            if (_hintLadder.value.isEmpty()) {
                _isLoadingHints.value = true
                try {
                    val hints = aiEngine.generateHintLadder(
                        context = getApplication(),
                        prompt = activity.prompt,
                        expectedAnswer = activity.expectedAnswer,
                        domain = domain
                    )
                    _hintLadder.value = hints
                    _revealedHintCount.value = if (hints.isNotEmpty()) 1 else 0
                } catch (e: Exception) {
                    Log.e("LearnViewModel", "Failed to generate hint ladder", e)
                } finally {
                    _isLoadingHints.value = false
                }
            } else {
                val current = _revealedHintCount.value
                if (current < _hintLadder.value.size) {
                    _revealedHintCount.value = current + 1
                }
            }
        }
    }

    fun toggleStudyTimer() {
        if (_isStudyTimerRunning.value) {
            studyTimerJob?.cancel()
            _isStudyTimerRunning.value = false
        } else {
            if (_studyTimerSecondsLeft.value <= 0) {
                _studyTimerSecondsLeft.value = _studyTimerTotalSeconds.value
            }
            _isStudyTimerRunning.value = true
            studyTimerJob = viewModelScope.launch {
                while (_studyTimerSecondsLeft.value > 0) {
                    delay(1000)
                    _studyTimerSecondsLeft.value -= 1
                }
                _isStudyTimerRunning.value = false
            }
        }
    }

    fun setStudyTimerMinutes(minutes: Int) {
        studyTimerJob?.cancel()
        _isStudyTimerRunning.value = false
        val total = minutes.coerceAtLeast(1) * 60
        _studyTimerTotalSeconds.value = total
        _studyTimerSecondsLeft.value = total
    }

    fun resetStudyTimer() {
        studyTimerJob?.cancel()
        _isStudyTimerRunning.value = false
        _studyTimerSecondsLeft.value = _studyTimerTotalSeconds.value
    }

    override fun onCleared() {
        super.onCleared()
        studyTimerJob?.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager.release()
            }
        }
    }
}
