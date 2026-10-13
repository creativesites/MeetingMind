package com.craftflowtechnologies.meetingmind.feature.moments

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.audio.MeetingRecordingService
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent
import com.craftflowtechnologies.meetingmind.core.companion.MomentLedgerStore
import com.craftflowtechnologies.meetingmind.core.companion.moment.NoteOutcome
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilTicket
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.devotional.LiturgicalCalendar
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/*
 * Note-ready plumbing for the signature moments (docs/mvp/ZURI_SIGNATURE_MOMENTS.md §3.2, §4, Z-16/Z-33).
 * The facts here are read from the recording's own record; nothing is guessed. The planner
 * (`MomentPlanner`, via `MomentLedgerStore.planOpen`) decides what plays; this file only supplies facts.
 */

/** What one recording's record says, for the planner and the copy. Pure data. */
data class NoteFacts(
    val noteId: String,
    val title: String,
    val outcome: NoteOutcome,
    val type: RecordingType,
    val space: NotebookSpace,
    val sermon: Boolean,
    val sensitive: Boolean,
    val audioMs: Long?,
    val transcriptWords: Int?,
    /** Transcribed on this phone (by choice, or because the online AI couldn't be reached). */
    val onDevice: Boolean
)

object NoteFactsRules {
    private val SensitiveTags = setOf("grief", "funeral", "memorial")

    /** A prayer request, or a grief, funeral or memorial tag (§1.3, §5.10). */
    fun sensitive(type: RecordingType, tags: Collection<String>): Boolean =
        type == RecordingType.PRAYER_REQUEST || tags.any { it.trim().lowercase() in SensitiveTags }

    /**
     * How processing ended (§4). A READY recording with words but no summary is partial; one with no words
     * found no speech; one made on the phone although the person chose the online AI is the honest fallback.
     */
    fun outcome(status: String, summary: String?, transcriptWords: Int, profile: String?, engine: String?): NoteOutcome = when (status) {
        MeetingStatus.MODEL_REQUIRED.name -> NoteOutcome.MODEL_REQUIRED
        MeetingStatus.ERROR.name -> NoteOutcome.FAILED
        MeetingStatus.READY.name -> when {
            transcriptWords == 0 -> NoteOutcome.NO_SPEECH
            summary.isNullOrBlank() -> NoteOutcome.PARTIAL
            fellBackToPhone(profile, engine) -> NoteOutcome.AI_FALLBACK
            else -> NoteOutcome.COMPLETE
        }
        else -> NoteOutcome.FAILED
    }

    /** The person chose the online AI, but the transcript came from an on-device engine. */
    fun fellBackToPhone(profile: String?, engine: String?): Boolean =
        profile == ProcessingProfile.INTERNET.name && engine != null && !engine.contains("gemini", ignoreCase = true)

    fun onDevice(profile: String?, engine: String?): Boolean =
        profile != ProcessingProfile.INTERNET.name || fellBackToPhone(profile, engine)

    /** Good Friday in either the Western or the Orthodox calendar: no celebration either way. */
    fun goodFriday(date: LocalDate): Boolean =
        date == LiturgicalCalendar.westernEaster(date.year).minusDays(2) || date == LiturgicalCalendar.orthodoxEaster(date.year).minusDays(2)

    fun words(texts: List<String>): Int = texts.sumOf { t -> t.split(Regex("\\s+")).count { it.isNotBlank() } }
}

/** Reads [NoteFacts] for a recording. Null when it doesn't exist. */
suspend fun readNoteFacts(context: Context, meetingId: String): NoteFacts? {
    val db = MeetMindDatabase.getInstance(context)
    val m: MeetingEntity = db.meetingDao().getMeetingById(meetingId) ?: return null
    val segments = runCatching { db.transcriptDao().getSegmentsForMeetingDirect(meetingId) }.getOrDefault(emptyList())
    val words = NoteFactsRules.words(segments.map { it.cleanedText ?: it.text })
    val type = runCatching { RecordingType.valueOf(m.recordingType) }.getOrDefault(RecordingType.GENERAL)
    val tags = m.noteId?.let { id -> runCatching { db.noteDao().observeTagsForNote(id).first().map { it.name } }.getOrNull() }.orEmpty()
    return NoteFacts(
        noteId = meetingId,
        title = m.title,
        outcome = NoteFactsRules.outcome(m.status, m.summaryText, words, m.processingProfile, m.transcriptionEngine),
        type = type,
        space = Workflows.space(type),
        sermon = type == RecordingType.SERMON,
        sensitive = NoteFactsRules.sensitive(type, tags),
        audioMs = m.durationMs.takeIf { it > 0 },
        transcriptWords = if (m.status == MeetingStatus.READY.name) words else null,
        onDevice = NoteFactsRules.onDevice(m.processingProfile, m.transcriptionEngine)
    )
}

