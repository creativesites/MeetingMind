package com.craftflowtechnologies.meetingmind.ai.asr

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.SherpaEngineManager
import com.craftflowtechnologies.meetingmind.ai.transcript.AsrContextBuilder
import com.craftflowtechnologies.meetingmind.ai.transcript.AsrWindow
import com.craftflowtechnologies.meetingmind.ai.transcript.AsrWindowReconciler
import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord
import com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion
import com.craftflowtechnologies.meetingmind.ai.transcript.TranscriptSource
import com.craftflowtechnologies.meetingmind.core.audio.AudioFormatConverter
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * On-device multilingual ASR: Meta Omnilingual ASR CTC 300M (INT8), run through sherpa-onnx's
 * [com.k2fsa.sherpa.onnx.OfflineRecognizer] configured as an Omnilingual CTC model.
 *
 * Supports 1,600+ languages including Zimbabwean and Zambian priority languages: Shona (sna),
 * North Ndebele (nde), Bemba (bem), Nyanja / Chichewa (nya), Tonga (toi), Lozi (loz),
 * Luvale (lue), Lunda (lun), Kaonde (kqn), as well as South African languages (isiZulu, isiXhosa,
 * Afrikaans, Sesotho, Setswana) and international languages (English, French, Portuguese, Swahili).
 *
 * Licensed under Apache-2.0 (permissive, commercial use and redistribution permitted).
 * See docs/SOUTHERN_AFRICAN_ASR_RESEARCH_AND_PLAN.md for evaluation and architectural details.
 */
