package com.example.core.scripture

/**
 * The 66 books of the Protestant canon: USFM code, display name, the names people say and
 * write for them, and how many verses each chapter has.
 *
 * Verse counts are the larger of the KJV count and the count modern translations use where the
 * two differ (e.g. 3 John has 14 verses in the KJV and 15 in the NIV/ESV), so a reference valid in
 * any common translation is accepted. Derived from two independent public-domain KJV datasets
 * (31,102 verses in 1,189 chapters) and checked against each other chapter by chapter.
 */
data class BibleBook(
    val usfm: String,
    val name: String,
    /** 1, 2 or 3 for numbered books ("1 Corinthians"); null otherwise. */
    val number: Int?,
    /** The name without its number: "Corinthians", "Kings". */
    val baseName: String,
    /** Lower-case alternative names and abbreviations for [baseName]. */
    val aliases: List<String>,
    val versesPerChapter: IntArray
) {
    val chapterCount: Int get() = versesPerChapter.size
    fun verseCount(chapter: Int): Int? = versesPerChapter.getOrNull(chapter - 1)
    val isNewTestament: Boolean get() = BibleBooks.all.indexOf(this) >= 39
}

object BibleBooks {
    val all: List<BibleBook> = listOf(
        BibleBook("GEN", "Genesis", null, "Genesis", listOf("gen", "gn"), intArrayOf(31, 25, 24, 26, 32, 22, 24, 22, 29, 32, 32, 20, 18, 24, 21, 16, 27, 33, 38, 18, 34, 24, 20, 67, 34, 35, 46, 22, 35, 43, 55, 32, 20, 31, 29, 43, 36, 30, 23, 23, 57, 38, 34, 34, 28, 34, 31, 22, 33, 26)),
        BibleBook("EXO", "Exodus", null, "Exodus", listOf("exod", "ex", "exo"), intArrayOf(22, 25, 22, 31, 23, 30, 25, 32, 35, 29, 10, 51, 22, 31, 27, 36, 16, 27, 25, 26, 36, 31, 33, 18, 40, 37, 21, 43, 46, 38, 18, 35, 23, 35, 35, 38, 29, 31, 43, 38)),
        BibleBook("LEV", "Leviticus", null, "Leviticus", listOf("lev", "lv"), intArrayOf(17, 16, 17, 35, 19, 30, 38, 36, 24, 20, 47, 8, 59, 57, 33, 34, 16, 30, 37, 27, 24, 33, 44, 23, 55, 46, 34)),
        BibleBook("NUM", "Numbers", null, "Numbers", listOf("num", "nm", "nb"), intArrayOf(54, 34, 51, 49, 31, 27, 89, 26, 23, 36, 35, 16, 33, 45, 41, 50, 13, 32, 22, 29, 35, 41, 30, 25, 18, 65, 23, 31, 40, 16, 54, 42, 56, 29, 34, 13)),
        BibleBook("DEU", "Deuteronomy", null, "Deuteronomy", listOf("deut", "dt", "deu"), intArrayOf(46, 37, 29, 49, 33, 25, 26, 20, 29, 22, 32, 32, 18, 29, 23, 22, 20, 22, 21, 20, 23, 30, 25, 22, 19, 19, 26, 68, 29, 20, 30, 52, 29, 12)),
        BibleBook("JOS", "Joshua", null, "Joshua", listOf("josh", "jos"), intArrayOf(18, 24, 17, 24, 15, 27, 26, 35, 27, 43, 23, 24, 33, 15, 63, 10, 18, 28, 51, 9, 45, 34, 16, 33)),
        BibleBook("JDG", "Judges", null, "Judges", listOf("judg", "jdg", "jg"), intArrayOf(36, 23, 31, 24, 31, 40, 25, 35, 57, 18, 40, 15, 25, 20, 20, 31, 13, 31, 30, 48, 25)),
        BibleBook("RUT", "Ruth", null, "Ruth", listOf("rth", "ru"), intArrayOf(22, 23, 18, 22)),
        BibleBook("1SA", "1 Samuel", 1, "Samuel", listOf("sam", "sa", "samuel"), intArrayOf(28, 36, 21, 22, 12, 21, 17, 22, 27, 27, 15, 25, 23, 52, 35, 23, 58, 30, 24, 43, 15, 23, 29, 22, 44, 25, 12, 25, 11, 31, 13)),
        BibleBook("2SA", "2 Samuel", 2, "Samuel", listOf("sam", "sa", "samuel"), intArrayOf(27, 32, 39, 12, 25, 23, 29, 18, 13, 19, 27, 31, 39, 33, 37, 23, 29, 33, 43, 26, 22, 51, 39, 25)),
        BibleBook("1KI", "1 Kings", 1, "Kings", listOf("kgs", "ki", "kings"), intArrayOf(53, 46, 28, 34, 18, 38, 51, 66, 28, 29, 43, 33, 34, 31, 34, 34, 24, 46, 21, 43, 29, 54)),
        BibleBook("2KI", "2 Kings", 2, "Kings", listOf("kgs", "ki", "kings"), intArrayOf(18, 25, 27, 44, 27, 33, 20, 29, 37, 36, 21, 21, 25, 29, 38, 20, 41, 37, 37, 21, 26, 20, 37, 20, 30)),
        BibleBook("1CH", "1 Chronicles", 1, "Chronicles", listOf("chron", "chr", "ch", "chronicles"), intArrayOf(54, 55, 24, 43, 26, 81, 40, 40, 44, 14, 47, 40, 14, 17, 29, 43, 27, 17, 19, 8, 30, 19, 32, 31, 31, 32, 34, 21, 30)),
        BibleBook("2CH", "2 Chronicles", 2, "Chronicles", listOf("chron", "chr", "ch", "chronicles"), intArrayOf(17, 18, 17, 22, 14, 42, 22, 18, 31, 19, 23, 16, 22, 15, 19, 14, 19, 34, 11, 37, 20, 12, 21, 27, 28, 23, 9, 27, 36, 27, 21, 33, 25, 33, 27, 23)),
        BibleBook("EZR", "Ezra", null, "Ezra", listOf("ezr"), intArrayOf(11, 70, 13, 24, 17, 22, 28, 36, 15, 44)),
        BibleBook("NEH", "Nehemiah", null, "Nehemiah", listOf("neh", "ne"), intArrayOf(11, 20, 32, 23, 19, 19, 73, 18, 38, 39, 36, 47, 31)),
        BibleBook("EST", "Esther", null, "Esther", listOf("esth", "est"), intArrayOf(22, 23, 15, 17, 14, 14, 10, 17, 32, 3)),
        BibleBook("JOB", "Job", null, "Job", listOf("jb"), intArrayOf(22, 13, 26, 21, 27, 30, 21, 22, 35, 22, 20, 25, 28, 22, 35, 22, 16, 21, 29, 29, 34, 30, 17, 25, 6, 14, 23, 28, 25, 31, 40, 22, 33, 37, 16, 33, 24, 41, 30, 24, 34, 17)),
        BibleBook("PSA", "Psalms", null, "Psalms", listOf("psalm", "ps", "psa", "pss", "psalms"), intArrayOf(6, 12, 8, 8, 12, 10, 17, 9, 20, 18, 7, 8, 6, 7, 5, 11, 15, 50, 14, 9, 13, 31, 6, 10, 22, 12, 14, 9, 11, 12, 24, 11, 22, 22, 28, 12, 40, 22, 13, 17, 13, 11, 5, 26, 17, 11, 9, 14, 20, 23, 19, 9, 6, 7, 23, 13, 11, 11, 17, 12, 8, 12, 11, 10, 13, 20, 7, 35, 36, 5, 24, 20, 28, 23, 10, 12, 20, 72, 13, 19, 16, 8, 18, 12, 13, 17, 7, 18, 52, 17, 16, 15, 5, 23, 11, 13, 12, 9, 9, 5, 8, 28, 22, 35, 45, 48, 43, 13, 31, 7, 10, 10, 9, 8, 18, 19, 2, 29, 176, 7, 8, 9, 4, 8, 5, 6, 5, 6, 8, 8, 3, 18, 3, 3, 21, 26, 9, 8, 24, 13, 10, 7, 12, 15, 21, 10, 20, 14, 9, 6)),
        BibleBook("PRO", "Proverbs", null, "Proverbs", listOf("prov", "pr", "prv", "proverb"), intArrayOf(33, 22, 35, 27, 23, 35, 27, 36, 18, 32, 31, 28, 25, 35, 33, 33, 28, 24, 29, 30, 31, 29, 35, 34, 28, 28, 27, 28, 27, 33, 31)),
        BibleBook("ECC", "Ecclesiastes", null, "Ecclesiastes", listOf("eccl", "ecc", "qoheleth", "eccles"), intArrayOf(18, 26, 22, 16, 20, 12, 29, 17, 18, 20, 10, 14)),
        BibleBook("SNG", "Song of Songs", null, "Song of Songs", listOf("song of solomon", "song", "sos", "canticles", "song of songs"), intArrayOf(17, 17, 11, 16, 16, 13, 13, 14)),
        BibleBook("ISA", "Isaiah", null, "Isaiah", listOf("isa", "is"), intArrayOf(31, 22, 26, 6, 30, 13, 25, 22, 21, 34, 16, 6, 22, 32, 9, 14, 14, 7, 25, 6, 17, 25, 18, 23, 12, 21, 13, 29, 24, 33, 9, 20, 24, 17, 10, 22, 38, 22, 8, 31, 29, 25, 28, 28, 25, 13, 15, 22, 26, 11, 23, 15, 12, 17, 13, 12, 21, 14, 21, 22, 11, 12, 19, 12, 25, 24)),
        BibleBook("JER", "Jeremiah", null, "Jeremiah", listOf("jer", "je"), intArrayOf(19, 37, 25, 31, 31, 30, 34, 22, 26, 25, 23, 17, 27, 22, 21, 21, 27, 23, 15, 18, 14, 30, 40, 10, 38, 24, 22, 17, 32, 24, 40, 44, 26, 22, 19, 32, 21, 28, 18, 16, 18, 22, 13, 30, 5, 28, 7, 47, 39, 46, 64, 34)),
        BibleBook("LAM", "Lamentations", null, "Lamentations", listOf("lam", "la"), intArrayOf(22, 22, 66, 22, 22)),
        BibleBook("EZK", "Ezekiel", null, "Ezekiel", listOf("ezek", "eze", "ezk"), intArrayOf(28, 10, 27, 17, 17, 14, 27, 18, 11, 22, 25, 28, 23, 23, 8, 63, 24, 32, 14, 49, 32, 31, 49, 27, 17, 21, 36, 26, 21, 26, 18, 32, 33, 31, 15, 38, 28, 23, 29, 49, 26, 20, 27, 31, 25, 24, 23, 35)),
        BibleBook("DAN", "Daniel", null, "Daniel", listOf("dan", "dn"), intArrayOf(21, 49, 30, 37, 31, 28, 28, 27, 27, 21, 45, 13)),
        BibleBook("HOS", "Hosea", null, "Hosea", listOf("hos", "ho"), intArrayOf(11, 23, 5, 19, 15, 11, 16, 14, 17, 15, 12, 14, 16, 9)),
        BibleBook("JOL", "Joel", null, "Joel", listOf("joel", "jl"), intArrayOf(20, 32, 21)),
        BibleBook("AMO", "Amos", null, "Amos", listOf("amos", "am"), intArrayOf(15, 16, 15, 13, 27, 14, 17, 14, 15)),
        BibleBook("OBA", "Obadiah", null, "Obadiah", listOf("obad", "ob"), intArrayOf(21)),
        BibleBook("JON", "Jonah", null, "Jonah", listOf("jonah", "jnh"), intArrayOf(17, 10, 10, 11)),
        BibleBook("MIC", "Micah", null, "Micah", listOf("mic", "mc"), intArrayOf(16, 13, 12, 13, 15, 16, 20)),
        BibleBook("NAM", "Nahum", null, "Nahum", listOf("nah", "na"), intArrayOf(15, 13, 19)),
        BibleBook("HAB", "Habakkuk", null, "Habakkuk", listOf("hab", "hb"), intArrayOf(17, 20, 19)),
        BibleBook("ZEP", "Zephaniah", null, "Zephaniah", listOf("zeph", "zep", "zp"), intArrayOf(18, 15, 20)),
        BibleBook("HAG", "Haggai", null, "Haggai", listOf("hag", "hg"), intArrayOf(15, 23)),
        BibleBook("ZEC", "Zechariah", null, "Zechariah", listOf("zech", "zec", "zc"), intArrayOf(21, 13, 10, 14, 11, 15, 14, 23, 17, 12, 17, 14, 9, 21)),
        BibleBook("MAL", "Malachi", null, "Malachi", listOf("mal", "ml"), intArrayOf(14, 17, 18, 6)),
        BibleBook("MAT", "Matthew", null, "Matthew", listOf("matt", "mt", "mat"), intArrayOf(25, 23, 17, 25, 48, 34, 29, 34, 38, 42, 30, 50, 58, 36, 39, 28, 27, 35, 30, 34, 46, 46, 39, 51, 46, 75, 66, 20)),
        BibleBook("MRK", "Mark", null, "Mark", listOf("mk", "mrk", "mar"), intArrayOf(45, 28, 35, 41, 43, 56, 37, 38, 50, 52, 33, 44, 37, 72, 47, 20)),
        BibleBook("LUK", "Luke", null, "Luke", listOf("lk", "luk"), intArrayOf(80, 52, 38, 44, 39, 49, 50, 56, 62, 42, 54, 59, 35, 35, 32, 31, 37, 43, 48, 47, 38, 71, 56, 53)),
        BibleBook("JHN", "John", null, "John", listOf("jn", "jhn", "joh"), intArrayOf(51, 25, 36, 54, 47, 71, 53, 59, 41, 42, 57, 50, 38, 31, 27, 33, 26, 40, 42, 31, 25)),
        BibleBook("ACT", "Acts", null, "Acts", listOf("acts", "ac", "act"), intArrayOf(26, 47, 26, 37, 42, 15, 60, 40, 43, 48, 30, 25, 52, 28, 41, 40, 34, 28, 41, 38, 40, 30, 35, 27, 27, 32, 44, 31)),
        BibleBook("ROM", "Romans", null, "Romans", listOf("rom", "ro", "rm"), intArrayOf(32, 29, 31, 25, 21, 23, 25, 39, 33, 21, 36, 21, 14, 23, 33, 27)),
        BibleBook("1CO", "1 Corinthians", 1, "Corinthians", listOf("cor", "co", "corinthians"), intArrayOf(31, 16, 23, 21, 13, 20, 40, 13, 27, 33, 34, 31, 13, 40, 58, 24)),
        BibleBook("2CO", "2 Corinthians", 2, "Corinthians", listOf("cor", "co", "corinthians"), intArrayOf(24, 17, 18, 18, 21, 18, 16, 24, 15, 18, 33, 21, 14)),
        BibleBook("GAL", "Galatians", null, "Galatians", listOf("gal", "ga"), intArrayOf(24, 21, 29, 31, 26, 18)),
        BibleBook("EPH", "Ephesians", null, "Ephesians", listOf("eph", "ephes"), intArrayOf(23, 22, 21, 32, 33, 24)),
        BibleBook("PHP", "Philippians", null, "Philippians", listOf("phil", "php", "pp"), intArrayOf(30, 30, 21, 23)),
        BibleBook("COL", "Colossians", null, "Colossians", listOf("col"), intArrayOf(29, 23, 25, 18)),
        BibleBook("1TH", "1 Thessalonians", 1, "Thessalonians", listOf("thess", "th", "thes", "thessalonians"), intArrayOf(10, 20, 13, 18, 28)),
        BibleBook("2TH", "2 Thessalonians", 2, "Thessalonians", listOf("thess", "th", "thes", "thessalonians"), intArrayOf(12, 17, 18)),
        BibleBook("1TI", "1 Timothy", 1, "Timothy", listOf("tim", "ti", "timothy"), intArrayOf(20, 15, 16, 16, 25, 21)),
        BibleBook("2TI", "2 Timothy", 2, "Timothy", listOf("tim", "ti", "timothy"), intArrayOf(18, 26, 17, 22)),
        BibleBook("TIT", "Titus", null, "Titus", listOf("tit"), intArrayOf(16, 15, 15)),
        BibleBook("PHM", "Philemon", null, "Philemon", listOf("philem", "phm", "phlm"), intArrayOf(25)),
        BibleBook("HEB", "Hebrews", null, "Hebrews", listOf("heb"), intArrayOf(14, 18, 19, 16, 14, 20, 28, 13, 28, 39, 40, 29, 25)),
        BibleBook("JAS", "James", null, "James", listOf("jas", "jm", "jms"), intArrayOf(27, 26, 18, 17, 20)),
        BibleBook("1PE", "1 Peter", 1, "Peter", listOf("pet", "pe", "pt", "peter"), intArrayOf(25, 25, 22, 19, 14)),
        BibleBook("2PE", "2 Peter", 2, "Peter", listOf("pet", "pe", "pt", "peter"), intArrayOf(21, 22, 18)),
        BibleBook("1JN", "1 John", 1, "John", listOf("jn", "jhn", "joh", "john"), intArrayOf(10, 29, 24, 21, 21)),
        BibleBook("2JN", "2 John", 2, "John", listOf("jn", "jhn", "joh", "john"), intArrayOf(13)),
        BibleBook("3JN", "3 John", 3, "John", listOf("jn", "jhn", "joh", "john"), intArrayOf(15)),
        BibleBook("JUD", "Jude", null, "Jude", listOf("jud", "jd"), intArrayOf(25)),
        BibleBook("REV", "Revelation", null, "Revelation", listOf("rev", "re", "rv", "revelations", "apocalypse"), intArrayOf(20, 29, 22, 11, 14, 17, 17, 13, 21, 11, 19, 18, 18, 20, 8, 21, 18, 24, 21, 15, 27, 21))
    )

    private val byUsfm = all.associateBy { it.usfm }

    fun byUsfm(code: String): BibleBook? = byUsfm[code.uppercase()]

    /** Every book name, for speech-recognition vocabulary hints. */
    val spokenNames: List<String> = all.map { it.name }.distinct()
}
