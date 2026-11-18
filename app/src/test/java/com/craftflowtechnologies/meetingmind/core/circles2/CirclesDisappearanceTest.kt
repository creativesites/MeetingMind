package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The founder's circles vanished after installing v49. The cause was a new sign-in (a reinstall gives a new anonymous
 * account), not a code change. These tests pin what the phone does with circles it can no longer read: it never
 * silently loses them, and they come back when the old account signs in again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CirclesDisappearanceTest {
    private val circle = Circle("c1", "New Life Fellowship", "small_group", "Cell group", CircleSettings(allowedTypes = listOf(PostType.Prayer)), 5)

    private fun setup(): Triple<CirclesRepository, FakeData, InMemoryLocalCircleStore> {
        val store = InMemoryLocalCircleStore().apply { addCircle("c1") }
        val data = FakeData()
        val api = FakeApi().apply { myCirclesResult = emptyList() }
        return Triple(CirclesRepository(api, data, FakeAuth(), store, clock = { 1L }), data, store)
    }

    @Test fun `a doesn't-exist read from this phone's cache keeps the circle, and only the server can say it is gone`() = runTest {
        val (repo, data, store) = setup()
        var last: CirclesListState? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.circles().collect { last = it } }
        data.circle.value = DataState.Ready(null, fromCache = true)
        advanceUntilIdle()
        assertEquals(listOf("c1"), store.circleIds())
        assertEquals(CirclesListState.Loading, last)

        data.circle.value = DataState.Ready(circle, fromCache = false)
        advanceUntilIdle()
        assertEquals(listOf("c1"), (last as CirclesListState.Ready).circles.map { it.id })

        data.circle.value = DataState.Ready(null, fromCache = false)
        advanceUntilIdle()
        assertTrue(store.circleIds().isEmpty())
        assertTrue(store.earlierCircleIds().isEmpty())
    }

    @Test fun `a circle a new sign-in can't read is kept aside and counted, then comes back when the old account signs in`() = runTest {
        val (repo, data, store) = setup()
        var last: CirclesListState? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.circles().collect { last = it } }
        data.circle.value = DataState.Failed(CirclesFailure(FailureKind.Rejected, "No access", "permission_denied"))
        advanceUntilIdle()
        assertEquals(listOf("c1"), store.earlierCircleIds())
        val shown = last as CirclesListState.Ready
        assertTrue(shown.circles.isEmpty())
        assertEquals(1, shown.earlier)

        // Signed in again with the account that owns it: the Worker lists it, so it is back and no longer "earlier".
        (repo.api as FakeApi).myCirclesResult = listOf("c1")
        data.circle.value = DataState.Ready(circle)
        repo.refresh(activatePush = false)
        advanceUntilIdle()
        assertEquals(listOf("c1"), store.circleIds())
        assertTrue(store.earlierCircleIds().isEmpty())
        assertEquals(0, (last as CirclesListState.Ready).earlier)
        assertEquals(listOf("c1"), (last as CirclesListState.Ready).circles.map { it.id })
    }

    @Test fun `create while signed out, sign in to the existing account, and the circle is still there`() = runTest {
        val auth = FakeAuth(uid = "guest-1", anonymous = true)
        val api = FakeApi()
        val store = InMemoryLocalCircleStore()
        val repo = CirclesRepository(api, FakeData(), auth, store, clock = { 1L })
        assertTrue(repo.createCircle("Made before sign-in", "small_group", "Circle", "Ann", CircleSettings(allowedTypes = listOf(PostType.Prayer)), "req-1") is CirclesResult.Ok)
        assertEquals(listOf("new-circle"), store.circleIds())

        // The Worker moves it to the account (adopt_guest.test.js checks ownership there); the account then lists both.
        api.adoptMoves = listOf("new-circle")
        api.myCirclesResult = listOf("old-circle", "new-circle")
        val r = repo.switchAccountKeepingCircles()

        assertTrue(r is LinkResult.Linked)
        assertEquals("other-phone-uid", auth.currentUid)
        assertEquals(listOf("guest:guest-1", "guest:guest-1"), api.adoptTokens)
        assertEquals(listOf("old-circle", "new-circle"), store.circleIds())
        assertTrue(store.earlierCircleIds().isEmpty())
    }

    @Test fun `when the circles can't be carried over, nothing switches and nothing is lost`() = runTest {
        val auth = FakeAuth(uid = "guest-1", anonymous = true)
        val api = FakeApi().apply { adoptFailure = CirclesFailure(FailureKind.NotFound, "This isn't available yet.", "unavailable") }
        val store = InMemoryLocalCircleStore().apply { addCircle("new-circle") }
        val repo = CirclesRepository(api, FakeData(), auth, store, clock = { 1L })

        val r = repo.switchAccountKeepingCircles()

        assertTrue(r is LinkResult.Failed)
        assertTrue((r as LinkResult.Failed).message.startsWith("The circles you made on this phone can't move to that account yet"))
        assertTrue("switch" !in auth.authCalls)
        assertEquals("guest-1", auth.currentUid)
        assertEquals(listOf("new-circle"), store.circleIds())
    }

    @Test fun `someone already signed in with Google switches without a carry-over`() = runTest {
        val auth = FakeAuth(uid = "g-1", anonymous = false)
        val api = FakeApi().apply { myCirclesResult = listOf("c9") }
        val repo = CirclesRepository(api, FakeData(), auth, InMemoryLocalCircleStore().apply { addCircle("c1") }, clock = { 1L })
        assertTrue(repo.switchAccountKeepingCircles() is LinkResult.Linked)
        assertTrue(api.adoptTokens.isEmpty())
    }
}
