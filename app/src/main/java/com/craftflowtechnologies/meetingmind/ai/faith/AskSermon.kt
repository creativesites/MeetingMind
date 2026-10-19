package com.craftflowtechnologies.meetingmind.ai.faith

import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment

/**
 * Grounding for Ask on a faith recording (Faith spec slice D): passages go to the model with the
 * same `[mm:ss]` labels the transcript shows, and what comes back is checked against them —
 * a marker that matches no passage is removed rather than shown as a chip that jumps nowhere.
 * Pure, so the eval cases in AskSermonTest run without a model.
 */
object AskSermon {
    val FAITH_TYPES = setOf(RecordingType.SERMON, RecordingType.BIBLE_STUDY, RecordingType.DEVOTIONAL, RecordingType.TESTIMONY, RecordingType.PRAYER, RecordingType.REFLECTION)

    fun isFaith(type: RecordingType?) = type in FAITH_TYPES

    private val MARKER = Regex("\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)\\]")

    /** The label the transcript and the answer chips share — "mm:ss", or "hh:mm:ss" past an hour. */
    fun marker(ms: Long): String = Formatters.formatDurationHms(ms)

    fun render(passages: List<TranscriptSegment>): String = passages.joinToString("\n") { p ->
        "[${marker(p.startMs)}] ${p.speakerName ?: "Speaker"}: ${(p.cleanedText ?: p.text).trim()}"
    }

    data class Grounded(
        val text: String,
        val cited: List<TranscriptSegment>,
        /** Markers the model wrote that matched no passage, removed from [text]. */
        val dropped: List<String>,
        /** Sentences in the model's own voice that claim to speak for God, removed from [text]. */
        val authorityViolations: List<String>,
        /** True when the answer says the recording doesn't cover the question. */
        val notCovered: Boolean
    ) {
        /** Claims about the recording with nothing to check them against. */
        val unverified get() = cited.isEmpty() && !notCovered
    }

    fun ground(answer: String, passages: List<TranscriptSegment>): Grounded {
        val byLabel = passages.associateBy { marker(it.startMs) }
        val cited = LinkedHashMap<String, TranscriptSegment>()
        val dropped = mutableListOf<String>()
        var text = MARKER.replace(answer) { m ->
            val key = normalise(m.groupValues[1])
            val seg = byLabel[key]
            if (seg != null) { cited[key] = seg; "[$key]" } else { dropped += m.groupValues[1]; "" }
        }
        val violations = FaithAuthorityGuard.violations(text)
        text = FaithAuthorityGuard.clean(text)
        text = text.replace(Regex("[ \\t]+([.,;:!?])"), "$1").replace(Regex("[ \\t]{2,}"), " ").trim()
        val notCovered = Regex("(doesn'?t|does not|didn'?t|did not) (seem to )?(cover|mention|say|address)", RegexOption.IGNORE_CASE).containsMatchIn(text)
        return Grounded(text, cited.values.toList(), dropped, violations, notCovered)
    }

    /** "05:07" and "5:07" are the same moment; the transcript writes two-digit minutes. */
    private fun normalise(label: String): String {
        val parts = label.split(":").map { it.toInt() }
        return if (parts.size == 3) "%02d:%02d:%02d".format(parts[0], parts[1], parts[2]) else "%02d:%02d".format(parts[0], parts[1])
    }
}
