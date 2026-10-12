package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/** Bump when the onboarding flow changes shape: saved progress from another version restarts at the welcome. */
const val ONBOARDING_VERSION = 2

enum class OnboardingStep { WELCOME, SAMPLE, GOALS, SETUP, BIBLE, PERMISSIONS }

/** How the person chose to start. */
enum class EntryRoute { SAMPLE, RECORD }

/** What the person captures. Several can be picked; each turns on the space it belongs to. */
enum class CaptureGoal(val label: String, val space: NotebookSpace) {
    WORK_MEETINGS("Work meetings", NotebookSpace.WORK),
    LEARNING("Learning and research", NotebookSpace.LEARNING),
    SERMONS("Sermons and Bible study", NotebookSpace.FAITH),
    REFLECTIONS("Personal reflections", NotebookSpace.PERSONAL),
    INTERVIEWS("Interviews and field research", NotebookSpace.WORK),
    IDEAS("Ideas and everyday conversations", NotebookSpace.PERSONAL)
}

/** What the person wants back from a recording. Stored for personalization; it never hides a feature. */
enum class DesiredOutcome(val label: String) {
    SUMMARIES("Clear summaries"),
    STRUCTURED_NOTES("Structured notes"),
    ACTION_ITEMS("Action items and commitments"),
    SEARCHABLE("Searchable knowledge"),
    QUESTIONS("Questions and answers"),
    SPEAKERS("Speaker identification")
}

/**
 * The steps for a route. The sample route shows the example, asks what the person captures, then sets
 * up the AI. The Bible step only appears for people who use Faith. The record route skips straight to
 * the AI choice, because a first recording that can't be transcribed would miss the point; the
 * microphone is asked for by the recorder itself.
 */
fun stepsFor(route: EntryRoute, spaces: Set<NotebookSpace>): List<OnboardingStep> = when (route) {
    EntryRoute.RECORD -> listOf(OnboardingStep.WELCOME, OnboardingStep.SETUP)
    EntryRoute.SAMPLE -> buildList {
        add(OnboardingStep.WELCOME)
        add(OnboardingStep.SAMPLE)
        add(OnboardingStep.GOALS)
        add(OnboardingStep.SETUP)
        if (NotebookSpace.FAITH in spaces) add(OnboardingStep.BIBLE)
        add(OnboardingStep.PERMISSIONS)
    }
}

/** Goals pick the spaces; no goals picked leaves every space on, as before. */
fun spacesFor(goals: Set<CaptureGoal>): Set<NotebookSpace> =
    if (goals.isEmpty()) NotebookSpace.entries.toSet() else goals.map { it.space }.toSet()

/** The saved step, or the welcome when it is missing, unreadable, or from another onboarding version. */
fun restoredStep(saved: String?, savedVersion: Int): OnboardingStep =
    if (savedVersion != ONBOARDING_VERSION) OnboardingStep.WELCOME
    else OnboardingStep.entries.firstOrNull { it.name == saved } ?: OnboardingStep.WELCOME
