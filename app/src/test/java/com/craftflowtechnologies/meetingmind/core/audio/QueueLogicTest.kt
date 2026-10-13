package com.craftflowtechnologies.meetingmind.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {

    @Test fun `index is clamped into the queue`() {
        assertEquals(0, QueueLogic.clampIndex(-3, 5))
        assertEquals(4, QueueLogic.clampIndex(9, 5))
        assertEquals(2, QueueLogic.clampIndex(2, 5))
        assertEquals(0, QueueLogic.clampIndex(3, 0))
    }

    @Test fun `next stops at the end of what is available`() {
        assertEquals(1, QueueLogic.nextIndex(0, 3))
        assertEquals(2, QueueLogic.nextIndex(1, 3))
        assertNull(QueueLogic.nextIndex(2, 3))
        assertNull(QueueLogic.nextIndex(0, 1))
    }

    @Test fun `previous restarts a part that has played a while, otherwise goes back`() {
        assertEquals(2, QueueLogic.previousTarget(2, positionMs = 10_000))
        assertEquals(1, QueueLogic.previousTarget(2, positionMs = 1_000))
        assertEquals(0, QueueLogic.previousTarget(0, positionMs = 500))
        assertEquals(1, QueueLogic.previousTarget(1, positionMs = 3_001))
        assertEquals(0, QueueLogic.previousTarget(1, positionMs = 3_000))
    }

    @Test fun `only a part that ended by itself pauses after a question`() {
        assertTrue(QueueLogic.pauseOnTransition(previousPauseAfter = true, autoTransition = true))
        assertFalse(QueueLogic.pauseOnTransition(previousPauseAfter = true, autoTransition = false)) // the person skipped
        assertFalse(QueueLogic.pauseOnTransition(previousPauseAfter = false, autoTransition = true))
        assertFalse(QueueLogic.pauseOnTransition(previousPauseAfter = null, autoTransition = true))
    }

    @Test fun `running out of parts mid-reading is waiting, not finished`() {
        assertTrue(QueueLogic.isWaitingForNext(playerEnded = true, currentIndex = 2, available = 3, expected = 5))
        assertFalse(QueueLogic.isWaitingForNext(playerEnded = true, currentIndex = 4, available = 5, expected = 5)) // all done
        assertFalse(QueueLogic.isWaitingForNext(playerEnded = false, currentIndex = 2, available = 3, expected = 5))
        assertFalse(QueueLogic.isWaitingForNext(playerEnded = true, currentIndex = 0, available = 3, expected = 5)) // more parts already queued
        assertFalse(QueueLogic.isWaitingForNext(playerEnded = true, currentIndex = 0, available = 0, expected = 5))
    }

    @Test fun `loaded audio is reused only when every part is the same`() {
        val a = listOf("read:n:s1:aa", "read:n:s2:bb")
        assertTrue(QueueLogic.canReuse(a, a.toList()))
        assertFalse(QueueLogic.canReuse(a, listOf("read:n:s1:aa", "read:n:s2:CHANGED"))) // regenerated section
        assertFalse(QueueLogic.canReuse(a, a + "read:n:s3:cc"))
        assertFalse(QueueLogic.canReuse(emptyList(), emptyList()))
    }

    @Test fun `resume lands where the person left off`() {
        assertEquals(2 to 40_000L, QueueLogic.resumePoint(2, 40_000, savedItemDurationMs = 120_000, count = 5))
    }

    @Test fun `resume past the end of a part goes to the start of the next`() {
        assertEquals(3 to 0L, QueueLogic.resumePoint(2, 119_000, savedItemDurationMs = 120_000, count = 5))
    }

    @Test fun `resume at the end of the last part stays on it, from the start`() {
        assertEquals(4 to 0L, QueueLogic.resumePoint(4, 119_800, savedItemDurationMs = 120_000, count = 5))
    }

    @Test fun `resume clamps a stale index and negative position`() {
        assertEquals(4 to 0L, QueueLogic.resumePoint(99, -5, null, count = 5))
        assertEquals(0 to 0L, QueueLogic.resumePoint(3, 1_000, 2_000, count = 0))
    }

    @Test fun `progress is saved at an interval`() {
        assertFalse(QueueLogic.progressDue(lastSavedAtMs = 1_000, nowMs = 5_999))
        assertTrue(QueueLogic.progressDue(lastSavedAtMs = 1_000, nowMs = 6_000))
    }

    @Test fun `queue info answers next, previous and the current part`() {
        val q = QueueInfo(titles = listOf("A", "B", "C"), index = 1, expectedCount = 4, blockIds = listOf(listOf("b1"), listOf("b2", "b3"), emptyList()))
        assertEquals("B", q.currentTitle)
        assertEquals(listOf("b2", "b3"), q.currentBlockIds)
        assertTrue(q.hasPrevious)
        assertTrue(q.hasNext) // a fourth part is still being made
        val done = q.copy(index = 3, expectedCount = 3)
        assertFalse(done.hasNext)
        assertEquals("", done.currentTitle)
    }

    @Test fun `read aloud signals fire only on real changes`() {
        assertEquals(ReadAloudSignals.Signal.STARTED, ReadAloudSignals.transition(wasReading = false, isReading = true))
        assertEquals(ReadAloudSignals.Signal.STOPPED, ReadAloudSignals.transition(wasReading = true, isReading = false))
        assertNull(ReadAloudSignals.transition(true, true))
        assertNull(ReadAloudSignals.transition(false, false))
    }

    @Test fun `only a playing read queue counts as reading`() {
        assertTrue(ReadAloudSignals.isReading(PlaybackState(phase = PlaybackPhase.PLAYING, recordingId = "read:note1")))
        assertFalse(ReadAloudSignals.isReading(PlaybackState(phase = PlaybackPhase.PAUSED, recordingId = "read:note1")))
        assertFalse(ReadAloudSignals.isReading(PlaybackState(phase = PlaybackPhase.PLAYING, recordingId = "devotional:note1")))
        assertFalse(ReadAloudSignals.isReading(PlaybackState()))
    }

    @Test fun `playback state carries the queue and stays active while waiting for a part`() {
        val waiting = PlaybackState(phase = PlaybackPhase.LOADING, recordingId = "read:n", queue = QueueInfo(listOf("A"), 0, 3, waitingForNext = true))
        assertTrue(waiting.isActive)
        assertEquals(3, waiting.queue?.expectedCount)
        assertNull(PlaybackState().queue)
    }
}
