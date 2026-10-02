package com.craftflowtechnologies.meetingmind.feature.learning

import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState

/** A bounded, local composition plan; it does not schedule notifications or fabricate progress. */
data class StudyWithMePlan(
    val minutes: Int,
    val orientation: String,
    val recallActivities: List<LearningActivity>,
    val focusConcept: LearningConcept?,
    val challenge: LearningActivity?,
    val emptyState: String? = null
) {
    val hasWork: Boolean get() = recallActivities.isNotEmpty() || challenge != null
}

object StudyWithMeComposer {
    private const val MAX_RECALL_15 = 3
    private const val MAX_RECALL_25 = 5

    fun compose(
        minutes: Int,
        dueActivities: List<LearningActivity>,
        concepts: List<LearningConcept>,
        allActivities: List<LearningActivity>
    ): StudyWithMePlan {
        require(minutes == 15 || minutes == 25) { "Study With Me supports 15 or 25 minutes." }
        val activeDue = dueActivities.filter { !it.isDismissed && !it.isStale }
        val focus = concepts.filter { !it.isDismissed }.minByOrNull { priority(it.state) }
        val recall = activeDue.take(if (minutes == 15) MAX_RECALL_15 else MAX_RECALL_25)
        val challenge = allActivities.firstOrNull { activity ->
            !activity.isDismissed && !activity.isStale && activity.id !in recall.map { it.id } &&
                (focus == null || activity.conceptId == focus.id)
        }
        val empty = if (recall.isEmpty() && challenge == null) {
            "There is nothing due right now. Choose a concept to review when you are ready."
        } else null
        val orientation = when {
            focus != null && recall.isNotEmpty() -> "Start with ${recall.size} short recall ${if (recall.size == 1) "prompt" else "prompts"}, then focus on ${focus.name}."
            focus != null -> "Use this quiet session to review ${focus.name}, then try one short challenge."
            else -> "Take this at your own pace. You can pause or exit at any time."
        }
        return StudyWithMePlan(minutes, orientation, recall, focus, challenge, empty)
    }

    private fun priority(state: LearningMasteryState) = when (state) {
        LearningMasteryState.NEEDS_REVIEW -> 0
        LearningMasteryState.LEARNING -> 1
        LearningMasteryState.NEW -> 2
        LearningMasteryState.DEVELOPING -> 3
        LearningMasteryState.STRONG -> 4
    }
}
