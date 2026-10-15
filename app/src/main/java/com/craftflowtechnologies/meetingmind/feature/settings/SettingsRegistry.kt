package com.craftflowtechnologies.meetingmind.feature.settings

/**
 * Every setting in the app, each in exactly one place (docs/mvp/ONE_PLACE.md section 5).
 *
 * This file is plain Kotlin on purpose: the screens render from it, search runs on it, and a unit test
 * checks it (8 groups, no duplicate keys, everything reachable by search). A new setting is one new
 * [SettingId] and one [SettingEntry] below, and the compiler then forces the screen to draw it.
 */
enum class SettingsGroup(val title: String, val summary: String) {
    ACCOUNT("Account", "You, what the app is for, notifications"),
    RECORDING("Recording", "Transcripts, speakers, automation"),
    AI("AI & offline models", "Models on this phone, Gemini key"),
    FAITH("Faith", "Bible, devotional, prayer reminders"),
    APPEARANCE("Appearance & home", "Colour, text size, home, companion"),
    PRIVACY("Privacy & security", "App lock, on-device only"),
    STORAGE("Storage & backup", "Back up, restore, clear data"),
    ABOUT("About", "Tour, sharing, diagnostics");

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: String?): SettingsGroup? = entries.firstOrNull { it.key == key }
    }
}

/** One value per setting. Never reuse or rename one: it is the key used by search and by jumps. */
enum class SettingId {
    // Account
    PROFILE, SPACES, NOTIFICATIONS, WORK, INTEGRATIONS,
    NOTIF_DEVOTIONAL, NOTIF_EXAMEN, NOTIF_PRAYER_TIMES, NOTIF_READING, NOTIF_EVENING_REFLECTION, NOTIF_MEETING_PREP,
    NOTIF_CIRCLES, NOTIF_WORK_MORNING, NOTIF_WORK_PREP, NOTIF_WORK_START, NOTIF_WORK_WEEKLY,
    NOTIF_RECORDING, NOTIF_DOWNLOADS,

    // Recording
    TIDY_FILLER, TRANSCRIPT_CLEANUP, SPEAKER_DETECTION, RECIPES,

    // AI & offline models
    PLAN_USAGE, SETUP_GUIDE, AI_ENGINE, GEMINI_KEY, WIFI_ONLY, GEMINI_CHECK, DEEPSEEK,

    // Faith
    BIBLE_TRANSLATION, BIBLE_OFFLINE, DEVOTIONAL, PRAYER_REMINDERS, ORIGINAL_PACKS, PERSONAL_TOUCH, FAITH_LOCK,

    // Appearance & home
    LOOK_AND_HOME, COMPANION, HERO_MODE, LOOK_FEEL, CALENDAR_ON_HOME,

    // Privacy & security
    APP_LOCK, ON_DEVICE_ONLY,

    // Storage & backup
    DATA_BACKUP, CLEAR_DATA,

    // About
    REPLAY_TOUR, SHARE_APP, CRASH_REPORT, DIAGNOSTIC_LOG, AUDIO_BENCHMARK
}

/** When a setting is drawn at all. Search skips an entry whose condition is false. */
/** FAITH_SPACE rows are not listed for someone without Faith, but search still finds them. */
enum class Needs { NOTHING, WORK_SPACE, FAITH_SPACE, FEATURE_RECIPES, CRASH_REPORT, DEBUG_BUILD }

/** Where an entry is drawn: in its group's list, or on the Notifications screen reached from Account. */
enum class Place { GROUP, NOTIFICATIONS }

data class SettingEntry(
    val id: SettingId,
    val group: SettingsGroup,
    val title: String,
    /** Extra words a person might type: "dark mode" finds Look and home. */
    val keywords: String = "",
    /** Under "Advanced" at the bottom of the group, not among its most-used rows. */
    val advanced: Boolean = false,
    val needs: Needs = Needs.NOTHING,
    val place: Place = Place.GROUP
) {
    val key: String get() = id.name
}

object SettingsRegistry {
    val groups: List<SettingsGroup> = SettingsGroup.entries

    private fun e(
        id: SettingId, group: SettingsGroup, title: String, keywords: String = "", advanced: Boolean = false,
        needs: Needs = Needs.NOTHING, place: Place = Place.GROUP
    ) = SettingEntry(id, group, title, keywords, advanced, needs, place)

    private fun n(id: SettingId, title: String, keywords: String, needs: Needs = Needs.NOTHING) =
        e(id, SettingsGroup.ACCOUNT, title, keywords, needs = needs, place = Place.NOTIFICATIONS)

