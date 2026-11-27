package com.craftflowtechnologies.meetingmind.core.common

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The two pure, non-LLM pieces around title generation: validation for a real title candidate,
 * and the date fallback used only when a recording has no usable transcript content.
 *
 * Titles themselves are produced by [com.craftflowtechnologies.meetingmind.ai.llm.RecordingTitles],
 * which prefers a dedicated short model call, then the synthesis title, then a deterministic
 * content-derived title, and only then the date fallback here.
 */
object MeetingTitleGenerator {
    private const val MAX_TITLE_LENGTH = 80

    // Titles that carry no real information about this specific recording — using one of these
    // would be technically "real LLM output" but practically as useless as a placeholder, so it's
    // rejected in favor of a content-derived title instead.
    private val GENERIC_TITLES = setOf(
        "meeting", "meeting summary", "untitled", "untitled meeting", "untitled recording",
        "team meeting", "general", "recording", "voice memo", "conversation", "new recording",
        "audio", "audio recording", "video", "import"
    )

    // File names that say nothing about the content — an imported recorder's "AUD_0042",
    // "IMG_1234", "recording", or a bare date. These must not be frozen as an IMPORT title;
    // leaving the source as AI lets processing replace them with a real one.
    private val UNUSABLE_IMPORT_NAME = Regex(
        "(?i)^(audio|aud|recording|record|voice[ _-]?memo|new[ _-]?recording|untitled|track|file|" +
            "vid|video|clip|img|image|mov|m4a|mp3|wav)[ _-]*\\d*$"
    )

    /**
     * A localized "type — date" title. Used only when a recording has no usable transcript
     * content (silence, or no words were recognised), never as a substitute for a title derived
     * from what was actually said.
     *
     * The date follows the device [Locale] and [TimeZone], not a hardcoded US format. The type
     * label comes from [RecordingType.displayName]; where the app later adds a localized resource
     * for a type, that resource should be passed in here instead of the enum's English label.
     *
     * [includeStartTime] adds the local start time (`MMM d, HH:mm`), which is how two recordings
     * of the same type on the same day are kept from sharing the identical title.
     */
    fun deterministicFallbackTitle(
        recordingType: RecordingType,
        createdAtMs: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
        includeStartTime: Boolean = false
    ): String {
        val pattern = if (includeStartTime) "MMM d, HH:mm" else "MMM d"
        val date = SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(Date(createdAtMs))
        return "${recordingType.displayName} — $date"
    }

    /**
     * Cleans up a real title candidate and rejects it (returning null) rather than using something
     * not actually useful: blank, wrapped in stray quotes, a generic placeholder that carries no
     * real information about the recording, or absurdly long output. Callers must derive a
     * content or date title themselves when this returns null — never fabricate a replacement
     * here.
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

    /**
     * Whether an imported file's own name is specific enough to keep as an `IMPORT` title. A name
     * that is generic, a bare camera/recorder counter, or only digits is not content, so the
     * recording keeps the AI source and processing is allowed to give it a real title.
     */
    fun isUsableImportedTitle(rawFileName: String?): Boolean {
        val title = sanitizeAndValidate(rawFileName) ?: return false
        if (title.length < 3) return false
        if (title.count { it.isLetter() } < 3) return false
        if (UNUSABLE_IMPORT_NAME.matches(title)) return false
        // A bare date or timestamp is a file's metadata, not a name.
        val digits = title.count { it.isDigit() }
        if (digits >= title.length / 2) return false
        return true
    }
}
