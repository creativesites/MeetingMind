package com.example.ai.diarization

import com.example.ai.common.AiResult
import com.example.core.model.Speaker
import com.example.core.model.TranscriptSegment
import java.io.File

/**
 * Assigns speaker identity to transcript segments.
 *
 * No implementation of this interface may assign speaker labels by heuristic (turn order,
 * pause length, etc.) and call it diarization — that is not diarization, it is a guess. A real
 * implementation must derive speaker identity from actual acoustic features (e.g. speaker
 * embeddings + clustering) computed from [audioFile] itself — the ASR-produced [segments] alone
 * (text + timestamps) are not sufficient input for real diarization, which is why this takes the
 * audio directly. Until a real model is installed, the app uses [UnavailableSpeakerDiarizer].
 * See docs/AI_ARCHITECTURE.md.
 */
interface SpeakerDiarizer {
    suspend fun diarize(
        audioFile: File,
        totalDurationMs: Long,
        segments: List<TranscriptSegment>,
        knownSpeakers: List<Speaker> = emptyList(),
        /** Null (or omitted) lets the clustering algorithm auto-detect the speaker count; a positive value forces exactly that many speakers. */
        expectedSpeakerCount: Int? = null
    ): AiResult<List<TranscriptSegment>>
}

/**
 * Default diarization implementation until a real on-device diarization model (speaker
 * embeddings + clustering) is integrated. Returns the input segments unmodified rather than
 * fabricating speaker turns — every segment keeps whatever speaker label ASR assigned it
 * (typically a single generic speaker) instead of being split by a guess.
 */
class UnavailableSpeakerDiarizer : SpeakerDiarizer {
    override suspend fun diarize(
        audioFile: File,
        totalDurationMs: Long,
        segments: List<TranscriptSegment>,
        knownSpeakers: List<Speaker>,
        expectedSpeakerCount: Int?
    ): AiResult<List<TranscriptSegment>> = AiResult.ModelUnavailable(
        modelId = "diarization",
        message = "No local speaker diarization model is installed on this device."
    )
}
