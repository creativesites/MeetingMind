package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.database.BriefEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/*
 * The Intelligence Brief (docs/PLAN_PROFESSIONAL.md D5.3). Its structure is deterministic, read from
 * items: what changed, decisions, commitments, risks, questions, the next seven days, decisions
 * required, and the evidence. The model writes only two things, the executive picture and the
 * recommended next conversation, as sentences that each cite an id in the pack. A sentence whose
 * citations don't all resolve is dropped. With no model, the brief renders with no prose.
 */

enum class BriefKind(val label: String) { MEETING("Meeting brief"), CLIENT("Client brief"), PROJECT("Project status"), RELATIONSHIP("Relationship brief"), WEEKLY("Weekly brief") }

enum class BriefStatus(val label: String) { ON_TRACK("On track"), ATTENTION("Attention") }

/** One sentence of the model's, with the ids it stands on. */
data class BriefSentence(val text: String, val cites: List<String>)

/** A line of the brief, tied to the item whose evidence opens. */
data class BriefLine(val text: String, val itemId: String?, val detail: String? = null)

/** A quote in the appendix, with when it was said. */
data class BriefEvidence(val itemId: String, val evidenceId: String, val quote: String, val meetingId: String?, val meetingTitle: String?, val meetingAt: Long?, val startMs: Long?)

/** What a brief is about: its kind, and the thing it is about. */
data class BriefTarget(val kind: BriefKind, val scope: PackScope) {
    /** "MEETING/abc", "CLIENT/org1", "WEEKLY/-": how the screen and the route name it. */
    val key: String get() = "${kind.name}/${scope.cacheKey.second}"

    companion object {
        fun forMeeting(id: String) = BriefTarget(BriefKind.MEETING, PackScope.Meeting(id))
        fun forClient(orgId: String) = BriefTarget(BriefKind.CLIENT, PackScope.Entity(ContextType.ORG, orgId))
        fun forProject(id: String) = BriefTarget(BriefKind.PROJECT, PackScope.Entity(ContextType.PROJECT, id))
        fun forPerson(id: String) = BriefTarget(BriefKind.RELATIONSHIP, PackScope.Entity(ContextType.PERSON, id))
        val weekly = BriefTarget(BriefKind.WEEKLY, PackScope.AllWork)

        fun of(kind: BriefKind, id: String): BriefTarget = when (kind) {
            BriefKind.MEETING -> forMeeting(id)
            BriefKind.CLIENT -> forClient(id)
            BriefKind.PROJECT -> forProject(id)
            BriefKind.RELATIONSHIP -> forPerson(id)
            BriefKind.WEEKLY -> weekly
        }
    }
}

data class Brief(
    val target: BriefTarget,
    val title: String,
    val generatedAt: Long,
    val status: BriefStatus,
    /** Commitments completed, and all of them: progress from counts. Null when there are none. */
    val progress: Pair<Int, Int>?,
    /** The model's prose. Both are empty when there is no model, or nothing it wrote could be cited. */
    val executive: List<BriefSentence>,
    val changes: List<BriefLine>,
    val decisions: List<BriefLine>,
    val youOwe: List<BriefLine>,
    val theyOwe: List<BriefLine>,
    val risks: List<BriefLine>,
    val questions: List<BriefLine>,
    val next7: List<BriefLine>,
    val decisionsRequired: List<BriefLine>,
    val recommended: List<BriefSentence>,
    val evidence: List<BriefEvidence>,
    /** Prints a confidentiality line: this was built from material that stays on the device. */
    val confidential: Boolean,
    val from: Long? = null,
    val to: Long? = null
) {
    val hasProse: Boolean get() = executive.isNotEmpty() || recommended.isNotEmpty()
    /** Every line's item id, in order, for the evidence appendix. */
    val citedItemIds: List<String> get() = (changes + decisions + youOwe + theyOwe + risks + questions + next7 + decisionsRequired).mapNotNull { it.itemId }.distinct()
}

/** Keeps a sentence only if it cites something and every citation is an id in the pack. */
object BriefGrounding {
    fun keep(sentences: List<BriefSentence>, allowed: Set<String>): List<BriefSentence> =
        sentences.filter { it.text.isNotBlank() && it.cites.isNotEmpty() && it.cites.all { c -> c in allowed } }

