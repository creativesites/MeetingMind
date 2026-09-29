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

    fun detect(segments: List<TranscriptSegment>): List<DetectedScripture> {
        val out = mutableListOf<DetectedScripture>()
        // The passage being spoken about, for "…and in verse 38" said a paragraph later.
        var current: Pair<ScriptureReference, Long>? = null
        for (segment in segments) {
            val text = segment.cleanedText ?: segment.text
            val matches = ScriptureReferenceParser.findAll(text)
            val carried = current?.takeIf { segment.startMs - it.second <= CARRY_MS }?.first
            matches.forEach { m -> out += DetectedScripture(m.reference, segment.id, momentOf(segment, text, m.start), m.text) }
            out += looseVerses(segment, text, matches, carried)
            (matches.lastOrNull()?.reference ?: carried)?.let { current = it to segment.endMs }
        }
        return out
    }

    private const val CARRY_MS = 90_000L

    private val LOOSE_VERSE = Regex("\\bverses?\\s+(\\d{1,3})(?:\\s*(?:to|through|thru|-|–)\\s*(\\d{1,3}))?", RegexOption.IGNORE_CASE)

    /**
     * "Open to Romans chapter 8 and look at verse 28": the verse is said a few words after the
     * chapter, or later ("…and in verse 38 to 39…"). A "verse N" that the parser didn't already
     * take belongs to the reference said just before it — in the paragraph, or in the last 90 seconds —
     * and only when that chapter really has the verse. With nothing said before it, nothing is added.
     */
    private fun looseVerses(segment: TranscriptSegment, text: String, matches: List<ScriptureMatch>, carried: ScriptureReference?): List<DetectedScripture> =
        LOOSE_VERSE.findAll(text).mapNotNull { m ->
            if (matches.any { m.range.first in it.start until it.end }) return@mapNotNull null
            val ref = matches.lastOrNull { it.end <= m.range.first }?.reference ?: carried ?: return@mapNotNull null
            val start = m.groupValues[1].toInt()
            val end = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
            val max = ref.book.verseCount(ref.chapter) ?: return@mapNotNull null
            if (start !in 1..max || (end != null && (end !in start..max))) return@mapNotNull null
            val loose = ScriptureReference(ref.book, ref.chapter, start, end?.takeIf { it != start })
            if (matches.any { it.reference == loose }) return@mapNotNull null
            DetectedScripture(loose, segment.id, momentOf(segment, text, m.range.first), m.value)
        }.toList()

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
