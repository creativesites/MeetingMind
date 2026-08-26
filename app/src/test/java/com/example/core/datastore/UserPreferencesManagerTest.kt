package com.example.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [AppPreferencesState.userName] is the one piece of Phase 15 §5's "User Identity" requirement
 * that's just a stored preference (the onboarding UI collecting it is exercised separately) — the
 * behavior worth pinning down here is that "the user typed nothing" and "the user has no name set"
 * are the same state, never two different ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesManagerTest {

    @Test
    fun `userName defaults to null before anything is set`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)

        assertNull(manager.preferencesFlow.first().userName)
    }

    @Test
    fun `setUserName persists a real name, trimmed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)

        manager.setUserName("  Winston  ")

        assertEquals("Winston", manager.preferencesFlow.first().userName)
    }

    @Test
    fun `setUserName with a blank string clears it back to null rather than storing empty`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = UserPreferencesManager(context)
        manager.setUserName("Winston")

        manager.setUserName("   ")

        assertNull(manager.preferencesFlow.first().userName)
    }
}
