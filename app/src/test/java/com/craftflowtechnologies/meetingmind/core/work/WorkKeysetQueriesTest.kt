package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.notes.KeysetPager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The keyset pages behind the Work page's Notes and Tasks segments (W-1), over ~1000 seeded notes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkKeysetQueriesTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var repo: WorkRepository

    private data class Seed(val i: Int, val id: String, val title: String, val workflow: String, val notebookId: String?, val updatedAt: Long, val createdAt: Long, val eventDate: Long?, val inScope: Boolean, val hasTask: Boolean, val hasRecording: Boolean)

    private val seeds = (0 until 1000).map { i ->
        val workflow = when (i % 3) { 0 -> "MEETING"; 1 -> "GENERAL"; else -> "CLIENT_CALL" }
        val notebook = when (i % 3) { 1 -> "nb1"; 2 -> "nb2"; else -> null }
        val outOfScope = i % 10 == 0
        val wf = if (outOfScope) "GENERAL" else workflow
        val nb = if (outOfScope) null else notebook
        val hidden = i % 50 in 1..3
        Seed(i, "n%04d".format(i), (if (i % 2 == 0) "Alpha " else "alpha ") + (i * 37 % 100), wf, nb, 1_000L + i / 7, i.toLong(), if (i % 2 == 0) 5_000L + i / 5 else null,
            inScope = !outOfScope && !hidden, hasTask = i % 11 == 0 || i % 13 == 0, hasRecording = i % 4 == 1 || i % 13 == 0)
    }
    private val inScope = seeds.filter { it.inScope }

    @Before
    fun setup() = runBlocking {
        db = ItemsFixture.database()
        repo = WorkRepository(db)
        db.notebookDao().upsert(NotebookEntity("nb1", "P1", "WORK", null, null, 1, 1, null, 0, kind = "PROJECT"))
        db.notebookDao().upsert(NotebookEntity("nb2", "P2", "WORK", null, null, 1, 1, null, 0, kind = "PROJECT"))
        for (s in seeds) {
            val hidden = s.i % 50
            db.noteDao().upsert(
                NoteEntity(s.id, s.title, s.workflow, s.notebookId, s.createdAt, s.updatedAt, s.eventDate, false, false, "OPEN", null, "{}", if (hidden == 3) 9L else null,
                    if (s.i % 89 == 0) "body mentions zebra here" else "plain", isDraft = hidden == 2, deletedAt = if (hidden == 1) 9L else null)
            )
            if (s.hasRecording) db.meetingDao().insertMeeting(MeetingEntity("m${s.id}", s.title, 1, 1, "LOCAL_RECORDING", null, "READY", 1, "en", null, recordingType = "MEETING", noteId = s.id))
            if (s.i % 11 == 0) db.taskDao().upsert(task("tn${s.id}", null, noteId = s.id))
            if (s.i % 13 == 0) {
                db.meetingDao().insertMeeting(MeetingEntity("mt${s.id}", s.title, 1, 1, "LOCAL_RECORDING", null, "READY", 1, "en", null, recordingType = "MEETING", noteId = s.id))
                db.taskDao().upsert(task("tm${s.id}", null, meetingId = "mt${s.id}"))
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun task(id: String, due: Long?, done: Long? = null, waiting: Boolean = false, noteId: String? = null, meetingId: String? = null, deleted: Long? = null, space: String? = "WORK") =
        TaskEntity(id, id, "", "TASK", due, null, "NONE", done, null, noteId, null, meetingId, null, null, 1, 1, deletedAt = deleted, waitingOn = waiting, space = space)

    private suspend fun allNotes(sort: WorkNoteSort, filters: WorkNoteFilters = WorkNoteFilters(), size: Int = 30): List<NoteEntity> {
        val out = mutableListOf<NoteEntity>()
        var cursor: Cursor? = null
        while (true) {
            val page = repo.workNotesPage(cursor, size, sort, filters)
            assertTrue(page.size <= size)
            out += page
            if (page.size < size) return out
            cursor = repo.workNoteCursor(page.last(), sort)
        }
    }

    private fun assertExactlyOnce(expectedIds: Collection<String>, got: List<NoteEntity>) {
        assertEquals("duplicates", got.size, got.map { it.id }.toSet().size)
        assertEquals(expectedIds.toSet(), got.map { it.id }.toSet())
    }

    @Test
    fun recent_pages_cover_every_in_scope_note_once_in_updatedAt_id_desc_order() = runBlocking {
        val got = allNotes(WorkNoteSort.RECENT)
        assertExactlyOnce(inScope.map { it.id }, got)
        assertEquals(inScope.sortedWith(compareByDescending<Seed> { it.updatedAt }.thenByDescending { it.id }).map { it.id }, got.map { it.id })
        // page sizes that don't divide the total, and size 1 edge, give the same sequence
        assertEquals(got.map { it.id }, allNotes(WorkNoteSort.RECENT, size = 7).map { it.id })
    }

    @Test
    fun meeting_date_sort_puts_undated_notes_last_and_covers_every_note_once() = runBlocking {
        val got = allNotes(WorkNoteSort.MEETING_DATE, size = 33)
        assertExactlyOnce(inScope.map { it.id }, got)
        assertEquals(inScope.sortedWith(compareByDescending<Seed> { it.eventDate ?: 0L }.thenByDescending { it.id }).map { it.id }, got.map { it.id })
        val firstUndated = got.indexOfFirst { it.eventDate == null }
        assertTrue(got.drop(firstUndated).all { it.eventDate == null })
    }

    @Test
    fun title_sort_is_case_insensitive_with_id_tiebreak_and_covers_every_note_once() = runBlocking {
        val got = allNotes(WorkNoteSort.TITLE, size = 25)
        assertExactlyOnce(inScope.map { it.id }, got)
        assertEquals(inScope.sortedWith(compareBy<Seed> { it.title.lowercase() }.thenBy { it.id }).map { it.id }, got.map { it.id })
    }

    @Test
    fun filters_narrow_the_scope_and_still_page_exactly_once() = runBlocking {
        val workTypes = WorkTypeNames.toSet()
        suspend fun check(filters: WorkNoteFilters, pred: (Seed) -> Boolean) {
            val expected = inScope.filter(pred)
            assertTrue("filter matches something", expected.isNotEmpty())
            for (sort in WorkNoteSort.entries) assertExactlyOnce(expected.map { it.id }, allNotes(sort, filters, 20))
        }
        check(WorkNoteFilters(hasOpenTasks = true)) { it.hasTask }
        check(WorkNoteFilters(hasRecording = true)) { it.hasRecording }
        check(WorkNoteFilters(notebookId = "nb2")) { it.notebookId == "nb2" }
        check(WorkNoteFilters(createdSince = 500)) { it.createdAt >= 500 }
        check(WorkNoteFilters(kind = WorkNoteKind.MEETINGS)) { it.workflow in workTypes }
        check(WorkNoteFilters(kind = WorkNoteKind.MY_NOTES)) { it.workflow !in workTypes }
        check(WorkNoteFilters(kind = WorkNoteKind.MY_NOTES, hasOpenTasks = true, createdSince = 300)) { it.workflow !in workTypes && it.hasTask && it.createdAt >= 300 }
    }

    @Test
    fun a_done_or_deleted_task_does_not_count_as_having_tasks() = runBlocking {
        db.noteDao().upsert(NoteEntity("solo", "Solo", "MEETING", null, 1, 1, null, false, false, "OPEN", null, "{}", null, ""))
        db.taskDao().upsert(task("t-done", null, done = 5, noteId = "solo"))
        db.taskDao().upsert(task("t-del", null, noteId = "solo", deleted = 5))
        assertTrue(allNotes(WorkNoteSort.RECENT, WorkNoteFilters(hasOpenTasks = true)).none { it.id == "solo" })
        db.taskDao().upsert(task("t-open", null, noteId = "solo"))
        assertTrue(allNotes(WorkNoteSort.RECENT, WorkNoteFilters(hasOpenTasks = true)).any { it.id == "solo" })
    }

    @Test
    fun the_keyset_pager_drives_the_repository_end_to_end() = runBlocking {
        val pager = KeysetPager(this, 30, { repo.workNoteCursor(it, WorkNoteSort.RECENT) }) { after, limit -> repo.workNotesPage(after, limit) }
        repeat(40) { pager.loadMore(); kotlinx.coroutines.yield(); while (pager.state.value.let { it.isLoadingFirst || it.isAppending }) kotlinx.coroutines.delay(1) }
        assertTrue(pager.state.value.endReached)
        assertExactlyOnce(inScope.map { it.id }, pager.state.value.items)
    }

    @Test
    fun a_note_edited_to_the_top_mid_scroll_never_duplicates_or_skips_older_notes() = runBlocking {
        val first = repo.workNotesPage(null, 30)
        val tail = first.last()
        // Edit a not-yet-seen note, bumping it to the very top, then continue from the cursor.
        val unseen = inScope.first { s -> first.none { it.id == s.id } }
        db.noteDao().upsert(db.noteDao().getById(unseen.id)!!.copy(updatedAt = 99_999))
        val rest = mutableListOf<NoteEntity>(); var c: Cursor? = repo.workNoteCursor(tail, WorkNoteSort.RECENT)
        while (true) { val p = repo.workNotesPage(c, 30); rest += p; if (p.size < 30) break; c = repo.workNoteCursor(p.last(), WorkNoteSort.RECENT) }
        val all = first + rest
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(inScope.size - 1, all.size) // only the bumped note (now above the cursor) is left for the next refresh
    }

    // ---------------------------------------------------------------- tasks

    private suspend fun allTasks(waiting: Boolean, size: Int): List<TaskEntity> {
        val out = mutableListOf<TaskEntity>(); var c: Cursor? = null
        while (true) {
            val p = repo.workTasksPage(c, size, waiting); out += p
            if (p.size < size) return out
            c = repo.workTaskCursor(p.last())
        }
    }

    @Test
    fun open_task_pages_order_by_due_with_undated_last_split_mine_and_waiting_and_skip_done_deleted_and_non_work() = runBlocking {
        val extra = (0 until 300).map { i -> task("x%03d".format(i), if (i % 5 == 0) null else 100L + i / 4, waiting = i % 3 == 0) }
        extra.forEach { db.taskDao().upsert(it) }
        db.taskDao().upsert(task("done", 1, done = 2)); db.taskDao().upsert(task("deleted", 1, deleted = 2)); db.taskDao().upsert(task("personal", 1, space = null))
        for (waiting in listOf(false, true)) {
            val got = allTasks(waiting, 17)
            val expected = (extra + seeds.filter { it.i % 11 == 0 }.map { task("tn${it.id}", null, noteId = it.id) } + seeds.filter { it.i % 13 == 0 }.map { task("tm${it.id}", null) })
                .filter { it.waitingOn == waiting }.distinctBy { it.id }
            assertEquals(got.size, got.map { it.id }.toSet().size)
            assertEquals(expected.map { it.id }.toSet(), got.map { it.id }.toSet())
            assertEquals(got.sortedWith(compareBy<TaskEntity> { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.id }).map { it.id }, got.map { it.id })
            assertTrue(got.none { it.id in setOf("done", "deleted", "personal") })
        }
        assertTrue(allTasks(false, 30).take(3).all { it.dueAt != null })
    }

    // ---------------------------------------------------------------- search

    @Test
    fun work_search_is_scoped_ranks_title_hits_first_and_pages_by_offset() = runBlocking {
        db.noteDao().upsert(NoteEntity("zt", "Zebra planning", "MEETING", null, 1, 1, null, false, false, "OPEN", null, "{}", null, "nothing"))
        db.noteDao().upsert(NoteEntity("zp", "Personal zebra", "GENERAL", null, 1, 5_000, null, false, false, "OPEN", null, "{}", null, "x")) // not Work
        val all = repo.searchWorkNotes("zebra", limit = 100)
        val expectedBody = inScope.filter { it.i % 89 == 0 }.map { it.id }
        assertEquals((expectedBody + "zt").toSet(), all.map { it.id }.toSet())
        assertEquals("zt", all.first().id)
        assertTrue(all.none { it.id == "zp" })
        val a = repo.searchWorkNotes("zebra", limit = 4, offset = 0); val b = repo.searchWorkNotes("zebra", limit = 4, offset = 4)
        assertEquals(all.take(8).map { it.id }, (a + b).map { it.id })
        assertTrue(repo.searchWorkNotes("zeb", limit = 100).isNotEmpty()) // prefix as typed
        assertTrue(repo.searchWorkNotes("   ").isEmpty()); assertTrue(repo.searchWorkNotes("\"*(").isEmpty())
        assertTrue(repo.searchWorkNotes("zebra", limit = 10, offset = SEARCH_MAX_RESULTS).isEmpty())
    }
}
