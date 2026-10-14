package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** How the Notes library is ordered. */
enum class NoteSort(val label: String) { UPDATED("Last edited"), CREATED("Date"), TITLE("Title") }

/** Which notes the library reads: live ones, archived ones, or the Trash. */
enum class LibraryMode { ACTIVE, ARCHIVED, TRASH }

/**
 * The chips on the Notes library (U-2). "All" is simply no chip. Work, Faith and Bible are spaces and add up (Work +
 * Faith shows both); Recordings and Pinned narrow whatever the spaces picked.
 */
enum class LibraryChip(val label: String, val isSpace: Boolean) {
    WORK("Work", true), FAITH("Faith", true), BIBLE("Bible", true),
    RECORDINGS("Recordings", false), PINNED("Pinned", false)
}

/**
 * Everything that decides which notes the library lists. [hideLocked] is true while the Faith lock is on and still
 * locked: search then leaves out private and Faith notes, and their previews stay blank.
 */
data class LibraryQuery(
    val mode: LibraryMode = LibraryMode.ACTIVE,
    val notebookId: String? = null,
    val chips: Set<LibraryChip> = emptySet(),
    val types: Set<RecordingType> = emptySet(),
    val tagId: String? = null,
    val search: String = "",
    val sort: NoteSort = NoteSort.UPDATED,
    val includeTranscripts: Boolean = false,
    val hideLocked: Boolean = false
) {
    /** The FTS MATCH expression for [search], or null when the text holds nothing searchable. */
    val match: String? get() = NoteSearch.match(search)
    val isSearching: Boolean get() = match != null
    /** Something other than the default list is asked for (search text or any filter). */
    val isFiltered: Boolean get() = isSearching || chips.isNotEmpty() || types.isNotEmpty() || tagId != null
    /** Pinned notes float to the top of a plain date-ordered list; not in search (rank decides) and not by title. */
    val pinFirst: Boolean get() = mode == LibraryMode.ACTIVE && !isSearching && sort != NoteSort.TITLE

    fun toggled(chip: LibraryChip) = copy(chips = if (chip in chips) chips - chip else chips + chip)
    fun clearedFilters() = copy(chips = emptySet(), types = emptySet(), tagId = null, search = "", includeTranscripts = false)
}

/** What a library row shows beyond the note itself. */
data class LibraryItem(
    val note: Note,
    /** Plain-text preview, or the matched passage when searching; blank for private or locked notes. */
    val preview: String,
    /** Total length of the note's recordings, null with none. */
    val durationMs: Long?,
    val hasRecording: Boolean,
    val transcript: NoteTranscriptStatus,
    val cursor: Cursor,
    /** The words to highlight in the title and preview (the search terms). */
    val highlight: List<String> = emptyList()
) {
    val id: String get() = note.id
}

/** Search text to FTS and back: what to MATCH, and which words to highlight in the results. */
object NoteSearch {
    const val MAX_TERMS = 8
    private val tokens = Regex("\"([^\"]+)\"|([\\p{L}\\p{N}’']+)")
    private val word = Regex("[\\p{L}\\p{N}]+")

