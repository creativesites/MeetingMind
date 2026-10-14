package com.craftflowtechnologies.meetingmind.core.model

/**
 * A private, invite-only fellowship circle (5–15 members).
 * Secured with end-to-end encryption (AES-256-GCM) so only members
 * holding the circle key can read shared prayers, testimonies, and sermon guides.
 */
data class Circle(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarEmoji: String = "🕊️",
    val inviteCode: String,
    val encryptionKeyBase64: String,
    val createdByMemberId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val memberCount: Int = 1
)

data class CircleMember(
    val id: String,
    val circleId: String,
    val displayName: String,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis(),
    val isSelf: Boolean = false
)

enum class MemberRole {
    ADMIN,
    MEMBER
}

enum class CirclePrayerStatus {
    ACTIVE,
    ANSWERED
}

/**
 * A prayer request shared to the circle.
 * Members can tap "Prayed for this" to increment [prayerCount] with gentle nudges.
 */
data class CirclePrayer(
    val id: String,
    val circleId: String,
    val authorName: String,
    val authorId: String,
    val requestText: String,
    val isUrgent: Boolean = false,
    val status: CirclePrayerStatus = CirclePrayerStatus.ACTIVE,
    val prayerCount: Int = 0,
    val prayedByMe: Boolean = false,
    val answeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A testimony or praise report shared with the circle to encourage members.
 * Can celebrate an answered prayer ([prayerRequestId]) or share an independent breakthrough.
 */
data class CircleTestimony(
    val id: String,
    val circleId: String,
    val authorName: String,
    val authorId: String,
    val title: String,
    val storyText: String,
    val scriptureRef: String? = null,
    val prayerRequestId: String? = null,
    val praiseCount: Int = 0,
    val praisedByMe: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A sermon workspace shared with the circle.
 * Carries the transcript summary and small-group discussion guide for group study.
 * Personal reflections and confessions stay 100% on-device and never leave the phone.
 */
data class CircleSermon(
    val id: String,
    val circleId: String,
    val title: String,
    val preacher: String? = null,
    val scripturePassage: String? = null,
    val sermonDate: String? = null,
    val discussionGuideJson: String = "",
    val transcriptSummary: String = "",
    val audioDurationSec: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
