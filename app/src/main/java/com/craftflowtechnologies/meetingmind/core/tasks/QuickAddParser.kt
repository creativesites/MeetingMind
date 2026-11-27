package com.craftflowtechnologies.meetingmind.core.tasks

import com.craftflowtechnologies.meetingmind.core.work.DueDates
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** A list the parser may match "#name" against. */
data class QuickAddList(val id: String, val name: String)

/** A token the parser recognised: a range of the original input, for highlighting. */
data class QuickAddSpan(val start: Int, val end: Int, val kind: Kind) {
    enum class Kind { DATE, TIME, LIST, PRIORITY, LABEL, REPEAT }
}

/**
 * What a quick-add line means. [title] is the line with every recognised token removed.
 * Anything ambiguous stays in the title: a bare "9:30", "at 3", "Sun Tzu", "#Ac" when two lists
 * match, "!5". Nothing is guessed.
 */
data class QuickAddResult(
    val title: String,
    /** Local start of the due day, or the due time when [hasTime]. Null when no day was given. */
    val dueAt: Long?,
    val hasTime: Boolean,
    val listId: String?,
    val listName: String?,
    /** 1 (urgent) to 4 (none). */
    val priority: Int,
    val labels: List<String>,
    /** An RRULE such as FREQ=WEEKLY;BYDAY=MO, or null when the line doesn't repeat. */
    val repeatRule: String?,
    val spans: List<QuickAddSpan>
)

/**
 * Reads a task typed in one line: "Send API docs to James tomorrow 3pm #Acme !1 @email".
 *
 * Deterministic and offline. Dates come from [DueDates], so the two agree on what "Friday" means.
 * Times, #lists, !priorities, @labels and "every …" repeats are read here. [now] is the clock, so
 * the same line parses the same way in a test.
 */
object QuickAddParser {

