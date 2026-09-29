package com.example.core.tasks

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.RecordingType
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A person's page: their prayer requests, answered prayers and testimonies are found through the notes about them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonFaithMemoryTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository
    private lateinit var tasks: TaskRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        notes = NoteRepository(ctx, db)
        tasks = TaskRepository(db.taskDao(), db.peopleDao())
    }

    @After fun tearDown() = db.close()

    @Test fun requestsAnsweredPrayersAndTestimoniesGatherOnThePerson() = runBlocking {
        val mary = tasks.savePerson("Mary", "Friend")
        val open = notes.createNote(RecordingType.PRAYER_REQUEST, "Mary's surgery")
        val other = notes.createNote(RecordingType.PRAYER_REQUEST, "Mary's job")
        tasks.linkNote(open.id, mary.id); tasks.linkNote(other.id, mary.id)
        notes.markAnswered(other.id)
        val testimony = notes.startTestimony(other.id)

        val onMary = tasks.observeNotesFor(mary.id).first()
        assertEquals(3, onMary.size)
        assertEquals(listOf("Mary's surgery"), onMary.filter { it.workflow == "PRAYER_REQUEST" && !it.answered }.map { it.title })
        assertEquals(listOf("Mary's job"), onMary.filter { it.workflow == "PRAYER_REQUEST" && it.answered }.map { it.title })
        // The testimony inherited who the request was for.
        assertTrue(onMary.any { it.id == testimony.id && it.workflow == "TESTIMONY" })
    }

    @Test fun trashedNotesLeaveThePersonsPage() = runBlocking {
        val p = tasks.savePerson("John")
        val n = notes.createNote(RecordingType.PRAYER_REQUEST, "For John")
        tasks.linkNote(n.id, p.id)
        notes.moveToTrash(n.id)
        assertTrue(tasks.observeNotesFor(p.id).first().isEmpty())
    }

    @Test fun ensurePeopleAddsPrayerListNamesOnceWithoutDuplicates() = runBlocking {
        tasks.ensurePeople(listOf("Ann", " Ben ", "ann", ""))
        assertEquals(listOf("Ann", "Ben"), tasks.allPeople().map { it.name })
    }
}
