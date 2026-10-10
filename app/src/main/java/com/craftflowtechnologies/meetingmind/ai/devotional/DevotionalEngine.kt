package com.craftflowtechnologies.meetingmind.ai.devotional

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.Devotional
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalLabels
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalOrigin
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSource
import com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalCalendar
import com.craftflowtechnologies.meetingmind.core.devotional.PassageRotation
import com.craftflowtechnologies.meetingmind.core.devotional.Tradition
import com.craftflowtechnologies.meetingmind.core.devotional.LocalDay
import com.craftflowtechnologies.meetingmind.core.devotional.Quote
import com.craftflowtechnologies.meetingmind.core.devotional.Quotes
import com.craftflowtechnologies.meetingmind.core.devotional.TopicPassages
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Who writes an on-demand devotional — the person's choice, not a silent fallback chain. */
enum class DevotionalWriter(val label: String) {
    /** The usual chain: Gemini if set up, then the phone's model, then a classic. */
    AUTO("Best available"),
    DEVICE("On this phone"),
    GEMINI("Gemini"),
    CLASSIC("A classic")
}

/** The chosen writer couldn't write it; nothing is saved and what was there stays. */
class DevotionalUnavailable(message: String) : Exception(message)

/**
 * A devotional asked for on demand (PLAN_V2 F2, "write me one"): anything left null follows the
 * profile. [variant] > 0 asks for a different one than the day's first.
 */
data class DevotionalAsk(
    val about: String? = null,
    val passage: String? = null,
    val topics: Set<String> = emptySet(),
    val tone: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalTone? = null,
    val minutes: Int? = null,
    val variant: Int = 0,
    val writer: DevotionalWriter = DevotionalWriter.AUTO,
    /** The hour it's asked for (it'll be read now); null for the scheduled morning one. */
    val hour: Int? = null,
    /**
     * The timetable's own writes: if AI models are set up but all fail (offline, quota, a bad
     * answer), say so instead of quietly filing a classic as today's devotional. With no AI set
     * up at all, the usual classic still stands in.
     */
    val keepToAi: Boolean = false
) {
    val custom get() = !about.isNullOrBlank() || !passage.isNullOrBlank() || topics.isNotEmpty() || tone != null || minutes != null

    fun toJson(): String = org.json.JSONObject().apply {
        about?.let { put("about", it) }; passage?.let { put("passage", it) }; put("topics", org.json.JSONArray(topics.toList()))
        tone?.let { put("tone", it.name) }; minutes?.let { put("minutes", it) }; put("variant", variant); put("writer", writer.name); hour?.let { put("hour", it) }; if (keepToAi) put("keepToAi", true)
    }.toString()

    companion object {
        fun fromJson(raw: String?): DevotionalAsk? {
            if (raw.isNullOrBlank()) return null
            val o = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return null
            val t = o.optJSONArray("topics")
            return DevotionalAsk(
                about = o.optString("about").takeIf { it.isNotBlank() }, passage = o.optString("passage").takeIf { it.isNotBlank() },
                topics = (0 until (t?.length() ?: 0)).map { t!!.getString(it) }.toSet(),
                tone = runCatching { com.craftflowtechnologies.meetingmind.core.devotional.DevotionalTone.valueOf(o.getString("tone")) }.getOrNull(),
                minutes = o.optInt("minutes", 0).takeIf { it > 0 }, variant = o.optInt("variant", 0),
                writer = runCatching { DevotionalWriter.valueOf(o.getString("writer")) }.getOrDefault(DevotionalWriter.AUTO),
                hour = if (o.has("hour")) o.optInt("hour") else null,
                keepToAi = o.optBoolean("keepToAi", false)
            )
        }
    }
}

/** A model the engine may try, best first. */
data class ModelCandidate(val model: LanguageModel, val id: String, val isCloud: Boolean)

/** What's known about the person's recent days, split by who may see it. */
data class DevotionalSignals(
    /** Safe to send anywhere: sermon titles and passages, themes, how busy today is. */
    val general: List<String> = emptyList(),
    /** Prayer requests and journal lines: used on the phone, sent to the cloud only when allowed. */
    val private: List<String> = emptyList(),
    /** The person's own recent words, read on the phone for the care check and nothing else. */
    val recentWords: List<String> = emptyList()
)

