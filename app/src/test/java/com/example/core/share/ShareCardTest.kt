package com.example.core.share

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShareCardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val verse = ShareCardContent(
        "Verse of the day", "Come to Me, all you who are weary and burdened, and I will give you rest.",
        "Matthew 11:28 · BSB", "The Holy Bible, Berean Standard Bible, BSB is produced in cooperation with Bible Hub."
    )

    @Test fun `cards render at every format's full size`() {
        ShareFormat.entries.forEach { f ->
            val b = ShareCardRenderer.render(context, verse, ShareStyle(format = f))
            assertEquals(f.width, b.width); assertEquals(f.height, b.height)
        }
        val half = ShareCardRenderer.render(context, verse, ShareStyle(), scale = 0.5f)
        assertEquals(540, half.width)
    }

    @Test fun `text is drawn - the card isn't just its background`() {
        val style = ShareStyle(background = BackgroundSpec.Solid(Color.BLACK), watermark = false)
        val withText = ShareCardRenderer.render(context, verse, style, scale = 0.25f)
        val empty = ShareCardRenderer.render(context, verse.copy(text = " ", eyebrow = null, reference = null, attribution = null, quoted = false), style, scale = 0.25f)
        val lit = (0 until withText.width).sumOf { x -> (0 until withText.height).count { y -> withText.getPixel(x, y) != Color.BLACK } }
        val litEmpty = (0 until empty.width).sumOf { x -> (0 until empty.height).count { y -> empty.getPixel(x, y) != Color.BLACK } }
        assertTrue("lit pixels $lit vs $litEmpty", lit > litEmpty + 200)
    }

    @Test fun `very long text still fits`() {
        val long = verse.copy(text = "Grace upon grace. ".repeat(120))
        val b = ShareCardRenderer.render(context, long, ShareStyle(format = ShareFormat.SQUARE), scale = 0.3f)
        assertEquals(324, b.width)
    }

    @Test fun `each day gets its own background, stable within the day`() {
        assertEquals(BackgroundPack.forDay(20_000, 1), BackgroundPack.forDay(20_000, 1))
        assertNotEquals(BackgroundPack.forDay(20_000, 1).id, BackgroundPack.forDay(20_001, 1).id)
        assertTrue(BackgroundPack.forDay(20_000, 3).dark)
        assertEquals(BackgroundPack.all.first(), BackgroundPack.byId("missing"))
        assertEquals(BackgroundPack.all.size, BackgroundPack.all.map { it.id }.toSet().size)
    }

    @Test fun `a photo background falls back to the pack when unreadable`() {
        val b = ShareCardRenderer.render(context, verse, ShareStyle(background = BackgroundSpec.Photo("/nope.jpg")), scale = 0.2f)
        assertEquals(216, b.width)
    }

    @Test fun `cards are saved as png for sharing, and a record of the look is kept`() {
        val bitmap = ShareCardRenderer.render(context, verse, ShareStyle(background = BackgroundSpec.Pack("night"), font = ShareFont.PLAYFAIR))
        val file = ShareActions.saveToCache(context, bitmap, "test_card")
        assertTrue(file.exists() && file.length() > 1000)
        assertEquals(1920, BitmapFactory.decodeFile(file.path).height)
        File("build/outputs/roborazzi").mkdirs()
        file.copyTo(File("build/outputs/roborazzi/share_card_story.png"), overwrite = true)
        val square = ShareCardRenderer.render(context, verse.copy(eyebrow = "Today's devotional"), ShareStyle(format = ShareFormat.SQUARE, background = BackgroundSpec.Pack("paper"), font = ShareFont.LORA_ITALIC, centered = false))
        ShareActions.saveToCache(context, square, "test_square").copyTo(File("build/outputs/roborazzi/share_card_square.png"), overwrite = true)
    }

    @Test fun `the image prompt forbids text and faces`() {
        val p = ImageBackgrounds.prompt("Rest for the weary, Matthew 11:28", ImageStyle.WATERCOLOUR)
        assertTrue(p.contains("No text") && p.contains("faces") && p.contains("watercolour"))
    }
}
