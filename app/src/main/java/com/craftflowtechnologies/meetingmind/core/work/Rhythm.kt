package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvents
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.notify.AppNotifications
import com.craftflowtechnologies.meetingmind.core.notify.DailyAlarms
import com.craftflowtechnologies.meetingmind.core.notify.DeepLink
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * When Work may speak (docs/PLAN_PROFESSIONAL.md D5.1, §8.4): only on the person's working days,
 * inside their working hours. Everything outside that is quiet. The weekly review is the one
 * nudge that may sit on a day off, because the person chose that day for it, and even then it
 * waits for working hours.
 */
object WorkWindow {
    /** `java.time` to the Calendar numbering [WorkSettings.workDays] uses: Sunday 1 … Saturday 7. */
    fun calendarDay(day: DayOfWeek): Int = if (day == DayOfWeek.SUNDAY) 1 else day.value + 1

    fun isWorkingDay(s: WorkSettings, date: LocalDate) = calendarDay(date.dayOfWeek) in s.workDays

    fun isReviewDay(s: WorkSettings, date: LocalDate) = calendarDay(date.dayOfWeek) == s.weeklyReviewDay

    fun inWorkingHours(s: WorkSettings, minuteOfDay: Int) = minuteOfDay in s.workStartMinute until s.workEndMinute

    private fun minute(now: LocalDateTime) = now.hour * 60 + now.minute

    /** Whether ordinary nudges (Pulse, Prep, Starting now) may arrive at [now]. */
    fun open(s: WorkSettings, now: LocalDateTime) = isWorkingDay(s, now.toLocalDate()) && inWorkingHours(s, minute(now))

    /** Whether the weekly review may arrive at [now]: its day, and working hours. */
    fun openForReview(s: WorkSettings, now: LocalDateTime) = isReviewDay(s, now.toLocalDate()) && inWorkingHours(s, minute(now))

    fun isQuiet(s: WorkSettings, now: LocalDateTime) = !open(s, now)

    /** A time the person set outside their working hours is moved to the nearest edge of them. */
    fun clampToHours(s: WorkSettings, minuteOfDay: Int): Int =
        if (s.workEndMinute <= s.workStartMinute) minuteOfDay else minuteOfDay.coerceIn(s.workStartMinute, s.workEndMinute - 1)
}

/** The event a Prep notification opened the app for, until its sheet is shown and dismissed. */
object PendingPrepare { val event = kotlinx.coroutines.flow.MutableStateFlow<PulseEvent?>(null) }

enum class WorkNoticeKind(val id: Int) { MORNING(7001), PREP(7100), START_NOW(7200), WEEKLY(7002) }

/** What a Work notification says and where it goes. [id] is stable per kind (per event for Prep and Start). */
data class WorkNotice(val kind: WorkNoticeKind, val id: Int, val title: String, val text: String, val link: DeepLink)

/** Facts the morning line is made from. */
data class MorningFacts(val meetingsToday: Int, val firstTitle: String?, val firstAt: String?, val needYou: Int, val inbox: Int)

/**
 * The words of each Work notification. A sensitive profile (clinical, legal…) gets counts and
 * nothing else — no meeting title, no name, no matter — because a lock screen is public.
 */
object WorkNotices {
    private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"

    fun morning(s: WorkSettings, now: LocalDateTime, f: MorningFacts): WorkNotice? {
        if (!s.notifyMorning || !WorkWindow.open(s, now)) return null
        if (f.meetingsToday == 0 && f.needYou == 0 && f.inbox == 0) return null
        val parts = mutableListOf<String>()
        if (f.meetingsToday > 0) {
            parts += if (!s.profile.sensitive && f.firstTitle != null && f.meetingsToday == 1) "${f.firstTitle}${f.firstAt?.let { " at $it" }.orEmpty()}"
            else if (!s.profile.sensitive && f.firstTitle != null) "${f.firstTitle}${f.firstAt?.let { " at $it" }.orEmpty()} and ${f.meetingsToday - 1} more"
            else plural(f.meetingsToday, "meeting") + " today"
        }
        if (f.needYou > 0) parts += plural(f.needYou, "thing") + " need" + (if (f.needYou == 1) "s" else "") + " you"
        if (f.inbox > 0) parts += "${f.inbox} to file"
        return WorkNotice(WorkNoticeKind.MORNING, WorkNoticeKind.MORNING.id, "Your day", parts.joinToString(" · "), DeepLink.Home)
    }

