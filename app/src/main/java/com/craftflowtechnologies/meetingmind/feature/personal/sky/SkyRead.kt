package com.craftflowtechnologies.meetingmind.feature.personal.sky

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.core.billing.FeatureGate
import com.craftflowtechnologies.meetingmind.core.billing.TieringMode
import com.craftflowtechnologies.meetingmind.core.billing.TieringModeStore
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where Zuri's read is written. Always this phone: reflections are private notes and never leave
 * the device, so there is no cloud route. [NONE] means no on-device model is installed yet.
 */
enum class SkyRoute { ON_DEVICE, NONE }

/** What Zuri's read came to. */
sealed interface SkyReadOutcome {
    data class Written(val text: String) : SkyReadOutcome

    /** No on-device model is installed: the card offers Setup. */
    data object NoModel : SkyReadOutcome

    /** The model gave nothing usable in time: one honest line. */
    data class Failed(val line: String) : SkyReadOutcome
}

/** Writes Zuri's read across a constellation's reflections. An interface so the sky can be tested without a model. */
interface SkyReader {
    /** Whether [read] can run here, so the card can say so before anything is written. */
    suspend fun route(): SkyRoute

    /** [entries] are already selected and cut by [SkyReadInput]. */
    suspend fun read(name: String, entries: List<String>): SkyReadOutcome
}

/**
 * The only model source for Zuri's read: the on-device model. It has no cloud method at all, so the
 * read cannot send a reflection anywhere else. Returns null when no on-device model is installed.
 */
fun interface LocalSkyModel {
    suspend fun resolve(): LanguageModel?

    /** Frees the on-device model once a read is done, as the guided sessions do. */
    suspend fun release() {}
}

/** The app's on-device summarisation model, resolved the same way the guided sessions resolve it. */
class DeviceSkyModel(private val context: Context) : LocalSkyModel {
    override suspend fun resolve(): LanguageModel? {
        val factory = com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory(
            context = context,
            modelStorage = com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage(context),
            geminiTransport = com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi.transport(context)
        )
        return runCatching { factory.resolveLocal(ModelCapability.SUMMARIZATION, ModelTier.RECOMMENDED) }
            .getOrNull()?.languageModel
    }

    override suspend fun release() {
        runCatching { com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager.release() }
    }
}

/** What a paid read may send: at most [MAX_ENTRIES] reflections, each cut to [MAX_CHARS]. */
object SkyReadInput {
    const val MAX_ENTRIES = 12
    const val MAX_CHARS = 1200

    /** The newest reflections first, up to [MAX_ENTRIES], each cut to [MAX_CHARS] characters. */
    fun select(entries: List<ReflectionEntry>): List<String> = entries
        .sortedByDescending { it.createdAt }
        .take(MAX_ENTRIES)
        .map { it.text.trim().take(MAX_CHARS) }
}

/** The prompt and the reply check. The model writes a short reflection; it never advises. */
object SkyReadPrompt {
    fun prompt(name: String, entries: List<String>): String = buildString {
        append("You are Zuri, a gentle companion. Below are a person's private reflections, written in their own words. ")
        append("They all come back to the theme \"").append(name).append("\".\n")
        append("Write a short reflection of two to four sentences, in plain English. ")
        append("Notice what the entries share, using their own words. Do not give advice, diagnosis or judgement. ")
        append("Do not invent details. Do not use headings, lists or quotation marks. Speak to the person as \"you\".\n\n")
        entries.forEachIndexed { i, text -> append("Entry ${i + 1}: ").append(text).append('\n') }
    }

    /** The reply as text, or null when the model gave nothing usable. */
    fun parse(reply: String): String? = reply.trim().removeSurrounding("\"").trim().takeIf { it.isNotEmpty() }
}

/**
 * Who may ask for Zuri's read. The rule is the one Deep Synthesis uses: a paid entitlement that
 * includes it, or the closed-testing build mode.
 */
fun interface SkyAccess {
    fun allowsZuri(): Boolean

    companion object {
        fun allows(canDeepSynthesis: Boolean, mode: TieringMode): Boolean =
            canDeepSynthesis || mode == TieringMode.CLOSED_TESTING

        fun gate(context: Context): SkyAccess = SkyAccess {
            allows(FeatureGate.can(Capability.DEEP_SYNTHESIS), TieringModeStore.current(context))
        }
    }
}

/**
 * Zuri's read on the phone's on-device model only. Reflections are private, so nothing is sent to
 * the cloud. With no on-device model installed, the read says so and offers Setup.
 */
class ModelSkyReader(private val local: LocalSkyModel) : SkyReader {

    override suspend fun route(): SkyRoute = if (local.resolve() != null) SkyRoute.ON_DEVICE else SkyRoute.NONE

    override suspend fun read(name: String, entries: List<String>): SkyReadOutcome {
        val model = local.resolve() ?: return SkyReadOutcome.NoModel
        val raw = withTimeoutOrNull(DEVICE_TIMEOUT_MS) {
            runCatching { model.generate(SkyReadPrompt.prompt(name, entries), maxOutputTokens = 300) }.getOrNull()
        }
        local.release()
        val text = (raw as? AiResult.Success)?.value?.let { SkyReadPrompt.parse(it) }
        return text?.let { SkyReadOutcome.Written(it) } ?: SkyReadOutcome.Failed(FAILED)
    }

    companion object {
        const val DEVICE_TIMEOUT_MS = 90_000L
        const val FAILED = "Zuri couldn't write the read just now. Your reflections are unchanged."
    }
}
