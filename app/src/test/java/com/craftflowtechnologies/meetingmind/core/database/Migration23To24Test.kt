package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 23 → 24: the "My creations" table. Every existing row survives and the new table works. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration23To24Test {
    private lateinit var context: Context
    private val dbName = "migration-23-24-test.db"
    private var migrated: MeetMindDatabase? = null
    private val tables = listOf("people", "tasks", "meetings", "notebooks", "notes", "decisions", "items", "inbox_items", "circles", "learning_sessions")

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 23)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO notebooks (id, name, space, createdAt, updatedAt, sortOrder) VALUES ('nb1', 'Faith', 'FAITH', 10, 10, 0)")
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, pinned, isPrivate, status, metadataJson, plainText) " +
                    "VALUES ('n1', 'Sunday', 'SERMON', 'nb1', 10, 10, 0, 0, 'OPEN', '{}', 'Grace upon grace.')"
            )
            db.execSQL("INSERT INTO inbox_items (id, kind, title, status, createdAt) VALUES ('in1', 'TEXT', 'Check', 'NEW', 15)")
        }
    }

    @After fun tearDown() { migrated?.close(); context.deleteDatabase(dbName) }

    private fun count(table: String): Int = SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c -> c.moveToFirst(); c.getInt(0) }
    }

    private fun open(): MeetMindDatabase = Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
        .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build().also { migrated = it }

    @Test fun `schema 23 had no create_cards table`() {
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE name = 'create_cards'", null).use { assertEquals(0, it.count) }
        }
    }

    @Test fun `every existing row survives`() {
        val before = tables.associateWith { count(it) }
        open().openHelper.writableDatabase
        migrated!!.close()
        assertEquals(before, tables.associateWith { count(it) })
        assertEquals(1, before.getValue("notes"))
        assertEquals(24, MeetMindDatabase.VERSION)
    }

    @Test fun `the new table stores and queries cards`() = runBlocking {
        val dao = open().createCardDao()
        dao.upsert(CreateCardEntity("c1", "VERSE", "grace", "John 3:16", "PEACEFUL", "Be still.", "John 3:16", 3034, "STORY", "{}", "[]", false, 100, 100))
        dao.upsert(CreateCardEntity("c2", "FREE_PROMPT", "", null, "FUNNY", "Monday called.", null, null, "SQUARE", "{}", "[]", true, 90, 90))
        assertNotNull(dao.get("c1"))
        assertEquals(2, count2())
        dao.delete("c1")
        assertEquals(1, count2())
        assertTrue(MeetMindDatabase.MIGRATION_23_24_SQL.any { it.contains("create_cards") })
    }

    private fun count2(): Int { migrated!!.openHelper.writableDatabase.query("SELECT COUNT(*) FROM create_cards").use { it.moveToFirst(); return it.getInt(0) } }
}
