package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** New signals are matched against what the project already holds, to propose what they replace. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangesMatcherTest {
    private lateinit var f: PulseFixture

    @Before fun setup() { f = PulseFixture() }
    @After fun tearDown() { f.db.close() }

    private fun oldDecision(text: String, project: String? = "nb", status: ItemStatus = ItemStatus.ACTIVE) =
        f.item(ItemKind.DECISION, status, text, project = project, meeting = "m1")

    private fun newMeeting(lines: List<String> = listOf("We'll say it again.")) = f.addMeeting("m2", f.now, lines)

    private fun detect(models: ChangeModels? = null, dismissed: Set<String> = emptySet()) = runBlocking { ChangeDetector(f.db, models) { f.now }.detect("m2", dismissed) }

    @Test fun aMovedDateIsProposedWithItsEvidence() {
        val old = oldDecision("Launch on October 14")
        newMeeting(listOf("Right. So the launch moves to October 21, we agreed.", "Fine by me."))
        f.addSignal("m2", "sig1", ItemKind.DECISION, "Launch on October 21", listOf(0))
        val p = detect().single()
        assertEquals(ChangeReason.DATE_MOVED, p.reason)
        assertEquals(old.id, p.target.id)
        assertEquals("This replaces: Launch on October 14?", p.question)
        assertEquals("Right. So the launch moves to October 21, we agreed.", p.quote)
        assertEquals(0L, p.startMs)
        assertEquals(listOf("m2_s0"), p.segmentIds)
    }

    @Test fun aDeadlineSignalCanMoveADecisionsDate() {
        oldDecision("Launch on October 14")
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DEADLINE, "The launch date is now October 21", listOf(0), value = "October 21")
        assertEquals(ChangeReason.DATE_MOVED, detect().single().reason)
    }

    @Test fun aReversalIsProposed() {
        val old = oldDecision("Use WordPress for the site")
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DECISION, "We will not use WordPress for the site after all", listOf(0))
        val p = detect().single()
        assertEquals(ChangeReason.REVERSED, p.reason); assertEquals(old.id, p.target.id)
    }

    @Test fun theSameStatementRepeatedIsNotAChange() {
        oldDecision("Launch on October 14")
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DECISION, "Launch on October 14.", listOf(0))
        assertTrue(detect().isEmpty())
    }

    @Test fun anUnrelatedStatementIsNotAChange() {
        oldDecision("Launch on October 14")
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DECISION, "Budget review on November 3", listOf(0))
        assertTrue(detect().isEmpty())
    }

    @Test fun onlyTheProjectsOwnOpenOrActiveItemsAreCandidates() {
        oldDecision("Launch on October 14", project = null)
        oldDecision("Launch on October 15", status = ItemStatus.SUPERSEDED)
        f.item(ItemKind.COMMITMENT, ItemStatus.OPEN, "Launch on October 16", Direction.MINE)
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DECISION, "Launch on October 21", listOf(0))
        assertTrue(detect().isEmpty())
    }

    @Test fun itemsFromTheSameRecordingAreNeverReplacedByIt() {
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Launch on October 14", meeting = "m2")
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DECISION, "Launch on October 21", listOf(0))
        assertTrue(detect().isEmpty())
    }

    @Test fun weakSignalsAndDismissedProposalsAreLeftOut() {
        oldDecision("Launch on October 14")
        newMeeting()
        f.addSignal("m2", "weak", ItemKind.DECISION, "Launch on October 21", listOf(0), confidence = 0.3f)
        assertTrue(detect().isEmpty())
        f.addSignal("m2", "strong", ItemKind.DECISION, "Launch on October 21", listOf(0))
        val p = detect().single()
        assertTrue(detect(dismissed = setOf(p.id)).isEmpty())
    }

    @Test fun eachOldItemIsReplacedByItsClosestNewStatementOnly() {
        oldDecision("Launch on October 14")
        newMeeting()
        f.addSignal("m2", "a", ItemKind.DECISION, "Launch on October 21", listOf(0))
        f.addSignal("m2", "b", ItemKind.DECISION, "The launch will be on October 28", listOf(0))
        assertEquals(1, detect().size)
    }

    // ---------------------------------------------------------------- the model's look

    private class Fake(val reply: String) : LanguageModel {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> { prompts += prompt; return AiResult.Success(reply) }
    }

    private fun reversalWorld() {
        oldDecision("Use WordPress for the site")
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DECISION, "We will not use WordPress for the site after all", listOf(0))
    }

    @Test fun theModelLooksOnlyAtCandidatesAndCanTurnOneDown() {
        reversalWorld()
        val no = Fake("{\"replaces\": false}")
        assertTrue(detect(ChangeModels { no }).isEmpty())
        assertEquals(1, no.prompts.size)
        assertTrue(no.prompts.single().contains("You are working with a transcript of a real recording")) // the fidelity contract
        assertTrue(no.prompts.single().contains("Use WordPress for the site"))
        val yes = detect(ChangeModels { Fake("```json\n{\"replaces\": true}\n```") }).single()
        assertTrue(yes.checkedByModel)
    }

    @Test fun aFirmDateMoveNeedsNoSecondOpinion() {
        oldDecision("Launch on October 14")
        newMeeting()
        f.addSignal("m2", "sig1", ItemKind.DECISION, "Launch on October 21", listOf(0))
        val model = Fake("{\"replaces\": false}")
        assertEquals(1, detect(ChangeModels { model }).size)
        assertTrue(model.prompts.isEmpty())
    }

    @Test fun noModelOrAnUnreadableAnswerLeavesTheProposalStanding() {
        reversalWorld()
        assertEquals(1, detect(ChangeModels { null }).size)
        assertEquals(1, detect(ChangeModels { Fake("I think so") }).size)
    }

    @Test fun theModelIsAskedOnceNotOncePerRefresh() = runBlocking {
        reversalWorld()
        val model = Fake("{\"replaces\": true}")
        val detector = ChangeDetector(f.db, { model }) { f.now }
        detector.detect("m2"); detector.detect("m2"); detector.detect("m2")
        assertEquals(1, model.prompts.size)
    }

}
