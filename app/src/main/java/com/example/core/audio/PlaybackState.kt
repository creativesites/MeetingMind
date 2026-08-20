package com.example.core.audio

/** Real, typed playback lifecycle — mirrors ProcessingStage's "no fake progress" discipline. */
enum class PlaybackPhase { IDLE, LOADING, PLAYING, PAUSED, COMPLETED, ERROR }

/**
 * Single source of truth for audio playback, owned by [PlaybackController] — never fabricated,
 * never duplicated per-screen. `recordingId` identifies which recording (not necessarily a
 * "meeting") is loaded, independent of which screen is currently observing it.
 */
data class PlaybackState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val recordingId: String? = null,
    val title: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val canSeek: Boolean = false,
    val errorMessage: String? = null
) {
    val isPlaying: Boolean get() = phase == PlaybackPhase.PLAYING
    val isPaused: Boolean get() = phase == PlaybackPhase.PAUSED
    val isActive: Boolean get() = phase == PlaybackPhase.PLAYING || phase == PlaybackPhase.PAUSED || phase == PlaybackPhase.LOADING
}
