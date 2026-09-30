package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Confirming the Wrap-up promotes findings to items, once each, with evidence and context. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromotionTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var work: WorkRepository

    @Before fun setup() { db = ItemsFixture.database(); ItemsFixture.seed(db); work = WorkRepository(db) }
    @After fun tearDown() { db.close() }

    private suspend fun byFinding(id: String) = db.itemDao().bySourceFinding(id)!!

    @Test fun confirmingPromotesEachKind() = runBlocking {
        work.confirm("m1")
        assertEquals(listOf("ACTIVE"), listOf(byFinding("d1").status))
        assertEquals("DECISION", byFinding("d1").kind)
        val q = byFinding("q1")
        assertEquals("QUESTION", q.kind); assertEquals("OPEN", q.status)
        val theirs = byFinding("a1")
        assertEquals("COMMITMENT", theirs.kind); assertEquals("THEIRS", theirs.direction); assertEquals("spk1", theirs.ownerSpeakerId)
        val mine = byFinding("a2")
        assertEquals("MINE", mine.direction); assertNull(mine.ownerPersonId)
        // The action I own is linked to the task confirm made.
        val task = db.workDao().tasksForMeeting("m1").first { it.sourceItemId == "a2" }
        assertEquals(task.id, mine.taskId)
        assertTrue(theirs.reviewed && mine.reviewed)
        // A question already answered in the recording comes across answered.
        val answered = byFinding("q2")
        assertEquals("ANSWERED", answered.status); assertEquals("EU", answered.answerText)
    }

    @Test fun promotingTwiceMakesNoDuplicates() = runBlocking {
        work.confirm("m1")
        val first = db.itemDao().allLive().size
        val events = db.itemDao().eventsSince(0).size
        work.confirm("m1")
        work.promoteToItems("m1", reviewed = true)
        assertEquals(first, db.itemDao().allLive().size)
        assertEquals(events, db.itemDao().eventsSince(0).size)
    }

    @Test fun evidenceIsCopiedFromTheTranscript() = runBlocking {
        work.confirm("m1")
        val ev = db.itemDao().evidenceFor(byFinding("a1").id).single()
        assertEquals("m1", ev.meetingId)
        assertEquals("[\"s1\"]", ev.segmentIdsJson)
        assertEquals(0L, ev.startMs); assertEquals(20_000L, ev.endMs)
        assertEquals("I'll send the docs by Friday.", ev.quote)
        assertTrue(db.itemDao().evidenceFor(byFinding("a2").id).isEmpty())
    }

    @Test fun projectAndOrganisationAreInheritedFromTheNotebook() = runBlocking {
        work.confirm("m1")
        work.confirm("m2")
        val decision = byFinding("d2")
        assertEquals("nb", decision.projectId); assertEquals("org1", decision.orgId)
        val links = db.itemDao().linksFor(decision.id).map { it.targetType to it.targetId }
        assertTrue(LinkType.PROJECT to "nb" in links)
        assertTrue(LinkType.ORG to "org1" in links)
        assertTrue(LinkType.MEETING to "m2" in links)
        assertTrue(LinkType.PERSON to "ana" in links)
        val ana = byFinding("a3")
        assertEquals("ana", ana.ownerPersonId)
        assertNull(byFinding("d1").projectId)
    }

    @Test fun unreviewedPromotionStaysOutOfTheListsUntilConfirmed() = runBlocking {
        work.promoteToItems("m1", reviewed = false)
        assertFalse(byFinding("a1").reviewed)
        assertTrue(runBlocking { db.itemDao().observeCommitments("THEIRS").first() }.isEmpty())
        // The person edits and dismisses in the Wrap-up, then confirms.
        work.dismiss(work.findings("m1").first { it.id == "a2" })
        work.confirm("m1")
        assertTrue(byFinding("a1").reviewed)
        assertNotNull(db.itemDao().getById(byFinding("a2").id)!!.deletedAt)
        assertEquals(1, db.itemDao().observeCommitments("THEIRS").first().size)
    }
}
