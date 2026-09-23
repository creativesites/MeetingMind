package com.example.ai.notes

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.llm.LanguageModel
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A piece of the person's own material that an AI tool may read and must cite: one block of a
 * note, or one paragraph of a recording's transcript.
 *
 * The transcript tools' `TranscriptSegment` generalised (docs/PLAN_V1.md, M9): the same contract —
 * cite what you used, invent nothing — over notes as well as recordings.
 */
data class SourcePassage(
    /** Where it lives: a block id, or a transcript segment id. */
    val id: String,
    val text: String,
    /** Where it came from, shown next to a citation: "Sunday service · Key points". */
    val label: String,
    /** The note it belongs to, so a citation can open it. */
    val noteId: String? = null
)

/** What the person asked a tool to do. */
enum class NoteAiTool(val label: String) {
    SUMMARIZE("Summarize"),
    ORGANIZE("Organize into sections"),
    EXTRACT_ACTIONS("Find action items"),
    ASK("Ask")
}

/** One thing a tool produced, with the passages it came from. */
data class CitedItem(
    val text: String,
    val sourceIds: List<String>,
    /** A secondary line the material itself gave — an owner, a due date. Never a guess. */
    val detail: String? = null
)

/** A template section filled from the person's own words. */
data class SectionDraft(val key: String, val title: String, val items: List<CitedItem>)

sealed interface NoteAiOutcome {
    data class Points(val items: List<CitedItem>) : NoteAiOutcome
    data class Sections(val sections: List<SectionDraft>, val leftOver: List<String>) : NoteAiOutcome
    data class Answer(val text: String, val sourceIds: List<String>, val found: Boolean) : NoteAiOutcome
}

/** A section the model may fill when organising (from the note's template). */
data class SectionSpec(val key: String, val title: String, val hint: String?)

/**
 * The note AI tools: summarize, organize into the note's template, find action items, and answer
 * questions — over one note or a whole notebook.
 *
 * Same discipline as the transcript tools:
 * - one fidelity contract on every prompt, plus the Faith clause whenever Faith material is read;
 * - passages go in under short ids (`p1`, `p2`…) and every item must cite them;
 * - output is validated, not trusted: an item whose citations don't resolve is dropped, a number
 *   that isn't in the cited passages drops the item, and organised text must be the person's own
 *   words (most of its words must appear in what it cites).
 *
 * There is deliberately no "continue writing": it would put words in the person's mouth.
 */
