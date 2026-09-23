package com.example.core.scripture

/** A Bible reference: a whole chapter when [verseStart] is null. */
data class ScriptureReference(
    val book: BibleBook,
    val chapter: Int,
    val verseStart: Int? = null,
    val verseEnd: Int? = null
) {
    val usfm: String get() = book.usfm

    /** "John 3:16–18", "1 Corinthians 13", "Psalm 23:1". */
    fun display(): String {
        // One psalm is a "Psalm"; the book is "Psalms".
        val name = if (book.usfm == "PSA") "Psalm" else book.name
        return when {
            verseStart == null -> "$name $chapter"
            verseEnd != null && verseEnd != verseStart -> "$name $chapter:$verseStart–$verseEnd"
            else -> "$name $chapter:$verseStart"
        }
    }

    /** The YouVersion / USFM passage id: "JHN.3.16", "JHN.3.16-JHN.3.18", "PSA.23". */
    fun passageId(): String = when {
        verseStart == null -> "$usfm.$chapter"
        verseEnd != null && verseEnd != verseStart -> "$usfm.$chapter.$verseStart-$usfm.$chapter.$verseEnd"
        else -> "$usfm.$chapter.$verseStart"
    }
}

/** A reference found in text, with where it was found. */
data class ScriptureMatch(val reference: ScriptureReference, val start: Int, val end: Int, val text: String)

/**
 * Finds Bible references in text — written ("Rom 8:28", "1 Cor. 13:4-7", "Ps 23") and spoken as
 * speech recognition writes them ("first Corinthians thirteen verses four through seven", "John
 * chapter three verse sixteen", "Psalm twenty three").
 *
 * Deterministic, never a model (design spec §5.2): every match is a real reference that exists in
 * the Bible's versification ([BibleBooks]) — John 3:99 is rejected, not "corrected".
 *
 * Several book names are also people's names or ordinary words (John, Mark, Job, Acts, Numbers…).
 * "John said three things" must not become John 3, so for those books a match needs a verse, the
 * word "chapter", or "the book of".
 */
object ScriptureReferenceParser {

    fun findAll(text: String): List<ScriptureMatch> = findAll(text, lenient = false)

    private fun findAll(text: String, lenient: Boolean): List<ScriptureMatch> {
        val tokens = tokenize(text)
        val matches = mutableListOf<ScriptureMatch>()
        var i = 0
        while (i < tokens.size) {
            val m = matchAt(tokens, i, text, lenient)
            if (m != null) {
                matches += m.first
                i = m.second
            } else i++
        }
        return matches
    }

    /**
     * Parses text a person typed as a reference ("jn 3 16", "Acts 2"). Typed input is taken at its
     * word, so the name-or-book guard doesn't apply.
     */
    fun parse(text: String): ScriptureReference? = findAll(text, lenient = true).firstOrNull()?.reference

    // ------------------------------------------------------------------ tokens

    private enum class Kind { WORD, NUMBER, COLON, DASH, COMMA, OTHER }

    private data class Token(val kind: Kind, val text: String, val start: Int, val end: Int, val value: Int? = null)

    private val TOKEN_REGEX = Regex("""\d+(?:st|nd|rd|th)?|[A-Za-z]+|[:.–—\-,;]""")

