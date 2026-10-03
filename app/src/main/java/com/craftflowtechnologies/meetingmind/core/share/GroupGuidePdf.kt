package com.craftflowtechnologies.meetingmind.core.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import com.craftflowtechnologies.meetingmind.ai.notes.GroupGuideExport
import com.craftflowtechnologies.meetingmind.ai.notes.SectionDraft
import java.io.File

/**
 * Writes a discussion guide as a clean A4 PDF with the platform's own PdfDocument — no library,
 * nothing leaves the phone. Serif body, small-caps section labels, a quiet footer on every page.
 */
object GroupGuidePdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 54f
    private const val FOOTER = 40f

    fun write(dir: File, title: String?, sections: List<SectionDraft>): File {
        dir.mkdirs()
        val file = File(dir, GroupGuideExport.fileName(title) + ".pdf")
        val doc = PdfDocument()
        val writer = PageWriter(doc)
        val width = (PAGE_W - 2 * MARGIN).toInt()
        val ink = Color.rgb(0x0F, 0x17, 0x2A)
        val soft = Color.rgb(0x47, 0x55, 0x69)
        val gold = Color.rgb(0x8A, 0x5A, 0x14)

        fun paint(size: Float, color: Int, face: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }
        val titlePaint = paint(24f, ink, Typeface.create(Typeface.SERIF, Typeface.BOLD))
        val subPaint = paint(11f, soft, Typeface.SANS_SERIF)
        val headPaint = paint(10.5f, gold, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)).apply { letterSpacing = 0.12f }
        val bodyPaint = paint(13f, ink, Typeface.SERIF)

        writer.draw(layout(GroupGuideExport.title(title), titlePaint, width), 6f)
        writer.draw(layout("Small-group discussion guide", subPaint, width), 22f)

        sections.filter { s -> s.items.any { it.text.isNotBlank() } }.forEach { section ->
            writer.keepWithNext(headPaint.textSize * 1.4f + 3 * bodyPaint.textSize)
            writer.draw(layout(section.title.uppercase(), headPaint, width), 8f)
            section.items.filter { it.text.isNotBlank() }.forEachIndexed { i, item ->
                val prefix = if (GroupGuideExport.isNumbered(section.key)) "${i + 1}.  " else "•  "
                val spannable = SpannableString(prefix + item.text.trim()).apply {
                    setSpan(LeadingMarginSpan.Standard(0, 20), 0, length, Spanned.SPAN_PARAGRAPH)
                }
                writer.draw(layout(spannable, bodyPaint, width, spacing = 1.18f), 9f)
            }
            writer.skip(10f)
        }
        writer.finish()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, spacing: Float = 1f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()

    /** Flows content down pages, starting a new one when the next block won't fit. */
    private class PageWriter(private val doc: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(0x94, 0xA3, 0xB8); typeface = Typeface.SANS_SERIF }

        private fun newPage() {
            closePage()
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
            canvas = page!!.canvas
            y = MARGIN
        }

        private fun closePage() {
            val p = page ?: return
            canvas!!.drawText("MeetingMind  ·  $number", MARGIN, PAGE_H - FOOTER + 14f, footerPaint)
            doc.finishPage(p)
            page = null
        }

        fun keepWithNext(needed: Float) { if (page == null || y + needed > PAGE_H - MARGIN - FOOTER) newPage() }

        fun draw(layout: StaticLayout, gap: Float) {
            if (page == null || y + layout.height > PAGE_H - MARGIN - FOOTER) newPage()
            val c = canvas!!
            c.save(); c.translate(MARGIN, y); layout.draw(c); c.restore()
            y += layout.height + gap
        }

        fun skip(gap: Float) { y += gap }

        fun finish() { if (page == null) newPage(); closePage() }
    }
}