    fun parse(input: String, now: Long, lists: List<QuickAddList> = emptyList()): QuickAddResult {
        val toks = Regex("\\S+").findAll(input).map { Tok(it.value, it.range.first, it.range.last + 1) }.toList()
        val taken = BooleanArray(toks.size)
        val spans = mutableListOf<QuickAddSpan>()
        fun take(from: Int, to: Int, kind: QuickAddSpan.Kind) {
            for (k in from until to) taken[k] = true
            spans += QuickAddSpan(toks[from].start, toks[to - 1].end, kind)
        }

        // "every day", "every weekday", "every week", "every month", "every monday".
        var repeatRule: String? = null
        var repeatWeekday: String? = null
        for (i in 0 until toks.size - 1) {
            if (taken[i] || taken[i + 1] || toks[i].core != "every") continue
            val word = toks[i + 1].core
            val weekday = WEEKDAY_CODES[word]
            val rule = (if (weekday != null) "FREQ=WEEKLY;BYDAY=$weekday" else REPEAT_WORDS[word]) ?: continue
            take(i, i + 2, QuickAddSpan.Kind.REPEAT)
            repeatRule = rule
            repeatWeekday = if (weekday != null) word else null
            break
        }

        // "!1" to "!4", "@label" and "#list", one token each.
        var priority = 4
        var priorityFound = false
        val labels = mutableListOf<String>()
        var list: QuickAddList? = null
        for (i in toks.indices) {
            if (taken[i]) continue
            val t = toks[i]
            when {
                !priorityFound && PRIORITY.matches(t.core) -> {
                    priority = t.core[1] - '0'
                    priorityFound = true
                    take(i, i + 1, QuickAddSpan.Kind.PRIORITY)
                }
                LABEL.matches(t.core) -> {
                    val name = t.raw.trimStart('@').trimEnd(*TRAILING)
                    if (name.isNotEmpty()) {
                        if (labels.none { it.equals(name, ignoreCase = true) }) labels += name
                        take(i, i + 1, QuickAddSpan.Kind.LABEL)
                    }
                }
                list == null && LIST.matches(t.core) -> {
                    val match = matchList(t.core.drop(1), lists)
                    if (match != null) {
                        list = match
                        take(i, i + 1, QuickAddSpan.Kind.LIST)
                    }
                }
            }
        }

        // A time of day: "3pm", "9.30am", "15:00", "3 pm", "at noon", "at midnight".
        var time: Int? = null
        for (i in toks.indices) {
            if (time != null) break
            val hit = timeAt(toks, taken, i) ?: continue
            var from = hit.from
            if (from > 0 && !taken[from - 1] && toks[from - 1].core == "at") from -= 1
            take(from, hit.to, QuickAddSpan.Kind.TIME)
            time = hit.minutes
        }

        // The first date phrase that DueDates can read. Only whole phrases count, so "may" or "sun" alone stay put.
        var dateStart: Long? = null
        for (i in toks.indices) {
            if (dateStart != null) break
            if (taken[i]) continue
            for (len in minOf(4, toks.size - i) downTo 1) {
                if ((i until i + len).any { taken[it] }) continue
                val phrase = (i until i + len).joinToString(" ") { toks[it].core }
                if (!DATE_PHRASE.matches(phrase)) continue
                val day = DueDates.parse(phrase, now) ?: continue
                dateStart = day
                var from = i
                if (from > 0 && !taken[from - 1] && toks[from - 1].core in LEAD_IN) from -= 1
                take(from, i + len, QuickAddSpan.Kind.DATE)
                break
            }
        }

        // A time with no day means today; a weekday repeat with no day starts on that weekday.
        val zone = ZoneId.systemDefault()
        val weekdayStart = if (dateStart == null && repeatWeekday != null) DueDates.parse(repeatWeekday, now) else null
        val day = dateStart ?: weekdayStart ?: if (time != null) DueDates.startOfDay(now) else null
        val dueAt = day?.let { d ->
            val minutes = time ?: return@let d
            Instant.ofEpochMilli(d).atZone(zone).toLocalDate().atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
        }

        val kept = toks.filterIndexed { i, _ -> !taken[i] }.joinToString(" ") { it.raw }
        val title = kept.trim().trimEnd(',', ';', ':').trim().ifEmpty { input.trim() }

        return QuickAddResult(
            title = title,
            dueAt = dueAt,
            hasTime = time != null,
            listId = list?.id,
            listName = list?.name,
            priority = priority,
            labels = labels,
            repeatRule = repeatRule,
            spans = spans.sortedBy { it.start }
        )
    }

    /** The one list [query] names: exact, then prefix, then a close spelling. A tie matches nothing. */
    fun matchList(query: String, lists: List<QuickAddList>): QuickAddList? {
        val q = norm(query)
        if (q.isEmpty()) return null
        val scored = lists.mapNotNull { list -> score(q, norm(list.name))?.let { list to it } }
        val best = scored.minOfOrNull { it.second } ?: return null
        return scored.filter { it.second == best }.singleOrNull()?.first
    }

    /** The legacy [TaskRepeat] for an RRULE, so code that still reads the enum repeats the task too. */
    fun legacyRepeat(rule: String?): TaskRepeat = when {
        rule == null -> TaskRepeat.NONE
        rule.startsWith("FREQ=DAILY") -> TaskRepeat.DAILY
        rule.startsWith("FREQ=WEEKLY") -> TaskRepeat.WEEKLY
        rule.startsWith("FREQ=MONTHLY") -> TaskRepeat.MONTHLY
        else -> TaskRepeat.NONE
    }

    private class Tok(val raw: String, val start: Int, val end: Int) {
        /** Lower case, without trailing punctuation: the form the rules match. */
        val core: String = raw.trimEnd(*TRAILING).lowercase(Locale.ROOT)
    }

    private class TimeHit(val minutes: Int, val from: Int, val to: Int)

