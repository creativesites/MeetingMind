package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment

/** Pure text and picture helpers the Study view models share. */
object StudyFormat {

    private val courseCodeRegex = Regex("^\\s*([A-Za-z]{2,5})\\s?(\\d{2,4}[A-Za-z]?)\\b")

    /** "BIO 201" from "BIO201 Biochemistry" or "BIO 201: Biochemistry"; null when the name has no code. */
    fun courseCode(courseName: String?): String? {
        val match = courseName?.let { courseCodeRegex.find(it) } ?: return null
        return "${match.groupValues[1].uppercase()} ${match.groupValues[2].uppercase()}"
    }

    /** "BIO 201 · Inhibition": the course code (or name) and the concept, or the session title alone. */
    fun sourceLabel(courseName: String?, sessionTitle: String, conceptName: String?): String {
        val head = courseCode(courseName) ?: courseName?.takeIf { it.isNotBlank() } ?: sessionTitle
        return if (conceptName != null) "$head · $conceptName" else head
    }

    /** "1 h 14 min", or "14 min" under an hour. */
    fun durationLabel(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h h $m min" else "$m min"
    }

    /**
     * About [bars] bars, each 0..1, showing how much was said across the lecture. The recording
     * has no amplitude data here, so speech density stands in for the waveform. Empty when there is
     * no audio time or no words.
     */
    fun waveform(segments: List<TranscriptSegment>, durationMs: Long, bars: Int = 60): List<Float> {
        if (durationMs <= 0L || segments.isEmpty() || bars <= 0) return emptyList()
        val binMs = durationMs.toDouble() / bars
        val density = DoubleArray(bars)
        for (s in segments) {
            val start = s.startMs.coerceIn(0L, durationMs).toDouble()
            val end = s.endMs.coerceIn(0L, durationMs).toDouble()
            if (end <= start) continue
            val first = (start / binMs).toInt().coerceIn(0, bars - 1)
            val last = ((end - 1) / binMs).toInt().coerceIn(0, bars - 1)
            for (b in first..last) {
                val binStart = b * binMs
                val binEnd = binStart + binMs
                density[b] += (minOf(end, binEnd) - maxOf(start, binStart)).coerceAtLeast(0.0)
            }
        }
        val max = density.maxOrNull() ?: 0.0
        if (max <= 0.0) return List(bars) { MIN_BAR }
        return density.map { d -> (d / max).toFloat().coerceIn(MIN_BAR, 1f) }
    }

    private const val MIN_BAR = 0.05f
}
