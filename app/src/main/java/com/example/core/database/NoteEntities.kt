package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The notes schema (docs/PLAN_V1.md §2), added in database version 13.
 *
 * Every table here is created by [MeetMindDatabase.MIGRATION_12_13] with SQL that must match
 * these declarations exactly — Room checks on open. `NoteSchemaTest` compares the two.
 */

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.example.core.model.NotebookSpace] name. */
    val space: String,
    val colorHex: String?,
    val icon: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
    val sortOrder: Int
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            // Deleting a notebook must never delete the notes in it.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["notebookId"]), Index(value = ["updatedAt"])]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** [com.example.core.model.RecordingType] name. */
    val workflow: String,
    val notebookId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val eventDate: Long?,
    val pinned: Boolean,
    val isPrivate: Boolean,
    /** [com.example.core.model.NoteStatus] name. */
    val status: String,
    val answeredAt: Long?,
    /** Flat string map, encoded by [com.example.core.repository.NoteCodec]. */
    val metadataJson: String,
    val archivedAt: Long?,
    /** Every text block's text, joined — search and list previews read this. */
    val plainText: String
)

@Entity(
    tableName = "note_blocks",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["noteId"])]
)
data class NoteBlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val position: Int,
    /** [com.example.core.model.NoteBlockType] name. */
    val type: String,
    val text: String,
    /** [com.example.core.notes.RichText.encodeSpans] output. */
    val spans: String,
    val payloadJson: String,
    /** [com.example.core.model.BlockSource] name. */
    val source: String,
    val sourceSegmentIdsJson: String,
    val sectionKey: String?,
    val isUserEdited: Boolean,
    val indent: Int,
    val checked: Boolean,
    val updatedAt: Long
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["noteId"])]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val kind: String,
    val path: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val caption: String?,
    val createdAt: Long
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["tagId"])]
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String
)

@Entity(
    tableName = "note_links",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["fromNoteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["toNoteId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["fromNoteId"]), Index(value = ["toNoteId"])]
)
data class NoteLinkEntity(
    @PrimaryKey val id: String,
    val fromNoteId: String,
    val toNoteId: String,
    val kind: String,
    val createdAt: Long
)

@Entity(
    tableName = "scripture_refs",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["noteId"]), Index(value = ["bookUsfm", "chapter"])]
)
data class ScriptureRefEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val blockId: String?,
    val bookUsfm: String,
    val chapter: Int,
    val verseStart: Int?,
    val verseEnd: Int?,
    val versionId: Int?,
    val origin: String,
    val meetingId: String?,
    val segmentId: String?,
    val startMs: Long?,
    val createdAt: Long
)

@Entity(tableName = "scripture_collections")
data class ScriptureCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "scripture_collection_items",
    foreignKeys = [
        ForeignKey(
            entity = ScriptureCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["collectionId"])]
)
data class ScriptureCollectionItemEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val bookUsfm: String,
    val chapter: Int,
    val verseStart: Int?,
    val verseEnd: Int?,
    val versionId: Int?,
    val comment: String?,
    val position: Int,
    val addedAt: Long
)

/**
 * A note AI run (docs/PLAN_V1.md M9): what was asked, over which note or notebook, and — once it
 * has run — the validated result. A row exists before the work is queued, so a result survives the
 * app being closed while it runs. Not tied to a meeting, which is why it isn't an [AiJobEntity].
 */
@androidx.room.Entity(tableName = "note_ai_jobs", indices = [androidx.room.Index(value = ["targetId"])])
data class NoteAiJobEntity(
    @androidx.room.PrimaryKey val id: String,
    /** NOTE or NOTEBOOK. */
    val targetKind: String,
    val targetId: String,
    val tool: String,
    val status: String,
    val inputJson: String,
    val resultJson: String?,
    val errorMessage: String?,
    /** Which model produced the result, for the "made by" line. */
    val engine: String?,
    val createdAt: Long,
    val updatedAt: Long
)
