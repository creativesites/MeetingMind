package com.craftflowtechnologies.meetingmind.core.notes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeysetPagerTest {
    private data class Item(val id: String, val key: Long)

    /** A newest-first source ordered by (key DESC, id DESC), like the DAO queries. */
    private class Source(items: List<Item>) {
        val rows = items.toMutableList()
        var calls = 0
        var failNext: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null
        val cursors = mutableListOf<Cursor?>()

        suspend fun page(after: Cursor?, limit: Int): List<Item> {
            calls++
            cursors += after
            gate?.await()
            failNext?.let { failNext = null; throw it }
            return rows.sortedWith(compareByDescending<Item> { it.key }.thenByDescending { it.id })
                .filter { after == null || it.key < after.longKey || (it.key == after.longKey && it.id < after.id) }
                .take(limit)
        }
    }

    private fun items(n: Int, keyOf: (Int) -> Long = { it.toLong() }) = (1..n).map { Item("i%03d".format(it), keyOf(it)) }
    private fun TestScope.pager(src: Source, size: Int = 30) = KeysetPager(this, size, { Cursor(it.key, it.id) }, src::page)

    @Test
    fun first_page_then_append_until_end_with_exact_boundary() = runTest {
        val src = Source(items(60)); val p = pager(src)
        p.loadMore(); assertTrue(p.state.value.isLoadingFirst); advanceUntilIdle()
        assertEquals(30, p.state.value.items.size); assertFalse(p.state.value.endReached)
        p.loadMore(); advanceUntilIdle()
        // 60 rows = two full pages, so the end shows up only on the (empty) third load.
        assertEquals(60, p.state.value.items.size); assertFalse(p.state.value.endReached)
        p.loadMore(); advanceUntilIdle()
        assertTrue(p.state.value.endReached); assertEquals(60, p.state.value.items.size)
        val calls = src.calls; p.loadMore(); advanceUntilIdle(); assertEquals(calls, src.calls)
    }

    @Test
    fun short_page_marks_end_and_empty_source_is_an_ended_empty_list() = runTest {
        val p = pager(Source(items(7))); p.loadMore(); advanceUntilIdle()
        assertTrue(p.state.value.endReached); assertEquals(7, p.state.value.items.size)
        val e = pager(Source(emptyList())); e.loadMore(); advanceUntilIdle()
        assertTrue(e.state.value.endReached); assertTrue(e.state.value.items.isEmpty()); assertFalse(e.state.value.isLoadingFirst)
    }

    @Test
    fun ties_on_sort_key_across_a_page_boundary_neither_repeat_nor_skip() = runTest {
        val src = Source(items(95) { 5L }) // one key for every row; only the id orders them
        val p = pager(src, 10)
        repeat(12) { p.loadMore(); advanceUntilIdle() }
        val ids = p.state.value.items.map { it.id }
        assertEquals(95, ids.size); assertEquals(95, ids.toSet().size)
        assertEquals(src.rows.map { it.id }.sortedDescending(), ids)
    }

    @Test
    fun an_item_inserted_at_the_top_mid_scroll_does_not_duplicate_or_skip_older_items() = runTest {
        val src = Source(items(50)); val p = pager(src, 10)
        p.loadMore(); advanceUntilIdle()
        src.rows += Item("new1", 1_000); src.rows += Item("new2", 999)
        repeat(6) { p.loadMore(); advanceUntilIdle() }
        val ids = p.state.value.items.map { it.id }
        assertEquals(50, ids.size); assertEquals(50, ids.toSet().size)
        assertFalse("new rows belong to the next refresh", "new1" in ids)
        p.refresh(); advanceUntilIdle(); repeat(6) { p.loadMore(); advanceUntilIdle() }
        assertEquals(listOf("new1", "new2"), p.state.value.items.take(2).map { it.id }); assertEquals(52, p.state.value.items.size)
    }

    @Test
    fun a_row_that_moves_between_pages_is_shown_once() = runTest {
        val src = Source(items(30)); val p = pager(src, 10)
        p.loadMore(); advanceUntilIdle()
        // i030 (already shown) is edited and now sorts after the cursor... it must not appear twice.
        src.rows.replaceAll { if (it.id == "i030") it.copy(key = 0) else it }
        repeat(4) { p.loadMore(); advanceUntilIdle() }
        val ids = p.state.value.items.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun concurrent_loadMore_calls_run_one_load() = runTest {
        val src = Source(items(100)); src.gate = CompletableDeferred(); val p = pager(src)
        repeat(5) { p.loadMore() }; advanceUntilIdle()
        assertEquals(1, src.calls)
        src.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(30, p.state.value.items.size)
        src.gate = null
        p.loadMore(); p.loadMore(); advanceUntilIdle()
        assertEquals(2, src.calls); assertEquals(60, p.state.value.items.size)
    }

    @Test
    fun refresh_reloads_from_the_top_keeps_old_rows_meanwhile_and_drops_stale_in_flight_loads() = runTest {
        val src = Source(items(100)); val p = pager(src)
        p.loadMore(); advanceUntilIdle(); p.loadMore(); advanceUntilIdle()
        assertEquals(60, p.state.value.items.size)
        src.gate = CompletableDeferred()
        p.loadMore(); advanceUntilIdle() // in flight, gated
        src.rows.clear(); src.rows += items(40)
        p.refresh(); advanceUntilIdle()
        assertTrue(p.state.value.isRefreshing); assertEquals(60, p.state.value.items.size)
        src.gate!!.complete(Unit); advanceUntilIdle()
        val s = p.state.value
        assertEquals(30, s.items.size); assertFalse(s.isRefreshing); assertFalse(s.isAppending); assertFalse(s.endReached)
        assertTrue(src.cursors.drop(3).contains(null))
        p.loadMore(); advanceUntilIdle()
        assertEquals(40, p.state.value.items.size); assertTrue(p.state.value.endReached)
    }

    @Test
    fun refresh_after_the_end_was_reached_can_page_again() = runTest {
        val src = Source(items(5)); val p = pager(src)
        p.loadMore(); advanceUntilIdle(); assertTrue(p.state.value.endReached)
        src.rows += items(60).map { it.copy(id = "z" + it.id) }
        p.refresh(); advanceUntilIdle()
        assertFalse(p.state.value.endReached); assertEquals(30, p.state.value.items.size)
    }

    @Test
    fun an_error_keeps_loaded_items_blocks_loadMore_and_retry_continues_from_the_same_cursor() = runTest {
        val src = Source(items(80)); val p = pager(src)
        p.loadMore(); advanceUntilIdle()
        val before = p.state.value.items
        src.failNext = IllegalStateException("offline")
        p.loadMore(); advanceUntilIdle()
        assertEquals(before, p.state.value.items); assertNotNull(p.state.value.error); assertFalse(p.state.value.isAppending)
        val calls = src.calls; p.loadMore(); advanceUntilIdle(); assertEquals(calls, src.calls)
        p.retry(); advanceUntilIdle()
        assertNull(p.state.value.error); assertEquals(60, p.state.value.items.size)
        assertEquals(src.cursors[1], src.cursors[2]) // the retry asked for the page that failed
    }

    @Test
    fun a_failed_first_load_retries_as_a_first_load_and_a_failed_refresh_keeps_rows() = runTest {
        val src = Source(items(40)); val p = pager(src)
        src.failNext = RuntimeException("x"); p.loadMore(); advanceUntilIdle()
        assertTrue(p.state.value.items.isEmpty()); assertNotNull(p.state.value.error); assertFalse(p.state.value.isLoadingFirst)
        p.retry(); advanceUntilIdle(); assertEquals(30, p.state.value.items.size); assertNull(p.state.value.error)
        src.failNext = RuntimeException("y"); p.refresh(); advanceUntilIdle()
        assertEquals(30, p.state.value.items.size); assertNotNull(p.state.value.error); assertFalse(p.state.value.isRefreshing)
        p.retry(); advanceUntilIdle()
        assertNull(p.state.value.error); assertEquals(30, p.state.value.items.size); assertNull(src.cursors.last())
    }
}
