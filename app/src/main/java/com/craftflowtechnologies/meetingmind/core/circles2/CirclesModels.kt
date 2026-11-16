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
    Devotional("devotional", "Devotional", "Share a devotional"),
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
    val isGeneral: Boolean get() = space.equals("general", ignoreCase = true)

    companion object {
        private val everyType = PostType.entries.toList()
        val generalTemplates: List<CircleTemplate> = listOf(
            CircleTemplate("friends_group", "Friends & family", "Keep in touch, celebrate wins and share everyday updates.",
                listOf(PostType.Achievement, PostType.Encouragement, PostType.Reading, PostType.Announcement, PostType.Note),
                listOf("Group", "Circle", "Crew", "Squad"), space = "general"),
            CircleTemplate("community_club", "Club or community", "Hobby groups, neighborhood clubs, associations and shared interests.",
                listOf(PostType.Announcement, PostType.Achievement, PostType.Encouragement, PostType.Reading, PostType.Note),
                listOf("Club", "Circle", "Community", "Group"), space = "general"),
            CircleTemplate("book_club", "Book club", "Reading plans, chapter reflections, discussion notes and recommendations.",
                listOf(PostType.Reading, PostType.Encouragement, PostType.Announcement, PostType.Achievement, PostType.Note),
                listOf("Book club", "Reading group", "Circle"), space = "general"),
            CircleTemplate("custom_general", "Custom group", "Configure your own community or personal circle.",
                listOf(PostType.Achievement, PostType.Encouragement, PostType.Reading, PostType.Announcement, PostType.Note),
                listOf("Group", "Circle", "Club"), space = "general")
        )

        val faithTemplates: List<CircleTemplate> = listOf(
            CircleTemplate("small_group", "Small group", "A cell or home group that meets and prays together.",
                listOf(PostType.Prayer, PostType.Testimony, PostType.Devotional, PostType.Study, PostType.Encouragement),
                listOf("Cell group", "Small group", "Home fellowship", "Life group")),
            CircleTemplate("prayer_partners", "Prayer partners", "Two to six people who pray for each other.",
                listOf(PostType.Prayer, PostType.Encouragement), listOf("Prayer partners")),
            CircleTemplate("bible_study", "Bible study", "Read and discuss together.",
                listOf(PostType.Devotional, PostType.Study, PostType.Reading, PostType.Prayer), listOf("Bible study")),
            CircleTemplate("family", "Family", "Pray, celebrate and encourage at home.",
                listOf(PostType.Prayer, PostType.Testimony, PostType.Devotional, PostType.Encouragement, PostType.Achievement), listOf("Family")),
            CircleTemplate("youth", "Youth or campus", "Everything on, including cards and fun.", everyType, listOf("Youth", "Campus group")),
            CircleTemplate("ministry_team", "Ministry team", "Announcements, prayer, devotionals and study for a team.",
                listOf(PostType.Prayer, PostType.Announcement, PostType.Devotional, PostType.Study), listOf("Team")),
            CircleTemplate("custom", "Custom", "You choose what to share.",
                listOf(PostType.Prayer, PostType.Testimony, PostType.Devotional, PostType.Encouragement), listOf("Circle"))
        )

        val workTemplates: List<CircleTemplate> = listOf(
            CircleTemplate("project_team", "Project team", "A team collaborating on deliverables, decisions and tasks.",
                listOf(PostType.Task, PostType.Decision, PostType.Brief, PostType.Standup, PostType.Blocker, PostType.Announcement, PostType.Note),
                listOf("Team", "Project", "Squad"), space = "work"),
            CircleTemplate("client_engagement", "Client engagement", "External client or partner collaboration workspace.",
                listOf(PostType.Decision, PostType.Brief, PostType.Task, PostType.Blocker, PostType.Announcement, PostType.Note),
                listOf("Project", "Engagement", "Team"), space = "work"),
            CircleTemplate("exec_board", "Leadership & Board", "Executive team, board or advisory committee.",
                listOf(PostType.Decision, PostType.Brief, PostType.Announcement, PostType.Note),
                listOf("Committee", "Board", "Leadership"), space = "work"),
            CircleTemplate("department", "Department", "Functional department or division sharing updates.",
                listOf(PostType.Announcement, PostType.Standup, PostType.Task, PostType.Decision, PostType.Blocker, PostType.Note),
                listOf("Department", "Division", "Team"), space = "work"),
            CircleTemplate("custom_work", "Custom work group", "Configure your own collaborative team space.",
                listOf(PostType.Task, PostType.Decision, PostType.Brief, PostType.Standup, PostType.Blocker, PostType.Announcement, PostType.Note),
                listOf("Team", "Group", "Circle"), space = "work")
        )

        val learningTemplates: List<CircleTemplate> = listOf(
            CircleTemplate("study_group", "Study group", "Classmates sharing study guides, practice quizzes and session notes.",
                listOf(PostType.Study, PostType.Announcement, PostType.Encouragement, PostType.Achievement, PostType.Note),
                listOf("Study group", "Cohort", "Study circle", "Class"), space = "learning"),
            CircleTemplate("course_cohort", "Course cohort", "A class or section working through a syllabus and lectures together.",
                listOf(PostType.Study, PostType.Announcement, PostType.Note),
                listOf("Course", "Cohort", "Class", "Section"), space = "learning"),
            CircleTemplate("exam_prep_room", "Exam prep room", "Intensive revision, past paper discussions, and flashcard drills.",
                listOf(PostType.Study, PostType.Announcement, PostType.Achievement, PostType.Note),
                listOf("Prep room", "Revision squad", "Study group"), space = "learning"),
            CircleTemplate("custom_learning", "Custom study group", "Configure your own collaborative academic space.",
                listOf(PostType.Study, PostType.Announcement, PostType.Encouragement, PostType.Achievement, PostType.Note),
                listOf("Group", "Circle", "Cohort"), space = "learning")
        )

        val all: List<CircleTemplate> get() = faithTemplates
        val allTemplates: List<CircleTemplate> get() = generalTemplates + faithTemplates + workTemplates + learningTemplates

        fun forSpace(space: String?): List<CircleTemplate> = when {
            space.equals("general", ignoreCase = true) || space.equals("community", ignoreCase = true) -> generalTemplates
            space.equals("work", ignoreCase = true) -> workTemplates
            space.equals("learning", ignoreCase = true) -> learningTemplates
            else -> faithTemplates
        }

        fun byId(id: String?): CircleTemplate =
            allTemplates.firstOrNull { it.id == id } ?: faithTemplates.last()
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
    val isGeneral: Boolean get() = space.equals("general", ignoreCase = true) || template in setOf("friends_group", "community_club", "book_club", "custom_general")
    val isWork: Boolean get() = space.equals("work", ignoreCase = true) || template in setOf("project_team", "client_engagement", "exec_board", "department", "custom_work")
    val isLearning: Boolean get() = space.equals("learning", ignoreCase = true) || template in setOf("study_group", "course_cohort", "exam_prep_room", "custom_learning")
    /** "Cell group" or "Team" for the header: the group's own word, never a generic one. */
    val groupWord: String get() = vocab.ifBlank { if (isGeneral) "Group" else if (isLearning) "Study group" else if (isWork) "Team" else "Circle" }
}

