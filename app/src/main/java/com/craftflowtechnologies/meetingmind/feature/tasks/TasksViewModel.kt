package com.craftflowtechnologies.meetingmind.feature.tasks

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvents
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.tasks.Person
import com.craftflowtechnologies.meetingmind.core.tasks.QuickAddList
import com.craftflowtechnologies.meetingmind.core.tasks.QuickAddParser
import com.craftflowtechnologies.meetingmind.core.tasks.QuickAddResult
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskLabel
import com.craftflowtechnologies.meetingmind.core.tasks.TaskList
import com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepeat
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import com.craftflowtechnologies.meetingmind.core.tasks.TaskSection
import com.craftflowtechnologies.meetingmind.core.work.Direction
import com.craftflowtechnologies.meetingmind.core.work.ItemRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the device calendar gives the Today page. Nothing is read until the person has granted it. */
interface TodayCalendar {
    fun allowed(): Boolean
    suspend fun between(from: Long, to: Long): List<CalendarEvent>
}

private class DeviceCalendar(context: Context) : TodayCalendar {
    private val events = CalendarEvents(context)
    override fun allowed(): Boolean = events.hasPermission()
    override suspend fun between(from: Long, to: Long): List<CalendarEvent> = events.between(from, to)
}

/** A date the person picked. A null [dueAt] clears the date. A null [repeatRule] does not repeat. */
data class DateChoice(val dueAt: Long?, val hasTime: Boolean, val repeatRule: String? = null)

/** What to do with the task on the Plan my day card. */
enum class PlanChoice { TODAY, NOT_TODAY, PICK }

/** The plan in progress: a fixed queue of task ids, and where the person has got to. */
data class PlanSession(
    val queue: List<String>,
    val index: Int = 0,
    val todayCount: Int = 0,
    val notTodayCount: Int = 0,
    val pickedCount: Int = 0
) {
    val current: String? get() = queue.getOrNull(index)
    val finished: Boolean get() = index >= queue.size
}

/**
 * The last change, offered as Undo. [before] is each task as it was. A [deleted] undo restores
 * them from the Trash.
 */
data class TaskUndo(val message: String, val before: List<Task>, val deleted: Boolean = false)

/**
 * The Tasks app's state and actions. Screens read the flows and call the suspend actions from a
 * coroutine scope; the actions write through [TaskRepository], so reminders, the checklist line
 * and commitments follow.
 */
