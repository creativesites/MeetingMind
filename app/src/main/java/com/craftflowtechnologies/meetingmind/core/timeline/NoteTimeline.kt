package com.craftflowtechnologies.meetingmind.core.timeline

/** One recording of a note: its id and its local duration, in the note's own order. */
data class SourceSpan(val meetingId: String, val durationMs: Long)

/** A position in one recording's own clock. Never a note-wide position. */
data class SourcePosition(val meetingId: String, val localMs: Long)

/**
 * The note-wide timeline (spec §7.2–7.4): every part of a note laid end to end in the note's
 * order. Positions are derived, never stored: a part's offset is the sum of the durations before
 * it, so reordering parts changes note-wide positions and nothing local.
 *
 * Both directions live here so no caller converts times by hand.
 */
object NoteTimeline {

    /** The note-wide start of each part, keyed by meeting id. Throws if a part is listed twice. */
    fun offsets(sources: List<SourceSpan>): Map<String, Long> {
        val seen = HashSet<String>()
        var start = 0L
        return sources.associate { span ->
            require(seen.add(span.meetingId)) { "recording ${span.meetingId} is listed twice" }
            val offset = start
            start += span.durationMs.coerceAtLeast(0L)
            span.meetingId to offset
        }
    }

    /** The note-wide time of a local position, or null when that recording is not part of the note. */
    fun toNoteTime(sources: List<SourceSpan>, position: SourcePosition): Long? {
        val offset = offsets(sources)[position.meetingId] ?: return null
        return offset + position.localMs
    }

    /**
     * The recording and local position behind a note-wide time, or null when the time lies outside
     * the note. A part with zero length owns no time. The very end of the note maps to the end of
     * its last part.
     */
    fun toSource(sources: List<SourceSpan>, noteMs: Long): SourcePosition? {
        if (noteMs < 0) return null
        var start = 0L
        var lastNonEmpty: SourcePosition? = null
        for (span in sources) {
            val length = span.durationMs.coerceAtLeast(0L)
            if (length > 0) {
                lastNonEmpty = SourcePosition(span.meetingId, length)
                if (noteMs < start + length) return SourcePosition(span.meetingId, noteMs - start)
            }
            start += length
        }
        return if (noteMs == start) lastNonEmpty else null
    }
}
