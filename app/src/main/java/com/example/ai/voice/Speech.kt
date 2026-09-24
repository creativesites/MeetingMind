package com.example.ai.voice

import com.example.core.devotional.Devotional
import com.example.core.devotional.DevotionalOrigin
import com.example.core.scripture.ScriptureReference

/** Who reads the devotional: a preset that maps to a Gemini voice and a delivery style. */
enum class PreacherStyle(val label: String, val line: String, val style: String, val male: String, val female: String) {
    WARM_PASTOR("Warm pastor", "Unhurried, kind, like Sunday morning",
        "a warm, unhurried pastor speaking to one person he cares about; gentle, sincere, with natural pauses", "Algieba", "Sulafat"),
    GENTLE_FRIEND("Gentle friend", "Soft and close, like a friend across the table",
        "a gentle friend speaking softly and personally, relaxed and reassuring", "Achird", "Vindemiatrix"),
    BOLD_PREACHER("Bold preacher", "Stirring and confident",
        "a confident, stirring preacher with conviction and energy, building to encouragement, never shouting", "Alnilam", "Kore"),
    CALM_TEACHER("Calm teacher", "Clear and steady",
        "a calm, clear Bible teacher, steady and thoughtful", "Iapetus", "Erinome"),
    STORYTELLER("Storyteller", "Vivid, with a storyteller's rhythm",
        "a storyteller reading aloud by the fire, vivid and expressive with a gentle rhythm", "Algenib", "Gacrux");
}

enum class VoiceGender(val label: String) { MALE("Male voice"), FEMALE("Female voice") }

/** How the devotional is read aloud. */
data class VoiceSettings(
    val style: PreacherStyle = PreacherStyle.WARM_PASTOR,
    val gender: VoiceGender = VoiceGender.MALE,
    /** Read the prayer aloud, with a pause before the Amen. */
    val speakPrayer: Boolean = true,
    /** Record the voice version every morning, ready with the text. */
    val autoVoice: Boolean = false,
    /** 0.8–1.2; the phone's voice honours it exactly, Gemini as a delivery hint. */
    val rate: Float = 1.0f
) {
    val voiceName get() = if (gender == VoiceGender.MALE) style.male else style.female
    val deliveryStyle get() = style.style + when {
        rate < 0.95f -> "; a slower pace than usual"
        rate > 1.05f -> "; a slightly brisker pace"
        else -> ""
    }
}

/** One stretch of speech and the pause after it. */
data class SpeechSegment(val kind: Kind, val text: String, val pauseAfterMs: Long) {
    enum class Kind { INTRO, SCRIPTURE, REFLECTION, APPLICATION, PRAYER, MOTIVATION, QUOTE, QUESTION, OUTRO }
}

/**
 * The devotional as something to listen to: an opening, the Scripture read in full (real verse
 * text, never the model's), the reflection, the prayer and — last — the word for today.
 */
object SpeechScript {

    fun build(d: Devotional, verseText: Map<ScriptureReference, String>, settings: VoiceSettings, name: String? = null): List<SpeechSegment> = buildList {
        val greeting = name?.takeIf { it.isNotBlank() }?.let { "Good morning, $it." } ?: "Good morning."
        val opening = if (d.origin == DevotionalOrigin.CLASSIC) "$greeting Today's reading is from ${d.engine ?: "a classic devotional"}." else "$greeting This is your devotional for today: ${d.title}."
        add(SpeechSegment(SpeechSegment.Kind.INTRO, opening, 700))
        d.keyText?.let { add(SpeechSegment(SpeechSegment.Kind.SCRIPTURE, it.trim('“', '”', '"'), 600)) }
        d.scripture.forEach { ref ->
            val text = verseText[ref]
            if (text != null) add(SpeechSegment(SpeechSegment.Kind.SCRIPTURE, "${spoken(ref)}. ${clean(text)}", 900))
            else add(SpeechSegment(SpeechSegment.Kind.SCRIPTURE, "Our reading is ${spoken(ref)}.", 700))
        }
        d.reflection.forEach { add(SpeechSegment(SpeechSegment.Kind.REFLECTION, it, 500)) }
        if (d.application.isNotEmpty()) {
            add(SpeechSegment(SpeechSegment.Kind.APPLICATION, "Today, you might: " + d.application.joinToString("; ") { it.trimEnd('.') } + ".", 700))
        }
        d.question?.let { add(SpeechSegment(SpeechSegment.Kind.QUESTION, "A question to sit with. $it", 1500)) }
        if (settings.speakPrayer) d.prayer?.let { p ->
            val body = p.trim().removeSuffix("Amen.").removeSuffix("Amen").trim().trimEnd(',')
            add(SpeechSegment(SpeechSegment.Kind.PRAYER, "Let's pray.", 1200))
            add(SpeechSegment(SpeechSegment.Kind.PRAYER, body, 900))
            add(SpeechSegment(SpeechSegment.Kind.PRAYER, "Amen.", 1400))
        }
        d.insight?.let { add(SpeechSegment(SpeechSegment.Kind.QUOTE, "${it.text} ${it.author} said that.".let { s -> if (it.author.isBlank()) it.text else s }, 800)) }
        d.motivation?.let { add(SpeechSegment(SpeechSegment.Kind.MOTIVATION, it, 600)) }
        add(SpeechSegment(SpeechSegment.Kind.OUTRO, "Go in peace.", 0))
    }

    /** "John 3:16–18" read as "John chapter 3, verses 16 to 18". */
    fun spoken(ref: ScriptureReference): String {
        val name = if (ref.book.usfm == "PSA") "Psalm" else ref.book.name
        return when {
            ref.verseStart == null -> "$name chapter ${ref.chapter}"
            ref.verseEnd != null && ref.verseEnd != ref.verseStart -> "$name chapter ${ref.chapter}, verses ${ref.verseStart} to ${ref.verseEnd}"
            else -> "$name chapter ${ref.chapter}, verse ${ref.verseStart}"
        }
    }

    /** Verse numbers and markup that shouldn't be read out. */
    fun clean(text: String): String = text
        .replace(Regex("""\[\d+]|\(\d+\)|(?<=\s|^)\d{1,3}(?=\s?[A-Z“"‘])"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * Groups segments into requests of at most [maxChars], each text with Gemini's inline pause
     * tags between segments. Never splits a segment unless it alone is too long (then by sentence).
     */
    fun chunks(segments: List<SpeechSegment>, maxChars: Int = 2400): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() { if (current.isNotBlank()) out += current.toString().trim(); current.clear() }
        for (s in segments) {
            val pieces = if (s.text.length <= maxChars) listOf(s.text) else s.text.split(Regex("(?<=[.!?])\\s+")).fold(mutableListOf<String>()) { acc, sentence ->
                if (acc.isNotEmpty() && acc.last().length + sentence.length + 1 <= maxChars) acc[acc.size - 1] = acc.last() + " " + sentence else acc += sentence
                acc
            }
            pieces.forEachIndexed { i, piece ->
                val tag = if (i < pieces.size - 1) " " else pauseTag(s.pauseAfterMs)
                if (current.length + piece.length + tag.length > maxChars) flush()
                current.append(piece).append(tag)
            }
        }
        flush()
        return out
    }

    fun pauseTag(ms: Long) = when {
        ms <= 0 -> " "
        ms < 1000 -> " <short pause> "
        else -> " <long pause> "
    }
}
