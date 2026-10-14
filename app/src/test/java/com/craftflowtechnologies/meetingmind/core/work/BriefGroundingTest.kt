package com.craftflowtechnologies.meetingmind.core.work

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Only the model's two pieces can be wrong, and only cited sentences survive; the rest is the database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BriefGroundingTest {
    private lateinit var f: PulseFixture
    private lateinit var w: Map<String, com.craftflowtechnologies.meetingmind.core.database.ItemEntity>

    @Before fun setup() { f = PulseFixture(); w = f.projectWorld() }
    @After fun tearDown() { f.db.close() }

    private fun id(name: String) = w.getValue(name).id
    private fun quoteId(name: String) = runBlocking { f.db.itemDao().evidenceFor(id(name)).first().id }
    private fun build(models: WorkModels?, target: BriefTarget = BriefTarget.forProject("nb"), refresh: Boolean = false, privacy: PackPrivacy = PackPrivacy { _, _ -> false }) =
        runBlocking { BriefBuilder(f.db, models, privacy) { f.now }.build(target, refresh) }

    private fun reply(vararg parts: Pair<String, List<String>>, recommended: List<Pair<String, List<String>>> = emptyList()): String {
        fun arr(l: List<Pair<String, List<String>>>) = l.joinToString(",") { (t, c) -> "{\"text\":\"$t\",\"cites\":[${c.joinToString(",") { "\"$it\"" }}]}" }
        return "{\"executive\":[${arr(parts.toList())}],\"recommended\":[${arr(recommended)}]}"
    }

    @Test fun uncitedAndBadlyCitedSentencesAreDropped() {
        val model = FakeWorkModel(reply(
            "Launch moved to October 21." to listOf(id("new")),
            "Everything is fine." to emptyList(),
            "The client loves us." to listOf("invented-id"),
            "One good cite and one bad." to listOf(id("deck"), "invented"),
            "Quoted directly." to listOf(quoteId("deck")),
            recommended = listOf("Settle the CMS question." to listOf(id("proposed")), "Ask about budget." to emptyList())
        ))
        val brief = build(FakeWorkModels(model))
        assertEquals(listOf("Launch moved to October 21.", "Quoted directly."), brief.executive.map { it.text })
        assertEquals(listOf("Settle the CMS question."), brief.recommended.map { it.text })
        assertTrue(brief.hasProse)
    }

    @Test fun theStructureIsTheDatabaseNotTheModel() {
        val withModel = build(FakeWorkModels(FakeWorkModel(reply("Anything." to listOf(id("deck"))))))
        val without = build(null)
        assertEquals(without.copy(executive = emptyList(), recommended = emptyList(), generatedAt = 0), withModel.copy(executive = emptyList(), recommended = emptyList(), generatedAt = 0))
        assertEquals(listOf("Send the deck"), without.youOwe.map { it.text })
        assertEquals("overdue since ${Pulse.shortDate(f.today - f.day, java.util.Locale.ENGLISH)}", without.youOwe.single().detail)
        assertEquals(listOf("API docs"), without.theyOwe.map { it.text })
        assertEquals("Ana · due ${Pulse.shortDate(f.today + 2 * f.day, java.util.Locale.ENGLISH)}", without.theyOwe.single().detail)
        assertEquals(listOf("Vendor may be late"), without.risks.map { it.text })
        assertEquals(listOf("Which region?"), without.questions.map { it.text })
        assertEquals(listOf("Adopt headless CMS"), without.decisionsRequired.map { it.text })
        assertEquals(listOf("Launch on October 21"), without.decisions.map { it.text })
        assertEquals(listOf("Send the deck", "API docs"), without.next7.map { it.text })
        assertTrue(without.changes.any { it.text == "Launch moved Oct 14 → Oct 21" })
    }

    @Test fun statusAndProgressComeFromCounts() {
        val attention = build(null)
        assertEquals(BriefStatus.ATTENTION, attention.status) // an overdue promise, a risk, a decision to make
        assertEquals(1 to 3, attention.progress) // Sign the contract is done; the deck and the docs are not
        runBlocking {
            f.items.setStatus(id("deck"), ItemStatus.COMPLETED); f.items.setStatus(id("risk"), ItemStatus.CLOSED); f.items.setStatus(id("proposed"), ItemStatus.ACTIVE)
        }
        val calm = build(null)
        assertEquals(BriefStatus.ON_TRACK, calm.status)
        assertEquals(2 to 3, calm.progress)
    }

    @Test fun everyLineOpensItsEvidence() {
        val brief = build(FakeWorkModels(FakeWorkModel(reply("Deck owed." to listOf(id("deck"))))))
        assertTrue(brief.evidence.any { it.itemId == id("deck") && it.quote == "I'll send the deck." && it.meetingTitle == "Acme review" && it.startMs == 20_000L })
        assertTrue(brief.evidence.any { it.itemId == id("new") && it.quote == "Actually, October 21 works better." })
        // Lines with no quote (a promise from someone, with no recording behind it) simply have none.
        assertEquals(brief.evidence.map { it.evidenceId }.distinct().size, brief.evidence.size)
        assertTrue(brief.citedItemIds.all { it in w.values.map { i -> i.id } })
    }

    @Test fun theModelIsToldTheRulesAndShownOnlyThePack() {
        val model = FakeWorkModel(reply("x" to listOf(id("deck"))))
        build(FakeWorkModels(model))
        val prompt = model.prompts.single()
        assertTrue(prompt.startsWith("You are working with a transcript of a real recording")) // the fidelity contract
        assertTrue(prompt.contains("A sentence with no citation will be thrown away"))
        assertTrue(prompt.contains("[${id("deck")}] COMMITMENT OPEN: Send the deck"))
        assertFalse(prompt.contains("Elsewhere"))
    }

    @Test fun proseIsKeptAndNotPaidForTwiceUntilTheDataChanges() {
        val models = FakeWorkModels(FakeWorkModel(reply("Deck owed." to listOf(id("deck")))))
        val first = build(models)
        val second = build(models)
        assertEquals(1, models.model!!.prompts.size)
        assertEquals(first.executive, second.executive)
        assertEquals(1, runBlocking { f.db.briefDao().count() })
        // A change to the record makes the prose stale.
        f.clockValue = f.now + 1000
        runBlocking { f.items.setDue(id("deck"), f.today + f.day, "tomorrow") }
        build(models)
        assertEquals(2, models.model.prompts.size)
        // And an explicit refresh always asks.
        build(models, refresh = true)
        assertEquals(3, models.model.prompts.size)
    }

    @Test fun keptProseIsCheckedAgainstTodaysPack() {
        val models = FakeWorkModels(FakeWorkModel(reply("Deck owed." to listOf(id("deck")))))
        build(models)
        runBlocking { f.db.briefDao().latest("PROJECT", "nb", "PROJECT")!!.let { f.db.briefDao().upsert(it.copy(contentJson = reply("Old claim." to listOf("gone-item")), createdAt = it.createdAt + 5_000_000)) } }
        assertTrue(build(models).executive.isEmpty())
    }

    @Test fun sensitiveMaterialIsHandedToTheModelChooserAsSensitive() {
        val models = FakeWorkModels(FakeWorkModel(reply("x" to listOf(id("deck")))))
        val brief = build(models, privacy = PackPrivacy { _, _ -> true })
        assertEquals(listOf(true), models.asked)
        assertTrue(brief.confidential)
        val plain = FakeWorkModels(null)
        assertFalse(build(plain, refresh = true).confidential)
        assertEquals(listOf(false), plain.asked)
    }

    @Test fun aWeeklyAMeetingAndARelationshipBriefAreScopedToTheirOwnThing() {
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Elsewhere", project = null, org = null, meeting = null)
        assertTrue(build(null, BriefTarget.weekly).decisions.any { it.text == "Elsewhere" })
        assertFalse(build(null, BriefTarget.forProject("nb")).decisions.any { it.text == "Elsewhere" })
        assertEquals("Acme review", build(null, BriefTarget.forMeeting("m1")).title)
        assertEquals("Ana", build(null, BriefTarget.forPerson("ana")).title)
        assertEquals("Acme", build(null, BriefTarget.forClient("org1")).title)
        assertEquals("CLIENT/org1", BriefTarget.forClient("org1").key)
        assertNull(build(null, BriefTarget.forPerson("bo")).progress)
    }
}
