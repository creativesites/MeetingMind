package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewQueriesTest {
    private lateinit var f: PulseFixture
    private lateinit var queries: ViewQueries

    @Before
    fun setup() {
        f = PulseFixture()
        queries = ViewQueries(f.db) { f.now }
    }

    @After
    fun tearDown() {
        f.db.close()
    }

    @Test
    fun projectKanbanPartitionsCardsByStatusAndOverdue() = runBlocking {
        // Create items in project "nb"
        // 1. To Do item (Open, future date)
        f.mine("Future task", due = f.today + 3 * f.day, status = ItemStatus.OPEN)
        // 2. In Progress item (Active status)
        f.mine("Active sprint item", status = ItemStatus.ACTIVE)
        // 3. Overdue item (Open status, but past due date) -> should be sorted into In Progress
        f.mine("Overdue item", due = f.today - 2 * f.day, status = ItemStatus.OPEN)
        // 4. Completed item -> should be in Done
        f.mine("Finished feature", status = ItemStatus.COMPLETED)

        val kanban = queries.projectKanban("nb")

        assertEquals(1, kanban.toDo.size)
        assertEquals("Future task", kanban.toDo.first().title)

        assertEquals(2, kanban.inProgress.size)
        val inProgressTitles = kanban.inProgress.map { it.title }.toSet()
        assertTrue(inProgressTitles.contains("Active sprint item"))
        assertTrue(inProgressTitles.contains("Overdue item"))

        assertEquals(1, kanban.done.size)
        assertEquals("Finished feature", kanban.done.first().title)
    }

    @Test
    fun projectKanbanIncludesUnlinkedTasks() = runBlocking {
        // Insert a raw task in project "nb"
        f.db.taskDao().insertTask(
            TaskEntity(
                id = "task_raw_1",
                title = "Raw project task",
                notebookId = "nb",
                dueAt = f.today + f.day,
                createdAt = f.now
            )
        )

        val kanban = queries.projectKanban("nb")
        val allCards = kanban.toDo + kanban.inProgress + kanban.done
        val rawTaskCard = allCards.find { it.id == "task_raw_1" }

        assertTrue(rawTaskCard != null)
        assertTrue(rawTaskCard!!.isTask)
        assertEquals("Raw project task", rawTaskCard.title)
    }

    @Test
    fun projectTimelinePartitionsSectionsCorrectly() = runBlocking {
        // Meeting is already inserted 5 days ago in PulseFixture ("m1") -> Past Activity
        // Add Overdue item
        f.mine("Overdue deliverable", due = f.today - f.day, status = ItemStatus.OPEN)
        // Add Today item
        f.mine("Deploy today", due = f.today + 2 * 3600_000L, status = ItemStatus.OPEN)
        // Add This Week item
        f.mine("Midweek check", due = f.today + 3 * f.day, status = ItemStatus.OPEN)
        // Add Upcoming (later) item
        f.mine("Next month milestone", due = f.today + 14 * f.day, status = ItemStatus.OPEN)

        val timeline = queries.projectTimeline("nb")
        val sectionTitles = timeline.map { it.title }

        assertTrue(sectionTitles.contains("Overdue"))
        assertTrue(sectionTitles.contains("Today"))
        assertTrue(sectionTitles.contains("This Week"))
        assertTrue(sectionTitles.contains("Upcoming"))
        assertTrue(sectionTitles.contains("Past Activity"))

        val overdueSection = timeline.find { it.title == "Overdue" }!!
        assertEquals("Overdue deliverable", overdueSection.items.first().title)

        val todaySection = timeline.find { it.title == "Today" }!!
        assertEquals("Deploy today", todaySection.items.first().title)
    }

    @Test
    fun risksQuerySortsHighSeverityFirst() = runBlocking {
        // Add medium and high risks
        f.items.create(
            ItemEntity(
                id = "r_med",
                kind = ItemKind.RISK.name,
                status = ItemStatus.OPEN.name,
                text = "Medium risk delay",
                severity = "MEDIUM",
                projectId = "nb",
                createdAt = f.now - 2 * f.day,
                updatedAt = f.now - 2 * f.day
            )
        )
        f.items.create(
            ItemEntity(
                id = "r_high",
                kind = ItemKind.RISK.name,
                status = ItemStatus.OPEN.name,
                text = "Critical security risk",
                severity = "HIGH",
                projectId = "nb",
                createdAt = f.now - f.day,
                updatedAt = f.now - f.day
            )
        )

        val risks = queries.risks("nb")
        assertEquals(2, risks.size)
        assertEquals("Critical security risk", risks[0].title)
        assertEquals("HIGH", risks[0].severity)
        assertEquals("Medium risk delay", risks[1].title)
        assertEquals("MEDIUM", risks[1].severity)
    }
}
