package com.example.ai.vad

import com.example.ai.common.AiResult
import com.example.ai.transcript.SpeechRegion
import java.io.File

/**
 * Historical name for [SpeechRegion], kept so the many existing call sites and tests that speak of
 * "speech intervals" keep compiling. It is the same type: VAD's output is an acoustic region, and
 * the canonical layer now owns that definition.
 */
typealias SpeechInterval = SpeechRegion

/**
 * Detects which portions of a recording contain speech.
 *
 * This answers exactly one question — **where is speech?** — and must never be read as answering
 * "where does a sentence or paragraph end". A region boundary is an acoustic fact about energy in
 * the signal; it carries no information about language. Grouping regions into decode windows is
 * [com.example.ai.transcript.AsrContextBuilder]'s job, and deciding where text breaks is the
 * utterance and paragraph layers'.
 *
 * No implementation may fabricate speech regions. A real implementation must actually decode and
 * analyze the audio samples; until one is integrated, the app uses
 * [UnavailableVoiceActivityDetector], which honestly reports that no VAD model is installed.
 */
interface VoiceActivityDetector {
    suspend fun detectSpeechIntervals(audioFile: File, totalDurationMs: Long): AiResult<List<SpeechRegion>>
}

/**
 * Default VAD implementation until a real on-device VAD model (e.g. Silero VAD) is integrated.
 * Deliberately does not analyze audio or invent speech activity — see docs/AI_ARCHITECTURE.md.
 */
class UnavailableVoiceActivityDetector : VoiceActivityDetector {
    override suspend fun detectSpeechIntervals(
        audioFile: File,
        totalDurationMs: Long
    ): AiResult<List<SpeechRegion>> = AiResult.ModelUnavailable(
        modelId = "vad",
        message = "No local voice activity detection model is installed on this device."
    )
}
