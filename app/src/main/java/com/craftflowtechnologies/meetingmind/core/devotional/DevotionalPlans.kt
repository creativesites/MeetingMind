package com.craftflowtechnologies.meetingmind.core.devotional

/** Whether a plan's text is in the app. */
enum class PlanStatus { BUNDLED, UNAVAILABLE }

/**
 * A reading plan: a devotional book with a reading for every day of the year, bundled in the app so it
 * works with no network, no key and no model. Each plan names where its text came from and why it may
 * be shipped; a plan whose text could not be verified stays in the catalogue as [PlanStatus.UNAVAILABLE]
 * (never shown to the person, never bundled) with the reason on record.
 */
data class DevotionalPlan(
    val id: String,
    val title: String,
    val author: String,
    /** The year it was first published, when verified; null when the source doesn't say. */
    val year: Int?,
    /** One line for the picker. */
    val blurb: String,
    /** Shown under every reading from the plan. */
    val attribution: String,
    /** Where the bundled text was fetched from. */
    val sourceUrl: String,
    val licence: String,
    /** Path under assets/, or null while the plan is unavailable. */
    val asset: String?,
    /** Whether the plan has a separate evening reading each day. */
    val hasEvening: Boolean,
    val status: PlanStatus,
    /** For an unavailable plan, why it isn't bundled. */
    val unavailableReason: String? = null
) {
    val available get() = status == PlanStatus.BUNDLED && asset != null
}

object DevotionalPlans {
    const val DEFAULT_ID = "morning-and-evening"
    const val FAITHS_CHECKBOOK = "faiths-checkbook"
    const val DAILY_LIGHT = "daily-light"
    const val STREAMS_IN_THE_DESERT = "streams-in-the-desert"

    private val morningAndEvening = DevotionalPlan(
        id = DEFAULT_ID, title = "Morning and Evening", author = "C. H. Spurgeon", year = 1865,
        blurb = "A reading for every morning and every evening of the year.",
        attribution = "From Morning and Evening by C. H. Spurgeon (1865), public domain.",
        sourceUrl = "https://ccel.org/ccel/spurgeon/morneve",
        licence = "Public domain (author died 1892). Text from the Christian Classics Ethereal Library.",
        asset = "devotionals/spurgeon_morning_evening.json.gz", hasEvening = true, status = PlanStatus.BUNDLED
    )

    private val faithsCheckbook = DevotionalPlan(
        id = FAITHS_CHECKBOOK, title = "Faith's Checkbook", author = "C. H. Spurgeon", year = null,
        blurb = "A promise from Scripture for each day, with a short word on taking God at his word.",
        attribution = "From Faith's Checkbook by C. H. Spurgeon, public domain.",
        sourceUrl = "https://ccel.org/ccel/spurgeon/checkbook",
        licence = "Public domain (author died 1892). Text from the Christian Classics Ethereal Library (ThML source, https://ccel.org/ccel/s/spurgeon/checkbook.xml).",
        asset = "devotionals/spurgeon_faiths_checkbook.json.gz", hasEvening = false, status = PlanStatus.BUNDLED
    )

    private val dailyLight = DevotionalPlan(
        id = DAILY_LIGHT, title = "Daily Light on the Daily Path", author = "Samuel Bagster", year = null,
        blurb = "Scripture only: a morning and an evening set of verses for each day.",
        attribution = "From Daily Light on the Daily Path (Bagster), public domain.",
        sourceUrl = "https://archive.org/details/dailylightondail00lond",
        licence = "Public domain (S. Bagster & Sons, 1901 edition), but no clean text was available.",
        asset = null, hasEvening = true, status = PlanStatus.UNAVAILABLE,
        unavailableReason = "Only a machine-scanned (OCR) copy was reachable: ccel.org and Project Gutenberg do not carry it. The scan mangles " +
            "words in the verses (drop capitals, broken words, garbled references), and scripture can't ship with unverified wording."
    )

    private val streamsInTheDesert = DevotionalPlan(
        id = STREAMS_IN_THE_DESERT, title = "Streams in the Desert", author = "L. B. Cowman", year = 1925,
        blurb = "A daily reading of encouragement for hard places.",
        attribution = "From Streams in the Desert by Mrs. Charles E. Cowman (1925).",
        sourceUrl = "https://archive.org/details/streamsindesert0000mrsc",
        licence = "Public domain in the US (published 1925); the author died in 1960, so it is still in copyright in life+70 countries until 2030.",
        asset = null, hasEvening = false, status = PlanStatus.UNAVAILABLE,
        unavailableReason = "Not bundled. Status outside the US is unclear (author died 1960), and the only reachable text is an OCR scan " +
            "with day headings missing, so the 366 readings can't be split reliably."
    )

    /** Every plan the app knows, bundled or not, in the order they are offered. */
    val all: List<DevotionalPlan> = listOf(morningAndEvening, faithsCheckbook, dailyLight, streamsInTheDesert)

    /** What a person can choose: only plans whose text is in the app. */
    val available: List<DevotionalPlan> get() = all.filter { it.available }

    val default: DevotionalPlan get() = morningAndEvening

    /** The plan with [id] if it is bundled; anything else (unknown, retired, unavailable) is the default plan. */
    fun resolve(id: String?): DevotionalPlan = available.firstOrNull { it.id == id } ?: default

    fun byId(id: String?): DevotionalPlan? = all.firstOrNull { it.id == id }
}
