package com.craftflowtechnologies.meetingmind.core.documents

/**
 * Turns what a person typed into a safe FTS4 `MATCH` expression for `document_pages_fts`.
 *
 * Only letters and digits survive, each word becomes a prefix term, and the words must all appear.
 * Anything else (quotes, `AND`, `NEAR`, punctuation) is dropped, so typing cannot break the query.
 */
object DocumentSearch {
    private val word = Regex("[\\p{L}\\p{N}]+")

    /** The MATCH expression, or null when there is nothing searchable (blank or punctuation only). */
    fun matchQuery(input: String): String? {
        val words = word.findAll(input).map { it.value }.toList()
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }
}
