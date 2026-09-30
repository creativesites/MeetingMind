package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import com.craftflowtechnologies.meetingmind.ai.routing.LanguageModelFactory
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEvidenceEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.SegmentSignalEntity
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelTier
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/*
 * Change detection (docs/PLAN_PROFESSIONAL.md D6): when a recording says a date moved, a decision
 * was reversed or the scope changed, find what it changes and PROPOSE the supersession. Nothing is
 * changed until the person confirms it in the Wrap-up. A cheap lexical and date match runs first;
 * a model looks only at the candidates that survive, and never leaves the phone for material that
 * has to stay on it.
 */

enum class ChangeReason { DATE_MOVED, REVERSED, REPLACED }

/** "This replaces: Launch Oct 14?" — with the words and moment that say so. */
data class ChangeProposal(
    val id: String,
    val meetingId: String,
    val signalId: String,
    val newKind: ItemKind,
    val newText: String,
    val newValue: String?,
    val segmentIds: List<String>,
    val startMs: Long?,
    val quote: String,
    val target: ItemEntity,
    val reason: ChangeReason,
    val score: Double,
    val checkedByModel: Boolean = false
) {
    /** The question the Changes card asks. */
    val question: String get() = "This replaces: ${target.text}?"
}

/** A model for the confirming check, resolved for one recording under its privacy rules. */
fun interface ChangeModels {
    /** The model allowed to see this recording, or null: no check runs and the lexical proposal stands. */
    suspend fun forMeeting(meetingId: String): LanguageModel?
}

/**
 * The production choice of model. A recording that must stay on the device (confidential project,
 * person or organisation, keep-on-device, Clinical and Legal) only ever gets the local model, and
 * the cloud transport isn't so much as asked; anything else follows the person's processing mode.
 */
class DeviceChangeModels(
    private val context: Context,
    private val modelStorage: ModelStorage,
    private val transport: GeminiTransport,
    private val profile: suspend () -> ProcessingProfile
) : ChangeModels {
    override suspend fun forMeeting(meetingId: String): LanguageModel? {
        val factory = LanguageModelFactory(context, modelStorage, transport)
        if (WorkPrivacy.mustStayOnDevice(context, meetingId = meetingId)) {
            return factory.resolveLocal(ModelCapability.SYNTHESIS, ModelTier.RECOMMENDED)?.languageModel
        }
        return factory.resolve(profile(), ModelCapability.SYNTHESIS, ModelTier.RECOMMENDED)?.languageModel
    }
}

/** A signal as the Wrap-up and change detection see it: its rows folded back into one. */
data class SignalView(
    val id: String, val kind: ItemKind, val text: String, val segmentIds: List<String>, val value: String?, val speakerId: String?, val confidence: Float, val startMs: Long?, val quote: String
) {
    /** For a commitment: who it is owed to and when, as the model gave them. */
    val counterparty: String? get() = detail("counterparty")
    val due: String? get() = detail("due")
    private fun detail(key: String) = value?.let { v -> runCatching { JSONObject(v).optString(key).ifBlank { null } }.getOrNull() }
}