    /** A time that starts at token [i], if there is one that is unambiguous. */
    private fun timeAt(toks: List<Tok>, taken: BooleanArray, i: Int): TimeHit? {
        if (taken[i]) return null
        val c = toks[i].core
        val nextFree = i + 1 < toks.size && !taken[i + 1]

        TIME_AMPM.matchEntire(c)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (hour !in 1..12 || minute !in 0..59) return null
            return TimeHit((hour % 12 + if (m.groupValues[3] == "pm") 12 else 0) * 60 + minute, i, i + 1)
        }
        TIME_24.matchEntire(c)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour !in 0..23 || minute !in 0..59) return null
            // "9:30" could be am or pm, so it stays text. "15:00" and "09:00" can't be anything else.
            if (hour < 13 && !m.groupValues[1].startsWith("0")) return null
            return TimeHit(hour * 60 + minute, i, i + 1)
        }
        if (HOUR_ONLY.matches(c) && nextFree && toks[i + 1].core in setOf("am", "pm")) {
            val hour = c.toInt()
            if (hour !in 1..12) return null
            return TimeHit((hour % 12 + if (toks[i + 1].core == "pm") 12 else 0) * 60, i, i + 2)
        }
        if (c == "at" && nextFree) {
            when (toks[i + 1].core) {
                "noon" -> return TimeHit(12 * 60, i, i + 2)
                "midnight" -> return TimeHit(0, i, i + 2)
            }
        }
        return null
    }

    private fun score(q: String, name: String): Int? {
        if (name.isEmpty()) return null
        return when {
            name == q -> 0
            name.startsWith(q) -> 1
            q.length >= 3 && name.contains(q) -> 2
            q.first() == name.first() && levenshtein(q, name) <= maxEdits(q.length) -> 3
            else -> null
        }
    }

    private fun maxEdits(length: Int): Int = when {
        length >= 7 -> 2
        length >= 4 -> 1
        else -> 0
    }

    private fun norm(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private val TRAILING = charArrayOf(',', '.', ';', ':', '?')
    private val PRIORITY = Regex("![1-4]")
    private val LABEL = Regex("@[\\p{L}\\p{N}_-]+")
    private val LIST = Regex("#[\\p{L}\\p{N}_-]+")
    private val TIME_AMPM = Regex("(\\d{1,2})(?:[:.](\\d{2}))?(am|pm)")
    private val TIME_24 = Regex("(\\d{1,2})[:.](\\d{2})")
    private val HOUR_ONLY = Regex("\\d{1,2}")
    private val LEAD_IN = setOf("by", "on", "due", "before", "until")

    private val WEEKDAY_CODES = mapOf(
        "monday" to "MO", "tuesday" to "TU", "wednesday" to "WE", "thursday" to "TH",
        "friday" to "FR", "saturday" to "SA", "sunday" to "SU"
    )
    private val REPEAT_WORDS = mapOf(
        "day" to "FREQ=DAILY",
        "weekday" to "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        "week" to "FREQ=WEEKLY",
        "month" to "FREQ=MONTHLY"
    )

    private const val MONTHS = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

    /**
     * Phrases DueDates reads as a day. Full weekday names only: "sat" and "sun" are often words.
     * Month and day must both be present, so "may" alone is never a date.
     */
    private val DATE_PHRASE = Regex(
        "today|tonight|tomorrow|tmrw|tmr|day after tomorrow|eod|eow|" +
            "end of (?:the )?(?:day|week|month)|this (?:week|month|evening|afternoon)|next week|next month|" +
            "(?:this |next )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|" +
            "in \\d{1,2} (?:day|days|week|weeks)|\\d{4}-\\d{1,2}-\\d{1,2}|" +
            "(?:$MONTHS) \\d{1,2}(?:st|nd|rd|th)?|\\d{1,2}(?:st|nd|rd|th)? (?:of )?(?:$MONTHS)"
    )
}
