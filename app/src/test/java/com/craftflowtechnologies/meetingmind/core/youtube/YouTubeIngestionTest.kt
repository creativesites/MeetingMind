package com.craftflowtechnologies.meetingmind.core.youtube

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiYouTubeEngine
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.routing.AiRoute
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeIngestionTest {

    @Test
    fun `parses watch, short, shorts, live, mobile, and embedded YouTube URLs`() {
        val watch = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=125s")
        assertNotNull(watch)
        assertEquals("dQw4w9WgXcQ", watch!!.videoId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", watch.canonicalWatchUrl)
        assertEquals(125L, watch.startSeconds)
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=60s", watch.urlAtMs(60_000L))

        val shortLink = YouTubeUrlParser.parse("Check out this sermon: https://youtu.be/AbCdEfGhIjK?si=xyz123&t=2m10s")
        assertNotNull(shortLink)
        assertEquals("AbCdEfGhIjK", shortLink!!.videoId)
        assertEquals(130L, shortLink.startSeconds)

        val shorts = YouTubeUrlParser.parse("https://youtube.com/shorts/1234567890A?feature=share")
        assertNotNull(shorts)
        assertEquals("1234567890A", shorts!!.videoId)

        val live = YouTubeUrlParser.parse("https://m.youtube.com/live/ZyXwVuTsRqP")
        assertNotNull(live)
        assertEquals("ZyXwVuTsRqP", live!!.videoId)

        assertNull(YouTubeUrlParser.parse("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeUrlParser.parse("not a link"))
    }

    @Test
    fun `default model router keeps gemini-3_8-flash and the live and tts models`() {
        assertEquals("gemini-3.8-flash", DefaultAiModelRouter.route(AiRoute.GEMINI_FAST).modelId)
        assertEquals("gemini-3.8-flash", DefaultAiModelRouter.route(AiRoute.GEMINI_INTELLIGENCE).modelId)
        assertEquals("gemini-3.8-flash", DefaultAiModelRouter.route(AiRoute.GEMINI_DEEP_REASONING).modelId)
        assertEquals("gemini-3.8-live", DefaultAiModelRouter.route(AiRoute.GEMINI_LIVE).modelId)
        assertEquals("gemini-3.8-live-extended-thinking", DefaultAiModelRouter.route(AiRoute.GEMINI_LIVE_EXTENDED).modelId)
        assertEquals("gemini-3.8-flash-tts", DefaultAiModelRouter.GEMINI_TTS_MODEL)
        assertEquals("gemini-3.8-flash-lite-tts", DefaultAiModelRouter.GEMINI_TTS_LITE_MODEL)
    }

    @Test
    fun `GeminiYouTubeEngine sends canonical YouTube URL on gemini-3_8-flash and parses structured bytes and segments`() = runBlocking {
        var capturedRequest: GeminiRequest? = null
        val fakeTransport = object : GeminiTransport {
            override fun isConfigured(): Boolean = true
            override suspend fun execute(request: GeminiRequest): AiResult<String> {
                capturedRequest = request
                return AiResult.Success(
                    """
                    {
                      "videoTitle": "Faith in the Storm — Sunday Sermon",
                      "channelOrSpeaker": "Pastor Grace",
                      "detectedCategory": "SERMON",
                      "summary": "A sermon on trusting God during seasons of uncertainty, anchored in Mark 4:35-41.",
                      "retentionBytes": [
                        {
                          "title": "Peace is a Person, Not the Absence of a Storm",
                          "detail": "Jesus slept in the boat because His peace came from the Father's promise, not the weather.",
                          "startSeconds": 45
                        },
                        {
                          "title": "Hold Fast to the Word Spoken Before the Wind Rose",
                          "detail": "Christ said 'Let us cross over to the other side' before the storm began (Mark 4:35).",
                          "startSeconds": 180
                        }
                      ],
                      "segments": [
                        {
                          "startSeconds": 0,
                          "endSeconds": 45,
                          "speaker": "Pastor Grace",
                          "text": "Turn with me in your Bibles to Mark chapter 4 verse 35."
                        },
                        {
                          "startSeconds": 45,
                          "endSeconds": 120,
                          "speaker": "Pastor Grace",
                          "text": "Peace is not the absence of a storm; peace is the presence of Jesus in your boat."
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }
        }

        val engine = GeminiYouTubeEngine(fakeTransport)
        val result = engine.extractVideo("https://youtu.be/dQw4w9WgXcQ")

        assertTrue(result is AiResult.Success)
        val req = capturedRequest!!
        assertEquals("gemini-3.8-flash", req.modelId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", req.externalMediaUri)
        assertEquals("video/mp4", req.externalMediaMimeType)

        val value = (result as AiResult.Success).value
        assertEquals("Faith in the Storm — Sunday Sermon", value.videoTitle)
        assertEquals("Pastor Grace", value.channelOrSpeaker)
        assertEquals(RecordingType.SERMON, value.detectedRecordingType)
        assertEquals(2, value.retentionBytes.size)
        assertEquals(45_000L, value.retentionBytes[0].startMs)
        assertTrue(value.words.isNotEmpty())
        assertTrue(value.words.any { it.text.contains("Mark") })
    }

    @Test
    fun `category fallback maps motivation and life skills videos accurately`() {
        assertEquals(
            RecordingType.MOTIVATION,
            GeminiYouTubeEngine.mapDetectedCategory("UNKNOWN", "Daily Discipline & Mindset Shift", "How to build unstoppable habits.")
        )
        assertEquals(
            RecordingType.LIFE_SKILLS,
            GeminiYouTubeEngine.mapDetectedCategory("UNKNOWN", "How to Negotiate Your Salary Step by Step", "Practical communication guide.")
        )
        assertEquals(
            RecordingType.LECTURE,
            GeminiYouTubeEngine.mapDetectedCategory("UNKNOWN", "MIT 18.06 Linear Algebra Lecture 1", "Professor Gilbert Strang explains matrix multiplication.")
        )
    }
}
