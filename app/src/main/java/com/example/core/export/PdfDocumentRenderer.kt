package com.example.core.export

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.example.core.notes.InlineStyle
import com.example.core.notes.RichText
import java.io.OutputStream

/**
 * Writes an [ExportDocument] as an A4 PDF with Android's own [PdfDocument].
 *
 * Text is laid out with [StaticLayout] and placed line by line, so a long paragraph flows onto
 * the next page instead of being cut off, and a heading is never left alone at the bottom of a
 * page. Every page carries a footer with its number.
 */
object PdfDocumentRenderer {

    private const val PAGE_WIDTH = 595 // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 56f
    private const val FOOTER_SPACE = 28f
    private const val CONTENT_WIDTH = (PAGE_WIDTH - 2 * MARGIN).toInt()

    private val ACCENT = Color.rgb(0x3E, 0x5B, 0xA9)
    private val INK = Color.rgb(0x1F, 0x1F, 0x1F)
    private val MUTED = Color.rgb(0x6B, 0x6B, 0x6B)
    private val RULE = Color.rgb(0xD0, 0xD0, 0xD0)
    private val HIGHLIGHT = Color.rgb(0xFF, 0xF1, 0x76)

    /** Where pages are drawn. A PDF on a device; a plain canvas in tests. */
    internal interface PageSink {
        fun begin(pageNumber: Int, width: Int, height: Int): Canvas
        fun end()
    }

    fun render(document: ExportDocument, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            layOut(document, object : PageSink {
                override fun begin(pageNumber: Int, width: Int, height: Int): Canvas =
                    pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create()).also { page = it }.canvas
                override fun end() { page?.let { pdf.finishPage(it) } }
            })
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    /** Draws [document] page by page into [sink] and returns the number of pages. */
    internal fun layOut(document: ExportDocument, sink: PageSink): Int {
        val writer = Writer(sink, document.serifBody)
        writer.writeDocument(document)
        writer.finish()
        return writer.pageCount
    }

    private class Writer(private val sink: PageSink, serif: Boolean) {
        private var pageNumber = 0
        private lateinit var canvas: Canvas
        private var y = 0f
        val pageCount get() = pageNumber
        private val bodyFace: Typeface = if (serif) Typeface.SERIF else Typeface.SANS_SERIF

        init { startPage() }

        private val bottom get() = PAGE_HEIGHT - MARGIN - FOOTER_SPACE

        private fun startPage() {
            pageNumber++
            canvas = sink.begin(pageNumber, PAGE_WIDTH, PAGE_HEIGHT)
            y = MARGIN
        }

        private fun endPage() {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 8f; textAlign = Paint.Align.CENTER }
            canvas.drawText("MeetingMind  ·  $pageNumber", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN / 2f, paint)
            sink.end()
        }

        private fun newPage() {
            endPage()
            startPage()
        }

        fun finish() = endPage()

        fun writeDocument(document: ExportDocument) {
            text(RichText.plain(document.title.ifBlank { "Untitled" }), paint(22f, INK, bold = true, face = Typeface.SANS_SERIF), after = 4f)
            document.subtitle?.let { text(RichText.plain(it), paint(10f, MUTED, face = Typeface.SANS_SERIF), after = 18f) }

            val numbers = document.numberedPositions()
            document.blocks.forEachIndexed { i, block -> write(block, numbers[i]) }

            if (document.closingNotes.isNotEmpty()) {
                rule()
                document.closingNotes.forEach { text(RichText.plain(it), paint(7.5f, MUTED), after = 3f) }
            }
        }

