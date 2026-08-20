# Third-Party Notices & Model Licenses

MeetMind incorporates the following open-source runtimes, models, and libraries. This list reflects what is actually integrated in the codebase today — not aspirational or planned components.

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

## 3. On-Device Inference Runtime

- **sherpa-onnx** (k2-fsa)
  - Source: `k2-fsa/sherpa-onnx`
  - License: **Apache License 2.0**
  - Usage: provides the `OfflineRecognizer`/`Vad` Android APIs and bundled ONNX Runtime native libraries (`libsherpa-onnx-jni.so`, `libonnxruntime.so`) that run the Parakeet ASR and Silero VAD models on-device. Consumed as a prebuilt AAR from the project's official GitHub Releases — no source modifications.

## 4. Language Intelligence & LLM

Not yet integrated. No LLM, summarization, or action-item-extraction model ships in this build — see `docs/AI_ARCHITECTURE.md` for current phase scope and non-goals.

## 5. Android Libraries

- **AndroidX & Jetpack Compose**: Apache License 2.0
- **Kotlin & Kotlinx Coroutines**: Apache License 2.0
- **Room Persistence**: Apache License 2.0
- **Firebase Android SDK** (Auth, Firestore): Apache License 2.0
- **Coil**: Apache License 2.0
- **OkHttp**: Apache License 2.0
