package com.example.core.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mini-player and MeetingDetailScreen both key their visibility/controls off these computed
 * properties (see MainActivity's `playbackState.isActive` gate and BentoAudioPlayerCard's
 * `playerState.isPlaying`) — a mistake here would show a stopped player as active, or hide a
 * loading one, which is exactly the class of "is it still playing?" bug this whole architecture
 * exists to prevent.
 */
class PlaybackStateTest {

    @Test
    fun `idle state is not active`() {
        val state = PlaybackState(phase = PlaybackPhase.IDLE)
        assertFalse(state.isActive)
        assertFalse(state.isPlaying)
        assertFalse(state.isPaused)
    }

    @Test
    fun `loading state is active but neither playing nor paused`() {
        val state = PlaybackState(phase = PlaybackPhase.LOADING)
        assertTrue(state.isActive)
        assertFalse(state.isPlaying)
        assertFalse(state.isPaused)
    }

    @Test
    fun `playing state is active and isPlaying`() {
        val state = PlaybackState(phase = PlaybackPhase.PLAYING)
        assertTrue(state.isActive)
        assertTrue(state.isPlaying)
        assertFalse(state.isPaused)
    }

    @Test
    fun `paused state is active and isPaused, not isPlaying`() {
        val state = PlaybackState(phase = PlaybackPhase.PAUSED)
        assertTrue(state.isActive)
        assertFalse(state.isPlaying)
        assertTrue(state.isPaused)
    }

    @Test
    fun `completed and error states are not active`() {
        assertFalse(PlaybackState(phase = PlaybackPhase.COMPLETED).isActive)
        assertFalse(PlaybackState(phase = PlaybackPhase.ERROR).isActive)
    }

    @Test
    fun `default state is a fully idle, empty state`() {
        val state = PlaybackState()
        assertFalse(state.isActive)
        assert(state.recordingId == null)
        assert(state.positionMs == 0L)
    }
}
