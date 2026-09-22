package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.core.model.AskPersonalizationContext
import com.example.core.model.ChatMessage
import com.example.core.model.MeetingSummary
import com.example.core.model.RecordingType
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackMeetingIntelligenceEngineTest {

    private class ScriptedEngine(private val answer: AiResult<ChatMessage>) : MeetingIntelligenceEngine {
        var calls = 0
        override suspend fun processMeeting(
            transcript: Transcript, meetingTitle: String, recordingType: RecordingType, customContext: String?
        ): AiResult<MeetingSummary> { calls++; return AiResult.Failed("not scripted") }

        override suspend fun askMeeting(
            question: String, transcript: Transcript,
            relevantSegments: List<TranscriptSegment>, personalization: AskPersonalizationContext
        ): AiResult<ChatMessage> { calls++; return answer }
    }

    private val transcript = Transcript("m1", emptyList())
    private fun reply(text: String) = AiResult.Success(ChatMessage("id", "m1", isUser = false, content = text))

    private suspend fun ask(engine: MeetingIntelligenceEngine) =
        engine.askMeeting("q", transcript, emptyList(), AskPersonalizationContext())

    @Test
    fun `a successful primary answer is used and the fallback never runs`() = runBlocking {
        val cloud = ScriptedEngine(reply("cloud"))
        val local = ScriptedEngine(reply("local"))

        val result = ask(FallbackMeetingIntelligenceEngine(cloud, local))

        assertEquals("cloud", (result as AiResult.Success).value.content)
        assertEquals(0, local.calls)
    }

    @Test
    fun `a failed primary hands over to the fallback`() = runBlocking {
        val cloud = ScriptedEngine(AiResult.ModelUnavailable("gemini", "no key"))
        val local = ScriptedEngine(reply("local"))

        val result = ask(FallbackMeetingIntelligenceEngine(cloud, local))

        assertEquals("local", (result as AiResult.Success).value.content)
    }

    @Test
    fun `with no fallback the primary failure is reported as is`() = runBlocking {
        val result = ask(FallbackMeetingIntelligenceEngine(ScriptedEngine(AiResult.Failed("offline")), null))

        assertTrue(result is AiResult.Failed)
    }
}
