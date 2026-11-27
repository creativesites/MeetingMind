package com.craftflowtechnologies.meetingmind.feature.today

import com.craftflowtechnologies.meetingmind.core.timeline.TimelineDays
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem

/**
 * Where Today's Timeline is: how many items it shows, how far back it has read, and whether older
 * notes exist. [revealFrom] is where the newest page starts, so only that page animates in.
 */
data class TimelinePage(
    val shown: Int = TimelinePaging.PAGE_SIZE,
    /** The first page appears with the screen; only pages added after it animate in. */
    val revealFrom: Int = TimelinePaging.PAGE_SIZE,
    val loading: Boolean = false,
    /** False until the oldest note's date has been read, so the end state never flashes early. */
    val checked: Boolean = false,
    /** The oldest note's date, or null with none. */
    val earliest: Long? = null,
    /** The start of the window read so far. */
    val windowFrom: Long = Long.MAX_VALUE
)

/** Paging for Today's Timeline: pages of [PAGE_SIZE], reading further back only when a page needs it. */
object TimelinePaging {
    const val PAGE_SIZE = 20

    /** How far ahead the Timeline shows planned items. */
    const val AHEAD_DAYS = 14

    /** The Timeline's items: newest first, nothing further ahead than [AHEAD_DAYS], each once. */
    fun riverItems(items: List<TimelineItem>, now: Long): List<TimelineItem> =
        items.filter { it.start <= now + AHEAD_DAYS * 86_400_000L }.sortedByDescending { it.start }.distinctBy { it.id }

    /** The items on screen. */
    fun visible(river: List<TimelineItem>, page: TimelinePage): List<TimelineItem> = river.take(page.shown)

    /** True when the window already reaches the oldest note, so reading further back finds nothing new. */
    fun reachedStart(windowFrom: Long, earliest: Long?): Boolean = earliest == null || windowFrom <= earliest

    /** Whether anything older can still be shown: loaded but hidden, or not read yet. */
    fun hasMore(loaded: Int, page: TimelinePage): Boolean =
        page.shown < loaded || !reachedStart(page.windowFrom, page.earliest)

    /** The next page needs no reading: it is loaded already, or nothing older exists to read. */
    fun nextIsLoaded(loaded: Int, page: TimelinePage): Boolean =
        loaded >= page.shown + PAGE_SIZE || reachedStart(page.windowFrom, page.earliest)

    /** The page after [page]: [loaded] items available; it animates in from where the last page ended. */
    fun next(loaded: Int, page: TimelinePage): TimelinePage {
        val shown = minOf(page.shown + PAGE_SIZE, maxOf(loaded, page.shown))
        return page.copy(shown = shown, revealFrom = minOf(page.shown, loaded), loading = false)
    }

    /**
     * Where the window starts once the next page is read: the start of the day of [pageReach] (the
     * date of the note that fills the page), or the oldest note's day when fewer are left. Never later
     * than [windowFrom].
     */
    fun olderWindowFrom(windowFrom: Long, pageReach: Long?, earliest: Long?): Long {
        val reach = pageReach ?: earliest ?: return windowFrom
        return minOf(TimelineDays.startOfDay(reach), windowFrom)
    }
}
