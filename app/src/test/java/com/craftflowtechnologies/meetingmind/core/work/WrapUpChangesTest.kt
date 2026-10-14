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
import java.util.Calendar
import java.util.Locale

/**
 * The definition of done for W9: on a two-meeting fixture where the launch date moves, the Changes
 * card proposes the supersession with the right evidence, and confirming it makes Pulse say
 * "Launch moved Oct 14 → Oct 21".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WrapUpChangesTest {
    private lateinit var f: PulseFixture
    private lateinit var work: WorkRepository

    private val sept20 get() = f.at(Calendar.SEPTEMBER, 20)
    private val oct6 get() = f.at(Calendar.OCTOBER, 6)

    @Before fun setup() {
        f = PulseFixture(); work = WorkRepository(f.db)
        // Meeting one: the date is decided and the Wrap-up confirmed.
        f.addMeeting("a", sept20, listOf("Okay, launch on October 14, agreed."))
        f.addDecision("a", "da", "Launch on October 14", 0)
        runBlocking { work.confirm("a") }
        // Meeting two: the date moves.
        f.addMeeting("b", oct6, listOf("Quick update: the launch moves to October 21.", "Ana will send the keys."))
        f.addDecision("b", "db", "Launch on October 21", 0)
        f.addSignal("b", "sigLaunch", ItemKind.DECISION, "Launch on October 21", listOf(0))
    }
    @After fun tearDown() { f.db.close() }

    private fun proposals() = runBlocking { ChangeDetector(f.db) { oct6 }.detect("b") }
    private fun pulseLines() = runBlocking { Pulse(f.db) { oct6 + 1 }.changesSince(0, Locale.ENGLISH) }.flatMap { g -> g.lines.map { it.text } }

    @Test fun theChangesCardProposesTheSupersessionWithEvidence() {
        val p = proposals().single()
        assertEquals("This replaces: Launch on October 14?", p.question)
        assertEquals("Quick update: the launch moves to October 21.", p.quote)
        assertEquals("b", p.meetingId)
        assertEquals(0L, p.startMs)
        assertEquals("Launch on October 14", p.target.text)
    }

    @Test fun nothingChangesUntilThePersonConfirms() = runBlocking {
        val old = f.db.itemDao().bySourceFinding("da")!!
        work.confirm("b") // done, with the card left alone
        assertEquals("ACTIVE", f.db.itemDao().getById(old.id)!!.status)
        assertFalse(pulseLines().any { it.contains("moved") })
    }

    @Test fun confirmingSupersedesAndPulseSaysWhatMoved() = runBlocking {
        val old = f.db.itemDao().bySourceFinding("da")!!
        val p = proposals().single()
        work.confirm("b", WrapUpChoices(changes = setOf(p.id)))
        val replaced = f.db.itemDao().getById(old.id)!!
        assertEquals("SUPERSEDED", replaced.status)
        // The decision promoted from the recording is the one that replaces it: no duplicate.
        val current = f.db.itemDao().bySourceFinding("db")!!
        assertEquals(old.id, current.supersedesId)
        assertEquals("ACTIVE", current.status)
        assertEquals(1, f.db.itemDao().allLive().count { it.kind == "DECISION" && it.status == "ACTIVE" })
        assertTrue(f.db.itemDao().linksFor(current.id).any { it.targetType == LinkType.ITEM && it.targetId == old.id && it.role == "SUPERSEDES" })
        assertEquals(listOf("Launch moved Oct 14 → Oct 21"), pulseLines().filter { it.contains("moved") })
    }

    @Test fun confirmingWhenTheDecisionWasOnlyASignalMakesTheItemWithItsEvidence() = runBlocking {
        f.db.workDao().deleteDecision("db")
        val old = f.db.itemDao().bySourceFinding("da")!!
        val p = proposals().single()
        work.confirm("b", WrapUpChoices(changes = setOf(p.id)))
        val created = f.db.itemDao().bySourceFinding("sig_sigLaunch")!!
        assertEquals(old.id, created.supersedesId); assertEquals("Launch on October 21", created.text)
        assertEquals("Quick update: the launch moves to October 21.", f.db.itemDao().evidenceFor(created.id).single().quote)
        assertEquals("nb", created.projectId)
        assertEquals(listOf("Launch moved Oct 14 → Oct 21"), pulseLines().filter { it.contains("moved") })
    }

    @Test fun notTheSameKeepsBothAndTheProposalDoesNotReturn() = runBlocking {
        val p = proposals().single()
        WrapUpSignals.dismissed(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "b") // nothing yet
        assertEquals(emptySet<String>(), WrapUpSignals.dismissed(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "b"))
        WrapUpSignals.dismiss(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "b", p.id)
        val dismissed = WrapUpSignals.dismissed(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "b")
        assertTrue(ChangeDetector(f.db) { oct6 }.detect("b", dismissed).isEmpty())
        work.confirm("b", WrapUpChoices(dismissed = dismissed))
        assertEquals("ACTIVE", f.db.itemDao().bySourceFinding("da")!!.status)
        assertEquals("ACTIVE", f.db.itemDao().bySourceFinding("db")!!.status)
    }

    @Test fun confirmingTwiceChangesNothingTheSecondTime() = runBlocking {
        val p = proposals().single()
        val choices = WrapUpChoices(changes = setOf(p.id))
        work.confirm("b", choices)
        val events = f.db.itemDao().eventsSince(0).size
        work.confirm("b", choices)
        assertEquals(events, f.db.itemDao().eventsSince(0).size)
    }
}
