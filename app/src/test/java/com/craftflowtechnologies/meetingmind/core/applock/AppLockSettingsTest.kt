package com.craftflowtechnologies.meetingmind.core.applock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSettingsTest {
    private class Store(var enabled: Boolean = false, var writes: Int = 0) : AppLockStore {
        override suspend fun setEnabled(enabled: Boolean) { this.enabled = enabled; writes++ }
    }

    private val enable = AuthPrompt("enable", "e")
    private val disable = AuthPrompt("disable", "d")
    private var clock = 0L
    private val controller = AppLockController(now = { clock })

    private fun settings(auth: FakeAuthenticator, store: Store) =
        AppLockSettings(auth, store, controller, enable, disable)

    @Test fun `app lock defaults off`() {
        assertFalse(com.craftflowtechnologies.meetingmind.core.datastore.AppPreferencesState().appLockEnabled)
    }

    @Test fun `enabling after successful authentication persists and starts unlocked`() = runBlocking {
        val store = Store(); val auth = FakeAuthenticator()
        controller.onPreferenceLoaded(false)
        assertEquals(AppLockChange.Changed, settings(auth, store).setEnabled(true))
        assertTrue(store.enabled)
        assertEquals(enable, auth.shownPrompts.single())
        assertEquals(AppLockState.Unlocked, controller.state.value)
    }

    @Test fun `enabling does not persist after a cancelled or failed authentication`() = runBlocking {
        for (result in listOf(AuthResult.Cancelled, AuthResult.LockedOut(true), AuthResult.Error(5, "x"))) {
            val store = Store(); val auth = FakeAuthenticator(result = result)
            controller.onPreferenceLoaded(false)
            assertEquals(AppLockChange.NotVerified(result), settings(auth, store).setEnabled(true))
            assertFalse(store.enabled); assertEquals(0, store.writes)
            assertEquals(AppLockState.Disabled, controller.state.value)
        }
    }

    @Test fun `enabling is refused, with the reason, when the phone cannot authenticate`() = runBlocking {
        for (reason in AppLockAvailability.entries.filter { it != AppLockAvailability.Available }) {
            val store = Store(); val auth = FakeAuthenticator(available = reason)
            assertEquals(AppLockChange.Unavailable(reason), settings(auth, store).setEnabled(true))
            assertEquals("no prompt is shown when it can't work", 0, auth.prompts)
            assertEquals(0, store.writes)
        }
    }

    @Test fun `disabling requires authentication`() = runBlocking {
        val store = Store(enabled = true); val auth = FakeAuthenticator()
        controller.onPreferenceLoaded(true)
        assertEquals(AppLockChange.Changed, settings(auth, store).setEnabled(false))
        assertEquals(1, auth.prompts)
        assertEquals(disable, auth.shownPrompts.single())
        assertFalse(store.enabled)
    }

    @Test fun `failed or cancelled authentication leaves app lock enabled`() = runBlocking {
        for (result in listOf(AuthResult.Cancelled, AuthResult.LockedOut(false), AuthResult.Error(1, "x"))) {
            val store = Store(enabled = true); val auth = FakeAuthenticator(result = result)
            assertEquals(AppLockChange.NotVerified(result), settings(auth, store).setEnabled(false))
            assertTrue(store.enabled); assertEquals(0, store.writes)
        }
    }

    @Test fun `disabling still works when the phone lost its screen lock so the owner is never trapped`() = runBlocking {
        val store = Store(enabled = true); val auth = FakeAuthenticator(available = AppLockAvailability.NoneEnrolled)
        assertEquals(AppLockChange.Changed, settings(auth, store).setEnabled(false))
        assertFalse(store.enabled)
    }

    @Test fun `temporary hardware trouble does not let anyone skip authentication to disable`() = runBlocking {
        val store = Store(enabled = true)
        val auth = FakeAuthenticator(available = AppLockAvailability.HardwareUnavailable, result = AuthResult.Unavailable(AppLockAvailability.HardwareUnavailable))
        assertTrue(settings(auth, store).setEnabled(false) is AppLockChange.NotVerified)
        assertTrue(store.enabled)
    }

    @Test fun `a storage failure while enabling rolls the session back`() = runBlocking {
        val failing = AppLockStore { throw IllegalStateException("disk") }
        controller.onPreferenceLoaded(false)
        runCatching { settings(FakeAuthenticator(), Store()).let { AppLockSettings(FakeAuthenticator(), failing, controller, enable, disable).setEnabled(true) } }
        assertEquals(AppLockState.Disabled, controller.state.value)
    }
}
