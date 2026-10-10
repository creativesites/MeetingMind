package com.craftflowtechnologies.meetingmind.ui

import com.craftflowtechnologies.meetingmind.ui.theme.AccentChoice
import com.craftflowtechnologies.meetingmind.ui.theme.Appearance
import com.craftflowtechnologies.meetingmind.ui.theme.GraphiteColors
import com.craftflowtechnologies.meetingmind.ui.theme.HomeSection
import com.craftflowtechnologies.meetingmind.ui.theme.HomeStyle
import com.craftflowtechnologies.meetingmind.ui.theme.PaperColors
import com.craftflowtechnologies.meetingmind.ui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    @Test fun roundTripsEveryChoice() {
        val a = Appearance(AccentChoice.ROSE, TextSize.LARGE, HomeStyle.FOCUS, setOf(HomeSection.STORIES, HomeSection.FOR_YOU))
        assertEquals(a, Appearance.decode(a.encode()))
        assertEquals(Appearance(), Appearance.decode(Appearance().encode()))
    }

    @Test fun unreadableValuesFallBackFieldByField() {
        assertEquals(Appearance(), Appearance.decode(null))
        assertEquals(Appearance(), Appearance.decode("garbage"))
        val partial = Appearance.decode("TEAL|HUGE|FOCUS|STORIES,NOPE")
        assertEquals(AccentChoice.TEAL, partial.accent)
        assertEquals(TextSize.DEFAULT, partial.textSize)
        assertEquals(HomeStyle.FOCUS, partial.homeStyle)
        assertEquals(setOf(HomeSection.STORIES), partial.hidden)
    }

    @Test fun existingInstallsKeepTodayAndNewInstallsGetEveryday() {
        // Nothing stored for the home: an install that finished onboarding earlier keeps Today.
        assertEquals(HomeStyle.TODAY, Appearance.decode(null, existingInstall = true).homeStyle)
        assertEquals(HomeStyle.TODAY, Appearance.decode("TEAL|DEFAULT||", existingInstall = true).homeStyle)
        assertEquals(HomeStyle.EVERYDAY, Appearance.decode(null, existingInstall = false).homeStyle)
        assertEquals(HomeStyle.EVERYDAY, Appearance.decode(null).homeStyle)
        // A stored choice always wins, in both cases.
        val stored = Appearance(homeStyle = HomeStyle.FOCUS).encode()
        assertEquals(HomeStyle.FOCUS, Appearance.decode(stored, existingInstall = true).homeStyle)
        assertEquals(HomeStyle.FOCUS, Appearance.decode(stored, existingInstall = false).homeStyle)
        assertEquals(HomeStyle.TODAY, Appearance.decode(Appearance(homeStyle = HomeStyle.TODAY).encode()).homeStyle)
    }

    @Test fun accentRecolorsBothPalettesAndKeepsTheRest() {
        AccentChoice.entries.forEach { c ->
            val dark = c.on(GraphiteColors)
            val light = c.on(PaperColors)
            assertEquals(c.dark, dark.accent)
            assertEquals(c.light, light.accent)
            assertEquals(GraphiteColors.ink, dark.ink)
            assertEquals(PaperColors.background, light.background)
        }
        assertNotEquals(AccentChoice.ROSE.on(GraphiteColors).accent, AccentChoice.INDIGO.on(GraphiteColors).accent)
    }

    @Test fun defaultIsEverydayWithEverythingShown() {
        val a = Appearance()
        assertEquals(HomeStyle.EVERYDAY, a.homeStyle)
        assertTrue(HomeSection.entries.all { a.shows(it) })
        assertFalse(a.copy(hidden = setOf(HomeSection.TIMELINE)).shows(HomeSection.TIMELINE))
    }
}

/** The accent is a token: every choice must read on both themes (docs/PLAN_PROFESSIONAL.md §8.7). */
class AccentTokenTest {
    private fun lum(c: androidx.compose.ui.graphics.Color): Double {
        fun ch(v: Float) = v.toDouble().let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double =
        (maxOf(lum(a), lum(b)) + 0.05) / (minOf(lum(a), lum(b)) + 0.05)

    @org.junit.Test fun theProfessionalChoicesAreOffered() {
        val names = com.craftflowtechnologies.meetingmind.ui.theme.AccentChoice.entries.map { it.name }
        listOf("INDIGO", "TEAL", "SLATE", "EMERALD", "CRIMSON").forEach { org.junit.Assert.assertTrue("$it missing", it in names) }
    }

    @org.junit.Test fun everyAccentReadsOnBothThemes() {
        com.craftflowtechnologies.meetingmind.ui.theme.AccentChoice.entries.forEach { a ->
            val dark = a.on(com.craftflowtechnologies.meetingmind.ui.theme.GraphiteColors)
            val light = a.on(com.craftflowtechnologies.meetingmind.ui.theme.PaperColors)
            org.junit.Assert.assertTrue("${a.name} on Graphite", contrast(dark.accent, dark.background) >= 3.0)
            org.junit.Assert.assertTrue("${a.name} on Paper", contrast(light.accent, light.background) >= 3.0)
            org.junit.Assert.assertNotEquals(dark.accent, light.accent)
        }
    }

    @org.junit.Test fun aSavedChoiceSurvivesItsEncoding() {
        val a = com.craftflowtechnologies.meetingmind.ui.theme.Appearance(accent = com.craftflowtechnologies.meetingmind.ui.theme.AccentChoice.CRIMSON)
        org.junit.Assert.assertEquals(a, com.craftflowtechnologies.meetingmind.ui.theme.Appearance.decode(a.encode()))
    }
}
