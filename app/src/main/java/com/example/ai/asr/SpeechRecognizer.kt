package com.example.ai.asr

import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.transcript.AsrWindowConfig
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.SpeechRegion
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
    val windowConfig: AsrWindowConfig = AsrWindowConfig()
)

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
 * diarization in hand — see [com.example.ai.transcript.CanonicalTranscriptAssembler].
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
