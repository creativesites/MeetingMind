package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateGeneratorTest {

    private fun generator(model: FakeModel, scripture: FakeScripture = FakeScripture()) = CreateGenerator(model, scripture)

    // ── Faith contract: scripture text is never the model's ───────────────────────────

    @Test fun `scripture text comes from the provider even when the model volunteers its own`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("Rest in the Shepherd today.", verseRef = "Psalm 23:1", verseText = "MODEL-WRITTEN VERSE")))
        val out = generator(model).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "peace", vibe = CreateVibe.PEACEFUL)) as CreateOutcome.Written
        val piece = out.options.single()
        assertEquals("The Lord is my shepherd; I shall not want.", piece.scripture?.text)
        assertFalse(piece.text.contains("MODEL-WRITTEN"))
    }

    @Test fun `the prompt never asks the model to write verse text`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("A line.", verseRef = "John 3:16")))
        generator(model).generate(CreateRequest(CreateSourceKind.VERSE, "grace", reference = "John 3:16", vibe = CreateVibe.REFLECTIVE))
        val (system, user) = model.prompts.single()
        assertTrue(system.contains("Scripture text is never yours to write"))
        assertTrue(system.contains("Scripture text comes only from the app's Bible library"))
        assertTrue(user.contains("Do not quote or paraphrase the verse"))
        assertFalse(user.contains("For God so loved"))
    }

    @Test fun `a verse the library cannot give is dropped and said so, not invented`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("Hold on.", verseRef = "John 3:99")))
        val out = generator(model).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "hope", vibe = CreateVibe.PRAYERFUL)) as CreateOutcome.Written
        assertNull(out.options.single().scripture)
        assertTrue(out.verseDropped)
    }

    @Test fun `a model that writes quoted scripture itself is rejected`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("As John 3:16 says, \\\"For God so loved the world that he gave his one and only Son\\\" and so on.")))
        val out = generator(model).generate(CreateRequest(CreateSourceKind.DEVOTIONAL, "love", vibe = CreateVibe.GRATEFUL))
        assertTrue(out is CreateOutcome.Failed)
    }

    @Test fun `words that claim to speak for God are rejected`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("God told me you will get the job.", "Quiet your heart today.")))
        val out = generator(model).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "x", vibe = CreateVibe.PEACEFUL)) as CreateOutcome.Written
        assertEquals(listOf("Quiet your heart today."), out.options.map { it.text })
    }

    @Test fun `a verse source with no words is the verse itself and needs no model`() = runTest {
        val model = FakeModel(mutableListOf(AiResult.Failed("must not be called")), available = false)
        val out = generator(model).generate(CreateRequest(CreateSourceKind.VERSE, "", reference = "Psalm 23:1")) as CreateOutcome.Written
        assertEquals("", out.options.single().text)
        assertEquals("The Lord is my shepherd; I shall not want.", out.options.single().scripture?.text)
        assertTrue(model.prompts.isEmpty())
    }

    // ── Reference change re-fetches ─────────────────────────────────────────────────

    @Test fun `changing the reference fetches the text again`() = runTest {
        val scripture = FakeScripture()
        val gen = generator(FakeModel(mutableListOf(FakeModel.one("x"))), scripture)
        val session = CreateSession(CreateCard(current = CardVersion("Rest.")), gen)
        session.setReference("Psalm 23:1")
        assertEquals("The Lord is my shepherd; I shall not want.", session.scripture.value?.text)
        session.setReference("Romans 8:28")
        assertEquals("And we know that in all things God works for the good of those who love him.", session.scripture.value?.text)
        assertEquals(listOf("Psalm 23:1", "Romans 8:28"), scripture.fetched)
        assertEquals("Romans 8:28", session.card.value.scriptureRef)
        // The card keeps the reference, not the text.
        assertFalse(session.card.value.toString().contains("shepherd"))
    }

    @Test fun `a bad reference leaves the card as it was`() = runTest {
        val gen = generator(FakeModel(mutableListOf(FakeModel.one("x"))))
        val session = CreateSession(CreateCard(current = CardVersion("Rest.", "Psalm 23:1")), gen)
        val r = session.setReference("John 3:99")
        assertTrue(r is ScriptureFetch.Invalid)
        assertEquals("Psalm 23:1", session.card.value.scriptureRef)
    }

    // ── Verbatim quotes ──────────────────────────────────────────────────────────────

    @Test fun `a note quote must be word for word`() = runTest {
        val note = "Grace is not a reward for the faithful. It is the water that makes faithfulness possible. We drink first."
        val altered = FakeModel.pieces("Grace is a reward for the faithful.", "It is the water that makes faithfulness possible.")
        val out = generator(FakeModel(mutableListOf(altered))).generate(CreateRequest(CreateSourceKind.SERMON_NOTE, note)) as CreateOutcome.Written
        assertEquals(listOf("It is the water that makes faithfulness possible."), out.options.map { it.text })
    }

    @Test fun `when every pick is reworded the note's own first sentences are used`() = runTest {
        val note = "Grace is not a reward. It is the water that makes faithfulness possible. ".repeat(6)
        val out = generator(FakeModel(mutableListOf(FakeModel.pieces("Totally different words.")))).generate(CreateRequest(CreateSourceKind.SERMON_NOTE, note)) as CreateOutcome.Written
        assertTrue(CreateGuards.isVerbatim(note, out.options.single().text.trimEnd('…', ' ')))
    }

    @Test fun `verbatim sources cannot be reworded by remix but can take a verse`() {
        assertFalse(RemixAction.SHORTER.allowedFor(CreateSourceKind.SERMON_NOTE))
        assertFalse(RemixAction.FUNNIER.allowedFor(CreateSourceKind.SELECTED_TEXT))
        assertTrue(RemixAction.ADD_VERSE.allowedFor(CreateSourceKind.SERMON_NOTE))
        assertTrue(RemixAction.SHORTER.allowedFor(CreateSourceKind.FREE_PROMPT))
    }

    // ── Moods ────────────────────────────────────────────────────────────────────────

    @Test fun `a prayer request allows only Prayerful and Peaceful`() {
        assertEquals(setOf(CreateVibe.PRAYERFUL, CreateVibe.PEACEFUL), CreateVibePolicy.allowed(CreateSourceKind.PRAYER))
        assertEquals(CreateVibe.PRAYERFUL, CreateVibePolicy.suggest(CreateSourceKind.PRAYER))
        assertTrue(CreateVibePolicy.enforce(CreateSourceKind.PRAYER, CreateVibe.FUNNY) in setOf(CreateVibe.PRAYERFUL, CreateVibe.PEACEFUL))
    }

    @Test fun `a model's mood outside the policy is overruled`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("Hold this one gently.", suggested = "Funny")))
        val out = generator(model).generate(CreateRequest(CreateSourceKind.PRAYER, "my mother is ill", vibe = null)) as CreateOutcome.Written
        assertTrue(out.suggested in setOf(CreateVibe.PRAYERFUL, CreateVibe.PEACEFUL))
        val user = model.prompts.single().second
        assertTrue(user.contains("Allowed vibes: Prayerful, Peaceful"))
        assertTrue(user.contains("only in a Prayerful or Peaceful voice"))
    }

    @Test fun `asking for a disallowed vibe on a prayer request falls back to the suggestion`() {
        val req = CreateRequest(CreateSourceKind.PRAYER, "x", vibe = CreateVibe.CELEBRATORY)
        assertTrue(req.effectiveVibe in setOf(CreateVibe.PRAYERFUL, CreateVibe.PEACEFUL))
    }

    @Test fun `grief leaves Peaceful only`() {
        assertEquals(setOf(CreateVibe.PEACEFUL), CreateVibePolicy.allowed(CreateSourceKind.TESTIMONY, grief = true))
    }

    @Test fun `every suggestion is allowed for every source`() {
        CreateSourceKind.entries.forEach { s ->
            listOf(false, true).forEach { grief ->
                (CreateVibe.entries + null).forEach { hint ->
                    assertTrue("$s $hint $grief", CreateVibePolicy.suggest(s, hint, grief) in CreateVibePolicy.allowed(s, grief))
                }
            }
        }
    }

    @Test fun `the companion pose for a card always respects the policy`() {
        assertEquals(CreateMode.PRAYERFUL, CreateVibePolicy.companionMode(CreateSourceKind.PRAYER, CreateVibe.PRAYERFUL))
        assertTrue(CreateVibePolicy.companionMode(CreateSourceKind.PRAYER, CreateVibe.CELEBRATORY) in setOf(CreateMode.PRAYERFUL, CreateMode.PEACEFUL))
    }

    // ── Non-faith contract ───────────────────────────────────────────────────────────

    @Test fun `non-faith vibes get the general content contract and no theology`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("Monday called. I sent it to voicemail.", suggested = "Funny")))
        val out = generator(model).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "Monday meetings", vibe = CreateVibe.FUNNY)) as CreateOutcome.Written
        val (system, user) = model.prompts.single()
        assertTrue(system.contains("No hateful, sexual, violent or demeaning content"))
        assertTrue(system.contains("No impersonation"))
        assertTrue(system.contains("No medical, financial or legal claims"))
        assertFalse(system.contains("Theological contract"))
        assertTrue(user.contains("Set verseRef to null"))
        assertNull(out.options.single().scripture)
    }

    @Test fun `a verse the model adds to a non-faith card is ignored`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("Work hard, rest well.", suggested = "Motivational", verseRef = "John 3:16")))
        val scripture = FakeScripture()
        val out = generator(model, scripture).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "work", vibe = CreateVibe.MOTIVATIONAL)) as CreateOutcome.Written
        assertNull(out.options.single().scripture)
        assertTrue(scripture.fetched.isEmpty())
    }

    @Test fun `medical claims are rejected on a general card`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("This tea cures anxiety.", "Drink some water.")))
        val out = generator(model).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "x", vibe = CreateVibe.WISDOM)) as CreateOutcome.Written
        assertEquals(listOf("Drink some water."), out.options.map { it.text })
    }

    @Test fun `faith moods get the faith contract`() = runTest {
        val model = FakeModel(mutableListOf(FakeModel.pieces("Thank you.", suggested = "Grateful")))
        generator(model).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "thanks", vibe = CreateVibe.GRATEFUL))
        assertTrue(model.prompts.single().first.contains("Never claim to speak for God"))
    }

    // ── Visible failure, never silent canned text ───────────────────────────────────

    @Test fun `a failed model call is a visible failure with the plain message`() = runTest {
        val out = generator(FakeModel(mutableListOf(AiResult.Failed("network")))).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "x", vibe = CreateVibe.JOYFUL))
        assertEquals(CreateOutcome.Failed("Couldn't generate — try again or write your own."), out)
    }

    @Test fun `garbage from the model is a visible failure`() = runTest {
        val out = generator(FakeModel(mutableListOf(AiResult.Success("sorry, I can't")))).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "x", vibe = CreateVibe.JOYFUL))
        assertTrue(out is CreateOutcome.Failed)
    }

    @Test fun `no model means unavailable, not canned text`() = runTest {
        val out = generator(FakeModel(mutableListOf(FakeModel.pieces("never")), available = false)).generate(CreateRequest(CreateSourceKind.FREE_PROMPT, "x", vibe = CreateVibe.FUNNY))
        assertTrue(out is CreateOutcome.Unavailable)
    }

    @Test fun `starters are labelled, exist for every vibe and respect the policy`() {
        assertTrue(CreateStarters.LABEL.contains("Starter"))
        CreateVibe.entries.forEach { v -> assertTrue("$v", CreateStarters.all.any { it.vibe == v }) }
        CreateStarters.forVibe(CreateVibe.PEACEFUL, CreateSourceKind.PRAYER).forEach {
            assertTrue(it.vibe in CreateVibePolicy.allowed(CreateSourceKind.PRAYER))
        }
        // Starters carry a reference at most, never verse text.
        assertTrue(CreateStarters.all.all { it.verseRef == null || com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser.parse(it.verseRef) != null })
    }

    @Test fun `every curated background exists in the pack`() {
        val ids = com.craftflowtechnologies.meetingmind.core.share.BackgroundPack.all.map { it.id }.toSet()
        CreateVibe.entries.forEach { v -> assertTrue("$v", CreateBackdrops.forVibe(v).all { it in ids }) }
    }

    private fun assertNull(x: Any?) = org.junit.Assert.assertNull(x)
}
