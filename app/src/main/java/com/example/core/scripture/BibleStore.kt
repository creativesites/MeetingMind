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
        createV2(db, addColumns = true)
    }

    /**
     * Version 2 (PLAN_V2 F5): section headings, narrated audio, commentaries, cross-references and
     * highlights. Additive only — existing downloads (and highlights, from now on) survive.
     */
    private fun createV2(db: SQLiteDatabase, addColumns: Boolean) {
        if (addColumns) {
            db.execSQL("ALTER TABLE bibles ADD COLUMN source TEXT")
            db.execSQL("ALTER TABLE bibles ADD COLUMN language TEXT")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS headings (bible_id INTEGER NOT NULL, book TEXT NOT NULL, chapter INTEGER NOT NULL, before_verse INTEGER NOT NULL, text TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS headings_by_chapter ON headings (bible_id, book, chapter)")
        db.execSQL("CREATE TABLE IF NOT EXISTS audio (bible_id INTEGER NOT NULL, book TEXT NOT NULL, chapter INTEGER NOT NULL, narrator TEXT NOT NULL, url TEXT NOT NULL, timings TEXT NOT NULL, PRIMARY KEY (bible_id, book, chapter, narrator))")
        db.execSQL("CREATE TABLE IF NOT EXISTS commentary (source TEXT NOT NULL, book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS commentary_by_chapter ON commentary (source, book, chapter)")
        db.execSQL("CREATE TABLE IF NOT EXISTS commentary_chapters (source TEXT NOT NULL, book TEXT NOT NULL, chapter INTEGER NOT NULL, PRIMARY KEY (source, book, chapter))")
        db.execSQL("CREATE TABLE IF NOT EXISTS crossrefs (book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, to_book TEXT NOT NULL, to_chapter INTEGER NOT NULL, to_start INTEGER, to_end INTEGER, score INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS crossrefs_by_verse ON crossrefs (book, chapter, verse)")
        db.execSQL("CREATE TABLE IF NOT EXISTS crossref_chapters (book TEXT NOT NULL, chapter INTEGER NOT NULL, PRIMARY KEY (book, chapter))")
        db.execSQL("CREATE TABLE IF NOT EXISTS highlights (book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, color TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (book, chapter, verse))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createV2(db, addColumns = true)
    }

    // ---------------------------------------------------------------- v2: headings, audio

    fun saveExtras(bibleId: Int, book: BibleBook, chapter: Int, headings: List<ChapterHeading>, audio: List<ChapterAudio>) {
        val db = writableDatabase
        db.beginTransaction()
        try { writeExtras(db, bibleId, book, chapter, headings, audio); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }

    private fun writeExtras(db: SQLiteDatabase, bibleId: Int, book: BibleBook, chapter: Int, headings: List<ChapterHeading>, audio: List<ChapterAudio>) {
        val args = arrayOf(bibleId.toString(), book.usfm, chapter.toString())
        db.execSQL("DELETE FROM headings WHERE bible_id = ? AND book = ? AND chapter = ?", args)
        headings.forEach { h -> db.insert("headings", null, ContentValues().apply { put("bible_id", bibleId); put("book", book.usfm); put("chapter", chapter); put("before_verse", h.beforeVerse); put("text", h.text) }) }
        audio.forEach { a ->
            db.insertWithOnConflict("audio", null, ContentValues().apply {
                put("bible_id", bibleId); put("book", book.usfm); put("chapter", chapter); put("narrator", a.narrator); put("url", a.url)
                put("timings", a.timings.joinToString(","))
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun headings(bibleId: Int, book: BibleBook, chapter: Int): List<ChapterHeading> = readableDatabase.rawQuery(
        "SELECT before_verse, text FROM headings WHERE bible_id = ? AND book = ? AND chapter = ? ORDER BY before_verse", arrayOf(bibleId.toString(), book.usfm, chapter.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(ChapterHeading(c.getInt(0), c.getString(1))) } }

    fun audio(bibleId: Int, book: BibleBook, chapter: Int): List<ChapterAudio> = readableDatabase.rawQuery(
        "SELECT narrator, url, timings FROM audio WHERE bible_id = ? AND book = ? AND chapter = ?", arrayOf(bibleId.toString(), book.usfm, chapter.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(ChapterAudio(c.getString(0), c.getString(1), c.getString(2).split(',').mapNotNull { it.toDoubleOrNull() })) } }
        .sortedByDescending { it.timings.isNotEmpty() }

    /** Stores a whole book's chapters in one transaction — how a downloaded translation arrives. */
    fun saveBook(info: BibleInfo, source: String?, chapters: List<Pair<ChapterContent, Pair<List<ChapterHeading>, List<ChapterAudio>>>>) {
        if (!info.offlineAllowed) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertBible(db, info, source)
            for ((content, extras) in chapters) {
                if (content.verses.isEmpty()) continue
                writeChapter(db, info, content)
                writeExtras(db, info.id, content.book, content.chapter, extras.first, extras.second)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun upsertBible(db: SQLiteDatabase, info: BibleInfo, source: String?) {
        db.insertWithOnConflict("bibles", null, ContentValues().apply {
            put("id", info.id); put("abbreviation", info.abbreviation); put("title", info.title); put("attribution", info.attribution)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        if (source != null) db.execSQL("UPDATE bibles SET source = ? WHERE id = ?", arrayOf(source, info.id))
    }

    /** The external id a stored translation came from ("helloao:BSB"), when known. */
    fun source(bibleId: Int): String? = readableDatabase.rawQuery("SELECT source FROM bibles WHERE id = ?", arrayOf(bibleId.toString()))
        .use { if (it.moveToFirst()) it.getString(0) else null }

    // ---------------------------------------------------------------- v2: commentary, cross-references

    fun commentary(source: String, book: BibleBook, chapter: Int): List<CommentaryEntry>? {
        val db = readableDatabase
        val has = db.rawQuery("SELECT 1 FROM commentary_chapters WHERE source = ? AND book = ? AND chapter = ?", arrayOf(source, book.usfm, chapter.toString())).use { it.moveToFirst() }
        if (!has) return null
        return db.rawQuery("SELECT verse, text FROM commentary WHERE source = ? AND book = ? AND chapter = ? ORDER BY verse", arrayOf(source, book.usfm, chapter.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(CommentaryEntry(c.getInt(0), c.getString(1))) } }
    }

    fun saveCommentary(source: String, book: BibleBook, chapter: Int, entries: List<CommentaryEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM commentary WHERE source = ? AND book = ? AND chapter = ?", arrayOf(source, book.usfm, chapter.toString()))
            entries.forEach { e -> db.insert("commentary", null, ContentValues().apply { put("source", source); put("book", book.usfm); put("chapter", chapter); put("verse", e.verse); put("text", e.text) }) }
            db.insertWithOnConflict("commentary_chapters", null, ContentValues().apply { put("source", source); put("book", book.usfm); put("chapter", chapter) }, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun crossRefs(book: BibleBook, chapter: Int): List<CrossRef>? {
        val db = readableDatabase
        val has = db.rawQuery("SELECT 1 FROM crossref_chapters WHERE book = ? AND chapter = ?", arrayOf(book.usfm, chapter.toString())).use { it.moveToFirst() }
        if (!has) return null
        return db.rawQuery("SELECT verse, to_book, to_chapter, to_start, to_end, score FROM crossrefs WHERE book = ? AND chapter = ? ORDER BY verse, score DESC", arrayOf(book.usfm, chapter.toString()))
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val b = BibleBooks.byUsfm(c.getString(1)) ?: continue
                        add(CrossRef(c.getInt(0), ScriptureReference(b, c.getInt(2), if (c.isNull(3)) null else c.getInt(3), if (c.isNull(4)) null else c.getInt(4)), c.getInt(5)))
                    }
                }
            }
    }

    fun saveCrossRefs(book: BibleBook, chapter: Int, refs: List<CrossRef>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM crossrefs WHERE book = ? AND chapter = ?", arrayOf(book.usfm, chapter.toString()))
            refs.forEach { r ->
                db.insert("crossrefs", null, ContentValues().apply {
                    put("book", book.usfm); put("chapter", chapter); put("verse", r.fromVerse); put("to_book", r.to.usfm); put("to_chapter", r.to.chapter)
                    r.to.verseStart?.let { put("to_start", it) }; r.to.verseEnd?.let { put("to_end", it) }; put("score", r.score)
                })
            }
            db.insertWithOnConflict("crossref_chapters", null, ContentValues().apply { put("book", book.usfm); put("chapter", chapter) }, SQLiteDatabase.CONFLICT_IGNORE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // ---------------------------------------------------------------- v2: highlights (the person's own)

    fun highlights(book: BibleBook, chapter: Int): Map<Int, String> = readableDatabase.rawQuery(
        "SELECT verse, color FROM highlights WHERE book = ? AND chapter = ?", arrayOf(book.usfm, chapter.toString())
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getInt(0), c.getString(1)) } }

    fun setHighlight(book: BibleBook, chapter: Int, verses: IntRange, color: String?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (v in verses) {
                if (color == null) db.execSQL("DELETE FROM highlights WHERE book = ? AND chapter = ? AND verse = ?", arrayOf(book.usfm, chapter.toString(), v.toString()))
                else db.insertWithOnConflict("highlights", null, ContentValues().apply { put("book", book.usfm); put("chapter", chapter); put("verse", v); put("color", color); put("created_at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Every highlight, newest first — for the Scripture page. */
    fun allHighlights(limit: Int = 500): List<Triple<ScriptureReference, String, Long>> = readableDatabase.rawQuery(
        "SELECT book, chapter, verse, color, created_at FROM highlights ORDER BY created_at DESC LIMIT ?", arrayOf(limit.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val b = BibleBooks.byUsfm(c.getString(0)) ?: continue
                add(Triple(ScriptureReference(b, c.getInt(1), c.getInt(2)), c.getString(3), c.getLong(4)))
            }
        }
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
    fun saveChapter(info: BibleInfo, content: ChapterContent, source: String? = null): Boolean {
        if (!info.offlineAllowed || content.verses.isEmpty()) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsertBible(db, info, source)
            writeChapter(db, info, content)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    private fun writeChapter(db: SQLiteDatabase, info: BibleInfo, content: ChapterContent) {
        run {
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
        }
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
            db.execSQL("DELETE FROM headings WHERE bible_id = ?", args)
            db.execSQL("DELETE FROM audio WHERE bible_id = ?", args)
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
        private const val VERSION = 2

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
