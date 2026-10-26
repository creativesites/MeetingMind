package com.craftflowtechnologies.meetingmind.ai.llm

import com.craftflowtechnologies.meetingmind.core.common.MeetingTitleGenerator
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.TitleSource
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/** A title plus the evidence for how it was produced. */
data class RecordingTitle(val title: String, val source: TitleSource)

/**
 * The single decision point for a recording's title.
 *
 * A title is content, so the order is strict:
 * 1. a dedicated, validated model title (short prompt, one retry — see
 *    [MeetingIntelligenceEngine.suggestTitle]);
 * 2. the synthesis call's own title, when it is a real title and not merely the fallback the
 *    engine was handed;
 * 3. a deterministic title derived from real transcript content — intelligence topics/decisions
 *    first, then the first segment's leading phrase, then the most salient short phrase;
 * 4. only when there is no usable transcript at all, a localized, disambiguated "type — date".
 *
 * A `USER` or `IMPORT` title is returned unchanged: those are names a person chose, and no
 * automatic pass may replace them.
 */
object RecordingTitles {
    /** Content fallback titles stay short enough to read in a list row. */
    const val MAX_CONTENT_TITLE_LENGTH = 60

    /** How much of the transcript the dedicated title prompt sees. */
    private const val FIRST_CHARS = 1200
    private const val LAST_CHARS = 300
    private const val ELISION = "\n[...transcript continues...]\n"

    private val WHITESPACE = Regex("\\s+")
    private val WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*")
    private val LEADING_FILLERS = Regex(
        "(?i)^(um+|uh+|erm+|ah+|oh+|so|okay|ok|yeah|yep|right|well|like|you know|i mean|actually|basically|anyway)[,\\s]+"
    )
    private val STOPWORDS = setOf(
        "the", "and", "that", "this", "with", "have", "from", "they", "will", "would", "there",
        "their", "about", "what", "when", "where", "which", "your", "just", "like", "yeah",
        "okay", "right", "well", "going", "gonna", "really", "thing", "things", "kind", "sort",
        "some", "then", "than", "them", "were", "been", "being", "because", "into", "over",
        "also", "very", "much", "more", "most", "can", "could", "should", "does", "did", "for",
        "but", "not", "you", "our", "are", "was", "his", "her", "its", "all", "any", "out"
    )

