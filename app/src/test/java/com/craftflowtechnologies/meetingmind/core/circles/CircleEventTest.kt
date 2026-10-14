package com.craftflowtechnologies.meetingmind.core.circles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CircleEventTest {

    @Test
    fun `all event types serialize and deserialize through JSON cleanly`() {
        // 1. MemberJoined
        val join = CircleEvent.MemberJoined("e1", "c1", "u1", 1000L, "Sarah")
        val joinRestored = CircleEvent.fromJsonString(join.toJsonString()) as? CircleEvent.MemberJoined
        assertNotNull(joinRestored)
        assertEquals(join, joinRestored)

        // 2. MemberLeft
        val left = CircleEvent.MemberLeft("e2", "c1", "u1", 2000L)
        val leftRestored = CircleEvent.fromJsonString(left.toJsonString()) as? CircleEvent.MemberLeft
        assertNotNull(leftRestored)
        assertEquals(left, leftRestored)

        // 3. PrayerPosted
        val prayer = CircleEvent.PrayerPosted("e3", "c1", "u1", 3000L, "p1", "Sarah", "Healing for dad", true)
        val prayerRestored = CircleEvent.fromJsonString(prayer.toJsonString()) as? CircleEvent.PrayerPosted
        assertNotNull(prayerRestored)
        assertEquals(prayer, prayerRestored)

        // 4. PrayerPrayed
        val prayed = CircleEvent.PrayerPrayed("e4", "c1", "u2", 4000L, "p1")
        val prayedRestored = CircleEvent.fromJsonString(prayed.toJsonString()) as? CircleEvent.PrayerPrayed
        assertNotNull(prayedRestored)
        assertEquals(prayed, prayedRestored)

        // 5. PrayerAnswered
        val answered = CircleEvent.PrayerAnswered("e5", "c1", "u1", 5000L, "p1")
        val answeredRestored = CircleEvent.fromJsonString(answered.toJsonString()) as? CircleEvent.PrayerAnswered
        assertNotNull(answeredRestored)
        assertEquals(answered, answeredRestored)

        // 6. TestimonyPosted
        val testimony = CircleEvent.TestimonyPosted("e6", "c1", "u1", 6000L, "t1", "Sarah", "Dad is healed!", "Praise God", "Ps 30:2", "p1")
        val testimonyRestored = CircleEvent.fromJsonString(testimony.toJsonString()) as? CircleEvent.TestimonyPosted
        assertNotNull(testimonyRestored)
        assertEquals(testimony, testimonyRestored)

        // 7. TestimonyAmen
        val amen = CircleEvent.TestimonyAmen("e7", "c1", "u2", 7000L, "t1")
        val amenRestored = CircleEvent.fromJsonString(amen.toJsonString()) as? CircleEvent.TestimonyAmen
        assertNotNull(amenRestored)
        assertEquals(amen, amenRestored)

        // 8. SermonShared
        val sermon = CircleEvent.SermonShared("e8", "c1", "u1", 8000L, "s1", "Sarah", "Grace", "Pastor John", "Eph 2:8", "Sunday", "{}", "Summary", 1200L)
        val sermonRestored = CircleEvent.fromJsonString(sermon.toJsonString()) as? CircleEvent.SermonShared
        assertNotNull(sermonRestored)
        assertEquals(sermon, sermonRestored)
    }
}
