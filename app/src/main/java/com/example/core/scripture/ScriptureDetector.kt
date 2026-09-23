package com.example.core.scripture

import com.example.core.model.TranscriptSegment

/** A reference heard in a recording: what, in which paragraph, and when. */
data class DetectedScripture(
    val reference: ScriptureReference,
    val segmentId: String,
    val startMs: Long,
    val heardAs: String
)

/** A reference with every moment it was mentioned, for the sermon's scripture list. */
data class ScriptureMention(val reference: ScriptureReference, val occurrences: List<DetectedScripture>) {
    val first: DetectedScripture get() = occurrences.first()
}

/**
 * Finds scripture references in a transcript (design spec §5.2): deterministic, and every hit is
 * anchored to a transcript segment and a moment in the audio, so tapping it plays where it was
 * said — the same evidence standard as every other citation in the app.
 */
object ScriptureDetector {

    fun detect(segments: List<TranscriptSegment>): List<DetectedScripture> = segments.flatMap { segment ->
        val text = segment.cleanedText ?: segment.text
        ScriptureReferenceParser.findAll(text).map { match ->
            DetectedScripture(match.reference, segment.id, momentOf(segment, text, match.start), match.text)
        }
    }

    /**
     * Groups repeats of the same reference (a preacher often reads a verse, then comes back to it)
     * in order of first mention. A range and a verse inside it count as the same passage only when
     * they are identical — "John 3:16" and "John 3:16–18" stay separate, as they were said.
     */
    fun mentions(detections: List<DetectedScripture>): List<ScriptureMention> =
        detections.groupBy { it.reference }
            .map { (ref, list) -> ScriptureMention(ref, list.sortedBy { it.startMs }) }
            .sortedBy { it.first.startMs }

    /**
     * Where in the segment the reference was said: its share of the text, applied to the segment's
     * duration. Speech runs at a roughly even pace within a paragraph, so this lands within a few
     * seconds — close enough to start playback just before the reading.
     */
    private fun momentOf(segment: TranscriptSegment, text: String, charOffset: Int): Long {
        if (text.isEmpty() || segment.endMs <= segment.startMs) return segment.startMs
        val fraction = charOffset.toDouble() / text.length
        return segment.startMs + ((segment.endMs - segment.startMs) * fraction).toLong()
    }
}
