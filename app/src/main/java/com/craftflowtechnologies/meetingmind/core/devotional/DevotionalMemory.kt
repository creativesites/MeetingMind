package com.craftflowtechnologies.meetingmind.core.devotional

import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser

/** What one recent devotional used, as remembered from its note's metadata. */
data class MemoryEntry(
    val day: String,
    val title: String,
    val passage: String?,
    val opening: String?,
    val point: String?,
    val format: DevotionalFormat?,
    val series: String? = null,
    val evening: Boolean = false
)

/**
 * The last fortnight of devotionals, so the next one is new: its passage outside the exclusion
 * window, its format not yesterday's, its title, opening and main point not a rerun.
 */
data class DevotionalMemory(val entries: List<MemoryEntry> = emptyList()) {

    val lastFormat: DevotionalFormat? get() = entries.firstOrNull { !it.evening }?.format
    val recentFormats: List<DevotionalFormat> get() = entries.filter { !it.evening }.mapNotNull { it.format }

    /** Whether [ref] was used within [days] days of [today]. Overlapping verses in the same chapter count. */
    fun usedRecently(ref: ScriptureReference, today: java.time.LocalDate, days: Int): Boolean {
        if (days <= 0) return false
        return entries.any { e ->
            val d = runCatching { java.time.LocalDate.parse(e.day) }.getOrNull() ?: return@any false
            if (java.time.temporal.ChronoUnit.DAYS.between(d, today) > days) return@any false
            val used = e.passage?.let { ScriptureReferenceParser.parse(it) } ?: return@any false
            overlaps(used, ref)
        }
    }

    /** The Bible books of the last [n] morning devotionals, so the next one can come from elsewhere. */
    fun recentBooks(n: Int = 4, today: java.time.LocalDate? = null, withinDays: Int = 14): Set<String> = entries.filter { !it.evening }
        .filter { e -> today == null || runCatching { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(e.day), today) <= withinDays }.getOrDefault(false) }
        .take(n)
        .mapNotNull { e -> e.passage?.let { ScriptureReferenceParser.parse(it)?.usfm } }.toSet()

    /** Lines for the prompt: what not to repeat. */
    fun promptLines(max: Int = 14): List<String> = entries.take(max).map { e ->
        listOfNotNull(
            "\"${e.title}\"",
            e.passage,
            e.format?.label,
            e.opening?.let { "opened: \"${it.take(120)}\"" },
            e.point?.let { "main point: \"${it.take(100)}\"" }
        ).joinToString(" · ")
    }

    /** Earlier days of [series], oldest first, for continuing it. */
    fun seriesSoFar(series: String): List<MemoryEntry> = entries.filter { it.series == series }.reversed()

    /** Why [d] repeats a recent devotional, or null when it's new. */
    fun repeatOf(d: Devotional): String? {
        val title = d.title.trim().lowercase()
        val opening = d.reflection.firstOrNull()?.let { DevotionalNotes.firstSentence(it) }.orEmpty()
        val point = d.application.firstOrNull() ?: d.motivation.orEmpty()
        for (e in entries) {
            if (title.isNotEmpty() && e.title.trim().lowercase() == title) return "the title \"${d.title}\" was used on ${e.day}"
            if (opening.isNotBlank() && e.opening != null && similarity(opening, e.opening) >= 0.6) return "it opens almost like the one on ${e.day} (\"${e.opening.take(80)}\")"
            if (point.isNotBlank() && e.point != null && similarity(point, e.point) >= 0.7) return "its main point repeats the one on ${e.day}"
        }
        return null
    }

    companion object {
        fun overlaps(a: ScriptureReference, b: ScriptureReference): Boolean {
            if (a.usfm != b.usfm || a.chapter != b.chapter) return false
            val aStart = a.verseStart ?: return true
            val bStart = b.verseStart ?: return true
            val aEnd = a.verseEnd ?: aStart
            val bEnd = b.verseEnd ?: bStart
            return aStart <= bEnd && bStart <= aEnd
        }

        /** Word-overlap similarity (Jaccard on words longer than 2 letters). */
        fun similarity(a: String, b: String): Double {
            fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}']+")).filter { it.length > 2 }.toSet()
            val x = words(a); val y = words(b)
            if (x.isEmpty() || y.isEmpty()) return 0.0
            return (x intersect y).size.toDouble() / (x union y).size
        }

        fun fromMetadata(items: List<Pair<String, Map<String, String>>>): DevotionalMemory = DevotionalMemory(items.mapNotNull { (title, m) ->
            val day = m[DevotionalNotes.META_DAY] ?: return@mapNotNull null
            MemoryEntry(
                day = day, title = title,
                passage = m[DevotionalNotes.META_PASSAGE],
                opening = m[DevotionalNotes.META_OPENING],
                point = m[DevotionalNotes.META_POINT],
                format = m[DevotionalNotes.META_FORMAT]?.let { runCatching { DevotionalFormat.valueOf(it) }.getOrNull() },
                series = m[DevotionalNotes.META_SERIES],
                evening = m[DevotionalNotes.META_EVENING] == "1"
            )
        }.sortedByDescending { it.day })
    }
}
