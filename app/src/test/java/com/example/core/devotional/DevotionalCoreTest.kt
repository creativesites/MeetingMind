package com.example.core.devotional

import com.example.core.model.Note
import com.example.core.model.NoteDocument
import com.example.core.model.NoteStatus
import com.example.core.model.RecordingType
import com.example.core.scripture.ScriptureReferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevotionalCoreTest {

    @Test fun `profile survives a round trip and junk falls back to defaults`() {
        val p = DevotionalProfile(enabled = true, source = DevotionalSource.MIX, tradition = Tradition.CATHOLIC, minutes = 7,
            tone = DevotionalTone.POET, topics = setOf("Hope", "Rest"), season = "Exams", aboutMe = "Nurse", includePrayer = false,
            deliveryMinutes = 420, sharePrivateWithCloud = true, lessOf = setOf("Grief"), moreOf = setOf("Joy"))
        assertEquals(p, DevotionalProfile.fromJson(p.toJson()))
        assertEquals(DevotionalProfile(), DevotionalProfile.fromJson("not json"))
        assertEquals(DevotionalProfile(), DevotionalProfile.fromJson(null))
        assertEquals(DevotionalSource.AI, DevotionalProfile.fromJson("""{"source":"NOPE"}""").source)
    }

    @Test fun `easter dates, western and orthodox`() {
        assertEquals(LocalDate.of(2024, 3, 31), LiturgicalCalendar.westernEaster(2024))
        assertEquals(LocalDate.of(2025, 4, 20), LiturgicalCalendar.westernEaster(2025))
        assertEquals(LocalDate.of(2026, 4, 5), LiturgicalCalendar.westernEaster(2026))
        assertEquals(LocalDate.of(2024, 5, 5), LiturgicalCalendar.orthodoxEaster(2024))
        assertEquals(LocalDate.of(2025, 4, 20), LiturgicalCalendar.orthodoxEaster(2025))
        assertEquals(LocalDate.of(2026, 4, 12), LiturgicalCalendar.orthodoxEaster(2026))
    }

    @Test fun `seasons and feasts of the church year`() {
        fun d(y: Int, m: Int, day: Int) = LiturgicalCalendar.dayOf(LocalDate.of(y, m, day))
        assertEquals(LiturgicalDay(LiturgicalSeason.LENT, "Ash Wednesday"), d(2026, 2, 18))
        assertEquals(LiturgicalSeason.HOLY_WEEK, d(2026, 4, 3).season)
        assertEquals("Good Friday", d(2026, 4, 3).feast)
        assertEquals(LiturgicalSeason.EASTER, d(2026, 4, 20).season)
        assertEquals(LiturgicalDay(LiturgicalSeason.PENTECOST, "Pentecost Sunday"), d(2026, 5, 24))
        assertEquals(LiturgicalDay(LiturgicalSeason.ADVENT, "First Sunday of Advent"), d(2026, 11, 29))
        assertEquals(LiturgicalSeason.ORDINARY, d(2026, 11, 28).season)
        assertEquals(LiturgicalSeason.CHRISTMAS, d(2026, 12, 25).season)
        assertEquals(LiturgicalSeason.CHRISTMAS, d(2027, 1, 3).season)
        assertEquals(LiturgicalSeason.EPIPHANY, d(2027, 1, 6).season)
        assertEquals(LiturgicalSeason.ORDINARY, d(2026, 9, 24).season)
        assertEquals("Ordinary Time", d(2026, 9, 24).describe())
    }

    private fun bundled(): ClassicDevotionals =
        File("src/main/assets/${ClassicDevotionals.ASSET}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it)) }

    @Test fun `the bundled classic has a morning and evening for every day, with real references`() {
        val c = bundled()
        assertEquals(732, c.size)
        val jan1 = c.forDate(LocalDate.of(2026, 1, 1))!!
        assertEquals("Joshua 5:12", jan1.reference?.display())
        assertTrue(jan1.keyText.contains("Canaan"))
        assertTrue(jan1.paragraphs.size >= 2)
        assertEquals(true, c.forDate(LocalDate.of(2026, 1, 1), evening = true)?.evening)
        assertNotNull(c.forDate(LocalDate.of(2028, 2, 29)))
        var day = LocalDate.of(2025, 1, 1)
        var unparsed = 0
        while (day.year == 2025) {
            listOf(false, true).forEach { ev -> val r = c.forDate(day, ev)!!; if (r.reference == null) unparsed++ }
            day = day.plusDays(1)
        }
        assertTrue("unparsed references: $unparsed", unparsed <= 10)
    }

    @Test fun `quotes parse, carry attribution, and change daily but not within a day`() {
        val quotes = Quotes.parse(File("src/main/assets/devotionals/quotes.json").readText())
        assertTrue(quotes.size >= 30)
        assertTrue(quotes.all { it.author.isNotBlank() && it.text.isNotBlank() })
        val d = LocalDate.of(2026, 9, 24)
        assertEquals(Quotes.pick(quotes, setOf("Rest"), d), Quotes.pick(quotes, setOf("Rest"), d))
        assertTrue("Rest" in Quotes.pick(quotes, setOf("Rest"), d)!!.topics)
        assertNull(Quotes.pick(emptyList(), setOf("Rest"), d))
    }

    @Test fun `every topic and season passage is a real reference`() {
        (TopicPassages.byTopic.values.flatten() + TopicPassages.bySeason.values.flatten()).forEach {
            assertNotNull(it, ScriptureReferenceParser.parse(it))
        }
        assertTrue(TopicPassages.byTopic.keys.containsAll(DevotionalTopics.all))
        assertEquals(setOf("Rest"), TopicPassages.topicsOf(ScriptureReferenceParser.parse("Psalm 23")!!))
    }

    @Test fun `passage choice follows topics, avoids less-of, and uses the season on feasts`() {
        val date = LocalDate.of(2026, 9, 24)
        val ref = TopicPassages.pick(date, setOf("Anxiety"), emptySet(), null)!!
        assertTrue(TopicPassages.topicsOf(ref).contains("Anxiety"))
        assertNull(TopicPassages.pick(date, setOf("Anxiety"), setOf("Anxiety"), null))
        val easter = LocalDate.of(2026, 4, 5)
        val seasonal = TopicPassages.pick(easter, setOf("Anxiety"), emptySet(), LiturgicalCalendar.dayOf(easter))!!
        assertTrue(TopicPassages.bySeason.getValue(LiturgicalSeason.EASTER).map { ScriptureReferenceParser.parse(it) }.contains(seasonal))
    }

    private fun devotional() = Devotional(
        day = LocalDay("2026-09-24"), origin = DevotionalOrigin.CLOUD_AI, title = "Rest for the weary",
        scripture = listOf(ScriptureReferenceParser.parse("Matthew 11:28-30")!!, ScriptureReferenceParser.parse("Psalm 23")!!),
        reflection = listOf("First paragraph.", "Second paragraph."), application = listOf("Take a walk", "Call a friend"),
        prayer = "Lord, teach me to rest. Amen.", motivation = "You are held.", insight = Quote("All shall be well.", "Julian of Norwich", "Revelations", emptySet()),
        question = "Where do you need rest?", label = DevotionalLabels.CLOUD, engine = "gemini-x"
    )

    private fun doc(d: Devotional): NoteDocument {
        val (blocks, refs) = DevotionalNotes.build("n1", d)
        val note = Note("n1", d.title, RecordingType.DEVOTIONAL, null, 0, 0, 0, false, false, NoteStatus.OPEN, null, DevotionalNotes.metadata(d))
        return NoteDocument(note, blocks, emptyList(), emptyList(), refs)
    }

    @Test fun `a devotional becomes a note and reads back the same`() {
        val d = devotional()
        val doc = doc(d)
        assertEquals(2, doc.scriptureRefs.size)
        assertEquals("2026-09-24", doc.note.metadata[DevotionalNotes.META_KEY])
        val back = DevotionalNotes.read(doc)!!
        assertEquals(d.copy(season = null), back)
        assertEquals("", DevotionalNotes.response(doc))
    }

    @Test fun `the response is written into its own section`() {
        val doc = doc(devotional())
        val blocks = DevotionalNotes.withResponse(doc, "Slow down.\nBreathe.")
        val withResponse = doc.copy(blocks = blocks.mapIndexed { i, b -> b.copy(position = i) })
        assertEquals("Slow down.\nBreathe.", DevotionalNotes.response(withResponse))
        assertEquals(devotional().reflection, DevotionalNotes.read(withResponse)!!.reflection)
    }

    @Test fun `classic readings keep the author's key text`() {
        val d = devotional().copy(origin = DevotionalOrigin.CLASSIC, keyText = "“Come unto me.”", application = emptyList(), prayer = null)
        val back = DevotionalNotes.read(doc(d))!!
        assertEquals("“Come unto me.”", back.keyText)
        assertEquals(DevotionalOrigin.CLASSIC, back.origin)
    }

    @Test fun `delays to the next delivery time`() {
        val now = LocalDateTime.of(2026, 9, 24, 5, 0)
        assertEquals(Duration.ofMinutes(90), DevotionalScheduler.delayUntil(now, 6 * 60 + 30))
        assertEquals(Duration.ofHours(23), DevotionalScheduler.delayUntil(now, 4 * 60))
        assertEquals(Duration.ZERO, DevotionalScheduler.delayUntil(now, 4 * 60, allowNow = true))
        // Negative minute-of-day: the evening before (a 00:30 delivery written at 23:00).
        assertEquals(Duration.ofHours(18), DevotionalScheduler.delayUntil(now, -60))
    }
}
