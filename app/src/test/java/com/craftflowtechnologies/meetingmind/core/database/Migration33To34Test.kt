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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 33 -> 34: Personal growth & living-journal tables (shipped by release/1.0-paid-launch as its 32).
 * Additive only: verifies existing notes/notebooks survive untouched and all 6 new tables operate cleanly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration33To34Test {
    private lateinit var context: Context
    private val dbName = "migration-33-34-test.db"
    private var migrated: MeetMindDatabase? = null

    private fun path() = context.getDatabasePath(dbName).path

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 33)
        SQLiteDatabase.openDatabase(path(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder, kind) " +
                    "VALUES ('nb_p', 'Personal', 'PERSONAL', NULL, NULL, 1000, 1000, NULL, 0, 'NOTEBOOK')"
            )
            db.execSQL(
                "INSERT INTO notes (id, title, workflow, notebookId, createdAt, updatedAt, pinned, isPrivate, status, metadataJson, plainText) " +
                    "VALUES ('note_j1', 'Evening reflection', 'JOURNAL', 'nb_p', 1000, 1000, 0, 1, 'OPEN', '{}', 'A calm walk')"
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
    fun databaseOpensAtTheCurrentVersionAndPreservesExistingNotes() {
        val db = openMigrated()
        assertEquals(MeetMindDatabase.VERSION, db.openHelper.readableDatabase.version)
        val note = runBlocking { db.noteDao().getById("note_j1") }
        assertNotNull(note)
        assertEquals("Evening reflection", note?.title)
    }

    @Test
    fun personalGrowthTablesSupportRoutinesStepsEnrollmentsAndDailyPlans() = runBlocking {
        val db = openMigrated()
        val dao = db.personalGrowthDao()

        val routine = PersonalRoutineEntity(
            id = "r1", title = "Morning Reset", timeWindow = "MORNING",
            scheduledMinuteOfDay = 450, anchorCue = "After coffee", createdAt = 1000, updatedAt = 1000
        )
        val steps = listOf(
            RoutineStepEntity(id = "s1", routineId = "r1", position = 0, kind = "BREATHING", title = "Breathe"),
            RoutineStepEntity(id = "s2", routineId = "r1", position = 1, kind = "INTENTION", title = "Set intention")
        )
        dao.saveRoutineWithSteps(routine, steps)

        assertEquals(1, dao.getActiveRoutines().size)
        assertEquals(2, dao.getStepsForRoutine("r1").size)

        val comp = RoutineCompletionEntity(
            id = "c1", routineId = "r1", epochDay = 20000L, status = "COMPLETED",
            currentStepIndex = 1, completedStepIdsJson = "[\"s1\",\"s2\"]",
            reflectionNoteId = "note_j1", startedAt = 1000, completedAt = 2000
        )
        dao.upsertCompletion(comp)
        assertEquals("COMPLETED", dao.latestCompletionForDay("r1", 20000L)?.status)

        val enrollment = GrowthEnrollmentEntity(
            id = "e1", kind = "JOURNEY", catalogId = "journey_better_morning",
            title = "Create a Better Morning", startedEpochDay = 20000L,
            targetStepsOrDays = 7, anchorWhyNoteId = "note_j1", lastActivityAt = 1500
        )
        dao.upsertEnrollment(enrollment)
        dao.upsertStepProgress(
            GrowthStepProgressEntity(
                id = "p1", enrollmentId = "e1", stepIndex = 0,
                completedEpochDay = 20000L, reflectionNoteId = "note_j1", completedAt = 1600
            )
        )
        assertEquals(1, dao.observeActiveEnrollments().first().size)
        assertEquals(1, dao.getStepProgress("e1").size)

        dao.upsertDailyPlan(
            PersonalDailyPlanEntity(
                epochDay = 20000L, intentionText = "Stay present",
                isMinEffortDay = true, moodVibe = "PEACEFUL", dayPageNoteId = "note_j1", updatedAt = 1700
            )
        )
        val loadedPlan = dao.getDailyPlan(20000L)
        assertNotNull(loadedPlan)
        assertEquals("Stay present", loadedPlan?.intentionText)
        assertTrue(loadedPlan?.isMinEffortDay == true)
    }
}
