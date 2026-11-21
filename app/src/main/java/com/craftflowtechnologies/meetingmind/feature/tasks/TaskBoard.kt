package com.craftflowtechnologies.meetingmind.feature.tasks

import com.craftflowtechnologies.meetingmind.core.tasks.Task
import java.time.LocalDate
import java.time.ZoneId

/** The columns of the Tasks board, left to right. Every top-level task sits in exactly one. */
enum class BoardColumn(val title: String, val empty: String) {
    NO_DATE("No date", "New tasks without a date land here."),
    TODAY("Today", "Nothing due today."),
    UPCOMING("Upcoming", "Nothing planned after today."),
    WAITING("Waiting on", "Nothing is waiting on someone else."),
    DONE("Done", "Tasks you finish this week show here.")
}

/** Sorting tasks into board columns. Pure, so the board and its tests read the same rules. */
object TaskBoard {
    /** How long a finished task stays on the board. */
    const val DONE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    /** The most finished tasks the Done column shows. */
    const val DONE_LIMIT = 30

    /** Done first, then Waiting on, then by date: none, today or earlier, or later. */
    fun columnOf(task: Task, today: LocalDate, zone: ZoneId): BoardColumn {
        if (task.done) return BoardColumn.DONE
        if (task.waitingOn) return BoardColumn.WAITING
        val day = TaskViews.dayOf(task, zone) ?: return BoardColumn.NO_DATE
        return if (day.isAfter(today)) BoardColumn.UPCOMING else BoardColumn.TODAY
    }

    /**
     * Every column with its tasks, in board order (empty columns included). Subtasks stay with their
     * parent. Done keeps the last week, newest first, up to [DONE_LIMIT].
     */
    fun columns(tasks: List<Task>, today: LocalDate, zone: ZoneId, now: Long): List<Pair<BoardColumn, List<Task>>> {
        val byColumn = TaskViews.roots(tasks).groupBy { columnOf(it, today, zone) }
        return BoardColumn.entries.map { column ->
            val items = byColumn[column].orEmpty()
            column to when (column) {
                BoardColumn.NO_DATE -> items.sortedWith(compareBy<Task> { it.priority }.thenByDescending { it.createdAt })
                BoardColumn.TODAY, BoardColumn.UPCOMING, BoardColumn.WAITING -> items.sortedWith(byDue)
                BoardColumn.DONE -> items
                    .filter { (it.doneAt ?: 0L) >= now - DONE_WINDOW_MS }
                    .sortedByDescending { it.doneAt ?: 0L }
                    .take(DONE_LIMIT)
            }
        }
    }

    /** The columns a card can move to from [from]: every other one. */
    fun targets(from: BoardColumn): List<BoardColumn> = BoardColumn.entries.filter { it != from }

    private val byDue: Comparator<Task> = compareBy<Task> { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.priority }
}
