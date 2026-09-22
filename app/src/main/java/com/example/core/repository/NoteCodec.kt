package com.example.core.repository

import com.example.core.database.AttachmentEntity
import com.example.core.database.NoteBlockEntity
import com.example.core.database.NoteEntity
import com.example.core.database.NoteLinkEntity
import com.example.core.database.NotebookEntity
import com.example.core.database.ScriptureCollectionEntity
import com.example.core.database.ScriptureCollectionItemEntity
import com.example.core.database.ScriptureRefEntity
import com.example.core.database.TagEntity
import com.example.core.model.Attachment
import com.example.core.model.AttachmentKind
import com.example.core.model.BlockSource
import com.example.core.model.Note
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteLink
import com.example.core.model.NoteLinkKind
import com.example.core.model.NoteStatus
import com.example.core.model.Notebook
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.model.ScriptureCollection
import com.example.core.model.ScriptureCollectionItem
import com.example.core.model.ScriptureOrigin
import com.example.core.model.ScriptureRef
import com.example.core.model.Tag
import com.example.core.notes.RichText
import org.json.JSONObject

/**
 * Entity ↔ domain conversion for the notes schema.
 *
 * Every enum read is forgiving: a value written by a newer build, or damaged on disk, falls back
 * to a safe default instead of making the whole note unreadable.
 */
internal object NoteCodec {

    fun encodeMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decodeMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.optString(it, "") }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)

    fun NoteEntity.toDomain() = Note(
        id = id,
        title = title,
        workflow = enumOr(workflow, RecordingType.GENERAL),
        notebookId = notebookId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        eventDate = eventDate,
        pinned = pinned,
        isPrivate = isPrivate,
        status = enumOr(status, NoteStatus.OPEN),
        answeredAt = answeredAt,
        metadata = decodeMap(metadataJson),
        archivedAt = archivedAt,
        plainText = plainText
    )

    fun Note.toEntity() = NoteEntity(
        id = id,
        title = title,
        workflow = workflow.name,
        notebookId = notebookId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        eventDate = eventDate,
        pinned = pinned,
        isPrivate = isPrivate,
        status = status.name,
        answeredAt = answeredAt,
        metadataJson = encodeMap(metadata),
        archivedAt = archivedAt,
        plainText = plainText
    )

    fun NoteBlockEntity.toDomain() = NoteBlock(
        id = id,
        noteId = noteId,
        position = position,
        type = enumOr(type, NoteBlockType.PARAGRAPH),
        content = RichText.decode(text, spans),
        payload = decodeMap(payloadJson),
        source = enumOr(source, BlockSource.USER),
        sourceSegmentIds = sourceSegmentIdsJson.toIdList(),
        sectionKey = sectionKey,
        isUserEdited = isUserEdited,
        indent = indent,
        checked = checked
    )

    fun NoteBlock.toEntity(updatedAt: Long) = NoteBlockEntity(
        id = id,
        noteId = noteId,
        position = position,
        type = type.name,
        text = content.text,
        spans = content.encodeSpans(),
        payloadJson = encodeMap(payload),
        source = source.name,
        sourceSegmentIdsJson = sourceSegmentIds.toIdsJson(),
        sectionKey = sectionKey,
        isUserEdited = isUserEdited,
        indent = indent,
        checked = checked,
        updatedAt = updatedAt
    )

    fun NotebookEntity.toDomain() = Notebook(
        id = id,
        name = name,
        space = enumOr(space, NotebookSpace.PERSONAL),
        colorHex = colorHex,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        sortOrder = sortOrder
    )

    fun Notebook.toEntity() = NotebookEntity(
        id = id,
        name = name,
        space = space.name,
        colorHex = colorHex,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        sortOrder = sortOrder
    )

    fun AttachmentEntity.toDomain() = Attachment(
        id = id,
        noteId = noteId,
        kind = enumOr(kind, AttachmentKind.FILE),
        path = path,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMs = durationMs,
        caption = caption,
        createdAt = createdAt
    )

    fun Attachment.toEntity() = AttachmentEntity(
        id = id,
        noteId = noteId,
        kind = kind.name,
        path = path,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMs = durationMs,
        caption = caption,
        createdAt = createdAt
    )

    fun TagEntity.toDomain() = Tag(id = id, name = name)

    fun NoteLinkEntity.toDomain() = NoteLink(
        id = id,
        fromNoteId = fromNoteId,
        toNoteId = toNoteId,
        kind = enumOr(kind, NoteLinkKind.RELATED),
        createdAt = createdAt
    )

    fun ScriptureRefEntity.toDomain() = ScriptureRef(
        id = id,
        noteId = noteId,
        blockId = blockId,
        bookUsfm = bookUsfm,
        chapter = chapter,
        verseStart = verseStart,
        verseEnd = verseEnd,
        versionId = versionId,
        origin = enumOr(origin, ScriptureOrigin.USER),
        meetingId = meetingId,
        segmentId = segmentId,
        startMs = startMs,
        createdAt = createdAt
    )

    fun ScriptureRef.toEntity() = ScriptureRefEntity(
        id = id,
        noteId = noteId,
        blockId = blockId,
        bookUsfm = bookUsfm,
        chapter = chapter,
        verseStart = verseStart,
        verseEnd = verseEnd,
        versionId = versionId,
        origin = origin.name,
        meetingId = meetingId,
        segmentId = segmentId,
        startMs = startMs,
        createdAt = createdAt
    )

    fun ScriptureCollectionEntity.toDomain() = ScriptureCollection(id, name, createdAt, updatedAt)

    fun ScriptureCollectionItemEntity.toDomain() = ScriptureCollectionItem(
        id = id,
        collectionId = collectionId,
        bookUsfm = bookUsfm,
        chapter = chapter,
        verseStart = verseStart,
        verseEnd = verseEnd,
        versionId = versionId,
        comment = comment,
        position = position,
        addedAt = addedAt
    )

    /** The searchable text of a note: every text block, one per line. */
    fun plainTextOf(blocks: List<NoteBlock>): String =
        blocks.filter { it.type.isText && it.content.text.isNotBlank() }.joinToString("\n") { it.content.text }
}
