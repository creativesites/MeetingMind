package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 28 → 29: Work Inbox Redesign (Increment 1). Adds source tracking, media metadata,
 * transcription status, and meeting link to `inbox_items`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration28To29Test {
    private lateinit var context: Context
    private val dbName = "migration-28-29-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 28)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO inbox_items (id, kind, uri, text, title, status, proposedJson, createdAt, processedAt, resultRefJson) " +
                    "VALUES ('ib1', 'TEXT', NULL, 'Discussion notes', 'Project Sync', 'NEW', NULL, 1000, NULL, NULL)"
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
    fun existingInboxItemsSurviveWithDefaultValues() {
        val db = openMigrated()
        db.openHelper.writableDatabase
        db.openHelper.readableDatabase.query(
            "SELECT id, kind, title, source, mediaDurationMs, mediaThumbnailUri, transcriptionStatus, meetingId FROM inbox_items WHERE id = 'ib1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ib1", c.getString(0))
            assertEquals("TEXT", c.getString(1))
            assertEquals("Project Sync", c.getString(2))
            assertNull(c.getString(3))
            assertTrue(c.isNull(4))
            assertNull(c.getString(5))
            assertEquals("NONE", c.getString(6))
            assertNull(c.getString(7))
        }
    }

    @Test
    fun newColumnsArePresentInInboxItemsTable() {
        val db = openMigrated()
        val expected = setOf("source", "mediaDurationMs", "mediaThumbnailUri", "transcriptionStatus", "meetingId")
        val found = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("PRAGMA table_info(inbox_items)").use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) {
                val col = c.getString(nameIndex)
                if (col in expected) found.add(col)
            }
        }
        assertEquals(expected, found)
    }

    @Test
    fun theDatabaseOpensAtSchemaTwentyNine() {
        assertEquals(29, MeetMindDatabase.VERSION)
        val db = openMigrated()
        assertEquals(29, db.openHelper.readableDatabase.version)
    }

    @Test
    fun daoCanWriteAndQueryWithNewColumns() = runBlocking {
        val db = openMigrated()
        val dao = db.inboxDao()
        val item = InboxItemEntity(
            id = "ib_video_1",
            kind = "VIDEO",
            uri = "/files/inbox/demo.mp4",
            title = "Demo Recording",
            status = "NEW",
            createdAt = 2000,
            source = "com.google.android.youtube",
            mediaDurationMs = 180000L,
            mediaThumbnailUri = "/files/inbox/demo_thumb.jpg",
            transcriptionStatus = "PENDING",
            meetingId = null
        )
        dao.upsert(item)
        val loaded = dao.get("ib_video_1")
        assertEquals(item, loaded)
        assertEquals("PENDING", loaded?.transcriptionStatus)
        assertEquals("com.google.android.youtube", loaded?.source)
        assertEquals(180000L, loaded?.mediaDurationMs)

        dao.updateTranscription("ib_video_1", "COMPLETED", transcript = "Full transcript text", meetingId = "m_100")
        val updated = dao.get("ib_video_1")
        assertEquals("COMPLETED", updated?.transcriptionStatus)
        assertEquals("Full transcript text", updated?.text)
        assertEquals("m_100", updated?.meetingId)
    }
}
