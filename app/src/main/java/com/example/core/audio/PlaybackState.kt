package com.example.core.audio

/** Real, typed playback lifecycle — mirrors ProcessingStage's "no fake progress" discipline. */
enum class PlaybackPhase { IDLE, LOADING, PLAYING, PAUSED, COMPLETED, ERROR }

/**
 * Single source of truth for audio playback, owned by [PlaybackController] — never fabricated,
 * never duplicated per-screen. `recordingId` identifies which recording (not necessarily a
 * "meeting") is loaded, independent of which screen is currently observing it.
 *
 * [currentSegmentId]/[currentSpeakerId] are computed here (from whatever segments were last
 * registered via [PlaybackController.setSegments] for the loaded recording) rather than by each
 * screen re-deriving "which segment is active" from [positionMs] on its own — the whole reason
 * this type exists is so there is exactly one place that answers that question. [isBuffering] is
 * deliberately separate from [phase]: a brief mid-playback rebuffer must not read as "paused" or
 * "stopped" to a screen only checking `phase`.
 */
data class PlaybackState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val recordingId: String? = null,
    val title: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val canSeek: Boolean = false,
    val errorMessage: String? = null,
    val isBuffering: Boolean = false,
    val currentSegmentId: String? = null,
    val currentSpeakerId: String? = null,
    /** Non-null while [PlaybackController.setLoop] has an active loop window for the current
     * recording — playback is repeatedly seeked back to `loopRange.first` on reaching
     * `loopRange.last`. Used by review-style UI that wants to replay a short window on repeat
     * (e.g. "loop the few seconds around this flagged word") without the screen polling position
     * itself to detect the boundary. */
    val loopRange: LongRange? = null
) {
    val isPlaying: Boolean get() = phase == PlaybackPhase.PLAYING
    val isPaused: Boolean get() = phase == PlaybackPhase.PAUSED
    val isActive: Boolean get() = phase == PlaybackPhase.PLAYING || phase == PlaybackPhase.PAUSED || phase == PlaybackPhase.LOADING
}