class ChangeDetector(
    private val database: MeetMindDatabase,
    private val models: ChangeModels? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.itemDao()
    /** The model's answer per proposal, so editing the Wrap-up doesn't ask it (or the network) again. */
    private val verdicts = HashMap<String, Boolean?>()

    /** The signals of a recording, one per signal, with the moment and words that back them. */
    suspend fun signals(meetingId: String): List<SignalView> = withContext(Dispatchers.IO) {
        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId).associateBy { it.id }
        database.signalDao().forMeeting(meetingId).groupBy { it.signalId.ifBlank { it.id } }.mapNotNull { (id, rows) -> rows.toView(id, segments) }
    }

    private fun List<SegmentSignalEntity>.toView(id: String, segments: Map<String, com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity>): SignalView? {
        val first = firstOrNull() ?: return null
        val kind = ItemKind.entries.firstOrNull { it.name == first.kind } ?: return null
        val segs = mapNotNull { segments[it.segmentId] }.sortedBy { it.startMs }
        return SignalView(id, kind, first.text, map { it.segmentId }, first.value, first.entityId, first.confidence, segs.firstOrNull()?.startMs,
            segs.joinToString(" ") { it.text.trim() }.take(ItemPromotion.QUOTE_LIMIT))
    }

    /**
     * What this recording changes. [dismissed] are proposals the person already turned down.
     * [useModel] is false when applying a confirmed change, which doesn't need a second opinion.
     */
    suspend fun detect(meetingId: String, dismissed: Set<String> = emptySet(), useModel: Boolean = true): List<ChangeProposal> = withContext(Dispatchers.IO) {
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return@withContext emptyList()
        val note = meeting.noteId?.let { database.noteDao().getById(it) }
        val projectId = note?.notebookId
        val orgId = projectId?.let { database.notebookDao().getById(it) }?.let { ContextRepository.orgOf(it.propertiesJson) }
        val targets = candidates(meetingId, projectId, orgId)
        if (targets.isEmpty()) return@withContext emptyList()
        val proposals = signals(meetingId).filter { it.kind in NEW_KINDS && it.confidence >= MIN_CONFIDENCE }.flatMap { s ->
            targets.mapNotNull { t -> match(s, t)?.let { (reason, score) ->
                ChangeProposal("${s.id}:${t.id}", meetingId, s.id, s.kind, s.text, s.value, s.segmentIds, s.startMs, s.quote, t, reason, score)
            } }
        }
        // One replacement per new statement: the closest match. And one new statement per old item.
        val best = proposals.groupBy { it.signalId }.map { (_, list) -> list.maxByOrNull { it.score }!! }
            .groupBy { it.target.id }.map { (_, list) -> list.maxByOrNull { it.score }!! }
            .filter { it.id !in dismissed }.sortedByDescending { it.score }
        if (!useModel || models == null) return@withContext best
        confirm(best, models.forMeeting(meetingId))
    }

    /** Open or active items on the project (or, with none, the organisation) that a new statement could replace. */
    private suspend fun candidates(meetingId: String, projectId: String?, orgId: String?): List<ItemEntity> {
        val scope = when {
            projectId != null -> dao.around(listOf(projectId))
            orgId != null -> dao.around(listOf(orgId))
            else -> return emptyList()
        }
        return scope.filter { it.meetingId != meetingId && !isSuperseded(it) && when (it.itemKind) {
            ItemKind.DECISION -> it.itemStatus == ItemStatus.ACTIVE || it.itemStatus == ItemStatus.PROPOSED
            ItemKind.DEADLINE, ItemKind.SCOPE_CHANGE -> it.itemStatus == ItemStatus.OPEN
            else -> false
        } }
    }

    private fun isSuperseded(i: ItemEntity) = i.itemStatus == ItemStatus.SUPERSEDED

    /** The cheap stage: same subject, then a different date, a reversal, or a replacement. */
    internal fun match(s: SignalView, old: ItemEntity): Pair<ChangeReason, Double>? {
        // A decision replaces a decision; a date or scope statement can replace any of the three.
        if (s.kind == ItemKind.DECISION && old.itemKind != ItemKind.DECISION) return null
        val newDate = ChangeSentences.findDate(s.text) ?: s.value?.let { ChangeSentences.findDate(it) }
        val oldDate = ChangeSentences.findDate(old.text) ?: old.value?.let { ChangeSentences.findDate(it) }
        val newWords = words(newDate?.let { ChangeSentences.subject(s.text, it) } ?: s.text)
        val oldWords = words(oldDate?.let { ChangeSentences.subject(old.text, it) } ?: old.text)
        if (newWords.isEmpty() || oldWords.isEmpty()) return null
        val overlap = newWords.intersect(oldWords).size.toDouble() / minOf(newWords.size, oldWords.size)
        if (normalise(s.text) == normalise(old.text)) return null
        return when {
            newDate != null && oldDate != null && newDate.label != oldDate.label && overlap >= 0.6 -> ChangeReason.DATE_MOVED to 0.9 + 0.05 * overlap
            overlap >= 0.6 && REVERSAL.containsMatchIn(s.text) && !REVERSAL.containsMatchIn(old.text) -> ChangeReason.REVERSED to 0.7 + 0.1 * overlap
            s.kind == ItemKind.DECISION && overlap >= 0.75 && newDate == oldDate -> ChangeReason.REPLACED to 0.5 + 0.1 * overlap
            else -> null
        }
    }

    /** The model's confirming look, for the candidates that survived. A firm date move stands on its own. */
    private suspend fun confirm(candidates: List<ChangeProposal>, model: LanguageModel?): List<ChangeProposal> {
        if (model == null) return candidates
        return candidates.mapNotNull { p ->
            if (p.reason == ChangeReason.DATE_MOVED) return@mapNotNull p
            val answer = if (verdicts.containsKey(p.id)) verdicts[p.id] else when (val reply = model.generate(prompt(p), maxOutputTokens = 80)) {
                is AiResult.Success -> verdict(reply.value)
                else -> null
            }.also { verdicts[p.id] = it }
            when (answer) {
                false -> null
                true -> p.copy(checkedByModel = true)
                null -> p
            }
        }
    }

    private fun prompt(p: ChangeProposal) = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Earlier statement: \"${p.target.text}\"")
        appendLine("New statement, said later: \"${p.newText}\"")
        if (p.quote.isNotBlank()) appendLine("Exactly as said: \"${p.quote}\"")
        appendLine()
        appendLine("Does the new statement replace or reverse the earlier one, so the earlier one no longer holds? Answer with JSON only: {\"replaces\": true} or {\"replaces\": false}.")
    }

    private fun verdict(raw: String): Boolean? = runCatching {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val json = JSONObject(cleaned.substring(cleaned.indexOf('{'), cleaned.lastIndexOf('}') + 1))
        if (json.has("replaces")) json.getBoolean("replaces") else null
    }.getOrNull()

    // ---------------------------------------------------------------- applying

    /**
     * The person confirmed [p]: the item made from this recording replaces the old one. If the
     * Wrap-up already promoted the same statement as a decision, that item is used; otherwise one is made.
     */
    suspend fun apply(p: ChangeProposal, items: ItemRepository = ItemRepository(database, clock)): ItemEntity? = withContext(Dispatchers.IO) {
        val old = dao.getById(p.target.id) ?: return@withContext null
        val fromMeeting = dao.forMeeting(p.meetingId)
        val existing = fromMeeting.firstOrNull { it.kind == p.newKind.name && it.id != old.id && similar(it.text, p.newText) }
        val evidenceId = existing?.let { dao.evidenceFor(it.id).firstOrNull()?.id }
        if (existing != null) {
            items.supersedeWith(old.id, existing.id, evidenceId)
            return@withContext dao.getById(existing.id)
        }
        val meeting = database.meetingDao().getMeetingById(p.meetingId)
        val status = if (p.newKind == ItemKind.DECISION && p.newValue.equals("PROPOSED", true)) ItemStatus.PROPOSED else if (p.newKind == ItemKind.DECISION) ItemStatus.ACTIVE else ItemStatus.OPEN
        val links = listOfNotNull(
            ItemLinkEntity("", LinkType.MEETING, p.meetingId, "SOURCE"),
            meeting?.noteId?.let { ItemLinkEntity("", LinkType.NOTE, it, "SOURCE") },
            old.projectId?.let { ItemLinkEntity("", LinkType.PROJECT, it, "PROJECT") },
            old.orgId?.let { ItemLinkEntity("", LinkType.ORG, it, "ORG") }
        )
        val segments = database.transcriptDao().getSegmentsForMeetingDirect(p.meetingId).filter { it.id in p.segmentIds }
        val evidence = segments.takeIf { it.isNotEmpty() }?.let {
            ItemEvidenceEntity("", "", p.meetingId, meeting?.noteId, null, jsonList(it.map { s -> s.id }), it.minOf { s -> s.startMs }, it.maxOf { s -> s.endMs }, p.quote)
        }
        items.supersede(
            old.id,
            ItemEntity("", p.newKind.name, status.name, p.newText, value = p.newValue?.takeIf { !it.equals("PROPOSED", true) }, projectId = old.projectId, orgId = old.orgId, meetingId = p.meetingId,
                noteId = meeting?.noteId, reviewed = true, source = ItemSource.AI, sourceFindingId = "sig_${p.signalId}", createdAt = meeting?.createdAt ?: clock(), updatedAt = clock()),
            listOfNotNull(evidence), links
        )
    }

    private fun similar(a: String, b: String): Boolean {
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x.intersect(y).size.toDouble() / minOf(x.size, y.size) >= 0.8
    }

    companion object {
        private val NEW_KINDS = setOf(ItemKind.DECISION, ItemKind.DEADLINE, ItemKind.SCOPE_CHANGE)
        private const val MIN_CONFIDENCE = 0.5f
        private val REVERSAL = Regex("""(?i)\b(not|no longer|instead|scrap(ped)?|cancel(l?ed)?|drop(ped)?|reverse[d]?|rather than|won't|will not|abandon(ed)?)\b""")
        private val STOP = setOf("the", "a", "an", "is", "are", "was", "to", "of", "on", "in", "at", "for", "we", "will", "be", "it", "and", "that", "this", "our", "now", "then", "so", "with", "by", "go", "going")

        internal fun normalise(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()

        internal fun words(text: String): Set<String> =
            normalise(text).split(' ').filter { it.length > 1 && it !in STOP && it !in REVERSAL_WORDS }.toSet()

        private val REVERSAL_WORDS = setOf("not", "no", "longer", "instead", "scrapped", "scrap", "cancel", "cancelled", "canceled", "dropped", "drop", "reversed", "rather", "than", "wont", "abandoned")
    }
}
