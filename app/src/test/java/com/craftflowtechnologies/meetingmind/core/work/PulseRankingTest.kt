package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pulse ranks what needs the person in a fixed order and never shows more than four rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PulseRankingTest {
    private lateinit var f: PulseFixture
    private lateinit var pulse: Pulse

    @Before fun setup() { f = PulseFixture(); pulse = Pulse(f.db) { f.now } }
    private fun quietWorld() { f.db.close(); f = PulseFixture(lastMeetingDaysAgo = 40); pulse = Pulse(f.db) { f.now } }
    @After fun tearDown() { f.db.close() }

    private fun attention(limit: Int = 4, projects: Set<String> = emptySet()) = runBlocking { pulse.attention(f.now, limit, projects) }

    @Test fun whatIOweOverdueOrDueTodayComesFirstOldestFirst() {
        f.mine("Due today", f.today + 3_600_000)
        f.mine("Overdue", f.today - f.day)
        f.mine("Due next week", f.today + 7 * f.day)
        f.theirs("They are late", f.today - 2 * f.day)
        val rows = attention()
        assertEquals(listOf("Overdue", "Due today", "They are late"), rows.map { it.title })
        assertEquals(listOf(AttentionKind.YOU_OWE, AttentionKind.YOU_OWE, AttentionKind.THEY_OWE), rows.map { it.kind })
        assertTrue(rows[0].detail.startsWith("Overdue since"))
        assertEquals("Due today · Acme launch", rows[1].detail)
    }

    @Test fun aTaskWithNoCommitmentCountsButOneWithACommitmentIsNotCountedTwice() = runBlocking {
        f.db.taskDao().upsert(TaskEntity("t1", "Book flights", "", "TASK", f.today, null, "NONE", null, null, null, null, null, null, null, 1, 1, space = "WORK"))
        f.db.taskDao().upsert(TaskEntity("t2", "Send contract", "", "TASK", f.today, null, "NONE", null, null, null, null, null, null, null, 1, 1, space = "WORK"))
        f.mine("Send contract", f.today, taskId = "t2")
        val titles = attention().map { it.title }
        assertEquals(listOf("Book flights", "Send contract"), titles.sorted())
        assertEquals(2, titles.size)
    }

    @Test fun proposedDecisionsComeNextThoseOnAProjectWithAMeetingTodayFirst() {
        f.item(ItemKind.DECISION, ItemStatus.PROPOSED, "Vendor choice", project = null, org = null, created = f.now - 1)
        f.item(ItemKind.DECISION, ItemStatus.PROPOSED, "Pricing model", created = f.now - 2 * f.day)
        f.mine("Overdue", f.today - f.day)
        val rows = attention(projects = setOf("nb"))
        assertEquals(listOf("Overdue", "Pricing model", "Vendor choice"), rows.map { it.title })
        assertTrue(rows[1].detail.contains("on today's agenda"))
        assertEquals(AttentionKind.DECISION_NEEDED, rows[1].kind)
        // Without a meeting today the newer one leads.
        assertEquals(listOf("Vendor choice", "Pricing model"), attention().filter { it.kind == AttentionKind.DECISION_NEEDED }.map { it.title })
    }

    @Test fun whatTheyOweCountsWhenOverdueOrOpenFiveDaysOrMore() {
        f.theirs("Fresh", created = f.now - 2 * f.day)
        f.theirs("Old", created = f.now - 6 * f.day)
        f.theirs("Overdue", due = f.today - f.day, created = f.now - day())
        f.theirs("Future date", due = f.today + 3 * f.day, created = f.now - 2 * f.day)
        assertEquals(listOf("Overdue", "Old"), attention().map { it.title })
        val old = attention().last()
        assertEquals("Ana · open 6 days", old.detail)
        assertTrue(AttentionAction.NUDGE in old.actions)
    }

    private fun day() = f.day

    @Test fun aQuietRelationshipWithOpenItemsClosesTheList() {
        quietWorld()
        f.theirs("Brand assets", created = f.now - 3 * f.day) // 3 days: not yet on its own
        val rows = attention()
        val quiet = rows.single()
        assertEquals(AttentionKind.QUIET, quiet.kind)
        assertEquals("Acme", quiet.title)
        assertEquals("No meeting for 40 days · 1 open", quiet.detail)
        assertEquals(ContextType.ORG, quiet.entityType)
        assertTrue(AttentionAction.PREPARE in quiet.actions)
        // Nobody is quiet if the window is longer than the gap.
        assertTrue(runBlocking { pulse.attention(f.now, 4, emptySet(), quietDays = 60) }.isEmpty())
    }

    @Test fun neverMoreThanFourRowsAndTheOrderHoldsAcrossTiers() {
        repeat(3) { f.mine("Mine $it", f.today - (it + 1) * f.day) }
        f.item(ItemKind.DECISION, ItemStatus.PROPOSED, "Decide")
        f.theirs("Late", f.today - f.day)
        f.theirs("Old", created = f.now - 9 * f.day)
        val four = attention()
        assertEquals(4, four.size)
        assertEquals(listOf(AttentionKind.YOU_OWE, AttentionKind.YOU_OWE, AttentionKind.YOU_OWE, AttentionKind.DECISION_NEEDED), four.map { it.kind })
        val all = attention(limit = 20)
        assertEquals(listOf(AttentionKind.YOU_OWE, AttentionKind.YOU_OWE, AttentionKind.YOU_OWE, AttentionKind.DECISION_NEEDED, AttentionKind.THEY_OWE, AttentionKind.THEY_OWE), all.map { it.kind }.take(6))
    }

    @Test fun finishedUnreviewedAndCancelledItemsNeverShow() {
        f.mine("Done", f.today - f.day, status = ItemStatus.COMPLETED)
        f.mine("Cancelled", f.today - f.day, status = ItemStatus.CANCELLED)
        f.item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Unreviewed", Direction.MINE, f.today - f.day, reviewed = false)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Already decided")
        assertTrue(attention().isEmpty())
    }

    @Test fun rowsCarryWhatTheirActionsNeed() {
        f.item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Send the deck", Direction.MINE, f.today - f.day, quote = "I'll send the deck", startMs = 42_000)
        val row = attention().first()
        assertEquals("m1", row.meetingId); assertEquals(42_000L, row.startMs)
        assertTrue(AttentionAction.PLAY in row.actions)
        assertEquals(ContextType.PROJECT, row.entityType); assertEquals("nb", row.entityId)
    }
}
