package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The projection, limited and grouped Work queries that replaced whole-table flows (S-6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkPerfQueriesTest {
    private lateinit var db: MeetMindDatabase

    @Before
    fun setup() {
        db = ItemsFixture.database()
        ItemsFixture.seed(db)
    }

    @After
    fun tearDown() = db.close()

    private fun task(id: String, due: Long?, done: Long? = null, meetingId: String? = null, waiting: Boolean = false) =
        TaskEntity(id, id, "", "TASK", due, null, "NONE", done, null, null, null, meetingId, null, null, 1, 1, waitingOn = waiting, space = "WORK")

    @Test
    fun referenced_meeting_titles_include_only_meetings_a_task_points_at() = runBlocking {
        db.taskDao().upsert(task("t1", 5, meetingId = "m2"))
        val titles = db.workDao().observeReferencedMeetingTitles().first()
        assertEquals(listOf("m2" to "Acme review"), titles.map { it.id to it.title })
    }

    @Test
    fun open_work_tasks_skip_done_order_by_due_and_respect_the_limit() = runBlocking {
        db.taskDao().upsert(task("late", 300)); db.taskDao().upsert(task("soon", 100)); db.taskDao().upsert(task("none", null))
        db.taskDao().upsert(task("done", 50, done = 60))
        val all = db.workDao().observeOpenWorkTasks(WorkTypeNames, 10).first().map { it.id }
        assertEquals(listOf("soon", "late", "none"), all)
        assertEquals(listOf("soon", "late"), db.workDao().observeOpenWorkTasks(WorkTypeNames, 2).first().map { it.id })
    }

    @Test
    fun open_my_task_count_excludes_done_and_waiting_on() = runBlocking {
        db.taskDao().upsert(task("a", 1)); db.taskDao().upsert(task("b", 2, waiting = true)); db.taskDao().upsert(task("c", 3, done = 4))
        assertEquals(1, db.workDao().observeOpenMyWorkTaskCount(WorkTypeNames).first())
    }

    @Test
    fun note_counts_come_back_grouped_in_one_query() = runBlocking {
        db.noteDao().upsert(NoteEntity("n3", "Another", "MEETING", "nb", 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        val counts = db.workDao().observeNoteCountsByNotebook().first().associate { it.notebookId to it.count }
        assertEquals(2, counts["nb"])
        assertFalse(counts.containsKey("missing"))
    }

    @Test
    fun ready_meetings_between_filters_by_window_and_status() = runBlocking {
        assertEquals(listOf("m2"), db.workDao().readyMeetingsBetween(1_500, 3_000).map { it.id })
        assertEquals(2, db.workDao().readyMeetingsBetween(0, 3_000).size)
    }

    @Test
    fun people_projections_drop_self() = runBlocking {
        db.peopleDao().upsert(PersonEntity("me", "Me", null, "", 1, 1, isSelf = true))
        assertTrue(db.workDao().otherPeople().none { it.isSelf })
        assertTrue(db.workDao().peopleNames().any { it.id == "me" })
    }
}
