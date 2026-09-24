package com.example.ai.voice

import com.example.core.devotional.Devotional
import com.example.core.devotional.DevotionalOrigin
import com.example.core.scripture.ScriptureReference

/**
 * Who reads the devotional: a preset that maps to a Gemini voice and a delivery style. The style
 * is `speech_metadata.style`, which also carries accent — so a Lusaka Pentecostal preacher sounds
 * like one. [tradition] groups them in the picker.
 */
enum class PreacherStyle(val label: String, val line: String, val style: String, val male: String, val female: String, val tradition: String = "Any church") {
    WARM_PASTOR("Warm pastor", "Unhurried, kind, like Sunday morning",
        "a warm, unhurried pastor speaking to one person he cares about; gentle, sincere, with natural pauses", "Algieba", "Sulafat"),
    PENTECOSTAL("Pentecostal preacher", "Fiery, joyful, full of faith — Sunday service in Lusaka",
        "a passionate African Pentecostal preacher with a warm Zambian English accent; fervent, joyful and full of faith, rising with conviction and a preacher's rhythm, " +
            "saying 'Amen' and 'Hallelujah' with real feeling, lively but always clear and never shouting",
        "Fenrir", "Kore", "Pentecostal & charismatic"),
    CHURCH_MOTHER("Church mother", "Tender and prayerful, like a praying grandmother",
        "a warm African church mother with a gentle Zambian English accent; tender, faith-filled and unhurried, like a grandmother praying over her child",
        "Charon", "Aoede", "Pentecostal & charismatic"),
    CATHOLIC_PRIEST("Priest or sister", "Reverent and prayerful, like a homily at Mass",
        "a reverent Catholic priest giving a short homily at Mass; prayerful, measured and dignified, warm and pastoral, with quiet pauses for reflection",
        "Charon", "Schedar", "Catholic"),
    BOLD_PREACHER("Bold preacher", "Stirring and confident",
        "a confident, stirring evangelical preacher with conviction and energy, building to encouragement, never shouting", "Alnilam", "Kore", "Evangelical & Baptist"),
    LITURGICAL_READER("Liturgical reader", "Clear and dignified, like a cathedral reading",
        "a dignified reader in Anglican or Reformed worship; clear diction, a measured, reverent pace, letting the words of Scripture carry the weight",
        "Rasalgethi", "Erinome", "Anglican, Methodist & Reformed"),
    GENTLE_FRIEND("Gentle friend", "Soft and close, like a friend across the table",
        "a gentle friend speaking softly and personally, relaxed and reassuring", "Achird", "Vindemiatrix"),
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
    enum class Kind(val section: VoiceSection) {
        INTRO(VoiceSection.SCRIPTURE), SCRIPTURE(VoiceSection.SCRIPTURE), REFLECTION(VoiceSection.REFLECTION),
        APPLICATION(VoiceSection.APPLY), QUESTION(VoiceSection.APPLY), PRAYER(VoiceSection.PRAYER),
        QUOTE(VoiceSection.WORD), MOTIVATION(VoiceSection.WORD), OUTRO(VoiceSection.WORD)
    }
}

/** The parts of a spoken devotional a listener can jump to. */
enum class VoiceSection(val label: String) {
    SCRIPTURE("Scripture"), REFLECTION("Reflection"), APPLY("Today"), PRAYER("Prayer"), WORD("A word");

    companion object {
        /** "SCRIPTURE:0,REFLECTION:31000,…" ⇄ marks, in order. */
        fun encode(marks: List<Pair<VoiceSection, Long>>) = marks.joinToString(",") { "${it.first.name}:${it.second}" }
        fun decode(raw: String?): List<Pair<VoiceSection, Long>> = raw.orEmpty().split(',').mapNotNull { part ->
            val (name, ms) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val section = entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            ms.toLongOrNull()?.let { section to it }
        }

        /** Where [section] starts and ends (the next section's start), or null if it wasn't recorded. */
        fun range(marks: List<Pair<VoiceSection, Long>>, section: VoiceSection): Pair<Long, Long?>? {
            val i = marks.indexOfFirst { it.first == section }.takeIf { it >= 0 } ?: return null
            return marks[i].second to marks.getOrNull(i + 1)?.second
        }
    }
}

/**
 * The devotional as something to listen to: an opening, the Scripture read in full (real verse
 * text, never the model's), the reflection, the prayer and — last — the word for today.
 */
object SpeechScript {

    /** "Good morning" in the morning — and good afternoon, evening, or just "Hello" late at night. */
    fun greetingFor(hour: Int): String = when (hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }

    fun build(d: Devotional, verseText: Map<ScriptureReference, String>, settings: VoiceSettings, name: String? = null, hour: Int = java.time.LocalTime.now().hour): List<SpeechSegment> = buildList {
        val hello = greetingFor(hour)
        val greeting = name?.takeIf { it.isNotBlank() }?.let { "$hello, $it." } ?: "$hello."
        val opening = if (d.origin == DevotionalOrigin.CLASSIC) "$greeting Today's reading is from ${d.engine ?: "a classic devotional"}." else "$greeting This is your devotional: ${d.title}."
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
     * The script as speech requests: one per segment (long ones split by sentence under
     * [maxChars]), each with the silence to put after it. Pauses are real silence joined in on the
     * phone — never inline tags, which a TTS model can end up reading out loud.
     */
    fun chunks(segments: List<SpeechSegment>, maxChars: Int = 2400): List<SpeechChunk> = buildList {
        for (s in segments) {
            val text = stripTags(s.text).takeIf { it.isNotBlank() } ?: continue
            val pieces = if (text.length <= maxChars) listOf(text) else text.split(Regex("(?<=[.!?])\\s+")).fold(mutableListOf<String>()) { acc, sentence ->
                if (acc.isNotEmpty() && acc.last().length + sentence.length + 1 <= maxChars) acc[acc.size - 1] = acc.last() + " " + sentence else acc += sentence
                acc
            }
            pieces.forEachIndexed { i, piece -> add(SpeechChunk(piece.trim(), if (i == pieces.lastIndex) s.pauseAfterMs else 150)) }
        }
    }

    /** Anything in angle or square brackets ("<short pause>", "[pause]") — stage directions, not words. */
    fun stripTags(text: String): String = text.replace(Regex("""<[^<>]{1,40}>|\[(?:short |long )?pause]""", RegexOption.IGNORE_CASE), " ").replace(Regex("\\s+"), " ").trim()
}

/** One speech request's words and the silence after them. */
data class SpeechChunk(val text: String, val pauseAfterMs: Long)
