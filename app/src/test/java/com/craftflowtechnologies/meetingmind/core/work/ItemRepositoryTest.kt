package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.tasks.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every write to an item logs an event, and supersede sets both the status and the link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ItemRepositoryTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var items: ItemRepository
    private var now = 1_000L

    @Before fun setup() { db = ItemsFixture.database(); items = ItemRepository(db) { now++ } }
    @After fun tearDown() { db.close() }

    private fun item(kind: ItemKind = ItemKind.DECISION, status: ItemStatus = ItemStatus.ACTIVE, text: String = "Use OAuth2") =
        ItemEntity("", kind.name, status.name, text, createdAt = 0, updatedAt = 0)

    private fun types(id: String) = runBlocking { items.events(id).map { it.type } }

    @Test fun createLogsTheEventInTheSameStep() = runBlocking {
        val a = items.create(item())
        assertEquals(listOf("CREATED"), types(a.id))
        assertNotNull(items.get(a.id))
    }

    @Test fun everyKindOfChangeLogsAnEvent() = runBlocking {
        val a = items.create(item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Send the docs"))
        items.setText(a.id, "Send the API docs")
        items.setDue(a.id, 5_000, "Friday")
        items.setOwner(a.id, "ana")
        items.setProject(a.id, "nb", "org1")
        items.setStatus(a.id, ItemStatus.COMPLETED)
        items.link(a.id, LinkType.PERSON, "ana", "OWNER")
        assertEquals(listOf("CREATED", "TEXT_CHANGED", "DUE_CHANGED", "OWNER_CHANGED", "PROJECT_CHANGED", "STATUS", "LINKED"), types(a.id))
        val done = items.get(a.id)!!
        assertEquals("Send the API docs", done.text)
        assertEquals("ana", done.ownerPersonId)
        assertNotNull(done.closedAt)
        val status = items.events(a.id).first { it.type == "STATUS" }
        assertTrue(status.beforeJson!!.contains("OPEN") && status.afterJson!!.contains("COMPLETED"))
    }

    @Test fun aChangeThatChangesNothingLogsNothing() = runBlocking {
        val a = items.create(item())
        items.setText(a.id, "Use OAuth2")
        items.setStatus(a.id, ItemStatus.ACTIVE)
        items.setDue(a.id, null, null)
        assertEquals(listOf("CREATED"), types(a.id))
    }

    @Test fun supersedeSetsBothTheStatusAndTheLink() = runBlocking {
        val old = items.create(item(text = "Launch October 14").copy(projectId = "nb", orgId = "org1"))
        val new = items.supersede(old.id, item(text = "Launch October 21"))!!
        assertEquals(ItemStatus.SUPERSEDED.name, items.get(old.id)!!.status)
        assertEquals(old.id, new.supersedesId)
        assertEquals("nb", new.projectId)
        assertTrue(items.links(new.id).any { it.targetType == LinkType.ITEM && it.targetId == old.id && it.role == "SUPERSEDES" })
        assertEquals(listOf("CREATED", "SUPERSEDED"), types(old.id))
        assertEquals(listOf("CREATED"), types(new.id))
        assertEquals(listOf(old.id, new.id), items.chain(new.id).map { it.id })
    }

    @Test fun answeringAQuestionClosesItAndLogsIt() = runBlocking {
        val q = items.create(item(ItemKind.QUESTION, ItemStatus.OPEN, "Which region?"))
        items.answer(q.id, "EU")
        val got = items.get(q.id)!!
        assertEquals(ItemStatus.ANSWERED.name, got.status)
        assertEquals("EU", got.answerText)
        assertEquals(listOf("CREATED", "ANSWERED"), types(q.id))
    }

    @Test fun deletedItemsLeaveTheListsButKeepTheirHistory() = runBlocking {
        val a = items.create(item())
        items.delete(a.id)
        assertTrue(db.itemDao().allLive().isEmpty())
        assertEquals(2, types(a.id).size)
    }

    @Test fun overdueAndDueSoonAreComputedNotStored() {
        val day = 86_400_000L
        val today = DueDates.startOfDay(10 * day)
        val open = ItemEntity("x", "COMMITMENT", "OPEN", "t", dueAt = today - day, createdAt = 0, updatedAt = 0)
        assertTrue(open.isOverdue(10 * day))
        assertFalse(open.copy(status = "COMPLETED").isOverdue(10 * day))
        assertTrue(open.copy(dueAt = today + day).isDueSoon(10 * day))
        assertFalse(open.copy(dueAt = null).isOverdue(10 * day))
    }

    @Test fun eachKindHasItsOwnStatuses() {
        assertEquals(ItemStatus.ACTIVE, ItemStatus.allowedFor(ItemKind.DECISION).first())
        assertTrue(ItemStatus.UNCLEAR in ItemStatus.allowedFor(ItemKind.COMMITMENT))
        assertEquals(listOf(ItemStatus.OPEN, ItemStatus.CLOSED), ItemStatus.allowedFor(ItemKind.RISK))
    }
}
