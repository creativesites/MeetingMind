package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** The four saved-filter chips (docs/PLAN_PROFESSIONAL.md §4.4, D5.5), answered from the database. */
enum class SavedFilter(val label: String) {
    PROMISED("What have I promised?"),
    WAITING_ON_ME("Who's waiting on me?"),
    ACTIVE_DECISIONS("Active decisions (3 months)"),
    CHANGED_THIS_MONTH("What changed this month?")
}

/** A line in an answer: its words, the item whose evidence opens, and who it concerns. */
data class AnswerLine(val text: String, val itemId: String? = null, val personId: String? = null, val detail: String? = null)

data class SavedAnswer(val filter: SavedFilter, val lines: List<AnswerLine>)

class SavedViews(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.itemDao()
    private val context = ContextRepository(database, clock)

    /** Optionally narrowed to a person, organisation or project page. */
    suspend fun answer(filter: SavedFilter, scope: Pair<ContextType, String>? = null): SavedAnswer = withContext(Dispatchers.IO) {
        val now = clock()
        val items = if (scope != null) context.items(scope.first, scope.second) else dao.allLive().filter { it.reviewed }
        val people = database.workDao().allPeople().associate { it.id to it.name }
        SavedAnswer(filter, when (filter) {
            SavedFilter.PROMISED -> open(items).filter { it.direction == Direction.MINE.name }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
                .map { AnswerLine(it.text, it.id, detail = dueLabel(it, now)) }
            SavedFilter.WAITING_ON_ME -> waitingOnMe(open(items).filter { it.direction == Direction.MINE.name }, people, now)
            SavedFilter.ACTIVE_DECISIONS -> items.filter { it.itemKind == ItemKind.DECISION && it.itemStatus == ItemStatus.ACTIVE && it.createdAt >= now - 90 * Pulse.DAY }
                .sortedByDescending { it.createdAt }.map { AnswerLine(it.text, it.id, detail = Pulse.shortDate(it.createdAt)) }
            SavedFilter.CHANGED_THIS_MONTH -> changed(items, now)
        })
    }

    private fun open(items: List<ItemEntity>) = items.filter { it.itemKind == ItemKind.COMMITMENT && (it.itemStatus == ItemStatus.OPEN || it.itemStatus == ItemStatus.UNCLEAR) }

    /**
     * My open promises, by the person they're owed to: the counterparty when it's known,
     * otherwise the other people in the meeting they were made in.
     */
    private suspend fun waitingOnMe(mine: List<ItemEntity>, people: Map<String, String>, now: Long): List<AnswerLine> {
        val selfId = database.workDao().self()?.id
        val byPerson = LinkedHashMap<String, MutableList<ItemEntity>>()
        val unattributed = mutableListOf<ItemEntity>()
        for (i in mine) {
            val who = listOfNotNull(i.counterpartyPersonId).ifEmpty {
                dao.linksFor(i.id).filter { it.targetType == LinkType.PERSON && it.targetId != selfId }.map { it.targetId }
            }
            if (who.isEmpty()) unattributed += i else who.forEach { byPerson.getOrPut(it) { mutableListOf() } += i }
        }
        val lines = byPerson.entries.sortedByDescending { it.value.size }.flatMap { (pid, list) ->
            list.sortedBy { it.dueAt ?: Long.MAX_VALUE }.map { AnswerLine(it.text, it.id, pid, "${people[pid] ?: "Someone"} · ${dueLabel(it, now)}") }
        }
        return lines + unattributed.map { AnswerLine(it.text, it.id, detail = dueLabel(it, now)) }
    }

    /** Everything that changed since the first of the month, as the same sentences Pulse uses. */
    private suspend fun changed(items: List<ItemEntity>, now: Long): List<AnswerLine> {
        val start = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val ids = items.map { it.id }.toSet()
        return Pulse(database, clock).changesSince(start - 1).flatMap { it.lines }.filter { it.itemId in ids }.map { AnswerLine(it.text, it.itemId, detail = Pulse.shortDate(it.at)) }
    }

    private fun dueLabel(i: ItemEntity, now: Long) = when {
        i.dueAt == null -> "no date"
        i.isOverdue(now) -> "overdue since ${Pulse.shortDate(i.dueAt)}"
        else -> "due ${Pulse.shortDate(i.dueAt)}"
    }
}