    val entries: List<SettingEntry> = listOf(
        // Account
        e(SettingId.PROFILE, SettingsGroup.ACCOUNT, "Your name and photo", "profile avatar picture name display"),
        e(SettingId.SPACES, SettingsGroup.ACCOUNT, "What it's for", "spaces work faith study personal everyday identity"),
        e(SettingId.NOTIFICATIONS, SettingsGroup.ACCOUNT, "Notifications", "alerts reminders push nudges sounds"),
        e(SettingId.WORK, SettingsGroup.ACCOUNT, "Work", "profile terms organisation project rhythm hours sign off", needs = Needs.WORK_SPACE),
        e(SettingId.INTEGRATIONS, SettingsGroup.ACCOUNT, "Integrations", "calendar storage email sharing google drive connect", advanced = true),
        n(SettingId.NOTIF_DEVOTIONAL, "Daily devotional", "morning devotional notification arrives", Needs.FAITH_SPACE),
        n(SettingId.NOTIF_EXAMEN, "Evening Examen", "examen evening devotional night", Needs.FAITH_SPACE),
        n(SettingId.NOTIF_PRAYER_TIMES, "Prayer times", "daily office prayer hours lauds vespers compline reminders", Needs.FAITH_SPACE),
        n(SettingId.NOTIF_READING, "Today's reading", "reading nudge bible plan 7 pm", Needs.FAITH_SPACE),
        n(SettingId.NOTIF_EVENING_REFLECTION, "Evening reflection", "reflect day 9 pm", Needs.FAITH_SPACE),
        n(SettingId.NOTIF_MEETING_PREP, "Before meetings", "meeting prayer calendar 15 minutes", Needs.FAITH_SPACE),
        n(SettingId.NOTIF_CIRCLES, "Circles", "circles push messages group fellowship"),
        n(SettingId.NOTIF_WORK_MORNING, "Work morning briefing", "work morning briefing day ahead", Needs.WORK_SPACE),
        n(SettingId.NOTIF_WORK_PREP, "Work meeting prep", "work prep before meeting lead", Needs.WORK_SPACE),
        n(SettingId.NOTIF_WORK_START, "Work: starting now, record?", "work record start meeting now", Needs.WORK_SPACE),
        n(SettingId.NOTIF_WORK_WEEKLY, "Work weekly review", "work weekly review friday", Needs.WORK_SPACE),
        n(SettingId.NOTIF_RECORDING, "Recording and processing", "transcription progress recording finished processing channel system"),
        n(SettingId.NOTIF_DOWNLOADS, "Model downloads", "download progress models channel system"),

        // Recording
        e(SettingId.TIDY_FILLER, SettingsGroup.RECORDING, "Tidy up filler words", "uh um hesitation transcript clean verbatim"),
        e(SettingId.TRANSCRIPT_CLEANUP, SettingsGroup.RECORDING, "Transcript cleanup", "conservative moderate aggressive polish wording"),
        e(SettingId.SPEAKER_DETECTION, SettingsGroup.RECORDING, "Speaker detection", "diarization who spoke automatic deterministic ai assisted"),
        e(SettingId.RECIPES, SettingsGroup.RECORDING, "Recipes & automation", "post meeting actions tasks review triggers", advanced = true, needs = Needs.FEATURE_RECIPES),

        // AI & offline models
        e(SettingId.PLAN_USAGE, SettingsGroup.AI, "Plan and usage", "plan subscription paywall usage gemini monthly billing upgrade"),
        e(SettingId.SETUP_GUIDE, SettingsGroup.AI, "Setup guide", "get started transcribe summarise permissions what the app needs"),
        e(SettingId.AI_ENGINE, SettingsGroup.AI, "AI Engine", "on-device models download pause resume remove offline whisper parakeet qwen"),
        e(SettingId.GEMINI_KEY, SettingsGroup.AI, "Gemini API key", "google internet mode cloud key api"),
        e(SettingId.WIFI_ONLY, SettingsGroup.AI, "Download models over Wi-Fi only", "wifi mobile data cellular downloads"),
        e(SettingId.GEMINI_CHECK, SettingsGroup.AI, "Check Gemini key", "test key transcription writing live voice", advanced = true),
        e(SettingId.DEEPSEEK, SettingsGroup.AI, "Backup AI (DeepSeek)", "deepseek fallback writer tokens key usage", advanced = true),

        // Faith
        e(SettingId.BIBLE_TRANSLATION, SettingsGroup.FAITH, "Bible translation", "version translation bsb kjv web open verses"),
        e(SettingId.BIBLE_OFFLINE, SettingsGroup.FAITH, "Bible on this phone", "offline copies download read search without internet"),
        e(SettingId.DEVOTIONAL, SettingsGroup.FAITH, "Devotional time & style", "devotional morning time tradition format length tone voice series language picture read aloud"),
        e(SettingId.PRAYER_REMINDERS, SettingsGroup.FAITH, "Prayer reminders", "prayer times daily office reading evening reflection quiet hours"),
        e(SettingId.ORIGINAL_PACKS, SettingsGroup.FAITH, "Original-language packs", "hebrew greek lexicon interlinear strongs original words", advanced = true),
        e(SettingId.PERSONAL_TOUCH, SettingsGroup.FAITH, "Personal touch", "devotional personal about me topics season prayer list shape", advanced = true),
        e(SettingId.FAITH_LOCK, SettingsGroup.FAITH, "Lock Faith notes", "fingerprint face biometric prayers private journal", advanced = true),

        // Appearance & home
        e(SettingId.LOOK_AND_HOME, SettingsGroup.APPEARANCE, "Look and home", "theme dark light mode system colour accent text size font home today focus everyday style sections"),
        e(SettingId.COMPANION, SettingsGroup.APPEARANCE, "Companion", "mimi zuri character presence name"),
        e(SettingId.HERO_MODE, SettingsGroup.APPEARANCE, "Home hero card", "daily scripture quote stats streak sky card banner", advanced = true),
        e(SettingId.LOOK_FEEL, SettingsGroup.APPEARANCE, "Look & feel", "professional sanctuary minimal cards classic vivid", advanced = true),
        e(SettingId.CALENDAR_ON_HOME, SettingsGroup.APPEARANCE, "Upcoming events on Home", "calendar events meetings services phone calendar permission", advanced = true),

        // Privacy & security
        e(SettingId.APP_LOCK, SettingsGroup.PRIVACY, "App lock", "fingerprint face biometric pin screen lock secure"),
        e(SettingId.ON_DEVICE_ONLY, SettingsGroup.PRIVACY, "On-device only", "offline internet mode cloud upload google privacy local"),

        // Storage & backup
        e(SettingId.DATA_BACKUP, SettingsGroup.STORAGE, "Data & backup", "backup restore export markdown trash deleted recordings"),
        e(SettingId.CLEAR_DATA, SettingsGroup.STORAGE, "Delete all meetings & audio", "clear erase wipe reset data storage space", advanced = true),

        // About
        e(SettingId.REPLAY_TOUR, SettingsGroup.ABOUT, "Show me around again", "tour tutorial intro welcome help"),
        e(SettingId.SHARE_APP, SettingsGroup.ABOUT, "Share MeetingMind", "invite friends tell"),
        e(SettingId.CRASH_REPORT, SettingsGroup.ABOUT, "Last crash report", "crash error closed unexpectedly", advanced = true, needs = Needs.CRASH_REPORT),
        e(SettingId.DIAGNOSTIC_LOG, SettingsGroup.ABOUT, "Gemini log", "diagnostics log requests errors support copy share", advanced = true),
        e(SettingId.AUDIO_BENCHMARK, SettingsGroup.ABOUT, "Audio benchmark (developer)", "benchmark sound detector", advanced = true, needs = Needs.DEBUG_BUILD)
    )

    private val byId: Map<SettingId, SettingEntry> = entries.associateBy { it.id }

    fun entry(id: SettingId): SettingEntry = byId.getValue(id)

    /** A group's rows for its own screen: most-used first, then Advanced. Notification rows live on their own screen. */
    fun inGroup(group: SettingsGroup): List<SettingEntry> =
        entries.filter { it.group == group && it.place == Place.GROUP }

    fun notifications(): List<SettingEntry> = entries.filter { it.place == Place.NOTIFICATIONS }

    /** The group screen that shows [entry]; a notification toggle is in Account (via Notifications). */
    fun screenGroup(entry: SettingEntry): SettingsGroup = entry.group

    /**
     * Settings matching every word of [query] in the title, the keywords or the group name. [available] drops
     * entries that don't exist for this person right now (Work without the Work space, debug-only rows).
     * Faith is not "unavailable" for people without it: it stays findable here.
     */
    fun search(query: String, available: (SettingEntry) -> Boolean = { true }): List<SettingEntry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { available(it) }.filter { entry ->
            val haystack = (entry.title + " " + entry.keywords + " " + entry.group.title).lowercase()
            words.all { it in haystack }
        }
    }
}
