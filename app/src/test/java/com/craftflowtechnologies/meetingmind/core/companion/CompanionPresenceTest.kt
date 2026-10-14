package com.craftflowtechnologies.meetingmind.core.companion

import org.junit.Assert.assertEquals
import org.junit.Test

/** The presence table of §3.3, row by row. */
class CompanionPresenceTest {
    private val now = LocalClock(10_000)

    private fun visible(page: CompanionPage, s: CompanionSettings) = CompanionPresence.visibleOn(page, s, now)

    @Test fun `presence table`() {
        // page -> (Around, Big moments, Off)
        val table = mapOf(
            CompanionPage.HOME to Triple(true, false, false),
            CompanionPage.RECORDING to Triple(true, true, false),
            CompanionPage.PROCESSING to Triple(true, true, false),
            CompanionPage.NOTE_READY to Triple(true, true, false),
            CompanionPage.FIRST_RECORDING to Triple(true, true, false),
            CompanionPage.WORRIED to Triple(true, true, false),
            CompanionPage.EMPTY to Triple(true, false, false),
            CompanionPage.MILESTONE to Triple(true, true, false),
            CompanionPage.READ_ALOUD to Triple(true, false, false),
            CompanionPage.ONBOARDING to Triple(true, true, true),
            CompanionPage.QUICK_SHEET to Triple(true, true, true),
            CompanionPage.COMPANION_SETTINGS to Triple(true, true, true)
        )
        assertEquals("every page is in the table", CompanionPage.entries.toSet(), table.keys)
        for ((page, row) in table) {
            assertEquals("$page around", row.first, visible(page, CompanionSettings(presence = Presence.AROUND)))
            assertEquals("$page moments", row.second, visible(page, CompanionSettings(presence = Presence.MOMENTS)))
            assertEquals("$page off", row.third, visible(page, CompanionSettings(presence = Presence.OFF)))
        }
    }

    @Test fun `no companion hides it everywhere except where the user chooses`() {
        for (page in CompanionPage.entries) {
            assertEquals("$page", page.isChoosing, visible(page, CompanionSettings(form = null)))
        }
    }

    @Test fun `hidden until a time hides it until then`() {
        val hidden = CompanionSettings(hiddenUntilMs = 20_000)
        assertEquals(false, visible(CompanionPage.HOME, hidden))
        assertEquals(true, visible(CompanionPage.QUICK_SHEET, hidden))
        assertEquals(true, CompanionPresence.visibleOn(CompanionPage.HOME, hidden, LocalClock(20_000)))
    }
}
