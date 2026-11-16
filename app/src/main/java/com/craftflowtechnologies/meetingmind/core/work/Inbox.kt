package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts
import com.craftflowtechnologies.meetingmind.core.database.DocumentPageEntity
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEvidenceEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.documents.ChunkSelection
import com.craftflowtechnologies.meetingmind.core.documents.DocumentPageText
import com.craftflowtechnologies.meetingmind.core.documents.DocumentReadState
import com.craftflowtechnologies.meetingmind.core.documents.PageBoxes
import com.craftflowtechnologies.meetingmind.core.documents.PageChunkSelector
import com.craftflowtechnologies.meetingmind.core.documents.ReadStatus
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

enum class InboxKind { TEXT, URL, PDF, AUDIO, IMAGE, VIDEO, FILE }
enum class InboxStatus { NEW, PROPOSED, FILED, DISMISSED }

/** One thing that arrived through the share sheet. */
data class SharedPart(
    val kind: InboxKind,
    val text: String? = null,
    val uri: String? = null,
    val title: String? = null,
    val mime: String? = null,
    val source: String? = null,
    val durationMs: Long? = null
)

/** The share intent, read without Android: the action, the mime types, the text and the streams. */
object ShareIntentParser {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    private val URL = Regex("""^(https?://\S+)$""", RegexOption.IGNORE_CASE)

    /** The mime types the manifest accepts. */
    val ACCEPTED = listOf("text/*", "application/pdf", "audio/*", "image/*", "video/*")

    fun kindOf(mime: String?): InboxKind = when {
        mime == null -> InboxKind.FILE
        mime.startsWith("text/") -> InboxKind.TEXT
        mime == "application/pdf" -> InboxKind.PDF
        mime.startsWith("audio/") -> InboxKind.AUDIO
        mime.startsWith("image/") -> InboxKind.IMAGE
        mime.startsWith("video/") -> InboxKind.VIDEO
        else -> InboxKind.FILE
    }

    fun parse(
        action: String?,
        mime: String?,
        text: String?,
        subject: String?,
        streams: List<Pair<String, String?>>,
        sourceApp: String? = null
    ): List<SharedPart> {
        if (action != ACTION_SEND && action != ACTION_SEND_MULTIPLE) return emptyList()
        val parts = mutableListOf<SharedPart>()
        streams.forEach { (uri, m) ->
            val type = m ?: mime
            parts += SharedPart(kindOf(type), uri = uri, title = subject, mime = type, source = sourceApp)
        }
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            val yt = com.craftflowtechnologies.meetingmind.core.youtube.YouTubeUrlParser.parse(t)
            parts += if (yt != null) {
                SharedPart(
                    InboxKind.URL,
                    text = yt.canonicalWatchUrl,
                    title = subject?.takeIf { it.isNotBlank() } ?: "YouTube Video (${yt.videoId})",
                    mime = mime,
                    source = "YouTube"
                )
            } else if (URL.matches(t)) {
                SharedPart(InboxKind.URL, text = t, title = subject?.takeIf { it.isNotBlank() } ?: hostOf(t), mime = mime, source = hostOf(t))
            } else {
                SharedPart(InboxKind.TEXT, text = t, title = subject?.takeIf { it.isNotBlank() } ?: t.lineSequence().first().take(60), mime = mime, source = sourceApp)
            }
        }
        return parts
    }

    private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/').removePrefix("www.")
}

/** A file copied into the app's own storage, so the share's permission needn't outlive the share. */
data class CopiedFile(
    val path: String,
    val name: String,
    val durationMs: Long? = null,
    val inferredSource: String? = null
)

/**
 * [enqueueRead] starts the on-device read of a document (a PDF or an image) the moment it is added. It is
 * a no-op in tests; the app passes `DocumentReading.enqueue`.
 */
