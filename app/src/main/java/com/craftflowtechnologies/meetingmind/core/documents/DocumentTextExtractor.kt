package com.craftflowtechnologies.meetingmind.core.documents

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads a document page by page (spec §R4, step 2).
 *
 * Each page tries its text layer first. A page with fewer than [DocumentLimits.TEXT_LAYER_MIN_CHARS]
 * characters is rendered and recognised instead. If recognition gives nothing or is unavailable, the
 * page keeps whatever the text layer had, so a page is never lost.
 *
 * Failures: a password stops the read with [ExtractionOutcome.Locked]; a file that cannot be opened
 * is [ExtractionOutcome.Failed]; a document with no words anywhere is a [ExtractionOutcome.Read] with
 * `hasText = false`. Cancellation is never swallowed.
 */
class DocumentTextExtractor(private val openers: DocumentSourceOpener) {

    /**
     * [onProgress] is called with (pageCount, pagesDone) once the page count is known, and after each
     * page. It may suspend, so the worker can update the database and the foreground notification.
     */
    suspend fun extract(
        file: File,
        kind: DocumentKind,
        fileBytes: Long,
        onProgress: suspend (pageCount: Int, readPages: Int) -> Unit = { _, _ -> }
    ): ExtractionOutcome = withContext(Dispatchers.IO) {
        val source = try {
            openers.open(file, kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DocumentLockedException) {
            return@withContext ExtractionOutcome.Locked
        } catch (e: Exception) {
            return@withContext ExtractionOutcome.Failed(FailureReason.CORRUPT)
        }
        // use() closes the PDF and the renderer on every path, including cancellation.
        source.use { doc -> readPages(doc, fileBytes, onProgress) }
    }

    private suspend fun readPages(
        doc: DocumentPageSource,
        fileBytes: Long,
        onProgress: suspend (pageCount: Int, readPages: Int) -> Unit
    ): ExtractionOutcome {
        val total = doc.pageCount
        if (total <= 0) return ExtractionOutcome.Failed(FailureReason.CORRUPT)
        val limit = minOf(total, DocumentLimits.MAX_PAGES)
        // Over 50 MB is flagged as too large even when every page fits under the 300-page limit.
        val truncated = limit < total || fileBytes > DocumentLimits.MAX_BYTES

        onProgress(total, 0)
        val pages = ArrayList<ExtractedPage>(limit)
        var ocrAttempts = 0
        var ocrAnswers = 0

        for (index in 0 until limit) {
            currentCoroutineContext().ensureActive()
            val layer = attempt { doc.textLayer(index) } ?: LayerText("", emptyList())
            val layerText = layer.text.trim()

            val page = if (layerText.length >= DocumentLimits.TEXT_LAYER_MIN_CHARS) {
                ExtractedPage(index + 1, layerText, PageTextSource.TEXT_LAYER, null, PageBoxes.encode(layer.boxes))
            } else {
                ocrAttempts++
                val ocr = attemptSuspend { doc.recognise(index) }
                if (ocr != null) ocrAnswers++
                if (ocr != null && ocr.text.isNotBlank()) {
                    ExtractedPage(index + 1, ocr.text.trim(), PageTextSource.OCR, ocr.confidence, PageBoxes.encode(ocr.boxes))
                } else {
                    ExtractedPage(index + 1, layerText, PageTextSource.TEXT_LAYER, null, PageBoxes.encode(layer.boxes))
                }
            }
            pages += page
            onProgress(total, index + 1)
        }

        val hasText = pages.any { it.text.isNotBlank() }
        // Pages needed recognition and none got an answer: recognition is not working on this phone.
        if (!hasText && ocrAttempts > 0 && ocrAnswers == 0) {
            return ExtractionOutcome.Failed(FailureReason.OCR_UNAVAILABLE)
        }
        return ExtractionOutcome.Read(pages, total, truncated, hasText)
    }

    /** Runs one page's work. A page that fails is treated as empty rather than failing the document. */
    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private suspend fun <T> attemptSuspend(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
