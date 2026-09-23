package com.example.ai.faith

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.ai.tools.TranscriptToolPrompts
import com.example.core.model.TranscriptSegment
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureReferenceParser
import org.json.JSONArray
import org.json.JSONObject

/** Words from the recording, and the transcript paragraphs they came from. */
data class CitedText(val text: String, val segmentIds: List<String>)

/** A passage the sermon was built on, with where it was cited. */
data class CitedScripture(val reference: ScriptureReference, val segmentIds: List<String>)

/** What a sermon's notes are made of. Every entry points back at the transcript. */
data class SermonExtraction(
    val title: String?,
    val mainScripture: List<CitedScripture>,
    val keyMessage: CitedText?,
    val keyPoints: List<CitedText>,
    val quotes: List<CitedText>,
    val application: CitedText?,
    val prayerPoints: List<CitedText>,
    val reflectionQuestions: List<CitedText>
) {
    val isEmpty: Boolean
        get() = mainScripture.isEmpty() && keyMessage == null && keyPoints.isEmpty() && quotes.isEmpty() &&
            application == null && prayerPoints.isEmpty() && reflectionQuestions.isEmpty()
}

/**
 * The prompts for Faith workflows (docs/PLAN_V1.md §8, the AI boundary).
 *
 * Every Faith prompt carries [FAITH_CONTRACT] verbatim on top of the transcript fidelity contract;
 * a test fails if one doesn't. MeetingMind reports what a preacher said about a passage. It never
 * interprets scripture in its own voice or speaks as a spiritual authority.
 */
object FaithPrompts {

    const val FAITH_CONTRACT = """
This is a recording of someone speaking about their faith.

You MUST NOT:
- interpret, explain or apply scripture in your own voice
- add theology, doctrine or spiritual advice that the speaker did not give
- speak as a spiritual authority, or tell the listener what God wants
- quote a Bible verse that the speaker did not read or refer to

You MUST:
- report the speaker's own explanation as theirs
- write reflection questions only from something the speaker said, and cite where they said it
"""

    val SERMON_SCHEMA = """
{
  "title": "a short title for the sermon, from its main message",
  "main_scripture": [{"reference": "Book chapter:verse as the preacher gave it", "segment_ids": ["..."]}],
  "key_message": {"text": "the preacher's main message in one or two sentences", "segment_ids": ["..."]},
  "key_points": [{"text": "one point, in the order the preacher made them", "segment_ids": ["..."]}],
  "quotes": [{"text": "a sentence the preacher said, copied word for word", "segment_ids": ["..."]}],
  "application": {"text": "what the preacher asked people to do", "segment_ids": ["..."]},
  "prayer_points": [{"text": "something the preacher prayed for or asked prayer for", "segment_ids": ["..."]}],
  "reflection_questions": [{"text": "a question drawn from something the preacher said", "segment_ids": ["..."]}]
}
""".trimIndent()

    fun sermonPrompt(segments: List<TranscriptSegment>, part: Int = 1, parts: Int = 1): String = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine(FAITH_CONTRACT.trim())
        appendLine()
        append("Task: Write notes on this sermon for someone who heard it. ")
        if (parts > 1) append("This is part $part of $parts of the sermon; only report what is in this part. ")
        appendLine(
            "Keep the preacher's order and wording. Quotes must be copied exactly from the transcript. " +
                "Leave any field empty or null when the sermon doesn't contain it."
        )
        appendLine()
        appendLine("Answer with JSON matching this schema, and nothing else:")
        appendLine(SERMON_SCHEMA)
        appendLine()
        appendLine("Transcript:")
        append(TranscriptToolPrompts.renderTranscript(segments))
    }
}

/** Turns the model's answer into a [SermonExtraction], dropping anything it can't stand behind. */
object SermonExtractionParser {

    private const val MAX_POINTS = 8
    private const val MAX_QUOTES = 5
    private const val MAX_LIST = 6

    fun parse(raw: String, segments: List<TranscriptSegment>): SermonExtraction? {
        val json = extractJson(raw) ?: return null
        val byId = segments.associateBy { it.id }

        fun ids(obj: JSONObject?): List<String> {
            val arr = obj?.optJSONArray("segment_ids") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { id -> id in byId } }.distinct()
        }

        fun cited(obj: JSONObject?): CitedText? {
            val text = obj?.optString("text")?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
            val segs = ids(obj)
            // Nothing is kept that doesn't point at the recording.
            return if (segs.isEmpty()) null else CitedText(text, segs)
        }

        fun list(key: String, max: Int): List<CitedText> =
            json.optJSONArray(key).objects().mapNotNull(::cited).distinctBy { normalise(it.text) }.take(max)

        val quotes = json.optJSONArray("quotes").objects().mapNotNull { q ->
            val c = cited(q) ?: return@mapNotNull null
            verbatim(c, segments, byId)
        }.distinctBy { normalise(it.text) }.take(MAX_QUOTES)

        val scripture = json.optJSONArray("main_scripture").objects().mapNotNull { s ->
            val ref = ScriptureReferenceParser.parse(s.optString("reference")) ?: return@mapNotNull null
            val segs = ids(s)
            if (segs.isEmpty()) null else CitedScripture(ref, segs)
        }.distinctBy { it.reference }

