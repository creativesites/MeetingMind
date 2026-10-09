package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.NotePersonCrossRef
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/*
 * The Work Inbox (docs/PLAN_PROFESSIONAL.md D7): the front door. Anything shared into the app
 * lands here first, gets at most one proposed filing, and is filed only when the person confirms
 * it. With no model there is a manual "File to…" picker; nothing is ever filed silently.
 */

enum class InboxKind { TEXT, URL, PDF, AUDIO, IMAGE, FILE }
enum class InboxStatus { NEW, PROPOSED, FILED, DISMISSED }

/** One thing that arrived through the share sheet. */
data class SharedPart(val kind: InboxKind, val text: String? = null, val uri: String? = null, val title: String? = null, val mime: String? = null)

/** The share intent, read without Android: the action, the mime types, the text and the streams. */
object ShareIntentParser {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    private val URL = Regex("""^(https?://\S+)$""", RegexOption.IGNORE_CASE)

    /** The mime types the manifest accepts. */
    val ACCEPTED = listOf("text/*", "application/pdf", "audio/*", "image/*")

    fun kindOf(mime: String?): InboxKind = when {
        mime == null -> InboxKind.FILE
        mime.startsWith("text/") -> InboxKind.TEXT
        mime == "application/pdf" -> InboxKind.PDF
        mime.startsWith("audio/") -> InboxKind.AUDIO
        mime.startsWith("image/") -> InboxKind.IMAGE
        else -> InboxKind.FILE
    }

    fun parse(action: String?, mime: String?, text: String?, subject: String?, streams: List<Pair<String, String?>>): List<SharedPart> {
        if (action != ACTION_SEND && action != ACTION_SEND_MULTIPLE) return emptyList()
        val parts = mutableListOf<SharedPart>()
        streams.forEach { (uri, m) -> val type = m ?: mime; parts += SharedPart(kindOf(type), uri = uri, title = subject, mime = type) }
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            parts += if (URL.matches(t)) SharedPart(InboxKind.URL, text = t, title = subject?.takeIf { it.isNotBlank() } ?: hostOf(t), mime = mime)
            else SharedPart(InboxKind.TEXT, text = t, title = subject?.takeIf { it.isNotBlank() } ?: t.lineSequence().first().take(60), mime = mime)
        }
        return parts
    }

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/').removePrefix("www.")
}

/** A file copied into the app's own storage, so the share's permission needn't outlive the share. */
data class CopiedFile(val path: String, val name: String)

class InboxRepository(private val database: MeetMindDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = database.inboxDao()

    /** Adds what was shared. Files are copied by [copy] first; a file that can't be copied is skipped. Returns the new items. */
    suspend fun add(parts: List<SharedPart>, copy: suspend (String) -> CopiedFile? = { null }): List<InboxItemEntity> = withContext(Dispatchers.IO) {
        parts.mapNotNull { p ->
            val copied = p.uri?.let { copy(it) }
            if (p.uri != null && copied == null) return@mapNotNull null
            InboxItemEntity(
                id = "inbox_${UUID.randomUUID()}", kind = p.kind.name, uri = copied?.path, text = p.text,
                title = p.title?.takeIf { it.isNotBlank() } ?: copied?.name?.substringBeforeLast('.'), status = InboxStatus.NEW.name, createdAt = clock()
            ).also { dao.upsert(it) }
        }
    }

    suspend fun dismiss(id: String) = withContext(Dispatchers.IO) { dao.get(id)?.let { dao.update(it.copy(status = InboxStatus.DISMISSED.name, processedAt = clock())) } }
    suspend fun get(id: String) = withContext(Dispatchers.IO) { dao.get(id) }
    fun observeOpen() = dao.observeOpen()
    fun observeOpenCount() = dao.observeOpenCount()
}

enum class FilingAction(val label: String) { NOTE("Note"), TASKS("Tasks"), DECISION("Decision"), CONTACT("Contact") }

