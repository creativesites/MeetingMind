package com.example.core.model

enum class MeetingSource {
    LOCAL_RECORDING,
    IMPORTED_AUDIO,
    IMPORTED_VIDEO,
    REMOTE_BOT
}

enum class MeetingStatus {
    RECORDING,

    /**
     * Capture finished and the audio is safely on disk, but the user has not chosen to process
     * it yet (Phase 15 §Part 2 / design `#7c`). Only `RECORDING -> SAVED` is reachable from the
     * capture path itself — nothing in capture may set `PROCESSING` directly, since "stop"
     * deliberately does not imply "start analyzing." The audio is never discarded on this
     * transition, or on any transition away from it.
     */
    SAVED,

    /**
     * The user chose "process later" (design `#7c`) rather than discarding or processing now.
     * Audio, markers and notes are all kept; a `WorkManager` `PeriodicWorkRequest` with
     * `requiresCharging` picks these up, alongside an explicit "Process now" from Home or the
     * workspace. Distinct from [SAVED]: this state records an actual decision to defer, not just
     * "hasn't been decided yet."
     */
    QUEUED,

    PROCESSING,
    READY,
    ERROR,

    /**
     * Recording finished and the audio is safely stored, but processing could not continue
     * because a required local AI model (VAD/ASR/diarization/LLM) is not installed on this
     * device. This is distinct from ERROR: nothing failed, a model just needs to be installed.
     * The audio is never discarded when a meeting ends up in this state.
     */
    MODEL_REQUIRED
}

enum class ModelCapability {
    TRANSCRIPTION,
    // Retained for backward compatibility with existing LlmModelResolver call sites and stored
    // preferences — every current LLM catalog entry still carries this alongside the more granular
    // capabilities below, which describe the same underlying "general-purpose instruct LLM"
    // ability at a finer grain rather than claiming something new.
    SUMMARIZATION,
    DIARIZATION,
    EMBEDDINGS,
    /** Reconstructs raw ASR output into a faithful, readable transcript — never summarizes, never
     * infers missing content. See [com.example.ai.pipeline.TranscriptAiCleanupEngine]. */
    TRANSCRIPT_CLEANUP,
    /** Structured decisions/action items/questions/follow-ups extraction. See
     * [com.example.ai.llm.RealMeetingIntelligenceEngine]. */
    EXTRACTION,
    /** Title/summary/key-points synthesis. See [com.example.ai.llm.RealMeetingIntelligenceEngine]. */
    SYNTHESIS,
    /** Grounded question-answering over a transcript. See [com.example.ai.llm.MeetingIntelligenceEngine.askMeeting]. */
    ASK_MEETING,
    /** Reviews ambiguous speaker assignments left after deterministic diarization and proposes
     * evidence-based merges — never invents a speaker, never asked to touch a confident one. See
     * [com.example.ai.diarization.DiarizationReconciliationEngine]. */
    DIARIZATION_RECONCILIATION
}

/**
 * How interventionist [com.example.ai.pipeline.TranscriptAiCleanupEngine] is allowed to be — a
 * real, first-class domain concept (not a raw string spliced into a prompt) because it drives
 * three separate things that must stay consistent with each other: the cleanup prompt's own
 * permissiveness rules, [com.example.ai.pipeline.TranscriptQualityValidator]'s acceptance
 * thresholds, and (see [com.example.core.model.TranscriptCleanupProfile]) model selection. See
 * `docs/AI_ARCHITECTURE.md` §8 for what each mode is allowed and forbidden to do.
 */
enum class TranscriptCleanupMode {
    /** Smallest possible semantic changes: fillers, exact repeats, obvious punctuation. Close to
     * the pre-mode v11 behavior. */
    CONSERVATIVE,
    /** A naturally readable transcript while staying strongly grounded — resolves self-corrections,
     * collapses accidental repetition, restructures awkward spoken grammar into written prose. */
    MODERATE,
    /** A polished, professional transcript — substantial restructuring and likely-ASR-mistake
     * correction using surrounding context allowed, but still never a summary and never inventing
     * information. The most interventionist mode this session ships. */
    AGGRESSIVE
}

/**
 * Which engine decides "who said what." A real, persisted, user-facing choice — never silently
 * hardcoded to one behavior. See `docs/AI_ARCHITECTURE.md` §8 "Diarization strategy scaffolding."
 */
enum class DiarizationStrategy {
    /** sherpa-onnx diarization + its own deterministic fragmentation reconciliation only — never
     * consults a model, however ambiguous the result looks. */
    DETERMINISTIC,
    /** After deterministic diarization runs, also asks a local LLM to review any speaker left
     * below [com.example.ai.diarization.AMBIGUOUS_SHARE_THRESHOLD] of the recording — never a
     * confident one — for textual evidence it's actually the same person as a dominant speaker. A
     * no-op when nothing is ambiguous, when no capable model is installed, or when the model
     * proposes nothing the guardrails accept; see [com.example.ai.diarization.DiarizationReconciliationEngine]. */
    AI_ASSISTED,
    /** MeetingMind decides based on recording context. Today: identical to [AI_ASSISTED] — only
     * attempts reconciliation when something is genuinely ambiguous — kept as a separate case so a
     * future heuristic (e.g. skip on a very long recording to save battery) has a real branch to
     * grow into. */
    AUTO
}

/** Real, typed processing stages — never a fabricated percentage. See [com.example.ai.pipeline.MeetingProcessingPipeline]. */
enum class ProcessingStage {
    IDLE,
    PREPARING_AUDIO,
    DETECTING_SPEECH,
    TRANSCRIBING,
    DIARIZING,
    /** Rule-based (today) transcript cleanup — see [com.example.ai.pipeline.TranscriptCleanupEngine]. */
    CLEANING_TRANSCRIPT,
    ANALYZING,
    SAVING_RESULTS,
    COMPLETED,
    FAILED,
    CANCELLED
}
