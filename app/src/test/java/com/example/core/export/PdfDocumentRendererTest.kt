package com.example.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.core.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric has no native PDF writer, so these run the layout into ordinary canvases and check
 * the pagination. On a device the same pages go into a PdfDocument.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfDocumentRendererTest {

    private class CountingSink : PdfDocumentRenderer.PageSink {
        var begun = 0
        var ended = 0
        override fun begin(pageNumber: Int, width: Int, height: Int): Canvas {
            begun++
            assertEquals(begun, pageNumber)
            return Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        }
        override fun end() { ended++ }
    }

    private fun paragraphs(n: Int) = (1..n).map { i ->
        ExportBlock.Paragraph(RichText.plain("Paragraph $i. " + "Faith comes by hearing. ".repeat(12)))
    }

    @Test
    fun `a short note fits on one page`() {
        val sink = CountingSink()
        val pages = PdfDocumentRenderer.layOut(ExportDocument("Short", blocks = paragraphs(2)), sink)
        assertEquals(1, pages)
        assertEquals(1, sink.ended)
    }

    @Test
    fun `a long document flows onto more pages and closes every page it opens`() {
        val document = ExportDocument(
            title = "Long sermon",
            subtitle = "Sunday",
            blocks = listOf(ExportBlock.Heading(1, RichText.plain("Notes"))) + paragraphs(120) + listOf(
                ExportBlock.ListItem(ListKind.NUMBERED, RichText.plain("One")),
                ExportBlock.ListItem(ListKind.CHECKLIST, RichText.plain("Done"), checked = true),
                ExportBlock.Scripture("John 3:16", "For God so loved the world…", "NIV"),
                ExportBlock.Scripture("Romans 8:28", null, null),
                ExportBlock.Image("/does/not/exist.png", "Missing"),
                ExportBlock.Excerpt("Pastor · 01:05", "Grace is enough"),
                ExportBlock.Divider
            ),
            closingNotes = listOf("© Biblica"),
            serifBody = true
        )
        val sink = CountingSink()
        val pages = PdfDocumentRenderer.layOut(document, sink)
        assertTrue("expected several pages, got $pages", pages > 3)
        assertEquals(sink.begun, sink.ended)
    }
}