    /** Prep, [WorkSettings.prepLeadMinutes] before an event with people MeetingMind already knows. */
    fun prep(s: WorkSettings, now: LocalDateTime, event: PulseEvent, line: PulseDayLine?, zone: ZoneId = ZoneId.systemDefault()): WorkNotice? {
        if (!s.notifyPrep || !WorkWindow.open(s, now)) return null
        if (line == null || line.firstMeeting) return null // nobody known: nothing to prepare from
        val begin = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.begin), zone)
        val minutes = java.time.Duration.between(now, begin).toMinutes()
        if (minutes < 1 || minutes > s.prepLeadMinutes) return null
        val open = line.open
        val title = if (s.profile.sensitive) "Your next meeting is in $minutes min" else "In $minutes min: ${event.title}"
        val text = when {
            s.profile.sensitive -> if (open > 0) "${plural(open, "thing")} open. Tap to prepare." else "Tap to prepare."
            line.withLabel != null && open > 0 -> "With ${line.withLabel} · ${plural(open, "thing")} still open. Tap to prepare."
            line.withLabel != null -> "With ${line.withLabel}. Tap to prepare."
            else -> "Tap to prepare."
        }
        return WorkNotice(WorkNoticeKind.PREP, WorkNoticeKind.PREP.id + (event.key.hashCode() and 0x3F), title, text,
            DeepLink.Prepare(event.key, event.title, event.begin, event.end, event.people.joinToString(", "), event.emails.joinToString(", ")))
    }

    /** "Starting now — record?", opt-in, for any event that starts within a few minutes either side. */
    fun startNow(s: WorkSettings, now: LocalDateTime, event: PulseEvent, workflow: String, zone: ZoneId = ZoneId.systemDefault()): WorkNotice? {
        if (!s.notifyStartNow || !WorkWindow.open(s, now)) return null
        val begin = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.begin), zone)
        val minutes = java.time.Duration.between(now, begin).toMinutes()
        if (minutes !in -5..1) return null
        return WorkNotice(WorkNoticeKind.START_NOW, WorkNoticeKind.START_NOW.id + (event.key.hashCode() and 0x3F),
            "Starting now — record?", if (s.profile.sensitive) "Tap to record." else event.title,
            DeepLink.RecordEvent(event.key, event.title, workflow))
    }

    fun weekly(s: WorkSettings, now: LocalDateTime, overdue: Int, waiting: Int): WorkNotice? {
        if (!s.notifyWeekly || !WorkWindow.openForReview(s, now)) return null
        val bits = buildList {
            if (overdue > 0) add("${plural(overdue, "thing")} slipped")
            if (waiting > 0) add("$waiting waiting on others")
        }
        return WorkNotice(WorkNoticeKind.WEEKLY, WorkNoticeKind.WEEKLY.id, "Your weekly review",
            if (bits.isEmpty()) "Look back, and plan next week." else bits.joinToString(" · ") + ". Look back, and plan next week.", DeepLink.WeeklyReview)
    }
}

/**
 * Sets the Work nudges going: the morning line and the weekly review on wall-clock alarms (so they
 * keep their time of day), and a 15-minute check for Prep and "Starting now". Whatever fires is
 * checked against [WorkWindow] again when it runs, so a changed setting or a delayed alarm can
 * never speak outside the person's hours.
 */
object WorkRhythmScheduler {
    private const val MORNING = "work-morning"
    private const val WEEKLY = "work-weekly"
    private const val TICK = "work-rhythm-tick"

    fun handles(key: String) = key == MORNING || key == WEEKLY

