package com.example.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * 14 → 15 folds the four finding tables into `items` and adds `people` (PLAN_PROFESSIONAL.md §10).
 * Room validates the migrated file against the entities on open, so any SQL drift fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkMigrationTest {

    private lateinit var context: Context
    private val dbName = "work-migration-test.db"
    private var migrated: MeetMindDatabase? = null

    /** Tuesday 29 September 2026, 10:00 local: when the recording was made. */
    private val recordedAt = Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, 29, 10, 0) }.timeInMillis

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName).allowMainThreadQueries().build().also {
            it.openHelper.writableDatabase
            it.close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("PRAGMA foreign_keys = OFF")
            listOf("note_people", "people", "items").forEach { db.execSQL("DROP TABLE $it") }
            db.execSQL("CREATE TABLE action_items (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, task TEXT NOT NULL, assigneeSpeakerId TEXT, assigneeName TEXT, deadline TEXT, confidence REAL, isCompleted INTEGER NOT NULL, sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]')")
            db.execSQL("CREATE TABLE decisions (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, text TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'DISCUSSION', confidence REAL, sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]')")
            db.execSQL("CREATE TABLE questions (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, text TEXT NOT NULL, askedBySpeakerId TEXT, resolved INTEGER NOT NULL, answer TEXT, sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]')")
            db.execSQL("CREATE TABLE follow_ups (id TEXT NOT NULL PRIMARY KEY, meetingId TEXT NOT NULL, description TEXT NOT NULL, ownerSpeakerId TEXT, deadline TEXT, sourceSegmentIdsJson TEXT NOT NULL DEFAULT '[]')")
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, eventDate, pinned, isPrivate, status, answeredAt, metadataJson, archivedAt, plainText) " +
                    "VALUES ('n1', 'Acme review', 'MEETING', NULL, $recordedAt, $recordedAt, $recordedAt, 0, 0, 'OPEN', NULL, '{}', NULL, '')"
            )
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, language, summaryText, updatedAt, recordingType, processingProfile, processingVersion, noteId) " +
                    "VALUES ('m1', 'Acme review', $recordedAt, 60000, 'LOCAL_RECORDING', NULL, 'READY', 2, 'en', 'Summary', $recordedAt, 'MEETING', 'OFFLINE', 0, 'n1')"
            )
            db.execSQL("INSERT INTO transcript_segments (id, meetingId, speakerId, speakerName, startMs, endMs, text, confidence, isUserEdited, sourceSegmentIdsJson, wordsJson) VALUES ('s1', 'm1', 'spk1', 'Sarah', 14000, 16000, 'I will send it Friday', NULL, 0, '[]', '[]')")
            db.execSQL("INSERT INTO action_items VALUES ('a1', 'm1', 'Send API docs', 'spk1', 'Sarah', 'by Friday', 0.9, 0, '[\"s1\"]')")
            db.execSQL("INSERT INTO action_items VALUES ('a2', 'm1', 'Book room', NULL, NULL, 'soon', NULL, 1, '[]')")
            db.execSQL("INSERT INTO decisions VALUES ('d1', 'm1', 'Use OAuth2', 'DECISION', 0.8, '[]')")
            db.execSQL("INSERT INTO questions VALUES ('q1', 'm1', 'When is launch?', NULL, 1, 'October', '[]')")
            db.execSQL("INSERT INTO follow_ups VALUES ('f1', 'm1', 'Send recap', NULL, 'tomorrow', '[]')")
            db.version = 14
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `findings become items attached to the note, with real due days`() = runBlocking {
        val db = Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(MeetMindDatabase.MIGRATION_14_15).allowMainThreadQueries().build().also { migrated = it }
        val items = db.itemDao().getRawForMeeting("m1").associateBy { it.id }
        assertEquals(5, items.size)

        val a1 = items.getValue("a1")
        assertEquals("TASK", a1.kind)
        assertEquals("n1", a1.noteId)
        assertEquals("OPEN", a1.status)
        assertEquals("spk1", a1.ownerSpeakerId)
        assertEquals("by Friday", a1.dueText)
        val friday = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 2) }.timeInMillis
        assertEquals(friday, a1.dueAt)
        assertEquals(14000L, a1.sourceStartMs)
        assertTrue("old findings don't ask to be reviewed again", a1.reviewed)

        val a2 = items.getValue("a2")
        assertEquals("DONE", a2.status)
        assertNull("'soon' is not a day", a2.dueAt)

        assertEquals("DECISION", items.getValue("d1").subtype)
        assertEquals("ANSWERED", items.getValue("q1").status)
        assertEquals("October", items.getValue("q1").answer)
        assertEquals("FOLLOW_UP", items.getValue("f1").subtype)
        assertNotNull(items.getValue("f1").dueAt)
    }

    @Test
    fun `the old tables are gone and the new ones are usable`() = runBlocking {
        val db = Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(MeetMindDatabase.MIGRATION_14_15).allowMainThreadQueries().build().also { migrated = it }
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertFalse("action_items" in tables)
        assertFalse("follow_ups" in tables)
        db.peopleDao().upsert(PersonEntity("p1", "PERSON", "Sarah Chen", "[]", "[]", "[]", null, null, null, false, null, false, 1, 1, null))
        db.peopleDao().linkSpeaker("spk1", "p1")
        // No speaker row exists in this fixture, so the owner falls back to the extracted name.
        assertEquals("Sarah", db.itemDao().getForMeeting("m1", "TASK").first { it.item.id == "a1" }.ownerDisplay)
    }
}
