package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * The context pack (docs/PLAN_PROFESSIONAL.md D5.3): one scoped, bounded bundle of items, evidence
 * quotes, meeting summaries and recent changes for a person, organisation, project, meeting or a
 * stretch of time. Briefs, Prepare's prose, monthly stories and scoped Ask all read through it, so
 * what a model is shown is always small, always about one thing, and always citable by id.
 */

sealed interface PackScope {
    data class Meeting(val id: String) : PackScope
    data class Entity(val type: ContextType, val id: String) : PackScope
    /** Everything in Work. */
    data object AllWork : PackScope

    /** The (entityType, entityId) a cached brief or story is filed under. */
    val cacheKey: Pair<String, String> get() = when (this) {
        is Meeting -> "MEETING" to id
        is Entity -> type.name to id
        AllWork -> "WORK" to "-"
    }
}

/** What was said, with where and when: a quote from the recording an item came from. */
data class PackEvidence(
    val id: String, val itemId: String, val meetingId: String?, val meetingTitle: String?, val meetingAt: Long?, val startMs: Long?, val quote: String
)

data class PackMeeting(val id: String, val title: String, val at: Long, val summary: String?)

data class ContextPack(
    val scope: PackScope,
    val title: String,
    val from: Long?,
    val to: Long?,
    val items: List<ItemEntity>,
    val evidence: List<PackEvidence>,
    val meetings: List<PackMeeting>,
    /** Recent changes as sentences, newest first, each tied to its item. */
    val events: List<TimelineEntry>,
    /** Everything the pack draws on is allowed to leave the phone only if this is false. */
    val sensitive: Boolean,
    val people: Map<String, String>
) {
    /** Every id a sentence written from this pack may cite. Anything else it cites is dropped. */
    val ids: Set<String> = items.map { it.id }.toSet() + evidence.map { it.id } + meetings.map { it.id }

    fun evidenceFor(itemId: String): PackEvidence? = evidence.firstOrNull { it.itemId == itemId }

    /** A last change in the pack, so cached prose knows when it went stale. */
    val lastChangeAt: Long get() = maxOf(items.maxOfOrNull { it.updatedAt } ?: 0L, events.maxOfOrNull { it.at } ?: 0L)

    fun ownerName(i: ItemEntity): String? = i.ownerPersonId?.let { people[it] }

    /** The pack as prompt text: one line per fact, led by its id in brackets. */
    fun render(): String = buildString {
        if (items.isNotEmpty()) {
            appendLine("Items:")
            items.forEach { i ->
                val bits = listOfNotNull(
                    i.direction?.let { if (it == Direction.MINE.name) "mine" else "theirs" }, ownerName(i)?.let { "owner $it" },
                    i.dueAt?.let { "due ${Pulse.shortDate(it, Locale.ENGLISH)}" }, i.severity?.let { "severity $it" }
                )
                appendLine("[${i.id}] ${i.kind} ${i.status}: ${i.text}" + if (bits.isNotEmpty()) " (${bits.joinToString(", ")})" else "")
                evidenceFor(i.id)?.let { appendLine("  said [${it.id}]: \"${it.quote}\"") }
            }
        }
        if (meetings.isNotEmpty()) {
            appendLine("Meetings:")
            meetings.forEach { m -> appendLine("[${m.id}] ${m.title} (${Pulse.shortDate(m.at, Locale.ENGLISH)})" + (m.summary?.let { ": $it" } ?: "")) }
        }
        if (events.isNotEmpty()) {
            appendLine("Recent changes:")
            events.forEach { e -> appendLine("- ${e.text}" + (e.itemId?.let { " [$it]" } ?: "")) }
        }
    }.trim()
}

