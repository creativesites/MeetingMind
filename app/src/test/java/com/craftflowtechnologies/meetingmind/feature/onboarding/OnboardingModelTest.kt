package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingModelTest {
    @Test fun `the record route is the welcome, the companion, the AI choice and the permissions ask`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.COMPANION, OnboardingStep.SETUP, OnboardingStep.PERMISSIONS),
            stepsFor(EntryRoute.RECORD, NotebookSpace.entries.toSet())
        )
    }

    @Test fun `learning is not offered in onboarding`() {
        // Cut from the MVP: the goal list must not offer it, and skipping goals must not switch its
        // space on. Stored legacy data for the space is untouched.
        assertEquals(
            listOf("WORK_MEETINGS", "SERMONS", "REFLECTIONS", "INTERVIEWS", "IDEAS"),
            CaptureGoal.entries.map { it.name }
        )
        assertFalse(NotebookSpace.LEARNING in OfferedNotebookSpaces)
        assertFalse(NotebookSpace.LEARNING in spacesFor(emptySet()))
    }

    @Test fun `the companion pick is step 1 on both routes`() {
        EntryRoute.entries.forEach { route -> assertEquals(OnboardingStep.COMPANION, stepsFor(route, NotebookSpace.entries.toSet())[1]) }
    }

    @Test fun `the bible and reading steps only appear for Faith`() {
        val withFaith = stepsFor(EntryRoute.SAMPLE, setOf(NotebookSpace.FAITH))
        val without = stepsFor(EntryRoute.SAMPLE, setOf(NotebookSpace.WORK))
        assertTrue(OnboardingStep.BIBLE in withFaith && OnboardingStep.PLAN in withFaith)
        assertFalse(OnboardingStep.BIBLE in without || OnboardingStep.PLAN in without)
        assertEquals(OnboardingStep.PERMISSIONS, without.last())
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.COMPANION, OnboardingStep.SAMPLE, OnboardingStep.GOALS, OnboardingStep.SETUP),
            without.take(5)
        )
    }

    @Test fun `goals pick spaces, and no goals leaves every offered space on`() {
        assertEquals(NotebookSpace.entries.toSet() - NotebookSpace.LEARNING, spacesFor(emptySet()))
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
