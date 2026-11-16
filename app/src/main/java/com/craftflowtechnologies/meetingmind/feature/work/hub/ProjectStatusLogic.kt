package com.craftflowtechnologies.meetingmind.feature.work.hub

/*
 * A project's status comes from counts, never from a guess (spec §6, Projects): past-due promises, open risks and
 * decisions still proposed. The reason line says which count decided it, in plain words.
 */

/** How the project reads at a glance; the pill colour follows it. */
enum class ProjectStatusKind { NEEDS_ATTENTION, RISKS_OPEN, DECISION_PENDING, QUIET, ON_TRACK }

/** The counts behind one project's status. Zero means nothing to say. */
data class ProjectCounts(val pastDuePromises: Int = 0, val openRisks: Int = 0, val pendingDecisions: Int = 0)

/** The status pill ([label]) and the sentence under it ([reason]). */
data class ProjectStatus(val kind: ProjectStatusKind, val label: String, val reason: String)

/** A project with no activity for this many days reads as quiet. */
const val PROJECT_QUIET_DAYS = 15

private const val DAY_MS = 86_400_000L

/**
 * Past-due promises outrank open risks, which outrank decisions to confirm. With none of those, a project quiet for
 * [PROJECT_QUIET_DAYS] reads as quiet, and anything else is on track.
 */
fun projectStatus(counts: ProjectCounts, lastActivity: Long?, now: Long): ProjectStatus = when {
    counts.pastDuePromises > 0 -> ProjectStatus(
        ProjectStatusKind.NEEDS_ATTENTION, "Needs attention",
        "${plural(counts.pastDuePromises, "promise")} past due"
    )
    counts.openRisks > 0 -> ProjectStatus(
        ProjectStatusKind.RISKS_OPEN, "Risk open",
        "${plural(counts.openRisks, "open risk", "open risks")}"
    )
    counts.pendingDecisions > 0 -> ProjectStatus(
        ProjectStatusKind.DECISION_PENDING, "To confirm",
        "${plural(counts.pendingDecisions, "decision")} not confirmed yet"
    )
    lastActivity == null -> ProjectStatus(ProjectStatusKind.QUIET, "No activity yet", "Nothing recorded for this project")
    now - lastActivity >= PROJECT_QUIET_DAYS * DAY_MS -> ProjectStatus(
        ProjectStatusKind.QUIET, "Quiet",
        "No activity for ${(now - lastActivity) / DAY_MS} days"
    )
    else -> ProjectStatus(ProjectStatusKind.ON_TRACK, "On track", "Nothing slipping")
}

private fun plural(n: Int, one: String, many: String = "${one}s"): String = if (n == 1) "1 $one" else "$n $many"
