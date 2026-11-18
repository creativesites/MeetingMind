package com.craftflowtechnologies.meetingmind.core.audio

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Automatic recovery of an interrupted recording: nothing recorded may be lost, and a journal is
 * cleared only once its recording has a meeting row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingAutoRecoveryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: RecordingJournalStore

    private fun entry(id: String, state: RecordingState = RecordingState.RECORDING, durationMs: Long = 60_000L) = RecordingJournalEntry(
        meetingId = id, title = "Talk $id", recordingType = "MEETING",
        audioFilePath = "/data/meetings/$id/capture.aac", lastKnownDurationMs = durationMs,
        markerCount = 0, noteCount = 0, lastHeartbeatAtMs = 1L, state = state.name,
        language = "fr", speakerCountPreference = 3, noteId = "note-$id", customContext = null
    )

    /** In-memory ports: which rows exist, what salvage finds, whether saving works. */
    private class FakePorts(
        val existing: MutableSet<String> = mutableSetOf(),
        val audio: Map<String, Pair<String, Long>> = emptyMap(),
        val saveFails: Boolean = false
    ) : RecordingAutoRecovery.Ports {
        val saved = mutableListOf<Triple<RecordingJournalEntry, String, Long>>()
        var salvageCalls = 0
        override suspend fun rowExists(meetingId: String) = meetingId in existing
        override suspend fun salvage(entry: RecordingJournalEntry): Pair<String, Long>? { salvageCalls++; return audio[entry.meetingId] }
        override suspend fun saveRow(entry: RecordingJournalEntry, audioPath: String, durationMs: Long) {
            if (saveFails) throw IllegalStateException("disk full")
            saved += Triple(entry, audioPath, durationMs)
            existing += entry.meetingId
        }
    }

    @Before fun setUp() { store = RecordingJournalStore(context); clean() }
    @After fun tearDown() = clean()
    private fun clean() {
        store.clear()
        File(context.filesDir, "recovery_parked").deleteRecursively()
    }

    // ---- One recording ----------------------------------------------------------------------

    @Test
    fun `an interrupted recording is saved with the longer of the two durations`() = runBlocking {
        val ports = FakePorts(audio = mapOf("a" to ("/data/meetings/a/audio.m4a" to 75_000L)))
        val outcome = RecordingAutoRecovery.recoverOne(entry("a", durationMs = 70_000L), ports)
        assertEquals(RecordingAutoRecovery.Outcome.Recovered(entry("a", durationMs = 70_000L), "/data/meetings/a/audio.m4a", 75_000L), outcome)
        assertEquals("/data/meetings/a/audio.m4a", ports.saved.single().second)
        // The note, language and speakers it was started with travel with it.
        assertEquals("note-a", ports.saved.single().first.noteId)
        assertEquals("fr", ports.saved.single().first.language)
    }

    @Test
    fun `a recording that already has a row is not saved twice`() = runBlocking {
        val ports = FakePorts(existing = mutableSetOf("a"), audio = mapOf("a" to ("/x.m4a" to 1L)))
        assertTrue(RecordingAutoRecovery.recoverOne(entry("a"), ports) is RecordingAutoRecovery.Outcome.AlreadySaved)
        assertEquals(0, ports.salvageCalls)
        assertTrue(ports.saved.isEmpty())
    }

    @Test
    fun `nothing playable gives NoAudio and no row`() = runBlocking {
        val ports = FakePorts()
        assertTrue(RecordingAutoRecovery.recoverOne(entry("a"), ports) is RecordingAutoRecovery.Outcome.NoAudio)
        assertTrue(ports.saved.isEmpty())
    }

    @Test
    fun `a row that can't be written gives NotYet`() = runBlocking {
        val ports = FakePorts(audio = mapOf("a" to ("/x.m4a" to 1L)), saveFails = true)
        assertTrue(RecordingAutoRecovery.recoverOne(entry("a"), ports) is RecordingAutoRecovery.Outcome.NotYet)
    }

    // ---- The journal and parked journals ----------------------------------------------------

    @Test
    fun `the journal is cleared only after its recording has a row`() = runBlocking {
        store.write(entry("a"))
        RecordingAutoRecovery.recoverAll(store, FakePorts(audio = mapOf("a" to ("/x.m4a" to 1L)), saveFails = true), isCapturing = false)
        assertEquals("a", store.read()?.meetingId)

        val outcomes = RecordingAutoRecovery.recoverAll(store, FakePorts(audio = mapOf("a" to ("/x.m4a" to 1L))), isCapturing = false)
        assertTrue(outcomes.single() is RecordingAutoRecovery.Outcome.Recovered)
        assertNull(store.read())
    }

    @Test
    fun `a recording stopped mid-save is recovered too`() = runBlocking {
        for (state in listOf(RecordingState.PAUSED, RecordingState.STOPPING, RecordingState.SAVING, RecordingState.SAVE_FAILED)) {
            store.write(entry("a", state))
            val outcomes = RecordingAutoRecovery.recoverAll(store, FakePorts(audio = mapOf("a" to ("/x.m4a" to 1L))), isCapturing = false)
            assertTrue(state.name, outcomes.single() is RecordingAutoRecovery.Outcome.Recovered)
            assertNull(state.name, store.read())
        }
    }

    @Test
    fun `the live recording's own journal is left alone`() = runBlocking {
        store.write(entry("live"))
        val ports = FakePorts(audio = mapOf("live" to ("/x.m4a" to 1L)))
        assertTrue(RecordingAutoRecovery.recoverAll(store, ports, isCapturing = true).isEmpty())
        assertEquals("live", store.read()?.meetingId)
        assertEquals(0, ports.salvageCalls)
    }

    @Test
    fun `a new recording parks the unrecovered one instead of overwriting it`() = runBlocking {
        store.write(entry("old"))
        assertTrue(store.parkIfOther("new"))
        store.write(entry("new"))

        // While "new" records, the parked "old" one is recovered; "new"'s journal stays.
        val outcomes = RecordingAutoRecovery.recoverAll(store, FakePorts(audio = mapOf("old" to ("/old.m4a" to 1L))), isCapturing = true)
        assertEquals("old", (outcomes.single() as RecordingAutoRecovery.Outcome.Recovered).entry.meetingId)
        assertTrue(store.parked().isEmpty())
        assertEquals("new", store.read()?.meetingId)
    }

    @Test
    fun `a parked recording whose row can't be written stays parked`() = runBlocking {
        store.write(entry("old"))
        store.parkIfOther("new")
        RecordingAutoRecovery.recoverAll(store, FakePorts(audio = mapOf("old" to ("/old.m4a" to 1L)), saveFails = true), isCapturing = true)
        assertEquals(listOf("old"), store.parked().map { it.meetingId })
    }

    @Test
    fun `parking leaves the same recording and finished journals in place`() {
        store.write(entry("a"))
        assertFalse(store.parkIfOther("a"))
        store.write(entry("b", RecordingState.SAVED))
        assertFalse(store.parkIfOther("c"))
        assertTrue(store.parked().isEmpty())
    }

    @Test
    fun `clearIf keeps a journal that belongs to another recording`() {
        store.write(entry("b"))
        store.clearIf("a")
        assertEquals("b", store.read()?.meetingId)
        store.clearIf("b")
        assertNull(store.read())
    }

    @Test
    fun `parked journals keep every field, and an old journal reads without the new ones`() {
        store.write(entry("a"))
        store.parkIfOther("b")
        assertEquals(entry("a"), store.parked().single())

        File(context.filesDir, "recovery.json").writeText(
            """{"meetingId":"o","title":"Old","recordingType":"MEETING","audioFilePath":"/p","lastKnownDurationMs":5,"lastHeartbeatAtMs":1,"state":"RECORDING"}"""
        )
        val old = store.read()!!
        assertNull(old.language)
        assertNull(old.speakerCountPreference)
        assertNull(old.noteId)
    }
}
