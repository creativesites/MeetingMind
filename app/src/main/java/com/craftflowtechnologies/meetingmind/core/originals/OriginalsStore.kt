package com.craftflowtechnologies.meetingmind.core.originals

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** The optional originals downloads. Each is a testament's words plus its lexicon, chosen and removed on their own. */
enum class OriginalsPack(val label: String, val language: String, val approxMb: Int, val files: List<String>, val lexicon: String, val hebrew: Boolean) {
    GREEK_NT(
        "Greek New Testament", "Greek", 35,
        listOf(
            "TAGNT Mat-Jhn - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt",
            "TAGNT Act-Rev - Translators Amalgamated Greek NT - STEPBible.org CC-BY.txt"
        ),
        "TBESG - Translators Brief lexicon of Extended Strongs for Greek - STEPBible.org CC BY.txt", false
    ),
    HEBREW_OT(
        "Hebrew Old Testament", "Hebrew", 75,
        listOf(
            "TAHOT Gen-Deu - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
            "TAHOT Jos-Est - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
            "TAHOT Job-Sng - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt",
            "TAHOT Isa-Mal - Translators Amalgamated Hebrew OT - STEPBible.org CC BY.txt"
        ),
        "TBESH - Translators Brief lexicon of Extended Strongs for Hebrew - STEPBible.org CC BY.txt", true
    );

    fun fileUrl(name: String) = BASE + (if (name.startsWith("TB")) "Lexicons/" else "Translators%20Amalgamated%20OT%2BNT/") + name.replace(" ", "%20").replace("+", "%2B")

    companion object {
        const val BASE = "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/master/"
        const val CREDIT = "Original-language data: STEPBible (Tyndale House, Cambridge), CC BY 4.0 — github.com/STEPBible/STEPBible-Data. " +
            "Grammar is decoded from its published codes; MeetingMind adds no interpretation of its own."
    }
}

/** Words and lexicon entries on the phone, in one small database of their own. */
class OriginalsStore(context: Context, name: String? = FILE_NAME) : SQLiteOpenHelper(context.applicationContext, name, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE words (book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, pos INTEGER NOT NULL, hebrew INTEGER NOT NULL, surface TEXT NOT NULL, translit TEXT NOT NULL, gloss TEXT NOT NULL, strongs TEXT NOT NULL, morph TEXT NOT NULL, lemma TEXT NOT NULL, PRIMARY KEY (book, chapter, verse, pos, hebrew))")
        db.execSQL("CREATE TABLE lexicon (strongs TEXT PRIMARY KEY, lemma TEXT NOT NULL, translit TEXT NOT NULL, pos TEXT NOT NULL, gloss TEXT NOT NULL, meaning TEXT NOT NULL)")
        db.execSQL("CREATE TABLE packs (id TEXT PRIMARY KEY, installed_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun installed(): Set<OriginalsPack> = readableDatabase.rawQuery("SELECT id FROM packs", null).use { c ->
        buildSet { while (c.moveToNext()) runCatching { OriginalsPack.valueOf(c.getString(0)) }.getOrNull()?.let(::add) }
    }

    fun markInstalled(pack: OriginalsPack) {
        writableDatabase.insertWithOnConflict("packs", null, ContentValues().apply { put("id", pack.name); put("installed_at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Words of one verse range, in reading order. */
    fun verses(book: String, chapter: Int, from: Int, to: Int): List<OriginalWord> =
        readableDatabase.rawQuery(
            "SELECT book, chapter, verse, pos, hebrew, surface, translit, gloss, strongs, morph, lemma FROM words WHERE book = ? AND chapter = ? AND verse BETWEEN ? AND ? ORDER BY verse, pos",
            arrayOf(book, chapter.toString(), from.toString(), to.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(OriginalWord(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4) == 1, c.getString(5), c.getString(6), c.getString(7), c.getString(8), c.getString(9), c.getString(10)))
            }
        }

    /** A lexicon entry for an extended Strong's number, falling back to the plain number ("H0430G" → "H0430"). */
    fun entry(strongs: String): LexiconEntry? {
        val key = strongs.trim().trim('{', '}')
        for (k in listOf(key, key.trimEnd { it.isLetter() && it != key.first() }, key.substringBefore('_'))) {
            readableDatabase.rawQuery("SELECT strongs, lemma, translit, pos, gloss, meaning FROM lexicon WHERE strongs = ? OR strongs LIKE ? ORDER BY length(strongs) LIMIT 1", arrayOf(k, "$k%")).use { c ->
                if (c.moveToFirst()) return LexiconEntry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))
            }
        }
        return null
    }

    fun saveWords(words: List<OriginalWord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement("INSERT OR REPLACE INTO words (book, chapter, verse, pos, hebrew, surface, translit, gloss, strongs, morph, lemma) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
            for (w in words) {
                st.clearBindings()
                st.bindString(1, w.book); st.bindLong(2, w.chapter.toLong()); st.bindLong(3, w.verse.toLong()); st.bindLong(4, w.position.toLong()); st.bindLong(5, if (w.hebrew) 1 else 0)
                st.bindString(6, w.surface); st.bindString(7, w.translit); st.bindString(8, w.gloss); st.bindString(9, w.strongs); st.bindString(10, w.morph); st.bindString(11, w.lemma)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun saveLexicon(entries: List<LexiconEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement("INSERT OR REPLACE INTO lexicon (strongs, lemma, translit, pos, gloss, meaning) VALUES (?,?,?,?,?,?)")
            for (e in entries) {
                st.clearBindings()
                st.bindString(1, e.strongs); st.bindString(2, e.lemma); st.bindString(3, e.translit); st.bindString(4, e.partOfSpeech); st.bindString(5, e.gloss); st.bindString(6, e.meaning)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun remove(pack: OriginalsPack) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("words", "hebrew = ?", arrayOf(if (pack.hebrew) "1" else "0"))
            db.delete("lexicon", "strongs LIKE ?", arrayOf(if (pack.hebrew) "H%" else "G%"))
            db.delete("packs", "id = ?", arrayOf(pack.name))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        db.execSQL("VACUUM")
    }

    companion object {
        const val FILE_NAME = "originals.db"
        @Volatile private var instance: OriginalsStore? = null
        fun get(context: Context): OriginalsStore = instance ?: synchronized(this) { instance ?: OriginalsStore(context).also { instance = it } }
    }
}
