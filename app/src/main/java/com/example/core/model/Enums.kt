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
    SUMMARIZATION,
    DIARIZATION,
    EMBEDDINGS
}
