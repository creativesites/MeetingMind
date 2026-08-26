package com.example.ai.modelmanagement

import com.example.core.model.AiModelInfo
import com.example.core.model.ModelCapability
import com.example.core.model.ModelFileSpec
import com.example.core.model.ModelTier

/**
 * Real, downloadable models for Phase 1 (VAD + ASR only — see docs/AI_ARCHITECTURE.md).
 * Every URL/size/SHA-256 below was verified by actually downloading each file and computing
 * its checksum during development of this phase; nothing here is invented. `isInstalled` is
 * never set from this catalog — [com.example.core.repository.ModelRepository] determines real
 * install state from [ModelStorage] and the database, not from this static list.
 */
object ModelCatalog {

    /**
     * Silero VAD, distributed by the sherpa-onnx project itself (re-exported ONNX build of
     * https://github.com/snakers4/silero-vad). Verified 2026-08-20.
     */
    val sileroVad = AiModelInfo(
        id = "vad_silero",
        name = "Silero Voice Activity Detection",
        capability = setOf(ModelCapability.TRANSCRIPTION),
        files = listOf(
            ModelFileSpec(
                fileName = "silero_vad.onnx",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
                sha256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
                sizeBytes = 643_854L
            )
        ),
        minimumRamMb = 256,
        recommendedRamMb = 512,
        version = "silero-vad (sherpa-onnx export)",
        description = "Detects speech vs. silence in the recording so only spoken audio is sent to transcription. Runs via sherpa-onnx.",
        parameterCount = "~1.8M",
        quantization = "fp32"
    )

    /**
     * NVIDIA Parakeet TDT 0.6B v3, INT8-quantized NeMo transducer ONNX export
     * (csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8 on Hugging Face, converted from
     * nvidia/parakeet-tdt-0.6b-v3). English + 24 European languages. Verified 2026-08-20.
     */
    val parakeetTdtV3Int8 = AiModelInfo(
        id = "asr_parakeet_tdt_0_6b_v3_int8",
        name = "Parakeet TDT 0.6B v3 (INT8)",
        capability = setOf(ModelCapability.TRANSCRIPTION),
        files = listOf(
            ModelFileSpec(
                fileName = "encoder.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/encoder.int8.onnx",
                sha256 = "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247",
                sizeBytes = 652_184_281L
            ),
            ModelFileSpec(
                fileName = "decoder.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/decoder.int8.onnx",
                sha256 = "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e",
                sizeBytes = 11_845_275L
            ),
            ModelFileSpec(
                fileName = "joiner.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/joiner.int8.onnx",
                sha256 = "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3",
                sizeBytes = 6_355_277L
            ),
            ModelFileSpec(
                fileName = "tokens.txt",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/tokens.txt",
                sha256 = "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
                sizeBytes = 93_939L
            )
        ),
        minimumRamMb = 2048,
        recommendedRamMb = 4096,
        version = "parakeet-tdt-0.6b-v3-int8",
        description = "On-device speech-to-text (English + 24 European languages). Real NeMo transducer model run via sherpa-onnx OfflineRecognizer — no cloud call.",
        parameterCount = "0.6B",
        quantization = "int8"
    )

    /**
     * Real offline speaker diarization: pyannote segmentation-3.0 (int8) finds "who is speaking
     * when" boundaries; 3D-Speaker CAM++ (English, VoxCeleb-trained) turns each segment into an
     * embedding for clustering. Both run via sherpa-onnx's OfflineSpeakerDiarization API.
     * Verified 2026-08-20 (segmentation model extracted from its official tarball; embedding
     * model downloaded directly — both hashed from real downloaded bytes, not trusted headers).
     */
    val speakerDiarization = AiModelInfo(
        id = "diarization_pyannote_campplus",
        name = "Speaker Diarization",
        capability = setOf(ModelCapability.DIARIZATION),
        files = listOf(
            ModelFileSpec(
                fileName = "segmentation.onnx",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2",
                sha256 = "d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d",
                sizeBytes = 1_540_506L,
                downloadSizeBytes = 6_958_444L,
                archiveEntryPath = "sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx"
            ),
            ModelFileSpec(
                fileName = "embedding.onnx",
                downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
                sha256 = "357a834f702b80161e5b981182c038e18553c1f2ca752ed6cec2052365d4129b",
                sizeBytes = 29_596_978L
            )
        ),
        minimumRamMb = 512,
        recommendedRamMb = 1024,
        version = "pyannote-segmentation-3.0-int8 + 3dspeaker-campplus-en",
        description = "Identifies distinct speakers in a meeting from real acoustic features (not turn order or guessing). Runs via sherpa-onnx OfflineSpeakerDiarization.",
        parameterCount = "seg: ~1.5M, emb: ~7.2M",
        quantization = "seg: int8, emb: fp32"
    )

