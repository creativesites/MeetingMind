package com.example.feature.assistant

import android.content.Context
import com.example.ai.assistant.AssistantAction
import com.example.ai.assistant.AssistantHost
import com.example.ai.assistant.AssistantScope
import com.example.ai.assistant.AssistantTool
import com.example.ai.assistant.ToolCall
import com.example.ai.assistant.ToolResult
import com.example.ai.faith.AskSermon
import com.example.core.database.MeetMindDatabase
import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.RecordingType
import com.example.core.notes.MarkdownImport
import com.example.core.repository.NoteRepository
import com.example.core.scripture.BibleStore
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureReferenceParser
import com.example.core.scripture.ScriptureService
import com.example.core.tasks.Task
import com.example.core.tasks.TaskKind
import com.example.core.tasks.TaskReminders
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** What the assistant may do to the open note — implemented by the note editor. */
interface EditorBridge {
    val noteId: String
    fun blocks(): List<NoteBlock>
    fun insert(afterId: String?, blocks: List<NoteBlock>): List<String>
    fun remove(ids: Set<String>): List<Pair<Int, NoteBlock>>
    fun restore(removed: List<Pair<Int, NoteBlock>>, dropIds: Set<String>)
    fun scripture(reference: ScriptureReference): NoteBlock
}

/**
 * Runs the assistant's tools against the real app: the open note (through [editor]), its
 * recording, the Bible library, the person's other notes, and tasks.
 */
