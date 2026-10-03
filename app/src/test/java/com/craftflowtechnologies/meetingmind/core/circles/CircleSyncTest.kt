package com.craftflowtechnologies.meetingmind.core.circles

import com.craftflowtechnologies.meetingmind.core.crypto.CircleCrypto
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CircleSyncTest {

    private val circleId = "circle-xyz"
    private val key = CircleCrypto.generateKey()

    private fun transportEvent(uid: String, event: CircleEvent): CircleTransportEvent {
        val json = event.toJsonString()
        val ciphertext = CircleCrypto.encrypt(json, key)
        return CircleTransportEvent(
            id = event.eventId,
            uid = uid,
            timestamp = event.timestamp,
            iv = "gcm",
            ciphertext = ciphertext
        )
    }

    @Test
    fun `two simulated members join, pray, and praise without fake data or count inflation`() {
        val memberAUid = "uid-sarah"
        val memberBUid = "uid-david"

        val events = mutableListOf<CircleTransportEvent>()

        // 1. Sarah creates & joins
        val joinA = CircleEvent.MemberJoined(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberAUid,
            timestamp = 1000L,
            displayName = "Sarah"
        )
        events.add(transportEvent(memberAUid, joinA))

        // State for Sarah: 1 member
        val stateA1 = CircleSync.reduce(circleId, key, memberAUid, events)
        assertEquals(1, stateA1.members.size)
        assertEquals("Sarah", stateA1.members[0].displayName)
        assertTrue(stateA1.members[0].isSelf)

        // 2. David joins via invite
        val joinB = CircleEvent.MemberJoined(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberBUid,
            timestamp = 2000L,
            displayName = "David"
        )
        events.add(transportEvent(memberBUid, joinB))

        // Both see 2 members now
        val stateA2 = CircleSync.reduce(circleId, key, memberAUid, events)
        assertEquals(2, stateA2.members.size)
        assertTrue(stateA2.members.any { it.displayName == "Sarah" && it.isSelf })
        assertTrue(stateA2.members.any { it.displayName == "David" && !it.isSelf })

        val stateB2 = CircleSync.reduce(circleId, key, memberBUid, events)
        assertEquals(2, stateB2.members.size)
        assertTrue(stateB2.members.any { it.displayName == "David" && it.isSelf })
        assertTrue(stateB2.members.any { it.displayName == "Sarah" && !it.isSelf })

        // 3. Sarah posts a prayer
        val prayerId = "prayer-1"
        val prayerPosted = CircleEvent.PrayerPosted(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberAUid,
            timestamp = 3000L,
            prayerId = prayerId,
            authorName = "Sarah",
            requestText = "Please pray for my exams tomorrow",
            isUrgent = true
        )
        events.add(transportEvent(memberAUid, prayerPosted))

        val stateAfterPost = CircleSync.reduce(circleId, key, memberBUid, events)
        assertEquals(1, stateAfterPost.prayers.size)
        assertEquals(0, stateAfterPost.prayers[0].prayerCount)
        assertFalse(stateAfterPost.prayers[0].prayedByMe)

        // 4. David taps "I prayed"
        val davidPrayed = CircleEvent.PrayerPrayed(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberBUid,
            timestamp = 4000L,
            prayerId = prayerId
        )
        events.add(transportEvent(memberBUid, davidPrayed))

        val stateDavidPrayed = CircleSync.reduce(circleId, key, memberBUid, events)
        assertEquals(1, stateDavidPrayed.prayers[0].prayerCount)
        assertTrue(stateDavidPrayed.prayers[0].prayedByMe)

        val stateSarahSeesDavidPrayed = CircleSync.reduce(circleId, key, memberAUid, events)
        assertEquals(1, stateSarahSeesDavidPrayed.prayers[0].prayerCount)
        assertFalse(stateSarahSeesDavidPrayed.prayers[0].prayedByMe)

        // 5. David taps "I prayed" again (idempotent tap test - MUST NOT inflate count)
        val davidPrayedAgain = CircleEvent.PrayerPrayed(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberBUid,
            timestamp = 4100L,
            prayerId = prayerId
        )
        events.add(transportEvent(memberBUid, davidPrayedAgain))

        val stateAfterDuplicate = CircleSync.reduce(circleId, key, memberBUid, events)
        assertEquals(1, stateAfterDuplicate.prayers[0].prayerCount) // Count remains 1!

        // 6. David attempts to mark Sarah's prayer answered -> MUST BE REJECTED (author only)
        val davidTriesAnswer = CircleEvent.PrayerAnswered(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberBUid,
            timestamp = 5000L,
            prayerId = prayerId
        )
        events.add(transportEvent(memberBUid, davidTriesAnswer))

        val stateAfterUnauthorizedAnswer = CircleSync.reduce(circleId, key, memberAUid, events)
        assertEquals(CirclePrayerStatus.ACTIVE, stateAfterUnauthorizedAnswer.prayers[0].status) // Still ACTIVE

        // 7. Sarah marks prayer answered -> ACCEPTED
        val sarahAnswers = CircleEvent.PrayerAnswered(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberAUid,
            timestamp = 6000L,
            prayerId = prayerId
        )
        events.add(transportEvent(memberAUid, sarahAnswers))

        val stateAfterSarahAnswer = CircleSync.reduce(circleId, key, memberAUid, events)
        assertEquals(CirclePrayerStatus.ANSWERED, stateAfterSarahAnswer.prayers[0].status)

        // 8. Sarah posts testimony celebrating answered prayer
        val testimonyId = "testimony-1"
        val testimonyPosted = CircleEvent.TestimonyPosted(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberAUid,
            timestamp = 7000L,
            testimonyId = testimonyId,
            authorName = "Sarah",
            title = "Aced my exam!",
            storyText = "God provided peace and memory recall during the whole exam.",
            scriptureRef = "Philippians 4:6-7",
            prayerRequestId = prayerId
        )
        events.add(transportEvent(memberAUid, testimonyPosted))

        // 9. David taps Amen
        val davidAmen = CircleEvent.TestimonyAmen(
            eventId = UUID.randomUUID().toString(),
            circleId = circleId,
            authorUid = memberBUid,
            timestamp = 8000L,
            testimonyId = testimonyId
        )
        events.add(transportEvent(memberBUid, davidAmen))

        val finalStateB = CircleSync.reduce(circleId, key, memberBUid, events)
        assertEquals(1, finalStateB.testimonies.size)
        assertEquals(1, finalStateB.testimonies[0].praiseCount)
        assertTrue(finalStateB.testimonies[0].praisedByMe)

        val finalStateA = CircleSync.reduce(circleId, key, memberAUid, events)
        assertEquals(1, finalStateA.testimonies.size)
        assertEquals(1, finalStateA.testimonies[0].praiseCount)
        assertFalse(finalStateA.testimonies[0].praisedByMe)
    }
}
