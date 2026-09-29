package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    fun getAllMeetings(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getMeetingByIdFlow(id: String): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    suspend fun getAllMeetingsDirect(): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingById(id: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchMeetingsByTitle(query: String): Flow<List<MeetingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: MeetingEntity)

    @Update
    suspend fun updateMeeting(meeting: MeetingEntity)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteMeetingById(id: String)

    @Query("DELETE FROM meetings")
    suspend fun deleteAllMeetings()

    @Query("SELECT COUNT(*) FROM meetings")
    fun getMeetingCountFlow(): Flow<Int>

    @Query("SELECT * FROM meetings WHERE status = :status")
    suspend fun getMeetingsWithStatus(status: String): List<MeetingEntity>
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId ORDER BY startMs ASC")
    fun getSegmentsForMeeting(meetingId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId ORDER BY startMs ASC")
    suspend fun getSegmentsForMeetingDirect(meetingId: String): List<TranscriptSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("SELECT * FROM transcript_segments WHERE text LIKE '%' || :query || '%'")
    suspend fun searchTranscriptSegments(query: String): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM transcript_segments WHERE id = :segmentId")
    suspend fun getSegmentById(segmentId: String): TranscriptSegmentEntity?

    @Query("UPDATE transcript_segments SET speakerName = :newName WHERE meetingId = :meetingId AND speakerId = :speakerId")
    suspend fun updateSpeakerName(meetingId: String, speakerId: String, newName: String)

    // Clears cleanedText along with the edit: a cached cleanup of the text being replaced is
    // stale the instant the user's correction lands, and a stale cached value must never keep
    // being shown/used in place of the fresh edit. wordsJson is cleared for the same reason — the
    // real per-word timestamps only ever corresponded to the pre-edit ASR text.
    @Query("UPDATE transcript_segments SET text = :newText, isUserEdited = 1, cleanedText = NULL, wordsJson = '[]' WHERE id = :segmentId")
    suspend fun updateSegmentText(segmentId: String, newText: String)

    // Never touches a segment the user has hand-corrected — isUserEdited = 1 always wins over a
    // cleanup pass, no matter when that pass runs relative to the edit.
    @Query("UPDATE transcript_segments SET cleanedText = :cleanedText WHERE id = :segmentId AND isUserEdited = 0")
    suspend fun updateCleanedText(segmentId: String, cleanedText: String?)

    @Query("DELETE FROM transcript_segments WHERE meetingId = :meetingId")
    suspend fun deleteSegmentsForMeeting(meetingId: String)

    @Query("DELETE FROM transcript_segments WHERE id = :segmentId")
    suspend fun deleteSegmentById(segmentId: String)

    // Reassigning a segment's speaker is a hand correction like any other edit — isUserEdited=1
    // for the same reason updateSegmentText sets it: a later cleanup/AI pass must never silently
    // overwrite it.
    @Query("UPDATE transcript_segments SET speakerId = :speakerId, speakerName = :speakerName, isUserEdited = 1 WHERE id = :segmentId")
    suspend fun reassignSegmentSpeaker(segmentId: String, speakerId: String?, speakerName: String?)
}

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speakers WHERE meetingId = :meetingId")
    fun getSpeakersForMeeting(meetingId: String): Flow<List<SpeakerEntity>>

    @Query("SELECT * FROM speakers WHERE meetingId = :meetingId")
    suspend fun getSpeakersForMeetingDirect(meetingId: String): List<SpeakerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakers(speakers: List<SpeakerEntity>)

    @Update
    suspend fun updateSpeaker(speaker: SpeakerEntity)

    @Query("DELETE FROM speakers WHERE id = :speakerId")
    suspend fun deleteSpeakerById(speakerId: String)
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics WHERE meetingId = :meetingId")
    fun getTopicsForMeeting(meetingId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE meetingId = :meetingId")
    suspend fun getTopicsForMeetingDirect(meetingId: String): List<TopicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Query("DELETE FROM topics WHERE meetingId = :meetingId")
    suspend fun deleteTopicsForMeeting(meetingId: String)
}

@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM embeddings WHERE meetingId = :meetingId")
    suspend fun getEmbeddingsForMeeting(meetingId: String): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings")
    suspend fun getAllEmbeddings(): List<EmbeddingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<EmbeddingEntity>)

    @Query("DELETE FROM embeddings WHERE meetingId = :meetingId")
    suspend fun deleteEmbeddingsForMeeting(meetingId: String)
}

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models")
    fun getAllModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE id = :id")
    suspend fun getModelById(id: String): AiModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AiModelEntity>)

    @Update
    suspend fun updateModel(model: AiModelEntity)
}

@Dao
interface ProcessingJobDao {
    @Query("SELECT * FROM processing_jobs ORDER BY startedAt DESC")
    fun getAllJobs(): Flow<List<ProcessingJobEntity>>

    @Query("SELECT * FROM processing_jobs WHERE isCompleted = 0 AND isFailed = 0")
    fun getActiveJobs(): Flow<List<ProcessingJobEntity>>

    @Query("SELECT * FROM processing_jobs WHERE meetingId = :meetingId")
    fun getJobForMeeting(meetingId: String): Flow<ProcessingJobEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateJob(job: ProcessingJobEntity)

    @Query("DELETE FROM processing_jobs WHERE id = :id")
    suspend fun deleteJob(id: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE meetingId = :meetingId ORDER BY timestamp ASC")
    fun getChatMessagesForMeeting(meetingId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE meetingId = :meetingId ORDER BY timestamp ASC")
    suspend fun getForMeetingDirect(meetingId: String): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE meetingId = :meetingId")
    suspend fun deleteMessagesForMeeting(meetingId: String)
}

@Dao
interface AiJobDao {
    @Query("SELECT * FROM ai_jobs WHERE id = :id")
    suspend fun getById(id: String): AiJobEntity?

    @Query("SELECT * FROM ai_jobs WHERE meetingId = :meetingId ORDER BY createdAt DESC")
    fun getForMeeting(meetingId: String): Flow<List<AiJobEntity>>

    @Query("SELECT * FROM ai_jobs WHERE meetingId = :meetingId")
    suspend fun getForMeetingDirect(meetingId: String): List<AiJobEntity>

    @Query("SELECT * FROM ai_jobs WHERE meetingId = :meetingId AND status IN ('QUEUED', 'RUNNING')")
    suspend fun getActiveForMeetingDirect(meetingId: String): List<AiJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(job: AiJobEntity)

    @Query("DELETE FROM ai_jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Clears a meeting's finished jobs in one statement — see
     * [com.example.core.repository.AiJobRepository.clearFinishedJobs] for why they are cleared. */
    @Query("DELETE FROM ai_jobs WHERE meetingId = :meetingId AND status IN ('SUCCEEDED', 'FAILED')")
    suspend fun deleteFinishedForMeeting(meetingId: String)
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary WHERE surfaceForm = :surfaceForm COLLATE NOCASE LIMIT 1")
    suspend fun findBySurfaceForm(surfaceForm: String): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: VocabularyEntity)

    @Query("SELECT * FROM vocabulary ORDER BY frequency DESC, lastConfirmedAt DESC")
    fun getAll(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary ORDER BY frequency DESC, lastConfirmedAt DESC")
    suspend fun getAllDirect(): List<VocabularyEntity>

    @Query("DELETE FROM vocabulary WHERE id = :id")
    suspend fun deleteById(id: String)
}