    fun parse(raw: String, key: String): List<BriefSentence> = runCatching {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val json = JSONObject(cleaned.substring(cleaned.indexOf('{'), cleaned.lastIndexOf('}') + 1))
        val arr = json.optJSONArray(key) ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val cites = o.optJSONArray("cites")?.let { c -> (0 until c.length()).map { c.optString(it) } }.orEmpty().filter { it.isNotBlank() }
            BriefSentence(o.optString("text").trim(), cites)
        }
    }.getOrDefault(emptyList())

    fun toJson(executive: List<BriefSentence>, recommended: List<BriefSentence>): String = JSONObject()
        .put("executive", sentencesJson(executive)).put("recommended", sentencesJson(recommended)).toString()

    private fun sentencesJson(list: List<BriefSentence>) = JSONArray().also { a -> list.forEach { a.put(JSONObject().put("text", it.text).put("cites", JSONArray(it.cites))) } }
}

class BriefBuilder(
    private val database: MeetMindDatabase,
    private val models: WorkModels? = null,
    private val privacy: PackPrivacy = PackPrivacy { _, _ -> false },
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val packs = ContextPackBuilder(database, privacy, clock)

    /**
     * Builds the brief. The structure is always fresh; the prose comes from the cache unless the data changed since.
     * With [ask] false a model is never asked, so a screen can show the structure at once and the prose when it arrives.
     */
    suspend fun build(target: BriefTarget, refresh: Boolean = false, ask: Boolean = true): Brief = withContext(Dispatchers.IO) {
        val now = clock()
        val (from, to) = window(target, now)
        val pack = packs.build(target.scope, from, to)
        val (executive, recommended) = prose(target, pack, refresh, ask)
        assemble(target, pack, executive, recommended, now)
    }

    private fun window(target: BriefTarget, now: Long): Pair<Long?, Long?> = when (target.kind) {
        BriefKind.WEEKLY -> (now - 7 * Pulse.DAY) to null
        BriefKind.MEETING -> null to null
        else -> (now - 14 * Pulse.DAY) to null
    }

    // ---------------------------------------------------------------- the deterministic structure

    private fun assemble(target: BriefTarget, pack: ContextPack, executive: List<BriefSentence>, recommended: List<BriefSentence>, now: Long): Brief {
        val open = pack.items.filter { it.itemStatus in setOf(ItemStatus.OPEN, ItemStatus.UNCLEAR, ItemStatus.PROPOSED) }
        fun line(i: ItemEntity, detail: String? = null) = BriefLine(i.text, i.id, detail)
        fun due(i: ItemEntity) = i.dueAt?.let { if (i.isOverdue(now)) "overdue since ${Pulse.shortDate(it, Locale.ENGLISH)}" else "due ${Pulse.shortDate(it, Locale.ENGLISH)}" }
        fun owed(i: ItemEntity) = listOfNotNull(pack.ownerName(i), due(i)).joinToString(" · ").ifBlank { null }

        val commitments = pack.items.filter { it.itemKind == ItemKind.COMMITMENT && it.itemStatus != ItemStatus.CANCELLED }
        val youOwe = open.filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.MINE.name }.sortedBy { it.dueAt ?: Long.MAX_VALUE }.map { line(it, due(it)) }
        val theyOwe = open.filter { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.THEIRS.name }.sortedBy { it.dueAt ?: Long.MAX_VALUE }.map { line(it, owed(it)) }
        val risks = open.filter { it.itemKind == ItemKind.RISK }.map { line(it, it.severity) }
        val questions = open.filter { it.itemKind == ItemKind.QUESTION }.map { line(it) }
        val decisionsRequired = open.filter { it.itemKind == ItemKind.DECISION }.map { line(it) }
        val decisions = pack.items.filter { it.itemKind == ItemKind.DECISION && it.itemStatus == ItemStatus.ACTIVE && it.createdAt >= now - 90 * Pulse.DAY }
            .sortedByDescending { it.createdAt }.map { line(it, Pulse.shortDate(it.createdAt, Locale.ENGLISH)) }
        val next7 = open.filter { it.dueAt != null && it.dueAt < DueDates.startOfDay(now) + 7 * Pulse.DAY && it.itemKind != ItemKind.QUESTION }
            .sortedBy { it.dueAt }.map { line(it, due(it)) }
        val changes = pack.events.map { BriefLine(it.text, it.itemId, Pulse.shortDate(it.at, Locale.ENGLISH)) }

        val attention = pack.items.any { it.isOverdue(now) && it.itemKind in setOf(ItemKind.COMMITMENT, ItemKind.DEADLINE) } || decisionsRequired.isNotEmpty() || risks.isNotEmpty()
        val brief = Brief(
            target = target, title = pack.title.ifBlank { target.kind.label }, generatedAt = now,
            status = if (attention) BriefStatus.ATTENTION else BriefStatus.ON_TRACK,
            progress = commitments.size.takeIf { it > 0 }?.let { total -> commitments.count { it.itemStatus == ItemStatus.COMPLETED } to total },
            executive = executive, changes = changes, decisions = decisions, youOwe = youOwe, theyOwe = theyOwe, risks = risks, questions = questions,
            next7 = next7, decisionsRequired = decisionsRequired, recommended = recommended, evidence = emptyList(), confidential = pack.sensitive, from = pack.from, to = pack.to
        )
        val evidence = (brief.citedItemIds + executive.flatMap { it.cites } + recommended.flatMap { it.cites }).distinct().mapNotNull { id ->
            pack.evidence.firstOrNull { it.itemId == id || it.id == id }
        }.distinctBy { it.id }.map { BriefEvidence(it.itemId, it.id, it.quote, it.meetingId, it.meetingTitle, it.meetingAt, it.startMs) }
        return brief.copy(evidence = evidence)
    }

    // ---------------------------------------------------------------- the model's two things

    private suspend fun prose(target: BriefTarget, pack: ContextPack, refresh: Boolean, ask: Boolean): Pair<List<BriefSentence>, List<BriefSentence>> {
        if (pack.items.isEmpty()) return emptyList<BriefSentence>() to emptyList()
        val (type, id) = target.scope.cacheKey
        val dao = database.briefDao()
        val cached = dao.latest(type, id, target.kind.name)
        if (cached != null && !refresh && cached.createdAt >= pack.lastChangeAt) {
            val parsed = BriefGrounding.parse(cached.contentJson, "executive") to BriefGrounding.parse(cached.contentJson, "recommended")
            // Cited ids are checked again against today's pack: a cache never outvotes the record.
            return BriefGrounding.keep(parsed.first, pack.ids) to BriefGrounding.keep(parsed.second, pack.ids)
        }
        if (!ask) return emptyList<BriefSentence>() to emptyList()
        val model = models?.forPack(pack.sensitive) ?: return emptyList<BriefSentence>() to emptyList()
        val raw = (model.generate(prompt(target, pack), maxOutputTokens = 500) as? AiResult.Success)?.value ?: return emptyList<BriefSentence>() to emptyList()
        val executive = BriefGrounding.keep(BriefGrounding.parse(raw, "executive"), pack.ids)
        val recommended = BriefGrounding.keep(BriefGrounding.parse(raw, "recommended"), pack.ids)
        if (executive.isNotEmpty() || recommended.isNotEmpty()) {
            dao.clear(type, id, target.kind.name)
            dao.upsert(BriefEntity("brief_${UUID.randomUUID()}", type, id, target.kind.name, BriefGrounding.toJson(executive, recommended),
                JSONArray((executive + recommended).flatMap { it.cites }.distinct()).toString(), clock()))
        }
        return executive to recommended
    }

    private fun prompt(target: BriefTarget, pack: ContextPack) = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Write two short pieces for a ${target.kind.label.lowercase()} about \"${pack.title}\", using ONLY the facts below.")
        appendLine("1. \"executive\": 2 to 4 sentences giving the picture: where things stand, what changed, what matters most.")
        appendLine("2. \"recommended\": 1 to 3 sentences on what the next conversation should cover.")
        appendLine("Every sentence must cite the ids, in square brackets in the facts, of the items or quotes it rests on. A sentence with no citation will be thrown away. Never state a date, name, number or decision that is not in the facts.")
        appendLine("Answer with JSON only: {\"executive\":[{\"text\":string,\"cites\":[string]}],\"recommended\":[{\"text\":string,\"cites\":[string]}]}")
        appendLine()
        appendLine("Facts:")
        append(pack.render())
    }
}
