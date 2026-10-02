package com.craftflowtechnologies.meetingmind.feature.learning

import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyWithMeComposerTest {
    @Test fun `prioritizes needs review and bounds fifteen minute recall`() {
        val focus = concept("weak", LearningMasteryState.NEEDS_REVIEW)
        val due = (1..5).map { activity("a$it", focus.id) }
        val plan = StudyWithMeComposer.compose(15, due, listOf(concept("strong", LearningMasteryState.STRONG), focus), due)
        assertEquals(focus.id, plan.focusConcept?.id)
        assertEquals(3, plan.recallActivities.size)
    }

    @Test fun `empty queues are calm and do not invent a challenge`() {
        val plan = StudyWithMeComposer.compose(25, emptyList(), emptyList(), emptyList())
        assertFalse(plan.hasWork)
        assertTrue(plan.emptyState!!.contains("nothing due"))
    }

    private fun concept(id: String, state: LearningMasteryState) = LearningConcept(id, "session", id, "definition", state = state, createdAt = 1, updatedAt = 1)
    private fun activity(id: String, conceptId: String) = LearningActivity(id, "session", conceptId, LearningActivityType.RECALL, "Prompt $id", "answer", createdAt = 1, updatedAt = 1)
}
