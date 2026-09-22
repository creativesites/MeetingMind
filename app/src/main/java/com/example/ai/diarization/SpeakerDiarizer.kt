package com.example.ai.diarization

import com.example.ai.common.AiResult
import com.example.ai.transcript.DiarizationTurn
import com.example.core.model.Speaker
import java.io.File

/**
 * Determines **who was speaking, when** — and nothing else.
 *
 * This interface used to return relabelled transcript segments, which quietly made diarization
 * responsible for transcript structure as well as speaker identity: whichever speaker overlapped a
 * segment most got every word in it, including the words on the far side of a real speaker change.
 * It now returns raw acoustic turns, and mapping words onto them (with a confidence per word) is
 * [com.example.ai.transcript.WordSpeakerAttributor]'s job.
 *
 * No implementation may assign speaker labels by heuristic (turn order, pause length, etc.) and
 * call it diarization — that is not diarization, it is a guess. A real implementation must derive
 * speaker identity from actual acoustic features (speaker embeddings + clustering) computed from
 * [audioFile] itself. Until a real model is installed, the app uses [UnavailableSpeakerDiarizer].
 * See docs/AI_ARCHITECTURE.md.
 */
interface SpeakerDiarizer {
    suspend fun diarize(
        audioFile: File,
        totalDurationMs: Long,
        /** Stable speaker-id prefix so ids are unique per meeting — see [speakerIdFor]. */
        meetingId: String,
        knownSpeakers: List<Speaker> = emptyList(),
        /** Null (or omitted) lets the clustering algorithm auto-detect the speaker count; a positive value forces exactly that many speakers. */
        expectedSpeakerCount: Int? = null
    ): AiResult<List<DiarizationTurn>>
}

/** The project-wide speaker id convention, unchanged: `spk_<meetingId>_<clusterIndex>`. */
fun speakerIdFor(meetingId: String, speakerIndex: Int): String = "spk_${meetingId}_$speakerIndex"

/**
 * The generic, honest display name for a diarized speaker. Never a guessed real name — the user
 * renames it later without disturbing the underlying id.
 */
fun defaultSpeakerNameFor(speakerIndex: Int): String = "Speaker ${speakerIndex + 1}"

/** Extracts the cluster index back out of an id built by [speakerIdFor]; 0 when unparseable. */
fun speakerIndexOf(speakerId: String): Int = speakerId.substringAfterLast('_').toIntOrNull() ?: 0

/**
 * Default diarization implementation until a real on-device diarization model (speaker
 * embeddings + clustering) is integrated. Reports itself unavailable rather than fabricating
 * speaker turns; the pipeline then leaves every word honestly unattributed.
 */
class UnavailableSpeakerDiarizer : SpeakerDiarizer {
    override suspend fun diarize(
        audioFile: File,
        totalDurationMs: Long,
        meetingId: String,
        knownSpeakers: List<Speaker>,
        expectedSpeakerCount: Int?
    ): AiResult<List<DiarizationTurn>> = AiResult.ModelUnavailable(
        modelId = "diarization",
        message = "No local speaker diarization model is installed on this device."
    )
}
