package com.craftflowtechnologies.meetingmind.ai.modelmanagement

import com.craftflowtechnologies.meetingmind.ai.asr.SupportedLanguages
import com.craftflowtechnologies.meetingmind.core.model.AiModelInfo
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier

/**
 * Decides which speech (ASR) model a recording is transcribed with, and which one to suggest
 * downloading. The speech counterpart of [LlmModelResolver].
 *
 * The saved selection alone is not enough: a person can pick Whisper, never download Parakeet, and
 * still be told to "download the offline speech model" because the default selection names a model
 * that isn't there. So every place that asks "can this phone transcribe?" and every place that
 * starts transcription asks this object, and they all get the same answer:
 *
 * 1. the selected model, when it is installed and handles the recording's language;
 * 2. otherwise the best installed model for that language (and this phone's memory);
 * 3. otherwise null: nothing installed can do it, which the pipeline reports as MODEL_REQUIRED.
 *
 * Pure: no Context, no disk. Callers pass in what is installed.
 */
object AsrModelResolver {

    /** How well a model handles a language. */
    enum class Fit { NONE, WORKS, BEST }

    /** Plain words for one speech model on the AI engine screen. */
    data class Guide(
        /** The languages it is for, in a person's words. */
        val languages: String,
        /** Speed against accuracy, one short phrase. */
        val tradeoff: String
    )

    private enum class Family { PARAKEET, WHISPER, OMNILINGUAL }

    /** English plus the 24 European languages Parakeet TDT v3 was trained on. */
    private val PARAKEET_LANGUAGES = setOf(
        "en", "es", "fr", "pt", "de", "it", "nl", "pl", "ru", "uk", "bg", "hr", "cs", "da",
        "et", "fi", "el", "hu", "lv", "lt", "mt", "ro", "sk", "sl", "sv"
    )

    /** Languages Whisper handles well that Parakeet does not. */
    private val WHISPER_BEST_LANGUAGES = SupportedLanguages.WHISPER_PREFERRED_LANGUAGES.toSet() + SupportedLanguages.TURKISH

    /** The Southern African languages Whisper was trained on at all (weakly). */
    private val WHISPER_AFRICAN_LANGUAGES = setOf(SupportedLanguages.AFRIKAANS, SupportedLanguages.SWAHILI, SupportedLanguages.SHONA)

    /** Phone locales use two-letter codes; the app's language list uses three for these languages. */
    private val TWO_TO_APP_CODE = mapOf(
        "af" to SupportedLanguages.AFRIKAANS,
        "sw" to SupportedLanguages.SWAHILI,
        "sn" to SupportedLanguages.SHONA,
        "zu" to SupportedLanguages.ZULU,
        "xh" to SupportedLanguages.XHOSA,
        "st" to SupportedLanguages.SESOTHO,
        "tn" to SupportedLanguages.SETSWANA,
        "ny" to SupportedLanguages.NYANJA,
        "nd" to SupportedLanguages.NORTH_NDEBELE
    )

    /** Every speech model in the catalog. */
    val speechModels: List<AiModelInfo>
        get() = ModelCatalog.entries.filter { ModelCapability.TRANSCRIPTION in it.capability }

    fun isSpeechModel(modelId: String?): Boolean = modelId != null && speechModels.any { it.id == modelId }

    /** The installed speech models, by id. */
    fun installedIds(modelStorage: ModelStorage): Set<String> =
        speechModels.filter { modelStorage.isInstalled(it.id) }.map { it.id }.toSet()

    /** "en-US", "EN", " sn " → the code the app's language list uses. Blank stays blank. */
    fun normalizeLanguage(language: String?): String {
        val base = language.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')
        return TWO_TO_APP_CODE[base] ?: base
    }

    private fun familyOf(modelId: String): Family? = when (modelId) {
        ModelCatalog.parakeetTdtV3Int8.id -> Family.PARAKEET
        ModelCatalog.whisperTurboInt8.id, ModelCatalog.whisperLargeV3Int8.id -> Family.WHISPER
        ModelCatalog.omnilingualAsr300mInt8.id, ModelCatalog.omnilingualAsr1bInt8.id -> Family.OMNILINGUAL
        else -> null
    }

    /**
     * How well [modelId] handles [language]. A blank, "auto" or unknown language fits every
     * speech model: the app can't tell, so it doesn't refuse.
     */
    fun fit(modelId: String, language: String?): Fit {
        val family = familyOf(modelId) ?: return Fit.NONE
        val code = normalizeLanguage(language)
        return when {
            SupportedLanguages.isAfricanLanguage(code) -> when (family) {
                Family.OMNILINGUAL -> Fit.BEST
                Family.WHISPER -> if (code in WHISPER_AFRICAN_LANGUAGES) Fit.WORKS else Fit.NONE
                Family.PARAKEET -> Fit.NONE
            }
            code in WHISPER_BEST_LANGUAGES -> when (family) {
                Family.WHISPER -> Fit.BEST
                Family.OMNILINGUAL -> Fit.WORKS
                Family.PARAKEET -> Fit.NONE
            }
            code in PARAKEET_LANGUAGES -> when (family) {
                Family.PARAKEET -> Fit.BEST
                Family.WHISPER, Family.OMNILINGUAL -> Fit.WORKS
            }
            else -> Fit.WORKS
        }
    }

