package com.craftflowtechnologies.meetingmind.feature.learn.explain

import android.app.Application
import com.craftflowtechnologies.meetingmind.ai.cloud.UsageMeter
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackEvaluation
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackCoverage
import com.craftflowtechnologies.meetingmind.ai.learning.TeachBackStatus
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learning.space.PracticeFormat
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonAi
import java.util.UUID

/** What the model said, or why it could not say anything. */
sealed interface ExplainAssessment {
    /** The model answered. [statuses] are in rubric order. */
    data class Judged(
        val statuses: List<IdeaCoverage>,
        val summary: String,
        val nextQuestion: String?,
        val overallCovered: Boolean
    ) : ExplainAssessment

    /** No model answered: no AI is set up, or the device cannot run one. */
    data object AiUnavailable : ExplainAssessment

    /** The managed cloud allowance for this month is used up. */
    data object AllowanceUsed : ExplainAssessment
}

/** Judges a spoken or typed explanation against the rubric, through the app's own model route. */
interface TeachBackAssessor {
    suspend fun assess(rubric: List<TeachBackCriterion>, transcript: String, concept: LearningConcept): ExplainAssessment
}

/**
 * The production assessor: the same model resolution Learning and Ask use (managed proxy, personal key or
 * on-device), so the call is metered like other AI. Returns [ExplainAssessment.AiUnavailable] rather than
 * a keyword guess when no model answers.
 */
class EngineTeachBackAssessor(private val application: Application) : TeachBackAssessor {
    override suspend fun assess(
        rubric: List<TeachBackCriterion>,
        transcript: String,
        concept: LearningConcept
    ): ExplainAssessment {
        val evaluation = LessonAi.engine(application).evaluateTeachBackWithAi(rubric, transcript, concept)
        if (evaluation == null) {
            return if (UsageMeter.state.value.atLimit) ExplainAssessment.AllowanceUsed else ExplainAssessment.AiUnavailable
        }
        return judged(rubric, evaluation)
    }

    companion object {
        /** Maps the engine's per-criterion statuses onto the rubric order. A criterion the reply left out is not yet covered. */
        fun judged(rubric: List<TeachBackCriterion>, evaluation: TeachBackEvaluation): ExplainAssessment.Judged {
            val byClaim = evaluation.criteriaEvaluations.associateBy { it.criterion.claim }
            val statuses = rubric.map { c ->
                when (byClaim[c.claim]?.status) {
                    TeachBackStatus.MET -> IdeaCoverage.COVERED
                    TeachBackStatus.PARTIAL -> IdeaCoverage.PARTLY
                    else -> IdeaCoverage.NOT_YET
                }
            }
            return ExplainAssessment.Judged(
                statuses = statuses,
                summary = evaluation.summary,
                nextQuestion = evaluation.socraticFollowUp?.takeIf { it.isNotBlank() },
                overallCovered = evaluation.overallCoverage == TeachBackCoverage.COVERED
            )
        }
    }
}

/** Saves an explanation as a learning attempt, so mastery and the sky see it. The audio is never saved. */
interface ExplainAttemptStore {
    suspend fun saveJudged(concept: LearningConcept, rubric: List<TeachBackCriterion>, transcript: String, covered: Boolean)
    suspend fun saveSelfRated(concept: LearningConcept, rubric: List<TeachBackCriterion>, transcript: String, rating: RecallButton)
}

/** Writes the attempt to the existing activity tables, under a Teach-Back activity for the concept. */
class StudyExplainAttemptStore(private val services: StudyServices) : ExplainAttemptStore {

    override suspend fun saveJudged(concept: LearningConcept, rubric: List<TeachBackCriterion>, transcript: String, covered: Boolean) {
        record(concept, rubric, transcript, isCorrect = covered, rating = if (covered) RecallRating.GOOD else RecallRating.AGAIN)
    }

    override suspend fun saveSelfRated(concept: LearningConcept, rubric: List<TeachBackCriterion>, transcript: String, rating: RecallButton) {
        record(concept, rubric, transcript, isCorrect = rating != RecallButton.MISSED, rating = rating.rating)
    }

    private suspend fun record(
        concept: LearningConcept,
        rubric: List<TeachBackCriterion>,
        transcript: String,
        isCorrect: Boolean,
        rating: RecallRating
    ) {
        val activityId = teachBackActivityId(concept, rubric)
        services.learning.recordAttempt(
            activityId = activityId,
            userResponse = transcript,
            isCorrect = isCorrect,
            selfRating = rating,
            feedback = FORMAT_TAG
        )
    }

    private suspend fun teachBackActivityId(concept: LearningConcept, rubric: List<TeachBackCriterion>): String {
        services.learning.getActivitiesForConcept(concept.id)
            .firstOrNull { it.type == LearningActivityType.TEACH_BACK }
            ?.let { return it.id }
        val now = services.now()
        val activity = LearningActivity(
            id = "act_${UUID.randomUUID()}",
            sessionId = concept.sessionId,
            conceptId = concept.id,
            type = LearningActivityType.TEACH_BACK,
            prompt = "Explain ${concept.name} as if to a friend.",
            expectedAnswer = concept.definition,
            difficulty = "HARD",
            createdAt = now,
            updatedAt = now,
            rubric = rubric
        )
        services.learning.saveActivities(listOf(activity))
        return activity.id
    }

    companion object {
        /** Written to the attempt's feedback, next to the other formats' tags. */
        val FORMAT_TAG: String = PracticeFormat.TagPrefix + "teach_back"
    }
}
