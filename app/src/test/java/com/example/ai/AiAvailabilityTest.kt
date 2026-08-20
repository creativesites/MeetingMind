package com.example.ai

import com.example.ai.asr.TranscriptionOptions
import com.example.ai.asr.UnavailableSpeechRecognizer
import com.example.ai.common.AiResult
import com.example.ai.diarization.UnavailableSpeakerDiarizer
import com.example.ai.llm.UnavailableLanguageModel
import com.example.ai.llm.UnavailableMeetingIntelligenceEngine
import com.example.ai.vad.UnavailableVoiceActivityDetector
import com.example.core.model.Transcript
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every default AI implementation must honestly report [AiResult.ModelUnavailable] rather than
 * fabricate output, since no real local VAD/ASR/diarization/LLM model is installed by default.
 * This is the automated guardrail against the app silently reverting to fake AI behavior.
 */
class AiAvailabilityTest {

    @Test
    fun `VAD reports model unavailable and never fabricates speech intervals`() = runBlocking {
        val result = UnavailableVoiceActivityDetector().detectSpeechIntervals(File("nonexistent.m4a"), 10_000L)
        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `ASR reports model unavailable and never fabricates a transcript`() = runBlocking {
        val result = UnavailableSpeechRecognizer().transcribe(
            audioFile = File("nonexistent.m4a"),
            totalDurationMs = 10_000L,
            meetingId = "m1",
            speechIntervals = emptyList(),
            options = TranscriptionOptions(),
            onProgress = { _, _ -> }
        )
        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `diarizer reports model unavailable rather than guessing speaker turns`() = runBlocking {
        val result = UnavailableSpeakerDiarizer().diarize(
            audioFile = File("nonexistent.m4a"),
            totalDurationMs = 10_000L,
            segments = emptyList()
        )
        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `language model reports model unavailable and never invents text`() = runBlocking {
        val result = UnavailableLanguageModel().generate("Summarize this meeting")
        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `meeting intelligence engine reports model unavailable for title, summary, and ask-meeting`() = runBlocking {
        val engine = UnavailableMeetingIntelligenceEngine()
        val transcript = Transcript(meetingId = "m1", segments = emptyList())

        assertTrue(engine.processMeeting(transcript, "Fallback Title") is AiResult.ModelUnavailable)
        assertTrue(engine.askMeeting("What was discussed?", transcript, emptyList()) is AiResult.ModelUnavailable)
    }
}
