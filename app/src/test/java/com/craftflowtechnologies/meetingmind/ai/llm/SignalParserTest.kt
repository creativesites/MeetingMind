package com.craftflowtechnologies.meetingmind.ai.llm

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Signals parse from the new format, and outputs in the older format still parse without them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalParserTest {
    private val valid = setOf("s1", "s2", "s3")
    private val speakers = mapOf("ana" to "spk1")

    private fun parse(raw: String) = MeetingIntelligenceJsonParser.parseExtraction(raw, "m1", valid, speakers)

    @Test fun theNewFormatCarriesSignalsWithEvidence() {
        val out = parse("""{"briefSummary":"x","signals":[
            {"kind":"DEADLINE","text":"Launch moves to October 21","sourceSegmentIds":["s1"],"confidence":0.8,"value":"October 21"},
            {"kind":"commitment","text":"Send the API docs","sourceSegmentIds":["s2","s3"],"speaker":"Ana","counterparty":"Bo","due":"Friday"},
            {"kind":"DECISION","text":"Use OAuth2","sourceSegmentIds":["s3"],"value":"PROPOSED","confidence":1.7}
        ]}""")
        assertEquals(3, out.signals.size)
        val deadline = out.signals[0]
        assertEquals("DEADLINE", deadline.kind); assertEquals("October 21", deadline.value); assertEquals(0.8f, deadline.confidence); assertEquals(listOf("s1"), deadline.sourceSegmentIds)
        val commitment = out.signals[1]
        assertEquals("COMMITMENT", commitment.kind)
        assertEquals("spk1", commitment.speakerId)
        assertEquals(listOf("s2", "s3"), commitment.sourceSegmentIds)
        val details = org.json.JSONObject(commitment.value!!)
        assertEquals("Bo", details.getString("counterparty")); assertEquals("Friday", details.getString("due"))
        assertEquals(0.6f, commitment.confidence) // none given: the default
        assertEquals("PROPOSED", out.signals[2].value)
        assertEquals(1f, out.signals[2].confidence) // clamped
    }

    @Test fun aSignalThatCitesNothingRealIsDropped() {
        val out = parse("""{"briefSummary":"x","signals":[
            {"kind":"RISK","text":"Budget may slip","sourceSegmentIds":["invented"]},
            {"kind":"RISK","text":"No citation at all","sourceSegmentIds":[]},
            {"kind":"RISK","text":"Half real","sourceSegmentIds":["s1","invented"]},
            {"kind":"WEATHER","text":"Unknown kind","sourceSegmentIds":["s1"]},
            {"kind":"RISK","text":"  ","sourceSegmentIds":["s1"]}
        ]}""")
        assertEquals(listOf("Half real"), out.signals.map { it.text })
        assertEquals(listOf("s1"), out.signals.single().sourceSegmentIds)
    }

    @Test fun nullFieldsAreNotReadAsTheWordNull() {
        val out = parse("""{"briefSummary":"x","signals":[{"kind":"COMMITMENT","text":"Send it","sourceSegmentIds":["s1"],"speaker":null,"counterparty":null,"due":null,"value":null}]}""")
        val s = out.signals.single()
        assertNull(s.speakerId); assertNull(s.value)
    }

    @Test fun theOlderFormatStillParsesAndHasNoSignals() {
        val old = """{"briefSummary":"A short call.","decisions":[{"text":"Use OAuth2","type":"DECISION","sourceSegmentIds":["s1"]}],
            "actionItems":[{"task":"Send docs","assigneeName":"Ana","deadline":"Friday","sourceSegmentIds":["s2"]}],
            "questions":[{"question":"Who owns it?","sourceSegmentIds":["s3"]}],"followUps":[{"description":"Ping Bo","owner":null,"deadline":null,"sourceSegmentIds":[]}]}"""
        val out = parse(old)
        assertEquals(1, out.decisions.size); assertEquals(1, out.actionItems.size); assertEquals(1, out.questions.size); assertEquals(1, out.followUps.size)
        assertEquals("A short call.", out.briefSummary)
        assertTrue(out.signals.isEmpty())
        assertEquals("spk1", out.actionItems.single().assigneeSpeakerId)
    }

    @Test fun malformedSignalsNeverDropTheRest() {
        val out = parse("""{"briefSummary":"x","decisions":[{"text":"Use OAuth2","sourceSegmentIds":["s1"]}],"signals":"not an array"}""")
        assertEquals(1, out.decisions.size); assertTrue(out.signals.isEmpty())
    }

    // ---------------------------------------------------------------- the engine

    private class Recording(val reply: String) : LanguageModel {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> { prompts += prompt; return AiResult.Success(reply) }
    }

    private val transcript = Transcript("m1", listOf(
        TranscriptSegment("s1", "m1", "spk1", "Ana", 0, 5000, "The launch moves to October 21 and the budget may slip."),
        TranscriptSegment("s2", "m1", "spk1", "Ana", 6000, 9000, "I'll send the docs by Friday.")
    ))

    private val reply = """{"briefSummary":"Launch moves.","signals":[
        {"kind":"DEADLINE","text":"Launch moves to October 21","sourceSegmentIds":["s1"],"value":"October 21"},
        {"kind":"RISK","text":"Budget may slip","sourceSegmentIds":["s1"]},
        {"kind":"COMMITMENT","text":"Send the docs","sourceSegmentIds":["s2"],"speaker":"Ana","due":"Friday"}]}"""

    private fun run(type: RecordingType, model: Recording = Recording(reply)): Pair<List<com.craftflowtechnologies.meetingmind.core.model.Signal>, Recording> = runBlocking {
        val result = RealMeetingIntelligenceEngine(model, 4096).processMeeting(transcript, "Call", type, null)
        (result as AiResult.Success).value.signals to model
    }

    @Test fun clientWorkKeepsEveryKindItAsksFor() {
        val (signals, model) = run(RecordingType.CLIENT_CALL)
        assertEquals(setOf("DEADLINE", "RISK", "COMMITMENT"), signals.map { it.kind }.toSet())
        assertTrue(model.prompts.first().contains("\"signals\""))
        assertTrue(model.prompts.first().contains("RISK: something that could go wrong"))
    }

    @Test fun aConsultationOnlyKeepsTheBaseKindsAndKeepsItsRule() {
        val (signals, model) = run(RecordingType.CONSULTATION)
        assertEquals(setOf("DEADLINE", "COMMITMENT"), signals.map { it.kind }.toSet()) // the model's RISK is not trusted here
        val prompt = model.prompts.first()
        assertTrue(prompt.contains("Never add a diagnosis, medication, dose, legal opinion or recommendation that was not spoken"))
        assertFalse(prompt.contains("RISK: something that could go wrong"))
    }

    @Test fun recordingsThatAreNotWorkAskForNoSignals() {
        val (signals, model) = run(RecordingType.LECTURE)
        assertTrue(signals.isEmpty())
        assertFalse(model.prompts.first().contains("\"signals\""))
        assertNotNull(model.prompts.first())
    }
}
