package com.craftflowtechnologies.meetingmind.core.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * The Android implementations of [DocumentSourceOpener]. A PDF is read with PdfBox-Android for its
 * text layer (with word positions) and with [PdfPageRenderer] and ML Kit for pages that need
 * recognition. An image is decoded once, turned upright, and recognised.
 */
class AndroidDocumentSourceOpener(context: Context) : DocumentSourceOpener {
    private val appContext = context.applicationContext

    override fun open(file: File, kind: DocumentKind): DocumentPageSource {
        if (!file.isFile) throw DocumentCorruptException("File is missing")
        return when (kind) {
            DocumentKind.PDF -> {
                PdfBoxBootstrap.ensure(appContext)
                try {
                    PdfDocumentSource(file)
                } catch (e: InvalidPasswordException) {
                    throw DocumentLockedException()
                } catch (e: SecurityException) {
                    // PdfRenderer refuses encrypted files; PdfBox may already have opened them.
                    throw DocumentLockedException()
                } catch (e: IOException) {
                    throw DocumentCorruptException()
                }
            }
            DocumentKind.IMAGE -> {
                if (!ImageDocumentSource.isDecodable(file)) throw DocumentCorruptException()
                ImageDocumentSource(file)
            }
        }
    }
}

/** PdfBox needs its resources (fonts, CMaps) loaded from the app's assets, once per process. */
internal object PdfBoxBootstrap {
    @Volatile private var ready = false

    @Synchronized
    fun ensure(context: Context) {
        if (ready) return
        PDFBoxResourceLoader.init(context.applicationContext)
        ready = true
    }
}

/** A PDF: text layer through PdfBox, rasterised pages through [PdfPageRenderer], recognition through ML Kit. */
internal class PdfDocumentSource(file: File) : DocumentPageSource {
    private val document: PDDocument = PDDocument.load(file, "")
    private val renderer: PdfPageRenderer
    private val ocr = OcrRecognizer()

    override val pageCount: Int

    init {
        try {
            // An owner password alone still lets the file be read; a user password does not (see above).
            if (document.isEncrypted && !document.currentAccessPermission.canExtractContent()) {
                throw DocumentLockedException()
            }
            pageCount = document.numberOfPages
            renderer = PdfPageRenderer.open(file)
        } catch (e: Throwable) {
            runCatching { document.close() }
            throw e
        }
    }

    override fun textLayer(index: Int): LayerText {
        val page = document.getPage(index)
        val box = page.cropBox ?: return LayerText("", emptyList())
        val stripper = WordBoxStripper(box.width, box.height)
        stripper.setStartPage(index + 1)
        stripper.setEndPage(index + 1)
        val text = stripper.getText(document)
        return LayerText(text.trim(), stripper.words)
    }

    override suspend fun recognise(index: Int): OcrText? {
        val bitmap = renderer.render(index) ?: return null
        return try {
            ocr.recognise(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        runCatching { ocr.close() }
        runCatching { renderer.close() }
        runCatching { document.close() }
    }
}

/**
 * Collects word boxes from PdfBox's glyph positions. A word is a run of glyphs without whitespace;
 * its box is the union of its glyphs. Coordinates are normalised to the page's crop box.
 */
internal class WordBoxStripper(
    private val pageWidth: Float,
    private val pageHeight: Float
) : PDFTextStripper() {

    val words = ArrayList<PageBox>()

    override fun writeString(text: String?, textPositions: List<TextPosition>?) {
        super.writeString(text, textPositions)
        if (textPositions == null || pageWidth <= 0f || pageHeight <= 0f) return

        val word = StringBuilder()
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE

        fun flush() {
            if (word.isNotEmpty() && right > left && bottom > top) {
                PageBox.clamped(
                    left = (left / pageWidth).toDouble(),
                    top = (top / pageHeight).toDouble(),
                    width = ((right - left) / pageWidth).toDouble(),
                    height = ((bottom - top) / pageHeight).toDouble(),
                    text = word.toString()
                )?.let { words += it }
            }
            word.setLength(0)
            left = Float.MAX_VALUE
            top = Float.MAX_VALUE
            right = -Float.MAX_VALUE
            bottom = -Float.MAX_VALUE
        }

        for (position in textPositions) {
            val glyph = position.unicode.orEmpty()
            if (glyph.isBlank()) {
                flush()
                continue
            }
            word.append(glyph)
            // yDirAdj is the baseline in display space (origin top-left); the glyph rises by its height.
            val baseline = position.yDirAdj
            left = min(left, position.xDirAdj)
            top = min(top, baseline - position.heightDir)
            right = max(right, position.xDirAdj + position.widthDirAdj)
            bottom = max(bottom, baseline)
        }
        flush()
    }
}

/** A single image (JPEG, PNG...): decoded at a size ML Kit can handle, turned upright, and recognised. */
internal class ImageDocumentSource(private val file: File) : DocumentPageSource {
    private val ocr = OcrRecognizer()

    override val pageCount: Int = 1

    override fun textLayer(index: Int): LayerText = LayerText("", emptyList())

    override suspend fun recognise(index: Int): OcrText? {
        if (index != 0) return null
        val bitmap = decodeUpright(file, DocumentLimits.MAX_RENDER_EDGE_PX) ?: return null
        return try {
            ocr.recognise(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        runCatching { ocr.close() }
    }

    companion object {
        fun isDecodable(file: File): Boolean {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            return bounds.outWidth > 0 && bounds.outHeight > 0
        }

        /** Decodes with a power-of-two sample so the longer side is at most [maxEdge], then applies EXIF rotation. */
        internal fun decodeUpright(file: File, maxEdge: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val longest = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= maxEdge) sample *= 2

            val decoded = BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null

            val degrees = runCatching {
                when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            if (degrees == 0f) return decoded

            val matrix = Matrix().apply { postRotate(degrees) }
            val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (upright !== decoded) decoded.recycle()
            return upright
        }
    }
}