    /** Prefix terms: `faith hope` becomes `faith* hope*`; "a phrase" stays a phrase. Null when nothing is searchable. */
    fun match(query: String): String? {
        val parts = mutableListOf<String>()
        for (m in tokens.findAll(query)) {
            if (parts.size >= MAX_TERMS) break
            val phrase = m.groupValues[1]
            if (phrase.isNotBlank()) {
                val words = word.findAll(phrase).map { it.value.lowercase() }.toList()
                if (words.isNotEmpty()) parts += "\"" + words.joinToString(" ") + "\""
            } else word.findAll(m.groupValues[2]).forEach { if (parts.size < MAX_TERMS) parts += it.value.lowercase() + "*" }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** The words to highlight: each search word, each word of a quoted phrase, lower-cased, longest first. */
    fun terms(query: String): List<String> =
        word.findAll(query).map { it.value.lowercase() }.distinct().take(MAX_TERMS * 2).sortedByDescending { it.length }.toList()

    /** Ranges of [text] that start a word matching one of [terms] (the same prefix rule FTS uses), merged and sorted. */
    fun highlights(text: String, terms: List<String>): List<IntRange> {
        if (text.isEmpty() || terms.isEmpty()) return emptyList()
        val found = mutableListOf<IntRange>()
        for (t in terms) {
            if (t.isEmpty()) continue
            var from = 0
            while (from <= text.length - t.length) {
                val i = text.indexOf(t, from, ignoreCase = true)
                if (i < 0) break
                if (i == 0 || !text[i - 1].isLetterOrDigit()) found += i until (i + t.length)
                from = i + 1
            }
        }
        if (found.isEmpty()) return emptyList()
        val sorted = found.sortedBy { it.first }
        val merged = mutableListOf(sorted.first())
        for (r in sorted.drop(1)) {
            val last = merged.last()
            if (r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last) else merged += r
        }
        return merged
    }
}

/** A note's text as a short, symbol-free preview. */
object NotePreview {
    private val fence = Regex("```[\\s\\S]*?```")
    private val image = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val lineMarker = Regex("^\\s*(?:#{1,6}\\s+|>+\\s?|[-*+]\\s+\\[[ xX]]\\s+|[-*+]\\s+|\\d+[.)]\\s+|-{3,}\\s*$|[*_]{3,}\\s*$)")
    private val emphasis = Regex("(\\*\\*|__|~~|`|\\*)")
    private val loneUnderscore = Regex("(?<![\\p{L}\\p{N}])_+|_+(?![\\p{L}\\p{N}])")
    private val spaces = Regex("\\s+")

    /** [raw] with Markdown symbols removed and whitespace collapsed to single spaces. */
    fun plain(raw: String): String {
        if (raw.isBlank()) return ""
        var text = fence.replace(raw) { it.value.removeSurrounding("```").trim() }
        text = image.replace(text, "$1")
        text = link.replace(text, "$1")
        text = text.lineSequence().map { lineMarker.replace(it, "") }.joinToString(" ")
        text = emphasis.replace(text, "")
        text = loneUnderscore.replace(text, "")
        return spaces.replace(text, " ").trim()
    }

    /** The first [max] characters, cut at a word. */
    fun lead(raw: String, max: Int = 180): String {
        val p = plain(raw)
        return if (p.length <= max) p else p.take(max).substringBeforeLast(' ', p.take(max)).trimEnd() + "…"
    }

    /** The passage around the first search hit, so a body match shows why the note was found. Falls back to [lead]. */
    fun around(raw: String, terms: List<String>, max: Int = 180): String {
        val p = plain(raw)
        val first = NoteSearch.highlights(p, terms).firstOrNull() ?: return lead(raw, max)
        if (first.first < max / 3) return lead(raw, max)
        val start = (first.first - max / 4).coerceAtLeast(0).let { s -> p.lastIndexOf(' ', s).let { if (it < 0) s else it + 1 } }
        val tail = p.substring(start)
        val cut = if (tail.length <= max) tail else tail.take(max).substringBeforeLast(' ', tail.take(max)).trimEnd() + "…"
        return "…$cut"
    }
}

/** Short labels for a row's right-hand side. */
object NoteLabels {
    /** "Just now", "12 min ago", "3 h ago", "Yesterday", "Tue", "9 Oct", "9 Oct 2025". */
    fun relativeDate(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        if (at <= 0L) return ""
        val diff = now - at
        if (diff < 0) return ""
        val minutes = diff / 60_000
        val d = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(d.toLocalDate(), today)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 && days == 0L -> "$minutes min ago"
            days == 0L -> "${minutes / 60} h ago"
            days == 1L -> "Yesterday"
            days < 7 -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            d.year == today.year -> "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, locale)}"
            else -> "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, locale)} ${d.year}"
        }
    }

    /** "45 s", "42 min", "1 h 5 min". */
    fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min"
            else -> "${s / 3600} h ${(s % 3600) / 60} min".replace(" 0 min", "")
        }
    }
}
