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
    /** Why the last [write] fell back, for the "why is this a classic today?" line. */
    var lastFallbackReason: String? = null
        private set

    suspend fun write(
        date: LocalDate,
        profile: DevotionalProfile,
        signals: DevotionalSignals = DevotionalSignals(),
        name: String? = null,
        evening: Boolean = false
    ): Devotional {
        lastFallbackReason = null
        val day = LiturgicalCalendar.dayOf(date, profile.tradition)
        val source = when (profile.source) {
            DevotionalSource.MIX -> if (date.dayOfWeek == DayOfWeek.SUNDAY) DevotionalSource.CLASSIC else DevotionalSource.AI
            else -> profile.source
        }
        if (source == DevotionalSource.CLASSIC || evening) return classic(date, evening, profile) ?: mine(date, profile)
        if (source == DevotionalSource.MINE) return mine(date, profile)

        // The care check comes before any AI writing, and never leaves the phone.
        if (DevotionalContract.crisisIn(signals.recentWords)) return care(date)

        val passage = passageFor(date, profile)
        val text = runCatching { verseText(passage) }.getOrNull()
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        for (candidate in runCatching { candidates() }.getOrDefault(emptyList())) {
            val shared = if (candidate.isCloud && !profile.sharePrivateWithCloud) signals.general else signals.general + signals.private
            val brief = DevotionalBrief(passage, text, profile, day, weekday, shared, name)
            val result = runCatching { candidate.model.generate(DevotionalContract.prompt(brief), maxOutputTokens = profile.words * 2 + 600) }
                .getOrElse { AiResult.Failed(it.message ?: "failed", it) }
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
        return classic(date, false, profile) ?: mine(date, profile)
    }

    /** Today's passage: the season's or the person's topics, then the Verse of the Day, then the classic's. */
    suspend fun passageFor(date: LocalDate, profile: DevotionalProfile): ScriptureReference {
        val follows = profile.tradition != com.example.core.devotional.Tradition.NON_DENOMINATIONAL || profile.topics.isEmpty()
        val day = if (follows) LiturgicalCalendar.dayOf(date, profile.tradition).takeIf { it.season != com.example.core.devotional.LiturgicalSeason.ORDINARY } else null
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