class InboxRepository(
    private val database: MeetMindDatabase,
    private val enqueueRead: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.inboxDao()

    /** Adds what was shared. Files are copied by [copy] first; a file that can't be copied is skipped. Returns the new items. */
    suspend fun add(parts: List<SharedPart>, copy: suspend (String) -> CopiedFile? = { null }): List<InboxItemEntity> = withContext(Dispatchers.IO) {
        parts.mapNotNull { p ->
            val copied = p.uri?.let { copy(it) }
            if (p.uri != null && copied == null) return@mapNotNull null
            val resolvedSource = p.source?.takeIf { it.isNotBlank() && it != "Shared" }
                ?: copied?.inferredSource
                ?: ShareIn.inferSource(uri = p.uri, fileName = copied?.name ?: p.title, mime = p.mime, rawSource = p.source)
            val isYouTubeUrl = p.kind == InboxKind.URL &&
                com.craftflowtechnologies.meetingmind.core.youtube.YouTubeUrlParser.isYouTubeUrl(p.text ?: p.uri)
            // Documents are read on the phone as soon as they arrive (spec §R4, step 1).
            val document = p.kind == InboxKind.PDF || p.kind == InboxKind.IMAGE
            InboxItemEntity(
                id = "inbox_${UUID.randomUUID()}",
                kind = p.kind.name,
                uri = copied?.path,
                text = p.text,
                title = p.title?.takeIf { it.isNotBlank() } ?: copied?.name?.substringBeforeLast('.'),
                status = InboxStatus.NEW.name,
                createdAt = clock(),
                source = resolvedSource,
                mediaDurationMs = p.durationMs ?: copied?.durationMs,
                transcriptionStatus = if (p.kind == InboxKind.AUDIO || p.kind == InboxKind.VIDEO || isYouTubeUrl) "PENDING" else "NONE",
                readStatus = if (document) ReadStatus.PENDING.name else ReadStatus.NONE.name
            ).also {
                dao.upsert(it)
                if (document) enqueueRead(it.id)
            }
        }
    }

    suspend fun updateTranscription(
        id: String,
        status: String,
        transcript: String? = null,
        meetingId: String? = null,
        durationMs: Long? = null
    ) = withContext(Dispatchers.IO) {
        dao.updateTranscription(id, status, transcript, meetingId, durationMs)
    }

    fun observePendingTranscriptions() = dao.observeByTranscriptionStatus("PENDING")
    fun observeByTranscriptionStatus(status: String) = dao.observeByTranscriptionStatus(status)

    suspend fun dismiss(id: String) = withContext(Dispatchers.IO) { dao.get(id)?.let { dao.update(it.copy(status = InboxStatus.DISMISSED.name, processedAt = clock())) } }

    /**
     * Puts a dismissed item back (the Undo after Dismiss). It returns to the state it had before:
     * PROPOSED when it still has a suggestion, otherwise NEW. Returns false when there was nothing to restore.
     */
    suspend fun restore(id: String): Boolean = withContext(Dispatchers.IO) {
        val item = dao.get(id) ?: return@withContext false
        if (item.status != InboxStatus.DISMISSED.name) return@withContext false
        val status = if (item.proposedJson != null) InboxStatus.PROPOSED else InboxStatus.NEW
        dao.update(item.copy(status = status.name, processedAt = null))
        true
    }
    suspend fun get(id: String) = withContext(Dispatchers.IO) { dao.get(id) }
    suspend fun getByUri(uri: String) = withContext(Dispatchers.IO) { dao.getByUri(uri) }
    suspend fun getByMeetingId(meetingId: String) = withContext(Dispatchers.IO) { dao.getByMeetingId(meetingId) }
    fun observeOpen() = dao.observeOpen()
    fun observeFiled() = dao.observeFiled()
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
    val reason: String? = null,
    /** For a document: two sentences on what it is, from its text only. Null when there is none. */
    val summary: String? = null,
    /** For a document: where each task or the decision was found, keyed by the claim's text. */
    val citations: Map<String, Citation> = emptyMap()
) {
    fun toJson(): String {
        val cites = JSONObject()
        citations.forEach { (claim, c) -> cites.put(claim, JSONObject().put("page", c.page).put("boxJson", c.boxJson ?: JSONObject.NULL)) }
        return JSONObject().put("action", action.name).put("target", target?.name ?: JSONObject.NULL).put("targetId", targetId ?: JSONObject.NULL)
            .put("targetLabel", targetLabel ?: JSONObject.NULL).put("title", title ?: JSONObject.NULL).put("tasks", JSONArray(tasks)).put("decision", decision ?: JSONObject.NULL)
            .put("contactName", contactName ?: JSONObject.NULL).put("contactEmail", contactEmail ?: JSONObject.NULL).put("contactPhone", contactPhone ?: JSONObject.NULL)
            .put("reason", reason ?: JSONObject.NULL).put("summary", summary ?: JSONObject.NULL).put("citations", cites).toString()
    }

    companion object {
        fun fromJson(json: String?): Filing? = runCatching {
            val o = JSONObject(json ?: return null)
            fun str(k: String) = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
            Filing(
                FilingAction.valueOf(o.getString("action")), str("target")?.let { FilingTarget.valueOf(it) }, str("targetId"), str("targetLabel"), str("title"),
                o.optJSONArray("tasks")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(), str("decision"), str("contactName"), str("contactEmail"), str("contactPhone"), str("reason"),
                str("summary"), citationsFrom(o.optJSONObject("citations"))
            )
        }.getOrNull()

        private fun citationsFrom(o: JSONObject?): Map<String, Citation> {
            if (o == null) return emptyMap()
            val out = LinkedHashMap<String, Citation>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val v = o.optJSONObject(key) ?: continue
                out[key] = Citation(v.optInt("page"), if (v.isNull("boxJson")) null else v.optString("boxJson").ifBlank { null })
            }
            return out
        }
    }
}

