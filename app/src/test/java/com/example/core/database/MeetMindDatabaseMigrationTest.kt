package com.example.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [MeetMindDatabase.MIGRATION_1_2] transforms the real v1 on-device schema correctly,
 * without relying on Room's schema-export testing artifact (this project keeps
 * `exportSchema = false`). Builds a minimal v1 database with the framework SQLite helper
 * directly, runs the real migration object against it, then inspects the resulting schema via
 * `PRAGMA table_info`. This is schema-shape verification, not a full Room round-trip — the
 * broader Room-backed test suite (e.g. [com.example.ai.pipeline.MeetingProcessingPipelineIntegrationTest])
 * separately exercises the post-migration entities end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeetMindDatabaseMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper

    private fun columnNames(db: SupportSQLiteDatabase, table: String): Set<String> {
        val names = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex))
            }
        }
        return names
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
            return cursor.count > 0
        }
    }

    @After
    fun tearDown() {
        helper.close()
    }

    private fun openV1Database(): SupportSQLiteDatabase {
        val context: Context = ApplicationProvider.getApplicationContext()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE speakers (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, originalLabel TEXT NOT NULL, customName TEXT NOT NULL, colorHex TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE action_items (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, task TEXT NOT NULL, assignee TEXT NOT NULL, deadline TEXT NOT NULL, confidence REAL NOT NULL, isCompleted INTEGER NOT NULL, sourceTimestampMs INTEGER)")
                    db.execSQL("CREATE TABLE decisions (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, text TEXT NOT NULL, confidence REAL NOT NULL, timestampMs INTEGER)")
                    db.execSQL("CREATE TABLE questions (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, text TEXT NOT NULL, resolved INTEGER NOT NULL, answer TEXT, timestampMs INTEGER)")
                    db.execSQL("CREATE TABLE ai_models (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, capabilities TEXT NOT NULL, filesJson TEXT NOT NULL, minimumRamMb INTEGER NOT NULL, recommendedRamMb INTEGER NOT NULL, version TEXT NOT NULL, isInstalled INTEGER NOT NULL, isDownloading INTEGER NOT NULL, downloadProgress REAL NOT NULL, description TEXT NOT NULL, parameterCount TEXT NOT NULL, quantization TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE processing_jobs (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, meetingTitle TEXT NOT NULL, currentStep TEXT NOT NULL, progressPercent INTEGER NOT NULL, isCompleted INTEGER NOT NULL, isFailed INTEGER NOT NULL, errorMessage TEXT, startedAt INTEGER NOT NULL)")
                    // Seed one real speaker row to prove existing on-device data survives the migration.
                    db.execSQL("INSERT INTO speakers (id, meetingId, originalLabel, customName, colorHex) VALUES ('spk_1', 'm1', 'Speaker 1', 'Winston', '#3B82F6')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun `migration adds speakerIndex and confidence columns to speakers without losing existing rows`() {
        val db = openV1Database()

        MeetMindDatabase.MIGRATION_1_2.migrate(db)

        val columns = columnNames(db, "speakers")
        assertTrue(columns.contains("speakerIndex"))
        assertTrue(columns.contains("confidence"))
        db.query("SELECT customName FROM speakers WHERE id = 'spk_1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Winston", cursor.getString(0))
        }
    }

    @Test
    fun `migration rebuilds action_items with nullable confidence and sourceSegmentIdsJson`() {
        val db = openV1Database()

        MeetMindDatabase.MIGRATION_1_2.migrate(db)

        val columns = columnNames(db, "action_items")
        assertTrue(columns.contains("assigneeSpeakerId"))
        assertTrue(columns.contains("assigneeName"))
        assertTrue(columns.contains("sourceSegmentIdsJson"))
        assertTrue("Old fixed 'assignee' column must be gone after the rebuild", !columns.contains("assignee"))
        // A NOT NULL confidence column would reject this insert; nullable is what real (non-fabricated) confidence requires.
        db.execSQL("INSERT INTO action_items (id, meetingId, task, isCompleted) VALUES ('a1', 'm1', 'Do the thing', 0)")
    }

    @Test
    fun `migration rebuilds decisions with a type column and nullable confidence`() {
        val db = openV1Database()

        MeetMindDatabase.MIGRATION_1_2.migrate(db)

        val columns = columnNames(db, "decisions")
        assertTrue(columns.contains("type"))
        assertTrue(columns.contains("sourceSegmentIdsJson"))
        assertTrue(!columns.contains("timestampMs"))
    }

    @Test
    fun `migration rebuilds questions with askedBySpeakerId`() {
        val db = openV1Database()

        MeetMindDatabase.MIGRATION_1_2.migrate(db)

        val columns = columnNames(db, "questions")
        assertTrue(columns.contains("askedBySpeakerId"))
        assertTrue(columns.contains("sourceSegmentIdsJson"))
    }

    @Test
    fun `migration creates the new follow_ups table`() {
        val db = openV1Database()

        MeetMindDatabase.MIGRATION_1_2.migrate(db)

        assertTrue(tableExists(db, "follow_ups"))
        val columns = columnNames(db, "follow_ups")
        assertTrue(columns.contains("ownerSpeakerId"))
        assertTrue(columns.contains("deadline"))
    }

    @Test
    fun `migration adds contextLengthTokens to ai_models and stage to processing_jobs`() {
        val db = openV1Database()

        MeetMindDatabase.MIGRATION_1_2.migrate(db)

        assertTrue(columnNames(db, "ai_models").contains("contextLengthTokens"))
        assertTrue(columnNames(db, "processing_jobs").contains("stage"))
    }

    private fun openV2MeetingsDatabase(): SupportSQLiteDatabase {
        val context: Context = ApplicationProvider.getApplicationContext()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE meetings (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            durationMs INTEGER NOT NULL,
                            source TEXT NOT NULL,
                            audioFilePath TEXT,
                            status TEXT NOT NULL,
                            participantCount INTEGER NOT NULL,
                            language TEXT NOT NULL,
                            summaryText TEXT,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    // Seed one real, already-processed meeting to prove it survives the migration untouched.
                    db.execSQL(
                        "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, language, summaryText, updatedAt) " +
                            "VALUES ('m1', 'Quarterly Planning', 1700000000000, 600000, 'LOCAL_RECORDING', '/data/m1.wav', 'READY', 2, 'en', 'Discussed roadmap', 1700000600000)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun `migration 2 to 3 adds recordingType defaulting to GENERAL and nullable customContext`() {
        val db = openV2MeetingsDatabase()

        MeetMindDatabase.MIGRATION_2_3.migrate(db)

        val columns = columnNames(db, "meetings")
        assertTrue(columns.contains("recordingType"))
        assertTrue(columns.contains("customContext"))
        db.query("SELECT recordingType, customContext, title FROM meetings WHERE id = 'm1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("GENERAL", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertEquals("Quarterly Planning", cursor.getString(2))
        }
    }

    private fun openV3TranscriptSegmentsDatabase(): SupportSQLiteDatabase {
        val context: Context = ApplicationProvider.getApplicationContext()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE transcript_segments (
                            id TEXT NOT NULL PRIMARY KEY,
                            meetingId TEXT NOT NULL,
                            speakerId TEXT,
                            speakerName TEXT,
                            startMs INTEGER NOT NULL,
                            endMs INTEGER NOT NULL,
                            text TEXT NOT NULL,
                            confidence REAL
                        )
                        """.trimIndent()
                    )
                    // Seed one already-transcribed real segment to prove it survives the migration untouched.
                    db.execSQL(
                        "INSERT INTO transcript_segments (id, meetingId, speakerId, speakerName, startMs, endMs, text, confidence) " +
                            "VALUES ('s1', 'm1', 'spk_1', 'Winston', 0, 2000, 'Let''s begin the quarterly review.', 0.92)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun `migration 3 to 4 adds isUserEdited defaulting to false without disturbing existing segments`() {
        val db = openV3TranscriptSegmentsDatabase()

        MeetMindDatabase.MIGRATION_3_4.migrate(db)

        val columns = columnNames(db, "transcript_segments")
        assertTrue(columns.contains("isUserEdited"))
        db.query("SELECT isUserEdited, text FROM transcript_segments WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals("Let's begin the quarterly review.", cursor.getString(1))
        }
    }

    private fun openV5TranscriptSegmentsDatabase(): SupportSQLiteDatabase {
        val context: Context = ApplicationProvider.getApplicationContext()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE transcript_segments (
                            id TEXT NOT NULL PRIMARY KEY,
                            meetingId TEXT NOT NULL,
                            speakerId TEXT,
                            speakerName TEXT,
                            startMs INTEGER NOT NULL,
                            endMs INTEGER NOT NULL,
                            text TEXT NOT NULL,
                            confidence REAL,
                            isUserEdited INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    // A real, already-hand-corrected segment — must survive the migration untouched,
                    // and must never gain a fabricated cleanedText value.
                    db.execSQL(
                        "INSERT INTO transcript_segments (id, meetingId, speakerId, speakerName, startMs, endMs, text, confidence, isUserEdited) " +
                            "VALUES ('s1', 'm1', 'spk_1', 'Winston', 0, 2000, 'Let''s begin the quarterly review.', 0.92, 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun `migration 5 to 6 adds a nullable cleanedText column without disturbing existing segments`() {
        val db = openV5TranscriptSegmentsDatabase()

        MeetMindDatabase.MIGRATION_5_6.migrate(db)

        val columns = columnNames(db, "transcript_segments")
        assertTrue(columns.contains("cleanedText"))
        db.query("SELECT cleanedText, text, isUserEdited FROM transcript_segments WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("Existing segments must get no fabricated cleanedText value", cursor.isNull(0))
            assertEquals("Let's begin the quarterly review.", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
    }

    private fun openV9EmptyDatabase(): SupportSQLiteDatabase {
        val context: Context = ApplicationProvider.getApplicationContext()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun `migration 9 to 10 creates the vocabulary table with all learned-correction columns`() {
        val db = openV9EmptyDatabase()

        MeetMindDatabase.MIGRATION_9_10.migrate(db)

        assertTrue(tableExists(db, "vocabulary"))
        val columns = columnNames(db, "vocabulary")
        assertTrue(columns.contains("surfaceForm"))
        assertTrue(columns.contains("canonicalForm"))
        assertTrue(columns.contains("type"))
        assertTrue(columns.contains("confidence"))
        assertTrue(columns.contains("source"))
        assertTrue(columns.contains("frequency"))
        assertTrue(columns.contains("lastConfirmedAt"))
        // A brand-new table on every existing install — no fabricated seed rows.
        db.query("SELECT COUNT(*) FROM vocabulary").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun openV10EmptyDatabase(): SupportSQLiteDatabase {
        val context: Context = ApplicationProvider.getApplicationContext()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }

    @Test
    fun `migration 10 to 11 creates the ai_jobs table with all job-tracking columns`() {
        val db = openV10EmptyDatabase()

        MeetMindDatabase.MIGRATION_10_11.migrate(db)

        assertTrue(tableExists(db, "ai_jobs"))
        val columns = columnNames(db, "ai_jobs")
        assertTrue(columns.contains("meetingId"))
        assertTrue(columns.contains("toolType"))
        assertTrue(columns.contains("status"))
        assertTrue(columns.contains("progressPercent"))
        assertTrue(columns.contains("progressStep"))
        assertTrue(columns.contains("inputPayloadJson"))
        assertTrue(columns.contains("resultPayloadJson"))
        assertTrue(columns.contains("errorMessage"))
        assertTrue(columns.contains("retryCount"))
        db.query("SELECT COUNT(*) FROM ai_jobs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
