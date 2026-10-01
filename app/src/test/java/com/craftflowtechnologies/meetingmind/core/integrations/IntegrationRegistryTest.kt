package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntegrationRegistryTest {

    private lateinit var context: Context
    private lateinit var registry: IntegrationRegistry

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = IntegrationRegistry(context)
    }

    @Test
    fun defaultProviders_areRegistered() {
        val providers = registry.allProviders()
        assertTrue(providers.isNotEmpty())

        assertNotNull(registry.findProvider(DeviceCalendarProvider.ID))
        assertNotNull(registry.findProvider(ShareSheetOutputChannel.ID))
        assertNotNull(registry.findProvider(SafStorageProvider.ID))
    }

    @Test
    fun primaryProviders_areAvailable() {
        assertEquals("calendar.device", registry.calendarProvider.id)
        assertEquals("output.sharesheet", registry.outputChannel.id)
        assertEquals("storage.saf", registry.storageProvider.id)
    }

    @Test
    fun providersWithCapability_filtersCorrectly() {
        val calendarProviders = registry.providersWithCapability(Capability.CALENDAR_EVENTS)
        assertTrue(calendarProviders.any { it.id == DeviceCalendarProvider.ID })

        val shareProviders = registry.providersWithCapability(Capability.OUTPUT_SHARE_SHEET)
        assertTrue(shareProviders.any { it.id == ShareSheetOutputChannel.ID })

        val storageProviders = registry.providersWithCapability(Capability.STORAGE_IMPORT_DOCS)
        assertTrue(storageProviders.any { it.id == SafStorageProvider.ID })
    }

    @Test
    fun stubProviders_areIncludedInComingLater() {
        val drive = registry.findProvider("storage.drive")
        assertNotNull(drive)
        assertEquals(ProviderStatus.COMING_LATER, drive!!.status)

        val zoom = registry.findProvider("meeting.zoom")
        assertNotNull(zoom)
        assertEquals(ProviderStatus.COMING_LATER, zoom!!.status)
    }

    @Test
    fun findUnknownProvider_returnsNull() {
        assertNull(registry.findProvider("unknown.provider.id"))
    }
}