    /**
     * Chooses the title for a recording. [transcriptTexts] is the real, displayable transcript in
     * order; [intelligence] is whatever the synthesis call produced, if anything.
     */
    fun resolve(
        existingTitle: String?,
        existingTitleSource: TitleSource,
        recordingType: RecordingType,
        createdAtMs: Long,
        transcriptTexts: List<String>,
        intelligence: MeetingSummary? = null,
        modelTitle: String? = null,
        /** The exact fallback string handed to the synthesis call. When the model echoes it back,
         * that is not evidence of a model title and must not be labelled `AI`. */
        synthesisFallbackTitle: String? = null,
        /** Titles of other same-type recordings from the same day, for date disambiguation. */
        siblingTitles: Set<String> = emptySet(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): RecordingTitle {
        // A person's own name, or a real imported file name, is never replaced automatically.
        if ((existingTitleSource == TitleSource.USER || existingTitleSource == TitleSource.IMPORT) &&
            !existingTitle.isNullOrBlank()
        ) {
            return RecordingTitle(existingTitle, existingTitleSource)
        }

        val dateFallbacks = setOf(
            MeetingTitleGenerator.deterministicFallbackTitle(recordingType, createdAtMs, locale, timeZone),
            MeetingTitleGenerator.deterministicFallbackTitle(
                recordingType, createdAtMs, locale, timeZone, includeStartTime = true
            )
        )

        // 1/2: a real model title. The synthesis title is only AI when it differs from the
        // fallback the engine was actually given — a model that omitted the field echoes that
        // fallback back verbatim.
        val modelCandidates = listOfNotNull(
            modelTitle?.let(::parseTitleCandidate),
            intelligence?.title?.takeUnless { it == synthesisFallbackTitle }?.let(::parseTitleCandidate)
        )
        modelCandidates.firstOrNull { it !in dateFallbacks }?.let {
            return RecordingTitle(it, TitleSource.AI)
        }

        // 3: real content, with no model involved.
        contentTitle(intelligence, transcriptTexts)?.let {
            return RecordingTitle(it, TitleSource.CONTENT)
        }

        // 4: no usable transcript content — the only case where a date alone is honest.
        return RecordingTitle(dateTitle(recordingType, createdAtMs, siblingTitles, locale, timeZone), TitleSource.DATE)
    }

    /** True when the transcript contains at least one actual word (not just punctuation/noise). */
    fun hasUsableWords(transcriptTexts: List<String>): Boolean =
        transcriptTexts.any { text -> text.any { it.isLetterOrDigit() } }

    /**
     * The title content fallback, deterministic and derived only from what was really said. Never
     * invents a phrase: every candidate is a verbatim (trimmed) piece of the intelligence output or
     * the transcript.
     */
    fun contentTitle(intelligence: MeetingSummary?, transcriptTexts: List<String>): String? {
        val candidates = mutableListOf<String>()
        intelligence?.topics?.forEach { candidates += it }
        intelligence?.decisions?.forEach { candidates += it.text }

        val phrases = transcriptTexts
            .mapNotNull { leadingPhrase(it) }
            .filter { it.isNotBlank() }
        candidates += phrases
        mostSalientPhrase(transcriptTexts)?.let { candidates += it }

        val cleaned = candidates.mapNotNull(::cleanContentCandidate).filter { it.isNotBlank() }
        // Prefer a candidate that actually says something rather than a placeholder word, but if
        // the transcript really only contains placeholder-like words, still return a content
        // title rather than dropping to a date one.
        return cleaned.firstOrNull { MeetingTitleGenerator.sanitizeAndValidate(it) != null }
            ?: cleaned.firstOrNull()
    }

    /**
     * The title shown to the synthesis call as its fallback — content-derived whenever there are
     * words, so an unparseable synthesis answer can only echo real content back, never a date.
     */
    fun provisionalTitle(
        recordingType: RecordingType,
        createdAtMs: Long,
        transcriptTexts: List<String>,
        intelligence: MeetingSummary? = null,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = contentTitle(intelligence, transcriptTexts)
        ?: MeetingTitleGenerator.deterministicFallbackTitle(recordingType, createdAtMs, locale, timeZone)

    /** The short, dedicated title prompt. [strict] is the one allowed retry. */
    fun titlePrompt(transcript: Transcript, recordingType: RecordingType, strict: Boolean = false): String {
        val full = transcript.segments.joinToString("\n") { (it.cleanedText ?: it.text).trim() }.trim()
        val excerpt = when {
            full.length <= FIRST_CHARS + LAST_CHARS -> full
            else -> full.take(FIRST_CHARS) + ELISION + full.takeLast(LAST_CHARS)
        }
        val focus = recordingType.focusGuidance().takeIf { it.isNotBlank() } ?: ""
        val strictLine = if (strict) {
            "Your previous answer was not usable. Return ONLY the title itself: no explanation, no labels, no quotes, no trailing punctuation."
        } else {
            "Return ONLY the title: no explanation, no labels, no quotes, no trailing punctuation."
        }
        return buildString {
            appendLine("Write a short, specific title for the recording transcript below.")
            appendLine("The title must be 3 to 8 words, in the language the recording is mostly spoken in.")
            appendLine("Describe what this recording is actually about, using only what the transcript says. Never invent names, topics or facts.")
            appendLine("Do not use a generic title such as \"Meeting\", \"Recording\", \"Team Meeting\" or \"Untitled\".")
            if (focus.isNotBlank()) appendLine("This is a ${recordingType.displayName}. $focus")
            appendLine(strictLine)
            appendLine()
            appendLine("Transcript excerpt:")
            append(excerpt)
        }.trim()
    }

    /**
     * Turns whatever the model returned for the title prompt into a validated title, or null.
     * Accepts either raw title text or a JSON object with a `title` field, stripping code fences,
     * labels and stray punctuation. Anything that does not look like a title is rejected rather
     * than shown.
     */
    fun parseTitleCandidate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw.replace("```json", "", ignoreCase = true).replace("```", "").trim()

        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching { JSONObject(text.substring(start, end + 1)).optString("title") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { text = it }
        }

        var title = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        title = title.trim().trimStart('-', '*', '#', '>', ' ').trim()
        title = title.removePrefix("Title:").removePrefix("title:").trim()
        title = title.trim('"', '\'', '`', ' ').trim()
        title = title.trimEnd('.', ',', ';', ':', '!', '?', '-', '—', ' ').trim()
        if (title.any { it == '{' || it == '}' || it == '[' || it == ']' }) return null
        if (title.count { it.isLetterOrDigit() } < 2) return null
        if (title.split(WHITESPACE).size > 12) return null
        return MeetingTitleGenerator.sanitizeAndValidate(title)
    }

    /** Local midnight-to-next-midnight bounds for [createdAtMs], in [timeZone]. */
    fun dayBounds(createdAtMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = createdAtMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val from = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return from to calendar.timeInMillis
    }

    private fun dateTitle(
        recordingType: RecordingType,
        createdAtMs: Long,
        siblingTitles: Set<String>,
        locale: Locale,
        timeZone: TimeZone
    ): String {
        val base = MeetingTitleGenerator.deterministicFallbackTitle(recordingType, createdAtMs, locale, timeZone)
        if (base !in siblingTitles) return base
        val withMinutes = MeetingTitleGenerator.deterministicFallbackTitle(
            recordingType, createdAtMs, locale, timeZone, includeStartTime = true
        )
        if (withMinutes !in siblingTitles) return withMinutes
        // Two recordings started in the same minute still must not share a title.
        val seconds = SimpleDateFormat("MMM d, HH:mm:ss", locale)
            .apply { this.timeZone = timeZone }
            .format(Date(createdAtMs))
        return "${recordingType.displayName} — $seconds"
    }

    /** The first few words of a segment, with leading filler and trailing punctuation removed. */
    private fun leadingPhrase(text: String): String? {
        var cleaned = text.replace(WHITESPACE, " ").trim()
        if (cleaned.isBlank()) return null
        while (true) {
            val match = LEADING_FILLERS.find(cleaned) ?: break
            cleaned = cleaned.removeRange(match.range).trim()
            if (cleaned.isBlank()) return null
        }
        val words = WORD.findAll(cleaned).map { it.value }.take(8).toList()
        if (words.isEmpty()) return null
        return words.joinToString(" ")
    }

    /**
     * A deterministic "most salient" phrase: the most frequent content word in the transcript,
     * with the words immediately around its first occurrence. Verbatim, never generated.
     */
    private fun mostSalientPhrase(transcriptTexts: List<String>): String? {
        val tokens = transcriptTexts
            .flatMap { text -> WORD.findAll(text.replace(WHITESPACE, " ")).map { it.value }.toList() }
        if (tokens.isEmpty()) return null
        val counts = tokens
            .filter { it.length >= 4 && it.lowercase(Locale.US) !in STOPWORDS }
            .groupingBy { it.lowercase(Locale.US) }.eachCount()
        val salient = counts.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            ?.key ?: return null
        val index = tokens.indexOfFirst { it.lowercase(Locale.US) == salient }
        if (index < 0) return null
        val from = (index - 2).coerceAtLeast(0)
        val to = (index + 3).coerceAtMost(tokens.size)
        return tokens.subList(from, to).joinToString(" ")
    }

    /** Trims a real phrase to a title-sized, sentence-cased string. Never adds words. */
    private fun cleanContentCandidate(raw: String): String? {
        var title = raw.replace(WHITESPACE, " ").trim()
        title = title.trim('"', '\'', '`', ' ', '-', '—', '•', '*')
        title = title.trimEnd('.', ',', ';', ':', '!', '?', '-', '—', ' ')
        if (title.isBlank()) return null
        if (title.count { it.isLetterOrDigit() } < 2) return null
        if (title.length > MAX_CONTENT_TITLE_LENGTH) {
            title = title.take(MAX_CONTENT_TITLE_LENGTH)
            val lastSpace = title.lastIndexOf(' ')
            if (lastSpace > MAX_CONTENT_TITLE_LENGTH / 2) title = title.substring(0, lastSpace)
            title = title.trimEnd('.', ',', ';', ':', '!', '?', '-', '—', ' ')
        }
        if (title.isBlank()) return null
        return title.replaceFirstChar { it.uppercase(Locale.getDefault()) }
    }
}
