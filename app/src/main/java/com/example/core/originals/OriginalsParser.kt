package com.example.core.originals

/** One word of the Greek New Testament or Hebrew Old Testament, as STEPBible's data gives it. */
data class OriginalWord(
    val book: String,          // USFM: "JHN"
    val chapter: Int,
    val verse: Int,
    val position: Int,
    val hebrew: Boolean,
    val surface: String,       // the word as written (Hebrew prefixes joined with "/")
    val translit: String,
    val gloss: String,         // STEPBible's short English for this occurrence — a translation aid, not a definition
    val strongs: String,       // extended Strong's: "G0976", "H0430G"; several joined with "/" or " + " are split into [strongsAll]
    val morph: String,         // Robinson (Greek) or OpenScriptures/ETCBC-style (Hebrew) code
    val lemma: String
) {
    val strongsAll: List<String> get() = strongs.split('/', '+', ' ').map { it.trim().trim('{', '}') }.filter { it.isNotEmpty() }
}

/** A lexicon entry: the word's dictionary form and a short meaning from the lexicon, never from an AI. */
data class LexiconEntry(val strongs: String, val lemma: String, val translit: String, val partOfSpeech: String, val gloss: String, val meaning: String)

/**
 * Reads STEPBible's tab-separated text files (Tyndale House, CC BY 4.0): TAGNT (Greek NT), TAHOT
 * (Hebrew OT), and the TBESG/TBESH lexicons. Line by line, so a 20 MB file never sits in memory.
 * Lines that aren't word rows (headers, licence text, blank lines) return null.
 */
object OriginalsParser {
    private val GREEK_REF = Regex("^([1-3A-Za-z]{3})\\.(\\d+)\\.(\\d+)#(\\d+)=(\\S+)$")

    /** STEPBible's three-letter book codes → USFM. */
    val BOOKS: Map<String, String> = mapOf(
        "Gen" to "GEN", "Exo" to "EXO", "Lev" to "LEV", "Num" to "NUM", "Deu" to "DEU", "Jos" to "JOS", "Jdg" to "JDG", "Rut" to "RUT",
        "1Sa" to "1SA", "2Sa" to "2SA", "1Ki" to "1KI", "2Ki" to "2KI", "1Ch" to "1CH", "2Ch" to "2CH", "Ezr" to "EZR", "Neh" to "NEH",
        "Est" to "EST", "Job" to "JOB", "Psa" to "PSA", "Pro" to "PRO", "Ecc" to "ECC", "Sng" to "SNG", "Isa" to "ISA", "Jer" to "JER",
        "Lam" to "LAM", "Ezk" to "EZK", "Dan" to "DAN", "Hos" to "HOS", "Jol" to "JOL", "Amo" to "AMO", "Oba" to "OBA", "Jon" to "JON",
        "Mic" to "MIC", "Nam" to "NAM", "Hab" to "HAB", "Zep" to "ZEP", "Hag" to "HAG", "Zec" to "ZEC", "Mal" to "MAL",
        "Mat" to "MAT", "Mrk" to "MRK", "Luk" to "LUK", "Jhn" to "JHN", "Act" to "ACT", "Rom" to "ROM", "1Co" to "1CO", "2Co" to "2CO",
        "Gal" to "GAL", "Eph" to "EPH", "Php" to "PHP", "Col" to "COL", "1Th" to "1TH", "2Th" to "2TH", "1Ti" to "1TI", "2Ti" to "2TI",
        "Tit" to "TIT", "Phm" to "PHM", "Heb" to "HEB", "Jas" to "JAS", "1Pe" to "1PE", "2Pe" to "2PE", "1Jn" to "1JN", "2Jn" to "2JN",
        "3Jn" to "3JN", "Jud" to "JUD", "Rev" to "REV"
    )

    /** TAGNT row: `Jhn.15.5#04=NKO ⇥ ἄμπελος, (ampelos) ⇥ vine, ⇥ G0288=N-NSF ⇥ ἄμπελος=vine ⇥ NA28+…`. Only words in the NA28 text. */
    fun greek(line: String): OriginalWord? {
        val c = line.split('\t')
        if (c.size < 6) return null
        val m = GREEK_REF.matchEntire(c[0]) ?: return null
        if (!c[5].contains("NA28")) return null
        val book = BOOKS[m.groupValues[1]] ?: return null
        val form = c[1]
        val open = form.lastIndexOf(" (")
        val surface = (if (open > 0) form.substring(0, open) else form).trim()
        val translit = if (open > 0) form.substring(open + 2).trimEnd(')') else ""
        // "G1473=P-1NS + G2532=CONJ": one written word standing for two.
        val pairs = c[3].split(" + ").map { it.split("=", limit = 2) }
        return OriginalWord(
            book, m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt(), false, nfc(surface), nfc(translit),
            nfc(c[2].trim()), pairs.joinToString(" + ") { it[0].trim() }, pairs.joinToString(" + ") { it.getOrElse(1) { "" }.trim() }, nfc(c[4].substringBefore('=').trim())
        )
    }

