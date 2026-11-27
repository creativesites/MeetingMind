package com.craftflowtechnologies.meetingmind.feature.learn.explain

import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackCoverage
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackCriterionEvaluation
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackEvaluation
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackStatus
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainAssessmentTest {

    private val rubric = listOf(
        TeachBackCriterion(claim = "Vmax is the maximum rate"),
        TeachBackCriterion(claim = "Binds the active site"),
        TeachBackCriterion(claim = "Vmax stays the same")
    )

    @Test
    fun `model statuses map to covered, partly and not yet in rubric order`() {
        val evaluation = TeachBackEvaluation(
            overallCoverage = TeachBackCoverage.PARTLY_COVERED,
            scorePercent = 60,
            summary = "Good start.",
            criteriaEvaluations = listOf(
                TeachBackCriterionEvaluation(rubric[1], TeachBackStatus.PARTIAL, "almost"),
                TeachBackCriterionEvaluation(rubric[0], TeachBackStatus.MET, "yes")
            ),
            missingClaims = listOf("Vmax stays the same"),
            socraticFollowUp = "What changes with more substrate?"
        )

        val judged = EngineTeachBackAssessor.judged(rubric, evaluation)

        assertEquals(listOf(IdeaCoverage.COVERED, IdeaCoverage.PARTLY, IdeaCoverage.NOT_YET), judged.statuses)
        assertEquals("Good start.", judged.summary)
        assertEquals("What changes with more substrate?", judged.nextQuestion)
        assertEquals(false, judged.overallCovered)
    }

    @Test
    fun `a criterion the reply leaves out is not yet covered, not guessed`() {
        val evaluation = TeachBackEvaluation(
            overallCoverage = TeachBackCoverage.COVERED,
            scorePercent = 90,
            summary = "Excellent.",
            criteriaEvaluations = emptyList(),
            missingClaims = emptyList()
        )
        val judged = EngineTeachBackAssessor.judged(rubric, evaluation)
        assertTrue(judged.statuses.all { it == IdeaCoverage.NOT_YET })
        assertTrue(judged.overallCovered)
        assertNull(judged.nextQuestion)
    }

    @Test
    fun `attempts are tagged as the teach-back format`() {
        assertEquals(PracticeFormat.TagPrefix + "teach_back", StudyExplainAttemptStore.FORMAT_TAG)
    }
}
