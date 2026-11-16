package com.craftflowtechnologies.meetingmind.core.tasks

import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.NotePersonCrossRef
import com.craftflowtechnologies.meetingmind.core.database.PeopleDao
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskDao
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskLabelEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskLabelLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskSectionEntity
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
    val updatedAt: Long = 0,
    // Work (schema 17): someone else owes it, who, and the finding it came from.
    val waitingOn: Boolean = false,
    val ownerSpeakerId: String? = null,
    val sourceItemId: String? = null,
    /** The space it belongs to (WORK, PERSONAL, LEARNING, FAITH), when known. */
    val space: String? = null,
    // Tasks manager (schema 31).
    /** 1 (urgent) to 4 (none). */
    val priority: Int = 4,
    val parentId: String? = null,
    /** The list it sits in; null is the Inbox. */
    val listId: String? = null,
    val sectionId: String? = null,
    val position: Double = 0.0,
    /** Whether [dueAt] carries a time of day. */
    val hasTime: Boolean = false,
    /** An RRULE such as FREQ=WEEKLY;BYDAY=MO; [repeat] is kept in step for older code. */
    val repeatRule: String? = null
) {
    val done get() = doneAt != null
}

/** A task list: a notebook of kind PROJECT or TASK_LIST. */
data class TaskList(val id: String, val name: String, val space: String, val colorHex: String?, val isProject: Boolean)
data class TaskSection(val id: String, val listId: String, val name: String, val position: Double)
data class TaskLabel(val id: String, val name: String, val color: String?)

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
    private val onRemindersChanged: suspend () -> Unit = {},
    /** Called after a task is ticked or reopened, so a commitment linked to it follows. */
    private val onTaskDone: suspend (taskId: String, done: Boolean) -> Unit = { _, _ -> }
) {
    private val zone: ZoneId = ZoneId.systemDefault()

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
        val id = task.id.ifBlank { "task_${UUID.randomUUID()}" }
        val existing = tasks.getById(id)
        val saved = task.copy(
            id = id,
            title = task.title.trim(),
            // A task with no space takes the one it was filed under, when that is known.
            space = task.space ?: existing?.space ?: contextSpace(task),
            createdAt = task.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        tasks.upsert(saved.toEntity(deletedAt = existing?.deletedAt))
        saved
    }.also { onRemindersChanged() }

    /** The space a task inherits from the list it sits in, or else from its note's notebook. */
    private suspend fun contextSpace(task: Task): String? =
        task.listId?.let { tasks.spaceOfList(it) } ?: task.noteId?.let { tasks.spaceForNote(it) }

    /**
     * Ticks a task, or moves a repeating one to its next date. Returns the task as it now is.
     * Built from the stored row, so fields this screen doesn't show (Waiting on, the source link,
     * the space) survive, and so does Trash status.
     */
    suspend fun toggleDone(id: String): Task? = io {
        val existing = tasks.getById(id) ?: return@io null
        val task = existing.toDomain()
        val now = clock()
        val next = when {
            task.done -> task.copy(doneAt = null)
            else -> TaskRules.nextOccurrence(task) ?: task.copy(doneAt = now)
        }.copy(updatedAt = now)
        tasks.upsert(next.toEntity(deletedAt = existing.deletedAt))
        next
    }.also { onRemindersChanged(); if (it != null) onTaskDone(it.id, it.done) }

    // ---- Tasks manager (schema 31) ----

    /** Task lists: notebooks of kind PROJECT (work) and TASK_LIST (the Tasks app). */
    fun observeLists(): Flow<List<TaskList>> = tasks.observeLists().map { l -> l.map { it.toTaskList() } }
    fun observeSections(listId: String): Flow<List<TaskSection>> = tasks.observeSections(listId).map { l -> l.map { TaskSection(it.id, it.listId, it.name, it.position) } }
    fun observeLabels(): Flow<List<TaskLabel>> = tasks.observeLabels().map { l -> l.map { TaskLabel(it.id, it.name, it.color) } }
    fun observeInList(listId: String): Flow<List<Task>> = tasks.observeInList(listId).map { l -> l.map { it.toDomain() } }

    /** Due today or earlier, open. Overdue tasks are included on purpose. */
    fun observeToday(): Flow<List<Task>> = tasks.observeDueBefore(startOfDayPlus(1)).map { l -> l.map { it.toDomain() } }
    /** Open tasks due in the next 14 days, starting tomorrow (Today has its own view). */
    fun observeUpcoming(): Flow<List<Task>> = tasks.observeDueBetween(startOfDayPlus(1), startOfDayPlus(15)).map { l -> l.map { it.toDomain() } }
    /** Open tasks with no list. */
    fun observeInbox(): Flow<List<Task>> = tasks.observeInbox().map { l -> l.map { it.toDomain() } }
    fun observePriority1(): Flow<List<Task>> = tasks.observePriority1().map { l -> l.map { it.toDomain() } }
    fun observeCompleted(limit: Int = 50): Flow<List<Task>> = tasks.observeCompleted(limit).map { l -> l.map { it.toDomain() } }
    suspend fun completed(limit: Int = 50): List<Task> = io { tasks.completedOnce(limit).map { it.toDomain() } }

    /** Full-text search over title and notes. Each word is matched as a prefix. Blank input finds nothing. */
    suspend fun search(query: String, limit: Int = 50): List<Task> = io {
        val match = ftsQuery(query) ?: return@io emptyList()
        tasks.searchTasks(match, limit).map { it.toDomain() }
    }

    suspend fun setPriority(id: String, priority: Int) { io { tasks.setPriority(id, priority.coerceIn(1, 4), clock()) } }

    /** Files a task into a list and section (both null is the Inbox), at [position] within it. */
    suspend fun move(id: String, listId: String?, sectionId: String?, position: Double) {
        io { tasks.move(id, listId, sectionId, position, clock()) }
    }

    suspend fun reorderTask(id: String, position: Double) { io { tasks.setTaskPosition(id, position) } }

    /** Sets a due date (null clears it). [hasTime] is false for a day with no time of day. */
    suspend fun reschedule(id: String, dueAt: Long?, hasTime: Boolean = false) {
        io { tasks.reschedule(id, dueAt, hasTime, clock()) }
        onRemindersChanged()
    }

    /** A subtask of [parentId], filed in the same list and section. */
    suspend fun addSubtask(parentId: String, title: String): Task? {
        val parent = get(parentId) ?: return null
        return save(Task("", title, parentId = parent.id, listId = parent.listId, sectionId = parent.sectionId, space = parent.space))
    }

    /**
     * Replaces a task's labels with these names. A name that is new becomes a label; matching is
     * case-blind, and a leading "@" is dropped.
     */
    suspend fun setLabels(taskId: String, names: List<String>): List<TaskLabel> = io {
        val wanted = names.map { it.trim().trimStart('@').trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        tasks.clearLabelLinks(taskId)
        wanted.map { name ->
            val label = tasks.labelByName(name) ?: TaskLabelEntity(id = "label_${UUID.randomUUID()}", name = name).also { tasks.upsertLabel(it) }
            tasks.linkLabel(TaskLabelLinkEntity(taskId, label.id))
            TaskLabel(label.id, label.name, label.color)
        }
    }

    suspend fun labelsFor(taskId: String): List<TaskLabel> = io { tasks.labelsForTask(taskId).map { TaskLabel(it.id, it.name, it.color) } }

    /** Adds a section at the end of a list, or renames [id] when it is given. */
    suspend fun saveSection(listId: String, name: String, id: String? = null): TaskSection = io {
        val sectionId = id ?: "section_${UUID.randomUUID()}"
        val position = tasks.nextSectionPosition(listId)
        val entity = TaskSectionEntity(id = sectionId, listId = listId, name = name.trim(), position = position)
        tasks.upsertSection(entity)
        TaskSection(entity.id, entity.listId, entity.name, entity.position)
    }

    suspend fun reorderSection(id: String, position: Double) { io { tasks.setSectionPosition(id, position) } }

    /** Deleting a section keeps its tasks; they move to the top of the list, unsectioned. */
    suspend fun deleteSection(id: String) {
        io {
            tasks.clearSectionOnTasks(id)
            tasks.deleteSection(id)
        }
    }

    private fun startOfDayPlus(days: Long): Long =
        Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate().plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()

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
    id = id,
    title = title,
    notes = notes,
    kind = runCatching { TaskKind.valueOf(kind) }.getOrDefault(TaskKind.TASK),
    dueAt = dueAt,
    remindAt = remindAt,
    repeat = runCatching { TaskRepeat.valueOf(repeat) }.getOrDefault(TaskRepeat.NONE),
    doneAt = doneAt,
    personId = personId,
    noteId = noteId,
    blockId = blockId,
    meetingId = meetingId,
    startMs = startMs,
    scripture = scripture,
    createdAt = createdAt,
    updatedAt = updatedAt,
    waitingOn = waitingOn,
    ownerSpeakerId = ownerSpeakerId,
    sourceItemId = sourceItemId,
    space = space,
    priority = priority,
    parentId = parentId,
    listId = listId,
    sectionId = sectionId,
    position = position,
    hasTime = hasTime,
    repeatRule = repeatRule
)

/**
 * Every field, by name. [deletedAt] is not on [Task]: callers that rewrite a stored row pass the
 * stored value through, so saving or ticking a task never restores it from the Trash.
 */
fun Task.toEntity(deletedAt: Long? = null) = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    kind = kind.name,
    dueAt = dueAt,
    remindAt = remindAt,
    repeat = repeat.name,
    doneAt = doneAt,
    personId = personId,
    noteId = noteId,
    blockId = blockId,
    meetingId = meetingId,
    startMs = startMs,
    scripture = scripture,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    waitingOn = waitingOn,
    ownerSpeakerId = ownerSpeakerId,
    sourceItemId = sourceItemId,
    space = space,
    priority = priority,
    parentId = parentId,
    listId = listId,
    sectionId = sectionId,
    position = position,
    hasTime = hasTime,
    repeatRule = repeatRule
)

private fun NotebookEntity.toTaskList() = TaskList(id, name, space, colorHex, kind == "PROJECT")

/**
 * FTS4 MATCH text: each word becomes a prefix term. Punctuation is dropped, and words are lower-cased
 * so that a typed "OR" or "NOT" is never read as an FTS operator. The query cannot be malformed.
 */
internal fun ftsQuery(raw: String): String? {
    val terms = raw.split(Regex("\\s+")).map { w -> w.filter { it.isLetterOrDigit() }.lowercase() }.filter { it.isNotEmpty() }
    return terms.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
}

fun PersonEntity.toDomain() = Person(id, name, relationship, notes)
