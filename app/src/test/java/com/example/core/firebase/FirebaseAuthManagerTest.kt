package com.example.core.firebase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards against the hardcoded fake "Winston Chen" sign-in ever coming back: there must be no
 * method anywhere on [FirebaseAuthManager] that can fabricate a signed-in user, and with no
 * Firebase configuration present (no google-services.json in this checkout), authentication
 * must honestly report [AuthAvailability.NotConfigured] rather than pretending to be signed in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAuthManagerTest {

    @Test
    fun `no method can fabricate a mock or local profile`() {
        val methodNames = FirebaseAuthManager::class.java.declaredMethods.map { it.name }
        assertFalse("setLocalMockProfile must not exist", methodNames.contains("setLocalMockProfile"))
        assertFalse("signInLocalUser must not exist", methodNames.contains("signInLocalUser"))
        // Kotlin mangles the JVM name of public functions returning kotlin.Result (e.g.
        // "signInWithGoogle-<hash>"), so match by prefix rather than exact name.
        assertTrue(
            "A real signInWithGoogle method must exist",
            methodNames.any { it.startsWith("signInWithGoogle") }
        )
    }

    @Test
    fun `with no Firebase configuration, no user is ever reported signed in`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = FirebaseAuthManager(context)

        assertNull("No user should be signed in without any sign-in action", manager.currentUser.value)
        assertFalse(manager.isUserSignedIn())
    }
}
