package com.craftflowtechnologies.meetingmind.core.devotional

import org.json.JSONArray
import org.json.JSONObject

/** Where each day's devotional comes from. */
enum class DevotionalSource(val label: String, val description: String) {
    AI("Written with AI", "A fresh devotional each day, a different passage and theme each time. Labelled as written with AI. If no AI is available, your reading plan fills in."),
    /** "A reading plan": a bundled devotional book chosen by [DevotionalProfile.planId]. Works with no network and no key. */
    CLASSIC("A reading plan", "A bundled devotional book with a reading for every day of the year. Works offline."),
    MINE("My own", "A guided page each morning with a verse to start from, for you to write."),
    MIX("A mix", "Written with AI in the week, a reading from your plan on Sundays.")
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
    PASTOR("Warm pastor", "a warm, unhurried pastor who cares for the reader"),
    FRIEND("Gentle friend", "a gentle friend sitting across the table — plain words, honest, kind"),
    TEACHER("Calm teacher", "a calm Bible teacher who opens up the passage's context and meaning"),
    POET("Poet", "a contemplative writer — vivid images, short lines, room to breathe"),
    SCHOLAR("Scholar", "a careful scholar — historical and literary insight, still devotional in heart")
}

/**
 * How much of what the person has shared (about me, topics, life season, name, sermons, prayer list)
 * may shape their devotionals. Generic by default, personal by invitation (founder rule, 2026-10-09).
 */
enum class PersonalTouch(val label: String, val description: String) {
    OFF("Off", "Devotionals for anyone, a surprise each day"),
    NOW_AND_THEN("Now and then", "About once a week, drawing on what you've shared"),
    ALWAYS("Always", "Every devotional considers what you've shared");

    /**
     * Whether what the person shared may inform the devotional of [date]. NOW_AND_THEN picks one day
     * in each week from the date alone, and never the last day of a week, so two personal days are
     * never next to each other (and the same date always answers the same).
     */
    fun appliesOn(date: java.time.LocalDate?): Boolean = when (this) {
        OFF -> false
        ALWAYS -> true
        NOW_AND_THEN -> date != null && personalDay(date.toEpochDay())
    }

    /**
     * Whether topics and "more of" may gently weight the day's passage and quote: never when OFF,
     * on the personal day for NOW_AND_THEN, and about one day in four for ALWAYS.
     */
    fun weightsOn(date: java.time.LocalDate): Boolean = when (this) {
        OFF -> false
        NOW_AND_THEN -> personalDay(date.toEpochDay())
        ALWAYS -> Math.floorMod(date.toEpochDay() * 3 + 1, 4L) == 0L
    }

    companion object {
        private fun personalDay(epochDay: Long): Boolean {
            val week = Math.floorDiv(epochDay, 7L)
            val h = (week * 2654435761L) xor (week ushr 3)
            val offset = Math.floorMod(h, 6L)            // 0..5: the seventh day is never personal
            return Math.floorMod(epochDay, 7L) == offset
        }
    }
}

/** Topics a person can ask to hear more about. */
object DevotionalTopics {
    // Most days are ordinary, good days: the everyday and joyful come first, hard things after.
    val all = listOf(
        "Joy", "Gratitude", "Wonder", "Purpose", "Work & calling", "Creativity", "Friendship", "Family",
        "Generosity", "Courage", "Wisdom", "Prayer", "Love", "Faith", "Hope", "Peace", "Rest", "Patience",
        "Trust", "Identity", "Forgiveness", "Healing", "Anxiety", "Grief"
    )
    val seasons = listOf(
        "Life is good", "A busy season", "Ordinary days", "Celebrating", "Building something", "Learning something new",
        "Starting out", "New job", "Leadership", "Marriage", "New parent", "Parenting teens", "Singleness", "Moving",
        "Travelling", "Retirement", "Exams", "Waiting", "A fresh start", "Money worries", "Illness", "Grief and loss"
    )
}

