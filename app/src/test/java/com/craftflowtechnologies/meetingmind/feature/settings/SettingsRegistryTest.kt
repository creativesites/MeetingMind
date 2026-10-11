package com.craftflowtechnologies.meetingmind.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRegistryTest {
    @Test fun `there are exactly eight groups in the agreed order`() {
        assertEquals(
            listOf("Account", "Recording", "AI & offline models", "Faith", "Appearance & home", "Privacy & security", "Storage & backup", "About"),
            SettingsRegistry.groups.map { it.title }
        )
    }

    @Test fun `no setting is registered twice and every id is registered`() {
        val keys = SettingsRegistry.entries.map { it.key }
        assertEquals("duplicate keys: ${keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}", keys.size, keys.toSet().size)
        assertEquals(SettingId.entries.toSet(), SettingsRegistry.entries.map { it.id }.toSet())
        val titles = SettingsRegistry.entries.map { it.group to it.title }
        assertEquals("the same title twice in one group", titles.size, titles.toSet().size)
    }

    @Test fun `every group has one to four most-used rows before Advanced`() {
        SettingsRegistry.groups.forEach { g ->
            val primary = SettingsRegistry.inGroup(g).filter { !it.advanced }
            assertTrue("${g.title} has ${primary.size} primary rows", primary.size in 1..4)
        }
    }

    @Test fun `every setting is reachable by searching for its title or any keyword`() {
        SettingsRegistry.entries.forEach { e ->
            assertTrue("${e.key} not found by title", SettingsRegistry.search(e.title).any { it.id == e.id })
            e.keywords.split(" ").filter { it.isNotBlank() }.forEach { word ->
                assertTrue("${e.key} not found by keyword '$word'", SettingsRegistry.search(word).any { it.id == e.id })
            }
        }
    }

    @Test fun `search matches every word, ignores case and finds Faith for anyone`() {
        assertTrue(SettingsRegistry.search("DARK mode").any { it.id == SettingId.LOOK_AND_HOME })
        assertTrue(SettingsRegistry.search("gemini key").any { it.id == SettingId.GEMINI_KEY })
        assertTrue(SettingsRegistry.search("zzzz nothing").isEmpty())
        assertTrue(SettingsRegistry.search("   ").isEmpty())
        // Hiding the Faith group never removes its settings from search.
        assertTrue(SettingsRegistry.search("prayer reminders").any { it.group == SettingsGroup.FAITH })
    }

    @Test fun `search drops settings that do not exist for this person`() {
        val none = SettingsRegistry.search("work") { it.needs != Needs.WORK_SPACE }
        assertTrue(none.none { it.needs == Needs.WORK_SPACE })
    }

    @Test fun `faith lives in the Faith group and every notification is listed on the notifications screen`() {
        listOf(SettingId.BIBLE_TRANSLATION, SettingId.BIBLE_OFFLINE, SettingId.ORIGINAL_PACKS, SettingId.DEVOTIONAL, SettingId.PRAYER_REMINDERS, SettingId.PERSONAL_TOUCH, SettingId.FAITH_LOCK)
            .forEach { assertEquals(SettingsGroup.FAITH, SettingsRegistry.entry(it).group) }
        val notifications = SettingsRegistry.notifications().map { it.id }.toSet()
        listOf(SettingId.NOTIF_DEVOTIONAL, SettingId.NOTIF_PRAYER_TIMES, SettingId.NOTIF_CIRCLES, SettingId.NOTIF_RECORDING).forEach { assertTrue(it in notifications) }
        assertTrue(SettingsRegistry.inGroup(SettingsGroup.ACCOUNT).any { it.id == SettingId.NOTIFICATIONS })
    }

    @Test fun `page keys round trip so a jump survives rotation`() {
        val pages = listOf(
            SettingsPage.Hub, SettingsPage.Group(SettingsGroup.FAITH, SettingId.FAITH_LOCK), SettingsPage.Group(SettingsGroup.ABOUT),
            SettingsPage.Notifications(SettingId.NOTIF_CIRCLES), SettingsPage.Devotional("PERSONAL"), SettingsPage.Devotional(null), SettingsPage.Reminders
        )
        pages.forEach { assertEquals(it, parsePage(it.encode())) }
        assertEquals(SettingsPage.Hub, parsePage("nonsense"))
    }
}