    fun sync(context: Context, s: WorkSettings, hasWork: Boolean = true) {
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull()
        if (!hasWork) { DailyAlarms.cancel(context, MORNING); DailyAlarms.cancel(context, WEEKLY); wm?.cancelUniqueWork(TICK); return }
        if (s.notifyMorning) DailyAlarms.schedule(context, MORNING, WorkWindow.clampToHours(s, s.morningMinute)) else DailyAlarms.cancel(context, MORNING)
        if (s.notifyWeekly) DailyAlarms.schedule(context, WEEKLY, WorkWindow.clampToHours(s, s.weeklyReviewMinute)) else DailyAlarms.cancel(context, WEEKLY)
        if (wm == null) return
        if (s.notifyPrep || s.notifyStartNow) {
            wm.enqueueUniquePeriodicWork(TICK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WorkRhythmWorker>(15, TimeUnit.MINUTES).setInputData(workDataOf(WorkRhythmWorker.KIND to WorkRhythmWorker.TICK)).build())
        } else wm.cancelUniqueWork(TICK)
    }

    fun onAlarm(context: Context, key: String) {
        val kind = if (key == MORNING) WorkRhythmWorker.MORNING else WorkRhythmWorker.WEEKLY
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork("$key-run", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WorkRhythmWorker>().setInputData(workDataOf(WorkRhythmWorker.KIND to kind)).build())
        }
    }
}

class WorkRhythmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = UserPreferencesManager(ctx)
        val s = prefs.workSettings.first()
        if (!prefs.usesWork()) return Result.success()
        val now = LocalDateTime.now()
        val db = MeetMindDatabase.getInstance(ctx)
        val pulse = Pulse(db)
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val events = todayEvents(ctx, prefs, nowMs)
        val notices = when (inputData.getString(KIND)) {
            MORNING -> {
                val lines = pulse.today(events)
                val attention = pulse.attention(nowMs, limit = 50, quietDays = s.quietDays).size
                val inbox = db.inboxDao().open().size
                val first = events.filter { it.begin >= nowMs - 15 * 60_000L }.minByOrNull { it.begin }
                val at = first?.let { java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault()).format(java.util.Date(it.begin)) }
                listOfNotNull(WorkNotices.morning(s, now, MorningFacts(lines.size, first?.title, at, attention, inbox)))
            }
            WEEKLY -> {
                val weekly = WeeklyReviews(db) { nowMs }
                val plan = weekly.build(nowMs)
                listOfNotNull(WorkNotices.weekly(s, now, plan.slipped.size, plan.waitingOnThem.size + plan.waitingOnMe.size))
            }
            else -> {
                val soon = events.filter { it.begin in nowMs - 6 * 60_000L..nowMs + (s.prepLeadMinutes + 1) * 60_000L }
                if (soon.isEmpty()) emptyList() else {
                    val lines = pulse.today(soon).associateBy { it.event.key }
                    soon.flatMap { e -> listOfNotNull(WorkNotices.prep(s, now, e, lines[e.key], zone), WorkNotices.startNow(s, now, e, DEFAULT_WORKFLOW, zone)) }
                }
            }
        }
        val sent = ctx.getSharedPreferences("work_rhythm_sent", Context.MODE_PRIVATE)
        notices.forEach { n ->
            // Once per occasion: an event's Prep and Start once each, the daily ones once a day.
            val token = "${n.kind}:${n.id}:${LocalDate.now()}"
            if (sent.getBoolean(token, false)) return@forEach
            sent.edit().putBoolean(token, true).apply()
            AppNotifications.workNotice(ctx, n)
        }
        // The next-meeting widget follows the calendar as it changes through the day.
        if (inputData.getString(KIND) == TICK) com.craftflowtechnologies.meetingmind.core.widget.Widgets.refresh(ctx)
        return Result.success()
    }

    private suspend fun todayEvents(ctx: Context, prefs: UserPreferencesManager, nowMs: Long): List<PulseEvent> {
        val on = prefs.preferencesFlow.first().calendarEnabled
        val calendar = CalendarEvents(ctx)
        if (!on || !calendar.hasPermission()) return emptyList()
        val end = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return calendar.between(nowMs - 60 * 60_000L, end).filter { !it.allDay }
            .map { PulseEvent(it.key, it.title, it.begin, it.end, it.otherPeople, it.attendees.filter { a -> !a.isSelf }.mapNotNull { a -> a.email }) }
    }

    companion object {
        const val KIND = "kind"
        const val MORNING = "morning"
        const val WEEKLY = "weekly"
        const val TICK = "tick"
        /** The recording type the "Starting now" notification opens with, until the event says otherwise. */
        const val DEFAULT_WORKFLOW = "MEETING"
    }
}
