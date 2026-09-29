package com.craftflowtechnologies.meetingmind.core.faith

/** Memory verses: a Scripture collection with a practice that hides more of the words each round. */
object MemoryVerses {
    const val COLLECTION = "Memory verses"

    /** Today's verse from [count] saved ones, turning daily. */
    fun indexFor(epochDay: Long, count: Int) = if (count <= 0) -1 else Math.floorMod(epochDay, count.toLong()).toInt()

    /**
     * [text] with words hidden for practice: level 0 shows everything; 1 hides every third word;
     * 2 every other word; 3 keeps only first letters. Punctuation stays so the rhythm stays.
     */
    fun mask(text: String, level: Int): String {
        if (level <= 0) return text
        var n = 0
        return Regex("\\p{L}[\\p{L}'’]*").replace(text) { m ->
            val w = m.value
            val hide = when (level) { 1 -> n % 3 == 2; 2 -> n % 2 == 1; else -> true }
            n++
            if (!hide) w else if (level >= 3) w.first() + "_".repeat((w.length - 1).coerceAtMost(8)) else "_".repeat(w.length.coerceAtMost(10))
        }
    }
}
