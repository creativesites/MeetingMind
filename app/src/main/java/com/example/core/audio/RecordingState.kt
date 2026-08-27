package com.example.core.audio

/**
 * The live-capture lifecycle (Phase 15 §Part 2 / design capture-pipeline spec §3.1), owned by
 * [MeetingRecordingService] and exposed as a [kotlinx.coroutines.flow.StateFlow]. Deliberately
 * separate from [com.example.core.model.MeetingStatus], which is the *persisted* lifecycle of the
 * recording object once it exists in Room — [RecordingState] only ever describes what the service
 * is doing to the microphone/file right now, and stops existing once capture is over.
 *
 * Only `RECORDING -> SAVED` is reachable from the capture path itself; nothing here ever implies
 * `MeetingStatus.PROCESSING` — stopping a recording is a capture-layer concern, starting analysis
 * of it is a separate, later decision made elsewhere.
 */
enum class RecordingState {
    /** No recording in flight. The service's resting state before start and after teardown. */
    IDLE,

    /** Between "start requested" and the recorder actually running — audio focus is being
     * requested and [AudioRecorder.startRecording] is being attempted. Brief; exists so a failure
     * here can land on [FAILED] instead of silently doing nothing. */
    PREPARING,

    RECORDING,

    /** Either the user paused, or [AudioManager] reported a transient focus loss (e.g. an
     * incoming call) and the service auto-paused on the caller's behalf — never auto-resumed. */
    PAUSED,

    /** Between "stop requested" and the recorder actually stopping — the file is being finalized
     * by [AudioRecorder.stopRecording]. */
    STOPPING,

    /** The audio file is finalized; the [com.example.core.model.Meeting] row is being written to
     * Room. This is the step that must complete before [SAVED] — the audio existing on disk is
     * not enough on its own, since nothing else in the app can find it without the database row. */
    SAVING,

    /** Terminal success: audio is on disk, the Meeting row is persisted. The service tears itself
     * down after reaching this state — a caller reads it once via the completion callback rather
     * than the service remaining bound in this state indefinitely. */
    SAVED,

    /** Starting the recorder itself failed (e.g. the microphone could not be opened). Distinct
     * from a later processing failure — [com.example.core.model.MeetingStatus.ERROR] never
     * applies here since no [com.example.core.model.Meeting] row was ever created to mark as
     * failed; the caller is responsible for surfacing this to the user directly. */
    FAILED
}
