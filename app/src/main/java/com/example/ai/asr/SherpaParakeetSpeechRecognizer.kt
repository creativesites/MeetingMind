package com.example.ai.asr

import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import com.example.ai.modelmanagement.SherpaEngineManager
import com.example.ai.vad.SpeechInterval
import com.example.core.audio.AudioFormatConverter
import com.example.core.model.TranscriptSegment
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File
import java.util.UUID

/**
 * Real on-device ASR: NVIDIA Parakeet TDT 0.6B v3 (INT8), run through sherpa-onnx's
 * `OfflineRecognizer` configured as a NeMo transducer model. See docs/AI_ARCHITECTURE.md for
 * the exact sherpa-onnx version, model source, and configuration this was verified against.
 *
 * Each VAD-detected speech interval becomes one `OfflineStream` — a cheap, per-segment
 * object — decoded against the single, process-wide [SherpaEngineManager]-held recognizer
 * instance (loading the ~650MB encoder once, not once per segment).
 */
class SherpaParakeetSpeechRecognizer(
    private val modelStorage: ModelStorage,
    private val modelId: String = ModelCatalog.parakeetTdtV3Int8.id
) : SpeechRecognizer {

    override suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        speechIntervals: List<SpeechInterval>,
        options: TranscriptionOptions,
        onProgress: (progress: Float, statusText: String) -> Unit
    ): AiResult<List<TranscriptSegment>> {
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

            val effectiveIntervals = speechIntervals.ifEmpty {
                listOf(SpeechInterval(startMs = 0L, endMs = totalDurationMs, confidence = null))
            }

            val segments = mutableListOf<TranscriptSegment>()
            val sampleRate = AudioFormatConverter.TARGET_SAMPLE_RATE
            for ((index, interval) in effectiveIntervals.withIndex()) {
                onProgress(
                    index.toFloat() / effectiveIntervals.size,
                    "Transcribing speech segment ${index + 1}/${effectiveIntervals.size}..."
                )

                val startSample = (interval.startMs * sampleRate / 1000L).toInt().coerceIn(0, decoded.samples.size)
                val endSample = (interval.endMs * sampleRate / 1000L).toInt().coerceIn(startSample, decoded.samples.size)
                if (endSample <= startSample) continue

                val segmentSamples = decoded.samples.copyOfRange(startSample, endSample)
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(segmentSamples, sampleRate)
                    recognizer.decode(stream)
                    val result = recognizer.getResult(stream)
                    val text = result.text.trim()
                    if (text.isNotEmpty()) {
                        segments.add(
                            TranscriptSegment(
                                id = UUID.randomUUID().toString(),
                                meetingId = meetingId,
                                speakerId = null,
                                speakerName = null,
                                startMs = interval.startMs,
                                endMs = interval.endMs,
                                text = text,
                                confidence = null
                            )
                        )
                    }
                } finally {
                    stream.release()
                }
            }

            onProgress(1.0f, "Transcription complete (${segments.size} segments)")
            AiResult.Success(segments)
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Speech recognition failed.", e)
        }
    }

    private companion object {
        // sherpa-onnx's own NeMo transducer example uses the library default of 80 mel bins;
        // no --num-mel-bins override is documented for Parakeet TDT models.
        const val FEATURE_DIM = 80
        const val NUM_THREADS = 2
    }
}
