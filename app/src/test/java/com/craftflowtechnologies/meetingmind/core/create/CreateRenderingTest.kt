package com.craftflowtechnologies.meetingmind.core.create

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CreateRenderingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val verse = ResolvedScripture("Psalm 23:1", "The Lord is my shepherd; I shall not want.", 3034, "BSB", "Berean Standard Bible")

    @Test fun `every format renders at its full size`() {
        CreateFormat.entries.forEach { f ->
            val b = CreateCardRenderer.render(context, CreateRenderInput("Be still.", verse, f), 1f)
            assertEquals(f.width, b.width); assertEquals(f.height, b.height)
        }
        assertEquals(1080 * 1350, CreateFormat.PORTRAIT.let { it.width * it.height })
        assertEquals(0.8f, CreateFormat.PORTRAIT.ratio, 0.001f)
        assertEquals(9f / 16f, CreateFormat.STORY.ratio, 0.001f)
    }

    private fun differs(a: Bitmap, b: Bitmap, y0: Int, y1: Int): Boolean {
        for (y in y0 until y1) for (x in 0 until a.width step 3) if (a.getPixel(x, y) != b.getPixel(x, y)) return true
        return false
    }

    @Test fun `story text keeps clear of the top and bottom bars`() {
        val design = CreateDesign(background = CreateBackground.Pack("slate"))
        val withText = CreateCardRenderer.render(context, CreateRenderInput("Be still, and know that I am God. ".repeat(4), verse, CreateFormat.STORY, design.copy(watermark = true)), 0.5f)
        val blank = CreateCardRenderer.render(context, CreateRenderInput("", null, CreateFormat.STORY, design), 0.5f)
        val h = withText.height
        assertFalse("top 13% must be clear", differs(withText, blank, 0, (h * 0.13f).toInt()))
        assertFalse("bottom 17% must be clear", differs(withText, blank, (h * 0.83f).toInt() + 2, h))
        assertTrue("the words are drawn", differs(withText, blank, (h * 0.3f).toInt(), (h * 0.7f).toInt()))
    }

    @Test fun `the watermark is only drawn when asked for`() {
        val base = CreateRenderInput("Be still.", null, CreateFormat.SQUARE, CreateDesign(background = CreateBackground.Pack("slate")))
        val off = CreateCardRenderer.render(context, base, 0.5f)
        val off2 = CreateCardRenderer.render(context, base, 0.5f)
        val on = CreateCardRenderer.render(context, base.copy(design = base.design.copy(watermark = true)), 0.5f)
        assertFalse(differs(off, off2, 0, off.height))
        assertTrue(differs(off, on, (off.height * 0.8f).toInt(), off.height))
    }

    @Test fun `a long passage is flagged instead of being cut`() {
        val long = verse.copy(text = "In the beginning was the Word, and the Word was with God. ".repeat(60))
        assertFalse(CreateCardRenderer.fits(context, CreateRenderInput("", long, CreateFormat.SQUARE)))
        assertTrue(CreateCardRenderer.fits(context, CreateRenderInput("Be still.", verse, CreateFormat.STORY)))
    }

    @Test fun `auto contrast picks white over a dark picture and dark over a light one`() {
        val dark = AutoContrast.decide(FloatArray(100) { 0.02f })
        assertFalse(dark.darkInk)
        val light = AutoContrast.decide(FloatArray(100) { 0.85f })
        assertTrue(light.darkInk)
    }

    @Test fun `auto contrast shades a busy mid-tone picture until the text passes 4_5 to 1`() {
        val mid = FloatArray(100) { 0.45f }
        val d = AutoContrast.decide(mid)
        val bg = if (d.darkInk) mid[0] + (1 - mid[0]) * d.scrim else mid[0] * (1 - d.scrim)
        val ratio = if (d.darkInk) (bg + 0.05f) / (0.008f + 0.05f) else (1.05f) / (bg + 0.05f)
        assertTrue("ratio $ratio", ratio >= 4.5f)
    }

    @Test fun `photo backgrounds get readable text whatever the photo`() {
        val file = java.io.File.createTempFile("bgphoto", ".png")
        listOf(android.graphics.Color.rgb(250, 250, 250), android.graphics.Color.rgb(5, 5, 5)).forEach { c ->
            val photo = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).also { it.eraseColor(c) }
            file.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val b = CreateCardRenderer.render(context, CreateRenderInput("Be still.", null, CreateFormat.SQUARE, CreateDesign(CreateBackground.Photo(file.path))), 0.4f)
            // Find the most extreme pixel: text is far from the shaded picture in luminance.
            var maxDelta = 0f
            val bgLum = AutoContrast.luminance(b.getPixel(2, 2))
            for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) maxDelta = maxOf(maxDelta, kotlin.math.abs(AutoContrast.luminance(b.getPixel(x, y)) - bgLum))
            assertTrue("contrast delta $maxDelta for $c", maxDelta > 0.3f)
        }
        file.delete()
    }
}