/** Where a note goes: a project's notebook, an organisation's or person's page, or an event's meeting. */
enum class FilingTarget { PROJECT, ORG, PERSON, EVENT }

/** The one filing proposed for an inbox item. Nothing in it has happened yet. */
data class Filing(
    val action: FilingAction,
    val target: FilingTarget? = null,
    val targetId: String? = null,
    val targetLabel: String? = null,
    val title: String? = null,
    val tasks: List<String> = emptyList(),
    val decision: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val reason: String? = null
) {
    fun toJson(): String = JSONObject().put("action", action.name).put("target", target?.name ?: JSONObject.NULL).put("targetId", targetId ?: JSONObject.NULL)
        .put("targetLabel", targetLabel ?: JSONObject.NULL).put("title", title ?: JSONObject.NULL).put("tasks", JSONArray(tasks)).put("decision", decision ?: JSONObject.NULL)
        .put("contactName", contactName ?: JSONObject.NULL).put("contactEmail", contactEmail ?: JSONObject.NULL).put("contactPhone", contactPhone ?: JSONObject.NULL)
        .put("reason", reason ?: JSONObject.NULL).toString()

    companion object {
        fun fromJson(json: String?): Filing? = runCatching {
            val o = JSONObject(json ?: return null)
            fun str(k: String) = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
            Filing(
                FilingAction.valueOf(o.getString("action")), str("target")?.let { FilingTarget.valueOf(it) }, str("targetId"), str("targetLabel"), str("title"),
                o.optJSONArray("tasks")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(), str("decision"), str("contactName"), str("contactEmail"), str("contactPhone"), str("reason")
            )
        }.getOrNull()
    }
}

/** A project, organisation or person a filing could go to. */
data class FilingCandidate(val target: FilingTarget, val id: String, val name: String, val confidential: Boolean)

