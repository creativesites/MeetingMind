package com.example.ai.devotional

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.devotional.ClassicDevotionals
import com.example.core.devotional.DevotionalLabels
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalSource
import com.example.core.devotional.Quote
import com.example.core.scripture.ScriptureReferenceParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevotionalEngineTest {

    private class FakeModel(private val answer: String?) : LanguageModel {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            prompts += prompt
            return answer?.let { AiResult.Success(it) } ?: AiResult.Failed("offline")
        }
    }

    private val classics = File("src/main/assets/${ClassicDevotionals.ASSET}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it)) }
    private val quotes = listOf(Quote("All shall be well.", "Julian of Norwich", "Revelations", setOf("Hope")))
    private val thursday = LocalDate.of(2026, 9, 24)
    private val sunday = LocalDate.of(2026, 9, 27)

    private val good = """
        Here you go:
        {"title":"Rest for the weary","references":["Psalm 23","Nowhere 99:1"],
         "reflection":["Jesus invites the tired to come. \"Come unto me, all ye that labour and are heavy laden\" (Matthew 11:28)","Rest is a gift, not a reward."],
         "application":["Take ten quiet minutes"],"prayer":"Lord, I bring you my tiredness. Amen.","motivation":"You are held today.","question":"What would rest look like?"}
    """.trimIndent()

    private fun engine(vararg models: Pair<LanguageModel, Boolean>) = DevotionalEngine(
        candidates = { models.mapIndexed { i, (m, cloud) -> ModelCandidate(m, if (cloud) "gemini-test" else "local-$i", cloud) } },
        verseText = { ref -> if (ref.display() == "Matthew 11:28") "Come unto me, all ye that labour and are heavy laden, and I will give you rest." else null },
        classics = classics, quotes = quotes, locale = Locale.UK
    )

    private val profile = DevotionalProfile(topics = setOf("Rest"))

    @Test fun `a good cloud answer becomes a labelled AI devotional on a real passage`() = runBlocking {
        val model = FakeModel(good)
        val d = engine(model to true).write(thursday, profile)
        assertEquals(DevotionalOrigin.CLOUD_AI, d.origin)
        assertEquals(DevotionalLabels.CLOUD, d.label)
        assertEquals("gemini-test", d.engine)
        assertEquals("Rest for the weary", d.title)
        // The passage is chosen on the phone (a Rest passage); the model's bad reference is dropped.
        assertTrue(com.example.core.devotional.TopicPassages.topicsOf(d.scripture.first()).contains("Rest"))
        assertFalse(d.scripture.any { it.display().startsWith("Nowhere") })
        assertEquals("Julian of Norwich", d.insight?.author)
        assertTrue(d.reflection[0].contains("heavy laden"))
        assertTrue(model.prompts.single().contains(DevotionalContract.CONTRACT))
    }

    @Test fun `private lines reach the cloud only with permission, and the device always`() = runBlocking {
        val signals = DevotionalSignals(general = listOf("Heard a sermon: \"Hope\""), private = listOf("Praying about: my dad's surgery"))
        val cloud = FakeModel(good)
        engine(cloud to true).write(thursday, profile, signals)
        assertFalse(cloud.prompts.single().contains("surgery"))
        assertTrue(cloud.prompts.single().contains("Heard a sermon"))

        val allowed = FakeModel(good)
        engine(allowed to true).write(thursday, profile.copy(sharePrivateWithCloud = true), signals)
        assertTrue(allowed.prompts.single().contains("surgery"))

        val device = FakeModel(good)
        engine(device to false).write(thursday, profile, signals)
        assertTrue(device.prompts.single().contains("surgery"))
    }

    @Test fun `a prophetic answer is rejected and the next model is tried`() = runBlocking {
        val bad = """{"title":"A word","reflection":["God is telling you that a new job is coming. Thus says the Lord: rise. I declare victory. Your breakthrough is coming."]}"""
        val cloud = FakeModel(bad)
        val local = FakeModel(good)
        val d = engine(cloud to true, local to false).write(thursday, profile)
        assertEquals(DevotionalOrigin.DEVICE_AI, d.origin)
        assertEquals(DevotionalLabels.DEVICE, d.label)
        assertEquals(1, local.prompts.size)
    }

    @Test fun `with no model the day's classic is used, and the reason is kept`() = runBlocking {
        val e = engine(FakeModel(null) to true)
        val d = e.write(thursday, profile)
        assertEquals(DevotionalOrigin.CLASSIC, d.origin)
        assertTrue(d.label.contains("Spurgeon"))
        assertEquals("offline", e.lastFallbackReason)
        val none = engine()
        assertEquals(DevotionalOrigin.CLASSIC, none.write(thursday, profile).origin)
    }

    @Test fun `crisis words in the person's own writing bring care instead of AI`() = runBlocking {
        val model = FakeModel(good)
        val d = engine(model to true).write(thursday, profile, DevotionalSignals(recentWords = listOf("Some days I want to die.")))
        assertEquals(DevotionalOrigin.CARE, d.origin)
        assertTrue(model.prompts.isEmpty())
        assertTrue(d.reflection.any { it.contains("988") })
    }

    @Test fun `classic, mix and my-own sources`() = runBlocking {
        val model = FakeModel(good)
        val e = engine(model to true)
        assertEquals(DevotionalOrigin.CLASSIC, e.write(thursday, profile.copy(source = DevotionalSource.CLASSIC)).origin)
        assertEquals(DevotionalOrigin.CLASSIC, e.write(sunday, profile.copy(source = DevotionalSource.MIX)).origin)
        assertEquals(DevotionalOrigin.CLOUD_AI, e.write(thursday, profile.copy(source = DevotionalSource.MIX)).origin)
        val mine = e.write(thursday, profile.copy(source = DevotionalSource.MINE))
        assertEquals(DevotionalOrigin.MINE, mine.origin)
        assertEquals(1, mine.scripture.size)
        assertTrue(mine.reflection.isEmpty())
        val evening = e.write(thursday, profile, evening = true)
        assertEquals(DevotionalOrigin.CLASSIC, evening.origin)
        assertTrue(evening.engine!!.contains("evening"))
    }

    @Test fun `the guard removes claims to speak for God and promised outcomes`() {
        val (kept, removed) = DevotionalContract.removeForbidden(
            "Rest is a gift. God is telling you to quit. You will be healed by Friday. He is near."
        )
        assertEquals("Rest is a gift. He is near.", kept)
        assertEquals(2, removed)
        assertEquals(0, DevotionalContract.removeForbidden("The Lord is my shepherd, the psalmist says.").second)
    }

    @Test fun `misquoted scripture is replaced by the real text or reduced to its reference`() = runBlocking {
        val real = "Come unto me, all ye that labour and are heavy laden, and I will give you rest."
        val lookup: suspend (com.example.core.scripture.ScriptureReference) -> String? = { if (it.display() == "Matthew 11:28") real else null }
        val wrong = DevotionalContract.fixQuotedScripture("He said “Everyone who is tired should take a long holiday” (Matthew 11:28).", lookup)
        assertTrue(wrong, wrong.contains("heavy laden"))
        val unknown = DevotionalContract.fixQuotedScripture("As written, “Something the model made up entirely here” (John 3:16).", lookup)
        assertTrue(unknown, unknown.contains("(see John 3:16)") && !unknown.contains("made up"))
        val right = "“Come unto me, all ye that labour and are heavy laden” (Matthew 11:28)"
        assertEquals(right, DevotionalContract.fixQuotedScripture(right, lookup))
    }

    @Test fun `answers are read from json with or without arrays`() {
        val a = DevotionalContract.parse("""{"title":"T","reflection":"One.\n\nTwo.","application":"Do it","prayer":"Amen"}""")!!
        assertEquals(listOf("One.", "Two."), a.reflection)
        assertEquals(listOf("Do it"), a.application)
        assertNull(DevotionalContract.parse("""{"title":"T"}"""))
        assertNull(DevotionalContract.parse("no json"))
    }

    @Test fun `the prompt carries the passage, tradition, voice and requested parts only`() {
        val brief = DevotionalBrief(
            ScriptureReferenceParser.parse("Psalm 23")!!, "The Lord is my shepherd", profile.copy(includePrayer = false, tradition = com.example.core.devotional.Tradition.CATHOLIC),
            null, "Thursday", emptyList(), "Ana"
        )
        val p = DevotionalContract.prompt(brief)
        assertTrue(p.contains("Passage: Psalm 23"))
        assertTrue(p.contains("Catholic"))
        assertTrue(p.contains("warm, unhurried pastor"))
        assertFalse(p.contains("\"prayer\""))
        assertTrue(p.contains("Ana"))
        listOf("Do not quote Bible verses", "Never claim to speak for God", "no medical, legal, financial", "Output only JSON").forEach { assertTrue(it, p.contains(it)) }
    }

    @Test fun `crisis detection`() {
        assertTrue(DevotionalContract.crisisIn(listOf("I keep thinking about suicide")))
        assertFalse(DevotionalContract.crisisIn(listOf("Died to self, alive in Christ", "a killer workout")))
    }

    @Test fun `a chosen writer is kept to, and failing is said rather than papered over`() = runBlocking {
        // Gemini chosen, Gemini fails: no classic stand-in, no blank page — an error.
        val e = runCatching { engine(FakeModel(null) to true, FakeModel(good) to false).write(thursday, profile, ask = DevotionalAsk(writer = DevotionalWriter.GEMINI)) }.exceptionOrNull()
        assertTrue(e is DevotionalUnavailable)
        // The phone chosen: only the phone's model is used, even when Gemini is there.
        val cloud = FakeModel(good)
        val d = engine(cloud to true, FakeModel(good) to false).write(thursday, profile, ask = DevotionalAsk(writer = DevotionalWriter.DEVICE))
        assertEquals(DevotionalOrigin.DEVICE_AI, d.origin)
        assertTrue(cloud.prompts.isEmpty())
        // Nothing on the phone: said so.
        assertTrue(runCatching { engine(FakeModel(good) to true).write(thursday, profile, ask = DevotionalAsk(writer = DevotionalWriter.DEVICE)) }.exceptionOrNull() is DevotionalUnavailable)
        // A classic, by choice, even with AI around.
        assertEquals(DevotionalOrigin.CLASSIC, engine(FakeModel(good) to true).write(thursday, profile, ask = DevotionalAsk(writer = DevotionalWriter.CLASSIC)).origin)
        // The automatic chain still ends in a classic, never an empty page, even many "another"s in.
        val auto = engine(FakeModel(null) to true).write(thursday, profile, ask = DevotionalAsk(variant = 9))
        assertEquals(DevotionalOrigin.CLASSIC, auto.origin)
        assertTrue(auto.reflection.isNotEmpty())
    }

    @Test fun `the writer survives the trip through the work queue`() {
        assertEquals(DevotionalWriter.GEMINI, DevotionalAsk.fromJson(DevotionalAsk(writer = DevotionalWriter.GEMINI).toJson())!!.writer)
    }
}
