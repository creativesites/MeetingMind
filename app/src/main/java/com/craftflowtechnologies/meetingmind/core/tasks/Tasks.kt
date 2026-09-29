package com.craftflowtechnologies.meetingmind.core.tasks

import com.craftflowtechnologies.meetingmind.core.database.NotePersonCrossRef
import com.craftflowtechnologies.meetingmind.core.database.PeopleDao
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskDao
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class TaskKind(val label: String) {
    TASK("Task"),
    /** Something to do because of a sermon or study — "Apply this". */
    APPLY("Apply"),
    PRAYER("Prayer"),
    FOLLOW_UP("Follow up")
}

enum class TaskRepeat(val label: String) { NONE("Doesn't repeat"), DAILY("Every day"), WEEKLY("Every week"), MONTHLY("Every month") }

data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val kind: TaskKind = TaskKind.TASK,
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val repeat: TaskRepeat = TaskRepeat.NONE,
    val doneAt: Long? = null,
    val personId: String? = null,
    val noteId: String? = null,
    val blockId: String? = null,
    val meetingId: String? = null,
    val startMs: Long? = null,
    val scripture: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val done get() = doneAt != null
}

/** A note that is about a person, for their page: prayer requests, answered prayers, testimonies and the rest. */
data class PersonNote(val id: String, val title: String, val workflow: String, val answered: Boolean, val updatedAt: Long)

data class Person(val id: String, val name: String, val relationship: String? = null, val notes: String = "", val openTasks: Int = 0, val noteCount: Int = 0)

/** Where a task sits in the list. */
enum class TaskBucket(val label: String) { OVERDUE("Overdue"), TODAY("Today"), UPCOMING("Upcoming"), SOMEDAY("No date"), DONE("Done") }

object TaskRules {
    fun bucket(task: Task, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): TaskBucket {
        if (task.done) return TaskBucket.DONE
        val due = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return TaskBucket.SOMEDAY
        return when {
            due.isBefore(today) -> TaskBucket.OVERDUE
            due == today -> TaskBucket.TODAY
            else -> TaskBucket.UPCOMING
        }
    }

    /**
     * Ticking a repeating task moves it to its next date instead of closing it; the reminder moves
     * with it, at the same time of day. Returns null for a task that doesn't repeat.
     */
    fun nextOccurrence(task: Task, zone: ZoneId = ZoneId.systemDefault()): Task? {
        if (task.repeat == TaskRepeat.NONE) return null
        fun step(ms: Long?): Long? = ms?.let {
            val t = Instant.ofEpochMilli(it).atZone(zone)
            when (task.repeat) {
                TaskRepeat.DAILY -> t.plusDays(1)
                TaskRepeat.WEEKLY -> t.plusWeeks(1)
                TaskRepeat.MONTHLY -> t.plusMonths(1)
                TaskRepeat.NONE -> t
            }.toInstant().toEpochMilli()
        }
        return task.copy(dueAt = step(task.dueAt), remindAt = step(task.remindAt), doneAt = null)
    }

