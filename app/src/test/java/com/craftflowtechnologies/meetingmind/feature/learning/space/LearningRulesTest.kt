package com.craftflowtechnologies.meetingmind.feature.learning.space

import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.DeterministicSpacedScheduler
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.feature.learning.components.formatLectureTimestamp
import com.craftflowtechnologies.meetingmind.feature.learning.components.isShowable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules behind the Learning space (design/learning-vertical-design-spec.md §6, §9, §13). */
class LearningRulesTest {

    private val now = 1_763_000_000_000L

    private fun session(id: String = "s1", studied: Long? = null, paused: Boolean = false) = LearningSession(
        id = id, noteId = "n_$id", title = "Enzyme Kinetics", courseName = "Human Biochemistry",
        status = if (paused) "PAUSED" else "ACTIVE", lastStudiedAt = studied, createdAt = now, updatedAt = now
    )

    private fun concept(id: String, state: LearningMasteryState, importance: ExamImportance = ExamImportance.HIGH_YIELD) =
        LearningConcept(
            id = id, sessionId = "s1", name = "Concept $id", definition = "Definition of $id",
            state = state, createdAt = now, updatedAt = now, examImportance = importance
        )

    private fun activity(
        id: String,
        conceptId: String? = "c1",
        type: LearningActivityType = LearningActivityType.MULTIPLE_CHOICE,
        diagnostic: Boolean = false,
        options: List<String> = emptyList(),
        answer: String = "answer"
    ) = LearningActivity(
        id = id, sessionId = "s1", conceptId = conceptId, type = type, prompt = "Prompt $id",
        expectedAnswer = answer, options = options, isDiagnostic = diagnostic, createdAt = now, updatedAt = now
    )

    private fun detail(
        concepts: List<LearningConcept>,
        activities: List<LearningActivity>,
        attempts: List<ActivityAttempt> = emptyList()
    ) = LearningRepository.LearningSessionDetail(session(), concepts, activities, attempts, emptyList())

    private fun attempt(activityId: String, conceptId: String, correct: Boolean) = ActivityAttempt(
        id = "a_$activityId", activityId = activityId, sessionId = "s1", conceptId = conceptId,
        userResponse = "x", isCorrect = correct, createdAt = now
    )

    private fun due(n: Int) = (1..n).map { i ->
        val a = activity("d$i", type = LearningActivityType.RECALL)
        LearningRepository.DueReviewItem(
            schedule = DeterministicSpacedScheduler.initialSchedule(a.id, "s1", "c1", now),
            activity = a, concept = null, session = session()
        )
    }

    private fun brief(
        dueItems: List<LearningRepository.DueReviewItem> = emptyList(),
        weak: List<LearningConcept> = emptyList()
    ) = LearningRepository.DailyCompanionBrief(
        recommendedMinutes = 10, dueReviews = dueItems, weakHighYieldConcepts = weak,
        unresolvedConfusionCount = 0, upcomingExams = emptyList(),
        sevenDayActivity = List(7) { false }, sevenDayAttemptCounts = List(7) { 0 }
    )

    private fun lecture(sessionId: String?, activities: Int) = LearningRepository.LectureNoteSummary(
        noteId = "n_$sessionId", sessionId = sessionId, meetingId = null, title = "Lecture 07", summary = "",
        notebookId = null, courseName = "Human Biochemistry", domain = SubjectDomain.GENERAL, workflow = "LECTURE",
        durationSeconds = 0, hasRecording = true, conceptCount = 3, activityCount = activities, strongCount = 0,
        updatedAt = now
    )

    // ── Daily plan ──

    @Test
    fun `plan is bounded and the rest wait until tomorrow`() {
        val plan = buildTodayPlan(brief(due(40)), listOf(session(studied = now)), emptyList())
        val recall = plan.items.first()
        assertEquals(PlanKind.RECALL, recall.kind)
        assertTrue(recall.detail.startsWith("$MaxPlanCards cards"))
        assertTrue(recall.detail.contains("25 more tomorrow"))
        assertTrue(plan.reason.contains("no penalty"))
    }

    @Test
    fun `five minute version keeps reviews only, at most five cards`() {
        val weak = listOf(concept("c1", LearningMasteryState.NEEDS_REVIEW))
        val plan = buildTodayPlan(brief(due(12), weak), listOf(session(studied = now)), emptyList(), quick = true)
        assertEquals(1, plan.items.size)
        assertTrue(plan.items.first().detail.startsWith("$QuickPlanCards cards"))
        assertTrue(plan.minutes <= 5)
    }

    @Test
    fun `plan offers one weak concept and one untested session`() {
        val weak = listOf(concept("c2", LearningMasteryState.LEARNING), concept("c1", LearningMasteryState.NEEDS_REVIEW))
        val plan = buildTodayPlan(brief(weak = weak), listOf(session(studied = null)), listOf(lecture("s1", 6)))
        assertEquals(listOf(PlanKind.STRENGTHEN, PlanKind.DIAGNOSTIC), plan.items.map { it.kind })
        assertEquals("c1", plan.items.first().concept?.id)
        assertTrue(plan.items.last().detail.contains("Not graded"))
    }

    @Test
    fun `nothing due gives an empty plan, never a backlog`() {
        val plan = buildTodayPlan(brief(), listOf(session(studied = now)), listOf(lecture("s1", 6)))
        assertTrue(plan.isEmpty)
        assertEquals(0, plan.minutes)
    }