/**
 * How someone wants their daily devotional (PLAN_V2 F2). Everything has a sensible default, so a
 * person who never opens the settings still gets a good devotional.
 */
data class DevotionalProfile(
    /** On by default: the written day and its notification at the set time are the promise (founder, 2026-10-11). */
    val enabled: Boolean = true,
    /** A plan by default: a new install has a devotional on day one, with no network and no key. */
    val source: DevotionalSource = DevotionalSource.CLASSIC,
    /** Which bundled plan [DevotionalSource.CLASSIC] reads from, and the fallback for the other sources. */
    val planId: String = DevotionalPlans.DEFAULT_ID,
    val tradition: Tradition = Tradition.NON_DENOMINATIONAL,
    /** Reading time: 3, 7 or 12 minutes. */
    val minutes: Int = 3,
    val tone: DevotionalTone = DevotionalTone.PASTOR,
    /** Used only when [personalTouch] is on, plus as gentle weights for the passage. */
    val topics: Set<String> = emptySet(),
    /** Used only when [personalTouch] is on. */
    val season: String? = null,
    /** A sentence or two the person writes about themselves. Used only when [personalTouch] is on. */
    val aboutMe: String = "",
    /** Whether the above (and name, sermons, prayer list) may shape devotionals. Off unless invited. */
    val personalTouch: PersonalTouch = PersonalTouch.OFF,
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
    val moreOf: Set<String> = emptySet(),
    /** How it's read aloud (PLAN_V2 F3). */
    val voice: com.craftflowtechnologies.meetingmind.ai.voice.VoiceSettings = com.craftflowtechnologies.meetingmind.ai.voice.VoiceSettings(),
    /** Paint a picture for each day's devotional with Gemini (Internet mode only) (PLAN_V2 F4). */
    val autoImage: Boolean = true,
    val imageStyle: String = "LANDSCAPE",
    /** Formats that take turns; with [rotateFormats] off, [fixedFormat] (or the first) every day. */
    val formats: Set<DevotionalFormat> = DevotionalFormat.DEFAULT_ROTATION,
    val rotateFormats: Boolean = true,
    val fixedFormat: DevotionalFormat? = null,
    val audience: DevotionalAudience = DevotionalAudience.ADULT,
    val readingLevel: ReadingLevel = ReadingLevel.STANDARD,
    /** A passage isn't used again within this many days (unless a series calls for it). */
    val passageExclusionDays: Int = 30,
    /** An evening Examen that looks back on the morning's devotional. */
    val eveningExamen: Boolean = true,
    /** The series being followed, if any. */
    val series: SeriesProgress? = null,
    /** The preset last applied, for display; everything it set stays editable. */
    val preset: TraditionPreset? = null,
    /** Written in this language (empty = the app's language). */
    val language: String = ""
) {
    val words: Int get() = when { minutes <= 3 -> 280; minutes <= 7 -> 650; else -> 1100 }

    /** The plan this profile reads from (the default plan when [planId] is unknown or unavailable). */
    val plan: DevotionalPlan get() = DevotionalPlans.resolve(planId)

    /** Whether the AI writes the devotional of [date] (the AI source, or a mix on every day but Sunday). */
    fun writesWithAiOn(date: java.time.LocalDate): Boolean =
        source == DevotionalSource.AI || source == DevotionalSource.MIX && date.dayOfWeek != java.time.DayOfWeek.SUNDAY

    /** What the person is reading, in a few words, for "Reading: …". */
    val readingName: String get() = when (source) {
        DevotionalSource.CLASSIC -> plan.title
        DevotionalSource.AI -> "Written with AI"
        DevotionalSource.MINE -> "Your own page"
        DevotionalSource.MIX -> "Written with AI, and ${plan.title}"
    }

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("source", source.name); put("planId", planId); put("tradition", tradition.name); put("minutes", minutes)
        put("tone", tone.name); put("topics", JSONArray(topics.toList())); season?.let { put("season", it) }
        put("aboutMe", aboutMe); put("prayer", includePrayer); put("motivation", includeMotivation)
        put("insight", includeInsight); put("question", includeQuestion); put("delivery", deliveryMinutes)
        put("sharePrivate", sharePrivateWithCloud); put("less", JSONArray(lessOf.toList())); put("more", JSONArray(moreOf.toList()))
        put("voiceStyle", voice.style.name); put("voiceGender", voice.gender.name); put("speakPrayer", voice.speakPrayer)
        put("autoVoice", voice.autoVoice); put("rate", voice.rate.toDouble())
        put("autoImage", autoImage); put("imageStyle", imageStyle)
        put("formats", JSONArray(formats.map { it.name })); put("rotate", rotateFormats); fixedFormat?.let { put("fixedFormat", it.name) }
        put("audience", audience.name); put("readingLevel", readingLevel.name); put("exclusionDays", passageExclusionDays)
        put("personalTouch", personalTouch.name); put("examen", eveningExamen); preset?.let { put("preset", it.name) }; put("language", language)
        series?.let { sp -> put("series", JSONObject().put("id", sp.seriesId).put("title", sp.title).put("passages", JSONArray(sp.passages)).put("start", sp.startedEpochDay)) }
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
                // A profile saved before plans existed has no planId: its "classic" was Morning and Evening.
                planId = o.optString("planId").takeIf { it.isNotBlank() } ?: DevotionalPlans.DEFAULT_ID,
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
                moreOf = set("more") ?: d.moreOf,
                voice = com.craftflowtechnologies.meetingmind.ai.voice.VoiceSettings(
                    style = runCatching { com.craftflowtechnologies.meetingmind.ai.voice.PreacherStyle.valueOf(o.getString("voiceStyle")) }.getOrDefault(d.voice.style),
                    gender = runCatching { com.craftflowtechnologies.meetingmind.ai.voice.VoiceGender.valueOf(o.getString("voiceGender")) }.getOrDefault(d.voice.gender),
                    speakPrayer = o.optBoolean("speakPrayer", d.voice.speakPrayer),
                    autoVoice = o.optBoolean("autoVoice", d.voice.autoVoice),
                    rate = o.optDouble("rate", d.voice.rate.toDouble()).toFloat().coerceIn(0.7f, 1.3f)
                ),
                autoImage = o.optBoolean("autoImage", d.autoImage),
                imageStyle = o.optString("imageStyle", d.imageStyle).ifBlank { d.imageStyle },
                formats = set("formats")?.mapNotNull { runCatching { DevotionalFormat.valueOf(it) }.getOrNull() }?.toSet()?.ifEmpty { null } ?: d.formats,
                rotateFormats = o.optBoolean("rotate", d.rotateFormats),
                fixedFormat = runCatching { DevotionalFormat.valueOf(o.getString("fixedFormat")) }.getOrNull(),
                audience = runCatching { DevotionalAudience.valueOf(o.getString("audience")) }.getOrDefault(d.audience),
                readingLevel = runCatching { ReadingLevel.valueOf(o.getString("readingLevel")) }.getOrDefault(d.readingLevel),
                passageExclusionDays = o.optInt("exclusionDays", d.passageExclusionDays).coerceIn(0, 365),
                // A profile saved before the setting existed has no field: that means Off.
                personalTouch = runCatching { PersonalTouch.valueOf(o.getString("personalTouch")) }.getOrDefault(PersonalTouch.OFF),
                eveningExamen = o.optBoolean("examen", d.eveningExamen),
                series = o.optJSONObject("series")?.let { so ->
                    val ps = so.optJSONArray("passages")
                    SeriesProgress(so.optString("id"), so.optString("title"), (0 until (ps?.length() ?: 0)).map { ps!!.getString(it) }, so.optLong("start"))
                }?.takeIf { it.passages.isNotEmpty() },
                preset = runCatching { TraditionPreset.valueOf(o.getString("preset")) }.getOrNull(),
                language = o.optString("language", d.language)
            )
        }
    }
}
