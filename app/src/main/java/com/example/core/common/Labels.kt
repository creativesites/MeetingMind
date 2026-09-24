package com.example.core.common

/**
 * Guards for labels that come from models or old data: "[]", "null", blank, bare punctuation and
 * quotes never reach the screen.
 */
object Labels {
    private val JUNK = setOf("[]", "{}", "null", "none", "n/a", "undefined", "\"\"", "''")

    /** The label cleaned for display, or null when there's nothing real in it. */
    fun clean(raw: String?): String? {
        val t = raw?.trim()?.trim('"', '\'', '[', ']', '{', '}', ',', ';')?.trim() ?: return null
        if (t.isEmpty() || t.lowercase() in JUNK) return null
        if (t.none { it.isLetterOrDigit() }) return null
        return t
    }
}
