package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.timeline.WeekReview
import com.craftflowtechnologies.meetingmind.core.timeline.WorkWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A project that needs the person this week, and why in numbers. */
data class ProjectAttention(val projectId: String, val name: String, val overdue: Int, val proposedDecisions: Int, val openRisks: Int)

/**
 * The weekly review (docs/PLAN_PROFESSIONAL.md D5.6): the facts, the five questions, and what's
 * open to plan next week from. Everything is read from the record; nothing is inferred.
 */
data class WeeklyReviewData(
    val from: Long,
    val to: Long,
    /** The facts, as the same [WeekReview] the Today card shows, with its work numbers filled in. */
    val week: WeekReview,
    /** What changed: the item events of the week, grouped and worded by the Pulse. */
    val changed: List<ChangeGroup>,
    /** What I finished: commitments completed in the week. */
    val finished: List<ItemEntity>,
    /** What slipped: open items whose date has passed. */
    val slipped: List<ItemEntity>,
    /** Waiting on me: what I owe that hasn't slipped yet. */
    val waitingOnMe: List<ItemEntity>,
    /** Waiting on them: what people owe me. */
    val waitingOnThem: List<ItemEntity>,
    val projects: List<ProjectAttention>,
    /** Open questions, to carry or drop with everything else. */
    val questions: List<ItemEntity>
) {
    /** Everything the plan for next week goes through once: slipped first, then what I owe, then what's owed to me. */
    val planItems: List<ItemEntity> get() = slipped + waitingOnMe + waitingOnThem + questions
}

/** What to do with one open item in next week's plan. */
enum class PlanChoice { CARRY, RESCHEDULE, DROP }

data class PlanDecision(val itemId: String, val choice: PlanChoice, /** For RESCHEDULE: the new day. */ val dueAt: Long? = null, val dueText: String? = null)

class WeeklyReviews(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val items = ItemRepository(database, clock)
    private val dao = database.itemDao()
    private val work = WorkRepository(database)

    /** The last seven days up to [now], and everything open at it. */
    suspend fun build(now: Long = clock()): WeeklyReviewData = withContext(Dispatchers.IO) {
        val from = DueDates.startOfDay(now) - 6 * DAY
        val live = dao.allLive().filter { it.reviewed }
        val open = live.filter { !it.itemStatus.isEnd }
        val commitments = open.filter { it.kind == ItemKind.COMMITMENT.name }
        val slipped = open.filter { it.isOverdue(now) && (it.kind == ItemKind.COMMITMENT.name || it.kind == ItemKind.QUESTION.name) }.sortedBy { it.dueAt }
        val slippedIds = slipped.map { it.id }.toSet()
        val mine = commitments.filter { it.itemDirection != Direction.THEIRS && it.id !in slippedIds }.sortedWith(compareBy({ it.dueAt == null }, { it.dueAt }))
        val theirs = commitments.filter { it.itemDirection == Direction.THEIRS && it.id !in slippedIds }.sortedWith(compareBy({ it.dueAt == null }, { it.dueAt }))
        val questions = open.filter { it.kind == ItemKind.QUESTION.name && it.id !in slippedIds }
        val finished = live.filter { it.kind == ItemKind.COMMITMENT.name && it.itemStatus == ItemStatus.COMPLETED && (it.closedAt ?: it.updatedAt) in from..now }
            .sortedByDescending { it.closedAt ?: it.updatedAt }
        val meetings = database.workDao().readyMeetingsBetween(from, now).filter { (work.isWork(it.recordingType) || it.recordingType == "GENERAL") }
        val week = WeekReview(
            recordings = meetings.size, notes = 0, faith = 0, answered = live.count { it.kind == ItemKind.QUESTION.name && it.itemStatus == ItemStatus.ANSWERED && (it.answeredAt ?: 0) in from..now },
            minutesRecorded = (meetings.sumOf { it.durationMs } / 60_000).toInt(),
            work = WorkWeek(
                meetings = meetings.size,
                decisions = live.count { it.kind == ItemKind.DECISION.name && it.createdAt in from..now },
                commitmentsMade = live.count { it.kind == ItemKind.COMMITMENT.name && it.createdAt in from..now },
                commitmentsDone = finished.size,
                openQuestions = questions.size + slipped.count { it.kind == ItemKind.QUESTION.name },
                slipped = slipped.size
            )
        )
        val names = database.notebookDao().let { nb -> open.mapNotNull { it.projectId }.distinct().associateWith { id -> nb.getById(id)?.name } }
        val projects = open.filter { it.projectId != null }.groupBy { it.projectId!! }.mapNotNull { (id, list) ->
            val overdue = list.count { it.isOverdue(now) }
            val proposed = list.count { it.kind == ItemKind.DECISION.name && it.itemStatus == ItemStatus.PROPOSED }
            val risks = list.count { it.kind == ItemKind.RISK.name }
            if (overdue + proposed + risks == 0) null else ProjectAttention(id, names[id] ?: "Project", overdue, proposed, risks)
        }.sortedByDescending { it.overdue * 3 + it.proposedDecisions * 2 + it.openRisks }
        val changed = Pulse(database, clock).changesSince(from)
        WeeklyReviewData(from, now, week, changed, finished, slipped, mine, theirs, projects, questions)
    }

    /** Monday of the coming week, at the start of the day (the review's default "carry to"). */
    fun nextMonday(now: Long = clock()): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = DueDates.startOfDay(now) }
        do c.add(java.util.Calendar.DAY_OF_MONTH, 1) while (c.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY)
        return c.timeInMillis
    }

    /**
     * Next week's plan, in one pass. Carry keeps the item, and moves a date that has already
     * passed to the coming Monday (an item that isn't late keeps its date, and one with no date
     * stays undated). Reschedule sets the day given. Drop ends it: cancelled for a commitment,
     * dropped for a question, closed for anything else. Each change is logged with the item's
     * other history, and a linked task follows its date.
     */
    suspend fun applyPlan(decisions: List<PlanDecision>, now: Long = clock()): Int = withContext(Dispatchers.IO) {
        var changed = 0
        val monday = nextMonday(now)
        decisions.forEach { d ->
            val item = items.get(d.itemId) ?: return@forEach
            when (d.choice) {
                PlanChoice.CARRY -> if (item.isOverdue(now)) { moveTo(item, monday, null); changed++ }
                PlanChoice.RESCHEDULE -> if (d.dueAt != null) { moveTo(item, d.dueAt, d.dueText); changed++ }
                PlanChoice.DROP -> {
                    val end = when (item.kind) {
                        ItemKind.COMMITMENT.name -> ItemStatus.CANCELLED
                        ItemKind.QUESTION.name -> ItemStatus.DROPPED
                        else -> ItemStatus.CLOSED
                    }
                    items.setStatus(item.id, end); changed++
                }
            }
        }
        changed
    }

    private suspend fun moveTo(item: ItemEntity, at: Long, text: String?) {
        items.setDue(item.id, at, text)
        item.taskId?.let { id -> database.taskDao().getById(id)?.takeIf { it.doneAt == null }?.let { database.taskDao().upsert(it.copy(dueAt = at, updatedAt = clock())) } }
    }

    private companion object { const val DAY = 86_400_000L }
}
