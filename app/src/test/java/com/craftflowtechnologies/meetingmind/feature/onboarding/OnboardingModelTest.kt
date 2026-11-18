package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingModelTest {
    @Test fun `the example route is welcome, companion, spaces, the example and how MeetingMind thinks`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.COMPANION, OnboardingStep.SPACES, OnboardingStep.SAMPLE, OnboardingStep.SETUP),
            stepsFor(EntryRoute.SAMPLE)
        )
    }

    @Test fun `the record route is the welcome, the companion and the AI choice`() {
        assertEquals(listOf(OnboardingStep.WELCOME, OnboardingStep.COMPANION, OnboardingStep.SETUP), stepsFor(EntryRoute.RECORD))
    }

    @Test fun `the companion pick is step 1 on both routes`() {
        EntryRoute.entries.forEach { route -> assertEquals(OnboardingStep.COMPANION, stepsFor(route)[1]) }
    }

    @Test fun `the four spaces are offered and all start on`() {
        assertEquals(listOf(NotebookSpace.WORK, NotebookSpace.LEARNING, NotebookSpace.FAITH, NotebookSpace.PERSONAL), OnboardingSpaces)
        assertEquals(NotebookSpace.entries.toSet(), OnboardingSpaces.toSet())
    }

    @Test fun `a space toggles, but the last one can't be turned off`() {
        val all = OnboardingSpaces.toSet()
        assertFalse(NotebookSpace.FAITH in toggleSpace(all, NotebookSpace.FAITH))
        assertTrue(NotebookSpace.FAITH in toggleSpace(all - NotebookSpace.FAITH, NotebookSpace.FAITH))
        assertEquals(setOf(NotebookSpace.WORK), toggleSpace(setOf(NotebookSpace.WORK), NotebookSpace.WORK))
    }

    @Test fun `the example follows the spaces`() {
        assertEquals(SampleKind.MEETING, sampleKindFor(OnboardingSpaces.toSet()))
        assertEquals(SampleKind.SERMON, sampleKindFor(setOf(NotebookSpace.FAITH)))
        assertEquals(SampleKind.SERMON, sampleKindFor(setOf(NotebookSpace.FAITH, NotebookSpace.PERSONAL)))
        assertEquals(SampleKind.LECTURE, sampleKindFor(setOf(NotebookSpace.LEARNING)))
        assertEquals(SampleKind.MEETING, sampleKindFor(setOf(NotebookSpace.WORK)))
    }

    @Test fun `saved progress resumes only for this onboarding version`() {
        assertEquals(OnboardingStep.SPACES, restoredStep("SPACES", ONBOARDING_VERSION))
        assertEquals(OnboardingStep.WELCOME, restoredStep("SPACES", ONBOARDING_VERSION - 1))
        // A step from the previous design (no longer a step) restarts cleanly at the welcome.
        assertEquals(OnboardingStep.WELCOME, restoredStep("GOALS", ONBOARDING_VERSION))
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
