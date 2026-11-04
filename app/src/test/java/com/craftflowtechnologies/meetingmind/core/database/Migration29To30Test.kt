package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 29 → 30: Learning vertical v2 substrate. Adds subjectDomain & signal counters to
 * `learning_sessions`, examImportance & formulaLatex to `learning_concepts`, Teach-Back
 * rubrics to `learning_activities`, audioPath/attachmentId to `activity_attempts`,
 * and the `learning_goals` table for exam readiness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration29To30Test {
    private lateinit var context: Context
    private val dbName = "migration-29-30-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 29)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // Insert a prerequisite note so learning_sessions FK passes
            db.execSQL(
                "INSERT INTO notes (id, title, preview, createdAt, updatedAt, isArchived, isDeleted, isPinned, isPrivate, space) " +
                    "VALUES ('note_1', 'Calculus Lecture', 'Derivatives', 1000, 1000, 0, 0, 0, 0, 'LEARNING')"
            )
            // Insert existing session at v29
            db.execSQL(
                "INSERT INTO learning_sessions (id, noteId, meetingId, title, courseName, status, lastStudiedAt, createdAt, updatedAt) " +
                    "VALUES ('sess_1', 'note_1', NULL, 'Derivatives and Limits', 'MATH 101', 'ACTIVE', 1050, 1000, 1050)"
            )
            // Insert existing concept at v29
            db.execSQL(
                "INSERT INTO learning_concepts (id, sessionId, name, description, masterLevel, isDismissed, isUserEdited, createdAt, updatedAt) " +
                    "VALUES ('conc_1', 'sess_1', 'Chain Rule', 'Derivative of composite function', 'DEVELOPING', 0, 0, 1000, 1050)"
            )
            // Insert existing activity at v29
            db.execSQL(
                "INSERT INTO learning_activities (id, sessionId, conceptId, type, prompt, answer, optionsJson, difficulty, isDiagnostic, isDismissed, isStale, createdAt, updatedAt) " +
                    "VALUES ('act_1', 'sess_1', 'conc_1', 'RECALL', 'What is chain rule?', 'f prime of g times g prime', '[]', 'MEDIUM', 0, 0, 0, 1000, 1050)"
            )
            // Insert existing attempt at v29
            db.execSQL(
                "INSERT INTO activity_attempts (id, activityId, sessionId, isCorrect, selfRating, feedback, createdAt) " +
                    "VALUES ('att_1', 'act_1', 'sess_1', 1, 'GOOD', 'Solid understanding', 1100)"
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
    fun existingLearningSessionsSurviveWithDefaultValues() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query(
            "SELECT id, title, subjectDomain, examSignalCount, unresolvedConfusionCount FROM learning_sessions WHERE id = 'sess_1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("sess_1", c.getString(0))
            assertEquals("Derivatives and Limits", c.getString(1))
            assertEquals("GENERAL", c.getString(2))
            assertEquals(0, c.getInt(3))
            assertEquals(0, c.getInt(4))
        }
    }

    @Test
    fun existingConceptsSurviveWithDefaults() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query(
            "SELECT id, name, examImportance, formulaLatex FROM learning_concepts WHERE id = 'conc_1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("conc_1", c.getString(0))
            assertEquals("Chain Rule", c.getString(1))
            assertEquals("NORMAL", c.getString(2))
            assertTrue(c.isNull(3))
        }
    }

    @Test
    fun existingActivitiesAndAttemptsSurviveWithDefaults() {
        val db = openMigrated()
        db.openHelper.readableDatabase.query(
            "SELECT id, rubricJson FROM learning_activities WHERE id = 'act_1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("act_1", c.getString(0))
            assertEquals("[]", c.getString(1))
        }
        db.openHelper.readableDatabase.query(
            "SELECT id, audioPath, attachmentId FROM activity_attempts WHERE id = 'att_1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("att_1", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun learningGoalsTableExistsAndOpensAtSchemaThirty() {
        assertEquals(30, MeetMindDatabase.VERSION)
        val db = openMigrated()
        assertEquals(30, db.openHelper.readableDatabase.version)

        val columns = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("PRAGMA table_info(learning_goals)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                columns.add(c.getString(nameIdx))
            }
        }
        val expected = setOf(
            "id", "notebookId", "title", "examDateMs", "examFormat",
            "sessionIdsJson", "topicWeightsJson", "pastPaperNoteId",
            "dailyMinutesTarget", "createdAt", "updatedAt"
        )
        assertEquals(expected, columns)
    }

    @Test
    fun daoCanWriteAndQueryLearningGoalsAndUpdatedEntities() = runBlocking {
        val db = openMigrated()
        val goalDao = db.learningGoalDao()
        val goal = LearningGoalEntity(
            id = "goal_final_exam",
            notebookId = null,
            title = "Math 101 Final Exam",
            examDateMs = 1750000000000L,
            examFormat = "MIXED",
            sessionIdsJson = "[\"sess_1\"]",
            topicWeightsJson = "{\"Derivatives\": 0.8}",
            pastPaperNoteId = "note_1",
            dailyMinutesTarget = 30,
            createdAt = 1200,
            updatedAt = 1200
        )
        goalDao.upsert(goal)
        val loaded = goalDao.getById("goal_final_exam")
        assertNotNull(loaded)
        assertEquals("Math 101 Final Exam", loaded?.title)
        assertEquals(30, loaded?.dailyMinutesTarget)

        val domainGoal = loaded!!.toDomain()
        assertEquals(listOf("sess_1"), domainGoal.sessionIds)
        assertEquals(0.8f, domainGoal.topicWeights["Derivatives"] ?: 0f, 0.01f)

        // Verify session dao updates
        val sessionDao = db.learningSessionDao()
        val session = sessionDao.getById("sess_1")
        assertNotNull(session)
        assertEquals(SubjectDomain.GENERAL, session!!.subjectDomain)

        val updatedSession = session.copy(
            subjectDomain = SubjectDomain.MATH_QUANT,
            examSignalCount = 3,
            unresolvedConfusionCount = 1
        )
        sessionDao.update(updatedSession)
        val reloadedSession = sessionDao.getById("sess_1")
        assertEquals(SubjectDomain.MATH_QUANT, reloadedSession?.subjectDomain)
        assertEquals(3, reloadedSession?.examSignalCount)
        assertEquals(1, reloadedSession?.unresolvedConfusionCount)
    }
}
