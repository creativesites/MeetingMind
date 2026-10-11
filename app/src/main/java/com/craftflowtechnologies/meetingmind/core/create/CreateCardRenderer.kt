package com.craftflowtechnologies.meetingmind.core.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.craftflowtechnologies.meetingmind.core.share.BackgroundPack
import com.craftflowtechnologies.meetingmind.core.share.ShareCardRenderer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Everything a card draws. The scripture text is passed in, fetched from the Bible library; it is never part of the saved card. */
data class CreateRenderInput(
    val text: String,
    val verse: ResolvedScripture? = null,
    val format: CreateFormat = CreateFormat.STORY,
    val design: CreateDesign = CreateDesign(),
    /** A small line above the words ("PEACEFUL"). Off by default; the card stays quiet. */
    val eyebrow: String? = null
)

/** Picks dark or light text, and how much to shade a picture, from samples of the picture under the words. */
object AutoContrast {
    data class Decision(val darkInk: Boolean, val scrim: Float)

    private const val WHITE_NEEDS = 0.183f   // max background luminance for white text at 4.5:1
    private const val DARK_NEEDS = 0.211f    // min background luminance for the dark ink at 4.5:1

    fun luminance(argb: Int): Float {
        fun lin(c: Int): Float { val v = c / 255f; return if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f) }
        return 0.2126f * lin(Color.red(argb)) + 0.7152f * lin(Color.green(argb)) + 0.0722f * lin(Color.blue(argb))
    }

    /** [luminances] are relative luminances 0..1 of the pixels under the text. The worst pixel of the chosen side must still pass. */
    fun decide(luminances: FloatArray, floor: Float = 0.12f): Decision {
        if (luminances.isEmpty()) return Decision(false, floor)
        val sorted = luminances.sortedArray()
        val p90 = sorted[((sorted.size - 1) * 0.9f).toInt()]
        val p10 = sorted[((sorted.size - 1) * 0.1f).toInt()]
        val whiteScrim = if (p90 <= WHITE_NEEDS) 0f else 1f - WHITE_NEEDS / p90
        val darkScrim = if (p10 >= DARK_NEEDS) 0f else (DARK_NEEDS - p10) / (1f - p10)
        return if (whiteScrim <= darkScrim) Decision(false, max(whiteScrim, floor).coerceAtMost(0.85f))
        else Decision(true, max(darkScrim, floor).coerceAtMost(0.85f))
    }
}

/**
 * Draws a Create card with the platform canvas. The studio preview and the shared image are the
 * same drawing. Story cards keep clear of the bars WhatsApp status and Instagram stories draw over
 * the top and bottom of the picture.
 */
object CreateCardRenderer {

    /** Space kept free, as fractions of the card, so status and story chrome never covers the words. */
    data class Safe(val top: Float, val bottom: Float, val side: Float)

    fun safe(format: CreateFormat): Safe = when (format) {
        CreateFormat.STORY -> Safe(top = 0.14f, bottom = 0.18f, side = 0.09f)
        CreateFormat.PORTRAIT -> Safe(top = 0.08f, bottom = 0.08f, side = 0.09f)
        CreateFormat.SQUARE -> Safe(top = 0.09f, bottom = 0.09f, side = 0.09f)
    }

    private class Blocks(val main: StaticLayout?, val verse: StaticLayout?, val reference: StaticLayout?, val gap: Float, val overflow: Boolean) {
        val height: Float get() = (main?.height ?: 0) + (verse?.height ?: 0) + (reference?.height ?: 0) + gap
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment, spacing: Float = 1.22f, maxLines: Int = Int.MAX_VALUE) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(align).setLineSpacing(0f, spacing).setMaxLines(maxLines).build()

