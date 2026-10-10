package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage

/**
 * Real app events (§3.3). Screens and services send these; nobody sets a companion state directly.
 */
sealed interface CompanionEvent {
    data class ScreenShown(val page: CompanionPage) : CompanionEvent
    data class RecordingStarted(val kind: RecordingKind, val space: NotebookSpace?) : CompanionEvent
    data object RecordingStopped : CompanionEvent
    /** The local "Quiet during sermon · tap to change" chip: this recording only (§3.4). */
    data class SermonQuietChanged(val quiet: Boolean) : CompanionEvent
    data class ProcessingStageChanged(val stage: ProcessingStage, val detail: StageDetail? = null) : CompanionEvent
    data class ProcessingFailed(val reason: FailureReason, val fix: FixAction) : CompanionEvent
    /**
     * A note finished. [sensitive] is true for prayer requests and grief/funeral/memorial notes:
     * nothing celebrates on those (§1.3, §5.10).
     */
    data class NoteReady(val noteId: String, val isFirstEver: Boolean, val sensitive: Boolean = false) : CompanionEvent
    /** The fix on the StatusLine was tapped (Retry, Redo online, Download, Open settings…). */
    data object RetryTapped : CompanionEvent
    data object ModelsMissing : CompanionEvent
    data object ModelsReady : CompanionEvent
    data object ReadAloudStarted : CompanionEvent
    data object ReadAloudStopped : CompanionEvent
    data class Milestone(val kind: MilestoneKind) : CompanionEvent
    data class OnboardingQuestion(val step: Int) : CompanionEvent
    data object QuizQuestion : CompanionEvent
    /** Once per Home entry, for the late-night Sleepy check. [hourOfDay] is local, 0–23. */
    data class ClockTick(val hourOfDay: Int) : CompanionEvent
    data class FormChanged(val form: CompanionForm?) : CompanionEvent
    /** A tap on the companion: a small "hi", at most once per 10 s, and it wakes a sleepy companion. */
    data object Tapped : CompanionEvent
    /** The host finished playing the current one-shot. */
    data object OneShotEnded : CompanionEvent
    /** Live whisper (Z-25 experiment): opt-in for this recording. */
    data class WhisperOptIn(val enabled: Boolean) : CompanionEvent
    data class WhisperPromptReady(val prompt: WhisperPrompt) : CompanionEvent
    data object WhisperDismissed : CompanionEvent
}
