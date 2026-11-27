package com.craftflowtechnologies.meetingmind.core.documents

import java.io.Closeable

/**
 * On-device document reading (spec §R4). Everything here runs on the phone: text is read from the
 * PDF text layer, and scanned pages are recognised with ML Kit. Nothing in this package sends a
 * document anywhere.
 */

/** What an inbox item is, as far as reading goes. Only these two are read. */
enum class DocumentKind {
    PDF,
    IMAGE;

    companion object {
        /** Maps `InboxItemEntity.kind`; null for anything that is not read (text, audio, links...). */
        fun fromItemKind(kind: String?): DocumentKind? = when (kind) {
            "PDF" -> PDF
            "IMAGE" -> IMAGE
            else -> null
        }
    }
}

/** Mirrors `InboxItemEntity.readStatus`. Stored by name. */
enum class ReadStatus {
    NONE, PENDING, READING, DONE, NO_TEXT, LOCKED, FAILED;

    companion object {
        fun parse(value: String?): ReadStatus = entries.firstOrNull { it.name == value } ?: NONE
    }
}

/** Where a page's words came from. Stored by name in `document_pages.source`. */
enum class PageTextSource { TEXT_LAYER, OCR }

/** Limits from the spec: read at most 300 pages; a file over 50 MB is flagged as too large. */
object DocumentLimits {
    const val MAX_PAGES = 300
    const val MAX_BYTES = 50L * 1024 * 1024
    /** A page whose text layer has fewer characters than this is rendered and recognised instead. */
    const val TEXT_LAYER_MIN_CHARS = 40
    /** Longest edge of a rasterised page, for recognition and for the viewer's first render. */
    const val MAX_RENDER_EDGE_PX = 1600
    /** Documents over this many pages run as a foreground notification. */
    const val FOREGROUND_PAGE_THRESHOLD = 20
    /** The text kept on the inbox item for compatibility with code that reads `InboxItemEntity.text`. */
    const val COMPAT_TEXT_CHARS = 8000
}

/** One page after reading. [page] is 1-based, as the viewer and citations use it. */
data class ExtractedPage(
    val page: Int,
    val text: String,
    val source: PageTextSource,
    val confidence: Double?,
    /** Word or line boxes as [PageBoxes] JSON, normalised to the page. Null when unknown. */
    val boxesJson: String?
)

/** A page's text layer (or recognised text), with boxes normalised to the page size. */
data class LayerText(val text: String, val boxes: List<PageBox>)

/** Text recognised from a rendered page. ML Kit v2 gives no per-word confidence, so it is null. */
data class OcrText(val text: String, val boxes: List<PageBox>, val confidence: Double? = null)

enum class FailureReason { CORRUPT, OCR_UNAVAILABLE }

/** What a read produced. The worker maps this to [ReadStatus]; see [DocumentReadState]. */
sealed interface ExtractionOutcome {
    /**
     * The pages that were read, in order. [pageCount] is the whole document; [truncated] is true when
     * pages past [DocumentLimits.MAX_PAGES] were not read. [hasText] is false for a document with no
     * readable words anywhere (NO_TEXT).
     */
    data class Read(
        val pages: List<ExtractedPage>,
        val pageCount: Int,
        val truncated: Boolean,
        val hasText: Boolean
    ) : ExtractionOutcome

    data object Locked : ExtractionOutcome

    data class Failed(val reason: FailureReason) : ExtractionOutcome
}

/** Thrown by a source that cannot open the file because it needs a password. */
class DocumentLockedException(message: String = "Document is password protected") : Exception(message)

/** Thrown by a source that cannot parse the file. */
class DocumentCorruptException(message: String = "Document could not be parsed") : Exception(message)

/**
 * What the extractor reads from. The Android implementation is in [AndroidDocumentSources]; tests use
 * fakes. Implementations are used from one coroutine at a time.
 */
interface DocumentPageSource : Closeable {
    /** Pages in the whole document. An image is one page. */
    val pageCount: Int

    /** The text layer of the 0-based [index], or empty when the page has none. */
    fun textLayer(index: Int): LayerText

    /** Renders the 0-based [index] and recognises it. Null when recognition is not available for it. */
    suspend fun recognise(index: Int): OcrText?
}

/** Opens a file as a [DocumentPageSource]. Throws [DocumentLockedException] or [DocumentCorruptException]. */
fun interface DocumentSourceOpener {
    fun open(file: java.io.File, kind: DocumentKind): DocumentPageSource
}
