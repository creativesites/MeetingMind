package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var repository: LearningRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LearningRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getOrCreateSessionForNote creates session and reuses it`() = runBlocking {
        val now = 1000L
        val note = NoteEntity(
            id = "note_1",
            title = "Enzyme Kinetics",
            workflow = RecordingType.LECTURE.name,
            notebookId = null,
            createdAt = now,
            updatedAt = now,
            eventDate = now,
            pinned = false,
            isPrivate = false,
            status = "OPEN",
            answeredAt = null,
            metadataJson = "{}",
            archivedAt = null,
            plainText = "Enzymes catalyze biochemical reactions."
        )
        db.noteDao().upsert(note)

        val session1 = repository.getOrCreateSessionForNote("note_1", "Biochem 201")
        assertEquals("note_1", session1.noteId)
        assertEquals("Enzyme Kinetics", session1.title)
        assertEquals("Biochem 201", session1.courseName)

        val session2 = repository.getOrCreateSessionForNote("note_1")
        assertEquals(session1.id, session2.id)
    }

    @Test
    fun `createTypedSession creates a Note in LEARNING space and session`() = runBlocking {
        val session = repository.createTypedSession("Neurobiology Lecture", "Neuro 101")
        assertNotNull(session.id)
        assertEquals("Neurobiology Lecture", session.title)
        assertEquals("Neuro 101", session.courseName)

        val note = db.noteDao().getById(session.noteId)
        assertNotNull(note)
        assertEquals(RecordingType.LECTURE.name, note?.workflow)

        val notebook = db.notebookDao().getById(note!!.notebookId!!)
        assertEquals(NotebookSpace.LEARNING.name, notebook?.space)
    }

    @Test
    fun `recordAttempt stores immutable attempt and advances schedule and concept mastery`() = runBlocking {
        val session = repository.createTypedSession("Cell Biology")
        val concept = LearningConcept(
            id = "c_1",
            sessionId = session.id,
            name = "Mitochondria",
            definition = "ATP synthesis site",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.saveConcepts(listOf(concept))

        val activity = LearningActivity(
            id = "a_1",
            sessionId = session.id,
            conceptId = concept.id,
            type = LearningActivityType.RECALL,
            prompt = "What organelle produces ATP?",
            expectedAnswer = "Mitochondria",
            isDiagnostic = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.saveActivities(listOf(activity))

        // Record a correct attempt
        val attempt1 = repository.recordAttempt(
            activityId = "a_1",
            userResponse = "Mitochondria",
            isCorrect = true,
            selfRating = RecallRating.GOOD,
            feedback = "Correctly recalled."
        )
        assertEquals("a_1", attempt1.activityId)
        assertTrue(attempt1.isCorrect)

        // Check concept state updated
        val updatedConcepts = repository.getConcepts(session.id)
        val updatedConcept = updatedConcepts.first { it.id == "c_1" }
        assertEquals(LearningMasteryState.LEARNING, updatedConcept.state)

        // Record an incorrect attempt
        repository.recordAttempt(
            activityId = "a_1",
            userResponse = "Nucleus",
            isCorrect = false,
            selfRating = RecallRating.AGAIN,
            feedback = "Mitochondria, not nucleus."
        )
        val conceptsAfterFail = repository.getConcepts(session.id)
        val failedConcept = conceptsAfterFail.first { it.id == "c_1" }
        assertEquals(LearningMasteryState.NEEDS_REVIEW, failedConcept.state)
    }

    @Test
    fun `daily brief bounds queue and recommends weak concept`() = runBlocking {
        val session = repository.createTypedSession("Organic Chemistry")
        val now = System.currentTimeMillis()

        val concept = LearningConcept(
            id = "c_1",
            sessionId = session.id,
            name = "SN2 Reaction",
            definition = "Bi-molecular nucleophilic substitution",
            state = LearningMasteryState.NEEDS_REVIEW,
            createdAt = now,
            updatedAt = now
        )
        repository.saveConcepts(listOf(concept))

        val activity = LearningActivity(
            id = "a_1",
            sessionId = session.id,
            conceptId = "c_1",
            type = LearningActivityType.RECALL,
            prompt = "Describe SN2 stereochemistry inversion",
            expectedAnswer = "Walden inversion",
            createdAt = now,
            updatedAt = now
        )
        repository.saveActivities(listOf(activity))

        val brief = repository.observeDailyBrief(now + 1000L).first()
        assertEquals(1, brief.dueActivities.size)
        assertNotNull(brief.weakConcept)
        assertEquals("SN2 Reaction", brief.weakConcept?.first?.name)
        assertFalse(brief.isDoneForToday)
    }

    @Test
    fun `pause and snooze controls update schedules`() = runBlocking {
        val session = repository.createTypedSession("Physics")
        val now = System.currentTimeMillis()
        val activity = LearningActivity(
            id = "a_1",
            sessionId = session.id,
            type = LearningActivityType.RECALL,
            prompt = "What is Newton's second law?",
            expectedAnswer = "F = ma",
            createdAt = now,
            updatedAt = now
        )
        repository.saveActivities(listOf(activity))

        // Snooze
        repository.snoozeActivity("a_1", now + 60_000L)
        val dueSnoozed = repository.observeDueReviews(now + 1000L).first()
        assertTrue(dueSnoozed.isEmpty())

        // Pause session
        repository.pauseSession(session.id, isPaused = true)
        val dueAfterPause = repository.observeDueReviews(now + 120_000L).first()
        assertTrue(dueAfterPause.isEmpty())
    }
}
