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

    @Query("UPDATE transcript_segments SET text = :newText, isUserEdited = 1 WHERE id = :segmentId")
    suspend fun updateSegmentText(segmentId: String, newText: String)

    @Query("DELETE FROM transcript_segments WHERE meetingId = :meetingId")
    suspend fun deleteSegmentsForMeeting(meetingId: String)
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
}

@Dao
interface ActionItemDao {
    @Query("SELECT * FROM action_items WHERE meetingId = :meetingId")
    fun getActionItemsForMeeting(meetingId: String): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE meetingId = :meetingId")
    suspend fun getActionItemsForMeetingDirect(meetingId: String): List<ActionItemEntity>

    @Query("SELECT * FROM action_items ORDER BY id DESC")
    fun getAllActionItems(): Flow<List<ActionItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionItems(items: List<ActionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionItem(item: ActionItemEntity)

    @Update
    suspend fun updateActionItem(item: ActionItemEntity)

    @Query("DELETE FROM action_items WHERE id = :id")
    suspend fun deleteActionItemById(id: String)

    @Query("DELETE FROM action_items WHERE meetingId = :meetingId")
    suspend fun deleteActionItemsForMeeting(meetingId: String)
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decisions WHERE meetingId = :meetingId")
    fun getDecisionsForMeeting(meetingId: String): Flow<List<DecisionEntity>>

    @Query("SELECT * FROM decisions WHERE meetingId = :meetingId")
    suspend fun getDecisionsForMeetingDirect(meetingId: String): List<DecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecisions(decisions: List<DecisionEntity>)

    @Query("DELETE FROM decisions WHERE meetingId = :meetingId")
    suspend fun deleteDecisionsForMeeting(meetingId: String)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE meetingId = :meetingId")
    fun getQuestionsForMeeting(meetingId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE meetingId = :meetingId")
    suspend fun getQuestionsForMeetingDirect(meetingId: String): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE meetingId = :meetingId")
    suspend fun deleteQuestionsForMeeting(meetingId: String)
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
interface FollowUpDao {
    @Query("SELECT * FROM follow_ups WHERE meetingId = :meetingId")
    fun getFollowUpsForMeeting(meetingId: String): Flow<List<FollowUpEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUps(followUps: List<FollowUpEntity>)

    @Query("DELETE FROM follow_ups WHERE meetingId = :meetingId")
    suspend fun deleteFollowUpsForMeeting(meetingId: String)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE meetingId = :meetingId")
    suspend fun deleteMessagesForMeeting(meetingId: String)
}
