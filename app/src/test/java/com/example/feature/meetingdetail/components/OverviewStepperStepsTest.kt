package com.example.feature.meetingdetail.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [buildOverviewSteps] decides which Overview steps a recording actually gets (Phase 15 §7) —
 * pulled out of the [OverviewStepper] composable specifically so this is unit-testable without a
 * Compose test harness. What Happened/Topics/Who Talked are always present; Decisions/Tasks/
 * Questions only appear when the recording type's [com.example.core.model.IntelligenceProfile]
 * actually extracts them.
 */
class OverviewStepperStepsTest {

    @Test
    fun `with nothing gated on, only the three always-present steps appear`() {
        val steps = buildOverviewSteps(showDecisionsStep = false, showTasksStep = false, showQuestionsStep = false)

        assertEquals(
            listOf(OverviewStepTarget.WHAT_HAPPENED, OverviewStepTarget.TOPICS, OverviewStepTarget.WHO_TALKED),
            steps
        )
    }

    @Test
    fun `with everything gated on, all six steps appear in a fixed order`() {
        val steps = buildOverviewSteps(showDecisionsStep = true, showTasksStep = true, showQuestionsStep = true)

        assertEquals(
            listOf(
                OverviewStepTarget.WHAT_HAPPENED,
                OverviewStepTarget.DECISIONS,
                OverviewStepTarget.TASKS,
                OverviewStepTarget.QUESTIONS,
                OverviewStepTarget.TOPICS,
                OverviewStepTarget.WHO_TALKED
            ),
            steps
        )
    }

    @Test
    fun `each gate independently controls only its own step`() {
        assertEquals(
            listOf(OverviewStepTarget.WHAT_HAPPENED, OverviewStepTarget.DECISIONS, OverviewStepTarget.TOPICS, OverviewStepTarget.WHO_TALKED),
            buildOverviewSteps(showDecisionsStep = true, showTasksStep = false, showQuestionsStep = false)
        )
        assertEquals(
            listOf(OverviewStepTarget.WHAT_HAPPENED, OverviewStepTarget.TASKS, OverviewStepTarget.TOPICS, OverviewStepTarget.WHO_TALKED),
            buildOverviewSteps(showDecisionsStep = false, showTasksStep = true, showQuestionsStep = false)
        )
        assertEquals(
            listOf(OverviewStepTarget.WHAT_HAPPENED, OverviewStepTarget.QUESTIONS, OverviewStepTarget.TOPICS, OverviewStepTarget.WHO_TALKED),
            buildOverviewSteps(showDecisionsStep = false, showTasksStep = false, showQuestionsStep = true)
        )
    }

    @Test
    fun `topics is never gated off, unlike decisions, tasks and questions`() {
        val steps = buildOverviewSteps(showDecisionsStep = false, showTasksStep = false, showQuestionsStep = false)
        assertEquals(true, steps.contains(OverviewStepTarget.TOPICS))
    }
}
