package com.craftflowtechnologies.meetingmind.feature.work.home

/** The sentences and labels the Work home derives from counts, as plain functions for JVM tests. */
object WorkHomeCopy {
    /** A project with no activity for this long, and work still open, reads as quiet. */
    const val QUIET_MS = 14L * 86_400_000L

    /** Projects with this many open tasks or more read as needing attention. */
    const val BUSY_OPEN_TASKS = 5

    fun plural(count: Int, word: String): String = "$count $word" + if (count == 1) "" else "s"

    /**
     * The one human sentence under "Your day": what the day holds, then what needs the person.
     * Example: "Two meetings, 5 tasks due and 1 overdue. One thing needs you."
     */
    fun summarySentence(meetings: Int, tasksDueToday: Int, overdue: Int, needYou: Int): String {
        val parts = listOfNotNull(
            meetings.takeIf { it > 0 }?.let { plural(it, "meeting") },
            tasksDueToday.takeIf { it > 0 }?.let { plural(it, "task") + " due" },
            overdue.takeIf { it > 0 }?.let { "$it overdue" }
        )
        if (parts.isEmpty() && needYou <= 0) return "A clear day so far. Record a conversation when one starts."
        val head = parts.joinToString(", ").replaceFirstChar { it.uppercase() }.let { if (it.isEmpty()) "" else "$it." }
        val tail = when {
            needYou <= 0 -> ""
            needYou == 1 -> "One thing needs you."
            else -> "$needYou things need you."
        }
        return listOf(head, tail).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** The status pill on a project card, from its open work and how recently it was touched. */
    fun projectStatus(openTasks: Int, lastActivity: Long?, now: Long): String = when {
        openTasks > 0 && lastActivity != null && now - lastActivity > QUIET_MS -> "Quiet"
        openTasks >= BUSY_OPEN_TASKS -> "Needs attention"
        openTasks > 0 -> "In progress"
        else -> "Clear"
    }

    /**
     * The badge on a person in the People strip: "you owe" when you owe them something, "owes" when
     * they owe you. If both, what you owe comes first, because it is the one you can act on.
     */
    fun peopleBadge(personId: String, owedToMe: Set<String>, iOwe: Set<String>): String? = when {
        personId in iOwe -> "you owe"
        personId in owedToMe -> "owes"
        else -> null
    }
}
