package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.work.DueDates
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteFilters
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteKind
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteSort
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * The Work page's pure decisions (WORK_UX §2): which segment opens, what the filters mean, how rows
 * are grouped under headers. No Android and no database here, so each rule has a plain unit test.
 */

enum class WorkSegment(val label: String) { TODAY("Today"), NOTES("Notes"), TASKS("Tasks"), PROJECTS("Projects") }

/** Today when there are meetings or items that need the person today; otherwise the notes. */
fun defaultSegment(meetingsToday: Int, needsYou: Int): WorkSegment =
    if (meetingsToday > 0 || needsYou > 0) WorkSegment.TODAY else WorkSegment.NOTES

/** The multi-select chips above the notes list. [MEETINGS] and [MY_NOTES] exclude each other. */
enum class NoteChip(val label: String) {
    MEETINGS("Meetings"), MY_NOTES("My notes"), HAS_TASKS("Has tasks"), HAS_RECORDING("Has recording"), THIS_WEEK("This week")
}

val WorkNoteSort.label: String
    get() = when (this) { WorkNoteSort.RECENT -> "Recent"; WorkNoteSort.MEETING_DATE -> "Meeting date"; WorkNoteSort.TITLE -> "Title" }

/** Everything that decides which notes the list shows. Survives rotation and process death (the page's SavedStateHandle). */
data class NotesQuery(
    val chips: Set<NoteChip> = emptySet(),
    val notebookId: String? = null,
    val sort: WorkNoteSort = WorkNoteSort.RECENT,
    val search: String = ""
) {
    val isSearching: Boolean get() = search.isNotBlank()
    val isFiltered: Boolean get() = chips.isNotEmpty() || notebookId != null

    fun toggled(chip: NoteChip): NotesQuery {
        val next = if (chip in chips) chips - chip else {
            val exclusive = when (chip) { NoteChip.MEETINGS -> NoteChip.MY_NOTES; NoteChip.MY_NOTES -> NoteChip.MEETINGS; else -> null }
            chips - setOfNotNull(exclusive) + chip
        }
        return copy(chips = next)
    }

    fun filters(weekStart: Long) = WorkNoteFilters(
        kind = when { NoteChip.MEETINGS in chips -> WorkNoteKind.MEETINGS; NoteChip.MY_NOTES in chips -> WorkNoteKind.MY_NOTES; else -> WorkNoteKind.ANY },
        hasOpenTasks = NoteChip.HAS_TASKS in chips,
        hasRecording = NoteChip.HAS_RECORDING in chips,
        notebookId = notebookId,
        createdSince = if (NoteChip.THIS_WEEK in chips) weekStart else null
    )
}

/** A Work note as the list shows it: one page row plus its indicators. [cursor] is where the next page starts after it. */
data class WorkNoteItem(
    val id: String,
    val title: String,
    val preview: String,
    val notebookId: String?,
    val workflow: RecordingType,
    /** The date the row sits under: edited time, or the meeting date when sorting by it. */
    val at: Long,
    val pinned: Boolean,
    val isPrivate: Boolean,
    val hasRecording: Boolean,
    val transcript: NoteTranscriptStatus,
    val openTasks: Int,
    val cursor: Cursor
) {
    val displayTitle: String get() = title.ifBlank { workflow.displayName }
}

/** A task row with its page cursor. */
data class WorkTaskRow(val task: WorkTask, val cursor: Cursor)

// ---------------------------------------------------------------- notes grouping

sealed interface NoteEntry {
    val key: String
    data class Header(val label: String) : NoteEntry { override val key get() = "h:$label" }
    data class Row(val item: WorkNoteItem) : NoteEntry { override val key get() = "n:${item.id}" }
}

/** Today / Yesterday / This week / Earlier (this month), then month headers. */
fun dateHeader(at: Long, now: Long): String {
    val today = DueDates.startOfDay(now)
    val day = DAY_MS
    return when {
        at >= today -> "Today"
        at >= today - day -> "Yesterday"
        at >= today - 6 * day -> "This week"
        else -> {
            val a = Calendar.getInstance().apply { timeInMillis = at }
            val n = Calendar.getInstance().apply { timeInMillis = now }
            when {
                a.get(Calendar.YEAR) == n.get(Calendar.YEAR) && a.get(Calendar.MONTH) == n.get(Calendar.MONTH) -> "Earlier"
                a.get(Calendar.YEAR) == n.get(Calendar.YEAR) -> SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(at))
                else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(at))
            }
        }
    }
}

