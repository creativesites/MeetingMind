package com.craftflowtechnologies.meetingmind.core.circles2

import androidx.compose.runtime.Immutable

enum class Role(val wire: String, val label: String) {
    Owner("owner", "Owner"), Admin("admin", "Admin"), Moderator("moderator", "Moderator"), Member("member", "Member");

    val isAdmin: Boolean get() = this == Owner || this == Admin
    val canModerate: Boolean get() = isAdmin || this == Moderator

    companion object { fun from(wire: String?): Role = entries.firstOrNull { it.wire == wire } ?: Member }
}

/** What people post (FAITH_V2 section 2.2). [wire] is the value the Worker and Firestore use. */
enum class PostType(val wire: String, val label: String, val verb: String, val adminOnly: Boolean = false) {
    // Faith & general post types
    Prayer("prayer", "Prayer request", "Ask for prayer"),
    Testimony("testimony", "Testimony", "Share a testimony"),
    Achievement("achievement", "Achievement", "Share a win"),
    Study("study", "Study share", "Share from your study"),
    Encouragement("encouragement", "Encouragement", "Encourage the group"),
    Reading("reading", "Reading plan", "Post about the plan"),
    Announcement("announcement", "Announcement", "Make an announcement", adminOnly = true),
    // Work vertical post types
    Task("task", "Action item", "Assign an action"),
    Decision("decision", "Decision", "Record a decision"),
    Brief("brief", "Meeting brief", "Share meeting summary"),
    Standup("standup", "Status update", "Share an update"),
    Blocker("blocker", "Blocker", "Ask for unblock"),
    /** Free-form: any member, any circle, published at once. Not in any template list. */
    Note("note", "Free-form", "Share anything");

    val isWork: Boolean get() = this in setOf(Task, Decision, Brief, Standup, Blocker)

    companion object { fun from(wire: String?): PostType? = entries.firstOrNull { it.wire == wire } }
}

enum class ReactionKind(val wire: String, val emoji: String, val label: String) {
    // Faith & general reactions
    Praying("praying", "🙏", "Praying"), Amen("amen", "Amen", "Amen"), Heart("heart", "❤️", "Love"), Celebrate("celebrate", "🎉", "Celebrate"),
    // Work vertical reactions
    Ack("ack", "👍", "On it"), Approved("approved", "✅", "Approved"), Priority("priority", "🔥", "Urgent"), Reviewing("reviewing", "👀", "Looking"), Win("win", "🎉", "Win");

    companion object { fun from(wire: String?): ReactionKind? = entries.firstOrNull { it.wire == wire } }
}

