package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The four saved-filter chips answer from the database, with no model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedViewsTest {
    private lateinit var f: PulseFixture
    private lateinit var saved: SavedViews

    @Before fun setup() { f = PulseFixture(); saved = SavedViews(f.db) { f.now } }
    @After fun tearDown() { f.db.close() }

    private fun answer(filter: SavedFilter, scope: Pair<ContextType, String>? = null) = runBlocking { saved.answer(filter, scope) }.lines

    @Test fun whatHaveIPromisedListsMyOpenCommitmentsSoonestFirst() {
        f.mine("Later", f.today + 9 * f.day); f.mine("Overdue", f.today - f.day); f.mine("No date"); f.mine("Done", status = ItemStatus.COMPLETED)
        f.theirs("Not mine")
        f.item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Unreviewed", Direction.MINE, reviewed = false)
        val lines = answer(SavedFilter.PROMISED)
        assertEquals(listOf("Overdue", "Later", "No date"), lines.map { it.text })
        assertTrue(lines[0].detail!!.startsWith("overdue since"))
        assertEquals("no date", lines[2].detail)
        assertTrue(lines.all { it.itemId != null })
    }

    @Test fun whoIsWaitingOnMeGroupsByThePersonItIsOwedTo() {
        val a = f.mine("For Ana", f.today + f.day)
        f.items.let { runBlocking { it.link(a.id, LinkType.PERSON, "ana", "PARTICIPANT") } }
        f.mine("For nobody in particular")
        val lines = answer(SavedFilter.WAITING_ON_ME)
        assertEquals("For Ana", lines[0].text); assertEquals("ana", lines[0].personId)
        assertTrue(lines[0].detail!!.startsWith("Ana · due"))
        assertEquals("For nobody in particular", lines[1].text); assertEquals(null, lines[1].personId)
    }

    @Test fun activeDecisionsCoverThreeMonths() {
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Recent", created = f.now - 10 * f.day)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Too old", created = f.now - 100 * f.day)
        f.item(ItemKind.DECISION, ItemStatus.SUPERSEDED, "Replaced", created = f.now - 10 * f.day)
        assertEquals(listOf("Recent"), answer(SavedFilter.ACTIVE_DECISIONS).map { it.text })
    }

    @Test fun whatChangedThisMonthIsThisMonthsSentences() {
        f.clockValue = f.at(java.util.Calendar.SEPTEMBER, 20)
        f.theirs("Last month's promise")
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 2)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Use OAuth2")
        assertEquals(listOf("Decided: Use OAuth2"), answer(SavedFilter.CHANGED_THIS_MONTH).map { it.text })
    }

    @Test fun chipsCanBeScopedToAContextPage() {
        f.mine("In the project"); f.mine("Elsewhere", project = null)
        assertEquals(listOf("In the project"), answer(SavedFilter.PROMISED, ContextType.PROJECT to "nb").map { it.text })
    }
}
