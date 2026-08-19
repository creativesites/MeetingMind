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
    ERROR
}

enum class ModelCapability {
    TRANSCRIPTION,
    SUMMARIZATION,
    DIARIZATION,
    EMBEDDINGS
}
