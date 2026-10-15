package com.craftflowtechnologies.meetingmind.core.create

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateGalleryTest {
    private lateinit var db: MeetMindDatabase
    private lateinit var gallery: CreateGallery
    private lateinit var dir: File

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "creations-test").apply { deleteRecursively() }
        gallery = CreateGallery(db.createCardDao(), dir)
    }

    @After fun tearDown() { db.close(); dir.deleteRecursively() }

    private fun card(text: String, at: Long = 1_000, pinned: Boolean = false) = CreateCard(
        source = CreateSourceKind.VERSE, sourceText = "grace", sourceRef = "John 3:16", vibe = CreateVibe.REFLECTIVE,
        current = CardVersion(text, "John 3:16", "Written"), past = listOf(CardVersion("Earlier.", null, "Original")),
        scriptureVersionId = 3034, format = CreateFormat.PORTRAIT,
        design = CreateDesign(CreateBackground.Photo("/tmp/p.jpg"), CreateFontPair.EDITORIAL, centered = false, includeCompanion = true, watermark = true, textScale = 1.2f),
        pinned = pinned, createdAt = at, updatedAt = at
    )

    @Test fun `a saved card comes back whole`() = runBlocking {
        val c = card("Be still.")
        assertTrue(gallery.save(c))
        val back = gallery.get(c.id)!!
        assertEquals("Be still.", back.text)
        assertEquals("John 3:16", back.scriptureRef)
        assertEquals(3034, back.scriptureVersionId)
        assertEquals(CreateFormat.PORTRAIT, back.format)
        assertEquals(CreateVibe.REFLECTIVE, back.vibe)
        assertEquals(CreateSourceKind.VERSE, back.source)
        assertEquals(c.design, back.design)
        assertEquals(listOf("Earlier."), back.past.map { it.text })
        assertTrue(back.canUndo)
    }

    @Test fun `scripture text is never stored, only the reference`() = runBlocking {
        val c = card("Be still.")
        gallery.save(c)
        val row = db.createCardDao().get(c.id)!!
        assertTrue(row.toString().contains("John 3:16"))
        assertFalse(row.toString().contains("For God so loved"))
    }

    @Test fun `a card made today has the safe defaults`() {
        val d = CreateDesign()
        assertFalse("watermark is opt-in", d.watermark)
        assertFalse("companion is opt-in", d.includeCompanion)
        assertEquals(CreateFormat.STORY, CreateCard().format)
    }

    @Test fun `a blank card is not worth keeping`() = runBlocking {
        assertFalse(gallery.save(CreateCard()))
        assertEquals(0, gallery.count.first())
    }

    @Test fun `pinned first then newest`() = runBlocking {
        val old = card("old", at = 1)
        val new = card("new", at = 3)
        val pinned = card("pinned", at = 2)
        listOf(old, new, pinned).forEach { gallery.save(it) }
        gallery.setPinned(pinned.id, true)
        assertEquals(listOf("pinned", "new", "old"), gallery.all.first().map { it.text })
    }

    @Test fun `saving again updates the same card`() = runBlocking {
        val c = card("one")
        gallery.save(c)
        gallery.save(c.withVersion(CardVersion("two", "John 3:16", "Edited")))
        assertEquals(1, gallery.count.first())
        assertEquals("two", gallery.get(c.id)!!.text)
    }

    @Test fun `delete removes the row and the thumbnail`() = runBlocking {
        val c = card("gone")
        gallery.save(c)
        val bmp = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        gallery.saveThumb(c.id, bmp)
        assertTrue(gallery.thumbFile(c.id)!!.exists())
        gallery.delete(c.id)
        assertNull(gallery.get(c.id))
        assertFalse(gallery.thumbFile(c.id)!!.exists())
    }

    @Test fun `a damaged design or version column still opens`() {
        assertEquals(CreateDesign(), CreateCardCodec.designFromJson("not json"))
        assertEquals(emptyList<CardVersion>(), CreateCardCodec.versionsFromJson("{"))
        val e = CreateCardCodec.toEntity(card("x")).copy(vibe = "NOPE", format = "NOPE", source = "NOPE")
        val c = CreateCardCodec.fromEntity(e)
        assertEquals(CreateFormat.STORY, c.format)
        assertEquals(CreateVibe.PEACEFUL, c.vibe)
    }
}
