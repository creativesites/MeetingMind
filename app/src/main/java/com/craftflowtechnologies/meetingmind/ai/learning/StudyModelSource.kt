package com.craftflowtechnologies.meetingmind.ai.learning

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory
import com.craftflowtechnologies.meetingmind.ai.routing.ResolvedLanguageModel
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy
import kotlinx.coroutines.flow.first

/**
 * Chooses the language model for a Study job. The seam exists so view models and tests can supply
 * a model without the device's settings.
 */
fun interface StudyModelSource {
    /** Null when no model is available for this note. */
    suspend fun resolve(noteId: String): ResolvedLanguageModel?
}

/**
 * The app's rule, the same one note AI uses (see `NoteAiWorker`): the processing profile from
 * settings, narrowed by the note's privacy, then [LanguageModelFactory] picks Gemini in Internet
 * mode with a key, or the best installed on-device model. Offline never reaches the network.
 */
class AppStudyModelSource(private val context: Context) : StudyModelSource {
    override suspend fun resolve(noteId: String): ResolvedLanguageModel? {
        val prefs = UserPreferencesManager(context).preferencesFlow.first()
        val profile = WorkPrivacy.forNote(context, noteId, prefs.processingProfile)
        val factory = LanguageModelFactory(
            context = context,
            modelStorage = LocalModelStorage(context),
            geminiTransport = CloudAi.transport(context)
        )
        return factory.resolve(profile, ModelCapability.SUMMARIZATION)
    }

    /** Frees the on-device model after a job, as note AI does. Cloud models hold nothing. */
    suspend fun done(model: ResolvedLanguageModel) {
        if (!model.isCloud) LlmEngineManager.release()
    }
}