class NoteAiEngine(
    private val languageModel: LanguageModel,
    private val maxOutputTokens: Int = 1536
) {

    suspend fun run(
        tool: NoteAiTool,
        passages: List<SourcePassage>,
        faith: Boolean,
        question: String? = null,
        sections: List<SectionSpec> = emptyList()
    ): AiResult<NoteAiOutcome> {
        val usable = passages.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return AiResult.Failed("There's nothing written here yet for ${tool.label.lowercase()} to work on.")
        if (tool == NoteAiTool.ASK && question.isNullOrBlank()) return AiResult.Failed("Type a question first.")
        if (tool == NoteAiTool.ORGANIZE && sections.isEmpty()) return AiResult.Failed("This note has no sections to organise into.")

        val aliases = usable.mapIndexed { i, p -> "p${i + 1}" to p }.toMap()
        val prompt = NoteAiPrompts.build(tool, aliases, faith, question, sections)
        val response = when (val r = languageModel.generate(prompt, maxOutputTokens)) {
            is AiResult.Success -> r.value
            else -> return AiResult.Failed(r.describeFailure() ?: "\"${tool.label}\" could not run.")
        }
        val json = extractJsonObject(response) ?: return AiResult.Failed("\"${tool.label}\" returned something that couldn't be read.")
        return AiResult.Success(parse(tool, json, aliases, sections))
    }

    internal fun parse(tool: NoteAiTool, json: JSONObject, aliases: Map<String, SourcePassage>, sections: List<SectionSpec>): NoteAiOutcome = when (tool) {
        NoteAiTool.SUMMARIZE -> NoteAiOutcome.Points(items(json.optJSONArray("points"), aliases, verbatim = false))
        NoteAiTool.EXTRACT_ACTIONS -> NoteAiOutcome.Points(items(json.optJSONArray("actions"), aliases, verbatim = true))
        NoteAiTool.ASK -> {
            val found = json.optBoolean("found", true)
            val sources = ids(json.optJSONArray("sources"), aliases)
            val text = json.optString("answer").trim()
            // An answer that cites nothing, or cites nothing real, is not an answer from the notes.
            if (!found || text.isEmpty() || sources.isEmpty() || !numbersSupported(text, sources.map { aliases.getValue(it).text }))
                NoteAiOutcome.Answer("", emptyList(), found = false)
            else NoteAiOutcome.Answer(text, sources.map { aliases.getValue(it).id }, found = true)
        }
        NoteAiTool.ORGANIZE -> {
            val byKey = sections.associateBy { it.key }
            val array = json.optJSONArray("sections")
            val drafts = buildList {
                for (i in 0 until (array?.length() ?: 0)) {
                    val o = array!!.optJSONObject(i) ?: continue
                    val spec = byKey[o.optString("key")] ?: continue
                    val list = items(o.optJSONArray("items"), aliases, verbatim = true)
                    if (list.isNotEmpty()) add(SectionDraft(spec.key, spec.title, list))
                }
            }.sortedBy { d -> sections.indexOfFirst { it.key == d.key } }
            // Anything the organised version doesn't use is kept, never lost.
            val used = drafts.flatMap { d -> d.items.flatMap { it.sourceIds } }.toSet()
            NoteAiOutcome.Sections(drafts, aliases.values.map { it.id }.filterNot { it in used })
        }
    }

    private fun items(array: JSONArray?, aliases: Map<String, SourcePassage>, verbatim: Boolean): List<CitedItem> = buildList {
        for (i in 0 until (array?.length() ?: 0)) {
            val o = array!!.optJSONObject(i) ?: continue
            val text = o.optString("text").trim()
            if (text.isEmpty()) continue
            val cited = ids(o.optJSONArray("sources"), aliases)
            if (cited.isEmpty()) continue
            val sourceTexts = cited.map { aliases.getValue(it).text }
            if (!numbersSupported(text, sourceTexts)) continue
            if (verbatim && !ownWords(text, sourceTexts)) continue
            val detail = o.optString("detail").trim().takeIf { it.isNotEmpty() && it != "null" }
                ?.takeIf { d -> sourceTexts.any { it.contains(d, ignoreCase = true) } }
            add(CitedItem(text, cited.map { aliases.getValue(it).id }, detail))
        }
    }.distinctBy { it.text.lowercase() }

    private fun ids(array: JSONArray?, aliases: Map<String, SourcePassage>): List<String> = buildList {
        for (j in 0 until (array?.length() ?: 0)) {
            val id = array!!.optString(j).trim()
            if (id in aliases && id !in this) add(id)
        }
    }

    companion object {
        private val NUMBER = Regex("\\d+(?:[.,:]\\d+)*")
        private val WORD = Regex("[\\p{L}\\p{N}']+")
        private val STOP = setOf(
            "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "with", "is", "are", "be", "that", "this",
            "it", "at", "by", "as", "from", "i", "we", "you", "my", "our", "your", "will", "should", "need", "needs"
        )

        /** Every number in [text] appears in the passages it cites. */
        fun numbersSupported(text: String, sources: List<String>): Boolean {
            val joined = sources.joinToString(" ")
            return NUMBER.findAll(text).all { joined.contains(it.value) }
        }

        /** Most of [text]'s content words come from its sources — it is the person's words, rearranged. */
        fun ownWords(text: String, sources: List<String>, threshold: Double = 0.7): Boolean {
            val words = WORD.findAll(text.lowercase()).map { it.value }.filter { it !in STOP && it.length > 1 }.toList()
            if (words.isEmpty()) return true
            val available = WORD.findAll(sources.joinToString(" ").lowercase()).map { it.value }.toSet()
            val stems = available.map { it.take(5) }.toSet()
            val hits = words.count { it in available || it.take(5) in stems }
            return hits.toDouble() / words.size >= threshold
        }

        fun extractJsonObject(raw: String): JSONObject? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try { JSONObject(raw.substring(start, end + 1)) } catch (e: JSONException) { null }
        }
    }
}

