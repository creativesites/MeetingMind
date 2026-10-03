package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CircleDao {

    // --- Circles ---
    @Query("SELECT * FROM circles ORDER BY createdAt DESC")
    fun observeAllCircles(): Flow<List<CircleEntity>>

    @Query("SELECT * FROM circles WHERE id = :id")
    fun observeCircleById(id: String): Flow<CircleEntity?>

    @Query("SELECT * FROM circles WHERE id = :id")
    suspend fun getCircleById(id: String): CircleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircle(circle: CircleEntity)

    @Update
    suspend fun updateCircle(circle: CircleEntity)

    @Query("UPDATE circles SET memberCount = :count WHERE id = :id")
    suspend fun updateMemberCount(id: String, count: Int)

    @Query("DELETE FROM circles WHERE id = :id")
    suspend fun deleteCircleById(id: String)

    // --- Members ---
    @Query("SELECT * FROM circle_members WHERE circleId = :circleId ORDER BY joinedAt ASC")
    fun observeMembers(circleId: String): Flow<List<CircleMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: CircleMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<CircleMemberEntity>)

    @Query("DELETE FROM circle_members WHERE id = :memberId")
    suspend fun deleteMember(memberId: String)

    // --- Prayers ---
    @Query("SELECT * FROM circle_prayers WHERE circleId = :circleId ORDER BY isUrgent DESC, createdAt DESC")
    fun observePrayers(circleId: String): Flow<List<CirclePrayerEntity>>

    @Query("SELECT * FROM circle_prayers WHERE id = :id")
    suspend fun getPrayerById(id: String): CirclePrayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrayer(prayer: CirclePrayerEntity)

    @Query("UPDATE circle_prayers SET prayerCount = prayerCount + 1, prayedByMe = 1 WHERE id = :prayerId")
    suspend fun recordPrayerTap(prayerId: String)

    @Query("UPDATE circle_prayers SET status = 'ANSWERED', answeredAt = :answeredAt WHERE id = :prayerId")
    suspend fun markPrayerAnswered(prayerId: String, answeredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM circle_prayers WHERE id = :id")
    suspend fun deletePrayer(id: String)

    // --- Testimonies ---
    @Query("SELECT * FROM circle_testimonies WHERE circleId = :circleId ORDER BY createdAt DESC")
    fun observeTestimonies(circleId: String): Flow<List<CircleTestimonyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestimony(testimony: CircleTestimonyEntity)

    @Query("UPDATE circle_testimonies SET praiseCount = praiseCount + 1, praisedByMe = 1 WHERE id = :testimonyId")
    suspend fun recordPraiseTap(testimonyId: String)

    @Query("DELETE FROM circle_testimonies WHERE id = :id")
    suspend fun deleteTestimony(id: String)

    // --- Sermons ---
    @Query("SELECT * FROM circle_sermons WHERE circleId = :circleId ORDER BY createdAt DESC")
    fun observeSermons(circleId: String): Flow<List<CircleSermonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSermon(sermon: CircleSermonEntity)

    @Query("DELETE FROM circle_sermons WHERE id = :id")
    suspend fun deleteSermon(id: String)
}
