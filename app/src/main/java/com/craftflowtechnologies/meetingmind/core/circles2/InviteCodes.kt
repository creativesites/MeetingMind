package com.craftflowtechnologies.meetingmind.core.circles2

/**
 * Invite codes look like `GRACE-7K2Q`: a word, a dash, four characters from an alphabet with no
 * vowels and no lookalikes. People paste all sorts of things (a whole WhatsApp message, a link, a
 * lowercase code with spaces, emoji, garbage), so parsing here never throws: it returns the first
 * well-formed code or null. This mirrors `extractCodes` in server/circles-api/src/logic.js; the
 * server re-validates everything, this only decides whether to enable the Join button.
 */
object InviteCodes {
    /** Same word list as the Worker. A pasted "self-care" must not look like a code. */
    val WORDS = listOf(
        "GRACE", "FAITH", "HOPE", "LIGHT", "PEACE", "JOY", "AMEN", "MERCY", "TRUST", "PRAISE", "SHEPHERD", "COVENANT",
        "ANCHOR", "BEACON", "CEDAR", "DOVE", "EMBER", "FIELD", "GARDEN", "HARVEST", "OLIVE", "RIVER", "ROCK", "SPRING",
        "STAR", "VINE", "WILLOW", "WINGS", "LAMP", "PATH", "SALT", "SEED"
    )
    const val ALPHABET = "23456789BCDFGHJKMNPQRSTVWXZ"

    private const val MAX_SCAN = 2000
    // Dash, a typographic dash (phones often swap "-" for an en dash), or a space: "GRACE 7K2Q" is still the code.
    private val pattern = Regex("(?<![A-Za-z0-9])([A-Za-z]{3,10})[ \\t\\-\\u2010-\\u2015\\u2212]{1,2}([$ALPHABET${ALPHABET.lowercase()}]{4})(?![A-Za-z0-9])")

    /** Every well-formed code in [text], in order, upper-cased, without duplicates. */
    fun extractAll(text: String?, max: Int = 3): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        for (m in pattern.findAll(text.take(MAX_SCAN))) {
            val word = m.groupValues[1].uppercase()
            if (word !in WORDS) continue
            out += "$word-${m.groupValues[2].uppercase()}"
            if (out.size >= max) break
        }
        return out.toList()
    }

    /** The first code found in whatever was pasted, or null. Never throws. */
    fun extract(text: String?): String? = extractAll(text, 1).firstOrNull()

    /** Link people can tap; the code is also written out in the share text for apps that do not link it. */
    fun link(code: String): String = "meetingmind://c/$code"

    /** The message shared from the invite sheet. The plain code is always in it. */
    fun shareText(circleName: String, vocab: String, code: String): String =
        "Join our $vocab \"${circleName.trim()}\" on MeetingMind.\nCode: $code\n${link(code)}\n" +
            "Open MeetingMind, go to Circles, tap Join and paste this message."

    /** The code carried by a deep link (`meetingmind://c/CODE`, `.../c/CODE`, or one with a ?code= query), or null. */
    fun fromLink(uri: String?): String? = extract(uri)
}