/**
 * The list's rows with headers between groups. Sorted by title the groups are letters; by meeting date a note
 * with no date sits under "No date"; otherwise they are the date headers. Pinned notes are not lifted: the order
 * is the order the pager loaded, so groups never move under the finger.
 */
fun groupNotes(items: List<WorkNoteItem>, sort: WorkNoteSort, now: Long): List<NoteEntry> {
    val out = ArrayList<NoteEntry>(items.size + 8)
    var last: String? = null
    items.forEach { item ->
        val header = when (sort) {
            WorkNoteSort.TITLE -> item.displayTitle.trim().firstOrNull()?.let { if (it.isLetter()) it.uppercaseChar().toString() else "#" } ?: "#"
            WorkNoteSort.MEETING_DATE -> if (item.at <= 0L) "No date" else dateHeader(item.at, now)
            WorkNoteSort.RECENT -> dateHeader(item.at, now)
        }
        if (header != last) { out += NoteEntry.Header(header); last = header }
        out += NoteEntry.Row(item)
    }
    return out
}

/** "10:30" for today, "9 Oct" for this year, "9 Oct 2025" before that. */
fun noteTimeLabel(at: Long, now: Long): String {
    if (at <= 0L) return ""
    val pattern = when {
        at >= DueDates.startOfDay(now) -> return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(at))
        Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR) -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(at))
}

/** First two lines' worth of a note's text, flattened. */
fun previewOf(plainText: String, max: Int = 180): String =
    plainText.trim().replace(Regex("\\s+"), " ").let { if (it.length > max) it.take(max).trimEnd() + "…" else it }

// ---------------------------------------------------------------- tasks grouping

enum class TaskBucket(val label: String) { OVERDUE("Overdue"), TODAY("Today"), THIS_WEEK("This week"), LATER("Later"), NO_DATE("No date") }

fun taskBucket(dueAt: Long?, now: Long): TaskBucket {
    if (dueAt == null) return TaskBucket.NO_DATE
    val today = DueDates.startOfDay(now)
    return when {
        dueAt < today -> TaskBucket.OVERDUE
        dueAt < today + DAY_MS -> TaskBucket.TODAY
        dueAt < today + 7 * DAY_MS -> TaskBucket.THIS_WEEK
        else -> TaskBucket.LATER
    }
}

sealed interface TaskEntry {
    val key: String
    data class Header(val bucket: TaskBucket, val count: Int) : TaskEntry { override val key get() = "th:${bucket.name}" }
    data class Row(val row: WorkTaskRow) : TaskEntry { override val key get() = "t:${row.task.id}" }
}

/** Rows in the pager's order (soonest due first, undated last) with a header at each bucket change. */
fun groupTasks(rows: List<WorkTaskRow>, now: Long): List<TaskEntry> {
    val buckets = rows.map { taskBucket(it.task.dueAt, now) }
    val counts = buckets.groupingBy { it }.eachCount()
    val out = ArrayList<TaskEntry>(rows.size + 5)
    var last: TaskBucket? = null
    rows.forEachIndexed { i, r ->
        if (buckets[i] != last) { last = buckets[i]; out += TaskEntry.Header(buckets[i], counts.getValue(buckets[i])) }
        out += TaskEntry.Row(r)
    }
    return out
}

// ---------------------------------------------------------------- header

/** "Thursday 9 Oct · 3 meetings · 2 need you", leaving out what is zero. */
fun contextLine(now: Long, meetingsToday: Int, needsYou: Int): String = listOfNotNull(
    SimpleDateFormat("EEEE d MMM", Locale.getDefault()).format(Date(now)),
    "$meetingsToday ${if (meetingsToday == 1) "meeting" else "meetings"}".takeIf { meetingsToday > 0 },
    "$needsYou need${if (needsYou == 1) "s" else ""} you".takeIf { needsYou > 0 }
).joinToString(" · ")

/** Start of the current week (Monday) for the "This week" chip. */
fun weekStart(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = DueDates.startOfDay(now)
    val back = (get(Calendar.DAY_OF_WEEK) + 5) % 7
    add(Calendar.DAY_OF_MONTH, -back)
}.timeInMillis

private const val DAY_MS = 86_400_000L