    private fun blocks(context: Context, input: CreateRenderInput, width: Int, availableH: Float, unit: Float, ink: Int, accent: Int, align: Layout.Alignment): Blocks {
        val d = input.design
        val body = input.text.trim()
        val verse = input.verse
        val hasBody = body.isNotEmpty()
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = ShareCardRenderer.typeface(context, d.fontPair.body) }
        val versePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = ShareCardRenderer.typeface(context, d.fontPair.scripture) }
        val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; typeface = Typeface.create(ShareCardRenderer.typeface(context, com.craftflowtechnologies.meetingmind.core.share.ShareFont.INTER), Typeface.BOLD)
        }
        val verseText = verse?.text?.trim()?.let { "“${it.trim('“', '”', '"')}”" }
        val total = body.length + (verseText?.length ?: 0)
        var size = (if (total < 90) 84f else if (total < 180) 68f else if (total < 320) 54f else 44f) * unit * d.textScale
        val minSize = 22f * unit
        var main: StaticLayout? = null
        var vl: StaticLayout? = null
        var rl: StaticLayout? = null
        var gap = 0f
        while (true) {
            gap = if (hasBody && verseText != null) 44f * unit else 0f
            bodyPaint.textSize = if (verseText != null && hasBody) size * 0.9f else size
            versePaint.textSize = if (hasBody) size * 0.74f else size * 0.92f
            refPaint.textSize = max(30f * unit, versePaint.textSize * 0.56f)
            main = if (hasBody) layout(body, bodyPaint, width, align) else null
            vl = verseText?.let { layout(it, versePaint, width, align, 1.26f) }
            rl = verse?.let { layout(if (it.versionAbbreviation.isBlank()) it.reference else it.reference + " · " + it.versionAbbreviation, refPaint, width, align, 1.1f, 2) }
            val h = (main?.height ?: 0) + (vl?.height ?: 0) + (rl?.height ?: 0) + gap + (if (rl != null) 40f * unit else 0f)
            if (h <= availableH || size <= minSize) {
                return Blocks(main, vl, rl, gap + (if (rl != null) 40f * unit else 0f), overflow = h > availableH)
            }
            size *= 0.93f
        }
    }

    /** False when the words and passage cannot fit the card even at the smallest size: the studio asks for a shorter passage. */
    fun fits(context: Context, input: CreateRenderInput): Boolean {
        val f = input.format
        val unit = min(f.width, f.height) / 1080f
        val s = safe(f)
        val width = (f.width * (1 - 2 * s.side)).toInt()
        val availableH = f.height * (1 - s.top - s.bottom) - 130f * unit - (if (input.design.includeCompanion) f.width * 0.2f else 0f)
        val b = blocks(context, input, width, availableH, unit, Color.WHITE, Color.WHITE, Layout.Alignment.ALIGN_CENTER)
        return !b.overflow
    }

    fun render(context: Context, input: CreateRenderInput, scale: Float = 1f, companion: Bitmap? = null): Bitmap {
        val f = input.format
        val d = input.design
        val w = max(1, (f.width * scale).toInt())
        val h = max(1, (f.height * scale).toInt())
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = min(w, h) / 1080f
        val s = safe(f)
        val left = w * s.side
        val right = w * (1 - s.side)
        val top = h * s.top
        val bottom = h * (1 - s.bottom)
        val textWidth = (right - left).toInt()
        val align = if (d.centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL

        // Background.
        var darkInk = false
        var scrim = 0f
        when (val bg = d.background) {
            is CreateBackground.Pack -> BackgroundPack.byId(bg.id).also { BackgroundPack.draw(canvas, it, w, h); darkInk = !it.dark }
            is CreateBackground.Photo -> {
                if (!BackgroundPack.drawPhoto(canvas, bg.path, w, h)) {
                    BackgroundPack.draw(canvas, BackgroundPack.all.first(), w, h)
                } else {
                    val decision = AutoContrast.decide(sample(bitmap, Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())))
                    darkInk = decision.darkInk
                    scrim = decision.scrim
                }
            }
        }
        if (scrim > 0f) {
            val c = if (darkInk) Color.WHITE else Color.BLACK
            canvas.drawColor(Color.argb((scrim * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c)))
        }
        val ink = if (darkInk) Color.rgb(15, 23, 42) else Color.WHITE
        val soft = Color.argb(205, Color.red(ink), Color.green(ink), Color.blue(ink))
        val accent = if (darkInk) Color.rgb(122, 78, 15) else Color.rgb(246, 211, 101)

        val companionSize = if (d.includeCompanion && companion != null) w * 0.2f else 0f
        val footH = 100f * unit
        val availableH = (bottom - top) - footH - companionSize - (if (companionSize > 0f) 24f * unit else 0f)
        val b = blocks(context, input, textWidth, availableH, unit, ink, accent, align)

        val eyebrowH = if (input.eyebrow != null) 64f * unit else 0f
        var y = top + (availableH - b.height - eyebrowH).coerceAtLeast(0f) / 2f
        input.eyebrow?.let { e ->
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent; textSize = 30f * unit; letterSpacing = 0.18f
                typeface = Typeface.create(ShareCardRenderer.typeface(context, com.craftflowtechnologies.meetingmind.core.share.ShareFont.INTER), Typeface.BOLD)
            }
            val l = layout(e.uppercase(), p, textWidth, align, 1f, 1)
            canvas.save(); canvas.translate(left, y); l.draw(canvas); canvas.restore(); y += eyebrowH
        }
        b.main?.let { canvas.save(); canvas.translate(left, y); it.draw(canvas); canvas.restore(); y += it.height + b.gap }
        b.verse?.let { canvas.save(); canvas.translate(left, y); it.draw(canvas); canvas.restore(); y += it.height + (if (b.main == null) b.gap else 0f) }
        b.reference?.let { canvas.save(); canvas.translate(left, y); it.draw(canvas); canvas.restore() }

        if (companion != null && companionSize > 0f) {
            val cx = if (d.centered) (w - companionSize) / 2f else left
            val cy = bottom - footH - companionSize
            canvas.drawBitmap(companion, null, android.graphics.RectF(cx, cy, cx + companionSize, cy + companionSize), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Foot: the translation's attribution is always printed; the watermark only if asked for.
        val sans = ShareCardRenderer.typeface(context, com.craftflowtechnologies.meetingmind.core.share.ShareFont.INTER)
        val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 21f * unit; typeface = sans }
        var footY = bottom
        if (d.watermark) {
            val mark = TextPaint(footPaint).apply { textSize = 23f * unit; typeface = Typeface.create(sans, Typeface.BOLD); letterSpacing = 0.06f }
            val l = layout("Made with MeetingMind", mark, textWidth, align, 1f, 1)
            footY -= l.height
            canvas.save(); canvas.translate(left, footY); l.draw(canvas); canvas.restore()
            footY -= 10f * unit
        }
        input.verse?.attribution?.takeIf { it.isNotBlank() }?.let { a ->
            val l = layout(a, footPaint, textWidth, align, 1.1f, 3)
            canvas.save(); canvas.translate(left, footY - l.height); l.draw(canvas); canvas.restore()
        }
        return bitmap
    }

    /** A coarse grid of luminances from the picture inside [area], before any shading. */
    private fun sample(bitmap: Bitmap, area: Rect, steps: Int = 14): FloatArray {
        val out = FloatArray(steps * steps)
        var i = 0
        for (gy in 0 until steps) for (gx in 0 until steps) {
            val x = (area.left + (area.width() * (gx + 0.5f) / steps).toInt()).coerceIn(0, bitmap.width - 1)
            val y = (area.top + (area.height() * (gy + 0.5f) / steps).toInt()).coerceIn(0, bitmap.height - 1)
            out[i++] = AutoContrast.luminance(bitmap.getPixel(x, y))
        }
        return out
    }
}
