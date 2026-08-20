package com.example.ai.vad

import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import com.example.ai.modelmanagement.SherpaEngineManager
import com.example.core.audio.AudioFormatConverter
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * Real Silero VAD via sherpa-onnx's [com.k2fsa.sherpa.onnx.Vad]. Decodes the actual recording
 * to 16 kHz mono PCM (see [AudioFormatConverter]) and feeds real audio frames through the
 * model — no synthetic/time-based speech detection.
 */
class SileroVadDetector(
    private val modelStorage: ModelStorage,
    private val modelId: String = ModelCatalog.sileroVad.id
) : VoiceActivityDetector {

    override suspend fun detectSpeechIntervals(
        audioFile: File,
        totalDurationMs: Long
    ): AiResult<List<SpeechInterval>> {
        if (!modelStorage.isInstalled(modelId)) {
            return AiResult.ModelUnavailable(modelId, "No local voice activity detection model is installed on this device.")
        }

        val modelFile = File(modelStorage.getModelDirectory(modelId), VAD_MODEL_FILE_NAME)
        if (!modelFile.exists()) {
            return AiResult.ModelUnavailable(modelId, "VAD model file is missing from local storage.")
        }

        return try {
            val decoded = AudioFormatConverter.decodeToMono16k(audioFile)
            if (decoded.samples.isEmpty()) {
                return AiResult.Success(emptyList())
            }

            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = modelFile.absolutePath,
                    threshold = SPEECH_THRESHOLD,
                    minSilenceDuration = MIN_SILENCE_DURATION_SEC,
                    minSpeechDuration = MIN_SPEECH_DURATION_SEC,
                    windowSize = WINDOW_SIZE_SAMPLES,
                    maxSpeechDuration = MAX_SPEECH_DURATION_SEC
                ),
                sampleRate = AudioFormatConverter.TARGET_SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu"
            )
            val vad = SherpaEngineManager.getOrCreateVad(modelId, config)

            val intervals = mutableListOf<SpeechInterval>()
            var offset = 0
            val samples = decoded.samples
            while (offset + WINDOW_SIZE_SAMPLES <= samples.size) {
                vad.acceptWaveform(samples.copyOfRange(offset, offset + WINDOW_SIZE_SAMPLES))
                drainSegments(vad, intervals)
                offset += WINDOW_SIZE_SAMPLES
            }
            vad.flush()
            drainSegments(vad, intervals)

            AiResult.Success(intervals)
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Voice activity detection failed.", e)
        }
    }

    private fun drainSegments(vad: com.k2fsa.sherpa.onnx.Vad, out: MutableList<SpeechInterval>) {
        while (!vad.empty()) {
            out.add(toSpeechInterval(vad.front()))
            vad.pop()
        }
    }

    private fun toSpeechInterval(segment: SpeechSegment): SpeechInterval {
        val sampleRate = AudioFormatConverter.TARGET_SAMPLE_RATE
        val startMs = segment.start.toLong() * 1000L / sampleRate
        val endMs = (segment.start.toLong() + segment.samples.size.toLong()) * 1000L / sampleRate
        return SpeechInterval(startMs = startMs, endMs = endMs, confidence = null)
    }

    private companion object {
        const val VAD_MODEL_FILE_NAME = "silero_vad.onnx"
        const val SPEECH_THRESHOLD = 0.5f
        const val MIN_SILENCE_DURATION_SEC = 0.25f
        const val MIN_SPEECH_DURATION_SEC = 0.25f
        const val WINDOW_SIZE_SAMPLES = 512
        // Meeting speech turns run much longer than short voice-command style utterances;
        // the sherpa-onnx example default is 5s, which is far too aggressive for meetings.
        const val MAX_SPEECH_DURATION_SEC = 30.0f
    }
}
