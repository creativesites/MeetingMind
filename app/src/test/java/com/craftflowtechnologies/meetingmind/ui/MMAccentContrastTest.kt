package com.craftflowtechnologies.meetingmind.ui

import com.craftflowtechnologies.meetingmind.ui.theme.AccentChoice
import com.craftflowtechnologies.meetingmind.ui.theme.GraphiteColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMAccent
import com.craftflowtechnologies.meetingmind.ui.theme.MMColors
import com.craftflowtechnologies.meetingmind.ui.theme.PaperColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MMAccentContrastTest {
    @Test
    fun `every accent meets 4_5 on surface and background in both palettes`() {
        val problems = mutableListOf<String>()
        for (base in listOf<MMColors>(PaperColors, GraphiteColors)) {
            for (a in MMAccent.entries) {
                val p = a.on(base)
                for ((name, bg) in listOf("surface" to base.surface, "background" to base.background)) {
                    val r = MMAccent.contrast(p.accent, bg)
                    if (r < 4.5) problems += "${a.label} on $name (dark=${base.isDark}) = $r"
                }
                val on = MMAccent.contrast(p.onAccent, p.accent)
                if (on < 4.5) problems += "${a.label} onAccent (dark=${base.isDark}) = $on"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `indigo equals the current default accent`() {
        for (base in listOf(PaperColors, GraphiteColors)) {
            val p = MMAccent.Indigo.on(base)
            assertEquals(base.accent, p.accent)
            assertEquals(base.onAccent, p.onAccent)
            assertEquals(AccentChoice.INDIGO.on(base).accentWash, p.accentWash)
        }
    }
}
