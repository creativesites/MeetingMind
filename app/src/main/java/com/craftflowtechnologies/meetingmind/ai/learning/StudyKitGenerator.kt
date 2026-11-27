package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.feature.learn.LessonSection
import com.craftflowtechnologies.meetingmind.feature.learn.LessonTerm
import com.craftflowtechnologies.meetingmind.feature.learn.OptionWhy
import com.craftflowtechnologies.meetingmind.feature.learn.QuickCheck
import com.craftflowtechnologies.meetingmind.feature.learn.StressReason
import com.craftflowtechnologies.meetingmind.feature.learn.StressedMoment
import com.craftflowtechnologies.meetingmind.feature.learn.StudyKit
import com.craftflowtechnologies.meetingmind.feature.learn.TimeRange
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A lecture to turn into a study kit: its transcript segments, in any order. */
data class StudyLectureInput(
    val sessionId: String,
    val meetingId: String?,
    val segments: List<TranscriptSegment>
)

/** Everything a generation produces, ready to persist: the kit, its concepts and its activities. */
data class GeneratedStudyContent(
    val kit: StudyKit,
    val concepts: List<LearningConcept>,
    val activities: List<LearningActivity>
)

sealed interface StudyKitResult {
    data class Ready(val content: GeneratedStudyContent) : StudyKitResult
    /** The lecture has no words to work from (still processing, or silence). */
    data object NoTranscript : StudyKitResult
    /** No AI is available here: offline with no local model, or not set up. */
    data class NeedsAi(val message: String) : StudyKitResult
    data class Failed(val message: String) : StudyKitResult
}

/**
 * Turns one lecture's transcript into a [StudyKit], its concepts and its activities (PLAN_LEARNING
 * §5.4). Every model answer is checked against the transcript before anything is kept:
 *
 * - every timestamp sits inside the lecture;
 * - every quote matches the transcript near its timestamp (token overlap of at least 0.6), or the
 *   item is dropped;
 * - every term appears in its section body and maps to a concept;
 * - multiple-choice items have four distinct options and a correct index in range, and the right
 *   answer's text does not appear in the question;
 * - questions are never sourced from hard-to-hear ranges, which are computed here from ASR
 *   confidence, never by the model;
 * - near-identical questions are dropped, and the lecture is capped at [MAX_CONCEPTS] concepts and
 *   [MAX_ACTIVITIES] activities.
 *
 * Long lectures are cut into chunks that fit [contextLengthTokens]. The summary is written last,
 * from the merged sections.
 */
