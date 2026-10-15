package com.craftflowtechnologies.meetingmind.core.companion.moment

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.Moment
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The ticket model (24 h expiry, encoding) and the store: issued, consumed once, deferred, rotation persisted. */
class UnveilTicketTest {
    @get:Rule val tmp = TemporaryFolder()

    private val hour = LocalClock.HourMs
    private val noon = 1_791_201_600_000L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After fun tearDown() = scope.cancel()

    private fun newStore(): MomentLedgerStore {
        val file = tmp.newFolder().resolve("moments.preferences_pb")
        return MomentLedgerStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private fun facts(flags: MomentFlags) = { t: UnveilTicket?, l: com.craftflowtechnologies.meetingmind.core.companion.MomentLedger, r: RotationState ->
        MomentFacts(noteId = "n1", ticket = t, clock = LocalClock(noon), ledger = l, rotation = r, flags = flags)
    }

    @Test fun `a ticket is valid for 24 hours`() {
        val t = UnveilTicket("n1", noon)
        assertTrue(t.isValid(noon))
        assertTrue(t.isValid(noon + 24 * hour - 1))
        assertFalse(t.isValid(noon + 24 * hour))
        assertTrue("a small clock skew is tolerated", t.isValid(noon - 10 * 60_000))
        assertFalse(t.isValid(noon - 2 * hour))
    }

    @Test fun `a ticket round-trips and junk is ignored`() {
        val t = UnveilTicket("n1", noon, NotebookSpace.FAITH, sermon = true, partial = false, firstEver = true, hello = false, deferred = true, previousReadyAtMs = noon - hour)
        assertEquals(t, UnveilTicket.decode("n1", t.encode()))
        val bare = UnveilTicket("n2", noon)
        assertEquals(bare, UnveilTicket.decode("n2", bare.encode()))
        assertNull(UnveilTicket.decode("n1", "nonsense"))
        assertNull(UnveilTicket.decode("n1", null))
        assertTrue(UnveilTicket.hello("h", noon).hello)
    }

    @Test fun `back to back is under 15 minutes after the previous note`() {
        assertTrue(UnveilTicket("n", noon, previousReadyAtMs = noon - 14 * 60_000).backToBack)
        assertFalse(UnveilTicket("n", noon, previousReadyAtMs = noon - 15 * 60_000).backToBack)
        assertFalse(UnveilTicket("n", noon).backToBack)
    }

    @Test fun `the store stamps the previous ready time and the ticket is consumed once`() = runBlocking {
        val store = newStore()
        store.issueTicket(UnveilTicket("n0", noon - 10 * 60_000))
        val issued = store.issueTicket(UnveilTicket("n1", noon - 60_000, firstEver = true))
        assertEquals(noon - 10 * 60_000, issued.previousReadyAtMs)
        assertNotNull(store.peekTicket("n1", noon))

        val first = store.planOpen("n1", LocalClock(noon), facts(MomentFlags.Budgeted))
        assertTrue(first.plan is MomentPlan.FirstKeep)
        assertNull("consumed", store.peekTicket("n1", noon))
        assertFalse(store.ledger.first().allows(Moment.FirstRecordingPeak, LocalClock(noon)))

        val second = store.planOpen("n1", LocalClock(noon), facts(MomentFlags.Budgeted))
        assertEquals(MomentPlan.Plain(PlainReason.NO_TICKET), second.plan)
    }

    @Test fun `an expired ticket plays nothing and is pruned`() = runBlocking {
        val store = newStore()
        store.issueTicket(UnveilTicket("old", noon - 30 * hour))
        assertNull(store.peekTicket("old", noon))
        // Issuing a newer ticket prunes the expired one.
        store.issueTicket(UnveilTicket("new", noon))
        val p = store.planOpen("old", LocalClock(noon), { t, l, r -> MomentFacts("old", t, clock = LocalClock(noon), ledger = l, rotation = r, flags = MomentFlags.Budgeted) })
        assertEquals(MomentPlan.Plain(PlainReason.NO_TICKET), p.plan)
    }

    @Test fun `a running recording keeps the ticket, deferred`() = runBlocking {
        val store = newStore()
        store.issueTicket(UnveilTicket("n1", noon - 60_000))
        val p = store.planOpen("n1", LocalClock(noon)) { t, l, r ->
            MomentFacts("n1", t, recordingActive = true, clock = LocalClock(noon), ledger = l, rotation = r, flags = MomentFlags.Budgeted)
        }
        assertEquals(MomentPlan.Plain(PlainReason.DEFERRED), p.plan)
        assertTrue(store.peekTicket("n1", noon)!!.deferred)
        val later = store.planOpen("n1", LocalClock(noon), facts(MomentFlags.Budgeted))
        assertEquals(MomentPlan.Nod(), later.plan)
        assertNull(store.peekTicket("n1", noon))
    }

    @Test fun `the rotation and the daily budget persist`() = runBlocking {
        val store = newStore()
        val opens = (0 until 5).map {
            store.planOpen("n$it", LocalClock(noon + it * hour)) { t, l, r ->
                MomentFacts("n$it", t, clock = LocalClock(noon + it * hour), ledger = l, rotation = r, flags = MomentFlags(firstKeepEveryNote = true, unveilOnEveryOpen = true))
            }.plan as MomentPlan.Unveil
        }
        assertEquals(UnveilRotation.preview(RotationState(), MomentSpace.EVERYDAY, 5), opens.map { it.variant })
        assertEquals(UnveilVariant.PLANT, store.rotation.first().last)

        store.issueTicket(UnveilTicket("b1", noon))
        store.issueTicket(UnveilTicket("b2", noon + 4 * hour - 60_000, previousReadyAtMs = null))
        val budget = { id: String, at: Long ->
            { t: UnveilTicket?, l: com.craftflowtechnologies.meetingmind.core.companion.MomentLedger, r: RotationState ->
                MomentFacts(id, t, clock = LocalClock(at), ledger = l, rotation = r, flags = MomentFlags.Budgeted)
            }
        }
        assertTrue(store.planOpen("b1", LocalClock(noon), budget("b1", noon)).plan is MomentPlan.Unveil)
        assertEquals(listOf(noon), store.ledger.first().history[Moment.Unveil.key])
        assertTrue(store.planOpen("b2", LocalClock(noon + 4 * hour), budget("b2", noon + 4 * hour)).plan is MomentPlan.Unveil)
        assertEquals(2, store.ledger.first().history[Moment.Unveil.key]!!.size)
        store.issueTicket(UnveilTicket("b3", noon + 8 * hour))
        assertEquals("the third today nods", MomentPlan.Nod(), store.planOpen("b3", LocalClock(noon + 8 * hour), budget("b3", noon + 8 * hour)).plan)
    }

    @Test fun `open sessions claim once`() {
        val s = OpenSession()
        assertTrue(s.claim())
        assertFalse(s.claim())
        assertTrue(s.claimed)
        assertFalse("restored from saved state", OpenSession(claimed = true).claim())
    }

    @Test fun `low-end rule`() {
        assertTrue(MomentEnvironment.isLowEnd(27, lowRam = false, totalRamBytes = 8L shl 30))
        assertTrue(MomentEnvironment.isLowEnd(33, lowRam = true, totalRamBytes = 8L shl 30))
        assertTrue(MomentEnvironment.isLowEnd(33, lowRam = false, totalRamBytes = 2L shl 30))
        assertFalse(MomentEnvironment.isLowEnd(33, lowRam = false, totalRamBytes = 4L shl 30))
    }
}
