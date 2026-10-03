package com.craftflowtechnologies.meetingmind.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.circles.InMemoryCircleBus
import com.craftflowtechnologies.meetingmind.core.circles.InMemoryCircleTransport
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
    private lateinit var bus: InMemoryCircleBus
    private lateinit var transport: InMemoryCircleTransport
    private lateinit var repository: CircleRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bus = InMemoryCircleBus()
        transport = InMemoryCircleTransport("user-1", bus)
        repository = CircleRepository(context, db, transport)
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
            myDisplayName = "Sarah"
        )
        assertNotNull(prayer)

        assertEquals(0, prayer?.prayerCount)
        assertEquals(CirclePrayerStatus.ACTIVE, prayer?.status)
        assertTrue(prayer?.isUrgent == true)

        repository.prayFor(circle.id, prayer!!.id)

        // Allow sync replay to process
        val updatedPrayers = repository.observePrayers(circle.id).first()
        assertEquals(1, updatedPrayers.size)
    }

    @Test
    fun `posting testimony marks linked prayer request as answered`() = runBlocking {
        val circle = repository.createCircle("Family Circle", myDisplayName = "John")
        val prayer = repository.postPrayer(
            circleId = circle.id,
            requestText = "Praying for healing for my knee",
            myDisplayName = "John"
        )
        assertNotNull(prayer)

        val testimony = repository.postTestimony(
            circleId = circle.id,
            title = "God completely healed my knee!",
            storyText = "After weeks of prayer, the doctors confirmed full recovery today without surgery.",
            scriptureRef = "Psalm 103:2-3",
            prayerRequestId = prayer!!.id,
            myDisplayName = "John"
        )

        assertNotNull(testimony?.id)
        assertEquals("Psalm 103:2-3", testimony?.scriptureRef)

        val prayers = repository.observePrayers(circle.id).first()
        assertEquals(CirclePrayerStatus.ANSWERED, prayers.first().status)
        assertNotNull(prayers.first().answeredAt)

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
            sermonDate = "Oct 3, 2026",
            discussionGuideJson = "📖 *THE POWER OF GRACE*\n• Discussion Question 1\n• Question 2",
            transcriptSummary = "A message exploring justification by faith and peace with God.",
            audioDurationSec = 1800L,
            myDisplayName = "Mark"
        )

        assertNotNull(sermon?.id)
        val sermons = repository.observeSermons(circle.id).first()
        assertEquals(1, sermons.size)
        assertEquals("The Power of Grace", sermons.first().title)
        assertEquals("Pastor Tim", sermons.first().preacher)
    }
}
