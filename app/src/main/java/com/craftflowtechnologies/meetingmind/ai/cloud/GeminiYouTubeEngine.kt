package com.craftflowtechnologies.meetingmind.ai.cloud

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.routing.AiModelRouter
import com.craftflowtechnologies.meetingmind.ai.routing.AiRoute
import com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter
import com.craftflowtechnologies.meetingmind.ai.transcript.AttributionConfidence
import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord
import com.craftflowtechnologies.meetingmind.ai.transcript.TranscriptSource
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.youtube.ParsedYouTubeUrl
import com.craftflowtechnologies.meetingmind.core.youtube.YouTubeUrlParser
import org.json.JSONObject

/**
 * Structured output returned from Gemini when processing a YouTube URL via `gemini-3.8-flash`.
 */
data class YouTubeExtractionResult(
    val parsedUrl: ParsedYouTubeUrl,
    val videoTitle: String,
    val channelOrSpeaker: String?,
    val detectedRecordingType: RecordingType,
    val summary: String,
    /** Bite-sized, high-retention takeaways with timestamps for fast reading and memory retention. */
    val retentionBytes: List<YouTubeRetentionByte>,
    val words: List<CanonicalWord>,
    val estimatedDurationMs: Long,
    val modelId: String
)

data class YouTubeRetentionByte(
    val title: String,
    val detail: String,
    val startMs: Long = 0L
)

/**
 * Ingests a public YouTube URL directly through the Gemini API (`gemini-3.8-flash`) using
 * `file_data` (`file_uri` = canonical YouTube URL, `mime_type` = `"video/mp4"`).
 *
 * Extracts:
 * 1. The video's title and primary speaker/channel
 * 2. The best-fit [RecordingType] (`SERMON`, `LECTURE`, `MOTIVATION`, `LIFE_SKILLS`, `BIBLE_STUDY`, `INTERVIEW`, `MEETING`, `RESEARCH`)
 * 3. High-retention "Key Bytes" for fast reading & memory retention
 * 4. A chronological, timestamped transcript converted into [CanonicalWord]s so the entire
 *    MeetingMind pipeline (Sermon notes, Lecture concepts & flashcards, Motivation/Life Skills playbooks,
 *    Ask AI with `[mm:ss]` citations, Read-to-Me with `gemini-3.8-flash-tts`, and Zuri Moments)
 *    works seamlessly.
 */
