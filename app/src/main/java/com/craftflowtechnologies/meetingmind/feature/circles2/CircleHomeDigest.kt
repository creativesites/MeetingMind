package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.runtime.Immutable
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.WorkStatus

/**
 * What Circle Home shows, worked out from data the screen already holds. Pure, so the order and the
 * catch-up ranking can be unit-tested. No counting of anyone's activity, no streaks.
 */
object CircleHomeDigest {
    private const val DAY = 86_400_000L

    @Immutable
    data class Faith(
        /** Today's devotional, or the newest one from the last week. */
        val devotional: FeedPost?,
        /** Open prayer requests, newest first, the ones I haven't prayed for leading. */
        val prayers: List<FeedPost>,
        val gatherings: List<CircleEvent>,
        /** The newest testimony, or an answered prayer when there's no testimony. */
        val testimony: FeedPost?,
        /** The newest reading-plan or study post. */
        val reading: FeedPost?,
        /** Leaders first, then everyone else by name. */
        val faces: List<Member>,
        val newSinceVisit: Int,
        /** True when nothing has been shared yet. Home shows a welcome instead of empty sections. */
        val quiet: Boolean
    )

    @Immutable
    data class Work(
        /** The next review, milestone or deadline. */
        val nextUp: CircleEvent?,
        val laterEvents: List<CircleEvent>,
        val tasksDone: Int,
        val tasksOpen: Int,
        /** Open blockers, decisions I haven't marked seen, and actions nobody owns. Each has one button. */
        val needsTeam: List<FeedPost>,
        /** Open actions due in the next seven days, or overdue, grouped by owner. */
        val dueThisWeek: List<Pair<String, List<FeedPost>>>,
        val latestBrief: FeedPost?,
        /** Source meetings, newest first: (meeting label, number of posts from it). */
        val sourceMeetings: List<Pair<String, Int>>,
        val decisions: Int,
        val newSinceVisit: Int,
        val quiet: Boolean
    ) {
        val progress: Float get() = if (tasksDone + tasksOpen == 0) 0f else tasksDone.toFloat() / (tasksDone + tasksOpen)
    }

    fun faith(state: CircleHomeUiState, now: Long): Faith {
        val live = state.posts.filter { !it.post.deleted }
        val devotional = live.filter { it.post.type == PostType.Devotional && it.post.createdAt >= now - 7 * DAY }
            .maxByOrNull { it.post.createdAt }
        val prayers = live.filter { it.post.type == PostType.Prayer && !it.post.isResolved }
            .sortedWith(compareBy<FeedPost> { it.prayedByMe }.thenByDescending { it.post.createdAt })
            .take(3)
        val testimony = live.filter { it.post.type == PostType.Testimony }.maxByOrNull { it.post.createdAt }
            ?: live.filter { it.post.type == PostType.Prayer && it.post.isResolved }.maxByOrNull { it.post.createdAt }
        val reading = live.filter { it.post.type == PostType.Reading || it.post.type == PostType.Study }
            .maxByOrNull { it.post.createdAt }
        return Faith(
            devotional = devotional,
            prayers = prayers,
            gatherings = upcoming(state.events, now).take(2),
            testimony = testimony,
            reading = reading,
            faces = faces(state.members),
            newSinceVisit = state.unreadFeedCount,
            quiet = live.isEmpty() && upcoming(state.events, now).isEmpty()
        )
    }

    fun work(state: CircleHomeUiState, now: Long): Work {
        val live = state.posts.filter { !it.post.deleted }
        val tasks = live.filter { it.post.type == PostType.Task }
        val open = tasks.filter { !it.post.isResolved }
        val blockers = live.filter { it.post.type == PostType.Blocker && !it.post.isResolved }
        val decisionsToSee = live.filter {
            it.post.type == PostType.Decision && !it.mine && it.myReaction != ReactionKind.Ack &&
                it.post.createdAt >= now - 14 * DAY
        }
        val unowned = open.filter { it.post.meta?.assigneeUid.isNullOrBlank() && it.post.meta?.assigneeName.isNullOrBlank() }
        val needsTeam = (blockers.sortedByDescending { it.post.createdAt } +
            decisionsToSee.sortedByDescending { it.post.createdAt } +
            unowned.sortedByDescending { it.post.createdAt }).take(4)
        val weekEnd = now + 7 * DAY
        val due = open.filter { p -> p.post.meta?.dueAt?.let { it <= weekEnd } == true }
            .sortedBy { it.post.meta?.dueAt }
        val byOwner = due.groupBy { p ->
            val m = p.post.meta
            when {
                m?.assigneeUid != null && m.assigneeUid == state.myUid -> "You"
                !m?.assigneeName.isNullOrBlank() -> m!!.assigneeName!!
                else -> "Nobody yet"
            }
        }.toList().sortedBy { (owner, _) -> if (owner == "You") 0 else if (owner == "Nobody yet") 2 else 1 }
        val meetings = live.filter { it.post.meta?.sourceKind == "meeting" }
            .groupBy { it.post.meta?.meetingDate?.takeIf { d -> d.isNotBlank() } ?: it.post.meta?.sourceId.orEmpty() }
            .filterKeys { it.isNotBlank() }
            .map { (k, v) -> Triple(k, v.size, v.maxOf { it.post.createdAt }) }
            .sortedByDescending { it.third }
            .take(3)
            .map { it.first to it.second }
        val events = upcoming(state.events, now)
        return Work(
            nextUp = events.firstOrNull(),
            laterEvents = events.drop(1).take(2),
            tasksDone = tasks.count { it.post.workStatus == WorkStatus.Done || it.post.isResolved },
            tasksOpen = open.size,
            needsTeam = needsTeam,
            dueThisWeek = byOwner,
            latestBrief = live.filter { it.post.type == PostType.Brief }.maxByOrNull { it.post.createdAt },
            sourceMeetings = meetings,
            decisions = live.count { it.post.type == PostType.Decision },
            newSinceVisit = state.unreadFeedCount,
            quiet = live.isEmpty() && events.isEmpty()
        )
    }

