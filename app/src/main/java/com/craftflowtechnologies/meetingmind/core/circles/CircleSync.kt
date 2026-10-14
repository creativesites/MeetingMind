package com.craftflowtechnologies.meetingmind.core.circles

import android.util.Log
import com.craftflowtechnologies.meetingmind.core.crypto.CircleCrypto
import com.craftflowtechnologies.meetingmind.core.database.CircleDao
import com.craftflowtechnologies.meetingmind.core.database.CircleEntity
import com.craftflowtechnologies.meetingmind.core.database.CircleMemberEntity
import com.craftflowtechnologies.meetingmind.core.database.CirclePrayerEntity
import com.craftflowtechnologies.meetingmind.core.database.CircleSermonEntity
import com.craftflowtechnologies.meetingmind.core.database.CircleTestimonyEntity
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.model.MemberRole

data class ReplayedCircleState(
    val members: List<CircleMember>,
    val prayers: List<CirclePrayer>,
    val testimonies: List<CircleTestimony>,
    val sermons: List<CircleSermon>
)

object CircleSync {
    private const val TAG = "CircleSync"

    /**
     * Pure function that decrypts and reduces a sequence of transport events
     * into a deterministic domain state.
     *
     * Invariants guaranteed:
     * - Every member count is the distinct count of real members.
     * - Prayer counts cannot be inflated (one tap per member UID).
     * - Only the original author of a prayer can transition it to ANSWERED.
     * - Amen counts cannot be inflated (one tap per member UID).
     */
    fun reduce(
        circleId: String,
        encryptionKeyBase64: String,
        currentUserId: String,
        transportEvents: List<CircleTransportEvent>
    ): ReplayedCircleState {
        val membersMap = linkedMapOf<String, CircleMember>()
        val prayersMap = linkedMapOf<String, CirclePrayer>()
        val prayerTapsMap = mutableMapOf<String, MutableSet<String>>() // prayerId -> set of memberUids
        val testimoniesMap = linkedMapOf<String, CircleTestimony>()
        val testimonyAmensMap = mutableMapOf<String, MutableSet<String>>() // testimonyId -> set of memberUids
        val sermonsMap = linkedMapOf<String, CircleSermon>()

        for (transportEvent in transportEvents) {
            val decryptedJson = runCatching {
                CircleCrypto.decrypt(transportEvent.ciphertext, encryptionKeyBase64)
            }.getOrElse { e ->
                Log.w(TAG, "Failed to decrypt event ${transportEvent.id}: ${e.message}")
                continue
            }

            val event = CircleEvent.fromJsonString(decryptedJson) ?: continue
            if (event.circleId != circleId) continue

            when (event) {
                is CircleEvent.MemberJoined -> {
                    membersMap[event.authorUid] = CircleMember(
                        id = event.authorUid,
                        circleId = circleId,
                        displayName = event.displayName.trim(),
                        role = event.role,
                        joinedAt = event.timestamp,
                        isSelf = event.authorUid == currentUserId
                    )
                }
                is CircleEvent.MemberLeft -> {
                    membersMap.remove(event.authorUid)
                }
                is CircleEvent.PrayerPosted -> {
                    prayersMap[event.prayerId] = CirclePrayer(
                        id = event.prayerId,
                        circleId = circleId,
                        authorName = event.authorName.trim(),
                        authorId = event.authorUid,
                        requestText = event.requestText.trim(),
                        isUrgent = event.isUrgent,
                        status = CirclePrayerStatus.ACTIVE,
                        prayerCount = 0,
                        prayedByMe = false,
                        answeredAt = null,
                        createdAt = event.timestamp
                    )
                }
                is CircleEvent.PrayerPrayed -> {
                    val taps = prayerTapsMap.getOrPut(event.prayerId) { mutableSetOf() }
                    taps.add(event.authorUid)
                }
                is CircleEvent.PrayerAnswered -> {
                    val existing = prayersMap[event.prayerId]
                    // Author-only security check: only the author or circle admin can mark answered
                    if (existing != null && (existing.authorId == event.authorUid || membersMap[event.authorUid]?.role == MemberRole.ADMIN)) {
                        prayersMap[event.prayerId] = existing.copy(
                            status = CirclePrayerStatus.ANSWERED,
                            answeredAt = event.timestamp
                        )
                    }
                }
                is CircleEvent.TestimonyPosted -> {
                    testimoniesMap[event.testimonyId] = CircleTestimony(
                        id = event.testimonyId,
                        circleId = circleId,
                        authorName = event.authorName.trim(),
                        authorId = event.authorUid,
                        title = event.title.trim(),
                        storyText = event.storyText.trim(),
                        scriptureRef = event.scriptureRef?.trim()?.takeIf { it.isNotBlank() },
                        prayerRequestId = event.prayerRequestId?.trim()?.takeIf { it.isNotBlank() },
                        praiseCount = 0,
                        praisedByMe = false,
                        createdAt = event.timestamp
                    )
                    // If celebrated an answered prayer, ensure status matches
                    if (!event.prayerRequestId.isNullOrBlank()) {
                        prayersMap[event.prayerRequestId]?.let { req ->
                            prayersMap[event.prayerRequestId] = req.copy(
                                status = CirclePrayerStatus.ANSWERED,
                                answeredAt = req.answeredAt ?: event.timestamp
                            )
                        }
                    }
                }
                is CircleEvent.TestimonyAmen -> {
                    val amens = testimonyAmensMap.getOrPut(event.testimonyId) { mutableSetOf() }
                    amens.add(event.authorUid)
                }
                is CircleEvent.SermonShared -> {
                    sermonsMap[event.sermonId] = CircleSermon(
                        id = event.sermonId,
                        circleId = circleId,
                        title = event.title.trim(),
                        preacher = event.preacher?.trim()?.takeIf { it.isNotBlank() },
                        scripturePassage = event.scripturePassage?.trim()?.takeIf { it.isNotBlank() },
                        sermonDate = event.sermonDate?.trim()?.takeIf { it.isNotBlank() },
                        discussionGuideJson = event.discussionGuideJson,
                        transcriptSummary = event.transcriptSummary,
                        audioDurationSec = event.audioDurationSec,
                        createdAt = event.timestamp
                    )
                }
            }
        }

        // Apply derived tap and amen counts
        val finalPrayers = prayersMap.values.map { p ->
            val taps = prayerTapsMap[p.id] ?: emptySet()
            p.copy(
                prayerCount = taps.size,
                prayedByMe = taps.contains(currentUserId)
            )
        }

        val finalTestimonies = testimoniesMap.values.map { t ->
            val amens = testimonyAmensMap[t.id] ?: emptySet()
            t.copy(
                praiseCount = amens.size,
                praisedByMe = amens.contains(currentUserId)
            )
        }

        return ReplayedCircleState(
            members = membersMap.values.toList(),
            prayers = finalPrayers,
            testimonies = finalTestimonies,
            sermons = sermonsMap.values.toList()
        )
    }

    /**
     * Replays the events and updates the Room cache transactionally.
     */
    suspend fun replayIntoDatabase(
        circle: Circle,
        transportEvents: List<CircleTransportEvent>,
        currentUserId: String,
        circleDao: CircleDao
    ) {
        val state = reduce(
            circleId = circle.id,
            encryptionKeyBase64 = circle.encryptionKeyBase64,
            currentUserId = currentUserId,
            transportEvents = transportEvents
        )

        // Update circle entity with real member count without replacing to prevent cascading deletes
        circleDao.updateMemberCount(circle.id, maxOf(1, state.members.size))

        // Update members
        for (m in state.members) {
            circleDao.insertMember(CircleMemberEntity.fromDomain(m))
        }

        // Update prayers
        for (p in state.prayers) {
            circleDao.insertPrayer(CirclePrayerEntity.fromDomain(p))
        }

        // Update testimonies
        for (t in state.testimonies) {
            circleDao.insertTestimony(CircleTestimonyEntity.fromDomain(t))
        }

        // Update sermons
        for (s in state.sermons) {
            circleDao.insertSermon(CircleSermonEntity.fromDomain(s))
        }
    }
}
