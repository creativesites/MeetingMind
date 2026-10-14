package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.SegmentSignalEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.feature.work.filterChips
import com.craftflowtechnologies.meetingmind.feature.work.filterSegments
import kotlinx.coroutines.flow.first
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

/** What signals add to the Wrap-up: unclear promises, "N more found", and the transcript's filter chips. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WrapUpExtrasTest {
    private lateinit var f: PulseFixture
    private lateinit var work: WorkRepository

    @Before fun setup() {
        f = PulseFixture(); work = WorkRepository(f.db)
        f.addMeeting("c", f.now, listOf("We'll send it over.", "Budget may slip if the vendor is late.", "Ana will send the keys by Friday.", "Use OAuth2."))
        f.addDecision("c", "dc", "Use OAuth2", 3)
        f.db.actionItemDao().let { runBlocking { it.insertActionItem(com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity("ac", "c", "Ana sends the keys", "spk_c", "Ana", "Friday", 0.9f, false, "[\"c_s2\"]")) } }
        f.addSignal("c", "unclear", ItemKind.COMMITMENT, "Send it over", listOf(0))
        f.addSignal("c", "risk", ItemKind.RISK, "Budget may slip if the vendor is late", listOf(1))
        f.addSignal("c", "coveredAction", ItemKind.COMMITMENT, "Ana will send the keys by Friday", listOf(2), speakerId = "spk_c", value = "{\"due\":\"Friday\"}")
        f.addSignal("c", "coveredDecision", ItemKind.DECISION, "Use OAuth2", listOf(3))
        f.addSignal("c", "faint", ItemKind.RISK, "Maybe something", listOf(1), confidence = 0.3f)
    }
    @After fun tearDown() { f.db.close() }

    private fun extras(dismissed: Set<String> = emptySet()) = runBlocking { WrapUpSignals.extras(f.db, "c", work.findings("c"), emptyList(), dismissed) }

    @Test fun signalsTheListsAlreadyCoverAreNotAskedTwice() {
        val e = extras()
        assertEquals(listOf("unclear"), e.unclear.map { it.id })
        assertEquals(listOf("risk"), e.more.map { it.id }) // not the covered promise or decision, not the faint one
    }

    @Test fun aPromiseWithNoOwnerAndNoDateIsUnclear() {
        val unclear = extras().unclear.single()
        assertTrue(WrapUpSignals.isUnclear(unclear))
        assertEquals("We'll send it over.", unclear.quote)
        val withDue = unclear.copy(value = "{\"due\":\"Friday\"}"); assertTrue(!WrapUpSignals.isUnclear(withDue))
        val withOwner = unclear.copy(speakerId = "spk_c"); assertTrue(!WrapUpSignals.isUnclear(withOwner))
        assertNull(unclear.due)
    }

    @Test fun dismissedSignalsLeave() {
        assertTrue(extras(dismissed = setOf("unclear", "risk")).let { it.unclear.isEmpty() && it.more.isEmpty() })
    }

    @Test fun leftAloneAnUnclearPromiseIsKeptAsUnclearNotGuessed() = runBlocking {
        work.confirm("c")
        val kept = f.db.itemDao().bySourceFinding("sig_unclear")!!
        assertEquals("UNCLEAR", kept.status); assertNull(kept.direction)
        assertEquals("We'll send it over.", f.db.itemDao().evidenceFor(kept.id).single().quote)
        assertNull(f.db.itemDao().bySourceFinding("sig_risk")) // extras nobody added are not filed
        assertEquals(listOf(kept.id), f.db.itemDao().observeUnclear().let { it.first() }.map { it.id })
    }

    @Test fun settlingMakesItAnOrdinaryCommitment() = runBlocking {
        work.confirm("c", WrapUpChoices(settled = mapOf("unclear" to Settlement(Direction.THEIRS, "ana", "Monday"))))
        val item = f.db.itemDao().bySourceFinding("sig_unclear")!!
        assertEquals("OPEN", item.status); assertEquals("THEIRS", item.direction); assertEquals("ana", item.ownerPersonId); assertEquals("Monday", item.dueText)
        assertTrue(item.dueAt != null)
    }

    @Test fun anUnclearItemCanBeSettledOrDismissedLater() = runBlocking {
        work.confirm("c")
        val item = f.db.itemDao().bySourceFinding("sig_unclear")!!
        ItemRepository(f.db).settle(item.id, Direction.MINE, null)
        assertEquals("OPEN", f.db.itemDao().getById(item.id)!!.status)
        assertEquals("MINE", f.db.itemDao().getById(item.id)!!.direction)
    }

    @Test fun extrasThePersonAddsBecomeItemsWithEvidence() = runBlocking {
        work.confirm("c", WrapUpChoices(add = setOf("risk")))
        val risk = f.db.itemDao().bySourceFinding("sig_risk")!!
        assertEquals("RISK", risk.kind); assertEquals("OPEN", risk.status); assertEquals("nb", risk.projectId)
        assertEquals("Budget may slip if the vendor is late.", f.db.itemDao().evidenceFor(risk.id).single().quote)
        work.confirm("c", WrapUpChoices(add = setOf("risk")))
        assertEquals(1, f.db.itemDao().allLive().count { it.kind == "RISK" })
    }

    @Test fun theTranscriptFilterChipsComeFromTheSignals() {
        val rows = runBlocking { f.db.signalDao().forMeeting("c") }
        val chips = filterChips(rows)
        assertEquals(listOf("Decisions" to 1, "Commitments" to 2, "Risks" to 1), chips.map { it.second to it.third })
        assertTrue(filterChips(emptyList<SegmentSignalEntity>()).isEmpty()) // no signals, no chips
        val all = (0..3).map { TranscriptSegment("c_s$it", "c", null, null, it * 1000L, it * 1000L + 500, "t$it") }
        assertEquals(listOf("c_s0", "c_s2"), filterSegments(all, rows, ItemKind.COMMITMENT).map { it.id })
        assertEquals(4, filterSegments(all, rows, null).size)
        assertTrue(filterSegments(all, emptyList(), ItemKind.RISK).isEmpty())
    }
}
