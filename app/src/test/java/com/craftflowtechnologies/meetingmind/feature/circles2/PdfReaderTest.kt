package com.craftflowtechnologies.meetingmind.feature.circles2

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReaderTest {

    @Test
    fun pageIndicatorReadsPageOfTotal() {
        assertEquals("Page 3 of 12", pdfPageIndicator(3, 12))
        assertEquals("Page 1 of 1", pdfPageIndicator(1, 1))
    }

    @Test
    fun pageIndicatorClampsCurrentPageIntoRange() {
        assertEquals("Page 1 of 4", pdfPageIndicator(0, 4))
        assertEquals("Page 4 of 4", pdfPageIndicator(9, 4))
    }

    @Test
    fun pageIndicatorIsEmptyWithNoPages() {
        assertEquals("", pdfPageIndicator(1, 0))
    }

    @Test
    fun letterPageKeepsAspectRatioAtScreenWidth() {
        // US Letter, 612 x 792 pt, drawn 1080 px wide: 1080 * 792 / 612 = 1397.6, so 1397 px tall.
        assertEquals(1397, pdfRenderHeightPx(612, 792, 1080))
    }

    @Test
    fun landscapePageIsShorterThanItIsWide() {
        assertEquals(300, pdfRenderHeightPx(800, 600, 400))
    }

    @Test
    fun unknownPageSizeFallsBackToSquare() {
        assertEquals(1080, pdfRenderHeightPx(0, 792, 1080))
    }

    @Test
    fun zeroTargetWidthNeverGivesZeroHeight() {
        assertEquals(1, pdfRenderHeightPx(612, 792, 0))
    }

    @Test
    fun normalPageRendersAtFullWidth() {
        assertEquals(Pair(1080, 1397), pdfRenderSizePx(612, 792, 1080))
    }

    @Test
    fun veryTallPageIsCappedAndKeepsItsAspectRatio() {
        // 612 x 14400 pt at 1080 px wide would be 25,412 px tall. It is capped at 6000 px and drawn narrower.
        val (width, height) = pdfRenderSizePx(612, 14400, 1080)
        assertEquals(PDF_MAX_RENDER_HEIGHT_PX, height)
        assertEquals(255, width)
    }
}
