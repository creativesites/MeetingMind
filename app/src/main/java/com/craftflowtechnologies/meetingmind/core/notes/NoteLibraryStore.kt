package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.database.LIBRARY_PIN_BOOST
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.repository.NoteCodec.toDomain
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.work.WorkTypeNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** What the Notes library reads. An interface so the view model can be tested without a database. */
interface LibraryData {
    /** One bounded page after [after] (null = first), in the order [q] asks for. */
    suspend fun page(q: LibraryQuery, after: Cursor?, limit: Int): List<LibraryItem>
    /** Emits whenever any note changes; the library reloads on it. */
    fun changes(): Flow<Int>
}

/**
 * The Notes library's reads (U-2): keyset pages over the notes table, or a ranked, offset-paged full-text search
 * (FTS4 has no sortable rank, and a search is a short list). Nothing here loads the whole table.
 */
class NoteLibraryStore(private val database: MeetMindDatabase) : LibraryData {
    private val dao = database.noteDao()
    private val faithTypes = Workflows.faith.map { it.name }

    override fun changes(): Flow<Int> = dao.observeNoteChanges()

    override suspend fun page(q: LibraryQuery, after: Cursor?, limit: Int): List<LibraryItem> = withContext(Dispatchers.IO) {
        val terms = if (q.isSearching) NoteSearch.terms(q.search) else emptyList()
        val rows: List<Pair<NoteEntity, Cursor>> = when {
            q.isSearching -> {
                val offset = after?.let { it.longKey.toInt() + 1 } ?: 0
                val capped = minOf(limit, SEARCH_MAX_RESULTS - offset)
                if (capped <= 0) emptyList() else search(q, capped, offset).mapIndexed { i, n -> n to Cursor((offset + i).toLong(), n.id) }
            }
            q.sort == NoteSort.TITLE -> byTitle(q, after, limit).map { it to Cursor(it.title, it.id) }
            else -> byDate(q, after, limit).map { it to Cursor(dateKey(it, q), it.id) }
        }
        val meetings = rows.map { it.first.id }.chunked(500).flatMap { dao.getMeetingsForNotes(it) }.filter { it.noteId != null }.groupBy { it.noteId!! }
        rows.map { (e, cursor) ->
            val note = e.toDomain()
            val held = q.hideLocked && (note.isPrivate || note.workflow.name in faithTypes)
            val mine = meetings[note.id].orEmpty()
            LibraryItem(
                note = note,
                preview = when {
                    note.isPrivate || held -> ""
                    q.isSearching -> NotePreview.around(note.plainText, terms)
                    else -> NotePreview.lead(note.plainText)
                },
                durationMs = mine.sumOf { it.durationMs }.takeIf { mine.isNotEmpty() && it > 0 },
                hasRecording = mine.isNotEmpty(),
                transcript = transcriptOf(mine),
                cursor = cursor,
                highlight = terms
            )
        }
    }

    private fun transcriptOf(meetings: List<MeetingEntity>) = when {
        meetings.any { it.status == "PROCESSING" || it.status == "RECORDING" } -> NoteTranscriptStatus.Processing
        meetings.any { it.status == "ERROR" } -> NoteTranscriptStatus.Failed
        meetings.any { it.status == "READY" } -> NoteTranscriptStatus.Ready
        else -> NoteTranscriptStatus.None
    }

    /** The sort key a date-ordered row carries: what the query orders by, pinned boosted when pinned-first applies. */
    internal fun dateKey(n: NoteEntity, q: LibraryQuery): Long =
        (if (q.sort == NoteSort.CREATED) n.eventDate ?: n.createdAt else n.updatedAt) + if (q.pinFirst && n.pinned) LIBRARY_PIN_BOOST else 0L

    private suspend fun byDate(q: LibraryQuery, after: Cursor?, limit: Int): List<NoteEntity> = dao.libraryByDate(
        mode(q), q.notebookId, spaceAny(q), flag(LibraryChip.WORK, q), flag(LibraryChip.FAITH, q), flag(LibraryChip.BIBLE, q),
        flag(LibraryChip.RECORDINGS, q), flag(LibraryChip.PINNED, q), anyType(q), typeNames(q), q.tagId, 0,
        WorkTypeNames, faithTypes, if (q.sort == NoteSort.CREATED) 1 else 0, if (q.pinFirst) 1 else 0,
        after?.longKey ?: Long.MAX_VALUE, after?.id ?: HIGH_ID, limit
    )

    private suspend fun byTitle(q: LibraryQuery, after: Cursor?, limit: Int): List<NoteEntity> = dao.libraryByTitle(
        mode(q), q.notebookId, spaceAny(q), flag(LibraryChip.WORK, q), flag(LibraryChip.FAITH, q), flag(LibraryChip.BIBLE, q),
        flag(LibraryChip.RECORDINGS, q), flag(LibraryChip.PINNED, q), anyType(q), typeNames(q), q.tagId, 0,
        WorkTypeNames, faithTypes, if (after == null) 0 else 1, after?.textKey ?: "", after?.id ?: "", limit
    )

    private suspend fun search(q: LibraryQuery, limit: Int, offset: Int): List<NoteEntity> = dao.librarySearch(
        mode(q), q.notebookId, spaceAny(q), flag(LibraryChip.WORK, q), flag(LibraryChip.FAITH, q), flag(LibraryChip.BIBLE, q),
        flag(LibraryChip.RECORDINGS, q), flag(LibraryChip.PINNED, q), anyType(q), typeNames(q), q.tagId, if (q.hideLocked) 1 else 0,
        WorkTypeNames, faithTypes, q.match!!, if (q.includeTranscripts) 1 else 0, limit, offset
    )

    private fun mode(q: LibraryQuery) = when (q.mode) { LibraryMode.ACTIVE -> 0; LibraryMode.ARCHIVED -> 1; LibraryMode.TRASH -> 2 }
    private fun flag(c: LibraryChip, q: LibraryQuery) = if (c in q.chips) 1 else 0
    private fun spaceAny(q: LibraryQuery) = if (q.chips.any { it.isSpace }) 1 else 0
    private fun anyType(q: LibraryQuery) = if (q.types.isEmpty()) 0 else 1
    /** SQLite is happy with an empty IN list, but a non-empty one keeps the query plan the same. */
    private fun typeNames(q: LibraryQuery) = q.types.map(RecordingType::name).ifEmpty { listOf("") }

    companion object {
        /** A search shows at most this many results (offset + limit). */
        const val SEARCH_MAX_RESULTS = 200
        private const val HIGH_ID = "￿"
    }
}
