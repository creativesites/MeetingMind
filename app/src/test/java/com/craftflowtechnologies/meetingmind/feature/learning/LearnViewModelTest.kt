package com.craftflowtechnologies.meetingmind.feature.learning

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearnViewModelTest {

    private lateinit var app: Application
    private lateinit var db: MeetMindDatabase
    private lateinit var learningRepo: LearningRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var aiEngine: LearningAiEngine
    private lateinit var viewModel: LearnViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        MeetMindDatabase.setInstanceForTest(db)

        learningRepo = LearningRepository(db)
        noteRepo = NoteRepository(app, db)
        aiEngine = LearningAiEngine()

        viewModel = LearnViewModel(
            application = app,
            database = db,
            learningRepo = learningRepo,
            noteRepo = noteRepo,
            aiEngine = aiEngine
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createCourse creates course with domain properties`() = runTest {
        viewModel.createCourse(
            name = "Calculus II",
            courseCode = "MATH 201",
            domain = SubjectDomain.MATH_STATS,
            instructor = "Prof. Euler",
            term = "Fall 2026"
        )

        val courses = viewModel.courses.first { it.isNotEmpty() }
        assertEquals(1, courses.size)
        val course = courses.first()
        assertEquals("Calculus II", course.notebook.name)
        assertEquals("MATH 201", course.properties.courseCode)
        assertEquals(SubjectDomain.MATH_STATS, course.properties.domain)
    }

    @Test
    fun `createOrUpdateGoal persists goal and updates priorities`() = runTest {
        val now = System.currentTimeMillis()
        val examDate = now + (10 * 24 * 3600 * 1000L)

        viewModel.createOrUpdateGoal(
            title = "Final Exam",
            examDateMs = examDate,
            targetGrade = "A",
            dailyMinutesTarget = 45
        )

        val goals = viewModel.goals.first { it.isNotEmpty() }
        assertEquals(1, goals.size)
        assertEquals("Final Exam", goals.first().title)
        assertEquals(45, goals.first().dailyMinutesTarget)
    }

    @Test
    fun `startPracticeSession and recordRecallAttempt advances state`() = runTest {
        val note = noteRepo.createNote(title = "Linear Algebra")
        val session = learningRepo.ensureSessionForNote(
            noteId = note.id,
            title = "Eigenvalues",
            domain = SubjectDomain.MATH_STATS
        )

        val concept = LearningConcept(
            id = "c_eigen",
            sessionId = session.id,
            name = "Eigenvalue",
            definition = "Scalar lambda where Av = lambda v",
            formulaLatex = "A v = \\lambda v",
            createdAt = 0L,
            updatedAt = 0L
        )
        val activity = LearningActivity(
            id = "act_eigen",
            sessionId = session.id,
            conceptId = concept.id,
            type = LearningActivityType.RECALL,
            prompt = "Define eigenvalue",
            expectedAnswer = "Av = lambda v",
            createdAt = 0L,
            updatedAt = 0L
        )
        learningRepo.saveConceptsAndActivities(session.id, listOf(concept), listOf(activity))

        viewModel.startPracticeSession(listOf(activity))
        assertEquals(1, viewModel.practiceActivities.value.size)
        assertEquals(0, viewModel.currentActivityIndex.value)

        viewModel.flipCard()
        assertTrue(viewModel.isFlipped.value)

        viewModel.recordRecallAttempt(
            userResponse = "Av = lambda v",
            isCorrect = true,
            rating = RecallRating.GOOD
        )

        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.lastOutcome.value)
        assertTrue(viewModel.lastOutcome.value?.attempt?.isCorrect == true)
    }

    @Test
    fun `explainMistake generates misconception analysis offline`() = runTest {
        val activity = LearningActivity(
            id = "act_derivative",
            sessionId = "s1",
            type = LearningActivityType.WORKED_PROBLEM,
            prompt = "Find d/dx of sin(x)",
            expectedAnswer = "cos(x)",
            createdAt = 0L,
            updatedAt = 0L
        )

        viewModel.explainMistake(activity, "tan(x)")
        testScheduler.advanceUntilIdle()

        val explanation = viewModel.mistakeExplanation.value
        assertNotNull(explanation)
        assertEquals("tan(x)", explanation?.studentMistake)
        assertTrue(explanation?.rootMisconception?.isNotBlank() == true)
    }

    @Test
    fun `generateCramSheet synthesizes markdown with formulas`() = runTest {
        val note = noteRepo.createNote(title = "Accounting 101")
        val session = learningRepo.ensureSessionForNote(
            noteId = note.id,
            title = "Double Entry",
            domain = SubjectDomain.ACCOUNTING_FINANCE
        )
        val concept = LearningConcept(
            id = "c_debit",
            sessionId = session.id,
            name = "Debit Rule",
            definition = "Debits increase assets and expenses",
            formulaLatex = "Assets = Liabilities + Equity",
            createdAt = 0L,
            updatedAt = 0L
        )
        learningRepo.saveConceptsAndActivities(session.id, listOf(concept), emptyList())

        var resultMarkdown: String? = null
        viewModel.generateCramSheet(listOf(session.id), "Midterm Cram Sheet") { md ->
            resultMarkdown = md
        }
        testScheduler.advanceUntilIdle()

        assertNotNull(resultMarkdown)
        assertTrue(resultMarkdown!!.contains("Midterm Cram Sheet"))
        assertTrue(resultMarkdown!!.contains("Debit Rule"))
        assertTrue(resultMarkdown!!.contains("Assets = Liabilities + Equity"))
    }
}
