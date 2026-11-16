package com.craftflowtechnologies.meetingmind.core.documents

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rasterises pages with the platform [PdfRenderer]. No dependency, and the renderer is the one the
 * viewer uses, so a recognised page and the picture a person sees come from the same engine.
 *
 * PdfRenderer is not thread-safe, so every call is synchronized. Callers run it on a background
 * dispatcher. The caller owns each returned bitmap and must recycle it.
 */
class PdfPageRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    private var closed = false

    val pageCount: Int
        @Synchronized get() = if (closed) 0 else renderer.pageCount

    /**
     * Renders the 0-based [index] so its longer side is [maxEdgePx] (never more than that). Returns null
     * for a bad index, a closed renderer, or when the device is out of memory.
     */
    @Synchronized
    fun render(index: Int, maxEdgePx: Int = DocumentLimits.MAX_RENDER_EDGE_PX): Bitmap? {
        if (closed || index < 0 || index >= renderer.pageCount) return null
        return try {
            renderer.openPage(index).use { page ->
                val scale = maxEdgePx.toFloat() / max(page.width, page.height).coerceAtLeast(1)
                val width = max(1, (page.width * scale).roundToInt())
                val height = max(1, (page.height * scale).roundToInt())
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // PDF pages can be transparent; recognition and the paper look both want white.
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: IllegalStateException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /** Opens [file]. Throws SecurityException for an encrypted PDF, and IOException for a corrupt one. */
        fun open(file: File): PdfPageRenderer {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfPageRenderer(descriptor, PdfRenderer(descriptor))
            } catch (e: Exception) {
                runCatching { descriptor.close() }
                throw e
            }
        }
    }
}
