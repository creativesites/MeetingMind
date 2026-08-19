# MeetMind — AI Architecture

This documents the **actual** AI runtimes/models present in the codebase today, and the **target** local-first architecture they need to be replaced with. See `docs/AUDIT.md` §D and §G for the full evidence behind every claim here.

## 1. Current State (As of This Audit)

| Interface | Current implementation | What it actually does |
|---|---|---|
| `VoiceActivityDetector` | `EnergyAndSpectralVad` | Computes `sin(time)` — never reads the audio file. Fabricated. |
| `SpeechRecognizer` | `RealSpeechRecognizer` | Cloud call to `gemini-2.5-flash` via raw HTTP (real transcription, but not local); falls back to placeholder strings like `"Voice audio segment (12s)"` if no API key/offline. |
| `SpeakerDiarizer` | `AcousticClusterDiarizer` | Alternates "Speaker N" by turn index / pause length. No acoustic analysis. |
| `MeetingIntelligenceEngine` | `RealMeetingIntelligenceEngine` | Cloud call to `gemini-3.5-flash` for summary/decisions/action items/Q&A; falls back to keyword/regex pattern matching (not ML) if offline. |
| `EmbeddingEngine` | `LocalEmbeddingEngine` | Real, local, deterministic 64-dim hash-based bag-of-words vector. Not a trained/semantic embedding model, but it is genuinely on-device. |

Model catalog (`ModelRepository`) lists 5 fictitious models (Whisper Tiny/Base/Small, "Mobile Intelligence 1B", "Semantic Vector Embedding 64D") with fabricated download URLs pointing at a non-existent domain (`models.meetmind.internal`). None of them are ever downloaded or loaded — "download" is two `delay()` calls and a boolean flip. No `.tflite`/`.onnx`/`.gguf`/`.bin` model file, native library, or inference runtime dependency exists anywhere in the project.

**Network dependency**: whenever `GEMINI_API_KEY` is configured (`BuildConfig.GEMINI_API_KEY`, injected via the `secrets-gradle-plugin` from a local `.env` file — the standard AI Studio flow), the app uploads full audio recordings and full transcript text to `https://generativelanguage.googleapis.com`. **This directly contradicts the product's local-first/privacy requirement** and is the top-priority item in the roadmap.

## 2. Target Architecture

```
                         ┌─────────────────────────┐
                         │   MeetingProcessingPipeline │
                         └─────────────┬────────────┘
                                       │
        ┌──────────────┬──────────────┼──────────────┬───────────────┐
        ▼              ▼              ▼               ▼               ▼
 VoiceActivityDetector SpeechRecognizer SpeakerDiarizer MeetingIntelligenceEngine EmbeddingEngine
        │              │              │               │               │
   (interface)     (interface)    (interface)      (interface)     (interface)
        │              │              │               │               │
   real local VAD  real local ASR  real local      real local LLM   real local
   (e.g. Silero    (e.g. whisper.cpp diarization    (e.g. small       embedding
   VAD via ONNX     or ONNX Runtime  (embeddings +   quantized        model (e.g.
   Runtime Mobile)  Mobile, quantized clustering)     instruction     a small
                     tiny/base)                        model, 1-3B,   sentence-
                                                        GGUF/ONNX)     embedding
                                                                       model)
```

All five interfaces already exist and are already the injection point used by `MeetingProcessingPipeline` — no call-site changes are needed to swap implementations, only new classes.

### Model management target
- `ModelRepository` should read/write through the existing `AiModelEntity`/`AiModelDao` (currently ignored) so installed state survives process death.
- Real download via streaming HTTP (OkHttp is already a dependency) with progress callbacks, writing to app-private storage (e.g., `filesDir/models/{modelId}/`).
- Checksum/integrity verification (SHA-256) against a manifest before marking a model installed.
- Respect the already-existing `wifiOnlyDownload` preference (currently defined in `UserPreferencesManager` but not consumed by anything).
- Deletion should remove the file and flip `isInstalled = false` in Room.

### Cloud Gemini path — decision needed
Three options, in order of alignment with the stated MVP principle:
1. **Remove the cloud path entirely** from the default build for the local-first MVP; keep `GeminiApiClient` code isolated/behind a build flag for a possible future "cloud boost" opt-in feature, not the default.
2. **Gate it behind an explicit, honest opt-in** setting (default OFF), with onboarding/settings copy corrected to describe what actually happens when it's on, and removing the current false "never leaves your device" claims unless the cloud path is fully removed.
3. **Route it through Firebase AI Logic** (the `firebase-ai` dependency is already present and unused) instead of a hand-rolled call with a client-embedded API key, if a cloud bridge is kept intentionally during the transition period before local models are ready — this at least removes the extractable-API-key security risk and allows App Check attestation.

Whichever is chosen, the current default-on, undisclosed behavior is not acceptable to ship.

## 3. Model Selection Guidance (for implementation, not yet decided)

- **ASR**: a quantized Whisper tiny/base (MIT license) via `whisper.cpp` (NDK) or ONNX Runtime Mobile is the most proven path for offline mobile ASR with timestamps.
- **VAD**: Silero VAD (MIT) is small (~1-2MB) and has existing ONNX Runtime Mobile ports — a good fit given ONNX Runtime would likely already be a dependency for ASR.
- **Diarization**: realistic MVP target is a lightweight speaker-embedding model (d-vector/x-vector class) + simple clustering (e.g., agglomerative) over VAD segments — full state-of-the-art diarization is out of scope for a phone.
- **LLM**: a small (1–3B parameter) instruction-tuned, quantized (Q4) model runnable via `llama.cpp` (GGUF) or the MediaPipe LLM Inference API. Expect materially lower summary/extraction quality than Gemini; set product expectations accordingly (see `docs/AUDIT.md` §J).
- **Embeddings**: a small sentence-embedding model (e.g., a distilled/quantized MiniLM-class model) via ONNX Runtime Mobile, replacing the current hash-based placeholder.

**Before integrating any specific model, re-verify its license terms** — `THIRD_PARTY_NOTICES.md` currently pre-declares licenses for models that aren't actually integrated; treat it as a template to complete once real choices are made, not a source of truth today.

## 4. RAM / Performance Tiers

`DeviceCapabilityDetector` already estimates total RAM and recommends a model tier (`recommendedAsrModelId`, `recommendedLlmModelId`) based on it. This is good groundwork that nothing currently consumes for actual model selection at runtime (only for a display recommendation during onboarding). The target: `ModelRepository`/pipeline should actually honor the selected/recommended model, and should refuse to load a model whose `minimumRamMb` exceeds the device's available RAM, degrading to a smaller tier automatically with a visible explanation to the user rather than crashing or silently underperforming.
