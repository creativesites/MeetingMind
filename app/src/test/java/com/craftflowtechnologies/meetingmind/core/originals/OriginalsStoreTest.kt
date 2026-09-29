package com.craftflowtechnologies.meetingmind.core.originals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OriginalsStoreTest {
    private lateinit var store: OriginalsStore
    private fun res(name: String) = javaClass.classLoader!!.getResource("originals/$name")!!.readText()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("originals-test.db")
        store = OriginalsStore(ctx, "originals-test.db")
    }

    @After fun tearDown() = store.close()

    /** A downloader whose "network" is the excerpts: every file of a pack returns the same text. */
    private fun downloader(pack: OriginalsPack) = OriginalsDownloader(store) { url ->
        BufferedReader(StringReader(when {
            url.contains("TBES") -> res("lexicon_excerpt.txt")
            pack.hebrew -> res("tahot_excerpt.txt")
            else -> res("tagnt_excerpt.txt")
        }))
    }

    @Test fun installingGreekMakesAVerseAndItsDictionaryReadable() {
        assertTrue(store.installed().isEmpty())
        var last = ""
        downloader(OriginalsPack.GREEK_NT).install(OriginalsPack.GREEK_NT) { _, _, label -> last = label }
        assertEquals("Done", last)
        assertEquals(setOf(OriginalsPack.GREEK_NT), store.installed())
        val verse = store.verses("JHN", 15, 5, 5)
        assertTrue(verse.size >= 20)
        assertEquals(listOf("ἐγώ", "εἰμί"), listOf(verse[0].lemma, verse[1].lemma))
        val entry = store.entry("G0026")
        assertNotNull(entry)
        assertEquals("love", entry!!.gloss)
    }

    @Test fun anExtendedNumberFindsItsEntryAndAPlainOneFindsTheExtended() {
        downloader(OriginalsPack.HEBREW_OT).install(OriginalsPack.HEBREW_OT)
        assertEquals("God", store.entry("H0430G")?.gloss)
        assertEquals("God", store.entry("{H0430G}")?.gloss)
        assertNotNull(store.entry("H0430"))
        assertNull(store.entry("H9999"))
    }

    @Test fun rerunningReplacesRowsAndRemovingClearsOnlyThatTestament() {
        downloader(OriginalsPack.GREEK_NT).install(OriginalsPack.GREEK_NT)
        downloader(OriginalsPack.HEBREW_OT).install(OriginalsPack.HEBREW_OT)
        val before = store.verses("GEN", 1, 1, 1).size
        downloader(OriginalsPack.HEBREW_OT).install(OriginalsPack.HEBREW_OT)
        assertEquals(before, store.verses("GEN", 1, 1, 1).size)
        store.remove(OriginalsPack.HEBREW_OT)
        assertTrue(store.verses("GEN", 1, 1, 1).isEmpty())
        assertTrue(store.verses("JHN", 15, 5, 5).isNotEmpty())
        assertEquals(setOf(OriginalsPack.GREEK_NT), store.installed())
    }

    @Test fun packUrlsAreWellFormed() {
        OriginalsPack.entries.forEach { p ->
            (p.files + p.lexicon).forEach { f ->
                val u = p.fileUrl(f)
                assertTrue(u, u.startsWith("https://raw.githubusercontent.com/STEPBible/STEPBible-Data/master/") && !u.contains(" ") )
            }
        }
    }
}
