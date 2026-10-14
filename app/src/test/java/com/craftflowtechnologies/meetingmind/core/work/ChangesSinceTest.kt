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
import java.util.Locale

/** "What changed" is specific sentences, never counts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangesSinceTest {
    private lateinit var f: PulseFixture
    private lateinit var pulse: Pulse

    @Before fun setup() { f = PulseFixture(); pulse = Pulse(f.db) { f.now } }
    @After fun tearDown() { f.db.close() }

    private fun lines(since: Long = 0) = runBlocking { pulse.changesSince(since, Locale.ENGLISH) }.flatMap { g -> g.lines.map { it.text } }

    @Test fun aNewCommitmentNamesWhoAndWhen() {
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 5)
        f.theirs("API docs", due = f.at(java.util.Calendar.OCTOBER, 9, 0))
        f.mine("the launch deck", due = f.at(java.util.Calendar.OCTOBER, 20, 0))
        assertEquals(listOf("You committed to the launch deck by Oct 20", "Ana committed to API docs by Fri"), lines())
    }

    @Test fun aMovedDateSaysFromAndTo() {
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 1)
        val c = f.mine("Launch", due = f.at(java.util.Calendar.OCTOBER, 14, 0))
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 6)
        runBlocking { f.items.setDue(c.id, f.at(java.util.Calendar.OCTOBER, 21, 0), "21 Oct") }
        assertEquals("Launch moved Oct 14 → Oct 21", lines(since = f.at(java.util.Calendar.OCTOBER, 5)).single())
    }

    @Test fun aSupersededDecisionSaysWhatMoved() {
        f.clockValue = f.at(java.util.Calendar.SEPTEMBER, 20)
        val old = f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Launch on October 14")
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 6)
        runBlocking { f.items.supersede(old.id, com.craftflowtechnologies.meetingmind.core.database.ItemEntity("", "DECISION", "ACTIVE", "Launch on October 21", reviewed = true, createdAt = 0, updatedAt = 0)) }
        // Only the change is news: the replacement's own creation isn't repeated.
        assertEquals(listOf("Launch moved Oct 14 → Oct 21"), lines(since = f.at(java.util.Calendar.OCTOBER, 5)))
    }

    @Test fun aDecisionThatChangedMoreThanADateQuotesBoth() {
        assertEquals("Decision changed: “Use WordPress” → “Use a headless CMS”", ChangeSentences.superseded("Use WordPress", "Use a headless CMS"))
        assertEquals("Ship moved Nov 3 → Nov 10", ChangeSentences.superseded("Ship on 3 November", "Ship pushed to November 10th"))
        assertEquals("Decision changed: “Launch on Oct 14” → “Launch on Oct 14”", ChangeSentences.superseded("Launch on Oct 14", "Launch on Oct 14"))
    }

    @Test fun otherChangesReadAsSentencesToo() {
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 3)
        val t = f.theirs("Brand assets")
        val q = f.item(ItemKind.QUESTION, ItemStatus.OPEN, "Which region?")
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Use OAuth2")
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 4)
        runBlocking { f.items.setStatus(t.id, ItemStatus.COMPLETED); f.items.answer(q.id, "EU") }
        val all = lines(since = f.at(java.util.Calendar.OCTOBER, 3, 13))
        assertEquals(setOf("Answered: Which region? — EU", "Ana delivered Brand assets"), all.toSet())
        assertTrue(lines().contains("New question: Which region?"))
        assertTrue(lines().contains("Decided: Use OAuth2"))
    }

    @Test fun onlyChangesAfterTheGivenTimeCountAndBookkeepingIsNotNews() {
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 2)
        val a = f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Before", reviewed = false)
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 6)
        runBlocking { f.items.markReviewed(a.id); f.items.setText(a.id, "Before, reworded") }
        val b = f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "Removed one")
        runBlocking { f.items.delete(b.id) }
        assertTrue(lines(since = f.at(java.util.Calendar.OCTOBER, 5)).isEmpty())
        assertEquals(listOf("Decided: Before, reworded"), lines(since = f.at(java.util.Calendar.OCTOBER, 1)))
    }

    @Test fun changesAreGroupedUnderTheirProjectThenOrganisationThenPerson() {
        f.clockValue = f.at(java.util.Calendar.OCTOBER, 5)
        f.mine("In the project")
        f.item(ItemKind.DECISION, ItemStatus.ACTIVE, "For the org", project = null)
        f.item(ItemKind.COMMITMENT, ItemStatus.OPEN, "For a person", Direction.THEIRS, owner = "bo", project = null, org = null)
        f.item(ItemKind.QUESTION, ItemStatus.OPEN, "Nowhere", project = null, org = null)
        val groups = runBlocking { pulse.changesSince(0, Locale.ENGLISH) }
        assertEquals(listOf("Acme launch" to ContextType.PROJECT, "Acme" to ContextType.ORG, "Bo" to ContextType.PERSON, "Other" to null), groups.map { it.title to it.entityType })
        assertTrue(groups.flatMap { it.lines }.all { it.itemId != null })
    }
}
