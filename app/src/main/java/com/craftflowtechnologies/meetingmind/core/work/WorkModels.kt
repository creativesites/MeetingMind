package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The model a piece of work-memory writing (a brief, a prepare line, a monthly story, a scoped
 * answer) may use. [sensitive] material only ever gets the local model, or none: the cloud isn't
 * even asked. Nothing needs a model to be usable; a null here means "render without prose".
 */
fun interface WorkModels {
    suspend fun forPack(sensitive: Boolean): LanguageModel?
}

class DeviceWorkModels(
    private val context: Context,
    private val modelStorage: ModelStorage,
    private val transport: GeminiTransport,
    private val profile: suspend () -> ProcessingProfile
) : WorkModels {
    override suspend fun forPack(sensitive: Boolean): LanguageModel? {
        val factory = LanguageModelFactory(context, modelStorage, transport)
        if (sensitive) return factory.resolveLocal(ModelCapability.SYNTHESIS, ModelTier.RECOMMENDED)?.languageModel
        return factory.resolve(profile(), ModelCapability.SYNTHESIS, ModelTier.RECOMMENDED)?.languageModel
    }
}

/** Whether what a pack draws on has to stay on this phone (docs/PLAN_PROFESSIONAL.md §6.4). */
fun interface PackPrivacy {
    suspend fun mustStayOnDevice(meetingIds: List<String>, scope: PackScope): Boolean
}

/** The production rule: any recording that must stay on the device, or a confidential person, organisation or project. */
class DevicePackPrivacy(private val context: Context) : PackPrivacy {
    override suspend fun mustStayOnDevice(meetingIds: List<String>, scope: PackScope): Boolean = withContext(Dispatchers.IO) {
        val db = MeetMindDatabase.getInstance(context)
        if (scope is PackScope.Entity) {
            val confidential = when (scope.type) {
                ContextType.PROJECT -> db.notebookDao().getById(scope.id)?.propertiesJson?.let { runCatching { org.json.JSONObject(it).optBoolean("confidential") }.getOrDefault(false) } == true
                else -> db.peopleDao().getById(scope.id)?.let { p -> p.confidential || p.orgId?.let { db.peopleDao().getById(it)?.confidential } == true } == true
            }
            if (confidential) return@withContext true
        }
        meetingIds.any { WorkPrivacy.mustStayOnDevice(context, meetingId = it) }
    }
}
