package com.craftflowtechnologies.meetingmind.core.work

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

/** Prepare builds what to bring to a conversation from items alone, with a real quote from last time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrepareTest {
    private lateinit var f: PulseFixture
    private lateinit var prepare: Prepare

    @Before fun setup() { f = PulseFixture(); prepare = Prepare(f.db) { f.now } }
    @After fun tearDown() { f.db.close() }

    private fun project() = runBlocking { prepare.forEntity(ContextType.PROJECT, "nb", f.now) }

    @Test fun lastTimeIsTheLastMeetingWithAQuoteFromIt() {
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Use OAuth2", quote = "We go with OAuth2.", startMs = 120_000)
        f.item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Send the docs", Direction.MINE, quote = "I'll send the docs by Friday.", startMs = 20_000)
        val last = project().lastMeeting!!
        assertEquals("m1", last.meetingId)
        assertEquals("Acme review", last.title)
        assertEquals("We go with OAuth2.", last.quote)          // the latest moment said, not the first
        assertEquals(120_000L, last.startMs)
        assertNotNull(last.itemId)
    }

    @Test fun splitsWhatIsOwedAndListsDecisionsAndQuestions() {
        f.mine("Send the docs", f.today + f.day)
        f.mine("Already sent", status = ItemStatus.COMPLETED)
        f.theirs("API keys", f.today - f.day)
        f.item(ItemKind.QUESTION, ItemStatus.OPEN, "Which region?")
        f.item(ItemKind.QUESTION, ItemStatus.ANSWERED, "Old question")
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Use OAuth2", created = f.now - 10 * f.day)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Too old", created = f.now - 120 * f.day)
        f.item(ItemKind.DECISION, ItemStatus.SUPERSEDED, "Replaced")
        val pack = project()
        assertEquals(listOf("Send the docs"), pack.youOwe.map { it.text })
        assertEquals(listOf("API keys"), pack.theyOwe.map { it.text })
        assertEquals(listOf("Which region?"), pack.questions.map { it.text })
        assertEquals(listOf("Use OAuth2"), pack.decisions.map { it.text })
        assertEquals(3, pack.stillOpen)
    }

    @Test fun theAgendaIsOpenItemsAndProposedDecisionsRanked() {
        f.mine("Mine, later", f.today + 5 * f.day)
        f.item(ItemKind.QUESTION, ItemStatus.OPEN, "A question")
        f.theirs("They owe")
        f.item(ItemKind.DECISION, ItemStatus.PROPOSED, "A proposal")
        f.mine("Overdue one", f.today - f.day)
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Settled") // never on an agenda
        f.mine("Done", status = ItemStatus.COMPLETED)
        val agenda = project().agenda
        assertEquals(listOf("Overdue one", "A proposal", "They owe", "A question", "Mine, later"), agenda.map { it.text })
        assertEquals(listOf(AgendaKind.OVERDUE, AgendaKind.DECIDE, AgendaKind.FOLLOW_UP, AgendaKind.RESOLVE, AgendaKind.FOLLOW_UP), agenda.map { it.kind })
    }

    @Test fun theAgendaIsCappedAtEight() {
        repeat(12) { f.mine("Task $it", f.today + (it + 1) * f.day) }
        assertEquals(Prepare.MAX_AGENDA, project().agenda.size)
    }

    @Test fun aPersonPackKeepsTheirPromisesAndMyOwnFromTheirMeetings() {
        f.theirs("Ana's promise", owner = "ana")
        f.theirs("Bo's promise", owner = "bo")
        val mine = f.mine("My promise")
        runBlocking { f.items.link(mine.id, LinkType.PERSON, "ana", "PARTICIPANT") } // made in a meeting with her
        val pack = runBlocking { prepare.forEntity(ContextType.PERSON, "ana", f.now) }
        assertEquals(listOf("Ana's promise"), pack.theyOwe.map { it.text })
        assertEquals(listOf("My promise"), pack.youOwe.map { it.text })
        assertEquals("Ana", pack.title)
    }

    @Test fun anEventUsesItsProjectWhenItsNoteIsFiledInOne() = runBlocking {
        f.mine("Send the docs")
        f.db.noteDao().upsert(f.db.noteDao().getById("n1")!!.copy(metadataJson = "{\"calendarEvent\":\"7@1000\"}"))
        val pack = prepare.forEvent(PulseEvent("7@1000", "Acme sync", f.now, f.now + 3_600_000), f.now)
        assertEquals("Acme sync", pack.title)
        assertEquals(listOf("Send the docs"), pack.youOwe.map { it.text })
    }

    @Test fun anEventWithKnownGuestsUsesThem() = runBlocking {
        f.theirs("Ana's promise", owner = "ana")
        val pack = prepare.forEvent(PulseEvent("9@1", "Catch-up", f.now, f.now + 1, people = listOf("Ana")), f.now)
        assertEquals(listOf("Ana's promise"), pack.theyOwe.map { it.text })
        assertEquals("m1", pack.lastMeeting?.meetingId)
    }

    @Test fun aFirstMeetingHasNothingToPrepareFrom() = runBlocking {
        val pack = prepare.forEvent(PulseEvent("9@2", "First meeting with NetOne", f.now, f.now + 1, people = listOf("Zed Stranger")), f.now)
        assertTrue(pack.isEmpty)
        assertNull(pack.lastMeeting)
        assertFalse(pack.title.isEmpty())
    }
}