/** One note that became ready, for the in-app snackbar (§3.2) and the queue while recording (§3.3 row 4). */
data class NoteReadyEvent(val noteId: String, val title: String, val sermon: Boolean, val sensitive: Boolean, val atMs: Long) {
    /** The companion event the reducer understands (`CompanionMachine` handles its caps). */
    fun companionEvent(isFirstEver: Boolean) = CompanionEvent.NoteReady(noteId, isFirstEver, sensitive)
}

/**
 * The notes that became ready and haven't been announced in the app yet. The snackbar host takes them;
 * while a recording runs they wait, and after the stop they come as one "{n} notes are ready." line.
 */
object NoteReadyEvents {
    private val _pending = MutableStateFlow<List<NoteReadyEvent>>(emptyList())
    val pending: StateFlow<List<NoteReadyEvent>> = _pending.asStateFlow()

    /** The last companion `NoteReady` emitted, for anything that drives the companion state. */
    private val _companion = MutableStateFlow<CompanionEvent.NoteReady?>(null)
    val companion: StateFlow<CompanionEvent.NoteReady?> = _companion.asStateFlow()

    fun emit(event: NoteReadyEvent, isFirstEver: Boolean) {
        _companion.value = event.companionEvent(isFirstEver)
        _pending.update { list -> list.filterNot { it.noteId == event.noteId } + event }
    }

    /** Takes everything pending (the host shows it). */
    fun drain(): List<NoteReadyEvent> {
        val out = _pending.value
        _pending.value = emptyList()
        return out
    }

    private val snackbarOpens = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** The person tapped Open on the "{title} is ready." snackbar, which already nodded (§3.5). */
    fun markSnackbarOpen(noteId: String) { snackbarOpens += noteId }

    /** Read once, when the note's moment is planned. */
    fun consumeSnackbarOpen(noteId: String): Boolean = snackbarOpens.remove(noteId)

    /** The note was opened another way (auto-navigation, a notification): no snackbar for it. */
    fun dismiss(noteId: String) = _pending.update { list -> list.filterNot { it.noteId == noteId } }
}

/**
 * Notes that succeeded after the person tapped a fix (Retry, Transcribe after a model download, Redo online).
 * Their arrival gets a nod, not a celebration (§4 "Retry succeeded"). Kept in memory and in a small
 * preferences file, so a retry that finishes in the background still counts. Only ids are stored.
 */
object RetriedNotes {
    private const val File = "meetmind_retried_notes"
    private const val Key = "ids"

    fun mark(context: Context, noteId: String) {
        val p = context.applicationContext.getSharedPreferences(File, Context.MODE_PRIVATE)
        p.edit().putStringSet(Key, (p.getStringSet(Key, emptySet()).orEmpty() + noteId).toSet()).apply()
    }

    fun contains(context: Context, noteId: String): Boolean =
        context.applicationContext.getSharedPreferences(File, Context.MODE_PRIVATE).getStringSet(Key, emptySet()).orEmpty().contains(noteId)

    /** Read once, when the note's moment is planned. */
    fun consume(context: Context, noteId: String): Boolean {
        val p = context.applicationContext.getSharedPreferences(File, Context.MODE_PRIVATE)
        val set = p.getStringSet(Key, emptySet()).orEmpty()
        if (noteId !in set) return false
        p.edit().putStringSet(Key, (set - noteId).toSet()).apply()
        return true
    }
}

/**
 * Called when a recording's processing finishes (WorkManager `SUCCEEDED`, from the worker, so it also runs
 * when the app is in the background). For a ready note it issues the [UnveilTicket] (the First Keep then
 * plays on its first open) and emits the companion `NoteReady`. Failures and missing models get no ticket:
 * their open is plain with the StatusLine. Returns the facts it read, or null.
 */
suspend fun onNoteProcessed(
    context: Context,
    meetingId: String,
    nowMs: Long = System.currentTimeMillis(),
    store: MomentLedgerStore = MomentLedgerStore(context)
): NoteFacts? {
    val facts = runCatching { readNoteFacts(context, meetingId) }.getOrNull() ?: return null
    if (facts.outcome == NoteOutcome.FAILED || facts.outcome == NoteOutcome.MODEL_REQUIRED) return facts
    val db = MeetMindDatabase.getInstance(context)
    val readyCount = runCatching { db.meetingDao().getMeetingsWithStatus(MeetingStatus.READY.name).size }.getOrDefault(2)
    val firstEver = readyCount <= 1
    runCatching {
        store.issueTicket(
            UnveilTicket(
                noteId = meetingId, readyAtMs = nowMs, space = facts.space, sermon = facts.sermon,
                partial = facts.outcome == NoteOutcome.PARTIAL, firstEver = firstEver
            )
        )
    }
    NoteReadyEvents.emit(NoteReadyEvent(meetingId, facts.title, facts.sermon, facts.sensitive, nowMs), firstEver)
    return facts
}

/** True while a recording is being captured anywhere in the app. */
fun recordingActive(): Boolean = MeetingRecordingService.capturing.value
