package com.example.core.model

import com.example.core.notes.RichText

/**
 * The Note is MeetingMind's main object (docs/PLAN_V1.md §2). A recording is one of the things a
 * note can contain, not the other way round, so a note can exist with no recording at all.
 */
data class Note(
    val id: String,
    val title: String,
    /** Which workflow shaped this note. Workflows are [RecordingType]s (PLAN_V1 §3). */
    val workflow: RecordingType,
    val notebookId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** When the thing the note is about happened, e.g. the Sunday a sermon was preached. */
    val eventDate: Long?,
    val pinned: Boolean,
    /** Private notes are left out of share and export unless the user asks for them. */
    val isPrivate: Boolean,
    val status: NoteStatus,
    val answeredAt: Long?,
    /** Workflow fields (speaker, church, translation, participants…), as flat string pairs. */
    val metadata: Map<String, String>,
    val archivedAt: Long? = null,
    /** Plain text of every text block, kept for search and list previews. */
    val plainText: String = ""
)

/** Only prayer requests use anything but [OPEN] today. */
enum class NoteStatus { OPEN, ANSWERED }

enum class NotebookSpace(val displayName: String) {
    WORK("Work"),
    LEARNING("Learning"),
    FAITH("Faith"),
    PERSONAL("Personal")
}

data class Notebook(
    val id: String,
    val name: String,
    val space: NotebookSpace,
    val colorHex: String?,
    val icon: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null,
    val sortOrder: Int = 0
)

enum class NoteBlockType {
    PARAGRAPH, HEADING_1, HEADING_2, HEADING_3,
    BULLET, NUMBERED, CHECKLIST, QUOTE, DIVIDER,
    SCRIPTURE, IMAGE, VIDEO, AUDIO, RECORDING, TRANSCRIPT_EXCERPT, NOTE_LINK;

    /** Whether the block holds editable text. The others are carried by their payload. */
    val isText: Boolean
        get() = this in TEXT_TYPES

    val isListItem: Boolean
        get() = this == BULLET || this == NUMBERED || this == CHECKLIST

    companion object {
        private val TEXT_TYPES = setOf(PARAGRAPH, HEADING_1, HEADING_2, HEADING_3, BULLET, NUMBERED, CHECKLIST, QUOTE)
    }
}

/**
 * Where a block's words came from. The UI marks anything that isn't [USER] so the user always
 * knows what they wrote and what was produced for them (PLAN_V1 §7, the AI boundary).
 */
enum class BlockSource { USER, AI, TRANSCRIPT, IMPORTED, SCRIPTURE }

data class NoteBlock(
    val id: String,
    val noteId: String,
    val position: Int,
    val type: NoteBlockType,
    val content: RichText = RichText.EMPTY,
    /** Type-specific data: an attachment id, a meeting id, a scripture reference, a note id. */
    val payload: Map<String, String> = emptyMap(),
    val source: BlockSource = BlockSource.USER,
    /** Transcript segments this block was drawn from, so it can jump back to the audio. */
    val sourceSegmentIds: List<String> = emptyList(),
    /** Which template section this block belongs to, e.g. "key_points". Null for free blocks. */
    val sectionKey: String? = null,
    val isUserEdited: Boolean = false,
    /** List nesting depth, 0 for top level. */
    val indent: Int = 0,
    val checked: Boolean = false
) {
    companion object {
        const val PAYLOAD_MEETING_ID = "meetingId"
        const val PAYLOAD_ATTACHMENT_ID = "attachmentId"
        const val PAYLOAD_NOTE_ID = "noteId"
        const val PAYLOAD_SCRIPTURE_REF_ID = "scriptureRefId"
        const val PAYLOAD_START_MS = "startMs"
        const val PAYLOAD_END_MS = "endMs"
        const val PAYLOAD_SPEAKER = "speaker"
    }
}

enum class AttachmentKind { IMAGE, VIDEO, AUDIO, FILE }

data class Attachment(
    val id: String,
    val noteId: String,
    val kind: AttachmentKind,
    /** Absolute path inside app storage. Attachments are always copied in, never referenced. */
    val path: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val caption: String? = null,
    val createdAt: Long
)

data class Tag(val id: String, val name: String)

enum class NoteLinkKind {
    RELATED,
    /** A prayer request's later update. */
    UPDATE_OF,
    /** An answered prayer pointing at its request. */
    ANSWERS,
    /** A testimony pointing at the answered prayer it tells. */
    TESTIMONY_OF
}

data class NoteLink(
    val id: String,
    val fromNoteId: String,
    val toNoteId: String,
    val kind: NoteLinkKind,
    val createdAt: Long
)

enum class ScriptureOrigin {
    /** Found by the deterministic parser in a transcript. */
    DETECTED,
    USER,
    /** Proposed by a model with a cited segment and re-validated by the parser. */
    AI
}

/**
 * A Bible reference. Only the reference is stored, never the verse text: publishers license
 * text for on-demand display, not storage (PLAN_V1 §6).
 */
data class ScriptureRef(
    val id: String,
    val noteId: String,
    val blockId: String?,
    /** USFM book code, e.g. "JHN", "1CO". */
    val bookUsfm: String,
    val chapter: Int,
    /** Null for a whole chapter. */
    val verseStart: Int?,
    val verseEnd: Int?,
    /** YouVersion Bible id. Null means the user's default translation. */
    val versionId: Int?,
    val origin: ScriptureOrigin,
    val meetingId: String? = null,
    val segmentId: String? = null,
    val startMs: Long? = null,
    val createdAt: Long
)

data class ScriptureCollection(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class ScriptureCollectionItem(
    val id: String,
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

/** A note together with everything the editor and exporters need. */
data class NoteDocument(
    val note: Note,
    val blocks: List<NoteBlock>,
    val attachments: List<Attachment>,
    val tags: List<Tag>,
    val scriptureRefs: List<ScriptureRef>
)
