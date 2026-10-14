package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.ProjectMemberEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/*
 * Context pages (docs/PLAN_PROFESSIONAL.md D5.2): one shape for a person, an organisation and a
 * project, all read from items and their change log. Nothing here needs a model.
 */

enum class ContextType { PERSON, ORG, PROJECT }

/** The five figures at the top of a context page. */
data class ContextHeader(
    val youOwe: Int,
    val theyOwe: Int,
    /** Open questions, proposed decisions and open risks and the like. */
    val open: Int,
    val decided: Int,
    /** The soonest date among what's still open. */
    val next: String?,
    val nextAt: Long?,
    val lastMeeting: MeetingEntity?
)

/** One dated entry: something that changed, or a meeting. */
data class TimelineEntry(val at: Long, val text: String, val itemId: String? = null, val meetingId: String? = null)

/** Who or what an event resolves to: people and/or a project, with a label for "With …". */
data class EventRef(val ids: List<String>, val projectId: String?, val label: String?, val title: String)

class ContextRepository(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.itemDao()
    private val work = database.workDao()

    /** The person, organisation (with its people) or project that items are looked up by. */
    suspend fun ids(type: ContextType, id: String): List<String> = withContext(Dispatchers.IO) {
        when (type) {
            ContextType.ORG -> listOf(id) + work.allPeople().filter { it.orgId == id }.map { it.id } + projectsOf(id).map { it.id }
            else -> listOf(id)
        }
    }

    /** The projects filed under an organisation. */
    suspend fun projectsOf(orgId: String) = withContext(Dispatchers.IO) {
        database.notebookDao().getAll().filter { it.kind == "PROJECT" && it.deletedAt == null && orgOf(it.propertiesJson) == orgId }
    }

    /** Reviewed items about the entity. Promises from someone else count only when that person made them. */
    suspend fun items(type: ContextType, id: String): List<ItemEntity> = withContext(Dispatchers.IO) {
        val all = dao.around(ids(type, id))
        when (type) {
            ContextType.PERSON -> all.filter { it.direction != Direction.THEIRS.name || it.ownerPersonId == id || it.counterpartyPersonId == id }
            else -> all
        }
    }

    suspend fun meetings(type: ContextType, id: String): List<MeetingEntity> = withContext(Dispatchers.IO) {
        when (type) {
            ContextType.PROJECT -> work.meetingsInProject(id)
            ContextType.PERSON -> work.meetingsWithPeople(listOf(id))
            ContextType.ORG -> (work.meetingsWithPeople(ids(type, id)) + projectsOf(id).flatMap { work.meetingsInProject(it.id) }).distinctBy { it.id }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun header(type: ContextType, id: String, now: Long = clock()): ContextHeader = withContext(Dispatchers.IO) {
        val items = items(type, id)
        val open = items.filter { it.itemStatus in setOf(ItemStatus.OPEN, ItemStatus.UNCLEAR, ItemStatus.PROPOSED) }
        val next = open.filter { it.dueAt != null }.minByOrNull { it.dueAt!! }
        ContextHeader(
            youOwe = open.count { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.MINE.name },
            theyOwe = open.count { it.itemKind == ItemKind.COMMITMENT && it.direction == Direction.THEIRS.name },
            open = open.count { it.itemKind != ItemKind.COMMITMENT },
            decided = items.count { it.itemKind == ItemKind.DECISION && it.itemStatus == ItemStatus.ACTIVE },
            next = next?.text, nextAt = next?.dueAt,
            lastMeeting = meetings(type, id).firstOrNull()
        )
    }

    /** Each decision still standing, with what it replaced before it, oldest first. Newest chains first. */
    suspend fun decisionHistory(type: ContextType, id: String): List<List<ItemEntity>> = withContext(Dispatchers.IO) {
        val repo = ItemRepository(database, clock)
        items(type, id).filter { it.itemKind == ItemKind.DECISION && it.itemStatus != ItemStatus.SUPERSEDED }
            .map { repo.chain(it.id) }.sortedByDescending { it.last().createdAt }
    }

    suspend fun risks(type: ContextType, id: String): List<ItemEntity> = items(type, id).filter { it.itemKind == ItemKind.RISK && it.itemStatus == ItemStatus.OPEN }

    /** Meetings and changes to items about this entity, newest first. [since] limits it, for "This month". */
    suspend fun timeline(type: ContextType, id: String, since: Long? = null, locale: Locale = Locale.getDefault()): List<TimelineEntry> = withContext(Dispatchers.IO) {
        val items = items(type, id).associateBy { it.id }
        val people = work.allPeople().associate { it.id to it.name }
        val speakers = items.values.mapNotNull { it.ownerSpeakerId }.distinct().mapNotNull { s -> work.speaker(s)?.let { s to it.customName.ifBlank { it.originalLabel } } }.toMap()
        val events = items.keys.chunked(400).flatMap { dao.eventsForItems(it) }.filter { since == null || it.at >= since }
        val lines = events.mapNotNull { e ->
            val item = items[e.itemId] ?: return@mapNotNull null
            ChangeSentences.of(e, item, people, speakers, locale)?.let { TimelineEntry(e.at, it, item.id, item.meetingId) }
        }
        val meetings = meetings(type, id).filter { since == null || it.createdAt >= since }.map { TimelineEntry(it.createdAt, "Met: ${it.title.ifBlank { "Meeting" }}", null, it.id) }
        (lines + meetings).sortedByDescending { it.at }
    }

    // ---------------------------------------------------------------- events

    /** The people and project an event stands for: its note's project, else the guests already known. */
    suspend fun resolveEvent(e: PulseEvent): EventRef = withContext(Dispatchers.IO) {
        val escaped = e.key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val note = database.noteDao().findByMetadata("%\"${com.craftflowtechnologies.meetingmind.core.repository.NoteRepository.CALENDAR_EVENT_KEY}\":\"$escaped\"%")
        val projectId = note?.notebookId
        val people = WorkPeople(database)
        val known = (e.people.map { people.resolve(it, null, create = false) } + e.emails.map { people.resolve(null, it, create = false) }).filterNotNull().distinctBy { it.id }
        val ids = (listOfNotNull(projectId) + known.map { it.id } + known.mapNotNull { it.orgId }).distinct()
        val label = projectId?.let { database.notebookDao().getById(it)?.name } ?: known.take(3).joinToString(", ") { it.name }.ifBlank { null }
        EventRef(ids, projectId, label, e.title)
    }

    // ---------------------------------------------------------------- editing

    /** An organisation's own fields (D4.4). */
    suspend fun setOrgDetails(orgId: String, domains: List<String>, description: String, urls: List<String>, properties: Map<String, String>) = withContext(Dispatchers.IO) {
        val org = database.peopleDao().getById(orgId) ?: return@withContext
        database.peopleDao().upsert(
            org.copy(
                domainsJson = jsonList(domains.map { it.trim().lowercase(Locale.ROOT).removePrefix("@") }.filter { it.isNotEmpty() }),
                description = description.trim(), urlsJson = jsonList(urls.map { it.trim() }.filter { it.isNotEmpty() }),
                propertiesJson = JSONObject(properties.filterKeys { it.isNotBlank() }).toString(), updatedAt = clock()
            )
        )
    }

    /** A project's status, dates and custom properties live in its properties JSON, beside the org and privacy. */
    suspend fun setProjectDetails(notebookId: String, status: String?, start: String?, end: String?, properties: Map<String, String>) = withContext(Dispatchers.IO) {
        val nb = database.notebookDao().getById(notebookId) ?: return@withContext
        val json = runCatching { JSONObject(nb.propertiesJson) }.getOrDefault(JSONObject())
        status?.let { json.put("status", it) }
        json.put("start", start.orEmpty()); json.put("end", end.orEmpty())
        json.put("custom", JSONObject(properties.filterKeys { it.isNotBlank() }))
        database.notebookDao().upsert(nb.copy(propertiesJson = json.toString(), updatedAt = clock()))
    }

    suspend fun members(notebookId: String): List<ProjectMemberEntity> = withContext(Dispatchers.IO) { dao.membersOf(notebookId) }
    suspend fun addMember(notebookId: String, personId: String, role: String = "") = withContext(Dispatchers.IO) { dao.upsertMember(ProjectMemberEntity(notebookId, personId, role.trim())) }
    suspend fun removeMember(notebookId: String, personId: String) = withContext(Dispatchers.IO) { dao.removeMember(notebookId, personId) }

    companion object {
        fun orgOf(propertiesJson: String): String? = runCatching { JSONObject(propertiesJson).optString("orgId").ifBlank { null } }.getOrNull()
        fun list(json: String): List<String> = runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
        fun map(json: String): Map<String, String> = runCatching { JSONObject(json).let { o -> o.keys().asSequence().associateWith { o.optString(it) } } }.getOrDefault(emptyMap())
    }
}
