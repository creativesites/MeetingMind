package com.example.core.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** What a share card says. The attribution is part of the card, never optional (licence rule). */
data class ShareCardContent(
    val eyebrow: String? = null,
    val text: String,
    /** "John 3:16 · BSB", "C. H. Spurgeon". */
    val reference: String? = null,
    /** A translation's copyright line or a source; printed small at the foot. */
    val attribution: String? = null,
    /** True for Scripture and quotes: set in quotation marks. */
    val quoted: Boolean = true
)

enum class ShareFormat(val label: String, val width: Int, val height: Int, val aspect: String) {
    STORY("Story / Status", 1080, 1920, "9:16"),
    SQUARE("Square post", 1080, 1080, "1:1"),
    WIDE("Wide", 1920, 1080, "16:9")
}

enum class ShareFont(val label: String, val asset: String) {
    LORA("Lora", "fonts/lora.ttf"),
    LORA_ITALIC("Lora italic", "fonts/lora_italic.ttf"),
    PLAYFAIR("Playfair", "fonts/playfair.ttf"),
    INTER("Inter", "fonts/inter.ttf"),
    OUTFIT("Outfit", "fonts/outfit.ttf")
}

data class ShareStyle(
    val format: ShareFormat = ShareFormat.STORY,
    val background: BackgroundSpec = BackgroundSpec.Pack("dawn"),
    val font: ShareFont = ShareFont.LORA,
    /** Relative text size, 0.7–1.4. */
    val textScale: Float = 1f,
    val centered: Boolean = true,
    /** How much the picture is darkened behind the text, 0–0.8. */
    val scrim: Float = 0.35f,
    val watermark: Boolean = true
)

/**
 * Draws a share card with the platform canvas. The studio's preview and the shared image are the
 * same drawing, so what you see is exactly what's sent.
 */
object ShareCardRenderer {

    private val cache = mutableMapOf<String, Typeface>()

    fun typeface(context: Context, font: ShareFont): Typeface =
        cache.getOrPut(font.asset) { runCatching { Typeface.createFromAsset(context.assets, font.asset) }.getOrDefault(Typeface.SERIF) }

    /** Renders at [scale] of the format's size (1 for sharing, less for a quick preview). */
    fun render(context: Context, content: ShareCardContent, style: ShareStyle, scale: Float = 1f): Bitmap {
        val w = (style.format.width * scale).toInt()
        val h = (style.format.height * scale).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = minOf(w, h) / 1080f

        // Background.
        var dark = true
        when (val bg = style.background) {
            is BackgroundSpec.Pack -> BackgroundPack.byId(bg.id).also { BackgroundPack.draw(canvas, it, w, h); dark = it.dark }
            is BackgroundSpec.Solid -> { canvas.drawColor(bg.color); dark = luminance(bg.color) < 0.55 }
            is BackgroundSpec.Photo -> if (!BackgroundPack.drawPhoto(canvas, bg.path, w, h)) BackgroundPack.draw(canvas, BackgroundPack.all.first(), w, h)
        }
        // Scrim: pictures always get one so text stays readable whatever the photo.
        val scrim = if (style.background is BackgroundSpec.Photo) style.scrim.coerceAtLeast(0.2f) else if (dark) style.scrim * 0.6f else 0f
        if (scrim > 0f) {
            val a = (scrim * 255).toInt().coerceIn(0, 230)
            val p = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Color.argb(a / 2, 0, 0, 0), Color.argb(a, 0, 0, 0), Color.argb(a, 0, 0, 0)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        val ink = if (dark || style.background is BackgroundSpec.Photo) Color.WHITE else Color.rgb(0x0F, 0x17, 0x2A)
        val soft = Color.argb(200, Color.red(ink), Color.green(ink), Color.blue(ink))
        val accent = if (dark || style.background is BackgroundSpec.Photo) Color.rgb(0xF6, 0xD3, 0x65) else Color.rgb(0xB7, 0x79, 0x1F)

        val margin = (if (style.format == ShareFormat.WIDE) 150f else 110f) * unit
        val textWidth = (w - 2 * margin).toInt()
        val align = if (style.centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val sans = typeface(context, ShareFont.INTER)

        // Body text, fitted: start large and shrink until it fits the middle of the card.
        val body = if (content.quoted) "“${content.text.trim().trim('“', '”', '"')}”" else content.text.trim()
        val available = h * (if (style.format == ShareFormat.STORY) 0.56f else 0.52f)
        var size = (if (body.length < 120) 78f else if (body.length < 260) 62f else 50f) * unit * style.textScale
        lateinit var layout: StaticLayout
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = typeface(context, style.font) }
        while (true) {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(body, 0, body.length, paint, textWidth).setAlignment(align).setLineSpacing(0f, 1.22f).build()
            if (layout.height <= available || size <= 24f * unit) break
            size *= 0.92f
        }
        if (layout.height > available) {
            // Still too long: cut at a sentence and mark it.
            val cut = body.take((body.length * available / layout.height).toInt()).substringBeforeLast(' ') + "…”"
            layout = StaticLayout.Builder.obtain(cut, 0, cut.length, paint, textWidth).setAlignment(align).setLineSpacing(0f, 1.22f).build()
        }

        // Eyebrow, body, reference as one block centred vertically.
        val eyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; textSize = 30f * unit; typeface = Typeface.create(sans, Typeface.BOLD); letterSpacing = 0.18f }
        val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; textSize = 40f * unit; typeface = Typeface.create(sans, Typeface.BOLD) }
        val eyebrowH = if (content.eyebrow != null) 30f * unit * 1.3f + 44f * unit else 0f
        val refH = if (content.reference != null) 44f * unit + 40f * unit * 1.3f else 0f
        var y = (h - (eyebrowH + layout.height + refH)) / 2f
        fun line(text: String, p: TextPaint, top: Float) {
            val l = StaticLayout.Builder.obtain(text, 0, text.length, p, textWidth).setAlignment(align).setMaxLines(2).build()
            canvas.save(); canvas.translate(margin, top); l.draw(canvas); canvas.restore()
        }
        content.eyebrow?.let { line(it.uppercase(), eyebrowPaint, y); y += eyebrowH }
        canvas.save(); canvas.translate(margin, y); layout.draw(canvas); canvas.restore()
        y += layout.height
        content.reference?.let { line(it, refPaint, y + 44f * unit) }

        // Foot: attribution, then a quiet mark.
        val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = soft; textSize = 22f * unit; typeface = sans }
        var foot = h - 70f * unit
        if (style.watermark) {
            val mark = TextPaint(footPaint).apply { textSize = 24f * unit; typeface = Typeface.create(sans, Typeface.BOLD); letterSpacing = 0.08f }
            line("MeetingMind", mark, foot)
            foot -= 50f * unit
        }
        content.attribution?.takeIf { it.isNotBlank() }?.let { a ->
            val l = StaticLayout.Builder.obtain(a, 0, a.length, footPaint, textWidth).setAlignment(align).setMaxLines(3).build()
            canvas.save(); canvas.translate(margin, foot - l.height); l.draw(canvas); canvas.restore()
        }
        return bitmap
    }

    private fun luminance(color: Int) = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
}