class SherpaOmnilingualSpeechRecognizer(
    private val modelStorage: ModelStorage,
    private val modelId: String = ModelCatalog.omnilingualAsr300mInt8.id,
    /** Where finished windows are remembered so an interrupted run resumes. Null disables it. */
    private val checkpoints: AsrCheckpointStore? = null
) : SpeechRecognizer {

    override suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        speechRegions: List<SpeechRegion>,
        options: TranscriptionOptions,
        onProgress: (progress: Float, statusText: String) -> Unit
    ): AiResult<List<CanonicalWord>> {
        if (!modelStorage.isInstalled(modelId)) {
            return AiResult.ModelUnavailable(modelId, "No local speech recognition model is installed on this device.")
        }

        val modelDir = modelStorage.getModelDirectory(modelId)
        val model = File(modelDir, "model.int8.onnx")
        val tokens = File(modelDir, "tokens.txt")
        if (!model.exists() || !tokens.exists()) {
            return AiResult.ModelUnavailable(modelId, "Speech recognition model files are incomplete on this device.")
        }

        return try {
            onProgress(0.05f, "Preparing audio for transcription...")
            val decoded = AudioFormatConverter.decodeToMono16k(audioFile)
            if (decoded.samples.isEmpty()) {
                onProgress(1.0f, "No audio to transcribe")
                return AiResult.Success(emptyList())
            }

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = AudioFormatConverter.TARGET_SAMPLE_RATE, featureDim = FEATURE_DIM),
                modelConfig = OfflineModelConfig(
                    omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = model.absolutePath),
                    tokens = tokens.absolutePath,
                    modelType = "omnilingual",
                    numThreads = NUM_THREADS,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
            val recognizer = SherpaEngineManager.getOrCreateRecognizer(modelId, config)

            val sampleRate = AudioFormatConverter.TARGET_SAMPLE_RATE
            val decodedDurationMs = decoded.samples.size.toLong() * 1000L / sampleRate
            val windows = AsrContextBuilder.buildWindows(
                regions = speechRegions,
                totalDurationMs = minOf(totalDurationMs.takeIf { it > 0 } ?: decodedDurationMs, decodedDurationMs),
                config = options.windowConfig
            )
            if (windows.isEmpty()) {
                onProgress(1.0f, "No speech to transcribe")
                return AiResult.Success(emptyList())
            }

            val alreadyDone = checkpoints?.load(meetingId, modelId, windows).orEmpty()
            val perWindowWords = mutableListOf<List<CanonicalWord>>()
            for ((index, window) in windows.withIndex()) {
                coroutineContext.ensureActive()
                val done = alreadyDone[index]
                if (done != null) {
                    perWindowWords += done
                } else {
                    onProgress(
                        index.toFloat() / windows.size,
                        if (alreadyDone.isNotEmpty()) "Resuming — transcribing ${index + 1} of ${windows.size}..."
                        else "Transcribing ${index + 1} of ${windows.size}..."
                    )
                    val words = decodeWindow(recognizer, decoded.samples, sampleRate, window)
                    checkpoints?.append(meetingId, modelId, windows, index, words)
                    options.onWindowDone?.invoke(window.startMs, window.endMs, words)
                    perWindowWords += words
                }
            }

            val words = AsrWindowReconciler.reconcile(perWindowWords)
            onProgress(1.0f, "Transcription complete (${words.size} words)")
            AiResult.Success(words)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Speech recognition failed.", e)
        }
    }

    private fun decodeWindow(
        recognizer: com.k2fsa.sherpa.onnx.OfflineRecognizer,
        samples: FloatArray,
        sampleRate: Int,
        window: AsrWindow
    ): List<CanonicalWord> {
        val startSample = (window.startMs * sampleRate / 1000L).toInt().coerceIn(0, samples.size)
        val endSample = (window.endMs * sampleRate / 1000L).toInt().coerceIn(startSample, samples.size)
        if (endSample <= startSample) return emptyList()

        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples.copyOfRange(startSample, endSample), sampleRate)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            if (result.text.isBlank()) emptyList()
            else buildWords(result.tokens, result.timestamps, window.startMs, window.endMs)
        } finally {
            stream.release()
        }
    }

    /**
     * Groups CTC output tokens into words with timestamps.
     *
     * In Omnilingual CTC models, tokens represent characters or subwords. The SentencePiece
     * word-start character (' ' or ' ') indicates word boundaries. Special tokens such as
     * <s>, </s>, <pad>, and <unk> are ignored.
     */
    internal fun buildWords(
        tokens: Array<String>,
        timestamps: FloatArray,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<CanonicalWord> {
        if (tokens.isEmpty()) return emptyList()

        data class Pending(val text: StringBuilder, val startMs: Long)

        val pending = mutableListOf<Pending>()
        var startNextWord = true

        for (i in tokens.indices) {
            val raw = tokens[i]
            if (raw == "<pad>" || raw == "<s>" || raw == "</s>" || raw == "<unk>") continue
            val tokenStartMs = windowStartMs + (timestamps.getOrElse(i) { 0f } * 1000).toLong()

            val isSpaceToken = raw == WORD_START_MARKER || raw == " "
            val hasLeadingSpace = raw.startsWith(WORD_START_MARKER) || raw.startsWith(" ")
            val cleaned = raw.removePrefix(WORD_START_MARKER).removePrefix(" ")

            if (isSpaceToken) {
                startNextWord = true
                continue
            }

            if (cleaned.isEmpty()) continue

            if (startNextWord || hasLeadingSpace || pending.isEmpty()) {
                pending += Pending(StringBuilder(cleaned), tokenStartMs)
                startNextWord = false
            } else {
                pending.last().text.append(cleaned)
            }
        }

        return pending.mapIndexedNotNull { index, word ->
            val text = word.text.toString().trim()
            if (text.isEmpty()) return@mapIndexedNotNull null
            val endMs = pending.getOrNull(index + 1)?.startMs ?: windowEndMs
            CanonicalWord(
                id = "w${windowStartMs}_$index",
                text = text,
                startMs = word.startMs,
                endMs = maxOf(endMs, word.startMs),
                confidence = null,
                source = TranscriptSource.LOCAL_ASR
            )
        }
    }

    private companion object {
        const val FEATURE_DIM = 80
        const val NUM_THREADS = 2
        const val WORD_START_MARKER = " " // SentencePiece ' ' (U+2581)
    }
}
