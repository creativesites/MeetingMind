package com.example.core.devotional

import org.json.JSONArray
import org.json.JSONObject

/** Where each day's devotional comes from. */
enum class DevotionalSource(val label: String, val description: String) {
    AI("Written for you", "A fresh devotional each day, shaped by what you're walking through. Labelled as AI-written."),
    CLASSIC("A classic", "Spurgeon's Morning and Evening — a reading for every morning and evening of the year."),
    MINE("My own", "A guided page each morning with a verse to start from, for you to write."),
    MIX("A mix", "Written for you in the week, a classic on Sundays.")
}

/** Shapes wording and calendar awareness; never doctrine the person hasn't chosen. */
enum class Tradition(val label: String) {
    NON_DENOMINATIONAL("Non-denominational"),
    EVANGELICAL("Evangelical"),
    CATHOLIC("Catholic"),
    ORTHODOX("Orthodox"),
    ANGLICAN("Anglican"),
    PENTECOSTAL("Pentecostal"),
    REFORMED("Reformed"),
    METHODIST("Methodist"),
    BAPTIST("Baptist")
}

/** The voice the devotional is written in (and later, spoken in). */
enum class DevotionalTone(val label: String, val guidance: String) {
    PASTOR("Warm pastor", "a warm, unhurried pastor who knows and loves the reader"),
    FRIEND("Gentle friend", "a gentle friend sitting across the table — plain words, honest, kind"),
    TEACHER("Calm teacher", "a calm Bible teacher who opens up the passage's context and meaning"),
    POET("Poet", "a contemplative writer — vivid images, short lines, room to breathe"),
    SCHOLAR("Scholar", "a careful scholar — historical and literary insight, still devotional in heart")
}

/** Topics a person can ask to hear more about. */
object DevotionalTopics {
    val all = listOf(
        "Peace", "Anxiety", "Hope", "Grief", "Faith", "Forgiveness", "Purpose", "Patience", "Courage",
        "Gratitude", "Rest", "Love", "Prayer", "Wisdom", "Identity", "Joy", "Trust", "Healing"
    )
    val seasons = listOf(
        "New job", "Exams", "Marriage", "New parent", "Parenting teens", "Grief and loss", "Illness",
        "Waiting", "Leadership", "Singleness", "Moving", "Retirement", "Money worries", "A fresh start"
    )
}

/**
 * How someone wants their daily devotional (PLAN_V2 F2). Everything has a sensible default, so a
 * person who never opens the settings still gets a good devotional.
 */
data class DevotionalProfile(
    val enabled: Boolean = false,
    val source: DevotionalSource = DevotionalSource.AI,
    val tradition: Tradition = Tradition.NON_DENOMINATIONAL,
    /** Reading time: 3, 7 or 12 minutes. */
    val minutes: Int = 3,
    val tone: DevotionalTone = DevotionalTone.PASTOR,
    val topics: Set<String> = emptySet(),
    val season: String? = null,
    /** A sentence or two the person writes about themselves. Theirs to edit or clear. */
    val aboutMe: String = "",
    val includePrayer: Boolean = true,
    val includeMotivation: Boolean = true,
    val includeInsight: Boolean = true,
    val includeQuestion: Boolean = true,
    /** When the devotional arrives, as minutes after midnight (06:30 by default). */
    val deliveryMinutes: Int = 6 * 60 + 30,
    /**
     * Prayer requests and journal lines may shape an AI devotional written *in the cloud* only
     * with this on. On-device writing always may use them — nothing leaves the phone.
     */
    val sharePrivateWithCloud: Boolean = false,
    /** Topics the person asked for less of ("Less like this"). */
    val lessOf: Set<String> = emptySet(),
    /** Topics the person asked for more of ("More like this"). */
    val moreOf: Set<String> = emptySet()
) {
    val words: Int get() = when { minutes <= 3 -> 280; minutes <= 7 -> 650; else -> 1100 }

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("source", source.name); put("tradition", tradition.name); put("minutes", minutes)
        put("tone", tone.name); put("topics", JSONArray(topics.toList())); season?.let { put("season", it) }
        put("aboutMe", aboutMe); put("prayer", includePrayer); put("motivation", includeMotivation)
        put("insight", includeInsight); put("question", includeQuestion); put("delivery", deliveryMinutes)
        put("sharePrivate", sharePrivateWithCloud); put("less", JSONArray(lessOf.toList())); put("more", JSONArray(moreOf.toList()))
    }.toString()

    companion object {
        /** Reads a stored profile; anything unreadable falls back to the default for that field. */
        fun fromJson(raw: String?): DevotionalProfile {
            if (raw.isNullOrBlank()) return DevotionalProfile()
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return DevotionalProfile()
            val d = DevotionalProfile()
            fun set(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }.toSet() }
            return DevotionalProfile(
                enabled = o.optBoolean("enabled", d.enabled),
                source = runCatching { DevotionalSource.valueOf(o.getString("source")) }.getOrDefault(d.source),
                tradition = runCatching { Tradition.valueOf(o.getString("tradition")) }.getOrDefault(d.tradition),
                minutes = o.optInt("minutes", d.minutes).coerceIn(2, 15),
                tone = runCatching { DevotionalTone.valueOf(o.getString("tone")) }.getOrDefault(d.tone),
                topics = set("topics") ?: d.topics,
                season = o.optString("season").takeIf { it.isNotBlank() },
                aboutMe = o.optString("aboutMe", d.aboutMe),
                includePrayer = o.optBoolean("prayer", d.includePrayer),
                includeMotivation = o.optBoolean("motivation", d.includeMotivation),
                includeInsight = o.optBoolean("insight", d.includeInsight),
                includeQuestion = o.optBoolean("question", d.includeQuestion),
                deliveryMinutes = o.optInt("delivery", d.deliveryMinutes).coerceIn(0, 24 * 60 - 1),
                sharePrivateWithCloud = o.optBoolean("sharePrivate", d.sharePrivateWithCloud),
                lessOf = set("less") ?: d.lessOf,
                moreOf = set("more") ?: d.moreOf
            )
        }
    }
}
