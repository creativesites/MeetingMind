package com.craftflowtechnologies.meetingmind.feature.work

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.craftflowtechnologies.meetingmind.ai.assistant.AskScope
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.work.ContextHeader
import com.craftflowtechnologies.meetingmind.core.work.ContextRepository
import com.craftflowtechnologies.meetingmind.core.work.ContextType
import com.craftflowtechnologies.meetingmind.core.work.Direction
import com.craftflowtechnologies.meetingmind.core.work.ItemKind
import com.craftflowtechnologies.meetingmind.core.work.ItemRepository
import com.craftflowtechnologies.meetingmind.core.work.ItemStatus
import com.craftflowtechnologies.meetingmind.core.work.Pulse
import com.craftflowtechnologies.meetingmind.core.work.WorkPeople
import com.craftflowtechnologies.meetingmind.core.work.itemKind
import com.craftflowtechnologies.meetingmind.core.work.itemStatus
import kotlinx.coroutines.flow.first
import java.util.Calendar

/*
 * Logic behind the context pages, Prepare and Ask. The pure parts (status from counts, the
 * structured queries that Ask suggestions run, the removal prompt) are plain functions so they can
 * be tested without a database.
 */

private const val DAY_MS = 86_400_000L

/** The status line on a context page: a verdict and its reason. Computed from counts, never by AI. */
data class ContextStatusLine(val attention: Boolean, val label: String, val reason: String)

object ContextStatus {
    private val openStates = setOf(ItemStatus.OPEN, ItemStatus.UNCLEAR, ItemStatus.PROPOSED)

    fun isOpen(status: ItemStatus): Boolean = status in openStates

    /** Still open, and its due time has passed. */
    fun isPastDue(status: ItemStatus, dueAt: Long?, now: Long): Boolean = dueAt != null && dueAt < now && isOpen(status)

    fun pastDueCount(items: List<ItemEntity>, now: Long): Int = items.count { isPastDue(it.itemStatus, it.dueAt, now) }

    /**
     * "Needs attention" only when something is past due. Otherwise it states what is open and
     * never makes a judgement about the relationship or the project.
     */
    fun of(header: ContextHeader, pastDue: Int): ContextStatusLine {
        val open = header.youOwe + header.theyOwe + header.open
        return when {
            pastDue > 0 -> ContextStatusLine(
                attention = true,
                label = "Needs attention",
                reason = if (pastDue == 1) "1 item is past due" else "$pastDue items are past due"
            )
            open == 0 && header.decided == 0 -> ContextStatusLine(false, "Nothing open", "No open items or decisions yet")
            open == 0 -> ContextStatusLine(false, "On track", "${header.decided} decided, nothing open")
            else -> ContextStatusLine(false, "On track", "${header.youOwe} you owe · ${header.theyOwe} they owe · ${header.open} open")
        }
    }
}

/** The words of the confirmation that must come before a person is removed. Removing never happens without it. */
object PersonRemoval {
    const val CONFIRM_LABEL = "Remove"

    fun title(name: String): String = "Remove ${name.ifBlank { "this person" }}?"

    fun body(name: String): String {
        val who = name.ifBlank { "This person" }
        return "$who will be taken off People. Recordings, notes and tasks that mention them stay as they are, " +
            "and their speaker links are cleared."
    }
}

/** The structured queries that the "instant" Ask suggestions run. They need no model and work offline. */
enum class InstantQuery { WAITING_FOR, PROMISED, DECISIONS_THIS_MONTH, CHANGED_THIS_WEEK }

/** One line of an instant answer. [itemId] lets it open its source; [detail] is the due date or the topic. */
data class InstantLine(val text: String, val detail: String?, val itemId: String?, val meetingId: String?, val at: Long?)

/** One meeting that an entity is in. */
data class MeetingRef(val id: String, val title: String, val at: Long)

/** What a source sheet shows about one item: who said it, when, and the quote kept as evidence. */
data class SourceDetail(
    val itemId: String,
    val meetingId: String?,
    val title: String,
    val quote: String?,
    val startMs: Long?,
    val at: Long?,
    val speaker: String?,
    val reviewed: Boolean
)

