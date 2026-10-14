package com.craftflowtechnologies.meetingmind.core.capture

import com.craftflowtechnologies.meetingmind.core.work.Mark
import org.json.JSONObject

/**
 * What the person told MeetingMind about one recording before it was made (spec §5.1): an
 * immutable snapshot, versioned so an older build can still read it. Fields this build does not
 * know are kept in [extra] and written back unchanged, so a newer build's data survives a round trip.
 */
data class CaptureContextSnapshot(
    val workflow: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val fields: Map<String, String> = emptyMap(),
    val customFocus: String? = null,
    val speakerCountPreference: Int? = null,
    val sourceTitle: String? = null,
    val capturedAt: Long,
    val extra: JSONObject = JSONObject()
) {
    fun toJson(): String = JSONObject(extra.toString()).apply {
        put(K_WORKFLOW, workflow)
        put(K_VERSION, schemaVersion)
        put(K_FIELDS, JSONObject(fields as Map<*, *>))
        customFocus?.let { put(K_FOCUS, it) }
        speakerCountPreference?.let { put(K_SPEAKERS, it) }
        sourceTitle?.let { put(K_TITLE, it) }
        put(K_CAPTURED_AT, capturedAt)
    }.toString()

    companion object {
        const val SCHEMA_VERSION = 1
        private const val K_WORKFLOW = "workflow"
        private const val K_VERSION = "schemaVersion"
        private const val K_FIELDS = "fields"
        private const val K_FOCUS = "customFocus"
        private const val K_SPEAKERS = "speakerCountPreference"
        private const val K_TITLE = "sourceTitle"
        private const val K_CAPTURED_AT = "capturedAt"
        private val KNOWN = setOf(K_WORKFLOW, K_VERSION, K_FIELDS, K_FOCUS, K_SPEAKERS, K_TITLE, K_CAPTURED_AT)

        /** Null for missing or unreadable JSON: a bad snapshot must never stop a recording from processing. */
        fun fromJson(json: String?): CaptureContextSnapshot? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                val fieldsObj = o.optJSONObject(K_FIELDS) ?: JSONObject()
                val fields = fieldsObj.keys().asSequence().associateWith { fieldsObj.optString(it) }
                val extra = JSONObject()
                o.keys().forEach { if (it !in KNOWN) extra.put(it, o.get(it)) }
                CaptureContextSnapshot(
                    workflow = o.optString(K_WORKFLOW),
                    schemaVersion = o.optInt(K_VERSION, 1),
                    fields = fields,
                    customFocus = o.optString(K_FOCUS).ifBlank { null },
                    speakerCountPreference = if (o.has(K_SPEAKERS)) o.optInt(K_SPEAKERS) else null,
                    sourceTitle = o.optString(K_TITLE).ifBlank { null },
                    capturedAt = o.optLong(K_CAPTURED_AT),
                    extra = extra
                )
            }.getOrNull()
        }
    }
}

/** One recording's transcript segment, with the ids and local timestamps the model must cite. */
data class PackSegment(val id: String, val meetingId: String, val startMs: Long, val text: String)

/** One recording's marks, with the stable id each mark keeps in the note. */
data class PackMark(val id: String, val meetingId: String, val mark: Mark, val nearbyText: String? = null)

/**
 * Builds the context the model receives, in the fixed order of spec §5.2. Every section is
 * labelled, so user-supplied context is never mistaken for something the speaker said.
 *
 * - Transcript segments and marks stay in separate sections; a mark's words never join the transcript.
 * - Marks are ordered by recording, then local time, so the order is stable for the same input.
 * - Nothing is truncated here. Budgeting across a long note is a separate, source-aware step.
 */
object CapturePack {
    const val FIDELITY = "Transcript text is the only evidence of what was said. Do not put words in anyone's mouth."
    const val MARKER_RULE = "A mark is the person's own signal of importance. It does not prove that any claim is true. A mark's text is the person's note, not speech."

    fun build(
        outputSchema: String,
        focus: String?,
        sharedNote: Map<String, String>,
        snapshots: List<Pair<String, CaptureContextSnapshot>>,
        marks: List<PackMark>,
        segments: List<PackSegment>
    ): String = buildString {
        section("Rules", FIDELITY + "\n" + MARKER_RULE)
        section("Output", outputSchema)
        focus?.takeIf { it.isNotBlank() }?.let { section("Custom focus (instruction, not transcript)", it.trim()) }
        section(
            "Note context (user-provided metadata, not transcript evidence)",
            sharedNote.entries.filter { it.value.isNotBlank() }.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { null }
        )
        snapshots.forEach { (meetingId, s) ->
            val lines = buildList {
                s.sourceTitle?.let { add("title: $it") }
                s.speakerCountPreference?.let { add("speakers: $it") }
                s.fields.entries.filter { it.value.isNotBlank() }.forEach { add("${it.key}: ${it.value}") }
            }
            section("Recording $meetingId context (user-provided metadata, not transcript evidence)", lines.joinToString("\n").ifBlank { null })
        }
        val orderedMarks = marks.sortedWith(compareBy({ it.meetingId }, { it.mark.atMs }, { it.id }))
        section(
            "Marks from the person (ordered by recording, then time)",
            orderedMarks.joinToString("\n") { m ->
                val text = m.mark.text?.let { " text=\"$it\"" }.orEmpty()
                val near = m.nearbyText?.let { " nearby=\"$it\"" }.orEmpty()
                "[${m.id}] recording=${m.meetingId} at=${m.mark.atMs}ms kind=${m.mark.kind.name}$text$near"
            }.ifBlank { null }
        )
        val orderedSegments = segments.sortedWith(compareBy({ it.meetingId }, { it.startMs }, { it.id }))
        section(
            "Transcript (evidence)",
            orderedSegments.joinToString("\n") { "[${it.id}] recording=${it.meetingId} at=${it.startMs}ms: ${it.text}" }.ifBlank { null }
        )
    }.trimEnd()

    private fun StringBuilder.section(title: String, body: String?) {
        if (body == null) return
        append("## ").append(title).append('\n').append(body).append("\n\n")
    }
}
