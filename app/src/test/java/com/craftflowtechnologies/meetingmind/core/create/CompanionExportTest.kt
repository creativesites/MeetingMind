package com.craftflowtechnologies.meetingmind.core.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Z-22: the export draws the companion bitmap exactly at the placement rect, for every format and anchor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CompanionExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `the companion lands on its placement rect at 9x16, 1x1 and 4x5`() {
        val px = CompanionPlacement.maxExportBitmapPx
        val companion = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        for (f in CreateFormat.entries) for (anchor in CompanionAnchor.entries) {
            val design = CreateDesign(background = CreateBackground.Pack("slate"), includeCompanion = true, companionAnchor = anchor, watermark = true)
            val text = "Be still, and know."
            val card = CreateCardRenderer.render(context, CreateRenderInput(text, null, f, design), 1f, companion)
            val r = CompanionPlacement.place(f, f.width.toFloat(), f.height.toFloat(), design.centered, anchor)
            assertEquals("$f $anchor", Color.MAGENTA, card.getPixel(((r.left + r.right) / 2).toInt(), ((r.top + r.bottom) / 2).toInt()))
            // Just outside the rect there is no companion.
            assertTrue("$f $anchor", card.getPixel((r.left - 4).toInt().coerceAtLeast(0), ((r.top + r.bottom) / 2).toInt()) != Color.MAGENTA)
            // Off: nothing is drawn there.
            val off = CreateCardRenderer.render(context, CreateRenderInput(text, null, f, design.copy(includeCompanion = false)), 1f, companion)
            assertTrue(off.getPixel(((r.left + r.right) / 2).toInt(), ((r.top + r.bottom) / 2).toInt()) != Color.MAGENTA)
        }
    }
}
