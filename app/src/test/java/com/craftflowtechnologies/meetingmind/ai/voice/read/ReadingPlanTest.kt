package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPlanTest {
    private val settings = ReadToMeSettings()

    private fun study() = extract(Blocks().apply {
        add(HEADING_2, "Observations", "observations", id = "h1")
        add(BULLET, "God loves", "observations", id = "o1")
        add(HEADING_2, "Reflection", "reflection_questions", id = "h2")
        add(BULLET, "What stands out?", "reflection_questions", id = "q1")
        add(BULLET, "What will you do?", "reflection_questions", id = "q2")
        add(HEADING_2, "Prayer", "prayer", id = "h3")
        add(PARAGRAPH, "Lord, help us", "prayer", edited = true, id = "p1")
    })

    @Test fun `without splitting the parts are the sections`() {
        val src = study()
        assertEquals(src.sections.map { it.id }, ReadingPlan.parts(src, splitAfterQuestions = false).map { it.id })
    }

    @Test fun `with splitting a part ends after each question that has more reading after it`() {
        val parts = ReadingPlan.parts(study(), splitAfterQuestions = true)
        assertEquals(listOf("h1", "h2", "h2#2", "h3"), parts.map { it.id })
        assertEquals(listOf("Observations", "Reflection", "Reflection", "Prayer"), parts.map { it.title })
        assertTrue(ReadingPlan.endsWithQuestion(parts[1]))   // heading + first question
        assertTrue(ReadingPlan.endsWithQuestion(parts[2]))   // second question, last in its section
        assertFalse(ReadingPlan.endsWithQuestion(parts[0]))
    }

    @Test fun `splitting loses and reorders nothing`() {
        val src = study()
        val before = src.sections.flatMap { it.units }.map { it.spoken }
        val after = ReadingPlan.parts(src, true).flatMap { it.units }.map { it.spoken }
        assertEquals(before, after)
    }

    @Test fun `part ids are stable between runs`() {
        assertEquals(ReadingPlan.parts(study(), true).map { it.id }, ReadingPlan.parts(study(), true).map { it.id })
    }

    @Test fun `the queue pauses itself only after a question and only when asked to`() {
        val parts = ReadingPlan.parts(study(), true)
        val until = settings.copy(pauseAfterQuestion = PauseAfterQuestion.UNTIL_RESUME)
        assertEquals(listOf(false, true, true, false), parts.map { ReadingPlan.pausesAfter(it, until) })
        assertTrue(parts.none { ReadingPlan.pausesAfter(it, settings.copy(pauseAfterQuestion = PauseAfterQuestion.SHORT)) })
    }

    @Test fun `chunks are exactly the spoken units, each with its silence`() {
        val part = ReadingPlan.parts(study(), false)[1]
        val chunks = ReadingPlan.chunks(part, settings.copy(pauseAfterQuestion = PauseAfterQuestion.MEDIUM))
        assertEquals(part.units.map { it.spoken }, chunks.map { it.text })
        assertEquals(700L, chunks[0].pauseAfterMs)                 // heading
        assertEquals(10_000L, chunks[1].pauseAfterMs)              // question
    }

    @Test fun `scripture gets a longer pause and a missing passage a shorter one`() {
        val src = extract(Blocks().apply { scripture("John 3:16-17"); scripture("Ps 23") }, lookupOf("John 3:16-17" to john316))
        val chunks = src.sections.flatMap { ReadingPlan.chunks(it, settings) }
        assertEquals(listOf(900L, 600L), chunks.map { it.pauseAfterMs })
    }

    @Test fun `reading can start from a section`() {
        val src = study()
        assertEquals(listOf("h2", "h3"), ReadingPlan.fromSection(src, "h2").sections.map { it.id })
        assertEquals(src, ReadingPlan.fromSection(src, null))
        assertEquals(src, ReadingPlan.fromSection(src, "nope"))
        assertEquals(src, ReadingPlan.fromSection(src, "h1"))
    }

    @Test fun `a part with no spoken text gives no chunks`() {
        assertTrue(ReadingPlan.chunks(SpeechSection("s", "t", emptyList()), settings).isEmpty())
        assertNull(null)
    }

    @Test fun `parts stay small with a short first part and keep every word in order`() {
        val long = (1..40).joinToString(" ") { "Sentence number $it is here." }
        val b = Blocks().apply {
            add(com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.HEADING_2, "One", id = "h1")
            add(com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH, long, id = "p1")
            add(com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH, long, id = "p2")
            add(com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH, "Short closing line.", id = "p3")
        }
        val src = extract(b)
        val parts = ReadingPlan.parts(src, false)
        fun len(p: SpeechSection) = p.units.sumOf { it.spoken.length }
        assertTrue("first part is short: ${len(parts[0])}", len(parts[0]) <= ReadingPlan.FIRST_PART_CHARS + 60)
        assertTrue(parts.drop(1).all { len(it) <= ReadingPlan.PART_CHARS + 1200 })
        assertTrue(parts.size >= 3)
        assertEquals(src.sections.flatMap { it.units }.joinToString(" ") { it.spoken }.split(Regex("\\s+")), parts.flatMap { it.units }.joinToString(" ") { it.spoken }.split(Regex("\\s+")))
        assertEquals(parts.map { it.id }, ReadingPlan.parts(src, false).map { it.id })
        assertEquals(parts.size, parts.map { it.id }.toSet().size)
        assertTrue("a heading never sits alone", parts.none { p -> p.units.all { it.role == SpeechRole.HEADING } })
    }
}
