package com.craftflowtechnologies.meetingmind.ai.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json is a stub on the plain JVM, so the parser runs under Robolectric as the other note tests do.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeCondenserTest {

    @Test
    fun `a short link becomes the plain watch address Gemini reads`() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeCondenser.watchUrl("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", YouTubeCondenser.watchUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
    }

    @Test
    fun `a link that is not a YouTube video has no watch address`() {
        assertNull(YouTubeCondenser.watchUrl("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeCondenser.watchUrl("https://www.youtube.com/@somechannel"))
    }

    @Test
    fun `Gemini's answer is read into title, summary, key points and transcript`() {
        val json = """
            {"title":"Pricing for small teams","summary":"A talk on pricing.",
             "keyPoints":["Charge for outcomes","Test a higher price"],
             "transcript":[{"timestamp":"00:00","text":"Welcome back."},{"timestamp":"00:31","text":"Today, pricing."}]}
        """.trimIndent()
        val video = YouTubeCondenser.parse(json)!!
        assertEquals("Pricing for small teams", video.title)
        assertEquals("A talk on pricing.", video.summary)
        assertEquals(listOf("Charge for outcomes", "Test a higher price"), video.keyPoints)
        assertEquals(listOf(
            CondensedVideo.TranscriptLine("00:00", "Welcome back."),
            CondensedVideo.TranscriptLine("00:31", "Today, pricing.")
        ), video.transcript)
    }

    @Test
    fun `blank transcript lines are dropped and missing lists become empty`() {
        val video = YouTubeCondenser.parse("""{"title":"T","summary":"S","transcript":[{"timestamp":"00:00","text":"  "}]}""")!!
        assertEquals(emptyList<CondensedVideo.TranscriptLine>(), video.transcript)
        assertEquals(emptyList<String>(), video.keyPoints)
    }

    @Test
    fun `an answer with neither summary nor transcript is refused, not shown empty`() {
        assertNull(YouTubeCondenser.parse("""{"title":"Only a title","summary":"","keyPoints":[],"transcript":[]}"""))
        assertNull(YouTubeCondenser.parse("not json at all"))
    }
}
