package com.craftflowtechnologies.meetingmind.core.aireport

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What leaves the phone in an AI-content report: the flagged passage and its labels, nothing more. */
class AiReportPayloadTest {

    private val secretNoteId = "note-SECRET-4711"
    private val secretMeetingId = "meeting-SECRET-0815"

    private fun source(text: String = "The AI said something unkind.", model: String? = "gemini-3.8-flash") = AiReportSource(
        feature = AiFeature.NOTE_BLOCK,
        text = text,
        modelId = model,
        noteId = secretNoteId,
        meetingId = secretMeetingId,
        moreNoteIds = listOf("note-SECRET-more"),
        moreMeetingIds = listOf("meeting-SECRET-more"),
        mustStayOnDevice = false,
    )

    private fun report(
        text: String = "The AI said something unkind.",
        comment: String? = "Please fix",
        model: String? = "gemini-3.8-flash",
        fallback: String? = "qwen-local",
    ) = AiReport.create(source(text, model), AiReportReason.OFFENSIVE, comment, fallback, "1.0-v48-dev", 1_700_000_000_000L, id = "0b7c1f7e-1111-4222-8333-944455556666")

    private fun keys(o: JSONObject) = o.keys().asSequence().toSet()

    @Test
    fun `payload carries exactly the allowed keys`() {
        assertEquals(AiReport.KEYS.toSet(), keys(report().toJson()))
    }

    @Test
    fun `payload without a comment leaves the comment key out`() {
        assertEquals(AiReport.KEYS.toSet() - "comment", keys(report(comment = null).toJson()))
        assertEquals(AiReport.KEYS.toSet() - "comment", keys(report(comment = "   ").toJson()))
    }

    @Test
    fun `payload holds the flagged text, reason, feature, model and version`() {
        val o = report().toJson()
        assertEquals("The AI said something unkind.", o.getString("text"))
        assertEquals("offensive", o.getString("reason"))
        assertEquals("note_block", o.getString("feature"))
        assertEquals("gemini-3.8-flash", o.getString("model"))
        assertEquals("1.0-v48-dev", o.getString("appVersion"))
        assertEquals("Please fix", o.getString("comment"))
    }

    @Test
    fun `note and meeting ids never reach the payload or the copied text`() {
        val r = report()
        val json = r.toJson().toString()
        val copy = r.copyText("help@example.org")
        listOf(secretNoteId, secretMeetingId, "SECRET").forEach {
            assertFalse("payload leaked $it", json.contains(it))
            assertFalse("copy text leaked $it", copy.contains(it))
        }
    }

    @Test
    fun `long text is cut to the limit and marked`() {
        val r = report(text = "a".repeat(AiReport.MAX_TEXT * 3))
        assertEquals(AiReport.MAX_TEXT, r.text.length)
        assertTrue(r.text.endsWith(AiReport.CUT_MARK))
    }

    @Test
    fun `long comment is cut to the limit`() {
        assertEquals(AiReport.MAX_COMMENT, report(comment = "b".repeat(2_000)).comment!!.length)
    }

    @Test
    fun `model falls back to the route, then to unknown`() {
        assertEquals("qwen-local", report(model = null).model)
        assertEquals(AiReport.UNKNOWN_MODEL, report(model = null, fallback = null).model)
        assertEquals(AiReport.MAX_MODEL, report(model = "m".repeat(500)).model.length)
    }

    @Test
    fun `copied text has the same facts and nothing else`() {
        val r = report()
        val copy = r.copyText(null)
        assertTrue(copy.contains("Reason: Offensive"))
        assertTrue(copy.contains("Comment: Please fix"))
        assertTrue(copy.contains("Feature: note_block"))
        assertTrue(copy.contains("Model: gemini-3.8-flash"))
        assertTrue(copy.contains("App version: 1.0-v48-dev"))
        assertTrue(copy.contains(r.reportId))
        assertTrue(copy.endsWith("The AI said something unkind."))
        assertFalse(copy.contains("Send to"))
        assertTrue(r.copyText("help@example.org").endsWith("Send to: help@example.org"))
    }

    @Test
    fun `json round trip keeps every field`() {
        val r = report()
        assertEquals(r, AiReport.fromJson(r.toJson()))
        val noComment = report(comment = null)
        assertEquals(noComment, AiReport.fromJson(noComment.toJson()))
        assertNull(AiReport.fromJson(JSONObject().put("reportId", "x")))
    }

    @Test
    fun `the sheet's privacy line names every field that is sent`() {
        val line = REPORT_PRIVACY_LINE.lowercase()
        listOf("passage", "reason", "comment", "feature", "model", "app version", "nothing else").forEach {
            assertTrue("privacy line should mention $it", line.contains(it))
        }
    }

    @Test
    fun `server answers map to sent, retry or drop`() {
        assertEquals(SendOutcome.SENT, AiReportSender.outcome(200))
        assertEquals(SendOutcome.DROP, AiReportSender.outcome(400))
        assertEquals(SendOutcome.DROP, AiReportSender.outcome(413))
        assertEquals(SendOutcome.RETRY, AiReportSender.outcome(404))
        assertEquals(SendOutcome.RETRY, AiReportSender.outcome(429))
        assertEquals(SendOutcome.RETRY, AiReportSender.outcome(503))
    }

    @Test
    fun `sender posts only the report, with no credentials`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
            server.start()
            val outcome = AiReportSender(server.url("/").toString(), OkHttpClient()).send(report())
            assertEquals(SendOutcome.SENT, outcome)
            val req = server.takeRequest()
            assertEquals("/v1/reportAiContent", req.path)
            assertEquals("POST", req.method)
            assertNull(req.getHeader("Authorization"))
            assertEquals(AiReport.KEYS.toSet(), keys(JSONObject(req.body.readUtf8())))
        }
    }

    @Test
    fun `sender keeps the report when the server is not there yet`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.start()
            assertEquals(SendOutcome.RETRY, AiReportSender(server.url("/").toString(), OkHttpClient()).send(report()))
        }
        assertEquals(SendOutcome.RETRY, AiReportSender("", OkHttpClient()).send(report()))
    }
}
