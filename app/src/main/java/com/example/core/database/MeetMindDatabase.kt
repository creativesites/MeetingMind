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
        ChatMessageEntity::class,
        VocabularyEntity::class
    ],
    version = 10,
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
    abstract fun vocabularyDao(): VocabularyDao

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

        /**
         * Adds the Recording Type / custom AI focus context fields (Phase 3A) — existing meetings
         * default to GENERAL (no fabricated type is inferred for past recordings).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN recordingType TEXT NOT NULL DEFAULT 'GENERAL'")
                db.execSQL("ALTER TABLE meetings ADD COLUMN customContext TEXT")
            }
        }

        /**
         * Adds real transcript-editing support (Phase 3B): [TranscriptSegmentEntity.isUserEdited]
         * marks a segment a person has hand-corrected, defaulting to false for every existing
         * segment — nothing already transcribed is retroactively (and wrongly) flagged as edited.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcript_segments ADD COLUMN isUserEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds real speaker-count context (Phase 4): what the user actually told MeetingMind about
         * expected speakers, captured at recording/import time or via the before-processing prompt,
         * so it can be reused instead of asked for twice and so the pipeline can skip diarization
         * entirely for a confirmed single-speaker recording. Nullable with no default — existing
         * meetings correctly have no stated preference, never a fabricated one.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN speakerCountPreference INTEGER")
            }
        }

        /**
         * Adds [TranscriptSegmentEntity.cleanedText] (Intelligence Orchestration Layer, Stage B):
         * the cached output of [com.example.ai.pipeline.TranscriptCleanupEngine], kept separate
         * from the real ASR/user-edited [TranscriptSegmentEntity.text] it was derived from. Nullable
         * with no default — every existing segment correctly has no cached cleanup yet rather than
         * a fabricated one; it's computed lazily on next processing.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcript_segments ADD COLUMN cleanedText TEXT")
            }
        }

        /**
         * Adds [TranscriptSegmentEntity.sourceSegmentIdsJson] (Intelligence Orchestration Layer,
         * Stage B follow-up: transcript structure fix): the raw ASR fragment id(s) a persisted
         * paragraph was merged from, so provenance survives even though individual raw fragments
         * are never persisted as their own rows. Defaults every existing row to `'[]'` — an honest
         * "unknown, this paragraph predates provenance tracking" rather than a fabricated guess.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcript_segments ADD COLUMN sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * Adds [TranscriptSegmentEntity.wordsJson] (Recording page redesign, phase 5): real
         * per-word timestamps from sherpa-onnx's `OfflineRecognizerResult.tokens`/`.timestamps`
         * (both genuinely present — see ai/asr/SherpaParakeetSpeechRecognizer), enabling tap-to-
         * seek at word granularity. Defaults every existing row to `'[]'` — an honest "this
         * segment predates word-level timing" rather than a fabricated guess. Deliberately not a
         * confidence field: sherpa-onnx's result type has no score/confidence at all, verified
         * against the actual v1.13.6 Kotlin API, so this phase does not attempt one.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transcript_segments ADD COLUMN wordsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * Adds [ChatMessageEntity.readSegmentCount] (Recording page redesign, phase 7): how many
         * transcript segments were actually retrieved as context for an Ask AI answer — the
         * "read N of M segments" honesty line. Also the phase that fixed
         * [com.example.core.domain.AskMeetingUseCase] always passing an empty retrieval list.
         * Defaults every existing row to 0 — an honest "unknown, this answer predates retrieval
         * tracking" rather than a fabricated count.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN readSegmentCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the `vocabulary` table (Phase 15 §4): learned surfaceForm -> canonicalForm
         * corrections, first populated from Replace All. A brand-new table, so nothing to
         * preserve — every existing install correctly starts with zero learned terms rather than
         * a fabricated seed list.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vocabulary (
                        id TEXT NOT NULL PRIMARY KEY,
                        surfaceForm TEXT NOT NULL,
                        canonicalForm TEXT NOT NULL,
                        type TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        source TEXT NOT NULL,
                        frequency INTEGER NOT NULL,
                        lastConfirmedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vocabulary_surfaceForm ON vocabulary(surfaceForm)")
            }
        }

        fun getInstance(context: Context): MeetMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeetMindDatabase::class.java,
                    "meetmind_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
