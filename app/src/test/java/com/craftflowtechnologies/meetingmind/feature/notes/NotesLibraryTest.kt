package com.craftflowtechnologies.meetingmind.feature.notes

import androidx.lifecycle.SavedStateHandle
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.notes.LibraryChip
import com.craftflowtechnologies.meetingmind.core.notes.LibraryData
import com.craftflowtechnologies.meetingmind.core.notes.LibraryItem
import com.craftflowtechnologies.meetingmind.core.notes.LibraryQuery
import com.craftflowtechnologies.meetingmind.core.notes.NoteSort
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesLibraryTest {

    private class Fake(count: Int = 10) : LibraryData {
        val items = (1..count).map { i ->
            LibraryItem(
                Note("n%02d".format(i), "Note $i", RecordingType.GENERAL, null, 0, 1000L - i, null, false, false, NoteStatus.OPEN, null, emptyMap()),
                "", null, false, NoteTranscriptStatus.None, Cursor(1000L - i, "n%02d".format(i))
            )
        }
        val calls = mutableListOf<Pair<LibraryQuery, Cursor?>>()
        val changes = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        override suspend fun page(q: LibraryQuery, after: Cursor?, limit: Int): List<LibraryItem> {
            calls += q to after
            return items.filter { after == null || it.cursor.longKey < after.longKey }.take(limit)
        }
        override fun changes(): Flow<Int> = changes
    }

    private fun TestScope.library(data: Fake = Fake(), handle: SavedStateHandle = SavedStateHandle(), size: Int = 3) =
        NotesLibrary(backgroundScope, data, saved = handle, pageSize = size, searchDebounceMs = 100)

    @Test fun `loads one page, then the next as the list asks`() = runTest {
        val data = Fake()
        val lib = library(data)
        runCurrent()
        assertEquals(listOf("n01", "n02", "n03"), lib.items.value.items.map { it.id })
        assertFalse(lib.items.value.endReached)
        lib.loadMore(); runCurrent()
        assertEquals(6, lib.items.value.items.size)
        assertEquals(data.items[2].cursor, data.calls.last().second)
        repeat(3) { lib.loadMore(); runCurrent() }
        assertEquals(10, lib.items.value.items.size)
        assertTrue(lib.items.value.endReached)
        lib.loadMore(); runCurrent()
        assertEquals("nothing more is asked for at the end", 4, data.calls.size)
    }

    @Test fun `a chip reloads from the top with the chip in the query`() = runTest {
        val data = Fake()
        val lib = library(data)
        runCurrent()
        lib.toggleChip(LibraryChip.WORK); runCurrent()
        assertEquals(setOf(LibraryChip.WORK), data.calls.last().first.chips)
        assertEquals(null, data.calls.last().second)
        lib.toggleChip(LibraryChip.PINNED); runCurrent()
        assertEquals(setOf(LibraryChip.WORK, LibraryChip.PINNED), lib.query.value.chips)
        lib.toggleChip(LibraryChip.WORK); runCurrent()
        assertEquals(setOf(LibraryChip.PINNED), lib.query.value.chips)
        lib.showAll(); runCurrent()
        assertTrue(lib.query.value.chips.isEmpty())
    }

    @Test fun `chips, types, sort, search and view come back from the saved state`() = runTest {
        val handle = SavedStateHandle()
        val first = library(handle = handle)
        runCurrent()
        first.toggleChip(LibraryChip.FAITH); first.toggleChip(LibraryChip.RECORDINGS)
        first.toggleType(RecordingType.SERMON)
        first.setSort(NoteSort.TITLE)
        first.setSearch("grace"); advanceTimeBy(150); runCurrent()
        first.setView(LibraryView.FOLDERS)
        // The process died: a new view model, the same saved state.
        val second = library(handle = handle)
        runCurrent()
        assertEquals(setOf(LibraryChip.FAITH, LibraryChip.RECORDINGS), second.query.value.chips)
        assertEquals(setOf(RecordingType.SERMON), second.query.value.types)
        assertEquals(NoteSort.TITLE, second.query.value.sort)
        assertEquals("grace", second.query.value.search)
        assertEquals("grace", second.searchText.value)
        assertEquals(LibraryView.FOLDERS, second.view.value)
    }

    @Test fun `the folder view is remembered`() = runTest {
        val handle = SavedStateHandle()
        library(handle = handle).setView(LibraryView.FOLDERS)
        assertEquals(LibraryView.FOLDERS, library(handle = handle).view.value)
    }

    @Test fun `typing waits for a pause, then searches once`() = runTest {
        val data = Fake()
        val lib = library(data)
        runCurrent()
        val before = data.calls.size
        lib.setSearch("g"); advanceTimeBy(60)
        lib.setSearch("gr"); advanceTimeBy(60)
        lib.setSearch("gra"); advanceTimeBy(60)
        runCurrent()
        assertEquals("the field follows every key", "gra", lib.searchText.value)
        assertEquals("the list has not moved yet", before, data.calls.size)
        advanceTimeBy(100); runCurrent()
        assertEquals(before + 1, data.calls.size)
        assertEquals("gra", data.calls.last().first.search)
        assertTrue(lib.query.value.isSearching)
    }

    @Test fun `clearing the search shows the list at once`() = runTest {
        val data = Fake()
        val lib = library(data)
        runCurrent()
        lib.setSearch("gra"); advanceTimeBy(150); runCurrent()
        lib.setSearch(""); runCurrent()
        assertEquals("", data.calls.last().first.search)
    }

    @Test fun `a change to any note reloads the first page`() = runTest {
        val data = Fake()
        val lib = library(data)
        runCurrent()
        lib.loadMore(); runCurrent()
        val calls = data.calls.size
        data.changes.emit(1)   // the first emission of a Room flow is the current state; reloads follow later ones
        advanceTimeBy(300); runCurrent()
        data.changes.emit(2); advanceTimeBy(300); runCurrent()
        assertTrue(data.calls.size > calls)
        assertEquals(null, data.calls.last().second)
    }

    @Test fun `locking and unlocking the Faith lock relists`() = runTest {
        val data = Fake()
        val lib = library(data)
        runCurrent()
        lib.setHideLocked(true); runCurrent()
        assertTrue(data.calls.last().first.hideLocked)
        val n = data.calls.size
        lib.setHideLocked(true); runCurrent()
        assertEquals("no change, no reload", n, data.calls.size)
        lib.setHideLocked(false); runCurrent()
        assertFalse(data.calls.last().first.hideLocked)
    }

    @Test fun `searching in transcripts is a query change that sticks`() = runTest {
        val data = Fake()
        val handle = SavedStateHandle()
        val lib = library(data, handle)
        runCurrent()
        lib.setSearch("budget"); advanceTimeBy(150); runCurrent()
        lib.setIncludeTranscripts(true); runCurrent()
        assertTrue(data.calls.last().first.includeTranscripts)
        lib.clearFilters(); runCurrent()
        assertEquals("", lib.searchText.value)
        assertFalse(lib.query.value.includeTranscripts)
    }

    @Test fun `a failing load keeps what was shown and can retry`() = runTest {
        var fail = false
        val base = Fake()
        val data = object : LibraryData by base {
            override suspend fun page(q: LibraryQuery, after: Cursor?, limit: Int): List<LibraryItem> {
                if (fail) throw IllegalStateException("disk")
                return base.page(q, after, limit)
            }
        }
        val lib = NotesLibrary(backgroundScope, data, saved = SavedStateHandle(), pageSize = 3)
        runCurrent()
        fail = true
        lib.loadMore(); runCurrent()
        assertEquals(3, lib.items.value.items.size)
        assertTrue(lib.items.value.error != null)
        fail = false
        lib.retry(); runCurrent()
        assertEquals(6, lib.items.value.items.size)
    }
}
