package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.crypto.CircleCrypto
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayerStatus
import com.craftflowtechnologies.meetingmind.core.model.MemberRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CircleRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var repository: CircleRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CircleRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `createCircle generates valid circle and self admin member`() = runBlocking {
        val circle = repository.createCircle(
            name = "Youth Leaders Fellowship",
            description = "Weekly prayer and planning",
            myDisplayName = "Pastor David"
        )

        assertNotNull(circle.id)
        assertEquals("Youth Leaders Fellowship", circle.name)
        assertTrue(circle.inviteCode.startsWith("mindcircle://join?"))

        val circles = repository.observeCircles().first()
        assertEquals(1, circles.size)
        assertEquals("Youth Leaders Fellowship", circles.first().name)

        val members = repository.observeMembers(circle.id).first()
        assertEquals(1, members.size)
        assertEquals("Pastor David", members.first().displayName)
        assertEquals(MemberRole.ADMIN, members.first().role)
        assertTrue(members.first().isSelf)
    }

    @Test
    fun `joinCircle adds circle and self member via invite code`() = runBlocking {
        val key = CircleCrypto.generateKey()
        val circleId = "fellowship-circle-999"
        val inviteUri = CircleCrypto.buildInviteUri(circleId, "Tuesday Men's Group", key)

        val joined = repository.joinCircle(inviteUri, myDisplayName = "Winston")
        assertNotNull(joined)
        assertEquals(circleId, joined?.id)

        val members = repository.observeMembers(circleId).first()
        assertEquals(1, members.size)
        assertEquals("Winston", members.first().displayName)
        assertEquals(MemberRole.MEMBER, members.first().role)
        assertTrue(members.first().isSelf)
    }

    @Test
    fun `prayer request workflow increments count and marks prayedByMe`() = runBlocking {
        val circle = repository.createCircle("Prayer Warriors", myDisplayName = "Sarah")
        val prayer = repository.postPrayer(
            circleId = circle.id,
            requestText = "Praying for guidance on new job transition",
            isUrgent = true,
            authorName = "Sarah"
        )

        assertEquals(0, prayer.prayerCount)
        assertEquals(CirclePrayerStatus.ACTIVE, prayer.status)
        assertTrue(prayer.isUrgent)

        repository.prayFor(prayer.id)

        val updatedPrayers = repository.observePrayers(circle.id).first()
        assertEquals(1, updatedPrayers.size)
        assertEquals(1, updatedPrayers.first().prayerCount)
        assertTrue(updatedPrayers.first().prayedByMe)
    }

    @Test
    fun `posting testimony marks linked prayer request as answered`() = runBlocking {
        val circle = repository.createCircle("Family Circle", myDisplayName = "John")
        val prayer = repository.postPrayer(
            circleId = circle.id,
            requestText = "Praying for healing for my knee",
            authorName = "John"
        )

        assertEquals(CirclePrayerStatus.ACTIVE, prayer.status)

        // Post testimony celebrating the answered prayer
        val testimony = repository.postTestimony(
            circleId = circle.id,
            title = "God completely healed my knee!",
            storyText = "After weeks of prayer, the doctors confirmed full recovery today without surgery.",
            scriptureRef = "Psalm 103:2-3",
            prayerRequestId = prayer.id,
            authorName = "John"
        )

        assertNotNull(testimony.id)
        assertEquals("Psalm 103:2-3", testimony.scriptureRef)

        // Verify the prayer request status transitioned to ANSWERED
        val prayers = repository.observePrayers(circle.id).first()
        assertEquals(CirclePrayerStatus.ANSWERED, prayers.first().status)
        assertNotNull(prayers.first().answeredAt)

        // Verify testimony is stored
        val testimonies = repository.observeTestimonies(circle.id).first()
        assertEquals(1, testimonies.size)
        assertEquals("God completely healed my knee!", testimonies.first().title)
    }

    @Test
    fun `shareSermon persists study guide in circle workspace`() = runBlocking {
        val circle = repository.createCircle("Sunday Small Group", myDisplayName = "Mark")
        val sermon = repository.shareSermon(
            circleId = circle.id,
            title = "The Power of Grace",
            preacher = "Pastor Tim",
            scripturePassage = "Romans 5:1-8",
            discussionGuideJson = "📖 *THE POWER OF GRACE*\n• Discussion Question 1\n• Question 2",
            transcriptSummary = "A message exploring justification by faith and peace with God."
        )

        assertNotNull(sermon.id)
        val sermons = repository.observeSermons(circle.id).first()
        assertEquals(1, sermons.size)
        assertEquals("The Power of Grace", sermons.first().title)
        assertEquals("Pastor Tim", sermons.first().preacher)
        assertTrue(sermons.first().discussionGuideJson.contains("THE POWER OF GRACE"))
    }
}
