package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion

enum class TeachBackStatus(val label: String) {
    MET("Met"),
    PARTIAL("Partial"),
    UNMET("Unmet")
}

enum class TeachBackCoverage(val label: String) {
    COVERED("Covered"),
    PARTLY_COVERED("Partly covered"),
    NOT_YET_DEMONSTRATED("Not yet demonstrated")
}

data class TeachBackCriterionEvaluation(
    val criterion: TeachBackCriterion,
    val status: TeachBackStatus,
    val feedback: String,
    val citedEvidence: String? = null
)

data class TeachBackEvaluation(
    val overallCoverage: TeachBackCoverage,
    val scorePercent: Int,
    val summary: String,
    val criteriaEvaluations: List<TeachBackCriterionEvaluation>,
    val missingClaims: List<String>,
    val socraticFollowUp: String? = null,
    val evidenceStartMs: Long? = null
)

data class MistakeExplanation(
    val studentMistake: String,
    val rootMisconception: String,
    val correctMentalModel: String,
    val stepByStepCorrection: String,
    val lectureStartMs: Long? = null
)

enum class EnrichmentKind(val label: String) {
    UNPACK_STEPS("Unpack skipped steps"),
    WORKED_EXAMPLE("Worked example"),
    PREREQUISITE_BRIDGE("Prerequisite bridge"),
    INTUITION("Formula intuition")
}

data class EnrichmentResult(
    val kind: EnrichmentKind,
    val label: String,
    val blocks: List<NoteBlock>
)

data class LectureSynthesis(
    val summary: String,
    val concepts: List<LearningConcept>,
    val activities: List<LearningActivity>,
    val examHints: List<String> = emptyList(),
    val confusionUnpacks: List<String> = emptyList()
)
