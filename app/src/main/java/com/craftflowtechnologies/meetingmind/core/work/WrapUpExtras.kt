package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEvidenceEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * What a recording's signals add to the Wrap-up (docs/PLAN_PROFESSIONAL.md D6): promises nobody
 * could place, and other kinds worth keeping, behind "N more found". Signals the four lists
 * already cover are left out, so nothing is asked twice.
 */

/** Someone the person settled an unclear promise on: who owes it, and when. */
data class Settlement(val direction: Direction, val personId: String? = null, val dueText: String? = null)

/** What the person decided in the Wrap-up about the changes and the extra signals. */
data class WrapUpChoices(
    /** Change proposals confirmed ([ChangeProposal.id]). */
    val changes: Set<String> = emptySet(),
    /** Extra signals the person chose to keep. */
    val add: Set<String> = emptySet(),
    /** Extra signals or unclear promises dismissed. */
    val dismissed: Set<String> = emptySet(),
    /** Unclear promises settled, by signal id. */
    val settled: Map<String, Settlement> = emptyMap()
)

data class WrapUpExtras(
    /** Promises with no owner and no date: settle or dismiss. */
    val unclear: List<SignalView>,
    /** Every other signal the lists didn't cover, for "N more found". */
    val more: List<SignalView>
)

object WrapUpSignals {
    /** True when a finding already says the same thing from the same paragraphs. */
    private fun covered(s: SignalView, findings: List<Finding>): Boolean {
        val kinds = when (s.kind) {
            ItemKind.COMMITMENT -> setOf(FindingKind.ACTION, FindingKind.FOLLOW_UP)
            ItemKind.DECISION -> setOf(FindingKind.DECISION)
            ItemKind.QUESTION -> setOf(FindingKind.QUESTION)
            else -> return false
        }
        return findings.any { f -> f.kind in kinds && (f.sourceSegmentIds.any { it in s.segmentIds } || ChangeDetector.words(f.text).let { w -> val t = ChangeDetector.words(s.text); w.isNotEmpty() && t.isNotEmpty() && w.intersect(t).size.toDouble() / minOf(w.size, t.size) >= 0.8 }) }
    }

    fun isUnclear(s: SignalView) = s.kind == ItemKind.COMMITMENT && s.speakerId == null && s.due == null

    /** [proposals] are left out of "more found": the Changes card already shows them. */
    suspend fun extras(database: MeetMindDatabase, meetingId: String, findings: List<Finding>, proposals: List<ChangeProposal>, dismissed: Set<String>): WrapUpExtras = withContext(Dispatchers.IO) {
        val usedByChanges = proposals.map { it.signalId }.toSet()
        val open = ChangeDetector(database).signals(meetingId).filter { it.id !in dismissed && it.id !in usedByChanges && !covered(it, findings) && it.confidence >= 0.5f }
        WrapUpExtras(unclear = open.filter(::isUnclear), more = open.filter { !isUnclear(it) })
    }

    /** Which signals the person chose to dismiss, kept on the phone so they don't come back. */
    fun dismissed(context: Context, meetingId: String): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(key(meetingId), emptySet()).orEmpty()

    fun dismiss(context: Context, meetingId: String, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(key(meetingId), prefs.getStringSet(key(meetingId), emptySet()).orEmpty() + id).apply()
    }

    private fun key(meetingId: String) = "dismissed_signals_$meetingId"
    private const val PREFS = "work_state"
}

/** Makes items from signals the person kept, and keeps unsettled promises as UNCLEAR rather than guessing. */
class SignalPromotion(private val database: MeetMindDatabase, private val items: ItemRepository = ItemRepository(database)) {
    private val dao = database.itemDao()

    suspend fun promote(meetingId: String, choices: WrapUpChoices, extras: WrapUpExtras): List<ItemEntity> = withContext(Dispatchers.IO) {
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return@withContext emptyList()
        val out = mutableListOf<ItemEntity>()
        for (s in extras.more) if (s.id in choices.add) create(meeting.createdAt, meetingId, s, null)?.let { out += it }
        for (s in extras.unclear) when {
            s.id in choices.settled -> create(meeting.createdAt, meetingId, s, choices.settled.getValue(s.id))?.let { out += it }
            // Nobody settled it and nobody dismissed it: it is kept as unclear, never guessed.
            else -> create(meeting.createdAt, meetingId, s, null)?.let { out += it }
        }
        out
    }

    private suspend fun create(at: Long, meetingId: String, s: SignalView, settled: Settlement?): ItemEntity? {
        val fid = "sig_${s.id}"
        dao.bySourceFinding(fid)?.let { return it }
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return null
        val note = meeting.noteId?.let { database.noteDao().getById(it) }
        val notebook = note?.notebookId?.let { database.notebookDao().getById(it) }
        val orgId = notebook?.let { ContextRepository.orgOf(it.propertiesJson) }
        val speaker = s.speakerId?.let { database.workDao().speaker(it) }
        val selfId = database.workDao().self()?.id
        val direction: Direction? = when {
            settled != null -> settled.direction
            s.kind != ItemKind.COMMITMENT -> null
            speaker?.personId != null -> if (speaker.personId == selfId) Direction.MINE else Direction.THEIRS
            else -> null
        }
        val status = when {
            s.kind == ItemKind.COMMITMENT && direction == null -> ItemStatus.UNCLEAR
            s.kind == ItemKind.COMMITMENT -> ItemStatus.OPEN
            s.kind == ItemKind.DECISION -> if (s.value.equals("PROPOSED", true)) ItemStatus.PROPOSED else ItemStatus.ACTIVE
            s.kind == ItemKind.QUESTION -> ItemStatus.OPEN
            else -> ItemStatus.OPEN
        }
        val dueText = settled?.dueText ?: s.due
        val dueAt = DueDates.parse(dueText, meeting.createdAt)
        val owner = settled?.personId ?: if (direction == Direction.THEIRS) speaker?.personId else null
        val links = listOfNotNull(
            ItemLinkEntity("", LinkType.MEETING, meetingId, "SOURCE"),
            meeting.noteId?.let { ItemLinkEntity("", LinkType.NOTE, it, "SOURCE") },
            notebook?.let { ItemLinkEntity("", LinkType.PROJECT, it.id, "PROJECT") },
            orgId?.let { ItemLinkEntity("", LinkType.ORG, it, "ORG") },
            owner?.let { ItemLinkEntity("", LinkType.PERSON, it, "OWNER") }
        )
        val segs = database.transcriptDao().getSegmentsForMeetingDirect(meetingId).filter { it.id in s.segmentIds }
        val evidence = segs.takeIf { it.isNotEmpty() }?.let {
            ItemEvidenceEntity("", "", meetingId, meeting.noteId, null, jsonList(it.map { x -> x.id }), it.minOf { x -> x.startMs }, it.maxOf { x -> x.endMs }, s.quote)
        }
        return items.create(
            ItemEntity("", s.kind.name, status.name, s.text, value = s.value?.takeIf { s.kind != ItemKind.COMMITMENT && !it.equals("PROPOSED", true) },
                ownerPersonId = owner, ownerSpeakerId = if (owner == null && direction == Direction.THEIRS) s.speakerId else null,
                projectId = notebook?.id, orgId = orgId, meetingId = meetingId, noteId = meeting.noteId, dueAt = dueAt, dueText = dueText,
                direction = direction?.name, confidence = s.confidence, reviewed = true, source = ItemSource.AI, sourceFindingId = fid, createdAt = at, updatedAt = at),
            listOfNotNull(evidence), links
        )
    }
}
