package com.example.ai.asr

import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import com.example.ai.modelmanagement.SherpaEngineManager
import com.example.ai.transcript.AsrContextBuilder
import com.example.ai.transcript.AsrWindow
import com.example.ai.transcript.AsrWindowReconciler
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.SpeechRegion
import com.example.ai.transcript.TranscriptSource
import com.example.core.audio.AudioFormatConverter
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Real on-device ASR: NVIDIA Parakeet TDT 0.6B v3 (INT8), run through sherpa-onnx's
 * `OfflineRecognizer` configured as a NeMo transducer model. See docs/AI_ARCHITECTURE.md for
 * the exact sherpa-onnx version, model source, and configuration this was verified against.
 *
 * ### What changed, and why
 *
 * This used to decode **one VAD region per stream**, so Parakeet never heard across a pause. Now
 * [AsrContextBuilder] groups regions into overlapping decode windows of tens of seconds, each
 * decoded as one `OfflineStream` against the single process-wide recognizer, and
 * [AsrWindowReconciler] removes the duplicate text the overlap produces. The model gets real
 * conversational context; the caller gets one chronological word stream with no segment structure
 * baked into it.
 */
class SherpaParakeetSpeechRecognizer(
    private val modelStorage: ModelStorage,
    private val modelId: String = ModelCatalog.parakeetTdtV3Int8.id
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
        val encoder = File(modelDir, "encoder.int8.onnx")
        val decoder = File(modelDir, "decoder.int8.onnx")
        val joiner = File(modelDir, "joiner.int8.onnx")
        val tokens = File(modelDir, "tokens.txt")
        if (!encoder.exists() || !decoder.exists() || !joiner.exists() || !tokens.exists()) {
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
                    transducer = OfflineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath
                    ),
                    tokens = tokens.absolutePath,
                    modelType = "nemo_transducer",
                    numThreads = NUM_THREADS,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
            val recognizer = SherpaEngineManager.getOrCreateRecognizer(modelId, config)

            val sampleRate = AudioFormatConverter.TARGET_SAMPLE_RATE
            // The decoded sample count is authoritative: a container's reported duration can be
            // wrong, and a window past the end of the buffer would decode silence.
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

            val perWindowWords = mutableListOf<List<CanonicalWord>>()
            for ((index, window) in windows.withIndex()) {
                // Cancellation must reach the inner decode loop, not just the coroutine wrapping
                // it: a 40-minute recording is dozens of windows and the user may leave at any
                // point. Checked before each decode so at most one window's work is wasted.
                coroutineContext.ensureActive()
                onProgress(
                    index.toFloat() / windows.size,
                    "Transcribing ${index + 1} of ${windows.size}..."
                )
                perWindowWords += decodeWindow(recognizer, decoded.samples, sampleRate, window)
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
     * Groups sherpa-onnx's raw sub-word tokens into real words with real timestamps. NeMo's
     * default (SentencePiece) tokenizer marks the start of a new word with a leading "▁" —
     * standard NeMo/Conformer/Parakeet convention — so a token carrying that marker starts a new
     * word and one without continues the word in progress. If a particular tokens.txt turns out
     * not to use that convention, every token folds into a single word spanning the whole window
     * rather than mis-segmenting — a real, honest degradation, never a guess dressed up as a
     * boundary.
     *
     * `timestamps` are seconds from the start of this window's own audio, so they are offset by
     * [windowStartMs] to become absolute recording time. A word's end is the next word's start
     * (words abut in a transducer's output); the last word ends at the window end.
     *
     * Confidence is left null throughout: sherpa-onnx's result type has no score field at all
     * (verified against the v1.13.6 Kotlin API), so nothing here claims to know how sure the model
     * was about any word — only when it was said.
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
        for (i in tokens.indices) {
            val raw = tokens[i]
            val tokenStartMs = windowStartMs + (timestamps.getOrElse(i) { 0f } * 1000).toLong()
            val isWordStart = raw.startsWith(WORD_START_MARKER) || raw.startsWith(" ")
            val cleaned = raw.removePrefix(WORD_START_MARKER).removePrefix(" ")
            if (isWordStart || pending.isEmpty()) {
                pending += Pending(StringBuilder(cleaned), tokenStartMs)
            } else {
                pending.last().text.append(cleaned)
            }
        }

        return pending.mapIndexedNotNull { index, word ->
            val text = word.text.toString().trim()
            if (text.isEmpty()) return@mapIndexedNotNull null
            val endMs = pending.getOrNull(index + 1)?.startMs ?: windowEndMs
            CanonicalWord(
                // Replaced with a transcript-wide id by AsrWindowReconciler; this local id only
                // has to be unique within the window.
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
        // sherpa-onnx's own NeMo transducer example uses the library default of 80 mel bins;
        // no --num-mel-bins override is documented for Parakeet TDT models.
        const val FEATURE_DIM = 80
        const val NUM_THREADS = 2
        const val WORD_START_MARKER = "▁" // SentencePiece "▁"
    }
}
