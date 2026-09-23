package com.example.core.scripture

/**
 * The full-Bible layer (docs/PLAN_V1.md §7): translations, whole chapters for reading, and search.
 *
 * The Faith features only ever talk to [BibleProvider]. Which service answers — YouVersion today,
 * API.Bible or a bundled public-domain text later — and whether the answer came from the phone or
 * the network is decided underneath it, in [BibleLibrary].
 */
interface BibleProvider : ScriptureProvider {
    /** The translations this app may show, as licensed to it. */
    suspend fun bibles(): List<BibleInfo>

    /** One whole chapter, verse by verse, for the reader. */
    suspend fun chapter(bibleId: Int, book: BibleBook, chapter: Int): ChapterResult

    /** Keyword search. Empty when [bibleId] can't be searched (see [canSearch]). */
    suspend fun search(bibleId: Int, query: String, limit: Int = 200): List<SearchHit> = emptyList()

    suspend fun canSearch(bibleId: Int): Boolean = false
}

/** A translation: what to call it, what must be printed with it, and which books it has. */
data class BibleInfo(
    val id: Int,
    val abbreviation: String,
    val title: String,
    /** Shown under any text from this version. Never blank. */
    val attribution: String,
    /** USFM codes of the books this version contains (some are New Testament only). */
    val books: List<String>,
    /** Whether the text may be kept on the phone for offline reading and search. */
    val offlineAllowed: Boolean
) {
    fun has(book: BibleBook) = books.isEmpty() || book.usfm in books
}

/** One verse of a chapter. [text] may hold line breaks (poetry). */
data class ChapterVerse(
    val number: Int,
    val text: String,
    /** The verse opens a new paragraph (or stanza). */
    val paragraph: Boolean,
    /** The verse is set as poetry, indented line by line. */
    val poetry: Boolean
)

data class ChapterContent(
    val bibleId: Int,
    val abbreviation: String,
    val attribution: String,
    val book: BibleBook,
    val chapter: Int,
    val verses: List<ChapterVerse>,
    val fromDevice: Boolean
) {
    /** Running text of [start]..[end] (the whole chapter when both are null). */
    fun text(start: Int? = null, end: Int? = null): String {
        val from = start ?: 1
        val to = end ?: start ?: Int.MAX_VALUE
        return verses.filter { it.number in from..to }.joinToString(" ") { it.text.replace('\n', ' ') }.trim()
    }
}

sealed interface ChapterResult {
    data class Found(val content: ChapterContent) : ChapterResult
    data class Unavailable(val reason: PassageResult.Reason, val message: String) : ChapterResult
}

data class SearchHit(val reference: ScriptureReference, val text: String)

/**
 * Which translations may be stored on the phone.
 *
 * Only text that is free to copy qualifies: public domain, or an open licence that allows copies.
 * Everything else (NIV, ESV, NLT…) is licensed for display on demand, so it is fetched each time
 * and held in memory only. A version counts as open when its own attribution says so, or when it
 * is one of the well-known open translations below.
 */
object BibleLicensing {
    private val OPEN_ABBREVIATIONS = setOf(
        "BSB", "ASV", "WEB", "WEBUS", "ENGWEBUS", "WEBBE", "WMB", "WMBBE", "CPDV", "GNV", "ENGGNV",
        "LSV", "FBV", "TCENT", "BBE", "YLT", "DRA", "OEB"
    )
    private val OPEN_WORDING = listOf("public domain", "creative commons", "cc by", "cc-by", "cc0")

    fun offlineAllowed(abbreviation: String, attribution: String?): Boolean {
        val text = attribution.orEmpty().lowercase()
        if (OPEN_WORDING.any { it in text }) return true
        return abbreviation.uppercase() in OPEN_ABBREVIATIONS
    }
}

/**
 * Turns a YouVersion chapter (`format=html`) into verses.
 *
 * The markup is a run of block `div`s — `p`/`m` paragraphs, `q1`/`q2` poetry lines, `d` psalm
 * titles — with `<span class="yv-v" v="N">` marking where each verse starts and a `yv-vlbl` label
 * holding the printed number. Anything before verse 1 (a psalm title) joins verse 1.
 */
object YouVersionChapterParser {
    private val BLOCK = Regex("""<div class="([^"]+)">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val VERSE_MARK = Regex("""<span class="yv-v" v="(\d+)"></span>(?:<span class="yv-vlbl">[^<]*</span>)?""")
    private val TAG = Regex("<[^>]+>")

    fun parse(html: String): List<ChapterVerse> {
        data class Building(val number: Int, val lines: MutableList<String>, val paragraph: Boolean, var poetry: Boolean)
        val verses = mutableListOf<Building>()
        var carried = StringBuilder() // text before the first verse marker

        for (block in BLOCK.findAll(html)) {
            val style = block.groupValues[1]
            if (style.startsWith("yv-")) continue
            val poetry = style.startsWith("q")
            val paragraphStyle = style == "p" || style == "m" || style == "pi" || style == "b" || style == "d"
            val body = block.groupValues[2]
            val marks = VERSE_MARK.findAll(body).toList()
            // Text in this block before its first verse marker continues the current verse.
            val leading = clean(body.substring(0, marks.firstOrNull()?.range?.first ?: body.length))
            if (leading.isNotEmpty()) {
                val current = verses.lastOrNull()
                if (current == null) carried.append(leading).append('\n')
                else { current.lines += leading; if (poetry) current.poetry = true }
            }
            for ((i, mark) in marks.withIndex()) {
                val number = mark.groupValues[1].toInt()
                val end = marks.getOrNull(i + 1)?.range?.first ?: body.length
                val text = clean(body.substring(mark.range.last + 1, end))
                val startsBlock = i == 0 && leading.isEmpty()
                val lines = mutableListOf<String>()
                if (verses.isEmpty() && carried.isNotEmpty()) { lines += carried.toString().trim(); carried = StringBuilder() }
                if (text.isNotEmpty()) lines += text
                verses += Building(number, lines, paragraph = startsBlock && (paragraphStyle || verses.isEmpty()), poetry = poetry)
            }
        }
        return verses.map { b ->
            val joined = if (b.poetry) b.lines.joinToString("\n") else b.lines.joinToString(" ")
            ChapterVerse(b.number, joined.replace(Regex("[ \\t]+"), " ").trim(), b.paragraph, b.poetry)
        }.filter { it.text.isNotEmpty() }
    }

    private fun clean(fragment: String): String = decode(fragment.replace(TAG, "")).replace(Regex("\\s+"), " ").trim()

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
}
