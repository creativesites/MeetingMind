package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Token registration with fakes: no Firebase, no network. */
class PushTokensTest {
    private class Rig(var token: String? = "tok-1-aaaaaaaaaaaaaaaaaaaa", configured: Boolean = true) {
        val api = FakeApi(configured)
        val store = InMemoryPushTokenStore()
        val manager = PushTokenManager(api, store) { token }
    }

    @Test fun `nothing is sent before the person uses Circles`() = runBlocking {
        val r = Rig()
        r.manager.onNewToken("tok-2-bbbbbbbbbbbbbbbbbbbb")
        assertTrue(r.api.registered.isEmpty())
        assertEquals("tok-2-bbbbbbbbbbbbbbbbbbbb", r.store.latestToken)
        assertFalse(r.store.wantsPush)
    }

    @Test fun `opening Circles registers the current token once`() = runBlocking {
        val r = Rig()
        r.manager.activate(); r.manager.activate()
        assertEquals(listOf("tok-1-aaaaaaaaaaaaaaaaaaaa"), r.api.registered)
        assertEquals("tok-1-aaaaaaaaaaaaaaaaaaaa", r.store.registeredToken)
    }

    @Test fun `a refreshed token registers after activation, and a token received earlier is picked up`() = runBlocking {
        val r = Rig(token = null)
        r.manager.onNewToken("tok-early-cccccccccccccccc")   // arrives before Circles is used
        r.manager.activate()                                 // Firebase has no token to give; the stored one is used
        assertEquals(listOf("tok-early-cccccccccccccccc"), r.api.registered)
        r.manager.onNewToken("tok-new-dddddddddddddddddd")
        assertEquals(listOf("tok-early-cccccccccccccccc", "tok-new-dddddddddddddddddd"), r.api.registered)
    }

    @Test fun `a failed registration is not recorded and is retried next time`() = runBlocking {
        val r = Rig()
        r.api.nextFailure = CirclesFailure.offline
        r.manager.activate()
        assertNull(r.store.registeredToken)
        r.manager.activate()
        assertEquals(2, r.api.registered.size)
        assertEquals("tok-1-aaaaaaaaaaaaaaaaaaaa", r.store.registeredToken)
    }

    @Test fun `an unconfigured build never calls the Worker`() = runBlocking {
        val r = Rig(configured = false)
        r.manager.activate()
        r.manager.onNewToken("tok-2-bbbbbbbbbbbbbbbbbbbb")
        assertTrue(r.api.registered.isEmpty())
    }

    @Test fun `leaving the last circle unregisters and stops pushes`() = runBlocking {
        val r = Rig()
        r.manager.activate()
        r.manager.deactivate()
        assertEquals(listOf("tok-1-aaaaaaaaaaaaaaaaaaaa"), r.api.unregistered)
        assertNull(r.store.registeredToken)
        r.manager.onNewToken("tok-3-eeeeeeeeeeeeeeeeeeee")
        assertEquals(1, r.api.registered.size) // wantsPush is off again
    }

    @Test fun `a new account registers the same token fresh`() = runBlocking {
        val r = Rig()
        r.manager.activate()
        r.manager.onAccountChanged()
        assertEquals(2, r.api.registered.size)
    }

    @Test fun `the repository activates push when Circles opens and on the first circle, and deactivates on the last leave`() = runBlocking {
        val r = Rig()
        val repo = repoWith(r.api, push = r.manager)
        repo.refresh()
        assertEquals(1, r.api.registered.size)
        r.api.joinedCircleId = "c9"
        repo.join("GRACE-7K2Q", "Ann")
        assertTrue(repo.isFirstCircle())
        repo.leave("c9")
        assertEquals(1, r.api.unregistered.size)
    }
}
