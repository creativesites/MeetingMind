package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
 * 31 → 32: Your garden. Additive only: the practices and practice_days tables. Existing tasks are
 * untouched, and the new tables work through their DAOs after the upgrade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration31To32Test {
    private lateinit var context: Context
    private val dbName = "migration-31-32-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 31)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO tasks (id, title, notes, kind, repeat, createdAt, updatedAt, waitingOn, space) " +
                    "VALUES ('t_plain', 'Read Romans 8', '', 'TASK', 'NONE', 1000, 1000, 0, 'PERSONAL')"
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
    fun databaseOpensAtVersion32() {
        val db = openMigrated()
        assertEquals(MeetMindDatabase.VERSION, db.openHelper.readableDatabase.version)
    }

    @Test
    fun existingTaskIsUnchanged() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query("SELECT title, space FROM tasks WHERE id = 't_plain'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Read Romans 8", c.getString(0))
            assertEquals("PERSONAL", c.getString(1))
        }
    }

    @Test
    fun practicesAndTheirDaysWorkThroughTheDaos() = runBlocking {
        val db = openMigrated()
        db.practiceDao().upsert(
            PracticeEntity(
                id = "practice_1", title = "Pray", sourceNoteId = null, sourceTitle = "Sermon 3", taskId = "t_plain",
                rhythm = "DAILY", remindMinutes = 420, checkInWeeks = 2, startedAt = 1000, checkInAt = 2000,
                state = "GROWING", endedAt = null, createdAt = 1000, updatedAt = 1000
            )
        )
        db.taskDao().insertPracticeDay(PracticeDayEntity("practice_1", "2026-11-16", 1000))
        db.taskDao().insertPracticeDay(PracticeDayEntity("practice_1", "2026-11-16", 2000))

        val garden = db.practiceDao().observeGarden().first()
        assertEquals(listOf("practice_1"), garden.map { it.id })
        assertEquals(1, db.practiceDao().daysFor("practice_1").size)
    }
}
