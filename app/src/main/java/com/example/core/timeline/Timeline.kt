package com.example.core.timeline

import android.content.Context
import com.example.core.calendar.CalendarEvent
import com.example.core.calendar.CalendarEvents
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.identity.AppIdentity
import com.example.core.model.Note
import com.example.core.model.NoteStatus
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.model.Workflows
import com.example.core.repository.NoteCodec.toDomain
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * One thing on the calendar and timeline (PLAN_V2 F1). Everything the app knows about a day comes
 * through here, so Today, the week strip, month, agenda and the timeline river all agree.
 */
enum class TimelineLayer(val label: String, val color: Long, val space: NotebookSpace?) {
    EVENTS("Calendar", 0xFF4F46E5, null),
    RECORDINGS("Recordings", 0xFFE11D48, null),
    NOTES("Notes", 0xFF059669, null),
    FAITH("Faith", 0xFFB7791F, NotebookSpace.FAITH),
    MEMORIES("On this day", 0xFF8B5CF6, null);

    companion object {
        /** What shows before the person chooses: everything their spaces allow. */
        fun defaultsFor(identity: AppIdentity): Set<TimelineLayer> =
            entries.filter { it.space == null || it.space in identity.spaces }.toSet()
    }
}

enum class ItemKind { EVENT, RECORDING, NOTE, ANSWERED_PRAYER, MEMORY }

/** Where tapping an item goes. */
sealed interface DeepTarget {
    data class Note(val noteId: String) : DeepTarget
    data class Recording(val meetingId: String, val startMs: Long? = null) : DeepTarget
    data class Event(val event: CalendarEvent) : DeepTarget
}

data class TimelineItem(
    val id: String,
    val layer: TimelineLayer,
    val kind: ItemKind,
    val start: Long,
    val end: Long?,
    val title: String,
    val subtitle: String?,
    /** A line or two that tells similar items apart: a recording's summary, a note's first words. */
    val summary: String? = null,
    /** A picture for the card: a note's photo. Null → the card draws its layer's gradient. */
    val coverPath: String?,
    val accent: Long,
    val workflow: RecordingType?,
    val people: List<String> = emptyList(),
    /** A short status: "Processing", "3 open tasks", "Recorded". */
    val badge: String? = null,
    val allDay: Boolean = false,
    val target: DeepTarget,
    /** For an event: the note made for it, when there is one. */
    val noteId: String? = null
) {
    val dayKey: Int get() = TimelineDays.key(start)
}

/** Day arithmetic in the phone's time zone. A day key is yyyymmdd. */
object TimelineDays {
    fun key(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

    /** Monday-first week start (the person's locale decides in the UI; Monday is the safe default). */
    fun startOfWeek(millis: Long, firstDay: Int = Calendar.getInstance().firstDayOfWeek): Long {
        val c = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
        while (c.get(Calendar.DAY_OF_WEEK) != firstDay) c.add(Calendar.DAY_OF_YEAR, -1)
        return c.timeInMillis
    }

    fun startOfMonth(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis); set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun addMonths(millis: Long, months: Int): Long = Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.MONTH, months) }.timeInMillis
}

/**
 * Turns notes, recordings, pictures and calendar events into timeline items. Pure, so the rules
 * are tested:
 * - a recording and its note are one moment → one card (the note's), carrying the recording's
 *   length, status and open tasks;
 * - an event with a note made for it shows once, as the event, opening that note;
 * - Faith workflows go to the Faith layer; answered prayers get their own milestone;
 * - only the layers asked for come back, soonest first.
 */
object TimelineAssembler {

    data class MeetingInfo(val id: String, val durationMs: Long, val status: String, val createdAt: Long, val summary: String? = null)

    fun assemble(
        notes: List<Note>,
        meetingsByNote: Map<String, List<MeetingInfo>>,
        openTasksByMeeting: Map<String, Int>,
        coverByNote: Map<String, String>,
        events: List<CalendarEvent>,
        answered: List<Note>,
        layers: Set<TimelineLayer>
    ): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()
        val noteByEvent = notes.mapNotNull { n -> n.metadata[NoteRepository.CALENDAR_EVENT_KEY]?.let { it to n } }.toMap()

