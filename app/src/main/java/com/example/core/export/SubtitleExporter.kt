package com.example.core.export

import com.example.core.model.TranscriptSegment
import java.util.Locale

/**
 * Real SRT/WebVTT export (docs/recording-page-implementation.md §3.7 item 39) — the professional
 * output the whole AI toolkit exists to produce a clean transcript for. One cue per persisted
 * segment; segment text is used verbatim (whatever the transcript tab currently shows as the best
 * available reading text — [TranscriptSegment.cleanedText] if present, else [TranscriptSegment.text]),
 * never rewritten for this export.
 */
object SubtitleExporter {

    fun srt(segments: List<TranscriptSegment>, includeSpeakerLabels: Boolean = true): String = buildString {
        segments.sortedBy { it.startMs }.forEachIndexed { index, seg ->
            appendLine(index + 1)
            appendLine("${srtTimestamp(seg.startMs)} --> ${srtTimestamp(seg.endMs)}")
            appendLine(cueText(seg, includeSpeakerLabels))
            appendLine()
        }
    }

    fun vtt(segments: List<TranscriptSegment>, includeSpeakerLabels: Boolean = true): String = buildString {
        appendLine("WEBVTT")
        appendLine()
        segments.sortedBy { it.startMs }.forEach { seg ->
            appendLine("${vttTimestamp(seg.startMs)} --> ${vttTimestamp(seg.endMs)}")
            appendLine(cueText(seg, includeSpeakerLabels))
            appendLine()
        }
    }

    private fun cueText(seg: TranscriptSegment, includeSpeakerLabels: Boolean): String {
        val text = seg.cleanedText ?: seg.text
        return if (includeSpeakerLabels && seg.speakerName != null) "${seg.speakerName}: $text" else text
    }

    private fun srtTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0)
        val hours = clamped / 3_600_000
        val minutes = (clamped % 3_600_000) / 60_000
        val seconds = (clamped % 60_000) / 1000
        val millis = clamped % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun vttTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0)
        val hours = clamped / 3_600_000
        val minutes = (clamped % 3_600_000) / 60_000
        val seconds = (clamped % 60_000) / 1000
        val millis = clamped % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
