package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiPlanTest {
    private val store = GeminiCredentialStore(ApplicationProvider.getApplicationContext<Context>())

    // The credential DataStore is process-wide, so a key or plan flag left here would leak into later test classes.
    @After fun tearDown() = runBlocking {
        store.clear()
        store.setPaidPlanConfirmed(false)
    }

    @Test fun `the plan is unconfirmed until the person says otherwise`() = runBlocking {
        store.setApiKey("my-own-key")
        assertFalse(store.keyPlanConfirmedPaid())
        store.setPaidPlanConfirmed(true)
        assertTrue(store.keyPlanConfirmedPaid())
        store.setPaidPlanConfirmed(false)
        assertFalse(store.keyPlanConfirmedPaid())
    }

    @Test fun `the built-in key is the owner's billed key`() {
        assertTrue(GeminiCredentialStore.SYSTEM_KEY_CONFIRMED_PAID)
    }
}
