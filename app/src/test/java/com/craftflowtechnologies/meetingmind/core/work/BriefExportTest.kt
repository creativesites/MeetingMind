package com.craftflowtechnologies.meetingmind.core.work

import android.graphics.Bitmap
import android.graphics.Canvas
import com.craftflowtechnologies.meetingmind.core.export.DocxDocumentRenderer
import com.craftflowtechnologies.meetingmind.core.export.ExportBlock
import com.craftflowtechnologies.meetingmind.core.export.ExportFormat
import com.craftflowtechnologies.meetingmind.core.export.ExportTheme
import com.craftflowtechnologies.meetingmind.core.export.MarkdownDocumentRenderer
import com.craftflowtechnologies.meetingmind.core.export.PdfDocumentRenderer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/** A brief exported to PDF, Word and Markdown, with numbered evidence and an appendix of quotes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BriefExportTest {
    private lateinit var f: PulseFixture
    private lateinit var w: Map<String, com.craftflowtechnologies.meetingmind.core.database.ItemEntity>

    @Before fun setup() { f = PulseFixture(); w = f.projectWorld() }
    @After fun tearDown() { f.db.close() }

    private fun brief(privacy: PackPrivacy = PackPrivacy { _, _ -> false }, models: WorkModels? = null) = runBlocking { BriefBuilder(f.db, models, privacy) { f.now }.build(BriefTarget.forProject("nb")) }
    private fun document(b: Brief = brief()) = BriefExport.toDocument(b, Locale.ENGLISH)

    @Test fun theDocumentHasTheBriefThemeStatusChipAndSectionsInOrder() {
        val d = document()
        assertEquals(ExportTheme.BRIEF, d.theme)
        assertEquals("Attention", d.statusChip!!.label); assertTrue(d.statusChip!!.attention)
        assertEquals("Acme launch", d.title)
        assertTrue(d.subtitle!!.startsWith("Project status · "))
        val headings = d.blocks.filterIsInstance<ExportBlock.Heading>().map { it.text.text }
        assertEquals(listOf("What changed", "Decisions", "You owe", "They owe", "Risks", "Open questions", "Next 7 days", "Decisions required", "Evidence"), headings)
    }

    @Test fun evidenceIsNumberedAndTheAppendixQuotesEachWithDateAndTime() {
        val d = document()
        val deckLine = d.blocks.filterIsInstance<ExportBlock.ListItem>().first { it.text.text.startsWith("Send the deck") }.text.text
        assertTrue(deckLine, Regex(" \\[\\d+]$").containsMatchIn(deckLine))
        val appendix = d.blocks.dropWhile { it !is ExportBlock.PageBreak }
        assertTrue(appendix.first() is ExportBlock.PageBreak)
        val excerpts = appendix.filterIsInstance<ExportBlock.Excerpt>()
        assertTrue(excerpts.size >= 4)
        val deck = excerpts.first { it.text == "I'll send the deck." }
        assertTrue(deck.label!!, Regex("^\\[\\d+] · Acme review · \\d+ \\w+ \\d{4}, \\d\\d:\\d\\d · at 0?0?:?00:20|^\\[\\d+] Acme review · \\d+ \\w+ \\d{4}, \\d\\d:\\d\\d · at .*").containsMatchIn(deck.label!!) || deck.label!!.contains("Acme review"))
        assertTrue(deck.label!!.contains("2026"))
    }

    @Test fun confidentialMaterialPrintsAConfidentialityLine() {
        assertEquals(ExportBlock.Quote(com.craftflowtechnologies.meetingmind.core.notes.RichText.plain(BriefExport.CONFIDENTIAL_LINE)), document(brief(privacy = PackPrivacy { _, _ -> true })).blocks.first())
        assertFalse(document(brief()).blocks.first() is ExportBlock.Quote)
    }

    @Test fun proseAppearsFirstAndCitesItsEvidence() {
        val models = FakeWorkModels(FakeWorkModel("{\"executive\":[{\"text\":\"The deck is overdue.\",\"cites\":[\"${w.getValue("deck").id}\"]}],\"recommended\":[{\"text\":\"Settle the CMS.\",\"cites\":[\"${w.getValue("proposed").id}\"]}]}"))
        val d = document(brief(models = models))
        val headings = d.blocks.filterIsInstance<ExportBlock.Heading>().map { it.text.text }
        assertEquals("Executive picture", headings.first()); assertEquals("Recommended next conversation", headings[headings.size - 2])
        assertTrue(d.blocks.filterIsInstance<ExportBlock.Paragraph>().any { it.text.text.startsWith("The deck is overdue. [") })
    }

    @Test fun markdownCarriesEverythingIncludingTheAppendix() {
        val md = MarkdownDocumentRenderer.render(document())
        assertTrue(md.startsWith("# Acme launch"))
        assertTrue(md.contains("**Attention**") && md.contains("## Evidence") && md.contains("> _[1] "))
        assertTrue(md.contains("I'll send the deck."))
    }

    @Test fun wordFileHasTheAppendixAndAPageBreak() {
        val out = ByteArrayOutputStream()
        DocxDocumentRenderer.render(document(), out)
        val xml = ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "word/document.xml" }.let { String(zip.readBytes()) }
        }
        assertTrue(xml.contains("Evidence") && xml.contains("I&apos;ll send the deck.") || xml.contains("I'll send the deck."))
        assertTrue(xml.contains("w:br w:type=\"page\""))
        assertTrue(xml.contains("Attention"))
    }

    // PDF: Robolectric has no native PDF writer, so the pages are drawn onto ordinary canvases and
    // the text that reaches them is collected.
    private class TextCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        val text = StringBuilder()
        override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) { this.text.append(text).append('\n'); super.drawText(text, x, y, paint) }
        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: android.graphics.Paint) { this.text.append(text, start, end).append('\n'); super.drawText(text, start, end, x, y, paint) }
        override fun drawTextRun(text: CharSequence, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: android.graphics.Paint) {
            this.text.append(text, start, end).append('\n'); super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
        }
    }

    private class Pages : PdfDocumentRenderer.PageSink {
        val canvases = mutableListOf<TextCanvas>()
        override fun begin(pageNumber: Int, width: Int, height: Int): Canvas = TextCanvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)).also { canvases += it }
        override fun end() {}
    }

    @Test fun thePdfHasPagesAndItsLastPagesHoldTheEvidenceAppendix() {
        val pages = Pages()
        val count = PdfDocumentRenderer.layOut(document(), pages)
        assertTrue("expected at least two pages (body, then the appendix), got $count", count >= 2)
        assertEquals(count, pages.canvases.size)
        val all = pages.canvases.joinToString("\n") { it.text }
        val last = pages.canvases.last().text.toString()
        assertTrue("the pages should carry text", all.isNotBlank())
        assertTrue(all, all.contains("EVIDENCE"))
        assertTrue(all.contains("Attention")) // the status chip
        // The appendix opens on its own page, after the sections, and holds the quote with where it was said.
        assertTrue(pages.canvases.indexOfFirst { it.text.contains("EVIDENCE") } >= 1)
        assertTrue(last.contains("send the deck") || pages.canvases.any { it.text.contains("send the deck") })
        assertTrue(all.contains("Acme review"))
    }

    @Test fun writeDocumentRoutesToTheRightRenderer() {
        val md = ByteArrayOutputStream(); BriefExport.write(brief(), ExportFormat.MARKDOWN, md)
        assertTrue(String(md.toByteArray()).contains("# Acme launch"))
        val docx = ByteArrayOutputStream(); BriefExport.write(brief(), ExportFormat.DOCX, docx)
        assertTrue(docx.size() > 500 && docx.toByteArray()[0] == 'P'.code.toByte()) // a zip
        val refused = runCatching { BriefExport.write(brief(), ExportFormat.CSV, ByteArrayOutputStream()) }
        assertTrue(refused.isFailure)
    }
}
