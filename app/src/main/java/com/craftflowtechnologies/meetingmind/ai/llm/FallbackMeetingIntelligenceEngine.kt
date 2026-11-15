package com.craftflowtechnologies.meetingmind.ai.llm

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.model.AskPersonalizationContext
import com.craftflowtechnologies.meetingmind.core.model.ChatMessage
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

    /**
     * The dedicated title pass follows exactly the same rule as synthesis: a local title is worse
     * than a cloud one, but a real title is much better than none. A successful cloud title is
     * never second-guessed.
     */
    override suspend fun suggestTitle(
        transcript: Transcript,
        recordingType: RecordingType
    ): AiResult<String> {
        val first = primary.suggestTitle(transcript, recordingType)
        if (first is AiResult.Success || fallback == null) return first
        return fallback.suggestTitle(transcript, recordingType)
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

    override fun askMeetingStream(
        question: String,
        transcript: Transcript,
        relevantSegments: List<TranscriptSegment>,
        personalization: AskPersonalizationContext
    ): Flow<AskStreamEvent> = flow {
        var succeeded = false
        var terminalEvent: AskStreamEvent.Complete? = null
        primary.askMeetingStream(question, transcript, relevantSegments, personalization).collect { ev ->
            when (ev) {
                is AskStreamEvent.Partial -> emit(ev)
                is AskStreamEvent.Complete -> {
                    terminalEvent = ev
                    if (ev.result is AiResult.Success) {
                        succeeded = true
                        emit(ev)
                    }
                }
            }
        }
        if (!succeeded) {
            if (fallback != null) {
                fallback.askMeetingStream(question, transcript, relevantSegments, personalization).collect { emit(it) }
            } else {
                terminalEvent?.let { emit(it) }
            }
        }
    }
}
