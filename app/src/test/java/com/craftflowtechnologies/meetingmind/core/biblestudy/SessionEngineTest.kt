package com.craftflowtechnologies.meetingmind.core.biblestudy

import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionEngineTest {

    private fun ref(s: String) = ScriptureReferenceParser.parse(s)!!

    @Test fun `today, need and plan share one shape ending in a close`() {
        val expected = listOf(StepType.WHY, StepType.READ, StepType.OBSERVE, StepType.OBSERVE, StepType.APPLY, StepType.PRAYER, StepType.LIVE, StepType.CLOSE)
        listOf(SessionKind.TODAY, SessionKind.NEED, SessionKind.PLAN).forEach { kind ->
            assertEquals(kind.name, expected, SessionPlan.steps(kind).map { it.type })
        }
        // Only the questions, the prayer and the live-it line take writing, and all of it is optional.
        val steps = SessionPlan.steps(SessionKind.TODAY)
        assertEquals(listOf("q1", "q2", "q3", "prayer", "live"), steps.filter { it.answerable }.map { it.id })
        assertEquals(steps.size, steps.map { it.id }.distinct().size)
    }

    @Test fun `SOAP is scripture, observation, application, prayer`() {
        val steps = SessionPlan.steps(SessionKind.SOAP)
        assertEquals(listOf("Scripture", "Observation", "Application", "Prayer", "Done"), steps.map { it.title })
        assertEquals(listOf(false, true, true, true, false), steps.map { it.answerable })
    }

    @Test fun `verse by verse has one step for each verse and then the close`() {
        val steps = SessionPlan.steps(SessionKind.VERSE_BY_VERSE, listOf(16, 17, 18))
        assertEquals(listOf("verse-16", "verse-17", "verse-18", "close"), steps.map { it.id })
        assertEquals(listOf(16, 17, 18, null), steps.map { it.verse })
        // Without verse numbers (text not loaded) it is still one step for the whole passage.
        assertEquals(listOf("verse-all", "close"), SessionPlan.steps(SessionKind.VERSE_BY_VERSE).map { it.id })
    }

    @Test fun `moving through a session stops at both ends`() {
        var s = SessionState(SessionKind.SOAP, SessionPlan.steps(SessionKind.SOAP))
        assertTrue(s.isFirst); assertEquals(s, s.back())
        repeat(10) { s = s.next() }
        assertTrue(s.isLast); assertEquals(StepType.CLOSE, s.current.type)
        assertEquals(1f, s.progress, 0f)
        assertEquals(0f, SessionState(SessionKind.SOAP, SessionPlan.steps(SessionKind.SOAP)).progress, 0f)
    }

    @Test fun `a session nobody has written in is untouched`() {
        var s = SessionState(SessionKind.TODAY, SessionPlan.steps(SessionKind.TODAY))
        assertFalse(s.touched)
        s = s.answer("q1", "   ")
        assertFalse("spaces are not writing", s.touched)
        assertTrue(s.written().isEmpty())
        s = s.answer("q1", "He notices them")
        assertTrue(s.touched)
        s = s.answer("q1", "")
        assertFalse("clearing it puts it back", s.touched)
    }

    @Test fun `written answers come back in step order under the question that was asked`() {
        var s = SessionState(SessionKind.TODAY, SessionPlan.steps(SessionKind.TODAY))
        s = s.withPrompt("q1", "What stands out?").answer("prayer", "Thank you").answer("q1", "The sheep")
        val w = s.written()
        assertEquals(listOf("q1", "prayer"), w.map { it.stepId })
        assertEquals("What stands out?", w[0].heading)
        assertEquals("Prayer", w[1].heading)
        assertEquals("The sheep", w[0].text)
    }

    @Test fun `the question bank always has a question, and the same one for the same passage`() {
        listOf(StepType.OBSERVE, StepType.APPLY, StepType.VERSE).forEach { t ->
            (listOf<Need?>(null) + Need.entries).forEach { need ->
                (0..3).forEach { o ->
                    val q = QuestionBank.question(t, need, QuestionBank.seedFor(ref("John 3:16")), o)
                    assertTrue("$t $need $o", q.endsWith("?"))
                    assertEquals(q, QuestionBank.question(t, need, QuestionBank.seedFor(ref("John 3:16")), o))
                }
            }
        }
        Need.entries.forEach { assertTrue(QuestionBank.prayer(it).endsWith("Amen.")); assertTrue(QuestionBank.liveIt(it).isNotBlank()) }
        assertTrue(QuestionBank.prayer(null).isNotBlank())
    }

    // ---------------------------------------------------------------- the passages

    @Test fun `every need has at least eight real passages and each one parses`() {
        Need.entries.forEach { need ->
            val raw = NeedPassages.raw(need)
            assertTrue("${need.key} has ${raw.size}", raw.size >= 8)
            assertEquals("${need.key}: every entry parses", raw.size, NeedPassages.passages(need).size)
            assertEquals("${need.key}: no repeats", raw.size, raw.map { it.substringBefore('|') }.distinct().size)
            NeedPassages.passages(need).forEach { assertTrue("${need.key} ${it.reference.display()} needs a why", it.why.length > 20) }
        }
        assertEquals(8, Need.entries.size)
    }

    @Test fun `a need passage changes from day to day and on another`() {
        val days = (0L until 8L).map { NeedPassages.pick(Need.ANXIOUS, 20000L + it).reference }
        assertEquals(8, days.distinct().size)
        assertTrue(NeedPassages.pick(Need.TIRED, 20000L, 0).reference != NeedPassages.pick(Need.TIRED, 20000L, 1).reference)
    }

    @Test fun `today's reading does not repeat within 30 days and honours less-of`() {
        val start = LocalDate.of(2026, 10, 11)
        val days = (0L until 60L).map { TodayPassage.forDate(start.plusDays(it))!!.reference }
        days.windowed(30).forEach { w -> assertEquals("a repeat inside 30 days", 30, w.distinct().size) }
        val reading = TodayPassage.forDate(start)!!
        assertEquals(2, reading.lines.size.coerceAtMost(2))
        assertTrue(reading.lines.first().isNotBlank())
        val less = DevotionalProfile(lessOf = setOf("Anxiety"))
        val withLess = (0L until 120L).mapNotNull { TodayPassage.forDate(start.plusDays(it), less)?.reference }
        assertTrue(withLess.none { com.craftflowtechnologies.meetingmind.core.devotional.PassageRotation.hasTheme(it, setOf("Anxiety")) })
        // "Another one" gives a different reading the same day.
        assertTrue(TodayPassage.forDate(start, variant = 1)!!.reference != reading.reference)
        assertNotNull(TodayPassage.forDate(start))
    }
}
