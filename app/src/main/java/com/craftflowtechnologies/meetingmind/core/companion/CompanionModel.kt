package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.ProcessingStage

/*
 * The companion's vocabulary (docs/mvp/ZURI_EXPERIENCE.md §1–§3, §10.2–§10.3).
 * Pure Kotlin: no Android or Compose types, so the reducer and its tests run on the plain JVM.
 */

/** One drawing of the companion. "No companion" is a setting ([CompanionSettings.form] == null), not a form. */
enum class CompanionForm { ZURI, NAS, WREN, PAGE }

/** Anything the companion can be drawn as: an app state, or a pose that exists only on Create cards. */
sealed interface CompanionVisual {
    val name: String
    val ordinal: Int
}

/**
 * App states (§3.1). The ordinal is part of the Rive contract (input `state`, see
 * [CompanionRiveContract]): append new values at the end, never reorder.
 */
enum class CompanionState : CompanionVisual {
    IDLE, LISTENING, THINKING, CELEBRATING, WORRIED, SLEEPY,
    /** Phase B (P-3). */
    CURIOUS,
    /** Phase B (P-5). */
    PROUD,
    /** Phase B (F-7 read-aloud). */
    READING;

    companion object {
        /** The six states the MVP draws and tests. */
        val Mvp: List<CompanionState> = listOf(IDLE, LISTENING, THINKING, CELEBRATING, WORRIED, SLEEPY)
    }
}

/**
 * Create-card poses (§3.2). Never app states. The ordinal is the Rive `mode` input minus one
 * (0 there means "no mode"); append only.
 */
enum class CreateMode : CompanionVisual { PRAYERFUL, PEACEFUL, GRATEFUL, JOYFUL, REFLECTIVE, CELEBRATORY }

/** How much the companion shows up (§7.1). */
enum class Presence { AROUND, MOMENTS, OFF }

/** Where a companion slot sits, for the presence filter (§3.3). */
enum class CompanionPage {
    HOME, RECORDING, PROCESSING, NOTE_READY, FIRST_RECORDING, WORRIED, EMPTY, MILESTONE, READ_ALOUD,
    /** The user is choosing a companion here, so it always shows. */
    ONBOARDING, QUICK_SHEET, COMPANION_SETTINGS;

    val isChoosing: Boolean get() = this == ONBOARDING || this == QUICK_SHEET || this == COMPANION_SETTINGS
}

/** Normal, Quiet (sermons, §3.4) or the short Nod one-shot. */
enum class CompanionVariant { Normal, Quiet, Nod }

/** The 3D tiers (§4.2), chosen by size. T0 never runs an idle clock. */
enum class CompanionTier {
    T0, T1, T2;

    companion object {
        fun forSizeDp(dp: Float): CompanionTier = when {
            dp < 48f -> T0
            dp < 144f -> T1
            else -> T2
        }
    }
}

/** What kind of recording started; only sermons start quiet. */
enum class RecordingKind { GENERAL, SERMON }

/** The fix a Worried companion always comes with (§5.6). */
enum class FixAction { RETRY, REDO_ONLINE, DOWNLOAD_MODELS, OPEN_SETTINGS, MANAGE_STORAGE }

/** Why the companion is worried. */
enum class FailureReason { PROCESSING_FAILED, AI_FALLBACK, MODEL_DOWNLOAD_FAILED, PERMISSION_DENIED, NO_STORAGE }

data class Failure(val reason: FailureReason, val fix: FixAction)

/** Real numbers from the pipeline. Null means unknown, and the caption drops the clause (§5.4). */
data class StageDetail(val doneMinutes: Int? = null, val totalMinutes: Int? = null, val voices: Int? = null)

/** What the host renders as text under a thinking companion. The companion never carries it alone. */
data class CaptionKey(val stage: ProcessingStage, val detail: StageDetail? = null)

/** Milestones that may earn a Proud moment, once each, ever (§3.1 Proud). Never loss-based. */
enum class MilestoneKind { FIRST_NOTE, TENTH_NOTE, READING_PLAN_DONE, SEVEN_DEVOTIONALS_IN_MONTH, ALL_CARDS_CLEARED }

enum class OneShotKind(val visual: CompanionVisual, val durationMs: Long) {
    CELEBRATING(CompanionState.CELEBRATING, 1_600),
    NOD(CompanionState.CELEBRATING, 600),
    PROUD(CompanionState.PROUD, 1_200),
    CURIOUS(CompanionState.CURIOUS, 600)
}

data class OneShot(val kind: OneShotKind, val startedAtMs: Long) {
    fun isOver(nowMs: Long): Boolean = nowMs - startedAtMs >= kind.durationMs
}

/** A live-whisper prompt (Z-25 experiment). Always cites the note it came from. */
data class WhisperPrompt(val id: String, val sourceNoteId: String)

/** What is happening during a recording. */
data class RecordingFacts(
    val kind: RecordingKind,
    val space: NotebookSpace?,
    /** Quiet during sermons; the local chip can switch this one recording to Listening. */
    val quiet: Boolean,
    /** Live whisper is opt-in per recording (Z-25). */
    val whisperOptIn: Boolean = false
)

/** The companion settings (§7.1, part of the F-6 personalization model). */
data class CompanionSettings(
    /** Null is "No companion". */
    val form: CompanionForm? = CompanionForm.ZURI,
    /** Null uses the form's display name. */
    val name: String? = null,
    val presence: Presence = Presence.AROUND,
    val hiddenUntilMs: Long? = null,
    val onRecordButton: Boolean = false,
    val quietSermons: Boolean = true,
    val createSuggest: Boolean = false,
    val seasonal: Boolean = true
)
