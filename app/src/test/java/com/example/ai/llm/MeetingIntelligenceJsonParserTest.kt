package com.example.ai.llm

import com.example.core.model.DecisionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [MeetingIntelligenceJsonParser] is the last line of defense against a local LLM's output ever
 * reaching Room unvalidated. Every test here targets one specific way real, imperfect LLM output
 * could otherwise corrupt or fabricate meeting data.
 *
 * Runs under Robolectric (rather than as a plain JVM test) because `org.json.JSONObject` resolves
 * to Android's unmocked SDK stub jar otherwise, whose methods throw instead of doing real work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeetingIntelligenceJsonParserTest {

    private val meetingId = "m1"
    private val validIds = setOf("seg1", "seg2", "seg3")

    @Test
    fun `parses a well-formed extraction response`() {
        val raw = """
            {"decisions":[{"text":"We will ship Friday","type":"DECISION","sourceSegmentIds":["seg1"]}],
             "actionItems":[{"task":"Write the proposal","assigneeName":"Alice","deadline":"Friday","sourceSegmentIds":["seg2"]}],
             "questions":[{"question":"Who owns billing?","askedBy":"Bob","sourceSegmentIds":["seg3"]}],
             "followUps":[{"description":"Check with legal","owner":"Alice","deadline":null,"sourceSegmentIds":["seg1"]}],
             "briefSummary":"Team agreed to ship Friday."}
        """.trimIndent()

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertEquals(1, result.decisions.size)
        assertEquals(DecisionType.DECISION, result.decisions[0].type)
        assertEquals(listOf("seg1"), result.decisions[0].sourceSegmentIds)
        assertEquals(1, result.actionItems.size)
        assertEquals("Alice", result.actionItems[0].assigneeName)
        assertEquals(1, result.questions.size)
        assertEquals(1, result.followUps.size)
        assertEquals("Team agreed to ship Friday.", result.briefSummary)
    }

    @Test
    fun `strips markdown code fences before parsing`() {
        val raw = "```json\n{\"decisions\":[],\"actionItems\":[],\"questions\":[],\"followUps\":[],\"briefSummary\":\"\"}\n```"

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertTrue(result.decisions.isEmpty())
    }

    @Test
    fun `trims leading and trailing chatter around the JSON object`() {
        val raw = "Sure, here is the JSON:\n{\"decisions\":[],\"actionItems\":[],\"questions\":[],\"followUps\":[],\"briefSummary\":\"\"}\nLet me know if you need anything else!"

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertTrue(result.decisions.isEmpty())
    }

    @Test
    fun `completely malformed output yields empty results, never a crash or a guess`() {
        val result = MeetingIntelligenceJsonParser.parseExtraction("not json at all, sorry I can't help with that", meetingId, validIds)

        assertEquals(ChunkExtraction.EMPTY, result)
    }

    @Test
    fun `a sourceSegmentIds entry the model was never shown is dropped, not trusted`() {
        val raw = """{"decisions":[{"text":"Launch decided","type":"DECISION","sourceSegmentIds":["seg1","seg_invented_99"]}],"actionItems":[],"questions":[],"followUps":[],"briefSummary":""}"""

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertEquals(listOf("seg1"), result.decisions[0].sourceSegmentIds)
    }

    @Test
    fun `an unrecognized decision type falls back to DISCUSSION rather than failing`() {
        val raw = """{"decisions":[{"text":"Maybe next quarter","type":"MAYBE_SOMEDAY","sourceSegmentIds":[]}],"actionItems":[],"questions":[],"followUps":[],"briefSummary":""}"""

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertEquals(DecisionType.DISCUSSION, result.decisions[0].type)
    }

    @Test
    fun `an item with blank required text is dropped rather than persisted empty`() {
        val raw = """{"decisions":[{"text":"","type":"DECISION","sourceSegmentIds":[]}],"actionItems":[{"task":"   ","sourceSegmentIds":[]}],"questions":[],"followUps":[],"briefSummary":""}"""

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertTrue(result.decisions.isEmpty())
        assertTrue(result.actionItems.isEmpty())
    }

    @Test
    fun `empty arrays for every category are accepted as a legitimate no-evidence result`() {
        val raw = """{"decisions":[],"actionItems":[],"questions":[],"followUps":[],"briefSummary":"Informal check-in, nothing decided."}"""

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertTrue(result.decisions.isEmpty())
        assertTrue(result.actionItems.isEmpty())
        assertTrue(result.questions.isEmpty())
        assertTrue(result.followUps.isEmpty())
    }

    @Test
    fun `an assignee name matching a known speaker is resolved to that speaker's id`() {
        val raw = """{"decisions":[],"actionItems":[{"task":"Send the deck","assigneeName":"Alice","sourceSegmentIds":[]}],"questions":[],"followUps":[],"briefSummary":""}"""

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds, speakerNameToId = mapOf("alice" to "spk_m1_0"))

        assertEquals("spk_m1_0", result.actionItems[0].assigneeSpeakerId)
        assertEquals("Alice", result.actionItems[0].assigneeName)
    }

    @Test
    fun `an assignee name with no matching known speaker keeps the name but no speaker id`() {
        val raw = """{"decisions":[],"actionItems":[{"task":"Send the deck","assigneeName":"Someone Unrecognized","sourceSegmentIds":[]}],"questions":[],"followUps":[],"briefSummary":""}"""

        val result = MeetingIntelligenceJsonParser.parseExtraction(raw, meetingId, validIds)

        assertEquals(null, result.actionItems[0].assigneeSpeakerId)
        assertEquals("Someone Unrecognized", result.actionItems[0].assigneeName)
    }

    @Test
    fun `synthesis parses title summary and key points`() {
        val raw = """{"title":"Q3 Launch Planning","summary":"The team finalized the Q3 launch date.","keyPoints":["Launch date set","Budget approved"]}"""

        val result = MeetingIntelligenceJsonParser.parseSynthesis(raw, "Fallback Title")

        assertEquals("Q3 Launch Planning", result.title)
        assertEquals("The team finalized the Q3 launch date.", result.summary)
        assertEquals(listOf("Launch date set", "Budget approved"), result.keyPoints)
    }

    @Test
    fun `synthesis falls back to the provided title when the model omits one`() {
        val raw = """{"summary":"Short catch-up.","keyPoints":[]}"""

        val result = MeetingIntelligenceJsonParser.parseSynthesis(raw, "Fallback Title")

        assertEquals("Fallback Title", result.title)
    }

    @Test
    fun `malformed synthesis output falls back cleanly instead of crashing`() {
        val result = MeetingIntelligenceJsonParser.parseSynthesis("I'm not able to produce JSON for that.", "Fallback Title")

        assertEquals("Fallback Title", result.title)
        assertEquals("", result.summary)
        assertTrue(result.keyPoints.isEmpty())
    }
}
