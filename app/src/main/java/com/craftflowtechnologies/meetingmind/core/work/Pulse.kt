package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEventEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Work Pulse (docs/PLAN_PROFESSIONAL.md D5.1): what needs the person today, what changed, and
 * what's on the calendar. Pure queries over items — no model, so it works offline and in airplane
 * mode. It shows at most four things: being right beats being complete.
 */

enum class AttentionKind { YOU_OWE, DECISION_NEEDED, THEY_OWE, QUIET }
enum class AttentionAction { PLAY, NUDGE, PREPARE, OPEN }

/** One row of "Needs you". [actions] are in the order they're offered. */
data class AttentionRow(
    val kind: AttentionKind,
    val title: String,
    val detail: String,
    val itemId: String? = null,
    val taskId: String? = null,
    val meetingId: String? = null,
    val startMs: Long? = null,
    /** PERSON, ORG or PROJECT: where "open" and "prepare" go. */
    val entityType: ContextType? = null,
    val entityId: String? = null,
    /** Who to nudge. */
    val personId: String? = null,
    val dueAt: Long? = null,
    val actions: List<AttentionAction> = emptyList()
)

/** One specific sentence in "what changed", with the item it's about so its evidence opens. */
data class ChangeLine(val text: String, val itemId: String?, val at: Long)

/** Changes grouped under the project, organisation or person they belong to. */
data class ChangeGroup(val title: String, val entityType: ContextType?, val entityId: String?, val lines: List<ChangeLine>)

/** A calendar event, as Pulse and Prepare need it. */
data class PulseEvent(val key: String, val title: String, val begin: Long, val end: Long, val people: List<String> = emptyList(), val emails: List<String> = emptyList())

/** A calendar line with what's still open from the last time. [firstMeeting] is true with no history at all. */
data class PulseDayLine(
    val event: PulseEvent,
    val youOwe: Int, val theyOwe: Int, val questions: Int, val decisionsNeeded: Int,
    val withLabel: String?,
    val firstMeeting: Boolean
) { val open: Int get() = youOwe + theyOwe + questions + decisionsNeeded }

