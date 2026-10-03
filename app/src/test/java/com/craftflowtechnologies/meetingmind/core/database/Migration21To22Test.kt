package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 21 → 22: Learning vertical. All existing rows survive and new learning tables work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration21To22Test {
    private lateinit var context: Context
    private val dbName = "migration-21-22-test.db"
    private var migrated: MeetMindDatabase? = null
    private val tables = listOf(
        "people", "tasks", "meetings", "notebooks", "notes", "decisions", "items",
        "item_evidence", "item_links", "item_events", "project_members", "segment_signals",
        "briefs", "memory_stories", "transcript_segments", "inbox_items"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 21)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO notebooks (id, name, space, createdAt, updatedAt, sortOrder) " +
                    "VALUES ('nb1', 'Biology', 'LEARNING', 10, 10, 0)"
            )
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, pinned, isPrivate, status, metadataJson, plainText) " +
                    "VALUES ('n1', 'Cell Respiration', 'LECTURE', 'nb1', 10, 10, 0, 0, 'OPEN', '{}', 'Mitochondria is the powerhouse.')"
            )
            db.execSQL(
                "INSERT INTO inbox_items (id, kind, title, status, createdAt) VALUES ('in1', 'TEXT', 'Check enzymes', 'NEW', 15)"
            )
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
        open().openHelper.writableDatabase
        migrated!!.close()
        assertEquals(before, tables.associateWith { count(it) })
        assertEquals(1, before.getValue("notes"))
        assertEquals(1, before.getValue("inbox_items"))
    }

    @Test
    fun learningTablesCanStoreAndQueryEntities() = runBlocking {
        val db = open()
        val sessionDao = db.learningSessionDao()
        val conceptDao = db.learningConceptDao()
        val activityDao = db.learningActivityDao()
        val attemptDao = db.activityAttemptDao()
        val scheduleDao = db.reviewScheduleDao()

        // 1. Session
        val session = LearningSessionEntity(
            id = "s1", noteId = "n1", meetingId = null, title = "Cell Respiration",
            courseName = "Biology 101", status = "ACTIVE", lastStudiedAt = null,
            createdAt = 100, updatedAt = 100
        )
        sessionDao.upsert(session)
        assertEquals("Cell Respiration", sessionDao.getById("s1")?.title)

        // 2. Concept
        val concept = LearningConceptEntity(
            id = "c1", sessionId = "s1", name = "Glycolysis", definition = "Breakdown of glucose",
            emphasis = "Key pathway", state = "NEW", createdAt = 100, updatedAt = 100
        )
        conceptDao.upsert(concept)
        assertEquals(1, conceptDao.getBySession("s1").size)

        // 3. Activity
        val activity = LearningActivityEntity(
            id = "act1", sessionId = "s1", conceptId = "c1", type = "RECALL",
            prompt = "What is the primary product of glycolysis?", expectedAnswer = "Pyruvate",
            difficulty = "MEDIUM", isDiagnostic = true, createdAt = 100, updatedAt = 100
        )
        activityDao.upsert(activity)
        assertEquals(1, activityDao.getDiagnosticBySession("s1").size)

        // 4. Attempt
        val attempt = ActivityAttemptEntity(
            id = "att1", activityId = "act1", sessionId = "s1", conceptId = "c1",
            userResponse = "Pyruvate", isCorrect = true, selfRating = "GOOD", feedback = "Correct",
            createdAt = 110
        )
        attemptDao.insert(attempt)
        assertEquals(1, attemptDao.getByActivity("act1").size)

        // 5. Schedule
        val schedule = ReviewScheduleEntity(
            id = "sch1", activityId = "act1", sessionId = "s1", conceptId = "c1",
            dueAt = 200, intervalDays = 1, repetitionCount = 1, createdAt = 100, updatedAt = 110
        )
        scheduleDao.upsert(schedule)
        assertNotNull(scheduleDao.getByActivity("act1"))
    }
}