/** The prompts. Every one carries [NOTE_CONTRACT]; Faith material adds [FAITH_NOTE_CONTRACT]. */
object NoteAiPrompts {

    const val NOTE_CONTRACT = """
You are helping a person work with their own notes. The passages below are theirs.

You MUST NOT:
- add any fact, name, number, date, decision or commitment that is not in the passages
- answer from general knowledge; only these passages count
- give advice, opinions or conclusions the passages do not contain
- continue or extend what the person wrote

You MUST:
- cite the id of every passage that supports what you write (ids look like p1, p2)
- leave a list empty when the passages don't support any entry — an empty result is correct
- keep the person's own meaning and, where asked, their own words
"""

    const val FAITH_NOTE_CONTRACT = """
These notes are about the person's faith: sermons, prayers, Bible study, reflections.

You MUST NOT:
- interpret, explain or apply scripture in your own voice
- add theology, doctrine or spiritual advice that is not in the notes
- speak as a spiritual authority, or say what God wants
- quote a Bible verse that the notes don't contain

You MUST:
- report what the person (or the preacher they noted) said as theirs
"""

    fun build(
        tool: NoteAiTool,
        passages: Map<String, SourcePassage>,
        faith: Boolean,
        question: String?,
        sections: List<SectionSpec>
    ): String = buildString {
        append(NOTE_CONTRACT.trim()).append("\n\n")
        if (faith) append(FAITH_NOTE_CONTRACT.trim()).append("\n\n")
        append("TASK\n").append(instruction(tool, question, sections)).append("\n\n")
        append("PASSAGES\n")
        for ((alias, p) in passages) {
            append("[").append(alias).append("] (").append(p.label.replace('\n', ' ').take(80)).append(") ")
            append(p.text.replace('\n', ' ').trim()).append('\n')
        }
        append("\nRespond with one JSON object and nothing else, in this shape:\n").append(schema(tool))
    }

    private fun instruction(tool: NoteAiTool, question: String?, sections: List<SectionSpec>): String = when (tool) {
        NoteAiTool.SUMMARIZE ->
            "Summarize these notes as a short list of the main points, most important first. One sentence each. " +
                "At most 8 points. Use the person's own terms."
        NoteAiTool.EXTRACT_ACTIONS ->
            "List every task, follow-up or commitment the notes actually state — something someone is to do. " +
                "Word each as a short action using the notes' own words. Put who and when in detail only if the notes say. " +
                "Do not turn ideas, hopes or questions into tasks."
        NoteAiTool.ASK ->
            "Answer this question using only the passages: \"${question.orEmpty().trim()}\". " +
                "Answer in two or three sentences. If the passages don't answer it, set found to false and leave answer empty."
        NoteAiTool.ORGANIZE ->
            "Sort what the person wrote into these sections. Move their sentences; do not rewrite them beyond small trims. " +
                "Leave a section out when nothing belongs in it. Sections:\n" +
                sections.joinToString("\n") { "- ${it.key}: ${it.title}" + (it.hint?.let { h -> " ($h)" } ?: "") }
    }

    private fun schema(tool: NoteAiTool): String = when (tool) {
        NoteAiTool.SUMMARIZE -> """{"points":[{"text":"…","sources":["p1"]}]}"""
        NoteAiTool.EXTRACT_ACTIONS -> """{"actions":[{"text":"…","detail":"who or when, or empty","sources":["p2"]}]}"""
        NoteAiTool.ASK -> """{"found":true,"answer":"…","sources":["p1","p3"]}"""
        NoteAiTool.ORGANIZE -> """{"sections":[{"key":"section_key","items":[{"text":"…","sources":["p1"]}]}]}"""
    }
}
