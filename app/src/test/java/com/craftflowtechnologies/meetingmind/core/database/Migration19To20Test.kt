package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 19 → 20: cached brief prose and monthly memory stories. Every existing row survives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration19To20Test {
    private lateinit var context: Context
    private val dbName = "migration-19-20-test.db"
    private var migrated: MeetMindDatabase? = null
    private val tables = listOf("people", "tasks", "meetings", "notebooks", "notes", "decisions", "items", "item_evidence", "item_links", "item_events", "project_members", "segment_signals", "transcript_segments")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 19)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, language, summaryText, updatedAt, recordingType, processingProfile, processingVersion) " +
                    "VALUES ('m1', 'Review', 5, 1000, 'LOCAL_RECORDING', NULL, 'READY', 2, 'en', NULL, 7, 'CLIENT_CALL', 'OFFLINE', 1)"
            )
            db.execSQL("INSERT INTO items (id, kind, status, text, reviewed, source, space, createdAt, updatedAt) VALUES ('i1', 'DECISION', 'ACTIVE', 'Launch on October 14', 1, 'AI', 'WORK', 5, 5)")
            db.execSQL("INSERT INTO item_events (id, itemId, entityType, entityId, type, at) VALUES ('v1', 'i1', 'ITEM', 'i1', 'CREATED', 5)")
            db.execSQL("INSERT INTO transcript_segments (id, meetingId, speakerId, speakerName, startMs, endMs, text, confidence, isUserEdited, cleanedText, sourceSegmentIdsJson, wordsJson) VALUES ('s1', 'm1', NULL, NULL, 0, 1000, 'Launch is October 14.', NULL, 0, NULL, '[]', '[]')")
            db.execSQL("INSERT INTO segment_signals (id, segmentId, meetingId, kind, confidence, text, signalId) VALUES ('g1', 's1', 'm1', 'DEADLINE', 0.8, 'Launch October 14', 'g')")
        }
    }

    @After
    fun tearDown() {
        migrated?.close()
        context.deleteDatabase(dbName)
    }

    private fun count(table: String): Int = SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c -> c.moveToFirst(); c.getInt(0) }
    }

    private fun open(): MeetMindDatabase =
        Room.databaseBuilder(context, MeetMindDatabase::class.java, dbName)
            .addMigrations(*MeetMindDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries().build().also { migrated = it }

    @Test
    fun everyExistingRowSurvives() = runBlocking {
        val before = tables.associateWith { count(it) }
        open().openHelper.writableDatabase // migrate, and let Room validate the result
        migrated!!.close()
        assertEquals(before, tables.associateWith { count(it) })
        assertEquals(1, before.getValue("segment_signals"))
    }

    @Test
    fun briefsAndStoriesCanBeStoredAndFound() = runBlocking {
        val db = open()
        assertNull(db.briefDao().latest("PROJECT", "nb", "PROJECT"))
        db.briefDao().upsert(BriefEntity("b1", "PROJECT", "nb", "PROJECT", "{}", "[]", 10))
        db.briefDao().upsert(BriefEntity("b2", "PROJECT", "nb", "PROJECT", "{\"executive\":[]}", "[\"i1\"]", 20))
        assertEquals("b2", db.briefDao().latest("PROJECT", "nb", "PROJECT")!!.id)
        db.briefDao().clear("PROJECT", "nb", "PROJECT")
        assertEquals(0, db.briefDao().count())
        db.memoryStoryDao().upsert(MemoryStoryEntity("PROJECT", "nb", "2026-09", "Launch was set.", "[\"i1\"]", 3, 30))
        db.memoryStoryDao().upsert(MemoryStoryEntity("PROJECT", "nb", "2026-09", "Launch was set again.", "[\"i1\"]", 4, 40)) // same month replaces
        db.memoryStoryDao().upsert(MemoryStoryEntity("PROJECT", "nb", "2026-10", "It moved.", "[]", 1, 50))
        assertEquals(listOf("2026-10", "2026-09"), db.memoryStoryDao().forEntity("PROJECT", "nb").map { it.month })
        assertEquals(4, db.memoryStoryDao().get("PROJECT", "nb", "2026-09")!!.itemCount)
        assertEquals("Launch on October 14", db.itemDao().getById("i1")!!.text)
    }
}
