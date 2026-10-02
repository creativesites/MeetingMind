package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.crypto.CircleCrypto
import com.craftflowtechnologies.meetingmind.core.database.CircleDao
import com.craftflowtechnologies.meetingmind.core.database.CircleEntity
import com.craftflowtechnologies.meetingmind.core.database.CircleMemberEntity
import com.craftflowtechnologies.meetingmind.core.database.CirclePrayerEntity
import com.craftflowtechnologies.meetingmind.core.database.CircleSermonEntity
import com.craftflowtechnologies.meetingmind.core.database.CircleTestimonyEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.model.MemberRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages Private Fellowship Circles (Tier 2).
 * Handles encrypted invite link generation, group membership, shared prayer wall with
 * "Prayed for this" increments, testimony posting with answered prayer transitions,
 * and small-group sermon study workspaces.
 */
class CircleRepository(
    private val context: Context,
    private val database: MeetMindDatabase = MeetMindDatabase.getInstance(context)
) {
    private val circleDao: CircleDao = database.circleDao()

    fun observeCircles(): Flow<List<Circle>> =
        circleDao.observeAllCircles().map { list -> list.map { it.toDomain() } }

    fun observeCircle(circleId: String): Flow<Circle?> =
        circleDao.observeCircleById(circleId).map { it?.toDomain() }

    fun observeMembers(circleId: String): Flow<List<CircleMember>> =
        circleDao.observeMembers(circleId).map { list -> list.map { it.toDomain() } }

    fun observePrayers(circleId: String): Flow<List<CirclePrayer>> =
        circleDao.observePrayers(circleId).map { list -> list.map { it.toDomain() } }

    fun observeTestimonies(circleId: String): Flow<List<CircleTestimony>> =
        circleDao.observeTestimonies(circleId).map { list -> list.map { it.toDomain() } }

    fun observeSermons(circleId: String): Flow<List<CircleSermon>> =
        circleDao.observeSermons(circleId).map { list -> list.map { it.toDomain() } }

    suspend fun createCircle(
        name: String,
        description: String = "",
        myDisplayName: String = "Me",
        avatarEmoji: String = "🕊️"
    ): Circle = withContext(Dispatchers.IO) {
        val circleId = UUID.randomUUID().toString()
        val memberId = UUID.randomUUID().toString()
        val aesKeyBase64 = CircleCrypto.generateKey()
        val inviteCode = CircleCrypto.buildInviteUri(circleId, name, aesKeyBase64)

        val circle = Circle(
            id = circleId,
            name = name.trim(),
            description = description.trim(),
            avatarEmoji = avatarEmoji,
            inviteCode = inviteCode,
            encryptionKeyBase64 = aesKeyBase64,
            createdByMemberId = memberId,
            createdAt = System.currentTimeMillis(),
            memberCount = 1
        )
        circleDao.insertCircle(CircleEntity.fromDomain(circle))

        val selfMember = CircleMember(
            id = memberId,
            circleId = circleId,
            displayName = myDisplayName.ifBlank { "Me" }.trim(),
            role = MemberRole.ADMIN,
            joinedAt = System.currentTimeMillis(),
            isSelf = true
        )
        circleDao.insertMember(CircleMemberEntity.fromDomain(selfMember))

        circle
    }

    suspend fun joinCircle(
        inviteUriOrCode: String,
        myDisplayName: String = "Me"
    ): Circle? = withContext(Dispatchers.IO) {
        val payload = CircleCrypto.parseInviteUri(inviteUriOrCode) ?: return@withContext null

        val existing = circleDao.getCircleById(payload.circleId)
        if (existing != null) {
            return@withContext existing.toDomain()
        }

        val circleId = payload.circleId
        val myMemberId = UUID.randomUUID().toString()
        val circle = Circle(
            id = circleId,
            name = payload.circleName,
            description = "Joined via invite",
            avatarEmoji = "🤝",
            inviteCode = inviteUriOrCode,
            encryptionKeyBase64 = payload.encryptionKeyBase64,
            createdByMemberId = "",
            createdAt = System.currentTimeMillis(),
            memberCount = 2
        )
        circleDao.insertCircle(CircleEntity.fromDomain(circle))

        val selfMember = CircleMember(
            id = myMemberId,
            circleId = circleId,
            displayName = myDisplayName.ifBlank { "Me" }.trim(),
            role = MemberRole.MEMBER,
            joinedAt = System.currentTimeMillis(),
            isSelf = true
        )
        circleDao.insertMember(CircleMemberEntity.fromDomain(selfMember))

        circle
    }

    suspend fun postPrayer(
        circleId: String,
        requestText: String,
        isUrgent: Boolean = false,
        authorName: String = "Me",
        authorId: String = "me"
    ): CirclePrayer = withContext(Dispatchers.IO) {
        val prayer = CirclePrayer(
            id = UUID.randomUUID().toString(),
            circleId = circleId,
            authorName = authorName.ifBlank { "Me" }.trim(),
            authorId = authorId,
            requestText = requestText.trim(),
            isUrgent = isUrgent,
            status = CirclePrayerStatus.ACTIVE,
            prayerCount = 0,
            prayedByMe = false,
            createdAt = System.currentTimeMillis()
        )
        circleDao.insertPrayer(CirclePrayerEntity.fromDomain(prayer))
        prayer
    }

    suspend fun prayFor(prayerId: String) = withContext(Dispatchers.IO) {
        circleDao.recordPrayerTap(prayerId)
    }

    suspend fun markPrayerAnswered(prayerId: String) = withContext(Dispatchers.IO) {
        circleDao.markPrayerAnswered(prayerId, System.currentTimeMillis())
    }

    suspend fun postTestimony(
        circleId: String,
        title: String,
        storyText: String,
        scriptureRef: String? = null,
        prayerRequestId: String? = null,
        authorName: String = "Me",
        authorId: String = "me"
    ): CircleTestimony = withContext(Dispatchers.IO) {
        val testimony = CircleTestimony(
            id = UUID.randomUUID().toString(),
            circleId = circleId,
            authorName = authorName.ifBlank { "Me" }.trim(),
            authorId = authorId,
            title = title.trim(),
            storyText = storyText.trim(),
            scriptureRef = scriptureRef?.trim()?.takeIf { it.isNotBlank() },
            prayerRequestId = prayerRequestId,
            praiseCount = 0,
            praisedByMe = false,
            createdAt = System.currentTimeMillis()
        )
        circleDao.insertTestimony(CircleTestimonyEntity.fromDomain(testimony))

        // If celebrating a specific answered prayer request, transition its status to ANSWERED.
        if (!prayerRequestId.isNullOrBlank()) {
            circleDao.markPrayerAnswered(prayerRequestId, System.currentTimeMillis())
        }

        testimony
    }

    suspend fun praiseTestimony(testimonyId: String) = withContext(Dispatchers.IO) {
        circleDao.recordPraiseTap(testimonyId)
    }

    suspend fun shareSermon(
        circleId: String,
        title: String,
        preacher: String? = null,
        scripturePassage: String? = null,
        sermonDate: String? = null,
        discussionGuideJson: String = "",
        transcriptSummary: String = "",
        audioDurationSec: Long = 0L
    ): CircleSermon = withContext(Dispatchers.IO) {
        val sermon = CircleSermon(
            id = UUID.randomUUID().toString(),
            circleId = circleId,
            title = title.trim(),
            preacher = preacher?.trim()?.takeIf { it.isNotBlank() },
            scripturePassage = scripturePassage?.trim()?.takeIf { it.isNotBlank() },
            sermonDate = sermonDate,
            discussionGuideJson = discussionGuideJson,
            transcriptSummary = transcriptSummary,
            audioDurationSec = audioDurationSec,
            createdAt = System.currentTimeMillis()
        )
        circleDao.insertSermon(CircleSermonEntity.fromDomain(sermon))
        sermon
    }

    suspend fun leaveCircle(circleId: String) = withContext(Dispatchers.IO) {
        circleDao.deleteCircleById(circleId)
    }
}
