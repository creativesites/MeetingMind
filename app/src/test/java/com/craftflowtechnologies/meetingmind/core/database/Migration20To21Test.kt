package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 20 → 21: the Work Inbox. Every existing row survives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration20To21Test {
    private lateinit var context: Context
    private val dbName = "migration-20-21-test.db"
    private var migrated: MeetMindDatabase? = null
    private val tables = listOf("people", "tasks", "meetings", "notebooks", "notes", "decisions", "items", "item_evidence", "item_links", "item_events", "project_members", "segment_signals", "briefs", "memory_stories", "transcript_segments")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 20)
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
            .addMigrations(MeetMindDatabase.MIGRATION_20_21, MeetMindDatabase.MIGRATION_20_21)
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
    fun theInboxStoresItemsAndFindsTheOpenOnes() = runBlocking {
        val db = open()
        val dao = db.inboxDao()
        dao.upsert(InboxItemEntity("a", "TEXT", text = "Call Ana about pricing", title = "Pricing", status = "NEW", createdAt = 10))
        dao.upsert(InboxItemEntity("b", "PDF", uri = "/files/inbox/x.pdf", status = "PROPOSED", proposedJson = "{}", createdAt = 20))
        dao.upsert(InboxItemEntity("c", "URL", text = "https://acme.com", status = "FILED", createdAt = 30, processedAt = 31, resultRefJson = "{}"))
        assertEquals(listOf("b", "a"), dao.open().map { it.id })
        assertEquals(2, dao.observeOpenCount().first())
        dao.update(dao.get("a")!!.copy(status = "DISMISSED"))
        assertEquals(listOf("b"), dao.open().map { it.id })
        assertNull(dao.get("b")!!.title)
        assertEquals(3, dao.all().size)
        assertEquals("Launch on October 14", db.itemDao().getById("i1")!!.text)
    }
}
