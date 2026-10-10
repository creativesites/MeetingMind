package com.craftflowtechnologies.meetingmind.core.circles2

import androidx.compose.runtime.Immutable

enum class Role(val wire: String, val label: String) {
    Owner("owner", "Owner"), Admin("admin", "Admin"), Member("member", "Member");

    val isAdmin: Boolean get() = this == Owner || this == Admin

    companion object { fun from(wire: String?): Role = entries.firstOrNull { it.wire == wire } ?: Member }
}

/** What people post (FAITH_V2 section 2.2). [wire] is the value the Worker and Firestore use. */
enum class PostType(val wire: String, val label: String, val verb: String, val adminOnly: Boolean = false) {
    Prayer("prayer", "Prayer request", "Ask for prayer"),
    Testimony("testimony", "Testimony", "Share a testimony"),
    Achievement("achievement", "Achievement", "Share a win"),
    Study("study", "Study share", "Share from your study"),
    Encouragement("encouragement", "Encouragement", "Encourage the group"),
    Reading("reading", "Reading plan", "Post about the plan"),
    Announcement("announcement", "Announcement", "Make an announcement", adminOnly = true);

    companion object { fun from(wire: String?): PostType? = entries.firstOrNull { it.wire == wire } }
}

enum class ReactionKind(val wire: String, val emoji: String, val label: String) {
    Praying("praying", "🙏", "Praying"), Amen("amen", "Amen", "Amen"), Heart("heart", "❤️", "Love"), Celebrate("celebrate", "🎉", "Celebrate");

    companion object { fun from(wire: String?): ReactionKind? = entries.firstOrNull { it.wire == wire } }
}

@Immutable
data class CircleSettings(
    val allowedTypes: List<PostType>,
    val whoCanInvite: WhoCanInvite = WhoCanInvite.Admins,
    val prayerApproval: Boolean = true,
    val reactions: List<ReactionKind> = ReactionKind.entries,
    val guidelines: String = ""
)

enum class WhoCanInvite(val wire: String, val label: String) {
    Admins("admins", "Only admins"), Members("members", "Every member");

    companion object { fun from(wire: String?): WhoCanInvite = entries.firstOrNull { it.wire == wire } ?: Admins }
}

/** A template sets sensible defaults; the creator can change everything (section 2.1). */
@Immutable
data class CircleTemplate(
    val id: String,
    val label: String,
    val blurb: String,
    val types: List<PostType>,
    /** Words for the group. The first is the default; the creator may type their own. */
    val vocab: List<String>
) {
    companion object {
        private val everyType = PostType.entries.toList()
        val all: List<CircleTemplate> = listOf(
            CircleTemplate("small_group", "Small group", "A cell or home group that meets and prays together.",
                listOf(PostType.Prayer, PostType.Testimony, PostType.Study, PostType.Encouragement),
                listOf("Cell group", "Small group", "Home fellowship", "Life group")),
            CircleTemplate("prayer_partners", "Prayer partners", "Two to six people who pray for each other.",
                listOf(PostType.Prayer, PostType.Encouragement), listOf("Prayer partners")),
            CircleTemplate("bible_study", "Bible study", "Read and discuss together.",
                listOf(PostType.Study, PostType.Reading, PostType.Prayer), listOf("Bible study")),
            CircleTemplate("family", "Family", "Pray, celebrate and encourage at home.",
                listOf(PostType.Prayer, PostType.Testimony, PostType.Encouragement, PostType.Achievement), listOf("Family")),
            CircleTemplate("youth", "Youth or campus", "Everything on, including cards and fun.", everyType, listOf("Youth", "Campus group")),
            CircleTemplate("ministry_team", "Ministry team", "Announcements, prayer and study for a team.",
                listOf(PostType.Prayer, PostType.Announcement, PostType.Study), listOf("Team")),
            CircleTemplate("custom", "Custom", "You choose what to share.",
                listOf(PostType.Prayer, PostType.Testimony, PostType.Encouragement), listOf("Circle"))
        )

        fun byId(id: String?): CircleTemplate = all.firstOrNull { it.id == id } ?: all.last()
    }
}

@Immutable
data class Circle(
    val id: String,
    val name: String,
    val template: String,
    val vocab: String,
    val settings: CircleSettings,
    val memberCount: Int,
    val ownerUid: String = ""
) {
    /** "Cell group" for the header: the group's own word, never a generic one. */
    val groupWord: String get() = vocab.ifBlank { "Circle" }
}

