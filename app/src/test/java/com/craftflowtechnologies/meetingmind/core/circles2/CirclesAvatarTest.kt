package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Circles uses the app's profile photo until the person picks one inside Circles. */
class CirclesAvatarTest {
    private fun setup(inCircle: Boolean = true): Triple<CirclesRepository, FakeApi, InMemoryLocalCircleStore> {
        val api = FakeApi()
        val store = InMemoryLocalCircleStore().apply { if (inCircle) addCircle("c1") }
        return Triple(CirclesRepository(api, FakeData(), FakeAuth(), store, clock = { 1L }), api, store)
    }
    private val jpeg: suspend () -> ByteArray? = { ByteArray(8) }

    @Test fun `the app photo becomes the Circles photo once per change`() = runBlocking {
        val (repo, api, store) = setup()
        assertTrue(repo.syncAppAvatar("/p/avatar_1.jpg@100", jpeg) is CirclesResult.Ok)
        assertNull(repo.syncAppAvatar("/p/avatar_1.jpg@100", jpeg))
        assertEquals(1, api.avatarUploads)
        assertTrue(repo.syncAppAvatar("/p/avatar_2.jpg@200", jpeg) is CirclesResult.Ok)
        assertEquals(2, api.avatarUploads)
        assertEquals("/p/avatar_2.jpg@200", store.appAvatarSynced)
    }

    @Test fun `a photo picked in Circles wins over the app photo, and no circle means no upload`() = runBlocking {
        val (repo, api, _) = setup()
        assertTrue(repo.setProfilePhoto(ByteArray(8)) is CirclesResult.Ok)
        assertNull(repo.syncAppAvatar("/p/avatar_3.jpg@300", jpeg))
        assertEquals(1, api.avatarUploads)

        val (empty, emptyApi, _) = setup(inCircle = false)
        assertNull(empty.syncAppAvatar("/p/avatar_1.jpg@100", jpeg))
        assertEquals(0, emptyApi.avatarUploads)
    }

    @Test fun `a failed upload is tried again next time`() = runBlocking {
        val (repo, api, store) = setup()
        api.nextFailure = CirclesFailure.offline
        assertTrue(repo.syncAppAvatar("/p/a.jpg@1", jpeg) is CirclesResult.Err)
        assertNull(store.appAvatarSynced)
        assertTrue(repo.syncAppAvatar("/p/a.jpg@1", jpeg) is CirclesResult.Ok)
    }
}
