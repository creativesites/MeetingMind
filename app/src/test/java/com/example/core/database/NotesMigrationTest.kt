package com.example.core.database

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

/**
 * Runs [MeetMindDatabase.MIGRATION_12_13] the way a phone does: Room opens a version-12 file,
 * runs the migration, then validates every table against the entities. Any difference between
 * the migration's SQL and NoteEntities.kt fails here with Room's own "Migration didn't properly
 * handle" error, instead of on a user's device.
 *
 * The version-12 file is made by creating today's schema and removing what 13 added, which keeps
 * every older table exactly as Room declares it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesMigrationTest {

    private lateinit var context: Context
    private val dbName = "notes-migration-test.db"
    private var migrated: MeetMindDatabase? = null

    private val notesTables = listOf(
        "scripture_collection_items", "scripture_collections", "scripture_refs", "note_links",
        "note_tags", "tags", "attachments", "note_blocks", "notes", "notebooks"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)

        // 1. Today's schema, created by Room.
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName).allowMainThreadQueries().build().also {
            it.openHelper.writableDatabase
            it.close()
        }

        // 2. Turn it back into version 12, holding two real recordings.
        val path = context.getDatabasePath(dbName).path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            notesTables.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
            // This SQLite has no DROP COLUMN; the table is still empty, so recreate it from
            // Room's own statement without the column, and put its other indices back.
            val meetingsSql = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='meetings'", null)
                .use { it.moveToFirst(); it.getString(0) }
            val indexSql = db.rawQuery(
                "SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='meetings' AND sql IS NOT NULL AND name != 'index_meetings_noteId'",
                null
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            val withoutNoteId = meetingsSql.replace(", `noteId` TEXT", "")
            check(withoutNoteId != meetingsSql) { "meetings no longer declares noteId as expected: $meetingsSql" }
            db.execSQL("DROP TABLE meetings")
            db.execSQL(withoutNoteId)
            indexSql.forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, " +
                    "language, summaryText, updatedAt, recordingType, customContext, speakerCountPreference, processingProfile, processingVersion) " +
                    "VALUES ('m1', 'Sunday service', 1700000000000, 600000, 'LOCAL_RECORDING', '/data/m1.wav', 'READY', 2, 'en', " +
                    "'Faith and patience', 1700000600000, 'LECTURE', NULL, NULL, 'OFFLINE', 0)"
            )
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, " +
                    "language, summaryText, updatedAt, recordingType, customContext, speakerCountPreference, processingProfile, processingVersion) " +
                    "VALUES ('m2', 'Morning thoughts', 1700100000000, 60000, 'LOCAL_RECORDING', '/data/m2.wav', 'READY', 1, 'en', " +
                    "NULL, 1700100060000, 'JOURNAL', NULL, 1, 'OFFLINE', 0)"
            )
            db.version = 12
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    private fun openMigrated(): MeetMindDatabase =
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(MeetMindDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
            .also { migrated = it }

    @Test
    fun `Room accepts the migrated schema`() {
        // Opening validates every table, index and foreign key against the entities.
        openMigrated().openHelper.writableDatabase
    }

    @Test
    fun `every recording gets exactly one note in My Notes`() = runBlocking {
        val db = openMigrated()

        val notes = db.noteDao().getAll()
        assertEquals(2, notes.size)
        val sermon = notes.single { it.id == "note_m1" }
        assertEquals("Sunday service", sermon.title)
        assertEquals("LECTURE", sermon.workflow)
        assertEquals(MeetMindDatabase.DEFAULT_NOTEBOOK_ID, sermon.notebookId)
        assertEquals(1700000000000, sermon.eventDate)
        assertEquals("Faith and patience", sermon.plainText)
        assertEquals(false, sermon.isPrivate)

        // Journal entries were private in spirit before; they are private notes now.
        assertTrue(notes.single { it.id == "note_m2" }.isPrivate)

        assertNotNull(db.notebookDao().getById(MeetMindDatabase.DEFAULT_NOTEBOOK_ID))
    }

    @Test
    fun `each note holds its recording and the recording points back`() = runBlocking {
        val db = openMigrated()

        val blocks = db.noteDao().getBlocks("note_m1")
        assertEquals(1, blocks.size)
        assertEquals("RECORDING", blocks.single().type)
        assertTrue(blocks.single().payloadJson.contains("\"meetingId\":\"m1\""))

        assertEquals("note_m1", db.meetingDao().getMeetingById("m1")!!.noteId)
        assertEquals(listOf("m1"), db.noteDao().getMeetingsForNote("note_m1").map { it.id })
    }

    @Test
    fun `recordings are untouched`() = runBlocking {
        val m1 = openMigrated().meetingDao().getMeetingById("m1")!!
        assertEquals("Sunday service", m1.title)
        assertEquals("/data/m1.wav", m1.audioFilePath)
        assertEquals("Faith and patience", m1.summaryText)
        assertEquals(600000, m1.durationMs)
    }
}
