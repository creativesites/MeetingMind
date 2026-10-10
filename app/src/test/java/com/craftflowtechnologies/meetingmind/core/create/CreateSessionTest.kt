package com.craftflowtechnologies.meetingmind.core.create

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateSessionTest {

    @Test fun `remix is a version and undo steps back`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.one("Shorter one.")))
        val s = CreateSession(CreateCard(current = CardVersion("A much longer line than we need.")), CreateGenerator(model, FakeScripture()))
        val out = s.remix(RemixAction.SHORTER)
        assertTrue(out is RemixOutcome.Done)
        assertEquals("Shorter one.", s.card.value.text)
        assertTrue(s.card.value.canUndo)
        s.undo()
        assertEquals("A much longer line than we need.", s.card.value.text)
        assertFalse(s.card.value.canUndo)
    }

    @Test fun `two quick remixes on one card never overlap and the second builds on the first`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.one("Warm."), FakeModel.one("Warm and funny.")), latencyMs = 500)
        val s = CreateSession(CreateCard(current = CardVersion("Plain.")), CreateGenerator(model, FakeScripture()))
        val results = listOf(
            async { s.remix(RemixAction.WARMER) },
            async { s.remix(RemixAction.FUNNIER) }
        ).awaitAll()
        advanceUntilIdle()
        assertEquals(1, model.maxInFlight)
        assertTrue(results.all { it is RemixOutcome.Done })
        // Order preserved: the second prompt was built from the first's result.
        assertTrue(model.prompts[1].second.contains("Warm."))
        assertEquals("Warm and funny.", s.card.value.text)
        assertEquals(listOf("Plain.", "Warm."), s.card.value.past.map { it.text })
    }

    @Test fun `generate and remix share the card's lock`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("Fresh line."), FakeModel.one("Fresh.")), latencyMs = 300)
        val s = CreateSession(CreateCard(current = CardVersion("Old.")), CreateGenerator(model, FakeScripture()))
        val a = async { s.write(CreateRequest(CreateSourceKind.FREE_PROMPT, "x", vibe = CreateVibe.JOYFUL)) }
        val b = async { s.remix(RemixAction.SHORTER) }
        a.await(); b.await()
        assertEquals(1, model.maxInFlight)
    }

    @Test fun `a failed remix leaves the card untouched and says so`() = runTest {
        val model = FakeModel(mutableListOf(com.craftflowtechnologies.meetingmind.ai.common.AiResult.Failed("boom")))
        val s = CreateSession(CreateCard(current = CardVersion("Keep me.")), CreateGenerator(model, FakeScripture()))
        val out = s.remix(RemixAction.WARMER)
        assertEquals(RemixOutcome.Failed(FAILED_MESSAGE), out)
        assertEquals("Keep me.", s.card.value.text)
        assertFalse(s.card.value.canUndo)
    }

    @Test fun `add a verse keeps the words and fetches the text from the library`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.one("MODEL REWROTE THIS", verseRef = "Psalm 23:1")))
        val s = CreateSession(CreateCard(current = CardVersion("Rest today.")), CreateGenerator(model, FakeScripture()))
        s.remix(RemixAction.ADD_VERSE)
        assertEquals("Rest today.", s.card.value.text)
        assertEquals("Psalm 23:1", s.card.value.scriptureRef)
        assertEquals("The Lord is my shepherd; I shall not want.", s.scripture.value?.text)
    }

    @Test fun `typing is one version so undo steps past the whole edit`() = runTest {
        val s = CreateSession(CreateCard(current = CardVersion("Start.")), CreateGenerator(FakeModel(mutableListOf(FakeModel.one("x"))), FakeScripture()))
        s.edit("St"); s.edit("Sta"); s.edit("Start over")
        assertEquals(1, s.card.value.past.size)
        s.undo()
        assertEquals("Start.", s.card.value.text)
    }

    @Test fun `offline remix is unavailable, not a canned rewrite`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.one("never")), available = false)
        val s = CreateSession(CreateCard(current = CardVersion("Same.")), CreateGenerator(model, FakeScripture()))
        assertTrue(s.remix(RemixAction.FUNNIER) is RemixOutcome.Unavailable)
        assertEquals("Same.", s.card.value.text)
    }

    @Test fun `identical wording is not a new version`() {
        val c = CreateCard(current = CardVersion("Same."))
        assertEquals(c, c.withVersion(CardVersion("Same.", null, "Again")))
    }
}
