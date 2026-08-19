package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MeetingEntity::class,
        TranscriptSegmentEntity::class,
        SpeakerEntity::class,
        ActionItemEntity::class,
        DecisionEntity::class,
        QuestionEntity::class,
        TopicEntity::class,
        EmbeddingEntity::class,
        AiModelEntity::class,
        ProcessingJobEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MeetMindDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun decisionDao(): DecisionDao
    abstract fun questionDao(): QuestionDao
    abstract fun topicDao(): TopicDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun processingJobDao(): ProcessingJobDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: MeetMindDatabase? = null

        fun getInstance(context: Context): MeetMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeetMindDatabase::class.java,
                    "meetmind_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
