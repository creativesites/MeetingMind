package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Prepare (docs/PLAN_PROFESSIONAL.md D5.4), the database half: what to bring to a conversation,
 * built from items alone. The last meeting's line is a real quote with its time, so it can be played.
 */

/** The last meeting with someone, and one thing said in it. */
data class LastMeeting(val meetingId: String, val title: String, val at: Long, val quote: String?, val startMs: Long?, val itemId: String?)

enum class AgendaKind(val label: String) { OVERDUE("Overdue"), DECIDE("Decide"), FOLLOW_UP("Follow up"), RESOLVE("Resolve"), REVIEW("Review") }

/** One suggested agenda line: open items and proposed decisions only, ranked. */
data class AgendaLine(val kind: AgendaKind, val text: String, val itemId: String)

data class PrepPack(
    val title: String,
    val lastMeeting: LastMeeting?,
    val youOwe: List<ItemEntity>,
    val theyOwe: List<ItemEntity>,
    val decisions: List<ItemEntity>,
    val questions: List<ItemEntity>,
    val agenda: List<AgendaLine>
) {
    val stillOpen: Int get() = youOwe.size + theyOwe.size + questions.size + agenda.count { it.kind == AgendaKind.DECIDE }
    /** Nothing to prepare from yet: a first meeting. */
    val isEmpty: Boolean get() = lastMeeting == null && stillOpen == 0 && decisions.isEmpty()
}

class Prepare(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val context = ContextRepository(database, clock)
    private val dao = database.itemDao()

    suspend fun forEntity(type: ContextType, id: String, now: Long = clock()): PrepPack = withContext(Dispatchers.IO) {
        val title = when (type) {
            ContextType.PROJECT -> database.notebookDao().getById(id)?.name
            else -> database.peopleDao().getById(id)?.name
        }.orEmpty()
        build(title, context.items(type, id), context.meetings(type, id).firstOrNull()?.let { Triple(it.id, it.title, it.createdAt) }, now)
    }

    /** A calendar event: its project if the note is filed in one, otherwise the guests already known. */
    suspend fun forEvent(event: PulseEvent, now: Long = clock()): PrepPack = withContext(Dispatchers.IO) {
        val ref = context.resolveEvent(event)
        if (ref.projectId != null) return@withContext forEntity(ContextType.PROJECT, ref.projectId, now).copy(title = event.title)
        val people = ref.ids
        val items = if (people.isEmpty()) emptyList() else dao.around(people)
        val last = if (people.isEmpty()) null else database.workDao().meetingsWithPeople(people).firstOrNull()
        build(event.title, items, last?.let { Triple(it.id, it.title, it.createdAt) }, now)
    }

    private suspend fun build(title: String, items: List<ItemEntity>, last: Triple<String, String, Long>?, now: Long): PrepPack {
        val open = items.filter { it.itemStatus in setOf(ItemStatus.OPEN, ItemStatus.UNCLEAR, ItemStatus.PROPOSED) }
        val youOwe = open.filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.MINE.name }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        val theyOwe = open.filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.THEIRS.name }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        val questions = open.filter { it.itemKind == ItemKind.QUESTION }
        val decisions = items.filter { it.itemKind == ItemKind.DECISION && it.itemStatus == ItemStatus.ACTIVE && it.createdAt >= now - 90 * Pulse.DAY }
        return PrepPack(title, lastMeeting(last, items), youOwe, theyOwe, decisions, questions, agenda(open, now))
    }

    /** The last meeting, with the last decision or commitment said in it as a quote. */
    private suspend fun lastMeeting(last: Triple<String, String, Long>?, items: List<ItemEntity>): LastMeeting? {
        last ?: return null
        val evidence = items.filter { it.meetingId == last.first && (it.itemKind == ItemKind.DECISION || it.itemKind == ItemKind.COMMITMENT) }
            .flatMap { i -> dao.evidenceFor(i.id).map { i to it } }.filter { it.second.quote.isNotBlank() }.maxByOrNull { it.second.startMs ?: 0L }
        return LastMeeting(last.first, last.second, last.third, evidence?.second?.quote, evidence?.second?.startMs, evidence?.first?.id)
    }

    /** Overdue first, then decisions to make, what people owe, questions, and what I owe. */
    private fun agenda(open: List<ItemEntity>, now: Long): List<AgendaLine> {
        fun rank(i: ItemEntity): Int = when {
            i.isOverdue(now) -> 0
            i.itemKind == ItemKind.DECISION -> 1
            i.direction == Direction.THEIRS.name -> 2
            i.itemKind == ItemKind.QUESTION -> 3
            i.direction == Direction.MINE.name -> 4
            else -> 5
        }
        fun kind(i: ItemEntity) = when (rank(i)) { 0 -> AgendaKind.OVERDUE; 1 -> AgendaKind.DECIDE; 2, 4 -> AgendaKind.FOLLOW_UP; 3 -> AgendaKind.RESOLVE; else -> AgendaKind.REVIEW }
        return open.filter { it.itemKind != ItemKind.DECISION || it.itemStatus == ItemStatus.PROPOSED }
            .sortedWith(compareBy<ItemEntity> { rank(it) }.thenBy { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.createdAt })
            .take(MAX_AGENDA).map { AgendaLine(kind(it), it.text, it.id) }
    }

    companion object { const val MAX_AGENDA = 8 }
}
