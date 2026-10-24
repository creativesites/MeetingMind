package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 26 → 27: Non-destructive transcript translation. Adds `transcript_translations` table.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration26To27Test {
    private lateinit var context: Context
    private val dbName = "migration-26-27-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 26)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, language, summaryText, updatedAt, recordingType, processingProfile, processingVersion, recordingOrder) " +
                    "VALUES ('m1', 'Q3 Planning', 1000, 60000, 'LOCAL_RECORDING', '/path/to/audio', 'READY', 2, 'en', 'Summary', 1000, 'GENERAL', 'OFFLINE', 0, 0)"
            )
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    private fun tableNames(): Set<String> = SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    @Test
    fun theMigrationAddsTranscriptTranslationsTable() {
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }
        migrated!!.openHelper.writableDatabase
        val after = tableNames()
        assertTrue("transcript_translations table must be created", "transcript_translations" in after)
        assertTrue("meetings table must survive", "meetings" in after)
    }

    @Test
    fun existingMeetingSurvivesMigrationIntact() {
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }
        val title = migrated!!.openHelper.readableDatabase.query("SELECT title FROM meetings WHERE id = 'm1'").use { c ->
            c.moveToFirst(); c.getString(0)
        }
        assertEquals("Q3 Planning", title)
    }

    @Test
    fun theDatabaseOpensAtSchemaTwentySeven() {
        // Schema 28 followed this one; opening from 26 still runs every later step.
        assertEquals(28, MeetMindDatabase.VERSION)
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }
        assertEquals(MeetMindDatabase.VERSION, migrated!!.openHelper.readableDatabase.version)
    }
}
