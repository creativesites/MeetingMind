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

/**
 * 30 → 31: the Tasks manager and on-device document reading (work-vertical-design-spec R3, R4).
 * Every change is additive. Existing tasks keep their values, get defaults, and are backfilled
 * into their note's list and into the WORK space where they are work tasks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration30To31Test {
    private lateinit var context: Context
    private val dbName = "migration-30-31-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 30)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder, kind) " +
                    "VALUES ('nb_client', 'Client', 'WORK', NULL, NULL, 1000, 1000, NULL, 0, 'PROJECT')"
            )
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, pinned, isPrivate, status, metadataJson, plainText) " +
                    "VALUES ('note_1', 'Call notes', 'MEETING', 'nb_client', 1000, 1000, 0, 0, 'OPEN', '{}', 'Discuss pricing')"
            )
            // From a note: its list comes from the note's notebook.
            db.execSQL(
                "INSERT INTO tasks (id, title, notes, kind, repeat, createdAt, updatedAt, noteId, waitingOn, sourceItemId, space) " +
                    "VALUES ('t_note', 'Send pricing', '', 'TASK', 'NONE', 1000, 1000, 'note_1', 0, NULL, NULL)"
            )
            // Waiting on someone: a work task.
            db.execSQL(
                "INSERT INTO tasks (id, title, notes, kind, repeat, createdAt, updatedAt, noteId, waitingOn, sourceItemId, space) " +
                    "VALUES ('t_wait', 'Ask Dan for the figures', '', 'TASK', 'NONE', 1000, 1000, NULL, 1, NULL, NULL)"
            )
            // From a finding in a recording: a work task.
            db.execSQL(
                "INSERT INTO tasks (id, title, notes, kind, repeat, createdAt, updatedAt, noteId, waitingOn, sourceItemId, space) " +
                    "VALUES ('t_src', 'Confirm budget', '', 'TASK', 'NONE', 1000, 1000, NULL, 0, 'item_1', NULL)"
            )
            // Already in a space: left as it is.
            db.execSQL(
                "INSERT INTO tasks (id, title, notes, kind, repeat, createdAt, updatedAt, noteId, waitingOn, sourceItemId, space) " +
                    "VALUES ('t_plain', 'Read Romans 8', '', 'TASK', 'NONE', 1000, 1000, NULL, 0, NULL, 'PERSONAL')"
            )
            db.execSQL(
                "INSERT INTO inbox_items (id, kind, uri, text, title, status, proposedJson, createdAt, processedAt, resultRefJson, transcriptionStatus) " +
                    "VALUES ('ib1', 'PDF', NULL, NULL, 'Contract', 'NEW', NULL, 1000, NULL, NULL, 'NONE')"
            )
            db.execSQL(
                "INSERT INTO item_evidence (id, itemId, segmentIdsJson, quote) VALUES ('ev1', 'item_1', '[]', 'the budget is 400')"
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
    fun databaseOpensAtVersion31() {
        val db = openMigrated()
        assertEquals(31, db.openHelper.readableDatabase.version)
    }

    @Test
    fun existingTasksKeepTheirValuesAndGetDefaults() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query(
            "SELECT title, priority, position, hasTime, parentId, sectionId, repeatRule FROM tasks WHERE id = 't_plain'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Read Romans 8", c.getString(0))
            assertEquals(4, c.getInt(1))
            assertEquals(0.0, c.getDouble(2), 0.0)
            assertEquals(0, c.getInt(3))
            assertTrue(c.isNull(4))
            assertTrue(c.isNull(5))
            assertTrue(c.isNull(6))
        }
    }

    @Test
    fun taskFromANoteIsFiledInTheNotesNotebook() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query("SELECT listId, space FROM tasks WHERE id = 't_note'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("nb_client", c.getString(0))
            // Not a work task by its own flags, so no space is invented.
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun workTasksGetTheWorkSpaceAndOthersStayUnlisted() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query("SELECT space, listId FROM tasks WHERE id = 't_wait'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("WORK", c.getString(0))
            assertTrue(c.isNull(1))
        }
        db.openHelper.readableDatabase.query("SELECT space FROM tasks WHERE id = 't_src'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("WORK", c.getString(0))
        }
        db.openHelper.readableDatabase.query("SELECT space FROM tasks WHERE id = 't_plain'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("PERSONAL", c.getString(0))
        }
    }

    @Test
    fun inboxItemsDefaultToNoReadingAndEvidenceHasNoPage() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query("SELECT readStatus, pageCount, readPages FROM inbox_items WHERE id = 'ib1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("NONE", c.getString(0))
            assertTrue(c.isNull(1))
            assertEquals(0, c.getInt(2))
        }
        db.openHelper.readableDatabase.query("SELECT quote, page, boxJson FROM item_evidence WHERE id = 'ev1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("the budget is 400", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun taskSearchFindsExistingTasksAfterTheBackfill() {
        val db = openMigrated()
        val hits = runBlocking { db.taskDao().searchTasks("pricing*", 10) }
        assertEquals(listOf("t_note"), hits.map { it.id })
    }

    @Test
    fun newTasksAreSearchableThroughTheTriggers() {
        val db = openMigrated()
        runBlocking {
            db.taskDao().upsert(
                TaskEntity(
                    id = "t_new", title = "Zebra crossing", notes = "bring the permit", kind = "TASK", dueAt = null, remindAt = null,
                    repeat = "NONE", doneAt = null, personId = null, noteId = null, blockId = null, meetingId = null,
                    startMs = null, scripture = null, createdAt = 1, updatedAt = 1
                )
            )
        }
        assertEquals(listOf("t_new"), runBlocking { db.taskDao().searchTasks("zebra*", 10) }.map { it.id })
        assertEquals(listOf("t_new"), runBlocking { db.taskDao().searchTasks("permit*", 10) }.map { it.id })
        assertTrue(runBlocking { db.taskDao().searchTasks("unrelated*", 10) }.isEmpty())
    }

    @Test
    fun documentPagesAreSearchableWithTheirPage() {
        val db = openMigrated()
        runBlocking {
            db.documentPageDao().upsertPages(
                listOf(
                    DocumentPageEntity("item_1", 1, "Cover page", "TEXT_LAYER"),
                    DocumentPageEntity("item_1", 4, "The invoice total is 400", "OCR", confidence = 0.91, boxesJson = "[]")
                )
            )
        }
        val hits = runBlocking { db.searchDao().documentPages("invoice*", 10) }
        assertEquals(1, hits.size)
        assertEquals("item_1", hits[0].itemId)
        assertEquals(4, hits[0].page)
        assertFalse(runBlocking { db.documentPageDao().forItem("item_1") }.isEmpty())
        assertNull(runBlocking { db.documentPageDao().page("item_1", 9) })
    }

    @Test
    fun taskLabelsAndSectionsAreEmptyAfterUpgrade() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM task_sections").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM task_labels").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }
}