/** [title] is a display label such as "Pastor". It never changes what the person may do; [role] does. */
@Immutable
/** [avatarVersion] is null when the person has no profile photo. The photo itself is fetched by uid and version. */
data class Member(
    val uid: String,
    val displayName: String,
    val role: Role,
    val muted: Boolean = false,
    val joinedAt: Long = 0L,
    val title: String? = null,
    val lastReadAt: Long = 0L,
    val avatarVersion: Long? = null,
    /** Outside the organisation (work circles). Set by an owner or admin; shown as the EXT tag. */
    val external: Boolean = false
)

@Immutable
enum class WorkPriority(val wire: String, val label: String) {
    Low("low", "Low"),
    Normal("normal", "Normal"),
    High("high", "High"),
    Urgent("urgent", "Urgent");

    companion object {
        fun from(wire: String?): WorkPriority? = entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) }
    }
}

@Immutable
enum class WorkStatus(val wire: String, val label: String) {
    Open("open", "Open"),
    InProgress("in_progress", "In progress"),
    Done("answered", "Done");

    companion object {
        fun from(wire: String?): WorkStatus = entries.firstOrNull { it.wire == wire } ?: Open
    }
}

/** A decision's status. "Replaced" is set by the Worker when a newer decision replaces this one. */
@Immutable
enum class DecisionStatus(val wire: String, val label: String) {
    Proposed("proposed", "Proposed"),
    Confirmed("confirmed", "Confirmed"),
    Replaced("replaced", "Replaced");

