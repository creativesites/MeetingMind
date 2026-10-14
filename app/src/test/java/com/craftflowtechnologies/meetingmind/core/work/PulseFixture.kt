package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEvidenceEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.NotebookEntity
import com.craftflowtechnologies.meetingmind.core.database.NotePersonCrossRef
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * A working world for the Pulse, Prepare and context tests: an organisation (Acme) with a person
 * (Ana) and a project (Acme launch), a meeting with them five days ago (or as many as the test asks), and helpers for items.
 */
class PulseFixture(val db: MeetMindDatabase = ItemsFixture.database(), lastMeetingDaysAgo: Int = 5) {
    /** Wednesday 7 October 2026, 10:00. */
    val now: Long = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 7, 10, 0) }.timeInMillis
    val day = 86_400_000L
    val today = DueDates.startOfDay(now)
    val items = ItemRepository(db) { clockValue }
    var clockValue = now

    fun at(month: Int, dayOfMonth: Int, hour: Int = 12): Long = Calendar.getInstance().apply { clear(); set(2026, month, dayOfMonth, hour, 0) }.timeInMillis

    init {
        runBlocking {
            db.peopleDao().upsert(PersonEntity("org1", "Acme", null, "", 1, 1, kind = "ORG"))
            db.peopleDao().upsert(PersonEntity("ana", "Ana", null, "", 1, 1, orgId = "org1"))
            db.peopleDao().upsert(PersonEntity("bo", "Bo", null, "", 1, 1))
            db.notebookDao().upsert(NotebookEntity("nb", "Acme launch", "WORK", null, null, 1, 1, null, 0, kind = "PROJECT", propertiesJson = "{\"orgId\":\"org1\"}"))
            db.noteDao().upsert(NoteEntity("n1", "Acme review", "CLIENT_CALL", "nb", 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
            db.peopleDao().link(NotePersonCrossRef("n1", "ana"))
            db.meetingDao().insertMeeting(MeetingEntity("m1", "Acme review", now - lastMeetingDaysAgo * day, 60000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "CLIENT_CALL", noteId = "n1", reviewedAt = 1))
        }
    }

    fun item(
        kind: ItemKind, status: ItemStatus, text: String, direction: Direction? = null, due: Long? = null, owner: String? = null, project: String? = "nb", org: String? = "org1",
        created: Long = now - day, reviewed: Boolean = true, meeting: String? = "m1", quote: String? = null, startMs: Long? = null, taskId: String? = null, supersedes: String? = null
    ): ItemEntity = runBlocking {
        items.create(
            ItemEntity("", kind.name, status.name, text, ownerPersonId = owner, projectId = project, orgId = org, meetingId = meeting, dueAt = due, taskId = taskId,
                direction = direction?.name, reviewed = reviewed, supersedesId = supersedes, createdAt = created, updatedAt = created),
            evidence = listOfNotNull(quote?.let { ItemEvidenceEntity("", "", meeting, null, null, "[]", startMs, startMs?.plus(3000), it) }),
            links = listOfNotNull(project?.let { com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity("", LinkType.PROJECT, it, "PROJECT") })
        )
    }

    fun mine(text: String, due: Long? = null, status: ItemStatus = ItemStatus.OPEN, taskId: String? = null, project: String? = "nb", created: Long = now - day) =
        item(ItemKind.COMMITMENT, status, text, Direction.MINE, due, project = project, taskId = taskId, created = created)
    fun theirs(text: String, due: Long? = null, created: Long = now - day, owner: String = "ana", status: ItemStatus = ItemStatus.OPEN) =
        item(ItemKind.COMMITMENT, status, text, Direction.THEIRS, due, owner = owner, created = created)
}