    /** TAHOT row: `Gen.1.1#01=L ⇥ בְּ/רֵאשִׁ֖ית ⇥ be./re.Shit ⇥ in/ beginning ⇥ H9003/{H7225G} ⇥ HR/Ncfsa ⇥ …`. The Leningrad text only. */
    fun hebrew(line: String): OriginalWord? {
        val c = line.split('\t')
        if (c.size < 6) return null
        val m = GREEK_REF.matchEntire(c[0]) ?: return null
        if (m.groupValues[5] != "L") return null
        val book = BOOKS[m.groupValues[1]] ?: return null
        val lemmaField = c.getOrNull(11).orEmpty() // "{H7225G=רֵאשִׁית=: beginning»…}"
        val lemma = lemmaField.split('=').getOrNull(1).orEmpty().trim()
        return OriginalWord(
            book, m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt(), true,
            nfc(c[1].trim()), nfc(c[2].trim()), nfc(c[3].replace("/ ", " ").replace("/", " ").trim()), c[4].trim(), c[5].trim(), nfc(lemma)
        )
    }

    /** TBESG / TBESH row: `H0430 ⇥ H0430G = a Name of ⇥ H3068G ⇥ אֱלֹהִים ⇥ e.lo.him ⇥ H:N-M ⇥ God ⇥ <meaning html>`. */
    fun lexicon(line: String): LexiconEntry? {
        val c = line.split('\t')
        if (c.size < 8 || !Regex("^[GH]\\d{4}").containsMatchIn(c[0])) return null
        val key = c[1].substringBefore('=').trim().ifEmpty { c[0] }
        return LexiconEntry(key, nfc(c[3].trim()), nfc(c[4].trim()), c[5].substringAfter(':', c[5]).trim(), nfc(c[6].trim()), nfc(plain(c[7])))
    }

    /** One Unicode form for everything stored, so a word from one file finds its entry in another. */
    fun nfc(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    /** The lexicon's HTML as plain text: line breaks kept, tags and Bible-reference markup dropped, length capped. */
    fun plain(html: String, max: Int = 700): String {
        val text = html.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<ref='[^']*'>"), "").replace("</ref>", "")
            .replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace(Regex("[ \\t]+"), " ").replace(Regex("\\s*\n\\s*"), "\n").trim()
        return if (text.length <= max) text else text.take(max).substringBeforeLast(' ') + "…"
    }
}

/**
 * Plain-English meanings of the grammar codes, so "N-NSF" reads "noun · nominative · singular ·
 * feminine". Decoded by rule from the published code systems — the app does not guess grammar.
 */
object MorphDecoder {
    private val CASE = mapOf('N' to "nominative", 'G' to "genitive", 'D' to "dative", 'A' to "accusative", 'V' to "vocative")
    private val NUMBER = mapOf('S' to "singular", 'P' to "plural")
    private val GENDER = mapOf('M' to "masculine", 'F' to "feminine", 'N' to "neuter")
    private val TENSE = mapOf('P' to "present", 'I' to "imperfect", 'F' to "future", 'A' to "aorist", 'R' to "perfect", 'L' to "pluperfect", '2' to "second")
    private val VOICE = mapOf('A' to "active", 'M' to "middle", 'P' to "passive", 'E' to "middle or passive", 'D' to "deponent", 'N' to "middle deponent", 'O' to "passive deponent")
    private val MOOD = mapOf('I' to "indicative", 'S' to "subjunctive", 'O' to "optative", 'M' to "imperative", 'N' to "infinitive", 'P' to "participle")
    private val PERSON = mapOf('1' to "1st person", '2' to "2nd person", '3' to "3rd person")
    private val PRONOUN = mapOf('P' to "personal pronoun", 'R' to "relative pronoun", 'C' to "reciprocal pronoun", 'D' to "demonstrative pronoun",
        'K' to "correlative pronoun", 'I' to "interrogative pronoun", 'X' to "indefinite pronoun", 'Q' to "correlative or interrogative pronoun",
        'F' to "reflexive pronoun", 'S' to "possessive pronoun")
    private val SIMPLE = mapOf("PREP" to "preposition", "CONJ" to "conjunction", "ADV" to "adverb", "PRT" to "particle", "INJ" to "interjection",
        "COND" to "conditional", "ARAM" to "Aramaic word", "HEB" to "Hebrew word")

    /** Greek (Robinson): "V-AAI-3S", "N-GSM-P", "T-NSF", "P-1NS". Two words joined by " + " decode separately. */
    fun greek(code: String): String = code.split('+').joinToString("  |  ") { greekOne(it.trim()) }