/**
 * Writes the day's devotional (PLAN_V2 F2). The chain always ends in something good: the cloud
 * model, then the on-device model, then the bundled classic for today — so there is a devotional
 * every morning, online or not, key or not.
 */
class DevotionalEngine(
    private val candidates: suspend () -> List<ModelCandidate>,
    private val verseText: suspend (ScriptureReference) -> String?,
    private val classics: ClassicDevotionals,
    private val quotes: List<Quote>,
    private val verseOfTheDay: suspend (LocalDate) -> ScriptureReference? = { null },
    private val locale: Locale = Locale.getDefault()) {
    companion object {
        const val CLOUD_TIMEOUT_MS = 60_000L
        const val DEVICE_TIMEOUT_MS = 100_000L
    }

    /** Why the last [write] fell back, for the "why is this a classic today?" line. */
    var lastFallbackReason: String? = null
        private set

    suspend fun write(
        date: LocalDate,
        profile: DevotionalProfile,
        signals: DevotionalSignals = DevotionalSignals(),
        name: String? = null,
        evening: Boolean = false,
        ask: DevotionalAsk? = null,
        /** Recent devotionals ("title — first lines"), newest first, for the model not to repeat. */
        recent: List<String> = emptyList(),
        /** The last fortnight, for the passage window, format rotation and the novelty check. */
        memory: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalMemory = com.craftflowtechnologies.meetingmind.core.devotional.DevotionalMemory(),
        /** This morning's devotional, for the evening Examen. */
        morning: Devotional? = null
    ): Devotional {
        lastFallbackReason = null
        // A one-off request (tone, length, topics) applies to this devotional only; the topics it names
        // are typed in the moment, so they steer it whatever the Personal touch setting says.
        val profile = if (ask == null) profile else profile.copy(tone = ask.tone ?: profile.tone, minutes = ask.minutes ?: profile.minutes)
        val askTopics = ask?.topics.orEmpty()
        val variant = ask?.variant ?: 0
        val day = LiturgicalCalendar.dayOf(date, profile.tradition)
        val source = if (ask?.custom == true) DevotionalSource.AI else when (profile.source) {
            DevotionalSource.MIX -> if (date.dayOfWeek == DayOfWeek.SUNDAY) DevotionalSource.CLASSIC else DevotionalSource.AI
            else -> profile.source
        }
        val writer = ask?.writer ?: DevotionalWriter.AUTO
        if (writer == DevotionalWriter.CLASSIC) return anyClassic(date, evening, profile, variant) ?: throw DevotionalUnavailable("No classic reading is available for today.")
        if (writer == DevotionalWriter.AUTO) {
            // An evening Examen is written when asked for; otherwise the evening reading is a classic.
            if (source == DevotionalSource.CLASSIC || evening && !profile.eveningExamen) return anyClassic(date, evening, profile, variant) ?: mine(date, profile)
            if (source == DevotionalSource.MINE) return mine(date, profile)
        }

        // The care check comes before any AI writing, and never leaves the phone.
        if (DevotionalContract.crisisIn(signals.recentWords)) return care(date)

        // A series sets the passage; otherwise a passage not used within the exclusion window.
        val series = profile.series?.takeIf { ask?.passage == null && !evening && !it.finished(date.toEpochDay()) }
        val seriesPassage = series?.passageFor(date.toEpochDay())?.let { ScriptureReferenceParser.parse(it) }
        val passage = ask?.passage?.let { ScriptureReferenceParser.parse(it) }
            ?: seriesPassage
            ?: (if (evening) morning?.scripture?.firstOrNull() else null)
            ?: freshPassage(date, profile, variant, memory, askTopics)
        val format = when {
            evening -> com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat.DAILY_EXAMEN
            !profile.rotateFormats -> profile.fixedFormat ?: profile.formats.firstOrNull() ?: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat.REFLECTION
            else -> com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat.pick(profile.formats, date.toEpochDay() + variant, memory.lastFormat, memory.recentFormats)
        }
        val seriesLine = series?.let { "${it.title}, day ${it.dayIndex(date.toEpochDay()) + 1} of ${it.passages.size}" }
        val seriesSoFar = series?.let { sp -> memory.seriesSoFar(sp.title).map { e -> "${e.title} (${e.passage}) — ${e.point ?: e.opening.orEmpty()}" } }.orEmpty()
        val morningLine = morning?.takeIf { evening }?.let { m -> "\"${m.title}\" on ${m.scripture.firstOrNull()?.display().orEmpty()}; it asked: ${m.application.joinToString("; ").ifBlank { m.question.orEmpty() }}" }
        val recentLines = recent.ifEmpty { memory.promptLines() }
        val text = runCatching { verseText(passage) }.getOrNull()
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        // A chosen writer is kept to: only its models are tried, and failing is said, not papered over.
        val pool = runCatching { candidates() }.getOrDefault(emptyList()).filter {
            when (writer) { DevotionalWriter.DEVICE -> !it.isCloud; DevotionalWriter.GEMINI -> it.isCloud; else -> true }
        }
        if (pool.isEmpty() && writer == DevotionalWriter.DEVICE) throw DevotionalUnavailable("There's no language model on this phone yet. Get one in Setup, or choose Gemini.")
        if (pool.isEmpty() && writer == DevotionalWriter.GEMINI) throw DevotionalUnavailable("Gemini isn't set up. Add your API key in Settings, or choose On this phone.")
        for (candidate in pool) {
            val shared = if (candidate.isCloud && !profile.sharePrivateWithCloud) signals.general else signals.general + signals.private
            // On demand, it's read now — say so; the scheduled one comes out in the morning but stays time-neutral.
            val brief = DevotionalBrief(
                passage, text, profile, day, weekday, shared, name, ask?.about, hour = ask?.hour,
                date = date, askTopics = askTopics,
                recent = recentLines, dayIndex = date.toEpochDay() + variant * 5L,
                format = format, series = seriesLine, seriesSoFar = seriesSoFar, morning = morningLine
            )
            suspend fun attempt(b: DevotionalBrief): Devotional? {
                // Each model gets a fair turn, not forever: a hung download or network moves on to the next.
                val result = runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(if (candidate.isCloud) CLOUD_TIMEOUT_MS else DEVICE_TIMEOUT_MS) {
                        candidate.model.generate(DevotionalContract.prompt(b), maxOutputTokens = profile.words * 2 + 600)
                    } ?: AiResult.Failed(if (candidate.isCloud) "Gemini took too long to answer." else "The on-device model took too long.")
                }.getOrElse { AiResult.Failed(it.message ?: "failed", it) }
                val raw = (result as? AiResult.Success)?.value
                if (raw == null) { lastFallbackReason = (result as? AiResult.Failed)?.message ?: "The AI model wasn't available."; return null }
                val answer = DevotionalContract.parse(raw)
                if (answer == null) { lastFallbackReason = "The AI answer couldn't be read."; return null }
                return check(answer, passage, profile, date, askTopics) ?: run { lastFallbackReason = "The AI answer didn't pass the devotional checks."; null }
            }
            var checked = attempt(brief) ?: continue
            // The novelty check: one rewrite if it repeats a recent devotional; a second repeat is kept.
            memory.repeatOf(checked)?.let { why -> attempt(brief.copy(retryNote = why))?.let { checked = it } }
            checked = checked.copy(format = format, seriesTitle = series?.title, seriesDay = series?.let { it.dayIndex(date.toEpochDay()) + 1 })
            return checked.copy(
                origin = if (candidate.isCloud) DevotionalOrigin.CLOUD_AI else DevotionalOrigin.DEVICE_AI,
                label = if (candidate.isCloud) DevotionalLabels.CLOUD else DevotionalLabels.DEVICE,
                engine = candidate.id,
                season = day
            )
        }
        if (writer == DevotionalWriter.DEVICE || writer == DevotionalWriter.GEMINI) {
            throw DevotionalUnavailable(lastFallbackReason ?: "The AI couldn't write one just now.")
        }
        if (pool.isNotEmpty() && ask?.keepToAi == true) throw DevotionalUnavailable(lastFallbackReason ?: "The AI couldn't write one just now.")
        if (lastFallbackReason == null) lastFallbackReason = "No AI model is set up, so today's reading is a classic."
        return anyClassic(date, false, profile, variant) ?: mine(date, profile)
    }

    /**
     * Topics and "more of" as gentle weights for the day's passage or quote: nothing when the Personal
     * touch is off, a light nudge on some days when it's on, plus whatever the person typed in the moment.
     */
    private fun quoteWeights(profile: DevotionalProfile, date: LocalDate, askTopics: Set<String> = emptySet()): Set<String> =
        askTopics + if (profile.personalTouch.weightsOn(date)) profile.topics + profile.moreOf else emptySet()

    /**
     * A passage outside the exclusion window, stepping through the candidates until one is fresh. It also
     * prefers a Bible book other than the last few days' (variety, not continuity).
     */
    suspend fun freshPassage(
        date: LocalDate, profile: DevotionalProfile, variant: Int,
        memory: com.craftflowtechnologies.meetingmind.core.devotional.DevotionalMemory,
        askTopics: Set<String> = emptySet()
    ): ScriptureReference {
        val candidates = (0 until 24).map { passageFor(date, profile, variant + it, askTopics) }
        val fresh = candidates.filter { !memory.usedRecently(it, date, profile.passageExclusionDays) }
        val books = memory.recentBooks(4, date)
        return fresh.firstOrNull { it.usfm !in books } ?: fresh.firstOrNull() ?: candidates.first()
    }

    /** Traditions that keep the church year, so its seasons choose the reading on Sundays and feasts. */
    private val followsChurchYear = setOf(Tradition.CATHOLIC, Tradition.ORTHODOX, Tradition.ANGLICAN, Tradition.METHODIST)

    /**
     * Today's passage. The season's reading on a feast (and on Sundays and in Holy Week for traditions that
     * keep the church year); otherwise the next passage in [PassageRotation], a cycle across the whole canon
     * that knows nothing about the reader. Topics and "more of" only weigh in (about one day in four at most)
     * when the person has turned on a Personal touch; "less of" is always honoured.
     */
    suspend fun passageFor(date: LocalDate, profile: DevotionalProfile, variant: Int = 0, askTopics: Set<String> = emptySet()): ScriptureReference {
        val less = profile.lessOf
        if (variant == 0) {
            val day = LiturgicalCalendar.dayOf(date, profile.tradition)
            val follows = profile.tradition in followsChurchYear
            val seasonal = day.season != com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalSeason.ORDINARY &&
                (day.feast != null || follows && (date.dayOfWeek == DayOfWeek.SUNDAY || day.season == com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalSeason.HOLY_WEEK))
            if (seasonal) TopicPassages.pick(date, emptyList(), less, day)?.let { return it }
        }
        val weights = quoteWeights(profile, date, askTopics)
        if (weights.isNotEmpty()) TopicPassages.pick(date.plusDays(variant * 37L), weights, less, null)?.let { return it }
        return PassageRotation.next(PassageRotation.indexFor(date, variant), less)
            ?: runCatching { verseOfTheDay(date) }.getOrNull()
            ?: classics.forDate(date)?.reference
            ?: ScriptureReferenceParser.parse("John 15:5")!!
    }

    /** Applies the labelled-AI rules to an answer; null when too little survives to be worth reading. */
    private suspend fun check(answer: DevotionalAnswer, passage: ScriptureReference, profile: DevotionalProfile, date: LocalDate, askTopics: Set<String> = emptySet()): Devotional? {
        var removed = 0
        suspend fun clean(s: String?): String? {
            if (s == null) return null
            val (kept, n) = DevotionalContract.removeForbidden(s)
            removed += n
            return DevotionalContract.fixQuotedScripture(kept) { runCatching { verseText(it) }.getOrNull() }.trim().takeIf { it.isNotEmpty() }
        }
        val reflection = answer.reflection.mapNotNull { clean(it) }
        if (reflection.isEmpty() || removed > 3) return null
        // A prayer or motivation that needed trimming is left out rather than printed half-cut.
        suspend fun whole(s: String?): String? {
            if (s == null) return null
            val before = removed
            val c = clean(s)
            return if (removed == before) c else null
        }
        val extra = answer.extraReferences.mapNotNull { ScriptureReferenceParser.parse(it) }.filter { it != passage }.distinct().take(2)
        return Devotional(
            day = LocalDay.of(date),
            origin = DevotionalOrigin.CLOUD_AI,
            title = clean(answer.title)?.trim('"', '“', '”')?.take(80) ?: passage.display(),
            scripture = listOf(passage) + extra,
            reflection = reflection,
            application = answer.application.mapNotNull { whole(it) },
            prayer = if (profile.includePrayer) whole(answer.prayer) else null,
            motivation = if (profile.includeMotivation) whole(answer.motivation) else null,
            insight = if (profile.includeInsight) Quotes.pick(quotes, quoteWeights(profile, date, askTopics), date, profile.lessOf) else null,
            question = if (profile.includeQuestion) whole(answer.question) else null,
            label = DevotionalLabels.CLOUD
        )
    }

    /** The day's classic, or — for "a different one" — the evening reading, then readings from nearby days. */
    /** A classic for the day, looking further afield rather than ever falling back to a blank page. */
    fun anyClassic(date: LocalDate, evening: Boolean, profile: DevotionalProfile, variant: Int): Devotional? =
        classicVariant(date, evening, profile, variant) ?: classic(date, evening, profile) ?: classic(date, !evening, profile)
            ?: (1..14).firstNotNullOfOrNull { classic(date.minusDays(it.toLong()), evening, profile)?.copy(day = LocalDay.of(date)) }

    fun classicVariant(date: LocalDate, evening: Boolean, profile: DevotionalProfile, variant: Int): Devotional? = when {
        variant <= 0 -> classic(date, evening, profile)
        variant == 1 -> classic(date, !evening, profile)
        else -> classic(date.minusDays((variant - 1) * 7L), variant % 2 == 0, profile)?.copy(day = LocalDay.of(date))
    }

    fun classic(date: LocalDate, evening: Boolean, profile: DevotionalProfile): Devotional? {
        val r = classics.forDate(date, evening) ?: return null
        val title = r.keyText.trim('"', '“', '”', ' ').let { if (it.length > 70) it.take(67).substringBeforeLast(' ') + "…" else it }
            .ifBlank { r.referenceText }
        return Devotional(
            day = LocalDay.of(date),
            origin = DevotionalOrigin.CLASSIC,
            title = title,
            scripture = listOfNotNull(r.reference),
            keyText = r.keyText.takeIf { it.isNotBlank() },
            reflection = r.paragraphs,
            insight = if (profile.includeInsight) Quotes.pick(quotes, quoteWeights(profile, date), date, profile.lessOf) else null,
            label = classics.attribution,
            engine = "${classics.author}, ${classics.title}${if (evening) " (evening)" else ""}",
            season = LiturgicalCalendar.dayOf(date, profile.tradition)
        )
    }

    suspend fun mine(date: LocalDate, profile: DevotionalProfile): Devotional {
        val passage = passageFor(date, profile)
        return Devotional(
            day = LocalDay.of(date), origin = DevotionalOrigin.MINE, title = passage.display(),
            scripture = listOf(passage), reflection = emptyList(), label = DevotionalLabels.MINE,
            season = LiturgicalCalendar.dayOf(date, profile.tradition)
        )
    }

    fun care(date: LocalDate) = Devotional(
        day = LocalDay.of(date), origin = DevotionalOrigin.CARE, title = "You're not alone",
        scripture = listOfNotNull(ScriptureReferenceParser.parse("Psalm 34:18")),
        reflection = DevotionalContract.CARE_TEXT, label = DevotionalLabels.CARE
    )
}

/** The devotionals of the last 14 days (not counting [today]'s own), as memory. */
fun memoryOf(notes: List<com.craftflowtechnologies.meetingmind.core.model.Note>, today: LocalDate): com.craftflowtechnologies.meetingmind.core.devotional.DevotionalMemory {
    val from = today.minusDays(14)
    return com.craftflowtechnologies.meetingmind.core.devotional.DevotionalMemory.fromMetadata(notes.map { it.title to it.metadata }).let { m ->
        m.copy(entries = m.entries.filter { e -> runCatching { LocalDate.parse(e.day) }.getOrNull()?.let { !it.isBefore(from) } == true })
    }
}
