package com.example.ai.gemini

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.core.model.ActionItem
import com.example.core.model.ChatMessage
import com.example.core.model.Decision
import com.example.core.model.MeetingSummary
import com.example.core.model.Question
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GeminiTranscriptionResult(
    val title: String,
    val language: String,
    val segments: List<TranscriptSegment>,
    val summary: String,
    val topics: List<String>,
    val actionItems: List<ActionItem>,
    val decisions: List<Decision>,
    val questions: List<Question>
)

class GeminiApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val AUDIO_MODEL = "gemini-2.5-flash"
        private const val TEXT_MODEL = "gemini-3.5-flash"
    }

    private fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isBlank() || key == "MY_GEMINI_API_KEY") "" else key
        } catch (e: Throwable) {
            ""
        }
    }

    fun isConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * Sends the recorded audio file to Gemini 2.5 Flash for true verbatim speech transcription,
     * speaker diarization, timestamps, and executive intelligence.
     */
    suspend fun transcribeAudio(
        audioFile: File,
        meetingId: String,
        totalDurationMs: Long,
        onProgress: ((Float, String) -> Unit)? = null
    ): GeminiTranscriptionResult? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API key is not configured, skipping cloud ASR")
            return@withContext null
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.w(TAG, "Audio file does not exist or is empty: ${audioFile.absolutePath}")
            return@withContext null
        }

        try {
            onProgress?.invoke(0.1f, "Encoding recorded audio stream...")
            val audioBytes = audioFile.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val mimeType = when (audioFile.extension.lowercase()) {
                "m4a", "mp4" -> "audio/mp4"
                "aac" -> "audio/aac"
                "wav" -> "audio/wav"
                "mp3" -> "audio/mp3"
                else -> "audio/mp4"
            }

            onProgress?.invoke(0.3f, "Transcribing actual speech with Gemini 2.5 Flash...")

            val systemInstruction = """
                You are an expert audio transcription and speech intelligence model.
                Transcribe the provided audio recording verbatim with exact timestamps and speaker separation.
                Do NOT make up conversations or use generic placeholders. Transcribe exactly what was spoken in the audio.
                If the audio contains silence, ambient noise, or no recognizable human speech, indicate that in the summary and provide an empty segments list.
                
                Respond ONLY with a JSON object strictly adhering to this schema:
                {
                  "title": "A succinct descriptive title based on the actual spoken content (e.g., 'Project Strategy Sync', 'Grocery List Memo', etc.)",
                  "language": "en",
                  "summary": "Clear executive summary of what was actually said",
                  "topics": ["Key topic 1", "Key topic 2"],
                  "segments": [
                    {
                      "speakerId": "speaker_1",
                      "speakerName": "Speaker 1",
                      "startMs": 0,
                      "endMs": 2500,
                      "text": "Exact words spoken",
                      "confidence": 0.95
                    }
                  ],
                  "actionItems": [
                    {
                      "task": "Specific task mentioned in speech",
                      "assignee": "Person mentioned or Unassigned",
                      "deadline": "Deadline mentioned or TBD"
                    }
                  ],
                  "decisions": [
                    {
                      "text": "Specific decision agreed upon in the audio",
                      "timestampMs": 0
                    }
                  ],
                  "questions": [
                    {
                      "text": "Specific question asked in the speech",
                      "answer": "Answer given in speech or Unanswered"
                    }
                  ]
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                // Audio part
                val inlineDataObj = JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64Audio)
                }
                partsArray.put(JSONObject().apply {
                    put("inlineData", inlineDataObj)
                })

                // Prompt part
                partsArray.put(JSONObject().apply {
                    put("text", "Please transcribe this audio recording into structured transcript segments, summary, action items, and decisions.")
                })

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
                put("generationConfig", genConfig)

                val sysInstructionObj = JSONObject().apply {
                    val sysParts = JSONArray()
                    sysParts.put(JSONObject().apply { put("text", systemInstruction) })
                    put("parts", sysParts)
                }
                put("systemInstruction", sysInstructionObj)
            }

            val request = Request.Builder()
                .url("$BASE_URL/$AUDIO_MODEL:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            onProgress?.invoke(0.6f, "Analyzing acoustics and speaker turns...")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                Log.e(TAG, "Gemini API error ${response.code}: $responseBody")
                return@withContext null
            }

            onProgress?.invoke(0.85f, "Parsing transcript segments and meeting insights...")

            parseTranscriptionResponse(responseBody, meetingId, totalDurationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transcribe audio with Gemini", e)
            null
        }
    }

    /**
     * Uses Gemini 3.5 Flash to summarize an existing transcript, extract action items and decisions.
     */
    suspend fun generateIntelligence(
        transcript: Transcript,
        fallbackTitle: String
    ): MeetingSummary? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || transcript.segments.isEmpty()) {
            return@withContext null
        }

        try {
            val transcriptText = transcript.segments.joinToString("\n") { seg ->
                "[${seg.speakerName}]: ${seg.text}"
            }

            val prompt = """
                Analyze the following meeting transcript. Provide a structured summary, extracted action items with assignees/deadlines, decisions reached, topics, and unanswered questions based strictly on the transcript.
                Do NOT invent information not present in the transcript.
                
                Transcript:
                $transcriptText
                
                Respond in JSON format:
                {
                  "title": "Concise descriptive title",
                  "summary": "Executive overview of discussions",
                  "topics": ["Topic 1", "Topic 2"],
                  "actionItems": [
                    {
                      "task": "Task description",
                      "assignee": "Assignee or Unassigned",
                      "deadline": "Deadline or TBD"
                    }
                  ],
                  "decisions": [
                    {
                      "text": "Decision statement",
                      "timestampMs": 0
                    }
                  ],
                  "questions": [
                    {
                      "text": "Question asked",
                      "answer": "Answer or Unanswered"
                    }
                  ]
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()
                partsArray.put(JSONObject().apply { put("text", prompt) })
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                }
                put("generationConfig", genConfig)
            }

            val request = Request.Builder()
                .url("$BASE_URL/$TEXT_MODEL:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: return@withContext null

            val cleanJson = cleanJsonResponse(rawText)
            val json = JSONObject(cleanJson)

            val title = json.optString("title", fallbackTitle)
            val summaryStr = json.optString("summary", "Transcript analyzed.")
            val topics = mutableListOf<String>()
            val topicsArray = json.optJSONArray("topics")
            if (topicsArray != null) {
                for (i in 0 until topicsArray.length()) {
                    topics.add(topicsArray.getString(i))
                }
            }

            val actionItems = mutableListOf<ActionItem>()
            val actionsArray = json.optJSONArray("actionItems")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val itemObj = actionsArray.getJSONObject(i)
                    actionItems.add(
                        ActionItem(
                            id = UUID.randomUUID().toString(),
                            meetingId = transcript.meetingId,
                            task = itemObj.optString("task", "Action item"),
                            assignee = itemObj.optString("assignee", "Unassigned"),
                            deadline = itemObj.optString("deadline", "TBD"),
                            confidence = 0.95f,
                            isCompleted = false
                        )
                    )
                }
            }

            val decisions = mutableListOf<Decision>()
            val decisionsArray = json.optJSONArray("decisions")
            if (decisionsArray != null) {
                for (i in 0 until decisionsArray.length()) {
                    val decObj = decisionsArray.getJSONObject(i)
                    decisions.add(
                        Decision(
                            id = UUID.randomUUID().toString(),
                            meetingId = transcript.meetingId,
                            text = decObj.optString("text", "Decision"),
                            confidence = 0.95f,
                            timestampMs = decObj.optLong("timestampMs", 0L)
                        )
                    )
                }
            }

            val questions = mutableListOf<Question>()
            val questionsArray = json.optJSONArray("questions")
            if (questionsArray != null) {
                for (i in 0 until questionsArray.length()) {
                    val qObj = questionsArray.getJSONObject(i)
                    questions.add(
                        Question(
                            id = UUID.randomUUID().toString(),
                            meetingId = transcript.meetingId,
                            text = qObj.optString("text", "Question"),
                            resolved = true,
                            answer = qObj.optString("answer", "Addressed in discussion"),
                            timestampMs = 0L
                        )
                    )
                }
            }

            MeetingSummary(
                title = title,
                summary = summaryStr,
                topics = if (topics.isNotEmpty()) topics else listOf("Meeting Discussion"),
                decisions = decisions,
                actionItems = actionItems,
                questions = questions,
                followUps = emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating intelligence with Gemini", e)
            null
        }
    }

    /**
     * Answers interactive questions about the transcript with exact quotes and timestamps.
     */
    suspend fun askQuestion(
        question: String,
        transcript: Transcript
    ): ChatMessage? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext null

        try {
            val transcriptText = transcript.segments.joinToString("\n") { seg ->
                "[${seg.speakerName} at ${seg.startMs}ms]: ${seg.text}"
            }

            val prompt = """
                You are MeetMind AI, an intelligent assistant answering questions grounded in a meeting transcript.
                Answer the user's question accurately using ONLY the transcript provided.
                Include direct quotes from the conversation and reference timestamps where applicable.
                
                Transcript:
                $transcriptText
                
                User Question:
                $question
                
                Respond in JSON:
                {
                  "answer": "Your comprehensive answer",
                  "sourceTimestamps": [0, 5000],
                  "sourceQuotes": ["Direct quote from transcript"]
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()
                partsArray.put(JSONObject().apply { put("text", prompt) })
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
                put("generationConfig", genConfig)
            }

            val request = Request.Builder()
                .url("$BASE_URL/$TEXT_MODEL:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            val root = JSONObject(responseBody)
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            val rawText = candidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                ?: return@withContext null

            val json = JSONObject(cleanJsonResponse(rawText))
            val answer = json.optString("answer", "No answer could be found in the transcript.")
            val timestamps = mutableListOf<Long>()
            val tsArray = json.optJSONArray("sourceTimestamps")
            if (tsArray != null) {
                for (i in 0 until tsArray.length()) {
                    timestamps.add(tsArray.optLong(i))
                }
            }

            val quotes = mutableListOf<String>()
            val quotesArray = json.optJSONArray("sourceQuotes")
            if (quotesArray != null) {
                for (i in 0 until quotesArray.length()) {
                    quotes.add(quotesArray.getString(i))
                }
            }

            ChatMessage(
                id = UUID.randomUUID().toString(),
                meetingId = transcript.meetingId,
                isUser = false,
                content = answer,
                timestamp = System.currentTimeMillis(),
                sourceTimestamps = timestamps,
                sourceQuotes = quotes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in askQuestion Gemini call", e)
            null
        }
    }

    private fun parseTranscriptionResponse(
        responseBody: String,
        meetingId: String,
        totalDurationMs: Long
    ): GeminiTranscriptionResult {
        val root = JSONObject(responseBody)
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
        val rawText = candidate?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            ?: "{}"

        val cleanJson = cleanJsonResponse(rawText)
        val json = JSONObject(cleanJson)

        val title = json.optString("title", "Voice Recording").ifBlank { "Voice Recording" }
        val language = json.optString("language", "en")
        val summary = json.optString("summary", "Audio recording transcribed successfully.")

        val segments = mutableListOf<TranscriptSegment>()
        val segmentsArray = json.optJSONArray("segments")
        if (segmentsArray != null) {
            for (i in 0 until segmentsArray.length()) {
                val segObj = segmentsArray.getJSONObject(i)
                val text = segObj.optString("text", "").trim()
                if (text.isNotBlank()) {
                    val startMs = segObj.optLong("startMs", (i * 2000L).coerceAtMost(totalDurationMs))
                    val endMs = segObj.optLong("endMs", (startMs + 2500L).coerceAtMost(totalDurationMs))
                    val speakerId = segObj.optString("speakerId", "speaker_1")
                    val speakerName = segObj.optString("speakerName", "Speaker 1")
                    val confidence = segObj.optDouble("confidence", 0.95).toFloat()

                    segments.add(
                        TranscriptSegment(
                            id = UUID.randomUUID().toString(),
                            meetingId = meetingId,
                            speakerId = speakerId,
                            speakerName = speakerName,
                            startMs = startMs,
                            endMs = if (endMs > startMs) endMs else startMs + 1000L,
                            text = text,
                            confidence = confidence
                        )
                    )
                }
            }
        }

        // If no segments were extracted (e.g. ambient audio), create a single informative segment
        if (segments.isEmpty()) {
            segments.add(
                TranscriptSegment(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    speakerId = "speaker_1",
                    speakerName = "Speaker 1",
                    startMs = 0L,
                    endMs = totalDurationMs.coerceAtLeast(1000L),
                    text = if (summary.isNotBlank()) summary else "No distinct human speech detected in recording.",
                    confidence = 0.85f
                )
            )
        }

        val topics = mutableListOf<String>()
        val topicsArray = json.optJSONArray("topics")
        if (topicsArray != null) {
            for (i in 0 until topicsArray.length()) {
                topics.add(topicsArray.getString(i))
            }
        }

        val actionItems = mutableListOf<ActionItem>()
        val actionsArray = json.optJSONArray("actionItems")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val itemObj = actionsArray.getJSONObject(i)
                val task = itemObj.optString("task", "").trim()
                if (task.isNotBlank()) {
                    actionItems.add(
                        ActionItem(
                            id = UUID.randomUUID().toString(),
                            meetingId = meetingId,
                            task = task,
                            assignee = itemObj.optString("assignee", "Unassigned"),
                            deadline = itemObj.optString("deadline", "TBD"),
                            confidence = 0.95f,
                            isCompleted = false
                        )
                    )
                }
            }
        }

        val decisions = mutableListOf<Decision>()
        val decisionsArray = json.optJSONArray("decisions")
        if (decisionsArray != null) {
            for (i in 0 until decisionsArray.length()) {
                val decObj = decisionsArray.getJSONObject(i)
                val text = decObj.optString("text", "").trim()
                if (text.isNotBlank()) {
                    decisions.add(
                        Decision(
                            id = UUID.randomUUID().toString(),
                            meetingId = meetingId,
                            text = text,
                            confidence = 0.95f,
                            timestampMs = decObj.optLong("timestampMs", 0L)
                        )
                    )
                }
            }
        }

        val questions = mutableListOf<Question>()
        val questionsArray = json.optJSONArray("questions")
        if (questionsArray != null) {
            for (i in 0 until questionsArray.length()) {
                val qObj = questionsArray.getJSONObject(i)
                val text = qObj.optString("text", "").trim()
                if (text.isNotBlank()) {
                    questions.add(
                        Question(
                            id = UUID.randomUUID().toString(),
                            meetingId = meetingId,
                            text = text,
                            resolved = true,
                            answer = qObj.optString("answer", "Addressed in discussion"),
                            timestampMs = 0L
                        )
                    )
                }
            }
        }

        return GeminiTranscriptionResult(
            title = title,
            language = language,
            segments = segments,
            summary = summary,
            topics = if (topics.isNotEmpty()) topics else listOf("Voice Note"),
            actionItems = actionItems,
            decisions = decisions,
            questions = questions
        )
    }

    private fun cleanJsonResponse(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json").trim()
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```").trim()
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```").trim()
        }
        return str
    }
}
