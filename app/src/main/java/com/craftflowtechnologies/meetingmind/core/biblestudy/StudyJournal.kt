package com.craftflowtechnologies.meetingmind.core.biblestudy

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.ScriptureOrigin
import com.craftflowtechnologies.meetingmind.core.model.ScriptureRef
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Metadata keys on a study journal entry. No schema change: they ride in the note's existing metadata map. */
object JournalMeta {
    /** The passage as a USFM id ("JHN.3.16-JHN.3.18"), what the reader's `ref` argument takes. */
    const val PASSAGE_REF = "passageRef"
    /** A [SessionKind.key]. */
    const val SESSION_KIND = "sessionKind"
    const val NEED = "studyNeed"
    /** For a devotional's reflection: the devotional note it answers. */
    const val DEVOTIONAL_NOTE = "devotionalNoteId"
    /** For a devotional's reflection: the day the devotional is for ("yyyy-MM-dd"). */
    const val DEVOTIONAL_DAY = "devotionalForDay"
}

/** The entry a session writes: title, metadata and the blocks. Pure, so it can be tested without a database. */
object JournalEntry {

    fun title(passage: ScriptureReference, kind: SessionKind) = "${passage.display()} · ${kind.label}"

    fun metadata(passage: ScriptureReference, kind: SessionKind, need: Need?, extra: Map<String, String> = emptyMap()): Map<String, String> =
        buildMap {
            put(JournalMeta.PASSAGE_REF, passage.passageId())
            put(JournalMeta.SESSION_KIND, kind.key)
            need?.let { put(JournalMeta.NEED, it.key) }
            putAll(extra)
        }

    /** The passage block (with its reference) followed by each written answer under the question it answered. */
    fun blocks(noteId: String, passage: ScriptureReference, written: List<Written>, newId: (String) -> String, startAt: Int = 0): Pair<List<NoteBlock>, ScriptureRef> {
        val blockId = newId("block"); val refId = newId("scripture")
        val list = mutableListOf<NoteBlock>()
        list += NoteBlock(
            blockId, noteId, startAt, NoteBlockType.SCRIPTURE, RichText.plain(passage.display()),
            mapOf(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to refId, "reference" to passage.display()), BlockSource.SCRIPTURE, sectionKey = "passage"
        )
        written.forEach { w ->
            list += NoteBlock(newId("block"), noteId, startAt + list.size, NoteBlockType.HEADING_3, RichText.plain(w.heading), sectionKey = w.stepId)
            list += NoteBlock(newId("block"), noteId, startAt + list.size, NoteBlockType.PARAGRAPH, RichText.plain(w.text), sectionKey = w.stepId, isUserEdited = true)
        }
        val ref = ScriptureRef(refId, noteId, blockId, passage.usfm, passage.chapter, passage.verseStart, passage.verseEnd, null, ScriptureOrigin.USER, createdAt = System.currentTimeMillis())
        return list to ref
    }
}

/**
 * Writes one session into the study journal (ONE_PLACE §3.3).
 *
 * Nothing exists until the person writes: [onChanged] does nothing for a session with no text, and
 * creates the note, with their first words already in it, on the first keystroke. A blank note is
 * never made first. After that it keeps the same note up to date. Closing an untouched session
 * leaves nothing behind.
 *
 * A devotional's reflection uses the same session ([SessionKind.DEVOTIONAL]): [title] and [extraMeta]
 * link the entry to its devotional, and [resumeNoteId] continues an entry written earlier, so
 * reopening the devotional edits the same note instead of making a second one.
 */
class JournalSession(
    private val notes: NoteRepository,
    private val passage: ScriptureReference,
    private val kind: SessionKind,
    private val need: Need? = null,
    private val title: String? = null,
    private val extraMeta: Map<String, String> = emptyMap(),
    resumeNoteId: String? = null
) {
    private val lock = Mutex()

    /** The journal entry's note id, once the person has written something. */
    var noteId: String? = resumeNoteId
        private set

    suspend fun onChanged(state: SessionState) = lock.withLock {
        val id = noteId
        // Nothing yet and nothing written: nothing is made. Text cleared from an existing entry is cleared.
        if (!state.touched && id == null) return@withLock
        val written = state.written()
        if (id == null) {
            val provisional = "pending"
            val (blocks, ref) = JournalEntry.blocks(provisional, passage, written, NoteRepository.Companion::newId)
            val note = notes.createNote(
                workflow = RecordingType.BIBLE_STUDY, title = title ?: JournalEntry.title(passage, kind),
                metadata = JournalEntry.metadata(passage, kind, need, extraMeta), initialBlocks = blocks, useTemplate = false
            )
            notes.addScriptureRefs(listOf(ref.copy(noteId = note.id)))
            noteId = note.id
        } else {
            val (blocks, ref) = JournalEntry.blocks(id, passage, written, NoteRepository.Companion::newId)
            notes.saveBlocks(id, blocks)
            notes.replaceScriptureRefs(id, ScriptureOrigin.USER, listOf(ref))
        }
    }

    /** Appends the session's answers to a note the person already has, as blocks under a heading. */
    suspend fun addToExisting(existingNoteId: String, state: SessionState): Boolean = lock.withLock {
        val written = state.written()
        if (written.isEmpty()) return@withLock false
        val doc = notes.getDocument(existingNoteId) ?: return@withLock false
        val existing = doc.blocks.sortedBy { it.position }
        val heading = NoteBlock(
            NoteRepository.newId("block"), existingNoteId, existing.size, NoteBlockType.HEADING_2,
            RichText.plain("Bible study · ${passage.display()}"), sectionKey = "bible_study"
        )
        val (added, ref) = JournalEntry.blocks(existingNoteId, passage, written, NoteRepository.Companion::newId, startAt = existing.size + 1)
        // A note that is only its empty first paragraph is replaced rather than left above the answers.
        val base = if (existing.size == 1 && existing[0].type == NoteBlockType.PARAGRAPH && existing[0].content.text.isBlank()) emptyList() else existing
        notes.saveBlocks(existingNoteId, base + heading + added)
        notes.addScriptureRefs(listOf(ref))
        true
    }
}

/** Reading the journal back: all entries, and the ones on a passage. */
class StudyJournal(private val notes: NoteRepository) {

    /** Every study journal entry, newest first. */
    fun observe(): Flow<List<Note>> = notes.observeNotesForWorkflows(listOf(RecordingType.BIBLE_STUDY))
        .map { list -> list.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt } }

    /** The person's notes that touch [passage], for "Your notes on this passage". */
    suspend fun onPassage(passage: ScriptureReference) = notes.notesOnPassage(passage).myNotes
}