    private fun canRun(model: AiModelInfo, ramMb: Int?) = ramMb == null || ramMb >= model.minimumRamMb
    private fun runsWell(model: AiModelInfo, ramMb: Int?) = ramMb == null || ramMb >= model.recommendedRamMb

    /**
     * The speech model to transcribe with, or null when nothing installed can handle [language].
     * [ramMb] only orders the installed alternatives; it never rules out the person's own choice.
     */
    fun resolve(installedIds: Set<String>, selectedId: String?, language: String?, ramMb: Int? = null): AiModelInfo? {
        val installed = speechModels.filter { it.id in installedIds }
        installed.firstOrNull { it.id == selectedId && fit(it.id, language) != Fit.NONE }?.let { return it }
        return bestInstalled(installed, language, ramMb)
    }

    fun resolve(modelStorage: ModelStorage, selectedId: String?, language: String?, ramMb: Int? = null): AiModelInfo? =
        resolve(installedIds(modelStorage), selectedId, language, ramMb)

    /** Among installed models: runs on this phone, then the language fit, then runs well, then the stronger one. */
    private fun bestInstalled(installed: List<AiModelInfo>, language: String?, ramMb: Int?): AiModelInfo? =
        installed
            .filter { fit(it.id, language) != Fit.NONE }
            .sortedWith(
                compareByDescending<AiModelInfo> { canRun(it, ramMb) }
                    .thenByDescending { fit(it.id, language).ordinal }
                    .thenByDescending { runsWell(it, ramMb) }
                    .thenByDescending { it.tier.ordinal }
            )
            .firstOrNull()

    /**
     * The speech model to suggest downloading on a phone with [ramMb] of memory, for [language].
     * Prefers one the phone can run, then the best language fit, then one the phone runs well,
     * then the balanced tier over the heaviest, then the smaller download. No language known
     * means English, the app's default.
     */
    fun recommended(ramMb: Int?, language: String?): AiModelInfo {
        val lang = normalizeLanguage(language).ifBlank { SupportedLanguages.ENGLISH }
        return speechModels
            .sortedWith(
                compareByDescending<AiModelInfo> { canRun(it, ramMb) }
                    .thenByDescending { fit(it.id, lang).ordinal }
                    .thenByDescending { runsWell(it, ramMb) }
                    .thenByDescending { it.tier == ModelTier.RECOMMENDED }
                    .thenBy { it.sizeBytes }
            )
            .firstOrNull()
            ?: ModelCatalog.parakeetTdtV3Int8
    }

    /**
     * The id to save as the selection so screens and processing agree, or null when the saved one
     * is fine. Only replaces a selection whose model is not installed: a person who chose an
     * installed model keeps it, even if one recording in another language uses something else.
     */
    fun selectionToSave(installedIds: Set<String>, selectedId: String?, language: String?, ramMb: Int? = null): String? {
        if (selectedId != null && selectedId in installedIds && isSpeechModel(selectedId)) return null
        val resolved = resolve(installedIds, selectedId, language, ramMb)
            ?: bestInstalled(speechModels.filter { it.id in installedIds }, null, ramMb)
        return resolved?.id?.takeIf { it != selectedId }
    }

    /** The engine name recorded on a transcript made with [modelId]. */
    fun engineName(modelId: String): String = when (modelId) {
        ModelCatalog.parakeetTdtV3Int8.id -> "parakeet-tdt-0.6b-v3"
        ModelCatalog.omnilingualAsr300mInt8.id -> "omnilingual-asr-300m-ctc"
        ModelCatalog.omnilingualAsr1bInt8.id -> "omnilingual-asr-1b-ctc"
        ModelCatalog.whisperLargeV3Int8.id -> "whisper-large-v3-int8"
        ModelCatalog.whisperTurboInt8.id -> "whisper-turbo-int8"
        else -> modelId
    }

    /** Short, plain words for a speech model, or null for anything that isn't one. */
    fun guide(modelId: String): Guide? = when (modelId) {
        ModelCatalog.parakeetTdtV3Int8.id -> Guide(
            languages = "English and 24 European languages, such as French, Spanish, German and Portuguese.",
            tradeoff = "Fast and accurate"
        )
        ModelCatalog.omnilingualAsr300mInt8.id -> Guide(
            languages = "African languages such as Shona, Ndebele, Bemba, Nyanja, Zulu and Swahili, and many more.",
            tradeoff = "Light and quick"
        )
        ModelCatalog.omnilingualAsr1bInt8.id -> Guide(
            languages = "The same African languages, heard more accurately.",
            tradeoff = "Most accurate, slower, needs a high-memory phone"
        )
        ModelCatalog.whisperTurboInt8.id -> Guide(
            languages = "Most world languages, including Japanese, Korean, Chinese, Hindi and Arabic.",
            tradeoff = "Quick, good accuracy"
        )
        ModelCatalog.whisperLargeV3Int8.id -> Guide(
            languages = "Most world languages, including Japanese, Korean, Chinese, Hindi and Arabic.",
            tradeoff = "Most accurate, slow, needs a high-memory phone"
        )
        else -> null
    }

    fun ramMbOf(totalRamGb: Float): Int = (totalRamGb * 1024f).toInt()
}
