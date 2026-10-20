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
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * On-device multilingual ASR using OpenAI Whisper models (Turbo, Large-v3) exported to ONNX
 * and run via sherpa-onnx's [com.k2fsa.sherpa.onnx.OfflineRecognizer] with [OfflineWhisperModelConfig].
 *
 * Provides high-accuracy transcription across 99+ languages with segment and word timestamps.
 * Fully offline, MIT licensed.
 */
class SherpaWhisperSpeechRecognizer(
    private val modelStorage: ModelStorage,
    private val modelId: String = ModelCatalog.whisperLargeV3Int8.id,
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
        val encoder = File(modelDir, "encoder.int8.onnx")
        val decoder = File(modelDir, "decoder.int8.onnx")
        val tokens = File(modelDir, "tokens.txt")
        if (!encoder.exists() || !decoder.exists() || !tokens.exists()) {
            return AiResult.ModelUnavailable(modelId, "Speech recognition model files are incomplete on this device.")
        }

        return try {
            onProgress(0.05f, "Preparing audio for transcription...")
            val decoded = AudioFormatConverter.decodeToMono16k(audioFile)
            if (decoded.samples.isEmpty()) {
                onProgress(1.0f, "No audio to transcribe")
                return AiResult.Success(emptyList())
            }

            val whisperLanguage = when (val lang = options.language.lowercase().trim()) {
                "", "auto" -> ""
                else -> lang
            }
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = AudioFormatConverter.TARGET_SAMPLE_RATE, featureDim = FEATURE_DIM),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        language = whisperLanguage,
                        task = "transcribe",
                        tailPaddings = -1
                    ),
                    tokens = tokens.absolutePath,
                    modelType = "whisper",
                    numThreads = NUM_THREADS,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search"
            )
            val cacheKey = if (whisperLanguage.isNotEmpty()) "$modelId:$whisperLanguage" else modelId
            val recognizer = SherpaEngineManager.getOrCreateRecognizer(cacheKey, config)

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
            else buildWords(result.text, result.tokens, result.timestamps, window.startMs, window.endMs)
        } finally {
            stream.release()
        }
    }

    /**
     * Groups Whisper tokens or transcribed text into words with timestamps.
     *
     * Whisper tokens use byte-level BPE with leading space indicators (' ', 'Ġ', or ' ').
     * If token-level decomposition is present, it constructs words and aligns their start times
     * to the earliest token's timestamp. If token timestamps are unavailable, it falls back to
     * linearly interpolating word boundaries across the window duration.
     */
    internal fun buildWords(
        text: String,
        tokens: Array<String>,
        timestamps: FloatArray,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<CanonicalWord> {
        if (text.isBlank()) return emptyList()

        data class Pending(val text: StringBuilder, val startMs: Long)
        val pending = mutableListOf<Pending>()

        if (tokens.isNotEmpty()) {
            for (i in tokens.indices) {
                val raw = tokens[i]
                if (raw.startsWith("<|") && raw.endsWith("|>")) continue // Special Whisper timestamp/control tokens

                val tokenStartMs = windowStartMs + (timestamps.getOrElse(i) { 0f } * 1000).toLong()
                val isWordStart = raw.startsWith(" ") || raw.startsWith("Ġ") || raw.startsWith(" ")
                val cleaned = raw.removePrefix(" ").removePrefix("Ġ").removePrefix(" ")

                if (isWordStart || pending.isEmpty()) {
                    pending += Pending(StringBuilder(cleaned), tokenStartMs)
                } else {
                    pending.last().text.append(cleaned)
                }
            }
        }

        val wordsFromTokens = pending.mapIndexedNotNull { index, word ->
            val wordText = word.text.toString().trim()
            if (wordText.isEmpty()) return@mapIndexedNotNull null
            val endMs = pending.getOrNull(index + 1)?.startMs ?: windowEndMs
            CanonicalWord(
                id = "w${windowStartMs}_$index",
                text = wordText,
                startMs = word.startMs,
                endMs = maxOf(endMs, word.startMs),
                confidence = null,
                source = TranscriptSource.LOCAL_ASR
            )
        }

        if (wordsFromTokens.isNotEmpty()) {
            return wordsFromTokens
        }

        // Fallback: split text by whitespace and interpolate across the window
        val rawWords = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawWords.isEmpty()) return emptyList()
        val durationMs = (windowEndMs - windowStartMs).coerceAtLeast(1L)
        val stepMs = durationMs / rawWords.size
        return rawWords.mapIndexed { idx, wordText ->
            val start = windowStartMs + idx * stepMs
            val end = if (idx == rawWords.lastIndex) windowEndMs else windowStartMs + (idx + 1) * stepMs
            CanonicalWord(
                id = "w${windowStartMs}_$idx",
                text = wordText,
                startMs = start,
                endMs = maxOf(end, start),
                confidence = null,
                source = TranscriptSource.LOCAL_ASR
            )
        }
    }

    private companion object {
        const val FEATURE_DIM = 80
        const val NUM_THREADS = 2
    }
}
