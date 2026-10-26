package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 27 → 28: title provenance. Adds `meetings.titleSource`, defaulting every existing row to `AI`
 * so an upgrade never mass-retitles a library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration27To28Test {
    private lateinit var context: Context
    private val dbName = "migration-27-28-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 27)
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

    private fun openMigrated(): MeetMindDatabase =
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { migrated = it }

    @Test
    fun existingRowsGetTitleSourceAiAndKeepTheirTitle() {
        val db = openMigrated()
        db.openHelper.writableDatabase
        db.openHelper.readableDatabase.query("SELECT title, titleSource FROM meetings WHERE id = 'm1'").use { c ->
            c.moveToFirst()
            assertEquals("Q3 Planning", c.getString(0))
            assertEquals("AI", c.getString(1))
        }
    }

    @Test
    fun titleSourceColumnIsPresentAfterMigration() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query("PRAGMA table_info(meetings)").use { c ->
            val nameIndex = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) {
                if (c.getString(nameIndex) == "titleSource") found = true
            }
            assertEquals(true, found)
        }
    }

    @Test
    fun theDatabaseOpensAtSchemaTwentyEightOrLater() {
        org.junit.Assert.assertTrue(MeetMindDatabase.VERSION >= 28)
        val db = openMigrated()
        assertEquals(MeetMindDatabase.VERSION, db.openHelper.readableDatabase.version)
    }
}
