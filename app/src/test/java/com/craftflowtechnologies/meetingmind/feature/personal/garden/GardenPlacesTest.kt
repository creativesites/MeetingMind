package com.craftflowtechnologies.meetingmind.feature.personal.garden

import com.craftflowtechnologies.meetingmind.feature.personal.PersonalTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every Sanctuary capability has its own garden page, and every page opens a real Sanctuary body or the
 * privacy page. If a capability is dropped from [GardenPlace], these tests fail.
 */
class GardenPlacesTest {

    @Test
    fun every_sanctuary_tab_is_reachable_from_the_garden() {
        val bodies = GardenPlace.entries.mapNotNull { it.tab }.toSet()
        PersonalTab.entries.forEach { tab ->
            assertTrue("No garden page shows ${tab.name}", tab in bodies)
        }
    }

    @Test
    fun only_privacy_is_a_page_without_a_sanctuary_body() {
        val withoutBody = GardenPlace.entries.filter { it.tab == null }
        assertEquals(listOf(GardenPlace.PRIVACY), withoutBody)
    }

    @Test
    fun places_have_unique_menu_ids_and_routes() {
        assertEquals(GardenPlace.entries.size, GardenPlace.entries.map { it.menuId }.toSet().size)
        assertEquals(GardenPlace.entries.size, GardenPlace.entries.map { it.route }.toSet().size)
    }

    @Test
    fun routes_are_one_page_per_place() {
        assertEquals("garden/rituals", GardenPlace.RITUALS.route)
        assertEquals("garden/journeys", GardenPlace.JOURNEYS.route)
        assertEquals("garden/journal", GardenPlace.JOURNAL.route)
        assertEquals("garden/seed_jar", GardenPlace.SEED_JAR.route)
        assertEquals("garden/keepsakes", GardenPlace.KEEPSAKES.route)
        assertEquals("garden/privacy", GardenPlace.PRIVACY.route)
    }

    @Test
    fun every_route_resolves_back_to_its_place() {
        GardenPlace.entries.forEach { place ->
            val segment = place.route.substringAfter("garden/")
            assertEquals(place, GardenPlace.fromRouteName(segment))
        }
    }

    @Test
    fun unknown_or_missing_names_resolve_to_nothing() {
        assertNull(GardenPlace.fromRouteName("growth"))
        assertNull(GardenPlace.fromRouteName(null))
    }
}
