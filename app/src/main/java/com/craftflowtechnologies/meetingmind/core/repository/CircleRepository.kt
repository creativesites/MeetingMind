package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import android.util.Log
import com.craftflowtechnologies.meetingmind.core.circles.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles.CircleSync
import com.craftflowtechnologies.meetingmind.core.circles.CircleTransport
import com.craftflowtechnologies.meetingmind.core.circles.FirestoreCircleTransport
import com.craftflowtechnologies.meetingmind.core.crypto.CircleCrypto
import com.craftflowtechnologies.meetingmind.core.database.CircleDao
import com.craftflowtechnologies.meetingmind.core.database.CircleEntity
import com.craftflowtechnologies.meetingmind.core.database.CircleMemberEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.model.MemberRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Private Fellowship Circles (Tier 2).
 *
 * Implements an end-to-end encrypted event log where:
 * 1. The relay/server (Firestore) only receives {uid, ts, iv, ciphertext}.
 * 2. All member lists, counts, and contents are derived by decrypting and replaying events.
 * 3. No fake data: real member UIDs and distinct counts for "prayed" and "amen".
 */
class CircleRepository(
    private val context: Context,
    private val database: MeetMindDatabase = MeetMindDatabase.getInstance(context),
    val transport: CircleTransport = FirestoreCircleTransport()
) {
    private val circleDao: CircleDao = database.circleDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSyncJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val TAG = "CircleRepository"
    }

    fun observeCircles(): Flow<List<Circle>> =
        circleDao.observeAllCircles().map { list -> list.map { it.toDomain() } }

    fun observeCircle(circleId: String): Flow<Circle?> {
        startSync(circleId)
        return circleDao.observeCircleById(circleId).map { it?.toDomain() }
    }

    fun observeMembers(circleId: String): Flow<List<CircleMember>> =
        circleDao.observeMembers(circleId).map { list -> list.map { it.toDomain() } }

    fun observePrayers(circleId: String): Flow<List<CirclePrayer>> =
        circleDao.observePrayers(circleId).map { list -> list.map { it.toDomain() } }

    fun observeTestimonies(circleId: String): Flow<List<CircleTestimony>> =
        circleDao.observeTestimonies(circleId).map { list -> list.map { it.toDomain() } }

    fun observeSermons(circleId: String): Flow<List<CircleSermon>> =
        circleDao.observeSermons(circleId).map { list -> list.map { it.toDomain() } }

    /**
     * Starts listening to transport events for a circle and replaying them into Room.
     */
    fun startSync(circleId: String) {
        if (activeSyncJobs.containsKey(circleId)) return
        val job = scope.launch {
            val circleEntity = circleDao.getCircleById(circleId) ?: return@launch
            val circle = circleEntity.toDomain()
            val currentUid = runCatching { transport.ensureAuthenticated() }.getOrDefault("")

            transport.observeEvents(circleId).collect { events ->
                CircleSync.replayIntoDatabase(
                    circle = circle,
                    transportEvents = events,
                    currentUserId = currentUid,
                    circleDao = circleDao
                )
            }
        }
        activeSyncJobs[circleId] = job
    }

    suspend fun createCircle(
        name: String,
        description: String = "",
        myDisplayName: String,
        avatarEmoji: String = "🕊️"
    ): Circle = withContext(Dispatchers.IO) {
        val circleId = UUID.randomUUID().toString()
        val uid = transport.ensureAuthenticated()
        val aesKeyBase64 = CircleCrypto.generateKey()
        val inviteCode = CircleCrypto.buildInviteUri(circleId, name.trim(), aesKeyBase64)

        val circle = Circle(
            id = circleId,
            name = name.trim(),
            description = description.trim(),
            avatarEmoji = avatarEmoji,
            inviteCode = inviteCode,
            encryptionKeyBase64 = aesKeyBase64,
            createdByMemberId = uid,
            createdAt = System.currentTimeMillis(),
            memberCount = 1
        )
        circleDao.insertCircle(CircleEntity.fromDomain(circle))

        val selfMember = CircleMember(
            id = uid,
            circleId = circleId,
            displayName = myDisplayName.trim(),
            role = MemberRole.ADMIN,
            joinedAt = System.currentTimeMillis(),
            isSelf = true
        )
        circleDao.insertMember(CircleMemberEntity.fromDomain(selfMember))

        // Publish Join event encrypted with the circle key
        val joinEvent = CircleEvent.MemberJoined(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            displayName = myDisplayName.trim(),
            role = MemberRole.ADMIN
        )
        publishEncrypted(circleId, aesKeyBase64, joinEvent)

        startSync(circleId)
        circle
    }

    suspend fun joinCircle(
        inviteUriOrCode: String,
        myDisplayName: String
    ): Circle? = withContext(Dispatchers.IO) {
        val payload = CircleCrypto.parseInviteUri(inviteUriOrCode) ?: return@withContext null
        val circleId = payload.circleId
        val uid = transport.ensureAuthenticated()

        val existing = circleDao.getCircleById(circleId)
        if (existing != null) {
            startSync(circleId)
            return@withContext existing.toDomain()
        }

        val circle = Circle(
            id = circleId,
            name = payload.circleName,
            description = "",
            avatarEmoji = "🤝",
            inviteCode = inviteUriOrCode,
            encryptionKeyBase64 = payload.encryptionKeyBase64,
            createdByMemberId = "",
            createdAt = System.currentTimeMillis(),
            memberCount = 1
        )
        circleDao.insertCircle(CircleEntity.fromDomain(circle))

        val selfMember = CircleMember(
            id = uid,
            circleId = circleId,
            displayName = myDisplayName.trim(),
            role = MemberRole.MEMBER,
            joinedAt = System.currentTimeMillis(),
            isSelf = true
        )
        circleDao.insertMember(CircleMemberEntity.fromDomain(selfMember))

        val joinEvent = CircleEvent.MemberJoined(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            displayName = myDisplayName.trim(),
            role = MemberRole.MEMBER
        )
        publishEncrypted(circleId, payload.encryptionKeyBase64, joinEvent)

        startSync(circleId)
        circle
    }

    suspend fun postPrayer(
        circleId: String,
        requestText: String,
        isUrgent: Boolean = false,
        myDisplayName: String
    ): CirclePrayer? = withContext(Dispatchers.IO) {
        val circle = circleDao.getCircleById(circleId)?.toDomain() ?: return@withContext null
        val uid = transport.ensureAuthenticated()
        val prayerId = UUID.randomUUID().toString()

        val event = CircleEvent.PrayerPosted(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            prayerId = prayerId,
            authorName = myDisplayName.trim(),
            requestText = requestText.trim(),
            isUrgent = isUrgent
        )
        publishEncrypted(circleId, circle.encryptionKeyBase64, event)

        val prayer = CirclePrayer(
            id = prayerId,
            circleId = circleId,
            authorName = myDisplayName.trim(),
            authorId = uid,
            requestText = requestText.trim(),
            isUrgent = isUrgent,
            prayerCount = 0,
            prayedByMe = false,
            createdAt = System.currentTimeMillis()
        )
        circleDao.insertPrayer(com.craftflowtechnologies.meetingmind.core.database.CirclePrayerEntity.fromDomain(prayer))
        prayer
    }

    suspend fun prayFor(circleId: String, prayerId: String) = withContext(Dispatchers.IO) {
        val circle = circleDao.getCircleById(circleId)?.toDomain() ?: return@withContext
        val uid = transport.ensureAuthenticated()

        val event = CircleEvent.PrayerPrayed(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            prayerId = prayerId
        )
        publishEncrypted(circleId, circle.encryptionKeyBase64, event)
    }

    suspend fun markPrayerAnswered(circleId: String, prayerId: String) = withContext(Dispatchers.IO) {
        val circle = circleDao.getCircleById(circleId)?.toDomain() ?: return@withContext
        val uid = transport.ensureAuthenticated()

        val event = CircleEvent.PrayerAnswered(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            prayerId = prayerId
        )
        publishEncrypted(circleId, circle.encryptionKeyBase64, event)
        circleDao.markPrayerAnswered(prayerId, System.currentTimeMillis())
    }

    suspend fun postTestimony(
        circleId: String,
        title: String,
        storyText: String,
        scriptureRef: String? = null,
        prayerRequestId: String? = null,
        myDisplayName: String
    ): CircleTestimony? = withContext(Dispatchers.IO) {
        val circle = circleDao.getCircleById(circleId)?.toDomain() ?: return@withContext null
        val uid = transport.ensureAuthenticated()
        val testimonyId = UUID.randomUUID().toString()

        val event = CircleEvent.TestimonyPosted(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            testimonyId = testimonyId,
            authorName = myDisplayName.trim(),
            title = title.trim(),
            storyText = storyText.trim(),
            scriptureRef = scriptureRef?.trim(),
            prayerRequestId = prayerRequestId?.trim()
        )
        publishEncrypted(circleId, circle.encryptionKeyBase64, event)

        val testimony = CircleTestimony(
            id = testimonyId,
            circleId = circleId,
            authorName = myDisplayName.trim(),
            authorId = uid,
            title = title.trim(),
            storyText = storyText.trim(),
            scriptureRef = scriptureRef?.trim(),
            prayerRequestId = prayerRequestId?.trim(),
            praiseCount = 0,
            praisedByMe = false,
            createdAt = System.currentTimeMillis()
        )
        circleDao.insertTestimony(com.craftflowtechnologies.meetingmind.core.database.CircleTestimonyEntity.fromDomain(testimony))
        testimony
    }

    suspend fun praiseTestimony(circleId: String, testimonyId: String) = withContext(Dispatchers.IO) {
        val circle = circleDao.getCircleById(circleId)?.toDomain() ?: return@withContext
        val uid = transport.ensureAuthenticated()

        val event = CircleEvent.TestimonyAmen(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            testimonyId = testimonyId
        )
        publishEncrypted(circleId, circle.encryptionKeyBase64, event)
    }

    suspend fun shareSermon(
        circleId: String,
        title: String,
        preacher: String? = null,
        scripturePassage: String? = null,
        sermonDate: String? = null,
        discussionGuideJson: String = "",
        transcriptSummary: String = "",
        audioDurationSec: Long = 0L,
        myDisplayName: String
    ): CircleSermon? = withContext(Dispatchers.IO) {
        val circle = circleDao.getCircleById(circleId)?.toDomain() ?: return@withContext null
        val uid = transport.ensureAuthenticated()
        val sermonId = UUID.randomUUID().toString()

        val event = CircleEvent.SermonShared(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = uid,
            timestamp = System.currentTimeMillis(),
            sermonId = sermonId,
            authorName = myDisplayName.trim(),
            title = title.trim(),
            preacher = preacher?.trim(),
            scripturePassage = scripturePassage?.trim(),
            sermonDate = sermonDate?.trim(),
            discussionGuideJson = discussionGuideJson,
            transcriptSummary = transcriptSummary,
            audioDurationSec = audioDurationSec
        )
        publishEncrypted(circleId, circle.encryptionKeyBase64, event)

        val sermon = CircleSermon(
            id = sermonId,
            circleId = circleId,
            title = title.trim(),
            preacher = preacher?.trim(),
            scripturePassage = scripturePassage?.trim(),
            sermonDate = sermonDate?.trim(),
            discussionGuideJson = discussionGuideJson,
            transcriptSummary = transcriptSummary,
            audioDurationSec = audioDurationSec,
            createdAt = System.currentTimeMillis()
        )
        circleDao.insertSermon(com.craftflowtechnologies.meetingmind.core.database.CircleSermonEntity.fromDomain(sermon))
        sermon
    }

    suspend fun leaveCircle(circleId: String) = withContext(Dispatchers.IO) {
        val circle = circleDao.getCircleById(circleId)?.toDomain()
        val uid = transport.ensureAuthenticated()

        if (circle != null) {
            val event = CircleEvent.MemberLeft(
                eventId = UUID.randomUUID().toString(),
                circleId = circleId,
                authorUid = uid,
                timestamp = System.currentTimeMillis()
            )
            runCatching { publishEncrypted(circleId, circle.encryptionKeyBase64, event) }
        }

        activeSyncJobs.remove(circleId)?.cancel()
        circleDao.deleteCircleById(circleId)
    }

    private suspend fun publishEncrypted(circleId: String, keyBase64: String, event: CircleEvent) {
        val jsonString = event.toJsonString()
        val ciphertext = CircleCrypto.encrypt(jsonString, keyBase64)
        transport.publishEvent(circleId = circleId, iv = "gcm", ciphertext = ciphertext).getOrThrow()
    }
}
