package com.craftflowtechnologies.meetingmind.feature.tasks

import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Tasks home's smart views. */
enum class SmartView(val label: String) {
    TODAY("Today"),
    UPCOMING("Upcoming"),
    INBOX("Inbox"),
    WAITING("Waiting on"),
    PRIORITY_1("Priority 1"),
    COMPLETED("Completed")
}

/**
 * The rules for what each view shows, and how dates read. Pure: "today" and the zone are passed
 * in, so every rule can be tested with a pinned clock.
 */
object TaskViews {

    /** The estimate the Today header uses for each open task. */
    const val MINUTES_PER_TASK = 25

    /** Top-level tasks only: a subtask is listed under its parent, not on its own. */
    fun roots(tasks: List<Task>): List<Task> = tasks.filter { it.parentId == null }

    fun open(tasks: List<Task>): List<Task> = roots(tasks).filter { !it.done }

    fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun dayOf(task: Task, zone: ZoneId): LocalDate? = task.dueAt?.let { dayOf(it, zone) }

    /** Work is a task whose own space is WORK, or whose list is a Work notebook. */
    fun isWork(task: Task, lists: List<TaskList>): Boolean {
        if (task.space == "WORK") return true
        val listId = task.listId ?: return false
        return lists.any { it.id == listId && it.space == "WORK" }
    }

    fun scoped(tasks: List<Task>, scope: TaskScope, lists: List<TaskList>): List<Task> =
        if (scope == TaskScope.ALL) tasks else tasks.filter { isWork(it, lists) }

    /** Open tasks dated before [today]. They never move on their own. */
    fun pastDue(tasks: List<Task>, today: LocalDate, zone: ZoneId): List<Task> =
        open(tasks).filter { dayOf(it, zone)?.isBefore(today) == true }.sortedWith(byDue)

    /** Open tasks dated exactly [day]. */
    fun dueOn(tasks: List<Task>, day: LocalDate, zone: ZoneId): List<Task> =
        open(tasks).filter { dayOf(it, zone) == day }

    /** Today's list: everything open that is dated today or earlier. */
    fun dueToday(tasks: List<Task>, today: LocalDate, zone: ZoneId): List<Task> =
        pastDue(tasks, today, zone) + dueOn(tasks, today, zone).sortedWith(byDue)

    /** Dated today with a time of day, in time order. */
    fun scheduledToday(tasks: List<Task>, today: LocalDate, zone: ZoneId): List<Task> =
        dueOn(tasks, today, zone).filter { it.hasTime }.sortedBy { it.dueAt }

    /** Dated today with no time of day. */
    fun anytimeToday(tasks: List<Task>, today: LocalDate, zone: ZoneId): List<Task> =
        dueOn(tasks, today, zone).filter { !it.hasTime }.sortedWith(byPriority)

    /** Open tasks dated after today, nearest first. */
    fun upcoming(tasks: List<Task>, today: LocalDate, zone: ZoneId): List<Task> =
        open(tasks).filter { dayOf(it, zone)?.isAfter(today) == true }.sortedWith(byDue)

    /** Open tasks with no list: quick adds not yet sorted. */
    fun inbox(tasks: List<Task>): List<Task> = open(tasks).filter { it.listId == null }.sortedByDescending { it.createdAt }

    fun waiting(tasks: List<Task>): List<Task> = open(tasks).filter { it.waitingOn }.sortedWith(byDue)

    fun priorityOne(tasks: List<Task>): List<Task> = open(tasks).filter { it.priority == 1 }.sortedWith(byDue)

    fun completed(tasks: List<Task>): List<Task> = roots(tasks).filter { it.done }.sortedByDescending { it.doneAt ?: 0L }

    fun viewOf(view: SmartView, tasks: List<Task>, today: LocalDate, zone: ZoneId): List<Task> = when (view) {
        SmartView.TODAY -> dueToday(tasks, today, zone)
        SmartView.UPCOMING -> upcoming(tasks, today, zone)
        SmartView.INBOX -> inbox(tasks)
        SmartView.WAITING -> waiting(tasks)
        SmartView.PRIORITY_1 -> priorityOne(tasks)
        SmartView.COMPLETED -> completed(tasks)
    }

