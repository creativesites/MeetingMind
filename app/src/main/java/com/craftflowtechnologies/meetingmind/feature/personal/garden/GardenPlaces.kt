package com.craftflowtechnologies.meetingmind.feature.personal.garden

import com.craftflowtechnologies.meetingmind.feature.navigation.Routes

/**
 * Every Sanctuary capability that has a home in "Your garden". The garden home, the Personal
 * menu and the tests all read this list, so a capability cannot be reachable from one place and
 * missing from another.
 *
 * [tab] names a tab of the Sanctuary screen that still hosts the capability's content. [tools]
 * opens its privacy and sync sheet on that screen.
 */
enum class GardenPlace(
    val title: String,
    val line: String,
    val tab: String?,
    val tools: Boolean = false,
    val menuId: String,
) {
    RITUALS(
        title = "Rituals",
        line = "Morning and evening routines, played aloud",
        tab = "SANCTUARY",
        menuId = "personal.rituals",
    ),
    JOURNEYS(
        title = "Journeys & challenges",
        line = "Seed packets to plant, one day at a time",
        tab = "GROWTH",
        menuId = "personal.journeys",
    ),
    JOURNAL(
        title = "Living journal",
        line = "Day pages, volumes and the calendar",
        tab = "JOURNAL",
        menuId = "personal.living_journal",
    ),
    SEED_JAR(
        title = "Seed jar",
        line = "Ideas you have kept, and the ones being grown",
        tab = "IDEAS",
        menuId = "personal.ideas",
    ),
    KEEPSAKES(
        title = "Keepsakes",
        line = "Time capsules and memories, sealed until their day",
        tab = "VAULT",
        menuId = "personal.vault",
    ),
    PRIVACY(
        title = "Privacy & sync",
        line = "Sanctuary lock, screenshot shield, Obsidian and Day One",
        tab = "SANCTUARY",
        tools = true,
        menuId = "personal.privacy",
    );

    /** The navigation route that opens this place. */
    val route: String get() = Routes.personalRoute(tab = tab, tools = tools)
}
