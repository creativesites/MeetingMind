package com.craftflowtechnologies.meetingmind.feature.work

import androidx.lifecycle.SavedStateHandle
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteFilters
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteKind
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkPageViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeStore(override var last: String? = null) : SegmentStore

    private class FakeData(count: Int = 10) : WorkPageData {
        val notes = (1..count).map { WorkNoteItem("n%02d".format(it), "Note $it", "", null, RecordingType.MEETING, (1000L - it), false, false, false, NoteTranscriptStatus.None, 0, Cursor(1000L - it, "n%02d".format(it))) }
        val calls = mutableListOf<Triple<Cursor?, WorkNoteSort, WorkNoteFilters>>()
        val searches = mutableListOf<Pair<String, Int>>()
        val trashed = mutableListOf<String>()
        val restored = mutableListOf<String>()
        override suspend fun notesPage(after: Cursor?, limit: Int, sort: WorkNoteSort, filters: WorkNoteFilters): List<WorkNoteItem> {
            calls += Triple(after, sort, filters)
            return notes.filter { it.id !in trashed || it.id in restored }.filter { after == null || it.cursor.longKey < after.longKey }.take(limit)
        }
        override suspend fun search(query: String, offset: Int, limit: Int): List<WorkNoteItem> {
            searches += query to offset
            return notes.take(2).mapIndexed { i, n -> n.copy(cursor = Cursor((offset + i).toLong(), "%06d".format(offset + i))) }.drop(offset).take(limit)
        }
        override suspend fun tasksPage(after: Cursor?, limit: Int, waiting: Boolean) = emptyList<WorkTaskRow>()
        override suspend fun setPinned(noteId: String, pinned: Boolean) = Unit
        override suspend fun moveToProject(noteId: String, notebookId: String?) = Unit
        override suspend fun trash(noteId: String) { trashed += noteId }
        override suspend fun restore(noteId: String) { restored += noteId }
        override suspend fun toggleTask(taskId: String) = Unit
        override suspend fun addTask(title: String) = Unit
    }

    private fun vm(data: WorkPageData = FakeData(), handle: SavedStateHandle = SavedStateHandle(), store: SegmentStore = FakeStore(), size: Int = 3) =
        WorkPageViewModel(data, handle, store, pageSize = size, searchDebounceMs = 100)

    // ---------------------------------------------------------------- default segment

    @Test fun `with nothing remembered the default is Today when there are meetings or needs`() = runTest(dispatcher) {
        val a = vm(); assertNull(a.segment.value); a.resolveDefault(2, 0); assertEquals(WorkSegment.TODAY, a.segment.value)
        val b = vm(); b.resolveDefault(0, 3); assertEquals(WorkSegment.TODAY, b.segment.value)
        val c = vm(); c.resolveDefault(0, 0); assertEquals(WorkSegment.NOTES, c.segment.value)
    }

    @Test fun `a remembered segment beats the default and the default does not override a later choice`() = runTest(dispatcher) {
        val remembered = vm(store = FakeStore("TASKS"))
        assertEquals(WorkSegment.TASKS, remembered.segment.value)
        remembered.resolveDefault(5, 5)
        assertEquals(WorkSegment.TASKS, remembered.segment.value)

        val fresh = vm(); fresh.resolveDefault(0, 0); fresh.select(WorkSegment.PROJECTS); fresh.resolveDefault(9, 9)
        assertEquals(WorkSegment.PROJECTS, fresh.segment.value)
    }

    @Test fun `choosing a segment is remembered for the next launch and survives process death`() = runTest(dispatcher) {
        val store = FakeStore(); val handle = SavedStateHandle()
        vm(store = store, handle = handle).select(WorkSegment.NOTES)
        assertEquals("NOTES", store.last)
        // Process death: the store is empty (new install of prefs) but the handle was restored.
        val restored = vm(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }), store = FakeStore())
        assertEquals(WorkSegment.NOTES, restored.segment.value)
        // A relaunch: the handle is gone, the store remains.
        assertEquals(WorkSegment.NOTES, vm(store = store).segment.value)
    }

    @Test fun `the default is not stored as a choice`() = runTest(dispatcher) {
        val store = FakeStore(); val v = vm(store = store); v.resolveDefault(1, 0)
        assertNull(store.last)
    }

    // ---------------------------------------------------------------- filter state

    @Test fun `filters sort project and search are restored from the saved state`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = vm(handle = handle)
        first.select(WorkSegment.NOTES)
        first.toggleChip(NoteChip.MEETINGS); first.toggleChip(NoteChip.HAS_TASKS); first.setProject("nb7"); first.setSort(WorkNoteSort.TITLE); first.setSearch("quote")
        advanceUntilIdle()
        val expected = first.query.value
        assertEquals(setOf(NoteChip.MEETINGS, NoteChip.HAS_TASKS), expected.chips)

        // Rotation keeps the ViewModel; process death rebuilds it from a copy of the handle.
        val restored = vm(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        assertEquals(expected, restored.query.value)
        assertEquals(WorkSegment.NOTES, restored.segment.value)
    }

    @Test fun `a filter change restarts the list with those filters and keyset cursors`() = runTest(dispatcher) {
        val data = FakeData(); val v = vm(data)
        v.select(WorkSegment.NOTES); advanceUntilIdle()
        assertEquals(listOf("n01", "n02", "n03"), v.notes.value.items.map { it.id })
        v.loadMoreNotes(); advanceUntilIdle()
        assertEquals(6, v.notes.value.items.size)
        assertEquals(Cursor(997L, "n03"), data.calls.last().first)

        v.toggleChip(NoteChip.MY_NOTES); advanceUntilIdle()
        assertEquals(WorkNoteKind.MY_NOTES, data.calls.last().third.kind)
        assertNull(data.calls.last().first)
        assertEquals(3, v.notes.value.items.size)
    }

    @Test fun `clearing filters goes back to everything`() = runTest(dispatcher) {
        val v = vm(); v.select(WorkSegment.NOTES)
        v.toggleChip(NoteChip.HAS_RECORDING); v.setProject("x"); v.clearFilters(); advanceUntilIdle()
        assertFalse(v.query.value.isFiltered)
    }

    @Test fun `search waits for a pause in typing and pages by rank`() = runTest(dispatcher) {
        val data = FakeData(); val v = vm(data); v.select(WorkSegment.NOTES); advanceUntilIdle()
        v.setSearch("q"); v.setSearch("qu"); v.setSearch("quo")
        advanceTimeBy(50); advanceUntilIdle().also { }
        advanceTimeBy(200); advanceUntilIdle()
        assertEquals(listOf("quo" to 0), data.searches)
        assertEquals(2, v.notes.value.items.size)
    }

    // ---------------------------------------------------------------- row actions

    @Test fun `deleting removes the note, offers undo and undo puts it back where it was`() = runTest(dispatcher) {
        val data = FakeData(); val v = vm(data); v.select(WorkSegment.NOTES); advanceUntilIdle()
        val events = mutableListOf<WorkPageEvent>()
        val job = backgroundScope.launch(dispatcher) { v.events.collect { events += it } }.also { advanceUntilIdle() }
        val second = v.notes.value.items[1]
        v.delete(listOf(second)); advanceUntilIdle()
        assertEquals(listOf("n01", "n03"), v.notes.value.items.map { it.id })
        val deleted = events.single() as WorkPageEvent.Deleted
        v.undoDelete(deleted.notes); advanceUntilIdle()
        assertEquals(listOf("n01", "n02", "n03"), v.notes.value.items.map { it.id })
        assertEquals(listOf("n02"), data.restored)
        job.cancel()
    }

    @Test fun `pinning updates the row in place without reloading`() = runTest(dispatcher) {
        val data = FakeData(); val v = vm(data); v.select(WorkSegment.NOTES); advanceUntilIdle()
        val calls = data.calls.size
        v.togglePin(v.notes.value.items[0]); advanceUntilIdle()
        assertTrue(v.notes.value.items[0].pinned)
        assertEquals(calls, data.calls.size)
    }

    @Test fun `moving a note out of the filtered project drops it from the list`() = runTest(dispatcher) {
        val v = vm(); v.select(WorkSegment.NOTES); v.setProject("nb1"); advanceUntilIdle()
        val first = v.notes.value.items[0]
        v.move(first, "nb2"); advanceUntilIdle()
        assertTrue(v.notes.value.items.none { it.id == first.id })
    }
}