    /**
     * Progress for Plan your day: (done, total). Total is the open tasks dated today or earlier plus
     * what was finished today. A task finished early, for a later date, does not count.
     */
    fun dayProgress(tasks: List<Task>, today: LocalDate, zone: ZoneId): Pair<Int, Int> {
        val doneToday = roots(tasks).filter { t ->
            t.done && t.doneAt?.let { dayOf(it, zone) } == today && dayOf(t, zone)?.isAfter(today) != true
        }
        val open = pastDue(tasks, today, zone).size + dueOn(tasks, today, zone).size
        return doneToday.size to (doneToday.size + open)
    }

    /**
     * What Plan my day asks about, in order: overdue, then promised, then due today or tomorrow.
     * Each task appears once.
     */
    fun planCandidates(tasks: List<Task>, today: LocalDate, zone: ZoneId, limit: Int = 12): List<Task> {
        val overdue = pastDue(tasks, today, zone)
        val promised = open(tasks).filter { it.sourceItemId != null }.sortedWith(byDue)
        val soon = open(tasks).filter { day -> dayOf(day, zone)?.let { !it.isAfter(today.plusDays(1)) } == true }.sortedWith(byDue)
        return (overdue + promised + soon).distinctBy { it.id }.take(limit)
    }

    /** The seven days from [today], for the week strip. */
    fun week(today: LocalDate): List<LocalDate> = (0L..6L).map { today.plusDays(it) }

    /** Days in the next week that have open tasks, for the strip's dots. */
    fun daysWithTasks(tasks: List<Task>, today: LocalDate, zone: ZoneId): Set<LocalDate> =
        open(tasks).mapNotNull { dayOf(it, zone) }.filter { !it.isBefore(today) && !it.isAfter(today.plusDays(6)) }.toSet()

    /**
     * Upcoming tasks by day, nearest first. A task later than the week goes under a null key ("Later").
     */
    fun groupUpcoming(tasks: List<Task>, today: LocalDate, zone: ZoneId): List<Pair<LocalDate?, List<Task>>> =
        upcoming(tasks, today, zone)
            .groupBy { t -> dayOf(t, zone)?.takeIf { !it.isAfter(today.plusDays(7)) } }
            .toList()

    /** "Today", "Tomorrow", or "Wed 18 Nov". */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(dayFormat)
    }

    /** "Today 15:00", "Tomorrow", "Wed 18 Nov"; null when the task has no date. */
    fun dueLabel(task: Task, today: LocalDate, zone: ZoneId): String? {
        val due = task.dueAt ?: return null
        val base = dayLabel(dayOf(due, zone), today)
        return if (task.hasTime) "$base ${timeOf(due, zone)}" else base
    }

    fun timeOf(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(timeFormat)

    /** Reads an RRULE the way the repeat picker writes it. */
    fun repeatLabel(rule: String?): String? {
        if (rule.isNullOrBlank()) return null
        val parts = rule.split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        return when (parts["FREQ"]) {
            "DAILY" -> "Every day"
            "MONTHLY" -> "Every month"
            "WEEKLY" -> {
                val days = parts["BYDAY"].orEmpty()
                when {
                    days.isEmpty() -> "Every week"
                    days == "MO,TU,WE,TH,FR" -> "Every weekday"
                    WEEKDAY_NAMES[days] != null -> "Every ${WEEKDAY_NAMES[days]}"
                    else -> "Repeats"
                }
            }
            else -> "Repeats"
        }
    }

    /** "1 task" or "3 tasks". */
    fun count(n: Int, one: String, many: String = "${one}s"): String = if (n == 1) "1 $one" else "$n $many"

    private val byDue: Comparator<Task> = compareBy<Task> { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.priority }
    private val byPriority: Comparator<Task> = compareBy<Task> { it.priority }.thenBy { it.dueAt ?: Long.MAX_VALUE }

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    private val WEEKDAY_NAMES = mapOf(
        "MO" to "Monday", "TU" to "Tuesday", "WE" to "Wednesday", "TH" to "Thursday",
        "FR" to "Friday", "SA" to "Saturday", "SU" to "Sunday"
    )
}