@Immutable
data class CircleSettings(
    val allowedTypes: List<PostType>,
    val whoCanInvite: WhoCanInvite = WhoCanInvite.Admins,
    val prayerApproval: Boolean = true,
    /** When on, a join waits for an admin (the Worker keeps a join request). Off keeps today's behaviour. */
    val joinApproval: Boolean = false,
    val reactions: List<ReactionKind> = ReactionKind.entries,
    val guidelines: String = "",
    val space: String = "faith"
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
    val vocab: List<String>,
    val space: String = "faith"
) {
    val isWork: Boolean get() = space.equals("work", ignoreCase = true)

    companion object {
        private val everyType = PostType.entries.toList()
        val faithTemplates: List<CircleTemplate> = listOf(
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

        val workTemplates: List<CircleTemplate> = listOf(
            CircleTemplate("project_team", "Project team", "A team collaborating on deliverables, decisions and tasks.",
                listOf(PostType.Task, PostType.Decision, PostType.Brief, PostType.Standup, PostType.Announcement, PostType.Note),
                listOf("Team", "Project", "Squad"), space = "work"),
            CircleTemplate("client_engagement", "Client engagement", "External client or partner collaboration workspace.",
                listOf(PostType.Decision, PostType.Brief, PostType.Task, PostType.Announcement, PostType.Note),
                listOf("Project", "Engagement", "Team"), space = "work"),
            CircleTemplate("exec_board", "Leadership & Board", "Executive team, board or advisory committee.",
                listOf(PostType.Decision, PostType.Brief, PostType.Announcement, PostType.Note),
                listOf("Committee", "Board", "Leadership"), space = "work"),
            CircleTemplate("department", "Department", "Functional department or division sharing updates.",
                listOf(PostType.Announcement, PostType.Standup, PostType.Task, PostType.Decision, PostType.Note),
                listOf("Department", "Division", "Team"), space = "work"),
            CircleTemplate("custom_work", "Custom work group", "Configure your own collaborative team space.",
                listOf(PostType.Task, PostType.Decision, PostType.Brief, PostType.Standup, PostType.Announcement, PostType.Note),
                listOf("Team", "Group", "Circle"), space = "work")
        )

        val all: List<CircleTemplate> get() = faithTemplates
        val allTemplates: List<CircleTemplate> get() = faithTemplates + workTemplates

        fun forSpace(space: String?): List<CircleTemplate> =
            if (space.equals("work", ignoreCase = true)) workTemplates else faithTemplates

        fun byId(id: String?): CircleTemplate =
            (faithTemplates + workTemplates).firstOrNull { it.id == id } ?: faithTemplates.last()
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
    val ownerUid: String = "",
    val description: String = "",
    val pinnedPostId: String? = null,
    /** Storage path of the cover photo, once one is set. */
    val coverPath: String? = null,
    val pinnedMessageId: String? = null,
    val space: String = "faith"
) {
    val isWork: Boolean get() = space.equals("work", ignoreCase = true) || template in setOf("project_team", "client_engagement", "exec_board", "department", "custom_work")
    /** "Cell group" or "Team" for the header: the group's own word, never a generic one. */
    val groupWord: String get() = vocab.ifBlank { if (isWork) "Team" else "Circle" }
}

/** [title] is a display label such as "Pastor". It never changes what the person may do; [role] does. */
@Immutable
/** [avatarVersion] is null when the person has no profile photo. The photo itself is fetched by uid and version. */
data class Member(val uid: String, val displayName: String, val role: Role, val muted: Boolean = false, val joinedAt: Long = 0L, val title: String? = null, val lastReadAt: Long = 0L, val avatarVersion: Long? = null)

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
    val editedAt: Long? = null,
    /** Photos attached to this post (at most 4). */
    val attachments: List<MediaAttachment> = emptyList()
)

/** A prayer request waiting for an admin. Carries no author. */
@Immutable
data class PendingPost(val id: String, val type: PostType, val body: String, val verseRef: String?, val anonymous: Boolean, val createdAt: Long)

@Immutable
data class PostUpdate(val id: String, val body: String, val createdAt: Long)

@Immutable
data class Comment(
    val id: String,
    /** Null for a comment by the hidden author of an anonymous request: the Worker wrote it and never stored a uid. */
    val authorUid: String?,
    val authorName: String,
    val body: String,
    val parentId: String?,
    val createdAt: Long,
    /** True when the anonymous request's author wrote this (shown as "Requester"). */
    val byRequester: Boolean = false
)

enum class MessageKind { Text, Card, Reply, Poll, Celebration, Chain, Image, Video, Voice, File, Unknown;
    companion object {
        fun from(wire: String?): MessageKind = when (wire) {
            "text" -> Text; "card" -> Card; "reply" -> Reply; "poll" -> Poll; "celebration" -> Celebration; "chain" -> Chain
            "image" -> Image; "video" -> Video; "voice" -> Voice; "file" -> File
            else -> Unknown
        }
    }
}

enum class DeliveryState { Pending, Sent, Failed }

/** A Create card shared into chat: template id + text + mood, rendered on each phone (no image upload). */
@Immutable
data class CardPayload(
    val templateId: String,
    val text: String,
    val mood: String? = null,
    /** Reference of a verse on the card ("John 3:16 · NIV"), and its words. Only cards made in the Create studio carry them. */
    val verseRef: String? = null,
    val verseText: String? = null
)

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
    val deleted: Boolean = false,
    /** Image, video, voice or file messages only. Written by the Worker (sendMessage). */
    val attachment: MediaAttachment? = null,
    /** The id of the message this was forwarded from, when it was forwarded. */
    val forwardedFrom: String? = null,
    /** Uids of members mentioned with @name in the text. */
    val mentions: List<String> = emptyList(),
    /** Client-side delivery state for optimistic messages. */
    val deliveryState: DeliveryState = DeliveryState.Sent
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

/** Event fields as shown. [startsAt] and [endsAt] are epoch milliseconds (UTC); [timeZone] is the IANA zone to show them in. */
@Immutable
data class CircleEvent(
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val onlineUrl: String?,
    val startsAt: Long,
    val endsAt: Long,
    val timeZone: String,
    val status: String,
    val createdByName: String
) {
    val cancelled: Boolean get() = status == "cancelled"
}

enum class RsvpAnswer(val wire: String, val label: String) {
    Going("going", "Going"), Maybe("maybe", "Maybe"), No("no", "Can't go");

    companion object { fun from(wire: String?): RsvpAnswer? = entries.firstOrNull { it.wire == wire } }
}

@Immutable
data class Rsvp(val uid: String, val displayName: String, val answer: RsvpAnswer)

/** What an admin writes for an event. The Worker checks times, zone and link. */
@Immutable
data class EventDraft(
    val title: String,
    val description: String,
    val location: String,
    val onlineUrl: String?,
    val startsAt: Long,
    val endsAt: Long,
    val timeZone: String
)

/** Someone waiting to be let in (joinApproval on). Admins see these; the Worker decides them. */
@Immutable
data class JoinRequest(val uid: String, val displayName: String, val createdAt: Long)

/** The result of a join: the circle, and whether the person is still waiting for an admin. */
data class JoinOutcome(val circleId: String, val pending: Boolean)

/** The kinds of file the Worker accepts. Limits match the Worker (docs/CIRCLES_BACKEND_HANDOFF.md section 6). */
enum class MediaKind(val wire: String, val maxBytes: Long, val mimes: List<String>) {
    Image("image", 10_000_000L, listOf("image/jpeg", "image/png", "image/webp")),
    Video("video", 50_000_000L, listOf("video/mp4", "video/webm")),
    Voice("voice", 25_000_000L, listOf("audio/mp4", "audio/aac", "audio/ogg")),
    File("file", 25_000_000L, listOf("application/pdf"));

    fun accepts(mime: String): Boolean = mime in mimes
}

/** A stored file as a message or post refers to it. [path] is `circles/{circleId}/media/{uuid}`. */
@Immutable
data class MediaAttachment(val path: String, val mime: String, val bytes: Long, val durationMs: Long? = null, val name: String? = null)

data class UploadTicket(val ticketId: String, val path: String)

/** Someone typing right now. Stale entries are dropped by the view model. */
@Immutable
data class TypingUser(val uid: String, val name: String, val updatedAt: Long)

