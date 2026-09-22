package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.core.model.AskPersonalizationContext
import com.example.core.model.ChatMessage
import com.example.core.model.MeetingSummary
import com.example.core.model.RecordingType
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment

/**
 * Tries a preferred engine and falls back to another when the first cannot produce a result.
 *
 * Internet mode used to hand intelligence to the cloud engine alone. If Gemini was unreachable —
 * no key yet, quota spent, no signal in a church basement — the recording finished with a
 * transcript and *no summary at all*, even on a phone with a perfectly good local model
 * installed. That is the wrong failure: a local summary is worse than a cloud one, but it is a
 * great deal better than nothing.
 *
 * The fallback only happens on a failure. A successful cloud result is never second-guessed, and
 * nothing here runs both engines and picks the nicer answer.
 *
 * @param primary Usually the cloud engine.
 * @param fallback Usually the on-device engine. Null means there is nothing to fall back to.
 */
class FallbackMeetingIntelligenceEngine(
    private val primary: MeetingIntelligenceEngine,
    private val fallback: MeetingIntelligenceEngine?
) : MeetingIntelligenceEngine {

    override suspend fun processMeeting(
        transcript: Transcript,
        meetingTitle: String,
        recordingType: RecordingType,
        customContext: String?
    ): AiResult<MeetingSummary> {
        val first = primary.processMeeting(transcript, meetingTitle, recordingType, customContext)
        if (first is AiResult.Success || fallback == null) return first
        return fallback.processMeeting(transcript, meetingTitle, recordingType, customContext)
    }

    override suspend fun askMeeting(
        question: String,
        transcript: Transcript,
        relevantSegments: List<TranscriptSegment>,
        personalization: AskPersonalizationContext
    ): AiResult<ChatMessage> {
        val first = primary.askMeeting(question, transcript, relevantSegments, personalization)
        if (first is AiResult.Success || fallback == null) return first
        return fallback.askMeeting(question, transcript, relevantSegments, personalization)
    }
}
