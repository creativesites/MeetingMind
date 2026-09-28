package com.example.core.notes

import com.example.core.database.MeetMindDatabase
import com.example.core.database.NoteVersionEntity
import com.example.core.database.NoteVersionSummary
import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.repository.NoteCodec
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Why a version was saved. The BEFORE_* reasons are the moments a lot changes at once. */
enum class VersionReason(val label: String) {
    EDIT_SESSION("Edited"),
    BEFORE_AI("Before AI"),
    BEFORE_RESTORE("Before restoring"),
    BEFORE_PASTE("Before paste"),
    BEFORE_IMPORT("Before import"),
    MANUAL("Saved version");

    /** Versions taken on purpose, or just before a big change, outlive the usual thinning. */
    val protected: Boolean get() = this != EDIT_SESSION
}

/** A saved version's contents. */
data class NoteSnapshot(val title: String, val blocks: List<NoteBlock>)

/**
 * The note's blocks as gzipped JSON. Every field of a block is kept, so restoring a version gives
 * back exactly what was there: styles, checkboxes, indents, payloads, where the text came from.
 */
object VersionCodec {
    fun encode(blocks: List<NoteBlock>): ByteArray {
        val array = JSONArray()
        blocks.forEach { b ->
            array.put(JSONObject().apply {
                put("id", b.id); put("type", b.type.name); put("text", b.content.text); put("spans", b.content.encodeSpans())
                put("payload", NoteCodec.encodeMap(b.payload)); put("source", b.source.name)
                put("segments", JSONArray(b.sourceSegmentIds)); b.sectionKey?.let { put("section", it) }
                put("edited", b.isUserEdited); put("indent", b.indent); put("checked", b.checked)
            })
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(array.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray, noteId: String): List<NoteBlock> {
        val json = GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val segments = o.optJSONArray("segments")
            NoteBlock(
                id = o.getString("id"),
                noteId = noteId,
                position = i,
                type = runCatching { NoteBlockType.valueOf(o.getString("type")) }.getOrDefault(NoteBlockType.PARAGRAPH),
                content = RichText.decode(o.optString("text"), o.optString("spans")),
                payload = NoteCodec.decodeMap(o.optString("payload")),
                source = runCatching { BlockSource.valueOf(o.optString("source")) }.getOrDefault(BlockSource.USER),
                sourceSegmentIds = (0 until (segments?.length() ?: 0)).map { segments!!.getString(it) },
                sectionKey = o.optString("section").takeIf { o.has("section") },
                isUserEdited = o.optBoolean("edited"),
                indent = o.optInt("indent"),
                checked = o.optBoolean("checked")
            )
        }
    }

    /** Plain text of a version, for the preview's diff and "Copy text". */
    fun lines(blocks: List<NoteBlock>): List<String> =
        blocks.filter { it.type.isText || it.type == NoteBlockType.DIVIDER }.map { b ->
            when (b.type) {
                NoteBlockType.HEADING_1 -> "# " + b.content.text
                NoteBlockType.HEADING_2 -> "## " + b.content.text
                NoteBlockType.HEADING_3 -> "### " + b.content.text
                NoteBlockType.BULLET -> "  ".repeat(b.indent) + "• " + b.content.text
                NoteBlockType.NUMBERED -> "  ".repeat(b.indent) + "– " + b.content.text
                NoteBlockType.CHECKLIST -> "  ".repeat(b.indent) + (if (b.checked) "☑ " else "☐ ") + b.content.text
                NoteBlockType.QUOTE -> "> " + b.content.text
                NoteBlockType.DIVIDER -> "———"
                else -> b.content.text
            }
        }
}

/**
 * Which versions to let go, so history stays useful without growing forever (PRD_M0 §4.6):
 * everything from the last day; then one an hour for a week; one a day for a month; one a week
 * after that. Protected versions are kept for 30 days whatever the rule, and a note never has more
 * than [MAX_PER_NOTE].
 */
object VersionRetention {
    const val MAX_PER_NOTE = 100
    const val BUDGET_BYTES = 50L * 1024 * 1024
    private const val HOUR = 60 * 60 * 1000L
    private const val DAY = 24 * HOUR

    /** [versions] are one note's, any order. Returns the ids to delete. */
    fun toPrune(versions: List<NoteVersionSummary>, now: Long): List<String> {
        val newestFirst = versions.sortedByDescending { it.createdAt }
        val keep = mutableListOf<NoteVersionSummary>()
        val seenBuckets = mutableSetOf<String>()
        for (v in newestFirst) {
            val age = now - v.createdAt
            val reason = runCatching { VersionReason.valueOf(v.reason) }.getOrDefault(VersionReason.EDIT_SESSION)
            val bucket = when {
                age < DAY -> null
                age < 7 * DAY -> "h" + v.createdAt / HOUR
                age < 30 * DAY -> "d" + v.createdAt / DAY
                else -> "w" + v.createdAt / (7 * DAY)
            }
            val protectedNow = reason.protected && age < 30 * DAY
            if (bucket == null || protectedNow || seenBuckets.add(bucket)) keep += v
        }
        // Hard cap: the oldest go first, unprotected before protected.
        val overCap = keep.size - MAX_PER_NOTE
        val capped = if (overCap > 0) {
            val removable = keep.sortedWith(compareBy<NoteVersionSummary>({ runCatching { VersionReason.valueOf(it.reason).protected }.getOrDefault(false) }, { it.createdAt }))
            keep - removable.take(overCap).toSet()
        } else keep
        val kept = capped.map { it.id }.toSet()
        return versions.map { it.id }.filter { it !in kept }
    }

