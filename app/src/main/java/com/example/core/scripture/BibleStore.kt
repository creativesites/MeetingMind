package com.example.core.scripture

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A translation kept on the phone, and how much of it has arrived. */
data class OfflineBible(val id: Int, val abbreviation: String, val title: String, val attribution: String, val chapters: Int, val complete: Boolean)

/**
 * Bible text kept on the phone, for reading offline and for full-text search.
 *
 * Only translations whose licence allows copies are ever written here ([BibleLicensing]); the
 * caller checks, and [saveChapter] refuses anything else as a second line of defence. It is its
 * own database file, apart from the notes database, so removing a translation is one delete and
 * never touches the person's notes.
 *
 * Search uses SQLite's FTS4 index over verse text, which ships with every Android phone.
 */
class BibleStore(context: Context, name: String? = FILE_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bibles (id INTEGER PRIMARY KEY, abbreviation TEXT NOT NULL, title TEXT NOT NULL, attribution TEXT NOT NULL, complete INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE chapters (bible_id INTEGER NOT NULL, book TEXT NOT NULL, chapter INTEGER NOT NULL, PRIMARY KEY (bible_id, book, chapter))")
        db.execSQL(
            "CREATE TABLE verses (id INTEGER PRIMARY KEY AUTOINCREMENT, bible_id INTEGER NOT NULL, book TEXT NOT NULL, book_index INTEGER NOT NULL, " +
                "chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL, paragraph INTEGER NOT NULL, poetry INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX verses_by_chapter ON verses (bible_id, book, chapter, verse)")
        db.execSQL("CREATE VIRTUAL TABLE verses_fts USING fts4(text, tokenize=unicode61)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Everything here can be downloaded again, so a new layout simply starts over.
        listOf("verses_fts", "verses", "chapters", "bibles").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    fun chapter(info: BibleInfo, book: BibleBook, chapter: Int): ChapterContent? {
        val db = readableDatabase
        val present = db.rawQuery(
            "SELECT 1 FROM chapters WHERE bible_id = ? AND book = ? AND chapter = ?",
            arrayOf(info.id.toString(), book.usfm, chapter.toString())
        ).use { it.moveToFirst() }
        if (!present) return null
        val verses = db.rawQuery(
            "SELECT verse, text, paragraph, poetry FROM verses WHERE bible_id = ? AND book = ? AND chapter = ? ORDER BY verse",
            arrayOf(info.id.toString(), book.usfm, chapter.toString())
        ).use { c ->
            buildList { while (c.moveToNext()) add(ChapterVerse(c.getInt(0), c.getString(1), c.getInt(2) == 1, c.getInt(3) == 1)) }
        }
        return ChapterContent(info.id, info.abbreviation, info.attribution, book, chapter, verses, fromDevice = true)
    }

    /** The stored version's details, so a downloaded translation reads with no connection at all. */
    fun info(bibleId: Int): BibleInfo? = readableDatabase.rawQuery(
        "SELECT abbreviation, title, attribution FROM bibles WHERE id = ?", arrayOf(bibleId.toString())
    ).use { c -> if (c.moveToFirst()) BibleInfo(bibleId, c.getString(0), c.getString(1), c.getString(2), emptyList(), offlineAllowed = true) else null }

    fun hasChapter(bibleId: Int, book: BibleBook, chapter: Int): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM chapters WHERE bible_id = ? AND book = ? AND chapter = ?",
        arrayOf(bibleId.toString(), book.usfm, chapter.toString())
    ).use { it.moveToFirst() }

    /** Stores one chapter, replacing any earlier copy. Refuses translations that may not be kept. */
    fun saveChapter(info: BibleInfo, content: ChapterContent): Boolean {
        if (!info.offlineAllowed || content.verses.isEmpty()) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("bibles", null, ContentValues().apply {
                put("id", info.id); put("abbreviation", info.abbreviation); put("title", info.title); put("attribution", info.attribution)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            deleteChapter(db, info.id, content.book.usfm, content.chapter)
            val bookIndex = BibleBooks.all.indexOf(content.book)
            for (v in content.verses) {
                val rowId = db.insert("verses", null, ContentValues().apply {
                    put("bible_id", info.id); put("book", content.book.usfm); put("book_index", bookIndex)
                    put("chapter", content.chapter); put("verse", v.number); put("text", v.text)
                    put("paragraph", if (v.paragraph) 1 else 0); put("poetry", if (v.poetry) 1 else 0)
                })
                db.insert("verses_fts", null, ContentValues().apply { put("docid", rowId); put("text", v.text) })
            }
            db.insert("chapters", null, ContentValues().apply { put("bible_id", info.id); put("book", content.book.usfm); put("chapter", content.chapter) })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    private fun deleteChapter(db: SQLiteDatabase, bibleId: Int, book: String, chapter: Int) {
        val args = arrayOf(bibleId.toString(), book, chapter.toString())
        db.execSQL("DELETE FROM verses_fts WHERE docid IN (SELECT id FROM verses WHERE bible_id = ? AND book = ? AND chapter = ?)", args)
        db.execSQL("DELETE FROM verses WHERE bible_id = ? AND book = ? AND chapter = ?", args)
        db.execSQL("DELETE FROM chapters WHERE bible_id = ? AND book = ? AND chapter = ?", args)
    }

    fun storedChapterCount(bibleId: Int): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM chapters WHERE bible_id = ?", arrayOf(bibleId.toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun markComplete(bibleId: Int, complete: Boolean) {
        writableDatabase.execSQL("UPDATE bibles SET complete = ? WHERE id = ?", arrayOf(if (complete) 1 else 0, bibleId))
    }

    fun isComplete(bibleId: Int): Boolean = readableDatabase.rawQuery(
        "SELECT complete FROM bibles WHERE id = ?", arrayOf(bibleId.toString())
    ).use { it.moveToFirst() && it.getInt(0) == 1 }

    fun offlineBibles(): List<OfflineBible> = readableDatabase.rawQuery(
        "SELECT b.id, b.abbreviation, b.title, b.attribution, b.complete, (SELECT COUNT(*) FROM chapters c WHERE c.bible_id = b.id) FROM bibles b ORDER BY b.abbreviation",
        null
    ).use { c ->
        buildList { while (c.moveToNext()) add(OfflineBible(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(5), c.getInt(4) == 1)) }
    }

    fun delete(bibleId: Int) {
        val db = writableDatabase
        val args = arrayOf(bibleId.toString())
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM verses_fts WHERE docid IN (SELECT id FROM verses WHERE bible_id = ?)", args)
            db.execSQL("DELETE FROM verses WHERE bible_id = ?", args)
            db.execSQL("DELETE FROM chapters WHERE bible_id = ?", args)
            db.execSQL("DELETE FROM bibles WHERE id = ?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Verses of [bibleId] containing every word of [query] (each word also matches as a prefix,
     * so "forgiv" finds "forgive" and "forgiveness"). A phrase in quotes must appear as written.
     * Results come in Bible order.
     */
    fun search(bibleId: Int, query: String, limit: Int = 200): List<SearchHit> {
        val match = ftsQuery(query) ?: return emptyList()
        return readableDatabase.rawQuery(
            "SELECT v.book, v.chapter, v.verse, v.text FROM verses_fts f JOIN verses v ON v.id = f.docid " +
                "WHERE verses_fts MATCH ? AND v.bible_id = ? ORDER BY v.book_index, v.chapter, v.verse LIMIT ?",
            arrayOf(match, bibleId.toString(), limit.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val book = BibleBooks.byUsfm(c.getString(0)) ?: continue
                    add(SearchHit(ScriptureReference(book, c.getInt(1), c.getInt(2)), c.getString(3)))
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "bible_offline.db"
        private const val VERSION = 1

        /** Builds an FTS query from what the person typed, keeping only letters, digits and quoted phrases. */
        fun ftsQuery(query: String): String? {
            val parts = mutableListOf<String>()
            Regex("\"([^\"]+)\"|([\\p{L}\\p{N}’']+)").findAll(query).forEach { m ->
                val phrase = m.groupValues[1]
                if (phrase.isNotBlank()) {
                    val words = Regex("[\\p{L}\\p{N}]+").findAll(phrase).map { it.value.lowercase() }.toList()
                    if (words.isNotEmpty()) parts += "\"" + words.joinToString(" ") + "\""
                } else {
                    Regex("[\\p{L}\\p{N}]+").findAll(m.groupValues[2]).forEach { parts += it.value.lowercase() + "*" }
                }
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

        @Volatile private var instance: BibleStore? = null
        fun get(context: Context): BibleStore = instance ?: synchronized(this) {
            instance ?: BibleStore(context).also { instance = it }
        }
    }
}
