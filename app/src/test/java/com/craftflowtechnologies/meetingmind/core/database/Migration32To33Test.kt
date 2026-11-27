package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 32 → 33. Two builds shipped a schema 32: this branch's (practices) and `release/1.0-paid-launch`'s
 * (six Personal tables, no practices). A phone on the release's 32 could not open this build
 * ("Room cannot verify the data integrity", found 214d8b9…). Both must now reach 33 with every
 * note kept, and the release's own tables left in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration32To33Test {
    private lateinit var context: Context
    private val dbName = "migration-32-33-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
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

    private fun insertTask(db: SQLiteDatabase) = db.execSQL(
        "INSERT INTO tasks (id, title, notes, kind, repeat, createdAt, updatedAt, waitingOn, space) " +
            "VALUES ('t_keep', 'Keep me', '', 'TASK', 'NONE', 1000, 1000, 0, 'PERSONAL')"
    )

    /** The release's 32: schema 31 plus the Personal tables, stamped 32 with the release's identity hash. */
    private fun createReleaseFlavour32() {
        ExportedSchema.create(context.getDatabasePath(dbName), 31)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            insertTask(db)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `personal_routines` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `whyDescription` TEXT NOT NULL DEFAULT '', " +
                    "`timeWindow` TEXT NOT NULL DEFAULT 'MORNING', `scheduledMinuteOfDay` INTEGER, `daysOfWeekMask` INTEGER NOT NULL DEFAULT 127, " +
                    "`reminderEnabled` INTEGER NOT NULL DEFAULT 0, `anchorCue` TEXT NOT NULL DEFAULT '', `soundscapeId` TEXT NOT NULL DEFAULT 'NONE', " +
                    "`linkedJourneyId` TEXT, `lifeChapterNotebookId` TEXT, `isArchived` INTEGER NOT NULL DEFAULT 0, `sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL("INSERT INTO personal_routines (id, title, createdAt, updatedAt) VALUES ('r1', 'Morning', 1, 1)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '214d8b9ba213110d72549dc391d3ed5a')")
            db.version = 32
        }
    }

    @Test
    fun releaseFlavour32OpensAtTheCurrentVersionWithNotesAndItsOwnTablesKept() = runBlocking {
        createReleaseFlavour32()
        val db = openMigrated()
        val sql = db.openHelper.readableDatabase
        assertEquals(MeetMindDatabase.VERSION, sql.version)
        sql.query("SELECT title FROM tasks WHERE id = 't_keep'").use { c -> assertTrue(c.moveToFirst()); assertEquals("Keep me", c.getString(0)) }
        sql.query("SELECT title FROM personal_routines WHERE id = 'r1'").use { c -> assertTrue(c.moveToFirst()); assertEquals("Morning", c.getString(0)) }
        // The practices tables it lacked now exist and work.
        db.taskDao().insertPracticeDay(PracticeDayEntity("p1", "2026-11-27", 1000))
        assertEquals(1, db.practiceDao().daysFor("p1").size)
    }

    @Test
    fun thisBranchs32OpensAtTheCurrentVersion() {
        ExportedSchema.create(context.getDatabasePath(dbName), 32)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { insertTask(it) }
        val db = openMigrated()
        val sql = db.openHelper.readableDatabase
        assertEquals(MeetMindDatabase.VERSION, sql.version)
        sql.query("SELECT title FROM tasks WHERE id = 't_keep'").use { c -> assertTrue(c.moveToFirst()) }
    }
}
