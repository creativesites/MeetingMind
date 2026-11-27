package com.craftflowtechnologies.meetingmind.feature.today.companion

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.companion.LocalClock
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalNotes
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.TimeZone

/** What "seen" the companion's home lines remember: keys only, never note content. */
class HomeNudgeMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("home_companion_lines", Context.MODE_PRIVATE)

    fun seen(key: String): Boolean = prefs.getBoolean("seen:$key", false)
    fun markSeen(key: String) { prefs.edit().putBoolean("seen:$key", true).apply() }

    /** The tip shown on [day], if any, so the same one stays all day. */
    fun tipFor(day: Long): Tip? = prefs.getString("tip:$day", null)?.let { n -> Tip.entries.firstOrNull { it.name == n } }
    fun rememberTip(day: Long, tip: Tip) {
        if (prefs.getString("tip:$day", null) == null) prefs.edit().putString("tip:$day", tip.name).apply()
    }
}

/**
 * Reads the facts for the companion's line on a home, once each time a home is shown. Every read is
 * guarded: a failure means the companion just says nothing.
 */
class HomeCompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val memory = HomeNudgeMemory(application)
    private val _facts = MutableStateFlow<HomeNudgeFacts?>(null)
    val facts: StateFlow<HomeNudgeFacts?> = _facts.asStateFlow()
    private var loadJob: Job? = null

    private val guard = CoroutineExceptionHandler { _, e -> Log.w(TAG, "home companion line failed", e) }

    fun seen(key: String) = memory.seen(key)

    fun refresh(now: Long = System.currentTimeMillis()) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(guard) {
            _facts.value = try { load(getApplication(), now) } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "home companion facts", e); null
            }
        }
    }

    /** The line was shown: a tip becomes today's tip. */
    fun shown(nudge: HomeNudge) {
        val tip = nudge.tip ?: return
        val today = _facts.value?.today ?: return
        memory.rememberTip(today, tip)
    }

    /** Opened or waved away: it doesn't come back. */
    fun done(nudge: HomeNudge) {
        if (!nudge.dismissable && nudge.action == null) return
        memory.markSeen(nudge.key) // the line re-picks (HomeCompanionLine bumps its version)
    }

    private suspend fun load(context: Context, now: Long): HomeNudgeFacts {
        val db = MeetMindDatabase.getInstance(context)
        val clock = LocalClock(now, TimeZone.getDefault().getOffset(now).toLong())
        val today = clock.localDay

        val recordings = runCatching { db.meetingDao().getMeetingCountFlow().first() }.getOrDefault(0)
        val written = runCatching { db.noteDao().observeWrittenCount().first() }.getOrDefault(0)

        val ledger = MomentLedgerStore(context)
        val ready = runCatching { db.meetingDao().getMeetingsWithStatus(MeetingStatus.READY.name) }.getOrDefault(emptyList())
            .filter { now - it.updatedAt in 0..HomeNudges.READY_FOR_MS }
            .sortedByDescending { it.updatedAt }
            .firstOrNull { runCatching { ledger.peekTicket(it.id, now) }.getOrNull() != null }

        val jobs = runCatching { db.processingJobDao().getUnfinishedJobs() }.getOrDefault(emptyList())
        val recent = runCatching { db.noteDao().observeRecent(60).first() }.getOrDefault(emptyList())
        val last = recent.firstOrNull()
        val earliest = runCatching { db.noteDao().earliestNoteDate() }.getOrNull()
        val devotionalReads = runCatching { db.noteDao().observeByWorkflows(listOf(DEVOTIONAL)).first() }.getOrDefault(emptyList())
            .mapNotNull { n -> runCatching { org.json.JSONObject(n.metadataJson).optString(DevotionalNotes.META_OPENED).toLongOrNull() }.getOrNull() }

        return HomeNudgeFacts(
            today = today,
            isNew = recordings == 0 && written == 0,
            readyMeeting = ready?.let { NudgeItem(it.id, it.title, it.noteId) },
            processing = jobs.maxByOrNull { it.startedAt }?.let { NudgeItem(it.meetingId, it.meetingTitle) },
            processingCount = jobs.size,
            lastNote = last?.let { NudgeItem(it.id, it.title.ifBlank { "your last note" }) },
            daysSinceLastNote = last?.let { today - clock.dayOf(it.updatedAt) } ?: 0,
            firstNoteDay = earliest?.let(clock::dayOf),
            tipShownToday = memory.tipFor(today),
            noteTimes = recent.map { it.createdAt },
            devotionalReadTimes = devotionalReads,
            nowMs = now,
            utcOffsetMs = clock.utcOffsetMs
        )
    }

    private companion object {
        const val TAG = "HomeCompanion"
        val DEVOTIONAL = com.craftflowtechnologies.meetingmind.core.model.RecordingType.DEVOTIONAL.name
    }
}
