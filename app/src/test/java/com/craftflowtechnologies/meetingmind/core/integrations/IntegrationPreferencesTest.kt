package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntegrationPreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: IntegrationPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = IntegrationPreferences(context)
    }

    @Test
    fun defaultEnabledProviders_includeCoreTools() = runBlocking {
        val state = preferences.state.first()
        assertTrue(DeviceCalendarProvider.ID in state.enabledProviderIds)
        assertTrue(ShareSheetOutputChannel.ID in state.enabledProviderIds)
        assertTrue(SafStorageProvider.ID in state.enabledProviderIds)
    }

    @Test
    fun toggleNotifyMe_addsAndRemoves() = runBlocking {
        val providerId = "storage.drive"

        val firstTap = preferences.toggleNotifyMe(providerId)
        assertTrue(firstTap)
        var state = preferences.state.first()
        assertTrue(providerId in state.notifyMeProviderIds)

        val secondTap = preferences.toggleNotifyMe(providerId)
        assertFalse(secondTap)
        state = preferences.state.first()
        assertFalse(providerId in state.notifyMeProviderIds)
    }

    @Test
    fun setProviderEnabled_updatesState() = runBlocking {
        val providerId = "email.google"
        assertFalse(preferences.isProviderEnabled(providerId))

        preferences.setProviderEnabled(providerId, true)
        assertTrue(preferences.isProviderEnabled(providerId))

        preferences.setProviderEnabled(providerId, false)
        assertFalse(preferences.isProviderEnabled(providerId))
    }

    @Test
    fun setConfidentialEmailOptIn_persists() = runBlocking {
        preferences.setConfidentialEmailOptIn(true)
        assertTrue(preferences.state.first().confidentialEmailOptIn)

        preferences.setConfidentialEmailOptIn(false)
        assertFalse(preferences.state.first().confidentialEmailOptIn)
    }
}
