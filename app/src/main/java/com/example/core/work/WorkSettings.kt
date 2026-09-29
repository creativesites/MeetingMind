package com.example.core.work

import com.example.core.model.RecordingType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * What kind of work someone does (PLAN_PROFESSIONAL.md §8.1). A profile is configuration only:
 * the words used, which workflows and outputs come first, what Home shows, and privacy defaults.
 * No screen branches on it.
 */
enum class WorkProfile(val label: String, val description: String) {
    CLIENT_WORK("Client work", "Consultants, agencies, freelancers"),
    FOUNDER("Founder", "Building a company"),
    SALES("Sales", "Accounts, deals and follow-ups"),
    MANAGEMENT("Management", "Teams, 1:1s and updates"),
    CLINICAL("Clinical", "Doctors, therapists, health professionals"),
    LEGAL("Legal", "Lawyers and legal teams"),
    RESEARCH("Research & journalism", "Interviews, sources and stories"),
    RECRUITING("Recruiting", "Candidates and roles"),
    PRODUCT("Product & engineering", "Standups, specs and decisions"),
    GENERAL("General", "A bit of everything");

    /** The words this profile uses by default (§8.3). */
    val terms: Terms get() = when (this) {
        CLIENT_WORK -> Terms("Client", "Project", "Contact")
        FOUNDER -> Terms("Company", "Project", "Contact")
        SALES -> Terms("Account", "Deal", "Contact")
        MANAGEMENT -> Terms("Team", "Project", "Colleague")
        CLINICAL -> Terms("Patient", "Case", "Patient")
        LEGAL -> Terms("Client", "Matter", "Client")
        RESEARCH -> Terms("Source", "Story", "Source")
        RECRUITING -> Terms("Candidate", "Role", "Candidate")
        PRODUCT -> Terms("Team", "Project", "Stakeholder")
        GENERAL -> Terms("Organisation", "Project", "Contact")
    }

    /** Record types offered first. */
    val recordTypes: List<RecordingType> get() = when (this) {
        CLIENT_WORK -> listOf(RecordingType.CLIENT_CALL, RecordingType.MEETING, RecordingType.ONE_ON_ONE)
        FOUNDER -> listOf(RecordingType.MEETING, RecordingType.ONE_ON_ONE, RecordingType.INTERVIEW)
        SALES -> listOf(RecordingType.CLIENT_CALL, RecordingType.MEETING)
        MANAGEMENT -> listOf(RecordingType.ONE_ON_ONE, RecordingType.STANDUP, RecordingType.MEETING)
        CLINICAL -> listOf(RecordingType.CONSULTATION, RecordingType.MEETING)
        LEGAL -> listOf(RecordingType.CONSULTATION, RecordingType.MEETING, RecordingType.INTERVIEW)
        RESEARCH -> listOf(RecordingType.INTERVIEW, RecordingType.RESEARCH)
        RECRUITING -> listOf(RecordingType.INTERVIEW, RecordingType.MEETING)
        PRODUCT -> listOf(RecordingType.STANDUP, RecordingType.MEETING, RecordingType.BRAINSTORM)
        GENERAL -> listOf(RecordingType.MEETING, RecordingType.CONVERSATION)
    }

    /** Clinical and legal work is confidential by default and never leaves the phone (§8.1). */
    val sensitive: Boolean get() = this == CLINICAL || this == LEGAL
}

data class Terms(val organisation: String, val project: String, val person: String) {
    fun plural(word: String): String = when {
        word.endsWith("y", ignoreCase = true) && word.length > 1 && word[word.length - 2].lowercaseChar() !in "aeiou" -> word.dropLast(1) + "ies"
        word.endsWith("s", ignoreCase = true) || word.endsWith("x", ignoreCase = true) || word.endsWith("ch", ignoreCase = true) -> word + "es"
        else -> word + "s"
    }
    val organisations get() = plural(organisation)
    val projects get() = plural(project)
    val people get() = plural(person)
}

