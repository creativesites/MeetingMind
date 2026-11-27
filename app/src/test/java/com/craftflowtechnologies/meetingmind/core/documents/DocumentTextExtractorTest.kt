package com.craftflowtechnologies.meetingmind.core.documents

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Fakes for the page source: the text layer per page, and what recognition returns per page. */
private class FakeSource(
    private val layers: List<String>,
    private val recognised: Map<Int, String> = emptyMap(),
    private val ocrThrows: Boolean = false,
    private val ocrCancels: Boolean = false
) : DocumentPageSource {
    override val pageCount: Int = layers.size
    var closed = false
    val recognisedIndexes = mutableListOf<Int>()

    override fun textLayer(index: Int): LayerText = LayerText(layers[index], emptyList())

    override suspend fun recognise(index: Int): OcrText? {
        recognisedIndexes += index
        if (ocrCancels) throw CancellationException("cancelled")
        if (ocrThrows) throw IllegalStateException("Play services unavailable")
        return recognised[index]?.let { OcrText(it, emptyList(), null) }
    }

    override fun close() {
        closed = true
    }
}

private fun openerReturning(source: DocumentPageSource) = DocumentSourceOpener { _, _ -> source }

private val longText = "This page has plenty of selectable words in its text layer for reading."

private fun extract(
    source: DocumentPageSource,
    bytes: Long = 1024L,
    onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
): ExtractionOutcome = runBlocking {
    DocumentTextExtractor(openerReturning(source)).extract(File("doc.pdf"), DocumentKind.PDF, bytes, onProgress)
}

class DocumentTextExtractorTest {

    @Test
    fun textLayerPageIsUsedWithoutRecognition() {
        val source = FakeSource(listOf(longText))
        val outcome = extract(source)
        assertTrue(outcome is ExtractionOutcome.Read)
        val read = outcome as ExtractionOutcome.Read
        assertEquals(PageTextSource.TEXT_LAYER, read.pages.single().source)
        assertEquals(1, read.pages.single().page)
        assertTrue(source.recognisedIndexes.isEmpty())
        assertTrue(read.hasText)
        assertFalse(read.truncated)
    }

    @Test
    fun shortTextLayerFallsBackToRecognition() {
        val source = FakeSource(listOf("Hi", longText), recognised = mapOf(0 to "Scanned signature page text"))
        val read = extract(source) as ExtractionOutcome.Read
        assertEquals(listOf(0), source.recognisedIndexes)
        assertEquals(PageTextSource.OCR, read.pages[0].source)
        assertEquals("Scanned signature page text", read.pages[0].text)
        assertEquals(PageTextSource.TEXT_LAYER, read.pages[1].source)
    }

    @Test
    fun recognitionThatReturnsNothingKeepsTheLayerText() {
        val source = FakeSource(listOf("Short"), recognised = emptyMap())
        val read = extract(source) as ExtractionOutcome.Read
        assertEquals("Short", read.pages.single().text)
        assertEquals(PageTextSource.TEXT_LAYER, read.pages.single().source)
    }

    @Test
    fun noWordsAnywhereIsNoText() {
        val source = FakeSource(listOf("", "  "), recognised = mapOf(0 to "   ", 1 to ""))
        val read = extract(source) as ExtractionOutcome.Read
        assertFalse(read.hasText)
        assertEquals(2, read.pages.size)
        assertEquals(2, read.pageCount)
    }

    @Test
    fun recognitionUnavailableOnAScanIsFailedNotNoText() {
        val source = FakeSource(listOf(""), ocrThrows = true)
        val outcome = extract(source)
        assertEquals(ExtractionOutcome.Failed(FailureReason.OCR_UNAVAILABLE), outcome)
    }

    @Test
    fun recognitionFailureOnOnePageDoesNotLoseTheOthers() {
        val source = FakeSource(listOf(longText, "", longText), recognised = emptyMap())
        val read = extract(source) as ExtractionOutcome.Read
        assertEquals(3, read.pages.size)
        assertTrue(read.hasText)
        assertEquals(longText, read.pages[2].text)
    }

    @Test
    fun lockedOpenerGivesLocked() {
        val opener = DocumentSourceOpener { _, _ -> throw DocumentLockedException() }
        val outcome = runBlocking { DocumentTextExtractor(opener).extract(File("x.pdf"), DocumentKind.PDF, 10L) }
        assertEquals(ExtractionOutcome.Locked, outcome)
    }

    @Test
    fun corruptOpenerGivesFailedCorrupt() {
        val opener = DocumentSourceOpener { _, _ -> throw DocumentCorruptException() }
        val outcome = runBlocking { DocumentTextExtractor(opener).extract(File("x.pdf"), DocumentKind.PDF, 10L) }
        assertEquals(ExtractionOutcome.Failed(FailureReason.CORRUPT), outcome)
    }

    @Test
    fun documentWithNoPagesIsCorrupt() {
        val outcome = extract(FakeSource(emptyList()))
        assertEquals(ExtractionOutcome.Failed(FailureReason.CORRUPT), outcome)
    }

    @Test
    fun morePagesThanTheLimitAreTruncatedAndFlagged() {
        val pages = List(DocumentLimits.MAX_PAGES + 112) { longText }
        val read = extract(FakeSource(pages)) as ExtractionOutcome.Read
        assertEquals(DocumentLimits.MAX_PAGES, read.pages.size)
        assertEquals(DocumentLimits.MAX_PAGES + 112, read.pageCount)
        assertTrue(read.truncated)
    }

    @Test
    fun exactlyTheLimitIsNotTruncated() {
        val pages = List(DocumentLimits.MAX_PAGES) { longText }
        val read = extract(FakeSource(pages)) as ExtractionOutcome.Read
        assertFalse(read.truncated)
    }

    @Test
    fun progressReportsTotalThenEachPage() {
        val progress = mutableListOf<Pair<Int, Int>>()
        extract(FakeSource(listOf(longText, longText, longText))) { total, done -> progress += total to done }
        assertEquals(listOf(3 to 0, 3 to 1, 3 to 2, 3 to 3), progress)
    }

    @Test
    fun sourceIsClosedOnSuccessAndOnFailure() {
        val ok = FakeSource(listOf(longText))
        extract(ok)
        assertTrue(ok.closed)

        val broken = FakeSource(listOf(""), ocrThrows = true)
        extract(broken)
        assertTrue(broken.closed)
    }

    @Test
    fun cancellationIsNotSwallowed() {
        val source = FakeSource(listOf(""), ocrCancels = true)
        try {
            extract(source)
            fail("Expected cancellation to propagate")
        } catch (expected: CancellationException) {
            // expected
        }
        assertTrue("source must still be closed", source.closed)
    }

    @Test
    fun emptyDocumentFromOpenerIsCorruptNotCrash() {
        val opener = DocumentSourceOpener { _, _ -> throw IllegalStateException("boom") }
        val outcome = runBlocking { DocumentTextExtractor(opener).extract(File("x.pdf"), DocumentKind.PDF, 10L) }
        assertNotNull(outcome)
        assertEquals(ExtractionOutcome.Failed(FailureReason.CORRUPT), outcome)
    }
}
