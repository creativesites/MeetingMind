package com.craftflowtechnologies.meetingmind.core.navigation

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes

/**
 * Where a menu row goes: a route, an action the host performs (opening the Create sheet), or an
 * action of the screen the menu was opened from ([Run], used by a vertical's own section).
 */
sealed interface MenuTarget {
    data class Go(val route: String) : MenuTarget
    data object Create : MenuTarget
    class Run(val action: () -> Unit) : MenuTarget
    /** Opens "Notes from a YouTube video", with the kind pre-set for [from] (Sermon for Faith, Lecture for Learning). */
    data class YouTube(val from: NotebookSpace? = null) : MenuTarget
}

/** What a row's icon is; the UI maps it to an image, so this model stays pure and JVM-testable. */
enum class MenuIcon {
    HOME, PULSE, TASKS, INBOX, REVIEW, RECORD, LEARN, PRACTICE, LIBRARY, FAITH, BIBLE, DEVOTIONAL, PRAY,
    PRAYER_LIST, PLANS, TESTIMONIES, JOURNAL, NOTES, SEARCH, CIRCLES, IMPORT, CREATE, ARCHIVE,
    SETTINGS, PLAN, AI, APPEARANCE, BACKUP, INTEGRATIONS, VIDEO, GARDEN
}

data class MenuEntry(val id: String, val label: String, val icon: MenuIcon, val target: MenuTarget, val hint: String? = null)

/** A titled group of rows. [space] is set for a space's own group, so it can be tinted and put first. */
data class MenuSection(val id: String, val title: String, val entries: List<MenuEntry>, val space: NotebookSpace? = null)

/**
 * Everything in the app, in one menu (opened from the avatar on every home and from inside each
 * vertical). Only the spaces the person uses appear. When opened from a vertical ([focus]), that
 * vertical's group comes first, so the menu is the same everywhere but tuned to where you are.
 * Pure, so the contents and the space rule are unit-tested on the JVM.
 */
object AppMenuModel {

    private fun go(id: String, label: String, icon: MenuIcon, route: String, hint: String? = null) = MenuEntry(id, label, icon, MenuTarget.Go(route), hint)

    fun section(space: NotebookSpace): MenuSection = when (space) {
        NotebookSpace.WORK -> MenuSection(
            "work", "Work", listOf(
                go("work.home", "Work", MenuIcon.PULSE, Routes.WORK, "Your day, what needs you, projects"),
                go("work.tasks", "Tasks", MenuIcon.TASKS, Routes.TASKS, "Everything you have to do"),
                go("work.inbox", "Inbox", MenuIcon.INBOX, Routes.WORK_INBOX, "Things shared in, waiting to be filed"),
                go("work.review", "Weekly review", MenuIcon.REVIEW, Routes.WEEKLY_REVIEW),
                go("work.record", "Record a meeting", MenuIcon.RECORD, Routes.recordTypeRoute(RecordingType.MEETING))
            ), NotebookSpace.WORK
        )
        NotebookSpace.LEARNING -> MenuSection(
            "learn", "Learning", listOf(
                go("learn.home", "Learn", MenuIcon.LEARN, Routes.LEARN, "Today's plan, library and progress"),
                go("learn.practice", "Practice", MenuIcon.PRACTICE, Routes.learnPracticeRoute(), "Daily recall from your lectures"),
                go("learn.record", "Record a lecture", MenuIcon.RECORD, Routes.recordTypeRoute(RecordingType.LECTURE)),
                MenuEntry("learn.youtube", "Lecture from a YouTube video", MenuIcon.VIDEO, MenuTarget.YouTube(NotebookSpace.LEARNING), "Paste a link, get notes and practice cards"),
                go("learn.import", "Learn from a file", MenuIcon.IMPORT, Routes.IMPORT, "Audio, video and documents")
            ), NotebookSpace.LEARNING
        )
        NotebookSpace.FAITH -> MenuSection(
            "faith", "Faith", listOf(
                go("faith.home", "Faith", MenuIcon.FAITH, Routes.FAITH, "Your faith space"),
                go("faith.bible", "Bible", MenuIcon.BIBLE, Routes.BIBLE_HOME, "Read, study and guided sessions"),
                go("faith.devotional", "Today's devotional", MenuIcon.DEVOTIONAL, Routes.devotionalRoute()),
                go("faith.pray", "Pray with me", MenuIcon.PRAY, Routes.prayRoute()),
                go("faith.prayer_list", "Prayer list", MenuIcon.PRAYER_LIST, Routes.PRAYER_LIST),
                go("faith.plans", "Reading plans", MenuIcon.PLANS, Routes.PLANS),
                go("faith.testimonies", "Testimonies", MenuIcon.TESTIMONIES, Routes.TESTIMONIES),
                go("faith.journal", "Study journal", MenuIcon.JOURNAL, Routes.BIBLE_JOURNAL),
                go("faith.record", "Record a sermon", MenuIcon.RECORD, Routes.recordTypeRoute(RecordingType.SERMON)),
                MenuEntry("faith.youtube", "Sermon from a YouTube video", MenuIcon.VIDEO, MenuTarget.YouTube(NotebookSpace.FAITH), "Paste a link, get sermon notes"),
                go("faith.settings", "Faith settings", MenuIcon.SETTINGS, Routes.settingsGroupRoute("faith"), "Bible translation, devotional, prayer reminders")
            ), NotebookSpace.FAITH
        )
        NotebookSpace.PERSONAL -> MenuSection(
            "personal", "Personal", listOf(
                // One list with the garden's places (feature/personal/garden/GardenPlace.kt).
                go("personal.garden", "Your garden", MenuIcon.GARDEN, Routes.PERSONAL, "Today's question, practices and ideas"),
                go("personal.rituals", "Rituals", MenuIcon.PLANS, Routes.gardenPlaceRoute("rituals"), "Morning and evening routines"),
                go("personal.journeys", "Journeys & challenges", MenuIcon.PLANS, Routes.gardenPlaceRoute("journeys"), "Guided journeys, challenges and coaching"),
                go("personal.living_journal", "Living journal", MenuIcon.JOURNAL, Routes.gardenPlaceRoute("journal"), "Day pages, volumes and the calendar"),
                go("personal.ideas", "Seed jar", MenuIcon.CREATE, Routes.gardenPlaceRoute("seed_jar"), "Ideas you have kept, and the ones being grown"),
                go("personal.vault", "Keepsakes", MenuIcon.ARCHIVE, Routes.gardenPlaceRoute("keepsakes"), "Time capsules and memories, sealed until their day"),
                go("personal.privacy", "Privacy & sync", MenuIcon.SETTINGS, Routes.gardenPlaceRoute("privacy"), "Sanctuary lock, Obsidian and Day One"),
                go("personal.idea", "Capture an idea", MenuIcon.RECORD, Routes.recordTypeRoute(RecordingType.IDEA))
            ), NotebookSpace.PERSONAL
        )
    }

