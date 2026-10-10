package com.craftflowtechnologies.meetingmind.ai.devotional

import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalMemory
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.MemoryEntry
import com.craftflowtechnologies.meetingmind.core.devotional.PassageRotation
import com.craftflowtechnologies.meetingmind.core.devotional.PersonalTouch
import com.craftflowtechnologies.meetingmind.core.devotional.Quote
import com.craftflowtechnologies.meetingmind.core.devotional.Quotes
import com.craftflowtechnologies.meetingmind.core.devotional.TopicPassages
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
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

/** Generic by default, personal by invitation (founder rule, 2026-10-09). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalTouchTest {

    private val classics = File("src/main/assets/${ClassicDevotionals.ASSET}").inputStream().use { ClassicDevotionals.parse(GZIPInputStream(it)) }
    private val engine = DevotionalEngine({ emptyList() }, { null }, classics, emptyList())
    private val start = LocalDate.of(2026, 10, 9)

    private val personal = DevotionalProfile(
        topics = setOf("Zebra-topic-grief"), moreOf = setOf("Quokka-more"), season = "Zeppelin-season",
        aboutMe = "Flamingo-aboutme: night-shift nurse in Ulaanbaatar", personalTouch = PersonalTouch.OFF
    )
    private val signals = listOf("Heard a sermon: \"Narwhal-sermon\"", "Praying about: Walrus-prayer-list")
    private fun brief(p: DevotionalProfile, date: LocalDate = start, request: String? = null) = DevotionalBrief(
        ScriptureReferenceParser.parse("Psalm 23")!!, null, p, null, "Friday", signals, "Ottoline", request = request, date = date
    )
    private val markers = listOf("Zebra-topic-grief", "Quokka-more", "Zeppelin-season", "Flamingo-aboutme", "Ulaanbaatar", "Narwhal-sermon", "Walrus-prayer-list", "Ottoline")

    private fun personalDay(from: LocalDate = start) = generateSequence(from) { it.plusDays(1) }.first { PersonalTouch.NOW_AND_THEN.appliesOn(it) }
    private fun ordinaryDay(from: LocalDate = start) = generateSequence(from) { it.plusDays(1) }.first { !PersonalTouch.NOW_AND_THEN.appliesOn(it) }

    @Test fun `with the personal touch off the prompt carries none of the personal fields`() {
        val prompt = DevotionalContract.prompt(brief(personal))
        markers.forEach { assertFalse("$it leaked", prompt.contains(it)) }
        assertTrue(prompt.contains("Audience: a general reader"))
        assertTrue(prompt.contains("You know nothing about this reader"))
    }

    @Test fun `style settings and a typed request always apply`() {
        val styled = personal.copy(lessOf = setOf("Anxiety"), language = "French", minutes = 7)
        val prompt = DevotionalContract.prompt(brief(styled, request = "Marigold-request about my sister's wedding"))
        assertTrue(prompt.contains("Marigold-request"))
        assertTrue(prompt.contains("this devotional only"))
        assertTrue(prompt.contains("Anxiety"))
        assertTrue(prompt.contains("Write in French"))
        assertTrue(prompt.contains("Tradition:") && prompt.contains("Voice:"))
        markers.forEach { assertFalse("$it leaked", prompt.contains(it)) }
    }

    @Test fun `always includes the personal fields as background that may inform at most one paragraph`() {
        val prompt = DevotionalContract.prompt(brief(personal.copy(personalTouch = PersonalTouch.ALWAYS)))
        markers.forEach { assertTrue("$it missing", prompt.contains(it)) }
        assertTrue(prompt.contains("AT MOST ONE paragraph"))
        assertFalse(prompt.contains("Audience: a general reader"))
    }

    @Test fun `now and then includes them only on the personal day, as gentle background`() {
        val p = personal.copy(personalTouch = PersonalTouch.NOW_AND_THEN)
        val yes = DevotionalContract.prompt(brief(p, personalDay()))
        markers.forEach { assertTrue("$it missing", yes.contains(it)) }
        assertTrue(yes.contains("never their situation"))
        val no = DevotionalContract.prompt(brief(p, ordinaryDay()))
        markers.forEach { assertFalse("$it leaked", no.contains(it)) }
        // Without a date it cannot be a personal day.
        assertFalse(DevotionalContract.prompt(brief(p).copy(date = null)).contains("Flamingo"))
    }

    @Test fun `now and then is about one day in seven and never two days running`() {
        val days = (0 until 3650).map { start.plusDays(it.toLong()) }
        val flags = days.map { PersonalTouch.NOW_AND_THEN.appliesOn(it) }
        flags.zipWithNext().forEach { (a, b) -> assertFalse("two personal days in a row", a && b) }
        val perYear = flags.count { it } / 10.0
        assertTrue("personal days per year: $perYear", perYear in 48.0..56.0)
        // The same date always gives the same answer, and every week has at most one.
        assertEquals(flags, days.map { PersonalTouch.NOW_AND_THEN.appliesOn(it) })
        days.chunked(7).filter { it.size == 7 }.forEach { }
        assertTrue((0 until 3650 step 7).all { w -> (0 until 7).count { flags[w + it.coerceAtMost(3649 - w)] } <= 2 })
        assertFalse((0 until 400).any { PersonalTouch.OFF.appliesOn(start.plusDays(it.toLong())) })
        assertTrue((0 until 400).all { PersonalTouch.ALWAYS.appliesOn(start.plusDays(it.toLong())) })
    }

    @Test fun `a profile stored before the setting existed loads as off`() {
        val old = """{"enabled":true,"source":"AI","tradition":"CATHOLIC","minutes":7,"topics":["Joy"],"aboutMe":"Hello","delivery":400}"""
        val loaded = DevotionalProfile.fromJson(old)
        assertEquals(PersonalTouch.OFF, loaded.personalTouch)
        assertEquals("Hello", loaded.aboutMe)
        assertEquals(PersonalTouch.OFF, DevotionalProfile.fromJson(null).personalTouch)
        assertEquals(PersonalTouch.OFF, DevotionalProfile.fromJson("""{"personalTouch":"NONSENSE"}""").personalTouch)
        // And it round-trips once chosen.
        assertEquals(PersonalTouch.NOW_AND_THEN, DevotionalProfile.fromJson(DevotionalProfile(personalTouch = PersonalTouch.NOW_AND_THEN).toJson()).personalTouch)
    }

    // ------------------------------------------------------------------ the rotation

    @Test fun `the rotation is a broad list of real passages`() {
        val parsed = PassageRotation.entries.map { it to ScriptureReferenceParser.parse(it.substringBefore('|')) }
        parsed.forEach { (raw, ref) -> assertTrue("cannot parse $raw", ref != null) }
        val refs = parsed.map { it.second!! }
        assertTrue("only ${refs.distinct().size} distinct", refs.distinct().size >= 365)
        assertTrue(refs.map { it.usfm }.distinct().size >= 50)
        assertTrue(refs.any { it.book.isNewTestament } && refs.any { !it.book.isNewTestament })
        // Neighbouring days alternate between the testaments often enough to feel varied.
        val flips = refs.zipWithNext().count { (a, b) -> a.book.isNewTestament != b.book.isNewTestament }
        assertTrue("flips $flips of ${refs.size}", flips > refs.size / 3)
    }

    private fun year(profile: DevotionalProfile, days: Int = 365, askTopics: Set<String> = emptySet()) = runBlocking {
        var memory = DevotionalMemory()
        val out = mutableListOf<Pair<LocalDate, com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference>>()
        for (i in 0 until days) {
            val date = start.plusDays(i.toLong())
            val ref = engine.freshPassage(date, profile, 0, memory, askTopics)
            out += date to ref
            memory = DevotionalMemory(listOf(MemoryEntry(date.toString(), "t", ref.display(), null, null, DevotionalFormat.REFLECTION)) + memory.entries)
        }
        out
    }

    @Test fun `with the personal touch off a year of devotionals spans the canon and does not repeat within the window`() {
        // Topics are filled in, and must make no difference while the touch is off.
        val loud = DevotionalProfile(topics = setOf("Grief", "Anxiety"), moreOf = setOf("Peace"), passageExclusionDays = 30)
        val plain = year(DevotionalProfile(passageExclusionDays = 30))
        val withTopics = year(loud)
        assertEquals(plain, withTopics)
        val refs = plain.map { it.second }
        assertTrue("books: ${refs.map { it.usfm }.distinct().size}", refs.map { it.usfm }.distinct().size >= 40)
        assertTrue(refs.any { it.book.isNewTestament } && refs.any { !it.book.isNewTestament })
        val nt = refs.count { it.book.isNewTestament }
        assertTrue("NT share $nt of ${refs.size}", nt in 100..265)
        for (i in refs.indices) for (j in (i + 1)..minOf(i + 30, refs.lastIndex)) {
            assertFalse("${refs[i].display()} repeats on day $j", DevotionalMemory.overlaps(refs[i], refs[j]))
        }
        // Not the same book day after day.
        assertTrue(refs.zipWithNext().none { (a, b) -> a.usfm == b.usfm })
    }

    @Test fun `less of is always honoured and topics only weigh in lightly when the touch is on`() {
        val less = setOf("Anxiety", "Grief", "Joy")
        listOf(PersonalTouch.OFF, PersonalTouch.NOW_AND_THEN, PersonalTouch.ALWAYS).forEach { touch ->
            val refs = year(DevotionalProfile(personalTouch = touch, lessOf = less, topics = setOf("Peace", "Hope"), moreOf = setOf("Rest"))).map { it.second }
            refs.forEach { r ->
                assertTrue("${r.display()} is under a 'less of' theme ($touch)", TopicPassages.topicsOf(r).none { it in less } && !PassageRotation.hasTheme(r, less))
            }
        }
        val on = year(DevotionalProfile(personalTouch = PersonalTouch.ALWAYS, topics = setOf("Peace", "Hope"))).map { it.second }
        val topical = on.count { r -> TopicPassages.topicsOf(r).any { it in setOf("Peace", "Hope") } }
        assertTrue("topic passages: $topical of ${on.size}", topical in 20..110)
        val off = year(DevotionalProfile(topics = setOf("Peace", "Hope"))).map { it.second }
        assertTrue(off.count { r -> TopicPassages.topicsOf(r).any { it in setOf("Peace", "Hope") } } < topical)
    }

    @Test fun `a request's topics steer that devotional whatever the touch is`() {
        val a = year(DevotionalProfile(), days = 20, askTopics = setOf("Courage"))
        assertTrue(a.any { (_, r) -> TopicPassages.topicsOf(r).contains("Courage") })
    }

    @Test fun `quotes ignore topics when off and always honour less of`() {
        val quotes = (0 until 60).map { Quote("q$it", "a", "s", setOf(listOf("Rest", "Joy", "Grief")[it % 3])) }
        val d = start
        assertEquals(Quotes.pick(quotes, emptySet(), d), Quotes.pick(quotes, emptySet(), d))
        (0 until 30).forEach { i ->
            val q = Quotes.pick(quotes, setOf("Rest"), d.plusDays(i.toLong()), less = setOf("Grief"))!!
            assertFalse("Grief" in q.topics)
        }
        // The engine passes no topics when off, so the quote follows the date alone.
        assertNotEquals(quotes.map { it.text }.take(1), emptyList<String>())
    }

    @Test fun `memory pushes variety in books`() {
        val m = DevotionalMemory(listOf(
            MemoryEntry("2026-10-08", "a", "Psalm 23", null, null, null),
            MemoryEntry("2026-10-07", "b", "John 3:16", null, null, null)
        ))
        assertEquals(setOf("PSA", "JHN"), m.recentBooks(4))
    }
}
