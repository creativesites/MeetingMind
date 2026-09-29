package com.example.core.applock

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {
    private var clock = 1_000L
    private fun controller(grace: Long = 30_000L) = AppLockController(now = { clock }, graceMs = grace)
    private fun AppLockController.unlocked() = apply {
        onPreferenceLoaded(true); beginUnlock(); onUnlockSucceeded()
    }

    @Test fun `fails closed until the preference is read`() {
        val c = controller()
        assertEquals(AppLockState.Locked, c.state.value)
        assertFalse(c.ready.value)
    }

    @Test fun `disabled means no lock`() {
        val c = controller()
        c.onPreferenceLoaded(false)
        assertEquals(AppLockState.Disabled, c.state.value)
        assertTrue(c.ready.value)
        assertFalse(c.state.value.isContentHidden)
        assertFalse(c.beginUnlock())
    }

    @Test fun `enabled and not authenticated is locked, authenticated is unlocked`() {
        val c = controller()
        c.onPreferenceLoaded(true)
        assertEquals(AppLockState.Locked, c.state.value)
        assertTrue(c.state.value.isContentHidden)
        assertTrue(c.beginUnlock())
        assertEquals(AppLockState.Unlocking, c.state.value)
        assertTrue("prompt up still hides content", c.state.value.isContentHidden)
        c.onUnlockSucceeded()
        assertEquals(AppLockState.Unlocked, c.state.value)
        assertFalse(c.state.value.isContentHidden)
    }

    @Test fun `a failed or cancelled unlock returns to locked`() {
        val c = controller()
        c.onPreferenceLoaded(true)
        c.beginUnlock(); c.onUnlockFailed()
        assertEquals(AppLockState.Locked, c.state.value)
    }

    @Test fun `returning after the grace period locks`() {
        val c = controller().unlocked()
        c.onAppBackgrounded()
        clock += 30_000
        c.onAppForegrounded()
        assertEquals(AppLockState.Locked, c.state.value)
    }

    @Test fun `returning within the grace period stays unlocked`() {
        val c = controller().unlocked()
        c.onAppBackgrounded()
        clock += 29_999
        c.onAppForegrounded()
        assertEquals(AppLockState.Unlocked, c.state.value)
    }

    @Test fun `zero grace locks on every return`() {
        val c = controller(grace = 0).unlocked()
        c.onAppBackgrounded()
        c.onAppForegrounded()
        assertEquals(AppLockState.Locked, c.state.value)
    }

    @Test fun `configuration change does not start the clock`() {
        val c = controller().unlocked()
        c.onAppBackgrounded(isChangingConfigurations = true)
        clock += 10 * 60_000
        c.onAppForegrounded()
        assertEquals(AppLockState.Unlocked, c.state.value)
    }

    @Test fun `internal navigation never reaches the controller and stays unlocked`() {
        val c = controller().unlocked()
        clock += 60 * 60_000 // an hour of use inside the app, no background event
        assertEquals(AppLockState.Unlocked, c.state.value)
    }

    @Test fun `a second background event does not extend the grace period`() {
        val c = controller().unlocked()
        c.onAppBackgrounded()
        clock += 20_000
        c.onAppBackgrounded() // e.g. another stop without a start in between
        clock += 20_000
        c.onAppForegrounded()
        assertEquals(AppLockState.Locked, c.state.value)
    }

    @Test fun `foreground without a prior background is ignored`() {
        val c = controller().unlocked()
        c.onAppForegrounded()
        assertEquals(AppLockState.Unlocked, c.state.value)
    }

    @Test fun `background while locked does not unlock or reset anything`() {
        val c = controller()
        c.onPreferenceLoaded(true)
        c.onAppBackgrounded(); clock += 1_000_000; c.onAppForegrounded()
        assertEquals(AppLockState.Locked, c.state.value)
    }

    @Test fun `turning the setting off unlocks everything and back on locks a fresh session`() {
        val c = controller().unlocked()
        c.onPreferenceLoaded(false)
        assertEquals(AppLockState.Disabled, c.state.value)
        c.onPreferenceLoaded(true)
        assertEquals(AppLockState.Locked, c.state.value)
    }

    @Test fun `preference echo after enabling keeps this session unlocked`() {
        val c = controller()
        c.onPreferenceLoaded(false)
        c.onEnabledBySession()
        c.onPreferenceLoaded(true)
        assertEquals(AppLockState.Unlocked, c.state.value)
    }

    @Test fun `lockNow locks an unlocked session and is a no-op when disabled`() {
        val c = controller().unlocked()
        c.lockNow()
        assertEquals(AppLockState.Locked, c.state.value)
        val off = controller(); off.onPreferenceLoaded(false); off.lockNow()
        assertEquals(AppLockState.Disabled, off.state.value)
    }

    @Test fun `unlockWith succeeds, fails and cancels back to the right state`() = runBlocking {
        val c = controller(); c.onPreferenceLoaded(true)
        val fake = FakeAuthenticator(result = AuthResult.Cancelled)
        assertEquals(AuthResult.Cancelled, c.unlockWith(fake, PROMPT))
        assertEquals(AppLockState.Locked, c.state.value)

        fake.result = AuthResult.LockedOut(permanent = false)
        assertEquals(AuthResult.LockedOut(false), c.unlockWith(fake, PROMPT))
        assertEquals(AppLockState.Locked, c.state.value)

        fake.result = AuthResult.Success
        assertEquals(AuthResult.Success, c.unlockWith(fake, PROMPT))
        assertEquals(AppLockState.Unlocked, c.state.value)
        assertEquals(3, fake.prompts)

        // Already unlocked: nothing to do and no prompt.
        assertEquals(null, c.unlockWith(fake, PROMPT))
        assertEquals(3, fake.prompts)
    }

    @Test fun `unlockWith cancelled mid-prompt leaves the lock engaged`() = runBlocking {
        val c = controller(); c.onPreferenceLoaded(true)
        val fake = FakeAuthenticator(throwCancellation = true)
        runCatching { c.unlockWith(fake, PROMPT) }
        assertEquals(AppLockState.Locked, c.state.value)
    }
}
