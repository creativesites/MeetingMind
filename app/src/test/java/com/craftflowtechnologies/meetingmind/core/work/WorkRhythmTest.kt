package com.craftflowtechnologies.meetingmind.core.work

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications
import com.craftflowtechnologies.meetingmind.core.notify.DeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** When Work may speak, and what it says on a lock screen. 7 October 2026 is a Wednesday; 9 October a Friday. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkRhythmTest {
    private val zone = ZoneId.systemDefault()
    private val wed10 = LocalDateTime.of(2026, 10, 7, 10, 0)
    private fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private val client = WorkSettings()
    private val clinical = WorkSettings.forProfile(WorkProfile.CLINICAL)

    private fun event(minutesFromNow: Long, now: LocalDateTime = wed10, title: String = "Acme review") =
        PulseEvent("e1@1", title, ms(now.plusMinutes(minutesFromNow)), ms(now.plusMinutes(minutesFromNow + 30)), listOf("Ana Moyo"), listOf("ana@acme.com"))
    private fun known(e: PulseEvent, open: Int = 2, label: String? = "Ana") = PulseDayLine(e, open, 0, 0, 0, label, firstMeeting = false)

    // ------------------------------------------------------------------ the window

    @Test fun workingDaysAndHoursOpenTheWindow() {
        assertTrue(WorkWindow.open(client, wed10))
        assertTrue(WorkWindow.open(client, LocalDateTime.of(2026, 10, 7, 8, 30)))   // the first minute
        assertFalse(WorkWindow.open(client, LocalDateTime.of(2026, 10, 7, 8, 29)))  // before
        assertFalse(WorkWindow.open(client, LocalDateTime.of(2026, 10, 7, 17, 30))) // the end is out
        assertFalse(WorkWindow.open(client, LocalDateTime.of(2026, 10, 10, 10, 0))) // Saturday
        assertFalse(WorkWindow.open(client, LocalDateTime.of(2026, 10, 11, 10, 0))) // Sunday
        assertTrue(WorkWindow.isQuiet(client, LocalDateTime.of(2026, 10, 7, 22, 0)))
    }

    @Test fun theSettingsDecideTheDays() {
        val saturdays = client.copy(workDays = setOf(7))
        assertTrue(WorkWindow.open(saturdays, LocalDateTime.of(2026, 10, 10, 10, 0)))
        assertFalse(WorkWindow.open(saturdays, wed10))
        assertEquals(4, WorkWindow.calendarDay(java.time.DayOfWeek.WEDNESDAY))
        assertEquals(1, WorkWindow.calendarDay(java.time.DayOfWeek.SUNDAY))
        assertEquals(7, WorkWindow.calendarDay(java.time.DayOfWeek.SATURDAY))
    }

    @Test fun aTimeSetOutsideWorkingHoursIsMovedIntoThem() {
        assertEquals(8 * 60 + 30, WorkWindow.clampToHours(client, 6 * 60))
        assertEquals(17 * 60 + 29, WorkWindow.clampToHours(client, 23 * 60))
        assertEquals(12 * 60, WorkWindow.clampToHours(client, 12 * 60))
    }

    // ------------------------------------------------------------------ morning

    private val facts = MorningFacts(meetingsToday = 2, firstTitle = "Acme review", firstAt = "10:30", needYou = 3, inbox = 1)

    @Test fun theMorningLineFiresOnlyInsideTheWindow() {
        assertNotNull(WorkNotices.morning(client, wed10, facts))
        assertNull(WorkNotices.morning(client, LocalDateTime.of(2026, 10, 7, 7, 0), facts))   // before hours
        assertNull(WorkNotices.morning(client, LocalDateTime.of(2026, 10, 7, 21, 0), facts))  // quiet evening
        assertNull(WorkNotices.morning(client, LocalDateTime.of(2026, 10, 10, 9, 0), facts))  // Saturday
        assertNull(WorkNotices.morning(client.copy(notifyMorning = false), wed10, facts))      // switched off
        assertNull(WorkNotices.morning(client, wed10, MorningFacts(0, null, null, 0, 0)))      // nothing to say
    }

    @Test fun theMorningLineNamesTheDayForOrdinaryWork() {
        val n = WorkNotices.morning(client, wed10, facts)!!
        assertEquals("Acme review at 10:30 and 1 more · 3 things need you · 1 to file", n.text)
        assertEquals(DeepLink.Home, n.link)
    }

    @Test fun aSensitiveProfileGetsCountsOnly() {
        val n = WorkNotices.morning(clinical, wed10, facts)!!
        assertEquals("2 meetings today · 3 things need you · 1 to file", n.text)
        assertFalse(n.text.contains("Acme")); assertFalse(n.title.contains("Acme"))
    }

    // ------------------------------------------------------------------ prep

    @Test fun prepComesTheLeadTimeBeforeAnEventWithKnownPeople() {
        val soon = event(15)
        val n = WorkNotices.prep(client, wed10, soon, known(soon))!!
        assertEquals("In 15 min: Acme review", n.title)
        assertEquals("With Ana · 2 things still open. Tap to prepare.", n.text)
        assertTrue(n.link is DeepLink.Prepare)
        assertEquals("Acme review", (n.link as DeepLink.Prepare).title)
    }

    @Test fun prepWaitsForItsTimeAndSkipsStrangersAndEndedEvents() {
        val far = event(40); assertNull(WorkNotices.prep(client, wed10, far, known(far)))
        val started = event(-3); assertNull(WorkNotices.prep(client, wed10, started, known(started)))
        val stranger = event(10); assertNull(WorkNotices.prep(client, wed10, stranger, PulseDayLine(stranger, 0, 0, 0, 0, null, firstMeeting = true)))
        assertNull(WorkNotices.prep(client, wed10, stranger, null))
        val custom = event(20); assertNull(WorkNotices.prep(client, wed10, custom, known(custom))) // lead is 15
        assertNotNull(WorkNotices.prep(client.copy(prepLeadMinutes = 30), wed10, custom, known(custom)))
    }

    @Test fun prepStaysQuietOutsideTheWindowAndWhenOff() {
        val evening = LocalDateTime.of(2026, 10, 7, 17, 40)
        val e = event(10, evening); assertNull(WorkNotices.prep(client, evening, e, known(e)))
        val sat = LocalDateTime.of(2026, 10, 10, 10, 0)
        val s = event(10, sat); assertNull(WorkNotices.prep(client, sat, s, known(s)))
        val ok = event(10); assertNull(WorkNotices.prep(client.copy(notifyPrep = false), wed10, ok, known(ok)))
    }

    @Test fun aSensitivePrepLeavesOutTheTitleAndTheNames() {
        val e = event(10, title = "Ms Nkomo — follow-up")
        val n = WorkNotices.prep(clinical, wed10, e, known(e, label = "Ms Nkomo"))!!
        assertEquals("Your next meeting is in 10 min", n.title)
        assertEquals("2 things open. Tap to prepare.", n.text)
        assertFalse((n.title + n.text).contains("Nkomo"))
    }

    // ------------------------------------------------------------------ starting now (opt-in)

    @Test fun startingNowAsksOnlyWhenTurnedOn() {
        val e = event(0)
        assertNull(WorkNotices.startNow(client, wed10, e, "MEETING"))
        val on = client.copy(notifyStartNow = true)
        val n = WorkNotices.startNow(on, wed10, e, "CLIENT_CALL")!!
        assertEquals("Starting now — record?", n.title)
        assertEquals("Acme review", n.text)
        assertEquals(DeepLink.RecordEvent("e1@1", "Acme review", "CLIENT_CALL"), n.link)
        assertNull(WorkNotices.startNow(on, wed10, event(30), "MEETING"))      // not yet
        assertNull(WorkNotices.startNow(on, wed10, event(-20), "MEETING"))     // long begun
        val evening = LocalDateTime.of(2026, 10, 7, 19, 0)
        assertNull(WorkNotices.startNow(on, evening, event(0, evening), "MEETING")) // outside hours
        assertEquals("Tap to record.", WorkNotices.startNow(on.copy(profile = WorkProfile.LEGAL), wed10, e, "MEETING")!!.text)
    }

    // ------------------------------------------------------------------ weekly review

    @Test fun theWeeklyReviewIsOnItsDayAndInsideWorkingHours() {
        val fri = LocalDateTime.of(2026, 10, 9, 16, 30)
        val n = WorkNotices.weekly(client, fri, overdue = 2, waiting = 3)!!
        assertEquals("Your weekly review", n.title)
        assertEquals("2 things slipped · 3 waiting on others. Look back, and plan next week.", n.text)
        assertEquals(DeepLink.WeeklyReview, n.link)
        assertNull(WorkNotices.weekly(client, wed10, 2, 3))                                   // not the day
        assertNull(WorkNotices.weekly(client, LocalDateTime.of(2026, 10, 9, 19, 0), 2, 3))    // evening
        assertNull(WorkNotices.weekly(client.copy(notifyWeekly = false), fri, 2, 3))
        assertEquals("Look back, and plan next week.", WorkNotices.weekly(client, fri, 0, 0)!!.text)
    }

    @Test fun aReviewDayThatIsNotAWorkingDayStillWorks() {
        val sat = client.copy(weeklyReviewDay = 7)
        assertNotNull(WorkNotices.weekly(sat, LocalDateTime.of(2026, 10, 10, 10, 0), 0, 0))
        assertNull(WorkNotices.weekly(sat, LocalDateTime.of(2026, 10, 10, 6, 0), 0, 0))
    }

    // ------------------------------------------------------------------ on the lock screen

    @Test fun whatIsPostedIsWhatWasRedacted() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val e = event(10, title = "Ms Nkomo — follow-up")
        val notice = WorkNotices.prep(clinical, wed10, e, known(e, label = "Ms Nkomo"))!!
        AppNotifications.workNotice(ctx, notice)
        val posted = Shadows.shadowOf(ctx.getSystemService(NotificationManager::class.java)).allNotifications.single()
        val text = posted.extras.getString("android.title") + " " + posted.extras.getCharSequence("android.text")
        assertFalse(text.contains("Nkomo"))
        assertTrue(text.contains("Your next meeting is in 10 min"))
        assertEquals(AppNotifications.CHANNEL_WORK_RHYTHM, posted.channelId)
    }

    // ------------------------------------------------------------------ settings

    @Test fun theNewSettingsSurviveTheirJson() {
        val s = WorkSettings(notifyMorning = false, morningMinute = 9 * 60, notifyPrep = false, notifyStartNow = true, notifyWeekly = false, weeklyReviewMinute = 15 * 60, workDays = setOf(2, 4))
        assertEquals(s, WorkSettings.fromJson(s.toJson()))
        // Settings kept before W12 have none of these and read as the defaults.
        val old = WorkSettings.fromJson("""{"profile":"CLIENT_WORK","days":[2,3],"prep":20}""")
        assertTrue(old.notifyMorning && old.notifyPrep && old.notifyWeekly); assertFalse(old.notifyStartNow)
        assertEquals(20, old.prepLeadMinutes)
    }
}
