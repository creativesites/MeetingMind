package com.example.core.common

import com.example.core.model.RecordingType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Title generation is folded into [com.example.ai.llm.RealMeetingIntelligenceEngine.processMeeting]'s
 * single structured-JSON synthesis call rather than run as a second, separate LLM pass purely for
 * a title — see that class's synthesis prompt, which already asks for `{"title": ...}` alongside
 * the summary. This object provides the two pure, non-LLM pieces around that: a deterministic
 * fallback for when no title is available at all, and validation for a real title candidate the
 * model did produce.
 */
object MeetingTitleGenerator {
    private const val MAX_TITLE_LENGTH = 80

    // Titles that carry no real information about this specific recording — using one of these
    // would be technically "real LLM output" but practically as useless as a placeholder, so it's
    // rejected in favor of the deterministic fallback instead.
    private val GENERIC_TITLES = setOf(
        "meeting", "meeting summary", "untitled", "untitled meeting", "team meeting",
        "general", "recording", "voice memo", "conversation", "new recording"
    )

    /**
     * A real, non-fabricated fallback title built only from data actually known about the
     * recording — its type and real creation date — never from guessed transcript content. Used
     * whenever no local LLM is installed, or the model's own title candidate doesn't pass
     * [sanitizeAndValidate].
     */
    fun deterministicFallbackTitle(recordingType: RecordingType, createdAtMs: Long): String {
        val date = SimpleDateFormat("MMM d", Locale.US).format(Date(createdAtMs))
        return "${recordingType.displayName} — $date"
    }

    /**
     * Cleans up a real LLM-generated title candidate and rejects it (returning null) rather than
     * using something not actually useful: blank, wrapped in stray quotes, a generic placeholder
     * that carries no real information about the recording, or absurdly long output. Callers must
     * fall back to [deterministicFallbackTitle] when this returns null — never fabricate a
     * replacement here.
     */
    fun sanitizeAndValidate(rawTitle: String?): String? {
        if (rawTitle == null) return null
        var title = rawTitle.trim()
        if (title.length >= 2) {
            val first = title.first()
            val last = title.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                title = title.substring(1, title.length - 1).trim()
            }
        }
        if (title.isBlank()) return null
        if (title.lowercase(Locale.US) in GENERIC_TITLES) return null
        if (title.length > MAX_TITLE_LENGTH) title = title.take(MAX_TITLE_LENGTH).trim()
        return title
    }
}
