package com.craftflowtechnologies.meetingmind.feature.onboarding

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/** Bump when the onboarding flow changes shape: saved progress from another version restarts at the welcome. */
const val ONBOARDING_VERSION = 3

/**
 * The onboarding steps. [COMPANION] is step 1 of the flow on both routes (ZURI_EXPERIENCE §5.1: bond and
 * ownership before anything is asked, progress shown filled from step 1). [PLAN] ("Choose how you read") and
 * [BIBLE] only appear for people who use Faith.
 */
enum class OnboardingStep { WELCOME, COMPANION, SAMPLE, GOALS, SETUP, BIBLE, PLAN, PERMISSIONS }

/** How the person chose to start. */
enum class EntryRoute { SAMPLE, RECORD }

/** What the person captures. Several can be picked; each turns on the space it belongs to. */
enum class CaptureGoal(val label: String, val space: NotebookSpace) {
    WORK_MEETINGS("Work meetings", NotebookSpace.WORK),
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
 * The steps for a route. Both routes start by picking who keeps the person company. The sample route
 * then shows the example (which plays the hello First Keep), asks what the person captures, then sets up
 * the AI. The Bible and reading steps only appear for people who use Faith. The record route goes from
 * the companion to the AI choice, because a first recording that can't be transcribed would miss the
 * point; the microphone is asked for by the recorder itself.
 */
fun stepsFor(route: EntryRoute, spaces: Set<NotebookSpace>): List<OnboardingStep> = when (route) {
    // The record route still ends on permissions: the microphone is asked for by the recorder
    // itself, but notifications (the only way a person learns their notes are ready) are asked for
    // here, with the reason, rather than never.
    EntryRoute.RECORD -> listOf(OnboardingStep.WELCOME, OnboardingStep.COMPANION, OnboardingStep.SETUP, OnboardingStep.PERMISSIONS)
    EntryRoute.SAMPLE -> buildList {
        add(OnboardingStep.WELCOME)
        add(OnboardingStep.COMPANION)
        add(OnboardingStep.SAMPLE)
        add(OnboardingStep.GOALS)
        add(OnboardingStep.SETUP)
        if (NotebookSpace.FAITH in spaces) {
            add(OnboardingStep.BIBLE)
            add(OnboardingStep.PLAN)
        }
        add(OnboardingStep.PERMISSIONS)
    }
}

/**
 * The spaces onboarding may offer. Learning is cut from the MVP and its space is never turned on by
 * this flow — stored legacy data for it is left alone, not migrated or deleted.
 */
val OfferedNotebookSpaces: Set<NotebookSpace> = NotebookSpace.entries.toSet() - NotebookSpace.LEARNING

/** Goals pick the spaces; no goals picked leaves every offered space on, as before. */
fun spacesFor(goals: Set<CaptureGoal>): Set<NotebookSpace> =
    if (goals.isEmpty()) OfferedNotebookSpaces else goals.map { it.space }.toSet()

/** The saved step, or the welcome when it is missing, unreadable, or from another onboarding version. */
fun restoredStep(saved: String?, savedVersion: Int): OnboardingStep =
    if (savedVersion != ONBOARDING_VERSION) OnboardingStep.WELCOME
    else OnboardingStep.entries.firstOrNull { it.name == saved } ?: OnboardingStep.WELCOME
