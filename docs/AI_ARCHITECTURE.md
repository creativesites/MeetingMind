# MeetMind — AI Architecture

This documents the **actual** AI runtimes/models present in the codebase today, and the **target** local-first architecture they need to be replaced with.

## 0. Status Update — P0 Privacy Reconciliation

The state described in §D/§G of `docs/AUDIT.md` (cloud Gemini calls, `sin(time)` fake VAD, regex "intelligence", `delay()`-based fake model downloads, hardcoded mock sign-in) has been **removed**. This section reflects the architecture as it stands now; the original audit findings below (§1 historical table) are kept as a record of what was found, not what ships today.

**What changed:**
- `ai/gemini/GeminiApiClient.kt` was deleted entirely — no class in the app calls any cloud AI endpoint. `PrivacyNoCloudPathTest` asserts this by reflection (the class must not exist) and by checking `MeetingProcessingPipeline`'s default constructor parameters.
- Every AI interface (`VoiceActivityDetector`, `SpeechRecognizer`, `SpeakerDiarizer`, `MeetingIntelligenceEngine`, and the new lower-level `LanguageModel`) now returns `AiResult<T>` — `Success`, `ModelUnavailable`, `DeviceUnsupported`, `InsufficientMemory`, or `Failed` — instead of a plain value. No implementation may return `Success` with fabricated content.
- The default implementation behind every one of those interfaces is an `Unavailable*` class (`UnavailableVoiceActivityDetector`, `UnavailableSpeechRecognizer`, `UnavailableSpeakerDiarizer`, `UnavailableMeetingIntelligenceEngine`, `UnavailableLanguageModel`) that honestly returns `AiResult.ModelUnavailable` — no sine waves, no placeholder transcript strings, no keyword-rule "intelligence."
- `MeetingProcessingPipeline` now branches on `AiResult`: if ASR is unavailable, processing stops, the recording is left exactly as it was, and the meeting is marked `MeetingStatus.MODEL_REQUIRED` (a new, distinct status — not `ERROR`, since nothing failed). If diarization or meeting intelligence are unavailable but ASR succeeded, the pipeline degrades gracefully (keeps ASR's own segments, leaves summary/decisions/action items/questions empty) rather than blocking the whole meeting or fabricating those fields.
- Model management (`ModelRepository`, package `ai/modelmanagement/`) is now a real architecture: `ModelStorage` (real app-private file storage + install marker), `ModelVerifier` (real SHA-256 checksum), `ModelDownloader` (interface; default `UnconfiguredModelDownloader` honestly fails since no production model source is hosted yet), and a static `ModelCatalog` of candidate models with `downloadUrl = null` / `sha256 = null` — **no invented URLs**. Installed state is read from real on-disk state + Room (`AiModelEntity`/`AiModelDao`), not an in-memory `delay()`-simulated flag.
- Firebase Authentication now uses a real Credential Manager → Google ID token → `FirebaseAuth.signInWithCredential` flow (`FirebaseAuthManager.signInWithGoogle`). The hardcoded "Winston Chen" mock profile is gone. If Firebase isn't configured (no `google-services.json`) or no web client ID is set, sign-in honestly reports `AuthAvailability.NotConfigured` instead of fabricating a signed-in user — recording and local processing are never gated on this.
- `GEMINI_API_KEY` / the secrets-gradle-plugin / `.env` machinery were removed entirely — the app requires no API key and no cloud configuration to build or run.

**What has NOT changed:** there is still no real local ASR/VAD/diarization/LLM model integrated. That is intentionally deferred to the next phase (see §3 below) — this pass only established an honest, privacy-safe, model-ready architecture, per its own scope.

## 1. Historical State (Before This Pass — see `docs/AUDIT.md` for full findings)

| Interface | Then-current implementation | What it did |
|---|---|---|
| `VoiceActivityDetector` | `EnergyAndSpectralVad` | Computes `sin(time)` — never reads the audio file. Fabricated. |
| `SpeechRecognizer` | `RealSpeechRecognizer` | Cloud call to `gemini-2.5-flash` via raw HTTP (real transcription, but not local); falls back to placeholder strings like `"Voice audio segment (12s)"` if no API key/offline. |
| `SpeakerDiarizer` | `AcousticClusterDiarizer` | Alternates "Speaker N" by turn index / pause length. No acoustic analysis. |
| `MeetingIntelligenceEngine` | `RealMeetingIntelligenceEngine` | Cloud call to `gemini-3.5-flash` for summary/decisions/action items/Q&A; falls back to keyword/regex pattern matching (not ML) if offline. |
| `EmbeddingEngine` | `LocalEmbeddingEngine` | Real, local, deterministic 64-dim hash-based bag-of-words vector. Not a trained/semantic embedding model, but it is genuinely on-device. Unchanged by this pass — see §4. |

Model catalog (`ModelRepository`) listed 5 fictitious models with fabricated download URLs pointing at a non-existent domain (`models.meetmind.internal`); "download" was two `delay()` calls and a boolean flip.

**Network dependency (now removed)**: whenever `GEMINI_API_KEY` was configured, the app uploaded full audio recordings and full transcript text to `https://generativelanguage.googleapis.com`. This directly contradicted the product's local-first/privacy requirement and was the top-priority item resolved in this pass.

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

### Model management — architecture done, downloader still unimplemented
- `ModelRepository` (`core/repository/Repositories.kt`) now reads/writes through `AiModelEntity`/`AiModelDao`, merged with real on-disk state from `ModelStorage`, so installed state survives process death. **Done.**
- `ModelStorage` → `LocalModelStorage` (`ai/modelmanagement/ModelStorage.kt`) is real: app-private storage at `filesDir/models/{modelId}/`, with an `.installed` marker file only written after verification. **Done.**
- `ModelVerifier` → `Sha256ModelVerifier` (`ai/modelmanagement/ModelVerifier.kt`) is a real, working SHA-256 implementation, ready to verify a real download the moment one exists. **Done.**
- `ModelDownloader` (`ai/modelmanagement/ModelDownloader.kt`) is an interface with only `UnconfiguredModelDownloader` implemented, which honestly returns `AiResult.ModelUnavailable` — **intentionally not implemented yet**, since no production model source/URL has been selected or hosted (see §3). This is the one piece of the model-management architecture still to be built once a real model is chosen.
- The `wifiOnlyDownload` preference (`UserPreferencesManager`) still isn't consumed by anything — wire it into the real `ModelDownloader` implementation when it's built.
- Deletion (`ModelRepository.deleteModel`) removes the on-disk file and flips `isInstalled = false` in Room. **Done.**

### Cloud Gemini path — resolved: removed entirely
Of the three options previously weighed here, **option 1 was chosen**: `GeminiApiClient` and everything that called it were deleted outright, along with the `GEMINI_API_KEY`/secrets-gradle-plugin/`.env` plumbing and the now-unused `retrofit`/`moshi`/`okhttp`/`firebase-ai` dependencies. There is no cloud AI "boost" mode, flag, or dormant code path left in the app — the class does not exist (`PrivacyNoCloudPathTest` asserts this). If a cloud bridge is ever wanted again as an explicit, disclosed, opt-in feature during the transition to real local models, it should be re-added through Firebase AI Logic (server-side attestation via App Check, no client-embedded API key) rather than reintroducing a hand-rolled REST client — but that is a future product decision, not something this pass left half-done.

## 3. Model Selection Guidance (for implementation, not yet decided)

- **ASR**: a quantized Whisper tiny/base (MIT license) via `whisper.cpp` (NDK) or ONNX Runtime Mobile is the most proven path for offline mobile ASR with timestamps.
- **VAD**: Silero VAD (MIT) is small (~1-2MB) and has existing ONNX Runtime Mobile ports — a good fit given ONNX Runtime would likely already be a dependency for ASR.
- **Diarization**: realistic MVP target is a lightweight speaker-embedding model (d-vector/x-vector class) + simple clustering (e.g., agglomerative) over VAD segments — full state-of-the-art diarization is out of scope for a phone.
- **LLM**: a small (1–3B parameter) instruction-tuned, quantized (Q4) model runnable via `llama.cpp` (GGUF) or the MediaPipe LLM Inference API. Expect materially lower summary/extraction quality than Gemini; set product expectations accordingly (see `docs/AUDIT.md` §J).
- **Embeddings**: a small sentence-embedding model (e.g., a distilled/quantized MiniLM-class model) via ONNX Runtime Mobile, replacing the current hash-based placeholder.

**Before integrating any specific model, re-verify its license terms** — `THIRD_PARTY_NOTICES.md` currently pre-declares licenses for models that aren't actually integrated; treat it as a template to complete once real choices are made, not a source of truth today.

## 4. RAM / Performance Tiers

`DeviceCapabilityDetector` already estimates total RAM and recommends a model tier (`recommendedAsrModelId`, `recommendedLlmModelId`) based on it. This is good groundwork that nothing currently consumes for actual model selection at runtime (only for a display recommendation during onboarding). The target: `ModelRepository`/pipeline should actually honor the selected/recommended model, and should refuse to load a model whose `minimumRamMb` exceeds the device's available RAM, degrading to a smaller tier automatically with a visible explanation to the user rather than crashing or silently underperforming.
