package com.example.ai.tools

import com.example.core.model.TranscriptAiToolType
import com.example.core.model.TranscriptSegment

/**
 * Which part of the transcript a tool run applies to.
 *
 * A scope is a real constraint, not a hint: the engine is only ever given the segments the scope
 * selects, so a tool cannot quietly reach outside what the user asked it to work on.
 */
sealed interface ToolScope {
    data object WholeTranscript : ToolScope

    /** The segments the user selected. */
    data class Selection(val segmentIds: List<String>) : ToolScope

    /** Everything from a point in the recording onwards. */
    data class FromHereOn(val startMs: Long) : ToolScope

    /** Only one speaker's turns. */
    data class OneSpeaker(val speakerId: String) : ToolScope

    fun apply(segments: List<TranscriptSegment>): List<TranscriptSegment> = when (this) {
        is WholeTranscript -> segments
        is Selection -> segmentIds.toSet().let { wanted -> segments.filter { it.id in wanted } }
        is FromHereOn -> segments.filter { it.endMs >= startMs }
        is OneSpeaker -> segments.filter { it.speakerId == speakerId }
    }

    /** How the scope should be described in a result header. */
    fun describe(): String = when (this) {
        is WholeTranscript -> "Whole transcript"
        is Selection -> if (segmentIds.size == 1) "1 selected passage" else "${segmentIds.size} selected passages"
        is FromHereOn -> "From this point on"
        is OneSpeaker -> "One speaker"
    }
}

/**
 * One thing a tool found, with the transcript it was found in.
 *
 * [sourceSegmentIds] is the whole point. An extracted item that cannot be traced back to the
 * paragraph that produced it — and through it to the speaker turn, the word ids, the timestamps
 * and the audio — is an assertion the user has no way to check. Findings whose citations do not
 * resolve to real segments have them dropped (see [TranscriptToolEngine]), which leaves the
 * finding visibly uncited rather than falsely cited.
 */
data class ToolFinding(
    val text: String,
    val sourceSegmentIds: List<String> = emptyList(),
    /** Optional secondary line — a speaker name, a role, a category. Null when there is nothing
     * real to put here; never filled with a guess. */
    val detail: String? = null,
    /** Earliest timestamp among the cited segments, for jump-to-audio. Null when uncited. */
    val startMs: Long? = null
)

/** A proposed change to one segment's text, for the user to accept or reject. */
data class SegmentEdit(
    val segmentId: String,
    val before: String,
    val after: String
)

/**
 * What a tool run produced.
 *
 * Four shapes rather than one bag of strings, because the UI has to do genuinely different things
 * with them: a revision needs an accept/reject review, findings need citations that jump to audio,
 * a document needs to be readable and exportable, and a title suggestion needs one tap to apply.
 */
sealed interface ToolOutcome {
    /** Proposed text changes, never applied without the user accepting them. */
    data class TranscriptRevision(
        val edits: List<SegmentEdit>,
        /** Segments the engine proposed something for that validation rejected. Reported so the
         * result is honest about having left them alone rather than silently showing fewer rows. */
        val rejectedCount: Int = 0
    ) : ToolOutcome

    data class Findings(val items: List<ToolFinding>) : ToolOutcome

    data class TextDocument(val markdown: String) : ToolOutcome

    data class TitleSuggestion(val title: String) : ToolOutcome

    /** A widened set of segment ids — [TranscriptAiToolType.EXPAND_CONTEXT] is deterministic and
     * involves no model at all. */
    data class ContextExpansion(val segmentIds: List<String>) : ToolOutcome
}

/** A completed tool run: what was asked for, and what came back. */
data class ToolRunResult(
    val tool: TranscriptAiToolType,
    val scopeDescription: String,
    val outcome: ToolOutcome,
    /** Which engine produced this — a model id, or "deterministic" for tools that use no model. */
    val engine: String
)

/** Serializes a [ToolScope] into an [com.example.core.database.AiJobEntity]'s input payload. */
object ToolScopeJson {

    fun encode(scope: ToolScope): org.json.JSONObject = org.json.JSONObject().apply {
        when (scope) {
            is ToolScope.WholeTranscript -> put("scope", "whole")
            is ToolScope.Selection -> {
                put("scope", "selection")
                put("segmentIds", org.json.JSONArray(scope.segmentIds))
            }
            is ToolScope.FromHereOn -> {
                put("scope", "fromHere")
                put("startMs", scope.startMs)
            }
            is ToolScope.OneSpeaker -> {
                put("scope", "speaker")
                put("speakerId", scope.speakerId)
            }
        }
    }

    /**
     * Falls back to the whole transcript for anything unrecognised. That is the safe direction:
     * a tool running over more of the transcript than intended produces a result the user can
     * ignore, whereas one silently running over nothing looks like the transcript had nothing
     * in it.
     */
    fun decode(payload: org.json.JSONObject): ToolScope = when (payload.optString("scope")) {
        "selection" -> {
            val array = payload.optJSONArray("segmentIds")
            val ids = (0 until (array?.length() ?: 0)).map { array!!.optString(it) }.filter { it.isNotBlank() }
            if (ids.isEmpty()) ToolScope.WholeTranscript else ToolScope.Selection(ids)
        }
        "fromHere" -> ToolScope.FromHereOn(payload.optLong("startMs"))
        "speaker" -> payload.optString("speakerId").takeIf { it.isNotBlank() }
            ?.let { ToolScope.OneSpeaker(it) } ?: ToolScope.WholeTranscript
        else -> ToolScope.WholeTranscript
    }
}