    /** Across every note, oldest first: what to delete to get under [budget]. */
    fun overBudget(oldestFirst: List<NoteVersionSummary>, totalBytes: Long, budget: Long = BUDGET_BYTES): List<String> {
        var total = totalBytes
        val out = mutableListOf<String>()
        for (v in oldestFirst) {
            if (total <= budget) break
            out += v.id
            total -= v.byteSize
        }
        return out
    }
}

/** Saves, lists and restores a note's versions. */
class NoteVersionRepository(
    private val database: MeetMindDatabase,
    private val notes: NoteRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.noteVersionDao()

    fun observe(noteId: String): Flow<List<NoteVersionSummary>> = dao.observeSummaries(noteId).flowOn(Dispatchers.IO)

    /**
     * Saves [blocks] (or the note as stored) as a version. Nothing is saved when it's identical to
     * the newest version, so calling this generously is cheap. Returns the new version's id.
     */
    suspend fun snapshot(
        noteId: String,
        reason: VersionReason,
        label: String? = null,
        blocks: List<NoteBlock>? = null,
        title: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val note = notes.getNote(noteId) ?: return@withContext null
        val content = blocks ?: notes.getDocument(noteId)?.blocks ?: return@withContext null
        val meaningful = content.filterNot { it.type == NoteBlockType.PARAGRAPH && it.content.isEmpty }
        if (meaningful.isEmpty()) return@withContext null
        val bytes = VersionCodec.encode(meaningful)
        val versionTitle = title ?: note.title
        val latest = dao.latest(noteId)
        if (latest != null && latest.title == versionTitle && latest.blocks.contentEquals(bytes)) return@withContext null
        val id = NoteRepository.newId("version")
        dao.insert(NoteVersionEntity(id, noteId, clock(), reason.name, label, versionTitle, bytes, bytes.size))
        prune(noteId)
        id
    }

    suspend fun load(versionId: String): NoteSnapshot? = withContext(Dispatchers.IO) {
        val v = dao.getById(versionId) ?: return@withContext null
        NoteSnapshot(v.title, VersionCodec.decode(v.blocks, v.noteId))
    }

    /**
     * Puts the note back as it was in [versionId]. The note as it is now is saved first, so a
     * restore can itself be undone. Returns the restored contents.
     */
    suspend fun restore(versionId: String): NoteSnapshot? = withContext(Dispatchers.IO) {
        val v = dao.getById(versionId) ?: return@withContext null
        snapshot(v.noteId, VersionReason.BEFORE_RESTORE)
        val restored = VersionCodec.decode(v.blocks, v.noteId)
        notes.saveBlocks(v.noteId, restored)
        if (notes.getNote(v.noteId)?.title != v.title) notes.renameNote(v.noteId, v.title)
        NoteSnapshot(v.title, restored)
    }

    private suspend fun prune(noteId: String) {
        val now = clock()
        val summaries = dao.forNote(noteId).map { NoteVersionSummary(it.id, it.noteId, it.createdAt, it.reason, it.label, it.title, it.byteSize) }
        VersionRetention.toPrune(summaries, now).takeIf { it.isNotEmpty() }?.let { dao.delete(it) }
        val total = dao.totalBytes()
        if (total > VersionRetention.BUDGET_BYTES) {
            VersionRetention.overBudget(dao.allSummariesOldestFirst(), total).takeIf { it.isNotEmpty() }?.let { dao.delete(it) }
        }
    }
}

/** One line of a version compared with another. */
data class DiffLine(val text: String, val change: Change) {
    enum class Change { SAME, ADDED, REMOVED }
}

/** Line-by-line comparison of two versions' text, for the history preview. */
object VersionDiff {
    fun lines(from: List<String>, to: List<String>): List<DiffLine> {
        val patch = com.github.difflib.DiffUtils.diff(from, to)
        val out = mutableListOf<DiffLine>()
        var i = 0
        for (delta in patch.deltas.sortedBy { it.source.position }) {
            while (i < delta.source.position) out += DiffLine(from[i++], DiffLine.Change.SAME)
            delta.source.lines.forEach { out += DiffLine(it, DiffLine.Change.REMOVED) }
            delta.target.lines.forEach { out += DiffLine(it, DiffLine.Change.ADDED) }
            i = delta.source.position + delta.source.lines.size
        }
        while (i < from.size) out += DiffLine(from[i++], DiffLine.Change.SAME)
        return out
    }
}
