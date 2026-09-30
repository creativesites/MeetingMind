package com.craftflowtechnologies.meetingmind.core.model

/**
 * Something a recording says, beyond the four lists: a promise, a decision, a risk, a date. It
 * always cites the paragraphs it came from; one that cites nothing real is dropped.
 *
 * [kind] is a [com.craftflowtechnologies.meetingmind.core.work.ItemKind] name. [value] is a date for
 * DEADLINE, a number for METRIC, "PROPOSED" for a decision only proposed, or a small JSON object
 * (speaker, counterparty, due) for a commitment.
 */
data class Signal(
    val id: String,
    val meetingId: String,
    val kind: String,
    val text: String,
    val sourceSegmentIds: List<String>,
    val value: String? = null,
    val confidence: Float = DEFAULT_CONFIDENCE,
    /** The speaker it is about, when the model named one that is in the recording. */
    val speakerId: String? = null
) {
    companion object { const val DEFAULT_CONFIDENCE = 0.6f }
}
