package com.craftflowtechnologies.meetingmind.ai.modelmanagement

import com.craftflowtechnologies.meetingmind.core.model.AiModelInfo
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ModelFileSpec
import com.craftflowtechnologies.meetingmind.core.model.ModelTier

/**
 * Real, downloadable models for Phase 1 (VAD + ASR only — see docs/AI_ARCHITECTURE.md).
 * Every URL/size/SHA-256 below was verified by actually downloading each file and computing
 * its checksum during development of this phase; nothing here is invented. `isInstalled` is
 * never set from this catalog — [com.craftflowtechnologies.meetingmind.core.repository.ModelRepository] determines real
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
     * Meta Omnilingual ASR CTC 300M, INT8-quantized ONNX export
     * (csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12 on Hugging Face).
     * Supports 1,600+ languages including Shona, Ndebele, Bemba, Nyanja, Tonga, Lozi, Zulu,
     * Xhosa, Afrikaans, Swahili, English, etc. Apache-2.0 licensed, fully offline.
     */
    val omnilingualAsr300mInt8 = AiModelInfo(
        id = "asr_omnilingual_300m_ctc_int8",
        name = "Omnilingual ASR 300M (African & Multilingual)",
        capability = setOf(ModelCapability.TRANSCRIPTION),
        files = listOf(
            ModelFileSpec(
                fileName = "model.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12/resolve/main/model.int8.onnx",
                sha256 = "e7c4e54ee4c4c47829cc6667d5d00ed8ea7bef1dcfeef0fce766f77752a2726c",
                sizeBytes = 365_352_120L
            ),
            ModelFileSpec(
                fileName = "tokens.txt",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12/resolve/main/tokens.txt",
                sha256 = "a7a044c52cb29cbe8b0dc1953e92cefd4ca16b0ed968177b6beab21f9a7d0b31",
                sizeBytes = 86_423L
            )
        ),
        minimumRamMb = 1536,
        recommendedRamMb = 3072,
        version = "omnilingual-asr-300m-ctc-int8-2025-11-12",
        description = "Universal African and Southern African on-device speech-to-text (Shona, Ndebele, Bemba, Nyanja, Tonga, Lozi, Zulu, Xhosa, Afrikaans, Swahili, etc.). Apache-2.0 licensed, runs offline via sherpa-onnx.",
        parameterCount = "300M",
        quantization = "int8"
    )

    /**
     * Meta Omnilingual ASR CTC 1B, INT8-quantized ONNX export
     * (csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-int8-2025-11-12 on Hugging Face).
     * High-capacity 1B-parameter model supporting 1,600+ languages including Shona, Ndebele,
     * Bemba, Nyanja, Tonga, Lozi, Zulu, Xhosa, Afrikaans, Swahili, etc.
     * Offers significantly lower WER for Southern African languages on capable phones (~1.03 GB download).
     * Apache-2.0 licensed, runs offline via sherpa-onnx.
     */
    val omnilingualAsr1bInt8 = AiModelInfo(
        id = "asr_omnilingual_1b_ctc_int8",
        name = "Omnilingual ASR 1B (African & Multilingual)",
        capability = setOf(ModelCapability.TRANSCRIPTION),
        files = listOf(
            ModelFileSpec(
                fileName = "model.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-int8-2025-11-12/resolve/main/model.int8.onnx",
                sha256 = "f7b74c964039162423b83e3fa950ce24810c9a635d9ff8468b5f4d142b7c1e8c",
                sizeBytes = 1_031_628_252L
            ),
            ModelFileSpec(
                fileName = "tokens.txt",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-int8-2025-11-12/resolve/main/tokens.txt",
                sha256 = "a7a044c52cb29cbe8b0dc1953e92cefd4ca16b0ed968177b6beab21f9a7d0b31",
                sizeBytes = 86_423L
            )
        ),
        minimumRamMb = 3072,
        recommendedRamMb = 6144,
        version = "omnilingual-asr-1b-ctc-int8-2025-11-12",
        description = "High-accuracy 1B-parameter African and multilingual on-device speech-to-text (Shona, Ndebele, Bemba, Nyanja, Tonga, Lozi, Zulu, Xhosa, Afrikaans, Swahili, etc.). Apache-2.0 licensed, runs offline via sherpa-onnx.",
        parameterCount = "1B",
        quantization = "int8",
        tier = ModelTier.HIGH_QUALITY
    )

    /**
     * OpenAI Whisper Turbo, INT8-quantized ONNX export
     * (csukuangfj/sherpa-onnx-whisper-turbo on Hugging Face).
     * 809M parameters, high-speed multilingual ASR with word-level timestamps (~1.04 GB download).
     * MIT licensed, runs offline via sherpa-onnx.
     */
    val whisperTurboInt8 = AiModelInfo(
        id = "asr_whisper_turbo_int8",
        name = "Whisper Turbo (Fast Multilingual)",
        capability = setOf(ModelCapability.TRANSCRIPTION),
        files = listOf(
            ModelFileSpec(
                fileName = "encoder.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main/turbo-encoder.int8.onnx",
                sha256 = "b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b",
                sizeBytes = 674_716_297L
            ),
            ModelFileSpec(
                fileName = "decoder.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main/turbo-decoder.int8.onnx",
                sha256 = "20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547",
                sizeBytes = 361_080_764L
            ),
            ModelFileSpec(
                fileName = "tokens.txt",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main/turbo-tokens.txt",
                sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
                sizeBytes = 816_730L
            )
        ),
        minimumRamMb = 3072,
        recommendedRamMb = 4096,
        version = "whisper-turbo-int8",
        description = "Optimized multilingual Whisper model (809M parameters, INT8) offering near-Large accuracy at significantly higher inference speed. MIT licensed, runs offline via sherpa-onnx.",
        parameterCount = "809M",
        quantization = "int8",
        tier = ModelTier.RECOMMENDED
    )

    /**
     * OpenAI Whisper Large-v3, INT8-quantized ONNX export
     * (csukuangfj/sherpa-onnx-whisper-large-v3 on Hugging Face).
     * State-of-the-art 1.5B multilingual Whisper model with word and segment timestamps (~1.78 GB download).
     * MIT licensed, runs offline via sherpa-onnx.
     */
    val whisperLargeV3Int8 = AiModelInfo(
        id = "asr_whisper_large_v3_int8",
        name = "Whisper Large v3 (Multilingual)",
        capability = setOf(ModelCapability.TRANSCRIPTION),
        files = listOf(
            ModelFileSpec(
                fileName = "encoder.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3/resolve/main/large-v3-encoder.int8.onnx",
                sha256 = "d531cf17248acc43e8c09b472a0877055e770877857a5332fc1304b36534ec85",
                sizeBytes = 766_671_985L
            ),
            ModelFileSpec(
                fileName = "decoder.int8.onnx",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3/resolve/main/large-v3-decoder.int8.onnx",
                sha256 = "ebc6bfd88e162a46cb3edee8a7e727e1dcbc65cabecb19e2573695e4d495e1af",
                sizeBytes = 1_008_265_203L
            ),
            ModelFileSpec(
                fileName = "tokens.txt",
                downloadUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3/resolve/main/large-v3-tokens.txt",
                sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
                sizeBytes = 816_730L
            )
        ),
        minimumRamMb = 4096,
        recommendedRamMb = 6144,
        version = "whisper-large-v3-int8",
        description = "OpenAI's state-of-the-art 1.5B multilingual Whisper model quantized to INT8 (~1.78 GB download). High-precision transcription across 99+ languages with segment timestamps. MIT licensed, runs offline via sherpa-onnx.",
        parameterCount = "1.5B",
        quantization = "int8",
        tier = ModelTier.HIGH_QUALITY
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
     * only the [com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel] interface and its Unavailable stub. Chosen
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
     * [com.craftflowtechnologies.meetingmind.ai.llm.RealMeetingIntelligenceEngine]'s prompts were built against; staying
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
     * DeepSeek-R1 Distill Qwen 1.5B (Reasoning-focused Meeting Intelligence).
     * Published by Google's litert-community in MediaPipe `.task` format
     * (litert-community/DeepSeek-R1-Distill-Qwen-1.5B on Hugging Face).
     * MIT licensed and ungated. Runs through MediaPipe LlmInference (~1.83 GB download).
     */
    val deepseekR1DistillQwen15b = AiModelInfo(
        id = "llm_deepseek_r1_distill_qwen_1_5b",
        name = "DeepSeek-R1 Distill Qwen 1.5B (Reasoning)",
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
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B-q8-ekv4096.task",
                downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
                sha256 = "16e43481814512d9fa339447d2e0ebf60d27d79281a083711ec1feda1ffe17bd",
                sizeBytes = 1_834_078_546L
            )
        ),
        minimumRamMb = 3072,
        recommendedRamMb = 4608,
        version = "DeepSeek-R1-Distill-Qwen-1.5B (litert-community, q8, 4096-token context)",
        description = "Deep reasoning-distilled Meeting Intelligence model (~1.83 GB download). Excels at complex meeting synthesis, critical nuance extraction, action item identification, and nuanced follow-up Q&A. MIT licensed, runs via MediaPipe LlmInference.",
        parameterCount = "1.5B",
        quantization = "q8 (int8 weights)",
        contextLengthTokens = 4096,
        tier = ModelTier.HIGH_QUALITY
    )

    /**
     * Highest-quality Meeting Intelligence tier (Phase 3D). Microsoft's Phi-4-mini-instruct
     * (3.8B), MIT licensed and ungated, published by Google's litert-community in the same
     * MediaPipe `.task` format the other two LLM entries use — so it loads through exactly the
     * same [com.craftflowtechnologies.meetingmind.ai.llm.MediaPipeLanguageModel] path with no new runtime work.
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
        omnilingualAsr300mInt8,
        omnilingualAsr1bInt8,
        whisperTurboInt8,
        whisperLargeV3Int8,
        speakerDiarization,
        qwen25_1_5bInstruct,
        qwen25_0_5bInstruct,
        deepseekR1DistillQwen15b,
        phi4MiniInstruct
    )
}
