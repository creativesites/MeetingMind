package com.example.ai.modelmanagement

import com.example.core.model.AiModelInfo
import com.example.core.model.ModelCapability

/**
 * The set of AI capabilities MeetMind's model manager knows about. These are **candidates**
 * under consideration for future local AI integration (see docs/AI_ARCHITECTURE.md), not models
 * that are actually installed or downloadable today: every entry has `downloadUrl = null` and
 * `sha256 = null` until a specific production model is selected, hosted, and its checksum
 * pinned here. `isInstalled` is never set from this catalog — [ModelRepository] determines real
 * install state from [ModelStorage] and the database, not from this static list.
 */
object ModelCatalog {
    val entries: List<AiModelInfo> = listOf(
        AiModelInfo(
            id = "asr_whisper_tiny",
            name = "Speech Recognition (Whisper Tiny class)",
            capability = setOf(ModelCapability.TRANSCRIPTION),
            sizeBytes = 0L,
            minimumRamMb = 2048,
            recommendedRamMb = 4096,
            downloadUrl = null,
            sha256 = null,
            version = "not-yet-selected",
            isInstalled = false,
            description = "Candidate lightweight on-device transcription model. Not yet integrated — see docs/AI_ARCHITECTURE.md.",
            parameterCount = "unselected",
            quantization = "unselected"
        ),
        AiModelInfo(
            id = "vad_default",
            name = "Voice Activity Detection",
            capability = setOf(ModelCapability.TRANSCRIPTION),
            sizeBytes = 0L,
            minimumRamMb = 512,
            recommendedRamMb = 1024,
            downloadUrl = null,
            sha256 = null,
            version = "not-yet-selected",
            isInstalled = false,
            description = "Candidate on-device voice activity detector (e.g. Silero VAD). Not yet integrated.",
            parameterCount = "unselected",
            quantization = "unselected"
        ),
        AiModelInfo(
            id = "diarization_default",
            name = "Speaker Diarization",
            capability = setOf(ModelCapability.DIARIZATION),
            sizeBytes = 0L,
            minimumRamMb = 1024,
            recommendedRamMb = 2048,
            downloadUrl = null,
            sha256 = null,
            version = "not-yet-selected",
            isInstalled = false,
            description = "Candidate speaker-embedding + clustering model. Not yet integrated.",
            parameterCount = "unselected",
            quantization = "unselected"
        ),
        AiModelInfo(
            id = "llm_local_intelligence",
            name = "Meeting Intelligence (Local LLM)",
            capability = setOf(ModelCapability.SUMMARIZATION),
            sizeBytes = 0L,
            minimumRamMb = 3072,
            recommendedRamMb = 6144,
            downloadUrl = null,
            sha256 = null,
            version = "not-yet-selected",
            isInstalled = false,
            description = "Candidate small quantized instruction model for summaries, decisions, and action items. Not yet integrated.",
            parameterCount = "unselected",
            quantization = "unselected"
        ),
        AiModelInfo(
            id = "embeddings_semantic",
            name = "Semantic Embeddings",
            capability = setOf(ModelCapability.EMBEDDINGS),
            sizeBytes = 0L,
            minimumRamMb = 1024,
            recommendedRamMb = 2048,
            downloadUrl = null,
            sha256 = null,
            version = "not-yet-selected",
            isInstalled = false,
            description = "Candidate small sentence-embedding model to eventually replace the current hash-based placeholder embeddings. Not yet integrated.",
            parameterCount = "unselected",
            quantization = "unselected"
        )
    )
}
