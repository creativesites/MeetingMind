package com.craftflowtechnologies.meetingmind.ai.devotional

import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalDay
import com.craftflowtechnologies.meetingmind.core.devotional.Tradition
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
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
    val hour: Int? = null,
    /** Recent devotionals' titles and first lines, newest first, so today's is not a rerun. */
    val recent: List<String> = emptyList(),
    /** Picks today's angle and opening; the day number plus "another one" steps. */
    val dayIndex: Long = 0,
    val format: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat? = null,
    /** "7 Days in Philippians, day 3 of 7" and a line for each earlier day. */
    val series: String? = null,
    val seriesSoFar: List<String> = emptyList(),
    /** For the evening Examen: this morning's devotional (title, passage, what it asked). */
    val morning: String? = null,
    /** Set on a second attempt: what the first repeated. */
    val retryNote: String? = null,
    /** The day it is written for; decides whether a "Now and then" Personal touch applies. */
    val date: java.time.LocalDate? = null,
    /** Topics typed for this one devotional ("Ask"); they apply to it whatever the Personal touch is. */
    val askTopics: Set<String> = emptySet()
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

    /**
     * Sent with every request: the shared Faith theology contract and the devotional prompt, both
     * versioned files in assets/prompts. Tested so neither can quietly lose a clause.
     */
    val CONTRACT: String get() = com.craftflowtechnologies.meetingmind.ai.faith.Prompts.faithContract + "\n\n" + com.craftflowtechnologies.meetingmind.ai.faith.Prompts.get("devotional").system()

    /** Ways into a passage, one per day, so the devotional's shape changes as well as its words. */
    val ANGLES = listOf(
        "Tell the story around the passage — who was there, what was at stake — and let the reader find themselves in it.",
        "Take one word or phrase from the passage and turn it over slowly: what it meant then, what it opens up now.",
        "Build it around one concrete, everyday image from ordinary life (not the reader's own details) that lights up the passage.",
        "Write it as a short guided practice: something to notice, try or do with the passage today, step by step.",
        "Open up the historical or cultural background that makes the passage surprising, then bring it home.",
        "Focus on delight and wonder — what in this passage is simply good news worth enjoying?",
        "Write it as a conversation with an honest question someone might ask about this passage, and answer it gently.",
        "Connect the passage to one character elsewhere in Scripture who lived the same truth, and learn from them.",
        "Keep it spare and poetic: short paragraphs, room to breathe, one idea held up to the light.",
        "Make it practical and energising: what this passage means for work, relationships and choices this week.",
        "Centre it on gratitude — trace what the passage invites the reader to thank God for, specifically.",
        "Let it be playful and warm — a light touch, a smile, still true and still reverent."
    )

    /** How the first sentence begins, stepped separately from the angle so the pairs keep changing. */
    val OPENINGS = listOf(
        "Begin with a short, striking sentence about the passage itself.",
        "Begin with a vivid detail from the passage's setting.",
        "Begin with a question that the passage answers.",
        "Begin with a small scene from ordinary life that anyone would recognise.",
        "Begin with a surprising fact or observation about the text.",
        "Begin mid-thought, as if continuing a conversation with a friend.",
        "Begin with one word from the passage, on its own."
    )

    fun angleFor(dayIndex: Long): String = ANGLES[Math.floorMod(dayIndex, ANGLES.size.toLong()).toInt()]
    fun openingFor(dayIndex: Long): String = OPENINGS[Math.floorMod(dayIndex * 3 + 1, OPENINGS.size.toLong()).toInt()]

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
        // Personal context (about me, topics, life season, name, sermons, prayer list) only on invitation.
        val personal = p.personalTouch.appliesOn(brief.date)
        return buildString {
            appendLine(CONTRACT)
            appendLine()
            appendLine("Tradition: ${p.tradition.label}${if (p.tradition == Tradition.CATHOLIC || p.tradition == Tradition.ORTHODOX || p.tradition == Tradition.ANGLICAN) " (use its usual vocabulary respectfully)" else ""}.")
            appendLine("Voice: ${p.tone.guidance}.")
            appendLine("Written for ${p.audience.guidance}, in ${p.readingLevel.guidance}.")
            if (p.language.isNotBlank()) appendLine("Write in ${p.language}.")
            brief.format?.let { f -> appendLine("Format — ${f.label}: ${f.guidance}${f.tradition?.let { t -> " (rooted in the $t tradition; present it as such)" } ?: ""}") }
            appendLine("Day: ${brief.weekday}${brief.season?.let { ", ${it.describe()}" } ?: ""}.")
            appendLine(timeLine(brief.hour))
            appendLine("Passage: ${brief.passage.display()}")
            brief.passageText?.let { appendLine("Passage text (for your understanding; do not copy it out): ${it.take(1500)}") }
            if (brief.askTopics.isNotEmpty()) appendLine("For this devotional they asked to focus on: ${brief.askTopics.joinToString(", ")}.")
            // "Less of" is a style choice, not personal context: always honoured.
            if (p.lessOf.isNotEmpty()) appendLine("Go lighter on these themes (don't build the devotional around them): ${p.lessOf.joinToString(", ")}.")
            if (!personal) appendLine("Audience: a general reader. You know nothing about them; do not guess or mention their job, family, health, mood, location or circumstances. Write something that could be read by anyone, and make it surprising.")
            appendLine("Today's angle: ${angleFor(brief.dayIndex)}")
            appendLine("Opening: ${openingFor(brief.dayIndex)}")
            if (brief.recent.isNotEmpty()) {
                appendLine("Recent devotionals in this app (don't repeat their titles, openings, images or ideas, and choose a different theme and a different part of the Bible):")
                brief.recent.take(14).forEach { appendLine("- ${it.take(220)}") }
            }
            brief.series?.let { sr ->
                appendLine("This is part of a series: $sr. Continue it — build on the earlier days, don't restart or repeat them.")
                brief.seriesSoFar.forEach { appendLine("- earlier: ${it.take(200)}") }
            }
            brief.morning?.let { appendLine("This evening Examen follows this morning's devotional: $it. Invite them to look back on it gently — the passage, what it asked, what the day held — without judging.") }
            brief.retryNote?.let { appendLine("Your previous draft was rejected because $it. Write something clearly different.") }
            brief.request?.trim()?.takeIf { it.isNotEmpty() }?.let {
                appendLine("This one was asked for in the moment, in their words — it applies to this devotional only; let it shape it, gently: ${it.take(600)}")
            }
            if (personal) appendPersonal(brief)
            appendLine()
            appendLine("Return a JSON object with:")
            sections.forEach { appendLine("- $it") }
        }
    }

    /** The shared personal context, framed so it stays background and never becomes the daily subject. */
    private fun StringBuilder.appendPersonal(brief: DevotionalBrief) {
        val p = brief.profile
        val lines = buildList {
            brief.name?.takeIf { it.isNotBlank() }?.let { add("Their first name: $it (use it at most once, or not at all).") }
            if (p.topics.isNotEmpty()) add("They'd like to grow in: ${p.topics.joinToString(", ")}.")
            if (p.moreOf.isNotEmpty()) add("They've enjoyed: ${p.moreOf.joinToString(", ")}.")
            p.season?.let { add("Life season: $it.") }
            p.aboutMe.trim().takeIf { it.isNotEmpty() }?.let { add("About them, in their words: ${it.take(400)}") }
            brief.signals.take(8).forEach { add("Lately: ${it.take(160)} (never quote it back verbatim)") }
        }
        if (lines.isEmpty()) return
        appendLine()
        if (p.personalTouch == com.craftflowtechnologies.meetingmind.core.devotional.PersonalTouch.NOW_AND_THEN) {
            appendLine("Personal background — the reader invited a personal touch about once a week, and today is one of those days. Keep it gentle background: the passage and its message are the subject, never their situation. Mention it in at most one short place, lightly, or just let it quietly colour one example.")
        } else {
            appendLine("Personal background — the reader asked for every devotional to consider this. It may inform AT MOST ONE paragraph, lightly. The passage, theme, angle and imagery must still be fresh and different from recent days; do not make their situation the subject, and do not repeat the same personal point from day to day.")
        }
        lines.forEach { appendLine("- $it") }
        appendLine()
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
        val json = com.craftflowtechnologies.meetingmind.ai.notes.NoteAiEngine.extractJsonObject(raw) ?: return null
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
        Regex("""\bgod (told|has told|showed|has shown) me\b"""),
        Regex("""\bgod (has revealed|reveals|is revealing) (to you )?that you (should|must|will)\b"""),
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