class TasksViewModel(
    app: Application,
    private val repo: TaskRepository,
    commitments: Flow<List<ItemEntity>>,
    private val calendar: TodayCalendar,
    private val setBlockChecked: suspend (blockId: String, checked: Boolean) -> Unit,
    private val clock: () -> Long
) : AndroidViewModel(app) {

    constructor(app: Application) : this(
        app,
        TaskReminders.repository(app),
        ItemRepository(MeetMindDatabase.getInstance(app)).observeCommitments(Direction.THEIRS),
        DeviceCalendar(app),
        { blockId, checked -> MeetMindDatabase.getInstance(app).noteDao().setBlockChecked(blockId, checked, System.currentTimeMillis()) },
        System::currentTimeMillis
    )

    private val zone: ZoneId = ZoneId.systemDefault()

    val tasks: StateFlow<List<Task>> = repo.observeTasks()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lists: StateFlow<List<TaskList>> = repo.observeLists()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val labels: StateFlow<List<TaskLabel>> = repo.observeLabels()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<Person>> = repo.observePeople()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Commitments other people owe the person (THEIRS), shown under Waiting on. */
    val commitmentsTheirs: StateFlow<List<ItemEntity>> = commitments
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val labelRevision = MutableStateFlow(0)

    /** Label names by task id. Recomputed when tasks change, or when a label is set here. */
    val labelsByTask: StateFlow<Map<String, List<String>>> = combine(repo.observeTasks(), labelRevision) { all, _ ->
        all.associate { t -> t.id to repo.labelsFor(t.id).map { it.name } }
    }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val events = MutableStateFlow<List<CalendarEvent>>(emptyList())

    /** Today's meetings from the device calendar, after [refreshCalendar]. */
    val todayEvents: StateFlow<List<CalendarEvent>> = events.asStateFlow()

    private val plan = MutableStateFlow<PlanSession?>(null)
    val planState: StateFlow<PlanSession?> = plan.asStateFlow()

    private val undoState = MutableStateFlow<TaskUndo?>(null)
    val undo: StateFlow<TaskUndo?> = undoState.asStateFlow()

    fun calendarAllowed(): Boolean = calendar.allowed()

    fun dismissUndo() {
        undoState.value = null
    }

    fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    fun sectionsOf(listId: String): Flow<List<TaskSection>> = repo.observeSections(listId)

    suspend fun snapshotTasks(): List<Task> = repo.observeTasks().first()

    suspend fun snapshotLists(): List<TaskList> = repo.observeLists().first()

    /** Reads today's meetings. A no-op result when the calendar is not granted. */
    suspend fun refreshCalendar() {
        val day = today()
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        events.value = runCatching { calendar.between(from, to) }.getOrDefault(emptyList())
    }

    // ---- Completion ----

    /** Ticks or unticks a task. A repeating task rolls forward instead of closing. Returns it as it now is. */
    suspend fun toggleDone(task: Task): Task? {
        val after = repo.toggleDone(task.id) ?: return null
        after.blockId?.let { setBlockChecked(it, after.done) }
        if (!task.done) undoState.value = TaskUndo(completionMessage(task, after), listOf(task))
        return after
    }

    /** Ticks a task from an event handler. Screens outside the Tasks app call this. */
    fun toggle(task: Task) {
        viewModelScope.launch { toggleDone(task) }
    }

    /** Saves a task, creating a named person first when [newPersonName] is given. The Work and Notes editors use this. */
    fun save(task: Task, newPersonName: String?) = viewModelScope.launch {
        val personId = newPersonName?.takeIf { it.isNotBlank() }?.let { repo.savePerson(it).id } ?: task.personId
        val saved = repo.save(task.copy(personId = personId))
        saved.noteId?.let { note -> personId?.let { repo.linkNote(note, it) } }
    }

    /** Moves a task to the Trash, with Undo. The Work and Notes editors use this. */
    fun delete(task: Task) = viewModelScope.launch { deleteTasks(listOf(task.id)) }

    /** Completes every open task in [ids] as one undoable step. Returns how many were completed. */
    suspend fun completeAll(ids: Collection<String>): Int {
        val before = ids.mapNotNull { repo.get(it) }.filter { !it.done }
        before.forEach { t ->
            repo.toggleDone(t.id)?.let { after ->
                after.blockId?.let { setBlockChecked(it, after.done) }
            }
        }
        if (before.isNotEmpty()) {
            undoState.value = TaskUndo("Completed ${TaskViews.count(before.size, "task")}", before)
        }
        return before.size
    }

    private suspend fun completionMessage(before: Task, after: Task): String {
        val next = after.dueAt
        if (!after.done && next != null) {
            return "Done · next one ${TaskViews.dayLabel(TaskViews.dayOf(next, zone), today())}"
        }
        if (before.sourceItemId != null) {
            val who = repo.allPeople().firstOrNull { it.id == before.personId }?.name
            return if (who != null) "Done · your promise to $who is closed too" else "Done · your promise is closed too"
        }
        return "Done"
    }

    /** Puts the last change back: ticks reopen, edits are written back, deletes are restored. */
    suspend fun undoLast() {
        val last = undoState.value ?: return
        undoState.value = null
        if (last.deleted) {
            last.before.forEach { repo.restore(it.id) }
            return
        }
        last.before.forEach { before ->
            val now = repo.get(before.id) ?: return@forEach
            val plain = before.repeatRule == null && before.repeat == TaskRepeat.NONE
            if (now.done != before.done && plain) {
                repo.toggleDone(before.id)
                before.blockId?.let { setBlockChecked(it, before.done) }
            } else {
                repo.save(before)
            }
        }
    }

    // ---- Editing one task ----

    suspend fun setTitle(id: String, title: String) {
        val t = repo.get(id) ?: return
        if (title.isNotBlank() && title.trim() != t.title) repo.save(t.copy(title = title.trim()))
    }

    suspend fun setNotes(id: String, notes: String) {
        val t = repo.get(id) ?: return
        if (notes != t.notes) repo.save(t.copy(notes = notes))
    }

    suspend fun setPriority(ids: Collection<String>, priority: Int) {
        ids.forEach { repo.setPriority(it, priority) }
    }

    /**
     * Sets the date, time and repeat of each task. A timed date also gets a reminder 30 minutes
     * before it. Without a time, the reminder stays as it was.
     */
    suspend fun applyDate(ids: Collection<String>, choice: DateChoice) {
        ids.forEach { id ->
            val t = repo.get(id) ?: return@forEach
            val due = choice.dueAt
            val remind = if (due != null && choice.hasTime) due - REMINDER_LEAD_MS else t.remindAt
            repo.save(
                t.copy(
                    dueAt = due,
                    hasTime = choice.hasTime && due != null,
                    repeatRule = choice.repeatRule,
                    repeat = QuickAddParser.legacyRepeat(choice.repeatRule),
                    remindAt = remind
                )
            )
        }
    }

    /** Files tasks into [listId] (null is the Inbox), taking that list's space. */
    suspend fun moveTo(ids: Collection<String>, listId: String?) {
        val target = listId?.let { id -> lists.value.firstOrNull { it.id == id } ?: snapshotLists().firstOrNull { it.id == id } }
        ids.forEachIndexed { i, id ->
            repo.move(id, listId, null, clock().toDouble() + i)
            if (target != null) repo.get(id)?.let { repo.save(it.copy(space = target.space)) }
        }
    }

    /** "Someone else is doing this": the tasks move to Waiting on under [name]. A null name takes them back. */
    suspend fun setWaitingOn(ids: Collection<String>, name: String?) {
        val person = name?.trim()?.takeIf { it.isNotEmpty() }?.let { repo.savePerson(it) }
        ids.forEach { id ->
            val t = repo.get(id) ?: return@forEach
            repo.save(t.copy(waitingOn = person != null, personId = person?.id ?: t.personId))
        }
    }

    suspend fun setLabels(id: String, names: List<String>) {
        repo.setLabels(id, names)
        labelRevision.value++
    }

    suspend fun addSubtask(parentId: String, title: String): Task? =
        if (title.isBlank()) null else repo.addSubtask(parentId, title.trim())

    suspend fun addSection(listId: String, name: String) {
        if (name.isNotBlank()) repo.saveSection(listId, name.trim())
    }

    suspend fun deleteSection(id: String) = repo.deleteSection(id)

    suspend fun createList(name: String, space: String): TaskList? = repo.createList(name, space)

    /** Moves the tasks to the Trash, with Undo. */
    suspend fun deleteTasks(ids: Collection<String>) {
        val before = ids.mapNotNull { repo.get(it) }
        if (before.isEmpty()) return
        before.forEach { repo.delete(it.id) }
        undoState.value = TaskUndo("Deleted ${TaskViews.count(before.size, "task")}", before, deleted = true)
    }

    // ---- Quick add ----

    /** Reads a quick-add line for the sheet's live preview. Pure apart from the clock. */
    fun parseQuickAdd(text: String): QuickAddResult =
        QuickAddParser.parse(text, clock(), lists.value.map { QuickAddList(it.id, it.name) })

    /** Creates the task a quick-add line describes. The Inbox when no #list matched. Null for a blank line. */
    suspend fun quickAdd(text: String): Task? {
        if (text.isBlank()) return null
        val parsed = QuickAddParser.parse(text, clock(), snapshotLists().map { QuickAddList(it.id, it.name) })
        if (parsed.title.isBlank()) return null
        val saved = repo.save(
            Task(
                id = "",
                title = parsed.title,
                dueAt = parsed.dueAt,
                hasTime = parsed.hasTime,
                listId = parsed.listId,
                priority = parsed.priority,
                repeatRule = parsed.repeatRule,
                repeat = QuickAddParser.legacyRepeat(parsed.repeatRule)
            )
        )
        if (parsed.labels.isNotEmpty()) {
            repo.setLabels(saved.id, parsed.labels)
            labelRevision.value++
        }
        return saved
    }

    // ---- Plan my day ----

    /** Starts a plan over today's candidates, in the order [TaskViews.planCandidates] gives. */
    suspend fun startPlan(): PlanSession {
        val queue = TaskViews.planCandidates(snapshotTasks(), today(), zone).map { it.id }
        val session = PlanSession(queue)
        plan.value = session
        return session
    }

    fun endPlan() {
        plan.value = null
    }

    /**
     * Decides the card on screen and moves to the next. TODAY moves the task to today (keeping its
     * time of day). NOT_TODAY leaves it alone. PICK applies [pick], which the date sheet produced.
     */
    suspend fun decidePlan(choice: PlanChoice, pick: DateChoice? = null) {
        val session = plan.value ?: return
        val id = session.current ?: return
        when (choice) {
            PlanChoice.TODAY -> repo.get(id)?.let { moveToDay(it, today()) }
            PlanChoice.NOT_TODAY -> Unit
            PlanChoice.PICK -> if (pick != null) applyDate(listOf(id), pick)
        }
        plan.value = session.copy(
            index = session.index + 1,
            todayCount = session.todayCount + if (choice == PlanChoice.TODAY) 1 else 0,
            notTodayCount = session.notTodayCount + if (choice == PlanChoice.NOT_TODAY) 1 else 0,
            pickedCount = session.pickedCount + if (choice == PlanChoice.PICK) 1 else 0
        )
    }

    private suspend fun moveToDay(t: Task, day: LocalDate) {
        val due = t.dueAt
        if (due != null && TaskViews.dayOf(due, zone) == day) return
        repo.reschedule(t.id, onDay(t.dueAt, t.hasTime, day), t.hasTime)
    }

    /** The same clock time on [day] as [dueAt] had, or the start of [day] when there was no time. */
    private fun onDay(dueAt: Long?, hasTime: Boolean, day: LocalDate): Long {
        if (!hasTime || dueAt == null) return day.atStartOfDay(zone).toInstant().toEpochMilli()
        val time = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalTime()
        return day.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** The rows a smart view shows, from the current data. The screens compute the same thing from [tasks]. */
    fun rowsFor(view: SmartView, all: List<Task>, lists: List<TaskList>, scope: TaskScope = TaskScope.ALL): List<Task> =
        TaskViews.viewOf(view, TaskViews.scoped(all, scope, lists), today(), zone)

    private companion object {
        const val REMINDER_LEAD_MS = 30 * 60_000L
    }
}
