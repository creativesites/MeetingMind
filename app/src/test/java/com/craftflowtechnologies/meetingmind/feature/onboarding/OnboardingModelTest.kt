package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingModelTest {
    @Test fun `the record route is just the welcome and the AI choice`() {
        assertEquals(listOf(OnboardingStep.WELCOME, OnboardingStep.SETUP), stepsFor(EntryRoute.RECORD, NotebookSpace.entries.toSet()))
    }

    @Test fun `the bible step only appears for Faith`() {
        val withFaith = stepsFor(EntryRoute.SAMPLE, setOf(NotebookSpace.FAITH))
        val without = stepsFor(EntryRoute.SAMPLE, setOf(NotebookSpace.WORK))
        assertTrue(OnboardingStep.BIBLE in withFaith)
        assertFalse(OnboardingStep.BIBLE in without)
        assertEquals(OnboardingStep.PERMISSIONS, without.last())
        assertEquals(listOf(OnboardingStep.WELCOME, OnboardingStep.SAMPLE, OnboardingStep.GOALS, OnboardingStep.SETUP), without.take(4))
    }

    @Test fun `goals pick spaces, and no goals leaves every space on`() {
        assertEquals(NotebookSpace.entries.toSet(), spacesFor(emptySet()))
        assertEquals(setOf(NotebookSpace.WORK, NotebookSpace.FAITH), spacesFor(setOf(CaptureGoal.WORK_MEETINGS, CaptureGoal.INTERVIEWS, CaptureGoal.SERMONS)))
    }

    @Test fun `saved progress resumes only for this onboarding version`() {
        assertEquals(OnboardingStep.GOALS, restoredStep("GOALS", ONBOARDING_VERSION))
        assertEquals(OnboardingStep.WELCOME, restoredStep("GOALS", ONBOARDING_VERSION - 1))
        assertEquals(OnboardingStep.WELCOME, restoredStep("NOT_A_STEP", ONBOARDING_VERSION))
        assertEquals(OnboardingStep.WELCOME, restoredStep(null, ONBOARDING_VERSION))
    }

    @Test fun `every example has a transcript, notes and an answer`() {
        SampleKind.entries.forEach { kind ->
            val s = SampleContent.of(kind)
            assertTrue(s.lines.size >= 5)
            assertTrue(s.actions.isNotEmpty() && s.summary.isNotBlank() && s.question.isNotBlank() && s.answer.isNotBlank())
        }
    }
}
