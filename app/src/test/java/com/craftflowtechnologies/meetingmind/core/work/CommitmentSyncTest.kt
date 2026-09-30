package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ticking a task closes its commitment and the reverse, from either path, without looping. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommitmentSyncTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var work: WorkRepository
    private lateinit var tasks: TaskRepository
    private lateinit var items: ItemRepository
    private lateinit var itemId: String
    private lateinit var taskId: String

    @Before fun setup() = runBlocking {
        db = ItemsFixture.database(); ItemsFixture.seed(db)
        work = WorkRepository(db); items = ItemRepository(db)
        tasks = TaskRepository(db.taskDao(), db.peopleDao(), onTaskDone = { id, done -> items.onTaskDone(id, done) })
        work.confirm("m1")
        val mine = db.itemDao().bySourceFinding("a2")!!
        itemId = mine.id; taskId = mine.taskId!!
    }
    @After fun tearDown() { db.close() }

    private suspend fun status() = db.itemDao().getById(itemId)!!.status
    private suspend fun statusEvents() = db.itemDao().eventsFor(itemId).count { it.type == "STATUS" }

    @Test fun tickingTheTaskInTheTaskScreensClosesTheCommitment() = runBlocking {
        tasks.toggleDone(taskId)
        assertEquals("COMPLETED", status())
        assertNotNull(db.itemDao().getById(itemId)!!.closedAt)
        tasks.toggleDone(taskId)
        assertEquals("OPEN", status())
        assertNull(db.itemDao().getById(itemId)!!.closedAt)
    }

    @Test fun tickingTheTaskInTheWorkSpaceDoesTheSame() = runBlocking {
        work.toggle(taskId)
        assertEquals("COMPLETED", status())
        work.toggle(taskId)
        assertEquals("OPEN", status())
    }

    @Test fun completingTheCommitmentTicksTheTask() = runBlocking {
        items.setStatus(itemId, ItemStatus.COMPLETED)
        assertNotNull(db.taskDao().getById(taskId)!!.doneAt)
        items.setStatus(itemId, ItemStatus.OPEN)
        assertNull(db.taskDao().getById(taskId)!!.doneAt)
    }

    @Test fun eachChangeLogsExactlyOnce() = runBlocking {
        val before = statusEvents()
        tasks.toggleDone(taskId)
        assertEquals(before + 1, statusEvents())
        // Reporting a state it is already in changes and logs nothing.
        items.onTaskDone(taskId, done = true)
        assertEquals(before + 1, statusEvents())
        items.setStatus(itemId, ItemStatus.COMPLETED)
        assertEquals(before + 1, statusEvents())
    }

    @Test fun cancelledCommitmentsAreNotReopenedByATick() = runBlocking {
        items.setStatus(itemId, ItemStatus.CANCELLED)
        tasks.toggleDone(taskId)
        assertEquals("CANCELLED", status())
    }

    @Test fun aCommitmentWithNoTaskIsTickedAsTheItem() = runBlocking {
        val theirs = db.itemDao().bySourceFinding("a1")!!
        work.toggle(theirs.taskId ?: theirs.id)
        assertEquals("COMPLETED", db.itemDao().getById(theirs.id)!!.status)
    }
}
