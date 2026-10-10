package com.craftflowtechnologies.meetingmind.core.circles2

import com.google.firebase.Timestamp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** The anonymous author's own comments must never carry a uid: they go through the Worker. */
class AnonymousCommentTest {
    @Test fun `a post I wrote anonymously is remembered on this phone`() = runBlocking {
        val store = InMemoryLocalCircleStore()
        val api = FakeApi().apply { createdPending = false }
        val repo = repoWith(api, store = store)
        val anon = (repo.createPost(CreatePostRequest("c1", PostType.Prayer, "Please pray", anonymous = true)) as CirclesResult.Ok).value
        val named = (repo.createPost(CreatePostRequest("c1", PostType.Prayer, "Named", anonymous = false)) as CirclesResult.Ok).value
        assertEquals(setOf(anon.postId), store.anonymousPostIds("c1"))
        assertEquals(setOf(anon.postId, named.postId), store.myPostIds("c1"))
    }

    @Test fun `commenting on my own anonymous request goes through the Worker with no uid`() = runBlocking {
        val store = InMemoryLocalCircleStore().apply { addAnonymousPost("c1", "p1") }
        val api = FakeApi(); val data = FakeData()
        val repo = repoWith(api, data, store = store)
        val r = repo.comment("c1", "p1", "Ann", "  Thank you all  ", null)
        assertTrue(r is CirclesResult.Ok)
        assertEquals(Triple("p1", "Thank you all", null), api.lastCommentAsAuthor)
        assertTrue("never written by the client", data.writes.none { it == "addComment" })
        assertTrue("counter sync still runs", "syncCounts" in api.calls)
    }

    @Test fun `commenting anywhere else is a normal client comment`() = runBlocking {
        val store = InMemoryLocalCircleStore().apply { addAnonymousPost("c1", "mine") }
        val api = FakeApi(); val data = FakeData()
        val repo = repoWith(api, data, store = store)
        repo.comment("c1", "someone-elses", "Ann", "Praying", "parent1")
        assertEquals(listOf("addComment"), data.writes)
        assertNull(api.lastCommentAsAuthor)
        // the same post id in another circle is not mine
        repo.comment("c2", "mine", "Ann", "Hi", null)
        assertEquals(listOf("addComment", "addComment"), data.writes)
    }

    @Test fun `a failed Worker comment is reported and not silently written by the client`() = runBlocking {
        val store = InMemoryLocalCircleStore().apply { addAnonymousPost("c1", "p1") }
        val api = FakeApi().apply { nextFailure = CirclesFailure.offline }
        val data = FakeData()
        val r = repoWith(api, data, store = store).comment("c1", "p1", "Ann", "Hello", null)
        assertEquals(FailureKind.Offline, r.failureOrNull?.kind)
        assertTrue(data.writes.isEmpty())
    }

    @Test fun `an empty comment is refused before any call`() = runBlocking {
        val api = FakeApi()
        val r = repoWith(api).comment("c1", "p1", "Ann", "   ", null)
        assertEquals(FailureKind.Rejected, r.failureOrNull?.kind)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun `a Worker-written comment with the author flag shows as the requester`() {
        val c = CirclesParsing.comment("cm1", mapOf("body" to "Thank you", "displayName" to "Requester", "author" to true, "createdAt" to Timestamp(Date(7))))
        assertEquals("Requester", c?.authorName); assertNull(c?.authorUid); assertTrue(c?.byRequester == true)
        // a comment with neither a uid nor the flag is still dropped, and the flag can't be faked onto a uid comment
        assertNull(CirclesParsing.comment("cm2", mapOf("body" to "x")))
        val named = CirclesParsing.comment("cm3", mapOf("authorUid" to "u1", "displayName" to "Ann", "body" to "x", "author" to true))
        assertEquals("Ann", named?.authorName); assertFalse(named?.byRequester == true)
    }
}
