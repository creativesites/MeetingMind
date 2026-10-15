package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Schema 24: "My creations", the gallery of Create cards. Scripture text is deliberately not a
 * column: only the reference and the translation id are kept, and the text is fetched again from
 * the Bible library when a card is opened or exported.
 */
@Entity(tableName = "create_cards", indices = [Index(value = ["updatedAt"]), Index(value = ["pinned"])])
data class CreateCardEntity(
    @PrimaryKey val id: String,
    /** A CreateSourceKind name. */
    val source: String,
    val sourceText: String,
    val sourceRef: String? = null,
    /** A CreateVibe name. */
    val vibe: String,
    val text: String,
    val scriptureRef: String? = null,
    val scriptureVersionId: Int? = null,
    /** A CreateFormat name. */
    val format: String,
    /** The CreateDesign as JSON. */
    val designJson: String,
    /** Older versions of the words, as JSON, newest last. */
    val versionsJson: String,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface CreateCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(card: CreateCardEntity)

    @Query("SELECT * FROM create_cards WHERE id = :id")
    suspend fun get(id: String): CreateCardEntity?

    /** Pinned first, then newest. Bounded: the gallery shows the latest 200. */
    @Query("SELECT * FROM create_cards ORDER BY pinned DESC, updatedAt DESC LIMIT 200")
    fun observeAll(): Flow<List<CreateCardEntity>>

    @Query("SELECT * FROM create_cards ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CreateCardEntity>>

    @Query("SELECT COUNT(*) FROM create_cards")
    fun observeCount(): Flow<Int>

    @Query("UPDATE create_cards SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM create_cards WHERE id = :id")
    suspend fun delete(id: String)
}
