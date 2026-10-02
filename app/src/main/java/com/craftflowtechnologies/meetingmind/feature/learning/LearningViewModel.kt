package com.craftflowtechnologies.meetingmind.feature.learning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.learning.GeneratedActivity
import com.craftflowtechnologies.meetingmind.ai.learning.GeneratedConcept
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import com.craftflowtechnologies.meetingmind.ai.learning.LearningIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.learning.ScopedAskLearning
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import com.craftflowtechnologies.meetingmind.core.repository.DailyBrief
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class LearningViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MeetMindDatabase.getInstance(application)
    val repository = LearningRepository(application, database)
    private val prefsManager = UserPreferencesManager(application)
    private val modelFactory = LanguageModelFactory(
        context = application,
        modelStorage = com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(application),
        geminiTransport = com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(application),
        router = DefaultAiModelRouter
    )

    val dailyBrief: StateFlow<DailyBrief?> = repository.observeDailyBrief()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSessions: StateFlow<List<LearningSession>> = repository.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueReviews: StateFlow<List<Pair<LearningActivity, ReviewSchedule>>> = repository.observeDueReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    suspend fun getOrCreateSession(noteId: String, courseName: String? = null): LearningSession {
        return repository.getOrCreateSessionForNote(noteId, courseName)
    }

    suspend fun createTypedSession(title: String, courseName: String? = null): LearningSession {
        return repository.createTypedSession(title, courseName)
    }

    fun generateStudyGuideAndDiagnostic(sessionId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isGenerating.value = true
            _statusMessage.value = "Extracting key concepts & study guide..."
            try {
                val scopedAsk = ScopedAskLearning(database, resolveLanguageModel())
                val passages = scopedAsk.gatherSessionPassages(sessionId)
                if (passages.isEmpty()) {
                    _statusMessage.value = "No note or transcript text found to extract from."
                    _isGenerating.value = false
                    return@launch
                }

                val engine = LearningIntelligenceEngine(resolveLanguageModel())
                val guideResult = engine.extractStudyGuide(passages)
                if (guideResult is AiResult.Success) {
                    val concepts = guideResult.value.map { gc ->
                        LearningConcept(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            name = gc.name,
                            definition = gc.definition,
                            emphasis = gc.emphasis,
                            relationships = gc.relationships,
                            evidence = gc.evidenceIds.map { eid ->
                                LearningEvidence(noteId = sessionId, segmentIds = listOf(eid))
                            },
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    repository.saveConcepts(concepts)

                    _statusMessage.value = "Generating diagnostic quiz..."
                    val diagResult = engine.generateDiagnostic(guideResult.value, passages)
                    if (diagResult is AiResult.Success) {
                        val activities = diagResult.value.map { ga ->
                            val matchedConcept = concepts.find { it.name.equals(ga.conceptName, ignoreCase = true) }
                            LearningActivity(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                conceptId = matchedConcept?.id,
                                type = ga.type,
                                prompt = ga.prompt,
                                expectedAnswer = ga.expectedAnswer,
                                options = ga.options,
                                difficulty = ga.difficulty,
                                evidence = ga.evidenceIds.map { eid ->
                                    LearningEvidence(noteId = sessionId, segmentIds = listOf(eid))
                                },
                                isDiagnostic = true,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        repository.saveActivities(activities)
                        _statusMessage.value = "Study guide and diagnostic ready!"
                    }
                } else {
                    _statusMessage.value = "Study guide extraction was not available."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed: ${e.localizedMessage}"
            } finally {
                _isGenerating.value = false
                onDone()
            }
        }
    }

    suspend fun recordAttempt(
        activityId: String,
        userResponse: String,
        isCorrect: Boolean,
        rating: RecallRating = if (isCorrect) RecallRating.GOOD else RecallRating.AGAIN,
        feedback: String = ""
    ): ActivityAttempt {
        return repository.recordAttempt(activityId, userResponse, isCorrect, rating, feedback)
    }

    fun snoozeActivity(activityId: String, hours: Int = 24) {
        viewModelScope.launch {
            val until = System.currentTimeMillis() + (hours * 60 * 60 * 1000L)
            repository.snoozeActivity(activityId, until)
        }
    }

    fun pauseSession(sessionId: String, isPaused: Boolean) {
        viewModelScope.launch {
            repository.pauseSession(sessionId, isPaused)
        }
    }

    suspend fun askTutor(sessionId: String, question: String): AiResult<LearningAnswer> {
        val model = resolveLanguageModel()
        val scopedAsk = ScopedAskLearning(database, model)
        return scopedAsk.ask(sessionId, question)
    }

    suspend fun createQuizMe(
        sessionId: String,
        conceptName: String,
        answerText: String,
        evidence: List<LearningEvidence>
    ): AiResult<LearningActivity> {
        val model = resolveLanguageModel()
        val scopedAsk = ScopedAskLearning(database, model)
        val res = scopedAsk.createQuizMeActivity(sessionId, conceptName, answerText, evidence)
        if (res is AiResult.Success) {
            repository.saveActivities(listOf(res.value))
        }
        return res
    }

    private suspend fun resolveLanguageModel(): com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel {
        val prefs = prefsManager.preferencesFlow.first()
        val profile = prefs?.processingProfile ?: ProcessingProfile.OFFLINE
        val resolved = modelFactory.resolve(profile, ModelCapability.SUMMARIZATION, ModelTier.RECOMMENDED)
        return resolved?.languageModel ?: com.craftflowtechnologies.meetingmind.ai.llm.UnavailableLanguageModel()
    }
}
