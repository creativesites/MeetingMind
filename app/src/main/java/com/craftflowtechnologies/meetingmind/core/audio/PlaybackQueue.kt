package com.craftflowtechnologies.meetingmind.core.audio

import android.net.Uri

/** One playable part of a queue (a section of a reading). */
data class QueueItem(
    /** Unique and content-specific: it includes whatever makes the audio current (a fingerprint), so changed audio is never mistaken for loaded audio. */
    val mediaId: String,
    val title: String,
    val uri: Uri,
    /** The note blocks this audio was made from, for scrolling and highlighting. */
    val blockIds: List<String> = emptyList(),
    val sectionId: String? = null,
    /** Pause when this item ends instead of running into the next (a reflection question). */
    val pauseAfter: Boolean = false
)

/** What the UI needs to know about a queue; no URIs. */
data class QueueInfo(
    val titles: List<String>,
    val index: Int,
    /** How many items the queue will have when it is complete; more than [titles] while sections are still being made. */
    val expectedCount: Int,
    /** The last available item finished but more are on the way: show "preparing", not "finished". */
    val waitingForNext: Boolean = false,
    val blockIds: List<List<String>> = emptyList(),
    /** Playback paused by itself at the end of a reflection question. */
    val pausedAfterQuestion: Boolean = false,
    /** The section each item was made from (a passage's part is `v<first verse>`), so a reader can follow it. */
    val sectionIds: List<String> = emptyList()
) {
    val count: Int get() = titles.size
    val currentSectionId: String? get() = sectionIds.getOrNull(index)
    val currentTitle: String get() = titles.getOrNull(index).orEmpty()
    val hasNext: Boolean get() = index < expectedCount - 1
    val hasPrevious: Boolean get() = index > 0
    val currentBlockIds: List<String> get() = blockIds.getOrNull(index).orEmpty()
}

/** Where a queue was left, handed to whoever stores it (see [PlaybackController.progressListener]). */
data class QueueProgress(
    val queueId: String,
    val mediaId: String,
    val index: Int,
    val positionMs: Long,
    val completed: Boolean
)

/** Queue decisions as pure functions, because the real player can't be faked in unit tests. */
object QueueLogic {

    fun clampIndex(index: Int, count: Int): Int = if (count <= 0) 0 else index.coerceIn(0, count - 1)

    /** The next item, or null at the end of what is available. */
    fun nextIndex(index: Int, available: Int): Int? = (index + 1).takeIf { it < available }

    /**
     * "Previous": more than [restartAfterMs] into an item restarts it; earlier than that goes to the
     * item before (or restarts the first one).
     */
    fun previousTarget(index: Int, positionMs: Long, restartAfterMs: Long = 3_000): Int =
        if (positionMs > restartAfterMs || index <= 0) index.coerceAtLeast(0) else index - 1

    /** Only an item that ended by itself pauses playback; a skip the person made does not. */
    fun pauseOnTransition(previousPauseAfter: Boolean?, autoTransition: Boolean): Boolean =
        previousPauseAfter == true && autoTransition

    /** The player ran out of items but the reading isn't complete yet. */
    fun isWaitingForNext(playerEnded: Boolean, currentIndex: Int, available: Int, expected: Int): Boolean =
        playerEnded && available in 1 until expected && currentIndex >= available - 1

    /** The same audio is already loaded for this queue: seek instead of reloading. */
    fun canReuse(loadedMediaIds: List<String>, requestedMediaIds: List<String>): Boolean =
        loadedMediaIds.isNotEmpty() && loadedMediaIds == requestedMediaIds

    /**
     * Where to start when resuming. A position within [endGraceMs] of the end of its item means the
     * person had effectively finished it, so resume at the start of the next one.
     */
    fun resumePoint(savedIndex: Int, savedPositionMs: Long, savedItemDurationMs: Long?, count: Int, endGraceMs: Long = 1_500): Pair<Int, Long> {
        if (count <= 0) return 0 to 0L
        val index = clampIndex(savedIndex, count)
        val position = savedPositionMs.coerceAtLeast(0)
        val finished = savedItemDurationMs != null && savedItemDurationMs > 0 && position >= savedItemDurationMs - endGraceMs
        return if (finished && index < count - 1) (index + 1) to 0L else index to (if (finished) 0L else position)
    }

    fun progressDue(lastSavedAtMs: Long, nowMs: Long, intervalMs: Long = 5_000): Boolean = nowMs - lastSavedAtMs >= intervalMs
}

/** When a reading starts and stops playing, for the companion's "reading" pose. */
object ReadAloudSignals {
    const val ID_PREFIX = "read:"

    fun isReading(state: PlaybackState): Boolean = state.recordingId?.startsWith(ID_PREFIX) == true && state.phase == PlaybackPhase.PLAYING

    enum class Signal { STARTED, STOPPED }

    fun transition(wasReading: Boolean, isReading: Boolean): Signal? = when {
        !wasReading && isReading -> Signal.STARTED
        wasReading && !isReading -> Signal.STOPPED
        else -> null
    }
}