class GeminiYouTubeEngine(
    private val transport: GeminiTransport,
    private val router: AiModelRouter = DefaultAiModelRouter
) {

    suspend fun extractVideo(
        rawUrlOrPath: String,
        requestedType: RecordingType = RecordingType.GENERAL,
        customContext: String? = null
    ): AiResult<YouTubeExtractionResult> {
        val parsed = YouTubeUrlParser.parse(rawUrlOrPath)
            ?: return AiResult.Failed("Please enter a valid YouTube video link (e.g. youtube.com/watch?v=... or youtu.be/...).")

        if (!transport.refreshConfigured()) {
            val fallbackModel = router.route(AiRoute.GEMINI_INTELLIGENCE).modelId ?: DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL
            return AiResult.ModelUnavailable(
                modelId = fallbackModel,
                message = "YouTube video transcription requires Internet mode with Google's AI enabled in Settings."
            )
        }

        val modelId = router.route(AiRoute.GEMINI_INTELLIGENCE).modelId
            ?: DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL

        val prompt = buildPrompt(parsed, requestedType, customContext)
        val request = GeminiRequest(
            modelId = modelId,
            systemInstruction = SYSTEM_INSTRUCTION,
            prompt = prompt,
            externalMediaUri = parsed.canonicalWatchUrl,
            externalMediaMimeType = "video/mp4",
            responseSchema = YOUTUBE_EXTRACTION_SCHEMA,
            thinkingLevel = ThinkingLevel.LOW,
            maxOutputTokens = 8_192,
            timeoutMs = 8 * 60_000L
        )

        val response = transport.execute(request)
        val finalResponse = if (response is AiResult.Failed && response.cause is java.io.IOException) {
            transport.execute(request)
        } else {
            response
        }

        if (finalResponse !is AiResult.Success) {
            return AiResult.Failed(
                finalResponse.describeFailure()
                    ?: "Gemini could not read this YouTube video. Make sure the video is public and try again."
            )
        }

        return parseExtractionResponse(finalResponse.value, parsed, requestedType, modelId)
            ?: AiResult.Failed("Gemini returned an unreadable response for this YouTube video. Please try again.")
    }

    internal fun buildPrompt(
        parsed: ParsedYouTubeUrl,
        requestedType: RecordingType,
        customContext: String?
    ): String = buildString {
        appendLine("Analyze this YouTube video (${parsed.canonicalWatchUrl}) and return its complete structured transcript, category classification, and high-retention takeaway bytes.")
        if (requestedType != RecordingType.GENERAL) {
            appendLine("User-selected category: ${requestedType.name} (${requestedType.displayName} — ${requestedType.shortDescription}).")
        } else {
            appendLine("Category detection: Automatically classify this video into the most accurate category among SERMON, LECTURE, MOTIVATION, LIFE_SKILLS, BIBLE_STUDY, INTERVIEW, RESEARCH, or MEETING.")
        }
        if (!customContext.isNullOrBlank()) {
            appendLine("User focus instructions: ${customContext.trim()}")
        }
        appendLine("Requirements:")
        appendLine("1. Provide a faithful, detailed chronological transcript divided into natural timestamped segments ('startSeconds', 'endSeconds', 'speaker', 'text').")
        appendLine("2. Extract 3 to 7 high-retention 'retentionBytes' — punchy, memorable bite-sized takeaways that help the reader quickly absorb and remember the core lessons without forgetting them.")
        appendLine("3. Ground every segment, quote, scripture reference, concept, or step strictly in what is actually spoken or shown in the video.")
    }

    companion object {
        const val SYSTEM_INSTRUCTION =
            "You are MeetingMind's YouTube video transcription and knowledge-retention engine. " +
                "Given a YouTube video, extract a faithful chronological transcript with accurate " +
                "timestamps in seconds and speaker names, classify the video's domain (SERMON, LECTURE, " +
                "MOTIVATION, LIFE_SKILLS, BIBLE_STUDY, INTERVIEW, RESEARCH, MEETING), and distill " +
                "high-retention bite-sized takeaways ('retentionBytes') so the person can read faster " +
                "and retain the information long-term. Never invent facts, scriptures, or quotes not " +
                "present in the video."

        const val YOUTUBE_EXTRACTION_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "videoTitle": { "type": "string" },
            "channelOrSpeaker": { "type": "string" },
            "detectedCategory": {
              "type": "string",
              "enum": ["SERMON", "LECTURE", "MOTIVATION", "LIFE_SKILLS", "BIBLE_STUDY", "INTERVIEW", "RESEARCH", "MEETING", "GENERAL"]
            },
            "summary": { "type": "string" },
            "retentionBytes": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "title": { "type": "string" },
                  "detail": { "type": "string" },
                  "startSeconds": { "type": "integer" }
                },
                "required": ["title", "detail"]
              }
            },
            "segments": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "startSeconds": { "type": "integer" },
                  "endSeconds": { "type": "integer" },
                  "speaker": { "type": "string" },
                  "text": { "type": "string" }
                },
                "required": ["startSeconds", "endSeconds", "text"]
              }
            }
          },
          "required": ["videoTitle", "detectedCategory", "summary", "retentionBytes", "segments"]
        }
        """

        internal fun parseExtractionResponse(
            rawJson: String,
            parsedUrl: ParsedYouTubeUrl,
            requestedType: RecordingType,
            modelId: String
        ): AiResult.Success<YouTubeExtractionResult>? = runCatching {
            val cleaned = rawJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val root = JSONObject(cleaned)
            val title = root.optString("videoTitle").trim().ifBlank { "YouTube Video (${parsedUrl.videoId})" }
            val channelOrSpeaker = root.optString("channelOrSpeaker").trim().ifBlank { null }
            val rawCategory = root.optString("detectedCategory").trim().uppercase()
            val detectedType = if (requestedType != RecordingType.GENERAL) {
                requestedType
            } else {
                mapDetectedCategory(rawCategory, title, root.optString("summary"))
            }
            val summary = root.optString("summary").trim()

            val bytesArray = root.optJSONArray("retentionBytes")
            val retentionBytes = mutableListOf<YouTubeRetentionByte>()
            if (bytesArray != null) {
                for (i in 0 until bytesArray.length()) {
                    val item = bytesArray.optJSONObject(i) ?: continue
                    val bTitle = item.optString("title").trim()
                    val bDetail = item.optString("detail").trim()
                    val startSec = item.optLong("startSeconds", 0L).coerceAtLeast(0L)
                    if (bTitle.isNotEmpty() && bDetail.isNotEmpty()) {
                        retentionBytes += YouTubeRetentionByte(
                            title = bTitle,
                            detail = bDetail,
                            startMs = startSec * 1000L
                        )
                    }
                }
            }

            val segmentsArray = root.optJSONArray("segments") ?: return null
            val words = mutableListOf<CanonicalWord>()
            val speakerIds = LinkedHashMap<String, String>()
            var maxEndMs = 0L
            var wordCounter = 0

            for (i in 0 until segmentsArray.length()) {
                val seg = segmentsArray.optJSONObject(i) ?: continue
                val text = seg.optString("text").trim()
                if (text.isEmpty()) continue
                val startSec = seg.optLong("startSeconds", i * 15L).coerceAtLeast(0L)
                val rawEndSec = seg.optLong("endSeconds", startSec + 12L)
                val startMs = maxOf(startSec * 1000L, maxEndMs)
                val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (tokens.isEmpty()) continue
                val estimatedSpanMs = maxOf((rawEndSec * 1000L) - startMs, tokens.size * 320L, 1_500L)
                val endMs = startMs + estimatedSpanMs
                maxEndMs = maxOf(maxEndMs, endMs)

                val speakerLabel = seg.optString("speaker").trim().ifBlank { channelOrSpeaker ?: "Speaker 1" }
                val speakerId = speakerIds.getOrPut(speakerLabel.lowercase()) {
                    "speaker_${speakerIds.size}"
                }

                val stepMs = (estimatedSpanMs / tokens.size.coerceAtLeast(1)).coerceAtLeast(80L)
                tokens.forEachIndexed { idx, token ->
                    val wStart = startMs + idx * stepMs
                    val wEnd = if (idx == tokens.lastIndex) endMs else (wStart + stepMs).coerceAtMost(endMs)
                    words += CanonicalWord(
                        id = "yt_${wordCounter++}",
                        text = token,
                        startMs = wStart,
                        endMs = maxOf(wEnd, wStart),
                        speakerId = speakerId,
                        attribution = AttributionConfidence.HIGH,
                        source = TranscriptSource.GEMINI_VERBATIM
                    )
                }
            }

            if (words.isEmpty()) return null

            AiResult.Success(
                YouTubeExtractionResult(
                    parsedUrl = parsedUrl,
                    videoTitle = title,
                    channelOrSpeaker = channelOrSpeaker,
                    detectedRecordingType = detectedType,
                    summary = summary,
                    retentionBytes = retentionBytes,
                    words = words,
                    estimatedDurationMs = maxEndMs.coerceAtLeast(1_000L),
                    modelId = modelId
                )
            )
        }.getOrNull()

        internal fun mapDetectedCategory(rawCategory: String, title: String, summary: String): RecordingType {
            val direct = RecordingType.entries.firstOrNull { it.name == rawCategory && it != RecordingType.GENERAL }
            if (direct != null) return direct
            val combined = "$title $summary".lowercase()
            return when {
                listOf("sermon", "pastor", "preach", "church", "scripture", "gospel", "homily", "ministry").any { it in combined } -> RecordingType.SERMON
                listOf("bible study", "exegesis", "verse by verse").any { it in combined } -> RecordingType.BIBLE_STUDY
                listOf("lecture", "course", "calculus", "algorithm", "chemistry", "physics", "biology", "professor", "exam", "university").any { it in combined } -> RecordingType.LECTURE
                listOf("motivation", "mindset", "discipline", "habit", "inspiration", "keynote", "self-improvement", "purpose").any { it in combined } -> RecordingType.MOTIVATION
                listOf("how to", "life skill", "tutorial", "step by step", "guide", "communication", "negotiation", "cooking", "personal finance", "productivity").any { it in combined } -> RecordingType.LIFE_SKILLS
                listOf("interview", "podcast", "q&a", "conversation with").any { it in combined } -> RecordingType.INTERVIEW
                else -> RecordingType.LECTURE
            }
        }
    }
}
