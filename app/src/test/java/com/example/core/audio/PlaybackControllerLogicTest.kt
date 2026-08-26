package com.example.core.audio

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlaybackController] wraps a Media3 [androidx.media3.session.MediaController] that this project
 * has no way to fake (no mocking library) — so the actual decision logic that drives the P0
 * playback-sync bugs is pulled out into pure top-level functions and tested directly here, rather
 * than left untestable inside the singleton. See docs/AI_ARCHITECTURE.md "Phase 15 §1 audit" for
 * the root-cause writeup these tests pin down.
 */
class PlaybackControllerLogicTest {

    // --- computePlaybackPhase: the actual bug behind "sync sometimes only works after
    // the 3rd/4th playback" — a mid-playback rebuffer must never read as stopped/loading ---

    @Test
    fun `ended state maps to COMPLETED regardless of isPlaying`() {
        assertEquals(
            PlaybackPhase.COMPLETED,
            computePlaybackPhase(Player.STATE_ENDED, isPlaying = false, playWhenReady = true, previousPhase = PlaybackPhase.PLAYING)
        )
    }

    @Test
    fun `isPlaying true maps to PLAYING even mid-buffer`() {
        assertEquals(
            PlaybackPhase.PLAYING,
            computePlaybackPhase(Player.STATE_BUFFERING, isPlaying = true, playWhenReady = true, previousPhase = PlaybackPhase.LOADING)
        )
    }

    @Test
    fun `a mid-playback rebuffer with playWhenReady still maps to PLAYING, not LOADING`() {
        // The exact case that previously made a brief network/decoder hiccup look identical to
        // "nothing has started yet": isPlaying can momentarily be false while ExoPlayer is
        // buffering, but playWhenReady staying true is what distinguishes "still trying to keep
        // playing" from a genuinely fresh, not-yet-started load.
        assertEquals(
            PlaybackPhase.PLAYING,
            computePlaybackPhase(Player.STATE_BUFFERING, isPlaying = false, playWhenReady = true, previousPhase = PlaybackPhase.PLAYING)
        )
    }

    @Test
    fun `an initial load buffering without playWhenReady maps to LOADING`() {
        assertEquals(
            PlaybackPhase.LOADING,
            computePlaybackPhase(Player.STATE_BUFFERING, isPlaying = false, playWhenReady = false, previousPhase = PlaybackPhase.IDLE)
        )
    }

    @Test
    fun `ready and not playing maps to PAUSED`() {
        assertEquals(
            PlaybackPhase.PAUSED,
            computePlaybackPhase(Player.STATE_READY, isPlaying = false, playWhenReady = false, previousPhase = PlaybackPhase.PLAYING)
        )
    }

    @Test
    fun `an unrecognized transition preserves the previous phase rather than guessing`() {
        assertEquals(
            PlaybackPhase.PAUSED,
            computePlaybackPhase(Player.STATE_IDLE, isPlaying = false, playWhenReady = false, previousPhase = PlaybackPhase.PAUSED)
        )
    }

    // --- isRecordingReadyToResume: the root cause behind "audio starts from the beginning
    // instead of the tapped timestamp" — whether a seek can land immediately vs. must be queued ---

    @Test
    fun `nothing loaded yet is never ready to resume`() {
        assertFalse(isRecordingReadyToResume(hasController = false, loadedRecordingId = null, requestedRecordingId = "m1", mediaItemCount = 0, phase = PlaybackPhase.IDLE))
    }

    @Test
    fun `a different recording already loaded is not ready to resume this one`() {
        assertFalse(isRecordingReadyToResume(hasController = true, loadedRecordingId = "m1", requestedRecordingId = "m2", mediaItemCount = 1, phase = PlaybackPhase.PAUSED))
    }

    @Test
    fun `a completed recording is not ready to resume - it must reload`() {
        assertFalse(isRecordingReadyToResume(hasController = true, loadedRecordingId = "m1", requestedRecordingId = "m1", mediaItemCount = 1, phase = PlaybackPhase.COMPLETED))
    }

    @Test
    fun `the same recording already prepared and not completed is ready to resume`() {
        assertTrue(isRecordingReadyToResume(hasController = true, loadedRecordingId = "m1", requestedRecordingId = "m1", mediaItemCount = 1, phase = PlaybackPhase.PAUSED))
        assertTrue(isRecordingReadyToResume(hasController = true, loadedRecordingId = "m1", requestedRecordingId = "m1", mediaItemCount = 1, phase = PlaybackPhase.PLAYING))
    }

    // --- shouldLoopBack ---

    @Test
    fun `no loop range never loops back`() {
        assertFalse(shouldLoopBack(positionMs = 999_999L, loopRange = null))
    }

    @Test
    fun `position within the loop range does not loop back yet`() {
        assertFalse(shouldLoopBack(positionMs = 5_000L, loopRange = 4_000L..8_000L))
    }

    @Test
    fun `position at or past the loop end loops back`() {
        assertTrue(shouldLoopBack(positionMs = 8_000L, loopRange = 4_000L..8_000L))
        assertTrue(shouldLoopBack(positionMs = 9_000L, loopRange = 4_000L..8_000L))
    }
}
