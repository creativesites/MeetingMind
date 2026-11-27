package com.craftflowtechnologies.meetingmind.feature.learning

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.learning.LearningAiEngine
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.BillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.ClosedTesting
import com.craftflowtechnologies.meetingmind.core.billing.Entitlements
import com.craftflowtechnologies.meetingmind.core.billing.PlanOffer
import com.craftflowtechnologies.meetingmind.core.billing.PurchaseResult
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

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

    private lateinit var savedGateway: BillingGateway
    private var savedAllAccess = false

    /** Billing is set up and the learner holds no paid plan. */
    private object UnpaidGateway : BillingGateway {
        override val configured: Boolean = true
        override val entitlements: StateFlow<Entitlements> = MutableStateFlow(Entitlements())
        override suspend fun offers(): List<PlanOffer> = emptyList()
        override suspend fun purchase(activity: android.app.Activity, offerId: String): PurchaseResult = PurchaseResult.Failed("test")
        override suspend fun restore(): PurchaseResult = PurchaseResult.Failed("test")
        override suspend fun refresh() = Unit
        override fun appUserId(): String? = null
    }

    @Before
    fun setUp() {
        savedGateway = Billing.gateway
        savedAllAccess = ClosedTesting.allAccess
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
        Billing.gateway = savedGateway
        ClosedTesting.allAccess = savedAllAccess
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    private fun awaitCondition(
        timeoutMs: Long = 5000,
        pollIntervalMs: Long = 25,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper(pollIntervalMs, TimeUnit.MILLISECONDS)
            if (condition()) return
            Thread.sleep(pollIntervalMs)
        }
        ShadowLooper.idleMainLooper(pollIntervalMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Waits for a view-model StateFlow to satisfy [predicate] while idling the paused main looper
     * (TESTING.md gotcha 1). A bare `first {}` can wait forever: Room posts the update to the main
     * looper, which only runs when something idles it.
     */
    private fun <T> awaitValue(flow: kotlinx.coroutines.flow.StateFlow<T>, predicate: (T) -> Boolean): T {
        val watcher = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            .launch { flow.collect {} }
        try {
            awaitCondition(timeoutMs = 10_000) { predicate(flow.value) }
            assertTrue("the flow never reached the expected value: ${flow.value}", predicate(flow.value))
            return flow.value
        } finally {
            watcher.cancel()
        }
    }

    @Test
    fun `createCourse creates course with domain properties`() = runTest {
        viewModel.createCourse(
            name = "Calculus II",
            courseCode = "MATH 201",
            domain = SubjectDomain.MATH_QUANT,
            instructor = "Prof. Euler",
            term = "Fall 2026"
        )

        val courses = awaitValue(viewModel.courses) { it.isNotEmpty() }
        assertEquals(1, courses.size)
        val course = courses.first()
        assertEquals("Calculus II", course.notebook.name)
        assertEquals("MATH 201", course.properties.courseCode)
        assertEquals(SubjectDomain.MATH_QUANT, course.properties.domain)
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

        val goals = awaitValue(viewModel.goals) { it.isNotEmpty() }
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
            domain = SubjectDomain.MATH_QUANT
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
        awaitCondition { viewModel.lastOutcome.value != null }
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
        // The cram sheet is a paid extra; closed testing opens every paid extra.
        ClosedTesting.allAccess = true
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
        awaitCondition { resultMarkdown != null }

        assertNotNull(resultMarkdown)
        assertTrue(resultMarkdown!!.contains("Midterm Cram Sheet"))
        assertTrue(resultMarkdown!!.contains("Debit Rule"))
        assertTrue(resultMarkdown!!.contains("Assets = Liabilities + Equity"))
    }

    @Test
    fun `AndroidViewModelFactory creates LearnViewModel via JvmOverloads constructor without reflection failure`() {
        val vm = ViewModelProvider.AndroidViewModelFactory.getInstance(app).create(LearnViewModel::class.java)
        assertNotNull(vm)
        assertNotNull(vm.learningRepo)
        assertNotNull(vm.noteRepo)
        assertNotNull(vm.aiEngine)
    }

    @Test
    fun `toggleSessionPause refreshes activeSessionDetail with updated isPaused state`() = runTest {
        val note = noteRepo.createNote(title = "Neuroscience 101")
        val session = learningRepo.ensureSessionForNote(
            noteId = note.id,
            title = "Action Potentials",
            domain = SubjectDomain.SCIENCES_MED
        )

        viewModel.loadSessionDetail(session.id)
        testScheduler.advanceUntilIdle()
        awaitCondition { viewModel.activeSessionDetail.value != null }

        val initialDetail = viewModel.activeSessionDetail.value
        assertNotNull(initialDetail)
        assertEquals(false, initialDetail?.session?.isPaused)
        assertEquals("ACTIVE", initialDetail?.session?.status)

        // Toggle pause -> should become PAUSED (isPaused = true)
        viewModel.toggleSessionPause(session.id)
        testScheduler.advanceUntilIdle()
        awaitCondition { viewModel.activeSessionDetail.value?.session?.isPaused == true }

        val pausedDetail = viewModel.activeSessionDetail.value
        assertNotNull(pausedDetail)
        assertTrue(pausedDetail!!.session.isPaused)
        assertEquals("PAUSED", pausedDetail.session.status)

        // Toggle again -> should resume to ACTIVE (isPaused = false)
        viewModel.toggleSessionPause(session.id)
        testScheduler.advanceUntilIdle()
        awaitCondition { viewModel.activeSessionDetail.value?.session?.isPaused == false }

        val resumedDetail = viewModel.activeSessionDetail.value
        assertNotNull(resumedDetail)
        assertEquals(false, resumedDetail?.session?.isPaused)
        assertEquals("ACTIVE", resumedDetail?.session?.status)
    }

    @Test
    fun `createCourseFromNotes links recorded lecture note to course and bootstraps study materials`() = runTest {
        val note = noteRepo.createNote(
            workflow = com.craftflowtechnologies.meetingmind.core.model.RecordingType.LECTURE,
            title = "MATH 204: Eigenvalues and Diagonalization"
        )
        noteRepo.saveBlocks(
            note.id,
            listOf(
                com.craftflowtechnologies.meetingmind.core.model.NoteBlock(
                    id = "b1",
                    noteId = note.id,
                    position = 0,
                    type = com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.CALLOUT,
                    content = com.craftflowtechnologies.meetingmind.core.notes.RichText.plain("Eigenvalue: A scalar lambda such that Av = lambda v for a nonzero eigenvector v."),
                    payload = mapOf(
                        com.craftflowtechnologies.meetingmind.core.model.NoteBlock.PAYLOAD_CALLOUT_TITLE to "Eigenvalue Definition"
                    )
                )
            )
        )

        var createdCourseId: String? = null
        viewModel.createCourseFromNotes(
            noteIds = listOf(note.id),
            courseName = "Linear Algebra",
            courseCode = "MATH 204",
            domain = SubjectDomain.MATH_QUANT
        ) { course ->
            createdCourseId = course.notebook.id
        }
        testScheduler.advanceUntilIdle()
        awaitCondition { createdCourseId != null }

        assertNotNull(createdCourseId)
        val updatedNote = noteRepo.getNote(note.id)
        assertEquals(createdCourseId, updatedNote?.notebookId)

        val session = learningRepo.ensureSessionForNote(note.id)
        val detail = learningRepo.getSessionDetail(session.id)
        assertNotNull(detail)
        assertEquals("Linear Algebra", detail?.session?.courseName)
        assertTrue(detail!!.concepts.isNotEmpty())
        assertTrue(detail.activities.isNotEmpty())
    }

    @Test
    fun `generateQuickTopicDeck populates practiceActivities for instant practice`() = runTest {
        var ready = false
        viewModel.generateQuickTopicDeck(
            topic = "Derivatives & Chain Rule",
            domain = SubjectDomain.MATH_QUANT
        ) {
            ready = true
        }
        testScheduler.advanceUntilIdle()
        awaitCondition(timeoutMs = 20_000) { ready && viewModel.practiceActivities.value.isNotEmpty() }

        assertTrue(ready)
        assertTrue(viewModel.practiceActivities.value.isNotEmpty())
    }

    @Test
    fun `saving a cram sheet under a goal id that is not a notebook saves to the default notebook`() = runTest {
        // A goal card's id, or "" for a goal with no course, used to reach the notes table and
        // fail its notebook foreign key, which ended the app.
        var savedId: String? = null
        viewModel.saveCramSheetAsNote("Finals cram", "goal_not_a_notebook", "# Finals\n\n- Recall the rule") { id ->
            savedId = id
        }
        testScheduler.advanceUntilIdle()
        awaitCondition { savedId != null }

        val note = noteRepo.getNote(assertNotNullId(savedId))
        assertNotNull(note)
        assertTrue(note!!.notebookId != "goal_not_a_notebook")
    }

    private fun assertNotNullId(id: String?): String {
        assertNotNull("the cram sheet was not saved", id)
        return id!!
    }

    @Test
    fun `generateCramSheet does nothing for a learner without a paid plan`() = runTest {
        Billing.gateway = UnpaidGateway
        ClosedTesting.allAccess = false
        val note = noteRepo.createNote(title = "Accounting 101")
        val session = learningRepo.ensureSessionForNote(noteId = note.id, title = "Double Entry", domain = SubjectDomain.ACCOUNTING_FINANCE)

        var resultMarkdown: String? = null
        viewModel.generateCramSheet(listOf(session.id), "Midterm Cram Sheet") { md -> resultMarkdown = md }
        testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)

        assertEquals(null, resultMarkdown)
    }
}
