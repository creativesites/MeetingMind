package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase

/**
 * The one-time move to items after the upgrade to schema 18 (docs/AGENT_BRIEF_PRO.md W7): every
 * work recording's findings are promoted, and every "waiting on" task becomes a commitment that
 * points at it. The task rows stay. Safe to run twice.
 */
object ItemBackfill {
    suspend fun run(database: MeetMindDatabase) {
        val work = WorkRepository(database)
        for (m in database.workDao().allMeetings()) {
            if (!work.isWork(m.recordingType)) continue
            // Recordings that were never through a Wrap-up come across unreviewed, out of the lists.
            // Their events carry the recording's date, so history doesn't read as news in "what changed".
            work.promoteToItems(m.id, reviewed = m.reviewedAt != null, clock = { m.createdAt })
        }
        waitingOnToCommitments(database)
    }

    internal suspend fun waitingOnToCommitments(database: MeetMindDatabase) {
        val dao = database.itemDao()
        for (t in database.taskDao().exportAll().filter { it.waitingOn && it.deletedAt == null }) {
            if (dao.commitmentsForTask(t.id).isNotEmpty()) continue
            val fromFinding = t.sourceItemId?.let { dao.bySourceFinding(it) }
            if (fromFinding != null) { ItemRepository(database) { t.createdAt }.setTask(fromFinding.id, t.id); continue }
            val note = t.noteId?.let { database.noteDao().getById(it) }
            val links = buildList {
                t.meetingId?.let { add(ItemLinkEntity("", LinkType.MEETING, it, "SOURCE")) }
                t.noteId?.let { add(ItemLinkEntity("", LinkType.NOTE, it, "SOURCE")) }
                note?.notebookId?.let { add(ItemLinkEntity("", LinkType.PROJECT, it, "PROJECT")) }
                t.personId?.let { add(ItemLinkEntity("", LinkType.PERSON, it, "OWNER")) }
            }
            ItemRepository(database) { t.createdAt }.create(
                ItemEntity(
                    id = "", kind = ItemKind.COMMITMENT.name, status = if (t.doneAt != null) ItemStatus.COMPLETED.name else ItemStatus.OPEN.name,
                    text = t.title, ownerPersonId = t.personId, ownerSpeakerId = t.ownerSpeakerId, projectId = note?.notebookId,
                    meetingId = t.meetingId, noteId = t.noteId, dueAt = t.dueAt, taskId = t.id, direction = Direction.THEIRS.name,
                    reviewed = true, source = ItemSource.USER, createdAt = t.createdAt, updatedAt = t.createdAt, closedAt = t.doneAt
                ),
                links = links
            )
        }
    }
}
