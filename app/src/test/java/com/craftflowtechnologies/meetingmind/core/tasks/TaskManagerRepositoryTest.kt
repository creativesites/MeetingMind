package com.craftflowtechnologies.meetingmind.core.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The Tasks manager's repository on an in-memory database. Covers the Work data-loss fix (a task
 * keeps Waiting on, its source and its space through save and tick), list and space inheritance,
 * the smart views, search, and sections, labels and subtasks. The clock is pinned to Monday
 * 16 November 2026, 10:00 UTC.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskManagerRepositoryTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var repo: TaskRepository
    private var now = Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
    private var reminderChanges = 0
    private val doneCalls = mutableListOf<Pair<String, Boolean>>()
    private val originalZone = TimeZone.getDefault()
    private val utc: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 11, 16)

    @Before fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TaskRepository(
            db.taskDao(),
            db.peopleDao(),
            clock = { now },
            onRemindersChanged = { reminderChanges++ },
            onTaskDone = { id, done -> doneCalls.add(id to done) }
        )
    }

    @After fun tearDown() {
        db.close()
        TimeZone.setDefault(originalZone)
    }

    private fun ms(date: LocalDate, hour: Int = 9): Long = date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

    /** A notebook row, written the way the app stores one (the entity has no insert helper for it here). */
    private fun insertNotebook(id: String, space: String, kind: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder, kind) " +
                "VALUES ('$id', '$id', '$space', NULL, NULL, 1, 1, NULL, 0, '$kind')"
        )
    }

    @Test fun workTaskKeepsWaitingOnSourceAndSpaceThroughTickAndSave() = runBlocking {
        val saved = repo.save(
            Task("", "Chase the invoice", waitingOn = true, ownerSpeakerId = "speaker-2", sourceItemId = "item-9", space = "WORK")
        )

        val ticked = repo.toggleDone(saved.id)!!
        assertNotNull(ticked.doneAt)
        assertTrue(ticked.waitingOn)
        assertEquals("item-9", ticked.sourceItemId)
        assertEquals("WORK", ticked.space)

        val reopened = repo.toggleDone(saved.id)!!
        assertNull(reopened.doneAt)

        repo.save(reopened.copy(title = "Chase the invoice today"))

        val stored = db.taskDao().getById(saved.id)!!
        assertEquals("Chase the invoice today", stored.title)
        assertTrue(stored.waitingOn)
        assertEquals("speaker-2", stored.ownerSpeakerId)
        assertEquals("item-9", stored.sourceItemId)
        assertEquals("WORK", stored.space)
        assertEquals(listOf(saved.id to true, saved.id to false), doneCalls)
    }

    @Test fun tickingATrashedTaskLeavesItInTheTrash() = runBlocking {
        val saved = repo.save(Task("", "Old idea"))
        repo.delete(saved.id)
        repo.toggleDone(saved.id)
        assertNotNull(db.taskDao().getById(saved.id)!!.deletedAt)
    }

    @Test fun taskWithoutASpaceTakesTheSpaceOfItsList() = runBlocking {
        insertNotebook("list-client", "WORK", "TASK_LIST")

        val filed = repo.save(Task("", "Draft the SOW", listId = "list-client"))
        assertEquals("WORK", filed.space)
        assertEquals("list-client", repo.toggleDone(filed.id)!!.listId)

        val loose = repo.save(Task("", "Buy milk"))
        assertNull(loose.space)
    }

    @Test fun listsAreProjectsAndTaskListsOnly() = runBlocking {
        insertNotebook("proj", "WORK", "PROJECT")
        insertNotebook("tlist", "PERSONAL", "TASK_LIST")
        insertNotebook("plain", "PERSONAL", "NOTEBOOK")

        val ids = repo.observeLists().first().map { it.id }.toSet()
        assertEquals(setOf("proj", "tlist"), ids)
        assertTrue(repo.observeLists().first().single { it.id == "proj" }.isProject)
    }

    @Test fun smartViewsFollowTheDueDateAndList() = runBlocking {
        insertNotebook("list-1", "WORK", "TASK_LIST")
        val overdue = repo.save(Task("", "Overdue", dueAt = ms(today.minusDays(1))))
        val dueToday = repo.save(Task("", "Today", dueAt = ms(today, 15)))
        val tomorrow = repo.save(Task("", "Tomorrow", dueAt = ms(today.plusDays(1))))
        val farAway = repo.save(Task("", "Far away", dueAt = ms(today.plusDays(20))))
        val doneToday = repo.save(Task("", "Done today", dueAt = ms(today), doneAt = 1L))
        val inbox = repo.save(Task("", "Unsorted"))
        val urgent = repo.save(Task("", "Urgent", priority = 1, listId = "list-1"))

        assertEquals(setOf(overdue.id, dueToday.id), repo.observeToday().first().map { it.id }.toSet())
        assertEquals(setOf(tomorrow.id), repo.observeUpcoming().first().map { it.id }.toSet())
        assertTrue(inbox.id in repo.observeInbox().first().map { it.id })
        assertFalse(urgent.id in repo.observeInbox().first().map { it.id })
        assertEquals(listOf(urgent.id), repo.observePriority1().first().map { it.id })
        assertEquals(listOf(doneToday.id), repo.completed().map { it.id })
        assertFalse(repo.observeToday().first().any { it.id == doneToday.id })
    }

    @Test fun moveFilesATaskIntoAListAndBackToTheInbox() = runBlocking {
        insertNotebook("list-2", "PERSONAL", "TASK_LIST")
        val t = repo.save(Task("", "File me"))
        repo.move(t.id, "list-2", null, 2.0)
        assertEquals("list-2", repo.get(t.id)!!.listId)
        assertEquals(2.0, repo.get(t.id)!!.position, 0.0)
        repo.move(t.id, null, null, 0.0)
        assertNull(repo.get(t.id)!!.listId)
    }

    @Test fun searchMatchesTitleAndNotesAsPrefixes() = runBlocking {
        repo.save(Task("", "Renew passport", notes = "bring the old one"))
        repo.save(Task("", "Call plumber"))

        assertEquals(listOf("Renew passport"), repo.search("passp").map { it.title })
        assertEquals(listOf("Renew passport"), repo.search("old").map { it.title })
        assertTrue(repo.search("   ").isEmpty())
        // Quotes and brackets mean something to MATCH; they are dropped, so this still finds the task.
        assertEquals(listOf("Renew passport"), repo.search("\"passport\" (").map { it.title })
    }

    @Test fun sectionsSubtasksAndLabels() = runBlocking {
        insertNotebook("list-3", "WORK", "TASK_LIST")
        val section = repo.saveSection("list-3", "This week")
        assertEquals(1.0, section.position, 0.0)

        val parent = repo.save(Task("", "Proposal", listId = "list-3", sectionId = section.id))
        val sub = repo.addSubtask(parent.id, "Draft outline")!!
        assertEquals(parent.id, sub.parentId)
        assertEquals("list-3", sub.listId)
        assertEquals(section.id, sub.sectionId)

        val labels = repo.setLabels(parent.id, listOf("@email", "Email", "errand"))
        assertEquals(listOf("email", "errand"), labels.map { it.name })
        assertEquals(listOf("email", "errand"), repo.labelsFor(parent.id).map { it.name })
        assertEquals(listOf("email"), repo.setLabels(parent.id, listOf("email")).map { it.name })

        repo.deleteSection(section.id)
        assertNull(repo.get(parent.id)!!.sectionId)
        assertTrue(repo.observeSections("list-3").first().isEmpty())
    }

    @Test fun rescheduleAndPriority() = runBlocking {
        val t = repo.save(Task("", "Call Mary"))
        repo.reschedule(t.id, ms(today.plusDays(2), 0), hasTime = false)
        val moved = repo.get(t.id)!!
        assertEquals(ms(today.plusDays(2), 0), moved.dueAt)
        assertFalse(moved.hasTime)
        repo.setPriority(t.id, 9)
        assertEquals(4, repo.get(t.id)!!.priority)
        repo.setPriority(t.id, 2)
        assertEquals(2, repo.get(t.id)!!.priority)
        assertTrue(reminderChanges >= 1)
    }
}