    private fun greekOne(code: String): String {
        SIMPLE[code]?.let { return it }
        val parts = code.split('-')
        val pos = parts.firstOrNull().orEmpty()
        val out = mutableListOf<String>()
        fun cng(s: String) { s.forEach { ch -> (CASE[ch] ?: NUMBER[ch] ?: GENDER[ch])?.let(out::add) } }
        when (pos) {
            "N" -> { out += "noun"; parts.getOrNull(1)?.let(::cng); if (parts.getOrNull(2) == "P") out += "proper name" }
            "A" -> { out += "adjective"; parts.getOrNull(1)?.let(::cng) }
            "T" -> { out += "article"; parts.getOrNull(1)?.let(::cng) }
            "V" -> {
                out += "verb"
                val t = parts.getOrNull(1).orEmpty()
                t.getOrNull(0)?.let { TENSE[it] }?.let(out::add)
                t.getOrNull(1)?.let { VOICE[it] }?.let(out::add)
                t.getOrNull(2)?.let { MOOD[it] }?.let(out::add)
                val rest = parts.getOrNull(2).orEmpty()
                if (rest.length == 2 && rest[0] in PERSON) { PERSON[rest[0]]?.let(out::add); NUMBER[rest[1]]?.let(out::add) } else cng(rest)
            }
            else -> {
                if (PRONOUN.containsKey(pos.firstOrNull() ?: ' ') && pos.length == 1) {
                    out += PRONOUN.getValue(pos[0])
                    val s = parts.getOrNull(1).orEmpty()
                    if (s.firstOrNull() in PERSON) { PERSON[s[0]]?.let(out::add); cng(s.drop(1)) } else cng(s)
                } else out += code
            }
        }
        return out.joinToString(" · ")
    }

    private val H_STEM = mapOf('q' to "Qal", 'N' to "Niphal", 'p' to "Piel", 'P' to "Pual", 'h' to "Hiphil", 'H' to "Hophal", 't' to "Hithpael")
    private val H_ASPECT = mapOf('p' to "perfect", 'q' to "sequential perfect", 'i' to "imperfect", 'w' to "sequential imperfect", 'h' to "cohortative",
        'j' to "jussive", 'v' to "imperative", 'r' to "active participle", 's' to "passive participle", 'a' to "infinitive absolute", 'c' to "infinitive construct")
    private val H_GENDER = mapOf('m' to "masculine", 'f' to "feminine", 'b' to "both genders", 'c' to "common")
    private val H_NUMBER = mapOf('s' to "singular", 'p' to "plural", 'd' to "dual")
    private val H_STATE = mapOf('a' to "absolute", 'c' to "construct", 'd' to "determined")

    /** Hebrew: "HNcmpa", "HVqp3ms", "HR/Ncfsa" (a prefix, then the word). */
    fun hebrew(code: String): String = code.split('/').joinToString("  +  ") { hebrewOne(it.trim().removePrefix("H")) }.replace(Regex("^ +"), "")

    private fun hebrewOne(c: String): String {
        if (c.isEmpty()) return ""
        val out = mutableListOf<String>()
        when (c[0]) {
            'R' -> out += "preposition"
            'C' -> out += "conjunction"
            'D' -> out += "adverb"
            'T' -> out += when (c.getOrNull(1)) { 'o' -> "object marker"; 'd' -> "definite article"; 'r' -> "relative particle"; 'i' -> "interrogative"; 'n' -> "negative"; else -> "particle" }
            'S' -> out += "suffix"
            'P' -> out += "pronoun"
            'A' -> { out += "adjective"; c.getOrNull(2)?.let { H_GENDER[it] }?.let(out::add); c.getOrNull(3)?.let { H_NUMBER[it] }?.let(out::add); c.getOrNull(4)?.let { H_STATE[it] }?.let(out::add) }
            'N' -> {
                out += when (c.getOrNull(1)) { 'p' -> "proper noun"; 'g' -> "gentilic noun"; else -> "noun" }
                c.getOrNull(2)?.let { H_GENDER[it] }?.let(out::add); c.getOrNull(3)?.let { H_NUMBER[it] }?.let(out::add); c.getOrNull(4)?.let { H_STATE[it] }?.let(out::add)
            }
            'V' -> {
                out += "verb"; c.getOrNull(1)?.let { H_STEM[it] }?.let(out::add); c.getOrNull(2)?.let { H_ASPECT[it] }?.let(out::add)
                c.getOrNull(3)?.let { PERSON[it] }?.let(out::add); c.getOrNull(4)?.let { H_GENDER[it] }?.let(out::add); c.getOrNull(5)?.let { H_NUMBER[it] }?.let(out::add)
            }
            else -> out += c
        }
        return out.joinToString(" · ")
    }
}
