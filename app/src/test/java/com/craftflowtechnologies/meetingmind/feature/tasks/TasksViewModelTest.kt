package com.craftflowtechnologies.meetingmind.feature.tasks

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.tasks.QuickAddParser
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.flow.flowOf
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Tasks ViewModel against a real in-memory database, with the clock pinned to Tuesday 17 November
 * 2026 at 10:00. Covers the smart views, bulk actions, Plan my day, quick add, repeats and Undo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasksViewModelTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 11, 17)
    private var clockMs: Long = at(today, 10, 0)

    private lateinit var db: MeetMindDatabase
    private lateinit var repo: TaskRepository
    private lateinit var vm: TasksViewModel

    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun dayOf(ms: Long): LocalDate = TaskViews.dayOf(ms, zone)

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        repo = TaskRepository(db.taskDao(), db.peopleDao(), clock = { clockMs }, notebooks = db.notebookDao())
        vm = TasksViewModel(app, repo, flowOf(emptyList<ItemEntity>()), NoCalendar, { _, _ -> }, { clockMs })
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    private suspend fun make(
        title: String,
        day: LocalDate? = null,
        hasTime: Boolean = false,
        listId: String? = null,
        priority: Int = 4,
        waiting: Boolean = false,
        sourceItemId: String? = null,
        personId: String? = null,
        repeatRule: String? = null
    ): Task {
        val dueAt = day?.let { if (hasTime) at(it, 15, 0) else it.atStartOfDay(zone).toInstant().toEpochMilli() }
        return repo.save(
            Task(
                id = "",
                title = title,
                dueAt = dueAt,
                hasTime = hasTime && dueAt != null,
                listId = listId,
                priority = priority,
                waitingOn = waiting,
                sourceItemId = sourceItemId,
                personId = personId,
                repeatRule = repeatRule,
                repeat = QuickAddParser.legacyRepeat(repeatRule)
            )
        )
    }

    @Test
    fun smartViewsSplitTasksByDate() = runBlocking {
        val overdue = make("Update risk register", LocalDate.of(2026, 11, 13))
        val timed = make("Call Mum", today, hasTime = true)
        val anytime = make("Pick up dry cleaning", today)
        val soon = make("Prepare discovery questions", LocalDate.of(2026, 11, 20))
        val waiting = make("Board pack comments", LocalDate.of(2026, 11, 19), waiting = true)
        val urgent = make("Sign the letter", priority = 1)
        val finished = make("Sent timeline")
        repo.toggleDone(finished.id)

        val all = vm.snapshotTasks()
        val lists = vm.snapshotLists()

        assertEquals(setOf(overdue.id, timed.id, anytime.id), vm.rowsFor(SmartView.TODAY, all, lists).map { it.id }.toSet())
        assertEquals(listOf(waiting.id, soon.id), vm.rowsFor(SmartView.UPCOMING, all, lists).map { it.id })
        assertEquals(listOf(waiting.id), vm.rowsFor(SmartView.WAITING, all, lists).map { it.id })
        assertEquals(listOf(urgent.id), vm.rowsFor(SmartView.PRIORITY_1, all, lists).map { it.id })
        assertEquals(listOf(finished.id), vm.rowsFor(SmartView.COMPLETED, all, lists).map { it.id })
        assertTrue(vm.rowsFor(SmartView.INBOX, all, lists).any { it.id == overdue.id })
        assertEquals(listOf(timed.id), TaskViews.scheduledToday(all, today, zone).map { it.id })
        assertEquals(listOf(anytime.id), TaskViews.anytimeToday(all, today, zone).map { it.id })
        // Done today counts toward progress: one finished, against three open for today or earlier.
        assertEquals(1 to 4, TaskViews.dayProgress(all, today, zone))
    }

    @Test
    fun workScopeKeepsOnlyWorkSpaceAndWorkLists() = runBlocking {
        val acme = repo.createList("Acme Integration", "WORK")!!
        val home = repo.createList("Home", "PERSONAL")!!
        val onWork = make("Draft plan", today, listId = acme.id)
        make("Buy bread", today, listId = home.id)
        val all = vm.snapshotTasks()
        val lists = vm.snapshotLists()

        assertEquals(listOf(onWork.id), TaskViews.scoped(all, TaskScope.WORK, lists).map { it.id })
        assertEquals(2, TaskViews.scoped(all, TaskScope.ALL, lists).size)
    }

    @Test
    fun bulkActionsApplyToEverySelectedTaskAndUndoReopens() = runBlocking {
        val acme = repo.createList("Acme Integration", "WORK")!!
        val personal = repo.createList("Errands", "PERSONAL")!!
        val a = make("Draft plan", listId = acme.id)
        val b = make("Book room")
        val ids = listOf(a.id, b.id)

        vm.setPriority(ids, 2)
        assertEquals(2, repo.get(a.id)!!.priority)
        assertEquals(2, repo.get(b.id)!!.priority)

        vm.applyDate(ids, DateChoice(dueAt = at(today.plusDays(1), 15, 0), hasTime = true))
        assertTrue(repo.get(b.id)!!.hasTime)
        assertEquals(LocalDate.of(2026, 11, 18), dayOf(repo.get(b.id)!!.dueAt!!))

        vm.moveTo(listOf(b.id), personal.id)
        assertEquals(personal.id, repo.get(b.id)!!.listId)
        assertEquals("PERSONAL", repo.get(b.id)!!.space)

        vm.setWaitingOn(ids, "Sarah")
        val sarah = repo.findPerson("Sarah")!!
        assertTrue(repo.get(a.id)!!.waitingOn)
        assertEquals(sarah.id, repo.get(a.id)!!.personId)

        assertEquals(2, vm.completeAll(ids))
        assertTrue(repo.get(a.id)!!.done && repo.get(b.id)!!.done)

        vm.undoLast()
        assertFalse(repo.get(a.id)!!.done)
        assertFalse(repo.get(b.id)!!.done)
    }

    @Test
    fun planMyDayWalksCandidatesAndMovesTheChosenOnes() = runBlocking {
        val james = repo.savePerson("James")
        val overdue = make("Update risk register", LocalDate.of(2026, 11, 13))
        val promised = make("Send docs", sourceItemId = "item-1", personId = james.id)
        val soon = make("Prepare questions", LocalDate.of(2026, 11, 18))
        make("Far away", LocalDate.of(2026, 12, 1))

        val session = vm.startPlan()
        assertEquals(listOf(overdue.id, promised.id, soon.id), session.queue)

        vm.decidePlan(PlanChoice.TODAY)
        assertEquals(today, dayOf(repo.get(overdue.id)!!.dueAt!!))

        vm.decidePlan(PlanChoice.NOT_TODAY)
        assertNull(repo.get(promised.id)!!.dueAt)

        vm.decidePlan(PlanChoice.PICK, DateChoice(dueAt = at(LocalDate.of(2026, 11, 19), 9, 0), hasTime = true))
        assertEquals(LocalDate.of(2026, 11, 19), dayOf(repo.get(soon.id)!!.dueAt!!))

        val finished = vm.planState.value!!
        assertTrue(finished.finished)
        assertEquals(1, finished.todayCount)
        assertEquals(1, finished.notTodayCount)
        assertEquals(1, finished.pickedCount)

        vm.endPlan()
        assertNull(vm.planState.value)
    }

    @Test
    fun quickAddReadsTheLineAndFilesItWhereItNames() = runBlocking {
        val acme = repo.createList("Acme Integration", "WORK")!!

        val saved = vm.quickAdd("Send API docs to James tomorrow 3pm #Acme !1 @email")!!
        assertEquals("Send API docs to James", saved.title)
        assertEquals(acme.id, saved.listId)
        assertEquals("WORK", saved.space)
        assertEquals(1, saved.priority)
        assertTrue(saved.hasTime)
        assertEquals(LocalDate.of(2026, 11, 18), dayOf(saved.dueAt!!))
        assertEquals(listOf("email"), repo.labelsFor(saved.id).map { it.name })

        val loose = vm.quickAdd("Buy stamps")!!
        assertNull(loose.listId)
        assertNull(vm.quickAdd("   "))
    }

    @Test
    fun completingARepeatingTaskRollsItForwardAndUndoRestoresIt() = runBlocking {
        val rota = make("Team check-in", today, repeatRule = "FREQ=WEEKLY;BYDAY=TU")

        val after = vm.toggleDone(rota)!!
        assertFalse(after.done)
        assertEquals(LocalDate.of(2026, 11, 24), dayOf(after.dueAt!!))
        assertTrue(vm.undo.value!!.message.startsWith("Done · next one "))

        vm.undoLast()
        assertEquals(today, dayOf(repo.get(rota.id)!!.dueAt!!))
    }

    @Test
    fun completingAPromisedTaskSaysThePromiseClosed() = runBlocking {
        val james = repo.savePerson("James Okafor")
        val promise = make("Send docs", today, sourceItemId = "item-1", personId = james.id)

        vm.toggleDone(promise)

        assertEquals("Done · your promise to James Okafor is closed too", vm.undo.value!!.message)
    }

    @Test
    fun deleteIsUndoable() = runBlocking {
        val t = make("Temporary")

        vm.deleteTasks(listOf(t.id))
        assertNotNull(vm.undo.value)
        assertEquals(0, vm.snapshotTasks().size)

        vm.undoLast()
        assertEquals(listOf(t.id), vm.snapshotTasks().map { it.id })
    }

    @Test
    fun upcomingGroupsDaysInTheWeekAndLaterAfterIt() = runBlocking {
        make("Tomorrow's thing", today.plusDays(1))
        make("Far away", LocalDate.of(2026, 12, 1))
        val all = vm.snapshotTasks()

        val groups = TaskViews.groupUpcoming(all, today, zone)

        assertEquals(listOf<LocalDate?>(today.plusDays(1), null), groups.map { it.first })
    }

    @Test
    fun quickAddFromWorkFilesTheTaskInWorkSoWorkShowsIt() = runBlocking {
        val home = repo.createList("Home", "PERSONAL")!!

        val fromWork = vm.quickAdd("Send the deck", space = "WORK")!!
        assertEquals("WORK", fromWork.space)
        val lists = vm.snapshotLists()
        assertEquals(listOf(fromWork.id), TaskViews.scoped(vm.snapshotTasks(), TaskScope.WORK, lists).map { it.id })

        // A #list's own space wins over the screen's.
        val named = vm.quickAdd("Buy milk #Home", space = "WORK")!!
        assertEquals(home.id, named.listId)
        assertEquals("PERSONAL", named.space)
    }

    @Test
    fun quickAddDatesTheTaskForTheColumnUnlessTheLineNamesADate() = runBlocking {
        val plain = vm.quickAdd("Call Ann", defaultDay = today)!!
        assertEquals(today, dayOf(plain.dueAt!!))
        assertFalse(plain.hasTime)

        val dated = vm.quickAdd("Call Bob tomorrow", defaultDay = today)!!
        assertEquals(today.plusDays(1), dayOf(dated.dueAt!!))
    }

    @Test
    fun theBoardPutsEveryTaskInOneColumn() = runBlocking {
        val undated = make("Someday idea")
        val late = make("Late report", today.minusDays(2))
        val now = make("Today call", today)
        val later = make("Next week review", today.plusDays(5))
        val waiting = make("Contract back from legal", today, waiting = true)
        val finished = make("Old chore")
        vm.toggleDone(finished)

        val columns = TaskBoard.columns(vm.snapshotTasks(), today, zone, clockMs).toMap()

        assertEquals(BoardColumn.entries.toList(), TaskBoard.columns(emptyList(), today, zone, clockMs).map { it.first })
        assertEquals(listOf(undated.id), columns.getValue(BoardColumn.NO_DATE).map { it.id })
        assertEquals(listOf(late.id, now.id), columns.getValue(BoardColumn.TODAY).map { it.id })
        assertEquals(listOf(later.id), columns.getValue(BoardColumn.UPCOMING).map { it.id })
        assertEquals(listOf(waiting.id), columns.getValue(BoardColumn.WAITING).map { it.id })
        assertEquals(listOf(finished.id), columns.getValue(BoardColumn.DONE).map { it.id })
    }

    @Test
    fun finishedTasksLeaveTheBoardAfterAWeek() = runBlocking {
        val old = make("Finished long ago")
        vm.toggleDone(old)
        clockMs += TaskBoard.DONE_WINDOW_MS + 1

        val columns = TaskBoard.columns(vm.snapshotTasks(), today, zone, clockMs).toMap()

        assertTrue(columns.getValue(BoardColumn.DONE).isEmpty())
    }

    @Test
    fun movingACardChangesItsDateOrStatus() = runBlocking {
        val t = make("Move me")
        fun column() = runBlocking { TaskBoard.columnOf(repo.get(t.id)!!, today, zone) }

        vm.moveToColumn(repo.get(t.id)!!, BoardColumn.TODAY)
        assertEquals(BoardColumn.TODAY, column())

        vm.moveToColumn(repo.get(t.id)!!, BoardColumn.UPCOMING)
        assertEquals(today.plusDays(1), dayOf(repo.get(t.id)!!.dueAt!!))

        vm.moveToColumn(repo.get(t.id)!!, BoardColumn.DONE)
        assertEquals(BoardColumn.DONE, column())

        // Leaving Done reopens the task.
        vm.moveToColumn(repo.get(t.id)!!, BoardColumn.TODAY)
        assertFalse(repo.get(t.id)!!.done)
        assertEquals(BoardColumn.TODAY, column())

        vm.moveToColumn(repo.get(t.id)!!, BoardColumn.NO_DATE)
        assertNull(repo.get(t.id)!!.dueAt)
    }

    @Test
    fun movingAWaitingCardOutTakesItBack() = runBlocking {
        val t = make("Waiting on Sam", today, waiting = true)

        vm.moveToColumn(repo.get(t.id)!!, BoardColumn.TODAY)

        assertFalse(repo.get(t.id)!!.waitingOn)
        assertEquals(BoardColumn.TODAY, TaskBoard.columnOf(repo.get(t.id)!!, today, zone))
    }

    private object NoCalendar : TodayCalendar {
        override fun allowed(): Boolean = false
        override suspend fun between(from: Long, to: Long): List<CalendarEvent> = emptyList()
    }
}
