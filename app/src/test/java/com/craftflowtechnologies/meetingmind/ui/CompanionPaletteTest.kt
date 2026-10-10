package com.craftflowtechnologies.meetingmind.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.craftflowtechnologies.meetingmind.ui.theme.GraphiteColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.PaperColors
import com.craftflowtechnologies.meetingmind.ui.theme.companionInk
import com.craftflowtechnologies.meetingmind.ui.theme.companionPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CompanionPaletteTest {

    @Test fun `eyes read on the body for every accent and theme`() {
        val problems = mutableListOf<String>()
        for (dark in listOf(false, true)) for (a in MMAccent.entries) {
            val p = companionPalette(a, dark)
            // §2.2: eye vs bodyTop. The eyes sit about a quarter of the way along the body gradient,
            // so check that colour too.
            val atEyes = Color(
                red = p.bodyTop.red + (p.bodyBottom.red - p.bodyTop.red) * 0.25f,
                green = p.bodyTop.green + (p.bodyBottom.green - p.bodyTop.green) * 0.25f,
                blue = p.bodyTop.blue + (p.bodyBottom.blue - p.bodyTop.blue) * 0.25f
            )
            for ((name, c) in listOf("bodyTop" to p.bodyTop, "body at the eyes" to atEyes)) {
                val r = MMAccent.contrast(p.eye, c)
                if (r < 4.5) problems += "${a.label} dark=$dark eye on $name = $r"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun `eye is the fixed companion ink in both themes`() {
        assertEquals(companionInk, companionPalette(MMAccent.Rose, false).eye)
        assertEquals(companionInk, companionPalette(MMAccent.Rose, true).eye)
    }

    @Test fun `matches the reference formulas`() {
        // Indigo light: body1 = mix(#5B5BD6, #fff, .5) = #ADADEB; deep = mix(#5B5BD6, #18181B, .25) = #4A4AA7.
        val light = companionPalette(MMAccent.Indigo, false)
        assertClose(Color(0xFFADADEB), light.bodyTop)
        assertClose(Color(0xFF4A4AA7), light.deep)
        // Indigo dark: body2 = mix(#9B9CF6, #0C111B, .1) = #8D8EE0.
        val dark = companionPalette(MMAccent.Indigo, true)
        assertClose(Color(0xFF8D8EE0), dark.bodyBottom)
    }

    @Test fun `palette follows the theme colours`() {
        for (base in listOf(PaperColors, GraphiteColors)) for (a in MMAccent.entries) {
            assertEquals(companionPalette(a, base.isDark), companionPalette(a.on(base)))
        }
    }

    @Test fun `the page stays light paper in both themes`() {
        for (a in MMAccent.entries) {
            val p = companionPalette(a, true)
            assertTrue("${a.label}", MMAccent.contrast(p.paper, companionInk) >= 7.0)
        }
    }

    private fun assertClose(expected: Color, actual: Color) {
        val e = expected.toArgb(); val x = actual.toArgb()
        for (shift in listOf(16, 8, 0)) {
            val d = abs(((e shr shift) and 0xFF) - ((x shr shift) and 0xFF))
            assertTrue("expected ${Integer.toHexString(e)} got ${Integer.toHexString(x)}", d <= 1)
        }
    }
}
