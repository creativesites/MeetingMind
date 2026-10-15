package com.craftflowtechnologies.meetingmind.core.scripture

/**
 * The full-Bible layer (docs/PLAN_V1.md §7): translations, whole chapters for reading, and search.
 *
 * The Faith features only ever talk to [BibleProvider]. Which service answers (the Free Use Bible API, or the phone) and whether the answer came from the phone or
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
        return abbreviation.uppercase() in OPEN_ABBREVIATIONS || (KJV_TREATED_AS_OPEN && abbreviation.equals("KJV", ignoreCase = true))
    }

    /**
     * Whether this translation's text may go to a cloud speech service and be kept as audio. Open text
     * only: licensed translations are for display on the phone, so Read to Me reads them with the
     * phone's own voice. Free Use Bible API translations (negative ids, see HelloAo.intId) are open.
     */
    fun openForCloud(bibleId: Int, abbreviation: String, attribution: String?): Boolean =
        bibleId < 0 || offlineAllowed(abbreviation, attribution)

    /**
     * The KJV is public domain in most places but is not on the open list above, and the owner has not
     * decided to add it. This one switch is where that decision goes.
     */
    const val KJV_TREATED_AS_OPEN = false
}
