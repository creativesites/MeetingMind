package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Schema 19 (docs/PLAN_PROFESSIONAL.md D6): what a recording's paragraphs say, kind by kind, with
 * evidence. One row per paragraph a signal cites, so a paragraph can be filtered by what it holds.
 */
@Entity(
    tableName = "segment_signals",
    indices = [Index(value = ["meetingId"]), Index(value = ["meetingId", "kind"]), Index(value = ["segmentId"])]
)
data class SegmentSignalEntity(
    @PrimaryKey val id: String,
    val segmentId: String,
    val meetingId: String,
    /** [com.craftflowtechnologies.meetingmind.core.work.ItemKind] name. */
    val kind: String,
    /** The speaker or person the signal is about, when known. */
    val entityId: String? = null,
    /** A date for DEADLINE, a number for METRIC, PROPOSED for a decision not yet made, or details as JSON. */
    val value: String? = null,
    val confidence: Float,
    /** What was said, in the signal's words. */
    @ColumnInfo(defaultValue = "") val text: String = "",
    /** Groups the rows of one signal that cites several paragraphs. */
    @ColumnInfo(defaultValue = "") val signalId: String = ""
)

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<SegmentSignalEntity>)

    @Query("SELECT * FROM segment_signals WHERE meetingId = :meetingId ORDER BY signalId, id")
    suspend fun forMeeting(meetingId: String): List<SegmentSignalEntity>

    @Query("SELECT * FROM segment_signals WHERE meetingId = :meetingId")
    fun observeForMeeting(meetingId: String): Flow<List<SegmentSignalEntity>>

    @Query("DELETE FROM segment_signals WHERE meetingId = :meetingId")
    suspend fun deleteForMeeting(meetingId: String)

    @Query("SELECT COUNT(*) FROM segment_signals")
    suspend fun count(): Int
}
