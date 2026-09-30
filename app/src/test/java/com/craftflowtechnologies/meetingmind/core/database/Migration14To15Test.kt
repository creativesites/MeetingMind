package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 14 → 15 (PRD_M0 §4.2) on a version-14 database holding a realistic library: every row survives,
 * drafts move into the indexed column, and Room accepts the result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration14To15Test {

    private lateinit var context: Context
    private val dbName = "migration-14-15-test.db"
    private var migrated: MeetMindDatabase? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 14)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder) VALUES ('nb', 'Work', 'WORK', '#123456', NULL, 1, 2, NULL, 0)")
            fun note(id: String, meta: String, archived: String = "NULL") = db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, eventDate, pinned, isPrivate, status, answeredAt, metadataJson, archivedAt, plainText) " +
                    "VALUES ('$id', 'Title $id', 'MEETING', 'nb', 10, 20, NULL, 1, 0, 'OPEN', NULL, '$meta', $archived, 'text of $id')"
            )
            note("kept", "{}")
            note("draft", "{\"draft\":\"1\",\"draftTitle\":\"Untitled\"}")
            note("archived", "{\"speaker\":\"Pastor\"}", archived = "99")
            db.execSQL(
                "INSERT INTO note_blocks (id, noteId, position, type, text, spans, payloadJson, source, sourceSegmentIdsJson, sectionKey, isUserEdited, indent, checked, updatedAt) " +
                    "VALUES ('b1', 'kept', 0, 'HEADING_1', 'Decisions', 'BOLD,0,9', '{}', 'USER', '[]', NULL, 0, 0, 0, 20)"
            )
            db.execSQL(
                "INSERT INTO note_blocks (id, noteId, position, type, text, spans, payloadJson, source, sourceSegmentIdsJson, sectionKey, isUserEdited, indent, checked, updatedAt) " +
                    "VALUES ('b2', 'kept', 1, 'CHECKLIST', 'Ship M0', '', '{}', 'AI', '[\"s1\"]', 'ai_actions', 1, 1, 1, 20)"
            )
            db.execSQL("INSERT INTO tags (id, name) VALUES ('t', 'client')")
            db.execSQL("INSERT INTO note_tags (noteId, tagId) VALUES ('kept', 't')")
            db.execSQL("INSERT INTO note_links (id, fromNoteId, toNoteId, kind, createdAt) VALUES ('l', 'kept', 'archived', 'RELATED', 5)")
            db.execSQL("INSERT INTO attachments (id, noteId, kind, path, mimeType, sizeBytes, width, height, durationMs, caption, createdAt) VALUES ('a', 'kept', 'IMAGE', '/x.jpg', 'image/jpeg', 10, 1, 1, NULL, 'cap', 5)")
            db.execSQL(
                "INSERT INTO scripture_refs (id, noteId, blockId, bookUsfm, chapter, verseStart, verseEnd, versionId, origin, meetingId, segmentId, startMs, createdAt) " +
                    "VALUES ('s', 'kept', 'b1', 'ROM', 8, 28, NULL, 1, 'USER', NULL, NULL, NULL, 5)"
            )
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    private fun open(): MeetMindDatabase =
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(MeetMindDatabase.MIGRATION_14_15, MeetMindDatabase.MIGRATION_15_16, MeetMindDatabase.MIGRATION_16_17)
            .allowMainThreadQueries().build().also { migrated = it }

    @Test
    fun `every row survives and Room accepts the schema`() = runBlocking {
        val db = open()
        val kept = db.noteDao().getById("kept")!!
        assertEquals("Title kept", kept.title)
        assertEquals("text of kept", kept.plainText)
        assertFalse(kept.isDraft)
        assertNull(kept.deletedAt)
        val blocks = db.noteDao().getBlocks("kept")
        assertEquals(listOf("Decisions", "Ship M0"), blocks.map { it.text })
        assertEquals("BOLD,0,9", blocks[0].spans)
        assertTrue(blocks[1].checked)
        assertEquals(3, db.noteDao().getAll().size)
        assertEquals(1, db.notebookDao().getAll().size)
        assertNull(db.notebookDao().getById("nb")!!.deletedAt)
    }

    @Test
    fun `drafts move into the indexed column and stay out of the library`() = runBlocking {
        val db = open()
        assertTrue(db.noteDao().getById("draft")!!.isDraft)
        assertFalse(db.noteDao().getById("archived")!!.isDraft)
        val active = db.noteDao().getActiveOnce()
        assertEquals(listOf("kept"), active.map { it.id })
    }

    @Test
    fun `the version history table works`() = runBlocking {
        val db = open()
        db.noteVersionDao().insert(NoteVersionEntity("v", "kept", 30, "MANUAL", null, "Title kept", byteArrayOf(1, 2, 3), 3))
        assertEquals(1, db.noteVersionDao().forNote("kept").size)
        // Versions go with their note when it is deleted for good.
        db.noteDao().delete("kept")
        assertEquals(0, db.noteVersionDao().forNote("kept").size)
    }

    @Test
    fun `a database from a newer app is refused, not wiped`() {
        MeetMindDatabase.forget() // Another test in this process may have opened the real one.
        val file = context.getDatabasePath(DatabaseGuard.NAME)
        ExportedSchema.create(file, 14)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder) VALUES ('nb', 'Mine', 'WORK', NULL, NULL, 1, 1, NULL, 0)")
            it.version = MeetMindDatabase.VERSION + 1
        }
        try {
            val result = DatabaseGuard.open(context)
            assertTrue(result is DatabaseGuard.OpenResult.Failed)
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM notebooks", null).use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            }
        } finally {
            MeetMindDatabase.forget()
            context.deleteDatabase(DatabaseGuard.NAME)
        }
    }

    @Test
    fun `a copy is kept before migrating, and only the newest three`() {
        val file = context.getDatabasePath(DatabaseGuard.NAME)
        val backups = File(context.filesDir, "db_backups")
        backups.deleteRecursively()
        try {
            ExportedSchema.create(file, 14)
            val copies = (1..5).map { i -> DatabaseGuard.copyBeforeMigration(context, 15, now = i.toLong())!!.also { it.setLastModified(i * 1000L) } }
            assertEquals(14, DatabaseGuard.storedVersion(copies.last()))
            assertEquals(3, backups.listFiles { f -> f.name.endsWith(".sqlite") }!!.size)
            // Nothing to copy when no migration is due.
            assertNull(DatabaseGuard.copyBeforeMigration(context, 14))
        } finally {
            backups.deleteRecursively()
            context.deleteDatabase(DatabaseGuard.NAME)
        }
    }
}
