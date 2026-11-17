package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.ui.text.AnnotatedString
import com.craftflowtechnologies.meetingmind.core.notes.YouTube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Crash audit (agents/CRASH_AUDIT.md): saved, pasted or AI content must never throw in Notes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesCrashGuardsTest {

    @Test fun `an absurd YouTube start time does not throw`() {
        assertEquals(Int.MAX_VALUE, YouTube.startSeconds("https://youtu.be/dQw4w9WgXcQ?t=99999999999s"))
        assertEquals(Int.MAX_VALUE, YouTube.startSeconds("https://youtu.be/dQw4w9WgXcQ?t=99999999999h1m"))
        assertEquals(125, YouTube.startSeconds("https://youtu.be/dQw4w9WgXcQ?t=2m5s"))
        assertEquals(0, YouTube.startSeconds("https://youtu.be/dQw4w9WgXcQ?t=0s"))
    }

    private val brokenLatex = listOf(
        "", "\\", "\\frac{", "\\frac{1}{", "}}}}", "{{{{", "\\begin{bmatrix", "\\begin{aligned} a &= \\\\", "\\ce{", "\\ce{SO4^{2-",
        "\\sqrt[", "x^", "x_", "\\left(", "\\mathbb{", "$$", "$", "\\(", "\\[", "a & b \\\\ c", "\\text{unterminated", "^^^___"
    )

    @Test fun `broken LaTeX parses to something drawable`() {
        brokenLatex.forEach { assertNotNull(it, LatexMathParser.parseOrText(it)) }
        brokenLatex.forEach { assertNotNull(it, InlineMath.render(it)) }
    }

    @Test fun `broken Mermaid parses to a diagram`() {
        listOf("", "flowchart", "graph LR\n A -->", "stateDiagram-v2\n [*] -->", "A[[[[", "-->|x|", "flowchart TD\n A{ --> B(").forEach {
            assertNotNull(it, MermaidDiagramParser.parseOrEmpty(it))
        }
    }

    /** The editor's visual transformation: Compose throws if an offset maps outside the text. */
    @Test fun `inline maths offset mapping always stays in range`() {
        val alphabet = "ab $\\()[]{}^_12 ​\n"
        val rnd = Random(17)
        repeat(2_000) {
            val s = buildString { repeat(rnd.nextInt(0, 40)) { append(alphabet[rnd.nextInt(alphabet.length)]) } } +
                listOf("", " [0]", " \$x^2\$", " \\ce{H2O}", " [Speaker 2]").random(rnd)
            val t = InlineMath.transform(AnnotatedString(s), maths = true, hideMarkers = true)
            for (i in 0..s.length) {
                val o = t.offsetMapping.originalToTransformed(i)
                assertTrue("$s @$i → $o", o in 0..t.text.length)
            }
            for (i in 0..t.text.length) {
                val o = t.offsetMapping.transformedToOriginal(i)
                assertTrue("$s ←$i → $o", o in 0..s.length)
            }
        }
    }
}
