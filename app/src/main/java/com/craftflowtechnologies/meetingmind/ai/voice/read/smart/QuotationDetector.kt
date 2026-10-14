package com.craftflowtechnologies.meetingmind.ai.voice.read.smart

/**
 * Finds text that is someone else's words inside a block of the person's writing: a quoted stretch, or a
 * run of words copied from a passage of Scripture the study uses. It only detects. A block with a
 * quotation in it is read exactly as written (never rewritten, never "corrected" to match a translation).
 */
object QuotationDetector {
    private val quoted = Regex("[\"“„«]([^\"”“«»]{25,})[\"”»]")
    private val word = Regex("[\\p{L}\\p{N}']+")
    const val OVERLAP_WORDS = 6

    fun containsQuotation(text: String, passages: List<String>): Boolean {
        if (quoted.containsMatchIn(text)) return true
        if (passages.isEmpty()) return false
        val own = words(text)
        if (own.size < OVERLAP_WORDS) return false
        val grams = own.windowed(OVERLAP_WORDS).map { it.joinToString(" ") }.toSet()
        return passages.any { p -> words(p).windowed(OVERLAP_WORDS).any { it.joinToString(" ") in grams } }
    }

    private fun words(text: String): List<String> = word.findAll(text.lowercase().replace('’', '\'')).map { it.value }.toList()
}
