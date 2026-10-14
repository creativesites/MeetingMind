package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One mark the person made in one recording (schema 25). Owned by its recording: the foreign key
 * cascades only when that recording is hard-deleted. Deleting a note never reaches here, because
 * a note does not own its recordings ([MeetingEntity.noteId] is deliberately not a foreign key).
 *
 * [timestampMs] is the recording's own logical clock, never a note-wide position. [kind] is the
 * stored [com.craftflowtechnologies.meetingmind.core.work.MarkKind] name, kept as text so a value
 * written by a newer build survives an older reader.
 */
@Entity(
    tableName = "recording_markers",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId", "timestampMs"]), Index(value = ["meetingId", "sequence"])]
)
data class RecordingMarkerEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    /** Insertion order within this recording. Ties on [timestampMs] are broken by this. */
    val sequence: Long,
    val timestampMs: Long,
    val kind: String,
    val text: String?,
    val sectionKey: String? = null,
    val resolvedSegmentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** False for a mark carried over from a legacy note-level list, true for one tapped live. */
    val createdDuringRecording: Boolean = true
)

/**
 * A legacy mark that could not be assigned to a recording without guessing (schema 25): it sat
 * on a note with several recordings, or with none, or it was malformed. Kept verbatim in
 * [rawJson] until the person assigns it. Never reconciled, never placed in a section.
 *
 * [status] is one of UNASSIGNED (note has several recordings), NO_RECORDING (note has none),
 * MALFORMED (the entry or its list could not be read).
 */
@Entity(
    tableName = "legacy_note_marks",
    indices = [Index(value = ["noteId"])]
)
data class LegacyNoteMarkEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val ordinal: Int,
    val status: String,
    val rawKind: String?,
    val atMs: Long?,
    val text: String?,
    val rawJson: String,
    val createdAt: Long
)

@Dao
interface RecordingMarkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(markers: List<RecordingMarkerEntity>)

    @Query("SELECT * FROM recording_markers WHERE meetingId = :meetingId ORDER BY timestampMs ASC, sequence ASC")
    suspend fun forMeeting(meetingId: String): List<RecordingMarkerEntity>

    /** The next insertion number for this recording: one past the highest already stored. */
    @Query("SELECT COALESCE(MAX(sequence) + 1, 0) FROM recording_markers WHERE meetingId = :meetingId")
    suspend fun nextSequence(meetingId: String): Long

    @Query("DELETE FROM recording_markers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LegacyNoteMarkDao {
    @Query("SELECT * FROM legacy_note_marks WHERE noteId = :noteId ORDER BY ordinal ASC")
    suspend fun forNote(noteId: String): List<LegacyNoteMarkEntity>
}
