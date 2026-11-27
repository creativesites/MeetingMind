package com.craftflowtechnologies.meetingmind.feature.learning

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.learning.AskPassage
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAsk
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.feature.learning.components.CitationAudio
import com.craftflowtechnologies.meetingmind.ai.learning.MistakeExplanation
import com.craftflowtechnologies.meetingmind.ai.learning.SimpleExplanation
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackEvaluation
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingCoordinator
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.learning.LearningAiStatus
import com.craftflowtechnologies.meetingmind.core.learning.StudyAiAnalytics
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
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.work.Marks
import com.craftflowtechnologies.meetingmind.feature.learning.space.MaxPlanCards
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeFormat
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticePlanItem
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeSetSize
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeStage
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeVariant
import com.craftflowtechnologies.meetingmind.feature.learning.space.QuizSetSize
import com.craftflowtechnologies.meetingmind.feature.learning.space.buildPracticeDeck
import com.craftflowtechnologies.meetingmind.feature.learning.space.nativeVariant
import com.craftflowtechnologies.meetingmind.feature.learning.space.stagesReached
import com.craftflowtechnologies.meetingmind.feature.learning.space.QuickPlanCards
import com.craftflowtechnologies.meetingmind.feature.learning.space.Release1PracticeTypes
import com.craftflowtechnologies.meetingmind.feature.learning.space.conceptSet
import com.craftflowtechnologies.meetingmind.feature.learning.space.diagnosticSet
import com.craftflowtechnologies.meetingmind.feature.learning.space.isRelease1Practice
import com.craftflowtechnologies.meetingmind.feature.learning.space.practiceItems
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMe
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

    /**
     * Every Learning task runs under this guard. A failed database write or AI call is logged and
     * the screen keeps its last state, instead of the exception ending the app.
     */
    private val crashGuard = CoroutineExceptionHandler { _, e -> Log.e("LearnViewModel", "Learning task failed", e) }

    /** The explanation being written; a newer wrong answer cancels the older one. */
    private var explainJob: Job? = null

    private val _isLoadingSession = MutableStateFlow(false)
    val isLoadingSession: StateFlow<Boolean> = _isLoadingSession.asStateFlow()

    private val _sessionLoadError = MutableStateFlow<String?>(null)
    val sessionLoadError: StateFlow<String?> = _sessionLoadError.asStateFlow()

    private val _activeTab = MutableStateFlow(LearnTab.TODAY)
    val activeTab: StateFlow<LearnTab> = _activeTab.asStateFlow()

    val courses: StateFlow<List<LearningRepository.LearningCourse>> = learningRepo.observeCourses()
        .catch { e -> Log.e("LearnViewModel", "Learning data could not be read", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<LearningSession>> = learningRepo.observeSessions()
        .catch { e -> Log.e("LearnViewModel", "Learning data could not be read", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lectureNotes: StateFlow<List<LearningRepository.LectureNoteSummary>> = learningRepo.observeLectureNotes()
        .catch { e -> Log.e("LearnViewModel", "Learning data could not be read", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyBrief: StateFlow<LearningRepository.DailyCompanionBrief?> = learningRepo.observeLiveDailyBrief()
        .catch { e -> Log.e("LearnViewModel", "Learning data could not be read", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val goals: StateFlow<List<LearningGoal>> = learningRepo.observeGoals()
        .catch { e -> Log.e("LearnViewModel", "Learning data could not be read", e) }
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
    private var detailObserveJob: Job? = null

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
        viewModelScope.launch(crashGuard) {
            runCatching { learningRepo.syncLectureNotesToSessions() }
            refreshExamPriorities()
        }
    }

    fun setTab(tab: LearnTab) {
        _activeTab.value = tab
    }

    fun syncLectures() {
        viewModelScope.launch(crashGuard) {
            runCatching { learningRepo.syncLectureNotesToSessions() }
            refreshExamPriorities(_selectedExamFilterId.value)
        }
    }

    fun refreshExamPriorities(goalOrCourseId: String? = null) {
        _selectedExamFilterId.value = goalOrCourseId
        viewModelScope.launch(crashGuard) {
            val ranked = learningRepo.computeExamPriorityMatrix(goalOrCourseId)
            _examPriorities.value = ranked
        }
    }

    fun loadSessionDetail(sessionIdOrNoteId: String) {
        viewModelScope.launch(crashGuard) {
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

                    // Keep the session's concepts, attempts and schedules live, so mastery and the
                    // next step update as soon as practice is recorded.
                    detailObserveJob?.cancel()
                    val liveSessionId = detail.session.id
                    detailObserveJob = viewModelScope.launch(crashGuard) {
                        learningRepo.observeSessionDetail(liveSessionId).collect { live ->
                            if (live != null && _activeSessionDetail.value?.session?.id == liveSessionId) {
                                _activeSessionDetail.value = live
                            }
                        }
                    }

                    // Observe live changes to the note document while Session Studio is open
                    documentObserveJob?.cancel()
                    val targetNoteId = detail.session.noteId
                    documentObserveJob = viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
            val note = noteRepo.createNote(
                workflow = RecordingType.LECTURE,
                title = title.ifBlank { "Study Note" },
                notebookId = courseNotebookId?.takeIf { noteRepo.getNotebook(it) != null }
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
        viewModelScope.launch(crashGuard) {
            learningRepo.toggleSessionPause(sessionId)
            val updated = learningRepo.getSessionDetail(sessionId)
            if (updated != null) {
                _activeSessionDetail.value = updated
            }
        }
    }

    fun startPracticeSession(activities: List<LearningActivity>) {
        // A plain deck is asked as written; beginMode sets varied formats after this.
        _practiceVariants.value = emptyMap()
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
        _practiceResults.value = emptyList()
        _activeTab.value = LearnTab.PRACTICE
    }

    fun loadWeakConceptsPractice(concepts: List<LearningConcept>, onReady: (() -> Unit)? = null) {
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
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
                    notebookId = courseNotebookId?.takeIf { noteRepo.getNotebook(it) != null }
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
        rating: RecallRating? = null,
        format: PracticeFormat? = null
    ) {
        val activities = _practiceActivities.value
        val currentIndex = _currentActivityIndex.value
        if (currentIndex !in activities.indices) return

        val activity = activities[currentIndex]
        viewModelScope.launch(crashGuard) {
            val outcome = try {
                learningRepo.recordAttempt(
                    activityId = activity.id,
                    userResponse = userResponse,
                    isCorrect = isCorrect,
                    rating = rating,
                    // The format is remembered so the next session asks this item another way.
                    feedback = format?.tag.orEmpty()
                )
            } catch (e: Exception) {
                // A failed save must never crash practice or pretend it worked.
                Log.e("LearnViewModel", "Failed to save attempt", e)
                _practiceError.value = "That answer couldn't be saved. Keep going; you can review it again later."
                return@launch
            }
            _lastOutcome.value = outcome
            _practiceResults.value = _practiceResults.value + PracticeResult(
                activity = activity,
                isCorrect = isCorrect,
                newState = outcome.masteryEvaluation?.state,
                reason = outcome.masteryEvaluation?.reason,
                nextReviewDays = outcome.schedule.intervalDays,
                format = format
            )
            val conceptId = activity.conceptId
            if (isCorrect && format != null && conceptId != null) {
                val stages = _practiceStages.value
                _practiceStages.value = stages + (conceptId to (stages[conceptId].orEmpty() + format.stage))
            }
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
        explainJob?.cancel()
        explainJob = viewModelScope.launch(crashGuard) {
            _isEvaluating.value = true
            val answer = studentAnswer.ifBlank { activity.expectedAnswer.ifBlank { "Incorrect attempt" } }
            val explanation = aiEngine.explainMistake(activity, answer)
            noteAiStatus(StudyAiAnalytics.FEATURE_MISTAKE, aiEngine.lastStatus, aiEngine.lastMadeLocally)
            _mistakeExplanation.value = explanation
            _isEvaluating.value = false
        }
    }

    /**
     * The managed allowance ran out during the last AI help on a practice item (mistake explanation, simpler
     * explanation, hints, teach-back). Null when nothing was refused. Screens show the calm panel with plans from it.
     */
    private val _aiAllowance = MutableStateFlow<LearningAiStatus.AllowanceUsedUp?>(null)
    val aiAllowance: StateFlow<LearningAiStatus.AllowanceUsedUp?> = _aiAllowance.asStateFlow()

    /** Records one AI call's outcome: an analytics event, and the allowance flag for the screens. */
    private fun noteAiStatus(feature: String, status: LearningAiStatus, madeOnPhone: Boolean = false) {
        StudyAiAnalytics.track(feature, status, madeOnPhone)
        _aiAllowance.value = status as? LearningAiStatus.AllowanceUsedUp
    }

    fun setVoiceRecording(recording: Boolean) {
        _isRecordingVoice.value = recording
    }

    fun setMicLevel(level: Float) {
        _micLevel.value = level.coerceIn(0f, 1f)
    }

    fun evaluateTeachBack(activity: LearningActivity, spokenTranscript: String) {
        viewModelScope.launch(crashGuard) {
            _isEvaluating.value = true
            val eval = aiEngine.evaluateTeachBack(
                rubric = activity.rubric,
                spokenTranscript = spokenTranscript,
                concept = null
            )
            noteAiStatus(StudyAiAnalytics.FEATURE_TEACH_BACK, eval.status, aiEngine.lastMadeLocally)
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
            val note = noteRepo.createNote(
                workflow = RecordingType.LECTURE,
                title = title.ifBlank { "Exam Cram Sheet" },
                notebookId = courseNotebookId?.takeIf { noteRepo.getNotebook(it) != null }
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
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
            _explainingSimpleConceptId.value = concept.id
            try {
                val explanation = aiEngine.explainSimply(
                    context = getApplication(),
                    conceptTitle = concept.name,
                    definition = concept.definition,
                    domain = domain
                )
                noteAiStatus(StudyAiAnalytics.FEATURE_EXPLAIN_SIMPLE, aiEngine.lastStatus, aiEngine.lastMadeLocally)
                _simpleExplanations.value = _simpleExplanations.value + (concept.id to explanation)
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to explain concept simply: ${concept.name}", e)
            } finally {
                _explainingSimpleConceptId.value = null
            }
        }
    }

    fun saveSimpleExplanationToNote(concept: LearningConcept, explanation: SimpleExplanation) {
        viewModelScope.launch(crashGuard) {
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
        viewModelScope.launch(crashGuard) {
            if (_hintLadder.value.isEmpty()) {
                _isLoadingHints.value = true
                try {
                    val hints = aiEngine.generateHintLadder(
                        context = getApplication(),
                        prompt = activity.prompt,
                        expectedAnswer = activity.expectedAnswer,
                        domain = domain
                    )
                    noteAiStatus(StudyAiAnalytics.FEATURE_HINTS, aiEngine.lastStatus, aiEngine.lastMadeLocally)
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
            studyTimerJob = viewModelScope.launch(crashGuard) {
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

    // ───────────────────────── Learning space (design spec §4, §12) ─────────────────────────

    /** Every visible concept, for Progress. */
    val allConcepts: StateFlow<List<LearningConcept>> = learningRepo.observeAllConcepts()
        .catch { e -> Log.e("LearnViewModel", "Learning data could not be read", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _practiceKind = MutableStateFlow(PracticeKind.SET)
    val practiceKind: StateFlow<PracticeKind> = _practiceKind.asStateFlow()

    private val _practiceTitle = MutableStateFlow("Practice")
    val practiceTitle: StateFlow<String> = _practiceTitle.asStateFlow()

    /** Concepts in the current practice, by id, so the summary can name what changed. */
    private val _practiceConcepts = MutableStateFlow<Map<String, LearningConcept>>(emptyMap())
    val practiceConcepts: StateFlow<Map<String, LearningConcept>> = _practiceConcepts.asStateFlow()

    private val _practiceResults = MutableStateFlow<List<PracticeResult>>(emptyList())
    val practiceResults: StateFlow<List<PracticeResult>> = _practiceResults.asStateFlow()

    private val _practiceError = MutableStateFlow<String?>(null)
    val practiceError: StateFlow<String?> = _practiceError.asStateFlow()

    /** True while a practice is open part-way, so Learn can offer to resume it. */
    val hasPausedPractice: Boolean
        get() {
            val index = _currentActivityIndex.value
            return index in 1 until _practiceActivities.value.size
        }

    /** How each item in the open practice is asked today, by activity id (see PracticeVariety.kt). */
    private val _practiceVariants = MutableStateFlow<Map<String, PracticeVariant>>(emptyMap())
    val practiceVariants: StateFlow<Map<String, PracticeVariant>> = _practiceVariants.asStateFlow()

    /** Stages each concept has shown with a correct answer, for the ladder in the summary. */
    private val _practiceStages = MutableStateFlow<Map<String, Set<PracticeStage>>>(emptyMap())
    val practiceStages: StateFlow<Map<String, Set<PracticeStage>>> = _practiceStages.asStateFlow()

    /** How the item is asked: the planned variant, or the activity's own question. */
    fun variantFor(activity: LearningActivity): PracticeVariant =
        _practiceVariants.value[activity.id] ?: nativeVariant(activity)

    /**
     * Opens a practice: a short, mixed set (sources interleaved, formats rotated against each
     * item's history, difficulty from each concept's state). If anything in the planning fails,
     * the items are asked as written, so practice always opens.
     */
    private suspend fun beginMode(
        kind: PracticeKind,
        title: String,
        activities: List<LearningActivity>,
        concepts: Collection<LearningConcept>,
        maxItems: Int = PracticeSetSize,
        interleave: Boolean = true,
        rampUp: Boolean = false,
        siblingActivities: List<LearningActivity> = activities
    ) {
        val sessionIds = activities.map { it.sessionId }.distinct()
        val attempts = runCatching {
            withContext(Dispatchers.IO) {
                if (sessionIds.isEmpty()) emptyList()
                else database.activityAttemptDao().getBySessions(sessionIds).map { it.toDomain() }
            }
        }.getOrElse { e -> Log.w("LearnViewModel", "Practice history unavailable", e); emptyList() }
        val known = runCatching { learningRepo.observeAllConcepts().first() }.getOrDefault(emptyList())
        val conceptMap = (concepts + known).distinctBy { it.id }.associateBy { it.id }
        val seed = System.currentTimeMillis() / com.craftflowtechnologies.meetingmind.core.model.DeterministicSpacedScheduler.DAY_MS * 31 + kind.ordinal
        val deck = runCatching {
            buildPracticeDeck(
                activities = activities,
                concepts = conceptMap,
                attempts = attempts,
                maxItems = maxItems,
                seed = seed,
                interleave = interleave,
                rampUp = rampUp,
                siblingActivities = siblingActivities
            )
        }.getOrElse { e ->
            Log.w("LearnViewModel", "Practice planning failed; asking items as written", e)
            activities.take(maxItems).map { PracticePlanItem(it, nativeVariant(it)) }
        }
        val types = (siblingActivities + activities).associate { it.id to it.type }
        _practiceKind.value = kind
        _practiceTitle.value = title
        _practiceConcepts.value = conceptMap
        _practiceError.value = null
        startPracticeSession(deck.map { it.activity })
        _practiceVariants.value = deck.associate { it.activity.id to it.variant }
        _practiceStages.value = runCatching { stagesReached(attempts, types) }.getOrDefault(emptyMap())
    }

    /** Daily Recall: today's due cards, bounded like the plan. [quick] is the five-minute version. */
    fun startDailyRecall(quick: Boolean = false, onReady: () -> Unit) {
        val cap = if (quick) QuickPlanCards else MaxPlanCards
        val due = dailyBrief.value?.dueReviews.orEmpty()
            .filter { it.activity.isRelease1Practice() }
        viewModelScope.launch(crashGuard) {
            _isLoadingPractice.value = true
            try {
                if (due.isNotEmpty()) {
                    beginMode(
                        PracticeKind.RECALL, "Daily Recall", due.map { it.activity }, due.mapNotNull { it.concept },
                        maxItems = cap
                    )
                } else {
                    val deck = learningRepo.getPracticeDeck(activityTypes = Release1PracticeTypes, onlyDue = true)
                    beginMode(PracticeKind.RECALL, "Daily Recall", deck, emptyList(), maxItems = cap)
                }
                onReady()
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to load Daily Recall", e)
                _practiceError.value = "Daily Recall could not be loaded. Your progress is safe. Try again."
            } finally {
                _isLoadingPractice.value = false
            }
        }
    }

    /** The session's ungraded diagnostic (5 to 8 questions). */
    fun startDiagnostic(sessionIdOrNoteId: String, onReady: () -> Unit) {
        withSessionDetail(sessionIdOrNoteId) { detail ->
            val set = diagnosticSet(detail)
            beginMode(
                PracticeKind.DIAGNOSTIC, "Quick check", set, detail.concepts,
                maxItems = set.size, siblingActivities = detail.activities
            )
            onReady()
        }
    }

    /** Up to three questions that strengthen one concept, climbing from recognition to explanation. */
    fun startConceptPractice(sessionIdOrNoteId: String, conceptId: String, onReady: () -> Unit) {
        withSessionDetail(sessionIdOrNoteId) { detail ->
            val concept = detail.concepts.firstOrNull { it.id == conceptId }
            beginMode(
                PracticeKind.CONCEPT,
                concept?.let { "Strengthen ${it.name}" } ?: "Strengthen",
                conceptSet(detail, conceptId),
                detail.concepts,
                interleave = false,
                rampUp = true,
                siblingActivities = detail.activities
            )
            onReady()
        }
    }

    /** A short mixed set from a session's Release 1 questions (due ones only when [dueOnly]). */
    fun startSessionPractice(sessionIdOrNoteId: String, dueOnly: Boolean, onReady: () -> Unit) {
        withSessionDetail(sessionIdOrNoteId) { detail ->
            val now = System.currentTimeMillis()
            val dueIds = detail.schedules.filter { !it.isPaused && it.dueAt <= now }.map { it.activityId }.toSet()
            val all = practiceItems(detail)
            // Due items first, so a capped set reviews what the scheduler asked for before anything else.
            val notDue: List<LearningActivity> = if (dueOnly) emptyList() else all.filter { it.id !in dueIds }
            val set = all.filter { it.id in dueIds } + notDue
            beginMode(
                PracticeKind.SET, if (dueOnly) "Due reviews" else detail.session.title, set, detail.concepts,
                siblingActivities = detail.activities
            )
            onReady()
        }
    }

    private fun withSessionDetail(
        sessionIdOrNoteId: String,
        block: suspend (LearningRepository.LearningSessionDetail) -> Unit
    ) {
        viewModelScope.launch(crashGuard) {
            _isLoadingPractice.value = true
            _practiceError.value = null
            try {
                val detail = _activeSessionDetail.value?.takeIf {
                    it.session.id == sessionIdOrNoteId || it.session.noteId == sessionIdOrNoteId
                } ?: learningRepo.getSessionDetail(sessionIdOrNoteId)
                    ?: database.learningSessionDao().getByNoteId(sessionIdOrNoteId)?.let { learningRepo.getSessionDetail(it.id) }
                if (detail == null) {
                    _practiceError.value = "This session could not be found."
                } else {
                    block(detail)
                }
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to start practice", e)
                _practiceError.value = "Practice could not be loaded. Your progress is safe. Try again."
            } finally {
                _isLoadingPractice.value = false
            }
        }
    }

    /** The item's saved schedule, so rating buttons can show the real next review. */
    suspend fun scheduleFor(activityId: String): ReviewSchedule? =
        runCatching { learningRepo.getSchedule(activityId) }.getOrNull()

    /** Moves the current item to the end of the deck. Nothing is recorded and nothing is lost. */
    fun skipCurrent() {
        val list = _practiceActivities.value
        val index = _currentActivityIndex.value
        if (index !in list.indices || list.size < 2) {
            nextCard()
            return
        }
        val item = list[index]
        _practiceActivities.value = list.toMutableList().apply { removeAt(index); add(item) }
        _isFlipped.value = false
        _lastOutcome.value = null
        _hintLadder.value = emptyList()
        _revealedHintCount.value = 0
    }

    /** Undo for [skipCurrent]: brings the last item back to the current position. */
    fun undoSkip(activityId: String) {
        val list = _practiceActivities.value.toMutableList()
        val from = list.indexOfFirst { it.id == activityId }
        val index = _currentActivityIndex.value
        if (from < 0 || index !in list.indices) return
        val item = list.removeAt(from)
        list.add(index, item)
        _practiceActivities.value = list
        _isFlipped.value = false
        _lastOutcome.value = null
    }

    /** Ends the practice. Answers are already saved, one by one, as they were given. */
    fun closePractice() {
        _practiceVariants.value = emptyMap()
        _practiceActivities.value = emptyList()
        _currentActivityIndex.value = 0
        _isFlipped.value = false
        _lastOutcome.value = null
        _practiceResults.value = emptyList()
    }

    fun renameConcept(concept: LearningConcept, newName: String) {
        val name = newName.trim()
        if (name.isEmpty() || name == concept.name) return
        updateConcept(concept.copy(name = name, isUserEdited = true, updatedAt = System.currentTimeMillis()))
    }

    /** Hide from practice (or bring back). Hidden concepts keep their history. */
    fun setConceptHidden(concept: LearningConcept, hidden: Boolean) {
        updateConcept(concept.copy(isDismissed = hidden, isUserEdited = true, updatedAt = System.currentTimeMillis()))
    }

    /** "Report a problem": questions on this concept are held back until it is regenerated or edited. */
    fun setConceptReported(concept: LearningConcept, reported: Boolean) {
        updateConcept(concept.copy(isStale = reported, isUserEdited = true, updatedAt = System.currentTimeMillis()))
    }

    private fun updateConcept(updated: LearningConcept) {
        viewModelScope.launch(crashGuard) {
            runCatching {
                learningRepo.saveConceptsAndActivities(updated.sessionId, listOf(updated), emptyList(), createInitialSchedules = false)
            }.onFailure { Log.e("LearnViewModel", "Failed to update concept", it) }
        }
    }

    // ───────────────────────── Citations: play the moment in place ─────────────────────────

    /** The recording behind a meeting id, if its audio file is on this phone. */
    suspend fun citationAudioFor(meetingId: String): CitationAudio? = withContext(Dispatchers.IO) {
        val m = runCatching { database.meetingDao().getMeetingById(meetingId) }.getOrNull() ?: return@withContext null
        val path = m.audioFilePath?.takeIf { java.io.File(it).exists() } ?: return@withContext null
        CitationAudio(recordingId = m.id, title = m.title.ifBlank { "Lecture" }, filePath = path)
    }

    // ───────────────────────── Ask Learning (design spec §9, D10) ─────────────────────────

    private val _askTurns = MutableStateFlow<List<AskTurn>>(emptyList())
    val askTurns: StateFlow<List<AskTurn>> = _askTurns.asStateFlow()

    private val _askScope = MutableStateFlow(AskScopeKind.SESSION)
    val askScope: StateFlow<AskScopeKind> = _askScope.asStateFlow()

    private var askKey: String? = null

    /**
     * Opens Ask for a session ([sessionId]) or for everything (null). A new context starts a new
     * conversation; reopening the same one keeps it.
     */
    fun openAsk(sessionId: String?) {
        val key = sessionId ?: "all"
        if (key != askKey) {
            askKey = key
            _askTurns.value = emptyList()
            _askScope.value = if (sessionId != null) AskScopeKind.SESSION else AskScopeKind.ALL
        }
    }

    fun setAskScope(scope: AskScopeKind) {
        _askScope.value = scope
    }

    /** What the current scope is called, for "Searching …" and "not in …" lines. */
    fun askScopeLabel(scope: AskScopeKind = _askScope.value): String {
        val session = _activeSessionDetail.value?.session?.takeIf { askKey == it.id }
        return when (scope) {
            AskScopeKind.SESSION -> session?.title ?: "this session"
            AskScopeKind.COURSE -> session?.courseName?.takeIf { it.isNotBlank() } ?: "this course"
            AskScopeKind.ALL -> "all your sessions"
        }
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _askTurns.value.any { it.pending }) return
        val scope = _askScope.value
        val label = askScopeLabel(scope)
        val turn = AskTurn(id = "ask_${UUID.randomUUID()}", question = q, scope = scope, scopeLabel = label, pending = true)
        _askTurns.value = _askTurns.value + turn
        viewModelScope.launch(crashGuard) {
            val answer = try {
                val passages = gatherPassages(scope)
                val ranked = LearningAsk.rank(q, passages)
                aiEngine.answerFromSources(q, ranked)
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Ask Learning failed", e)
                null
            }
            val status = aiEngine.lastStatus
            val madeOnPhone = aiEngine.lastMadeLocally
            StudyAiAnalytics.track(StudyAiAnalytics.FEATURE_HOME_ASK, status, madeOnPhone)
            updateTurn(turn.id) {
                it.copy(
                    pending = false,
                    answer = answer,
                    allowance = status as? LearningAiStatus.AllowanceUsedUp,
                    madeOnPhone = madeOnPhone,
                    error = if (answer == null) "Something went wrong while searching. Try asking again." else null
                )
            }
        }
    }

    /** General knowledge, only on the student's request, always labelled as not from the lecture. */
    fun askGenerally(turnId: String) {
        val turn = _askTurns.value.firstOrNull { it.id == turnId } ?: return
        updateTurn(turnId) { it.copy(generalPending = true) }
        viewModelScope.launch(crashGuard) {
            val general = runCatching { aiEngine.answerGenerally(turn.question) }.getOrNull()
            val status = aiEngine.lastStatus
            val madeOnPhone = aiEngine.lastMadeLocally
            StudyAiAnalytics.track(StudyAiAnalytics.FEATURE_HOME_ASK, status, madeOnPhone)
            val allowance = status as? LearningAiStatus.AllowanceUsedUp
            updateTurn(turnId) {
                it.copy(
                    generalPending = false,
                    general = general,
                    allowance = allowance ?: it.allowance,
                    madeOnPhone = madeOnPhone,
                    error = if (general == null && allowance == null) "A general answer needs an AI model, and none is available right now." else null
                )
            }
        }
    }

    private fun updateTurn(id: String, change: (AskTurn) -> AskTurn) {
        _askTurns.value = _askTurns.value.map { if (it.id == id) change(it) else it }
    }

    private suspend fun gatherPassages(scope: AskScopeKind): List<AskPassage> {
        val detail = _activeSessionDetail.value?.takeIf { askKey == it.session.id }
        val sessionsNow = learningRepo.observeSessions().first()
        val titles = sessionsNow.associate { it.id to it.title }
        val summaries = learningRepo.observeLectureNotes().first()
        fun summaryPassages(sessionIds: Set<String>) = summaries
            .filter { it.sessionId in sessionIds && it.summary.isNotBlank() }
            .map { AskPassage(0, it.summary, LearningEvidence(noteId = it.noteId, meetingId = it.meetingId, quote = it.summary.take(220)), it.title) }
        return when {
            scope == AskScopeKind.SESSION && detail != null -> {
                val title = detail.session.title
                val segments = _activeSessionSegments.value
                val meetingId = _activeSessionMeetingId.value
                val transcript = LearningAsk.transcriptPassages(segments, detail.session.noteId, meetingId, title)
                val notes = if (segments.isEmpty()) {
                    LearningAsk.notePassages(_activeSessionDocument.value?.note?.plainText.orEmpty(), detail.session.noteId, title)
                } else emptyList()
                transcript + notes + LearningAsk.conceptPassages(detail.concepts) { title } + summaryPassages(setOf(detail.session.id))
            }
            else -> {
                val course = detail?.session?.courseName?.takeIf { it.isNotBlank() }
                val ids = if (scope == AskScopeKind.COURSE && course != null) {
                    sessionsNow.filter { it.courseName.equals(course, ignoreCase = true) }.map { it.id }.toSet()
                } else sessionsNow.map { it.id }.toSet()
                val concepts = learningRepo.observeAllConcepts().first().filter { it.sessionId in ids }
                LearningAsk.conceptPassages(concepts) { titles[it] ?: "Your notes" } + summaryPassages(ids)
            }
        }
    }

    /**
     * "Quiz me" on an answer: practice on the concepts the question and answer are about, from
     * the student's own questions. Nothing new is invented.
     */
    fun quizFromAnswer(turn: AskTurn, onReady: () -> Unit) {
        viewModelScope.launch(crashGuard) {
            _isLoadingPractice.value = true
            _practiceError.value = null
            try {
                val text = (turn.question + " " + (turn.answer?.text ?: "")).lowercase()
                val all = learningRepo.observeAllConcepts().first()
                val active = _activeSessionDetail.value?.takeIf { askKey == it.session.id }
                val pool = if (turn.scope == AskScopeKind.SESSION && active != null) active.concepts.filter { !it.isDismissed } else all
                val matched = pool.filter { it.name.length > 2 && text.contains(it.name.lowercase()) }.take(3)
                val items = mutableListOf<LearningActivity>()
                val concepts = mutableListOf<LearningConcept>()
                matched.map { it.sessionId }.distinct().forEach { sid ->
                    val d = if (active?.session?.id == sid) active else learningRepo.getSessionDetail(sid)
                    if (d != null) {
                        concepts += d.concepts
                        matched.filter { it.sessionId == sid }.forEach { c -> items += conceptSet(d, c.id) }
                    }
                }
                if (items.isEmpty() && active != null) {
                    items += diagnosticSet(active)
                    concepts += active.concepts
                }
                if (items.isEmpty()) {
                    _practiceError.value = "There are no practice questions on this yet."
                } else {
                    beginMode(PracticeKind.SET, "Quiz: ${turn.question.take(40)}", items.distinctBy { it.id }, concepts, maxItems = QuizSetSize)
                    onReady()
                }
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Quiz me failed", e)
                _practiceError.value = "Practice could not be loaded. Try again."
            } finally {
                _isLoadingPractice.value = false
            }
        }
    }

    // ───────────────────────── Create a study session (3 steps) ─────────────────────────

    private val _createStage = MutableStateFlow<CreateStage?>(null)
    val createStage: StateFlow<CreateStage?> = _createStage.asStateFlow()

    private val _createdNoteId = MutableStateFlow<String?>(null)
    val createdNoteId: StateFlow<String?> = _createdNoteId.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError.asStateFlow()

    /** A course chosen before recording, applied to the next lecture that arrives. */
    private var pendingRecordCourse: Pair<String, Long>? = null

    fun resetCreate() {
        _createStage.value = null
        _createdNoteId.value = null
        _createError.value = null
    }

    /**
     * Step 3, "Build": puts an existing recording or note in the chosen course and builds its study
     * guide and questions. The source itself is never changed.
     */
    fun buildStudySession(noteId: String, choice: CourseChoice) {
        _createError.value = null
        _createdNoteId.value = null
        viewModelScope.launch(crashGuard) {
            try {
                _createStage.value = CreateStage.COURSE
                val courseId = resolveCourse(choice)
                if (courseId != null) learningRepo.assignNotesToCourse(courseId, listOf(noteId))
                _createStage.value = CreateStage.GUIDE
                val meeting = withContext(Dispatchers.IO) { database.meetingDao().getMeetingByNoteId(noteId) }
                val title = noteRepo.getNote(noteId)?.title?.ifBlank { null } ?: meeting?.title ?: "Study session"
                val session = learningRepo.ensureSessionForNote(noteId = noteId, meetingId = meeting?.id, title = title)
                learningRepo.bootstrapSessionMaterials(session.id, force = false, allowDefaultFallback = true)
                _createStage.value = CreateStage.READY
                _createdNoteId.value = noteId
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to build study session", e)
                _createError.value = "The study guide couldn't be built. Your recording and notes are unchanged. Try again."
            }
        }
    }

    /** Write study notes: makes the note in the chosen course and opens it. */
    fun createNotesSession(choice: CourseChoice, onCreated: (noteId: String) -> Unit) {
        viewModelScope.launch(crashGuard) {
            try {
                val courseId = resolveCourse(choice)
                createStudyNote(title = "Study notes", courseNotebookId = courseId, onCreated = onCreated)
            } catch (e: Exception) {
                Log.e("LearnViewModel", "Failed to create study notes", e)
                _createError.value = "The note couldn't be created. Try again."
            }
        }
    }

    /** Record a lecture: remembers the course so the lecture lands in it when it is ready. */
    fun prepareRecording(choice: CourseChoice, onReady: () -> Unit) {
        viewModelScope.launch(crashGuard) {
            val courseId = runCatching { resolveCourse(choice) }.getOrNull()
            pendingRecordCourse = courseId?.let { it to System.currentTimeMillis() }
            onReady()
        }
    }

    /** Files a lecture recorded after [prepareRecording] into the course chosen for it, once. */
    private fun applyPendingRecordCourse(lectures: List<LearningRepository.LectureNoteSummary>) {
        val (courseId, since) = pendingRecordCourse ?: return
        val fresh = lectures.firstOrNull { !it.isAssignedToCourse && it.hasRecording && it.updatedAt >= since } ?: return
        pendingRecordCourse = null
        assignNoteToCourse(fresh.noteId, courseId)
    }

    init {
        viewModelScope.launch(crashGuard) {
            lectureNotes.collect { applyPendingRecordCourse(it) }
        }
    }

    private suspend fun resolveCourse(choice: CourseChoice): String? = when (choice) {
        is CourseChoice.None -> null
        is CourseChoice.Existing -> choice.notebookId
        is CourseChoice.New -> learningRepo.createOrUpdateCourse(
            name = choice.name.trim(),
            properties = CourseProperties(courseCode = choice.code.trim(), lecturer = choice.lecturer.trim()),
            colorHex = null,
            icon = null
        ).notebook.id
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

/** Which practice mode is open (design spec §12.1: learn_practice?mode=). */
enum class PracticeKind { RECALL, DIAGNOSTIC, CONCEPT, SET }

/** One answered item in the open practice, for the completion summary. */
data class PracticeResult(
    val activity: LearningActivity,
    val isCorrect: Boolean,
    val newState: LearningMasteryState?,
    val reason: String?,
    val nextReviewDays: Int,
    /** How the item was asked (null for items asked outside the Learning space's practice mode). */
    val format: PracticeFormat? = null
)

/** How widely Ask Learning searches. Session is the default; it never searches the web. */
enum class AskScopeKind(val label: String) { SESSION("This session"), COURSE("This course"), ALL("All sessions") }

data class AskTurn(
    val id: String,
    val question: String,
    val scope: AskScopeKind,
    val scopeLabel: String,
    val pending: Boolean = false,
    val answer: LearningAnswer? = null,
    val generalPending: Boolean = false,
    val general: LearningAnswer? = null,
    val error: String? = null,
    /** Set when the managed allowance ran out. The answer above (if any) is still the lecture's own words. */
    val allowance: LearningAiStatus.AllowanceUsedUp? = null,
    /** True when the answer was made on this phone after the cloud route ran out. Said quietly. */
    val madeOnPhone: Boolean = false
)

/** Step 2 of creating a session. */
sealed interface CourseChoice {
    data object None : CourseChoice
    data class Existing(val notebookId: String) : CourseChoice
    data class New(val name: String, val code: String = "", val lecturer: String = "") : CourseChoice
}

/** Step 3 stages, shown honestly as done / now / waiting. */
enum class CreateStage { COURSE, GUIDE, READY }
