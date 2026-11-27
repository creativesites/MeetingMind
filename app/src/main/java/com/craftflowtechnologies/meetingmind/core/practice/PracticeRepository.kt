package com.craftflowtechnologies.meetingmind.core.practice

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.PracticeDayEntity
import com.craftflowtechnologies.meetingmind.core.database.PracticeEntity
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import com.craftflowtechnologies.meetingmind.core.tasks.QuickAddParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class PracticeState { GROWING, BLOOMED, LET_GO }

/**
 * A practice as the app shows it. [days] are the local dates it was kept; [taskId] is the
 * repeating task in Tasks that carries it.
 */
data class Practice(
    val id: String,
    val title: String,
    val sourceNoteId: String?,
    val sourceTitle: String?,
    val taskId: String?,
    val rhythm: Rhythm,
    val remindMinutes: Int?,
    val checkInWeeks: Int,
    val startedAt: Long,
    val checkInAt: Long?,
    val state: PracticeState,
    val endedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val days: Set<LocalDate> = emptySet()
)

/**
 * Practices: planting one (which also makes its repeating task), tending it on a day, and
 * blooming or letting it go. Times and days are read in [zone].
 */
class PracticeRepository(
    private val db: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Called after a practice's task changes, so reminders are rescheduled. */
    private val onRemindersChanged: suspend () -> Unit = {}
) {
    private val practices = db.practiceDao()
    private val taskDao = db.taskDao()
    private val tasks = TaskRepository(db.taskDao(), db.peopleDao(), clock = clock, onRemindersChanged = onRemindersChanged)

    /** Growing practices first, then bloomed ones; oldest first within each group. */
    fun observeGarden(): Flow<List<Practice>> =
        combine(practices.observeGarden(), practices.observeAllDays()) { rows, days ->
            val byPractice = days.groupBy({ it.practiceId }, { LocalDate.parse(it.day) })
            rows.map { it.toDomain(byPractice[it.id].orEmpty().toSet()) }
        }

    /** Practices the person let go, most recent first, with the days they kept. */
    fun observeLetGo(): Flow<List<Practice>> =
        combine(practices.observeLetGo(), practices.observeAllDays()) { rows, days ->
            val byPractice = days.groupBy({ it.practiceId }, { LocalDate.parse(it.day) })
            rows.map { it.toDomain(byPractice[it.id].orEmpty().toSet()) }
        }

    suspend fun get(id: String): Practice? = io {
        val row = practices.getById(id) ?: return@io null
        row.toDomain(practices.daysFor(id).map { LocalDate.parse(it.day) }.toSet())
    }

    /**
     * Plants a practice and its repeating task. The task is due today when today is one of the
     * rhythm's days, else on the next one; it carries a reminder when [remindMinutes] is set.
     */
    suspend fun plant(
        title: String,
        rhythm: Rhythm,
        remindMinutes: Int?,
        checkInWeeks: Int,
        sourceNoteId: String?,
        sourceTitle: String?
    ): Practice = io {
        val now = clock()
        val today = dayOf(now)
        val id = "practice_${UUID.randomUUID()}"
        val rule = rhythm.rrule(today.dayOfWeek)
        val firstDue = nextScheduled(today, rhythm.scheduledDays(today.dayOfWeek))

        val task = tasks.save(
            Task(
                id = "",
                title = title.trim(),
                kind = TaskKind.PRACTICE,
                dueAt = dueAt(firstDue, remindMinutes),
                remindAt = remindMinutes?.let { dueAt(firstDue, it) },
                repeat = QuickAddParser.legacyRepeat(rule),
                noteId = sourceNoteId,
                sourceItemId = id,
                space = "PERSONAL",
                hasTime = remindMinutes != null,
                repeatRule = rule
            )
        )

        val entity = PracticeEntity(
            id = id,
            title = title.trim(),
            sourceNoteId = sourceNoteId,
            sourceTitle = sourceTitle,
            taskId = task.id,
            rhythm = rhythm.name,
            remindMinutes = remindMinutes,
            checkInWeeks = checkInWeeks,
            startedAt = now,
            checkInAt = PracticeGrowth.checkInAt(now, checkInWeeks),
            state = PracticeState.GROWING.name,
            endedAt = null,
            createdAt = now,
            updatedAt = now
        )
        practices.upsert(entity)
        entity.toDomain(emptySet())
    }

    /**
     * Tends a practice today. An open task due today or earlier is ticked, which logs the day; any
     * other case logs today directly, so a day can be kept without its task.
     */
    suspend fun tendToday(id: String) {
        io {
        val now = clock()
        val today = dayOf(now)
        val practice = practices.getById(id) ?: return@io
        val task = practice.taskId?.let { taskDao.getById(it) }
            ?.takeIf { it.deletedAt == null && it.doneAt == null }
        val dueByToday = task?.dueAt?.let { dayOf(it) <= today } == true
        if (task != null && dueByToday) {
            tasks.toggleDone(task.id)
        } else {
            taskDao.insertPracticeDay(PracticeDayEntity(id, today.toString(), now))
        }
        }
    }

    /** Takes back today's kept day only. The task, if it was ticked, is not reopened. */
    suspend fun untendToday(id: String) {
        io { practices.deleteDay(id, dayOf(clock()).toString()) }
    }

    /** Pushes the check-in question out by the practice's check-in length, from now. */
    suspend fun keepGoing(id: String) {
        io {
            val practice = practices.getById(id) ?: return@io
            val now = clock()
            practices.upsert(practice.copy(checkInAt = PracticeGrowth.checkInAt(now, practice.checkInWeeks), updatedAt = now))
        }
    }

    /** Changes the rhythm and reminder of a practice, and of the task that carries it. */
    suspend fun adjust(id: String, rhythm: Rhythm, remindMinutes: Int?) {
        io {
        val practice = practices.getById(id) ?: return@io
        val now = clock()
        val startDay = dayOf(practice.startedAt).dayOfWeek
        val rule = rhythm.rrule(startDay)
        val scheduled = rhythm.scheduledDays(startDay)

        practice.taskId?.let { taskId ->
            tasks.get(taskId)?.let { task ->
                val currentDay = task.dueAt?.let { dayOf(it) } ?: dayOf(now)
                val due = nextScheduled(currentDay, scheduled)
                tasks.save(
                    task.copy(
                        dueAt = dueAt(due, remindMinutes),
                        remindAt = remindMinutes?.let { dueAt(due, it) },
                        hasTime = remindMinutes != null,
                        repeat = QuickAddParser.legacyRepeat(rule),
                        repeatRule = rule
                    )
                )
            }
        }
        practices.upsert(practice.copy(rhythm = rhythm.name, remindMinutes = remindMinutes, updatedAt = now))
        }
    }

    /** The practice has grown into something lasting. Its task leaves Tasks; its days stay. */
    suspend fun bloom(id: String) {
        end(id, PracticeState.BLOOMED)
    }

    /** The practice is let go. Its task leaves Tasks; its days stay. */
    suspend fun letGo(id: String) {
        end(id, PracticeState.LET_GO)
    }

    private suspend fun end(id: String, state: PracticeState) {
        io {
            val practice = practices.getById(id) ?: return@io
            val now = clock()
            practices.upsert(practice.copy(state = state.name, endedAt = now, updatedAt = now))
            practice.taskId?.let { tasks.delete(it) }
        }
    }

    private fun PracticeEntity.toDomain(days: Set<LocalDate>) = Practice(
        id = id,
        title = title,
        sourceNoteId = sourceNoteId,
        sourceTitle = sourceTitle,
        taskId = taskId,
        rhythm = Rhythm.parse(rhythm),
        remindMinutes = remindMinutes,
        checkInWeeks = checkInWeeks,
        startedAt = startedAt,
        checkInAt = checkInAt,
        state = runCatching { PracticeState.valueOf(state) }.getOrDefault(PracticeState.GROWING),
        endedAt = endedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        days = days
    )

    private fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** [day] when it is one of [scheduled], else the next day that is. [scheduled] is never empty. */
    private fun nextScheduled(day: LocalDate, scheduled: Set<DayOfWeek>): LocalDate {
        var d = day
        while (d.dayOfWeek !in scheduled) d = d.plusDays(1)
        return d
    }

    /** Start of [day] in [zone], or [remindMinutes] after it when there is a reminder time. */
    private fun dueAt(day: LocalDate, remindMinutes: Int? = null): Long =
        day.atStartOfDay(zone).plusMinutes((remindMinutes ?: 0).toLong()).toInstant().toEpochMilli()

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
