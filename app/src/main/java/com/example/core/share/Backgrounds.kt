package com.example.core.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/** What sits behind a share card or story. */
sealed interface BackgroundSpec {
    /** One of the built-in painted backgrounds — offline, free, no licence to worry about. */
    data class Pack(val id: String) : BackgroundSpec
    /** A picture: the person's own photo, or one Gemini made. */
    data class Photo(val path: String, val generated: Boolean = false) : BackgroundSpec
    data class Solid(val color: Int) : BackgroundSpec
}

/** A soft pool of light. Positions and radius are fractions of the card. */
data class Glow(val x: Float, val y: Float, val r: Float, val color: Int)

/**
 * A painted background: a gradient, glows, and optionally stars. [dark] says whether light text
 * reads on it.
 */
data class PackBackground(
    val id: String,
    val name: String,
    val colors: List<Int>,
    val glows: List<Glow> = emptyList(),
    val stars: Boolean = false,
    val dark: Boolean = true,
    /** Gradient direction: true top→bottom, false diagonal. */
    val vertical: Boolean = true
)

/**
 * The built-in pack (PLAN_V2 F4): painted in code rather than shipped as photos, so it costs
 * nothing in size, needs no network, and draws crisply at any card size.
 */
object BackgroundPack {
    private fun c(hex: Long) = hex.toInt()

    val all: List<PackBackground> = listOf(
        PackBackground("dawn", "Dawn", listOf(c(0xFF2B2140), c(0xFF8E4B6B), c(0xFFF3A76B)), listOf(Glow(0.72f, 0.78f, 0.55f, c(0xAAFFD28A)))),
        PackBackground("golden", "Golden hour", listOf(c(0xFF3A2A1A), c(0xFFB7791F), c(0xFFF6D365)), listOf(Glow(0.8f, 0.2f, 0.5f, c(0x99FFF1C2)))),
        PackBackground("dusk", "Dusk", listOf(c(0xFF1B1530), c(0xFF4C2A6B), c(0xFFC0587E)), listOf(Glow(0.25f, 0.85f, 0.5f, c(0x88FF9A8B)))),
        PackBackground("night", "Night sky", listOf(c(0xFF070B1E), c(0xFF141E46), c(0xFF2B3A78)), listOf(Glow(0.78f, 0.18f, 0.18f, c(0xCCF4F1E6))), stars = true),
        PackBackground("ocean", "Still waters", listOf(c(0xFF0B2A3C), c(0xFF1D6F8C), c(0xFF8FD3E0)), listOf(Glow(0.3f, 0.2f, 0.45f, c(0x66E0FBFF)))),
        PackBackground("forest", "Green pastures", listOf(c(0xFF0F2A1D), c(0xFF2F7D5B), c(0xFFB6D7A8)), listOf(Glow(0.75f, 0.25f, 0.45f, c(0x66FFF7C2)))),
        PackBackground("lavender", "Lavender", listOf(c(0xFF3B2B63), c(0xFF7C6BB5), c(0xFFE6DDF7)), listOf(Glow(0.2f, 0.3f, 0.5f, c(0x55FFFFFF)))),
        PackBackground("ember", "Ember", listOf(c(0xFF1A0B0B), c(0xFF7A1F1F), c(0xFFE0703A)), listOf(Glow(0.5f, 0.95f, 0.6f, c(0x99FFB26B)))),
        PackBackground("sanctuary", "Sanctuary", listOf(c(0xFF1B1530), c(0xFF2B2140), c(0xFF3A2A1A)), listOf(Glow(0.85f, 0.1f, 0.5f, c(0x8CB7791F)))),
        PackBackground("slate", "Slate", listOf(c(0xFF0F172A), c(0xFF1E293B), c(0xFF334155)), listOf(Glow(0.2f, 0.15f, 0.4f, c(0x446366F1)))),
        PackBackground("paper", "Paper", listOf(c(0xFFFCFAF6), c(0xFFF6EFE3)), listOf(Glow(0.8f, 0.15f, 0.4f, c(0x55F6D365))), dark = false),
        PackBackground("sage", "Sage", listOf(c(0xFFEFF6F1), c(0xFFD7E8DC)), listOf(Glow(0.2f, 0.85f, 0.45f, c(0x55FFFFFF))), dark = false),
        PackBackground("rose", "Rose", listOf(c(0xFFFFF1F2), c(0xFFFBD5DC)), listOf(Glow(0.85f, 0.2f, 0.45f, c(0x66FFFFFF))), dark = false),
        PackBackground("sky", "Morning sky", listOf(c(0xFFBFE3FF), c(0xFFFFF4E0)), listOf(Glow(0.75f, 0.25f, 0.3f, c(0xCCFFF6D0))), dark = false),
        PackBackground("indigo", "Indigo", listOf(c(0xFF4F46E5), c(0xFF7C3AED)), listOf(Glow(0.85f, 0.1f, 0.5f, c(0x44FFFFFF))), vertical = false),
        PackBackground("aurora", "Aurora", listOf(c(0xFF04141F), c(0xFF0E4D4A), c(0xFF3FB28F), c(0xFF1D2B5A)), listOf(Glow(0.3f, 0.35f, 0.5f, c(0x5546E6A5))), stars = true)
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    /** A background for a date and a kind of card, so each day's stories look different but stable. */
    fun forDay(epochDay: Long, salt: Int = 0, darkOnly: Boolean = true): PackBackground {
        val pool = if (darkOnly) all.filter { it.dark } else all
        return pool[Math.floorMod(epochDay * 7 + salt * 3, pool.size.toLong()).toInt()]
    }

    fun draw(canvas: Canvas, bg: PackBackground, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val stops = FloatArray(bg.colors.size) { it / (bg.colors.size - 1f).coerceAtLeast(1f) }
        paint.shader = if (bg.vertical) LinearGradient(0f, 0f, 0f, h.toFloat(), bg.colors.toIntArray(), stops, Shader.TileMode.CLAMP)
            else LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), bg.colors.toIntArray(), stops, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val size = maxOf(w, h)
        bg.glows.forEach { g ->
            val r = g.r * size
            paint.shader = RadialGradient(g.x * w, g.y * h, r, intArrayOf(g.color, g.color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(g.x * w, g.y * h, r, paint)
        }
        if (bg.stars) {
            paint.shader = null
            val random = Random(bg.id.hashCode())
            repeat(90) {
                paint.color = android.graphics.Color.argb(40 + random.nextInt(170), 255, 255, 255)
                canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h * 0.7f, (0.6f + random.nextFloat() * 1.8f) * size / 1080f, paint)
            }
        }
    }

    /** Draws [path] to fill the card, cropped to its centre. False if it can't be read. */
    fun drawPhoto(canvas: Canvas, path: String, w: Int, h: Int): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val bmp: Bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val scale = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
        val sw = (w / scale).toInt()
        val sh = (h / scale).toInt()
        val left = (bmp.width - sw) / 2
        val top = (bmp.height - sh) / 2
        canvas.drawBitmap(bmp, Rect(left, top, left + sw, top + sh), RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
        return true
    }
}
