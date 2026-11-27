package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.CircleSettings
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostMeta
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CircleHomeDigestTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private val faith = Circle("c1", "New Life Fellowship", "cell_group", "", CircleSettings(allowedTypes = PostType.entries.filter { !it.isWork }), 5, space = "faith")
    private val work = Circle("w1", "Acme Integration Team", "project_team", "", CircleSettings(allowedTypes = PostType.entries), 3, space = "work")
    private val me = Member("me", "Ann Mensah", Role.Member)
    private val grace = Member("g", "Grace Banda", Role.Admin)
    private val daniel = Member("d", "Daniel Phiri", Role.Member)

    private fun fp(id: String, type: PostType, ago: Long, mine: Boolean = false, prayed: Boolean = false, answered: Boolean = false,
                   reaction: ReactionKind? = null, meta: PostMeta? = null, body: String = "post $id") =
        FeedPost(Post(id, type, body, createdAt = now - ago, answered = answered, authorName = "Grace", meta = meta), mine, prayed, reaction)

    private fun event(id: String, inDays: Long, cancelled: Boolean = false) =
        CircleEvent(id, "Event $id", "", "Hall", null, now + inDays * day, now + inDays * day + 3_600_000L, "UTC", if (cancelled) "cancelled" else "scheduled", "Grace")

    private fun state(circle: Circle, posts: List<FeedPost>, events: List<CircleEvent> = emptyList(), lastVisit: Long = 0L, unread: Int = 0) =
        CircleHomeUiState(loading = false, circle = circle, me = me, members = listOf(daniel, me, grace), posts = posts, events = events, lastVisitAt = lastVisit, unreadFeedCount = unread)

    @Test fun `faith home picks the recent devotional, unprayed prayers first, and leaders first`() {
        val s = state(faith, listOf(
            fp("dev_old", PostType.Devotional, 9 * day),
            fp("dev", PostType.Devotional, 2 * 3_600_000L),
            fp("p1", PostType.Prayer, 3_600_000L, prayed = true),
            fp("p2", PostType.Prayer, 2 * 3_600_000L),
            fp("p3", PostType.Prayer, 3 * day, answered = true),
            fp("t", PostType.Testimony, day),
            fp("r", PostType.Reading, day)
        ), events = listOf(event("e2", 5), event("e1", 2), event("x", 1, cancelled = true)))
        val d = CircleHomeDigest.faith(s, now)
        assertEquals("dev", d.devotional?.post?.id)
        assertEquals(listOf("p2", "p1"), d.prayers.map { it.post.id })
        assertEquals(listOf("e1", "e2"), d.gatherings.map { it.id })
        assertEquals("t", d.testimony?.post?.id)
        assertEquals("r", d.reading?.post?.id)
        assertEquals("g", d.faces.first().uid)
        assertTrue(!d.quiet)
    }

    @Test fun `faith home with nothing shared is quiet and an answered prayer stands in for a testimony`() {
        assertTrue(CircleHomeDigest.faith(state(faith, emptyList()), now).quiet)
        val d = CircleHomeDigest.faith(state(faith, listOf(fp("p", PostType.Prayer, day, answered = true))), now)
        assertEquals("p", d.testimony?.post?.id)
        assertNull(d.devotional)
    }

    @Test fun `work overview lists blockers, unseen decisions and unowned actions, and groups due actions by owner`() {
        val s = state(work, listOf(
            fp("b", PostType.Blocker, day, meta = PostMeta(blockedOn = "Legal")),
            fp("dec_seen", PostType.Decision, day, reaction = ReactionKind.Ack),
            fp("dec", PostType.Decision, 2 * day),
            fp("t_mine", PostType.Task, day, meta = PostMeta(assigneeUid = "me", assigneeName = "Ann", dueAt = now + 2 * day)),
            fp("t_sarah", PostType.Task, day, meta = PostMeta(assigneeName = "Sarah Chen", dueAt = now - day)),
            fp("t_later", PostType.Task, day, meta = PostMeta(assigneeName = "James", dueAt = now + 20 * day)),
            fp("t_none", PostType.Task, day),
            FeedPost(Post("t_done", PostType.Task, "done", createdAt = now - day, status = "answered"), false, false),
            fp("brief", PostType.Brief, day, meta = PostMeta(sourceKind = "meeting", meetingDate = "12 Nov"))
        ), events = listOf(event("go", 12)))
        val w = CircleHomeDigest.work(s, now)
        assertEquals(listOf("b", "dec", "t_none"), w.needsTeam.map { it.post.id })
        assertEquals(listOf("You", "Sarah Chen"), w.dueThisWeek.map { it.first })
        assertEquals(1, w.tasksDone)
        assertEquals(4, w.tasksOpen)
        assertEquals("brief", w.latestBrief?.post?.id)
        assertEquals(listOf("12 Nov" to 1), w.sourceMeetings)
        assertEquals("go", w.nextUp?.id)
        assertEquals(0.2f, w.progress, 0.001f)
    }

    @Test fun `catch-up ranks what names me first, then prayers and decisions, caps at five, skips my own and old posts`() {
        val s = state(work, listOf(
            fp("old", PostType.Decision, 5 * day),
            fp("note", PostType.Note, 3_600_000L),
            fp("brief", PostType.Brief, 2 * 3_600_000L),
            fp("dec", PostType.Decision, 3 * 3_600_000L),
            fp("mine", PostType.Decision, 3_600_000L, mine = true),
            fp("assigned", PostType.Task, 4 * 3_600_000L, meta = PostMeta(assigneeUid = "me")),
            fp("mention", PostType.Note, 5 * 3_600_000L, body = "@Ann Mensah can you check?")
        ), events = listOf(event("review", 3)), lastVisit = now - 2 * day)
        val deck = CircleHomeDigest.catchUp(s, now)
        assertEquals(5, deck.size)
        assertEquals(listOf("assigned", "mention", "dec", "brief"), deck.take(4).map { it.post?.post?.id })
        assertEquals("Needs you", deck[0].reason)
        assertEquals("review", deck[4].event?.id)
    }

    @Test fun `faces line names people, never counts what they did`() {
        assertEquals("You, Grace and 1 other", facesLine(listOf(grace, me, daniel), "me"))
        assertEquals("You and Grace", facesLine(listOf(grace, me), "me"))
        assertEquals("Grace and Daniel", facesLine(listOf(grace, daniel), "me"))
        assertEquals("Nobody here yet", facesLine(emptyList(), "me"))
    }

    @Test fun `days until reads naturally`() {
        assertEquals("today", CircleHomeDigest.daysUntil(now, now + 1_000L, java.time.ZoneOffset.UTC))
        assertEquals("tomorrow", CircleHomeDigest.daysUntil(now, now + day, java.time.ZoneOffset.UTC))
        assertEquals("in 12 days", CircleHomeDigest.daysUntil(now, now + 12 * day, java.time.ZoneOffset.UTC))
        assertEquals("2 days ago", CircleHomeDigest.daysUntil(now, now - 2 * day, java.time.ZoneOffset.UTC))
    }

    @Test fun `daily rhythm only offers times outside quiet hours and never counts`() {
        assertTrue(CircleRhythm.slots.all { it.minuteOfDay in (7 * 60)..(21 * 60) })
        val (title, body) = CircleRhythm.text("New Life Fellowship", isWork = false)
        assertEquals("A moment with New Life Fellowship", title)
        assertTrue(body.none { it.isDigit() })
        val at = LocalDateTime.of(2026, 11, 16, 8, 0)
        assertEquals(4 * 3_600_000L + 30 * 60_000L, CircleRhythm.delayUntil(at, 12 * 60 + 30))
        assertEquals(23 * 3_600_000L, CircleRhythm.delayUntil(at, 7 * 60))
    }
}
