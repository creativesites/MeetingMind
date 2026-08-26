package com.example.core.model

enum class MeetingSource {
    LOCAL_RECORDING,
    IMPORTED_AUDIO,
    IMPORTED_VIDEO,
    REMOTE_BOT
}

enum class MeetingStatus {
    RECORDING,
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
    ASK_MEETING
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
