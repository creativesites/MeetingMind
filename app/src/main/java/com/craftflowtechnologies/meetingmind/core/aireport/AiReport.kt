package com.craftflowtechnologies.meetingmind.core.aireport

import org.json.JSONObject
import java.util.UUID

/**
 * Reporting AI-written content to the developer (Google Play's AI-Generated Content policy).
 * Design: `design/ai-content-reporting.md`.
 *
 * Everything in this file is plain Kotlin so the privacy rules can be tested on the JVM.
 */

/** Which feature wrote the content. [wire] is what the server receives; it never changes once shipped. */
enum class AiFeature(val wire: String) {
    NOTE_BLOCK("note_block"),
    LECTURE_NOTES("lecture_notes"),
    MEETING_SUMMARY("meeting_summary"),
    ASK_RECORDING("ask_recording"),
    ASK_LEARNING("ask_learning"),
    PRACTICE("practice"),
    ASSISTANT("assistant"),
    ASK_EVERYTHING("ask_everything"),
    WORK_ASK("work_ask"),
    WORK_BRIEF("work_brief"),
    WORK_PULSE("work_pulse"),
    DEEP_SYNTHESIS("deep_synthesis"),
    DEVOTIONAL("devotional"),
    PRAYER("prayer"),
    SERMON("sermon"),
    BIBLE_STUDY("bible_study"),
    CIRCLE_BRIEF("circle_brief"),
    HOME_STORY("home_story"),
    YOUTUBE_IMPORT("youtube_import"),
}

enum class AiReportReason(val wire: String, val label: String) {
    OFFENSIVE("offensive", "Offensive"),
    HARMFUL("harmful", "Harmful or unsafe"),
    MISLEADING("misleading", "False or misleading"),
    OTHER("other", "Something else"),
}

/**
 * What a screen knows about the content being reported. [noteId] and [meetingId] are used on the
 * phone only, to decide whether the report may leave it; they are never put in a report.
 */
data class AiReportSource(
    val feature: AiFeature,
    val text: String,
    /** The model that wrote it, when the screen knows. Otherwise the current route is used. */
    val modelId: String? = null,
    val noteId: String? = null,
    val meetingId: String? = null,
    /** The screen already knows this content must stay on the phone (e.g. a confidential project). */
    val mustStayOnDevice: Boolean = false,
    /** Further notes and recordings the content was drawn from (e.g. a synthesis across sessions). Phone only. */
    val moreNoteIds: List<String> = emptyList(),
    val moreMeetingIds: List<String> = emptyList(),
)

/** The privacy facts that decide whether a report may be uploaded. */
data class ReportPrivacy(
    /** Processing is "Private and on-device" (`ProcessingProfile.OFFLINE`). */
    val onDeviceMode: Boolean = false,
    /** The note is marked private. */
    val privateNote: Boolean = false,
    /** Confidential Work, or Work settings keep it on the phone (`WorkPrivacy.mustStayOnDevice`). */
    val keepOnDevice: Boolean = false,
)

sealed interface ReportDelivery {
    data object Upload : ReportDelivery
    /** The report never leaves the phone; the person may copy it. [why] completes "This stays on your phone because …". */
    data class CopyOnly(val why: String) : ReportDelivery
}

object AiReportPolicy {
    fun delivery(p: ReportPrivacy): ReportDelivery = when {
        p.keepOnDevice -> ReportDelivery.CopyOnly("this work is kept on your phone")
        p.privateNote -> ReportDelivery.CopyOnly("this note is private")
        p.onDeviceMode -> ReportDelivery.CopyOnly("you use private, on-device mode")
        else -> ReportDelivery.Upload
    }
}

/** One report, exactly as it will be sent or copied. Nothing else about the note goes with it. */
data class AiReport(
    val reportId: String,
    val feature: AiFeature,
    val reason: AiReportReason,
    val comment: String?,
    val text: String,
    val model: String,
    val appVersion: String,
    val createdAt: Long,
) {
    companion object {
        const val MAX_TEXT = 4_000
        const val MAX_COMMENT = 500
        const val MAX_MODEL = 80
        const val MAX_VERSION = 40
        const val CUT_MARK = " […]"
        const val UNKNOWN_MODEL = "unknown"

        /** Payload key on a note block hidden after its report (read by the note editor). */
        const val PAYLOAD_HIDDEN_AFTER_REPORT = "hiddenAfterReport"

        /** The keys a report carries, and the only ones: a test pins this. */
        val KEYS = listOf("reportId", "feature", "reason", "comment", "text", "model", "appVersion", "createdAt")

        fun create(
            source: AiReportSource,
            reason: AiReportReason,
            comment: String?,
            fallbackModel: String?,
            appVersion: String,
            now: Long,
            id: String = UUID.randomUUID().toString(),
        ) = AiReport(
            reportId = id,
            feature = source.feature,
            reason = reason,
            comment = comment?.trim()?.take(MAX_COMMENT)?.takeIf { it.isNotEmpty() },
            text = cut(source.text.trim()),
            model = (source.modelId ?: fallbackModel)?.trim()?.take(MAX_MODEL)?.takeIf { it.isNotEmpty() } ?: UNKNOWN_MODEL,
            appVersion = appVersion.trim().take(MAX_VERSION),
            createdAt = now,
        )

        private fun cut(text: String): String =
            if (text.length <= MAX_TEXT) text else text.take(MAX_TEXT - CUT_MARK.length).trimEnd() + CUT_MARK

        fun fromJson(o: JSONObject): AiReport? = runCatching {
            AiReport(
                reportId = o.getString("reportId"),
                feature = AiFeature.entries.first { it.wire == o.getString("feature") },
                reason = AiReportReason.entries.first { it.wire == o.getString("reason") },
                comment = if (o.has("comment")) o.getString("comment") else null,
                text = o.getString("text"),
                model = o.getString("model"),
                appVersion = o.getString("appVersion"),
                createdAt = o.getLong("createdAt"),
            )
        }.getOrNull()
    }

    /** The body sent to the server, and what the queue stores. Only [KEYS]. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("reportId", reportId)
        put("feature", feature.wire)
        put("reason", reason.wire)
        comment?.let { put("comment", it) }
        put("text", text)
        put("model", model)
        put("appVersion", appVersion)
        put("createdAt", createdAt)
    }

    /** The same facts as plain text, for "Copy report" when the report must stay on the phone. */
    fun copyText(supportEmail: String? = null): String = buildString {
        appendLine("MeetingMind: report of AI-written content")
        appendLine("Reason: ${reason.label}")
        comment?.let { appendLine("Comment: $it") }
        appendLine("Feature: ${feature.wire}")
        appendLine("Model: $model")
        appendLine("App version: $appVersion")
        appendLine("Report id: $reportId")
        appendLine()
        appendLine("Reported text:")
        appendLine(text)
        supportEmail?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            append("Send to: $it")
        }
    }.trimEnd()
}
