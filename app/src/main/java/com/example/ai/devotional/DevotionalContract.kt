package com.example.ai.devotional

import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.LiturgicalDay
import com.example.core.devotional.Tradition
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureReferenceParser
import org.json.JSONObject

/** What the model is asked to write about, gathered on the phone. */
data class DevotionalBrief(
    val passage: ScriptureReference,
    /** The passage's text, when available, so the model reflects on the real words. */
    val passageText: String?,
    val profile: DevotionalProfile,
    val season: LiturgicalDay?,
    val weekday: String,
    /** Short lines about the person's recent life, already filtered for what may be sent. */
    val signals: List<String>,
    val name: String?,
    /** What the person asked for in their own words, when they asked for one on demand. */
    val request: String? = null,
    /** The hour it's being read (0–23); null for the scheduled morning devotional. */
    val hour: Int? = null
)

/** The model's answer, before checking. */
data class DevotionalAnswer(
    val title: String,
    val extraReferences: List<String>,
    val reflection: List<String>,
    val application: List<String>,
    val prayer: String?,
    val motivation: String?,
    val question: String?
)

/**
 * The rules an AI-written devotional is held to (PLAN_V2 F2): what the prompt asks for, and the
 * checks that run on every answer whatever the prompt said.
 */
object DevotionalContract {

    /** Sent with every request. Tested so it can't quietly lose a clause. */
    val CONTRACT = """
        You are writing a short Christian devotional for one person. It will be shown to them labelled "AI-written devotional".
        Rules you must follow:
        1. Write about the passage given. Do not quote Bible verses word for word — the app shows the real verse text itself. Refer to verses by reference (e.g. "John 15:5") and paraphrase briefly.
        2. Never claim to speak for God. Do not write "God is telling you", "the Lord says to you", "I prophesy", "thus says the Lord", or promise specific outcomes (healing, money, a job, a spouse, a date).
        3. Give no medical, legal, financial or crisis advice. If life is hard, be gentle and encourage talking to a trusted person or pastor.
        4. Stay within historic, mainstream Christian teaching, in the tradition named. Where Christians differ, do not take sides.
        5. Be warm, honest and specific. No clichés, no guilt, no hype. Address the reader as "you".
        6. Output only JSON, no other text.
    """.trimIndent()

    fun prompt(brief: DevotionalBrief): String {
        val p = brief.profile
        val sections = buildList {
            add("\"title\": a short title (max 8 words)")
            add("\"references\": up to 2 other Bible references that support the reflection (references only, e.g. \"Romans 8:28\")")
            add("\"reflection\": an array of paragraphs, about ${p.words} words in total")
            add("\"application\": an array of 1–3 small, concrete things to do today (each under 15 words)")
            if (p.includePrayer) add("\"prayer\": a short prayer in the first person (\"Lord, …\"), 60–120 words, ending with Amen")
            if (p.includeMotivation) add("\"motivation\": one or two encouraging sentences to carry into the day")
            if (p.includeQuestion) add("\"question\": one reflective question to sit with")
        }
        return buildString {
            appendLine(CONTRACT)
            appendLine()
            appendLine("Tradition: ${p.tradition.label}${if (p.tradition == Tradition.CATHOLIC || p.tradition == Tradition.ORTHODOX || p.tradition == Tradition.ANGLICAN) " (use its usual vocabulary respectfully)" else ""}.")
            appendLine("Voice: ${p.tone.guidance}.")
            appendLine("Day: ${brief.weekday}${brief.season?.let { ", ${it.describe()}" } ?: ""}.")
            appendLine(timeLine(brief.hour))
            appendLine("Passage: ${brief.passage.display()}")
            brief.passageText?.let { appendLine("Passage text (for your understanding; do not copy it out): ${it.take(1500)}") }
            if (p.topics.isNotEmpty()) appendLine("They'd like to grow in: ${p.topics.joinToString(", ")}.")
            if (p.moreOf.isNotEmpty()) appendLine("They've enjoyed: ${p.moreOf.joinToString(", ")}.")
            if (p.lessOf.isNotEmpty()) appendLine("Go lighter on: ${p.lessOf.joinToString(", ")}.")
            p.season?.let { appendLine("Life season: $it.") }
            p.aboutMe.trim().takeIf { it.isNotEmpty() }?.let { appendLine("About them, in their words: ${it.take(400)}") }
            brief.name?.takeIf { it.isNotBlank() }?.let { appendLine("Their first name: $it (use it at most once).") }
            brief.request?.trim()?.takeIf { it.isNotEmpty() }?.let {
                appendLine("They asked for a devotional about this, in their words — let it shape everything, gently: ${it.take(600)}")
            }
            if (brief.signals.isNotEmpty()) {
                appendLine("What's been happening (use gently, never quote it back verbatim):")
                brief.signals.take(8).forEach { appendLine("- ${it.take(160)}") }
            }
            appendLine()
            appendLine("Return a JSON object with:")
            sections.forEach { appendLine("- $it") }
        }
    }

    /**
     * When it will be read. People write devotionals whenever they like through the day, so it
     * never assumes morning: it names the actual part of the day, and otherwise stays time-neutral.
     */
    fun timeLine(hour: Int?): String = when (hour) {
        null -> "Time: it's delivered in the morning, but may be read at any hour — avoid time-specific greetings like \"good morning\" or \"as you start your day\"."
        in 4..11 -> "Time: it's being read in the morning. Speak to the day ahead."
        in 12..16 -> "Time: it's being read in the afternoon, mid-day. Don't say \"good morning\" or talk about starting the day; meet them in the middle of it."
        in 17..21 -> "Time: it's being read in the evening. Don't say \"good morning\"; help them look back on the day and rest."
        else -> "Time: it's being read late at night. Don't say \"good morning\"; be quiet and restful, pointing to peace and sleep."
    }

