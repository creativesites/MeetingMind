package com.craftflowtechnologies.meetingmind.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLinksTest {
    @Test
    fun `withAppLink appends Play Store URL once`() {
        val shared = AppLinks.withAppLink("Morning reflection from Psalm 23.")
        assertTrue(shared.contains(AppLinks.DOWNLOAD_URL))
        assertTrue(AppLinks.containsDownloadLink(shared))

        // Idempotent: calling twice never duplicates the URL
        val doubleWrapped = AppLinks.withAppLink(shared)
        assertEquals(shared, doubleWrapped)
    }

    @Test
    fun `withAppLink handles empty body cleanly`() {
        val shared = AppLinks.withAppLink("   ")
        assertTrue(AppLinks.containsDownloadLink(shared))
        assertTrue(shared.startsWith("Download MeetingMind:"))
    }

    @Test
    fun `shortCaptionWithLink includes download link and does not duplicate`() {
        val caption = AppLinks.shortCaptionWithLink("Healed & Whole")
        assertTrue(caption.contains("Healed & Whole"))
        assertTrue(AppLinks.containsDownloadLink(caption))
        assertEquals(caption, AppLinks.shortCaptionWithLink(caption))
        assertFalse(AppLinks.containsDownloadLink("Plain verse without URL"))
    }
}