class AppAssistantHost(
    private val context: Context,
    override val scope: AssistantScope,
    private val editor: EditorBridge?
) : AssistantHost {
    private val db = MeetMindDatabase.getInstance(context)
    private val notes = NoteRepository(context, db)
    private val tasks = TaskReminders.repository(context)
    private val bible by lazy { ScriptureService.library(context) }

    /** Confirmations waiting for the person, and how to undo what was applied, by action id. */
    private val pending = mutableMapOf<String, suspend () -> Unit>()
    private val undos = mutableMapOf<String, suspend () -> Unit>()

    override suspend fun outline(): String {
        val blocks = editor?.blocks().orEmpty()
        if (blocks.isEmpty()) return "(empty)"
        return blocks.take(120).joinToString("\n") { b ->
            val text = when (b.type) {
                NoteBlockType.SCRIPTURE -> "Scripture: " + (b.payload["reference"] ?: b.content.text)
                NoteBlockType.MARKDOWN -> b.content.text.replace('\n', ' ')
                else -> b.content.text
            }
            "${b.id} | ${b.type.name.lowercase()}${if (b.checked) " (done)" else ""} | ${text.take(160)}"
        }
    }

    override suspend fun read(call: ToolCall): ToolResult = when (call.tool) {
        AssistantTool.READ_NOTE -> ToolResult("Read the note", outline())
        AssistantTool.SEARCH_NOTES -> searchNotes(call.str("query").orEmpty())
        AssistantTool.READ_TRANSCRIPT -> readTranscript(call.str("query"))
        AssistantTool.GET_VERSES -> verses(call.str("reference"), call.str("translation"))
        AssistantTool.CROSS_REFERENCES -> crossRefs(call.str("reference"))
        AssistantTool.COMMENTARY -> commentary(call.str("reference"), call.str("source"))
        AssistantTool.COMPARE_TRANSLATIONS -> compare(call.str("reference"))
        AssistantTool.ORIGINAL -> original(call.str("reference"))
        else -> ToolResult("Skipped", "not a read tool", ok = false)
    }

    override suspend fun write(call: ToolCall): Pair<ToolResult, AssistantAction> {
        val id = "act_" + UUID.randomUUID().toString().take(8)
        return when (call.tool) {
            AssistantTool.INSERT_BLOCKS -> {
                val ed = editor ?: return noNote(id)
                val md = call.str("markdown") ?: return fail(id, "insert_blocks needs markdown")
                val blocks = scriptureSafe(MarkdownImport.parse(md, ed.noteId, BlockSource.AI), ed)
                if (blocks.isEmpty()) return fail(id, "nothing to insert")
                val ids = ed.insert(call.str("after_block_id"), blocks).toSet()
                undos[id] = { ed.remove(ids) }
                ToolResult("Added ${plural(blocks.size, "block")}", "inserted ${blocks.size} blocks: ${ids.joinToString()}") to
                    AssistantAction(id, "Added ${plural(blocks.size, "block")} to the note", preview = md.take(400))
            }
            AssistantTool.INSERT_SCRIPTURE -> {
                val ed = editor ?: return noNote(id)
                val ref = call.str("reference")?.let(ScriptureReferenceParser::parse) ?: return fail(id, "couldn't read the reference '${call.str("reference")}'")
                val block = ed.scripture(ref)
                val ids = ed.insert(call.str("after_block_id"), listOf(block)).toSet()
                undos[id] = { ed.remove(ids) }
                ToolResult("Added ${ref.display()}", "inserted scripture block ${block.id} for ${ref.display()}") to AssistantAction(id, "Added ${ref.display()}")
            }
            AssistantTool.REPLACE_BLOCKS, AssistantTool.DELETE_BLOCKS -> {
                val ed = editor ?: return noNote(id)
                val targets = call.list("block_ids").toSet()
                val existing = ed.blocks().filter { it.id in targets }
                if (existing.isEmpty()) return fail(id, "no such block ids: ${targets.joinToString()}")
                val md = call.str("markdown")
                val replacing = call.tool == AssistantTool.REPLACE_BLOCKS
                if (replacing && md == null) return fail(id, "replace_blocks needs markdown")
                pending[id] = {
                    val firstIndex = ed.blocks().indexOfFirst { it.id in targets }
                    val before = ed.blocks().getOrNull(firstIndex - 1)?.id
                    val removed = ed.remove(targets)
                    val added = if (replacing) ed.insert(before ?: FIRST, scriptureSafe(MarkdownImport.parse(md!!, ed.noteId, BlockSource.AI), ed)).toSet() else emptySet()
                    undos[id] = { ed.restore(removed, added) }
                }
                val old = existing.joinToString("\n") { it.content.text.take(200) }
                ToolResult(
                    if (replacing) "Proposed a rewrite" else "Proposed removing ${plural(existing.size, "block")}",
                    "waiting for the person to confirm"
                ) to AssistantAction(
                    id,
                    if (replacing) "Replace ${plural(existing.size, "block")}?" else "Remove ${plural(existing.size, "block")}?",
                    preview = if (replacing) "Before:\n$old\n\nAfter:\n${md!!.take(600)}" else old,
                    needsConfirmation = true
                )
            }
            AssistantTool.CREATE_TASK -> {
                val title = call.str("title") ?: return fail(id, "create_task needs a title")
                val zone = ZoneId.systemDefault()
                val remind = call.str("remind_at")?.let { runCatching { LocalDateTime.parse(it.take(16)) }.getOrNull() }
                val due = call.str("due")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: remind?.toLocalDate()
                val person = call.str("person")?.let { name -> tasks.findPerson(name) ?: tasks.savePerson(name) }
                val kind = when (call.str("kind")?.lowercase()) {
                    "apply" -> TaskKind.APPLY; "prayer" -> TaskKind.PRAYER; "follow_up", "follow up" -> TaskKind.FOLLOW_UP
                    else -> if (scope.sermon) TaskKind.APPLY else TaskKind.TASK
                }
                val saved = tasks.save(
                    Task(
                        id = "", title = title, kind = kind,
                        dueAt = due?.atTime(remind?.toLocalTime() ?: LocalTime.of(9, 0))?.atZone(zone)?.toInstant()?.toEpochMilli(),
                        remindAt = remind?.atZone(zone)?.toInstant()?.toEpochMilli(),
                        personId = person?.id, noteId = scope.noteId, meetingId = scope.meetingId
                    )
                )
                if (person != null && scope.noteId != null) tasks.linkNote(scope.noteId, person.id)
                undos[id] = { tasks.delete(saved.id) }
                val when_ = listOfNotNull(due?.toString(), remind?.toLocalTime()?.toString()?.let { "reminder $it" }).joinToString(", ")
                ToolResult("Created a task", "created task ${saved.id} '$title' $when_") to
                    AssistantAction(id, "Task: $title" + (if (when_.isNotBlank()) " · $when_" else ""), openTaskId = saved.id)
            }
            AssistantTool.CREATE_NOTE -> {
                val title = call.str("title") ?: "New note"
                val workflow = if (scope.faith) RecordingType.REFLECTION else RecordingType.GENERAL
                val note = notes.createNote(workflow = workflow, title = title, useTemplate = false)
                val blocks = MarkdownImport.parse(call.str("markdown").orEmpty(), note.id, BlockSource.AI)
                if (blocks.isNotEmpty()) notes.saveBlocks(note.id, blocks)
                undos[id] = { notes.moveToTrash(note.id) }
                ToolResult("Created a note", "created note ${note.id} '$title'") to AssistantAction(id, "New note: $title", openNoteId = note.id)
            }
            else -> fail(id, "not a write tool")
        }
    }

    suspend fun confirm(actionId: String): Boolean = pending.remove(actionId)?.let { it(); true } ?: false
    fun discard(actionId: String) { pending.remove(actionId) }
    suspend fun undo(actionId: String): Boolean = undos.remove(actionId)?.let { it(); true } ?: false

    // ------------------------------------------------------------------ reads

    private suspend fun searchNotes(query: String): ToolResult {
        val fts = BibleStore.ftsQuery(query) ?: return ToolResult("Searched notes", "empty query", ok = false)
        val hits = runCatching { db.searchDao().notes(fts, 8) }.getOrDefault(emptyList()).filter { it.id != scope.noteId }
        val text = hits.joinToString("\n") { h ->
            val date = java.time.Instant.ofEpochMilli(h.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            "\"${h.title.ifBlank { "Untitled" }}\" ($date, ${h.workflow.lowercase()}): ${h.snippet}"
        }
        return ToolResult("Searched notes for “$query” · ${hits.size}", text.ifBlank { "no notes found" })
    }

    private suspend fun readTranscript(query: String?): ToolResult {
        val meetingId = scope.meetingId ?: return ToolResult("No recording", "this note has no recording", ok = false)
        val segments = db.transcriptDao().getSegmentsForMeetingDirect(meetingId).map { it.toDomainSegment() }
        if (segments.isEmpty()) return ToolResult("No transcript yet", "the recording has no transcript yet", ok = false)
        val chosen = if (query.isNullOrBlank()) {
            // An overview: evenly spaced paragraphs across the whole recording.
            val step = (segments.size / 40).coerceAtLeast(1)
            segments.filterIndexed { i, _ -> i % step == 0 }.take(40)
        } else {
            com.example.ai.retrieval.TranscriptRetriever(com.example.ai.embeddings.LocalEmbeddingEngine(), topK = 14)
                .retrieve(segments, query).map { it.segment }.sortedBy { it.startMs }
        }
        return ToolResult(if (query.isNullOrBlank()) "Read the recording" else "Read the recording for “$query”", AskSermon.render(chosen))
    }

    private suspend fun versionId(translation: String?): Int {
        val offline = runCatching { BibleStore.get(context).offlineBibles() }.getOrDefault(emptyList())
        translation?.let { t -> offline.firstOrNull { it.abbreviation.equals(t, true) || it.title.contains(t, true) }?.let { return it.id } }
        return com.example.core.datastore.UserPreferencesManager(context).preferencesFlow.first().bibleVersionId
    }

    private suspend fun verses(reference: String?, translation: String?): ToolResult {
        val ref = reference?.let(ScriptureReferenceParser::parse) ?: return ToolResult("Couldn't read a reference", "couldn't parse '$reference'", ok = false)
        return when (val r = bible.passage(ref, versionId(translation))) {
            is PassageResult.Found -> ToolResult("Read ${ref.display()} · ${r.passage.versionAbbreviation}", "${ref.display()} (${r.passage.versionAbbreviation}): ${r.passage.text}")
            is PassageResult.Unavailable -> ToolResult("${ref.display()} unavailable", "unavailable: ${r.message}", ok = false)
        }
    }

    private suspend fun crossRefs(reference: String?): ToolResult {
        val ref = reference?.let(ScriptureReferenceParser::parse) ?: return ToolResult("Couldn't read a reference", "couldn't parse '$reference'", ok = false)
        val all = bible.crossRefs(ref.book, ref.chapter) ?: return ToolResult("Cross references unavailable", "cross references need the internet once", ok = false)
        val range = (ref.verseStart ?: 1)..(ref.verseEnd ?: ref.verseStart ?: 200)
        val picked = all.filter { it.fromVerse in range }.sortedByDescending { it.score }.take(12)
        return ToolResult("Found ${picked.size} cross references", picked.joinToString("\n") { "${ref.book.name} ${ref.chapter}:${it.fromVerse} → ${it.to.display()}" }.ifBlank { "none" })
    }

    private suspend fun commentary(reference: String?, source: String?): ToolResult {
        val ref = reference?.let(ScriptureReferenceParser::parse) ?: return ToolResult("Couldn't read a reference", "couldn't parse '$reference'", ok = false)
        val src = com.example.core.scripture.HelloAo.commentaries.firstOrNull { it.id == source } ?: com.example.core.scripture.HelloAo.commentaries.first()
        val entries = bible.commentary(src.id, ref.book, ref.chapter) ?: return ToolResult("Commentary unavailable", "commentary needs the internet once", ok = false)
        val start = ref.verseStart ?: 1
        val end = ref.verseEnd ?: ref.verseStart ?: 999
        // An entry speaks to its verse and on until the next entry.
        val relevant = entries.filterIndexed { i, e -> e.verse <= end && (entries.getOrNull(i + 1)?.verse ?: 999) > start }
        val text = relevant.joinToString("\n\n") { "v${it.verse}: ${it.text}" }.take(5000)
        return ToolResult("Read ${src.name} on ${ref.display()}", "${src.name} on ${ref.display()}:\n${text.ifBlank { "no entry for these verses" }}")
    }

    private suspend fun original(reference: String?): ToolResult {
        val ref = reference?.let(ScriptureReferenceParser::parse) ?: return ToolResult("Couldn't read a reference", "couldn't parse '$reference'", ok = false)
        val store = com.example.core.originals.OriginalsStore.get(context)
        val hebrew = com.example.core.scripture.BibleBooks.all.indexOf(ref.book) < 39
        val pack = if (hebrew) com.example.core.originals.OriginalsPack.HEBREW_OT else com.example.core.originals.OriginalsPack.GREEK_NT
        val (words, installed) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store.verses(ref.usfm, ref.chapter, ref.verseStart ?: 1, ref.verseEnd ?: ref.verseStart ?: 200) to (pack in store.installed())
        }
        if (!installed) return ToolResult("${pack.language} not downloaded", "the person hasn't downloaded the ${pack.label} pack; say so and don't answer from memory", ok = false)
        if (words.isEmpty()) return ToolResult("Not in the ${pack.language} text", "no ${pack.language} words for ${ref.display()}", ok = false)
        val lines = words.take(120).joinToString("\n") { w ->
            val e = w.strongsAll.firstNotNullOfOrNull { store.entry(it) }
            val grammar = if (hebrew) com.example.core.originals.MorphDecoder.hebrew(w.morph) else com.example.core.originals.MorphDecoder.greek(w.morph)
            "v${w.verse} ${w.surface} (${w.translit}) = ${w.gloss} | lemma ${w.lemma} ${w.strongs} | $grammar" + (e?.let { " | dictionary: ${it.gloss}" } ?: "")
        }
        return ToolResult("Read ${ref.display()} in ${pack.language}", lines)
    }

    private suspend fun compare(reference: String?): ToolResult {
        val ref = reference?.let(ScriptureReferenceParser::parse) ?: return ToolResult("Couldn't read a reference", "couldn't parse '$reference'", ok = false)
        val offline = runCatching { BibleStore.get(context).offlineBibles() }.getOrDefault(emptyList()).take(4)
        val ids = (listOf(versionId(null)) + offline.map { it.id }).distinct().take(4)
        val out = ids.mapNotNull { id -> (bible.passage(ref, id) as? PassageResult.Found)?.passage?.let { "${it.versionAbbreviation}: ${it.text}" } }
        return ToolResult("Compared ${out.size} translations", out.joinToString("\n\n").ifBlank { "no translations available offline" })
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A quote that ends in a reference is Scripture the model typed from memory: it becomes a real
     * Scripture block for that reference, whose text comes from the Bible library.
     */
    private fun scriptureSafe(blocks: List<NoteBlock>, ed: EditorBridge): List<NoteBlock> = blocks.map { b ->
        if (b.type != NoteBlockType.QUOTE) return@map b
        val ref = quotedScripture(b.content.text) ?: return@map b
        ed.scripture(ref)
    }

    private fun noNote(id: String) = fail(id, "no note is open")
    private fun fail(id: String, why: String) = ToolResult("Couldn't do that", "error: $why", ok = false) to AssistantAction(id, why, state = AssistantAction.State.DISCARDED)
    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    companion object {
        /** Sentinel for "insert at the top". */
        const val FIRST = "\u0000first"

        /** "“…” (John 3:16)" or "… — Romans 8:28": the reference a quoted verse names at its end. */
        fun quotedScripture(text: String): ScriptureReference? {
            val tail = Regex("[(—–-]\\s*([1-3]?\\s?[A-Za-z][A-Za-z .]+\\s\\d+(?::\\d+(?:[-–]\\d+)?)?)\\s*\\)?\\s*[.]?$").find(text.trim()) ?: return null
            return ScriptureReferenceParser.findAll(tail.groupValues[1]).firstOrNull()?.reference
        }
    }
}

private fun com.example.core.database.TranscriptSegmentEntity.toDomainSegment() = com.example.core.model.TranscriptSegment(
    id = id, meetingId = meetingId, speakerId = speakerId, speakerName = speakerName, startMs = startMs, endMs = endMs,
    text = text, confidence = confidence, isUserEdited = isUserEdited, cleanedText = cleanedText
)
