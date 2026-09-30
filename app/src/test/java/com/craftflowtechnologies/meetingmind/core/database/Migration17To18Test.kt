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

/** 17 → 18: items and their tables, project members, and an organisation's fields; every row survives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration17To18Test {
    private lateinit var context: Context
    private val dbName = "migration-17-18-test.db"
    private var migrated: MeetMindDatabase? = null
    private val tables = listOf("people", "tasks", "meetings", "speakers", "notebooks", "notes", "action_items", "decisions", "questions", "follow_ups", "note_people", "transcript_segments")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        ExportedSchema.create(context.getDatabasePath(dbName), 17)
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO people (id, name, relationship, notes, createdAt, updatedAt, deletedAt, kind, orgId, emailsJson, phonesJson, aliasesJson, isSelf, confidential) VALUES ('org', 'Acme', NULL, '', 1, 1, NULL, 'ORG', NULL, '[]', '[]', '[]', 0, 0)")
            db.execSQL("INSERT INTO people (id, name, relationship, notes, createdAt, updatedAt, deletedAt, kind, orgId, emailsJson, phonesJson, aliasesJson, isSelf, confidential) VALUES ('p1', 'Mary', 'Friend', '', 1, 1, NULL, 'PERSON', 'org', '[\"m@acme.com\"]', '[]', '[]', 0, 0)")
            db.execSQL(
                "INSERT INTO tasks (id, title, notes, kind, dueAt, remindAt, repeat, doneAt, personId, noteId, blockId, meetingId, startMs, scripture, createdAt, updatedAt, deletedAt, waitingOn) " +
                    "VALUES ('t1', 'Get the keys', '', 'TASK', NULL, NULL, 'NONE', NULL, 'p1', NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, 1)"
            )
            db.execSQL(
                "INSERT INTO meetings (id, title, createdAt, durationMs, source, audioFilePath, status, participantCount, language, summaryText, updatedAt, recordingType, processingProfile, processingVersion) " +
                    "VALUES ('m1', 'Review', 5, 1000, 'LOCAL_RECORDING', NULL, 'READY', 2, 'en', NULL, 7, 'MEETING', 'OFFLINE', 1)"
            )
            db.execSQL("INSERT INTO decisions (id, meetingId, text, type, confidence, sourceSegmentIdsJson) VALUES ('d1', 'm1', 'Use OAuth2', 'DECISION', 0.9, '[]')")
            db.execSQL("INSERT INTO notebooks (id, name, space, colorHex, icon, createdAt, updatedAt, archivedAt, sortOrder, deletedAt, kind, propertiesJson) VALUES ('nb', 'Acme', 'WORK', NULL, NULL, 1, 1, NULL, 0, NULL, 'PROJECT', '{}')")
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
            .addMigrations(MeetMindDatabase.MIGRATION_17_18, MeetMindDatabase.MIGRATION_18_19, MeetMindDatabase.MIGRATION_19_20)
            .allowMainThreadQueries().build().also { migrated = it }

    @Test
    fun everyExistingRowSurvives() = runBlocking {
        val before = tables.associateWith { count(it) }
        val db = open()
        db.openHelper.writableDatabase // migrate and let Room validate the result
        migrated!!.close()
        assertEquals(before, tables.associateWith { count(it) })
        assertTrue(before.getValue("people") == 2 && before.getValue("decisions") == 1)
    }

    @Test
    fun peopleGainTheirOrganisationFieldsWithDefaults() = runBlocking {
        val db = open()
        val acme = db.peopleDao().getById("org")!!
        assertEquals("[]", acme.domainsJson); assertEquals("", acme.description); assertEquals("[]", acme.urlsJson)
        assertNull(acme.logoPath); assertEquals("{}", acme.propertiesJson)
        val mary = db.peopleDao().getById("p1")!!
        assertEquals("[\"m@acme.com\"]", mary.emailsJson); assertEquals("org", mary.orgId)
        assertEquals(true, db.taskDao().getById("t1")!!.waitingOn)
    }

    @Test
    fun theNewTablesWorkAndTheSourceFindingIsUnique() = runBlocking {
        val db = open()
        val dao = db.itemDao()
        dao.insert(ItemEntity("i1", "DECISION", "ACTIVE", "Use OAuth2", sourceFindingId = "d1", createdAt = 1, updatedAt = 1))
        dao.insert(ItemEntity("i2", "DECISION", "ACTIVE", "Typed by hand", createdAt = 1, updatedAt = 1))
        dao.insert(ItemEntity("i3", "DECISION", "ACTIVE", "Typed by hand too", createdAt = 1, updatedAt = 1))
        assertEquals("ACTIVE", dao.getById("i1")!!.status)
        assertEquals(false, dao.getById("i1")!!.reviewed)
        assertEquals("WORK", dao.getById("i1")!!.space)
        assertEquals("AI", dao.getById("i1")!!.source)
        val duplicate = runCatching { dao.insert(ItemEntity("i4", "DECISION", "ACTIVE", "again", sourceFindingId = "d1", createdAt = 1, updatedAt = 1)) }
        assertTrue(duplicate.isFailure)
        dao.insertEvidence(ItemEvidenceEntity("e1", "i1", "m1", quote = "we go with OAuth2"))
        dao.insertLink(ItemLinkEntity("i1", "PROJECT", "nb", "PROJECT"))
        dao.insertEvent(ItemEventEntity("v1", "i1", "ITEM", "i1", "CREATED", at = 5))
        dao.upsertMember(ProjectMemberEntity("nb", "p1", "Lead"))
        assertEquals(1, dao.evidenceFor("i1").size)
        assertEquals(1, dao.linksFor("i1").size)
        assertEquals(1, dao.eventsSince(0).size)
        assertEquals("Lead", dao.membersOf("nb").single().role)
    }
}