        if (TimelineLayer.EVENTS in layers) {
            for (e in events) {
                val note = noteByEvent[e.key]
                val recorded = note != null && meetingsByNote[note.id].orEmpty().isNotEmpty()
                items += TimelineItem(
                    id = "ev:${e.key}", layer = TimelineLayer.EVENTS, kind = ItemKind.EVENT, start = e.begin, end = e.end,
                    title = e.title, subtitle = listOfNotNull(e.location, e.calendarName).joinToString(" · ").ifBlank { null },
                    coverPath = note?.let { coverByNote[it.id] }, accent = e.color?.toLong()?.and(0xFFFFFFFFL) ?: TimelineLayer.EVENTS.color,
                    summary = note?.let { summaryOf(it, meetingsByNote[it.id].orEmpty()) },
                    workflow = note?.workflow, people = e.otherPeople,
                    badge = when { recorded -> "Recorded"; note != null -> "Notes" ; else -> null },
                    allDay = e.allDay,
                    target = if (note != null) DeepTarget.Note(note.id) else DeepTarget.Event(e),
                    noteId = note?.id
                )
            }
        }

        val shownAsEvent = if (TimelineLayer.EVENTS in layers) events.mapNotNull { noteByEvent[it.key]?.id }.toSet() else emptySet()
        for (n in notes) {
            if (n.id in shownAsEvent) continue
            val meetings = meetingsByNote[n.id].orEmpty()
            val faith = Workflows.space(n.workflow) == NotebookSpace.FAITH
            val layer = when {
                faith -> TimelineLayer.FAITH
                meetings.isNotEmpty() -> TimelineLayer.RECORDINGS
                else -> TimelineLayer.NOTES
            }
            if (layer !in layers) continue
            val duration = meetings.sumOf { it.durationMs }
            val processing = meetings.any { it.status == "PROCESSING" || it.status == "RECORDING" }
            val tasks = meetings.sumOf { openTasksByMeeting[it.id] ?: 0 }
            val start = n.eventDate ?: n.createdAt
            items += TimelineItem(
                id = "note:${n.id}", layer = layer, kind = if (meetings.isNotEmpty()) ItemKind.RECORDING else ItemKind.NOTE,
                start = start, end = if (duration > 0) start + duration else null,
                title = n.title.ifBlank { n.workflow.displayName },
                subtitle = listOfNotNull(
                    duration.takeIf { it > 0 }?.let { formatDuration(it) },
                    n.metadata["speaker"], n.metadata["participants"]?.let { "With $it" }
                ).joinToString(" · ").ifBlank { null },
                summary = summaryOf(n, meetings),
                coverPath = coverByNote[n.id], accent = layer.color, workflow = n.workflow,
                people = n.metadata["participants"]?.split(", ").orEmpty(),
                badge = when {
                    processing -> "Processing"
                    tasks > 0 -> "$tasks open ${if (tasks == 1) "task" else "tasks"}"
                    n.workflow == RecordingType.PRAYER_REQUEST && n.status == NoteStatus.OPEN -> "Praying"
                    else -> null
                },
                target = DeepTarget.Note(n.id), noteId = n.id
            )
        }

        if (TimelineLayer.FAITH in layers) {
            for (n in answered) {
                val at = n.answeredAt ?: continue
                items += TimelineItem(
                    id = "answered:${n.id}", layer = TimelineLayer.FAITH, kind = ItemKind.ANSWERED_PRAYER, start = at, end = null,
                    title = "Answered: ${n.title.ifBlank { "prayer" }}", subtitle = "Praying since ${formatShortDate(n.createdAt)}",
                    coverPath = coverByNote[n.id], accent = TimelineLayer.FAITH.color, workflow = n.workflow,
                    badge = "Answered", target = DeepTarget.Note(n.id), noteId = n.id
                )
            }
        }
        return items.sortedWith(compareBy<TimelineItem> { it.allDay.not() }.thenBy { it.start })
    }

    /**
     * What makes this one recognisable at a glance: the recording's summary when there is one,
     * else the note's own first words — skipping the title and template headings, which every
     * note of a kind shares.
     */
    fun summaryOf(n: Note, meetings: List<MeetingInfo>): String? {
        meetings.firstNotNullOfOrNull { it.summary?.trim()?.takeIf { s -> s.isNotEmpty() } }?.let { return clip(it) }
        val skip = (com.example.core.model.Workflows.template(n.workflow).sections.map { it.title.trim().lowercase() } + n.title.trim().lowercase()).toSet()
        val lines = n.plainText.lines().map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() !in skip }
        return lines.take(3).joinToString(" · ").takeIf { it.isNotBlank() }?.let(::clip)
    }

    private fun clip(s: String) = if (s.length <= 180) s else s.take(177).trimEnd() + "…"

    fun formatDuration(ms: Long): String {
        val minutes = (ms / 60_000).toInt()
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "${maxOf(minutes, 1)} min"
    }

    private fun formatShortDate(ms: Long) = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(ms))
}