/** Where a claim was found in a document: its 1-based page, and the highlight on that page when known (boxJson). */
data class Citation(val page: Int, val boxJson: String? = null)

/** A `[p. N]` marker, as [PageChunkSelector] writes it before each page's text. */
private val PAGE_MARK = Regex("""\[p\. (\d+)\]\n""")

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
        // Read the row again: the document reader may have moved it on since the caller looked, and
        // a write from a stale copy would undo its read state.
        val current = dao.get(item.id) ?: item
        val document = current.kind == InboxKind.PDF.name || current.kind == InboxKind.IMAGE.name
        val readState = ReadStatus.parse(current.readStatus)
        // A document that is still being read waits for its text. Only a read that finished, or failed in
        // a way that leaves the file to be filed by name, is proposed from.
        if (document && readState != ReadStatus.DONE && readState != ReadStatus.NO_TEXT &&
            readState != ReadStatus.LOCKED && readState != ReadStatus.FAILED
        ) return@withContext null

        val candidates = candidates()
        val pages = if (document && readState == ReadStatus.DONE) database.documentPageDao().forItem(current.id) else emptyList()
        // Pages are chosen for the model by what matters (spec §R4, step 3), not by where they sit in the file.
        val selection = if (document) PageChunkSelector.select(pages.map { DocumentPageText(it.page, it.text) }, candidates.map { it.name }) else null
        val haystack = listOfNotNull(current.title, if (document) pages.joinToString("\n") { it.text } else current.text).joinToString("\n")
        val matched = candidates.filter { mentions(haystack, it.name) }.sortedBy { it.target.ordinal }
        var filing: Filing? = matched.firstOrNull()?.let { c ->
            Filing(FilingAction.NOTE, c.target, c.id, c.name, title = current.title, reason = "Mentions ${c.name}")
        }
        val body = selection?.text ?: current.text.orEmpty()
        if (body.isNotBlank()) {
            val sensitive = keepOnDevice || matched.any { it.confidential }
            val model = models?.forPack(sensitive)
            if (model != null) {
                val raw = (model.generate(prompt(current, candidates, body, document), maxOutputTokens = 400) as? AiResult.Success)?.value
                refine(raw, body, candidates)?.let { filing = it }
            }
        }
        val proposed = filing?.let { if (selection != null) cite(it, selection, pages) else it }
        // Written from the fresh row, and only the proposal's columns change.
        dao.update(current.copy(status = if (proposed != null) InboxStatus.PROPOSED.name else InboxStatus.NEW.name, proposedJson = proposed?.toJson()))
        proposed
    }

    private fun prompt(item: InboxItemEntity, candidates: List<FilingCandidate>, body: String, document: Boolean) = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine("Something was shared into a work inbox. Propose ONE way to file it, using ONLY what it says.")
        appendLine("Choose \"NOTE\" (keep it as a note), \"TASKS\" (it asks for specific things to do), \"DECISION\" (it states a decision) or \"CONTACT\" (it holds someone's contact details).")
        appendLine("\"target\" is the id of one of these, or null: " + candidates.take(40).joinToString("; ") { "${it.id} = ${it.name}" })
        appendLine("Tasks, decisions and contact details must be taken from the text, never invented.")
        if (document) appendLine("This is a document. Each block of text starts with its page marker, such as [p. 4]. Use only that text.")
        val summaryField = if (document) ",\"summary\":string|null" else ""
        appendLine("Answer with JSON only: {\"action\":string,\"target\":string|null,\"title\":string|null,\"tasks\":[string],\"decision\":string|null,\"contact\":{\"name\":string|null,\"email\":string|null,\"phone\":string|null}$summaryField}")
        if (document) appendLine("\"summary\" is two sentences saying what the document is, from its text.")
        appendLine()
        appendLine("Title: ${item.title.orEmpty()}")
        appendLine("Text:")
        append(body.take(6000))
    }

    /** Adds the page each task and the decision was found on, and the highlight when the page has boxes. */
    private fun cite(filing: Filing, selection: ChunkSelection, pages: List<DocumentPageEntity>): Filing {
        if (selection.text.isBlank() || pages.isEmpty()) return filing
        val segments = pageSegments(selection.text)
        val byPage = pages.associateBy { it.page }
        val claims = filing.tasks + listOfNotNull(filing.decision)
        val citations = LinkedHashMap<String, Citation>()
        claims.forEach { claim ->
            val page = pageFor(claim, segments) ?: return@forEach
            citations[claim] = Citation(page, boxFor(claim, byPage[page]))
        }
        return filing.copy(citations = citations)
    }

    private fun pageSegments(text: String): List<Pair<Int, String>> {
        val marks = PAGE_MARK.findAll(text).toList()
        return marks.mapIndexed { i, m ->
            val end = if (i + 1 < marks.size) marks[i + 1].range.first else text.length
            m.groupValues[1].toInt() to text.substring(m.range.last + 1, end)
        }
    }

    /** The page whose text shares the most words with the claim; null when none shares any. */
    private fun pageFor(claim: String, segments: List<Pair<Int, String>>): Int? {
        val words = ChangeDetector.words(claim)
        if (words.isEmpty()) return null
        return segments.map { (page, body) ->
            val src = ChangeDetector.words(body)
            page to words.count { it in src }
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    /** The stored word boxes on the page whose words are in the claim, as JSON; null when there are none. */
    private fun boxFor(claim: String, page: DocumentPageEntity?): String? {
        if (page == null) return null
        val words = ChangeDetector.words(claim)
        val boxes = PageBoxes.parse(page.boxesJson).filter { box ->
            box.text?.let { t -> ChangeDetector.words(t).any { it in words } } == true
        }
        return PageBoxes.encode(boxes)
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
        val summary = o.optString("summary").takeIf { !o.isNull("summary") && it.isNotBlank() && groundedIn(it, source) }
        val filing = Filing(
            action, target?.target, target?.id, target?.name, title = o.optString("title").takeIf { !o.isNull("title") && it.isNotBlank() },
            tasks = tasks, decision = decision, contactName = field("name"), contactEmail = field("email"), contactPhone = field("phone"), reason = "Read from what was shared",
            summary = summary
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
            FilingAction.NOTE -> {
                val (noteId, created) = fileNote(item, filing)
                ref.put("noteId", noteId).put("noteCreated", created)
            }
            FilingAction.TASKS -> {
                val work = WorkRepository(database)
                val notebook = filing.takeIf { it.target == FilingTarget.PROJECT }?.targetId
                val filed = notebook?.let { fileNote(item, filing.copy(title = filing.title ?: item.title)) }
                filed?.let { (noteId, created) -> ref.put("noteId", noteId).put("noteCreated", created) }
                filing.tasks.forEach { work.addTask(it, noteId = filed?.first) }
                ref.put("tasks", filing.tasks.size)
            }
            FilingAction.DECISION -> {
                val text = filing.decision ?: return@withContext false
                val project = filing.takeIf { it.target == FilingTarget.PROJECT }?.targetId
                val org = filing.takeIf { it.target == FilingTarget.ORG }?.targetId ?: project?.let { database.notebookDao().getById(it) }?.let { ContextRepository.orgOf(it.propertiesJson) }
                val links = listOfNotNull(project?.let { ItemLinkEntity("", LinkType.PROJECT, it, "PROJECT") }, org?.let { ItemLinkEntity("", LinkType.ORG, it, "ORG") })
                // A decision found on a document keeps its page (and highlight) as evidence.
                val evidence = filing.citations[text]?.let { c ->
                    listOf(ItemEvidenceEntity(id = "", itemId = "", quote = text, page = c.page, boxJson = c.boxJson))
                }.orEmpty()
                val created = ItemRepository(database, clock).create(
                    ItemEntity("", ItemKind.DECISION.name, ItemStatus.ACTIVE.name, text, projectId = project, orgId = org, reviewed = true, source = ItemSource.USER, createdAt = clock(), updatedAt = clock()),
                    evidence = evidence,
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

    /**
     * Puts a filed item back in the Inbox (the Undo after filing). A note the filing made is removed; a
     * decision it recorded is deleted (soft, with its change log). Tasks it added stay in Tasks, and a
     * note that already existed keeps the notebook it was moved to. Returns false when nothing was filed.
     */
    suspend fun unfile(itemId: String): Boolean = withContext(Dispatchers.IO) {
        val item = dao.get(itemId) ?: return@withContext false
        if (item.status != InboxStatus.FILED.name) return@withContext false
        val ref = runCatching { JSONObject(item.resultRefJson ?: "{}") }.getOrElse { JSONObject() }
        if (ref.optBoolean("noteCreated") && ref.has("noteId")) {
            NoteRepository(context, database, clock).deleteNote(ref.getString("noteId"))
        }
        if (ref.has("itemId")) ItemRepository(database, clock).delete(ref.getString("itemId"))
        val status = if (item.proposedJson != null) InboxStatus.PROPOSED else InboxStatus.NEW
        dao.update(item.copy(status = status.name, processedAt = null, resultRefJson = null))
        true
    }

    /** A note with the shared words (or a line naming the file), in the project, or on the person's or organisation's page. Returns its id, and whether this filing created it. */
    private suspend fun fileNote(item: InboxItemEntity, filing: Filing): Pair<String, Boolean> {
        val notes = NoteRepository(context, database, clock)
        val notebook = when (filing.target) {
            FilingTarget.PROJECT -> filing.targetId
            FilingTarget.EVENT -> filing.targetId?.let { key -> findEventNote(key) }?.notebookId
            else -> null
        }
        val existingNoteId = item.meetingId?.let { mid ->
            database.meetingDao().getMeetingById(mid)?.noteId?.takeIf { nid ->
                database.noteDao().getById(nid) != null
            }
        }
        val noteId = if (existingNoteId != null) {
            if (notebook != null) notes.moveToNotebook(existingNoteId, notebook)
            existingNoteId
        } else {
            val body = listOfNotNull(item.text?.takeIf { it.isNotBlank() }, item.uri?.let { "Attached: ${java.io.File(it).name}" }).joinToString("\n\n").ifBlank { item.title.orEmpty() }
            val blocks = body.split("\n\n").filter { it.isNotBlank() }.map { NoteBlock(NoteRepository.newId("block"), "", 0, NoteBlockType.PARAGRAPH, RichText.plain(it.trim())) }
            val meta = buildMap { item.uri?.let { put("sourceFile", it) }; put("fromInbox", item.id) }
            notes.createNote(workflow = RecordingType.GENERAL, title = filing.title ?: item.title ?: "Shared note", notebookId = notebook, metadata = meta, initialBlocks = blocks, useTemplate = false).id
        }
        when (filing.target) {
            FilingTarget.PERSON, FilingTarget.ORG -> filing.targetId?.let { database.peopleDao().link(NotePersonCrossRef(noteId, it)) }
            FilingTarget.EVENT -> filing.targetId?.let { key -> findEventNote(key)?.let { en -> database.workDao().peopleIdsFor(en.id).forEach { database.peopleDao().link(NotePersonCrossRef(noteId, it)) } } }
            else -> Unit
        }
        return noteId to (existingNoteId == null)
    }

    private suspend fun findEventNote(key: String) = database.noteDao().findByMetadata("%\"${NoteRepository.CALENDAR_EVENT_KEY}\":\"${key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}\"%")
}
