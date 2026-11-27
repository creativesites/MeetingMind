package com.craftflowtechnologies.meetingmind.feature.learn.lesson

import android.app.Application
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Types and the Ask engine for the lesson's "Ask about this lecture" sheet (the bottom bar's Ask).
 *
 * The retrieval is the one Ask Learning uses (`LearningAsk`: passages, ranking, grounded answer with
 * numbered citations). Only the scope is narrower: this lecture, or its course.
 */

/** Where an Ask question looks. The lesson opens on [THIS_LECTURE]. */
enum class LessonAskScope(val label: String) {
    THIS_LECTURE("This lecture"),
    COURSE("This course")
}

/** One question and its answer. [pending] while searching; [general] only when the learner asked for it. */
data class LessonAskTurn(
    val id: String,
    val question: String,
    val scope: LessonAskScope,
    val pending: Boolean = false,
    val answer: LearningAnswer? = null,
    val generalPending: Boolean = false,
    val general: LearningAnswer? = null,
    val error: String? = null
)

/** The concept's state the concept sheet's "Not right?" panel needs: what the learner has already changed. */
data class ConceptFlags(
    val hidden: Boolean = false,
    val reported: Boolean = false,
    val userEdited: Boolean = false
)

/**
 * The on-device or cloud engine behind Ask. Built the way Learning builds its own, so both answer the same way.
 */
object LessonAi {
    fun engine(application: Application): LearningAiEngine = LearningAiEngine(
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
                runCatching { com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager.release() }
            }
        }
    )
}
