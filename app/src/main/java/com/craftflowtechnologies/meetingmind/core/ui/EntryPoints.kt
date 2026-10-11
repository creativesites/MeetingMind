package com.craftflowtechnologies.meetingmind.core.ui

/** What an entry point leads to (docs/mvp/ONE_PLACE.md §1). */
enum class EntryKind {
    /** A real feature: reading, praying, a guided session, circles, recording. */
    EXPERIENCE,

    /** Only creates a note. Its label must start with "Write" and it must be visually quiet. */
    WRITE,

    /** Opens a sheet or picker. */
    SHEET
}

/** How loudly an entry is drawn. HERO and PROMINENT are "prominent"; QUIET is a small tile, row, chip or text action. */
enum class EntryStyle { HERO, PROMINENT, QUIET }

enum class EntryScreen { FAITH_HOME, TODAY_QUICK_ROW, EVERYDAY_HOME, BIBLE_HOME }

data class EntryPoint(val id: String, val screen: EntryScreen, val label: String, val kind: EntryKind, val style: EntryStyle) {
    val prominent: Boolean get() = style != EntryStyle.QUIET
}

/**
 * Every entry the four main surfaces offer, with its label, destination kind and style. The screens
 * read their labels from here, and `EntryPointContractTest` fails when a WRITE entry is prominent or
 * doesn't say "Write …", so a big button can't quietly open a blank template again.
 */
object EntryPoints {
    private fun e(id: String, screen: EntryScreen, label: String, kind: EntryKind, style: EntryStyle) = EntryPoint(id, screen, label, kind, style)

    // ---- Faith home: Bible, devotional, Pray with me, Circles lead; writing is small and quiet.
    val faithBible = e("faith.bible", EntryScreen.FAITH_HOME, "Bible", EntryKind.EXPERIENCE, EntryStyle.HERO)
    val faithContinue = e("faith.continue", EntryScreen.FAITH_HOME, "Continue", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val faithStartReading = e("faith.start_reading", EntryScreen.FAITH_HOME, "Start reading", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val faithGuideMe = e("faith.guide_me", EntryScreen.FAITH_HOME, "Guide me", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val faithJustRead = e("faith.just_read", EntryScreen.FAITH_HOME, "Just read", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val faithDevotional = e("faith.devotional", EntryScreen.FAITH_HOME, "Today's devotional", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val faithPray = e("faith.pray", EntryScreen.FAITH_HOME, "Pray with me", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val faithCircles = e("faith.circles", EntryScreen.FAITH_HOME, "Circles", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val faithVerseWrite = e("faith.votd.write", EntryScreen.FAITH_HOME, "Write on it", EntryKind.WRITE, EntryStyle.QUIET)
    val faithVerseShare = e("faith.votd.share", EntryScreen.FAITH_HOME, "Share", EntryKind.SHEET, EntryStyle.QUIET)
    val faithTestimonies = e("faith.testimonies", EntryScreen.FAITH_HOME, "Testimonies", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val faithRecordSermon = e("faith.record_sermon", EntryScreen.FAITH_HOME, "Record a sermon", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val faithWritePrayer = e("faith.write.prayer", EntryScreen.FAITH_HOME, "Write a prayer", EntryKind.WRITE, EntryStyle.QUIET)
    val faithWritePrayerRequest = e("faith.write.prayer_request", EntryScreen.FAITH_HOME, "Write a prayer request", EntryKind.WRITE, EntryStyle.QUIET)
    val faithWriteBlessing = e("faith.write.blessing", EntryScreen.FAITH_HOME, "Write down a blessing", EntryKind.WRITE, EntryStyle.QUIET)
    val faithWriteTestimony = e("faith.write.testimony", EntryScreen.FAITH_HOME, "Write a testimony", EntryKind.WRITE, EntryStyle.QUIET)
    val faithWriteReflection = e("faith.write.reflection", EntryScreen.FAITH_HOME, "Write a reflection", EntryKind.WRITE, EntryStyle.QUIET)
    val faithWriteDevotional = e("faith.write.devotional", EntryScreen.FAITH_HOME, "Write a devotional", EntryKind.WRITE, EntryStyle.QUIET)

    /** The six small writing tiles under the quiet "Write" heading, in display order. */
    val faithWriteTiles = listOf(
        faithWritePrayer, faithWritePrayerRequest, faithWriteBlessing,
        faithWriteTestimony, faithWriteReflection, faithWriteDevotional
    )

    // ---- Today / work home quick row: small round shortcuts, all quiet.
    val quickFaith = e("quick.faith", EntryScreen.TODAY_QUICK_ROW, "Faith", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val quickCreate = e("quick.create", EntryScreen.TODAY_QUICK_ROW, "Create", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val quickWork = e("quick.work", EntryScreen.TODAY_QUICK_ROW, "Work", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val quickPrayer = e("quick.prayer", EntryScreen.TODAY_QUICK_ROW, "Prayer", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val quickDevotional = e("quick.devotional", EntryScreen.TODAY_QUICK_ROW, "Devotional", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val quickTestimonies = e("quick.testimonies", EntryScreen.TODAY_QUICK_ROW, "Testimonies", EntryKind.EXPERIENCE, EntryStyle.QUIET)

    // ---- Everyday home.
    val everydayRecord = e("everyday.record", EntryScreen.EVERYDAY_HOME, "Record", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val everydayOpen = e("everyday.open", EntryScreen.EVERYDAY_HOME, "Open", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val everydayPrepare = e("everyday.prepare", EntryScreen.EVERYDAY_HOME, "Prepare", EntryKind.SHEET, EntryStyle.QUIET)

    // ---- Bible home.
    val bibleContinue = e("bible.continue", EntryScreen.BIBLE_HOME, "Continue", EntryKind.EXPERIENCE, EntryStyle.HERO)
    val bibleStartReading = e("bible.start_reading", EntryScreen.BIBLE_HOME, "Start reading", EntryKind.EXPERIENCE, EntryStyle.HERO)
    val bibleGuideMe = e("bible.guide_me", EntryScreen.BIBLE_HOME, "Guide me", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)
    val bibleJustRead = e("bible.just_read", EntryScreen.BIBLE_HOME, "Just read", EntryKind.EXPERIENCE, EntryStyle.QUIET)
    val bibleStudyPassage = e("bible.study_passage", EntryScreen.BIBLE_HOME, "Study a passage", EntryKind.SHEET, EntryStyle.QUIET)
    val bibleWalkMe = e("bible.walk_me", EntryScreen.BIBLE_HOME, "Walk me through it", EntryKind.EXPERIENCE, EntryStyle.PROMINENT)

    val all: List<EntryPoint> = listOf(
        faithBible, faithContinue, faithStartReading, faithGuideMe, faithJustRead, faithDevotional, faithPray, faithCircles,
        faithVerseWrite, faithVerseShare, faithTestimonies, faithRecordSermon,
        faithWritePrayer, faithWritePrayerRequest, faithWriteBlessing, faithWriteTestimony, faithWriteReflection, faithWriteDevotional,
        quickFaith, quickCreate, quickWork, quickPrayer, quickDevotional, quickTestimonies,
        everydayRecord, everydayOpen, everydayPrepare,
        bibleContinue, bibleStartReading, bibleGuideMe, bibleJustRead, bibleStudyPassage, bibleWalkMe
    )
}
