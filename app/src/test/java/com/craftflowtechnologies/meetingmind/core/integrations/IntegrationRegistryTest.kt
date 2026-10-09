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
    fun stubProviders_areHiddenByDefault() {
        // FEATURE_STUB_INTEGRATIONS is off for MVP, so "Notify me" stubs are not registered.
        assertNull(registry.findProvider("storage.drive"))
        assertNull(registry.findProvider("meeting.zoom"))
        assertTrue(registry.allProviders().none { it.status == ProviderStatus.COMING_LATER })
    }

    @Test
    fun stubProviders_stillDefined() {
        // The feature code stays in the tree behind its flag.
        val stubs = StubIntegrationProvider.comingLaterProviders()
        assertEquals(8, stubs.size)
        assertTrue(stubs.all { it.status == ProviderStatus.COMING_LATER })
    }

    @Test
    fun emailProviders_hiddenByDefault() {
        assertNull(registry.findProvider(GoogleEmailProvider.ID))
        assertNull(registry.findProvider(MicrosoftEmailProvider.ID))
    }

    @Test
    fun findUnknownProvider_returnsNull() {
        assertNull(registry.findProvider("unknown.provider.id"))
    }
}
