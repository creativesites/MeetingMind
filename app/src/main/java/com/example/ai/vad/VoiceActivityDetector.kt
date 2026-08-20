package com.example.ai.vad

import com.example.ai.common.AiResult
import java.io.File

data class SpeechInterval(
    val startMs: Long,
    val endMs: Long,
    // Null when the VAD engine only gives a binary speech/non-speech boundary (e.g. Silero VAD
    // via sherpa-onnx) rather than a real per-segment confidence score.
    val confidence: Float? = null
)

/**
 * Detects which portions of a recording contain speech.
 *
 * No implementation of this interface may fabricate speech intervals. A real implementation
 * must actually decode and analyze the audio samples; until one is integrated, the app uses
 * [UnavailableVoiceActivityDetector], which honestly reports that no VAD model is installed.
 */
interface VoiceActivityDetector {
    suspend fun detectSpeechIntervals(audioFile: File, totalDurationMs: Long): AiResult<List<SpeechInterval>>
}

/**
 * Default VAD implementation until a real on-device VAD model (e.g. Silero VAD) is integrated.
 * Deliberately does not analyze audio or invent speech activity — see docs/AI_ARCHITECTURE.md.
 */
class UnavailableVoiceActivityDetector : VoiceActivityDetector {
    override suspend fun detectSpeechIntervals(
        audioFile: File,
        totalDurationMs: Long
    ): AiResult<List<SpeechInterval>> = AiResult.ModelUnavailable(
        modelId = "vad",
        message = "No local voice activity detection model is installed on this device."
    )
}