    /** One card in the catch-up deck. [reason] is the small line above the post ("Needs you", "Prayer request"). */
    @Immutable
    data class DeckCard(val post: FeedPost?, val event: CircleEvent?, val reason: String)

    /**
     * The catch-up deck (spec §9.3): at most five cards, each with one verb, ranked
     * 1) things that name me, 2) prayers, answers and decisions to see, 3) testimonies, briefs, devotionals,
     * 4) gatherings or reviews within a week. Ties go to the newest.
     */
    fun catchUp(state: CircleHomeUiState, now: Long, limit: Int = 5): List<DeckCard> {
        val since = if (state.lastVisitAt > 0L) state.lastVisitAt else now - DAY
        val me = state.myUid
        val myName = state.me?.displayName?.takeIf { it.isNotBlank() }
        val fresh = state.posts.filter { !it.mine && !it.post.deleted && it.post.createdAt > since }
        fun namesMe(p: FeedPost): Boolean {
            val m = p.post.meta
            if (me != null && m?.assigneeUid == me) return true
            if (myName != null && p.post.body.contains("@$myName", ignoreCase = true)) return true
            return false
        }
        fun rank(p: FeedPost): Int = when {
            namesMe(p) -> 1
            p.post.type == PostType.Prayer || p.post.type == PostType.Decision -> 2
            p.post.type == PostType.Testimony || p.post.type == PostType.Brief || p.post.type == PostType.Devotional -> 3
            else -> 4
        }
        fun reason(p: FeedPost): String = when {
            namesMe(p) -> "Needs you"
            p.post.type == PostType.Prayer && p.post.isResolved -> "Prayer answered"
            p.post.type == PostType.Prayer -> "Prayer request"
            p.post.type == PostType.Decision -> "Decision"
            p.post.type == PostType.Blocker -> "Blocker"
            else -> p.post.type.label
        }
        val postCards = fresh
            .sortedWith(compareBy<FeedPost> { rank(it) }.thenByDescending { it.post.createdAt })
            .map { DeckCard(it, null, reason(it)) }
        val ranked = postCards.filter { rankOf(it, ::rank) <= 3 }
        val eventCards = upcoming(state.events, now).filter { it.startsAt <= now + 7 * DAY }
            .map { DeckCard(null, it, if (state.circle?.isWork == true) "Coming up" else "Gathering") }
        val rest = postCards.filter { rankOf(it, ::rank) > 3 }
        return (ranked + eventCards + rest).take(limit)
    }

    private fun rankOf(c: DeckCard, rank: (FeedPost) -> Int): Int = c.post?.let(rank) ?: 4

    private fun upcoming(events: List<CircleEvent>, now: Long): List<CircleEvent> =
        events.filter { !it.cancelled && it.endsAt >= now }.sortedBy { it.startsAt }

    private fun faces(members: List<Member>): List<Member> =
        members.sortedWith(compareBy<Member> { if (it.role.isAdmin) 0 else 1 }.thenBy { it.displayName.lowercase() })

    /** "in 3 days", "tomorrow", "today", "2 days ago". */
    fun daysUntil(now: Long, then: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val d = java.time.temporal.ChronoUnit.DAYS.between(day(now), day(then))
        return when {
            d == 0L -> "today"
            d == 1L -> "tomorrow"
            d > 1L -> "in $d days"
            d == -1L -> "yesterday"
            else -> "${-d} days ago"
        }
    }
}