    companion object { fun from(wire: String?): DecisionStatus? = entries.firstOrNull { it.wire == wire } }
}

@Immutable
data class ChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean = false
)

@Immutable
data class PostMeta(
    val sourceKind: String? = null,
    val sourceId: String? = null,
    val assigneeUid: String? = null,
    val assigneeName: String? = null,
    val dueText: String? = null,
    val dueAt: Long? = null,
    val priority: WorkPriority? = null,
    val blockedOn: String? = null,
    val decisionOwner: String? = null,
    val meetingDate: String? = null,
    val tags: List<String> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
    /** Decision posts only. The phone sends proposed or confirmed; the Worker sets replaced. */
    val decisionStatus: DecisionStatus? = null,
    /** Decision posts only: the live decision this one replaces. */
    val replacesPostId: String? = null,
    /** Read-only on the phone. The Worker sets it on the decision that was replaced. Never sent. */
    val replacedByPostId: String? = null
) {
    val isEmpty: Boolean
        get() = listOfNotNull(sourceKind, sourceId, assigneeUid, assigneeName, dueText, priority, blockedOn, decisionOwner, meetingDate).all { it.toString().isBlank() } &&
            dueAt == null && tags.isEmpty() && checklist.isEmpty() &&
            decisionStatus == null && replacesPostId.isNullOrBlank() && replacedByPostId.isNullOrBlank()
}

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
    val attachments: List<MediaAttachment> = emptyList(),
    val meta: PostMeta? = null,
    val status: String = if (answered) "answered" else "open"
) {
    val workStatus: WorkStatus get() = WorkStatus.from(status)
    val isResolved: Boolean get() = answered || status == "answered"
}

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
data class Poll(
    val id: String,
    val question: String,
    val options: List<PollOption>,
    val multi: Boolean,
    val closed: Boolean,
    val createdBy: String,
    val closesAt: Long? = null,
    val correctOptionId: String? = null,
    val explanation: String? = null
)

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

/** One thing people are asked to bring to an event (The Table). [claimedByUid] is null while nobody has said they'll bring it. */
@Immutable
data class BringItem(
    val id: String,
    val text: String,
    val claimedByUid: String? = null,
    val claimedByName: String? = null
) {
    val claimed: Boolean get() = claimedByUid != null
}

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
    val createdByName: String,
    /** Up to 20 items. Empty when the event has none. */
    val bringList: List<BringItem> = emptyList()
) {
    val cancelled: Boolean get() = status == "cancelled"
}

enum class RsvpAnswer(val wire: String, val label: String) {
    Going("going", "Going"), Maybe("maybe", "Maybe"), No("no", "Can't go");

    companion object { fun from(wire: String?): RsvpAnswer? = entries.firstOrNull { it.wire == wire } }
}

@Immutable
data class Rsvp(val uid: String, val displayName: String, val answer: RsvpAnswer)

/** What an admin writes for an event. The Worker checks times, zone and link. [bringList] is the whole list: it replaces the old one. */
@Immutable
data class EventDraft(
    val title: String,
    val description: String,
    val location: String,
    val onlineUrl: String?,
    val startsAt: Long,
    val endsAt: Long,
    val timeZone: String,
    val bringList: List<String> = emptyList()
)

/**
 * What a code leads to, before joining. [circleId] is null only if the Worker omits it. [coverPath] is a storage path,
 * which only members can download. [inviterName] is null when the inviter has left.
 */
@Immutable
data class InvitePreview(
    val circleId: String?,
    val name: String,
    val space: String,
    val template: String?,
    val memberCount: Int,
    val coverPath: String?,
    val inviterName: String?
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

