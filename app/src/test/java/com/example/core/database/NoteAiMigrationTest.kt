package com.example.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 13 → 14 adds the note AI job table and touches nothing else; Room validates the result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteAiMigrationTest {

    private lateinit var context: Context
    private val dbName = "note-ai-migration-test.db"
    private var migrated: MeetMindDatabase? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName).allowMainThreadQueries().build().also {
            it.openHelper.writableDatabase
            it.close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE note_ai_jobs")
            db.execSQL(
                "INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder) " +
                    "VALUES ('nb', 'My Notes', 'PERSONAL', NULL, NULL, 1, 1, NULL, 0)"
            )
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, eventDate, pinned, isPrivate, status, answeredAt, metadataJson, archivedAt, plainText) " +
                    "VALUES ('n1', 'Kept', 'GENERAL', 'nb', 1, 1, 1, 0, 0, 'OPEN', NULL, '{}', NULL, 'hello')"
            )
            db.version = 13
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `the job table is added and notes are untouched`() = runBlocking {
        val db = Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(MeetMindDatabase.MIGRATION_13_14).allowMainThreadQueries().build().also { migrated = it }
        assertEquals("Kept", db.noteDao().getById("n1")!!.title)
        db.noteAiJobDao().upsert(NoteAiJobEntity("j", "NOTE", "n1", "SUMMARIZE", "QUEUED", "{}", null, null, null, 1, 1))
        assertEquals("SUMMARIZE", db.noteAiJobDao().getById("j")!!.tool)
    }
}
