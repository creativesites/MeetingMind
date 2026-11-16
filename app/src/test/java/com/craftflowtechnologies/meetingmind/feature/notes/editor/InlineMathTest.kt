package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.ui.text.AnnotatedString
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Maths and chemistry inside note text (design/notes-blocks-and-math-plan.md, B). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InlineMathTest {

    private fun latex(text: String) = InlineMath.find(text).map { it.latex to it.display }

    @Test fun `finds every delimiter`() {
        assertEquals(listOf("(2x+1)(x+2)" to false), latex("So \$(2x+1)(x+2)\$ is the answer."))
        assertEquals(listOf("x^2" to false), latex("Area \\(x^2\\) here"))
        assertEquals(listOf("a+b" to true), latex("Display \$\$a+b\$\$ now"))
        assertEquals(listOf("E=mc^2" to true), latex("\\[E=mc^2\\]"))
        assertEquals(listOf("\\ce{H2SO4}" to false), latex("Sulfuric acid, \\ce{H2SO4}, is strong."))
        assertEquals(listOf("\\ce{Na+}" to false, "x" to false), latex("\$\\ce{Na+}\$ and \$x\$"))
    }

    @Test fun `money and escaped dollars stay text`() {
        assertTrue(InlineMath.find("It costs \$5 and \$10 today.").isEmpty())
        assertTrue(InlineMath.find("Pay \$ 5 or \\\$3.").isEmpty())
        assertTrue(InlineMath.find("Between \$5 and \$").isEmpty())
        assertTrue(InlineMath.find("No maths here.").isEmpty())
    }

    @Test fun `renders operators, scripts and Greek`() {
        val r = InlineMath.render("2x \\cdot x = 2x^2")
        assertFalse(r.text, r.text.contains("\\") || r.text.contains("^"))
        assertTrue(r.text, r.text.contains("·") && r.text.contains("="))
        assertTrue(r.spanStyles.any { it.item.baselineShift != null })
        assertEquals("θ\u2009≠\u2009π", InlineMath.render("\\theta \\neq \\pi").text)
    }

    @Test fun `renders chemistry`() {
        val acid = InlineMath.render("\\ce{H2SO4}")
        assertEquals("H2SO4", acid.text)
        assertEquals(2, acid.spanStyles.count { it.item.baselineShift != null }) // the 2 and the 4 are lowered
        val reaction = InlineMath.render("\\ce{2H2 + O2 -> 2H2O}").text
        assertTrue(reaction, reaction.contains("→") && reaction.contains("+"))
        assertTrue(InlineMath.render("\\ce{SO4^{2-}}").text.contains("2−"))
    }

    @Test fun `fractions and roots`() {
        assertEquals("1\u20442", InlineMath.render("\\frac{1}{2}").text)
        assertEquals("(a+b)/(2c)", InlineMath.render("\\frac{a+b}{2c}").text.replace("\u2009", ""))
        assertEquals("√x", InlineMath.render("\\sqrt{x}").text)
    }

    @Test fun `a formula that will not parse reads as plain text`() {
        val bad = InlineMath.render("\\frac{1}{2").text
        assertFalse(bad, bad.contains("\\") || bad.contains("{"))
        assertEquals("12", bad)
    }

    @Test fun `transform keeps offsets valid and hides orphan markers`() {
        val source = "\u200BFactor \$2x^2 + 5x + 2\$ fully [0]."
        val t = InlineMath.transform(AnnotatedString(source), maths = true, hideMarkers = true)
        assertFalse(t.text.text, t.text.text.contains("\$") || t.text.text.contains("[0]"))
        assertTrue(t.text.text.endsWith("fully."))
        for (i in 0..source.length) assertTrue(t.offsetMapping.originalToTransformed(i) in 0..t.text.length)
        for (i in 0..t.text.length) assertTrue(t.offsetMapping.transformedToOriginal(i) in 0..source.length)
        // The text before the formula maps one to one.
        assertEquals(3, t.offsetMapping.originalToTransformed(3))
        assertEquals(source.length, t.offsetMapping.originalToTransformed(source.length).let { t.offsetMapping.transformedToOriginal(it) })
    }

    @Test fun `spoken form reads the maths`() {
        val spoken = InlineMath.spoken("Area is \$x^2\$.")
        assertFalse(spoken, spoken.contains("\$") || spoken.contains("^"))
    }

    @Test fun `display maths on its own lines becomes an equation block`() {
        val blocks = MarkdownImport.parse("Solve:\n\n\$\$\nx = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}\n\$\$\n\nThen check.", "n", BlockSource.AI)
        assertEquals(listOf(NoteBlockType.PARAGRAPH, NoteBlockType.MATH, NoteBlockType.PARAGRAPH), blocks.map { it.type })
        assertEquals("x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}", blocks[1].content.text)
        val oneLine = MarkdownImport.parse("\\[ E = mc^2 \\]", "n")
        assertEquals(NoteBlockType.MATH, oneLine.single().type)
        assertEquals("E = mc^2", oneLine.single().content.text)
        // Never closed: the dollars are text.
        assertEquals(NoteBlockType.PARAGRAPH, MarkdownImport.parse("\$\$ open\n\nmore", "n").first().type)
    }
}
