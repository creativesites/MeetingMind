package com.example.core.scripture

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelloAoTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("helloao/$name")!!.readText()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `a chapter parses into verses, headings and timed narrations`() {
        val c = HelloAo.parseChapter(JSONObject(res("bsb_jhn3.json")))
        assertEquals(36, c.verses.size)
        assertTrue(c.verses[15].text.startsWith("For God so loved the world"))
        assertFalse("footnote markers are dropped", c.verses[2].text.contains("noteId"))
        assertEquals(ChapterHeading(1, "Jesus and Nicodemus"), c.headings.first())
        assertTrue(c.audio.isNotEmpty())
        // A single chapter links to its timings; the client fetches them (the file's "verses").
        val linked = c.audio.first { it.timingsLink != null }
        assertEquals("/api/BSB/JHN/3.${linked.narrator}.audioTimings.json", linked.timingsLink)
        val timed = linked.copy(timings = HelloAo.parseTimings("""{"verses":[${(1..36).joinToString(",") { (it * 5.0).toString() }}]}"""))
        assertTrue(timed.timings.size >= 35)
        assertNull(timed.verseAt(0))
        assertEquals(1, timed.verseAt(5_100))
        assertEquals(16, timed.verseAt((timed.timings[15] * 1000).toLong() + 10))
        assertEquals((timed.timings[15] * 1000).toLong(), timed.startOf(16))
        assertEquals(5_000L, timed.startOf(1))
    }

    @Test fun `psalms keep their lines, title and subtitle`() {
        val c = HelloAo.parseChapter(JSONObject(res("bsb_psa23.json")))
        assertEquals("The LORD is my shepherd;\nI shall not want.", c.verses[0].text)
        assertTrue(c.verses[0].poetry)
        assertEquals("The LORD Is My Shepherd\nA Psalm of David.", c.headings.single { it.beforeVerse == 1 }.text)
    }

    @Test fun `cross-references and commentary`() {
        val refs = HelloAo.parseCrossRefs(res("xref_jhn3.json"))
        val v16 = refs.filter { it.fromVerse == 16 }
        assertTrue(v16.isNotEmpty())
        assertTrue(v16.all { it.to.book.usfm.length == 3 && it.score >= 0 })
        val notes = HelloAo.parseCommentary(res("jfb_jhn3.json"))
        assertTrue(notes.isNotEmpty() && notes.all { it.text.isNotBlank() })
    }

    @Test fun `a whole translation streams book by book`() {
        val chapters = mutableListOf<Pair<String, Int>>()
        var books = 0
        HelloAo.streamComplete(res("bsb_complete_small.json").byteInputStream(), { b, n, ch -> assertTrue(ch.verses.isNotEmpty()); chapters += b.usfm to n }, { books = it })
        assertEquals(listOf("GEN" to 1, "GEN" to 2, "EXO" to 1, "EXO" to 2), chapters)
        // The complete file carries timings inline.
        val first = mutableListOf<HelloAoChapter>()
        HelloAo.streamComplete(res("bsb_complete_small.json").byteInputStream(), { _, _, ch -> first += ch }, {})
        assertTrue(first.first().audio.first().timings.size >= 30)
        assertEquals(2, books)
    }

    @Test fun `ids are negative, stable and distinct`() {
        assertEquals(HelloAo.intId("BSB"), HelloAo.intId("BSB"))
        assertTrue(HelloAo.intId("BSB") < 0 && HelloAo.isHelloAo(HelloAo.intId("eng_kjv")))
        assertTrue(HelloAo.intId("BSB") != HelloAo.intId("eng_kjv"))
        val cat = HelloAo.parseCatalog("""{"translations":[{"id":"BSB","shortName":"BSB","name":"Berean Standard Bible","englishName":"Berean Standard Bible","language":"eng","languageEnglishName":"English","licenseUrl":"https://x","numberOfBooks":66,"totalNumberOfChapters":1189}]}""")
        assertEquals("BSB", cat.single().info().abbreviation)
        assertTrue(cat.single().info().offlineAllowed)
    }

    @Test fun `upgrading the store keeps downloaded chapters and adds the new tables`() {
        val name = "bible_v1_test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE bibles (id INTEGER PRIMARY KEY, abbreviation TEXT NOT NULL, title TEXT NOT NULL, attribution TEXT NOT NULL, complete INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE chapters (bible_id INTEGER NOT NULL, book TEXT NOT NULL, chapter INTEGER NOT NULL, PRIMARY KEY (bible_id, book, chapter))")
            db.execSQL("CREATE TABLE verses (id INTEGER PRIMARY KEY AUTOINCREMENT, bible_id INTEGER NOT NULL, book TEXT NOT NULL, book_index INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL, paragraph INTEGER NOT NULL, poetry INTEGER NOT NULL)")
            db.execSQL("CREATE VIRTUAL TABLE verses_fts USING fts4(text, tokenize=unicode61)")
            db.execSQL("INSERT INTO bibles VALUES (3034, 'BSB', 'Berean', 'BSB attribution', 1)")
            db.execSQL("INSERT INTO chapters VALUES (3034, 'JHN', 3)")
            db.execSQL("INSERT INTO verses (bible_id, book, book_index, chapter, verse, text, paragraph, poetry) VALUES (3034, 'JHN', 42, 3, 16, 'For God so loved the world', 1, 0)")
            db.version = 1
        }
        val store = BibleStore(context, name)
        val john = BibleBooks.byUsfm("JHN")!!
        val info = store.info(3034)!!
        assertEquals("For God so loved the world", store.chapter(info, john, 3)!!.verses.single().text)
        assertTrue(store.isComplete(3034))
        store.saveExtras(3034, john, 3, listOf(ChapterHeading(1, "Nicodemus")), listOf(ChapterAudio("david", "https://a/b.mp3", listOf(1.5, 3.0))))
        assertEquals("Nicodemus", store.headings(3034, john, 3).single().text)
        assertEquals(listOf(1.5, 3.0), store.audio(3034, john, 3).single().timings)
        store.setHighlight(john, 3, 16..17, "yellow")
        assertEquals(mapOf(16 to "yellow", 17 to "yellow"), store.highlights(john, 3))
        store.setHighlight(john, 3, 17..17, null)
        assertEquals(setOf(16), store.highlights(john, 3).keys)
        assertNull(store.crossRefs(john, 3))
        store.saveCrossRefs(john, 3, listOf(CrossRef(16, ScriptureReference(BibleBooks.byUsfm("ROM")!!, 5, 8), 40)))
        assertEquals("Romans 5:8", store.crossRefs(john, 3)!!.single().to.display())
        store.saveCommentary("matthew-henry", john, 3, listOf(CommentaryEntry(1, "Nicodemus was a Pharisee.")))
        assertEquals(1, store.commentary("matthew-henry", john, 3)!!.size)
        assertNull(store.commentary("john-gill", john, 3))
        store.close()
    }

    @Test fun `a whole book saves in one go with its extras, and the source is remembered`() {
        val store = BibleStore(context, null)
        val t = HelloAoTranslation("BSB", "BSB", "Berean Standard Bible", "Berean Standard Bible", "eng", "English", null, 66, 1189)
        val gen = BibleBooks.byUsfm("GEN")!!
        val chapter = ChapterContent(t.intId, "BSB", t.attribution, gen, 1, listOf(ChapterVerse(1, "In the beginning God created the heavens and the earth.", true, false)), true)
        store.saveBook(t.info(), "helloao:BSB", listOf(chapter to (listOf(ChapterHeading(1, "The Creation")) to emptyList())))
        assertEquals("helloao:BSB", store.source(t.intId))
        assertNotNull(store.chapter(t.info(), gen, 1))
        assertEquals("The Creation", store.headings(t.intId, gen, 1).single().text)
        assertEquals(1, store.search(t.intId, "beginning").size)
        store.close()
    }
}
