package com.example.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The work schema (docs/PLAN_PROFESSIONAL.md §5), added in database version 15 by
 * [MeetMindDatabase.MIGRATION_14_15]. The SQL there must match these declarations exactly; Room
 * checks on open, and `WorkMigrationTest` opens a migrated database to prove it.
 *
 * None of this is Professional-only. Faith uses people (preachers, a small group), items (study
 * questions, prayer follow-ups) and project notebooks (a sermon series) the same way.
 */

/**
 * One thing that came out of a conversation or was written down: a task, a decision or a question.
 * Replaces the four per-meeting tables (`action_items`, `decisions`, `questions`, `follow_ups`)
 * with one list that can be asked "what do I owe, what am I waiting on" across everything.
 *
 * [meetingId] is null for an item written by hand outside a recording.
 */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["meetingId"]),
        Index(value = ["noteId"]),
        Index(value = ["kind", "status"]),
        Index(value = ["ownerPersonId"]),
        Index(value = ["dueAt"])
    ]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val meetingId: String?,
    val noteId: String?,
    /** [com.example.core.work.ItemKind] name. */
    val kind: String,
    /** A finer kind: a decision's [com.example.core.model.DecisionType], or FOLLOW_UP for a task. */
    val subtype: String?,
    val text: String,
    /** [com.example.core.work.ItemStatus] name. */
    val status: String,
    /** The speaker who owns it (or asked it). The shown name is always looked up, never copied. */
    val ownerSpeakerId: String?,
    /** The person who owns it, when known directly rather than through a speaker. */
    val ownerPersonId: String?,
    /** The owner's name as extracted or typed: shown only when neither id resolves. */
    val ownerName: String?,
    /** Start of the due day, local time. Null when there's no date or it couldn't be read. */
    val dueAt: Long?,
    /** The deadline in the words it was given ("by Friday"). */
    val dueText: String?,
    val answer: String?,
    val answeredAt: Long?,
    /** A notebook of kind PROJECT, copied from the note so project lists don't need a join. */
    val projectId: String?,
    /** [com.example.core.work.ItemSource] name. */
    val source: String,
    val confidence: Float?,
    /** False until the person has seen it in the Wrap-up or touched it. */
    val reviewed: Boolean,
    val sourceSegmentIdsJson: String,
    val sourceStartMs: Long?,
    /** For a decision: the later decision that replaced it. */
    val supersededById: String?,
    val metadataJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

/** An item with its owner's name as it is right now (docs/PLAN_PROFESSIONAL.md §5.5). */
data class ItemWithOwner(
    @Embedded val item: ItemEntity,
    val ownerDisplay: String?,
    val ownerIsSelf: Boolean?
)

/**
 * A person or an organisation. Built from MeetingMind's own history (named speakers, calendar
 * attendees, names typed as owners), never from the phone's contacts (PLAN_PROFESSIONAL.md §5.1).
 */
@Entity(
    tableName = "people",
    indices = [Index(value = ["name"]), Index(value = ["orgId"]), Index(value = ["kind"])]
)
data class PersonEntity(
    @PrimaryKey val id: String,
    /** PERSON or ORG. */
    val kind: String,
    val name: String,
    val aliasesJson: String,
    val emailsJson: String,
    val phonesJson: String,
    val orgId: String?,
    val role: String?,
    /** [com.example.core.work.Channel] name the person last used successfully with them. */
    val preferredChannel: String?,
    /** The app's own user. At most one row. */
    val isSelf: Boolean,
    val notes: String?,
    val confidential: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSeenAt: Long?
)

/** Who a note involves, and how. */
@Entity(
    tableName = "note_people",
    primaryKeys = ["noteId", "personId", "role"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["personId"])]
)
data class NotePersonCrossRef(
    val noteId: String,
    val personId: String,
    /** ATTENDEE, SPEAKER or MENTIONED. */
    val role: String,
    val createdAt: Long
)
