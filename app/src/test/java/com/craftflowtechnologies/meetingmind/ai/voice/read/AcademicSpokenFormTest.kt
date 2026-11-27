package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.AcademicSpokenForm
import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.ScriptStore
import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.SmartShaper
import com.craftflowtechnologies.meetingmind.ai.voice.read.smart.SpokenForm
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType.PARAGRAPH
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicSpokenFormTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `converts core LaTeX math constructs into natural spoken English`() {
        assertEquals("a over b", AcademicSpokenForm.apply("\\frac{a}{b}"))
        assertEquals("the square root of x", AcademicSpokenForm.apply("\\sqrt{x}"))
        assertEquals("x squared", AcademicSpokenForm.apply("x^2"))
        assertEquals("x cubed", AcademicSpokenForm.apply("x^3"))
        assertEquals("x to the power of n", AcademicSpokenForm.apply("x^{n}"))
        assertEquals("x sub i", AcademicSpokenForm.apply("x_{i}"))
        assertEquals("the sum from i equals 1 to n of x sub i", AcademicSpokenForm.apply("\\sum_{i=1}^{n} x_{i}"))
        assertEquals("the integral from a to b of f(x) dx", AcademicSpokenForm.apply("\\int_{a}^{b} f(x) dx"))
        assertEquals("partial f over partial x", AcademicSpokenForm.apply("\\frac{\\partial f}{\\partial x}"))
    }

    @Test
    fun `converts Greek letters, relations, arrows and chemistry notation`() {
        assertEquals("alpha plus or minus beta", AcademicSpokenForm.apply("\\alpha \\pm \\beta"))
        assertEquals("omega is less than or equal to 1", AcademicSpokenForm.apply("\\omega \\le 1"))
        assertEquals("x is greater than or equal to 0", AcademicSpokenForm.apply("x \\ge 0"))
        assertEquals("a is not equal to b", AcademicSpokenForm.apply("a \\ne b"))
        assertEquals("pi is approximately 3.14", AcademicSpokenForm.apply("\\pi \\approx 3.14"))
        assertEquals("x approaches 0", AcademicSpokenForm.apply("x \\to 0"))
        assertEquals("A yields B", AcademicSpokenForm.apply("A \\rightarrow B"))
        assertEquals("H 2 O", AcademicSpokenForm.apply("\\ce{H2O}"))
    }

    @Test
    fun `converts accounting shorthand and ratios without corrupting honorifics`() {
        assertEquals("Debit Cash 500, Credit Revenue 500", AcademicSpokenForm.apply("Dr Cash 500, Cr Revenue 500"))
        val ratios = AcademicSpokenForm.apply("Under IFRS and GAAP, check ROE, COGS, and P/E ratio.")
        assertTrue(ratios.contains("I F R S"))
        assertTrue(ratios.contains("G A A P"))
        assertTrue(ratios.contains("return on equity"))
        assertTrue(ratios.contains("cost of goods sold"))
        assertTrue(ratios.contains("price to earnings ratio"))
        // "Dr." with a period is an honorific, not a debit entry
        assertEquals("Dr. Luke wrote the Gospel.", AcademicSpokenForm.apply("Dr. Luke wrote the Gospel."))
    }

    @Test
    fun `converts Big-O complexity notation`() {
        assertEquals("Merge sort runs in big O of n log n time.", AcademicSpokenForm.apply("Merge sort runs in O(n log n) time."))
        assertEquals("BFS runs in big O of V plus E time.", AcademicSpokenForm.apply("BFS runs in O(V + E) time."))
        assertEquals("Bubble sort is big O of n squared.", AcademicSpokenForm.apply("Bubble sort is O(n^2)."))
    }

    @Test
    fun `narrates fenced code blocks and Markdown pipe tables without raw pipes or backticks`() {
        val codeAndTable = """
            ```python
            def clamp(x):
                return x <= 10
            ```
            | Account | Dr | Cr |
            |---|---|---|
            | Cash | 1200 | — |
            | Service Revenue | — | 1200 |
        """.trimIndent()

        val spoken = AcademicSpokenForm.apply(codeAndTable)
        assertFalse("Should not contain backticks: $spoken", spoken.contains('`'))
        assertFalse("Should not contain raw pipes: $spoken", spoken.contains('|'))
        assertFalse("Should not contain table dashes: $spoken", spoken.contains("---"))
        assertTrue(spoken.contains("Code example in Python."))
        assertTrue(spoken.contains("is less than or equal to"))
        assertTrue(spoken.contains("Table with columns Account, Debit, Credit."))
        assertTrue(spoken.contains("Row 1: Account Cash, Debit 1200."))
        assertTrue(spoken.contains("Row 2: Account Service Revenue, Credit 1200."))
    }

    @Test
    fun `builds natural audio cram script skipping dismissed concepts`() {
        val concepts = listOf(
            LearningConcept(
                id = "c1",
                sessionId = "s1",
                name = "Quadratic Formula",
                definition = "Solves ax^2 + bx + c = 0 via \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
                emphasis = "Guaranteed on the midterm exam",
                relationships = listOf("Discriminant"),
                createdAt = 1L,
                updatedAt = 1L
            ),
            LearningConcept(
                id = "c2",
                sessionId = "s1",
                name = "Dismissed Concept",
                definition = "Should not be spoken",
                isDismissed = true,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val script = AcademicSpokenForm.buildAudioCramScript(
            courseTitle = "MATH 101",
            sessionTitle = "Polynomial Roots",
            concepts = concepts,
            examHints = listOf("Memorize O(n log n) vs O(n^2) bounds")
        )
        assertTrue(script.contains("Audio cram for MATH 101: Polynomial Roots."))
        assertTrue(script.contains("Concept 1, Quadratic Formula"))
        assertTrue(script.contains("plus or minus the square root of"))
        assertTrue(script.contains("Lecturer emphasis: Guaranteed on the midterm exam."))
        assertTrue(script.contains("Exam alert 1: Memorize big O of n log n versus big O of n squared bounds."))
        assertFalse(script.contains("Dismissed Concept"))
    }

    @Test
    fun `wires cleanly into SpokenForm and SmartShaper without breaking standard prose`() = runBlocking {
        assertEquals("Grow 10 percent for example, with prayer and so on.", SpokenForm.apply("Grow 10% e.g., with prayer etc."))
        assertEquals("The runtime is big O of n log n, for example, merge sort.", SpokenForm.apply("The runtime is O(n log n), e.g., merge sort."))

        val blocks = Blocks().apply {
            add(PARAGRAPH, "In dynamic programming, state transitions take O(V + E) time and \\sum_{i=1}^{n} x_{i} space.", id = "p1")
        }
        val section = extract(blocks).sections.single()
        val shaped = SmartShaper(model = null, store = ScriptStore(tmp.newFile())).shape(section)
        val unitText = shaped.units.single().text
        assertTrue(unitText.contains("big O of V plus E"))
        assertTrue(unitText.contains("the sum from i equals 1 to n of x sub i"))
    }
}