@Immutable
data class Member(val uid: String, val displayName: String, val role: Role, val muted: Boolean = false, val joinedAt: Long = 0L)

@Immutable
data class PostCounts(val prayed: Int = 0, val comments: Int = 0, val updates: Int = 0, val reactions: Map<ReactionKind, Int> = emptyMap())

/** A published post. [authorUid] and [authorName] are null for anonymous posts: the server never stores them. */
@Immutable
data class Post(
    val id: String,
    val type: PostType,
    val body: String,
    val verseRef: String? = null,
    val anonymous: Boolean = false,
    val answered: Boolean = false,
    val deleted: Boolean = false,
    val authorUid: String? = null,
    val authorName: String? = null,
    val counts: PostCounts = PostCounts(),
    val createdAt: Long = 0L,
    val editedAt: Long? = null
)

/** A prayer request waiting for an admin. Carries no author. */
@Immutable
data class PendingPost(val id: String, val type: PostType, val body: String, val verseRef: String?, val anonymous: Boolean, val createdAt: Long)

@Immutable
data class PostUpdate(val id: String, val body: String, val createdAt: Long)

@Immutable
data class Comment(val id: String, val authorUid: String, val authorName: String, val body: String, val parentId: String?, val createdAt: Long)

enum class MessageKind { Text, Card, Reply, Poll, Celebration, Chain, Unknown;
    companion object {
        fun from(wire: String?): MessageKind = when (wire) {
            "text" -> Text; "card" -> Card; "reply" -> Reply; "poll" -> Poll; "celebration" -> Celebration; "chain" -> Chain
            else -> Unknown
        }
    }
}

/** A Create card shared into chat: template id + text + mood, rendered on each phone (no image upload). */
@Immutable
data class CardPayload(val templateId: String, val text: String, val mood: String? = null)

@Immutable
data class ChatMessage(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val kind: MessageKind,
    val text: String,
    val replyTo: String? = null,
    val card: CardPayload? = null,
    val pollId: String? = null,
    val chainId: String? = null,
    val celebration: String? = null,
    val companion: String? = null,
    val createdAt: Long = 0L,
    val editedAt: Long? = null,
    val deleted: Boolean = false
)

@Immutable
data class MessageReaction(val emoji: String, val count: Int, val mine: Boolean)

val CHAT_EMOJI: List<String> = listOf("🙏", "❤️", "😂", "🎉", "👍", "🔥")

@Immutable
data class PollOption(val id: String, val text: String)

@Immutable
data class Poll(val id: String, val question: String, val options: List<PollOption>, val multi: Boolean, val closed: Boolean, val createdBy: String)

/** A poll plus live votes (uid to the option ids chosen). */
@Immutable
data class PollState(val poll: Poll, val votes: Map<String, List<String>>, val myUid: String) {
    val voters: Int get() = votes.size
    fun count(optionId: String): Int = votes.values.count { optionId in it }
    val mine: List<String> get() = votes[myUid].orEmpty()
    /** Share of voters, 0..1, for the bar. */
    fun fraction(optionId: String): Float = if (voters == 0) 0f else count(optionId).toFloat() / voters
}

@Immutable
data class Chain(val id: String, val title: String, val postId: String?, val startsAt: Long, val endsAt: Long, val hours: Int, val createdByName: String)

@Immutable
data class Slot(val hour: Int, val uid: String, val name: String)

@Immutable
data class ChainState(val chain: Chain, val slots: List<Slot>, val myUid: String, val now: Long) {
    val claimed: Int get() = slots.map { it.hour }.toSet().size
    val progress: Float get() = if (chain.hours == 0) 0f else claimed.toFloat() / chain.hours
    val ended: Boolean get() = now >= chain.endsAt
    fun slotFor(hour: Int): Slot? = slots.firstOrNull { it.hour == hour }
    val currentHour: Int get() = (((now - chain.startsAt) / 3_600_000L).toInt()).coerceIn(0, chain.hours - 1)
}

@Immutable
data class Invite(val code: String, val expiresAt: String?, val maxUses: Int?)

/** What a member can celebrate about themselves (the Worker refuses to celebrate anyone else). */
enum class CelebrationKind(val wire: String, val label: String) {
    Birthday("birthday", "It's my birthday"), Answered("answered", "My prayer was answered"),
    Streak("streak", "I hit a streak"), Milestone("milestone", "I reached a milestone")
}

/** The companion forms the Worker accepts, by id. */
val COMPANION_IDS: List<String> = listOf("zuri", "nas", "wren", "page")