class InboxProposer(
    private val database: MeetMindDatabase,
    private val models: WorkModels? = null,
    private val keepOnDevice: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.inboxDao()

    suspend fun candidates(): List<FilingCandidate> = withContext(Dispatchers.IO) {
        val projects = database.notebookDao().getAll().filter { it.kind == "PROJECT" && it.deletedAt == null && it.archivedAt == null }
            .map { FilingCandidate(FilingTarget.PROJECT, it.id, it.name, runCatching { JSONObject(it.propertiesJson).optBoolean("confidential") }.getOrDefault(false)) }
        val people = database.workDao().otherPeople()
            .map { FilingCandidate(if (it.kind == PersonKind.ORG.name) FilingTarget.ORG else FilingTarget.PERSON, it.id, it.name, it.confidential) }
        projects + people.filter { it.target == FilingTarget.ORG } + people.filter { it.target == FilingTarget.PERSON }
    }

    /**
     * Proposes one filing and stores it on the item. Names mentioned in the title or text pick the
     * target (a project first, then an organisation, then a person); a model, if there is one and the
     * material may leave the phone, may refine it into tasks, a decision or contact details. Every
     * task or decision it offers must be drawn from the shared text, or it is dropped.
     */
    suspend fun propose(item: InboxItemEntity): Filing? = withContext(Dispatchers.IO) {
        val candidates = candidates()
        val haystack = listOfNotNull(item.title, item.text).joinToString("\n")
        val matched = candidates.filter { mentions(haystack, it.name) }.sortedBy { it.target.ordinal }
        var filing: Filing? = matched.firstOrNull()?.let { c ->
            Filing(FilingAction.NOTE, c.target, c.id, c.name, title = item.title, reason = "Mentions ${c.name}")
        }
        val text = item.text
        if (text != null && text.isNotBlank()) {
            val sensitive = keepOnDevice || matched.any { it.confidential }
            val model = models?.forPack(sensitive)
            if (model != null) {
                val raw = (model.generate(prompt(item, candidates), maxOutputTokens = 400) as? AiResult.Success)?.value
                refine(raw, text, candidates)?.let { filing = it }
            }
        }
        val proposed = filing
        dao.update(item.copy(status = if (proposed != null) InboxStatus.PROPOSED.name else InboxStatus.NEW.name, proposedJson = proposed?.toJson()))
        proposed
    }

    private fun prompt(item: InboxItemEntity, candidates: List<FilingCandidate>) = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Something was shared into a work inbox. Propose ONE way to file it, using ONLY what it says.")
        appendLine("Choose \"NOTE\" (keep it as a note), \"TASKS\" (it asks for specific things to do), \"DECISION\" (it states a decision) or \"CONTACT\" (it holds someone's contact details).")
        appendLine("\"target\" is the id of one of these, or null: " + candidates.take(40).joinToString("; ") { "${it.id} = ${it.name}" })
        appendLine("Tasks, decisions and contact details must be taken from the text, never invented.")
        appendLine("Answer with JSON only: {\"action\":string,\"target\":string|null,\"title\":string|null,\"tasks\":[string],\"decision\":string|null,\"contact\":{\"name\":string|null,\"email\":string|null,\"phone\":string|null}}")
        appendLine()
        appendLine("Title: ${item.title.orEmpty()}")
        appendLine("Text:")
        append(item.text.orEmpty().take(6000))
    }

    internal fun refine(raw: String?, source: String, candidates: List<FilingCandidate>): Filing? = runCatching {
        val cleaned = (raw ?: return null).replace("```json", "").replace("```", "").trim()
        val o = JSONObject(cleaned.substring(cleaned.indexOf('{'), cleaned.lastIndexOf('}') + 1))
        val action = FilingAction.entries.firstOrNull { it.name == o.optString("action").uppercase() } ?: return null
        val target = o.optString("target").takeIf { !o.isNull("target") && it.isNotBlank() }?.let { id -> candidates.firstOrNull { it.id == id } }
        val tasks = o.optJSONArray("tasks")?.let { a -> (0 until a.length()).map { a.getString(it).trim() } }.orEmpty().filter { groundedIn(it, source) }
        val decision = o.optString("decision").takeIf { !o.isNull("decision") && it.isNotBlank() && groundedIn(it, source) }
        val contact = o.optJSONObject("contact")
        fun field(k: String) = contact?.takeIf { !it.isNull(k) }?.optString(k)?.trim()?.takeIf { it.isNotBlank() && source.contains(it, ignoreCase = true) }
        val filing = Filing(
            action, target?.target, target?.id, target?.name, title = o.optString("title").takeIf { !o.isNull("title") && it.isNotBlank() },
            tasks = tasks, decision = decision, contactName = field("name"), contactEmail = field("email"), contactPhone = field("phone"), reason = "Read from what was shared"
        )
        // A proposal that needs something the text didn't hold falls back to a plain note.
        when {
            action == FilingAction.TASKS && tasks.isEmpty() -> filing.copy(action = FilingAction.NOTE)
            action == FilingAction.DECISION && decision == null -> filing.copy(action = FilingAction.NOTE)
            action == FilingAction.CONTACT && filing.contactName == null && filing.contactEmail == null -> filing.copy(action = FilingAction.NOTE)
            else -> filing
        }
    }.getOrNull()

    /** Most of a task's words must be in the shared text. */
    private fun groundedIn(claim: String, source: String): Boolean {
        val words = ChangeDetector.words(claim)
        if (words.isEmpty()) return false
        val src = ChangeDetector.words(source)
        return words.count { it in src }.toDouble() / words.size >= 0.6
    }

    private fun mentions(text: String, name: String): Boolean = name.length >= 3 && Regex("\\b" + Regex.escape(name) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

/** Files an inbox item once the person confirms. Everything goes through the existing repositories. */
class InboxFiler(private val context: Context, private val database: MeetMindDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = database.inboxDao()

    suspend fun file(itemId: String, filing: Filing): Boolean = withContext(Dispatchers.IO) {
        val item = dao.get(itemId) ?: return@withContext false
        if (item.status == InboxStatus.FILED.name) return@withContext false
        val ref = JSONObject().put("action", filing.action.name)
        when (filing.action) {
            FilingAction.NOTE -> ref.put("noteId", fileNote(item, filing))
            FilingAction.TASKS -> {
                val work = WorkRepository(database)
                val notebook = filing.takeIf { it.target == FilingTarget.PROJECT }?.targetId
                val noteId = notebook?.let { fileNote(item, filing.copy(title = filing.title ?: item.title)) }
                filing.tasks.forEach { work.addTask(it, noteId = noteId) }
                ref.put("tasks", filing.tasks.size)
            }
            FilingAction.DECISION -> {
                val text = filing.decision ?: return@withContext false
                val project = filing.takeIf { it.target == FilingTarget.PROJECT }?.targetId
                val org = filing.takeIf { it.target == FilingTarget.ORG }?.targetId ?: project?.let { database.notebookDao().getById(it) }?.let { ContextRepository.orgOf(it.propertiesJson) }
                val links = listOfNotNull(project?.let { ItemLinkEntity("", LinkType.PROJECT, it, "PROJECT") }, org?.let { ItemLinkEntity("", LinkType.ORG, it, "ORG") })
                val created = ItemRepository(database, clock).create(
                    ItemEntity("", ItemKind.DECISION.name, ItemStatus.ACTIVE.name, text, projectId = project, orgId = org, reviewed = true, source = ItemSource.USER, createdAt = clock(), updatedAt = clock()),
                    links = links
                )
                ref.put("itemId", created.id)
            }
            FilingAction.CONTACT -> {
                val name = filing.contactName ?: filing.contactEmail?.substringBefore('@') ?: return@withContext false
                val people = WorkPeople(database)
                val person = people.createPerson(name, null, filing.contactEmail, filing.contactPhone, null)
                ref.put("personId", person.id)
            }
        }
        dao.update(item.copy(status = InboxStatus.FILED.name, processedAt = clock(), proposedJson = filing.toJson(), resultRefJson = ref.toString()))
        true
    }

    /** A note with the shared words (or a line naming the file), in the project, or on the person's or organisation's page. */
    private suspend fun fileNote(item: InboxItemEntity, filing: Filing): String {
        val notes = NoteRepository(context, database, clock)
        val notebook = when (filing.target) {
            FilingTarget.PROJECT -> filing.targetId
            FilingTarget.EVENT -> filing.targetId?.let { key -> findEventNote(key) }?.notebookId
            else -> null
        }
        val body = listOfNotNull(item.text?.takeIf { it.isNotBlank() }, item.uri?.let { "Attached: ${java.io.File(it).name}" }).joinToString("\n\n").ifBlank { item.title.orEmpty() }
        val blocks = body.split("\n\n").filter { it.isNotBlank() }.map { NoteBlock(NoteRepository.newId("block"), "", 0, NoteBlockType.PARAGRAPH, RichText.plain(it.trim())) }
        val meta = buildMap { item.uri?.let { put("sourceFile", it) }; put("fromInbox", item.id) }
        val note = notes.createNote(workflow = RecordingType.GENERAL, title = filing.title ?: item.title ?: "Shared note", notebookId = notebook, metadata = meta, initialBlocks = blocks, useTemplate = false)
        when (filing.target) {
            FilingTarget.PERSON, FilingTarget.ORG -> filing.targetId?.let { database.peopleDao().link(NotePersonCrossRef(note.id, it)) }
            FilingTarget.EVENT -> filing.targetId?.let { key -> findEventNote(key)?.let { en -> database.workDao().peopleIdsFor(en.id).forEach { database.peopleDao().link(NotePersonCrossRef(note.id, it)) } } }
            else -> Unit
        }
        return note.id
    }

    private suspend fun findEventNote(key: String) = database.noteDao().findByMetadata("%\"${NoteRepository.CALENDAR_EVENT_KEY}\":\"${key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}\"%")
}
