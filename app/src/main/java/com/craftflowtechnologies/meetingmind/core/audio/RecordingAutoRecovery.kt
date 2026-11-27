package com.craftflowtechnologies.meetingmind.core.audio

import android.content.Context
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.MeetingSource
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Brings back a recording that was interrupted (the process killed, a crash, a flat battery)
 * without asking first, so nothing recorded can be lost by a dismissed prompt. Its audio is
 * salvaged (the ADTS capture is trimmed and remuxed), a meeting row is made with status SAVED
 * ("audio kept, notes not made yet"), and only then is its journal cleared. The person is then
 * told and offered "Make notes" or "Later"; either way the recording is in their notes.
 *
 * Runs on launch, and in the background after a new recording starts, for any journal the new
 * recording moved aside ([RecordingJournalStore.parkIfOther]).
 */
object RecordingAutoRecovery {

    sealed interface Outcome {
        val entry: RecordingJournalEntry

        /** Saved as a SAVED meeting. */
        data class Recovered(override val entry: RecordingJournalEntry, val audioPath: String, val durationMs: Long) : Outcome

        /** A row already existed: the process died between saving and clearing the journal. */
        data class AlreadySaved(override val entry: RecordingJournalEntry) : Outcome

        /** Nothing playable reached the disk (only the first seconds were recorded). */
        data class NoAudio(override val entry: RecordingJournalEntry) : Outcome

        /** The row couldn't be written. The journal is kept, and it is tried again next launch. */
        data class NotYet(override val entry: RecordingJournalEntry) : Outcome
    }

    /** The outside world, so the decision in [recoverOne] runs on the JVM. */
    internal interface Ports {
        suspend fun rowExists(meetingId: String): Boolean
        /** A playable file and its duration, or null when nothing playable was saved. */
        suspend fun salvage(entry: RecordingJournalEntry): Pair<String, Long>?
        suspend fun saveRow(entry: RecordingJournalEntry, audioPath: String, durationMs: Long)
    }

    /** One interrupted recording. Never throws; the journal is cleared by the caller unless [Outcome.NotYet]. */
    internal suspend fun recoverOne(entry: RecordingJournalEntry, ports: Ports): Outcome {
        if (runCatching { ports.rowExists(entry.meetingId) }.getOrDefault(false)) return Outcome.AlreadySaved(entry)
        val audio = runCatching { ports.salvage(entry) }.getOrNull() ?: return Outcome.NoAudio(entry)
        val durationMs = maxOf(entry.lastKnownDurationMs, audio.second)
        return if (runCatching { ports.saveRow(entry, audio.first, durationMs) }.isSuccess) {
            Outcome.Recovered(entry, audio.first, durationMs)
        } else {
            Outcome.NotYet(entry)
        }
    }

    /**
     * Recovers the journal (unless that recording is still live) and every parked journal.
     * [isCapturing] says whether the main journal belongs to the recording running now.
     */
    internal suspend fun recoverAll(store: RecordingJournalStore, ports: Ports, isCapturing: Boolean): List<Outcome> {
        val outcomes = mutableListOf<Outcome>()
        if (!isCapturing) {
            store.read()?.takeIf { it.state in RecordingJournalStore.RECOVERABLE_STATES }?.let { entry ->
                val outcome = recoverOne(entry, ports)
                outcomes += outcome
                if (outcome !is Outcome.NotYet) store.clearIf(entry.meetingId)
            }
        }
        for (entry in store.parked()) {
            if (entry.state !in RecordingJournalStore.RECOVERABLE_STATES) {
                store.clearParked(entry.meetingId)
                continue
            }
            val outcome = recoverOne(entry, ports)
            outcomes += outcome
            if (outcome !is Outcome.NotYet) store.clearParked(entry.meetingId)
        }
        return outcomes
    }

    private val mutex = Mutex()

    private val _notices = MutableStateFlow<List<Outcome>>(emptyList())
    /** What to tell the person, oldest first: recovered, nothing saved, or not added yet. */
    val notices: StateFlow<List<Outcome>> = _notices.asStateFlow()

    fun dismiss(outcome: Outcome) {
        _notices.value = _notices.value - outcome
    }

    /** Recovers everything pending. One run at a time, so a recording is never saved twice. */
    suspend fun recoverPending(context: Context): List<Outcome> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val outcomes = recoverAll(
                store = RecordingJournalStore(app),
                ports = AppPorts(app),
                isCapturing = MeetingRecordingService.capturing.value
            )
            outcomes.forEach { o ->
                com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs.log("recording", "recovery ${o.entry.meetingId}: ${o.javaClass.simpleName}")
            }
            val notable = outcomes.filter { it !is Outcome.AlreadySaved }
            if (notable.isNotEmpty()) _notices.value = _notices.value + notable
            outcomes
        }
    }

    private class AppPorts(private val context: Context) : Ports {
        private val database get() = MeetMindDatabase.getInstance(context)

        override suspend fun rowExists(meetingId: String): Boolean =
            database.meetingDao().getMeetingById(meetingId) != null

        override suspend fun salvage(entry: RecordingJournalEntry): Pair<String, Long>? =
            RecordingAudioSalvager.salvageForRecovery(context, entry.meetingId, entry.audioFilePath)
                ?.let { it.file.absolutePath to it.durationMs }

        override suspend fun saveRow(entry: RecordingJournalEntry, audioPath: String, durationMs: Long) {
            val type = RecordingType.entries.firstOrNull { it.name == entry.recordingType } ?: RecordingType.GENERAL
            MeetingRepository(context, database).createInitialMeeting(
                id = entry.meetingId,
                title = entry.title,
                source = MeetingSource.LOCAL_RECORDING,
                audioFilePath = audioPath,
                recordingType = type,
                customContext = entry.customContext,
                speakerCountPreference = entry.speakerCountPreference,
                language = entry.language ?: "en",
                noteId = entry.noteId
            )
            // SAVED: the audio is kept and the notes are not made yet, so it shows in the library
            // with "Make notes" rather than as a recording still in progress.
            val dao = database.meetingDao()
            dao.getMeetingById(entry.meetingId)?.let { row ->
                dao.updateMeeting(row.copy(status = MeetingStatus.SAVED.name, durationMs = maxOf(row.durationMs, durationMs), updatedAt = System.currentTimeMillis()))
            }
        }
    }
}
