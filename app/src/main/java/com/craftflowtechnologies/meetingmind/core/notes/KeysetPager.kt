package com.craftflowtechnologies.meetingmind.core.notes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Where the next page starts: the last row's sort key plus its id, which breaks ties between rows that
 * share a key. [sortKey] is a Long (dates) or a String (titles); the query decides which.
 */
data class Cursor(val sortKey: Any, val id: String) {
    init { require(sortKey is Long || sortKey is String) { "sortKey must be Long or String" } }
    val longKey: Long get() = sortKey as Long
    val textKey: String get() = sortKey as String
}

/** What a list shows. [items] survive an [error]; [isRefreshing] is a reload over items already on screen. */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val isLoadingFirst: Boolean = false,
    val isAppending: Boolean = false,
    val endReached: Boolean = false,
    val error: Throwable? = null,
    val isRefreshing: Boolean = false
)

/**
 * A small keyset pager (no Paging 3). UI-agnostic: a ViewModel owns one, collects [state] and calls
 * [loadMore] when the user nears the end of the list.
 *
 * - [loadPage] returns up to `limit` rows strictly after the cursor (null = from the start), in a stable
 *   order that ends in the id. [cursorOf] reads a row's cursor.
 * - Rows are de-duplicated by cursor id, so a row that moves between pages is never shown twice.
 * - Only one load runs at a time; extra [loadMore] calls while loading, at the end or after an error
 *   are ignored (a scroll listener can call it freely). [retry] repeats the failed load.
 * - [refresh] cancels anything in flight and reloads from the top; the old rows stay visible until the
 *   first page lands, and stay if it fails.
 * - A failed load keeps every row already loaded.
 */
class KeysetPager<T>(
    private val scope: CoroutineScope,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val cursorOf: (T) -> Cursor,
    private val loadPage: suspend (after: Cursor?, limit: Int) -> List<T>
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state

    private val lock = Any()
    private var generation = 0
    private var job: Job? = null
    private var seen = HashSet<String>()
    private var last: Cursor? = null
    private var started = false
    private var failedWasReset = true

    init { require(pageSize > 0) }

    /** Loads the next page (or the first, if nothing has loaded yet). No-op while loading, at the end or in error. */
    fun loadMore() { synchronized(lock) {
        val s = _state.value
        if (job?.isActive == true || s.endReached || s.error != null) return
        start(reset = !started)
    } }

    /** Reloads from the top. Always starts, cancelling any load in flight. */
    fun refresh() { synchronized(lock) {
        generation++
        job?.cancel()
        start(reset = true)
    } }

    /** Repeats the load that failed. No-op when there is no error. */
    fun retry() { synchronized(lock) {
        if (_state.value.error == null || job?.isActive == true) return
        start(reset = failedWasReset)
    } }

    /**
     * Changes the rows already on screen (a pin, a delete, an undo) without reloading: the cursor and the
     * de-dup set are untouched, so paging carries on from where it was. Rows a transform adds are marked seen.
     */
    fun update(transform: (List<T>) -> List<T>) { synchronized(lock) {
        val next = transform(_state.value.items)
        next.forEach { seen.add(cursorOf(it).id) }
        _state.value = _state.value.copy(items = next)
    } }

    private fun start(reset: Boolean) {
        val gen = generation
        val after = if (reset) null else last
        _state.value = _state.value.let {
            if (reset) it.copy(error = null, endReached = false, isAppending = false, isLoadingFirst = it.items.isEmpty(), isRefreshing = it.items.isNotEmpty())
            else it.copy(error = null, isAppending = true)
        }
        job = scope.launch {
            val result = try {
                Result.success(loadPage(after, pageSize))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Result.failure(t)
            }
            synchronized(lock) {
                if (gen != generation) return@launch
                result.fold(
                    onSuccess = { page ->
                        if (reset) seen = HashSet()
                        val fresh = page.filter { seen.add(cursorOf(it).id) }
                        page.lastOrNull()?.let { last = cursorOf(it) }
                        if (reset && page.isEmpty()) last = null
                        started = true
                        val base = if (reset) emptyList() else _state.value.items
                        _state.value = PagedState(items = base + fresh, endReached = page.size < pageSize)
                    },
                    onFailure = { t ->
                        failedWasReset = reset
                        _state.value = _state.value.copy(error = t, isLoadingFirst = false, isAppending = false, isRefreshing = false)
                    }
                )
            }
        }
    }

    companion object { const val DEFAULT_PAGE_SIZE = 30 }
}