class Pulse(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.itemDao()
    private val context = ContextRepository(database, clock)

    // ---------------------------------------------------------------- attention

    /**
     * The rows that need the person, in this order:
     * 1. what I owe that is overdue or due today (commitments, and tasks with no commitment)
     * 2. decisions still proposed — first on a project with a meeting today
     * 3. what people owe me that is overdue, or has been open five days or more
     * 4. relationships gone quiet with things still open
     */
    suspend fun attention(now: Long = clock(), limit: Int = 4, projectIdsToday: Set<String> = emptySet(), quietDays: Int = 21): List<AttentionRow> = withContext(Dispatchers.IO) {
        val names = names()
        val endOfToday = DueDates.startOfDay(now) + DAY
        val out = mutableListOf<AttentionRow>()
        fun room() = out.size < limit

        // 1. What I owe.
        val openCommitments = dao.openCommitments()
        val linkedTaskIds = dao.openCommitmentTaskIds().toSet()
        val mineDue = openCommitments.filter { it.direction == Direction.MINE.name && it.dueAt != null && it.dueAt < endOfToday }.map { c ->
            Due(c.dueAt!!, AttentionRow(AttentionKind.YOU_OWE, c.text, dueDetail(c.dueAt, now, projectTitle(c)), itemId = c.id, taskId = c.taskId, meetingId = c.meetingId,
                startMs = dao.evidenceFor(c.id).firstOrNull()?.startMs, entityType = entityOf(c)?.first, entityId = entityOf(c)?.second, dueAt = c.dueAt,
                actions = listOf(if (c.meetingId != null) AttentionAction.PLAY else AttentionAction.OPEN, AttentionAction.OPEN).distinct()))
        }
        val taskDue = database.workDao().openWorkTasks(WorkTypeNames).filter { !it.waitingOn && it.dueAt != null && it.dueAt < endOfToday && it.id !in linkedTaskIds }.map { t ->
            Due(t.dueAt!!, AttentionRow(AttentionKind.YOU_OWE, t.title, dueDetail(t.dueAt, now, null), taskId = t.id, meetingId = t.meetingId, startMs = t.startMs, dueAt = t.dueAt,
                actions = listOf(if (t.meetingId != null) AttentionAction.PLAY else AttentionAction.OPEN)))
        }
        (mineDue + taskDue).sortedBy { it.at }.forEach { if (room()) out += it.row }

        // 2. Decisions still to be made.
        val proposed = dao.byKindStatus(ItemKind.DECISION.name, ItemStatus.PROPOSED.name)
            .sortedWith(compareByDescending<ItemEntity> { it.projectId in projectIdsToday }.thenByDescending { it.createdAt })
        for (d in proposed) if (room()) out += AttentionRow(
            AttentionKind.DECISION_NEEDED, d.text, listOfNotNull("Decision needed", projectTitle(d), "on today's agenda".takeIf { d.projectId in projectIdsToday }).joinToString(" · "),
            itemId = d.id, meetingId = d.meetingId, startMs = dao.evidenceFor(d.id).firstOrNull()?.startMs, entityType = entityOf(d)?.first, entityId = entityOf(d)?.second,
            actions = listOfNotNull(if (d.meetingId != null) AttentionAction.PLAY else null, AttentionAction.PREPARE.takeIf { d.projectId != null }, AttentionAction.OPEN)
        )

        // 3. What people owe me.
        val theirs = openCommitments.filter { it.direction == Direction.THEIRS.name }
            .filter { (it.dueAt != null && it.dueAt < DueDates.startOfDay(now)) || it.createdAt <= now - 5 * DAY }
            .sortedWith(compareBy<ItemEntity> { it.dueAt == null || it.dueAt >= DueDates.startOfDay(now) }.thenBy { it.dueAt ?: it.createdAt })
        for (c in theirs) if (room()) {
            val who = c.ownerPersonId?.let { names[it] } ?: c.ownerSpeakerId?.let { speakerName(it) } ?: "Someone"
            val overdue = c.isOverdue(now)
            out += AttentionRow(
                AttentionKind.THEY_OWE, c.text,
                "$who · " + if (overdue) "was due ${shortDate(c.dueAt!!)}" else "open ${((now - c.createdAt) / DAY).toInt()} days",
                itemId = c.id, taskId = c.taskId, meetingId = c.meetingId, startMs = dao.evidenceFor(c.id).firstOrNull()?.startMs,
                entityType = entityOf(c)?.first, entityId = entityOf(c)?.second, personId = c.ownerPersonId, dueAt = c.dueAt,
                actions = listOfNotNull(AttentionAction.NUDGE.takeIf { c.ownerPersonId != null }, if (c.meetingId != null) AttentionAction.PLAY else null, AttentionAction.OPEN)
            )
        }

        // 4. Quiet relationships.
        if (room()) quiet(now, quietDays, names).forEach { if (room()) out += it }
        out
    }

    private suspend fun quiet(now: Long, quietDays: Int, names: Map<String, String>): List<AttentionRow> {
        val open = dao.openItems()
        val people = database.workDao().allPeople().filter { !it.isSelf }
        val orgIdsWithItems = open.mapNotNull { it.orgId }.toSet()
        val rows = mutableListOf<Pair<Long, AttentionRow>>()
        val orgsShown = mutableSetOf<String>()
        for (p in people) {
            val isOrg = p.kind == PersonKind.ORG.name
            val ids = if (isOrg) listOf(p.id) + people.filter { it.orgId == p.id }.map { it.id } else listOf(p.id)
            val mine = open.filter { i -> i.ownerPersonId in ids || i.orgId == p.id && isOrg || (!isOrg && i.direction != Direction.MINE.name && i.ownerPersonId == p.id) }
            if (mine.isEmpty()) continue
            val last = database.workDao().meetingsWithPeople(ids).firstOrNull()?.createdAt ?: continue
            if (now - last < quietDays * DAY) continue
            if (isOrg) orgsShown += p.id
            rows += last to AttentionRow(
                AttentionKind.QUIET, p.name, "No meeting for ${((now - last) / DAY).toInt()} days · ${mine.size} open",
                entityType = if (isOrg) ContextType.ORG else ContextType.PERSON, entityId = p.id, personId = p.id.takeIf { !isOrg },
                actions = listOf(AttentionAction.PREPARE, AttentionAction.OPEN)
            )
        }
        // A person whose organisation is already shown doesn't take a second row.
        return rows.sortedBy { it.first }.map { it.second }
            .filter { r -> r.entityType == ContextType.ORG || people.firstOrNull { it.id == r.entityId }?.orgId !in orgsShown }
    }

    // ---------------------------------------------------------------- what changed

    /** Every change since [since], as specific sentences under the project, organisation or person they belong to. */
    suspend fun changesSince(since: Long, locale: Locale = Locale.getDefault()): List<ChangeGroup> = withContext(Dispatchers.IO) {
        val events = dao.eventsSince(since)
        groupChanges(events, locale)
    }

    internal suspend fun groupChanges(events: List<ItemEventEntity>, locale: Locale = Locale.getDefault()): List<ChangeGroup> {
        val names = names()
        val projects = database.notebookDao().let { nb -> events.mapNotNull { e -> e.itemId }.distinct().mapNotNull { dao.getById(it)?.projectId }.distinct().associateWith { nb.getById(it)?.name } }
        val speakers = events.mapNotNull { it.itemId }.distinct().mapNotNull { dao.getById(it)?.ownerSpeakerId }.distinct().mapNotNull { id -> speakerName(id)?.let { id to it } }.toMap()
        val groups = LinkedHashMap<String, MutableList<ChangeLine>>()
        val meta = HashMap<String, Triple<String, ContextType?, String?>>()
        for (e in events) {
            val item = e.itemId?.let { dao.getById(it) } ?: continue
            if (item.deletedAt != null) continue
            val sentence = ChangeSentences.of(e, item, names, speakers, locale) ?: continue
            val (title, type, id) = when {
                item.projectId != null && projects[item.projectId] != null -> Triple(projects[item.projectId]!!, ContextType.PROJECT, item.projectId)
                item.orgId != null && names[item.orgId] != null -> Triple(names[item.orgId]!!, ContextType.ORG, item.orgId)
                item.ownerPersonId != null && names[item.ownerPersonId] != null -> Triple(names[item.ownerPersonId]!!, ContextType.PERSON, item.ownerPersonId)
                else -> Triple("Other", null, null)
            }
            val key = "${type ?: "-"}:${id ?: "-"}"
            meta[key] = Triple(title, type, id)
            groups.getOrPut(key) { mutableListOf() } += ChangeLine(sentence, item.id, e.at)
        }
        // Project first, then organisation, then person; the busiest recent group leads within each.
        return groups.map { (k, lines) -> meta.getValue(k).let { (t, ty, id) -> ChangeGroup(t, ty, id, lines) } }
            .sortedWith(compareBy<ChangeGroup> { it.entityType?.ordinal?.let { o -> if (o == ContextType.PROJECT.ordinal) 0 else if (o == ContextType.ORG.ordinal) 1 else 2 } ?: 3 }
                .thenByDescending { g -> g.lines.maxOf { it.at } })
    }

    // ---------------------------------------------------------------- today

    /** The day's calendar lines, each with what's still open from the last time with those people. */
    suspend fun today(events: List<PulseEvent>): List<PulseDayLine> = withContext(Dispatchers.IO) {
        events.sortedBy { it.begin }.map { e ->
            val ref = context.resolveEvent(e)
            val items = if (ref.ids.isEmpty()) emptyList() else dao.around(ref.ids)
            val open = items.filter { it.status == ItemStatus.OPEN.name || it.status == ItemStatus.PROPOSED.name || it.status == ItemStatus.UNCLEAR.name }
            PulseDayLine(
                e,
                youOwe = open.count { it.kind == ItemKind.COMMITMENT.name && it.direction == Direction.MINE.name },
                theyOwe = open.count { it.kind == ItemKind.COMMITMENT.name && it.direction == Direction.THEIRS.name },
                questions = open.count { it.kind == ItemKind.QUESTION.name },
                decisionsNeeded = open.count { it.kind == ItemKind.DECISION.name },
                withLabel = ref.label,
                firstMeeting = ref.ids.isEmpty() || database.workDao().let { w -> ref.projectId?.let { w.meetingsInProject(it) } ?: w.meetingsWithPeople(ref.ids) }.isEmpty()
            )
        }
    }

    /** The projects that have a meeting among [events], for ranking their proposed decisions. */
    suspend fun projectIdsFor(events: List<PulseEvent>): Set<String> = withContext(Dispatchers.IO) {
        events.mapNotNull { context.resolveEvent(it).projectId }.toSet()
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun names(): Map<String, String> = database.workDao().allPeople().associate { it.id to it.name }

    private suspend fun speakerName(id: String): String? = database.workDao().speaker(id)?.let { it.customName.ifBlank { it.originalLabel } }

    private suspend fun projectTitle(i: ItemEntity): String? = i.projectId?.let { database.notebookDao().getById(it)?.name }

    private suspend fun entityOf(i: ItemEntity): Pair<ContextType, String>? = when {
        i.projectId != null -> ContextType.PROJECT to i.projectId
        i.orgId != null -> ContextType.ORG to i.orgId
        i.ownerPersonId != null -> ContextType.PERSON to i.ownerPersonId
        else -> null
    }

    private fun dueDetail(dueAt: Long, now: Long, project: String?): String {
        val overdue = dueAt < DueDates.startOfDay(now)
        return listOfNotNull(if (overdue) "Overdue since ${shortDate(dueAt)}" else "Due today", project).joinToString(" · ")
    }

    private class Due(val at: Long, val row: AttentionRow)

    companion object {
        const val DAY = 86_400_000L
        fun shortDate(ms: Long, locale: Locale = Locale.getDefault()): String = SimpleDateFormat("MMM d", locale).format(Date(ms))
    }
}

/** The exact sentences "what changed" is made of. Never a bare count. */
object ChangeSentences {
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val MONTH_NAMES = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private val MONTH_DAY = Regex("""(?i)\b$MONTH_NAMES\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val DAY_MONTH = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?$MONTH_NAMES\b""")
    private val FILLER = setOf("on", "to", "by", "for", "is", "was", "moved", "pushed", "until", "at", "the", "date", "now", "from", "in", "be", "will", "we", "go", "live")

    class Found(val range: IntRange, val label: String)

    fun findDate(text: String): Found? {
        MONTH_DAY.find(text)?.let { m -> return Found(m.range, label(m.groupValues[1], m.groupValues[2])) }
        DAY_MONTH.find(text)?.let { m -> return Found(m.range, label(m.groupValues[2], m.groupValues[1])) }
        return null
    }

    private fun label(month: String, day: String): String {
        val i = MONTHS.indexOf(month.lowercase().take(3))
        return MONTHS[i].replaceFirstChar { it.uppercase() } + " " + day.toInt()
    }

    /** "Launch on October 14" → "Launch": what the decision is about, without its date and filler. */
    fun subject(text: String, date: Found): String {
        val words = text.removeRange(date.range).split(Regex("\\s+")).map { it.trim(' ', ',', '.', ';', ':', '-', '—') }.filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.last().lowercase() in FILLER) words.removeAt(words.lastIndex)
        while (words.isNotEmpty() && words.first().lowercase() in FILLER) words.removeAt(0)
        return words.joinToString(" ")
    }

    /** A decision replaced by another: "Launch moved Oct 14 → Oct 21" when only the date differs. */
    fun superseded(oldText: String, newText: String): String {
        val od = findDate(oldText)
        val nd = findDate(newText)
        if (od != null && nd != null && od.label != nd.label) {
            val a = subject(oldText, od); val b = subject(newText, nd)
            if (a.isNotEmpty() && a.equals(b, ignoreCase = true)) return "$b moved ${od.label} → ${nd.label}"
        }
        return "Decision changed: “$oldText” → “$newText”"
    }

    /** " by Fri" within the week of the change, " by Oct 21" beyond it. */
    private fun by(dueAt: Long?, at: Long, locale: Locale): String {
        if (dueAt == null) return ""
        val soon = dueAt >= DueDates.startOfDay(at) && dueAt < DueDates.startOfDay(at) + 7 * Pulse.DAY
        return " by " + if (soon) SimpleDateFormat("EEE", locale).format(Date(dueAt)) else Pulse.shortDate(dueAt, locale)
    }

    /** The sentence for one logged event, or null when it isn't news (reviewed flags, deletions, links). */
    fun of(e: ItemEventEntity, item: ItemEntity, people: Map<String, String>, speakers: Map<String, String>, locale: Locale = Locale.getDefault()): String? {
        val before = e.beforeJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val after = e.afterJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val owner = item.ownerPersonId?.let { people[it] } ?: item.ownerSpeakerId?.let { speakers[it] }
        val kind = runCatching { ItemKind.valueOf(item.kind) }.getOrDefault(ItemKind.DECISION)
        return when (e.type) {
            ItemEventType.CREATED.name -> when {
                item.supersedesId != null -> null // the replaced decision's own line tells it
                kind == ItemKind.COMMITMENT && item.direction == Direction.THEIRS.name -> "${owner ?: "Someone"} committed to ${item.text}${by(item.dueAt, e.at, locale)}"
                kind == ItemKind.COMMITMENT -> "You committed to ${item.text}${by(item.dueAt, e.at, locale)}"
                kind == ItemKind.DECISION -> "Decided: ${item.text}"
                kind == ItemKind.QUESTION -> "New question: ${item.text}"
                else -> "New ${kind.name.lowercase().replace('_', ' ')}: ${item.text}"
            }
            ItemEventType.DUE_CHANGED.name -> {
                val was = before?.optLong("dueAt", 0L)?.takeIf { it > 0 && !before.isNull("dueAt") }
                val now = after?.optLong("dueAt", 0L)?.takeIf { it > 0 && !after.isNull("dueAt") }
                when {
                    was != null && now != null -> "${item.text} moved ${Pulse.shortDate(was, locale)} → ${Pulse.shortDate(now, locale)}"
                    now != null -> "${item.text} now due ${Pulse.shortDate(now, locale)}"
                    else -> "${item.text} no longer has a date"
                }
            }
            ItemEventType.SUPERSEDED.name -> superseded(before?.optString("text").orEmpty().ifBlank { item.text }, after?.optString("text").orEmpty().ifBlank { item.text })
            ItemEventType.ANSWERED.name -> "Answered: ${item.text}" + (item.answerText?.let { " — $it" } ?: "")
            ItemEventType.OWNER_CHANGED.name -> "${item.text} now with ${owner ?: "you"}"
            ItemEventType.STATUS.name -> {
                val status = after?.optString("status").orEmpty()
                when {
                    after == null || status.isEmpty() -> null // reviewed flags and removals aren't news
                    status == ItemStatus.COMPLETED.name && item.direction == Direction.THEIRS.name -> "${owner ?: "Someone"} delivered ${item.text}"
                    status == ItemStatus.COMPLETED.name -> "You completed ${item.text}"
                    status == ItemStatus.CANCELLED.name -> "Cancelled: ${item.text}"
                    status == ItemStatus.REVERSED.name -> "Reversed: ${item.text}"
                    status == ItemStatus.ACTIVE.name -> "Decision confirmed: ${item.text}"
                    status == ItemStatus.OPEN.name -> "Reopened: ${item.text}"
                    status == ItemStatus.DROPPED.name -> "Dropped: ${item.text}"
                    status == ItemStatus.CLOSED.name -> "Closed: ${item.text}"
                    else -> null
                }
            }
            else -> null
        }
    }
}
