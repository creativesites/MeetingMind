package com.craftflowtechnologies.meetingmind.ai.asr

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.transcript.AsrWindowConfig
import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord
import com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion
import java.io.File

data class TranscriptionOptions(
    val modelId: String = ModelCatalog.parakeetTdtV3Int8.id,
    val language: String = "en",
    /**
     * Terms the recording is likely to contain — participant names, the project or company name,
     * the user's learned vocabulary. Engines that can bias recognition toward known terminology
     * use it; engines that cannot ignore it. Never used to rewrite what was actually recognized.
     */
    val vocabularyHints: List<String> = emptyList(),
    val temperature: Float = 0.0f,
    val includeTimestamps: Boolean = true,
    /** How speech regions are grouped into decode windows — see [AsrWindowConfig]. */
    val windowConfig: AsrWindowConfig = AsrWindowConfig(),
    /** Called with each decode window's words as it finishes (absolute times), so the work can be saved and handed to another engine. */
    val onWindowDone: ((startMs: Long, endMs: Long, words: List<com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord>) -> Unit)? = null
)

/** Supported local and regional languages with model resolution mapping. */
object SupportedLanguages {
    const val ENGLISH = "en"
    const val SPANISH = "es"
    const val FRENCH = "fr"
    const val PORTUGUESE = "pt"
    const val GERMAN = "de"
    const val ITALIAN = "it"
    const val DUTCH = "nl"
    const val JAPANESE = "ja"
    const val KOREAN = "ko"
    const val MANDARIN = "zh"
    const val HINDI = "hi"
    const val ARABIC = "ar"

    const val SHONA = "sna"
    const val NORTH_NDEBELE = "nde"
    const val BEMBA = "bem"
    const val NYANJA = "nya"
    const val TONGA = "toi"
    const val LOZI = "loz"
    const val LUVALE = "lue"
    const val LUNDA = "lun"
    const val KAONDE = "kqn"
    const val ZULU = "zul"
    const val XHOSA = "xho"
    const val AFRIKAANS = "afr"
    const val SESOTHO = "sot"
    const val SETSWANA = "tsn"
    const val SWAHILI = "swa"

    const val INDONESIAN = "id"
    const val VIETNAMESE = "vi"
    const val TURKISH = "tr"
    const val RUSSIAN = "ru"
    const val POLISH = "pl"

    val SOUTHERN_AFRICAN_LANGUAGES = listOf(
        SHONA, NORTH_NDEBELE, BEMBA, NYANJA, TONGA, LOZI,
        LUVALE, LUNDA, KAONDE, ZULU, XHOSA, AFRIKAANS, SESOTHO, SETSWANA, SWAHILI
    )

    val TIER_A_INTERNATIONAL_LANGUAGES = listOf(
        ENGLISH, SPANISH, FRENCH, PORTUGUESE, GERMAN, ITALIAN, DUTCH,
        JAPANESE, KOREAN, MANDARIN, HINDI, ARABIC
    )

    val WHISPER_PREFERRED_LANGUAGES = listOf(
        JAPANESE, KOREAN, MANDARIN, HINDI, ARABIC, INDONESIAN, VIETNAMESE
    )

    fun isAfricanLanguage(languageCode: String): Boolean =
        SOUTHERN_AFRICAN_LANGUAGES.contains(languageCode.lowercase().trim())

    fun isWhisperPreferredLanguage(languageCode: String): Boolean =
        WHISPER_PREFERRED_LANGUAGES.contains(languageCode.lowercase().trim())

    fun defaultModelIdForLanguage(languageCode: String): String {
        val code = languageCode.lowercase().trim()
        return when {
            isAfricanLanguage(code) -> ModelCatalog.omnilingualAsr300mInt8.id
            isWhisperPreferredLanguage(code) -> ModelCatalog.whisperTurboInt8.id
            else -> ModelCatalog.parakeetTdtV3Int8.id
        }
    }

    fun displayName(languageCode: String): String = when (languageCode.lowercase().trim()) {
        SHONA -> "Shona (chiShona)"
        NORTH_NDEBELE -> "Ndebele (isiNdebele)"
        BEMBA -> "Bemba (iciBemba)"
        NYANJA -> "Nyanja / Chichewa"
        TONGA -> "Tonga (chiTonga)"
        LOZI -> "Lozi (siLozi)"
        LUVALE -> "Luvale"
        LUNDA -> "Lunda"
        KAONDE -> "Kaonde"
        ZULU -> "isiZulu"
        XHOSA -> "isiXhosa"
        AFRIKAANS -> "Afrikaans"
        SESOTHO -> "Sesotho"
        SETSWANA -> "Setswana"
        SWAHILI -> "Kiswahili"
        ENGLISH -> "English"
        SPANISH -> "Spanish (Español)"
        FRENCH -> "French (Français)"
        PORTUGUESE -> "Portuguese (Português)"
        GERMAN -> "German (Deutsch)"
        ITALIAN -> "Italian (Italiano)"
        DUTCH -> "Dutch (Nederlands)"
        JAPANESE -> "Japanese (日本語)"
        KOREAN -> "Korean (한국어)"
        MANDARIN -> "Mandarin (中文)"
        HINDI -> "Hindi (हिन्दी)"
        ARABIC -> "Arabic (العربية)"
        INDONESIAN -> "Indonesian (Bahasa Indonesia)"
        VIETNAMESE -> "Vietnamese (Tiếng Việt)"
        TURKISH -> "Turkish (Türkçe)"
        RUSSIAN -> "Russian (Русский)"
        POLISH -> "Polish (Polski)"
        else -> languageCode
    }
}

/**
 * Converts recorded audio into a chronological stream of timed words.
 *
 * ### Why words and not segments
 *
 * This interface used to return `List<TranscriptSegment>`, one segment per VAD region. That made a
 * VAD boundary simultaneously an ASR context boundary and a transcript boundary: a sentence split
 * by a 700 ms breath was decoded as two unrelated utterances, costing real recognition accuracy,
 * and then rendered as two transcript fragments. Both defects had the same single cause.
 *
 * Perception's honest output is *what was said and when*. Where sentences, speaker turns and
 * paragraphs fall is decided later, by layers that have the words, the punctuation and the
 * diarization in hand — see [com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalTranscriptAssembler].
 *
 * No implementation may return fabricated or placeholder words. A real implementation must run
 * genuine speech recognition; until one is available, the app uses [UnavailableSpeechRecognizer],
 * which honestly reports that no ASR model is installed. See docs/AI_ARCHITECTURE.md.
 */
interface SpeechRecognizer {
    /**
     * @param speechRegions Where VAD found speech. Empty means VAD was unavailable or found
     *   nothing; a real implementation must then decode the recording end to end rather than
     *   returning nothing, so a missing VAD model degrades quality without silencing the app.
     * @return Words with timestamps absolute to the recording, in chronological order, with
     *   overlap between decode windows already reconciled.
     */
    suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        speechRegions: List<SpeechRegion>,
        options: TranscriptionOptions,
        onProgress: (progress: Float, statusText: String) -> Unit
    ): AiResult<List<CanonicalWord>>
}

/**
 * Default ASR implementation when no real speech recognition model is available.
 * Deliberately does not call any cloud API and does not invent transcript text.
 */
class UnavailableSpeechRecognizer : SpeechRecognizer {
    override suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        speechRegions: List<SpeechRegion>,
        options: TranscriptionOptions,
        onProgress: (progress: Float, statusText: String) -> Unit
    ): AiResult<List<CanonicalWord>> = AiResult.ModelUnavailable(
        modelId = options.modelId,
        message = "No local speech recognition model is installed on this device."
    )
}
