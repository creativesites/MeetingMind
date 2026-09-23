package com.example.core.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.core.database.MeetMindDatabase
import com.example.core.database.NoteLinkEntity
import com.example.core.database.NoteTagCrossRef
import com.example.core.database.ScriptureCollectionEntity
import com.example.core.database.ScriptureCollectionItemEntity
import com.example.core.database.TagEntity
import com.example.core.model.Attachment
import com.example.core.model.BlockSource
import com.example.core.model.Note
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteDocument
import com.example.core.model.NoteLink
import com.example.core.model.NoteLinkKind
import com.example.core.model.NoteStatus
import com.example.core.model.Notebook
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.model.ScriptureCollection
import com.example.core.model.ScriptureCollectionItem
import com.example.core.model.ScriptureRef
import com.example.core.model.Tag
import com.example.core.repository.NoteCodec.toDomain
import com.example.core.repository.NoteCodec.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Everything about notes, notebooks, tags, links and scripture references.
 *
 * Recordings stay in [MeetingRepository]; this class only knows which note each recording
 * belongs to (docs/PLAN_V1.md §2).
 */
class NoteRepository(
    private val context: Context,
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val noteDao = database.noteDao()
    private val notebookDao = database.notebookDao()
    private val attachmentDao = database.attachmentDao()
    private val scriptureDao = database.scriptureDao()

    // ---------------------------------------------------------------- reading

    fun observeNotes(): Flow<List<Note>> =
        noteDao.observeActive().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeNotesInNotebook(notebookId: String): Flow<List<Note>> =
        noteDao.observeInNotebook(notebookId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeNotesForWorkflows(workflows: Collection<RecordingType>): Flow<List<Note>> =
        noteDao.observeByWorkflows(workflows.map { it.name }).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeArchivedNotes(): Flow<List<Note>> =
        noteDao.observeArchived().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeNotesWithTag(tagId: String): Flow<List<Note>> =
        noteDao.observeNotesWithTag(tagId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeNote(noteId: String): Flow<Note?> =
        noteDao.observeById(noteId).map { it?.toDomain() }.flowOn(Dispatchers.IO)

    /** The whole note as the editor sees it. Emits null once the note is deleted. */
    fun observeDocument(noteId: String): Flow<NoteDocument?> = combine(
        noteDao.observeById(noteId),
        noteDao.observeBlocks(noteId),
        attachmentDao.observeForNote(noteId),
        noteDao.observeTagsForNote(noteId),
        scriptureDao.observeForNote(noteId)
    ) { note, blocks, attachments, tags, refs ->
        note?.let {
            NoteDocument(
                note = it.toDomain(),
                blocks = blocks.map { b -> b.toDomain() },
                attachments = attachments.map { a -> a.toDomain() },
                tags = tags.map { t -> t.toDomain() },
                scriptureRefs = refs.map { r -> r.toDomain() }
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getDocument(noteId: String): NoteDocument? = withContext(Dispatchers.IO) {
        val note = noteDao.getById(noteId) ?: return@withContext null
        NoteDocument(
            note = note.toDomain(),
            blocks = noteDao.getBlocks(noteId).map { it.toDomain() },
            attachments = attachmentDao.getForNote(noteId).map { it.toDomain() },
            tags = noteDao.getTagsForNote(noteId).map { it.toDomain() },
            scriptureRefs = scriptureDao.getForNote(noteId).map { it.toDomain() }
        )
    }

    suspend fun getNote(noteId: String): Note? = withContext(Dispatchers.IO) { noteDao.getById(noteId)?.toDomain() }

    fun observeBacklinks(noteId: String): Flow<List<Note>> =
        noteDao.observeBacklinks(noteId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeLinks(noteId: String): Flow<List<NoteLink>> =
        noteDao.observeLinks(noteId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeRecordingIds(noteId: String): Flow<List<String>> =
        noteDao.observeMeetingsForNote(noteId).map { list -> list.map { it.id } }.flowOn(Dispatchers.IO)

    suspend fun searchNotes(query: String, limit: Int = 50): List<Note> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else noteDao.searchText(q, limit).map { it.toDomain() }
    }

    // ---------------------------------------------------------------- writing notes

    /**
     * Creates a note. With no [initialBlocks] it starts with one empty paragraph, so the editor
     * always has somewhere to put the cursor.
     */
    suspend fun createNote(
        workflow: RecordingType = RecordingType.GENERAL,
        title: String = "",
        notebookId: String? = null,
        isPrivate: Boolean = com.example.core.model.Workflows.isPrivateByDefault(workflow),
        eventDate: Long? = null,
        metadata: Map<String, String> = emptyMap(),
        initialBlocks: List<NoteBlock> = emptyList(),
        /** Start from the workflow's template sections when no blocks are given. */
        useTemplate: Boolean = true,
        /** Opened for the person to fill in: hidden, and discarded if left without content. */
        draft: Boolean = false
    ): Note = withContext(Dispatchers.IO) {
        val now = clock()
        val id = newId("note")
        val note = Note(
            id = id,
            title = title,
            workflow = workflow,
            notebookId = notebookId ?: defaultNotebookFor(workflow).id,
            createdAt = now,
            updatedAt = now,
            eventDate = eventDate ?: now,
            pinned = false,
            isPrivate = isPrivate,
            status = NoteStatus.OPEN,
            answeredAt = null,
            metadata = if (draft) metadata + (NoteContent.DRAFT to "1") + (NoteContent.DRAFT_TITLE to title) else metadata
        )
        val templated = if (initialBlocks.isEmpty() && useTemplate) {
            com.example.core.model.Workflows.startingBlocks(workflow, id, forRecording = false)
        } else initialBlocks
        val blocks = (templated.ifEmpty { listOf(paragraph(id)) })
            .mapIndexed { i, b -> b.copy(noteId = id, position = i) }
        val withText = note.copy(plainText = NoteCodec.plainTextOf(blocks))
        database.withTransaction {
            noteDao.upsert(withText.toEntity())
            noteDao.replaceBlocks(id, blocks.map { it.toEntity(now) })
        }
        withText
    }

    /** Saves title, pin, privacy, notebook, dates and metadata. Blocks are saved by [saveBlocks]. */
    suspend fun updateNote(note: Note) = withContext(Dispatchers.IO) {
        val existing = noteDao.getById(note.id) ?: return@withContext
        noteDao.upsert(note.copy(updatedAt = clock(), plainText = existing.plainText).toEntity())
    }

    suspend fun renameNote(noteId: String, title: String) = withContext(Dispatchers.IO) {
        val existing = noteDao.getById(noteId)?.toDomain() ?: return@withContext
        // Once the user names a note, recordings stop renaming it.
        val metadata = existing.metadata - META_TITLE_FROM_RECORDING
        noteDao.upsert(existing.copy(title = title.trim(), metadata = metadata, updatedAt = clock()).toEntity())
    }

    /**
     * Replaces the note's blocks with [blocks], in the order given. Positions are rewritten, so
     * the caller only has to get the order right.
     */
    suspend fun saveBlocks(noteId: String, blocks: List<NoteBlock>) = withContext(Dispatchers.IO) {
        val now = clock()
        val ordered = blocks.mapIndexed { i, b -> b.copy(noteId = noteId, position = i) }
        database.withTransaction {
            noteDao.replaceBlocks(noteId, ordered.map { it.toEntity(now) })
            noteDao.touch(noteId, now, plainTextWithRecordings(noteId, ordered))
        }
    }

    /** A draft that now has content becomes a real note, shown in lists. */
    suspend fun keepDraft(noteId: String) = mutate(noteId) { it.copy(metadata = it.metadata - NoteContent.DRAFT - NoteContent.DRAFT_TITLE) }

    /** Whether the stored note has anything of the person's in it. */
    suspend fun hasUserContent(noteId: String): Boolean = withContext(Dispatchers.IO) {
        val doc = getDocument(noteId) ?: return@withContext false
        NoteContent.hasUserContent(doc.note, doc.blocks, doc.attachments.isNotEmpty(), doc.tags.isNotEmpty(), noteDao.getMeetingsForNote(noteId).isNotEmpty())
    }

    suspend fun setPinned(noteId: String, pinned: Boolean) = mutate(noteId) { it.copy(pinned = pinned) }

    suspend fun setPrivate(noteId: String, isPrivate: Boolean) = mutate(noteId) { it.copy(isPrivate = isPrivate) }

    suspend fun moveToNotebook(noteId: String, notebookId: String?) = mutate(noteId) { it.copy(notebookId = notebookId) }

    suspend fun archiveNote(noteId: String) = mutate(noteId) { it.copy(archivedAt = clock()) }

    suspend fun unarchiveNote(noteId: String) = mutate(noteId) { it.copy(archivedAt = null) }

    suspend fun setStatus(noteId: String, status: NoteStatus) = mutate(noteId) {
        it.copy(status = status, answeredAt = if (status == NoteStatus.ANSWERED) clock() else null)
    }

    /**
     * Deletes a note and its blocks, tags, links, references and attachment files.
     *
     * Recordings are not deleted with it: audio is the one thing a user cannot recreate, so they
     * are detached and kept. Returns the ids of the detached recordings so the caller can offer
     * to delete them too.
     */
    suspend fun deleteNote(noteId: String): List<String> = withContext(Dispatchers.IO) {
        val attachments = attachmentDao.getForNote(noteId)
        val recordings = noteDao.getMeetingsForNote(noteId).map { it.id }
        database.withTransaction {
            recordings.forEach { noteDao.attachMeeting(it, null) }
            noteDao.delete(noteId)
            noteDao.deleteUnusedTags()
        }
        attachments.forEach { runCatching { File(it.path).delete() } }
        attachmentsDir(noteId).delete()
        recordings
    }

    private suspend fun mutate(noteId: String, change: (Note) -> Note) = withContext(Dispatchers.IO) {
        val existing = noteDao.getById(noteId)?.toDomain() ?: return@withContext
        noteDao.upsert(change(existing).copy(updatedAt = clock()).toEntity())
    }

    // ---------------------------------------------------------------- recordings

    /**
     * Gives a new recording its note, or files it into [existingNoteId] ("record here").
     *
     * A fresh note takes its title from the recording until the user renames it, so the title
     * the processing pipeline generates later flows through (see [syncFromRecording]).
     */
    suspend fun attachRecording(
        meetingId: String,
        title: String,
        workflow: RecordingType,
        createdAt: Long,
        existingNoteId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val now = clock()
        val existing = existingNoteId?.let { noteDao.getById(it) }
        if (existing != null) {
            val blocks = noteDao.getBlocks(existing.id).map { it.toDomain() }
            val updated = blocks + recordingBlock(existing.id, meetingId)
            database.withTransaction {
                if (existing.metadataJson.contains("\"${NoteContent.DRAFT}\":\"1\"")) {
                    val keep = existing.toDomain().let { it.copy(metadata = it.metadata - NoteContent.DRAFT - NoteContent.DRAFT_TITLE) }
                    noteDao.upsert(keep.toEntity())
                }
                noteDao.attachMeeting(meetingId, existing.id)
                noteDao.replaceBlocks(existing.id, updated.mapIndexed { i, b -> b.copy(position = i).toEntity(now) })
                noteDao.touch(existing.id, now, existing.plainText)
            }
            return@withContext existing.id
        }
        val noteId = "note_$meetingId"
        val note = Note(
            id = noteId,
            title = title,
            workflow = workflow,
            notebookId = defaultNotebookFor(workflow).id,
            createdAt = createdAt,
            updatedAt = now,
            eventDate = createdAt,
            pinned = false,
            isPrivate = com.example.core.model.Workflows.isPrivateByDefault(workflow),
            status = NoteStatus.OPEN,
            answeredAt = null,
            metadata = mapOf(META_TITLE_FROM_RECORDING to "true")
        )
        database.withTransaction {
            noteDao.upsert(note.toEntity())
            // The recording, then the person's own sections of the workflow's template; processing
            // adds the AI sections above them once the transcript exists.
            val starting = listOf(recordingBlock(noteId, meetingId)) +
                com.example.core.model.Workflows.startingBlocks(workflow, noteId, forRecording = true)
            noteDao.replaceBlocks(noteId, starting.mapIndexed { i, b -> b.copy(position = i).toEntity(now) })
            noteDao.attachMeeting(meetingId, noteId)
        }
        noteId
    }

    /**
     * Brings a note up to date after its recording was processed or renamed: the recording's
     * title (unless the user has named the note) and its summary, which search reads.
     */
    suspend fun syncFromRecording(meetingId: String) = withContext(Dispatchers.IO) {
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return@withContext
        val noteId = meeting.noteId ?: return@withContext
        val note = noteDao.getById(noteId)?.toDomain() ?: return@withContext
        val blocks = noteDao.getBlocks(noteId).map { it.toDomain() }
        val titleFollows = note.metadata[META_TITLE_FROM_RECORDING] == "true" &&
            noteDao.getMeetingsForNote(noteId).size == 1
        val updated = note.copy(
            title = if (titleFollows) meeting.title else note.title,
            plainText = plainTextWithRecordings(noteId, blocks)
        )
        if (updated != note) noteDao.upsert(updated.toEntity())
    }

    /**
     * Puts processing's generated sections into a note, replacing the previous run's.
     *
     * A section the person has edited is theirs and is left exactly as it is; only untouched
     * generated blocks are replaced. New sections go straight after the recording, above the
     * person's own sections. Scripture references are replaced the same way.
     */
    suspend fun applyGeneratedSections(
        noteId: String,
        generated: List<NoteBlock>,
        refs: List<ScriptureRef>,
        keys: Set<String>
    ) = withContext(Dispatchers.IO) {
        val current = noteDao.getBlocks(noteId).map { it.toDomain() }
        if (noteDao.getById(noteId) == null) return@withContext
        val editedKeys = current.filter { it.sectionKey in keys && (it.isUserEdited || it.source == BlockSource.USER) }
            .mapNotNull { it.sectionKey }.toSet()
        val kept = current.filterNot { it.sectionKey in keys && it.sectionKey !in editedKeys }
        val fresh = generated.filter { it.sectionKey !in editedKeys }
        val insertAt = kept.indexOfLast { it.type == NoteBlockType.RECORDING }.let { if (it < 0) 0 else it + 1 }
        val merged = kept.toMutableList().apply { addAll(insertAt, fresh) }
        val keptRefIds = kept.mapNotNull { it.payload[NoteBlock.PAYLOAD_SCRIPTURE_REF_ID] }.toSet()
        val freshBlockIds = fresh.map { it.id }.toSet()
        database.withTransaction {
            scriptureDao.getForNote(noteId)
                .filter { it.id !in keptRefIds && it.origin != com.example.core.model.ScriptureOrigin.USER.name }
                .forEach { scriptureDao.delete(it.id) }
            val newRefs = refs.filter { it.blockId in freshBlockIds }
            if (newRefs.isNotEmpty()) scriptureDao.upsert(newRefs.map { it.toEntity() })
            val now = clock()
            noteDao.replaceBlocks(noteId, merged.mapIndexed { i, b -> b.copy(position = i).toEntity(now) })
            noteDao.touch(noteId, now, plainTextWithRecordings(noteId, merged))
        }
    }

    private suspend fun plainTextWithRecordings(noteId: String, blocks: List<NoteBlock>): String {
        val summaries = noteDao.getMeetingsForNote(noteId).mapNotNull { it.summaryText?.takeIf { s -> s.isNotBlank() } }
        return (listOf(NoteCodec.plainTextOf(blocks)) + summaries).filter { it.isNotBlank() }.joinToString("\n")
    }

    // ---------------------------------------------------------------- prayer lifecycle

    /** Adds a dated update under a prayer request's Updates section. */
    suspend fun addPrayerUpdate(requestId: String, text: String) = withContext(Dispatchers.IO) {
        val blocks = noteDao.getBlocks(requestId).map { it.toDomain() }
        saveBlocks(requestId, withPrayerUpdate(blocks, requestId, text, clock()))
    }

    /** Marks a prayer request answered, today. */
    suspend fun markAnswered(requestId: String) = setStatus(requestId, NoteStatus.ANSWERED)

    /** Reopens a request marked answered by mistake. */
    suspend fun reopenRequest(requestId: String) = setStatus(requestId, NoteStatus.OPEN)

    /**
     * Starts a testimony for an answered request, with "What I prayed for" filled from the
     * request in the person's own words, and linked back to it.
     */
    suspend fun startTestimony(requestId: String): Note = withContext(Dispatchers.IO) {
        val request = noteDao.getById(requestId)?.toDomain() ?: error("No such request")
        val requestText = noteDao.getBlocks(requestId).map { it.toDomain() }
            .filter { it.sectionKey == "request" && it.type != NoteBlockType.HEADING_2 && it.type.isText && it.content.text.isNotBlank() }
            .joinToString("\n") { it.content.text }
        val testimony = createNote(workflow = RecordingType.TESTIMONY, title = request.title.ifBlank { "Answered prayer" }.let { "Testimony: $it" })
        if (requestText.isNotBlank()) {
            val blocks = noteDao.getBlocks(testimony.id).map { it.toDomain() }
            saveBlocks(testimony.id, blocks.map { b ->
                if (b.sectionKey == "prayed_for" && b.type != NoteBlockType.HEADING_2 && b.content.isEmpty)
                    b.copy(content = com.example.core.notes.RichText.plain(requestText)) else b
            })
        }
        link(testimony.id, requestId, NoteLinkKind.TESTIMONY_OF)
        testimony
    }

    /**
     * The note for one calendar event occurrence — made the first time, found again after that —
     * holding who was invited, where, and when (PLAN_V1 M8). Recording from the event files into it.
     */
    suspend fun noteForCalendarEvent(event: com.example.core.calendar.CalendarEvent, workflow: RecordingType): Note = withContext(Dispatchers.IO) {
        val key = event.key
        val escaped = key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        noteDao.findByMetadata("%\"$CALENDAR_EVENT_KEY\":\"$escaped\"%")?.toDomain()?.let { return@withContext it }
        val people = event.otherPeople
        createNote(
            workflow = workflow,
            title = event.title,
            eventDate = event.begin,
            draft = true,
            metadata = buildMap {
                put(CALENDAR_EVENT_KEY, key)
                put("eventStart", event.begin.toString())
                put("eventEnd", event.end.toString())
                if (people.isNotEmpty()) put("participants", people.joinToString(", "))
                event.location?.let { put("location", it) }
                event.calendarName?.let { put("calendar", it) }
            }
        )
    }

    /**
     * Notes related to [noteId], each with the reasons it matched (shared verses, tags, links,
     * similar wording). No model and no network: see [com.example.ai.notes.RelatedNotes].
     */
    suspend fun relatedNotes(noteId: String, limit: Int = 8): List<Pair<Note, com.example.ai.notes.RelatedNote>> = withContext(Dispatchers.IO) {
        val notes = noteDao.getAll().filter { it.archivedAt == null }.map { it.toDomain() }
        val tags = noteDao.getAllNoteTags().groupBy({ it.noteId }, { it.name })
        val refs = scriptureDao.getAll().groupBy { it.noteId }
        val links = noteDao.getAllLinks()
        val linked = HashMap<String, MutableSet<String>>()
        links.forEach { l ->
            linked.getOrPut(l.fromNoteId) { mutableSetOf() } += l.toNoteId
            linked.getOrPut(l.toNoteId) { mutableSetOf() } += l.fromNoteId
        }
        val signals = notes.map { n ->
            val passages = refs[n.id].orEmpty().map { "${it.bookUsfm} ${it.chapter}" }.toSet()
            com.example.ai.notes.NoteSignals(
                noteId = n.id,
                text = n.title + " " + n.plainText,
                tags = tags[n.id].orEmpty().toSet(),
                passages = passages,
                passageLabels = passages.associateWith { key ->
                    val (usfm, chapter) = key.split(' ')
                    (com.example.core.scripture.BibleBooks.byUsfm(usfm)?.name ?: usfm) + " " + chapter
                },
                linked = linked[n.id].orEmpty()
            )
        }
        val target = signals.firstOrNull { it.noteId == noteId } ?: return@withContext emptyList()
        val byId = notes.associateBy { it.id }
        com.example.ai.notes.RelatedNotes.find(target, signals, limit).mapNotNull { r -> byId[r.noteId]?.let { it to r } }
    }

    /** A devotional that starts from a verse (the Verse of the Day, or one the person picked). */
    suspend fun startDevotional(reference: com.example.core.scripture.ScriptureReference): Note = withContext(Dispatchers.IO) {
        val note = createNote(workflow = RecordingType.DEVOTIONAL, title = reference.display())
        val blockId = newId("block")
        val refId = newId("scripture")
        val scripture = NoteBlock(
            id = blockId, noteId = note.id, position = 0, type = NoteBlockType.SCRIPTURE,
            content = com.example.core.notes.RichText.plain(reference.display()),
            payload = mapOf(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to refId, "reference" to reference.display()),
            source = BlockSource.SCRIPTURE, sectionKey = "scripture"
        )
        val blocks = noteDao.getBlocks(note.id).map { it.toDomain() }
        val emptyBody = blocks.indexOfFirst { it.sectionKey == "scripture" && it.type != NoteBlockType.HEADING_2 }
        val withVerse = if (emptyBody >= 0) blocks.toMutableList().apply { set(emptyBody, scripture) } else blocks + scripture
        saveBlocks(note.id, withVerse)
        addScriptureRefs(listOf(ScriptureRef(refId, note.id, blockId, reference.usfm, reference.chapter, reference.verseStart, reference.verseEnd, null, com.example.core.model.ScriptureOrigin.USER, createdAt = clock())))
        getNote(note.id)!!
    }

    fun observeThemes(workflows: Collection<RecordingType>): Flow<List<com.example.core.database.NameCount>> = combine(
        noteDao.observeTagCountsForWorkflows(workflows.map { it.name }),
        noteDao.observeTopicCountsForWorkflows(workflows.map { it.name })
    ) { tags, topics ->
        (tags + topics).groupBy { it.name.trim().lowercase() }
            .map { (_, list) -> com.example.core.database.NameCount(list.first().name.trim(), list.sumOf { it.count }) }
            .sortedByDescending { it.count }
    }.flowOn(Dispatchers.IO)

    fun observeFaithMedia(): Flow<List<Attachment>> =
        attachmentDao.observeForWorkflows(com.example.core.model.Workflows.faith.map { it.name })
            .map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- notebooks

    fun observeNotebooks(): Flow<List<Notebook>> =
        notebookDao.observeActive().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeNotebookCounts(): Flow<Map<String, Int>> =
        notebookDao.observeNoteCounts().map { rows -> rows.associate { it.notebookId to it.count } }.flowOn(Dispatchers.IO)

    suspend fun getNotebook(id: String): Notebook? = withContext(Dispatchers.IO) { notebookDao.getById(id)?.toDomain() }

    suspend fun createNotebook(
        name: String,
        space: NotebookSpace,
        colorHex: String? = null,
        icon: String? = null
    ): Notebook = withContext(Dispatchers.IO) {
        val now = clock()
        val notebook = Notebook(newId("notebook"), name.trim(), space, colorHex, icon, now, now)
        notebookDao.upsert(notebook.toEntity())
        notebook
    }

    suspend fun updateNotebook(notebook: Notebook) = withContext(Dispatchers.IO) {
        notebookDao.upsert(notebook.copy(updatedAt = clock()).toEntity())
    }

    suspend fun archiveNotebook(id: String) = withContext(Dispatchers.IO) {
        val existing = notebookDao.getById(id) ?: return@withContext
        notebookDao.upsert(existing.copy(archivedAt = clock(), updatedAt = clock()))
    }

    /** Deletes the notebook only. Its notes move out of it (the foreign key sets them to none). */
    suspend fun deleteNotebook(id: String) = withContext(Dispatchers.IO) {
        if (id != MeetMindDatabase.DEFAULT_NOTEBOOK_ID) notebookDao.delete(id)
    }

    /** Faith notes gather in the Faith notebook; everything else starts in My Notes. */
    suspend fun defaultNotebookFor(workflow: RecordingType): Notebook {
        val space = com.example.core.model.Workflows.space(workflow)
        return if (space == NotebookSpace.FAITH) ensureSpaceNotebook(space) else ensureDefaultNotebook()
    }

    /** "My Notes" — recreated if it has gone missing, because new notes must land somewhere. */
    suspend fun ensureDefaultNotebook(): Notebook = withContext(Dispatchers.IO) {
        notebookDao.getById(MeetMindDatabase.DEFAULT_NOTEBOOK_ID)?.toDomain() ?: run {
            val now = clock()
            val notebook = Notebook(MeetMindDatabase.DEFAULT_NOTEBOOK_ID, "My Notes", NotebookSpace.PERSONAL, null, null, now, now)
            notebookDao.upsert(notebook.toEntity())
            notebook
        }
    }

    /** The notebook a space's new notes go into, created on first use (e.g. "Faith"). */
    suspend fun ensureSpaceNotebook(space: NotebookSpace): Notebook = withContext(Dispatchers.IO) {
        if (space == NotebookSpace.PERSONAL) return@withContext ensureDefaultNotebook()
        notebookDao.getBySpace(space.name).firstOrNull()?.toDomain() ?: createNotebook(space.displayName, space)
    }

    // ---------------------------------------------------------------- tags

    fun observeTags(): Flow<List<Tag>> = noteDao.observeTags().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    /** Tags are matched case-insensitively, so "Grace" and "grace" are one tag. */
    suspend fun addTag(noteId: String, name: String): Tag? = withContext(Dispatchers.IO) {
        val clean = name.trim().removePrefix("#").trim()
        if (clean.isEmpty()) return@withContext null
        val tag = noteDao.getTagByName(clean) ?: TagEntity(newId("tag"), clean).also { noteDao.insertTag(it) }
        noteDao.insertNoteTag(NoteTagCrossRef(noteId, tag.id))
        tag.toDomain()
    }

    suspend fun removeTag(noteId: String, tagId: String) = withContext(Dispatchers.IO) {
        noteDao.deleteNoteTag(noteId, tagId)
        noteDao.deleteUnusedTags()
    }

    // ---------------------------------------------------------------- links

    suspend fun link(fromNoteId: String, toNoteId: String, kind: NoteLinkKind): NoteLink = withContext(Dispatchers.IO) {
        val link = NoteLinkEntity(newId("link"), fromNoteId, toNoteId, kind.name, clock())
        noteDao.insertLink(link)
        link.toDomain()
    }

    suspend fun getLinks(noteId: String): List<NoteLink> = withContext(Dispatchers.IO) {
        noteDao.getLinks(noteId).map { it.toDomain() }
    }

    suspend fun unlink(linkId: String) = withContext(Dispatchers.IO) { noteDao.deleteLink(linkId) }

    // ---------------------------------------------------------------- attachments

    /** Where a note's copied-in media lives. Deleted with the note. */
    fun attachmentsDir(noteId: String): File = File(context.filesDir, "notes/$noteId").apply { mkdirs() }

    suspend fun addAttachment(attachment: Attachment): Attachment = withContext(Dispatchers.IO) {
        attachmentDao.upsert(attachment.toEntity())
        attachment
    }

    suspend fun getAttachment(id: String): Attachment? = withContext(Dispatchers.IO) { attachmentDao.getById(id)?.toDomain() }

    suspend fun deleteAttachment(id: String) = withContext(Dispatchers.IO) {
        val existing = attachmentDao.getById(id) ?: return@withContext
        attachmentDao.delete(id)
        runCatching { File(existing.path).delete() }
    }

    // ---------------------------------------------------------------- scripture

    suspend fun addScriptureRefs(refs: List<ScriptureRef>) = withContext(Dispatchers.IO) {
        if (refs.isNotEmpty()) scriptureDao.upsert(refs.map { it.toEntity() })
    }

    /** Replaces every reference of one origin, e.g. re-running detection after a transcript edit. */
    suspend fun replaceScriptureRefs(noteId: String, origin: com.example.core.model.ScriptureOrigin, refs: List<ScriptureRef>) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                scriptureDao.deleteForNoteWithOrigin(noteId, origin.name)
                if (refs.isNotEmpty()) scriptureDao.upsert(refs.map { it.toEntity() })
            }
        }

    suspend fun deleteScriptureRef(id: String) = withContext(Dispatchers.IO) { scriptureDao.delete(id) }

    fun observeAllScriptureRefs(): Flow<List<ScriptureRef>> =
        scriptureDao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeScriptureCollections(): Flow<List<ScriptureCollection>> =
        scriptureDao.observeCollections().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    fun observeCollectionItems(collectionId: String): Flow<List<ScriptureCollectionItem>> =
        scriptureDao.observeCollectionItems(collectionId).map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.IO)

    suspend fun createScriptureCollection(name: String): ScriptureCollection = withContext(Dispatchers.IO) {
        val now = clock()
        val entity = ScriptureCollectionEntity(newId("collection"), name.trim(), now, now)
        scriptureDao.upsertCollection(entity)
        entity.toDomain()
    }

    suspend fun deleteScriptureCollection(id: String) = withContext(Dispatchers.IO) { scriptureDao.deleteCollection(id) }

    suspend fun addToCollection(
        collectionId: String,
        bookUsfm: String,
        chapter: Int,
        verseStart: Int?,
        verseEnd: Int?,
        versionId: Int?,
        comment: String? = null
    ): ScriptureCollectionItem = withContext(Dispatchers.IO) {
        val entity = ScriptureCollectionItemEntity(
            id = newId("verse"),
            collectionId = collectionId,
            bookUsfm = bookUsfm,
            chapter = chapter,
            verseStart = verseStart,
            verseEnd = verseEnd,
            versionId = versionId,
            comment = comment,
            position = scriptureDao.nextCollectionPosition(collectionId),
            addedAt = clock()
        )
        scriptureDao.upsertCollectionItem(entity)
        entity.toDomain()
    }

    suspend fun removeFromCollection(itemId: String) = withContext(Dispatchers.IO) { scriptureDao.deleteCollectionItem(itemId) }

    // ---------------------------------------------------------------- helpers

    fun paragraph(noteId: String, source: BlockSource = BlockSource.USER) =
        NoteBlock(id = newId("block"), noteId = noteId, position = 0, type = NoteBlockType.PARAGRAPH, source = source)

    private fun recordingBlock(noteId: String, meetingId: String) = NoteBlock(
        id = "block_rec_$meetingId",
        noteId = noteId,
        position = 0,
        type = NoteBlockType.RECORDING,
        payload = mapOf(NoteBlock.PAYLOAD_MEETING_ID to meetingId),
        source = BlockSource.TRANSCRIPT,
        sectionKey = "recording"
    )

    companion object {
        const val CALENDAR_EVENT_KEY = "calendarEvent"

        /**
         * [blocks] with a dated update added under the Updates section — after its last block,
         * or in place of its empty placeholder. Pure, so the open editor can apply it to the
         * blocks it holds instead of racing its own autosave.
         */
        fun withPrayerUpdate(blocks: List<NoteBlock>, requestId: String, text: String, now: Long): List<NoteBlock> {
            val date = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(now))
            val update = NoteBlock(
                id = newId("block"), noteId = requestId, position = 0, type = NoteBlockType.PARAGRAPH,
                content = com.example.core.notes.RichText.plain("$date — ${text.trim()}")
                    .applyStyle(com.example.core.notes.InlineStyle.BOLD, 0, date.length),
                sectionKey = "updates"
            )
            val lastIndex = blocks.indexOfLast { it.sectionKey == "updates" }
            return if (lastIndex < 0) blocks + update else blocks.toMutableList().apply {
                val last = this[lastIndex]
                if (last.type.isText && last.type != NoteBlockType.HEADING_2 && last.content.isEmpty) set(lastIndex, update.copy(id = last.id))
                else add(lastIndex + 1, update)
            }
        }

        /** Metadata flag: the note's title still follows its recording's title. */
        const val META_TITLE_FROM_RECORDING = "titleFromRecording"

        fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
    }
}
