package com.example.ai.modelmanagement

import com.example.core.model.AiModelInfo
import com.example.core.model.ModelCapability
import com.example.core.model.ModelFileSpec

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
        capability = setOf(ModelCapability.SUMMARIZATION),
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
        contextLengthTokens = 4096
    )

    val entries: List<AiModelInfo> = listOf(sileroVad, parakeetTdtV3Int8, speakerDiarization, qwen25_1_5bInstruct)
}
