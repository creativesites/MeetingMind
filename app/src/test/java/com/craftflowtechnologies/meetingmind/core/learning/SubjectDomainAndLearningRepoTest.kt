package com.craftflowtechnologies.meetingmind.core.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.model.CourseProperties
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningGoal
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubjectDomainAndLearningRepoTest {
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var repo: LearningRepository
    private var testClockMs: Long = 1_700_000_000_000L

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LearningRepository(db, clock = { testClockMs })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun subjectDomainsHaveNonEmptyVocabularyAndTemplates() {
        val domains = SubjectDomain.entries
        assertEquals(10, domains.size)
        domains.forEach { domain ->
            assertTrue(domain.displayName.isNotBlank())
            assertTrue(domain.shortLabel.isNotBlank())
            assertTrue(domain.vocabularyHints().isNotEmpty())
            assertTrue(domain.templateSections().isNotEmpty())
        }
    }

    @Test
    fun spokenNotationNormalizerConvertsTechnicalNotation() {
        val csInput = "The merge sort algorithm has time complexity of big o of n log n and space complexity of big o of n."
        val csExpected = "The merge sort algorithm has time complexity of O(n log n) and space complexity of O(n)."
        assertEquals(csExpected, SpokenNotationNormalizer.normalize(csInput, SubjectDomain.PROGRAMMING_CS))

        val mathInput = "If x is greater than or equal to y plus or minus capital delta."
        val mathNormalized = SpokenNotationNormalizer.normalize(mathInput, SubjectDomain.MATH_STATS)
        assertTrue(mathNormalized.contains("≥"))
        assertTrue(mathNormalized.contains("±"))
        assertTrue(mathNormalized.contains("Δ"))

        val chemInput = "h two s o four reacts to form h two o and s o three"
        val chemNormalized = SpokenNotationNormalizer.normalize(chemInput, SubjectDomain.CHEMISTRY)
        assertTrue(chemNormalized.contains("H₂SO₄"))
        assertTrue(chemNormalized.contains("H₂O"))
        assertTrue(chemNormalized.contains("→"))
    }

    @Test
    fun learningRepositoryManagesCoursesSessionsAndAttempts() = runBlocking {
        // 1. Create course notebook
        val course = repo.createOrUpdateCourse(
            name = "CS 201: Data Structures",
            properties = CourseProperties(
                courseCode = "CS201",
                domain = SubjectDomain.PROGRAMMING_CS
            ),
            colorHex = "#6366F1"
        )
        assertNotNull(course)
        assertEquals("CS 201: Data Structures", course.notebook.name)

        // 2. Insert backing note
        val note = NoteEntity(
            id = "note_bst",
            title = "Binary Search Trees",
            preview = "BST properties and rotations",
            createdAt = testClockMs,
            updatedAt = testClockMs,
            isArchived = false,
            isDeleted = false,
            isPinned = false,
            isPrivate = false,
            notebookId = course.notebook.id,
            space = NotebookSpace.LEARNING.name
        )
        db.noteDao().insert(note)

        // 3. Create or attach session
        val session = repo.ensureSessionForNote(
            noteId = "note_bst",
            title = "Binary Search Trees",
            meetingId = null,
            domain = SubjectDomain.PROGRAMMING_CS,
            courseName = "CS 201"
        )
        assertEquals("note_bst", session.noteId)
        assertEquals(SubjectDomain.PROGRAMMING_CS.name, session.subjectDomain)

        // 4. Save concepts & activities
        val concept = LearningConcept(
            id = "conc_bst_lookup",
            sessionId = session.id,
            name = "BST Search Complexity",
            definition = "O(h) where h is tree height",
            state = LearningMasteryState.DEVELOPING,
            examImportance = ExamImportance.HIGH_YIELD,
            formulaLatex = "O(h) \\le O(\\log n)",
            createdAt = testClockMs,
            updatedAt = testClockMs
        )
        val activity = LearningActivity(
            id = "act_bst_1",
            sessionId = session.id,
            conceptId = concept.id,
            type = LearningActivityType.RECALL,
            prompt = "What is the search complexity of an unbalanced BST in the worst case?",
            expectedAnswer = "O(n) when degenerated into a linked list.",
            difficulty = "MEDIUM",
            createdAt = testClockMs,
            updatedAt = testClockMs
        )
        repo.saveConceptsAndActivities(session.id, listOf(concept), listOf(activity))

        // 5. Verify session detail loads
        val detail = repo.observeSessionDetail(session.id).first()
        assertNotNull(detail)
        assertEquals(1, detail?.concepts?.size)
        assertEquals("BST Search Complexity", detail?.concepts?.first()?.name)
        assertEquals(ExamImportance.HIGH_YIELD, detail?.concepts?.first()?.examImportance)

        // 6. Record attempt and check scheduler progression
        val outcome = repo.recordAttempt(
            activityId = activity.id,
            userResponse = "O(n)",
            isCorrect = true,
            rating = RecallRating.GOOD,
            feedback = "Spot on"
        )
        assertTrue(outcome.attempt.isCorrect)
        assertTrue(outcome.schedule.intervalDays >= 1)
        assertTrue(outcome.schedule.dueAt > testClockMs)

        // 7. Create exam goal & verify countdown
        val goal = LearningGoal(
            id = "goal_cs_midterm",
            notebookId = course.notebook.id,
            title = "CS 201 Midterm",
            examDateMs = testClockMs + (5L * 24 * 60 * 60 * 1000), // 5 days out
            sessionIds = listOf(session.id),
            dailyMinutesTarget = 30,
            createdAt = testClockMs,
            updatedAt = testClockMs
        )
        repo.saveGoal(goal)

        val goals = repo.observeGoals().first()
        assertEquals(1, goals.size)
        assertEquals("CS 201 Midterm", goals.first().title)

        // 8. Daily companion brief
        val brief = repo.observeDailyBrief(now = testClockMs).first()
        assertTrue(brief.recommendedMinutes >= 10)
    }
}