    fun parse(raw: String): DevotionalAnswer? {
        val json = com.example.ai.notes.NoteAiEngine.extractJsonObject(raw) ?: return null
        fun strings(key: String): List<String> {
            json.optJSONArray(key)?.let { a -> return (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() } } }
            return json.optString(key).trim().takeIf { it.isNotEmpty() }?.split(Regex("\n\\s*\n"))?.map { it.trim() }.orEmpty()
        }
        fun text(key: String) = json.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
        val reflection = strings("reflection")
        if (reflection.isEmpty()) return null
        return DevotionalAnswer(
            title = text("title")?.take(80) ?: "",
            extraReferences = strings("references").take(3),
            reflection = reflection,
            application = strings("application").take(3),
            prayer = text("prayer"),
            motivation = text("motivation"),
            question = text("question")
        )
    }

    // ---------------------------------------------------------------- the guard

    /** Claims to speak for God, prophecy, and promised outcomes. A sentence with one is removed. */
    private val FORBIDDEN = listOf(
        Regex("""\bgod (is|was) (telling|saying to|speaking to|showing) you\b"""),
        Regex("""\b(god|the lord|jesus|the spirit|the holy spirit) (says|said|is saying|wants to say|told me|tells you) (to you|that you|this)\b"""),
        Regex("""\b(thus|so) says the lord\b"""),
        Regex("""\bi (prophesy|declare|decree)\b"""),
        Regex("""\b(this|here) is (a|your|the) (word|prophecy|message) from (god|the lord)\b"""),
        Regex("""\bgod (will|is going to) (heal|cure|give you|bless you with|provide you with|bring you) (a |an |the |your )?(job|husband|wife|spouse|money|promotion|baby|child|house|healing|cure|breakthrough)"""),
        Regex("""\byou will (receive|get|find) (a |an |the |your )?(new )?(job|husband|wife|spouse|money|promotion|baby|healing|breakthrough|miracle)"""),
        Regex("""\byou (will|shall) be (healed|cured|made well|delivered from (this|your) (illness|disease|sickness))\b"""),
        Regex("""\byour (breakthrough|miracle|healing) is (coming|here|on its way)\b"""),
        Regex("""\b(stop|quit) (taking|your) (medication|medicine|treatment|pills)\b"""),
        Regex("""\b(invest|buy|sell) (in )?(stocks|crypto|shares|bitcoin)\b""")
    )

    /** The sentences of [text] that carry a forbidden claim, removed. */
    fun removeForbidden(text: String): Pair<String, Int> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val kept = sentences.filter { s -> val l = s.lowercase(); FORBIDDEN.none { it.containsMatchIn(l) } }
        return kept.joinToString(" ").trim() to (sentences.size - kept.size)
    }

    /**
     * A long quotation followed by a reference — "“For God so loved…” (John 3:16)" — is checked
     * against the real text. If it doesn't match (or can't be checked), the quotation is replaced
     * by the real verse text, or, when that isn't available, dropped so only the reference stays.
     */
    suspend fun fixQuotedScripture(text: String, verseText: suspend (ScriptureReference) -> String?): String {
        val pattern = Regex("""[“"]([^”"]{25,})[”"]\s*[(\[]?((?:[1-3]\s?)?[A-Z][a-z]+\.?\s+\d+(?::\d+(?:\s*[-–]\s*\d+)?)?)[)\]]?""")
        var out = text
        for (m in pattern.findAll(text).toList().reversed()) {
            val quoted = m.groupValues[1]
            val ref = ScriptureReferenceParser.parse(m.groupValues[2]) ?: continue
            val real = verseText(ref)?.trim()
            val replacement = when {
                real != null && similar(quoted, real) -> m.value
                real != null && real.length <= 400 -> "“$real” (${ref.display()})"
                else -> "(see ${ref.display()})"
            }
            out = out.replaceRange(m.range, replacement)
        }
        return out
    }

    private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}']+")).filter { it.length > 2 }.toSet()

    /** Most of the quoted words appear in the real verse. */
    fun similar(quoted: String, real: String): Boolean {
        val q = words(quoted)
        if (q.isEmpty()) return false
        return q.count { it in words(real) }.toDouble() / q.size >= 0.8
    }

    // ---------------------------------------------------------------- care

    private val CRISIS = listOf(
        "kill myself", "killing myself", "suicide", "suicidal", "end my life", "ending my life", "want to die",
        "wanna die", "better off dead", "self harm", "self-harm", "harm myself", "hurt myself", "cut myself", "cutting myself",
        "no reason to live", "can't go on living", "being abused", "he hits me", "she hits me", "abusing me", "afraid for my life"
    )

    /** Whether the person's own recent words suggest they may be in danger. Checked on the phone only. */
    fun crisisIn(texts: Collection<String>): Boolean = texts.any { t -> val l = t.lowercase(); CRISIS.any { it in l } }

    val CARE_TEXT = listOf(
        "Some of what you've written lately sounds really heavy. You matter, and you don't have to carry this alone.",
        "If you might act on thoughts of harming yourself, or you're not safe, please call your local emergency number now.",
        "You can talk to someone today: in the US call or text 988; in the UK and Ireland call Samaritans on 116 123; elsewhere, findahelpline.com lists free, confidential lines near you.",
        "Consider telling someone you trust — a friend, a pastor, a counsellor — how things really are."
    )
}
