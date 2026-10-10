package com.craftflowtechnologies.meetingmind.core.companion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Calendar
import java.util.TimeZone

class CompanionSettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After fun tearDown() = scope.cancel()

    private fun dataStore(name: String = "settings.preferences_pb") =
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve(name) }

    @Test fun `defaults follow section 7_1`() = runBlocking {
        val s = CompanionSettingsStore(dataStore()).settings.first()
        assertEquals(CompanionForm.ZURI, s.form)
        assertNull(s.name)
        assertEquals(Presence.AROUND, s.presence)
        assertNull(s.hiddenUntilMs)
        assertFalse(s.onRecordButton)
        assertTrue(s.quietSermons)
        assertFalse(s.createSuggest)
        assertTrue(s.seasonal)
    }

    @Test fun `every setting survives a reload`() = runBlocking {
        val ds = dataStore()
        val store = CompanionSettingsStore(ds)
        store.setForm(CompanionForm.WREN)
        store.setName("  Pip  ")
        store.setPresence(Presence.MOMENTS)
        store.setHiddenUntil(123_456L)
        store.setOnRecordButton(true)
        store.setQuietSermons(false)
        store.setCreateSuggest(true)

        val s = CompanionSettingsStore(ds).settings.first()
        assertEquals(CompanionForm.WREN, s.form)
        assertEquals("Pip", s.name)
        assertEquals(Presence.MOMENTS, s.presence)
        assertEquals(123_456L, s.hiddenUntilMs)
        assertTrue(s.onRecordButton)
        assertFalse(s.quietSermons)
        assertTrue(s.createSuggest)

        store.setHiddenUntil(null)
        assertNull(store.settings.first().hiddenUntilMs)
    }

    @Test fun `no companion is stored and read back as null`() = runBlocking {
        val store = CompanionSettingsStore(dataStore())
        store.setForm(null)
        assertNull(store.settings.first().form)
        store.setForm(CompanionForm.NAS)
        assertEquals(CompanionForm.NAS, store.settings.first().form)
    }

    @Test fun `a stored form outside the roster falls back to Zuri`() = runBlocking {
        val ds = dataStore()
        val store = CompanionSettingsStore(ds, roster = listOf(CompanionForm.ZURI, CompanionForm.NAS))
        store.setForm(CompanionForm.PAGE)
        assertEquals(CompanionForm.ZURI, store.settings.first().form)
        store.setForm(CompanionForm.NAS)
        assertEquals(CompanionForm.NAS, store.settings.first().form)
        // No companion is not a form, so a roster change never brings one back.
        store.setForm(null)
        assertNull(store.settings.first().form)
    }

    @Test fun `an unknown stored form or presence reads as the default`() = runBlocking {
        val ds = dataStore()
        ds.edit {
            it[stringPreferencesKey("companion.form")] = "DRAGON"
            it[stringPreferencesKey("companion.presence")] = "LOUD"
        }
        val s = CompanionSettingsStore(ds).settings.first()
        assertEquals(CompanionForm.ZURI, s.form)
        assertEquals(Presence.AROUND, s.presence)
    }

    @Test fun `a blank name clears the rename`() = runBlocking {
        val store = CompanionSettingsStore(dataStore())
        store.setName("Pip")
        assertEquals("Pip", store.settings.first().name)
        store.setName("   ")
        assertNull(store.settings.first().name)
    }

    @Test fun `names are trimmed and cut to 14 characters`() {
        assertEquals("Pip", CompanionSettingsRules.normalizeName("  Pip "))
        assertEquals("abcdefghijklmn", CompanionSettingsRules.normalizeName("abcdefghijklmnop"))
        assertNull(CompanionSettingsRules.normalizeName(""))
        assertNull(CompanionSettingsRules.normalizeName(null))
    }

    @Test fun `hide lasts until the next 07 00 local time`() {
        val zone = TimeZone.getTimeZone("Africa/Lusaka")
        fun at(h: Int, m: Int) = Calendar.getInstance(zone).apply { set(2026, Calendar.OCTOBER, 10, h, m, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        fun hourOf(ms: Long) = Calendar.getInstance(zone).apply { timeInMillis = ms }.let { it.get(Calendar.DAY_OF_MONTH) to it.get(Calendar.HOUR_OF_DAY) }

        assertEquals(10 to 7, hourOf(CompanionSettingsRules.nextMorning(at(5, 30), zone)))
        assertEquals(11 to 7, hourOf(CompanionSettingsRules.nextMorning(at(7, 0), zone)))
        assertEquals(11 to 7, hourOf(CompanionSettingsRules.nextMorning(at(14, 0), zone)))
        assertEquals(11 to 7, hourOf(CompanionSettingsRules.nextMorning(at(23, 59), zone)))
    }
}
