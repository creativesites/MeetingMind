package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.PersonEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
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

/** The upgrade to items: "waiting on" tasks become commitments that keep their task, and history is promoted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaitingOnMigrationTest {
    private lateinit var db: MeetMindDatabase

    @Before fun setup() { db = ItemsFixture.database() }
    @After fun tearDown() { db.close() }

    private fun task(id: String, waiting: Boolean, doneAt: Long? = null, sourceItemId: String? = null, deleted: Long? = null) = TaskEntity(
        id, "Get ", "", "TASK", 9_000, null, "NONE", doneAt, "ana", null, null, null, null, null, 1, 1, deleted,
        waitingOn = waiting, sourceItemId = sourceItemId, space = "WORK"
    )

    @Test fun eachWaitingOnTaskBecomesATheirsCommitmentAndKeepsItsTask() = runBlocking {
        ItemsFixture.seed(db, reviewed = true)
        db.taskDao().upsert(task("t1", waiting = true))
        db.taskDao().upsert(task("t2", waiting = true, doneAt = 5))
        db.taskDao().upsert(task("t3", waiting = false))
        db.taskDao().upsert(task("t4", waiting = true, deleted = 3))
        ItemBackfill.waitingOnToCommitments(db)
        val c1 = db.itemDao().commitmentsForTask("t1").single()
        assertEquals("THEIRS", c1.direction); assertEquals("OPEN", c1.status); assertEquals("ana", c1.ownerPersonId); assertEquals(9_000L, c1.dueAt)
        assertTrue(c1.reviewed)
        assertEquals("COMPLETED", db.itemDao().commitmentsForTask("t2").single().status)
        assertTrue(db.itemDao().commitmentsForTask("t3").isEmpty())
        assertTrue(db.itemDao().commitmentsForTask("t4").isEmpty())
        assertEquals(4, db.taskDao().exportAll().size)
        assertNotNull(db.taskDao().getById("t1"))
    }

    @Test fun runningTwiceMakesNoDuplicates() = runBlocking {
        db.peopleDao().upsert(PersonEntity("ana", "Ana", null, "", 1, 1))
        db.taskDao().upsert(task("t1", waiting = true))
        ItemBackfill.waitingOnToCommitments(db)
        ItemBackfill.waitingOnToCommitments(db)
        assertEquals(1, db.itemDao().allLive().size)
    }

    @Test fun aWaitingOnTaskFromAFindingJoinsTheItemPromotedFromIt() = runBlocking {
        ItemsFixture.seed(db, reviewed = true)
        db.taskDao().upsert(task("t1", waiting = true, sourceItemId = "a1").copy(meetingId = "m1"))
        ItemBackfill.run(db)
        val item = db.itemDao().bySourceFinding("a1")!!
        assertEquals("t1", item.taskId)
        assertEquals(1, db.itemDao().allLive().count { it.taskId == "t1" })
    }

    @Test fun everyReviewedMeetingsFindingsExistAsItemsWithEvidence() = runBlocking {
        ItemsFixture.seed(db, reviewed = true)
        ItemBackfill.run(db)
        val live = db.itemDao().allLive()
        // Decisions, unresolved and resolved questions, and the promises from both meetings.
        assertEquals(setOf("d1", "d2", "q1", "q2", "a1", "a2", "a3"), live.mapNotNull { it.sourceFindingId }.toSet())
        assertTrue(live.all { it.reviewed })
        for (id in listOf("d1", "d2", "q1", "a1", "a3")) assertEquals(1, db.itemDao().evidenceFor(db.itemDao().bySourceFinding(id)!!.id).size)
        assertEquals("m2", db.itemDao().bySourceFinding("d2")!!.meetingId)
        // The findings themselves are untouched.
        assertEquals(3, db.actionItemDao().getActionItemsForMeetingDirect("m1").size + db.actionItemDao().getActionItemsForMeetingDirect("m2").size)
    }

    @Test fun recordingsNobodyReviewedComeAcrossUnreviewed() = runBlocking {
        ItemsFixture.seed(db, reviewed = false)
        ItemBackfill.run(db)
        assertTrue(db.itemDao().allLive().isNotEmpty())
        assertTrue(db.itemDao().allLive().none { it.reviewed })
    }
}
