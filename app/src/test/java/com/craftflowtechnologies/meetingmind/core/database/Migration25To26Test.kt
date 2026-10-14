package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 25 → 26: Circles v1 is retired. Its five tables are dropped and nothing else changes. Notes and meetings
 * written at schema 25 must survive intact, because v1 data never reached release builds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration25To26Test {
    private lateinit var context: Context
    private val dbName = "migration-25-26-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    private val circleTables = listOf("circles", "circle_members", "circle_prayers", "circle_testimonies", "circle_sermons")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 25)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, pinned, isPrivate, status, metadataJson, plainText) " +
                    "VALUES ('n1', 'Keep me', 'SERMON', NULL, 10, 10, 0, 0, 'OPEN', '{}', 'text')"
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
    fun theFiveCircleV1TablesExistBeforeTheMigration() {
        assertTrue(tableNames().containsAll(circleTables))
    }

    @Test
    fun theMigrationDropsOnlyTheCircleV1Tables() {
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }
        migrated!!.openHelper.writableDatabase // Room runs migrations on first open, not on build
        val after = tableNames()
        circleTables.forEach { assertFalse("$it should be gone", it in after) }
        assertTrue("notes must survive", "notes" in after)
        assertTrue("meetings must survive", "meetings" in after)
    }

    @Test
    fun theNoteWrittenAtSchemaTwentyFiveIsUnchanged() {
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }
        val title = migrated!!.openHelper.readableDatabase.query("SELECT title FROM notes WHERE id = 'n1'").use { c ->
            c.moveToFirst(); c.getString(0)
        }
        assertEquals("Keep me", title)
    }

    @Test
    fun theDatabaseOpensAtSchemaTwentySix() {
        assertEquals(26, MeetMindDatabase.VERSION)
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }
        assertEquals(26, migrated!!.openHelper.readableDatabase.version)
    }
}
