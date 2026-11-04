package com.craftflowtechnologies.meetingmind.feature.learning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.ai.learning.MistakeExplanation
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackEvaluation
import com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingCoordinator
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.CourseProperties
import com.craftflowtechnologies.meetingmind.core.model.ExamPriorityItem
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LearnTab { TODAY, COURSES, PRACTICE, EXAM }

enum class PracticeMode { FLASHCARD, WORKED_PROBLEM, TEACH_BACK }

class LearnViewModel(
    application: Application,
    database: MeetMindDatabase = MeetMindDatabase.getInstance(application),
    val learningRepo: LearningRepository = LearningRepository(database),
    val noteRepo: NoteRepository = NoteRepository(application, database),
    val aiEngine: LearningAiEngine = LearningAiEngine(),
    private val readingCoordinator: ReadingCoordinator = ReadToMe.coordinator(application)
) : AndroidViewModel(application) {

    private val _activeTab = MutableStateFlow(LearnTab.TODAY)
    val activeTab: StateFlow<LearnTab> = _activeTab.asStateFlow()

    val courses: StateFlow<List<LearningRepository.LearningCourse>> = learningRepo.observeCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<LearningSession>> = learningRepo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyBrief: StateFlow<LearningRepository.DailyCompanionBrief?> = learningRepo.observeDailyBrief()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val goals: StateFlow<List<LearningGoal>> = learningRepo.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _examPriorities = MutableStateFlow<List<ExamPriorityItem>>(emptyList())
    val examPriorities: StateFlow<List<ExamPriorityItem>> = _examPriorities.asStateFlow()

    // Practice Arena state
    private val _activeSessionDetail = MutableStateFlow<LearningRepository.LearningSessionDetail?>(null)
    val activeSessionDetail: StateFlow<LearningRepository.LearningSessionDetail?> = _activeSessionDetail.asStateFlow()

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

    init {
        refreshExamPriorities()
    }

    fun setTab(tab: LearnTab) {
        _activeTab.value = tab
    }

    fun refreshExamPriorities(goalOrCourseId: String? = null) {
        viewModelScope.launch {
            val ranked = learningRepo.computeExamPriorityMatrix(goalOrCourseId)
            _examPriorities.value = ranked
        }
    }

    fun loadSessionDetail(sessionId: String) {
        viewModelScope.launch {
            val detail = learningRepo.getSessionDetail(sessionId)
            _activeSessionDetail.value = detail
        }
    }

    fun createCourse(
        name: String,
        courseCode: String = "",
        domain: SubjectDomain = SubjectDomain.GENERAL,
        instructor: String = "",
        term: String = "",
        colorHex: String? = null,
        icon: String? = null
    ) {
        viewModelScope.launch {
            val props = CourseProperties(
                courseCode = courseCode.trim(),
                lecturer = instructor.trim(),
                domain = domain
            )
            learningRepo.createOrUpdateCourse(
                name = name.trim(),
                properties = props,
                colorHex = colorHex,
                icon = icon
            )
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
        }
    }

    fun startPracticeSession(activities: List<LearningActivity>) {
        _practiceActivities.value = activities
        _currentActivityIndex.value = 0
        _isFlipped.value = false
        _lastOutcome.value = null
        _mistakeExplanation.value = null
        _teachBackEvaluation.value = null
        _activeTab.value = LearnTab.PRACTICE
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

            if (!isCorrect) {
                // Pre-generate mistake explanation
                explainMistake(activity, userResponse)
            }
        }
    }

    fun nextCard() {
        val activities = _practiceActivities.value
        if (_currentActivityIndex.value + 1 < activities.size) {
            _currentActivityIndex.value += 1
            _isFlipped.value = false
            _lastOutcome.value = null
            _mistakeExplanation.value = null
            _teachBackEvaluation.value = null
        }
    }

    fun explainMistake(activity: LearningActivity, studentAnswer: String) {
        viewModelScope.launch {
            _isEvaluating.value = true
            val explanation = aiEngine.explainMistake(activity, studentAnswer)
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
            learningRepo.recordAttempt(
                activityId = activity.id,
                userResponse = spokenTranscript,
                isCorrect = isSuccess,
                rating = if (isSuccess) RecallRating.GOOD else RecallRating.HARD,
                feedback = eval.summary
            )
        }
    }

    fun generateCramSheet(sessionIds: List<String>, title: String, onGenerated: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _isGeneratingCramSheet.value = true
            val markdown = learningRepo.buildCramSheetMarkdown(sessionIds, title)
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
            val blocks = listOf(
                com.craftflowtechnologies.meetingmind.core.model.NoteBlock(
                    id = NoteRepository.newId("block"),
                    noteId = note.id,
                    position = 0,
                    type = com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH,
                    content = com.craftflowtechnologies.meetingmind.core.notes.RichText.plain(markdown)
                )
            )
            noteRepo.saveBlocks(note.id, blocks)
            withContext(Dispatchers.Main) {
                onSaved(note.id)
            }
        }
    }

    fun readSessionAloud(noteId: String) {
        readingCoordinator.start(noteId)
    }
}