    @Test
    fun `teach-back is never planned in release 1`() {
        val tb = activity("tb", type = LearningActivityType.TEACH_BACK)
        val item = LearningRepository.DueReviewItem(DeterministicSpacedScheduler.initialSchedule("tb", "s1", null, now), tb, null, session())
        assertTrue(buildTodayPlan(brief(listOf(item)), listOf(session(studied = now)), emptyList()).isEmpty)
    }

    @Test
    fun `greeting follows the clock`() {
        assertEquals("Good morning", greetingFor(9))
        assertEquals("Good afternoon", greetingFor(14))
        assertEquals("Good evening", greetingFor(21))
        assertEquals("Good evening", greetingFor(2))
    }

    // ── Session next step and diagnostic ──

    @Test
    fun `next step is the diagnostic before any practice`() {
        val d = detail(listOf(concept("c1", LearningMasteryState.NEW)), (1..6).map { activity("a$it") })
        val step = nextStepFor(d)
        assertEquals(NextStepKind.DIAGNOSTIC, step.kind)
        assertTrue(step.detail.contains("not graded"))
    }

    @Test
    fun `next step strengthens the concept that needs review first`() {
        val concepts = listOf(concept("c1", LearningMasteryState.LEARNING), concept("c2", LearningMasteryState.NEEDS_REVIEW))
        val d = detail(concepts, listOf(activity("a1", "c1"), activity("a2", "c2")), listOf(attempt("a2", "c2", false)))
        val step = nextStepFor(d)
        assertEquals(NextStepKind.STRENGTHEN, step.kind)
        assertEquals("c2", step.concept?.id)
    }

    @Test
    fun `hidden and reported concepts are held back from practice`() {
        val hidden = concept("c2", LearningMasteryState.NEW).copy(isDismissed = true)
        val reported = concept("c3", LearningMasteryState.NEW).copy(isStale = true)
        val d = detail(
            listOf(concept("c1", LearningMasteryState.NEW), hidden, reported),
            listOf(activity("a1", "c1"), activity("a2", "c2"), activity("a3", "c3"))
        )
        assertEquals(listOf("a1"), practiceItems(d).map { it.id })
        assertEquals(listOf("a1"), diagnosticSet(d).map { it.id })
    }

    @Test
    fun `diagnostic prefers flagged items and stays between five and eight`() {
        val flagged = (1..9).map { activity("f$it", diagnostic = true) }
        assertEquals(8, diagnosticSet(detail(emptyList(), flagged)).size)
        val mixed = (1..10).map { activity("m$it", conceptId = "c$it") }
        assertEquals(6, diagnosticSet(detail(emptyList(), mixed)).size)
    }

    @Test
    fun `concept set is at most three items on that concept`() {
        val acts = (1..5).map { activity("a$it", "c1") } + activity("b1", "c2")
        val set = conceptSet(detail(emptyList(), acts), "c1")
        assertEquals(3, set.size)
        assertTrue(set.all { it.conceptId == "c1" })
    }

    // ── Answer checking ──

    @Test
    fun `correct option is found by text or letter, never by loose containment`() {
        val opts = listOf("Km increases", "Vmax decreases", "Both change", "Neither")
        assertEquals(0, correctOptionIndex(activity("a", options = opts, answer = "Km increases")))
        assertEquals(1, correctOptionIndex(activity("a", options = opts, answer = "B")))
        assertEquals(2, correctOptionIndex(activity("a", options = opts, answer = "c) Both change")))
        // "A" used to match any answer containing the letter A; it must not.
        assertNull(correctOptionIndex(activity("a", options = listOf("A", "B"), answer = "Apparent Km rises")))
        assertNull(correctOptionIndex(activity("a", options = opts, answer = "")))
        // An answer that starts with the word "A" is matched by text, not read as option A.
        assertEquals(1, correctOptionIndex(activity("a", options = listOf("A lipid", "A protein"), answer = "A protein channel")))
        assertNull(correctOptionIndex(activity("a", options = listOf("Lipid", "Protein"), answer = "A sugar")))
    }

    @Test
    fun `key points split a model answer into at most four`() {
        val pts = keyPointsOf("The inhibitor binds the active site. Km rises. Vmax is unchanged. It is reversible. Extra point.")
        assertEquals(4, pts.size)
        assertEquals("The inhibitor binds the active site.", pts.first())
    }

    @Test
    fun `suggested ticks come from shared wording and can be empty`() {
        val pts = listOf("Inhibitor binds the active site", "Maximum velocity unchanged")
        assertEquals(listOf(0), suggestedTicks(pts, "it binds the active site of the enzyme"))
        assertTrue(suggestedTicks(pts, "").isEmpty())
    }

    @Test
    fun `lecture timestamps read as minutes and seconds`() {
        assertEquals("24:12", formatLectureTimestamp(1_452_000))
        assertEquals("1:02:05", formatLectureTimestamp(3_725_000))
        assertEquals("0:00", formatLectureTimestamp(-5))
    }

    @Test
    fun `mastery counts ignore hidden concepts`() {
        val counts = masteryCounts(
            listOf(concept("a", LearningMasteryState.STRONG), concept("b", LearningMasteryState.STRONG).copy(isDismissed = true))
        )
        assertEquals(1, counts[LearningMasteryState.STRONG])
        assertFalse(counts.containsKey(LearningMasteryState.NEW))
    }

    @Test
    fun `evidence without quote or moment is not shown as a citation`() {
        assertFalse(LearningEvidence().isShowable)
        assertTrue(LearningEvidence(startMs = 1000).isShowable)
        assertTrue(LearningEvidence(quote = "Km rises").isShowable)
    }
}
