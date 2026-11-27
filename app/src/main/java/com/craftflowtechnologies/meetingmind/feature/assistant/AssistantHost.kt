package com.craftflowtechnologies.meetingmind.feature.assistant

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantAction
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantHost
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantScope
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantTool
import com.craftflowtechnologies.meetingmind.ai.assistant.ToolCall
import com.craftflowtechnologies.meetingmind.ai.assistant.ToolResult
import com.craftflowtechnologies.meetingmind.ai.faith.AskSermon
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.NoteStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.BibleStore
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureService
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders
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
    private val learning = com.craftflowtechnologies.meetingmind.core.repository.LearningRepository(db)
    private val tasks = TaskReminders.repository(context)
    private val bible by lazy { ScriptureService.library(context) }

    /** Confirmations waiting for the person, and how to undo what was applied, by action id. */
    private val pending = mutableMapOf<String, suspend () -> Unit>()
    private val undos = mutableMapOf<String, suspend () -> Unit>()

    override suspend fun outline(): String {
        val blocks = editor?.blocks()?.takeIf { it.isNotEmpty() }
            ?: scope.noteId?.let { id -> notes.getDocument(id)?.blocks }.orEmpty()
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
        AssistantTool.LIST_PRAYER_REQUESTS -> listPrayerRequests()
        AssistantTool.LIST_TASKS -> listTasks()
        AssistantTool.LIST_UPCOMING_REMINDERS -> listUpcomingReminders()
        AssistantTool.LIST_COURSES -> listCourses()
        AssistantTool.GET_STUDY_CONCEPTS -> getStudyConcepts(call.str("query"))
        else -> ToolResult("Skipped", "not a read tool", ok = false)
    }

    override suspend fun write(call: ToolCall): Pair<ToolResult, AssistantAction> {
        val id = "act_" + UUID.randomUUID().toString().take(8)
        return when (call.tool) {
            AssistantTool.INSERT_BLOCKS -> {
                val md = call.str("markdown") ?: return fail(id, "insert_blocks needs markdown")
                val ed = editor
                if (ed != null) {
                    val blocks = scriptureSafe(MarkdownImport.parse(md, ed.noteId, BlockSource.AI), ed)
                    if (blocks.isEmpty()) return fail(id, "nothing to insert")
                    val ids = ed.insert(call.str("after_block_id"), blocks).toSet()
                    undos[id] = { ed.remove(ids) }
                    ToolResult("Added ${plural(blocks.size, "block")}", "inserted ${blocks.size} blocks: ${ids.joinToString()}") to
                        AssistantAction(id, "Added ${plural(blocks.size, "block")} to the note", preview = md.take(400))
                } else {
                    val targetNoteId = scope.noteId ?: return noNote(id)
                    val existing = notes.getDocument(targetNoteId)?.blocks.orEmpty()
                    val parsed = MarkdownImport.parse(md, targetNoteId, BlockSource.AI)
                    if (parsed.isEmpty()) return fail(id, "nothing to insert")
                    val combined = (existing + parsed).mapIndexed { index, block -> block.copy(position = index) }
                    notes.saveBlocks(targetNoteId, combined)
                    undos[id] = { notes.saveBlocks(targetNoteId, existing) }
                    ToolResult("Added ${plural(parsed.size, "block")}", "inserted ${parsed.size} blocks into note $targetNoteId") to
                        AssistantAction(id, "Added ${plural(parsed.size, "block")} to the note", preview = md.take(400), openNoteId = targetNoteId)
                }
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
            AssistantTool.COMPLETE_TASK -> {
                val taskId = call.str("task_id") ?: return fail(id, "complete_task needs task_id")
                val existing = tasks.get(taskId) ?: return fail(id, "no task with id '$taskId'")
                if (existing.doneAt != null) return fail(id, "'${existing.title}' is already completed")
                tasks.toggleDone(taskId)
                undos[id] = { tasks.toggleDone(taskId) }
                ToolResult("Completed task", "marked task '${existing.title}' done") to
                    AssistantAction(id, "Completed: ${existing.title}", openTaskId = taskId)
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
            AssistantTool.CREATE_PRAYER_REQUEST -> {
                val title = call.str("title") ?: return fail(id, "create_prayer_request needs a title")
                val request = notes.createNote(workflow = RecordingType.PRAYER_REQUEST, title = title, useTemplate = false)
                val blocks = MarkdownImport.parse(call.str("markdown").orEmpty(), request.id, BlockSource.AI)
                if (blocks.isNotEmpty()) notes.saveBlocks(request.id, blocks)
                undos[id] = { notes.moveToTrash(request.id) }
                ToolResult("Added a prayer request", "created prayer request ${request.id} '$title'") to AssistantAction(id, "Prayer request: $title", openNoteId = request.id)
            }
            AssistantTool.MARK_PRAYER_ANSWERED -> {
                val requestId = call.str("request_id") ?: return fail(id, "mark_prayer_answered needs request_id")
                val entity = db.noteDao().getById(requestId)
                if (entity == null || entity.workflow != RecordingType.PRAYER_REQUEST.name) return fail(id, "no prayer request with id '$requestId'")
                if (entity.status == NoteStatus.ANSWERED.name) return fail(id, "'${entity.title}' is already marked answered")
                notes.markAnswered(requestId)
                undos[id] = { notes.setStatus(requestId, NoteStatus.OPEN) }
                val title = entity.title.ifBlank { "Prayer request" }
                ToolResult("Marked answered", "marked prayer request '$title' answered") to AssistantAction(id, "Answered: $title", openNoteId = requestId)
            }
            AssistantTool.CREATE_FLASHCARDS -> {
                val cardsArr = call.args.optJSONArray("cards")
                val topic = call.str("topic")
                val session = if (scope.noteId != null) {
                    learning.ensureSessionForNote(scope.noteId, title = scope.title)
                } else {
                    val existing = db.learningSessionDao().getAllSessions().firstOrNull()?.toDomain()
                    if (existing != null && topic.isNullOrBlank()) {
                        existing
                    } else {
                        val deckTitle = topic?.takeIf { it.isNotBlank() } ?: "Study Flashcards"
                        val note = notes.createNote(workflow = RecordingType.LECTURE, title = deckTitle, useTemplate = false)
                        learning.ensureSessionForNote(note.id, title = deckTitle)
                    }
                }
                val activities = mutableListOf<com.craftflowtechnologies.meetingmind.core.model.LearningActivity>()
                if (cardsArr != null && cardsArr.length() > 0) {
                    for (i in 0 until cardsArr.length()) {
                        val item = cardsArr.optJSONObject(i) ?: continue
                        val q = item.optString("prompt").trim()
                        val a = item.optString("answer").trim()
                        val t = when (item.optString("type", "RECALL").uppercase()) {
                            "WORKED_PROBLEM" -> com.craftflowtechnologies.meetingmind.core.model.LearningActivityType.WORKED_PROBLEM
                            "TEACH_BACK" -> com.craftflowtechnologies.meetingmind.core.model.LearningActivityType.TEACH_BACK
                            "MULTIPLE_CHOICE" -> com.craftflowtechnologies.meetingmind.core.model.LearningActivityType.MULTIPLE_CHOICE
                            else -> com.craftflowtechnologies.meetingmind.core.model.LearningActivityType.RECALL
                        }
                        if (q.isNotBlank() && a.isNotBlank()) {
                            val now = System.currentTimeMillis()
                            activities += com.craftflowtechnologies.meetingmind.core.model.LearningActivity(
                                id = "act_${java.util.UUID.randomUUID()}",
                                sessionId = session.id,
                                type = t,
                                prompt = q,
                                expectedAnswer = a,
                                difficulty = "MEDIUM",
                                createdAt = now,
                                updatedAt = now
                            )
                        }
                    }
                }
                if (activities.isEmpty()) return fail(id, "create_flashcards needs at least one card with prompt and answer")
                learning.saveConceptsAndActivities(sessionId = session.id, concepts = emptyList(), activities = activities)
                val preview = activities.take(3).joinToString("\n") { "• ${it.prompt}" }
                ToolResult("Created ${plural(activities.size, "flashcard")}", "saved ${activities.size} flashcards in session ${session.id}") to
                    AssistantAction(id, "Added ${plural(activities.size, "flashcard")} to Practice Arena", preview = preview, openNoteId = session.noteId)
            }
            AssistantTool.CREATE_COURSE -> {
                val name = call.str("name") ?: return fail(id, "create_course needs a course name")
                val code = call.str("code").orEmpty()
                val domain = call.str("domain")?.let { com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain.fromName(it) }
                val course = if (scope.noteId != null) {
                    learning.createCourseFromNotes(
                        noteIds = listOf(scope.noteId),
                        courseName = name,
                        courseCode = code,
                        domain = domain
                    )
                } else {
                    val resolvedDomain = domain ?: com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain.detect(name, code)
                    learning.createOrUpdateCourse(
                        name = name,
                        properties = com.craftflowtechnologies.meetingmind.core.model.CourseProperties(
                            courseCode = code,
                            domain = resolvedDomain
                        )
                    )
                }
                val label = if (course.properties.courseCode.isNotBlank()) "${course.properties.courseCode} · ${course.notebook.name}" else course.notebook.name
                ToolResult("Created course $label", "created course ${course.notebook.id} '$label'") to
                    AssistantAction(id, "Course: $label", openNoteId = scope.noteId)
            }
            AssistantTool.CREATE_EXAM_GOAL -> {
                val title = call.str("title") ?: return fail(id, "create_exam_goal needs a title")
                val days = call.str("days_from_now")?.toLongOrNull()?.coerceIn(1L, 365L) ?: 14L
                val targetGrade = call.str("target_grade") ?: "A"
                val examDateMs = System.currentTimeMillis() + days * 86_400_000L
                val now = System.currentTimeMillis()
                val goal = learning.saveGoal(
                    com.craftflowtechnologies.meetingmind.core.model.LearningGoal(
                        id = "",
                        title = title,
                        examDateMs = examDateMs,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                ToolResult("Added exam goal '$title'", "created exam goal ${goal.id} '$title' in $days days") to
                    AssistantAction(id, "Exam goal: $title ($days days · Target $targetGrade)")
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
            val prefs = runCatching { com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(context).preferencesFlow.first() }.getOrNull()
            val effective = prefs?.processingProfile?.let {
                com.craftflowtechnologies.meetingmind.core.work.WorkPrivacy.forMeeting(context, meetingId, it)
            }
            val embedder: com.craftflowtechnologies.meetingmind.ai.embeddings.EmbeddingEngine =
                if (effective == com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile.INTERNET) {
                    com.craftflowtechnologies.meetingmind.ai.embeddings.NeuralEmbeddingEngine(context)
                } else {
                    com.craftflowtechnologies.meetingmind.ai.embeddings.LocalEmbeddingEngine()
                }
            com.craftflowtechnologies.meetingmind.ai.retrieval.TranscriptRetriever(embedder, topK = 14)
                .retrieve(segments, query).map { it.segment }.sortedBy { it.startMs }
        }
        return ToolResult(if (query.isNullOrBlank()) "Read the recording" else "Read the recording for “$query”", AskSermon.render(chosen))
    }

    private suspend fun versionId(translation: String?): Int {
        val offline = runCatching { BibleStore.get(context).offlineBibles() }.getOrDefault(emptyList())
        translation?.let { t -> offline.firstOrNull { it.abbreviation.equals(t, true) || it.title.contains(t, true) }?.let { return it.id } }
        return com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager(context).preferencesFlow.first().bibleVersionId
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
        val src = com.craftflowtechnologies.meetingmind.core.scripture.HelloAo.commentaries.firstOrNull { it.id == source } ?: com.craftflowtechnologies.meetingmind.core.scripture.HelloAo.commentaries.first()
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
        val store = com.craftflowtechnologies.meetingmind.core.originals.OriginalsStore.get(context)
        val hebrew = com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks.all.indexOf(ref.book) < 39
        val pack = if (hebrew) com.craftflowtechnologies.meetingmind.core.originals.OriginalsPack.HEBREW_OT else com.craftflowtechnologies.meetingmind.core.originals.OriginalsPack.GREEK_NT
        val (words, installed) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            store.verses(ref.usfm, ref.chapter, ref.verseStart ?: 1, ref.verseEnd ?: ref.verseStart ?: 200) to (pack in store.installed())
        }
        if (!installed) return ToolResult("${pack.language} not downloaded", "the person hasn't downloaded the ${pack.label} pack; say so and don't answer from memory", ok = false)
        if (words.isEmpty()) return ToolResult("Not in the ${pack.language} text", "no ${pack.language} words for ${ref.display()}", ok = false)
        val lines = words.take(120).joinToString("\n") { w ->
            val e = w.strongsAll.firstNotNullOfOrNull { store.entry(it) }
            val grammar = if (hebrew) com.craftflowtechnologies.meetingmind.core.originals.MorphDecoder.hebrew(w.morph) else com.craftflowtechnologies.meetingmind.core.originals.MorphDecoder.greek(w.morph)
            "v${w.verse} ${w.surface} (${w.translit}) = ${w.gloss} | lemma ${w.lemma} ${w.strongs} | $grammar" + (e?.let { " | dictionary: ${it.gloss}" } ?: "")
        }
        return ToolResult("Read ${ref.display()} in ${pack.language}", lines)
    }

    private suspend fun listPrayerRequests(): ToolResult {
        val open = db.noteDao().getUpdatedSince(0, 400)
            .filter { it.workflow == RecordingType.PRAYER_REQUEST.name && it.status == NoteStatus.OPEN.name && it.deletedAt == null }
        if (open.isEmpty()) return ToolResult("No open prayer requests", "the prayer list has no open requests")
        val text = open.joinToString("\n") { "${it.id}: ${it.title.ifBlank { "A prayer request" }}" }
        return ToolResult("Read the prayer list · ${open.size} open", text)
    }

    private suspend fun listTasks(): ToolResult {
        val open = tasks.openTasks()
        if (open.isEmpty()) return ToolResult("No open tasks", "no open tasks found")
        val zone = ZoneId.systemDefault()
        val lines = open.take(30).joinToString("\n") { t ->
            val due = t.dueAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }
            val extra = listOfNotNull(due?.let { "due $it" }, t.kind.name.lowercase()).joinToString(", ")
            "${t.id}: ${t.title} ($extra)"
        }
        return ToolResult("Read ${open.size} open tasks", lines)
    }

    private suspend fun listUpcomingReminders(): ToolResult {
        val scheduled = tasks.upcomingReminders()
        val withDue = tasks.openTasks().filter { it.remindAt != null || it.dueAt != null }
        val combined = (scheduled + withDue)
            .distinctBy { it.id }
            .sortedBy { it.remindAt ?: it.dueAt ?: Long.MAX_VALUE }
            .take(20)
        if (combined.isEmpty()) {
            return ToolResult("No upcoming reminders", "no scheduled task reminders or dated deadlines")
        }
        val zone = ZoneId.systemDefault()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val now = System.currentTimeMillis()
        val lines = combined.joinToString("\n") { t ->
            val whenMs = t.remindAt ?: t.dueAt
            val whenText = whenMs?.let {
                java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().format(fmt)
            } ?: "unscheduled"
            val tag = if (whenMs != null && whenMs < now) "OVERDUE" else if (t.remindAt != null) "reminder" else "due"
            "${t.id}: ${t.title} [$tag: $whenText]"
        }
        return ToolResult("Read ${combined.size} reminders", lines)
    }

    private suspend fun listCourses(): ToolResult {
        val courses = learning.observeCourses().first()
        val sessions = db.learningSessionDao().getAllSessions()
        if (courses.isEmpty() && sessions.isEmpty()) {
            return ToolResult("No courses yet", "No courses or study sessions created yet.")
        }
        val lines = buildString {
            if (courses.isNotEmpty()) {
                appendLine("Courses (${courses.size}):")
                courses.forEach { c ->
                    val domain = c.properties.domain.displayName
                    val code = c.properties.courseCode.ifBlank { "COURSE" }
                    appendLine("- $code: ${c.notebook.name} ($domain) · ${c.sessionCount} lectures · ${c.conceptCount} concepts (${c.strongConceptCount} strong, ${c.dueReviewCount} due)")
                }
            }
            if (sessions.isNotEmpty()) {
                appendLine("\nRecent Lecture Sessions (${sessions.size.coerceAtMost(10)}):")
                sessions.take(10).forEach { s ->
                    appendLine("- ${s.title.ifBlank { "Untitled Lecture" }} [course=${s.courseName ?: "Unassigned"}, domain=${s.subjectDomain}, noteId=${s.noteId}]")
                }
            }
        }
        return ToolResult("Read ${courses.size} courses & ${sessions.size} lectures", lines.trim())
    }

    private suspend fun getStudyConcepts(query: String?): ToolResult {
        val concepts = if (scope.noteId != null) {
            val session = db.learningSessionDao().getByNoteId(scope.noteId)
            if (session != null) db.learningConceptDao().getBySession(session.id)
            else db.learningConceptDao().getAllActive()
        } else {
            db.learningConceptDao().getAllActive()
        }
        val filtered = if (!query.isNullOrBlank()) {
            val q = query.trim().lowercase()
            concepts.filter {
                it.name.lowercase().contains(q) ||
                    it.definition.lowercase().contains(q) ||
                    (it.formulaLatex?.lowercase()?.contains(q) == true)
            }.ifEmpty { concepts }
        } else concepts

        if (filtered.isEmpty()) {
            return ToolResult("Read study concepts · 0", "No extracted concepts yet. Read the note or transcript directly and help the student extract key concepts or flashcards.")
        }
        val text = filtered.take(30).joinToString("\n") { c ->
            val formula = c.formulaLatex?.takeIf { it.isNotBlank() }?.let { " | Formula: $it" }.orEmpty()
            "- ${c.name} [${c.state}, exam=${c.examImportance}]: ${c.definition}$formula"
        }
        return ToolResult("Read ${filtered.size} study concepts", text)
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

private fun com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity.toDomainSegment() = com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment(
    id = id, meetingId = meetingId, speakerId = speakerId, speakerName = speakerName, startMs = startMs, endMs = endMs,
    text = text, confidence = confidence, isUserEdited = isUserEdited, cleanedText = cleanedText
)