/** Start of the calendar month that contains [now], in local time. */
fun monthStart(now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * Reads for the context pages, Prepare and Ask. Everything here comes from the existing
 * repositories; nothing writes.
 */
class ContextPageViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MeetMindDatabase.getInstance(application)
    private val context = ContextRepository(database)
    private val itemRepo = ItemRepository(database)
    private val people = WorkPeople(database)

    /** Reviewed and unreviewed items about the entity, as the context repository reads them. */
    suspend fun items(type: ContextType, id: String): List<ItemEntity> = context.items(type, id)

    suspend fun meetings(type: ContextType, id: String): List<MeetingRef> =
        context.meetings(type, id).map { MeetingRef(it.id, it.title.ifBlank { "Meeting" }, it.createdAt) }

    /** Projects that a person's items are filed under, by id and name. */
    suspend fun projectsTouched(type: ContextType, id: String): List<Pair<String, String>> {
        val ids = context.items(type, id).mapNotNull { it.projectId }.distinct()
        return ids.mapNotNull { pid ->
            database.notebookDao().getById(pid)?.takeIf { it.deletedAt == null }?.let { pid to it.name }
        }
    }

    /** The quote, moment, meeting and speaker behind one item. */
    suspend fun source(itemId: String): SourceDetail? {
        val item = itemRepo.get(itemId) ?: return null
        val evidence = itemRepo.evidence(itemId).firstOrNull()
        val meetingId = item.meetingId ?: evidence?.meetingId
        val meetingTitle = meetingId?.let { database.meetingDao().getMeetingById(it)?.title }?.takeIf { it.isNotBlank() }
        val speaker = (item.ownerPersonId ?: item.counterpartyPersonId)?.let { people.get(it)?.name }
        return SourceDetail(
            itemId = item.id,
            meetingId = meetingId,
            title = meetingTitle ?: item.text,
            quote = evidence?.quote?.takeIf { it.isNotBlank() },
            startMs = evidence?.startMs,
            at = item.createdAt,
            speaker = speaker,
            reviewed = item.reviewed
        )
    }

    /** Runs one structured query over the scope. No model is involved. */
    suspend fun instant(query: InstantQuery, scope: AskScope, now: Long = System.currentTimeMillis()): List<InstantLine> {
        val pool: List<ItemEntity> = if (scope.type != null && scope.id != null) {
            context.items(scope.type, scope.id)
        } else {
            (itemRepo.observeCommitments(Direction.MINE).first() +
                itemRepo.observeCommitments(Direction.THEIRS).first() +
                itemRepo.observeDecisions().first() +
                itemRepo.observeOpenQuestions().first()).distinctBy { it.id }
        }
        val inRange = pool.filter { (scope.from == null || it.createdAt >= scope.from) && (scope.to == null || it.createdAt < scope.to) }
        return when (query) {
            InstantQuery.WAITING_FOR -> inRange
                .filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.THEIRS.name && ContextStatus.isOpen(it.itemStatus) }
                .sortedBy { it.dueAt ?: Long.MAX_VALUE }
                .map { it.toLine() }
            InstantQuery.PROMISED -> inRange
                .filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.MINE.name && ContextStatus.isOpen(it.itemStatus) }
                .sortedBy { it.dueAt ?: Long.MAX_VALUE }
                .map { it.toLine() }
            InstantQuery.DECISIONS_THIS_MONTH -> {
                val start = monthStart(now)
                pool.filter {
                    it.itemKind == ItemKind.DECISION && it.createdAt >= start &&
                        it.itemStatus != ItemStatus.SUPERSEDED && it.itemStatus != ItemStatus.REVERSED
                }.sortedByDescending { it.createdAt }.map { it.toLine() }
            }
            InstantQuery.CHANGED_THIS_WEEK -> {
                val ids = pool.map { it.id }.toSet()
                Pulse(database).changesSince(now - 7 * DAY_MS)
                    .flatMap { group -> group.lines.map { InstantLine(it.text, group.title, it.itemId, null, it.at) } }
                    .filter { scope.type == null || (it.itemId != null && it.itemId in ids) }
                    .sortedByDescending { it.at ?: 0L }
            }
        }
    }

    private fun ItemEntity.toLine(): InstantLine =
        InstantLine(text, dueAt?.let { "due ${Pulse.shortDate(it)}" }, id, meetingId, createdAt)
}
