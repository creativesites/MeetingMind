package com.example.ai.devotional

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.devotional.ClassicDevotionals
import com.example.core.devotional.Devotional
import com.example.core.devotional.DevotionalLabels
import com.example.core.devotional.DevotionalOrigin
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalSource
import com.example.core.devotional.LiturgicalCalendar
import com.example.core.devotional.LocalDay
import com.example.core.devotional.Quote
import com.example.core.devotional.Quotes
import com.example.core.devotional.TopicPassages
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureReferenceParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * A devotional asked for on demand (PLAN_V2 F2, "write me one"): anything left null follows the
 * profile. [variant] > 0 asks for a different one than the day's first.
 */
data class DevotionalAsk(
    val about: String? = null,
    val passage: String? = null,
    val topics: Set<String> = emptySet(),
    val tone: com.example.core.devotional.DevotionalTone? = null,
    val minutes: Int? = null,
    val variant: Int = 0
) {
    val custom get() = !about.isNullOrBlank() || !passage.isNullOrBlank() || topics.isNotEmpty() || tone != null || minutes != null

    fun toJson(): String = org.json.JSONObject().apply {
        about?.let { put("about", it) }; passage?.let { put("passage", it) }; put("topics", org.json.JSONArray(topics.toList()))
        tone?.let { put("tone", it.name) }; minutes?.let { put("minutes", it) }; put("variant", variant)
    }.toString()

    companion object {
        fun fromJson(raw: String?): DevotionalAsk? {
            if (raw.isNullOrBlank()) return null
            val o = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return null
            val t = o.optJSONArray("topics")
            return DevotionalAsk(
                about = o.optString("about").takeIf { it.isNotBlank() }, passage = o.optString("passage").takeIf { it.isNotBlank() },
                topics = (0 until (t?.length() ?: 0)).map { t!!.getString(it) }.toSet(),
                tone = runCatching { com.example.core.devotional.DevotionalTone.valueOf(o.getString("tone")) }.getOrNull(),
                minutes = o.optInt("minutes", 0).takeIf { it > 0 }, variant = o.optInt("variant", 0)
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
    private val locale: Locale = Locale.getDefault()
) {
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
        ask: DevotionalAsk? = null
    ): Devotional {
        lastFallbackReason = null
        val profile = if (ask == null) profile else profile.copy(
            tone = ask.tone ?: profile.tone, minutes = ask.minutes ?: profile.minutes,
            topics = if (ask.topics.isNotEmpty()) ask.topics else profile.topics
        )
        val variant = ask?.variant ?: 0
        val day = LiturgicalCalendar.dayOf(date, profile.tradition)
        val source = if (ask?.custom == true) DevotionalSource.AI else when (profile.source) {
            DevotionalSource.MIX -> if (date.dayOfWeek == DayOfWeek.SUNDAY) DevotionalSource.CLASSIC else DevotionalSource.AI
            else -> profile.source
        }
        if (source == DevotionalSource.CLASSIC || evening) return classicVariant(date, evening, profile, variant) ?: mine(date, profile)
        if (source == DevotionalSource.MINE) return mine(date, profile)

        // The care check comes before any AI writing, and never leaves the phone.
        if (DevotionalContract.crisisIn(signals.recentWords)) return care(date)

        val passage = ask?.passage?.let { ScriptureReferenceParser.parse(it) } ?: passageFor(date, profile, variant)
        val text = runCatching { verseText(passage) }.getOrNull()
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        for (candidate in runCatching { candidates() }.getOrDefault(emptyList())) {
            val shared = if (candidate.isCloud && !profile.sharePrivateWithCloud) signals.general else signals.general + signals.private
            val brief = DevotionalBrief(passage, text, profile, day, weekday, shared, name, ask?.about)
            // Each model gets a fair turn, not forever: a hung download or network moves on to the next.
            val result = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(if (candidate.isCloud) CLOUD_TIMEOUT_MS else DEVICE_TIMEOUT_MS) {
                    candidate.model.generate(DevotionalContract.prompt(brief), maxOutputTokens = profile.words * 2 + 600)
                } ?: AiResult.Failed(if (candidate.isCloud) "Gemini took too long to answer." else "The on-device model took too long.")
            }.getOrElse { AiResult.Failed(it.message ?: "failed", it) }
            val raw = (result as? AiResult.Success)?.value
            if (raw == null) { lastFallbackReason = (result as? AiResult.Failed)?.message ?: "The AI model wasn't available."; continue }
            val answer = DevotionalContract.parse(raw)
            if (answer == null) { lastFallbackReason = "The AI answer couldn't be read."; continue }
            val checked = check(answer, passage, profile, date) ?: run { lastFallbackReason = "The AI answer didn't pass the devotional checks."; null } ?: continue
            return checked.copy(
                origin = if (candidate.isCloud) DevotionalOrigin.CLOUD_AI else DevotionalOrigin.DEVICE_AI,
                label = if (candidate.isCloud) DevotionalLabels.CLOUD else DevotionalLabels.DEVICE,
                engine = candidate.id,
                season = day
            )
        }
        if (lastFallbackReason == null) lastFallbackReason = "No AI model is set up, so today's reading is a classic."
        return classicVariant(date, false, profile, variant) ?: mine(date, profile)
    }

    /** Today's passage: the season's or the person's topics, then the Verse of the Day, then the classic's. */
    suspend fun passageFor(date: LocalDate, profile: DevotionalProfile, variant: Int = 0): ScriptureReference {
        val follows = profile.tradition != com.example.core.devotional.Tradition.NON_DENOMINATIONAL || profile.topics.isEmpty()
        val day = if (follows && variant == 0) LiturgicalCalendar.dayOf(date, profile.tradition).takeIf { it.season != com.example.core.devotional.LiturgicalSeason.ORDINARY } else null
        // A different one: step through the topic lists, or through the classic's key verses.
        if (variant > 0) {
            TopicPassages.pick(date.plusDays(variant * 37L), profile.topics + profile.moreOf, profile.lessOf, null)?.let { return it }
            classics.forDate(date.minusDays(variant * 11L), evening = variant % 2 == 1)?.reference?.let { return it }
        }
        return TopicPassages.pick(date, profile.topics + profile.moreOf, profile.lessOf, day)
            ?: runCatching { verseOfTheDay(date) }.getOrNull()
            ?: classics.forDate(date)?.reference
            ?: ScriptureReferenceParser.parse("John 15:5")!!
    }

    /** Applies the labelled-AI rules to an answer; null when too little survives to be worth reading. */
    private suspend fun check(answer: DevotionalAnswer, passage: ScriptureReference, profile: DevotionalProfile, date: LocalDate): Devotional? {
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
            insight = if (profile.includeInsight) Quotes.pick(quotes, profile.topics + profile.moreOf, date) else null,
            question = if (profile.includeQuestion) whole(answer.question) else null,
            label = DevotionalLabels.CLOUD
        )
    }

    /** The day's classic, or — for "a different one" — the evening reading, then readings from nearby days. */
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
            insight = if (profile.includeInsight) Quotes.pick(quotes, profile.topics, date) else null,
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