    /**
     * "Call Mary tomorrow" → a title, and the person if they're already known. Deterministic:
     * only exact names the person has saved, never a guess at who "she" is.
     */
    fun personMentioned(title: String, people: List<Person>): Person? =
        people.sortedByDescending { it.name.length }.firstOrNull { p ->
            Regex("\\b${Regex.escape(p.name)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(title)
        }
}

class TaskRepository(
    private val tasks: TaskDao,
    private val people: PeopleDao,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called after anything that changes when reminders fire. */
    private val onRemindersChanged: suspend () -> Unit = {}
) {
    fun observeTasks(): Flow<List<Task>> = tasks.observeAll().map { l -> l.map { it.toDomain() } }
    fun observeForNote(noteId: String): Flow<List<Task>> = tasks.observeForNote(noteId).map { l -> l.map { it.toDomain() } }
    fun observeForPerson(personId: String): Flow<List<Task>> = tasks.observeForPerson(personId).map { l -> l.map { it.toDomain() } }
    fun observePeople(): Flow<List<Person>> = people.observeWithCounts().map { l -> l.map { Person(it.id, it.name, it.relationship, it.notes, it.openTasks, it.noteCount) } }
    fun observeNotesFor(personId: String): Flow<List<PersonNote>> = people.observeNotesFor(personId).map { l ->
        l.map { PersonNote(it.id, it.title, it.workflow, it.status == "ANSWERED", it.updatedAt) }
    }
    suspend fun notesFor(personId: String): List<PersonNote> = io { people.notesForOnce(personId).map { PersonNote(it.id, it.title, it.workflow, it.status == "ANSWERED", it.updatedAt) } }
    /** Makes sure everyone on the prayer list is also a person, once and quietly. */
    suspend fun ensurePeople(names: List<String>) = io { names.map { it.trim() }.filter { it.isNotEmpty() }.forEach { if (people.findByName(it) == null) savePerson(it) } }
    fun observePeopleForNote(noteId: String): Flow<List<Person>> = people.observeForNote(noteId).map { l -> l.map { it.toDomain() } }

    suspend fun get(id: String): Task? = io { tasks.getById(id)?.toDomain() }
    suspend fun forBlock(blockId: String): Task? = io { tasks.getByBlock(blockId)?.toDomain() }
    suspend fun openTasks(): List<Task> = io { tasks.getOpen().map { it.toDomain() } }
    suspend fun allPeople(): List<Person> = io { people.getAll().map { it.toDomain() } }
    suspend fun upcomingReminders(): List<Task> = io { tasks.upcomingReminders(clock()).map { it.toDomain() } }

    suspend fun save(task: Task): Task = io {
        val now = clock()
        val saved = task.copy(
            id = task.id.ifBlank { "task_${UUID.randomUUID()}" },
            title = task.title.trim(),
            createdAt = task.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        tasks.upsert(saved.toEntity())
        saved
    }.also { onRemindersChanged() }

    /** Ticks a task, or moves a repeating one to its next date. Returns the task as it now is. */
    suspend fun toggleDone(id: String): Task? = io {
        val task = tasks.getById(id)?.toDomain() ?: return@io null
        val now = clock()
        val next = when {
            task.done -> task.copy(doneAt = null)
            else -> TaskRules.nextOccurrence(task) ?: task.copy(doneAt = now)
        }.copy(updatedAt = now)
        tasks.upsert(next.toEntity())
        next
    }.also { onRemindersChanged() }

    suspend fun delete(id: String) { io { tasks.setDeleted(id, clock()) }; onRemindersChanged() }
    suspend fun restore(id: String) { io { tasks.setDeleted(id, null) }; onRemindersChanged() }

    suspend fun savePerson(name: String, relationship: String? = null, notes: String = "", id: String? = null): Person = io {
        val now = clock()
        val existing = id?.let { people.getById(it) } ?: people.findByName(name.trim())
        val entity = PersonEntity(
            id = existing?.id ?: "person_${UUID.randomUUID()}",
            name = name.trim(),
            relationship = relationship?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.relationship,
            notes = notes.ifBlank { existing?.notes.orEmpty() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        people.upsert(entity)
        entity.toDomain()
    }

    suspend fun findPerson(name: String): Person? = io { people.findByName(name.trim())?.toDomain() }
    suspend fun deletePerson(id: String) = io { people.setDeleted(id, clock()) }
    suspend fun linkNote(noteId: String, personId: String) = io { people.link(NotePersonCrossRef(noteId, personId)) }
    suspend fun unlinkNote(noteId: String, personId: String) = io { people.unlink(noteId, personId) }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}

fun TaskEntity.toDomain() = Task(
    id, title, notes, runCatching { TaskKind.valueOf(kind) }.getOrDefault(TaskKind.TASK), dueAt, remindAt,
    runCatching { TaskRepeat.valueOf(repeat) }.getOrDefault(TaskRepeat.NONE), doneAt, personId, noteId, blockId, meetingId, startMs, scripture, createdAt, updatedAt
)

fun Task.toEntity() = TaskEntity(id, title, notes, kind.name, dueAt, remindAt, repeat.name, doneAt, personId, noteId, blockId, meetingId, startMs, scripture, createdAt, updatedAt)

fun PersonEntity.toDomain() = Person(id, name, relationship, notes)
