package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KanbanMoveTest {
    private lateinit var f: PulseFixture
    private lateinit var items: ItemRepository

    @Before
    fun setup() {
        f = PulseFixture()
        items = f.items
    }

    @After
    fun tearDown() {
        f.db.close()
    }

    @Test
    fun movingItemToDoneRecordsStatusEventInLog() = runBlocking {
        // Create an open commitment item
        val item = f.mine("Implement feature", status = ItemStatus.OPEN)

        // Move to Done via repository (same code path as Kanban move)
        items.setStatus(item.id, ItemStatus.COMPLETED)

        val updated = items.get(item.id)
        assertNotNull(updated)
        assertEquals(ItemStatus.COMPLETED.name, updated!!.status)
        assertNotNull(updated.closedAt)

        // Verify transaction log
        val events = items.events(item.id)
        assertTrue(events.any { it.type == "STATUS" })
        val statusEvent = events.first { it.type == "STATUS" }
        assertTrue(statusEvent.beforeJson!!.contains("OPEN"))
        assertTrue(statusEvent.afterJson!!.contains("COMPLETED"))
    }

    @Test
    fun movingLinkedItemToDoneUpdatesUnderlyingTask() = runBlocking {
        // 1. Create a task in DB
        val taskId = "task_sync_1"
        f.db.taskDao().upsert(
            TaskEntity(
                id = taskId,
                title = "Backend migration",
                notes = "",
                kind = "TASK",
                dueAt = f.today + f.day,
                remindAt = null,
                repeat = "NEVER",
                doneAt = null,
                personId = null,
                noteId = "n1",
                blockId = null,
                meetingId = null,
                startMs = null,
                scripture = null,
                createdAt = f.now,
                updatedAt = f.now
            )
        )

        // 2. Create commitment item linked to this task
        val item = f.mine("Backend migration item", taskId = taskId, status = ItemStatus.OPEN)

        // 3. Complete the item
        items.setStatus(item.id, ItemStatus.COMPLETED)

        // 4. Verify task doneAt is updated
        val updatedTask = f.db.taskDao().getById(taskId)
        assertNotNull(updatedTask)
        assertNotNull(updatedTask!!.doneAt)

        // 5. Reopening item clears task doneAt
        items.setStatus(item.id, ItemStatus.OPEN)
        val reopenedTask = f.db.taskDao().getById(taskId)
        assertNotNull(reopenedTask)
        assertNull(reopenedTask!!.doneAt)
    }

    @Test
    fun directTaskCardMoveUpdatesDoneTimestamp() = runBlocking {
        val taskId = "task_standalone_1"
        val task = TaskEntity(
            id = taskId,
            title = "Design review",
            notes = "",
            kind = "TASK",
            dueAt = f.today + 2 * f.day,
            remindAt = null,
            repeat = "NEVER",
            doneAt = null,
            personId = null,
            noteId = "n1",
            blockId = null,
            meetingId = null,
            startMs = null,
            scripture = null,
            createdAt = f.now,
            updatedAt = f.now
        )
        f.db.taskDao().upsert(task)

        // Simulate Kanban move to DONE for raw task card
        val doneTimestamp = f.now + 1_000L
        f.db.taskDao().setDone(taskId, doneTimestamp, f.now + 1_000L)

        val completedTask = f.db.taskDao().getById(taskId)
        assertNotNull(completedTask)
        assertEquals(doneTimestamp, completedTask!!.doneAt)

        // Simulate move back to TO_DO
        f.db.taskDao().setDone(taskId, null, f.now + 2_000L)
        val openTask = f.db.taskDao().getById(taskId)
        assertNotNull(openTask)
        assertNull(openTask!!.doneAt)
    }
}
