package com.craftflowtechnologies.meetingmind.ai.youtube

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.core.notes.YouTube
import org.json.JSONArray
import org.json.JSONObject

/** A public YouTube video as Gemini reads it: a title, a short summary, key points and a timed transcript. */
data class CondensedVideo(
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val transcript: List<TranscriptLine>
) {
    data class TranscriptLine(val timestamp: String, val text: String)
}

/**
 * Turns a public YouTube video into a summary and transcript by giving its address to Gemini.
 *
 * The app never downloads the video, its audio or its captions. YouTube's terms bar downloading and
 * automated access; Gemini reads public YouTube addresses as a video input, so Google fetches the
 * video itself. Private and unlisted videos cannot be read this way.
 */
object YouTubeCondenser {

    const val SYSTEM_INSTRUCTION =
        "You turn a video into notes for the person who asked for them. Use only what is said or shown in the video. " +
            "Do not add facts, names or numbers that the video does not contain. Write the transcript as spoken, " +
            "with each line's start time as MM:SS."

    const val PROMPT =
        "Watch this video. Return its title, a summary of five to eight sentences, the key points as short sentences, " +
            "and a transcript of what is spoken, one entry per speaker turn or about every 30 seconds."

    const val SCHEMA = """
    {
      "type": "object",
      "properties": {
        "title": { "type": "string" },
        "summary": { "type": "string" },
        "keyPoints": { "type": "array", "items": { "type": "string" } },
        "transcript": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "timestamp": { "type": "string" },
              "text": { "type": "string" }
            },
            "required": ["timestamp", "text"]
          }
        }
      },
      "required": ["title", "summary", "keyPoints", "transcript"]
    }
    """

    /** The plain watch address Gemini reads, or null when [url] is not a YouTube video. */
    fun watchUrl(url: String): String? = YouTube.idOf(url)?.let { "https://www.youtube.com/watch?v=$it" }

    suspend fun condense(transport: GeminiTransport, url: String): AiResult<CondensedVideo> {
        val videoUri = watchUrl(url) ?: return AiResult.Failed("That isn't a YouTube video link.")
        val reply = transport.execute(
            GeminiRequest(
                modelId = DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                systemInstruction = SYSTEM_INSTRUCTION,
                prompt = PROMPT,
                responseSchema = SCHEMA,
                videoUri = videoUri
            )
        )
        return when (reply) {
            is AiResult.Success -> parse(reply.value)?.let { AiResult.Success(it) }
                ?: AiResult.Failed("Gemini's answer for this video couldn't be read. Try again.")
            else -> AiResult.Failed(reply.describeFailure() ?: "Couldn't read that video.")
        }
    }

    /** Reads Gemini's JSON. Null when it holds neither a summary nor any transcript. */
    fun parse(json: String): CondensedVideo? = runCatching {
        val o = JSONObject(json)
        val transcript = o.optJSONArray("transcript").objects()
            .map { CondensedVideo.TranscriptLine(it.optString("timestamp").trim(), it.optString("text").trim()) }
            .filter { it.text.isNotBlank() }
        val summary = o.optString("summary").trim()
        if (summary.isBlank() && transcript.isEmpty()) return@runCatching null
        CondensedVideo(
            title = o.optString("title").trim(),
            summary = summary,
            keyPoints = o.optJSONArray("keyPoints").strings().map { it.trim() }.filter { it.isNotBlank() },
            transcript = transcript
        )
    }.getOrNull()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