/** Reads everything for a range of days and hands it to [TimelineAssembler]. */
class TimelineRepository(
    private val context: Context,
    private val database: MeetMindDatabase = MeetMindDatabase.getInstance(context),
    private val calendar: CalendarEvents = CalendarEvents(context)
) {
    suspend fun between(from: Long, to: Long, layers: Set<TimelineLayer>, includeCalendar: Boolean): List<TimelineItem> = withContext(Dispatchers.IO) {
        val noteDao = database.noteDao()
        val notes = noteDao.getNotesBetween(from, to).map { it.toDomain() }
        val answered = if (TimelineLayer.FAITH in layers) noteDao.getAnsweredBetween(from, to).map { it.toDomain() } else emptyList()
        val ids = (notes + answered).map { it.id }.distinct()
        val meetings = if (ids.isEmpty()) emptyList() else ids.chunked(500).flatMap { noteDao.getMeetingsForNotes(it) }
        val openTasks = if (meetings.isEmpty()) emptyMap() else meetings.map { it.id }.chunked(500)
            .flatMap { database.actionItemDao().getOpenForMeetings(it) }.groupingBy { it.meetingId }.eachCount()
        val covers = coversFor(notes + answered)
        val events = if (includeCalendar && TimelineLayer.EVENTS in layers && calendar.hasPermission()) calendar.between(from, to) else emptyList()
        TimelineAssembler.assemble(
            notes, meetings.groupBy { it.noteId.orEmpty() }.mapValues { (_, list) -> list.map { it.info() } },
            openTasks, covers, events, answered, layers
        ).filter { it.start < to && (it.end ?: it.start) >= from }
    }

    /** Notes from this date in earlier years. */
    suspend fun onThisDay(day: Long): List<TimelineItem> = withContext(Dispatchers.IO) {
        val md = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date(day))
        val notes = database.noteDao().getOnThisDay(md, TimelineDays.startOfDay(day)).map { it.toDomain() }
        val covers = coversFor(notes)
        notes.map { n ->
            val years = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = n.eventDate ?: n.createdAt }.get(Calendar.YEAR)
            TimelineItem(
                id = "memory:${n.id}", layer = TimelineLayer.MEMORIES, kind = ItemKind.MEMORY, start = n.eventDate ?: n.createdAt, end = null,
                title = n.title.ifBlank { n.workflow.displayName }, subtitle = if (years == 1) "A year ago today" else "$years years ago today",
                coverPath = covers[n.id], accent = TimelineLayer.MEMORIES.color, workflow = n.workflow,
                target = DeepTarget.Note(n.id), noteId = n.id
            )
        }
    }

    /** Recordings over the last weeks, for learning when the person usually records what. */
    suspend fun recentRecordings(since: Long): List<Pair<RecordingType, Long>> = withContext(Dispatchers.IO) {
        database.noteDao().getNotesBetween(since, Long.MAX_VALUE).map { it.toDomain() }.let { notes ->
            val withMeetings = if (notes.isEmpty()) emptySet() else notes.map { it.id }.chunked(500)
                .flatMap { database.noteDao().getMeetingsForNotes(it) }.mapNotNull { it.noteId }.toSet()
            notes.filter { it.id in withMeetings }.map { it.workflow to (it.eventDate ?: it.createdAt) }
        }
    }

    private fun MeetingEntity.info() = TimelineAssembler.MeetingInfo(id, durationMs, status, createdAt, summaryText)

    /** A note's chosen cover, else its first photo. */
    private suspend fun coversFor(notes: List<Note>): Map<String, String> {
        if (notes.isEmpty()) return emptyMap()
        val attachmentDao = database.attachmentDao()
        val first = notes.map { it.id }.distinct().chunked(500).flatMap { attachmentDao.getImagesForNotes(it) }
            .groupBy { it.noteId }.mapValues { it.value.first().path }
        val chosen = notes.mapNotNull { n ->
            n.metadata[NoteRepository.COVER_KEY]?.takeIf { it.isNotBlank() }?.let { id -> attachmentDao.getById(id)?.let { n.id to it.path } }
        }.toMap()
        return first + chosen
    }
}
