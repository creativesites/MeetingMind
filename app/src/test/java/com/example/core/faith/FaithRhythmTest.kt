package com.example.core.faith

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.scripture.BibleBooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaithRhythmTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `the year plan reads every chapter once, in order, in 365 days`() {
        val year = ReadingPlans.byId("year")!!
        assertEquals(365, year.length)
        val all = year.days.flatten()
        assertEquals(BibleBooks.all.sumOf { it.chapterCount }, all.size)
        assertEquals(all.size, all.distinct().size)
        assertEquals("Genesis 1", all.first().display())
        assertEquals("Revelation 22", all.last().display())
    }

    @Test fun `every plan is well formed`() {
        ReadingPlans.all.forEach { p -> assertTrue(p.id, p.days.isNotEmpty() && p.days.all { it.isNotEmpty() }) }
        assertEquals(90, ReadingPlans.byId("nt90")!!.length)
        assertEquals(89, ReadingPlans.byId("gospels30")!!.days.flatten().size)
        assertEquals(24, ReadingPlans.byId("advent")!!.length)
        assertEquals(40, ReadingPlans.byId("lent")!!.length)
        assertEquals(150 + 31, ReadingPlans.byId("psaprov")!!.days.flatten().size)
    }

    @Test fun `a day's readings are described compactly`() {
        val year = ReadingPlans.byId("year")!!
        assertEquals("Genesis 1–3", ReadingPlans.describe(year.days[0]))
        val mixed = ReadingPlans.byId("psaprov")!!.days[0]
        assertTrue(ReadingPlans.describe(mixed).contains("Proverbs 1"))
    }

    @Test fun `progress knows today, what's next and how far behind`() {
        val plan = ReadingPlans.byId("nt90")!!
        val p = PlanProgress(plan, startedEpochDay = 100, done = setOf(0, 1, 3))
        assertEquals(5, p.dayFor(105))
        assertEquals(2, p.nextUnread)
        assertEquals(2, p.behind(105)) // days 2 and 4 missed; today (5) isn't "behind" yet
        assertEquals(0, p.behind(100))
        assertEquals(plan.length - 1, p.dayFor(10_000))
        assertFalse(p.finished)
    }

    @Test fun `plans and the prayer list are kept`() {
        val store = FaithStore(context, null)
        store.start("nt90", 20_000)
        store.setDone("nt90", 0, true); store.setDone("nt90", 1, true); store.setDone("nt90", 1, false)
        val active = store.active().single()
        assertEquals(setOf(0), active.done)
        store.stop("nt90")
        assertTrue(store.active().isEmpty())
        val mum = store.addPerson("Mum", "her knee")
        store.addPerson("Our church")
        store.markPrayed(mum)
        assertEquals(listOf("Mum", "Our church"), store.people().map { it.name })
        assertEquals(1, store.people().first().prayedCount)
        assertEquals(1, store.prayedDays().size)
        store.removePerson(mum)
        assertEquals(listOf("Our church"), store.people().map { it.name })
        store.close()
    }

    @Test fun `rotation puts the least recently prayed first and keeps today's once ticked`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 9, 24)
        val startToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val people = listOf(
            PrayerPerson(1, "A", "", startToday - 86_400_000L, 3),
            PrayerPerson(2, "B", "", null, 0),
            PrayerPerson(3, "C", "", startToday - 3 * 86_400_000L, 2),
            PrayerPerson(4, "D", "", startToday - 2 * 86_400_000L, 1)
        )
        assertEquals(listOf("B", "C", "D"), PrayerRotation.today(people, today.toEpochDay(), zone = zone).map { it.name })
        val afterPrayingC = people.map { if (it.name == "C") it.copy(lastPrayedAt = startToday + 60_000) else it }
        assertTrue("C" in PrayerRotation.today(afterPrayingC, today.toEpochDay(), zone = zone).map { it.name })
        assertTrue(PrayerRotation.today(emptyList(), today.toEpochDay()).isEmpty())
    }

    @Test fun `reminder settings round trip, and quiet hours wrap midnight`() {
        val s = ReminderSettings(prayerTimes = setOf(PrayerTime.MORNING, PrayerTime.EVENING), readingNudge = true, meetingPrep = true, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertEquals(s, ReminderSettings.fromJson(s.toJson()))
        assertEquals(ReminderSettings(), ReminderSettings.fromJson("junk"))
        assertTrue(s.isQuiet(23 * 60)); assertTrue(s.isQuiet(3 * 60)); assertFalse(s.isQuiet(12 * 60))
        assertFalse(ReminderSettings(quietStart = 0, quietEnd = 0).isQuiet(3 * 60))
        assertTrue(ReminderSettings(quietStart = 13 * 60, quietEnd = 14 * 60).isQuiet(13 * 60 + 30))
        assertNull(ReadingPlans.byId("nope"))
    }
}
