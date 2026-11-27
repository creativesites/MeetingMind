package com.craftflowtechnologies.meetingmind.feature.today

import com.craftflowtechnologies.meetingmind.core.timeline.DeepTarget
import com.craftflowtechnologies.meetingmind.core.timeline.ItemKind
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineDays
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's Timeline pages: 20 at a time, reading further back only when a page needs it, and a clear end. */
class TimelinePagingTest {

    private val day = 86_400_000L
    private val now = 1_790_000_000_000L

    private fun item(id: String, start: Long) = TimelineItem(
        id = id, layer = TimelineLayer.NOTES, kind = ItemKind.NOTE, start = start, end = null,
        title = id, subtitle = null, coverPath = null, accent = 0L, workflow = null, target = DeepTarget.Note(id)
    )

    @Test fun `the first page is twenty items, newest first, nothing too far ahead`() {
        val items = (0 until 30).map { item("n$it", now - it * day) } + item("far", now + 30 * day) + item("n0", now)
        val river = TimelinePaging.riverItems(items, now)
        assertEquals(30, river.size) // the far-ahead item and the duplicate are gone
        assertEquals("n0", river.first().id)
        val first = TimelinePaging.visible(river, TimelinePage())
        assertEquals(TimelinePaging.PAGE_SIZE, first.size)
        assertEquals("n19", first.last().id)
    }

    @Test fun `the next page reveals loaded items and animates only the new ones`() {
        val page = TimelinePage(checked = true, earliest = now - 400 * day, windowFrom = now - 500 * day)
        assertTrue(TimelinePaging.nextIsLoaded(45, page))
        val next = TimelinePaging.next(45, page)
        assertEquals(40, next.shown)
        assertEquals(20, next.revealFrom) // cards 20 to 39 fade in; the first page doesn't replay
        assertEquals(45, TimelinePaging.next(45, next).shown) // a short last page
    }

    @Test fun `a page that isn't loaded yet reads further back`() {
        val page = TimelinePage(checked = true, earliest = now - 900 * day, windowFrom = now - 60 * day)
        assertFalse(TimelinePaging.nextIsLoaded(25, page))
        assertTrue(TimelinePaging.hasMore(20, page))
    }

    @Test fun `the end is reached when everything is shown and the window covers the oldest note`() {
        val page = TimelinePage(shown = 20, checked = true, earliest = now - 30 * day, windowFrom = now - 60 * day)
        assertFalse(TimelinePaging.hasMore(12, page))
        assertTrue(TimelinePaging.hasMore(25, page)) // loaded but not shown yet
        assertTrue(TimelinePaging.nextIsLoaded(25, page)) // nothing older to read, so just reveal
        assertFalse(TimelinePaging.hasMore(0, TimelinePage(checked = true, earliest = null, windowFrom = now))) // no notes at all
    }

    @Test fun `reading back reaches the day of the note that fills the page, or the oldest note`() {
        val windowFrom = TimelineDays.startOfDay(now) - 60 * day
        val reach = now - 90 * day + 5 * 3_600_000L
        assertEquals(TimelineDays.startOfDay(reach), TimelinePaging.olderWindowFrom(windowFrom, reach, earliest = now - 400 * day))
        val earliest = now - 70 * day
        val stop = TimelinePaging.olderWindowFrom(windowFrom, pageReach = null, earliest = earliest)
        assertEquals(TimelineDays.startOfDay(earliest), stop)
        assertTrue(TimelinePaging.reachedStart(stop, earliest))
        assertEquals(windowFrom, TimelinePaging.olderWindowFrom(windowFrom, pageReach = null, earliest = null)) // no notes: stay put
    }
}
