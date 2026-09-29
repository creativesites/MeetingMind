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

    @Test fun defaultIsTodayWithEverythingShown() {
        val a = Appearance()
        assertEquals(HomeStyle.TODAY, a.homeStyle)
        assertTrue(HomeSection.entries.all { a.shows(it) })
        assertFalse(a.copy(hidden = setOf(HomeSection.TIMELINE)).shows(HomeSection.TIMELINE))
    }
}
