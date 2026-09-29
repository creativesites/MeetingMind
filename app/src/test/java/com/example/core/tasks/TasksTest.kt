package com.example.core.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.RecordingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasksTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var repo: TaskRepository
    private var now = 1_000L
    private var syncs = 0
    private val utc: ZoneId = ZoneOffset.UTC

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MeetMindDatabase::class.java).allowMainThreadQueries().build()
        repo = TaskRepository(db.taskDao(), db.peopleDao(), clock = { now }, onRemindersChanged = { syncs++ })
    }

    @After fun tearDown() = db.close()

    private fun ms(date: LocalDate, hour: Int = 9) = date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

    @Test fun bucketsByDueDate() {
        val today = LocalDate.of(2026, 9, 29)
        fun b(due: LocalDate?, done: Boolean = false) = TaskRules.bucket(Task("t", "x", dueAt = due?.let { ms(it) }, doneAt = if (done) 1 else null), today, utc)
        assertEquals(TaskBucket.OVERDUE, b(today.minusDays(1)))
        assertEquals(TaskBucket.TODAY, b(today))
        assertEquals(TaskBucket.UPCOMING, b(today.plusDays(3)))
        assertEquals(TaskBucket.SOMEDAY, b(null))
        assertEquals(TaskBucket.DONE, b(today, done = true))
    }

    @Test fun repeatingTaskMovesOnInsteadOfClosing() = runBlocking {
        val d = LocalDate.of(2026, 1, 31)
        val t = repo.save(Task("", "Pray for Mary", kind = TaskKind.PRAYER, dueAt = ms(d), remindAt = ms(d, 7), repeat = TaskRepeat.MONTHLY))
        val next = TaskRules.nextOccurrence(t, utc)!!
        assertEquals(ms(LocalDate.of(2026, 2, 28)), next.dueAt)
        assertEquals(ms(LocalDate.of(2026, 2, 28), 7), next.remindAt)
        val toggled = repo.toggleDone(t.id)!!
        assertFalse(toggled.done)
        assertTrue(toggled.dueAt!! > t.dueAt!!)
        assertTrue(syncs >= 2)
    }

    @Test fun oneOffTaskTicksAndUnticks() = runBlocking {
        val t = repo.save(Task("", "Read Romans 8"))
        now = 5_000
        assertEquals(5_000L, repo.toggleDone(t.id)!!.doneAt)
        assertNull(repo.toggleDone(t.id)!!.doneAt)
    }

    @Test fun peopleAreMatchedByExactNameOnly() = runBlocking {
        val mary = repo.savePerson("Mary")
        repo.savePerson("Mary Ann")
        val people = repo.allPeople()
        assertEquals("Mary Ann", TaskRules.personMentioned("Call Mary Ann tomorrow", people)?.name)
        assertEquals(mary.id, TaskRules.personMentioned("visit mary", people)?.id)
        assertNull(TaskRules.personMentioned("Call her back", people))
        assertNull(TaskRules.personMentioned("Summary of Maryland trip", people))
        // Saving the same name again updates rather than duplicates.
        assertEquals(mary.id, repo.savePerson("mary", relationship = "Sister").id)
        assertEquals(2, repo.observePeople().first().size)
    }

    @Test fun deletedTaskCanBeRestored() = runBlocking {
        val t = repo.save(Task("", "Follow up with John", kind = TaskKind.FOLLOW_UP))
        repo.delete(t.id)
        assertTrue(repo.observeTasks().first().isEmpty())
        repo.restore(t.id)
        assertEquals(1, repo.observeTasks().first().size)
    }

    private val notes by lazy { com.example.core.repository.NoteRepository(ApplicationProvider.getApplicationContext<Context>(), db) }

    @Test fun requestsAnsweredPrayersAndTestimoniesGatherOnThePerson() = runBlocking {
        val mary = repo.savePerson("Mary", "Friend")
        val open = notes.createNote(RecordingType.PRAYER_REQUEST, "Mary's surgery")
        val other = notes.createNote(RecordingType.PRAYER_REQUEST, "Mary's job")
        repo.linkNote(open.id, mary.id); repo.linkNote(other.id, mary.id)
        notes.markAnswered(other.id)
        val testimony = notes.startTestimony(other.id)

        val onMary = repo.notesFor(mary.id)
        assertEquals(3, onMary.size)
        assertEquals(listOf("Mary's surgery"), onMary.filter { it.workflow == "PRAYER_REQUEST" && !it.answered }.map { it.title })
        assertEquals(listOf("Mary's job"), onMary.filter { it.workflow == "PRAYER_REQUEST" && it.answered }.map { it.title })
        // The testimony inherited who the request was for.
        assertTrue(onMary.any { it.id == testimony.id && it.workflow == "TESTIMONY" })
    }

    @Test fun trashedNotesLeaveThePersonsPage() = runBlocking {
        val p = repo.savePerson("John")
        val n = notes.createNote(RecordingType.PRAYER_REQUEST, "For John")
        repo.linkNote(n.id, p.id)
        notes.moveToTrash(n.id)
        assertTrue(repo.notesFor(p.id).isEmpty())
    }

    @Test fun ensurePeopleAddsPrayerListNamesOnceWithoutDuplicates() = runBlocking {
        repo.ensurePeople(listOf("Ann", " Ben ", "ann", ""))
        assertEquals(listOf("Ann", "Ben"), repo.allPeople().map { it.name })
    }
}
