package com.craftflowtechnologies.meetingmind.core.practice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.PracticeDayEntity
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** Practices on an in-memory database. The clock is pinned to Monday 16 November 2026, 10:00 UTC. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PracticeRepositoryTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var repo: PracticeRepository
    private val utc: ZoneId = ZoneId.of("UTC")
    private var now = at(LocalDate.of(2026, 11, 16), 10)
    private val originalZone = TimeZone.getDefault()
    private val monday = LocalDate.of(2026, 11, 16)
    private val day = 24L * 60 * 60 * 1000

    @Before fun setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PracticeRepository(db, clock = { now }, zone = utc)
    }

    @After fun tearDown() {
        db.close()
        TimeZone.setDefault(originalZone)
    }

    private fun at(date: LocalDate, hour: Int): Long = date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(utc).toLocalDate()

    @Test fun plantingMakesARepeatingPracticeTask() = runBlocking {
        val p = repo.plant("Pray before email", Rhythm.DAILY, remindMinutes = 7 * 60, checkInWeeks = 2, sourceNoteId = null, sourceTitle = "Sermon 3")

        val task = db.taskDao().getById(p.taskId!!)!!
        assertEquals("PRACTICE", task.kind)
        assertEquals(p.id, task.sourceItemId)
        assertEquals("FREQ=DAILY", task.repeatRule)
        assertEquals("DAILY", task.repeat)
        assertEquals("PERSONAL", task.space)
        assertTrue(task.hasTime)
        assertEquals(at(monday, 7), task.dueAt)
        assertEquals(task.dueAt, task.remindAt)
        assertEquals(now + 14 * day, p.checkInAt)
        assertEquals(PracticeState.GROWING, p.state)
        assertEquals("Sermon 3", p.sourceTitle)
    }

    @Test fun aPracticeWithNoEndHasNoCheckIn() = runBlocking {
        val p = repo.plant("Read a psalm", Rhythm.DAILY, remindMinutes = null, checkInWeeks = 0, sourceNoteId = null, sourceTitle = null)
        assertNull(p.checkInAt)
        assertEquals(false, db.taskDao().getById(p.taskId!!)!!.hasTime)
    }

    @Test fun firstDueIsTheNextScheduledDay() = runBlocking {
        now = at(LocalDate.of(2026, 11, 21), 10) // Saturday
        val p = repo.plant("Walk", Rhythm.THREE_A_WEEK, remindMinutes = null, checkInWeeks = 0, sourceNoteId = null, sourceTitle = null)
        assertEquals(LocalDate.of(2026, 11, 23), dateOf(db.taskDao().getById(p.taskId!!)!!.dueAt!!))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", db.taskDao().getById(p.taskId!!)!!.repeatRule)
    }

    @Test fun gardenShowsGrowingThenBloomedOldestFirstAndNotLetGo() = runBlocking {
        val bloomedFirst = repo.plant("Old one", Rhythm.DAILY, null, 0, null, null)
        now += day
        val growingA = repo.plant("A", Rhythm.DAILY, null, 0, null, null)
        now += day
        val growingB = repo.plant("B", Rhythm.DAILY, null, 0, null, null)
        now += day
        val gone = repo.plant("Gone", Rhythm.DAILY, null, 0, null, null)
        repo.bloom(bloomedFirst.id)
        repo.letGo(gone.id)

        val garden = repo.observeGarden().first()
        assertEquals(listOf(growingA.id, growingB.id, bloomedFirst.id), garden.map { it.id })
        assertEquals(PracticeState.BLOOMED, garden.last().state)
    }

    @Test fun tendingTicksTheTaskOnceAndLogsOneDay() = runBlocking {
        val p = repo.plant("Pray", Rhythm.DAILY, null, 0, null, null)
        repo.tendToday(p.id)
        // The task moved on to tomorrow, so a second tend today is not due and logs nothing new.
        repo.tendToday(p.id)

        val after = repo.get(p.id)!!
        assertEquals(setOf(monday), after.days)
        assertEquals(monday.plusDays(1), dateOf(db.taskDao().getById(p.taskId!!)!!.dueAt!!))
    }

    @Test fun tendingWhenTheTaskIsNotDueLogsTodayAndLeavesTheTaskAlone() = runBlocking {
        now = at(LocalDate.of(2026, 11, 21), 10) // Saturday: three a week plants on Monday 23
        val p = repo.plant("Walk", Rhythm.THREE_A_WEEK, null, 0, null, null)
        val dueBefore = db.taskDao().getById(p.taskId!!)!!.dueAt

        now = at(LocalDate.of(2026, 11, 22), 10) // Sunday, nothing due yet
        repo.tendToday(p.id)

        assertEquals(setOf(LocalDate.of(2026, 11, 22)), repo.get(p.id)!!.days)
        assertEquals(dueBefore, db.taskDao().getById(p.taskId!!)!!.dueAt)
    }

    @Test fun completingAPracticeTaskThroughToggleDoneLogsOneDayEvenWhenTickedTwice() = runBlocking {
        val tasks = TaskRepository(db.taskDao(), db.peopleDao(), clock = { now })
        val oneOff = tasks.save(Task("", "Sit quietly", kind = TaskKind.PRACTICE, dueAt = at(monday, 9), sourceItemId = "practice_quiet"))

        tasks.toggleDone(oneOff.id) // completes: logs today
        tasks.toggleDone(oneOff.id) // reopens: logs nothing new
        tasks.toggleDone(oneOff.id) // completes again the same day: ignored by the key

        val days = db.practiceDao().daysFor("practice_quiet")
        assertEquals(listOf("2026-11-16"), days.map { it.day })
    }

    @Test fun aRepeatingPracticeTickedTwiceLogsOneDay() = runBlocking {
        val tasks = TaskRepository(db.taskDao(), db.peopleDao(), clock = { now })
        val repeating = tasks.save(
            Task("", "Repeat", kind = TaskKind.PRACTICE, dueAt = at(monday, 9), sourceItemId = "practice_rep", repeatRule = "FREQ=DAILY")
        )
        tasks.toggleDone(repeating.id)
        tasks.toggleDone(repeating.id)
        assertEquals(1, db.practiceDao().daysFor("practice_rep").size)
    }

    @Test fun untendingRemovesOnlyTodaysDay() = runBlocking {
        val p = repo.plant("Pray", Rhythm.DAILY, null, 0, null, null)
        db.taskDao().insertPracticeDay(PracticeDayEntity(p.id, "2026-11-10", now))
        repo.tendToday(p.id)
        assertEquals(setOf(LocalDate.of(2026, 11, 10), monday), repo.get(p.id)!!.days)

        repo.untendToday(p.id)
        assertEquals(setOf(LocalDate.of(2026, 11, 10)), repo.get(p.id)!!.days)
    }

    @Test fun blooming_endsThePracticeAndTakesItsTaskOutButKeepsDays() = runBlocking {
        val p = repo.plant("Pray", Rhythm.DAILY, null, 0, null, null)
        repo.tendToday(p.id)
        repo.bloom(p.id)

        val after = repo.get(p.id)!!
        assertEquals(PracticeState.BLOOMED, after.state)
        assertEquals(now, after.endedAt)
        assertEquals(setOf(monday), after.days)
        assertNotNull(db.taskDao().getById(p.taskId!!)!!.deletedAt)
    }

    @Test fun letGoEndsThePracticeAndLeavesTheGarden() = runBlocking {
        val p = repo.plant("Pray", Rhythm.DAILY, null, 0, null, null)
        repo.tendToday(p.id)
        repo.letGo(p.id)

        assertEquals(PracticeState.LET_GO, repo.get(p.id)!!.state)
        assertEquals(emptyList<Practice>(), repo.observeGarden().first())
        assertNotNull(db.taskDao().getById(p.taskId!!)!!.deletedAt)
    }

    @Test fun keepGoingPushesTheCheckInFromNow() = runBlocking {
        val p = repo.plant("Pray", Rhythm.DAILY, null, 2, null, null)
        now += 3 * day
        repo.keepGoing(p.id)
        assertEquals(now + 14 * day, repo.get(p.id)!!.checkInAt)
    }

    @Test fun adjustChangesTheRhythmAndTheTask() = runBlocking {
        val p = repo.plant("Pray", Rhythm.DAILY, remindMinutes = 7 * 60, checkInWeeks = 0, sourceNoteId = null, sourceTitle = null)
        repo.adjust(p.id, Rhythm.THREE_A_WEEK, remindMinutes = 8 * 60)

        val after = repo.get(p.id)!!
        assertEquals(Rhythm.THREE_A_WEEK, after.rhythm)
        assertEquals(480, after.remindMinutes)
        val task = db.taskDao().getById(p.taskId!!)!!
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", task.repeatRule)
        assertEquals("WEEKLY", task.repeat)
        assertEquals(at(monday, 8), task.dueAt)
        assertEquals(at(monday, 8), task.remindAt)
    }
}
