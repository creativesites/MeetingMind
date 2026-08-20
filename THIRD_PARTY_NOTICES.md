# Third-Party Notices & Model Licenses

MeetingMind incorporates the following open-source runtimes, models, and libraries. This list reflects what is actually integrated in the codebase today — not aspirational or planned components.

## 1. On-Device Speech Recognition (ASR)

- **NVIDIA Parakeet TDT 0.6B v3 (INT8)**
  - Source: NVIDIA / Hugging Face (`nvidia/parakeet-tdt-0.6b-v3`), converted to sherpa-onnx ONNX format
  - License: **CC-BY-4.0**
  - Usage: downloaded on demand at runtime (not bundled in the APK) and run entirely on-device via sherpa-onnx's `OfflineRecognizer` (`nemo_transducer` model type). Attribution required per CC-BY-4.0; no modification of the model weights is performed by this app.

## 2. On-Device Voice Activity Detection (VAD)

- **Silero VAD**
  - Source: Silero Team (`snakers4/silero-vad`)
  - License: **MIT License**
  - Attribution: Copyright (c) 2020-present Silero Team.
  - Usage: downloaded on demand at runtime and run entirely on-device via sherpa-onnx's `Vad` API (`SileroVadModelConfig`).

## 3. On-Device Speaker Diarization

- **pyannote/segmentation-3.0** (sherpa-onnx int8 export)
  - Source: `pyannote/segmentation-3.0` on Hugging Face, converted to ONNX by the sherpa-onnx project
  - License: **MIT License** (Copyright (c) 2022 CNRS)
  - Usage: downloaded on demand (extracted from the project's official `.tar.bz2` release asset) and run on-device via sherpa-onnx's `OfflineSpeakerDiarization` to find speaker-change boundaries.

- **3D-Speaker CAM++ (English, VoxCeleb-trained)**
  - Source: Alibaba DAMO Academy (`alibaba-damo-academy/3D-Speaker`), re-exported to ONNX by the sherpa-onnx project
  - License: **Apache License 2.0**
  - Usage: downloaded on demand and run on-device via sherpa-onnx's `OfflineSpeakerDiarization` to turn each diarization segment into a speaker embedding for clustering.

## 4. Language Intelligence & LLM

- **Qwen2.5-1.5B-Instruct** (litert-community `.task` build, q8 quantized)
  - Source: Qwen Team / Alibaba Cloud (`Qwen/Qwen2.5-1.5B-Instruct`), converted to the MediaPipe LiteRT `.task` format by the `litert-community` Hugging Face organization
  - License: **Apache License 2.0**
  - Usage: downloaded on demand (not bundled in the APK) and run entirely on-device via MediaPipe's `LlmInference` API to generate meeting summaries, decisions, action items, questions, and follow-ups from the real transcript only.

- **MediaPipe Tasks GenAI** (`com.google.mediapipe:tasks-genai`)
  - Source: Google (`google/mediapipe`)
  - License: **Apache License 2.0**
  - Usage: provides the `LlmInference` Android API and bundled native inference engine that runs the Qwen2.5 model on-device.

## 5. On-Device Inference Runtime

- **sherpa-onnx** (k2-fsa)
  - Source: `k2-fsa/sherpa-onnx`
  - License: **Apache License 2.0**
  - Usage: provides the `OfflineRecognizer`/`Vad`/`OfflineSpeakerDiarization` Android APIs and bundled ONNX Runtime native libraries (`libsherpa-onnx-jni.so`, `libonnxruntime.so`) that run the Parakeet ASR, Silero VAD, and speaker diarization models on-device. Consumed as a prebuilt AAR from the project's official GitHub Releases — no source modifications.

- **Apache Commons Compress**
  - Source: Apache Software Foundation
  - License: **Apache License 2.0**
  - Usage: extracts the one `.onnx` file needed from the official pyannote segmentation model's `.tar.bz2` release archive. Not used for any other purpose.

## 6. Android Libraries

- **AndroidX & Jetpack Compose**: Apache License 2.0
- **Kotlin & Kotlinx Coroutines**: Apache License 2.0
- **Room Persistence**: Apache License 2.0
- **Firebase Android SDK** (Auth, Firestore): Apache License 2.0
- **Coil**: Apache License 2.0
- **OkHttp**: Apache License 2.0
