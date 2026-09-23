package com.example.core.scripture

import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BibleStoreTest {

    private lateinit var store: BibleStore
    private val john = BibleBooks.byUsfm("JHN")!!
    private val lam = BibleBooks.byUsfm("LAM")!!
    private val bsb = BibleInfo(3034, "BSB", "Berean Standard Bible", "Public Domain", emptyList(), offlineAllowed = true)
    private val niv = BibleInfo(111, "NIV", "New International Version", "© Biblica", emptyList(), offlineAllowed = false)

    private fun content(info: BibleInfo, book: BibleBook, chapter: Int, vararg verses: String) = ChapterContent(
        info.id, info.abbreviation, info.attribution, book, chapter,
        verses.mapIndexed { i, t -> ChapterVerse(i + 1, t, paragraph = i == 0, poetry = false) }, fromDevice = false
    )

    @Before fun setUp() { store = BibleStore(ApplicationProvider.getApplicationContext(), null) }
    @After fun tearDown() { store.close() }

    @Test
    fun `a stored chapter reads back verse for verse`() {
        assertTrue(store.saveChapter(bsb, content(bsb, john, 3, "Now there was a man", "He came to Jesus at night")))
        val back = store.chapter(bsb, john, 3)!!
        assertEquals(listOf(1, 2), back.verses.map { it.number })
        assertTrue(back.fromDevice)
        assertTrue(back.verses[0].paragraph)
        assertEquals(1, store.storedChapterCount(3034))
        assertNull(store.chapter(bsb, john, 4))
    }

    @Test
    fun `copyrighted translations are never stored`() {
        assertFalse(store.saveChapter(niv, content(niv, john, 3, "For God so loved the world")))
        assertFalse(store.hasChapter(111, john, 3))
    }

    @Test
    fun `search finds words, prefixes and phrases, in Bible order`() {
        store.saveChapter(bsb, content(bsb, john, 3, "For God so loved the world", "whoever believes in Him"))
        store.saveChapter(bsb, content(bsb, lam, 3, "Because of the loving devotion of the LORD", "Great is Your faithfulness"))

        assertEquals(listOf("Lamentations 3:2"), store.search(3034, "faithful").map { it.reference.display() })
        // "lov" matches loved and loving; Lamentations comes before John.
        assertEquals(listOf("Lamentations 3:1", "John 3:1"), store.search(3034, "lov").map { it.reference.display() })
        assertEquals(listOf("John 3:1"), store.search(3034, "\"so loved\"").map { it.reference.display() })
        assertTrue(store.search(3034, "loved faithfulness").isEmpty()) // every word must match
        assertTrue(store.search(9999, "loved").isEmpty())
    }

    @Test
    fun `replacing a chapter doesn't leave stale search entries`() {
        store.saveChapter(bsb, content(bsb, john, 3, "old wording"))
        store.saveChapter(bsb, content(bsb, john, 3, "new wording"))
        assertTrue(store.search(3034, "old").isEmpty())
        assertEquals(1, store.search(3034, "new").size)
    }

    @Test
    fun `removing a translation removes its text and index`() {
        store.saveChapter(bsb, content(bsb, john, 3, "For God so loved the world"))
        store.markComplete(3034, true)
        assertTrue(store.isComplete(3034))
        assertEquals(listOf("BSB"), store.offlineBibles().map { it.abbreviation })
        store.delete(3034)
        assertTrue(store.offlineBibles().isEmpty())
        assertTrue(store.search(3034, "loved").isEmpty())
        assertFalse(store.hasChapter(3034, john, 3))
    }

    @Test
    fun `search input is sanitised`() {
        assertEquals("grace* peace*", BibleStore.ftsQuery("Grace, peace!"))
        assertEquals("\"fear not\" love*", BibleStore.ftsQuery("\"fear not\" love"))
        assertNull(BibleStore.ftsQuery("  -- ** "))
        assertEquals("god* s*", BibleStore.ftsQuery("God's")) // apostrophes split words, never break the query
    }

    // ---------------------------------------------------------------- library

    private class FakeRemote(private val info: List<BibleInfo>) : BibleProvider {
        var chapterCalls = 0
        var passageCalls = 0
        var online = true
        override val isConfigured = true
        override suspend fun bibles() = if (online) info else emptyList()
        override suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult {
            chapterCalls++
            if (!online) return ChapterResult.Unavailable(PassageResult.Reason.OFFLINE, "offline")
            val i = info.first { it.id == bibleId }
            return ChapterResult.Found(ChapterContent(bibleId, i.abbreviation, i.attribution, book, chapter,
                listOf(ChapterVerse(1, "In the beginning was the Word", true, false), ChapterVerse(2, "He was with God", false, false)), false))
        }
        override suspend fun passage(reference: ScriptureReference, versionId: Int): PassageResult {
            passageCalls++
            return PassageResult.Unavailable(PassageResult.Reason.OFFLINE, "offline")
        }
        override suspend fun verseOfTheDay(dayOfYear: Int): ScriptureReference? = null
    }

    @Test
    fun `the library keeps open chapters and reads them back offline`() = runBlocking {
        val remote = FakeRemote(listOf(bsb, niv))
        val library = BibleLibrary(remote, store)

        library.chapter(3034, john, 1)
        library.chapter(111, john, 1)
        assertTrue(store.hasChapter(3034, john, 1))
        assertFalse(store.hasChapter(111, john, 1))

        remote.online = false
        val again = library.chapter(3034, john, 1) as ChapterResult.Found
        assertTrue(again.content.fromDevice)
        assertEquals(2, remote.chapterCalls) // served from the phone the second time

        val passage = library.passage(ScriptureReference(john, 1, 2), 3034) as PassageResult.Found
        assertEquals("He was with God", passage.passage.text)
        assertEquals(0, remote.passageCalls)

        // Offline, the translations on the phone are still offered.
        assertEquals(listOf(3034), library.bibles().map { it.id })
    }

    @Test
    fun `search is offered only for a complete download`() = runBlocking {
        val library = BibleLibrary(FakeRemote(listOf(bsb)), store)
        library.chapter(3034, john, 1)
        assertFalse(library.canSearch(3034))
        store.markComplete(3034, true)
        assertTrue(library.canSearch(3034))
        assertEquals(listOf("John 1:1"), library.search(3034, "beginning", 10).map { it.reference.display() })
    }
}
