package com.example.ai.diarization

import com.example.ai.common.AiResult
import com.example.core.model.Speaker
import com.example.core.model.TranscriptSegment

/**
 * Assigns speaker identity to transcript segments.
 *
 * No implementation of this interface may assign speaker labels by heuristic (turn order,
 * pause length, etc.) and call it diarization — that is not diarization, it is a guess. A real
 * implementation must derive speaker identity from actual acoustic features (e.g. speaker
 * embeddings + clustering). Until one is integrated, the app uses [UnavailableSpeakerDiarizer].
 * See docs/AI_ARCHITECTURE.md.
 */
interface SpeakerDiarizer {
    suspend fun diarize(
        segments: List<TranscriptSegment>,
        knownSpeakers: List<Speaker> = emptyList()
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
        segments: List<TranscriptSegment>,
        knownSpeakers: List<Speaker>
    ): AiResult<List<TranscriptSegment>> = AiResult.ModelUnavailable(
        modelId = "diarization",
        message = "No local speaker diarization model is installed on this device."
    )
}
