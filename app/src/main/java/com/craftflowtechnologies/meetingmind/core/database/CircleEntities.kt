package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.model.MemberRole

@Entity(
    tableName = "circles",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class CircleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val avatarEmoji: String = "🕊️",
    val inviteCode: String,
    val encryptionKeyBase64: String,
    val createdByMemberId: String,
    val createdAt: Long,
    val memberCount: Int = 1
) {
    fun toDomain(): Circle = Circle(
        id = id,
        name = name,
        description = description,
        avatarEmoji = avatarEmoji,
        inviteCode = inviteCode,
        encryptionKeyBase64 = encryptionKeyBase64,
        createdByMemberId = createdByMemberId,
        createdAt = createdAt,
        memberCount = memberCount
    )

    companion object {
        fun fromDomain(c: Circle): CircleEntity = CircleEntity(
            id = c.id,
            name = c.name,
            description = c.description,
            avatarEmoji = c.avatarEmoji,
            inviteCode = c.inviteCode,
            encryptionKeyBase64 = c.encryptionKeyBase64,
            createdByMemberId = c.createdByMemberId,
            createdAt = c.createdAt,
            memberCount = c.memberCount
        )
    }
}

@Entity(
    tableName = "circle_members",
    foreignKeys = [
        ForeignKey(
            entity = CircleEntity::class,
            parentColumns = ["id"],
            childColumns = ["circleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["circleId"]),
        Index(value = ["isSelf"])
    ]
)
data class CircleMemberEntity(
    @PrimaryKey val id: String,
    val circleId: String,
    val displayName: String,
    @ColumnInfo(defaultValue = "MEMBER") val role: String = "MEMBER",
    val joinedAt: Long,
    val isSelf: Boolean = false
) {
    fun toDomain(): CircleMember = CircleMember(
        id = id,
        circleId = circleId,
        displayName = displayName,
        role = runCatching { MemberRole.valueOf(role) }.getOrDefault(MemberRole.MEMBER),
        joinedAt = joinedAt,
        isSelf = isSelf
    )

    companion object {
        fun fromDomain(m: CircleMember): CircleMemberEntity = CircleMemberEntity(
            id = m.id,
            circleId = m.circleId,
            displayName = m.displayName,
            role = m.role.name,
            joinedAt = m.joinedAt,
            isSelf = m.isSelf
        )
    }
}

@Entity(
    tableName = "circle_prayers",
    foreignKeys = [
        ForeignKey(
            entity = CircleEntity::class,
            parentColumns = ["id"],
            childColumns = ["circleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["circleId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class CirclePrayerEntity(
    @PrimaryKey val id: String,
    val circleId: String,
    val authorName: String,
    val authorId: String,
    val requestText: String,
    val isUrgent: Boolean = false,
    @ColumnInfo(defaultValue = "ACTIVE") val status: String = "ACTIVE",
    val prayerCount: Int = 0,
    val prayedByMe: Boolean = false,
    val answeredAt: Long? = null,
    val createdAt: Long
) {
    fun toDomain(): CirclePrayer = CirclePrayer(
        id = id,
        circleId = circleId,
        authorName = authorName,
        authorId = authorId,
        requestText = requestText,
        isUrgent = isUrgent,
        status = runCatching { CirclePrayerStatus.valueOf(status) }.getOrDefault(CirclePrayerStatus.ACTIVE),
        prayerCount = prayerCount,
        prayedByMe = prayedByMe,
        answeredAt = answeredAt,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(p: CirclePrayer): CirclePrayerEntity = CirclePrayerEntity(
            id = p.id,
            circleId = p.circleId,
            authorName = p.authorName,
            authorId = p.authorId,
            requestText = p.requestText,
            isUrgent = p.isUrgent,
            status = p.status.name,
            prayerCount = p.prayerCount,
            prayedByMe = p.prayedByMe,
            answeredAt = p.answeredAt,
            createdAt = p.createdAt
        )
    }
}

@Entity(
    tableName = "circle_testimonies",
    foreignKeys = [
        ForeignKey(
            entity = CircleEntity::class,
            parentColumns = ["id"],
            childColumns = ["circleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["circleId"]),
        Index(value = ["createdAt"]),
        Index(value = ["prayerRequestId"])
    ]
)
data class CircleTestimonyEntity(
    @PrimaryKey val id: String,
    val circleId: String,
    val authorName: String,
    val authorId: String,
    val title: String,
    val storyText: String,
    val scriptureRef: String? = null,
    val prayerRequestId: String? = null,
    val praiseCount: Int = 0,
    val praisedByMe: Boolean = false,
    val createdAt: Long
) {
    fun toDomain(): CircleTestimony = CircleTestimony(
        id = id,
        circleId = circleId,
        authorName = authorName,
        authorId = authorId,
        title = title,
        storyText = storyText,
        scriptureRef = scriptureRef,
        prayerRequestId = prayerRequestId,
        praiseCount = praiseCount,
        praisedByMe = praisedByMe,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(t: CircleTestimony): CircleTestimonyEntity = CircleTestimonyEntity(
            id = t.id,
            circleId = t.circleId,
            authorName = t.authorName,
            authorId = t.authorId,
            title = t.title,
            storyText = t.storyText,
            scriptureRef = t.scriptureRef,
            prayerRequestId = t.prayerRequestId,
            praiseCount = t.praiseCount,
            praisedByMe = t.praisedByMe,
            createdAt = t.createdAt
        )
    }
}

@Entity(
    tableName = "circle_sermons",
    foreignKeys = [
        ForeignKey(
            entity = CircleEntity::class,
            parentColumns = ["id"],
            childColumns = ["circleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["circleId"]),
        Index(value = ["createdAt"])
    ]
)
data class CircleSermonEntity(
    @PrimaryKey val id: String,
    val circleId: String,
    val title: String,
    val preacher: String? = null,
    val scripturePassage: String? = null,
    val sermonDate: String? = null,
    val discussionGuideJson: String = "",
    val transcriptSummary: String = "",
    val audioDurationSec: Long = 0L,
    val createdAt: Long
) {
    fun toDomain(): CircleSermon = CircleSermon(
        id = id,
        circleId = circleId,
        title = title,
        preacher = preacher,
        scripturePassage = scripturePassage,
        sermonDate = sermonDate,
        discussionGuideJson = discussionGuideJson,
        transcriptSummary = transcriptSummary,
        audioDurationSec = audioDurationSec,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(s: CircleSermon): CircleSermonEntity = CircleSermonEntity(
            id = s.id,
            circleId = s.circleId,
            title = s.title,
            preacher = s.preacher,
            scripturePassage = s.scripturePassage,
            sermonDate = s.sermonDate,
            discussionGuideJson = s.discussionGuideJson,
            transcriptSummary = s.transcriptSummary,
            audioDurationSec = s.audioDurationSec,
            createdAt = s.createdAt
        )
    }
}
