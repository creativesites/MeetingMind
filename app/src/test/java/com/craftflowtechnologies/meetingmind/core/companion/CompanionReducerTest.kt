package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent.ClockTick
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent.FormChanged
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent.Milestone
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent.NoteReady
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent.ProcessingFailed
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent.ProcessingStageChanged
import com.craftflowtechnologies.meetingmind.core.companion.CompanionEvent.RecordingStarted
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CompanionReducerTest {

    private var ledger = MomentLedger()
    private var nowMs = 1_000_000_000L
    private var ctx = ReduceContext()

    private fun context() = ctx.copy(clock = LocalClock(nowMs), ledger = ledger)

    /** Runs events in order, recording moments like the machine does. */
    private fun run(vararg events: CompanionEvent, from: CompanionUiState = CompanionUiState()): CompanionUiState =
        events.fold(from) { s, e ->
            val r = CompanionReducer.reduce(s, e, context())
            r.moments.forEach { ledger = ledger.record(it, LocalClock(nowMs)) }
            r.state
        }

    private val sermon = RecordingStarted(RecordingKind.SERMON, NotebookSpace.FAITH)
    private val meeting = RecordingStarted(RecordingKind.GENERAL, NotebookSpace.WORK)

    @Test fun `starts idle`() {
        val s = CompanionUiState()
        assertEquals(CompanionState.IDLE, s.base)
        assertEquals(CompanionState.IDLE, s.visual)
    }

    @Test fun `screen shown records the page and keeps the state`() {
        val s = run(CompanionEvent.ScreenShown(CompanionPage.HOME))
        assertEquals(CompanionPage.HOME, s.page)
        assertEquals(CompanionState.IDLE, s.base)
    }

    @Test fun `recording starts listening`() {
        val s = run(meeting)
        assertEquals(CompanionState.LISTENING, s.base)
        assertEquals(CompanionVariant.Normal, s.variant)
    }

    @Test fun `a sermon starts quiet when the setting is on`() {
        val s = run(sermon)
        assertEquals(CompanionState.LISTENING, s.base)
        assertEquals(CompanionVariant.Quiet, s.variant)
    }

    @Test fun `a sermon is not quiet when the setting is off`() {
        ctx = ctx.copy(settings = CompanionSettings(quietSermons = false))
        assertEquals(CompanionVariant.Normal, run(sermon).variant)
    }

    @Test fun `the local chip switches only this recording`() {
        val s = run(sermon, CompanionEvent.SermonQuietChanged(false))
        assertEquals(CompanionVariant.Normal, s.variant)
        val next = run(CompanionEvent.RecordingStopped, sermon, from = s)
        assertEquals("the next sermon starts quiet again", CompanionVariant.Quiet, next.variant)
    }

    @Test fun `no one-shots while quiet`() {
        val s = run(sermon, Milestone(MilestoneKind.FIRST_NOTE), CompanionEvent.Tapped, FormChanged(CompanionForm.NAS))
        assertNull(s.oneShot)
        assertTrue("the milestone was not spent", ledger.allows(Moment.Proud(MilestoneKind.FIRST_NOTE), LocalClock(nowMs)))
    }

    @Test fun `stopping a recording thinks with no caption until a real stage arrives`() {
        val s = run(meeting, CompanionEvent.RecordingStopped)
        assertEquals(CompanionState.THINKING, s.base)
        assertNull(s.caption)
    }

    @Test fun `stopping when not recording does nothing`() {
        assertEquals(CompanionState.IDLE, run(CompanionEvent.RecordingStopped).base)
    }

    @Test fun `every working stage thinks and captions that stage`() {
        val working = listOf(
            ProcessingStage.PREPARING_AUDIO, ProcessingStage.DETECTING_SPEECH, ProcessingStage.TRANSCRIBING,
            ProcessingStage.DIARIZING, ProcessingStage.CLEANING_TRANSCRIPT, ProcessingStage.ANALYZING,
            ProcessingStage.SAVING_RESULTS
        )
        for (stage in working) {
            val detail = StageDetail(doneMinutes = 3, totalMinutes = null)
            val s = run(ProcessingStageChanged(stage, detail))
            assertEquals(stage.name, CompanionState.THINKING, s.base)
            assertEquals(CaptionKey(stage, detail), s.caption)
        }
    }

    @Test fun `stage changes only change the caption`() {
        val a = run(ProcessingStageChanged(ProcessingStage.TRANSCRIBING))
        val b = run(ProcessingStageChanged(ProcessingStage.ANALYZING), from = a)
        assertEquals(CompanionState.THINKING, b.base)
        assertEquals(ProcessingStage.ANALYZING, b.caption?.stage)
    }

    @Test fun `cancelled goes idle with the cancelled caption`() {
        val s = run(ProcessingStageChanged(ProcessingStage.TRANSCRIBING), ProcessingStageChanged(ProcessingStage.CANCELLED))
        assertEquals(CompanionState.IDLE, s.base)
        assertEquals(ProcessingStage.CANCELLED, s.caption?.stage)
    }

    @Test fun `completed and idle stages`() {
        val s = run(ProcessingStageChanged(ProcessingStage.TRANSCRIBING), ProcessingStageChanged(ProcessingStage.COMPLETED))
        assertEquals(CompanionState.IDLE, s.base)
        assertEquals(s, run(ProcessingStageChanged(ProcessingStage.IDLE), from = s))
    }

    @Test fun `failure is worried with its fix`() {
        val s = run(ProcessingStageChanged(ProcessingStage.ANALYZING), ProcessingFailed(FailureReason.PROCESSING_FAILED, FixAction.RETRY))
        assertEquals(CompanionState.WORRIED, s.base)
        assertEquals(FixAction.RETRY, s.fix)
        assertNull(s.caption)
    }

    @Test fun `worried without a fix is impossible`() {
        assertThrows(IllegalArgumentException::class.java) { CompanionUiState(base = CompanionState.WORRIED, fix = null) }
    }

    @Test fun `worried outranks everything, including recording and a one-shot`() {
        val s = run(
            ProcessingFailed(FailureReason.MODEL_DOWNLOAD_FAILED, FixAction.DOWNLOAD_MODELS),
            meeting, CompanionEvent.ModelsMissing
        )
        assertEquals(CompanionState.WORRIED, s.base)
        val withShot = s.copy(oneShot = OneShot(OneShotKind.CELEBRATING, nowMs))
        assertEquals(CompanionState.WORRIED, withShot.visual)
    }

    @Test fun `a permission failure clears when recording starts again`() {
        val s = run(ProcessingFailed(FailureReason.PERMISSION_DENIED, FixAction.OPEN_SETTINGS), meeting)
        assertEquals(CompanionState.LISTENING, s.base)
    }

    @Test fun `retry thinks, then nods on success`() {
        val s = run(
            ProcessingFailed(FailureReason.PROCESSING_FAILED, FixAction.RETRY),
            CompanionEvent.RetryTapped
        )
        assertEquals(CompanionState.THINKING, s.base)
        val done = run(NoteReady("n", isFirstEver = false), from = s)
        assertEquals(OneShotKind.NOD, done.oneShot?.kind)
        assertEquals(CompanionVariant.Nod, done.variant)
    }

    @Test fun `retry that fails again is worried again`() {
        val s = run(
            ProcessingFailed(FailureReason.PROCESSING_FAILED, FixAction.RETRY),
            CompanionEvent.RetryTapped,
            ProcessingFailed(FailureReason.PROCESSING_FAILED, FixAction.RETRY)
        )
        assertEquals(CompanionState.WORRIED, s.base)
    }

    @Test fun `a non-retry fix just clears the worry`() {
        val s = run(ProcessingFailed(FailureReason.NO_STORAGE, FixAction.MANAGE_STORAGE), CompanionEvent.RetryTapped)
        assertEquals(CompanionState.IDLE, s.base)
        assertNull(s.fix)
    }

    @Test fun `retry with nothing failed does nothing`() {
        assertEquals(CompanionUiState(), run(CompanionEvent.RetryTapped))
    }

    @Test fun `first ever note gets the full celebration once`() {
        val s = run(NoteReady("a", isFirstEver = true))
        assertEquals(OneShotKind.CELEBRATING, s.oneShot?.kind)
        assertEquals(CompanionState.CELEBRATING, s.visual)
        assertFalse(ledger.allows(Moment.FirstRecordingPeak, LocalClock(nowMs)))
    }

    @Test fun `the full celebration is once a day, later notes nod`() {
        val first = run(NoteReady("a", isFirstEver = false))
        assertEquals(OneShotKind.CELEBRATING, first.oneShot?.kind)
        nowMs += 5_000
        val second = run(NoteReady("b", isFirstEver = false), from = first.copy(oneShot = null))
        assertEquals(OneShotKind.NOD, second.oneShot?.kind)
        nowMs += LocalClock.DayMs
        val tomorrow = run(NoteReady("c", isFirstEver = false), from = second.copy(oneShot = null))
        assertEquals(OneShotKind.CELEBRATING, tomorrow.oneShot?.kind)
    }

    @Test fun `nothing celebrates on sensitive notes or on Good Friday`() {
        assertNull(run(NoteReady("p", isFirstEver = true, sensitive = true)).oneShot)
        assertTrue("the peak is kept for a later note", ledger.allows(Moment.FirstRecordingPeak, LocalClock(nowMs)))
        ctx = ctx.copy(goodFriday = true)
        assertNull(run(NoteReady("q", isFirstEver = false)).oneShot)
    }

    @Test fun `note ready ends thinking`() {
        val s = run(ProcessingStageChanged(ProcessingStage.SAVING_RESULTS), NoteReady("a", false))
        assertEquals(CompanionState.IDLE, s.base)
        assertNull(s.caption)
    }

    @Test fun `one-shots end on their own time or when the host says so`() {
        val s = run(NoteReady("a", false))
        assertEquals(CompanionState.CELEBRATING, s.visual)
        assertNull(run(CompanionEvent.OneShotEnded, from = s).oneShot)
        nowMs += OneShotKind.CELEBRATING.durationMs
        assertNull(run(CompanionEvent.ScreenShown(CompanionPage.HOME), from = s).oneShot)
    }

    @Test fun `listening outranks thinking, thinking outranks reading, reading outranks sleepy`() {
        val all = run(CompanionEvent.ModelsMissing, CompanionEvent.ReadAloudStarted, ProcessingStageChanged(ProcessingStage.ANALYZING), meeting)
        assertEquals(CompanionState.LISTENING, all.base)
        assertEquals(CompanionState.THINKING, run(CompanionEvent.RecordingStopped, from = all).base)
        val reading = run(ProcessingStageChanged(ProcessingStage.COMPLETED), from = run(CompanionEvent.RecordingStopped, from = all))
        assertEquals(CompanionState.READING, reading.base)
        assertEquals(CompanionState.SLEEPY, run(CompanionEvent.ReadAloudStopped, from = reading).base)
        assertEquals(CompanionState.IDLE, run(CompanionEvent.ReadAloudStopped, CompanionEvent.ModelsReady, from = reading).base)
    }

    @Test fun `late night is sleepy and any interaction wakes it`() {
        for (h in listOf(23, 0, 3, 4)) assertEquals("$h", CompanionState.SLEEPY, run(ClockTick(h)).base)
        for (h in listOf(5, 9, 12, 22)) assertEquals("$h", CompanionState.IDLE, run(ClockTick(h)).base)
        assertEquals(CompanionState.IDLE, run(ClockTick(23), CompanionEvent.Tapped).base)
    }

    @Test fun `models missing is sleepy until ready`() {
        assertEquals(CompanionState.SLEEPY, run(CompanionEvent.ModelsMissing).base)
        assertEquals(CompanionState.IDLE, run(CompanionEvent.ModelsMissing, CompanionEvent.ModelsReady).base)
    }

    @Test fun `a milestone is proud once, ever`() {
        val s = run(Milestone(MilestoneKind.TENTH_NOTE))
        assertEquals(CompanionState.PROUD, s.visual)
        nowMs += 10 * LocalClock.DayMs
        assertNull(run(Milestone(MilestoneKind.TENTH_NOTE)).oneShot)
        assertEquals(OneShotKind.PROUD, run(Milestone(MilestoneKind.FIRST_NOTE)).oneShot?.kind)
    }

    @Test fun `questions make it curious`() {
        assertEquals(CompanionState.CURIOUS, run(CompanionEvent.OnboardingQuestion(2)).visual)
        assertEquals(CompanionState.CURIOUS, run(CompanionEvent.QuizQuestion).visual)
    }

    @Test fun `a tap nods at most once per 10 seconds`() {
        val a = run(CompanionEvent.Tapped)
        assertEquals(OneShotKind.NOD, a.oneShot?.kind)
        nowMs += 1_000
        assertNull(run(CompanionEvent.Tapped, from = a.copy(oneShot = null)).oneShot)
        nowMs += 10_000
        assertEquals(OneShotKind.NOD, run(CompanionEvent.Tapped).oneShot?.kind)
    }

    @Test fun `choosing a form nods, choosing none does not`() {
        assertEquals(OneShotKind.NOD, run(FormChanged(CompanionForm.WREN)).oneShot?.kind)
        assertNull(run(FormChanged(null)).oneShot)
    }

    @Test fun `whisper needs the flag, an opt-in and a non-sermon recording`() {
        val prompt = WhisperPrompt("w1", sourceNoteId = "note-7")
        // Flag off: nothing.
        assertNull(run(meeting, CompanionEvent.WhisperOptIn(true), CompanionEvent.WhisperPromptReady(prompt)).whisper)
        ctx = ctx.copy(liveWhisper = true)
        // No opt-in: nothing.
        assertNull(run(meeting, CompanionEvent.WhisperPromptReady(prompt)).whisper)
        // Sermon: never.
        assertNull(run(sermon, CompanionEvent.SermonQuietChanged(false), CompanionEvent.WhisperOptIn(true), CompanionEvent.WhisperPromptReady(prompt)).whisper)
        val on = run(meeting, CompanionEvent.WhisperOptIn(true), CompanionEvent.WhisperPromptReady(prompt))
        assertEquals(prompt, on.whisper)
        assertNull(run(CompanionEvent.WhisperDismissed, from = on).whisper)
        assertNull("stopping the recording drops the prompt", run(CompanionEvent.RecordingStopped, from = on).whisper)
    }

    @Test fun `the reducer is pure`() {
        val s = run(meeting)
        val c = context()
        assertEquals(CompanionReducer.reduce(s, CompanionEvent.RecordingStopped, c), CompanionReducer.reduce(s, CompanionEvent.RecordingStopped, c))
    }
}