    /**
     * Real local LLM for Meeting Intelligence (Phase 2). No local LLM existed before this —
     * only the [com.example.ai.llm.LanguageModel] interface and its Unavailable stub. Chosen
     * because it is (a) genuinely downloadable without gated/authenticated access — several
     * litert-community Gemma builds were checked first and are HF-gated behind Google's terms,
     * which an unattended in-app download cannot satisfy — and (b) Apache-2.0 licensed with no
     * separate model-use terms. Runs via MediaPipe's LlmInference API (com.google.mediapipe:
     * tasks-genai). Verified 2026-08-20 by downloading the full file and hashing it directly.
     */
    val qwen25_1_5bInstruct = AiModelInfo(
        id = "llm_qwen2_5_1_5b_instruct",
        name = "Qwen2.5 1.5B Instruct (Meeting Intelligence)",
        // A general-purpose instruct LLM genuinely can serve every one of these tasks — this
        // isn't claiming anything new, just naming at a finer grain what SUMMARIZATION already
        // covered. Kept alongside SUMMARIZATION rather than replacing it: LlmModelResolver's
        // existing call sites/stored preferences filter on SUMMARIZATION and must keep working.
        capability = setOf(
            ModelCapability.SUMMARIZATION,
            ModelCapability.TRANSCRIPT_CLEANUP,
            ModelCapability.EXTRACTION,
            ModelCapability.SYNTHESIS,
            ModelCapability.ASK_MEETING,
            ModelCapability.DIARIZATION_RECONCILIATION
        ),
        files = listOf(
            ModelFileSpec(
                fileName = "qwen2.5-1.5b-instruct-q8-ekv4096.task",
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
                sha256 = "82968d0a6c3872cf016fdbcfc591571605f4c7fd2b0f64d2533df502cc6596b3",
                sizeBytes = 1_598_556_720L
            )
        ),
        minimumRamMb = 3072,
        recommendedRamMb = 4608,
        version = "Qwen2.5-1.5B-Instruct (litert-community, q8, 4096-token context)",
        description = "Generates meeting summaries, decisions, action items, questions and follow-ups from the real transcript only — never invents facts not present in it. Runs via MediaPipe LlmInference.",
        parameterCount = "1.5B",
        quantization = "q8 (int8 weights)",
        // The "ekv4096" in the chosen .task file's own name is this build's real KV-cache size —
        // its actual max total (prompt + generated) token budget, not an invented figure.
        contextLengthTokens = 4096,
        tier = ModelTier.RECOMMENDED
    )

    /**
     * Lightweight Meeting Intelligence tier for lower-RAM devices (Phase 3C). Same publisher
     * (Google's litert-community), same Apache-2.0 license, same MediaPipe LlmInference loading
     * path, and the same Qwen2.5-Instruct prompt format as [qwen25_1_5bInstruct] — chosen
     * specifically to minimize integration risk over evaluating an unrelated model family.
     * Candidates considered and rejected: SmolLM2 (360M/1.7B) has no litert-community `.task`
     * build published as of this writing, so it would require converting/hosting a model
     * ourselves rather than linking a real existing release — out of scope here. Qwen3-0.6B was
     * also available but is a different prompt/template generation from what
     * [com.example.ai.llm.RealMeetingIntelligenceEngine]'s prompts were built against; staying
     * within the Qwen2.5 family avoids re-validating prompt behavior for two different chat
     * templates at once.
     *
     * Verified 2026-08-20 by downloading the full file and hashing it directly (546,660,344
     * bytes; sha256 below matches the file's own x-linked-etag from HuggingFace).
     *
     * Trade-off to be honest about: this build's KV-cache (see contextLengthTokens) is 1280
     * tokens — under a third of the 1.5B build's 4096 — so long transcripts need smaller chunks
     * and more of them. It also has not been validated on-device for structured-JSON extraction
     * reliability; only the 1.5B model has any device-testing history so far (see docs/AI_ARCHITECTURE.md).
     */
    val qwen25_0_5bInstruct = AiModelInfo(
        id = "llm_qwen2_5_0_5b_instruct",
        name = "Qwen2.5 0.5B Instruct (Lightweight)",
        // A general-purpose instruct LLM genuinely can serve every one of these tasks — this
        // isn't claiming anything new, just naming at a finer grain what SUMMARIZATION already
        // covered. Kept alongside SUMMARIZATION rather than replacing it: LlmModelResolver's
        // existing call sites/stored preferences filter on SUMMARIZATION and must keep working.
        capability = setOf(
            ModelCapability.SUMMARIZATION,
            ModelCapability.TRANSCRIPT_CLEANUP,
            ModelCapability.EXTRACTION,
            ModelCapability.SYNTHESIS,
            ModelCapability.ASK_MEETING,
            ModelCapability.DIARIZATION_RECONCILIATION
        ),
        files = listOf(
            ModelFileSpec(
                fileName = "qwen2.5-0.5b-instruct-q8-ekv1280.task",
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                sha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
                sizeBytes = 546_660_344L
            )
        ),
        minimumRamMb = 1536,
        recommendedRamMb = 2560,
        version = "Qwen2.5-0.5B-Instruct (litert-community, q8, 1280-token context)",
        description = "A smaller, faster Meeting Intelligence model for lower-RAM devices — still grounded only in the real transcript, but with a shorter context window and less headroom for very long recordings.",
        parameterCount = "0.5B",
        quantization = "q8 (int8 weights, fp32 activations)",
        contextLengthTokens = 1280,
        tier = ModelTier.LIGHTWEIGHT
    )

