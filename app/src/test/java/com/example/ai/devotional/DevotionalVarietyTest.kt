package com.example.ai.devotional

import com.example.ai.common.AiResult
import com.example.ai.faith.PromptAsset
import com.example.ai.faith.Prompts
import com.example.ai.llm.LanguageModel
import com.example.core.devotional.ClassicDevotionals
import com.example.core.devotional.Devotional
import com.example.core.devotional.DevotionalFormat
import com.example.core.devotional.DevotionalMemory
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalSeries
import com.example.core.devotional.LocalDay
import com.example.core.devotional.MemoryEntry
import com.example.core.devotional.SeriesProgress
import com.example.core.devotional.TraditionPreset
import com.example.core.scripture.ScriptureReferenceParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevotionalVarietyTest {

    private class Scripted(vararg answers: String) : LanguageModel {
        private val queue = ArrayDeque(answers.toList())
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            prompts += prompt
            return AiResult.Success(queue.removeFirstOrNull() ?: prompts.let { "" })
        }
    }

    private fun answer(title: String, opening: String, point: String) =
        """{"title":"$title","references":[],"reflection":["$opening And more words follow here."],"application":["$point"],"prayer":"Lord, thank you. Amen.","motivation":"Go well.","question":"What now?"}"""

    private val classics = File("src/main/assets/${ClassicDevotionals.ASSET}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it)) }
    private val day = LocalDate.of(2026, 10, 5)
    private fun engine(m: LanguageModel) = DevotionalEngine({ listOf(ModelCandidate(m, "gemini-test", true)) }, { null }, classics, emptyList())

    @Test fun `the same format never comes twice in a row, and every format in the pool comes round`() {
        val pool = DevotionalFormat.DEFAULT_ROTATION
        var last: DevotionalFormat? = null
        val seen = mutableListOf<DevotionalFormat>()
        for (i in 0 until 60) {
            val f = DevotionalFormat.pick(pool, 1000L + i, last, seen.asReversed())
            assertNotEquals("day $i repeated $last", last, f)
            seen += f; last = f
        }
        assertEquals(pool, seen.toSet())
    }

    @Test fun `a passage used within the exclusion window is not chosen again`() = runBlocking {
        val e = engine(Scripted())
        val profile = DevotionalProfile(topics = setOf("Peace"), passageExclusionDays = 30)
        val usual = e.passageFor(day, profile)
        val memory = DevotionalMemory(listOf(MemoryEntry(day.minusDays(3).toString(), "Old", usual.display(), null, null, DevotionalFormat.REFLECTION)))
        val fresh = e.freshPassage(day, profile, 0, memory)
        assertFalse(DevotionalMemory.overlaps(fresh, usual))
        // Outside the window it may come back.
        val old = DevotionalMemory(listOf(MemoryEntry(day.minusDays(40).toString(), "Old", usual.display(), null, null, null)))
        assertEquals(usual, e.freshPassage(day, profile, 0, old))
    }

    @Test fun `a draft that repeats a recent devotional is rewritten once`() = runBlocking {
        val memory = DevotionalMemory(listOf(MemoryEntry(day.minusDays(1).toString(), "Held in the middle of it", "Psalm 46:1", "From your room in Lusaka, the city hums with life.", "Pause before your first meeting", DevotionalFormat.REFLECTION)))
        val model = Scripted(
            answer("Held in the middle of it", "From your room in Lusaka, the city hums with life.", "Pause before your first meeting"),
            answer("Bread for the road", "Elijah slept under a broom tree.", "Eat something slowly today")
        )
        val d = engine(model).write(day, DevotionalProfile(), memory = memory)
        assertEquals("Bread for the road", d.title)
        assertEquals(2, model.prompts.size)
        assertTrue(model.prompts[1].contains("previous draft was rejected because"))
        assertTrue(model.prompts[0].contains("\"Held in the middle of it\""))
    }

    @Test fun `the chosen format is in the prompt and recorded on the devotional`() = runBlocking {
        val model = Scripted(answer("Slow reading", "Read it twice.", "Notice one word"))
        val d = engine(model).write(day, DevotionalProfile(rotateFormats = false, fixedFormat = DevotionalFormat.LECTIO_DIVINA))
        assertEquals(DevotionalFormat.LECTIO_DIVINA, d.format)
        assertTrue(model.prompts.single().contains("Format — Lectio Divina"))
        assertTrue(model.prompts.single().contains("Benedictine and contemplative"))
    }

    @Test fun `a series sets the passage and carries the earlier days`() = runBlocking {
        val s = DevotionalSeries.catalog.first { it.id == "philippians7" }
        val progress = SeriesProgress(s.id, s.title, s.passages, day.minusDays(2).toEpochDay())
        val memory = DevotionalMemory(listOf(MemoryEntry(day.minusDays(1).toString(), "Joy in chains", "Philippians 1:12-26", "Paul wrote from prison.", "Look for good in a hard place", DevotionalFormat.REFLECTION, series = s.title)))
        val model = Scripted(answer("The mind of Christ", "Humility is strength.", "Serve someone quietly"))
        val d = engine(model).write(day, DevotionalProfile(series = progress), memory = memory)
        assertEquals("Philippians 2:1-11", d.scripture.first().display().replace('–', '-'))
        assertEquals(3, d.seriesDay)
        assertTrue(model.prompts.single().contains("7 Days in Philippians, day 3 of 7"))
        assertTrue(model.prompts.single().contains("earlier: Joy in chains"))
    }

    @Test fun `the evening Examen looks back on the morning`() = runBlocking {
        val morning = Devotional(LocalDay.of(day), DevotionalOrigin.CLOUD_AI, "Bread for the road", listOf(ScriptureReferenceParser.parse("1 Kings 19:1-8")!!),
            reflection = listOf("Elijah slept."), application = listOf("Eat something slowly today"), label = "AI")
        val model = Scripted(answer("Looking back", "Where was God today?", "Name one gift"))
        val d = engine(model).write(day, DevotionalProfile(eveningExamen = true), evening = true, morning = morning)
        assertEquals(DevotionalFormat.DAILY_EXAMEN, d.format)
        assertTrue(model.prompts.single().contains("follows this morning's devotional"))
        assertTrue(model.prompts.single().contains("Eat something slowly today"))
        assertTrue(d.scripture.first().display().startsWith("1 Kings 19"))
    }

    @Test fun `presets set a coherent starting point that stays editable`() {
        val p = TraditionPreset.CONTEMPLATIVE.applyTo(DevotionalProfile())
        assertTrue(DevotionalFormat.LECTIO_DIVINA in p.formats && DevotionalFormat.BREATH_PRAYER in p.formats)
        assertEquals(TraditionPreset.CONTEMPLATIVE, p.preset)
        val edited = p.copy(formats = p.formats - DevotionalFormat.QUIET)
        assertEquals(edited, DevotionalProfile.fromJson(edited.toJson()))
    }

    @Test fun `every prompt asset declares its contract`() {
        val dir = File("src/main/assets/prompts")
        val files = dir.listFiles { f -> f.extension == "md" }!!.toList()
        assertTrue(files.size >= 2)
        files.forEach { f ->
            val p = PromptAsset.parse(f.readText())
            PromptAsset.REQUIRED_FIELDS.forEach { assertTrue("${f.name} lacks $it", p.fields[it]?.isNotBlank() == true) }
            PromptAsset.REQUIRED_SECTIONS.forEach { assertTrue("${f.name} lacks section $it", p.sections[it]?.isNotBlank() == true) }
            assertEquals(f.nameWithoutExtension, p.id)
            assertTrue(p.version > 0)
        }
        assertTrue(Prompts.faithContract.contains("Never claim to speak for God"))
    }
}
