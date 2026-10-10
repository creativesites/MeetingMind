package com.craftflowtechnologies.meetingmind.core.create

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiHttpTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import kotlinx.coroutines.flow.first

/** The text model Create writes with. A seam so tests can script it. */
interface CreateTextModel {
    /** False when there is no model to ask (no Internet mode, no key): the studio then offers the editor and starters. */
    suspend fun available(): Boolean
    suspend fun complete(system: String, prompt: String, timeoutMs: Long = 40_000L): AiResult<String>
}

/** Gemini behind the app's own transport. */
class GeminiCreateModel(private val context: Context) : CreateTextModel {
    override suspend fun available(): Boolean = runCatching {
        UserPreferencesManager(context).preferencesFlow.first().processingProfile == ProcessingProfile.INTERNET &&
            GeminiCredentialStore(context).getApiKey() != null
    }.getOrDefault(false)

    override suspend fun complete(system: String, prompt: String, timeoutMs: Long): AiResult<String> =
        GeminiHttpTransport(GeminiCredentialStore(context)).execute(
            GeminiRequest(
                modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                systemInstruction = system,
                prompt = prompt,
                temperature = 0.9f,
                timeoutMs = timeoutMs
            )
        )
}

/** Verse text that came from the Bible library, with the translation it came from. Never stored on a card. */
data class ResolvedScripture(
    val reference: String,
    val text: String,
    val versionId: Int,
    val versionAbbreviation: String,
    val attribution: String
)

sealed interface ScriptureFetch {
    data class Found(val scripture: ResolvedScripture) : ScriptureFetch
    /** The text is not a reference the Bible knows (John 3:99). */
    data class Invalid(val input: String) : ScriptureFetch
    /** A real reference whose text could not be loaded (offline, not licensed). */
    data class Unavailable(val reference: String, val message: String) : ScriptureFetch
}

/** Where scripture text comes from: the Bible provider, by reference. Create never accepts verse text from a model. */
interface CreateScriptureLookup {
    suspend fun fetch(reference: String, versionId: Int? = null): ScriptureFetch
}

class ProviderScriptureLookup(private val service: ScriptureService) : CreateScriptureLookup {
    constructor(context: Context) : this(ScriptureService(context))

    override suspend fun fetch(reference: String, versionId: Int?): ScriptureFetch {
        val parsed = ScriptureReferenceParser.parse(reference) ?: return ScriptureFetch.Invalid(reference)
        return when (val r = service.passage(parsed, versionId)) {
            is PassageResult.Found -> ScriptureFetch.Found(
                ResolvedScripture(parsed.display(), r.passage.text.trim(), r.passage.versionId, r.passage.versionAbbreviation, r.passage.attribution)
            )
            is PassageResult.Unavailable -> ScriptureFetch.Unavailable(parsed.display(), r.message)
        }
    }
}
