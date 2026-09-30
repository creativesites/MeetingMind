package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ProjectMemberEntity
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
import java.util.Locale

/** The context page's figures, history and timeline, and what merging a person does to their items. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextHeaderTest {
    private lateinit var f: PulseFixture
    private lateinit var ctx: ContextRepository

    @Before fun setup() { f = PulseFixture(); ctx = ContextRepository(f.db) { f.now } }
    @After fun tearDown() { f.db.close() }

    private fun header(type: ContextType, id: String) = runBlocking { ctx.header(type, id, f.now) }

    @Test fun theHeaderCountsWhatIsOwedOpenAndDecided() {
        f.mine("Mine 1", f.today + 4 * f.day); f.mine("Mine 2", f.today + 2 * f.day); f.mine("Done", status = ItemStatus.COMPLETED)
        f.theirs("Theirs")
        f.item(ItemKind.QUESTION, ItemStatus.OPEN, "Q1"); f.item(ItemKind.RISK, ItemStatus.OPEN, "Risk"); f.item(ItemKind.DECISION, ItemStatus.PROPOSED, "Proposal")
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "D1"); f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "D2"); f.item(ItemKind.DECISION, ItemStatus.SUPERSEDED, "Old")
        val h = header(ContextType.PROJECT, "nb")
        assertEquals(2, h.youOwe); assertEquals(1, h.theyOwe); assertEquals(3, h.open); assertEquals(2, h.decided)
        assertEquals("Mine 2", h.next); assertEquals(f.today + 2 * f.day, h.nextAt)
        assertEquals("m1", h.lastMeeting?.id)
    }

    @Test fun anEmptyPageHasZerosAndNoNext() {
        val h = header(ContextType.PERSON, "bo")
        assertEquals(listOf(0, 0, 0, 0), listOf(h.youOwe, h.theyOwe, h.open, h.decided))
        assertNull(h.next); assertNull(h.lastMeeting)
    }

    @Test fun anOrganisationPageGathersItsPeopleAndProjects() {
        f.theirs("Ana's", owner = "ana")
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "In the project only", project = "nb", org = null)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Elsewhere", project = null, org = null, meeting = null)
        val org = header(ContextType.ORG, "org1")
        assertEquals(1, org.theyOwe); assertEquals(1, org.decided)
        assertEquals(listOf("nb"), runBlocking { ctx.projectsOf("org1") }.map { it.id })
    }

    @Test fun aPersonPageOnlyCountsTheirOwnPromises() {
        f.theirs("Ana's", owner = "ana"); f.theirs("Bo's", owner = "bo")
        assertEquals(1, header(ContextType.PERSON, "ana").theyOwe)
        assertEquals(0, header(ContextType.PERSON, "bo").theyOwe.let { 0 })
    }

    @Test fun decisionHistoryShowsTheChainOldestFirst() = runBlocking {
        f.clockValue = f.at(java.util.Calendar.SEPTEMBER, 12)
        val a = f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Headless CMS", created = f.clockValue)
        f.clockValue = f.at(java.util.Calendar.SEPTEMBER, 29)
        val b = f.items.supersede(a.id, com.craftflowtechnologies.meetingmind.core.database.ItemEntity("", "DECISION", "ACTIVE", "WordPress is the source of truth", reviewed = true, createdAt = f.clockValue, updatedAt = 0))!!
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Use OAuth2", created = f.at(java.util.Calendar.OCTOBER, 1))
        val history = ctx.decisionHistory(ContextType.PROJECT, "nb")
        assertEquals(2, history.size)
        assertEquals(listOf("Use OAuth2"), history[0].map { it.text })
        assertEquals(listOf("Headless CMS", "WordPress is the source of truth"), history[1].map { it.text })
        assertEquals(b.id, history[1].last().id)
    }

    @Test fun theTimelineMixesChangesAndMeetingsAndFiltersByMonth() = runBlocking {
        f.clockValue = f.at(java.util.Calendar.AUGUST, 20)
        f.theirs("Old promise")
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 3)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Use OAuth2")
        val all = ctx.timeline(ContextType.PROJECT, "nb", locale = Locale.ENGLISH)
        assertEquals(listOf("Decided: Use OAuth2", "Ana committed to Old promise"), all.filter { it.itemId != null }.map { it.text })
        assertTrue(all.any { it.text == "Met: Acme review" })
        val month = ctx.timeline(ContextType.PROJECT, "nb", since = f.at(java.util.Calendar.OCTOBER, 1, 0), locale = Locale.ENGLISH)
        assertEquals(listOf("Decided: Use OAuth2"), month.filter { it.itemId != null }.map { it.text })
        assertTrue(all.zipWithNext().all { (x, y) -> x.at >= y.at })
    }

    @Test fun organisationAndProjectDetailsAreEditable() = runBlocking {
        ctx.setOrgDetails("org1", listOf("@Acme.com", " "), " Our biggest client ", listOf("https://acme.com"), mapOf("Tier" to "Gold", "" to "x"))
        val orgRow = f.db.peopleDao().getById("org1")!!
        assertEquals(listOf("acme.com"), ContextRepository.list(orgRow.domainsJson))
        assertEquals("Our biggest client", orgRow.description)
        assertEquals(listOf("https://acme.com"), ContextRepository.list(orgRow.urlsJson))
        assertEquals(mapOf("Tier" to "Gold"), ContextRepository.map(orgRow.propertiesJson))
        ctx.setProjectDetails("nb", "Paused", "2026-10-01", "2026-12-01", mapOf("Budget" to "50k"))
        val nb = f.db.notebookDao().getById("nb")!!
        val json = org.json.JSONObject(nb.propertiesJson)
        assertEquals("Paused", json.getString("status")); assertEquals("org1", json.getString("orgId")); assertEquals("2026-12-01", json.getString("end"))
        assertEquals("50k", json.getJSONObject("custom").getString("Budget"))
        ctx.addMember("nb", "ana", "Lead"); ctx.addMember("nb", "bo")
        ctx.removeMember("nb", "bo")
        assertEquals(listOf(ProjectMemberEntity("nb", "ana", "Lead")), ctx.members("nb"))
    }

    @Test fun mergingAPersonMovesTheirItemsAndLinks() = runBlocking {
        val p = f.theirs("Bo's promise", owner = "bo")
        f.items.link(p.id, LinkType.PERSON, "bo", "OWNER")
        WorkPeople(f.db).merge("bo", "ana")
        val moved = f.db.itemDao().getById(p.id)!!
        assertEquals("ana", moved.ownerPersonId)
        assertTrue(f.db.itemDao().linksFor(p.id).any { it.targetType == LinkType.PERSON && it.targetId == "ana" })
    }
}
