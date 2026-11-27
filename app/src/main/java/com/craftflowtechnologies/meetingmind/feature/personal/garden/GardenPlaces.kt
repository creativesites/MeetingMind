package com.craftflowtechnologies.meetingmind.feature.personal.garden

import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.feature.personal.PersonalTab

/**
 * Every Sanctuary capability with its own garden page. The garden home, the Personal menu and the
 * tests all read this list, so a capability cannot be reachable from one place and missing from another.
 *
 * [tab] is the Sanctuary body the page shows. Privacy & sync has no body tab: it is its own page.
 */
enum class GardenPlace(
    val title: String,
    val line: String,
    val tab: PersonalTab?,
    val menuId: String,
) {
    RITUALS(
        title = "Rituals",
        line = "Morning and evening routines, played aloud",
        tab = PersonalTab.SANCTUARY,
        menuId = "personal.rituals",
    ),
    JOURNEYS(
        title = "Journeys & challenges",
        line = "Seed packets to plant, one day at a time",
        tab = PersonalTab.GROWTH,
        menuId = "personal.journeys",
    ),
    JOURNAL(
        title = "Living journal",
        line = "Day pages, volumes and the calendar",
        tab = PersonalTab.JOURNAL,
        menuId = "personal.living_journal",
    ),
    SEED_JAR(
        title = "Seed jar",
        line = "Ideas you have kept, and the ones being grown",
        tab = PersonalTab.IDEAS,
        menuId = "personal.ideas",
    ),
    KEEPSAKES(
        title = "Keepsakes",
        line = "Time capsules and memories, sealed until their day",
        tab = PersonalTab.VAULT,
        menuId = "personal.vault",
    ),
    PRIVACY(
        title = "Privacy & sync",
        line = "Sanctuary lock, screenshot shield, Obsidian and Day One",
        tab = null,
        menuId = "personal.privacy",
    );

    /** The navigation route that opens this page. */
    val route: String get() = Routes.gardenPlaceRoute(name.lowercase())

    companion object {
        /** The place named in a route argument, or null for an unknown name. */
        fun fromRouteName(name: String?): GardenPlace? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
