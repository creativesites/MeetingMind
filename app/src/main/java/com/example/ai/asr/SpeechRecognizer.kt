package com.example.ai.asr

import com.example.ai.common.AiResult
import com.example.ai.vad.SpeechInterval
import com.example.core.model.TranscriptSegment
import java.io.File

data class TranscriptionOptions(
    val modelId: String = "whisper_tiny",
    val language: String = "en",
    val prompt: String = "",
    val temperature: Float = 0.0f,
    val includeTimestamps: Boolean = true
)

/**
 * Converts recorded audio into transcript segments.
 *
 * No implementation of this interface may return fabricated or placeholder text. A real
 * implementation must run genuine on-device speech recognition (e.g. a quantized Whisper
 * model via whisper.cpp or ONNX Runtime Mobile). Until one is integrated, the app uses
 * [UnavailableSpeechRecognizer], which honestly reports that no ASR model is installed
 * instead of inventing a transcript. See docs/AI_ARCHITECTURE.md.
 */
interface SpeechRecognizer {
    suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        speechIntervals: List<SpeechInterval>,
        options: TranscriptionOptions,
        onProgress: (progress: Float, statusText: String) -> Unit
    ): AiResult<List<TranscriptSegment>>
}

/**
 * Default ASR implementation until a real on-device speech recognition model is integrated.
 * Deliberately does not call any cloud API and does not invent transcript text.
 */
class UnavailableSpeechRecognizer : SpeechRecognizer {
    override suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        speechIntervals: List<SpeechInterval>,
        options: TranscriptionOptions,
        onProgress: (progress: Float, statusText: String) -> Unit
    ): AiResult<List<TranscriptSegment>> = AiResult.ModelUnavailable(
        modelId = options.modelId,
        message = "No local speech recognition model is installed on this device."
    )
}
