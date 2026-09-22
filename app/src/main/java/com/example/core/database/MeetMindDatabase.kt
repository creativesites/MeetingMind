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
        VocabularyEntity::class,
        AiJobEntity::class,
        NotebookEntity::class,
        NoteEntity::class,
        NoteBlockEntity::class,
        AttachmentEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteLinkEntity::class,
        ScriptureRefEntity::class,
        ScriptureCollectionEntity::class,
        ScriptureCollectionItemEntity::class
    ],
    version = 13,
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
    abstract fun aiJobDao(): AiJobDao
    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun scriptureDao(): ScriptureDao

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

        /**
         * Adds the `ai_jobs` table (Phase 15 §5): persisted background AI Tools runs, so a job
         * survives process death instead of being tied to a ViewModel's `viewModelScope`. A
         * brand-new table — nothing to preserve.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        meetingId TEXT NOT NULL,
                        toolType TEXT NOT NULL,
                        status TEXT NOT NULL,
                        progressPercent INTEGER NOT NULL DEFAULT 0,
                        progressStep TEXT NOT NULL DEFAULT '',
                        inputPayloadJson TEXT NOT NULL DEFAULT '{}',
                        resultPayloadJson TEXT,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(meetingId) REFERENCES meetings(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_jobs_meetingId ON ai_jobs(meetingId)")
            }
        }

        /**
         * Records how each meeting was actually processed (canonical-transcript overhaul).
         *
         * Every column is added with a default, so existing meetings migrate untouched and stay
         * fully readable: an older transcript reports `processingVersion = 0`, which is how a
         * later build can tell that it predates the word-centric pipeline and was therefore
         * produced by the code path that fragmented sentences — without having to guess from its
         * contents.
         *
         * No table is added for words or speaker turns. Words are already persisted inside
         * `transcript_segments.wordsJson`, which the overhaul extended to round-trip word ids,
         * speakers, attribution confidence and source engine; speaker turns are derivable from
         * those words. A second copy of the same data, with no reader, would be a schema to keep
         * in sync for nothing.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meetings ADD COLUMN processingProfile TEXT NOT NULL DEFAULT 'OFFLINE'")
                db.execSQL("ALTER TABLE meetings ADD COLUMN transcriptionEngine TEXT")
                db.execSQL("ALTER TABLE meetings ADD COLUMN transcriptionModelId TEXT")
                db.execSQL("ALTER TABLE meetings ADD COLUMN processingVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meetings ADD COLUMN qualityMetricsJson TEXT")
            }
        }

        /** The notebook every migrated recording's note is filed in. */
        const val DEFAULT_NOTEBOOK_ID = "notebook_my_notes"

        /**
         * Adds the notes schema (docs/PLAN_V1.md §2) and gives every existing recording a note.
         *
         * Nothing is moved or dropped. Each recording gets a note with the same title, type and
         * dates, holding a single RECORDING block that points back at it, and the recording is
         * linked to its note through the new `meetings.noteId`. All of it lands in a "My Notes"
         * notebook so the library is never empty after an upgrade.
         *
         * The CREATE statements must match the entities in NoteEntities.kt exactly; Room checks
         * on open, and `NoteSchemaTest` checks in CI.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                NOTES_SCHEMA_SQL.forEach { db.execSQL(it) }

                db.execSQL("ALTER TABLE meetings ADD COLUMN noteId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meetings_noteId ON meetings(noteId)")

                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder) " +
                        "VALUES (?, 'My Notes', 'PERSONAL', NULL, NULL, ?, ?, NULL, 0)",
                    arrayOf<Any>(DEFAULT_NOTEBOOK_ID, now, now)
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, eventDate,
                        pinned, isPrivate, status, answeredAt, metadataJson, archivedAt, plainText)
                    SELECT 'note_' || id, title, recordingType, ?, createdAt, updatedAt, createdAt,
                        0, CASE WHEN recordingType = 'JOURNAL' THEN 1 ELSE 0 END, 'OPEN', NULL, '{}', NULL,
                        COALESCE(summaryText, '')
                    FROM meetings
                    """.trimIndent(),
                    arrayOf<Any>(DEFAULT_NOTEBOOK_ID)
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO note_blocks (id, noteId, position, type, text, spans, payloadJson, source,
                        sourceSegmentIdsJson, sectionKey, isUserEdited, indent, checked, updatedAt)
                    SELECT 'block_rec_' || id, 'note_' || id, 0, 'RECORDING', '', '',
                        '{"meetingId":"' || REPLACE(id, '"', '') || '"}', 'TRANSCRIPT', '[]', 'recording', 0, 0, 0, updatedAt
                    FROM meetings
                    """.trimIndent()
                )
                db.execSQL("UPDATE meetings SET noteId = 'note_' || id")
            }
        }

        /** CREATE statements for the notes tables, in dependency order. */
        internal val NOTES_SCHEMA_SQL: List<String> = listOf(
            """CREATE TABLE IF NOT EXISTS `notebooks` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `space` TEXT NOT NULL,
                `colorHex` TEXT, `icon` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                `archivedAt` INTEGER, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
            """CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `workflow` TEXT NOT NULL,
                `notebookId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `eventDate` INTEGER,
                `pinned` INTEGER NOT NULL, `isPrivate` INTEGER NOT NULL, `status` TEXT NOT NULL, `answeredAt` INTEGER,
                `metadataJson` TEXT NOT NULL, `archivedAt` INTEGER, `plainText` TEXT NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`notebookId`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""",
            "CREATE INDEX IF NOT EXISTS `index_notes_notebookId` ON `notes` (`notebookId`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)",
            """CREATE TABLE IF NOT EXISTS `note_blocks` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `position` INTEGER NOT NULL,
                `type` TEXT NOT NULL, `text` TEXT NOT NULL, `spans` TEXT NOT NULL, `payloadJson` TEXT NOT NULL,
                `source` TEXT NOT NULL, `sourceSegmentIdsJson` TEXT NOT NULL, `sectionKey` TEXT,
                `isUserEdited` INTEGER NOT NULL, `indent` INTEGER NOT NULL, `checked` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE INDEX IF NOT EXISTS `index_note_blocks_noteId` ON `note_blocks` (`noteId`)",
            """CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `kind` TEXT NOT NULL,
                `path` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `width` INTEGER,
                `height` INTEGER, `durationMs` INTEGER, `caption` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE INDEX IF NOT EXISTS `index_attachments_noteId` ON `attachments` (`noteId`)",
            "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)",
            """CREATE TABLE IF NOT EXISTS `note_tags` (`noteId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`noteId`, `tagId`),
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE INDEX IF NOT EXISTS `index_note_tags_tagId` ON `note_tags` (`tagId`)",
            """CREATE TABLE IF NOT EXISTS `note_links` (`id` TEXT NOT NULL, `fromNoteId` TEXT NOT NULL, `toNoteId` TEXT NOT NULL,
                `kind` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`fromNoteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                FOREIGN KEY(`toNoteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE INDEX IF NOT EXISTS `index_note_links_fromNoteId` ON `note_links` (`fromNoteId`)",
            "CREATE INDEX IF NOT EXISTS `index_note_links_toNoteId` ON `note_links` (`toNoteId`)",
            """CREATE TABLE IF NOT EXISTS `scripture_refs` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `blockId` TEXT,
                `bookUsfm` TEXT NOT NULL, `chapter` INTEGER NOT NULL, `verseStart` INTEGER, `verseEnd` INTEGER,
                `versionId` INTEGER, `origin` TEXT NOT NULL, `meetingId` TEXT, `segmentId` TEXT, `startMs` INTEGER,
                `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE INDEX IF NOT EXISTS `index_scripture_refs_noteId` ON `scripture_refs` (`noteId`)",
            "CREATE INDEX IF NOT EXISTS `index_scripture_refs_bookUsfm_chapter` ON `scripture_refs` (`bookUsfm`, `chapter`)",
            """CREATE TABLE IF NOT EXISTS `scripture_collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
            """CREATE TABLE IF NOT EXISTS `scripture_collection_items` (`id` TEXT NOT NULL, `collectionId` TEXT NOT NULL,
                `bookUsfm` TEXT NOT NULL, `chapter` INTEGER NOT NULL, `verseStart` INTEGER, `verseEnd` INTEGER,
                `versionId` INTEGER, `comment` TEXT, `position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`collectionId`) REFERENCES `scripture_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
            "CREATE INDEX IF NOT EXISTS `index_scripture_collection_items_collectionId` ON `scripture_collection_items` (`collectionId`)"
        )

        fun getInstance(context: Context): MeetMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeetMindDatabase::class.java,
                    "meetmind_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Test-only seam: lets a Robolectric test swap in an in-memory database for code that,
         * like [com.example.ai.pipeline.AiToolWorker] and [com.example.ai.pipeline.MeetingProcessingWorker],
         * must resolve [getInstance] internally (WorkManager instantiates Workers itself, so there
         * is no constructor to inject a database into). Never called from production code.
         */
        @androidx.annotation.VisibleForTesting
        fun setInstanceForTest(database: MeetMindDatabase?) {
            INSTANCE = database
        }
    }
}