        private fun write(block: ExportBlock, number: Int?) {
            when (block) {
                is ExportBlock.Heading -> {
                    val size = when (block.level) { 1 -> 16f; 2 -> 13.5f; else -> 12f }
                    y += if (block.level == 1) 12f else 8f
                    text(block.text, paint(size, if (block.level >= 3) ACCENT else INK, bold = true, face = Typeface.SANS_SERIF), after = 5f, keepWithNext = 36f)
                }
                is ExportBlock.Paragraph -> if (block.text.isEmpty) y += 6f else text(block.text, body(), after = 7f)
                is ExportBlock.ListItem -> {
                    val indent = 14f + block.indent * 16f
                    val marker = when (block.kind) {
                        ListKind.BULLET -> listOf("•", "◦", "▪")[block.indent % 3]
                        ListKind.NUMBERED -> "${number ?: 1}."
                        ListKind.CHECKLIST -> if (block.checked) "☑" else "☐"
                    }
                    val content = if (block.kind == ListKind.CHECKLIST && block.checked) {
                        block.text.applyStyle(InlineStyle.STRIKETHROUGH, 0, block.text.text.length)
                    } else block.text
                    text(content, body(), indent = indent + 12f, after = 3.5f, marker = marker, markerX = indent - 2f)
                }
                is ExportBlock.Quote -> barred { text(block.text, body(italic = true, color = Color.rgb(0x40, 0x40, 0x40)), indent = 14f, after = 8f) }
                is ExportBlock.Scripture -> {
                    y += 4f
                    val version = block.versionAbbreviation?.let { " ($it)" } ?: ""
                    barred {
                        text(RichText.plain(block.reference + version), paint(9.5f, ACCENT, bold = true, face = Typeface.SANS_SERIF), indent = 14f, after = 2f, keepWithNext = 30f)
                        if (block.text != null) text(RichText.plain(block.text), body(), indent = 14f, after = 8f)
                        else text(RichText.plain("Verse text unavailable offline."), body(italic = true, color = MUTED), indent = 14f, after = 8f)
                    }
                }
                is ExportBlock.Excerpt -> {
                    block.label?.let { text(RichText.plain(it), paint(8.5f, MUTED, face = Typeface.SANS_SERIF), indent = 14f, after = 1f, keepWithNext = 30f) }
                    barred { text(RichText.plain(block.text), body(italic = true, color = Color.rgb(0x40, 0x40, 0x40)), indent = 14f, after = 8f) }
                }
                is ExportBlock.Image -> image(block)
                is ExportBlock.Recording -> {
                    val detail = block.detail?.let { " · $it" } ?: ""
                    val label = "Recording: ${block.title}$detail"
                    val line = RichText.plain(label).applyStyle(InlineStyle.BOLD, 0, label.length)
                    text(line, paint(10.5f, INK, face = Typeface.SANS_SERIF), after = 3f, keepWithNext = 30f)
                    block.summary?.takeIf { it.isNotBlank() }?.let { text(RichText.plain(it), body(), after = 8f) }
                }
                is ExportBlock.Facts -> {
                    block.rows.forEach { (k, v) ->
                        text(RichText.plain("$k: $v").applyStyle(InlineStyle.BOLD, 0, k.length + 1), body(size = 10f), after = 2f)
                    }
                    y += 6f
                }
                ExportBlock.Divider -> rule()
            }
        }

        /** Draws a coloured bar down the left of whatever [content] writes, across page breaks. */
        private fun barred(content: () -> Unit) {
            val startPage = pageNumber
            val startY = y
            content()
            val bar = Paint().apply { color = ACCENT; strokeWidth = 2.5f }
            // Only the part on the current page is drawn; earlier pages are already closed.
            val top = if (pageNumber == startPage) startY else MARGIN
            canvas.drawLine(MARGIN + 2f, top, MARGIN + 2f, y - 6f, bar)
        }

