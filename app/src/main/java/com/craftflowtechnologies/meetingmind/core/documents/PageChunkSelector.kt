package com.craftflowtechnologies.meetingmind.core.documents

/** One page's text, as read. [page] is 1-based. */
data class DocumentPageText(val page: Int, val text: String)

/** The text chosen for the proposer. [pages] are the page numbers included, in reading order. */
data class ChunkSelection(val text: String, val pages: List<Int>)

/**
 * Picks the pages the proposer reads, inside a character budget (spec §R4, step 3), instead of
 * truncating the document at its start.
 *
 * Priority, highest first:
 * 1. the first two pages (title, parties, purpose);
 * 2. pages that mention a known person, organisation or project;
 * 3. pages with requirement or decision cues ("shall", "must", "agreed", "decided", "deadline"),
 *    most cues first.
 *
 * The chosen pages go back in reading order. Each block starts with a `[p. N]` marker, so a finding
 * from the proposer can cite the page it came from.
 */
object PageChunkSelector {
    const val DEFAULT_BUDGET_CHARS = 6000

    /** A single page is never given more than this, so one long page cannot use the whole budget. */
    const val PER_PAGE_CHARS = 2000

    private const val MIN_NAME_LENGTH = 3

    private val cue = Regex(
        "\\b(shall|must|agreed|decided|deadline|decision|requirement)\\b",
        RegexOption.IGNORE_CASE
    )
    private val inlineSpace = Regex("[ \\t]+")
    private val extraBlankLines = Regex("\\n{3,}")

    fun select(
        pages: List<DocumentPageText>,
        entityNames: Collection<String> = emptyList(),
        budgetChars: Int = DEFAULT_BUDGET_CHARS
    ): ChunkSelection {
        val usable = pages
            .filter { it.text.isNotBlank() }
            .map { it.copy(text = tidy(it.text)) }
            .sortedBy { it.page }
        if (usable.isEmpty() || budgetChars <= 0) return ChunkSelection("", emptyList())

        val names = entityNames
            .map { it.trim() }
            .filter { it.length >= MIN_NAME_LENGTH }
            .distinct()
            .map { name ->
                Regex(
                    "(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])",
                    RegexOption.IGNORE_CASE
                )
            }

        val first = usable.take(2)
        val mentioning = usable.filter { page -> names.any { it.containsMatchIn(page.text) } }
        val cued = usable
            .map { it to cue.findAll(it.text).count() }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<DocumentPageText, Int>> { it.second }.thenBy { it.first.page })
            .map { it.first }

        // Candidates in priority order, each page once.
        val candidates = LinkedHashMap<Int, DocumentPageText>()
        (first + mentioning + cued).forEach { candidates.putIfAbsent(it.page, it) }

        val chosen = ArrayList<DocumentPageText>()
        var used = 0
        for (candidate in candidates.values) {
            val remaining = budgetChars - used
            if (remaining <= 0) break
            val marker = "[p. ${candidate.page}]\n"
            // Leave room for the marker, and skip a page that would only leave a stub behind.
            val room = remaining - marker.length
            if (room < MIN_USEFUL_CHARS) continue
            val excerpt = clip(candidate.text, minOf(PER_PAGE_CHARS, room))
            if (excerpt.isEmpty()) continue
            chosen += candidate.copy(text = excerpt)
            used += marker.length + excerpt.length + SEPARATOR.length
        }

        val inOrder = chosen.sortedBy { it.page }
        val text = inOrder.joinToString(SEPARATOR) { "[p. ${it.page}]\n${it.text}" }
        return ChunkSelection(text, inOrder.map { it.page })
    }

    /** Keeps the start of [text] up to [max] characters, cut at a word boundary where one is near. */
    internal fun clip(text: String, max: Int): String {
        if (text.length <= max) return text.trim()
        val cut = text.lastIndexOf(' ', max).takeIf { it >= max / 2 } ?: max
        return text.substring(0, cut).trimEnd()
    }

    private fun tidy(text: String): String =
        text.replace("\r\n", "\n").replace(inlineSpace, " ").replace(extraBlankLines, "\n\n").trim()

    private const val SEPARATOR = "\n\n"
    private const val MIN_USEFUL_CHARS = 120
}
