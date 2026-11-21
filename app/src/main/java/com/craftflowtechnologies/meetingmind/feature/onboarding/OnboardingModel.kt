package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/** Bump when the onboarding flow changes shape: saved progress from another version restarts at the welcome. */
const val ONBOARDING_VERSION = 4

/**
 * The onboarding steps (launch redesign, founder-approved 2026-11-17, `design/Launch Experience Redesign.dc.html`
 * turn 1, with the founder's changes):
 *
 * - [COMPANION]: pick who keeps you company, on both routes (the companion is part of onboarding).
 * - [SPACES]: the four spaces, all selected by default; one question instead of goals, outcomes and a work profile.
 * - [SAMPLE]: the example becoming notes, ending in Zuri's First Keep reveal (the hello moment).
 * - [SETUP]: how MeetingMind thinks, Internet or on this phone. Always saved; no model catalogue here.
 * - [READY]: "You're all set": what was chosen, an optional Sign in with Google for Circles, and the first thing to
 *   do (record, a YouTube video's notes, or the on-phone download), one tap away.
 *
 * Permissions are asked where they are used (the recorder asks for the microphone, the processing screen for
 * notifications), and the Bible and reading plan are set up in Faith, so neither is an onboarding step.
 */
enum class OnboardingStep { WELCOME, COMPANION, SPACES, SAMPLE, SETUP, READY }

/** How the person chose to start. */
enum class EntryRoute { SAMPLE, RECORD }

/**
 * The steps for a route. Both start with the companion. The example route then asks which spaces to keep, shows
 * the example (with the hello First Keep), sets how the AI runs and ends on "You're all set". The record route goes
 * from the companion straight to the AI choice and then the recorder, because a first recording that can't be
 * transcribed would miss the point. Adding [OnboardingStep.READY] at the end leaves saved progress valid, so
 * [ONBOARDING_VERSION] stays.
 */
fun stepsFor(route: EntryRoute): List<OnboardingStep> = when (route) {
    EntryRoute.RECORD -> listOf(OnboardingStep.WELCOME, OnboardingStep.COMPANION, OnboardingStep.SETUP)
    EntryRoute.SAMPLE -> listOf(OnboardingStep.WELCOME, OnboardingStep.COMPANION, OnboardingStep.SPACES, OnboardingStep.SAMPLE, OnboardingStep.SETUP, OnboardingStep.READY)
}

/** The spaces onboarding offers, in the order they are shown. All four start selected. */
val OnboardingSpaces: List<NotebookSpace> = listOf(NotebookSpace.WORK, NotebookSpace.LEARNING, NotebookSpace.FAITH, NotebookSpace.PERSONAL)

/** Turns one space on or off. The last selected space can't be turned off: MeetingMind needs at least one. */
fun toggleSpace(current: Set<NotebookSpace>, space: NotebookSpace): Set<NotebookSpace> = when {
    space !in current -> current + space
    current.size <= 1 -> current
    else -> current - space
}

/** The example that fits the spaces: a sermon when Faith is the only space, a lecture for Learning alone, else a meeting. */
fun sampleKindFor(spaces: Set<NotebookSpace>): SampleKind = when (spaces) {
    setOf(NotebookSpace.FAITH), setOf(NotebookSpace.FAITH, NotebookSpace.PERSONAL) -> SampleKind.SERMON
    setOf(NotebookSpace.LEARNING), setOf(NotebookSpace.LEARNING, NotebookSpace.PERSONAL) -> SampleKind.LECTURE
    else -> SampleKind.MEETING
}

/** The saved step, or the welcome when it is missing, unreadable, or from another onboarding version. */
fun restoredStep(saved: String?, savedVersion: Int): OnboardingStep =
    if (savedVersion != ONBOARDING_VERSION) OnboardingStep.WELCOME
    else OnboardingStep.entries.firstOrNull { it.name == saved } ?: OnboardingStep.WELCOME
