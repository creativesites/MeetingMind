package com.craftflowtechnologies.meetingmind.ai.voice.read.smart

import com.craftflowtechnologies.meetingmind.ai.voice.read.Blocks
import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechRole
import com.craftflowtechnologies.meetingmind.ai.voice.read.SpeechSection
import com.craftflowtechnologies.meetingmind.ai.voice.read.extract
import com.craftflowtechnologies.meetingmind.ai.voice.read.john316
import com.craftflowtechnologies.meetingmind.ai.voice.read.lookupOf
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartReadTest {
    @get:Rule val tmp = TemporaryFolder()

    private val study = ScriptValidator.Limits.STUDY
    private val src = "Paul wrote to the church in Rome in 57 AD. He did not say the road would be easy, and it might take years."

    // --- validator ---
    @Test fun `a faithful smoothing passes`() {
        val out = "Paul wrote to the church in Rome in 57 AD. He did not say that the road would be easy, and it might take years."
        assertNull(ScriptValidator.check(src, out, study))
    }
    @Test fun `a changed or added number fails`() {
        assertEquals("numbers", ScriptValidator.check(src, src.replace("57", "58"), study))
        assertEquals("numbers", ScriptValidator.check(src, "$src It took 3 years.", ScriptValidator.Limits.GENERAL))
    }
    @Test fun `a dropped number fails`() {
        assertEquals("numbers", ScriptValidator.check(src, "Paul wrote to the church in Rome in AD. He did not say the road would be easy, and it might take years.", study))
    }
    @Test fun `a flipped negation fails`() {
        assertEquals("negation", ScriptValidator.check(src, src.replace("did not say", "did say"), study))
    }
    @Test fun `a dropped hedge fails`() {
        assertEquals("hedging", ScriptValidator.check(src, src.replace("it might take years", "it will take years"), study))
    }
    @Test fun `a new name fails and a dropped name fails`() {
        assertEquals("new name", ScriptValidator.check(src, src.replace("Rome", "Corinth"), study))
        assertEquals("new name", ScriptValidator.check(src, "$src Calvin agreed with him.", ScriptValidator.Limits.GENERAL))
        assertEquals("dropped name", ScriptValidator.check(src, src.replace("in Rome ", ""), ScriptValidator.Limits.GENERAL))
    }
    @Test fun `new quotation marks fail`() {
        assertEquals("quotation marks", ScriptValidator.check(src, "Paul wrote \"to the church\" in Rome in 57 AD. He did not say the road would be easy, and it might take years.", study))
    }
    @Test fun `prophetic phrases fail unless the original had them`() {
        assertEquals("prophetic phrase", ScriptValidator.check(src, "$src God told me this.", ScriptValidator.Limits.GENERAL))
    }
    @Test fun `length and markup fail`() {
        assertEquals("length", ScriptValidator.check(src, "Paul wrote.", study))
        assertEquals("markup", ScriptValidator.check(src, "**$src**", study))
        assertEquals("empty", ScriptValidator.check(src, "  ", study))
    }

    // --- quotations ---
    @Test fun `quoted stretch and copied verse words are detected`() {
        assertTrue(QuotationDetector.containsQuotation("He said \"love your neighbour as yourself every single day\" often.", emptyList()))
        assertTrue(QuotationDetector.containsQuotation("Remember for God so loved the world that he gave, always.", listOf("For God so loved the world that he gave his one and only Son")))
        assertFalse(QuotationDetector.containsQuotation("God's love is large and steady.", listOf("For God so loved the world that he gave his one and only Son")))
    }

    // --- spoken form ---
    @Test fun `spoken form spells out shorthand and leaves numbers alone`() {
        assertEquals("Grow 10 percent for example, with prayer and so on.", SpokenForm.apply("Grow 10% e.g., with prayer etc."))
        assertEquals("Read this and that", SpokenForm.apply("Read this & that"))
    }

    // --- shaper ---
    private fun studyPart(): SpeechSection {
        val b = Blocks().apply {
            add(HEADING_2, "Observations", key = "observations", id = "h1")
            add(PARAGRAPH, src, key = "observations", id = "p1")
            add(PARAGRAPH, "He wrote \"for the Jews and the Greeks alike and all who believe\" in his letter.", key = "observations", id = "p2")
            scripture("John 3:16-17", key = "passage", id = "s1")
            add(PARAGRAPH, "My own prayer for this week and all the weeks that come after it.", key = "my_prayer", id = "p3")
        }
        return extract(b, lookupOf("John 3:16-17" to john316), privateKeys = emptySet()).sections.flatMap { listOf(it) }.let { secs ->
            SpeechSection("all", "All", secs.flatMap { it.units }, emptyList())
        }
    }

    private class FakeModel(val answer: (String) -> String?) : RewriteModel {
        var calls = 0
        val prompts = mutableListOf<String>()
        override suspend fun rewrite(prompt: String): String? { calls++; prompts += prompt; return answer(prompt) }
    }

    private fun store() = ScriptStore(tmp.newFile())

    @Test fun `only plain prose is sent and scripture quotes prayers and quotation blocks never are`() = runBlocking {
        val model = FakeModel { it.substringAfterLast("\n\n").replace(" did not say", " did not say that") }
        val part = studyPart()
        val shaped = SmartShaper(model, store(), listOf(john316.verses!!.joinToString(" ") { it.text })).shape(part)
        assertEquals(1, model.calls)
        assertTrue(model.prompts.single().contains("Paul wrote"))
        assertTrue(model.prompts.none { it.contains("my own prayer", ignoreCase = true) || it.contains("so loved the world") || it.contains("Jews and the Greeks") })
        // Locked and quotation units are word for word what they were.
        part.units.zip(shaped.units).filter { (a, _) -> a.locked || a.text.contains("Jews and the Greeks") }.forEach { (a, b) -> assertEquals(a.spoken, b.spoken) }
        assertTrue(shaped.units.first { it.blockIds == listOf("p1") }.text.contains("did not say that"))
    }

    @Test fun `a rewrite that breaks a rule is replaced by the original wording and recorded`() = runBlocking {
        val st = store()
        val model = FakeModel { src.replace("did not say", "did say") }
        val shaped = SmartShaper(model, st, emptyList()).shape(studyPart())
        assertEquals(src, shaped.units.first { it.blockIds == listOf("p1") }.text)
        assertEquals(2, model.calls) // one retry, then as written
        assertTrue(st.all().any { it.kind == ChangeKind.AS_WRITTEN && it.before == src })
    }

    @Test fun `a model that cannot answer leaves the wording as written`() = runBlocking {
        val shaped = SmartShaper(FakeModel { null }, store(), emptyList()).shape(studyPart())
        assertEquals(src, shaped.units.first { it.blockIds == listOf("p1") }.text)
    }

    @Test fun `without a model only the spoken form is applied`() = runBlocking {
        val b = Blocks().apply { add(PARAGRAPH, "Grow 10% in prayer, e.g., each morning and each evening this week.", id = "p1") }
        val part = extract(b).sections.single()
        val shaped = SmartShaper(null, store(), emptyList()).shape(part)
        assertEquals("Grow 10 percent in prayer, for example, each morning and each evening this week.", shaped.units.single().text)
    }

    @Test fun `a saved rewrite is used again without asking the model`() = runBlocking {
        val st = store()
        val first = FakeModel { it.substringAfterLast("\n\n").replace(" did not say", " did not say that") }
        SmartShaper(first, st, emptyList()).shape(studyPart())
        val second = FakeModel { error("should not be asked") }
        val shaped = SmartShaper(second, st, emptyList()).shape(studyPart())
        assertEquals(0, second.calls)
        assertTrue(shaped.units.first { it.blockIds == listOf("p1") }.text.contains("did not say that"))
    }

    @Test fun `private text is never sent`() = runBlocking {
        val b = Blocks().apply { add(PARAGRAPH, "A long private paragraph that must stay on this phone and never be rewritten anywhere.", key = "secret", id = "p1") }
        val part = extract(b, privateKeys = setOf("secret")).sections.single()
        val model = FakeModel { it }
        SmartShaper(model, store(), emptyList()).shape(part)
        assertEquals(0, model.calls)
    }
}
