package com.craftflowtechnologies.meetingmind.core.create

import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.companion.CreateModePolicy
import com.craftflowtechnologies.meetingmind.core.companion.CreateSource

/**
 * Which vibes a card may use, and which one to suggest. The six moods are delegated to
 * [CreateModePolicy] (so the rules live in one place and are enforced in code, never by a model);
 * the general vibes are allowed wherever the source is not a prayer, grief or a sacred text.
 */
object CreateVibePolicy {

    private val general = listOf(CreateVibe.MOTIVATIONAL, CreateVibe.FUNNY, CreateVibe.WISDOM, CreateVibe.LOVE, CreateVibe.CUSTOM)

    /** Wisdom and Love sit comfortably beside scripture; Funny and Motivational do not. */
    private val onSacred = setOf(CreateVibe.WISDOM, CreateVibe.LOVE, CreateVibe.CUSTOM)

    fun allowed(source: CreateSourceKind, grief: Boolean = false, goodFriday: Boolean = false): Set<CreateVibe> {
        val moods = CreateModePolicy.allowed(source.policy, null, grief, goodFriday).map { CreateVibe.fromMood(it) }.toSet()
        val extras = when {
            grief || source.policy == CreateSource.PRAYER_REQUEST -> emptySet()
            source.policy == CreateSource.SCRIPTURE_VERSE || source.policy == CreateSource.SERMON_QUOTE ||
                source.policy == CreateSource.DEVOTIONAL -> onSacred
            else -> general.toSet()
        }
        return moods + extras
    }

    /** Allowed vibes in chip order (moods first, then the general ones). */
    fun ordered(source: CreateSourceKind, grief: Boolean = false, goodFriday: Boolean = false): List<CreateVibe> {
        val ok = allowed(source, grief, goodFriday)
        return CreateVibe.entries.filter { it in ok }
    }

    /** [wanted] if it is allowed, else the suggestion. A model's pick passes through here too. */
    fun enforce(source: CreateSourceKind, wanted: CreateVibe?, grief: Boolean = false, goodFriday: Boolean = false): CreateVibe =
        wanted?.takeIf { it in allowed(source, grief, goodFriday) } ?: suggest(source, null, grief, goodFriday)

    /**
     * The suggestion: a hint from the model or the words themselves if allowed, otherwise the
     * companion policy's pick for the source. The result is always one of [allowed].
     */
    fun suggest(source: CreateSourceKind, hint: CreateVibe? = null, grief: Boolean = false, goodFriday: Boolean = false): CreateVibe {
        val ok = allowed(source, grief, goodFriday)
        if (hint != null && hint in ok) return hint
        val mood: CreateMode = CreateModePolicy.suggest(source.policy, hint?.policyVibe, grief, goodFriday)
        return CreateVibe.fromMood(mood).takeIf { it in ok } ?: ok.first()
    }

    /** A quick, local read of the words (no model) so the studio can preselect a chip before anything is generated. */
    fun suggestFromText(source: CreateSourceKind, text: String, grief: Boolean = false, goodFriday: Boolean = false): CreateVibe {
        val t = text.lowercase()
        val hint = when {
            Regex("""\b(funny|joke|lol|monday|meeting|haha|hilarious)\b""").containsMatchIn(t) -> CreateVibe.FUNNY
            Regex("""\b(thank|thanks|grateful|gratitude|blessed)\b""").containsMatchIn(t) -> CreateVibe.GRATEFUL
            Regex("""\b(celebrate|congrat|won|promotion|graduat|milestone|achieved)\b""").containsMatchIn(t) -> CreateVibe.CELEBRATORY
            Regex("""\b(rejoice|joy|glad|delight)\b""").containsMatchIn(t) -> CreateVibe.JOYFUL
            Regex("""\b(love|beloved|heart)\b""").containsMatchIn(t) -> CreateVibe.LOVE
            Regex("""\b(goal|discipline|hustle|push|grind|never give up|keep going)\b""").containsMatchIn(t) -> CreateVibe.MOTIVATIONAL
            Regex("""\b(wisdom|wise|learn|lesson|think)\b""").containsMatchIn(t) -> CreateVibe.WISDOM
            Regex("""\b(wonder|reflect|remember|consider|ponder)\b""").containsMatchIn(t) -> CreateVibe.REFLECTIVE
            Regex("""\b(pray|prayer|lord|father)\b""").containsMatchIn(t) -> CreateVibe.PRAYERFUL
            Regex("""\b(peace|rest|still|calm|quiet)\b""").containsMatchIn(t) -> CreateVibe.PEACEFUL
            else -> null
        }
        return suggest(source, hint, grief, goodFriday)
    }

    /** The pose to draw when "Include {companion}" is on: the mood itself, or the policy's pose for a general vibe. */
    fun companionMode(source: CreateSourceKind, vibe: CreateVibe, grief: Boolean = false): CreateMode {
        val allowedModes = CreateModePolicy.allowed(source.policy, vibe.policyVibe, grief)
        vibe.mood?.takeIf { it in allowedModes }?.let { return it }
        return CreateModePolicy.suggest(source.policy, vibe.policyVibe, grief)
    }
}
