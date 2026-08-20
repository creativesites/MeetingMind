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

    val entries: List<AiModelInfo> = listOf(sileroVad, parakeetTdtV3Int8)
}
