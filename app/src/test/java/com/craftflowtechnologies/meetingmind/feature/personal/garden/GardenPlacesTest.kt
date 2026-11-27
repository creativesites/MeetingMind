package com.craftflowtechnologies.meetingmind.feature.personal.garden

import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.feature.personal.PersonalTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every Sanctuary capability has a place in "Your garden", and every place opens a real Sanctuary
 * tab or sheet. If a capability is ever dropped from [GardenPlace], these tests fail.
 */
class GardenPlacesTest {

    @Test
    fun every_sanctuary_tab_is_reachable_from_the_garden() {
        val reachableTabs = GardenPlace.entries.mapNotNull { it.tab }.toSet()
        PersonalTab.entries.forEach { tab ->
            assertTrue("No garden place opens ${tab.name}", tab.name in reachableTabs)
        }
    }

    @Test
    fun every_place_names_a_real_tab_or_the_privacy_sheet() {
        val tabNames = PersonalTab.entries.map { it.name }.toSet()
        GardenPlace.entries.forEach { place ->
            place.tab?.let { assertTrue("${place.name} names unknown tab $it", it in tabNames) }
            assertTrue("${place.name} needs a tab or tools", place.tab != null || place.tools)
        }
    }

    @Test
    fun places_have_unique_menu_ids_and_routes() {
        assertEquals(GardenPlace.entries.size, GardenPlace.entries.map { it.menuId }.toSet().size)
        assertEquals(GardenPlace.entries.size, GardenPlace.entries.map { it.route }.toSet().size)
    }

    @Test
    fun routes_open_the_sanctuary_with_the_right_tab_and_sheet() {
        assertEquals("personal_sanctuary?tab=GROWTH", GardenPlace.JOURNEYS.route)
        assertEquals("personal_sanctuary?tab=VAULT", GardenPlace.KEEPSAKES.route)
        assertEquals("personal_sanctuary?tab=SANCTUARY&tools=true", GardenPlace.PRIVACY.route)
        assertFalse(GardenPlace.RITUALS.route.contains("tools"))
    }

    @Test
    fun route_builder_keeps_the_plain_forms_unchanged() {
        assertEquals("personal_sanctuary", Routes.personalRoute())
        assertEquals("personal_sanctuary?tab=IDEAS", Routes.personalRoute("IDEAS"))
        assertEquals("personal_sanctuary?tab=SANCTUARY&tools=true", Routes.personalRoute("SANCTUARY", tools = true))
    }
}
