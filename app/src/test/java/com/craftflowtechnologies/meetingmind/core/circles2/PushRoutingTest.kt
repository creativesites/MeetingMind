package com.craftflowtechnologies.meetingmind.core.circles2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deep-link routing for the Worker's push types and our own notification links. */
class PushRoutingTest {
    private fun data(kind: String?, circle: String? = "c1", post: String? = null) =
        mapOf("kind" to kind, "circleId" to circle, "postId" to post)

    @Test fun `a new post opens that post in its circle`() {
        assertEquals(CircleTarget("c1", CircleDestination.Feed, "p9"), PushRouting.fromData(data("post", post = "p9")))
    }

    @Test fun `approved and answered open the post, pending and rejected open the feed`() {
        assertEquals(CircleTarget("c1", CircleDestination.Feed, "p9"), PushRouting.fromData(data("approved", post = "p9")))
        assertEquals(CircleTarget("c1", CircleDestination.Feed, "p9"), PushRouting.fromData(data("answered", post = "p9")))
        assertEquals(CircleTarget("c1", CircleDestination.Feed), PushRouting.fromData(data("pending")))
        assertEquals(CircleTarget("c1", CircleDestination.Feed), PushRouting.fromData(data("rejected")))
    }

    @Test fun `chat pushes open the chat tab`() {
        for (k in listOf("poll", "chain", "celebration")) assertEquals(CircleTarget("c1", CircleDestination.Chat), PushRouting.fromData(data(k)))
    }

    @Test fun `the daily digest has no circle and opens the list`() {
        assertEquals(CircleTarget(null, CircleDestination.CirclesList), PushRouting.fromData(mapOf("kind" to "digest")))
    }

    @Test fun `a bad circle id or an unknown kind falls back to the list, and data without a kind is not ours`() {
        assertEquals(CircleTarget(null, CircleDestination.CirclesList), PushRouting.fromData(data("post", circle = "../etc")))
        assertEquals(CircleTarget(null, CircleDestination.CirclesList), PushRouting.fromData(data("something-new")))
        assertNull(PushRouting.fromData(mapOf("foo" to "bar")))
    }

    @Test fun `a post id that is not an id is dropped`() {
        assertEquals(CircleTarget("c1", CircleDestination.Feed, null), PushRouting.fromData(data("post", post = "a b/c")))
    }

    @Test fun `our notification links round-trip`() {
        for (t in listOf(
            CircleTarget("c1", CircleDestination.Feed, "p9"), CircleTarget("c1", CircleDestination.Feed),
            CircleTarget("c1", CircleDestination.Chat), CircleTarget(null, CircleDestination.CirclesList)
        )) assertEquals(t, PushRouting.fromUri(PushRouting.uri(t)))
        assertEquals("meetingmind://c/open?circle=c1&post=p9", PushRouting.uri(CircleTarget("c1", CircleDestination.Feed, "p9")))
    }

    @Test fun `an invite link is not an open link`() {
        assertNull(PushRouting.fromUri("meetingmind://c/GRACE-7K2Q"))
        assertNull(PushRouting.fromUri("meetingmind://c/openGRACE"))
        assertNull(PushRouting.fromUri(null))
        assertEquals("GRACE-7K2Q", InviteCodes.fromLink("meetingmind://c/GRACE-7K2Q"))
    }

    @Test fun `notification text never carries post words`() {
        for (k in PushKind.entries) {
            val (title, body) = PushRouting.fallbackText(k)
            assertTrue(title.isNotBlank() && body.isNotBlank())
        }
        assertFalse(PushKind.from("post") == PushKind.Unknown)
        assertEquals(PushKind.Unknown, PushKind.from(null))
        assertEquals(PushKind.Unknown, PushKind.from(""))
    }

    @Test fun `each notification gets its own id per kind, circle and post`() {
        val a = PushRouting.notificationId(data("post", post = "p1"))
        assertEquals(a, PushRouting.notificationId(data("post", post = "p1")))
        assertFalse(a == PushRouting.notificationId(data("post", post = "p2")))
    }
}
