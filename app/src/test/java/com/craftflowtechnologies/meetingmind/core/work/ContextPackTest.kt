package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One bounded, scoped bundle of items, quotes, meetings and changes, citable by id. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextPackTest {
    private lateinit var f: PulseFixture
    private lateinit var w: Map<String, com.craftflowtechnologies.meetingmind.core.database.ItemEntity>

    @Before fun setup() { f = PulseFixture(); w = f.projectWorld() }
    @After fun tearDown() { f.db.close() }

    private fun pack(scope: PackScope, from: Long? = null, to: Long? = null, onlyChanged: Boolean = false, max: Int = ContextPackBuilder.MAX_ITEMS, privacy: PackPrivacy = PackPrivacy { _, _ -> false }) =
        runBlocking { ContextPackBuilder(f.db, privacy) { f.now }.build(scope, from, to, onlyChanged, max) }

    @Test fun aProjectPackHoldsItsItemsQuotesAndMeeting() {
        val p = pack(PackScope.Entity(ContextType.PROJECT, "nb"))
        assertEquals("Acme launch", p.title)
        assertTrue(p.items.map { it.text }.containsAll(listOf("Launch on October 21", "Send the deck", "Vendor may be late", "Adopt headless CMS")))
        assertEquals("I'll send the deck.", p.evidenceFor(w.getValue("deck").id)!!.quote)
        assertEquals("Acme review", p.evidenceFor(w.getValue("deck").id)!!.meetingTitle)
        assertEquals(listOf("m1"), p.meetings.map { it.id })
        assertTrue(p.events.isNotEmpty())
    }

    @Test fun everyIdInThePackCanBeCitedAndNothingElse() {
        val p = pack(PackScope.Entity(ContextType.PROJECT, "nb"))
        assertTrue(w.getValue("deck").id in p.ids)
        assertTrue(p.evidenceFor(w.getValue("deck").id)!!.id in p.ids)
        assertTrue("m1" in p.ids)
        assertFalse("invented" in p.ids)
    }

    @Test fun scopesAreScoped() {
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Elsewhere", project = null, org = null, meeting = null)
        assertFalse(pack(PackScope.Entity(ContextType.PROJECT, "nb")).items.any { it.text == "Elsewhere" })
        assertTrue(pack(PackScope.AllWork).items.any { it.text == "Elsewhere" })
        assertEquals(listOf("Ana's promise"), f.run { theirs("Ana's promise", owner = "ana"); pack(PackScope.Entity(ContextType.PERSON, "ana")).items.filter { it.text == "Ana's promise" }.map { it.text } })
        assertEquals("Ana", pack(PackScope.Entity(ContextType.PERSON, "ana")).title)
        val m = pack(PackScope.Meeting("m1"))
        assertTrue(m.items.all { it.meetingId == "m1" })
        assertEquals("Acme review", m.title)
    }

    @Test fun aPackIsBoundedMostPressingFirst() {
        val small = pack(PackScope.AllWork, max = 3)
        assertEquals(3, small.items.size)
        assertEquals("Send the deck", small.items.first().text) // the overdue promise leads
        assertEquals("Adopt headless CMS", small.items[1].text) // then the decision still to make
        assertTrue(small.ids.size <= 3 + small.evidence.size + small.meetings.size)
    }

    @Test fun aRangeKeepsWhatChangedInItPlusWhatIsStillOpen() {
        val recent = pack(PackScope.Entity(ContextType.PROJECT, "nb"), from = f.now - 4 * f.day)
        assertTrue(recent.items.any { it.text == "Launch on October 21" }) // changed 3 days ago
        assertTrue(recent.items.any { it.text == "Launch on October 14" }) // superseded 3 days ago: that is a change in the range
        f.clockValue = f.now - 60 * f.day
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Settled long ago", created = f.now - 60 * f.day)
        f.clockValue = f.now
        assertFalse(pack(PackScope.Entity(ContextType.PROJECT, "nb"), from = f.now - 4 * f.day).items.any { it.text == "Settled long ago" })
        assertTrue(recent.items.any { it.text == "Send the deck" }) // open
        val onlyChanged = pack(PackScope.Entity(ContextType.PROJECT, "nb"), from = f.now - 4 * f.day, onlyChanged = true)
        assertTrue(onlyChanged.items.all { i -> i.updatedAt >= f.now - 4 * f.day || onlyChanged.events.any { it.itemId == i.id } })
    }

    @Test fun quotesAndSummariesAreTrimmed() {
        f.item(ItemKind.RISK, ItemStatus.OPEN, "Long one", quote = "word ".repeat(200), startMs = 1)
        runBlocking { f.db.meetingDao().insertMeeting(f.db.meetingDao().getMeetingById("m1")!!.copy(summaryText = "s".repeat(1000))) }
        val p = pack(PackScope.Entity(ContextType.PROJECT, "nb"))
        assertTrue(p.evidence.all { it.quote.length <= ContextPackBuilder.QUOTE })
        assertTrue(p.meetings.all { (it.summary?.length ?: 0) <= ContextPackBuilder.SUMMARY })
    }

    @Test fun itKnowsWhenItsMaterialMustStayOnThePhone() {
        assertFalse(pack(PackScope.AllWork).sensitive)
        var seen: List<String>? = null
        val p = pack(PackScope.Entity(ContextType.PROJECT, "nb"), privacy = PackPrivacy { ids, _ -> seen = ids; true })
        assertTrue(p.sensitive)
        assertEquals(listOf("m1"), seen)
    }

    @Test fun theRenderedPackLeadsEveryFactWithItsId() {
        val text = pack(PackScope.Entity(ContextType.PROJECT, "nb")).render()
        assertTrue(text.contains("[${w.getValue("deck").id}] COMMITMENT OPEN: Send the deck (mine, due "))
        assertTrue(text.contains("said ["))
        assertTrue(text.contains("Meetings:") && text.contains("Recent changes:"))
        assertTrue(text.contains("Launch moved Oct 14 → Oct 21"))
    }
}