        private fun rule() {
            y += 6f
            if (y > bottom) newPage()
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply { color = RULE; strokeWidth = 0.75f })
            y += 12f
        }

        private fun image(block: ExportBlock.Image) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(block.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                text(RichText.plain("[Image not available]"), body(italic = true, color = MUTED), after = 6f)
                return
            }
            val maxHeight = (bottom - MARGIN) * 0.8f
            var w = minOf(CONTENT_WIDTH.toFloat(), bounds.outWidth.toFloat())
            var h = w * bounds.outHeight / bounds.outWidth
            if (h > maxHeight) { h = maxHeight; w = h * bounds.outWidth / bounds.outHeight }
            // Decode only as many pixels as the page can show (about 2x for print sharpness).
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w * 2) sample *= 2
            val bitmap = BitmapFactory.decodeFile(block.path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bitmap == null) {
                text(RichText.plain("[Image not available]"), body(italic = true, color = MUTED), after = 6f)
                return
            }
            if (y + h > bottom) newPage()
            val left = MARGIN + (CONTENT_WIDTH - w) / 2f
            canvas.drawBitmap(bitmap, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
            y += h + 4f
            block.caption?.takeIf { it.isNotBlank() }?.let {
                text(RichText.plain(it), paint(8.5f, MUTED, face = Typeface.SANS_SERIF), after = 8f, center = true)
            } ?: run { y += 6f }
        }

        /**
         * Lays out [rich] and draws it, breaking between lines when a page fills.
         * [keepWithNext] reserves room so a heading is not stranded at the foot of a page.
         */
        private fun text(
            rich: RichText,
            paint: TextPaint,
            indent: Float = 0f,
            after: Float = 0f,
            keepWithNext: Float = 0f,
            marker: String? = null,
            markerX: Float = 0f,
            center: Boolean = false
        ) {
            val width = (CONTENT_WIDTH - indent).toInt().coerceAtLeast(40)
            val spanned = spanned(rich)
            val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, width)
                .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1.5f, 1.12f)
                .setIncludePad(false)
                .build()
            val firstLineHeight = if (layout.lineCount > 0) layout.getLineBottom(0).toFloat() else 0f
            if (y + firstLineHeight + keepWithNext > bottom) newPage()

            var line = 0
            while (line < layout.lineCount) {
                // As many lines as fit on this page.
                val top = layout.getLineTop(line)
                var last = line
                while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= bottom) last++
                val chunkHeight = (layout.getLineBottom(last) - top).toFloat()
                if (y + chunkHeight > bottom && y > MARGIN) { newPage(); continue }

                canvas.save()
                canvas.translate(MARGIN + indent, y - top)
                canvas.clipRect(0f, top.toFloat(), width.toFloat(), layout.getLineBottom(last).toFloat())
                layout.draw(canvas)
                canvas.restore()

                if (line == 0 && marker != null) {
                    val markerPaint = TextPaint(paint).apply { typeface = Typeface.create(bodyFace, Typeface.NORMAL); isStrikeThruText = false }
                    canvas.drawText(marker, MARGIN + markerX, y + layout.getLineBaseline(0) - top, markerPaint)
                }
                y += chunkHeight
                line = last + 1
                if (line < layout.lineCount) newPage()
            }
            y += after
        }

        private fun spanned(rich: RichText): CharSequence {
            val builder = SpannableStringBuilder(rich.text)
            for (span in rich.spans) {
                val what: Any = when (span.style) {
                    InlineStyle.BOLD -> StyleSpan(Typeface.BOLD)
                    InlineStyle.ITALIC -> StyleSpan(Typeface.ITALIC)
                    InlineStyle.UNDERLINE -> UnderlineSpan()
                    InlineStyle.STRIKETHROUGH -> StrikethroughSpan()
                    InlineStyle.HIGHLIGHT -> BackgroundColorSpan(HIGHLIGHT)
                    InlineStyle.CODE -> TypefaceSpan("monospace")
                    InlineStyle.LINK -> ForegroundColorSpan(ACCENT)
                }
                builder.setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (span.style == InlineStyle.LINK) builder.setSpan(UnderlineSpan(), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return builder
        }

        private fun body(size: Float = 10.5f, italic: Boolean = false, color: Int = INK) =
            paint(size, color, italic = italic, face = bodyFace)

        private fun paint(size: Float, color: Int, bold: Boolean = false, italic: Boolean = false, face: Typeface = bodyFace) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                val style = when {
                    bold && italic -> Typeface.BOLD_ITALIC
                    bold -> Typeface.BOLD
                    italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                typeface = Typeface.create(face, style)
            }
    }
}