    /** Things every person has, whatever their spaces. */
    val everyday = MenuSection(
        "everyday", "Everyday", listOf(
            go("everyday.home", "Today", MenuIcon.HOME, Routes.HOME),
            go("everyday.notes", "Notes", MenuIcon.NOTES, Routes.NOTES),
            MenuEntry("everyday.create", "New", MenuIcon.CREATE, MenuTarget.Create, "Record, write, photos, import"),
            MenuEntry("everyday.youtube", "Notes from a YouTube video", MenuIcon.VIDEO, MenuTarget.YouTube(), "Paste a link to a public video"),
            go("everyday.tasks", "Tasks", MenuIcon.TASKS, Routes.TASKS),
            go("everyday.search", "Search everything", MenuIcon.SEARCH, Routes.SEARCH),
            go("everyday.circles", "Circles", MenuIcon.CIRCLES, Routes.FELLOWSHIP, "Your groups: chat, posts and events"),
            go("everyday.import", "Import a file", MenuIcon.IMPORT, Routes.IMPORT, "Audio, video and documents"),
            go("everyday.archive", "Archive", MenuIcon.ARCHIVE, Routes.NOTES_ARCHIVE)
        )
    )

    val app = MenuSection(
        "app", "App", listOf(
            go("app.settings", "Settings", MenuIcon.SETTINGS, Routes.SETTINGS),
            go("app.plan", "Plans", MenuIcon.PLAN, Routes.paywallRoute("SETTINGS")),
            go("app.ai", "AI engine", MenuIcon.AI, Routes.MODELS, "Online and on-device models"),
            go("app.appearance", "Appearance", MenuIcon.APPEARANCE, Routes.APPEARANCE, "Theme, home style and accent"),
            go("app.integrations", "Connected apps", MenuIcon.INTEGRATIONS, Routes.INTEGRATIONS),
            go("app.backup", "Backup and data", MenuIcon.BACKUP, Routes.DATA_BACKUP)
        )
    )

    /**
     * The menu for someone with [spaces], opened from [focus] (null on a home). The focused space
     * leads; the others follow in the bar's order; Everyday and App close the list. A space that is
     * turned off has no group, and none of its rows appear anywhere.
     *
     * [local] is the vertical's own menu (its tools, like Work's), passed by the screen the menu is
     * opened from. It takes the focused space's place, so the main menu is the base and the
     * vertical adds to it. It is ignored when that space is off. A route is listed once: where it
     * first appears.
     */
    fun sections(
        spaces: Set<NotebookSpace>,
        focus: NotebookSpace? = null,
        lead: NotebookSpace? = null,
        local: MenuSection? = null
    ): List<MenuSection> {
        val spaceOrder = (listOfNotNull(focus, lead) + SpaceStyle.ORDER).distinct().filter { it in spaces }
        val ownMenu = local?.takeIf { focus != null && focus in spaces }?.copy(space = focus)
        val spaceSections = spaceOrder.map { if (it == focus && ownMenu != null) ownMenu else section(it) }
        // Tasks is in Work's group when Work is on, so it is not listed twice.
        val everydayFor = if (NotebookSpace.WORK in spaces) everyday.copy(entries = everyday.entries.filter { it.id != "everyday.tasks" }) else everyday
        val ordered = if (focus != null) spaceSections + everydayFor + app else listOf(everydayFor) + spaceSections + app
        val seen = mutableSetOf<String>()
        return ordered.map { section ->
            section.copy(entries = section.entries.filter { e -> (e.target as? MenuTarget.Go)?.route?.let(seen::add) ?: true })
        }.filter { it.entries.isNotEmpty() }
    }
}
