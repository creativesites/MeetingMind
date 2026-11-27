package com.craftflowtechnologies.meetingmind.feature.personal.sky

/** One reflection as the sky sees it: its words (title and text) and the tags it carries. */
data class ReflectionEntry(
    val id: String,
    val text: String,
    val createdAt: Long,
    val tags: List<String> = emptyList()
)

/**
 * A theme that keeps coming back: the reflections that carry it, oldest first.
 * [firstAt] is when the theme first came up.
 */
data class Constellation(
    val name: String,
    val entryIds: List<String>,
    val firstAt: Long
)

/**
 * Finds the themes in a set of reflections, entirely on the phone.
 *
 * A theme is a word (or a tag) that appears in at least [MIN_ENTRIES] reflections. Words are
 * lower-cased, English stopwords and words under four letters are dropped, and simple plurals
 * merge ("inbox" and "inboxes"). Tags count as themes too. Themes rank by how many reflections
 * carry them, then by how recently they came up, and at most [MAX_CONSTELLATIONS] are returned.
 * Pure and deterministic, so it is tested on the JVM.
 */
object ReflectionThemes {
    const val MAX_CONSTELLATIONS = 5
    const val MIN_ENTRIES = 2
    private const val MIN_WORD_LENGTH = 4

    private val WORD = Regex("\\p{L}+")

    fun find(entries: List<ReflectionEntry>): List<Constellation> {
        val themes = LinkedHashMap<String, Theme>()
        for (entry in entries) {
            for ((key, display) in themesOf(entry)) {
                themes.getOrPut(key) { Theme(display) }.add(entry)
            }
        }
        return themes.values
            .filter { it.ids.size >= MIN_ENTRIES }
            .sortedWith(compareByDescending<Theme> { it.ids.size }.thenByDescending { it.lastAt }.thenBy { it.display })
            .take(MAX_CONSTELLATIONS)
            .map { Constellation(name = it.display, entryIds = it.ids.toList(), firstAt = it.firstAt) }
    }

    /** The theme keys in one reflection, each with the name it is shown under. */
    private fun themesOf(entry: ReflectionEntry): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        for (word in WORD.findAll(entry.text.lowercase()).map { it.value }) {
            if (word.length < MIN_WORD_LENGTH || word in STOPWORDS) continue
            found.putIfAbsent(singular(word), word.titleCase())
        }
        for (tag in entry.tags) {
            val clean = tag.trim().replace(Regex("\\s+"), " ").lowercase()
            if (clean.isEmpty() || clean in STOPWORDS) continue
            val key = if (' ' in clean) clean else singular(clean)
            found.putIfAbsent(key, tag.trim().replace(Regex("\\s+"), " ").titleCase())
        }
        return found
    }

    /** Merges the simple English plurals: "inboxes" to "inbox", "lists" to "list", "mornings" to "morning". */
    internal fun singular(word: String): String = when {
        word.length > 4 && word.endsWith("xes") -> word.dropLast(2)
        word.length > 4 && (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses")) -> word.dropLast(2)
        word.length > 4 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is") -> word.dropLast(1)
        else -> word
    }

    private fun String.titleCase(): String = split(' ').joinToString(" ") { part ->
        part.replaceFirstChar { it.titlecase() }
    }

    private class Theme(val display: String) {
        val ids = LinkedHashSet<String>()
        var firstAt = Long.MAX_VALUE
        var lastAt = Long.MIN_VALUE

        fun add(entry: ReflectionEntry) {
            ids.add(entry.id)
            if (entry.createdAt < firstAt) firstAt = entry.createdAt
            if (entry.createdAt > lastAt) lastAt = entry.createdAt
        }
    }

    /** Words that say nothing about what a reflection is about. */
    val STOPWORDS: Set<String> = setOf(
        // pronouns and determiners
        "i", "me", "my", "mine", "myself", "we", "us", "our", "ours", "you", "your", "yours", "he", "him", "his",
        "she", "her", "hers", "it", "its", "they", "them", "their", "theirs", "this", "that", "these", "those",
        "who", "whom", "whose", "which", "what", "all", "any", "both", "each", "few", "more", "most", "other",
        "some", "such", "same", "own", "one", "someone", "anyone", "everyone", "nobody", "something", "anything",
        "everything", "nothing", "many", "much", "lot", "bit",
        // auxiliaries and common verbs
        "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does",
        "did", "doing", "will", "would", "shall", "should", "can", "could", "may", "might", "must", "get", "gets",
        "got", "going", "went", "gone", "make", "made", "know", "knew", "think", "thought", "want", "wanted",
        "need", "needed", "like", "said", "say", "let", "feel", "felt", "feeling", "really", "just", "even",
        "still", "also", "yet", "very", "too", "quite",
        // function words
        "and", "but", "or", "nor", "so", "if", "then", "than", "as", "of", "at", "by", "for", "with", "about",
        "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from",
        "up", "down", "in", "out", "on", "off", "over", "under", "again", "further", "once", "here", "there",
        "when", "where", "why", "how", "because", "while", "until", "not", "only", "no", "now", "back", "there",
        "s", "t", "don", "doesn", "didn", "isn", "wasn", "can",
        // time words that say nothing on their own
        "today", "day", "days", "thing", "things", "time", "times", "week", "weeks", "moment", "moments", "later"
    )
}
