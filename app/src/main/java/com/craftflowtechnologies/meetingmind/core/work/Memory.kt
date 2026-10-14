package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MemoryStoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/*
 * Memory (docs/PLAN_PROFESSIONAL.md D5.5): the history view of a person, organisation or project.
 * The counts, "Most important" and "Still open" come from the database. The story is one cited
 * paragraph per month, written by the model when a month is missing or its item count has changed,
 * and kept, so it is never paid for twice.
 */

data class MemoryCounts(val meetings: Int, val decisions: Int, val commitments: Int, val questions: Int, val risks: Int)

data class MemoryMonth(val month: String, val itemCount: Int, val text: String?, val cites: List<String>)

data class MemoryHistory(val counts: MemoryCounts, val months: List<MemoryMonth>, val mostImportant: List<ItemEntity>, val stillOpen: List<ItemEntity>)

class MemoryRepository(
    private val database: MeetMindDatabase,
    private val models: WorkModels? = null,
    private val privacy: PackPrivacy = PackPrivacy { _, _ -> false },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val dao = database.itemDao()
    private val context = ContextRepository(database, clock)
    private val packs = ContextPackBuilder(database, privacy, clock)

    /** The history view. [tell] is false to read what's already written without asking a model. */
    suspend fun history(type: ContextType, id: String, tell: Boolean = true, months: Int = 12): MemoryHistory = withContext(Dispatchers.IO) {
        val now = clock()
        val items = context.items(type, id)
        val counts = MemoryCounts(
            meetings = context.meetings(type, id).size,
            decisions = items.count { it.itemKind == ItemKind.DECISION && it.itemStatus == ItemStatus.ACTIVE },
            commitments = items.count { it.itemKind == ItemKind.COMMITMENT && it.itemStatus != ItemStatus.CANCELLED },
            questions = items.count { it.itemKind == ItemKind.QUESTION },
            risks = items.count { it.itemKind == ItemKind.RISK }
        )
        val perMonth = changedPerMonth(items).entries.sortedByDescending { it.key }.take(months)
        // A first open on a long history writes only the newest few months; the rest follow on later opens.
        var written = 0
        val stories = perMonth.map { (month, count) ->
            val stored = database.memoryStoryDao().get(type.name, id, month)
            val stale = stored == null || stored.itemCount != count
            val allowed = tell && (!stale || written < MAX_WRITES_PER_OPEN)
            if (stale && allowed) written++
            story(type, id, month, count, allowed)
        }
        MemoryHistory(counts, stories, mostImportant(items, now), stillOpen(items))
    }

    /** Distinct items that changed in each month, by "yyyy-MM". */
    internal suspend fun changedPerMonth(items: List<ItemEntity>): Map<String, Int> {
        if (items.isEmpty()) return emptyMap()
        val events = items.map { it.id }.chunked(400).flatMap { dao.eventsForItems(it) }
        return events.groupBy { YearMonth.from(Instant.ofEpochMilli(it.at).atZone(zone)).toString() }.mapValues { (_, list) -> list.mapNotNull { it.itemId }.distinct().size }
    }

    /** One month's story: kept if the month still holds as many items as when it was written, else written again. */
    private suspend fun story(type: ContextType, id: String, month: String, itemCount: Int, tell: Boolean): MemoryMonth {
        val stored = database.memoryStoryDao().get(type.name, id, month)
        if (stored != null && stored.itemCount == itemCount) return stored.toMonth()
        if (!tell) return stored?.toMonth() ?: MemoryMonth(month, itemCount, null, emptyList())
        val written = write(type, id, month, itemCount) ?: return stored?.toMonth() ?: MemoryMonth(month, itemCount, null, emptyList())
        return written
    }

    private fun MemoryStoryEntity.toMonth() = MemoryMonth(month, itemCount, text.ifBlank { null }, citedIds())
    private fun MemoryStoryEntity.citedIds(): List<String> = runCatching { JSONArray(citedIdsJson).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    private suspend fun write(type: ContextType, id: String, month: String, itemCount: Int): MemoryMonth? {
        val ym = YearMonth.parse(month)
        val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val pack = packs.build(PackScope.Entity(type, id), from, to, onlyChanged = true)
        if (pack.items.isEmpty()) return null
        val model = models?.forPack(pack.sensitive) ?: return null
        val raw = (model.generate(prompt(pack, month), maxOutputTokens = 350) as? AiResult.Success)?.value ?: return null
        val sentences = BriefGrounding.keep(BriefGrounding.parse(raw, "story"), pack.ids)
        // A month the model couldn't cite is kept as empty, so it isn't asked again until the count moves.
        val text = sentences.joinToString(" ") { it.text }
        database.memoryStoryDao().upsert(MemoryStoryEntity(type.name, id, month, text, JSONArray(sentences.flatMap { it.cites }.distinct()).toString(), itemCount, clock()))
        return MemoryMonth(month, itemCount, text.ifBlank { null }, sentences.flatMap { it.cites }.distinct())
    }

    private fun prompt(pack: ContextPack, month: String) = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Tell what happened with \"${pack.title}\" in $month in one short paragraph of 2 to 4 sentences, using ONLY the facts below.")
        appendLine("Every sentence must cite the ids, in square brackets in the facts, of the items or quotes it rests on. A sentence with no citation will be thrown away. Never state a date, name, number or decision that is not in the facts.")
        appendLine("Answer with JSON only: {\"story\":[{\"text\":string,\"cites\":[string]}]}")
        appendLine()
        appendLine("Facts:")
        append(pack.render())
    }

    /** What matters most: overdue promises, decisions still to make, open risks, then recent decisions. */
    internal fun mostImportant(items: List<ItemEntity>, now: Long, limit: Int = 5): List<ItemEntity> {
        fun rank(i: ItemEntity) = when {
            i.isOverdue(now) -> 0
            i.itemStatus == ItemStatus.PROPOSED -> 1
            i.itemKind == ItemKind.RISK && i.itemStatus == ItemStatus.OPEN -> 2
            i.itemKind == ItemKind.DECISION && i.itemStatus == ItemStatus.ACTIVE -> 3
            i.itemStatus == ItemStatus.OPEN -> 4
            else -> 9
        }
        return items.filter { rank(it) < 9 }.sortedWith(compareBy<ItemEntity> { rank(it) }.thenByDescending { it.createdAt }).take(limit)
    }

    companion object { const val MAX_WRITES_PER_OPEN = 3 }

    internal fun stillOpen(items: List<ItemEntity>): List<ItemEntity> =
        items.filter { it.itemStatus in setOf(ItemStatus.OPEN, ItemStatus.UNCLEAR, ItemStatus.PROPOSED) }.sortedWith(compareBy<ItemEntity> { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.createdAt })
}