enum class Tone(val label: String) { FORMAL("Formal"), FRIENDLY("Friendly"), BRIEF("Brief") }

enum class GreetingStyle(val label: String) { PLAYFUL("Playful"), PLAIN("Plain"), OFF("Off") }

/** The sections of the Work Home, in the person's order (§7). */
enum class WorkSection(val label: String) {
    TO_REVIEW("To review"),
    FOLLOW_UPS("Follow-ups to send"),
    MY_TASKS("My tasks"),
    WAITING_ON("Waiting on"),
    OPEN_QUESTIONS("Open questions"),
    PEOPLE("People")
}

/** The fourth tab in the bottom bar (§7.4). */
enum class TabSlot(val label: String) { SEARCH("Search"), WORK("Work") }

data class WorkSettings(
    val profile: WorkProfile = WorkProfile.CLIENT_WORK,
    /** Words the person typed; null means the profile's own. */
    val termsOverride: Terms? = null,
    val sections: List<WorkSection> = WorkSection.entries,
    val hiddenSections: Set<WorkSection> = emptySet(),
    val tabSlot: TabSlot = TabSlot.SEARCH,
    val greeting: GreetingStyle = GreetingStyle.PLAYFUL,
    // Rhythm (§8.4)
    val workDays: Set<Int> = setOf(2, 3, 4, 5, 6),
    val workStartMinute: Int = 8 * 60 + 30,
    val workEndMinute: Int = 17 * 60 + 30,
    val prepLeadMinutes: Int = 15,
    val weeklyReviewDay: Int = 6,
    val quietDays: Int = 21,
    // Outputs (§8.5)
    val tone: Tone = Tone.FRIENDLY,
    val signOff: String = "Thanks",
    val signature: String = "",
    /** Null = adaptive: chosen per person and region (§6.5). */
    val defaultChannel: Channel? = null,
    val footer: Boolean = true,
    // Privacy (§8.6)
    /** Work recordings are processed on the phone even when Internet mode is on. */
    val onDeviceOnly: Boolean = false,
    val consentReminder: Boolean = true
) {
    val terms: Terms get() = termsOverride ?: profile.terms

    val visibleSections: List<WorkSection> get() = sections.filter { it !in hiddenSections }

    /** Whether new work stays on the phone: the setting, or a sensitive profile. */
    val keepOnDevice: Boolean get() = onDeviceOnly || profile.sensitive

    /** Whether shared output carries "Notes by MeetingMind". Never for clinical or legal work. */
    val showFooter: Boolean get() = footer && !profile.sensitive

    fun toJson(): String = JSONObject().apply {
        put("profile", profile.name)
        termsOverride?.let { put("terms", JSONObject().put("org", it.organisation).put("project", it.project).put("person", it.person)) }
        put("sections", JSONArray(sections.map { it.name }))
        put("hidden", JSONArray(hiddenSections.map { it.name }))
        put("tab", tabSlot.name)
        put("greeting", greeting.name)
        put("days", JSONArray(workDays.toList()))
        put("start", workStartMinute); put("end", workEndMinute)
        put("prep", prepLeadMinutes); put("review", weeklyReviewDay); put("quiet", quietDays)
        put("tone", tone.name); put("signOff", signOff); put("signature", signature)
        defaultChannel?.let { put("channel", it.name) }
        put("footer", footer); put("onDevice", onDeviceOnly); put("consent", consentReminder)
    }.toString()

    companion object {
        /** A new profile's defaults, keeping nothing the person set explicitly is our job, not here. */
        fun forProfile(profile: WorkProfile) = WorkSettings(
            profile = profile,
            sections = when (profile) {
                WorkProfile.MANAGEMENT, WorkProfile.PRODUCT -> listOf(WorkSection.MY_TASKS, WorkSection.WAITING_ON, WorkSection.TO_REVIEW, WorkSection.FOLLOW_UPS, WorkSection.OPEN_QUESTIONS, WorkSection.PEOPLE)
                WorkProfile.CLINICAL, WorkProfile.LEGAL, WorkProfile.RESEARCH, WorkProfile.RECRUITING -> listOf(WorkSection.TO_REVIEW, WorkSection.MY_TASKS, WorkSection.FOLLOW_UPS, WorkSection.WAITING_ON, WorkSection.OPEN_QUESTIONS, WorkSection.PEOPLE)
                else -> WorkSection.entries
            },
            footer = !profile.sensitive,
            onDeviceOnly = profile.sensitive,
            tone = if (profile.sensitive) Tone.FORMAL else Tone.FRIENDLY
        )

        fun fromJson(raw: String?): WorkSettings {
            if (raw.isNullOrBlank()) return WorkSettings()
            return runCatching {
                val o = JSONObject(raw)
                fun <E : Enum<E>> list(key: String, parse: (String) -> E?): List<E> =
                    o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { parse(a.getString(it)) } }.orEmpty()
                val sections = list("sections") { n -> WorkSection.entries.firstOrNull { it.name == n } }
                WorkSettings(
                    profile = enumOr(o.optString("profile"), WorkProfile.CLIENT_WORK),
                    termsOverride = o.optJSONObject("terms")?.let { Terms(it.optString("org"), it.optString("project"), it.optString("person")) },
                    // Sections added in a later version appear at the end rather than vanishing.
                    sections = (sections + WorkSection.entries.filter { it !in sections }),
                    hiddenSections = list("hidden") { n -> WorkSection.entries.firstOrNull { it.name == n } }.toSet(),
                    tabSlot = enumOr(o.optString("tab"), TabSlot.SEARCH),
                    greeting = enumOr(o.optString("greeting"), GreetingStyle.PLAYFUL),
                    workDays = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: setOf(2, 3, 4, 5, 6),
                    workStartMinute = o.optInt("start", 8 * 60 + 30),
                    workEndMinute = o.optInt("end", 17 * 60 + 30),
                    prepLeadMinutes = o.optInt("prep", 15),
                    weeklyReviewDay = o.optInt("review", 6),
                    quietDays = o.optInt("quiet", 21),
                    tone = enumOr(o.optString("tone"), Tone.FRIENDLY),
                    signOff = o.optString("signOff", "Thanks"),
                    signature = o.optString("signature", ""),
                    defaultChannel = o.optString("channel").takeIf { it.isNotEmpty() }?.let { enumOr(it, Channel.SHARE) },
                    footer = o.optBoolean("footer", true),
                    onDeviceOnly = o.optBoolean("onDevice", false),
                    consentReminder = o.optBoolean("consent", true)
                )
            }.getOrDefault(WorkSettings())
        }

        /**
         * Where WhatsApp is the ordinary business channel, it's offered first (§6.5). By region,
         * from the phone's locale, only when nothing better is known about the person.
         */
        private val WHATSAPP_FIRST = setOf(
            // Africa
            "ZW", "ZA", "ZM", "MW", "MZ", "BW", "NA", "KE", "UG", "TZ", "RW", "NG", "GH", "CM", "CI", "SN", "ET", "EG", "MA", "DZ", "TN", "AO", "CD", "SL", "LR", "LS", "SZ",
            // Latin America
            "BR", "MX", "AR", "CO", "PE", "CL", "VE", "EC", "BO", "PY", "UY", "GT", "HN", "SV", "NI", "CR", "PA", "DO",
            // South and Southeast Asia, the Middle East
            "IN", "PK", "BD", "LK", "NP", "ID", "MY", "SG", "PH", "AE", "SA", "QA", "KW", "BH", "OM", "JO", "LB", "IL", "TR"
        )

        fun whatsappFirstRegion(locale: Locale = Locale.getDefault()): Boolean = locale.country.uppercase(Locale.ROOT) in WHATSAPP_FIRST
    }
}
