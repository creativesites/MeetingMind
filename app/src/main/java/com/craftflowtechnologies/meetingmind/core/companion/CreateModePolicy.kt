package com.craftflowtechnologies.meetingmind.core.companion

/** Where a Create card's text came from (FAITH_V2 §3). */
enum class CreateSource {
    PRAYER_REQUEST, SCRIPTURE_VERSE, SERMON_QUOTE, DEVOTIONAL, ANSWERED_PRAYER, TESTIMONY, ACHIEVEMENT, STUDY_WIN, CUSTOM
}

/** The card's vibe (FAITH_V2 §3). */
enum class CreateVibe { ENCOURAGING, LOVE, MOTIVATIONAL, FUNNY, WISDOM, REFLECTIVE, CELEBRATION, CUSTOM }

/**
 * Which companion poses a Create card may use, and which one to suggest (§3.2). Restrictions are
 * enforced here in code, never by a model. The source wins over the vibe. The suggestion only
 * picks a pose; it never writes or changes text.
 */
object CreateModePolicy {

    private val All: Set<CreateMode> = CreateMode.entries.toSet()
    private val Sacred: Set<CreateMode> =
        setOf(CreateMode.PRAYERFUL, CreateMode.PEACEFUL, CreateMode.GRATEFUL, CreateMode.REFLECTIVE)

    /**
     * @param grief the source note is tagged grief/funeral/memorial: Peaceful only.
     * @param goodFriday nothing celebrates on Good Friday.
     */
    fun allowed(source: CreateSource, vibe: CreateVibe? = null, grief: Boolean = false, goodFriday: Boolean = false): Set<CreateMode> {
        if (grief) return setOf(CreateMode.PEACEFUL)
        val bySource = when (source) {
            CreateSource.PRAYER_REQUEST -> setOf(CreateMode.PRAYERFUL, CreateMode.PEACEFUL)
            CreateSource.SCRIPTURE_VERSE, CreateSource.SERMON_QUOTE, CreateSource.DEVOTIONAL -> Sacred
            else -> All
        }
        return if (goodFriday) bySource - CreateMode.JOYFUL - CreateMode.CELEBRATORY else bySource
    }

    /** The suggested pose, always one of [allowed]. */
    fun suggest(source: CreateSource, vibe: CreateVibe? = null, grief: Boolean = false, goodFriday: Boolean = false): CreateMode {
        val allowed = allowed(source, vibe, grief, goodFriday)
        val wanted = when (source) {
            CreateSource.PRAYER_REQUEST -> CreateMode.PRAYERFUL
            CreateSource.SCRIPTURE_VERSE, CreateSource.SERMON_QUOTE, CreateSource.DEVOTIONAL ->
                if (vibe == CreateVibe.REFLECTIVE) CreateMode.REFLECTIVE else CreateMode.PEACEFUL
            CreateSource.ANSWERED_PRAYER, CreateSource.TESTIMONY -> CreateMode.GRATEFUL
            CreateSource.ACHIEVEMENT, CreateSource.STUDY_WIN -> CreateMode.CELEBRATORY
            CreateSource.CUSTOM -> when (vibe) {
                CreateVibe.ENCOURAGING, CreateVibe.LOVE -> CreateMode.GRATEFUL
                CreateVibe.MOTIVATIONAL, CreateVibe.FUNNY -> CreateMode.JOYFUL
                CreateVibe.WISDOM, CreateVibe.REFLECTIVE -> CreateMode.REFLECTIVE
                CreateVibe.CELEBRATION -> CreateMode.CELEBRATORY
                CreateVibe.CUSTOM, null -> CreateMode.PEACEFUL
            }
        }
        return if (wanted in allowed) wanted else if (CreateMode.PEACEFUL in allowed) CreateMode.PEACEFUL else allowed.first()
    }

    /**
     * Whether "Include {companion}" starts switched on. Off by default on every card; the Advanced
     * "Suggest {companion} on cards" setting pre-enables it for achievements and study wins only.
     */
    fun includeByDefault(source: CreateSource, createSuggest: Boolean): Boolean =
        createSuggest && (source == CreateSource.ACHIEVEMENT || source == CreateSource.STUDY_WIN)
}