    private fun tokenize(text: String): List<Token> {
        val raw = TOKEN_REGEX.findAll(text).map { m ->
            val t = m.value
            when {
                t[0].isDigit() -> Token(Kind.NUMBER, t, m.range.first, m.range.last + 1, t.takeWhile { it.isDigit() }.toIntOrNull())
                t == ":" -> Token(Kind.COLON, t, m.range.first, m.range.last + 1)
                // "3.16" is written for 3:16 in some traditions; a full stop after a book
                // abbreviation ("Rom.") is dropped by the name matcher.
                t == "." -> Token(Kind.COLON, t, m.range.first, m.range.last + 1)
                t == "-" || t == "–" || t == "—" -> Token(Kind.DASH, t, m.range.first, m.range.last + 1)
                t == "," || t == ";" -> Token(Kind.COMMA, t, m.range.first, m.range.last + 1)
                else -> Token(Kind.WORD, t.lowercase(), m.range.first, m.range.last + 1)
            }
        }.toList()
        return combineNumberWords(raw)
    }

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)

    /**
     * Turns spoken numbers into number tokens: "twenty three" → 23, "one hundred and nineteen" →
     * 119. Two numbers side by side stay two numbers — "three sixteen" is chapter 3, verse 16 —
     * because only a tens word absorbs a following unit.
     */
    private fun combineNumberWords(tokens: List<Token>): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.kind != Kind.WORD || (t.text !in UNITS && t.text !in TENS && t.text != "hundred" && t.text != "a")) {
                out += t; i++; continue
            }
            var j = i
            var value = 0
            var consumed = false
            // Optional hundreds: "one hundred", "a hundred".
            val lead = tokens[j].text
            if (j + 1 < tokens.size && tokens[j + 1].text == "hundred" && (lead in UNITS || lead == "a")) {
                value = (UNITS[lead] ?: 1) * 100
                j += 2
                consumed = true
                if (j < tokens.size && tokens[j].text == "and") j++
            } else if (lead == "a" || lead == "hundred") {
                out += t; i++; continue
            }
            if (j < tokens.size && tokens[j].text in TENS) {
                value += TENS.getValue(tokens[j].text); j++; consumed = true
                // "twenty-nine": a hyphen written straight between the two words.
                if (j + 1 < tokens.size && tokens[j].kind == Kind.DASH && tokens[j].start == tokens[j - 1].end &&
                    tokens[j + 1].text in UNITS && tokens[j + 1].start == tokens[j].end) j++
                if (j < tokens.size && tokens[j].text in UNITS && UNITS.getValue(tokens[j].text) in 1..9) {
                    value += UNITS.getValue(tokens[j].text); j++
                }
            } else if (j < tokens.size && tokens[j].text in UNITS) {
                value += UNITS.getValue(tokens[j].text); j++; consumed = true
            }
            if (!consumed) { out += t; i++; continue }
            out += Token(Kind.NUMBER, tokens.subList(i, j).joinToString(" ") { it.text }, t.start, tokens[j - 1].end, value)
            i = j
        }
        return out
    }

    // ------------------------------------------------------------------ books

    private val ORDINALS = mapOf(
        "1" to 1, "1st" to 1, "first" to 1, "i" to 1,
        "2" to 2, "2nd" to 2, "second" to 2, "ii" to 2,
        "3" to 3, "3rd" to 3, "third" to 3, "iii" to 3
    )

    /** Names that are also everyday words or first names. */
    private val AMBIGUOUS = setOf("JHN", "MRK", "LUK", "JAS", "JUD", "JOB", "ACT", "NUM", "JDG", "RUT", "AMO", "DAN", "TIT", "JOL", "EST", "JON", "MIC", "LAM", "REV", "EXO", "GEN", "PRO", "HAG", "MAL")

    private val SINGLE_CHAPTER = setOf("OBA", "PHM", "2JN", "3JN", "JUD")

    /** Unnumbered names and aliases → book, as token lists so multi-word names match. */
    private val plainNames: List<Pair<List<String>, BibleBook>> by lazy {
        BibleBooks.all.filter { it.number == null }.flatMap { book ->
            (listOf(book.name.lowercase()) + book.aliases).distinct().map { it.split(' ') to book }
        }.sortedByDescending { it.first.size }
    }

    /** Base names of numbered books ("corinthians", "cor") → the books by number. */
    private val numberedNames: Map<String, Map<Int, BibleBook>> by lazy {
        val map = mutableMapOf<String, MutableMap<Int, BibleBook>>()
        BibleBooks.all.filter { it.number != null }.forEach { book ->
            (listOf(book.baseName.lowercase()) + book.aliases).forEach { alias -> map.getOrPut(alias) { mutableMapOf() }[book.number!!] = book }
        }
        map
    }

    private data class BookHit(val book: BibleBook, val start: Int, val next: Int, val explicitBookOf: Boolean)

    private fun bookAt(tokens: List<Token>, i: Int): BookHit? {
        var start = i
        var explicit = false
        // "the book of Isaiah" makes an ambiguous name certain.
        if (tokens.getOrNull(i)?.text == "book" && tokens.getOrNull(i + 1)?.text == "of") { start = i + 2; explicit = true }

        val first = tokens.getOrNull(start) ?: return null
        // Numbered: "1 Corinthians", "first Cor", "II Timothy", "1Cor" is split by the tokenizer.
        val ordinal = when (first.kind) {
            Kind.NUMBER -> first.value?.takeIf { it in 1..3 && first.text.all { c -> c.isDigit() } || first.text.matches(Regex("[123](st|nd|rd)")) }
            Kind.WORD -> ORDINALS[first.text]
            else -> null
        }
        if (ordinal != null) {
            val nameToken = tokens.getOrNull(start + 1)
            if (nameToken?.kind == Kind.WORD) {
                val book = numberedNames[nameToken.text]?.get(ordinal)
                if (book != null) {
                    // "i" as a roman numeral only when written in capitals ("I Corinthians"), never
                    // the pronoun: checked by the caller through the original text.
                    var next = start + 2
                    if (tokens.getOrNull(next)?.let { it.kind == Kind.COLON && it.text == "." } == true) next++
                    return BookHit(book, start, next, explicit)
                }
            }
        }
        if (first.kind != Kind.WORD) return null
        for ((words, book) in plainNames) {
            if (start + words.size > tokens.size) continue
            if (words.indices.all { k -> tokens[start + k].kind == Kind.WORD && tokens[start + k].text == words[k] }) {
                var next = start + words.size
                // "Rom." — a full stop straight after an abbreviation.
                if (tokens.getOrNull(next)?.let { it.kind == Kind.COLON && it.text == "." } == true &&
                    tokens.getOrNull(next + 1)?.kind == Kind.NUMBER) next++
                return BookHit(book, start, next, explicit)
            }
        }
        return null
    }

    // ------------------------------------------------------------------ grammar

    private val CHAPTER_WORDS = setOf("chapter", "chapters", "chap", "ch")
    private val VERSE_WORDS = setOf("verse", "verses", "v", "vv", "vs", "ver")
    private val RANGE_WORDS = setOf("to", "through", "thru", "until", "till")

    /** Words a preacher uses just before a reference: "turn to Luke 15", "in Mark 10". */
    private fun hasCue(tokens: List<Token>, bookStart: Int): Boolean {
        val prev = tokens.getOrNull(bookStart - 1)?.text ?: return false
        val prev2 = tokens.getOrNull(bookStart - 2)?.text
        return prev in setOf("in", "from", "read", "reading", "reads", "see", "cf") ||
            (prev == "to" && prev2 in setOf("turn", "open", "go", "back", "over", "turning"))
    }

    private fun matchAt(tokens: List<Token>, i: Int, source: String, lenient: Boolean): Pair<ScriptureMatch, Int>? {
        val hit = bookAt(tokens, i) ?: return null
        val book = hit.book
        // A lone lower-case "i" before a numbered book is the pronoun ("i john…" is not 1 John).
        if (tokens[hit.start].text == "i" && source[tokens[hit.start].start] == 'i') return null

        var j = hit.next
        var sawChapterWord = false
        if (tokens.getOrNull(j)?.text in CHAPTER_WORDS) { sawChapterWord = true; j++ }
        val chapterToken = tokens.getOrNull(j)?.takeIf { it.kind == Kind.NUMBER } ?: return null
        var chapter = chapterToken.value ?: return null
        j++

        var verseStart: Int? = null
        var verseEnd: Int? = null
        var sawVerseMarker = false
        var end = chapterToken.end

        fun number(at: Int) = tokens.getOrNull(at)?.takeIf { it.kind == Kind.NUMBER }

        // Verse: ":16", ".16", "verse 16", or (spoken, or "John 3 16") simply the next number.
        val sep = tokens.getOrNull(j)
        when {
            sep?.kind == Kind.COLON && number(j + 1) != null -> { verseStart = number(j + 1)!!.value; end = number(j + 1)!!.end; j += 2; sawVerseMarker = true }
            sep?.kind == Kind.COMMA && tokens.getOrNull(j + 1)?.text in VERSE_WORDS && number(j + 2) != null -> {
                verseStart = number(j + 2)!!.value; end = number(j + 2)!!.end; j += 3; sawVerseMarker = true
            }
            sep?.text in VERSE_WORDS && number(j + 1) != null -> { verseStart = number(j + 1)!!.value; end = number(j + 1)!!.end; j += 2; sawVerseMarker = true }
            sep?.kind == Kind.NUMBER -> { verseStart = sep.value; end = sep.end; j += 1 }
        }

        // Range: "-18", "to 18", "through eighteen", "and 17" (only the very next verse).
        if (verseStart != null) {
            val r = tokens.getOrNull(j)
            when {
                (r?.kind == Kind.DASH || r?.text in RANGE_WORDS) && number(j + 1) != null -> {
                    val n = number(j + 1)!!
                    // "3:16-4:2" crosses chapters: keep the start, which is where it is read from.
                    if (tokens.getOrNull(j + 2)?.kind == Kind.COLON && number(j + 3) != null) {
                        end = number(j + 3)!!.end
                    } else if (n.value != null && n.value > verseStart) { verseEnd = n.value; end = n.end }
                }
                r?.text == "and" && number(j + 1)?.value == verseStart + 1 -> { verseEnd = verseStart + 1; end = number(j + 1)!!.end }
            }
        }

        // Guard against names and ordinary words: an ambiguous book needs a verse, "chapter", or
        // "the book of". Everything else is accepted from a chapter alone ("Psalm 23", "Romans 8").
        val certain = lenient || hit.explicitBookOf || sawChapterWord || sawVerseMarker || verseStart != null || hasCue(tokens, hit.start)
        if (book.usfm in AMBIGUOUS && !certain) return null

        // One-chapter books: "Jude 3" is verse 3.
        if (book.usfm in SINGLE_CHAPTER && verseStart == null && chapter > 1) {
            verseStart = chapter; chapter = 1
        }


        // Versification: the chapter and verses must exist.
        val verses = book.verseCount(chapter) ?: return null
        if (verseStart != null && verseStart !in 1..verses) return null
        if (verseEnd != null && verseEnd > verses) verseEnd = verses
        if (verseEnd != null && verseEnd <= (verseStart ?: 0)) verseEnd = null

        val startChar = tokens[hit.start].start
        val ref = ScriptureReference(book, chapter, verseStart, verseEnd)
        return ScriptureMatch(ref, startChar, end, source.substring(startChar, end)) to j
    }
}