class ContextPackBuilder(
    private val database: MeetMindDatabase,
    private val privacy: PackPrivacy = PackPrivacy { _, _ -> false },
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.itemDao()
    private val context = ContextRepository(database, clock)

    /**
     * [from] and [to] narrow a pack to a stretch of time: items that changed in it, plus, unless
     * [onlyChanged], everything still open. The pack is capped at [maxItems], most pressing first.
     */
    suspend fun build(scope: PackScope, from: Long? = null, to: Long? = null, onlyChanged: Boolean = false, maxItems: Int = MAX_ITEMS): ContextPack = withContext(Dispatchers.IO) {
        val now = clock()
        val scoped = when (scope) {
            is PackScope.Meeting -> dao.forMeeting(scope.id).filter { it.reviewed }
            is PackScope.Entity -> context.items(scope.type, scope.id)
            PackScope.AllWork -> dao.allLive().filter { it.reviewed }
        }
        val allEvents = if (scoped.isEmpty()) emptyList() else scoped.map { it.id }.chunked(400).flatMap { dao.eventsForItems(it) }
        val ranged = if (from == null && to == null) allEvents else allEvents.filter { (from == null || it.at >= from) && (to == null || it.at < to) }
        val changedIds = ranged.mapNotNull { it.itemId }.toSet()
        val inRange = if (from == null && to == null) scoped else scoped.filter { it.id in changedIds || (!onlyChanged && it.isOpen()) }
        val chosen = inRange.sortedWith(compareBy<ItemEntity> { rank(it, now) }.thenByDescending { it.updatedAt }).take(maxItems)
        val chosenIds = chosen.map { it.id }.toSet()

        val meetingsById = database.meetingDao()
        val evidence = chosen.mapNotNull { i ->
            val e = dao.evidenceFor(i.id).firstOrNull { it.quote.isNotBlank() } ?: return@mapNotNull null
            val m = e.meetingId?.let { meetingsById.getMeetingById(it) }
            PackEvidence(e.id, i.id, e.meetingId, m?.title, m?.createdAt, e.startMs, e.quote.take(QUOTE))
        }
        val meetingIds = (chosen.mapNotNull { it.meetingId } + (scope as? PackScope.Meeting)?.id.let { listOfNotNull(it) }).distinct()
        val meetings = meetingIds.mapNotNull { meetingsById.getMeetingById(it) }.sortedByDescending { it.createdAt }.take(MAX_MEETINGS)
            .map { PackMeeting(it.id, it.title, it.createdAt, it.summaryText?.trim()?.take(SUMMARY)?.ifBlank { null }) }

        val people = database.workDao().allPeople().associate { it.id to it.name }
        val speakers = chosen.mapNotNull { it.ownerSpeakerId }.distinct().mapNotNull { s -> database.workDao().speaker(s)?.let { s to it.customName.ifBlank { it.originalLabel } } }.toMap()
        val itemsById = chosen.associateBy { it.id }
        val events = ranged.filter { it.itemId in chosenIds }.mapNotNull { e ->
            val item = itemsById[e.itemId] ?: return@mapNotNull null
            ChangeSentences.of(e, item, people, speakers, Locale.ENGLISH)?.let { TimelineEntry(e.at, it, item.id, item.meetingId) }
        }.sortedByDescending { it.at }.take(MAX_EVENTS)

        val title = when (scope) {
            is PackScope.Meeting -> meetingsById.getMeetingById(scope.id)?.title.orEmpty()
            is PackScope.Entity -> if (scope.type == ContextType.PROJECT) database.notebookDao().getById(scope.id)?.name.orEmpty() else people[scope.id].orEmpty()
            PackScope.AllWork -> "My work"
        }
        val sensitive = privacy.mustStayOnDevice(meetingIds, scope)
        ContextPack(scope, title, from, to, chosen, evidence, meetings, events, sensitive, people)
    }

    /** Overdue first, then decisions to make, then what's open, then what's settled. */
    private fun rank(i: ItemEntity, now: Long): Int = when {
        i.isOverdue(now) -> 0
        i.itemStatus == ItemStatus.PROPOSED -> 1
        i.isOpen() -> 2
        else -> 3
    }

    private fun ItemEntity.isOpen() = itemStatus in setOf(ItemStatus.OPEN, ItemStatus.UNCLEAR, ItemStatus.PROPOSED)

    companion object {
        const val MAX_ITEMS = 60
        const val MAX_MEETINGS = 8
        const val MAX_EVENTS = 20
        const val QUOTE = 200
        const val SUMMARY = 400
    }
}
