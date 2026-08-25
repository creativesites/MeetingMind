package com.example.ai.llm

import com.example.ai.common.AiResult
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the Phase 3D fixes to how a summary is produced. The failure these guard against is the
 * one users actually hit: a short, real recording coming back as "nothing specific was discussed"
 * because the synthesis step was only ever shown the *extracted items*, never the transcript.
 *
 * Runs under Robolectric for the same reason [MeetingIntelligenceJsonParserTest] does — the parser
 * this engine delegates to needs a real `org.json`, not the Android SDK's throwing stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeetingIntelligenceGroundingTest {

    /** Records every prompt it is asked to generate from, and replies with scripted responses. */
    private class ScriptedLanguageModel(
        private val responses: List<AiResult<String>>
    ) : LanguageModel {
        val prompts = mutableListOf<String>()
        private var callIndex = 0

        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            prompts += prompt
            val response = responses.getOrElse(callIndex) { AiResult.Failed("no scripted response") }
            callIndex++
            return response
        }
    }

    private fun transcriptOf(vararg texts: String) = Transcript(
        meetingId = "m1",
        segments = texts.mapIndexed { index, text ->
            TranscriptSegment(
                id = "seg$index",
                meetingId = "m1",
                startMs = index * 2000L,
                endMs = index * 2000L + 1800L,
                text = text
            )
        }
    )

    @Test
    fun `synthesis prompt contains the real transcript text, not only extracted evidence`() = runBlocking {
        val model = ScriptedLanguageModel(
            listOf(
                // Extraction: a genuinely empty result, as a casual note would produce.
                AiResult.Success("""{"briefSummary":"","decisions":[],"actionItems":[],"questions":[],"followUps":[]}"""),
                AiResult.Success("""{"title":"Dentist reminder","summary":"A reminder to book a dentist appointment.","keyPoints":[]}""")
            )
        )
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        engine.processMeeting(transcriptOf("Remember to book the dentist for next Tuesday."), "Fallback")

        val synthesisPrompt = model.prompts.last()
        assertTrue(
            "Synthesis prompt must include the actual transcript:\n$synthesisPrompt",
            synthesisPrompt.contains("book the dentist")
        )
    }

    @Test
    fun `a short recording with no decisions still produces a real summary`() = runBlocking {
        val model = ScriptedLanguageModel(
            listOf(
                AiResult.Success("""{"briefSummary":"","decisions":[],"actionItems":[],"questions":[],"followUps":[]}"""),
                AiResult.Success("""{"title":"Dentist reminder","summary":"A reminder to book a dentist appointment for next Tuesday.","keyPoints":[]}""")
            )
        )
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        val result = engine.processMeeting(transcriptOf("Remember to book the dentist for next Tuesday."), "Fallback")

        val summary = (result as AiResult.Success).value
        assertEquals("A reminder to book a dentist appointment for next Tuesday.", summary.summary)
        assertTrue("A note with no decisions must not invent any", summary.decisions.isEmpty())
        assertTrue("A note with no tasks must not invent any", summary.actionItems.isEmpty())
    }

    @Test
    fun `falls back to per-chunk summaries when the synthesis call itself fails`() = runBlocking {
        val model = ScriptedLanguageModel(
            listOf(
                AiResult.Success("""{"briefSummary":"The team walked through the Q3 budget.","decisions":[],"actionItems":[],"questions":[],"followUps":[]}"""),
                AiResult.Failed("synthesis blew up")
            )
        )
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        val result = engine.processMeeting(transcriptOf("We went through the Q3 budget line by line."), "Fallback")

        val summary = (result as AiResult.Success).value
        assertEquals("The team walked through the Q3 budget.", summary.summary)
    }

    @Test
    fun `salvages prose from an extraction response whose JSON is malformed`() = runBlocking {
        val model = ScriptedLanguageModel(
            listOf(
                // Real, useful prose — but not parseable as the requested schema.
                AiResult.Success("The speaker is reminding themselves to renew the domain before it lapses next month."),
                AiResult.Failed("synthesis unavailable")
            )
        )
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        val result = engine.processMeeting(transcriptOf("Need to renew the domain before it lapses."), "Fallback")

        val summary = (result as AiResult.Success).value
        assertTrue(
            "Real model prose must not be discarded just because its JSON was malformed: '${summary.summary}'",
            summary.summary.contains("renew the domain")
        )
    }

    @Test
    fun `filler words are stripped from prompts sent to the model`() = runBlocking {
        val model = ScriptedLanguageModel(
            listOf(
                AiResult.Success("""{"briefSummary":"ok","decisions":[],"actionItems":[],"questions":[],"followUps":[]}"""),
                AiResult.Success("""{"title":"T","summary":"S","keyPoints":[]}""")
            )
        )
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        engine.processMeeting(transcriptOf("Uh, so we should, uh, ship on Friday."), "Fallback")

        val extractionPrompt = model.prompts.first()
        assertFalse(
            "Hesitation noise wastes a small context window:\n$extractionPrompt",
            extractionPrompt.contains("Uh, so we should")
        )
        assertTrue(extractionPrompt.contains("ship on Friday"))
    }

    @Test
    fun `reports honest failure when the model produces nothing at all`() = runBlocking {
        val model = ScriptedLanguageModel(listOf(AiResult.Failed("engine down")))
        val engine = RealMeetingIntelligenceEngine(model, contextLengthTokens = 4096)

        val result = engine.processMeeting(transcriptOf("Anything at all."), "Fallback")

        assertTrue("A total model failure must not be dressed up as a summary", result is AiResult.Failed)
    }
}