        return SermonExtraction(
            title = json.optString("title").trim().takeIf { it.isNotEmpty() && it != "null" && it.length <= 120 },
            mainScripture = scripture,
            keyMessage = cited(json.optJSONObject("key_message")),
            keyPoints = list("key_points", MAX_POINTS),
            quotes = quotes,
            application = cited(json.optJSONObject("application")),
            prayerPoints = list("prayer_points", MAX_LIST),
            reflectionQuestions = list("reflection_questions", MAX_LIST)
        )
    }

    /**
     * A quote is kept only if it really was said: its words must appear, in order, in a cited
     * paragraph (ignoring case and punctuation) — and if the model cited the wrong paragraph but
     * the words are elsewhere in the transcript, the citation is corrected rather than trusted.
     */
    internal fun verbatim(quote: CitedText, segments: List<TranscriptSegment>, byId: Map<String, TranscriptSegment>): CitedText? {
        val needle = normalise(quote.text)
        if (needle.split(' ').size < 3) return null
        val cited = quote.segmentIds.mapNotNull { byId[it] }
        if (normalise(cited.joinToString(" ") { it.cleanedText ?: it.text }).contains(needle)) return quote
        val found = segments.firstOrNull { normalise(it.cleanedText ?: it.text).contains(needle) } ?: return null
        return quote.copy(segmentIds = listOf(found.id))
    }

    /** Merges the notes from consecutive parts of a long sermon. */
    fun merge(parts: List<SermonExtraction>): SermonExtraction = SermonExtraction(
        title = parts.firstNotNullOfOrNull { it.title },
        mainScripture = parts.flatMap { it.mainScripture }.distinctBy { it.reference },
        keyMessage = parts.firstNotNullOfOrNull { it.keyMessage },
        keyPoints = parts.flatMap { it.keyPoints }.distinctBy { normalise(it.text) }.take(MAX_POINTS),
        quotes = parts.flatMap { it.quotes }.distinctBy { normalise(it.text) }.take(MAX_QUOTES),
        application = parts.lastOrNull { it.application != null }?.application,
        prayerPoints = parts.flatMap { it.prayerPoints }.distinctBy { normalise(it.text) }.take(MAX_LIST),
        reflectionQuestions = parts.flatMap { it.reflectionQuestions }.distinctBy { normalise(it.text) }.take(MAX_LIST)
    )

    internal fun normalise(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun extractJson(raw: String): JSONObject? {
        val cleaned = raw.replace("```json", "", ignoreCase = true).replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/**
 * Writes a sermon's notes with whatever model [LanguageModel] resolves to — Gemini in Internet mode,
 * the on-device model otherwise. A sermon too long for the model's context is read in parts and
 * the parts' notes merged, so a small on-device model can still do it, just more slowly.
 */
class SermonExtractionEngine(
    private val languageModel: LanguageModel,
    private val contextTokens: Int
) {
    suspend fun extract(segments: List<TranscriptSegment>, onPart: (Int, Int) -> Unit = { _, _ -> }): AiResult<SermonExtraction> {
        if (segments.isEmpty()) return AiResult.Failed("There is no transcript to write notes from.")
        val parts = chunk(segments)
        val results = mutableListOf<SermonExtraction>()
        var lastFailure: AiResult<*>? = null
        parts.forEachIndexed { i, part ->
            onPart(i + 1, parts.size)
            when (val out = languageModel.generate(FaithPrompts.sermonPrompt(part, i + 1, parts.size), maxOutputTokens = OUTPUT_TOKENS)) {
                is AiResult.Success -> SermonExtractionParser.parse(out.value, segments)?.let { results += it }
                else -> lastFailure = out
            }
        }
        if (results.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            return (lastFailure as? AiResult<SermonExtraction>) ?: AiResult.Failed("The model's answer couldn't be used.")
        }
        return AiResult.Success(if (results.size == 1) results.single() else SermonExtractionParser.merge(results))
    }

    /** Splits the transcript so each part plus the prompt fits the model's context. */
    internal fun chunk(segments: List<TranscriptSegment>): List<List<TranscriptSegment>> {
        val budgetChars = ((contextTokens - OUTPUT_TOKENS - PROMPT_OVERHEAD_TOKENS).coerceAtLeast(600)) * CHARS_PER_TOKEN
        val parts = mutableListOf<List<TranscriptSegment>>()
        var current = mutableListOf<TranscriptSegment>()
        var size = 0
        for (s in segments) {
            val len = (s.cleanedText ?: s.text).length + s.id.length + 24
            if (current.isNotEmpty() && size + len > budgetChars) {
                parts += current; current = mutableListOf(); size = 0
            }
            current += s; size += len
        }
        if (current.isNotEmpty()) parts += current
        return parts
    }

    companion object {
        const val OUTPUT_TOKENS = 1_500
        private const val PROMPT_OVERHEAD_TOKENS = 900
        private const val CHARS_PER_TOKEN = 4
    }
}