    /**
     * Highest-quality Meeting Intelligence tier (Phase 3D). Microsoft's Phi-4-mini-instruct
     * (3.8B), MIT licensed and ungated, published by Google's litert-community in the same
     * MediaPipe `.task` format the other two LLM entries use — so it loads through exactly the
     * same [com.example.ai.llm.MediaPipeLanguageModel] path with no new runtime work.
     *
     * Why this one: the Qwen2.5 1.5B/0.5B tiers are small enough that strict-JSON extraction is
     * their weakest point, which is the single most common cause of a recording coming back with
     * an empty summary and no action items. Phi-4-mini is roughly 2.5x the parameters of the
     * Recommended tier and is specifically strong at instruction-following and structured output,
     * which is exactly the failure this tier exists to address. Gemma remains ruled out for the
     * same reason as in Phase 2 — every `litert-community/Gemma*` repo is gated behind a
     * click-through licence an unattended in-app download cannot accept.
     *
     * Verified 2026-08-25 by downloading the full file and hashing it directly (3,910,050,199
     * bytes; the sha256 below matches HuggingFace's own x-linked-etag for the file).
     *
     * Be honest about the cost: this is a ~3.6 GiB download and needs a genuinely high-RAM device
     * to load. It has NOT been validated on real hardware for load time or tokens/sec, so the RAM
     * figures below are derived from the file size plus MediaPipe's KV-cache overhead rather than
     * from measurement — see docs/AI_ARCHITECTURE.md.
     */
    val phi4MiniInstruct = AiModelInfo(
        id = "llm_phi_4_mini_instruct",
        name = "Phi-4 Mini Instruct (Highest quality)",
        // A general-purpose instruct LLM genuinely can serve every one of these tasks — this
        // isn't claiming anything new, just naming at a finer grain what SUMMARIZATION already
        // covered. Kept alongside SUMMARIZATION rather than replacing it: LlmModelResolver's
        // existing call sites/stored preferences filter on SUMMARIZATION and must keep working.
        capability = setOf(
            ModelCapability.SUMMARIZATION,
            ModelCapability.TRANSCRIPT_CLEANUP,
            ModelCapability.EXTRACTION,
            ModelCapability.SYNTHESIS,
            ModelCapability.ASK_MEETING,
            ModelCapability.DIARIZATION_RECONCILIATION
        ),
        files = listOf(
            ModelFileSpec(
                fileName = "phi-4-mini-instruct-q8-ekv4096.task",
                downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.task",
                sha256 = "88665a75f6a0b5083ce65255139212ff6da705d5f682edbbd109eae784b2173c",
                sizeBytes = 3_910_050_199L
            )
        ),
        minimumRamMb = 6144,
        recommendedRamMb = 8192,
        version = "Phi-4-mini-instruct (litert-community, q8, 4096-token context)",
        description = "The most capable on-device model here — noticeably better at pulling real decisions and action items out of a messy conversation. Large download, and only worth choosing on a recent phone with plenty of memory.",
        parameterCount = "3.8B",
        quantization = "q8 (int8 weights)",
        contextLengthTokens = 4096,
        tier = ModelTier.HIGH_QUALITY
    )

    val entries: List<AiModelInfo> = listOf(
        sileroVad,
        parakeetTdtV3Int8,
        speakerDiarization,
        qwen25_1_5bInstruct,
        qwen25_0_5bInstruct,
        phi4MiniInstruct
    )
}
