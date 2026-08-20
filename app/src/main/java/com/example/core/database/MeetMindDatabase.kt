package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        MeetingEntity::class,
        TranscriptSegmentEntity::class,
        SpeakerEntity::class,
        ActionItemEntity::class,
        DecisionEntity::class,
        QuestionEntity::class,
        FollowUpEntity::class,
        TopicEntity::class,
        EmbeddingEntity::class,
        AiModelEntity::class,
        ProcessingJobEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MeetMindDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun decisionDao(): DecisionDao
    abstract fun questionDao(): QuestionDao
    abstract fun followUpDao(): FollowUpDao
    abstract fun topicDao(): TopicDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun processingJobDao(): ProcessingJobDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: MeetMindDatabase? = null

        /**
         * Adds real speaker-diarization and meeting-intelligence columns/tables (Phase 2).
         * `action_items`/`decisions`/`questions` are rebuilt (never populated by any real code
         * path before this phase — [com.example.ai.llm.UnavailableMeetingIntelligenceEngine] was
         * always the default, so these tables are guaranteed empty; no data-preserving INSERT is
         * needed). `meetings`/`transcript_segments`/`speakers` keep their real on-device data —
         * `speakers` only gains columns, nothing is dropped or rebuilt.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE speakers ADD COLUMN speakerIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE speakers ADD COLUMN confidence REAL")

                db.execSQL("DROP TABLE IF EXISTS action_items")
                db.execSQL(
                    """
                    CREATE TABLE action_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        meetingId TEXT NOT NULL,
                        task TEXT NOT NULL,
                        assigneeSpeakerId TEXT,
                        assigneeName TEXT,
                        deadline TEXT,
                        confidence REAL,
                        isCompleted INTEGER NOT NULL,
                        sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]',
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_action_items_meetingId ON action_items(meetingId)")

                db.execSQL("DROP TABLE IF EXISTS decisions")
                db.execSQL(
                    """
                    CREATE TABLE decisions (
                        id TEXT NOT NULL PRIMARY KEY,
                        meetingId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'DISCUSSION',
                        confidence REAL,
                        sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]',
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_decisions_meetingId ON decisions(meetingId)")

                db.execSQL("DROP TABLE IF EXISTS questions")
                db.execSQL(
                    """
                    CREATE TABLE questions (
                        id TEXT NOT NULL PRIMARY KEY,
                        meetingId TEXT NOT NULL,
                        text TEXT NOT NULL,
                        askedBySpeakerId TEXT,
                        resolved INTEGER NOT NULL,
                        answer TEXT,
                        sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]',
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_meetingId ON questions(meetingId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS follow_ups (
                        id TEXT NOT NULL PRIMARY KEY,
                        meetingId TEXT NOT NULL,
                        description TEXT NOT NULL,
                        ownerSpeakerId TEXT,
                        deadline TEXT,
                        sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]',
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_follow_ups_meetingId ON follow_ups(meetingId)")

                db.execSQL("ALTER TABLE ai_models ADD COLUMN contextLengthTokens INTEGER")
                db.execSQL("ALTER TABLE processing_jobs ADD COLUMN stage TEXT NOT NULL DEFAULT 'IDLE'")
            }
        }

        fun getInstance(context: Context): MeetMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeetMindDatabase::class.java,
                    "meetmind_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
