package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NoteBlockEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningPassage
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

    @Test
    fun `gatherSessionPassages extracts authentic note blocks and transcript segments`() = runBlocking {
        val session = repository.createTypedSession("Biochemistry 101")
        val now = System.currentTimeMillis()

        // 1. Insert Note Block
        val block = NoteBlockEntity(
            id = "b_1",
            noteId = session.noteId,
            position = 0,
            type = "PARAGRAPH",
            text = "Enzymes stabilize the transition state.",
            spans = "",
            payloadJson = "{}",
            source = "USER",
            sourceSegmentIdsJson = "[]",
            sectionKey = "notes",
            isUserEdited = false,
            indent = 0,
            checked = false,
            updatedAt = now
        )
        db.noteDao().upsertBlocks(listOf(block))

        // 2. Insert Meeting and Transcript Segment
        val meeting = MeetingEntity(
            id = "meet_1",
            title = "Enzyme Lecture",
            createdAt = now,
            durationMs = 60_000L,
            source = "LOCAL_RECORDING",
            audioFilePath = null,
            status = "READY",
            participantCount = 1,
            language = "en",
            summaryText = null,
            noteId = session.noteId
        )
        db.meetingDao().insertMeeting(meeting)

        val segment = TranscriptSegmentEntity(
            id = "seg_1",
            meetingId = "meet_1",
            speakerId = null,
            speakerName = "Prof",
            startMs = 5000L,
            endMs = 12000L,
            text = "Active sites exhibit induced fit when substrate binds.",
            confidence = null
        )
        db.transcriptDao().insertSegments(listOf(segment))

        val passages = repository.gatherSessionPassages(session.id)
        assertEquals(2, passages.size)

        val notePassage = passages.filterIsInstance<LearningPassage.NoteBlock>().firstOrNull()
        assertNotNull(notePassage)
        assertEquals(session.noteId, notePassage?.noteId)
        assertEquals("b_1", notePassage?.blockId)
        val noteEvidence = notePassage?.toEvidence()
        assertEquals(session.noteId, noteEvidence?.noteId)
        assertEquals("b_1", noteEvidence?.blockId)

        val transcriptPassage = passages.filterIsInstance<LearningPassage.TranscriptSegment>().firstOrNull()
        assertNotNull(transcriptPassage)
        assertEquals(session.noteId, transcriptPassage?.noteId)
        assertEquals("meet_1", transcriptPassage?.meetingId)
        assertEquals("seg_1", transcriptPassage?.segmentId)
        assertEquals(5000L, transcriptPassage?.startMs)
        assertEquals(12000L, transcriptPassage?.endMs)
        val transcriptEvidence = transcriptPassage?.toEvidence()
        assertEquals("meet_1", transcriptEvidence?.meetingId)
        assertEquals(5000L, transcriptEvidence?.startMs)
    }

    @Test
    fun `checkAndMarkStaleEntities detects missing source blocks and transcript segments`() = runBlocking {
        val session = repository.createTypedSession("Genetics")
        val now = System.currentTimeMillis()

        val validBlock = NoteBlockEntity(
            id = "block_existing",
            noteId = session.noteId,
            position = 0,
            type = "PARAGRAPH",
            text = "DNA transcription precedes translation.",
            spans = "",
            payloadJson = "{}",
            source = "USER",
            sourceSegmentIdsJson = "[]",
            sectionKey = "notes",
            isUserEdited = false,
            indent = 0,
            checked = false,
            updatedAt = now
        )
        db.noteDao().upsertBlocks(listOf(validBlock))

        val validConcept = LearningConcept(
            id = "c_valid",
            sessionId = session.id,
            name = "Transcription",
            definition = "RNA synthesis",
            evidence = listOf(LearningEvidence(noteId = session.noteId, blockId = "block_existing")),
            isStale = false,
            createdAt = now,
            updatedAt = now
        )
        val staleConcept = LearningConcept(
            id = "c_stale",
            sessionId = session.id,
            name = "Deleted Fact",
            definition = "Refers to missing block",
            evidence = listOf(LearningEvidence(noteId = session.noteId, blockId = "block_deleted")),
            isStale = false,
            createdAt = now,
            updatedAt = now
        )
        repository.saveConcepts(listOf(validConcept, staleConcept))

        val validActivity = LearningActivity(
            id = "a_valid",
            sessionId = session.id,
            conceptId = "c_valid",
            type = LearningActivityType.RECALL,
            prompt = "What precedes translation?",
            expectedAnswer = "Transcription",
            evidence = listOf(LearningEvidence(noteId = session.noteId, blockId = "block_existing")),
            isStale = false,
            createdAt = now,
            updatedAt = now
        )
        val staleActivity = LearningActivity(
            id = "a_stale",
            sessionId = session.id,
            conceptId = "c_valid",
            type = LearningActivityType.RECALL,
            prompt = "What was deleted?",
            expectedAnswer = "Ghost",
            evidence = listOf(LearningEvidence(noteId = session.noteId, blockId = "block_ghost")),
            isStale = false,
            createdAt = now,
            updatedAt = now
        )
        repository.saveActivities(listOf(validActivity, staleActivity))

        // Check before running stale detection
        assertFalse(repository.getConcepts(session.id).first { it.id == "c_stale" }.isStale)
        assertFalse(repository.getActivities(session.id).first { it.id == "a_stale" }.isStale)

        // Run stale detection
        repository.checkAndMarkStaleEntities(session.id)

        // Concepts: c_valid stays fresh, c_stale becomes stale
        assertFalse(repository.getConcepts(session.id).first { it.id == "c_valid" }.isStale)
        assertTrue(repository.getConcepts(session.id).first { it.id == "c_stale" }.isStale)

        // Activities: a_valid stays fresh, a_stale becomes stale
        assertFalse(repository.getActivities(session.id).first { it.id == "a_valid" }.isStale)
        assertTrue(repository.getActivities(session.id).first { it.id == "a_stale" }.isStale)

        // Stale activity is withheld from due review queue
        val due = repository.observeDueReviews(now + 10_000L).first()
        val dueIds = due.map { it.first.id }
        assertTrue(dueIds.contains("a_valid"))
        assertFalse(dueIds.contains("a_stale"))
    }

    @Test
    fun `addCustomConcept and updateConcept preserve isUserEdited flag`() = runBlocking {
        val session = repository.createTypedSession("Chemistry")
        val concept = repository.addCustomConcept(
            sessionId = session.id,
            name = "Entropy",
            definition = "Measure of disorder",
            emphasis = "HIGH"
        )
        assertTrue(concept.isUserEdited)
        assertEquals("Entropy", concept.name)

        val updated = repository.updateConcept(concept.copy(definition = "Degree of randomness or disorder"))
        assertTrue(updated.isUserEdited)
        assertEquals("Degree of randomness or disorder", updated.definition)

        val fetched = repository.getConcepts(session.id).first { it.id == concept.id }
        assertTrue(fetched.isUserEdited)
        assertEquals("Degree of randomness or disorder", fetched.definition)
    }

    @Test
    fun `regenerateSession preserves user-edited concepts and attempts while replacing unedited items`() = runBlocking {
        val session = repository.createTypedSession("Calculus")
        val now = System.currentTimeMillis()

        // 1. Initial items: 1 auto concept, 1 user-edited concept
        val autoConcept = LearningConcept(
            id = "c_auto",
            sessionId = session.id,
            name = "Limit",
            definition = "Value a function approaches",
            isUserEdited = false,
            createdAt = now,
            updatedAt = now
        )
        val userConcept = LearningConcept(
            id = "c_user",
            sessionId = session.id,
            name = "Derivative",
            definition = "Instantaneous rate of change",
            isUserEdited = true,
            createdAt = now,
            updatedAt = now
        )
        repository.saveConcepts(listOf(autoConcept, userConcept))

        // 2. Initial activities: 1 with attempts, 1 without attempts
        val actWithAttempt = LearningActivity(
            id = "a_attempted",
            sessionId = session.id,
            conceptId = userConcept.id,
            type = LearningActivityType.RECALL,
            prompt = "What is a derivative?",
            expectedAnswer = "Rate of change",
            createdAt = now,
            updatedAt = now
        )
        val actUnattempted = LearningActivity(
            id = "a_unattempted",
            sessionId = session.id,
            conceptId = autoConcept.id,
            type = LearningActivityType.RECALL,
            prompt = "What is a limit?",
            expectedAnswer = "Approached value",
            createdAt = now,
            updatedAt = now
        )
        repository.saveActivities(listOf(actWithAttempt, actUnattempted))

        // Record attempt on a_attempted
        repository.recordAttempt(
            activityId = "a_attempted",
            userResponse = "Rate of change",
            isCorrect = true,
            selfRating = RecallRating.GOOD,
            feedback = "Correct"
        )
        val attemptsBefore = repository.getAttemptsForSession(session.id)
        assertEquals(1, attemptsBefore.size)

        // 3. Regenerate session with new concepts & activities
        val newAutoConcept = LearningConcept(
            id = "c_auto_new",
            sessionId = session.id,
            name = "Integral",
            definition = "Area under curve",
            isUserEdited = false,
            createdAt = now + 1000L,
            updatedAt = now + 1000L
        )
        val newActivity = LearningActivity(
            id = "a_new",
            sessionId = session.id,
            conceptId = newAutoConcept.id,
            type = LearningActivityType.RECALL,
            prompt = "What is an integral?",
            expectedAnswer = "Area under curve",
            createdAt = now + 1000L,
            updatedAt = now + 1000L
        )

        repository.regenerateSession(
            sessionId = session.id,
            newConcepts = listOf(newAutoConcept),
            newActivities = listOf(newActivity)
        )

        // 4. Verify concepts: c_auto replaced, c_user preserved, c_auto_new added
        val currentConcepts = repository.getConcepts(session.id)
        val currentConceptIds = currentConcepts.map { it.id }
        assertFalse(currentConceptIds.contains("c_auto"))
        assertTrue(currentConceptIds.contains("c_user"))
        assertTrue(currentConceptIds.contains("c_auto_new"))

        // 5. Verify activities: a_attempted is preserved as dismissed (so attempt history survives),
        //    a_unattempted is deleted, a_new is active
        val attemptsAfter = repository.getAttemptsForSession(session.id)
        assertEquals(1, attemptsAfter.size) // Attempt history strictly preserved!

        val activeActivities = repository.getActivities(session.id)
        val activeIds = activeActivities.map { it.id }
        assertTrue(activeIds.contains("a_new"))
        assertFalse(activeIds.contains("a_unattempted"))
        assertFalse(activeIds.contains("a_attempted")) // a_attempted is dismissed so excluded from active activities

        // Verify review schedules: a_new has an active schedule, old schedules are removed
        val dueList = repository.observeDueReviews(now + 10_000L).first()
        val dueActIds = dueList.map { it.first.id }
        assertTrue(dueActIds.contains("a_new"))
        assertFalse(dueActIds.contains("a_attempted"))
        assertFalse(dueActIds.contains("a_unattempted"))
    }
}