class StudyKitGenerator(
    private val model: LanguageModel,
    private val modelId: String,
    private val contextLengthTokens: Int,
    private val maxOutputTokens: Int = DEFAULT_OUTPUT_TOKENS,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {

    private class Line(val startMs: Long, val endMs: Long, val text: String, val segmentId: String)

    private class Lecture(val lines: List<Line>, val endMs: Long, val hardToHear: List<TimeRange>)

    private class ConceptDraft(
        val name: String,
        val definition: String,
        val emphasis: String?,
        val relationships: List<String>,
        val evidence: Evidence
    )

    private class Evidence(val startMs: Long, val endMs: Long, val quote: String)

    private class SectionDraft(
        val title: String,
        val startMs: Long?,
        val body: String,
        val terms: List<Pair<String, String>>,
        val quickCheck: Triple<String, String, String?>?
    )

    private class StressDraft(val evidence: Evidence, val reason: StressReason, val conceptName: String?)

    private class ActivityDraft(
        val type: LearningActivityType,
        val conceptName: String,
        val prompt: String,
        val expectedAnswer: String,
        val options: List<String>,
        val rightWhy: String,
        val wrongWhys: Map<Int, String>,
        val evidence: Evidence
    )

    private sealed interface Ask {
        data class Json(val obj: JSONObject) : Ask
        data object Malformed : Ask
        data class Stop(val result: StudyKitResult) : Ask
    }

    suspend fun generate(input: StudyLectureInput, onProgress: (String) -> Unit = {}): StudyKitResult {
        val lines = input.segments
            .mapNotNull { s ->
                (s.cleanedText ?: s.text).trim().takeIf { it.isNotEmpty() }?.let { Line(s.startMs, s.endMs, it, s.id) }
            }
            .sortedBy { it.startMs }
        if (lines.isEmpty()) return StudyKitResult.NoTranscript

        val lectureEnd = lines.maxOf { it.endMs }.coerceAtLeast(lines.last().startMs)
        val lecture = Lecture(lines, lectureEnd, hardToHearRanges(input.segments))
        val chunks = chunk(lines, budgetChars())

        // 1. Reading: concepts and sections, chunk by chunk.
        onProgress(CAPTION_READING)
        val readings = mutableListOf<JSONObject>()
        var malformed = 0
        for (chunkLines in chunks) {
            when (val ask = askJson(readingPrompt(chunkLines))) {
                is Ask.Stop -> return ask.result
                is Ask.Malformed -> malformed++
                is Ask.Json -> readings += ask.obj
            }
        }
        if (readings.isEmpty()) return StudyKitResult.Failed(MALFORMED_MESSAGE)

        // 2. Stressed moments and concept validation.
        onProgress(CAPTION_STRESSED)
        val concepts = mergeConcepts(readings.flatMap { parseConcepts(it, lecture) })
        val conceptIds = concepts.associate { it.name.lowercase() to it.id }
        val sections = mergeSections(readings.flatMap { parseSections(it, lecture) }, conceptIds)
        val stressed = readings.flatMap { parseStressed(it, lecture) }
            .distinctBy { it.evidence.startMs to it.reason }
            .map { draft ->
                StressedMoment(
                    startMs = draft.evidence.startMs,
                    endMs = draft.evidence.endMs,
                    quote = draft.evidence.quote,
                    reason = draft.reason,
                    conceptId = draft.conceptName?.lowercase()?.let { conceptIds[it] }
                )
            }

        // 3. Quick checks: multiple-choice and recall activities, from a sample of chunks.
        onProgress(CAPTION_CHECKS)
        val conceptNames = concepts.map { it.name }
        val activityChunks = sampleChunks(chunks, MAX_ACTIVITY_CALLS)
        val activityDrafts = mutableListOf<ActivityDraft>()
        val perChunkCap = (MAX_ACTIVITIES / activityChunks.size.coerceAtLeast(1)).coerceAtLeast(2)
        for (chunkLines in activityChunks) {
            when (val ask = askJson(activityPrompt(chunkLines, conceptNames))) {
                is Ask.Stop -> return ask.result
                is Ask.Malformed -> malformed++
                is Ask.Json -> activityDrafts += parseActivities(ask.obj, lecture, conceptNames).take(perChunkCap)
            }
        }
        val activities = acceptActivities(activityDrafts, concepts, lecture)

        // 4. Summary, written last from the merged sections.
        val summary = when (val ask = askJson(summaryPrompt(sections, concepts))) {
            is Ask.Stop -> return ask.result
            is Ask.Malformed -> return StudyKitResult.Failed(MALFORMED_MESSAGE)
            is Ask.Json -> ask.obj.optString("summary").trim()
        }
        if (summary.isBlank()) return StudyKitResult.Failed(MALFORMED_MESSAGE)

        val createdAt = now()
        val sessionId = input.sessionId
        val conceptEntities = concepts.map { draft ->
            LearningConcept(
                id = draft.id,
                sessionId = sessionId,
                name = draft.name,
                definition = draft.definition,
                emphasis = draft.emphasis,
                relationships = draft.relationships,
                evidence = listOf(draft.evidence.toLearningEvidence(input.meetingId, lecture)),
                state = LearningMasteryState.NEW,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
        val activityEntities = activities.map { a ->
            LearningActivity(
                id = a.id,
                sessionId = sessionId,
                conceptId = a.conceptId,
                type = a.type,
                prompt = a.prompt,
                expectedAnswer = a.expectedAnswer,
                options = a.options,
                evidence = listOf(a.evidence.toLearningEvidence(input.meetingId, lecture)),
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
        val kit = StudyKit(
            sessionId = sessionId,
            summary = summary,
            stressed = stressed,
            sections = sections.mapIndexed { i, s ->
                LessonSection(
                    index = i,
                    title = s.title,
                    body = s.body,
                    terms = s.terms.map { (text, conceptId) -> LessonTerm(text, conceptId) },
                    quickCheck = s.quickCheck?.let { (prompt, answer, conceptId) ->
                        QuickCheck(prompt, answer, conceptId)
                    },
                    startMs = s.startMs
                )
            },
            hardToHear = lecture.hardToHear,
            optionWhys = activities.flatMap { a ->
                a.wrongWhys.map { (index, why) -> OptionWhy(a.id, index, why) }
            },
            rightWhys = activities.filter { it.rightWhy.isNotBlank() }.associate { it.id to it.rightWhy },
            modelId = modelId,
            createdAt = createdAt
        )
        return StudyKitResult.Ready(GeneratedStudyContent(kit, conceptEntities, activityEntities))
    }

    // ---------------------------------------------------------------- model calls

    private suspend fun askJson(prompt: String): Ask {
        val reply = when (val res = model.generate(prompt, maxOutputTokens)) {
            is AiResult.Success -> res.value
            is AiResult.ModelUnavailable, is AiResult.DeviceUnsupported,
            is AiResult.InsufficientMemory, is AiResult.InsufficientStorage ->
                return Ask.Stop(StudyKitResult.NeedsAi(res.describeFailure() ?: NO_AI_MESSAGE))
            is AiResult.Failed -> return Ask.Stop(StudyKitResult.Failed(res.describeFailure() ?: GENERIC_FAILURE))
        }
        val json = extractJsonObject(reply) ?: return Ask.Malformed
        return Ask.Json(json)
    }

    // ---------------------------------------------------------------- reading

    private fun parseConcepts(root: JSONObject, lecture: Lecture): List<ConceptDraft> {
        val arr = root.optJSONArray("concepts") ?: return emptyList()
        return objects(arr).mapNotNull { o ->
            val name = o.text("name") ?: return@mapNotNull null
            val definition = o.text("definition") ?: return@mapNotNull null
            val evidence = readEvidence(o, lecture) ?: return@mapNotNull null
            ConceptDraft(
                name = name,
                definition = definition,
                emphasis = o.text("emphasis"),
                relationships = strings(o.optJSONArray("relationships")).distinct(),
                evidence = evidence
            )
        }
    }

    private fun mergeConcepts(drafts: List<ConceptDraft>): List<MergedConcept> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<MergedConcept>()
        for (d in drafts) {
            if (!seen.add(d.name.lowercase())) continue
            out += MergedConcept(newId(), d.name, d.definition, d.emphasis, d.relationships, d.evidence)
            if (out.size == MAX_CONCEPTS) break
        }
        return out
    }

    private class MergedConcept(
        val id: String,
        val name: String,
        val definition: String,
        val emphasis: String?,
        val relationships: List<String>,
        val evidence: Evidence
    )

    private fun parseSections(root: JSONObject, lecture: Lecture): List<SectionDraft> {
        val arr = root.optJSONArray("sections") ?: return emptyList()
        return objects(arr).mapNotNull { o ->
            val title = o.text("title") ?: return@mapNotNull null
            val body = o.text("body") ?: return@mapNotNull null
            val start = o.optLong("startMs", -1L).takeIf { o.has("startMs") && it in 0..lecture.endMs }
            val terms = objects(o.optJSONArray("terms")).mapNotNull { t ->
                val text = t.text("text") ?: return@mapNotNull null
                val conceptName = t.text("conceptName") ?: return@mapNotNull null
                // A term must occur in the section body it underlines.
                if (!body.contains(text, ignoreCase = true)) return@mapNotNull null
                text to conceptName
            }
            val qc = o.optJSONObject("quickCheck")?.let { q ->
                val prompt = q.text("prompt")
                val answer = q.text("answer")
                if (prompt != null && answer != null) Triple(prompt, answer, q.text("conceptName")) else null
            }
            SectionDraft(title, start, body, terms, qc)
        }
    }

    /** Sections keep lecture order; terms are resolved to concept ids and unresolved ones dropped. */
    private fun mergeSections(drafts: List<SectionDraft>, conceptIds: Map<String, String>): List<ResolvedSection> {
        return drafts
            .sortedBy { it.startMs ?: Long.MAX_VALUE }
            .map { d ->
                ResolvedSection(
                    title = d.title,
                    body = d.body,
                    startMs = d.startMs,
                    terms = d.terms.mapNotNull { (text, name) -> conceptIds[name.lowercase()]?.let { text to it } },
                    quickCheck = d.quickCheck?.let { (p, a, name) -> Triple(p, a, name?.let { conceptIds[it.lowercase()] }) }
                )
            }
    }

    private class ResolvedSection(
        val title: String,
        val body: String,
        val startMs: Long?,
        val terms: List<Pair<String, String>>,
        val quickCheck: Triple<String, String, String?>?
    )

    private fun parseStressed(root: JSONObject, lecture: Lecture): List<StressDraft> {
        val arr = root.optJSONArray("stressed") ?: return emptyList()
        return objects(arr).mapNotNull { o ->
            val reason = parseReason(o.text("reason")) ?: return@mapNotNull null
            val evidence = readEvidence(o, lecture) ?: return@mapNotNull null
            StressDraft(evidence, reason, o.text("conceptName"))
        }
    }

    // ---------------------------------------------------------------- activities

    private fun parseActivities(root: JSONObject, lecture: Lecture, conceptNames: List<String>): List<ActivityDraft> {
        val arr = root.optJSONArray("activities") ?: return emptyList()
        return objects(arr).mapNotNull { o ->
            val type = when (o.text("type")?.uppercase()) {
                "MULTIPLE_CHOICE" -> LearningActivityType.MULTIPLE_CHOICE
                "RECALL" -> LearningActivityType.RECALL
                else -> return@mapNotNull null
            }
            val prompt = o.text("prompt") ?: return@mapNotNull null
            val conceptName = o.text("conceptName")?.takeIf { name -> conceptNames.any { it.equals(name, true) } }
                ?: return@mapNotNull null
            val evidence = readEvidence(o, lecture) ?: return@mapNotNull null
            when (type) {
                LearningActivityType.MULTIPLE_CHOICE -> {
                    val options = strings(o.optJSONArray("options")).map { it.trim() }.filter { it.isNotEmpty() }
                    if (options.size != CHOICE_COUNT || options.map { it.lowercase() }.distinct().size != CHOICE_COUNT) {
                        return@mapNotNull null
                    }
                    if (!o.has("correctIndex")) return@mapNotNull null
                    val correct = o.optInt("correctIndex", -1)
                    if (correct !in options.indices) return@mapNotNull null
                    val wrong = wrongWhys(o.optJSONObject("wrongWhys"))
                    ActivityDraft(
                        type = type,
                        conceptName = conceptName,
                        prompt = prompt,
                        expectedAnswer = options[correct],
                        options = options,
                        rightWhy = o.text("rightWhy").orEmpty(),
                        wrongWhys = wrong,
                        evidence = evidence
                    )
                }
                else -> {
                    val answer = o.text("answer") ?: return@mapNotNull null
                    ActivityDraft(
                        type = type,
                        conceptName = conceptName,
                        prompt = prompt,
                        expectedAnswer = answer,
                        options = emptyList(),
                        rightWhy = o.text("rightWhy").orEmpty(),
                        wrongWhys = emptyMap(),
                        evidence = evidence
                    )
                }
            }
        }
    }

    private fun wrongWhys(obj: JSONObject?): Map<Int, String> {
        if (obj == null) return emptyMap()
        val out = linkedMapOf<Int, String>()
        for (key in obj.keys()) {
            val index = key.toIntOrNull() ?: continue
            val why = obj.optString(key).trim()
            if (index in 0 until CHOICE_COUNT && why.isNotEmpty()) out[index] = why
        }
        return out
    }

    /** Applies the activity rules that need the whole lecture: answer leakage, hard-to-hear, duplicates, caps. */
    private fun acceptActivities(drafts: List<ActivityDraft>, concepts: List<MergedConcept>, lecture: Lecture): List<AcceptedActivity> {
        val accepted = mutableListOf<AcceptedActivity>()
        for (d in drafts) {
            if (accepted.size >= MAX_ACTIVITIES) break
            if (answerLeaks(d.prompt, d.expectedAnswer)) continue
            if (overlapsHardToHear(d.evidence, lecture.hardToHear)) continue
            val concept = concepts.firstOrNull { it.name.equals(d.conceptName, ignoreCase = true) } ?: continue
            if (accepted.any { tokenJaccard(it.prompt, d.prompt) >= NEAR_DUPLICATE }) continue
            val wrongWhys = d.wrongWhys.filterKeys { it in d.options.indices }
            accepted += AcceptedActivity(
                id = newId(),
                conceptId = concept.id,
                type = d.type,
                prompt = d.prompt,
                expectedAnswer = d.expectedAnswer,
                options = d.options,
                evidence = d.evidence,
                rightWhy = d.rightWhy,
                wrongWhys = wrongWhys
            )
        }
        return accepted
    }

    private class AcceptedActivity(
        val id: String,
        val conceptId: String,
        val type: LearningActivityType,
        val prompt: String,
        val expectedAnswer: String,
        val options: List<String>,
        val evidence: Evidence,
        val rightWhy: String,
        val wrongWhys: Map<Int, String>
    )

    // ---------------------------------------------------------------- evidence

    /** An evidence object with valid timestamps and a quote that matches the transcript there, or null. */
    private fun readEvidence(o: JSONObject, lecture: Lecture): Evidence? {
        if (!o.has("startMs") || !o.has("endMs")) return null
        val start = o.optLong("startMs", -1L)
        val end = o.optLong("endMs", -1L)
        if (start < 0 || end < start || end > lecture.endMs) return null
        val quote = o.text("quote") ?: return null
        if (!quoteMatches(quote, start, end, lecture.lines.map { Triple(it.startMs, it.endMs, it.text) })) return null
        return Evidence(start, end, quote)
    }

    private fun Evidence.toLearningEvidence(meetingId: String?, lecture: Lecture): LearningEvidence = LearningEvidence(
        meetingId = meetingId,
        segmentIds = lecture.lines.filter { it.endMs >= startMs && it.startMs <= endMs }.map { it.segmentId },
        startMs = startMs,
        endMs = endMs,
        quote = quote
    )

    private fun overlapsHardToHear(e: Evidence, ranges: List<TimeRange>): Boolean =
        ranges.any { r -> e.startMs < r.endMs && e.endMs > r.startMs }

    // ---------------------------------------------------------------- prompts

    private fun readingPrompt(chunk: List<Line>): String = buildString {
        appendLine("You are reading part of a lecture transcript to help a student study.")
        appendLine("Use only what the transcript says. Do not add facts it does not contain.")
        appendLine("Each line starts with its time in milliseconds. Copy those times exactly into startMs and endMs.")
        appendLine("Quotes must be the transcript's own words, copied exactly from one or more lines.")
        appendLine("Output one JSON object only, in this exact shape:")
        appendLine(READING_SCHEMA)
        appendLine("reason must be one of REPEATED, NAMED_EXAM, SLOWED_DOWN, CALLED_IMPORTANT.")
        appendLine()
        appendLine("TRANSCRIPT:")
        chunk.forEach { appendLine("[${it.startMs}] ${it.text}") }
    }

    private fun activityPrompt(chunk: List<Line>, conceptNames: List<String>): String = buildString {
        appendLine("Write short quick checks for a student, based only on this part of the lecture.")
        appendLine("Each question must be answerable from the transcript. Never put the answer's words in the question.")
        appendLine("MULTIPLE_CHOICE: exactly four distinct options, one correct. correctIndex is 0-3.")
        appendLine("RECALL: a short question with a one-sentence answer.")
        appendLine("conceptName must be one of: ${conceptNames.joinToString("; ")}.")
        appendLine("Each line starts with its time in milliseconds. Copy startMs and endMs from the lines the question uses.")
        appendLine("Output one JSON object only, in this exact shape:")
        appendLine(ACTIVITY_SCHEMA)
        appendLine()
        appendLine("TRANSCRIPT:")
        chunk.forEach { appendLine("[${it.startMs}] ${it.text}") }
    }

    private fun summaryPrompt(sections: List<ResolvedSection>, concepts: List<MergedConcept>): String = buildString {
        appendLine("Write a summary of this lecture for a student, about three sentences, readable in 30 seconds.")
        appendLine("Use only the material below. Do not add facts.")
        appendLine("Output one JSON object only: {\"summary\": \"...\"}")
        appendLine()
        appendLine("IDEAS: ${concepts.joinToString("; ") { it.name }}")
        appendLine()
        sections.forEach { s -> appendLine("${s.title}: ${s.body.take(SUMMARY_SECTION_CHARS)}") }
    }

    // ---------------------------------------------------------------- chunking and budgets

    private fun budgetChars(): Int {
        val tokens = contextLengthTokens - PROMPT_RESERVE_TOKENS - maxOutputTokens
        return (tokens * CHARS_PER_TOKEN).coerceAtLeast(MIN_CHUNK_CHARS)
    }

    private fun chunk(lines: List<Line>, budget: Int): List<List<Line>> {
        val chunks = mutableListOf<List<Line>>()
        var current = mutableListOf<Line>()
        var size = 0
        for (line in lines) {
            val cost = line.text.length + LINE_OVERHEAD
            if (current.isNotEmpty() && size + cost > budget) {
                chunks += current
                current = mutableListOf()
                size = 0
            }
            current += line
            size += cost
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    private fun sampleChunks(chunks: List<List<Line>>, max: Int): List<List<Line>> {
        if (chunks.size <= max) return chunks
        return List(max) { i -> chunks[i * chunks.size / max] }
    }

    companion object {
        const val DEFAULT_OUTPUT_TOKENS = 1536
        const val MAX_CONCEPTS = 12
        const val MAX_ACTIVITIES = 16

        /** Captions shown while the kit is made. Plain words, no jargon. */
        const val CAPTION_READING = "Reading your lecture"
        const val CAPTION_STRESSED = "Finding what your lecturer stressed"
        const val CAPTION_CHECKS = "Writing your quick checks"

        /** Quote-to-transcript token overlap that counts as a match (PLAN_LEARNING §5.4). */
        const val QUOTE_OVERLAP = 0.6

        /** Two questions sharing this much of their vocabulary are the same question. */
        const val NEAR_DUPLICATE = 0.8

        /** Transcript window either side of a quote's timestamps that a quote may match in. */
        const val QUOTE_WINDOW_MS = 10_000L

        /** Low-confidence segments below this ASR confidence are hard to hear. */
        const val HARD_TO_HEAR_CONFIDENCE = 0.5f

        private const val CHOICE_COUNT = 4
        private const val MAX_ACTIVITY_CALLS = 4
        private const val PROMPT_RESERVE_TOKENS = 900
        private const val CHARS_PER_TOKEN = 3
        private const val MIN_CHUNK_CHARS = 2_000
        private const val LINE_OVERHEAD = 16
        private const val SUMMARY_SECTION_CHARS = 240
        private const val MALFORMED_MESSAGE = "The model's answer could not be read. Try again."
        private const val GENERIC_FAILURE = "The study kit could not be made."
        private const val NO_AI_MESSAGE = "No AI is set up on this device for study kits."

        private const val READING_SCHEMA = """{
  "concepts": [{"name": "", "definition": "", "emphasis": "", "relationships": [""], "startMs": 0, "endMs": 0, "quote": ""}],
  "sections": [{"title": "", "startMs": 0, "body": "", "terms": [{"text": "", "conceptName": ""}], "quickCheck": {"prompt": "", "answer": "", "conceptName": ""}}],
  "stressed": [{"startMs": 0, "endMs": 0, "quote": "", "reason": "REPEATED", "conceptName": ""}]
}"""

        private const val ACTIVITY_SCHEMA = """{
  "activities": [{"type": "MULTIPLE_CHOICE", "conceptName": "", "prompt": "", "options": ["", "", "", ""], "correctIndex": 0, "rightWhy": "", "wrongWhys": {"1": ""}, "startMs": 0, "endMs": 0, "quote": ""}]
}"""

        // --------------------------------------------------------- pure rules, exposed for tests

        /** Hard-to-hear ranges from ASR confidence: low-confidence segments, merged when they run together. */
        fun hardToHearRanges(segments: List<TranscriptSegment>): List<TimeRange> {
            val low = segments
                .filter { s -> (s.confidence ?: 1f) < HARD_TO_HEAR_CONFIDENCE }
                .sortedBy { it.startMs }
            val merged = mutableListOf<TimeRange>()
            for (s in low) {
                val last = merged.lastOrNull()
                if (last != null && s.startMs <= last.endMs + HARD_TO_HEAR_MERGE_GAP_MS) {
                    merged[merged.lastIndex] = TimeRange(last.startMs, maxOf(last.endMs, s.endMs))
                } else {
                    merged += TimeRange(s.startMs, s.endMs)
                }
            }
            return merged
        }

        private const val HARD_TO_HEAR_MERGE_GAP_MS = 1_000L

        /**
         * Whether [quote] matches the transcript near [startMs]..[endMs]: at least [QUOTE_OVERLAP] of
         * its words appear in the lines (start ms, end ms, text) within [QUOTE_WINDOW_MS] of that span.
         */
        fun quoteMatches(quote: String, startMs: Long, endMs: Long, lines: List<Triple<Long, Long, String>>): Boolean {
            val quoteTokens = tokens(quote)
            if (quoteTokens.isEmpty()) return false
            val nearby = lines.filter { (s, e, _) ->
                e >= startMs - QUOTE_WINDOW_MS && s <= endMs + QUOTE_WINDOW_MS
            }
            if (nearby.isEmpty()) return false
            val transcript = nearby.flatMap { tokens(it.third) }.toSet()
            val overlap = quoteTokens.count { it in transcript }.toDouble() / quoteTokens.size
            return overlap >= QUOTE_OVERLAP
        }

        /** Whether the right answer's words appear in the question. */
        fun answerLeaks(prompt: String, answer: String): Boolean {
            val a = answer.trim().lowercase()
            if (a.isEmpty()) return true
            val p = prompt.lowercase()
            if (p.contains(a) && a.length >= 3) return true
            return a.length < 3 && tokens(prompt).contains(a)
        }

        private fun tokens(text: String): List<String> =
            Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).map { it.value }.toList()

        /** Jaccard similarity of the two texts' word sets. */
        fun tokenJaccard(a: String, b: String): Double {
            val x = tokens(a).toSet()
            val y = tokens(b).toSet()
            if (x.isEmpty() && y.isEmpty()) return 1.0
            val union = (x + y).size.toDouble()
            return x.intersect(y).size / union
        }

        private fun parseReason(raw: String?): StressReason? {
            val value = raw?.trim() ?: return null
            return StressReason.values().firstOrNull { it.name.equals(value, true) || it.label.equals(value, true) }
        }

        private fun JSONObject.text(key: String): String? = optString(key, "").trim().takeIf { it.isNotEmpty() }

        private fun objects(arr: JSONArray?): List<JSONObject> {
            if (arr == null) return emptyList()
            return List(arr.length()) { i -> arr.optJSONObject(i) }.filterNotNull()
        }

        private fun strings(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return List(arr.length()) { i -> arr.optString(i).trim() }.filter { it.isNotEmpty() }
        }

        internal fun extractJsonObject(raw: String): JSONObject? = runCatching {
            val trimmed = raw.trim()
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start != -1 && end > start) JSONObject(trimmed.substring(start, end + 1)) else null
        }.getOrNull()
    }
}
